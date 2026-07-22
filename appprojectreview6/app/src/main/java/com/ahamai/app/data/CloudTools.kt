package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-level cloud-powered tools that wrap the E2B sandbox with sensible defaults.
 *
 * Each helper:
 *  - uploads the input file (or project) to the sandbox
 *  - runs a well-known tool with sensible flags (jadx, apktool, apkleaks, mobsfscan, nuclei, etc.)
 *  - returns a concise, model-readable report
 *  - downloads any useful artifacts back to the project
 *
 * The cloud sandbox is a Debian 12 (x86_64) box running as root. The base image ships git, curl,
 * wget, node/npm, python3/pip and ImageMagick; anything else (jadx, apktool, nuclei, nmap, sqlmap,
 * semgrep, …) is provisioned on demand via the aham_apt / aham_pip / aham_gh helpers in
 * [PROVISION_FUNCS], installs being idempotent and cached for the life of the sandbox.
 */
object CloudTools {

    private suspend fun syncProject(ctx: Context, projectDir: String): Triple<String, String, String> {
        val prefs = PreferencesManager(ctx)
        val apiKey = prefs.getE2bApiKey()
        val template = prefs.getE2bTemplate()
        // FastSync keeps a per-project manifest and only re-uploads changed files in PARALLEL.
        // On a typical incremental call this does 0 work — that's the whole point.
        FastSync.syncProjectAtOnce(projectDir, apiKey, template, File(projectDir))
        return Triple(apiKey, template, projectDir)
    }

    private suspend fun run(ctx: Context, projectDir: String, cmd: String, timeoutSec: Int = 120): E2BClient.ShellResult {
        val (apiKey, template, _) = syncProject(ctx, projectDir)
        return E2BClient.exec(projectDir, apiKey, template, cmd, "/workspace", timeoutSec)
    }

    /**
     * Self-monitoring run: like [run] but uses [E2BClient.execResilient] so a stuck sandbox is
     * detected and re-spawned, and the caller gets a heartbeat every 5s while the command runs.
     * Use this for anything that might be slow (installs, scans, builds).
     */
    suspend fun runMonitored(
        ctx: Context, projectDir: String, cmd: String, timeoutSec: Int = 180,
        onHeartbeat: (String) -> Unit = {}
    ): E2BClient.ShellResult {
        val (apiKey, template, _) = syncProject(ctx, projectDir)
        return E2BClient.execResilient(projectDir, apiKey, template, cmd, "/workspace", timeoutSec, onHeartbeat)
    }

    /**
     * Upload a single project file straight into the sandbox at /workspace/<relPath>.
     *
     * Needed for the APK security tools because [E2BClient.syncProjectToSandbox] deliberately
     * skips `.apk` files (and anything > 2 MB), so the binary they operate on would otherwise
     * never reach the sandbox. Returns true on success.
     */
    private suspend fun uploadTarget(ctx: Context, projectDir: String, relPath: String): Boolean {
        val prefs = PreferencesManager(ctx)
        val f = File(projectDir, relPath)
        if (!f.exists() || !f.isFile) return false
        return E2BClient.uploadFile(
            projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(), f, "/workspace/$relPath"
        )
    }

    /**
     * Make sure a Python module is importable in the sandbox, installing it via pip if not.
     * Unlike the old fire-and-forget `pip install ... >/dev/null 2>&1; true`, this verifies the
     * import afterwards and returns a human-readable error if the install genuinely failed, so the
     * caller can report it instead of leaking a cryptic ModuleNotFoundError later.
     *
     * Returns null on success, or an error string describing the failure.
     */
    private suspend fun ensurePyModule(
        ctx: Context, projectDir: String, importName: String, pipPkg: String = importName, timeoutSec: Int = 240
    ): String? {
        val prefs = PreferencesManager(ctx)
        val apiKey = prefs.getE2bApiKey()
        val template = prefs.getE2bTemplate()
        val cmd =
            "python3 -c 'import $importName' 2>/dev/null && echo __OK__ && exit 0; " +
            "pip install --no-input -q --break-system-packages $pipPkg 2>&1 | tail -4; " +
            "python3 -c 'import $importName' 2>/dev/null && echo __OK__ || echo __FAIL__"
        val res = E2BClient.exec(projectDir, apiKey, template, cmd, "/workspace", timeoutSec)
        return if (res.stdout.contains("__OK__")) null
        else "could not install '$pipPkg' in the sandbox:\n${res.formatted(800)}"
    }

    // ----------------------------------------------------------------------------------------
    // Provisioning + sandbox plumbing shared with CyberTools / MediaTools.
    // ----------------------------------------------------------------------------------------

    /**
     * A small bash "toolbox" of install helpers, prepended (sourced) to any command that needs to
     * self-provision a tool. Written with the placeholder `DOLLAR` instead of `$` so the Kotlin
     * source stays readable; [prov] swaps it back before the command is sent.
     *
     *   aham_apt  <pkgs...>            apt-get install (runs update once, non-interactive, quiet)
     *   aham_pip  <pkgs...>            pip install (--break-system-packages, quiet)
     *   aham_gh   <owner/repo> <assetRegex> <binInArchive>   download the LATEST GitHub release
     *                                   asset (zip or tar.gz, auto-resolved) and drop the binary
     *                                   into /usr/local/bin.
     */
    private val PROVISION_FUNCS: String = """
        export DEBIAN_FRONTEND=noninteractive
        export PATH="/usr/local/bin:DOLLARPATH"
        aham_apt(){ [ -f /tmp/.aham_aptup ] || { apt-get update -qq >/dev/null 2>&1; touch /tmp/.aham_aptup; }; apt-get install -y -qq -o DPkg::Lock::Timeout=420 "DOLLAR@" >/dev/null 2>&1 || apt-get install -y -o DPkg::Lock::Timeout=420 "DOLLAR@"; }
        aham_pip(){ pip install -q --no-input --break-system-packages "DOLLAR@" 2>&1 | tail -2; }
        aham_gh(){ u=DOLLAR(curl -sL "https://api.github.com/repos/DOLLAR1/releases/latest" | grep -oE '"browser_download_url": *"[^"]*"' | cut -d'"' -f4 | grep -iE "DOLLAR2" | grep -viE 'sha256|sha512|\.asc|\.sig' | head -1); [ -z "DOLLARu" ] && { echo "no asset for DOLLAR1"; return 1; }; cd /tmp && rm -rf _gh && mkdir _gh && cd _gh; curl -sL "DOLLARu" -o a; case "DOLLARu" in *.zip) unzip -o -q a;; *.tar.gz|*.tgz) tar xzf a;; esac; f=DOLLAR(find . -name "DOLLAR3" -type f | head -1); install -m755 "DOLLARf" /usr/local/bin/DOLLAR3 2>/dev/null || cp "DOLLARf" /usr/local/bin/DOLLAR3; cd /; }
    """.trimIndent().replace("DOLLAR", "$") + "\n"

    /** Sync all project source files up to /workspace (skips .apk / large binaries). Public wrapper. */
    suspend fun syncProjectUp(ctx: Context, projectDir: String): Int {
        val prefs = PreferencesManager(ctx)
        val apiKey = prefs.getE2bApiKey()
        val template = prefs.getE2bTemplate()
        // Use FastSync — only changed files are uploaded, in parallel.
        val summary = FastSync.syncProjectAtOnce(projectDir, apiKey, template, File(projectDir))
        return Regex("(\\d+) uploaded").find(summary)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * Force a full re-sync (clears the FastSync manifest and re-uploads everything). Use this
     * after the project has been swapped, rebuilt from scratch, or when tool output suggests the
     * sandbox is out of sync (e.g. file-not-found errors that shouldn't happen).
     */
    suspend fun forceResync(ctx: Context, projectDir: String): Int {
        FastSync.invalidate(projectDir)
        return syncProjectUp(ctx, projectDir)
    }

    /** Public wrapper around [ensurePyModule] for other tool objects (e.g. MediaTools). */
    suspend fun ensurePython(ctx: Context, projectDir: String, importName: String, pipPkg: String): String? =
        ensurePyModule(ctx, projectDir, importName, pipPkg)

    /**
     * Start [command] as a BACKGROUND job in the sandbox (survives via setsid). Returns instantly
     * with a job id. The agent can then poll [jobStatus] to monitor long-running work (big installs,
     * long scans, builds) and react if it stalls or fails — instead of blocking on one long call.
     */
    suspend fun runJob(ctx: Context, projectDir: String, command: String): String {
        val id = "job_" + System.currentTimeMillis()
        val script = PROVISION_FUNCS + "\n" + command + "\n"
        val b64 = Base64.encodeToString(script.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val launch = "mkdir -p /workspace/.jobs; echo '$b64' | base64 -d > /workspace/.jobs/$id.sh; " +
            "setsid bash /workspace/.jobs/$id.sh >/workspace/.jobs/$id.log 2>&1 </dev/null & echo \$! > /workspace/.jobs/$id.pid; echo STARTED"
        val res = execIn(ctx, projectDir, launch, 40)
        return if (res.stdout.contains("STARTED"))
            "JOB STARTED — id: $id\nIt is running in the background. Poll with JOB_STATUS \"$id\" to watch its output and completion."
        else "ERROR starting job:\n${res.formatted(700)}"
    }

    /** Check a background job started by [runJob]: running/finished + its latest output. */
    suspend fun jobStatus(ctx: Context, projectDir: String, id: String): String {
        val jid = id.trim().filter { it.isLetterOrDigit() || it == '_' }
        if (jid.isBlank()) return "ERROR: provide the job id from RUN_JOB."
        val cmd = "j=/workspace/.jobs/$jid; " +
            "if [ -f \$j.pid ]; then if kill -0 \$(cat \$j.pid) 2>/dev/null; then echo 'STATE: running'; else echo 'STATE: finished'; fi; else echo 'STATE: unknown (no such job)'; fi; " +
            "echo '--- latest output ---'; tail -c 5000 \$j.log 2>/dev/null"
        val res = execIn(ctx, projectDir, cmd, 30)
        val body = "JOB $jid\n${res.formatted(6000)}"
        // When the background job is done, reverse-sync any new cloud outputs into the project.
        return if (res.stdout.contains("STATE: finished")) {
            val prefs = PreferencesManager(ctx)
            val pull = FastSync.pullBackFromSandbox(
                projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(), File(projectDir)
            )
            "$body\n[auto-sync] $pull"
        } else body
    }

    /** Prepend the provisioning toolbox so a command can call aham_apt / aham_pip / aham_gh. */
    private fun prov(cmd: String): String = PROVISION_FUNCS + cmd

    /** Run a command in the project's sandbox. Public so CyberTools / MediaTools can reuse it. */
    suspend fun execIn(ctx: Context, projectDir: String, cmd: String, timeoutSec: Int = 120): E2BClient.ShellResult {
        val prefs = PreferencesManager(ctx)
        return E2BClient.exec(projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(), cmd, "/workspace", timeoutSec)
    }

    /** Run a command WITH the provisioning toolbox sourced first. */
    suspend fun execProv(ctx: Context, projectDir: String, cmd: String, timeoutSec: Int = 240): E2BClient.ShellResult =
        execIn(ctx, projectDir, prov(cmd), timeoutSec)

    /**
     * Ensure a CLI [bin] exists in the sandbox; if not, run [installScript] (which may call the
     * aham_* helpers) and re-check. Returns null on success or a human-readable error.
     */
    suspend fun ensureCmd(
        ctx: Context, projectDir: String, bin: String, installScript: String, timeoutSec: Int = 360
    ): String? {
        val cmd = prov(
            "command -v '$bin' >/dev/null 2>&1 && { echo __OK__; exit 0; }; " +
            "$installScript; " +
            "command -v '$bin' >/dev/null 2>&1 && echo __OK__ || echo __FAIL__"
        )
        val res = execIn(ctx, projectDir, cmd, timeoutSec)
        return if (res.stdout.contains("__OK__")) null
        else "could not provision '$bin' in the sandbox:\n${res.formatted(700)}"
    }

    /** Upload one project file to /workspace/<relPath>. Public wrapper around [uploadTarget]. */
    suspend fun pushProjectFile(ctx: Context, projectDir: String, relPath: String): Boolean =
        uploadTarget(ctx, projectDir, relPath)

    /**
     * Pull a sandbox file or directory back into the project. Single files are fetched directly;
     * directories are enumerated and each file pulled, preserving relative layout. Returns a
     * short summary.
     */
    suspend fun cloudPull(ctx: Context, projectDir: String, remotePath: String, projectDest: String): String {
        val apiKey = PreferencesManager(ctx).getE2bApiKey()
        val remoteAbs = if (remotePath.startsWith("/")) remotePath else "/workspace/$remotePath"
        val kind = execIn(ctx, projectDir,
            "if [ -d '$remoteAbs' ]; then echo DIR; elif [ -f '$remoteAbs' ]; then echo FILE; else echo NONE; fi", 20)
            .stdout.trim()
        when {
            kind.startsWith("FILE") -> {
                val dest = projectDest.ifBlank { remoteAbs.substringAfterLast('/') }
                val local = File(projectDir, dest)
                local.parentFile?.mkdirs()
                val ok = E2BClient.downloadFile(apiKey, remoteAbs, local)
                return if (ok && local.exists()) "PULLED file → project/$dest (${local.length()} bytes)"
                else "ERROR: failed to pull $remoteAbs"
            }
            kind.startsWith("DIR") -> {
                val list = execIn(ctx, projectDir, "find '$remoteAbs' -type f | head -500", 30).stdout
                    .lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (list.isEmpty()) return "PULLED: directory $remoteAbs is empty."
                var n = 0
                for (remoteFile in list) {
                    val rel = remoteFile.removePrefix(remoteAbs).removePrefix("/")
                    val local = File(File(projectDir, projectDest), rel)
                    local.parentFile?.mkdirs()
                    if (E2BClient.downloadFile(apiKey, remoteFile, local) && local.exists()) n++
                }
                return "PULLED $n/${list.size} files from $remoteAbs → project/$projectDest/"
            }
            else -> return "ERROR: $remoteAbs not found in the sandbox."
        }
    }

    /** Push a project file (or every file under a folder) into the sandbox at [remoteDest]. */
    suspend fun cloudPush(ctx: Context, projectDir: String, projectPath: String, remoteDest: String): String {
        val prefs = PreferencesManager(ctx)
        val src = File(projectDir, projectPath)
        if (!src.exists()) return "ERROR: project path not found: $projectPath"
        val remoteBase = remoteDest.ifBlank { "/workspace/$projectPath" }
            .let { if (it.startsWith("/")) it else "/workspace/$it" }
        if (src.isFile) {
            val ok = E2BClient.uploadFile(projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(), src, remoteBase)
            return if (ok) "PUSHED project/$projectPath → $remoteBase" else "ERROR: push failed."
        }
        var n = 0; var total = 0
        src.walkTopDown().forEach { f ->
            if (!f.isFile) return@forEach
            total++
            val rel = f.relativeTo(src).path
            if (E2BClient.uploadFile(projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(), f, "$remoteBase/$rel")) n++
        }
        return "PUSHED $n/$total files from project/$projectPath → $remoteBase/"
    }

    /** List files in the sandbox under [path] (default /workspace). */
    suspend fun cloudList(ctx: Context, projectDir: String, path: String): String {
        val p = path.ifBlank { "/workspace" }.let { if (it.startsWith("/")) it else "/workspace/$it" }
        val res = execIn(ctx, projectDir,
            "echo '== $p =='; ls -la '$p' 2>&1 | head -100; echo; echo '== tree (files) =='; find '$p' -maxdepth 3 -type f 2>/dev/null | head -200", 30)
        return "CLOUD FILES:\n${res.formatted(4000)}"
    }

    /**
     * CLOUD_INSTALL — let the agent provision anything on demand. [manager] is apt | pip | npm |
     * go | gh | auto; [spec] is the package list (for gh: "owner/repo assetRegex binName").
     */
    suspend fun installPackages(ctx: Context, projectDir: String, manager: String, spec: String): String {
        val mgr = manager.trim().lowercase().ifBlank { "auto" }
        val pkgs = spec.trim()
        if (pkgs.isBlank()) return "ERROR: nothing to install (empty package list)."
        val cmd = when (mgr) {
            "apt", "apt-get" -> "aham_apt $pkgs; echo '--- done ---'; for p in $pkgs; do command -v \"\$p\" >/dev/null 2>&1 && echo \"ok: \$p\"; done"
            "pip", "pip3", "python" -> "aham_pip $pkgs"
            "npm", "node" -> "npm install -g $pkgs 2>&1 | tail -5"
            "go" -> "command -v go >/dev/null 2>&1 || aham_apt golang-go; for m in $pkgs; do GOBIN=/usr/local/bin go install \"\$m\" 2>&1 | tail -2; done"
            "gh", "github" -> "aham_gh $pkgs && echo '--- gh install done ---'"
            else -> "aham_apt $pkgs 2>/dev/null; aham_pip $pkgs 2>/dev/null; echo '--- tried apt + pip ---'"
        }
        val res = execProv(ctx, projectDir, cmd, 480)
        return "INSTALL ($mgr) $pkgs:\n${res.formatted(3000)}"
    }

    /** Decompile an APK using jadx (Java source) + apktool (resources/manifest). */
    suspend fun decompileApk(ctx: Context, projectDir: String, apkRelPath: String, outFolder: String): String = withContext(Dispatchers.IO) {
        val apk = File(projectDir, apkRelPath)
        if (!apk.exists() || !apk.isFile) {
            return@withContext "ERROR: APK not found in project: $apkRelPath"
        }
        val apkRemote = "/workspace/$apkRelPath"
        val outRemote = "/workspace/$outFolder"
        // Upload the APK itself (project sync skips .apk / large binaries).
        if (!uploadTarget(ctx, projectDir, apkRelPath)) {
            return@withContext "ERROR: failed to upload APK to the cloud sandbox ($apkRelPath)."
        }
        // Provision jadx + apktool (real installs — they are NOT apt packages on Debian).
        ensureCmd(ctx, projectDir, "apktool",
            "aham_apt default-jre-headless; " +
            "curl -sL https://raw.githubusercontent.com/iBotPeaches/Apktool/master/scripts/linux/apktool -o /usr/local/bin/apktool; " +
            "j=\$(curl -sL https://api.github.com/repos/iBotPeaches/Apktool/releases/latest | grep -oE '\"browser_download_url\": *\"[^\"]*apktool_[^\"]*\\.jar\"' | cut -d'\"' -f4 | head -1); " +
            "curl -sL \"\$j\" -o /usr/local/bin/apktool.jar; chmod +x /usr/local/bin/apktool /usr/local/bin/apktool.jar", 300)
        ensureCmd(ctx, projectDir, "jadx",
            "aham_apt default-jre-headless; " +
            "u=\$(curl -sL https://api.github.com/repos/skylot/jadx/releases/latest | grep -oE '\"browser_download_url\": *\"[^\"]*jadx-[0-9][^\"]*\\.zip\"' | cut -d'\"' -f4 | head -1); " +
            "cd /tmp && curl -sL \"\$u\" -o jadx.zip && unzip -o -q jadx.zip -d /opt/jadx && chmod +x /opt/jadx/bin/jadx && ln -sf /opt/jadx/bin/jadx /usr/local/bin/jadx", 300)
        // Run jadx (Java source) into <outFolder>/src and apktool (resources) into <outFolder>/res.
        val r1 = execProv(ctx, projectDir, "rm -rf '$outRemote' && mkdir -p '$outRemote/src' '$outRemote/res' && jadx -q -d '$outRemote/src' '$apkRemote' 2>&1 | tail -5; echo '---jadx done---'", 300)
        val r2 = execProv(ctx, projectDir, "apktool d -f -o '$outRemote/res' '$apkRemote' 2>&1 | tail -5; echo '---apktool done---'", 300)
        // Pull the source tree back to the project so READ_FILE works.
        val outLocal = File(projectDir, outFolder)
        outLocal.mkdirs()
        // List the top of the decompiled tree so the model knows what was generated.
        val tree = execProv(ctx, projectDir, "find '$outRemote' -type f | head -120", 30)
        // Download key files (manifest + a few sample classes) back to the project.
        // Pulling the whole tree could be huge, so we just sync the AndroidManifest.
        runCatching {
            E2BClient.downloadFile(
                PreferencesManager(ctx).getE2bApiKey(),
                "$outRemote/res/AndroidManifest.xml",
                File(outLocal, "AndroidManifest.xml")
            )
        }
        // Tar the decompiled source into a small archive and pull it back so individual classes are readable.
        runCatching {
            execProv(ctx, projectDir, "cd '$outRemote' && tar czf /workspace/${outFolder}_src.tar.gz src 2>/dev/null; true", 60)
        }
        val tarLocal = File(projectDir, "${outFolder}_src.tar.gz")
        runCatching {
            E2BClient.downloadFile(
                PreferencesManager(ctx).getE2bApiKey(),
                "/workspace/${outFolder}_src.tar.gz",
                tarLocal
            )
        }
        if (tarLocal.exists() && tarLocal.length() > 0) {
            // Extract locally so READ_FILE can access individual classes.
            try {
                ProcessBuilder("tar", "xzf", tarLocal.absolutePath, "-C", outLocal.absolutePath)
                    .redirectErrorStream(true).start().waitFor()
            } catch (_: Exception) { /* Android may not have tar — leave the .tar.gz for the user */ }
        }
        buildString {
            appendLine("APK DECOMPILED → project/$outFolder/")
            appendLine("Java source: $outFolder/src/   (jadx)")
            appendLine("Resources:  $outFolder/res/   (apktool — AndroidManifest.xml pulled back)")
            if (tarLocal.exists()) appendLine("Source bundle: $outFolder/${outFolder}_src.tar.gz (${tarLocal.length() / 1024} KB)")
            appendLine()
            appendLine("Top of decompiled tree:")
            appendLine(tree.stdout.take(3000))
            if (r1.stderr.isNotBlank() || r1.exitCode != 0) appendLine("[jadx] ${r1.formatted(500)}")
        }
    }

    /** Extract APK metadata via androguard (package, version, permissions, components, SDK). */
    suspend fun apkInfo(ctx: Context, projectDir: String, apkRelPath: String): String = withContext(Dispatchers.IO) {
        val apk = File(projectDir, apkRelPath)
        if (!apk.exists()) return@withContext "ERROR: APK not found: $apkRelPath"
        val prefs = PreferencesManager(ctx)
        val apiKey = prefs.getE2bApiKey()
        val template = prefs.getE2bTemplate()

        // 1) Upload the APK itself — syncProjectToSandbox skips .apk / large files, so it would
        //    never reach the sandbox otherwise.
        if (!uploadTarget(ctx, projectDir, apkRelPath)) {
            return@withContext "ERROR: failed to upload APK to the cloud sandbox ($apkRelPath)."
        }
        // 2) Ensure androguard is importable (install + verify, with a clear error on failure).
        ensurePyModule(ctx, projectDir, "androguard", "androguard")?.let {
            return@withContext "ERROR: $it"
        }

        val script = """
            try:
                from loguru import logger
                logger.remove()   # silence androguard's very verbose DEBUG logging
            except Exception:
                pass
            from androguard.core.apk import APK
            import json
            a = APK("/workspace/$apkRelPath")
            info = {
              "package": a.get_package(),
              "version": a.get_androidversion_name(),
              "min_sdk": a.get_min_sdk_version(),
              "target_sdk": a.get_target_sdk_version(),
              "permissions": a.get_permissions(),
              "activities": a.get_activities(),
              "services": a.get_services(),
              "receivers": a.get_receivers(),
              "providers": a.get_providers(),
              "main_activity": a.get_main_activity(),
              "signers": len(a.get_certificates())
            }
            print(json.dumps(info, indent=2))
        """.trimIndent()
        val scriptFile = File(projectDir, ".apkinfo_${System.currentTimeMillis()}.py")
        scriptFile.writeText(script)
        try {
            // Upload the script directly (don't rely on a full project sync that a parallel tool
            // call might interleave with).
            E2BClient.uploadFile(projectDir, apiKey, template, scriptFile, "/workspace/${scriptFile.name}")
            val res = E2BClient.exec(
                projectDir, apiKey, template,
                "python3 '/workspace/${scriptFile.name}'", "/workspace", 120
            )
            "APK INFO for $apkRelPath:\n${res.formatted(4000)}"
        } finally {
            scriptFile.delete()
        }
    }

    /** Scan a file or directory for hardcoded secrets using apkleaks. */
    suspend fun scanSecrets(ctx: Context, projectDir: String, targetRelPath: String): String = withContext(Dispatchers.IO) {
        // Upload the target (apkleaks usually runs on an .apk, which project sync skips).
        uploadTarget(ctx, projectDir, targetRelPath)
        ensurePyModule(ctx, projectDir, "apkleaks", "apkleaks")?.let {
            return@withContext "ERROR: $it"
        }
        val out = execProv(ctx, projectDir, "apkleaks -f '/workspace/$targetRelPath' -o /workspace/_secrets.json 2>&1 | tail -3; echo '---'; cat /workspace/_secrets.json 2>/dev/null | head -200", 240)
        "SECRET SCAN of $targetRelPath:\n${out.formatted(5000)}"
    }

    /** Run MobSF static analysis on an APK or project folder. */
    suspend fun securityAudit(ctx: Context, projectDir: String, targetRelPath: String): String = withContext(Dispatchers.IO) {
        // Upload the target if it's a file (e.g. an .apk that project sync skips).
        uploadTarget(ctx, projectDir, targetRelPath)
        ensurePyModule(ctx, projectDir, "mobsfscan", "mobsfscan")?.let {
            return@withContext "ERROR: $it"
        }
        // mobsfscan works on source folders; if the target is an APK, fall back to apktool output + a manifest check.
        val out = execProv(ctx, projectDir,
            "if [ -d '/workspace/$targetRelPath' ]; then " +
                "cd '/workspace/$targetRelPath' && mobsfscan . --json > /workspace/_audit.json 2>/dev/null; " +
            "elif [ -f '/workspace/$targetRelPath' ]; then " +
                "mkdir -p /workspace/_audit_src && apktool d -f '/workspace/$targetRelPath' -o /workspace/_audit_src >/dev/null 2>&1 && cd /workspace/_audit_src && mobsfscan . --json > /workspace/_audit.json 2>/dev/null; " +
            "fi; echo '--- audit done ---'; cat /workspace/_audit.json 2>/dev/null | head -300",
            300
        )
        "SECURITY AUDIT of $targetRelPath:\n${out.formatted(6000)}"
    }

    /** Scan a web target with nuclei (5000+ templates) + nmap (top ports). */
    suspend fun webScan(ctx: Context, projectDir: String, targetUrl: String): String = withContext(Dispatchers.IO) {
        // Provision nuclei (latest prebuilt) + nmap (apt).
        ensureCmd(ctx, projectDir, "nmap", "aham_apt nmap", 240)
        val nucleiErr = ensureCmd(ctx, projectDir, "nuclei",
            "aham_gh projectdiscovery/nuclei 'linux_amd64.zip' nuclei", 300)
        // Derive host from URL: strip scheme + path.
        val host = targetUrl
            .removePrefix("https://").removePrefix("http://")
            .substringBefore('/').ifBlank { targetUrl }
        // Run nmap (fast top-ports) + nuclei (severity medium+).
        val r1 = execProv(ctx, projectDir, "nmap -sV -T4 --top-ports 100 -Pn '$host' 2>&1 | head -60", 180)
        val r2 = execProv(ctx, projectDir, "nuclei -u '$targetUrl' -severity medium,high,critical -silent 2>&1 | head -80; echo '---nuclei done---'", 240)
        buildString {
            appendLine("WEB SCAN of $targetUrl")
            appendLine()
            appendLine("=== nmap (top 100 ports) ===")
            appendLine(r1.stdout.take(2000))
            appendLine()
            appendLine("=== nuclei (medium+ severity) ===")
            if (nucleiErr != null) appendLine("[nuclei not available] $nucleiErr")
            appendLine(r2.stdout.take(2500))
            if (r2.stderr.isNotBlank()) appendLine("[nuclei stderr] ${r2.stderr.take(400)}")
        }
    }

    /**
     * Mirror a website's source (HTML/CSS/JS/assets) into the project using wget. [depth] limits
     * recursion (default 2); the mirror is capped to keep it sane, then pulled back to the project.
     */
    suspend fun cloneSite(ctx: Context, projectDir: String, url: String, outFolder: String, depth: Int): String = withContext(Dispatchers.IO) {
        val out = outFolder.ifBlank { "site_" + System.currentTimeMillis() }
        val d = if (depth in 1..5) depth else 2
        val res = execProv(ctx, projectDir,
            "rm -rf '/workspace/$out' && mkdir -p '/workspace/$out'; " +
            "wget -e robots=off -r -l $d -k -K -p -E -np -nH --restrict-file-names=windows " +
            "--timeout=20 --tries=2 -Q50m -P '/workspace/$out' '${url.trim()}' 2>&1 | tail -8; " +
            "echo '--- mirror done ---'; find '/workspace/$out' -type f | head -60; " +
            "echo; echo \"files: \$(find '/workspace/$out' -type f | wc -l)  size: \$(du -sh '/workspace/$out' 2>/dev/null | cut -f1)\"",
            420)
        val pull = cloudPull(ctx, projectDir, "/workspace/$out", out)
        buildString {
            appendLine("SITE CLONE of ${url.trim()} → project/$out/")
            appendLine(res.formatted(4000))
            appendLine()
            appendLine(pull)
        }
    }

    /**
     * Rebuild an APK from an apktool-decompiled folder, then zipalign + sign it (v1/v2/v3, auto
     * debug key) with uber-apk-signer so the result is installable. The signed APK is pulled back
     * into the project. [srcFolderRel] is the decompiled folder (the one containing apktool.yml,
     * e.g. the `res` folder produced by APK_DECOMPILE); [outApkRel] is where to save it.
     */
    suspend fun rebuildApk(ctx: Context, projectDir: String, srcFolderRel: String, outApkRel: String): String = withContext(Dispatchers.IO) {
        val src = File(projectDir, srcFolderRel)
        if (!src.exists() || !src.isDirectory) return@withContext "ERROR: decompiled folder not found in project: $srcFolderRel"
        val outApk = outApkRel.ifBlank { "rebuilt.apk" }
        // Provision apktool + java + uber-apk-signer.
        ensureCmd(ctx, projectDir, "apktool",
            "aham_apt default-jre-headless; curl -sL https://raw.githubusercontent.com/iBotPeaches/Apktool/master/scripts/linux/apktool -o /usr/local/bin/apktool; " +
            "j=\$(curl -sL https://api.github.com/repos/iBotPeaches/Apktool/releases/latest | grep -oE '\"browser_download_url\": *\"[^\"]*apktool_[^\"]*\\.jar\"' | cut -d'\"' -f4 | head -1); " +
            "curl -sL \"\$j\" -o /usr/local/bin/apktool.jar; chmod +x /usr/local/bin/apktool /usr/local/bin/apktool.jar", 300)
            ?.let { return@withContext "ERROR: $it" }
        // Push the decompiled sources up.
        val pushed = cloudPush(ctx, projectDir, srcFolderRel, "/workspace/_rebuild_src")
        // Build, then sign with uber-apk-signer (downloads a debug key automatically).
        val res = execProv(ctx, projectDir,
            "set -o pipefail; " +
            "rm -f /workspace/_built.apk; " +
            "apktool b '/workspace/_rebuild_src' -o /workspace/_built.apk 2>&1 | tail -8; " +
            "echo '--- apktool build done ---'; " +
            "[ -f /workspace/_built.apk ] || { echo 'BUILD FAILED — no apk produced'; exit 1; }; " +
            "s=\$(curl -sL https://api.github.com/repos/patrickfav/uber-apk-signer/releases/latest | grep -oE '\"browser_download_url\": *\"[^\"]*\\.jar\"' | cut -d'\"' -f4 | head -1); " +
            "curl -sL \"\$s\" -o /usr/local/bin/uber-apk-signer.jar; " +
            "java -jar /usr/local/bin/uber-apk-signer.jar -a /workspace/_built.apk --overwrite --allowResign 2>&1 | tail -8; " +
            "echo '--- signing done ---'; ls -la /workspace/_built.apk",
            420)
        // Pull the signed APK back.
        val local = File(projectDir, outApk)
        local.parentFile?.mkdirs()
        val ok = E2BClient.downloadFile(PreferencesManager(ctx).getE2bApiKey(), "/workspace/_built.apk", local)
        buildString {
            appendLine("APK REBUILD from project/$srcFolderRel/")
            appendLine(pushed)
            appendLine(res.formatted(3500))
            appendLine()
            if (ok && local.exists() && local.length() > 0)
                appendLine("SIGNED APK → project/$outApk (${local.length() / 1024} KB). Use EXPORT_TO_DEVICE to save it to the phone, or install it.")
            else
                appendLine("WARNING: build/sign produced no downloadable APK — check the log above.")
        }
    }
}
