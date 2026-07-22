package com.ahamai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Run multi-language projects in the cloud sandbox and produce **Artifacts**
 * (run logs + live URL + screenshots) as proof — Antigravity-style verify.
 *
 * Supports: static HTML, Node/React/Vite/Next, Python (script + Flask/FastAPI),
 * PHP built-in server, Java (main / Maven / Gradle), Go, Rust, generic shell.
 */
object AppRunner {

    data class Stack(
        val id: String,
        val label: String,
        val isWeb: Boolean,
        val port: Int = 0,
        /** Shell to prepare (install deps). */
        val prepare: String = "",
        /** Start server in background (web) or run once (cli). */
        val run: String,
        val entryHint: String = ""
    )

    /**
     * Auto-detect stack from project files.
     */
    fun detect(projectDir: String): Stack {
        val root = File(projectDir)
        fun has(name: String) = File(root, name).exists()
        fun any(pred: (File) -> Boolean) = root.walkTopDown().maxDepth(3).any(pred)
        fun read(name: String) = runCatching { File(root, name).readText() }.getOrDefault("")

        // Node / React / Vite / Next
        if (has("package.json")) {
            val pkg = read("package.json")
            val scripts = pkg
            val port = when {
                pkg.contains("\"next\"") -> 3000
                pkg.contains("vite") -> 5173
                pkg.contains("react-scripts") || pkg.contains("\"react\"") -> 3000
                else -> 3000
            }
            val startCmd = when {
                scripts.contains("\"dev\"") -> "npm run dev -- --host 0.0.0.0 --port $port"
                scripts.contains("\"start\"") -> "HOST=0.0.0.0 PORT=$port npm start"
                scripts.contains("\"serve\"") -> "npm run serve"
                else -> "npx --yes serve -l $port ."
            }
            val label = when {
                pkg.contains("\"next\"") -> "Next.js"
                pkg.contains("vite") && pkg.contains("react") -> "React (Vite)"
                pkg.contains("\"react\"") -> "React"
                pkg.contains("vue") -> "Vue"
                else -> "Node.js"
            }
            return Stack(
                id = "node",
                label = label,
                isWeb = true,
                port = port,
                prepare = "if [ -f package-lock.json ]; then npm ci --no-audit --no-fund; else npm install --no-audit --no-fund; fi",
                run = startCmd,
                entryHint = "package.json"
            )
        }

        // Python web or script
        if (has("requirements.txt") || has("pyproject.toml") || has("main.py") || has("app.py") ||
            any { it.extension == "py" && it.name != "setup.py" }
        ) {
            val req = if (has("requirements.txt")) "pip install -q --break-system-packages -r requirements.txt 2>/dev/null || pip install -q -r requirements.txt" else "true"
            val pyWeb = when {
                has("app.py") && read("app.py").contains("Flask", true) ->
                    Stack("python-flask", "Python Flask", true, 5000, req,
                        "pip install -q --break-system-packages flask 2>/dev/null; FLASK_APP=app.py python3 -m flask run --host=0.0.0.0 --port=5000",
                        "app.py")
                has("main.py") && (read("main.py").contains("FastAPI") || read("main.py").contains("uvicorn")) ->
                    Stack("python-fastapi", "Python FastAPI", true, 8000, req,
                        "pip install -q --break-system-packages fastapi uvicorn 2>/dev/null; python3 -m uvicorn main:app --host 0.0.0.0 --port 8000",
                        "main.py")
                has("manage.py") ->
                    Stack("python-django", "Python Django", true, 8000, req,
                        "pip install -q --break-system-packages django 2>/dev/null; python3 manage.py runserver 0.0.0.0:8000",
                        "manage.py")
                else -> null
            }
            if (pyWeb != null) return pyWeb
            val entry = listOf("main.py", "app.py", "run.py", "script.py")
                .firstOrNull { has(it) }
                ?: root.listFiles()?.firstOrNull { it.extension == "py" }?.name
                ?: "main.py"
            return Stack(
                id = "python",
                label = "Python",
                isWeb = false,
                prepare = req,
                run = "python3 $entry",
                entryHint = entry
            )
        }

        // PHP
        if (has("composer.json") || has("index.php") || any { it.extension == "php" }) {
            val doc = when {
                has("public/index.php") -> "public"
                has("index.php") -> "."
                else -> "."
            }
            return Stack(
                id = "php",
                label = "PHP",
                isWeb = true,
                port = 8080,
                prepare = if (has("composer.json")) "composer install --no-interaction 2>/dev/null || true" else "true",
                run = "php -S 0.0.0.0:8080 -t $doc",
                entryHint = "$doc/index.php"
            )
        }

        // Java / Kotlin JVM
        if (has("pom.xml")) {
            return Stack(
                id = "maven",
                label = "Java (Maven)",
                isWeb = false,
                prepare = "true",
                run = "mvn -q -DskipTests package && ls target/*.jar | head -1 | xargs -I{} java -jar {}",
                entryHint = "pom.xml"
            )
        }
        if (has("build.gradle") || has("build.gradle.kts")) {
            // Android projects build via GH_BUILD_APK; plain JVM may use run
            val isAndroid = has("app/build.gradle") || has("app/build.gradle.kts") ||
                File(root, "settings.gradle").exists() || File(root, "settings.gradle.kts").exists()
            if (isAndroid) {
                return Stack(
                    id = "android",
                    label = "Android",
                    isWeb = false,
                    prepare = "true",
                    run = "echo 'Android project — use GH_BUILD_APK for APK artifact (local gradle may be heavy).' && ./gradlew tasks --all 2>/dev/null | head -40 || true",
                    entryHint = "gradle"
                )
            }
            return Stack(
                id = "gradle",
                label = "Java/Kotlin (Gradle)",
                isWeb = false,
                prepare = "chmod +x ./gradlew 2>/dev/null || true",
                run = "./gradlew run 2>/dev/null || ./gradlew build",
                entryHint = "build.gradle"
            )
        }
        if (any { it.extension == "java" }) {
            val main = root.walkTopDown().maxDepth(4)
                .firstOrNull { it.extension == "java" && it.readText().contains("public static void main") }
            val rel = main?.relativeTo(root)?.path ?: "Main.java"
            return Stack(
                id = "java",
                label = "Java",
                isWeb = false,
                run = "javac $(find . -name '*.java') 2>&1 && java ${rel.removeSuffix(".java").replace('/', '.')}",
                entryHint = rel
            )
        }

        // Go
        if (has("go.mod") || any { it.extension == "go" }) {
            return Stack(
                id = "go",
                label = "Go",
                isWeb = false,
                prepare = "go mod tidy 2>/dev/null || true",
                run = "go run .",
                entryHint = "go"
            )
        }

        // Rust
        if (has("Cargo.toml")) {
            return Stack(
                id = "rust",
                label = "Rust",
                isWeb = false,
                run = "cargo run",
                entryHint = "Cargo.toml"
            )
        }

        // Static HTML
        if (has("index.html") || any { it.extension == "html" }) {
            return Stack(
                id = "static",
                label = "Static HTML",
                isWeb = true,
                port = 8765,
                run = "python3 -m http.server 8765 --bind 0.0.0.0",
                entryHint = "index.html"
            )
        }

        return Stack(
            id = "unknown",
            label = "Generic",
            isWeb = false,
            run = "ls -la && echo 'No auto-run recipe — specify a command via RUN_APP with custom shell.'",
            entryHint = ""
        )
    }

    /**
     * Run the project (or [customCmd]), capture stdout log + optional live screenshot.
     * Returns agent-readable proof with [[ARTIFACT]] / [[FILE]] markers.
     */
    suspend fun runAndCapture(
        context: Context,
        projectDir: String,
        customCmd: String = "",
        skipScreenshot: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(context)
        if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
            // Offline fallback: static HTML preview artifact only
            val stack = detect(projectDir)
            if (stack.id == "static" || File(projectDir, "index.html").exists()) {
                ArtifactStore.register(
                    projectDir, ArtifactStore.Kind.PREVIEW,
                    title = "Static preview (offline)",
                    path = "index.html",
                    language = "html",
                    detail = "Cloud engine off — use PREVIEW_WEB_APP for in-app view."
                )
                return@withContext "Cloud engine not configured. Detected ${stack.label}.\n" +
                    "Call PREVIEW_WEB_APP index.html for on-device preview.\n" +
                    "Enable Profile → Cloud Engine for full RUN_APP (Python/PHP/React/Java) + screenshots."
            }
            return@withContext "ERROR: Cloud engine not configured. Enable E2B in Profile → Cloud Engine to run ${detect(projectDir).label} and capture screenshots."
        }

        val stack = detect(projectDir)
        val apiKey = prefs.getE2bApiKey()
        val template = prefs.getE2bTemplate()
        FastSync.syncProjectAtOnce(projectDir, apiKey, template, File(projectDir))

        val sb = StringBuilder()
        sb.appendLine("RUN_APP · stack=${stack.label} (${stack.id})")
        if (stack.entryHint.isNotBlank()) sb.appendLine("entry: ${stack.entryHint}")

        // Prepare deps
        if (stack.prepare.isNotBlank() && customCmd.isBlank()) {
            val prep = E2BClient.exec(projectDir, apiKey, template, stack.prepare, "/workspace", 300)
            if (!prep.isSuccess && prep.exitCode != 0) {
                sb.appendLine("prepare: exit ${prep.exitCode}")
                sb.appendLine(prep.formatted(1500))
            } else {
                sb.appendLine("prepare: ok")
            }
        }

        val runCmd = customCmd.trim().ifBlank { stack.run }
        if (stack.isWeb && customCmd.isBlank()) {
            // Background server + health check + public URL + screenshot
            val port = stack.port.takeIf { it > 0 } ?: 8080
            val boot = """
                pkill -f 'port $port' 2>/dev/null || true
                pkill -f ':$port' 2>/dev/null || true
                cd /workspace
                nohup bash -lc ${shellQuote(runCmd)} > /tmp/ahamai-run.log 2>&1 &
                echo ${'$'}! > /tmp/ahamai-run.pid
                for i in ${'$'}(seq 1 40); do
                  if curl -sf -o /dev/null --max-time 1 http://127.0.0.1:$port/ || \
                     curl -sf -o /dev/null --max-time 1 http://127.0.0.1:$port/index.html; then
                    echo __READY__
                    exit 0
                  fi
                  sleep 0.5
                done
                echo __NOT_READY__
                tail -40 /tmp/ahamai-run.log 2>/dev/null || true
                exit 1
            """.trimIndent()

            val res = E2BClient.exec(projectDir, apiKey, template, boot, "/workspace", 120)
            val ready = res.stdout.contains("__READY__")
            val logTail = res.formatted(2500)
            sb.appendLine("server: ${if (ready) "ready" else "not ready"} (port $port)")
            sb.appendLine(logTail.take(1200))

            val logArt = ArtifactStore.registerText(
                projectDir,
                ArtifactStore.Kind.RUN_LOG,
                title = "${stack.label} run log",
                body = "cmd: $runCmd\n\n$logTail",
                language = stack.id,
                success = ready
            )
            sb.appendLine(ArtifactStore.marker(logArt))

            if (ready) {
                // Ensure sandbox is warm then public URL
                E2BClient.exec(projectDir, apiKey, template, "true", "/workspace", 10)
                val publicUrl = E2BClient.sandboxPortUrl(port)
                    ?: E2BClient.activeSandboxId()?.let { E2BClient.publicHost(it, port) }

                if (!publicUrl.isNullOrBlank()) {
                    sb.appendLine("live: $publicUrl")
                    ArtifactStore.register(
                        projectDir, ArtifactStore.Kind.PREVIEW,
                        title = "${stack.label} live server",
                        url = publicUrl,
                        language = stack.id,
                        detail = "Running on port $port"
                    )
                    if (!skipScreenshot) {
                        val shotRel = ".ahamai/artifacts/shot_${stack.id}_${System.currentTimeMillis()}.png"
                        val shot = WebScreenshot.captureToProject(projectDir, publicUrl, shotRel)
                        sb.appendLine(shot)
                        if (shot.startsWith("OK")) {
                            val art = ArtifactStore.register(
                                projectDir, ArtifactStore.Kind.SCREENSHOT,
                                title = "${stack.label} screenshot",
                                path = shotRel,
                                url = publicUrl,
                                language = stack.id,
                                success = true,
                                detail = "Proof the app is running"
                            )
                            sb.appendLine(ArtifactStore.marker(art))
                            sb.appendLine("[[FILE]]$shotRel[[/FILE]]")
                            // Walkthrough note
                            val walk = ArtifactStore.registerText(
                                projectDir,
                                ArtifactStore.Kind.WALKTHROUGH,
                                title = "Walkthrough · ${stack.label}",
                                body = "Started ${stack.label} on $publicUrl\nScreenshot: $shotRel\nCommand: $runCmd",
                                language = stack.id,
                                url = publicUrl
                            )
                            sb.appendLine(ArtifactStore.marker(walk))
                        }
                    }
                } else {
                    sb.appendLine("live: (could not resolve public sandbox URL — log artifact still saved)")
                }
            } else {
                // Save failure as artifact
                ArtifactStore.registerText(
                    projectDir, ArtifactStore.Kind.RUN_LOG,
                    title = "${stack.label} failed to start",
                    body = logTail,
                    language = stack.id,
                    success = false
                )
            }
        } else {
            // One-shot CLI run
            val res = E2BClient.exec(projectDir, apiKey, template, runCmd, "/workspace", 180)
            val out = res.formatted(6000)
            sb.appendLine("exit: ${res.exitCode}")
            sb.appendLine(out.take(3000))
            val art = ArtifactStore.registerText(
                projectDir,
                ArtifactStore.Kind.RUN_LOG,
                title = "${stack.label} output",
                body = "cmd: $runCmd\nexit: ${res.exitCode}\n\n$out",
                language = stack.id,
                success = res.isSuccess || res.exitCode == 0
            )
            sb.appendLine(ArtifactStore.marker(art))
            // If output looks like HTML path, note preview
            if (File(projectDir, "index.html").exists()) {
                ArtifactStore.register(
                    projectDir, ArtifactStore.Kind.PREVIEW,
                    title = "index.html available",
                    path = "index.html",
                    language = "html"
                )
                sb.appendLine("Tip: PREVIEW_WEB_APP index.html for on-device preview.")
            }
        }

        sb.appendLine()
        sb.append(ArtifactStore.formatList(projectDir))
        sb.toString()
    }

    private fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"
}
