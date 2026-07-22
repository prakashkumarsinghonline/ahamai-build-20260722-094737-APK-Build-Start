package com.ahamai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LSP-style diagnostics for the code agent.
 *
 * Maps file extensions to compiler/type-checker commands that run in the cloud sandbox.
 * After each file write/edit, the agent can call [diagnose] to get real diagnostics
 * (errors, warnings) back in the agent loop — no need for a full language server.
 *
 * Commands are project-aware: they auto-detect the build system (package.json, Cargo.toml,
 * build.gradle.kts, go.mod, etc.) and use the right tool with the right args.
 */
object LspManager {

    data class DiagnosticResult(
        val filePath: String,
        val language: String,
        val ok: Boolean,
        val output: String,
        val errorCount: Int,
        val warningCount: Int
    ) {
        fun formatted(): String = buildString {
            append("[$language] $filePath → ")
            if (ok) {
                append("no issues")
            } else {
                append("$errorCount error(s), $warningCount warning(s)")
            }
            if (output.isNotBlank()) {
                append("\n").append(output.take(3000))
            }
        }
    }

    /**
     * Returns a set of file extensions this manager can diagnose.
     */
    fun supportedExtensions(): Set<String> = setOf(
        "kt", "kts", "ts", "tsx", "js", "jsx", "py",
        "rs", "go", "java", "swift", "rb", "php"
    )

    /**
     * Build a diagnostic command for the given file in the given project.
     * Returns null when no diagnostic is available (unknown language or missing tooling).
     *
     * @param projectDir absolute path to the project root
     * @param filePath   project-relative path to the file (e.g. "src/Main.kt")
     */
    fun diagnosticCommand(projectDir: String, filePath: String): String? {
        val ext = filePath.substringAfterLast('.').lowercase()
        val has = { name: String -> File(projectDir, name).exists() }
        return when (ext) {
            "kt", "kts" -> kotlinCommand(projectDir, has)
            "ts", "tsx" -> typescriptCommand(projectDir, has)
            "js", "jsx" -> javascriptCommand(projectDir, has)
            "py"        -> pythonCommand(projectDir, has, filePath)
            "rs"        -> rustCommand(projectDir, has)
            "go"        -> goCommand(projectDir, has)
            "java"      -> javaCommand(projectDir, has)
            "swift"     -> swiftCommand(projectDir, has)
            "rb"        -> rubyCommand(projectDir, has)
            "php"       -> phpCommand(projectDir, has)
            else        -> null
        }
    }

    /**
     * Run diagnostics for a file in the cloud sandbox.
     * Returns a [DiagnosticResult] — never throws.
     */
    suspend fun diagnose(
        context: Context,
        projectDir: String,
        filePath: String
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        val ext = filePath.substringAfterLast('.').lowercase()
        val lang = languageLabel(ext)
        val cmd = diagnosticCommand(projectDir, filePath)

        if (cmd == null) {
            return@withContext DiagnosticResult(filePath, lang, true, "", 0, 0)
        }

        val prefs = PreferencesManager(context)
        if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
            return@withContext DiagnosticResult(
                filePath, lang, true,
                "(cloud engine not configured — skipping diagnostics)", 0, 0
            )
        }

        try {
            val apiKey = prefs.getE2bApiKey()
            val template = prefs.getE2bTemplate()
            FastSync.syncProjectAtOnce(projectDir, apiKey, template, File(projectDir))
            val res = E2BClient.exec(projectDir, apiKey, template, cmd, "/workspace", 120)
            parseOutput(res.formatted(6000))
        } catch (e: Exception) {
            DiagnosticResult(filePath, lang, true, "(diagnostic error: ${e.message?.take(200)})", 0, 0)
        }
    }

    // ── Language-specific diagnostic command builders ──

    private fun kotlinCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("build.gradle.kts") || has("build.gradle") ->
            "./gradlew compileDebugKotlin 2>&1 | tail -80"
        has("settings.gradle.kts") || has("settings.gradle") ->
            "./gradlew compileDebugKotlin 2>&1 | tail -80"
        else -> null
    }

    private fun typescriptCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("tsconfig.json") ->
            "npx tsc --noEmit 2>&1 | head -100"
        has("package.json") && File(projectDir, "package.json").readText().contains("typescript") ->
            "npx tsc --noEmit 2>&1 | head -100"
        else -> null
    }

    private fun javascriptCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("package.json") -> {
            val pkg = File(projectDir, "package.json").readText()
            when {
                "eslint" in pkg -> "npx eslint . --max-warnings=0 2>&1 | head -60"
                else -> "node --check index.js 2>&1; node --check src/index.js 2>&1; echo '(no dedicated linter found)'"
            }
        }
        else -> null
    }

    private fun pythonCommand(projectDir: String, has: (String) -> Boolean, filePath: String): String? = when {
        has("pyproject.toml") -> {
            // Prefer pyright or ruff when the project has them configured
            val cfg = File(projectDir, "pyproject.toml").readText()
            when {
                "[tool.pyright]" in cfg -> "pip install -q pyright 2>/dev/null; pyright $filePath 2>&1 | head -60"
                "[tool.ruff]" in cfg -> "pip install -q ruff 2>/dev/null; ruff check $filePath 2>&1 | head -60"
                else -> "python3 -m py_compile $filePath 2>&1; echo '---'; pip install -q ruff 2>/dev/null; ruff check $filePath 2>&1 | head -60"
            }
        }
        has("setup.py") || has("requirements.txt") ->
            "pip install -q ruff 2>/dev/null; ruff check $filePath 2>&1 | head -60"
        else ->
            "python3 -m py_compile $filePath 2>&1 | head -40"
    }

    private fun rustCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("Cargo.toml") -> "cargo check 2>&1 | tail -80"
        else -> null
    }

    private fun goCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("go.mod") -> "go vet ./... 2>&1 | head -80"
        else -> null
    }

    private fun javaCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("build.gradle.kts") || has("build.gradle") || has("pom.xml") ->
            // Delegate to the project's build system — compile-only, no tests
            if (has("gradlew")) "./gradlew compileJava 2>&1 | tail -80"
            else if (has("mvnw")) "./mvnw compile -q 2>&1 | tail -80"
            else null
        else -> null
    }

    private fun swiftCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("Package.swift") -> "swift build 2>&1 | tail -60"
        else -> null
    }

    private fun rubyCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("Gemfile") -> "bundle exec ruby -c app.rb 2>&1; bundle exec ruby -c lib/**/*.rb 2>&1 | head -40"
        else -> "ruby -c *.rb 2>&1 | head -40"
    }

    private fun phpCommand(projectDir: String, has: (String) -> Boolean): String? = when {
        has("composer.json") -> "composer validate 2>&1; php -l app.php 2>&1 | head -40"
        else -> "php -l *.php 2>&1 | head -40"
    }

    // ── Helpers ──

    private fun languageLabel(ext: String): String = when (ext) {
        "kt", "kts" -> "Kotlin"
        "ts", "tsx" -> "TypeScript"
        "js", "jsx" -> "JavaScript"
        "py" -> "Python"
        "rs" -> "Rust"
        "go" -> "Go"
        "java" -> "Java"
        "swift" -> "Swift"
        "rb" -> "Ruby"
        "php" -> "PHP"
        else -> ext.uppercase()
    }

    private fun parseOutput(raw: String): DiagnosticResult {
        val lower = raw.lowercase()
        val errorCount = Regex("(\\d+) error(s)?", RegexOption.IGNORE_CASE).findAll(lower)
            .sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        val warningCount = Regex("(\\d+) warning(s)?", RegexOption.IGNORE_CASE).findAll(lower)
            .sumOf { it.groupValues[1].toIntOrNull() ?: 0 }
        // Heuristic: if exit code-like markers suggest failure, count errors even if pattern missed them
        val hasFailure = lower.contains("error") || lower.contains("fail") ||
            lower.contains("cannot find") || lower.contains("not found") ||
            lower.contains("does not exist") || lower.contains("unresolved reference") ||
            lower.contains("type mismatch") || lower.contains("is not a function") ||
            lower.contains("cannot resolve") || lower.contains("undefined") ||
            lower.contains("syntaxerror") || lower.contains("compilation failed")
        val ok = !hasFailure || (errorCount == 0 && warningCount == 0 && !hasFailure)
        return DiagnosticResult("", "", ok, raw.trim(), errorCount, warningCount)
    }
}
