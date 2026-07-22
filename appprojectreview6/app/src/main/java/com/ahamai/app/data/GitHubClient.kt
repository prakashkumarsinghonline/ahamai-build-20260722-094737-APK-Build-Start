package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * GitHub REST API client (token-based). Lets the user connect a repo, the agent edit it
 * locally, then push commits / open PRs / manage issues, and build an APK via GitHub Actions.
 *
 * All network methods are suspend and run on Dispatchers.IO.
 */
object GitHubClient {

    private const val API = "https://api.github.com"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    data class GhUser(val login: String, val name: String, val avatarUrl: String)
    data class GhRepo(val fullName: String, val defaultBranch: String, val private: Boolean, val updatedAt: String, val language: String?)
    data class BuildState(val status: String, val conclusion: String?, val htmlUrl: String, val runId: Long)
    data class BuildStepInfo(val name: String, val status: String, val conclusion: String?)

    private fun req(token: String, url: String): Request.Builder =
        Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "AhamAI-Android")

    // ---------------------------------------------------------------- auth / repos

    // Set this to a GitHub OAuth App client id to enable the "Login with GitHub" device flow.
    // It can ALSO be set remotely (no rebuild) via Firebase Remote Config key `github_client_id`
    // or Firestore config/settings { github_client_id }. While unset everywhere, the UI
    // automatically falls back to the manual Personal Access Token dialog.
    const val GITHUB_CLIENT_ID = "Ov23ctE9bY3vDHI37s4T" // Hardcoded for testing — set remotely via Remote Config key `github_client_id` for production

    /** Effective client id: the compiled-in constant, or the remotely-configured value. */
    fun effectiveClientId(): String = GITHUB_CLIENT_ID.ifBlank { RemoteConfigManager.githubClientId() }

    /** True when "Login with GitHub" can be used (a client id is available). */
    fun isLoginConfigured(): Boolean = effectiveClientId().isNotBlank()

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUri: String, val interval: Int, val expiresIn: Int)

    /** Starts GitHub device-flow login. Returns null if not configured or on error. */
    suspend fun startDeviceLogin(): DeviceCode? = withContext(Dispatchers.IO) {
        val clientId = effectiveClientId()
        if (clientId.isBlank()) return@withContext null
        try {
            // delete_repo required so temp APK-build repos can always be wiped after success/fail.
            val body = "client_id=$clientId&scope=repo%20workflow%20delete_repo".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val r = client.newCall(Request.Builder().url("https://github.com/login/device/code")
                .addHeader("Accept","application/json").post(body).build()).execute()
            r.use {
                if (!it.isSuccessful) return@withContext null
                val o = org.json.JSONObject(it.body!!.string())
                DeviceCode(o.getString("device_code"), o.getString("user_code"), o.getString("verification_uri"),
                    o.optInt("interval",5), o.optInt("expires_in",900))
            }
        } catch (_: Exception) { null }
    }

    /** Polls once for the access token. Returns Pair(token, status). status in: "ok","pending","slow_down","expired","denied","error". */
    suspend fun pollDeviceToken(deviceCode: String): Pair<String?, String> = withContext(Dispatchers.IO) {
        try {
            val body = ("client_id=${effectiveClientId()}&device_code=$deviceCode" +
                "&grant_type=urn:ietf:params:oauth:grant-type:device_code").toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val r = client.newCall(Request.Builder().url("https://github.com/login/oauth/access_token")
                .addHeader("Accept","application/json").post(body).build()).execute()
            r.use {
                val o = org.json.JSONObject(it.body!!.string())
                val tok = o.optString("access_token", "")
                if (tok.isNotBlank()) return@withContext tok to "ok"
                when (o.optString("error","")) {
                    "authorization_pending" -> null to "pending"
                    "slow_down" -> null to "slow_down"
                    "expired_token" -> null to "expired"
                    "access_denied" -> null to "denied"
                    else -> null to "error"
                }
            }
        } catch (_: Exception) { null to "error" }
    }

    /** Validates the token and returns the user, or null if invalid. */
    suspend fun getUser(token: String): GhUser? = withContext(Dispatchers.IO) {
        try {
            client.newCall(req(token, "$API/user").get().build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val o = JSONObject(r.body?.string() ?: "{}")
                GhUser(o.optString("login"), o.optString("name", o.optString("login")), o.optString("avatar_url"))
            }
        } catch (e: Exception) { null }
    }

    /** Lists repos the user can push to (owner + collaborator), most recently updated first. */
    suspend fun listRepos(token: String): List<GhRepo> = withContext(Dispatchers.IO) {
        val out = mutableListOf<GhRepo>()
        try {
            var page = 1
            while (page <= 3) {
                val url = "$API/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator&page=$page"
                client.newCall(req(token, url).get().build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext out
                    val arr = JSONArray(r.body?.string() ?: "[]")
                    if (arr.length() == 0) { page = 99; return@use }
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        out.add(GhRepo(
                            o.optString("full_name"),
                            o.optString("default_branch", "main"),
                            o.optBoolean("private"),
                            o.optString("updated_at"),
                            o.optString("language").takeIf { it != "null" && it.isNotBlank() }
                        ))
                    }
                }
                page++
            }
        } catch (_: Exception) {}
        out
    }

    suspend fun getRepo(token: String, fullName: String): GhRepo? = withContext(Dispatchers.IO) {
        try {
            client.newCall(req(token, "$API/repos/$fullName").get().build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val o = JSONObject(r.body?.string() ?: "{}")
                GhRepo(o.optString("full_name"), o.optString("default_branch", "main"),
                    o.optBoolean("private"), o.optString("updated_at"),
                    o.optString("language").takeIf { it != "null" && it.isNotBlank() })
            }
        } catch (e: Exception) { null }
    }

    /**
     * True for throwaway public APK-build repos created by [createTempBuildRepo].
     * Pattern: `ahamai-build-<stamp>-…` or legacy `*-build-yyyyMMdd-HHmmss`.
     * Never true for normal user repos (so we never auto-delete real projects).
     */
    fun isTempBuildRepo(fullName: String): Boolean {
        val name = fullName.substringAfter('/').trim()
        if (name.isBlank()) return false
        if (name.startsWith("ahamai-build-", ignoreCase = true)) return true
        // Legacy naming from older builds: projectName-build-yyyyMMdd-HHmmss
        return Regex(""".+-build-\d{8}-\d{6}$""", RegexOption.IGNORE_CASE).matches(name)
    }

    /** Deletes a repository by fullName (owner/name). Used to clean up temporary build repos. */
    suspend fun deleteRepo(token: String, fullName: String): String = withContext(Dispatchers.IO) {
        if (fullName.isBlank() || !fullName.contains('/')) return@withContext "ERROR: invalid repo name"
        // Retry — GitHub occasionally returns 403/502 right after a busy Actions run.
        var last = "ERROR: delete failed"
        repeat(3) { attempt ->
            try {
                val r = client.newCall(req(token, "$API/repos/$fullName").delete().build()).execute()
                r.use { resp ->
                    if (resp.isSuccessful || resp.code == 404) {
                        // 404 = already gone — treat as success (no public trace left)
                        return@withContext if (resp.code == 404) "Already gone: $fullName" else "Deleted $fullName"
                    }
                    last = "ERROR: delete failed: ${resp.code} ${resp.body?.string()?.take(120) ?: resp.message}"
                }
            } catch (e: Exception) {
                last = "ERROR: delete exception: ${e.message}"
            }
            if (attempt < 2) delay(800L * (attempt + 1))
        }
        last
    }

    /**
     * Delete a throwaway build repo if [fullName] matches the temp pattern.
     * No-op for permanent/user repos. Returns a short status string.
     */
    suspend fun deleteTempBuildRepo(token: String, fullName: String): String {
        if (fullName.isBlank()) return "skip: empty"
        if (!isTempBuildRepo(fullName)) return "skip: not a temp build repo ($fullName)"
        return deleteRepo(token, fullName)
    }

    /**
     * Create a PUBLIC throwaway repo for one APK build (unlimited free Actions minutes).
     * Caller MUST [deleteTempBuildRepo] on success, failure, or push error so source
     * never stays visible on GitHub.
     */
    suspend fun createTempBuildRepo(token: String, projectName: String): String? {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
        val slug = projectName.trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "-")
            .trim('-')
            .take(24)
            .ifBlank { "app" }
        // Prefix is the safety key for isTempBuildRepo + orphan cleanup.
        return createRepo(token, "ahamai-build-$stamp-$slug", private = false)
    }

    /**
     * Best-effort: delete orphaned public temp build repos left by crashes / failed
     * cleanups (name starts with ahamai-build- or matches legacy -build-stamp).
     * Keeps the user's real repos. Caps at [max] so listing stays fast.
     */
    suspend fun cleanupOrphanTempBuildRepos(token: String, max: Int = 15): Int = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext 0
        var deleted = 0
        try {
            val repos = listRepos(token).filter { isTempBuildRepo(it.fullName) }.take(max)
            for (r in repos) {
                val out = deleteRepo(token, r.fullName)
                if (!out.startsWith("ERROR")) deleted++
            }
        } catch (_: Exception) {}
        deleted
    }

    /** Creates a new repository under the user's account (auto-initialized with main branch). */
    suspend fun createRepo(token: String, name: String, private: Boolean = true): String? = withContext(Dispatchers.IO) {
        val safe = name.trim().replace(Regex("[^A-Za-z0-9_.-]"), "-").ifBlank { "ahamai-app" }
        val res = postJson(token, "$API/user/repos", JSONObject().apply {
            put("name", safe); put("private", private); put("auto_init", true)
            // Mark temp public build repos clearly in GitHub UI / API description.
            put(
                "description",
                if (!private && safe.startsWith("ahamai-build-"))
                    "AhamAI temporary APK build — auto-deleted after success or failure"
                else
                    "Created with AhamAI"
            )
        })
        if (res == null || res.has("_error")) null else res.optString("full_name").takeIf { it.isNotBlank() }
    }

    /** Lists branch names of a repo. */
    suspend fun listBranches(token: String, repo: String): List<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(req(token, "$API/repos/$repo/branches?per_page=100").get().build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext emptyList()
                val arr = JSONArray(r.body?.string() ?: "[]")
                (0 until arr.length()).map { arr.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
            }
        } catch (e: Exception) { emptyList() }
    }

    /** Per-account projects root (matches ProjectManager isolation). */
    private fun projectsRootFor(context: Context): File {
        val uid = AuthManager.uid()?.takeIf { it.isNotBlank() } ?: "guest"
        return File(context.filesDir, "projects_$uid").apply { mkdirs() }
    }

    /**
     * Downloads a repo (zipball) and extracts it into a new local project dir.
     * Returns the project dir path or null. Works for private repos with a valid token.
     */
    suspend fun downloadRepo(context: Context, token: String, fullName: String, branch: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$API/repos/$fullName/zipball/$branch"
                client.newCall(req(token, url).get().build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext null
                    val bytes = r.body?.bytes() ?: return@withContext null
                    val projectsRoot = projectsRootFor(context)
                    val safeName = fullName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9_.-]"), "_")
                    val projectDir = File(projectsRoot, "gh_${System.currentTimeMillis()}_$safeName").apply { mkdirs() }
                    ZipInputStream(bytes.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            val name = entry.name
                            // GitHub zipball wraps everything in "owner-repo-sha/" — strip that first segment
                            val rel = name.substringAfter('/', "")
                            if (rel.isNotBlank() && !rel.contains("..")) {
                                val outFile = File(projectDir, rel)
                                if (outFile.canonicalPath.startsWith(projectDir.canonicalPath)) {
                                    if (entry.isDirectory) outFile.mkdirs()
                                    else { outFile.parentFile?.mkdirs(); FileOutputStream(outFile).use { zis.copyTo(it) } }
                                }
                            }
                            zis.closeEntry(); entry = zis.nextEntry
                        }
                    }
                    projectDir.absolutePath
                }
            } catch (e: Exception) { null }
        }

    // ---------------------------------------------------------------- remote browse (no full clone needed)

    /** Agent-friendly list of repos the user can access. */
    suspend fun listReposText(token: String, query: String = ""): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "ERROR: GitHub not connected. Ask the user to connect GitHub (Repository on Agent home)."
        val all = listRepos(token)
        if (all.isEmpty()) return@withContext "No repositories found (token needs 'repo' scope, or account has no repos)."
        val q = query.trim().lowercase()
        val filtered = if (q.isBlank()) all else all.filter {
            it.fullName.lowercase().contains(q) || (it.language?.lowercase()?.contains(q) == true)
        }
        if (filtered.isEmpty()) return@withContext "No repos matching '$query'."
        buildString {
            // Keep match-note outside the string so static brace scanners aren't confused by nested quotes.
            val matchNote = if (q.isNotBlank()) " matching \"$query\"" else ""
            appendLine("GitHub repositories (${filtered.size}$matchNote):")
            filtered.take(40).forEach { r ->
                val vis = if (r.private) "private" else "public"
                val lang = r.language ?: "-"
                appendLine("• ${r.fullName}  [$vis · $lang · default:${r.defaultBranch}]")
            }
            if (filtered.size > 40) appendLine("… and ${filtered.size - 40} more. Narrow with a search query.")
            appendLine()
            append("Tip: GH_OPEN_REPO <owner/name> [branch] to download & work locally, or GH_READ_REMOTE / GH_LIST_REMOTE to browse without cloning.")
        }
    }

    /** Agent-friendly branch list. */
    suspend fun listBranchesText(token: String, repo: String): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
        if (repo.isBlank()) return@withContext "ERROR: No repo. Pass owner/name or connect a repo first."
        val branches = listBranches(token, repo)
        if (branches.isEmpty()) return@withContext "ERROR: No branches found for $repo (check name/token)."
        buildString {
            appendLine("Branches on $repo (${branches.size}):")
            branches.forEach { appendLine("• $it") }
            appendLine()
            append("Use GH_SWITCH_BRANCH <name> to download that branch, or GH_CREATE_BRANCH <name> [from] to create one.")
        }
    }

    /**
     * Read a single file from GitHub via Contents API (no full clone).
     * [path] is path in repo; [ref] is branch/tag/sha (optional).
     */
    suspend fun readRemoteFile(token: String, repo: String, path: String, ref: String = ""): String =
        withContext(Dispatchers.IO) {
            try {
                if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                if (repo.isBlank()) return@withContext "ERROR: repo required (owner/name)."
                val clean = path.trim().trimStart('/')
                if (clean.isBlank()) return@withContext "ERROR: file path required."
                val url = buildString {
                    append("$API/repos/$repo/contents/${clean.split('/').joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }}")
                    if (ref.isNotBlank()) append("?ref=${java.net.URLEncoder.encode(ref, "UTF-8")}")
                }
                client.newCall(req(token, url).get().build()).execute().use { r ->
                    val body = r.body?.string() ?: ""
                    if (!r.isSuccessful) return@withContext "ERROR: HTTP ${r.code} reading $repo/$clean — ${body.take(200)}"
                    val o = JSONObject(body)
                    if (o.optString("type") == "dir") {
                        return@withContext "ERROR: '$clean' is a directory. Use GH_LIST_REMOTE to list it."
                    }
                    val encoding = o.optString("encoding")
                    val contentB64 = o.optString("content").replace("\n", "")
                    if (encoding != "base64" || contentB64.isBlank()) {
                        return@withContext "ERROR: cannot decode content for $clean (may be too large — use GH_OPEN_REPO)."
                    }
                    val bytes = Base64.decode(contentB64, Base64.DEFAULT)
                    // Skip obvious binaries
                    val lower = clean.lowercase()
                    if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jar") ||
                        lower.endsWith(".apk") || lower.endsWith(".so") || lower.endsWith(".woff")
                    ) {
                        return@withContext "BINARY file $clean (${bytes.size} bytes). Open the repo locally to inspect."
                    }
                    val text = bytes.toString(Charsets.UTF_8)
                    val lines = text.lines()
                    val shown = if (lines.size > 400) {
                        lines.take(400).joinToString("\n") +
                            "\n… (${lines.size - 400} more lines — open repo with GH_OPEN_REPO for full file)"
                    } else text
                    "REMOTE FILE $repo@${ref.ifBlank { "default" }}:$clean (${lines.size} lines, ${bytes.size} bytes)\n\n$shown"
                }
            } catch (e: Exception) { "ERROR: ${e.message}" }
        }

    /** List files/folders at a remote path (Contents API). */
    suspend fun listRemotePath(token: String, repo: String, path: String = "", ref: String = ""): String =
        withContext(Dispatchers.IO) {
            try {
                if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                if (repo.isBlank()) return@withContext "ERROR: repo required (owner/name)."
                val clean = path.trim().trimStart('/')
                val url = buildString {
                    append("$API/repos/$repo/contents")
                    if (clean.isNotBlank()) {
                        append("/")
                        append(clean.split('/').joinToString("/") {
                            java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                        })
                    }
                    if (ref.isNotBlank()) append("?ref=${java.net.URLEncoder.encode(ref, "UTF-8")}")
                }
                client.newCall(req(token, url).get().build()).execute().use { r ->
                    val body = r.body?.string() ?: ""
                    if (!r.isSuccessful) return@withContext "ERROR: HTTP ${r.code} listing $repo/$clean — ${body.take(200)}"
                    // File object vs array
                    val trimmed = body.trim()
                    // Use both braces in the probes so naive brace balancers (static verify) stay balanced.
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        val o = JSONObject(trimmed)
                        return@withContext "REMOTE PATH is a file: ${o.optString("path")} (${o.optLong("size")} bytes). Use GH_READ_REMOTE to read it."
                    }
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) return@withContext "Empty directory: ${clean.ifBlank { "/" }}"
                    buildString {
                        appendLine("REMOTE LIST $repo@${ref.ifBlank { "default" }}:/${clean.ifBlank { "" }} (${arr.length()} entries)")
                        val dirs = mutableListOf<String>()
                        val files = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val name = o.optString("name")
                            val type = o.optString("type")
                            val size = o.optLong("size")
                            if (type == "dir") dirs.add("  [dir]  $name/")
                            else files.add("  [file] $name ($size b)")
                        }
                        dirs.sorted().forEach { appendLine(it) }
                        files.sorted().forEach { appendLine(it) }
                        appendLine()
                        append("Use GH_READ_REMOTE <path> [ref] to read a file, or GH_LIST_REMOTE <subdir> to go deeper.")
                    }
                }
            } catch (e: Exception) { "ERROR: ${e.message}" }
        }

    /**
     * Create a new empty private/public repo under the user, connect prefs, return result text.
     * Does not download locally — call openRepoLocally after if needed.
     */
    suspend fun createRepoAndConnect(
        context: Context,
        token: String,
        name: String,
        private: Boolean = true,
        description: String = "Created with AhamAI"
    ): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
        val full = createRepo(token, name, private)
            ?: return@withContext "ERROR: could not create repo (name taken, or token lacks 'repo' scope)."
        val info = getRepo(token, full)
        val branch = info?.defaultBranch ?: "main"
        PreferencesManager(context).saveConnectedRepo(full, branch)
        AuthManager.backupGithubToken(context)
        "OK: Created repository https://github.com/$full (default branch: $branch, ${if (private) "private" else "public"}).\n" +
            "Connected in app. Use GH_OPEN_REPO $full to download a local workspace, or GH_PUSH after local edits."
    }

    /**
     * Open/clone a remote repo into a local workspace and connect it.
     * Returns a marker the UI understands: [[GH_OPEN_PROJECT]]dir|name|repo|branch[[/GH_OPEN_PROJECT]]
     */
    suspend fun openRepoLocally(
        context: Context,
        token: String,
        fullName: String,
        branch: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "ERROR: GitHub not connected. Ask user to connect GitHub first."
        val repo = fullName.trim().removePrefix("https://github.com/").removeSuffix(".git").trim('/')
        if (!repo.contains('/')) return@withContext "ERROR: repo must be owner/name (got '$fullName')."
        val info = getRepo(token, repo)
            ?: return@withContext "ERROR: repo '$repo' not found or no access."
        val br = branch.ifBlank { info.defaultBranch }.ifBlank { "main" }
        val dir = downloadRepo(context, token, repo, br)
            ?: return@withContext "ERROR: failed to download $repo@$br (check branch name / token)."
        PreferencesManager(context).saveConnectedRepo(repo, br)
        AuthManager.backupGithubToken(context)
        val shortName = repo.substringAfterLast('/')
        ProjectManager.setSessionTitle(dir, shortName)
        "OK: Opened $repo@$br locally.\n" +
            "[[GH_OPEN_PROJECT]]$dir|$shortName|$repo|$br[[/GH_OPEN_PROJECT]]\n" +
            "You can now LIST_FILES / READ_FILE / EDIT_FILE in this workspace. GH_PUSH / GH_PR target this connection."
    }

    /**
     * Switch connected branch: re-download that branch into a new local dir and update prefs.
     * Marker for UI rebind included.
     */
    suspend fun switchBranchLocally(
        context: Context,
        token: String,
        newBranch: String,
        repoOverride: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
        val prefs = PreferencesManager(context)
        val repo = repoOverride.ifBlank { prefs.getConnectedRepo() }
        if (repo.isBlank()) return@withContext "ERROR: No repo connected. Use GH_OPEN_REPO or GH_LIST_REPOS first."
        val br = newBranch.trim()
        if (br.isBlank()) return@withContext "ERROR: branch name required."
        val branches = listBranches(token, repo)
        if (branches.isNotEmpty() && br !in branches) {
            return@withContext "ERROR: branch '$br' not found on $repo. Available: ${branches.take(20).joinToString(", ")}" +
                if (branches.size > 20) "…" else "" +
                "\nCreate it with GH_CREATE_BRANCH $br first, or ask the user which branch to use."
        }
        val dir = downloadRepo(context, token, repo, br)
            ?: return@withContext "ERROR: failed to download $repo@$br."
        prefs.saveConnectedRepo(repo, br)
        AuthManager.backupGithubToken(context)
        val shortName = repo.substringAfterLast('/')
        ProjectManager.setSessionTitle(dir, "$shortName ($br)")
        "OK: Switched to branch '$br' on $repo (fresh local copy).\n" +
            "[[GH_OPEN_PROJECT]]$dir|$shortName|$repo|$br[[/GH_OPEN_PROJECT]]\n" +
            "Note: previous local uncommitted edits on the old workspace are NOT copied — they remain in the old project folder if needed."
    }

    /** Create branch on remote; optionally switch local workspace to it. */
    suspend fun createBranchAndMaybeSwitch(
        context: Context,
        token: String,
        newBranch: String,
        fromBranch: String = "",
        switchLocal: Boolean = true,
        repoOverride: String = ""
    ): String = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
        val prefs = PreferencesManager(context)
        val repo = repoOverride.ifBlank { prefs.getConnectedRepo() }
        if (repo.isBlank()) return@withContext "ERROR: No repo connected. GH_OPEN_REPO or GH_CREATE_REPO first."
        val from = fromBranch.ifBlank { prefs.getConnectedBranch() }.ifBlank {
            getRepo(token, repo)?.defaultBranch ?: "main"
        }
        // Sanitize the branch name so it's ALWAYS a valid git ref: strip refs/heads/, collapse
        // whitespace to '-', drop illegal chars, trim separators. If the model gave no name (the
        // cause of GitHub's 422 "refs/heads/ is not a valid ref name"), auto-generate a safe one.
        val safeBranch = newBranch.trim().removePrefix("refs/heads/").trim()
            .replace(Regex("\\s+"), "-")
            .replace(Regex("[^A-Za-z0-9._/-]"), "-")
            .trim('-', '/', '.')
            .take(100)
            .ifBlank { "ahamai-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) }
        val created = createBranch(token, repo, safeBranch, from)
        if (created.startsWith("ERROR")) return@withContext created
        if (!switchLocal) {
            return@withContext "$created (remote only). Call GH_SWITCH_BRANCH $safeBranch to work on it locally."
        }
        val switched = switchBranchLocally(context, token, safeBranch, repo)
        "$created\n$switched"
    }

    // ---------------------------------------------------------------- workspace backup repo

    /** Name of the private repo used to back up large workspaces (one per user account). */
    const val BACKUP_REPO_NAME = "ahamai-workspaces"

    data class BackupRepo(val fullName: String, val branch: String)

    /** Ensures the user's private "ahamai-workspaces" backup repo exists; returns it or null.
     *  Reuses it if present, otherwise creates it (auto-initialised so it has a default branch). */
    suspend fun ensureBackupRepo(token: String): BackupRepo? = withContext(Dispatchers.IO) {
        try {
            val user = getUser(token) ?: return@withContext null
            val full = "${user.login}/$BACKUP_REPO_NAME"
            getRepo(token, full)?.let { return@withContext BackupRepo(it.fullName, it.defaultBranch) }
            val created = createRepo(token, BACKUP_REPO_NAME, private = true) ?: return@withContext null
            val branch = getRepo(token, created)?.defaultBranch ?: "main"
            BackupRepo(created, branch)
        } catch (_: Exception) { null }
    }

    /** Raw zipball bytes of a repo branch (used to restore all backed-up workspaces in one download). */
    suspend fun fetchRepoZip(token: String, repo: String, branch: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            client.newCall(req(token, "$API/repos/$repo/zipball/$branch").get().build()).execute().use { r ->
                if (!r.isSuccessful) null else r.body?.bytes()
            }
        } catch (_: Exception) { null }
    }

    // ---------------------------------------------------------------- git data (commit/push)

    private fun getJson(token: String, url: String): JSONObject? = try {
        client.newCall(req(token, url).get().build()).execute().use { r ->
            if (r.isSuccessful) JSONObject(r.body?.string() ?: "{}") else null
        }
    } catch (e: Exception) { null }

    private fun postJson(token: String, url: String, body: JSONObject, method: String = "POST"): JSONObject? = try {
        val rb = body.toString().toRequestBody(JSON)
        val b = req(token, url)
        when (method) { "PATCH" -> b.patch(rb); "PUT" -> b.put(rb); else -> b.post(rb) }
        client.newCall(b.build()).execute().use { r ->
            val s = r.body?.string() ?: ""
            if (r.isSuccessful) {
                // Many GitHub write endpoints (e.g. workflow dispatch) return 204 No Content.
                if (s.isBlank()) JSONObject() else JSONObject(s)
            } else JSONObject().put("_error", "HTTP ${r.code}: $s".take(300))
        }
    } catch (e: Exception) { JSONObject().put("_error", e.message ?: "error") }

    /**
     * Commits multiple files to [branch] in one commit using the Git Data API.
     * files: relative path -> raw bytes. Returns a result message.
     *
     * ULTRA-FAST PUSH: this used to upload every file's blob SEQUENTIALLY (one HTTP round-trip
     * each), so pushing a whole project (or big files like fonts/images) was painfully slow. Now:
     *   1. We fetch the branch's current tree ONCE and compute each local file's git blob SHA
     *      locally (sha1 of "blob <len>\0<bytes>"). Files whose content already matches what's in
     *      the repo are SKIPPED entirely — no upload, and they're left in place via base_tree.
     *   2. Only the CHANGED/new files have their blobs uploaded, and those uploads run in PARALLEL
     *      (up to 8 at once) instead of one after another.
     * On a re-push where little changed this turns dozens of round-trips into a handful, and the
     * first push of a large project uploads all blobs concurrently. Both are dramatically faster.
     */
    suspend fun commitFiles(token: String, repo: String, branch: String, files: Map<String, ByteArray>, message: String): String =
        withContext(Dispatchers.IO) {
            try {
                if (files.isEmpty()) return@withContext "ERROR: no files to commit"
                // 1. latest commit sha of branch — retry, because a just-created repo's branch
                //    ref may not be available immediately (race condition).
                var refObj = getJson(token, "$API/repos/$repo/git/ref/heads/$branch")
                var tries = 0
                while ((refObj == null || refObj.optJSONObject("object") == null) && tries < 6) {
                    delay(2500); tries++
                    refObj = getJson(token, "$API/repos/$repo/git/ref/heads/$branch")
                }
                if (refObj == null) return@withContext "ERROR: branch '$branch' not found (repo not ready)"
                val baseSha = refObj.optJSONObject("object")?.optString("sha")
                    ?: return@withContext "ERROR: could not read branch head"
                // 2. base tree
                val commitObj = getJson(token, "$API/repos/$repo/git/commits/$baseSha")
                val baseTree = commitObj?.optJSONObject("tree")?.optString("sha")
                    ?: return@withContext "ERROR: could not read base tree"

                // 2b. Fetch the branch's FULL tree once and map path -> blob sha. Lets us skip
                //     re-uploading files that are already identical in the repo. Best-effort: if
                //     the tree is truncated (giant repo) or unavailable we just upload everything.
                val remoteShas = HashMap<String, String>()
                runCatching {
                    val rt = getJson(token, "$API/repos/$repo/git/trees/$baseTree?recursive=1")
                    if (rt != null && !rt.optBoolean("truncated", false)) {
                        val arr = rt.optJSONArray("tree")
                        if (arr != null) for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            if (o.optString("type") == "blob")
                                remoteShas[o.optString("path")] = o.optString("sha")
                        }
                    }
                }

                // 3. Partition into unchanged (skip) vs changed (need a blob upload).
                val changed = ArrayList<Pair<String, ByteArray>>()
                var skipped = 0
                for ((path, bytes) in files) {
                    if (remoteShas[path] == gitBlobSha(bytes)) skipped++
                    else changed.add(path to bytes)
                }

                if (changed.isEmpty()) {
                    return@withContext "OK: $repo@$branch already up to date ($skipped file(s) unchanged) — nothing to push."
                }

                // 3b. Upload the changed blobs IN PARALLEL (bounded concurrency), each with retry.
                val treeArr = JSONArray()
                val failed = java.util.concurrent.atomic.AtomicReference<String?>(null)
                val gate = Semaphore(8)
                val entries = coroutineScope {
                    changed.map { (path, bytes) ->
                        async(Dispatchers.IO) {
                            gate.withPermit {
                                if (failed.get() != null) return@withPermit null
                                var blob = postJson(token, "$API/repos/$repo/git/blobs", JSONObject().apply {
                                    put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                    put("encoding", "base64")
                                })
                                var bt = 0
                                while ((blob == null || blob.optString("sha").isBlank() || blob.has("_error")) && bt < 3) {
                                    delay(1500); bt++
                                    blob = postJson(token, "$API/repos/$repo/git/blobs", JSONObject().apply {
                                        put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                                        put("encoding", "base64")
                                    })
                                }
                                val blobSha = blob?.optString("sha")?.takeIf { it.isNotBlank() }
                                if (blobSha == null) {
                                    failed.compareAndSet(null, "blob upload failed for $path (${blob?.optString("_error")})")
                                    return@withPermit null
                                }
                                JSONObject().apply {
                                    put("path", path); put("mode", "100644"); put("type", "blob"); put("sha", blobSha)
                                }
                            }
                        }
                    }.awaitAll()
                }
                failed.get()?.let { return@withContext "ERROR: $it" }
                entries.filterNotNull().forEach { treeArr.put(it) }
                // 4. create tree (only the changed entries; base_tree keeps everything else)
                val tree = postJson(token, "$API/repos/$repo/git/trees", JSONObject().apply {
                    put("base_tree", baseTree); put("tree", treeArr)
                })
                val treeSha = tree?.optString("sha")?.takeIf { it.isNotBlank() }
                    ?: return@withContext "ERROR: tree creation failed (${tree?.optString("_error")})"
                // 5. create commit
                val commit = postJson(token, "$API/repos/$repo/git/commits", JSONObject().apply {
                    put("message", message); put("tree", treeSha); put("parents", JSONArray().put(baseSha))
                })
                val commitSha = commit?.optString("sha")?.takeIf { it.isNotBlank() }
                    ?: return@withContext "ERROR: commit failed (${commit?.optString("_error")})"
                // 6. update ref
                val upd = postJson(token, "$API/repos/$repo/git/refs/heads/$branch",
                    JSONObject().put("sha", commitSha).put("force", false), "PATCH")
                if (upd?.has("_error") == true) return@withContext "ERROR: ref update failed (${upd.optString("_error")})"
                val skipNote = if (skipped > 0) " (skipped $skipped unchanged)" else ""
                "OK: Pushed ${changed.size} file(s)$skipNote to $repo@$branch (commit ${commitSha.take(7)})"
            } catch (e: Exception) { "ERROR: ${e.message}" }
        }

    /** Git blob object id for [bytes]: sha1 of the header "blob <len>\u0000" followed by content.
     *  Matches what GitHub stores, so we can detect files that are already identical in the repo. */
    private fun gitBlobSha(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(("blob " + bytes.size + "\u0000").toByteArray(Charsets.UTF_8))
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Creates a new branch from the head of [fromBranch]. */
    suspend fun createBranch(token: String, repo: String, newBranch: String, fromBranch: String): String =
        withContext(Dispatchers.IO) {
            // Final safety net: never send an empty/malformed ref to GitHub (→ 422 "not a valid ref name").
            val branch = newBranch.trim().removePrefix("refs/heads/").trim()
            if (branch.isBlank()) return@withContext "ERROR: branch name is required (was empty). Provide a name like 'feature-x'."
            try {
                val refObj = getJson(token, "$API/repos/$repo/git/ref/heads/$fromBranch")
                    ?: return@withContext "ERROR: source branch not found"
                val sha = refObj.optJSONObject("object")?.optString("sha") ?: return@withContext "ERROR: no head sha"
                val res = postJson(token, "$API/repos/$repo/git/refs",
                    JSONObject().put("ref", "refs/heads/$branch").put("sha", sha))
                if (res?.has("_error") == true) "ERROR: ${res.optString("_error")}" else "OK: branch '$branch' created"
            } catch (e: Exception) { "ERROR: ${e.message}" }
        }

    suspend fun createPullRequest(token: String, repo: String, title: String, head: String, base: String, body: String): String =
        withContext(Dispatchers.IO) {
            val res = postJson(token, "$API/repos/$repo/pulls", JSONObject().apply {
                put("title", title); put("head", head); put("base", base); put("body", body)
            })
            if (res?.has("_error") == true) "ERROR: ${res.optString("_error")}"
            else "OK: PR #${res?.optInt("number")} created: ${res?.optString("html_url")}"
        }

    suspend fun listIssues(token: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            client.newCall(req(token, "$API/repos/$repo/issues?state=open&per_page=20").get().build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext "ERROR: HTTP ${r.code}"
                val arr = JSONArray(r.body?.string() ?: "[]")
                if (arr.length() == 0) return@withContext "No open issues."
                val sb = StringBuilder("Open issues:\n")
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.has("pull_request")) continue
                    sb.append("#${o.optInt("number")}: ${o.optString("title")}\n")
                }
                sb.toString().trim()
            }
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    suspend fun createIssue(token: String, repo: String, title: String, body: String): String =
        withContext(Dispatchers.IO) {
            val res = postJson(token, "$API/repos/$repo/issues", JSONObject().put("title", title).put("body", body))
            if (res?.has("_error") == true) "ERROR: ${res.optString("_error")}"
            else "OK: issue #${res?.optInt("number")} created: ${res?.optString("html_url")}"
        }

    // ---------------------------------------------------------------- APK build via Actions

    private const val BUILD_WORKFLOW_PATH = ".github/workflows/ahamai-build.yml"

    private val BUILD_WORKFLOW_YAML = """
name: AhamAI Build
on:
  workflow_dispatch:
  push:
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
      - name: Install SDK packages
        run: sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" || true
      - name: Locate Gradle project root
        run: |
          # Uploaded/pushed source often has its real Gradle root (settings.gradle[.kts] /
          # gradlew) inside a subfolder rather than at the repo root - e.g. an unzipped
          # project landing at my-app/. Find the SHALLOWEST directory holding one of those
          # markers and build there instead of blindly assuming the repo root.
          root=""
          best_depth=999999
          while IFS= read -r -d '' f; do
            d=§(dirname "§f")
            slashes=§(echo "§d" | tr -cd '/' | wc -c)
            if [ "§slashes" -lt "§best_depth" ]; then
              best_depth=§slashes
              root="§d"
            fi
          done < <(find . \( -name .git -o -name node_modules -o -name .gradle -o -name build \) -prune -o -type f \( -name settings.gradle.kts -o -name settings.gradle -o -name gradlew \) -print0)
          root=§{root#./}
          if [ -z "§root" ]; then root="."; fi
          echo "Detected Gradle project root: §root"
          echo "BUILD_DIR=§root" >> "§GITHUB_ENV"
      - name: Compile check (fast fail)
        run: |
          cd "§BUILD_DIR"
          if [ ! -f ./gradlew ] || [ ! -f ./gradle/wrapper/gradle-wrapper.jar ]; then
            echo "Gradle wrapper missing - generating it"
            gradle wrapper --gradle-version 8.7 --no-daemon
          fi
          chmod +x ./gradlew
          # Lightweight Kotlin-only compile: catches type/syntax/unresolved-ref errors in
          # seconds (no R8/minify/packaging) so a broken build fails fast with clear logs
          # before the heavier assembleDebug step runs.
          ./gradlew compileDebugKotlin --no-daemon --stacktrace
      - name: Build debug APK
        run: |
          cd "§BUILD_DIR"
          if [ ! -f ./gradlew ] || [ ! -f ./gradle/wrapper/gradle-wrapper.jar ]; then
            echo "Gradle wrapper missing - generating it"
            gradle wrapper --gradle-version 8.7 --no-daemon
          fi
          chmod +x ./gradlew
          ./gradlew assembleDebug --no-daemon --stacktrace
      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: app-apk
          path: '**/build/outputs/apk/**/*.apk'
          if-no-files-found: error
""".trimIndent().replace('§', '$')

    /** Ensures the build workflow exists on the default branch (creates/updates via Contents API). */
    private suspend fun ensureBuildWorkflow(token: String, repo: String, defaultBranch: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Check existing to get sha (for update)
                val existing = getJson(token, "$API/repos/$repo/contents/$BUILD_WORKFLOW_PATH?ref=$defaultBranch")
                val existingContent = existing?.optString("content")?.replace("\n", "")
                val want = Base64.encodeToString(BUILD_WORKFLOW_YAML.toByteArray(), Base64.NO_WRAP)
                if (existingContent == want) return@withContext true // already up to date
                val body = JSONObject().apply {
                    put("message", "Add AhamAI build workflow")
                    put("content", want)
                    put("branch", defaultBranch)
                    existing?.optString("sha")?.takeIf { it.isNotBlank() }?.let { put("sha", it) }
                }
                val res = postJson(token, "$API/repos/$repo/contents/$BUILD_WORKFLOW_PATH", body, "PUT")
                res?.has("_error") != true
            } catch (e: Exception) { false }
        }

    // Newest run id that existed BEFORE the last dispatch, per repo. Until a run NEWER than
    // this appears, latestBuildState reports a synthetic "queued" state instead of returning
    // the previous run — otherwise the first status poll right after a dispatch sees the OLD
    // completed run, declares instant success, and downloads that run's STALE APK artifact.
    private val buildBaseline = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /**
     * Starts an APK build: ensures the workflow, then dispatches it on [buildRef] — the branch
     * the project was just pushed to, so the build always compiles the code that was pushed
     * (dispatching on the repo's default branch built stale code whenever the connected branch
     * differed). [defaultBranch] additionally receives the workflow file so GitHub registers it.
     * Returns a result message.
     */
    suspend fun startApkBuild(token: String, repo: String, buildRef: String, defaultBranch: String = buildRef): String =
        withContext(Dispatchers.IO) {
            try {
                if (!ensureBuildWorkflow(token, repo, defaultBranch))
                    return@withContext "ERROR: couldn't add the build workflow (need 'workflow' scope on the token)."
                if (buildRef != defaultBranch && !ensureBuildWorkflow(token, repo, buildRef))
                    return@withContext "ERROR: couldn't add the build workflow to '$buildRef' (need 'workflow' scope on the token)."
                // Record the newest existing run BEFORE dispatching so status polls ignore it.
                val baseline = fetchLatestRun(token, repo)?.runId ?: 0L
                // Give GitHub a moment to register the new workflow
                delay(2500)
                val res = postJson(token, "$API/repos/$repo/actions/workflows/ahamai-build.yml/dispatches",
                    JSONObject().put("ref", buildRef))
                if (res?.has("_error") == true) "ERROR: dispatch failed (${res.optString("_error")})"
                else {
                    buildBaseline[repo] = baseline
                    "OK: Build started on $repo@$buildRef. Track status with build status."
                }
            } catch (e: Exception) { "ERROR: ${e.message}" }
        }

    /**
     * Returns the latest AhamAI build run state. If a build was just dispatched and its run
     * hasn't appeared on GitHub yet (takes a few seconds), returns a synthetic "queued" state
     * so callers keep polling instead of mistaking the previous run for the new build.
     */
    suspend fun latestBuildState(token: String, repo: String): BuildState? = withContext(Dispatchers.IO) {
        val baseline = buildBaseline[repo]
        val st = fetchLatestRun(token, repo)
        if (baseline != null) {
            if (st == null || st.runId <= baseline)
                return@withContext BuildState("queued", null, st?.htmlUrl ?: "", -1L)
            buildBaseline.remove(repo)
        }
        st
    }

    /** Raw "most recent AhamAI run" lookup, without the just-dispatched baseline filtering. */
    private suspend fun fetchLatestRun(token: String, repo: String): BuildState? = withContext(Dispatchers.IO) {
        try {
            val o = getJson(token, "$API/repos/$repo/actions/runs?per_page=5") ?: return@withContext null
            val runs = o.optJSONArray("workflow_runs") ?: return@withContext null
            for (i in 0 until runs.length()) {
                val r = runs.getJSONObject(i)
                if (r.optString("name").contains("AhamAI", true) || r.optString("path").contains("ahamai-build")) {
                    return@withContext BuildState(
                        r.optString("status"), r.optString("conclusion").takeIf { it != "null" && it.isNotBlank() },
                        r.optString("html_url"), r.optLong("id")
                    )
                }
            }
            // fall back to most recent run
            if (runs.length() > 0) {
                val r = runs.getJSONObject(0)
                BuildState(r.optString("status"), r.optString("conclusion").takeIf { it != "null" && it.isNotBlank() },
                    r.optString("html_url"), r.optLong("id"))
            } else null
        } catch (e: Exception) { null }
    }

    /** Returns the live steps of the latest build's (first) job, for showing progress. */
    suspend fun buildSteps(token: String, repo: String, runId: Long): List<BuildStepInfo> = withContext(Dispatchers.IO) {
        try {
            val jobsObj = getJson(token, "$API/repos/$repo/actions/runs/$runId/jobs") ?: return@withContext emptyList()
            val jobs = jobsObj.optJSONArray("jobs") ?: return@withContext emptyList()
            if (jobs.length() == 0) return@withContext emptyList()
            val steps = jobs.getJSONObject(0).optJSONArray("steps") ?: return@withContext emptyList()
            (0 until steps.length()).map {
                val s = steps.getJSONObject(it)
                BuildStepInfo(
                    s.optString("name"),
                    s.optString("status"),
                    s.optString("conclusion").takeIf { c -> c != "null" && c.isNotBlank() }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    data class BuildProgress(val steps: List<BuildStepInfo>, val jobId: Long)

    /** Live steps + the job id (so we can also tail its logs) in a single jobs call. */
    suspend fun fetchBuildProgress(token: String, repo: String, runId: Long): BuildProgress = withContext(Dispatchers.IO) {
        try {
            val jobsObj = getJson(token, "$API/repos/$repo/actions/runs/$runId/jobs") ?: return@withContext BuildProgress(emptyList(), -1)
            val jobs = jobsObj.optJSONArray("jobs") ?: return@withContext BuildProgress(emptyList(), -1)
            if (jobs.length() == 0) return@withContext BuildProgress(emptyList(), -1)
            val job = jobs.getJSONObject(0)
            val jobId = job.optLong("id")
            val stepsArr = job.optJSONArray("steps")
            val steps = if (stepsArr == null) emptyList() else (0 until stepsArr.length()).map {
                val s = stepsArr.getJSONObject(it)
                BuildStepInfo(
                    s.optString("name"),
                    s.optString("status"),
                    s.optString("conclusion").takeIf { c -> c != "null" && c.isNotBlank() }
                )
            }
            BuildProgress(steps, jobId)
        } catch (e: Exception) { BuildProgress(emptyList(), -1) }
    }

    /** Returns the last [maxLines] cleaned lines of a job's log, for a live log tail. Best-effort
     *  (GitHub may return nothing until logs exist for a running job). */
    suspend fun buildLogTail(token: String, repo: String, jobId: Long, maxLines: Int = 14): String = withContext(Dispatchers.IO) {
        if (jobId <= 0) return@withContext ""
        try {
            val text = client.newCall(req(token, "$API/repos/$repo/actions/jobs/$jobId/logs").get().build())
                .execute().use { if (it.isSuccessful) it.body?.string() ?: "" else "" }
            if (text.isBlank()) return@withContext ""
            val ts = Regex("^\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z\\s+")
            text.split('\n')
                .map { it.replace(ts, "").replace(Regex("^##\\[[a-z]+\\][^ ]*\\s?", RegexOption.IGNORE_CASE), "").trimEnd() }
                .filter { it.isNotBlank() }
                .takeLast(maxLines)
                .joinToString("\n") { it.take(160) }
        } catch (e: Exception) { "" }
    }

    /** Fetches the failure reason from the latest build's failed job logs (authenticated). */
    suspend fun buildLogs(token: String, repo: String): String = withContext(Dispatchers.IO) {
        try {
            val st = latestBuildState(token, repo) ?: return@withContext "No build runs found yet."
            val jobsObj = getJson(token, "$API/repos/$repo/actions/runs/${st.runId}/jobs")
                ?: return@withContext "Could not read jobs for run ${st.runId}."
            val jobs = jobsObj.optJSONArray("jobs") ?: return@withContext "No jobs found."
            // find a failed job, else the first job
            var jobId = -1L; var jobName = ""; var failedStep = ""
            for (i in 0 until jobs.length()) {
                val j = jobs.getJSONObject(i)
                if (j.optString("conclusion") == "failure") {
                    jobId = j.optLong("id"); jobName = j.optString("name")
                    val steps = j.optJSONArray("steps")
                    if (steps != null) for (k in 0 until steps.length()) {
                        val s = steps.getJSONObject(k)
                        if (s.optString("conclusion") == "failure") { failedStep = s.optString("name"); break }
                    }
                    break
                }
            }
            if (jobId < 0 && jobs.length() > 0) jobId = jobs.getJSONObject(0).optLong("id")
            if (jobId < 0) return@withContext "No job logs available."
            // job logs endpoint returns plain text (redirect followed automatically)
            val logText = client.newCall(req(token, "$API/repos/$repo/actions/jobs/$jobId/logs").get().build())
                .execute().use { if (it.isSuccessful) it.body?.string() ?: "" else "" }
            // Pull out the ACTUAL compiler/build errors instead of the generic Gradle/JVM
            // stack-trace tail (those real errors sit in the MIDDLE of the log, so a blind
            // takeLast() always missed them and only showed "Compilation error. See log...").
            val extracted = extractBuildErrors(logText)
            val detail = when {
                extracted.isNotBlank() -> extracted
                logText.length > 3500 -> "--- log tail ---\n" + logText.takeLast(3500)
                logText.isNotBlank() -> logText
                else -> "(logs empty or not yet available)"
            }
            "Build conclusion: ${st.conclusion}\nFailed step: ${failedStep.ifBlank { "(unknown)" }} (job: $jobName)\n--- errors ---\n$detail"
        } catch (e: Exception) { "ERROR reading logs: ${e.message}" }
    }

    /**
     * Pulls the lines that actually matter (compiler errors, the Gradle "what went wrong"
     * block, AAPT/resource errors) out of a large, timestamped CI log, so the agent and
     * user see the REAL error instead of a generic Gradle/JVM stack-trace tail.
     *
     * Handles GitHub Actions log formatting: strips the per-line ISO timestamp prefix and
     * the ##[error]/##[warning] workflow-command markers, and skips JVM stack frames.
     */
    fun extractBuildErrors(rawLog: String): String {
        if (rawLog.isBlank()) return ""
        val tsRegex = Regex("^\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z\\s+")
        val markRegex = Regex("^##\\[(error|warning)\\]")
        // Lines we most want: Kotlin "e:", javac/AAPT "error:", and key diagnostics.
        val primary = Regex(
            "(?i)(^\\s*e: |error: |unresolved reference|type mismatch|cannot find symbol|" +
            "cannot access|none of the following candidates|^\\s*> task .*failed|aapt|: error:)"
        )
        val compileErrors = LinkedHashSet<String>()
        val whatWentWrong = StringBuilder()
        var capturing = false

        for (r in rawLog.split('\n')) {
            val line = r.replace(tsRegex, "").replace(markRegex, "").trimEnd()
            if (line.isBlank()) continue
            // Ignore JVM stack-trace frames ("\tat ...", "... N more") — pure noise.
            val t = line.trimStart()
            if (t.startsWith("at ") || (t.startsWith("... ") && t.endsWith(" more"))) continue

            if (primary.containsMatchIn(line)) compileErrors.add(line.trim().take(300))

            when {
                line.contains("* What went wrong") -> { capturing = true; whatWentWrong.append(line.trim()).append('\n') }
                capturing && (line.startsWith("* Try") || line.startsWith("* Get more help") ||
                        line.startsWith("BUILD FAILED") || line.startsWith("* Exception")) -> capturing = false
                capturing && whatWentWrong.length < 700 -> whatWentWrong.append(line.trim()).append('\n')
            }
        }

        val sb = StringBuilder()
        if (compileErrors.isNotEmpty()) {
            sb.append("Compiler / build errors:\n")
            compileErrors.take(30).forEach { sb.append("  - ").append(it).append('\n') }
        }
        if (whatWentWrong.isNotBlank()) sb.append("\n").append(whatWentWrong.toString().trim()).append('\n')
        return sb.toString().trim()
    }
    suspend fun downloadBuildArtifact(context: Context, token: String, repo: String, runId: Long): String =
        withContext(Dispatchers.IO) {
            try {
                val o = getJson(token, "$API/repos/$repo/actions/runs/$runId/artifacts") ?: return@withContext "ERROR: no artifacts info"
                val arts = o.optJSONArray("artifacts") ?: return@withContext "ERROR: no artifacts"
                if (arts.length() == 0) return@withContext "ERROR: build produced no artifacts"
                val art = arts.getJSONObject(0)
                if (art.optBoolean("expired")) return@withContext "ERROR: artifact expired"
                val dlUrl = art.optString("archive_download_url")
                // artifact is a ZIP containing the apk
                client.newCall(req(token, dlUrl).get().build()).execute().use { r ->
                    if (!r.isSuccessful) return@withContext "ERROR: HTTP ${r.code} downloading artifact"
                    val zipBytes = r.body?.bytes() ?: return@withContext "ERROR: empty artifact"
                    // extract first .apk
                    ZipInputStream(zipBytes.inputStream().buffered()).use { zis ->
                        var entry = zis.nextEntry
                        while (entry != null) {
                            if (entry.name.endsWith(".apk", true)) {
                                val apkBytes = zis.readBytes()
                                val name = "AhamAI_build_${System.currentTimeMillis()}.apk"
                                return@withContext DeviceStorage.saveBytesToDownloads(
                                    context, apkBytes, name, "application/vnd.android.package-archive"
                                )
                            }
                            zis.closeEntry(); entry = zis.nextEntry
                        }
                    }
                    "ERROR: no .apk found inside artifact"
                }
            } catch (e: Exception) { "ERROR: ${e.message}" }
        }
}
