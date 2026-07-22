package com.ahamai.app.data

import java.io.File

/**
 * Instant, **offline** static verification for the coding agent.
 *
 * No Android SDK, no Gradle, no cloud, no network — pure source scan that returns in
 * milliseconds. Catches the high-frequency failures that waste cloud build minutes:
 *
 *  - Unbalanced `{}` `()` `[]` (string/comment-aware) + **safe instant auto-fix**
 *  - Unclosed quotes / broken string state at EOF
 *  - Import checks: duplicates, unused, missing Compose / common Android imports
 *  - Kotlin package vs directory mismatch
 *  - Manifest activity/service classes that don't exist
 *  - Compose without `buildFeatures { compose = true }`
 *  - Missing Gradle wrapper pieces for APK builds
 *  - Basic XML / JSON well-formedness
 *  - Common Kotlin syntax red flags (orphan `else`, empty when, etc.)
 *
 * Use via tools: `VERIFY` / `VERIFY_BUILD` / auto after every edit (appendDiagnostics).
 */
object StaticVerifier {

    data class Issue(
        val severity: Severity,
        val path: String,
        val line: Int,          // 1-based; 0 = file-level
        val message: String
    ) {
        enum class Severity { ERROR, WARNING }
        fun format(): String = buildString {
            append(if (severity == Severity.ERROR) "ERROR" else "WARN")
            if (path.isNotBlank()) append(" $path")
            if (line > 0) append(":$line")
            append(" — ").append(message)
        }
    }

    data class Report(
        val issues: List<Issue>,
        val filesScanned: Int,
        val mode: String
    ) {
        val errors: List<Issue> get() = issues.filter { it.severity == Issue.Severity.ERROR }
        val warnings: List<Issue> get() = issues.filter { it.severity == Issue.Severity.WARNING }
        val ok: Boolean get() = errors.isEmpty()

        fun format(maxLines: Int = 40): String = buildString {
            append("STATIC VERIFY [$mode]: ")
            if (ok) {
                append("PASSED ✓  ($filesScanned file(s)")
                if (warnings.isNotEmpty()) append(", ${warnings.size} warning(s)")
                append("). No blocking issues — safe to proceed")
                if (mode.contains("android", true) || mode.contains("apk", true))
                    append(" / start cloud APK build")
                append(".\n")
            } else {
                append("FAILED ✗  ${errors.size} error(s)")
                if (warnings.isNotEmpty()) append(", ${warnings.size} warning(s)")
                append(" in $filesScanned file(s). FIX before DONE / GH_BUILD_APK:\n")
            }
            // Errors first — warnings (unused imports, etc.) must not bury real blockers
            val ordered = errors + warnings
            ordered.take(maxLines).forEachIndexed { i, issue ->
                append("  ${i + 1}. ${issue.format()}\n")
            }
            if (ordered.size > maxLines) append("  … +${ordered.size - maxLines} more\n")
            if (!ok) {
                append("\nNext: fix ERROR lines only (unused-import warnings do not block cloud builds), ")
                append("re-run VERIFY_BUILD, then GH_BUILD_APK.\n")
            }
        }.trim()
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Verify one file instantly. [relPath] is project-relative.
     */
    fun verifyFile(projectDir: String, relPath: String): Report {
        val f = ProjectManager.resolveFile(projectDir, relPath)
            ?: return Report(
                listOf(Issue(Issue.Severity.ERROR, relPath, 0, "File not found (or outside sandbox)")),
                0, "file"
            )
        val rel = runCatching {
            f.relativeTo(File(projectDir)).path
        }.getOrDefault(relPath)
        val text = runCatching { f.readText() }.getOrElse {
            return Report(listOf(Issue(Issue.Severity.ERROR, rel, 0, "Cannot read: ${it.message}")), 0, "file")
        }
        val issues = mutableListOf<Issue>()
        scanFile(rel, text, f.extension.lowercase(), issues)
        return Report(issues, 1, "file")
    }

    /**
     * Verify a path scope (file or folder) or whole project when [pathScope] blank.
     * @param mode "quick" | "full" | "android" | "apk"
     */
    fun verify(
        projectDir: String,
        pathScope: String = "",
        mode: String = "quick"
    ): Report {
        val m = mode.trim().lowercase().ifBlank { "quick" }
        val root = File(projectDir)
        if (!root.isDirectory) {
            return Report(listOf(Issue(Issue.Severity.ERROR, "", 0, "Project dir missing")), 0, m)
        }

        val issues = mutableListOf<Issue>()
        var scanned = 0

        // Android/APK structural checks first when requested
        if (m == "android" || m == "apk" || m == "full") {
            val buildRoot = ProjectManager.detectBuildRoot(projectDir)
            val apk = ApkPreflight.check(projectDir, buildRoot)
            // Convert preflight into issues (avoid duplicate delimiter noise by scanning files ourselves too)
            apk.blocking.forEach {
                issues.add(Issue(Issue.Severity.ERROR, "", 0, it))
            }
            apk.warnings.forEach {
                issues.add(Issue(Issue.Severity.WARNING, "", 0, it))
            }
        }

        val files = collectSourceFiles(projectDir, pathScope, m)
        val androidMode = m == "android" || m == "apk"
        for ((rel, file) in files) {
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            if (text.length > 1_500_000) continue
            scanned++
            scanFile(rel, text, file.extension.lowercase(), issues, importNoise = !androidMode)
        }

        // Android/APK: unused/duplicate-import noise is NOT a cloud-build blocker (Gradle
        // / Kotlin compiler may warn, but these flooded VERIFY with 200+ false FAIL signals).
        // Keep only structural ERRORs + a few high-signal warnings for the agent.
        if (androidMode) {
            val kept = issues.filter { issue ->
                when {
                    issue.severity == Issue.Severity.ERROR -> true
                    issue.message.startsWith("Unused import") -> false
                    issue.message.startsWith("Duplicate import") -> false
                    issue.message.contains("Uses `remember`") -> false
                    issue.message.contains("Uses `mutableStateOf`") -> false
                    issue.message.contains("Uses Compose `Modifier`") -> false
                    issue.message.contains("Function signature may be missing") -> false
                    issue.message.contains("Orphan `else`") -> false
                    else -> true // package/manifest/wrapper style warnings still useful
                }
            }
            issues.clear()
            issues.addAll(kept)
        }

        // Cross-file Android checks when full/android (manifest classes already in ApkPreflight)
        if (m == "full") {
            // Duplicate package+class names (simple)
            val classNames = HashMap<String, MutableList<String>>()
            for ((rel, file) in files.filter { it.second.extension == "kt" || it.second.extension == "java" }) {
                val text = runCatching { file.readText() }.getOrNull() ?: continue
                Regex("""\b(class|object|interface)\s+([A-Z][A-Za-z0-9_]*)""").findAll(text).forEach { match ->
                    val name = match.groupValues[2]
                    classNames.getOrPut(name) { mutableListOf() }.add(rel)
                }
            }
            classNames.filter { it.value.size > 1 && it.key.length > 2 }.entries.take(5).forEach { (name, paths) ->
                issues.add(
                    Issue(
                        Issue.Severity.WARNING, paths.first(), 0,
                        "Type `$name` declared in multiple files: ${paths.take(4).joinToString()} — may be OK (nested) but check collisions."
                    )
                )
            }
        }

        // Deduplicate similar messages
        val deduped = issues.distinctBy { "${it.severity}|${it.path}|${it.line}|${it.message.take(120)}" }
        return Report(deduped, scanned, m)
    }

    /** Short one-liner for embedding after edit success. */
    fun quickFileNote(projectDir: String, relPath: String): String? {
        if (relPath.isBlank()) return null
        val r = verifyFile(projectDir, relPath)
        if (r.ok && r.warnings.isEmpty()) return null
        if (r.ok) return "[static] ${r.warnings.size} warning(s) in $relPath"
        return "[static VERIFY FAILED] " + r.errors.take(3).joinToString("; ") { it.format() }
    }

    data class AutoFixResult(
        val applied: Boolean,
        val fixes: List<String>,
        val report: Report
    ) {
        fun format(): String = buildString {
            if (applied && fixes.isNotEmpty()) {
                append("[static AUTO-FIX] applied ${fixes.size} fix(es):\n")
                fixes.forEach { append("  • $it\n") }
                append("\n")
            }
            append(report.format(maxLines = 20))
        }.trim()
    }

    /**
     * Verify one file; if safe auto-fixes are available (missing closers, missing Compose
     * import, duplicate imports), apply them to disk and re-verify.
     *
     * Never invents logic — only mechanical closers / import lines.
     */
    fun verifyAndAutoFixFile(projectDir: String, relPath: String): AutoFixResult {
        val first = verifyFile(projectDir, relPath)
        val f = ProjectManager.resolveFile(projectDir, relPath)
            ?: return AutoFixResult(false, emptyList(), first)
        val ext = f.extension.lowercase()
        if (ext !in setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx")) {
            return AutoFixResult(false, emptyList(), first)
        }
        val original = runCatching { f.readText() }.getOrNull()
            ?: return AutoFixResult(false, emptyList(), first)

        val (fixed, fixes) = applySafeAutoFixes(original, ext, relPath)
        if (fixes.isEmpty() || fixed == original) {
            return AutoFixResult(false, emptyList(), first)
        }
        // Only write if we improved error count or fixed something concrete
        val probeIssues = mutableListOf<Issue>()
        scanFile(relPath, fixed, ext, probeIssues)
        val beforeErr = first.errors.size
        val afterErr = probeIssues.count { it.severity == Issue.Severity.ERROR }
        if (afterErr > beforeErr) {
            return AutoFixResult(false, emptyList(), first) // refuse worse fix
        }
        runCatching { f.writeText(fixed) }
            .onFailure {
                return AutoFixResult(
                    false, emptyList(),
                    Report(
                        listOf(Issue(Issue.Severity.ERROR, relPath, 0, "Auto-fix write failed: ${it.message}")),
                        1, "autofix"
                    )
                )
            }
        FastSync.invalidate(projectDir)
        val second = verifyFile(projectDir, relPath)
        return AutoFixResult(true, fixes, second)
    }

    /**
     * Safe mechanical fixes only:
     *  - append missing `}` `)` `]` when counts are positive and no mid-file surplus closers
     *  - add missing Compose / common Android imports
     *  - drop exact-duplicate import lines
     */
    fun applySafeAutoFixes(src: String, ext: String, rel: String = ""): Pair<String, List<String>> {
        var text = src
        val fixes = mutableListOf<String>()

        // 1) Duplicate imports (exact line)
        if (ext in setOf("kt", "kts", "java")) {
            val (t1, n) = removeDuplicateImports(text)
            if (n > 0) {
                text = t1
                fixes.add("Removed $n duplicate import line(s)")
            }
        }

        // 2) Missing high-signal imports (Compose / Android)
        if (ext in setOf("kt", "kts")) {
            val (t2, added) = ensureKotlinImports(text)
            if (added.isNotEmpty()) {
                text = t2
                added.forEach { fixes.add("Added import `$it`") }
            }
        }

        // 3) Brace / paren / bracket closers at EOF (only surplus opens, no mid-file extra closers)
        if (ext in setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx")) {
            val (t3, braceFix) = tryCloseDelimiters(text)
            if (braceFix != null) {
                text = t3
                fixes.add(braceFix)
            }
        }

        return text to fixes
    }

    // ── File collection ───────────────────────────────────────────────────────

    private fun collectSourceFiles(
        projectDir: String,
        pathScope: String,
        mode: String
    ): List<Pair<String, File>> {
        val exts = when (mode) {
            "android", "apk" -> setOf("kt", "kts", "java", "xml", "gradle")
            "quick" -> setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "json", "xml", "gradle")
            else -> setOf(
                "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "go", "rs",
                "json", "xml", "gradle", "md", "css", "html", "swift", "rb", "php"
            )
        }
        val scope = pathScope.trim()
        val out = mutableListOf<Pair<String, File>>()

        if (scope.isNotBlank()) {
            val f = ProjectManager.resolveFile(projectDir, scope)
                ?: File(projectDir, ProjectManager.cleanRelPath(scope)).takeIf {
                    ProjectManager.isInsideProject(projectDir, it)
                }
            if (f != null && f.isFile) {
                if (f.extension.lowercase() in exts || f.extension.isEmpty()) {
                    out.add(relOf(projectDir, f) to f)
                }
                return out
            }
            if (f != null && f.isDirectory) {
                walkCollect(projectDir, f, exts, out)
                return out
            }
        }

        // Session-touched preference: whole project text files via shared walker
        val list = ProjectManager.listTextFiles(projectDir)
        for (rel in list) {
            val ext = rel.substringAfterLast('.', "").lowercase()
            if (ext !in exts && ext.isNotEmpty()) continue
            val f = File(projectDir, rel)
            if (f.isFile) out.add(rel to f)
        }
        // Cap for speed on huge monorepos
        return if (out.size > 400) out.take(400) else out
    }

    private fun walkCollect(
        projectDir: String,
        dir: File,
        exts: Set<String>,
        out: MutableList<Pair<String, File>>
    ) {
        val entries = dir.listFiles() ?: return
        for (f in entries) {
            if (f.isDirectory) {
                val n = f.name
                if (n in setOf(".git", "node_modules", "build", ".gradle", "dist", ".cxx", "intermediates")) continue
                if (n.startsWith(".") && n !in setOf(".github")) continue
                walkCollect(projectDir, f, exts, out)
            } else {
                val ext = f.extension.lowercase()
                if (ext in exts || (ext.isEmpty() && f.name in setOf("gradlew", "Dockerfile"))) {
                    if (ProjectManager.isInsideProject(projectDir, f)) {
                        out.add(relOf(projectDir, f) to f)
                    }
                }
            }
            if (out.size >= 400) return
        }
    }

    private fun relOf(projectDir: String, f: File): String =
        runCatching { f.relativeTo(File(projectDir)).path }.getOrDefault(f.name)

    // ── Per-file scanners ─────────────────────────────────────────────────────

    private fun scanFile(
        rel: String,
        text: String,
        ext: String,
        issues: MutableList<Issue>,
        importNoise: Boolean = true
    ) {
        // Delimiters — all code-like text
        if (ext in setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx", "go", "rs", "swift", "c", "cpp", "h", "cs", "gradle")) {
            delimiterIssues(rel, text, issues)
            stringStateIssues(rel, text, ext, issues)
        }
        when (ext) {
            "kt", "kts" -> {
                kotlinPackage(rel, text, issues)
                // Android preflight: only critical import errors (missing @Composable), not unused spam
                kotlinImportIssues(rel, text, issues, includeNoise = importNoise)
                if (importNoise) kotlinRedFlags(rel, text, issues)
            }
            "java" -> {
                javaPackage(rel, text, issues)
                if (importNoise) javaImportIssues(rel, text, issues)
                delimiterIssues(rel, text, issues) // already called
            }
            "json" -> jsonIssues(rel, text, issues)
            "xml" -> xmlIssues(rel, text, issues)
            "js", "jsx", "ts", "tsx" -> jsRedFlags(rel, text, issues)
            "py" -> pythonRedFlags(rel, text, issues)
            "gradle" -> { /* kts covered by kt */ }
        }
        // build.gradle.kts filename
        if (rel.endsWith("build.gradle.kts") || rel.endsWith("build.gradle")) {
            gradleScriptFlags(rel, text, issues)
        }
    }

    // ── Delimiter / string state ──────────────────────────────────────────────

    /**
     * Returns first imbalance with approximate line number.
     * Public for tests / reuse.
     */
    fun findDelimiterImbalance(src: String): Pair<String, Int>? {
        var curly = 0; var paren = 0; var square = 0
        var i = 0
        val n = src.length
        var line = 1
        var firstBadLine = 0
        var firstBadKind = ""

        fun mark(kind: String) {
            if (firstBadLine == 0) {
                firstBadLine = line
                firstBadKind = kind
            }
        }

        while (i < n) {
            val c = src[i]
            when {
                c == '\n' -> line++
                c == '/' && i + 1 < n && src[i + 1] == '/' -> {
                    while (i < n && src[i] != '\n') i++
                    continue
                }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) {
                        if (src[i] == '\n') line++
                        i++
                    }
                    i += 2
                    continue
                }
                // Kotlin/JS raw triple quotes
                c == '"' && i + 2 < n && src[i + 1] == '"' && src[i + 2] == '"' -> {
                    i += 3
                    while (i + 2 < n && !(src[i] == '"' && src[i + 1] == '"' && src[i + 2] == '"')) {
                        if (src[i] == '\n') line++
                        i++
                    }
                    i += 3
                    continue
                }
                c == '"' -> {
                    // Double-quoted string. Kotlin may embed ${ expr } with nested quotes —
                    // scan templates so nested " do not terminate the outer string early
                    // (that false-end corrupts brace counts and reports phantom imbalances).
                    i++
                    while (i < n && src[i] != '"') {
                        if (src[i] == '\\' && i + 1 < n) {
                            i += 2
                            continue
                        }
                        if (src[i] == '\n') line++
                        if (src[i] == '$' && i + 1 < n && src[i + 1] == '{') {
                            i += 2
                            var depth = 1
                            while (i < n && depth > 0) {
                                when (src[i]) {
                                    '{' -> { depth++; i++ }
                                    '}' -> { depth--; i++ }
                                    '\n' -> { line++; i++ }
                                    '"' -> {
                                        i++
                                        while (i < n && src[i] != '"') {
                                            if (src[i] == '\\' && i + 1 < n) {
                                                i += 2
                                                continue
                                            }
                                            if (src[i] == '\n') line++
                                            // shallow nested ${} inside nested string
                                            if (src[i] == '$' && i + 1 < n && src[i + 1] == '{') {
                                                i += 2
                                                var d2 = 1
                                                while (i < n && d2 > 0) {
                                                    when (src[i]) {
                                                        '{' -> d2++
                                                        '}' -> d2--
                                                        '\n' -> line++
                                                    }
                                                    i++
                                                }
                                                continue
                                            }
                                            i++
                                        }
                                        if (i < n && src[i] == '"') i++
                                    }
                                    '\'' -> {
                                        i++
                                        while (i < n && src[i] != '\'') {
                                            if (src[i] == '\\' && i + 1 < n) {
                                                i += 2
                                                continue
                                            }
                                            if (src[i] == '\n') line++
                                            i++
                                        }
                                        if (i < n && src[i] == '\'') i++
                                    }
                                    else -> i++
                                }
                            }
                            continue
                        }
                        i++
                    }
                }
                c == '\'' -> {
                    // Kotlin/JS often uses ' for chars; also JS strings — treat as string
                    i++
                    while (i < n && src[i] != '\'') {
                        if (src[i] == '\\' && i + 1 < n) i++
                        if (src[i] == '\n') line++
                        i++
                    }
                }
                c == '`' -> {
                    // JS template literal
                    i++
                    while (i < n && src[i] != '`') {
                        if (src[i] == '\\' && i + 1 < n) i++
                        if (src[i] == '\n') line++
                        // skip ${ ... } roughly
                        if (src[i] == '$' && i + 1 < n && src[i + 1] == '{') {
                            i += 2
                            var depth = 1
                            while (i < n && depth > 0) {
                                when (src[i]) {
                                    '{' -> depth++
                                    '}' -> depth--
                                    '\n' -> line++
                                }
                                i++
                            }
                            continue
                        }
                        i++
                    }
                }
                c == '{' -> curly++
                c == '}' -> {
                    curly--
                    if (curly < 0) mark("braces {}")
                }
                c == '(' -> paren++
                c == ')' -> {
                    paren--
                    if (paren < 0) mark("parentheses ()")
                }
                c == '[' -> square++
                c == ']' -> {
                    square--
                    if (square < 0) mark("brackets []")
                }
            }
            if (curly < 0 || paren < 0 || square < 0) {
                return firstBadKind to firstBadLine
            }
            i++
        }
        return when {
            curly != 0 -> "braces {} (${if (curly > 0) "$curly unclosed {" else "${-curly} extra }"})" to
                (if (firstBadLine > 0) firstBadLine else line)
            paren != 0 -> "parentheses () (${if (paren > 0) "$paren unclosed" else "extra"})" to
                (if (firstBadLine > 0) firstBadLine else line)
            square != 0 -> "brackets []" to (if (firstBadLine > 0) firstBadLine else line)
            else -> null
        }
    }

    private fun delimiterIssues(rel: String, text: String, issues: MutableList<Issue>) {
        val imb = findDelimiterImbalance(text) ?: return
        issues.add(
            Issue(
                Issue.Severity.ERROR, rel, imb.second,
                "Unbalanced ${imb.first} — file looks truncated/malformed and will not compile."
            )
        )
    }

    /**
     * Detect EOF while still inside a string (common truncation).
     *
     * Forward, escape-aware scan: the character AFTER a backslash is always skipped, so char
     * literals like `'\\'` and strings ending in `\\"` no longer flip the parser into a stuck
     * state (the old `text[i-1] != '\\'` check mis-fired on these and cascaded into bogus
     * "unclosed string" reports on perfectly valid files). Each string is scanned to its own
     * close, so we only report the string that genuinely runs to EOF.
     *
     * Backtick is language-aware: a JS/TS template literal (multi-line, interpolating) but in
     * Kotlin/Java just a single-line escaped identifier — never a string — so it can't be
     * reported as an "unclosed template literal".
     */
    private fun stringStateIssues(rel: String, text: String, ext: String, issues: MutableList<Issue>) {
        var i = 0
        val n = text.length
        var line = 1
        val backtickTemplate = ext in setOf("js", "jsx", "ts", "tsx")

        while (i < n) {
            val c = text[i]
            when {
                c == '\n' -> { line++; i++ }
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    while (i < n && text[i] != '\n') i++
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) {
                        if (text[i] == '\n') line++
                        i++
                    }
                    i += 2
                }
                // Kotlin/JS raw triple-quoted string
                c == '"' && i + 2 < n && text[i + 1] == '"' && text[i + 2] == '"' -> {
                    val startLine = line
                    i += 3
                    var closed = false
                    while (i + 2 < n) {
                        if (text[i] == '"' && text[i + 1] == '"' && text[i + 2] == '"') {
                            i += 3; closed = true; break
                        }
                        if (text[i] == '\n') line++
                        i++
                    }
                    if (!closed) {
                        issues.add(Issue(Issue.Severity.ERROR, rel, startLine, "Unclosed triple-quoted string (\"\"\") — likely truncated file."))
                        return
                    }
                }
                // Normal double-quoted string (Kotlin templates may embed ${…} with nested quotes)
                c == '"' -> {
                    val startLine = line
                    i++
                    var closed = false
                    while (i < n) {
                        val d = text[i]
                        if (d == '\\' && i + 1 < n) { if (text[i + 1] == '\n') line++; i += 2; continue }
                        // ${ expr } — skip nested so quotes inside templates don't end the string
                        if (d == '$' && i + 1 < n && text[i + 1] == '{') {
                            i += 2
                            var depth = 1
                            while (i < n && depth > 0) {
                                when (text[i]) {
                                    '{' -> { depth++; i++ }
                                    '}' -> { depth--; i++ }
                                    '\n' -> { line++; i++ }
                                    '"' -> {
                                        i++
                                        while (i < n && text[i] != '"') {
                                            if (text[i] == '\\' && i + 1 < n) { i += 2; continue }
                                            if (text[i] == '\n') line++
                                            if (text[i] == '$' && i + 1 < n && text[i + 1] == '{') {
                                                i += 2
                                                var d2 = 1
                                                while (i < n && d2 > 0) {
                                                    when (text[i]) {
                                                        '{' -> d2++
                                                        '}' -> d2--
                                                        '\n' -> line++
                                                    }
                                                    i++
                                                }
                                                continue
                                            }
                                            i++
                                        }
                                        if (i < n && text[i] == '"') i++
                                    }
                                    '\'' -> {
                                        i++
                                        while (i < n && text[i] != '\'') {
                                            if (text[i] == '\\' && i + 1 < n) { i += 2; continue }
                                            if (text[i] == '\n') line++
                                            i++
                                        }
                                        if (i < n && text[i] == '\'') i++
                                    }
                                    else -> i++
                                }
                            }
                            continue
                        }
                        if (d == '"') { i++; closed = true; break }
                        if (d == '\n') line++
                        i++
                    }
                    if (!closed) {
                        issues.add(Issue(Issue.Severity.ERROR, rel, startLine, "Unclosed double-quoted string — likely truncated file."))
                        return
                    }
                }
                // Char literal (Kotlin/Java) or single-quoted string (JS)
                c == '\'' -> {
                    val startLine = line
                    i++
                    var closed = false
                    while (i < n) {
                        val d = text[i]
                        if (d == '\\' && i + 1 < n) { if (text[i + 1] == '\n') line++; i += 2; continue }
                        if (d == '\'') { i++; closed = true; break }
                        if (d == '\n') line++
                        i++
                    }
                    if (!closed) {
                        issues.add(Issue(Issue.Severity.ERROR, rel, startLine, "Unclosed single-quoted string / char literal — likely truncated file."))
                        return
                    }
                }
                // JS/TS template literal (interpolating, multi-line)
                c == '`' && backtickTemplate -> {
                    val startLine = line
                    i++
                    var closed = false
                    while (i < n) {
                        val d = text[i]
                        if (d == '\\' && i + 1 < n) { if (text[i + 1] == '\n') line++; i += 2; continue }
                        if (d == '$' && i + 1 < n && text[i + 1] == '{') {
                            i += 2
                            var depth = 1
                            while (i < n && depth > 0) {
                                when (text[i]) { '{' -> depth++; '}' -> depth--; '\n' -> line++ }
                                i++
                            }
                            continue
                        }
                        if (d == '`') { i++; closed = true; break }
                        if (d == '\n') line++
                        i++
                    }
                    if (!closed) {
                        issues.add(Issue(Issue.Severity.ERROR, rel, startLine, "Unclosed template literal (`) — likely truncated file."))
                        return
                    }
                }
                // Kotlin/Java escaped identifier `like this` — single line, never a multi-line string.
                c == '`' -> {
                    var j = i + 1
                    while (j < n && text[j] != '`' && text[j] != '\n') j++
                    i = if (j < n && text[j] == '`') j + 1 else i + 1
                }
                else -> i++
            }
        }
    }

    // ── Language-specific ─────────────────────────────────────────────────────

    private fun kotlinPackage(rel: String, text: String, issues: MutableList<Issue>) {
        val pkg = Regex("(?m)^\\s*package\\s+([A-Za-z0-9_.]+)").find(text)?.groupValues?.get(1)
            ?: return
        val srcRoots = listOf(
            "src/main/java/", "src/main/kotlin/",
            "src/test/java/", "src/test/kotlin/",
            "src/androidTest/java/", "src/androidTest/kotlin/"
        )
        val underSrc = srcRoots.firstOrNull { rel.contains(it) } ?: return
        val dirPath = rel.substringAfter(underSrc).substringBeforeLast('/', "")
        val expected = pkg.replace('.', '/')
        if (dirPath.isNotEmpty() && dirPath != expected) {
            val line = text.lineSequence().indexOfFirst { it.contains("package") }.let { if (it < 0) 1 else it + 1 }
            issues.add(
                Issue(
                    Issue.Severity.ERROR, rel, line,
                    "package '$pkg' does not match folder '$dirPath' (expected '$expected'). " +
                        "Move file or fix package — Kotlin compile will fail."
                )
            )
        }
    }

    private fun javaPackage(rel: String, text: String, issues: MutableList<Issue>) {
        kotlinPackage(rel, text, issues) // same rule
    }

    private fun kotlinRedFlags(rel: String, text: String, issues: MutableList<Issue>) {
        val lines = text.lines()
        lines.forEachIndexed { idx, raw ->
            val line = raw.trim()
            val ln = idx + 1
            // Orphan `else` only at the very start of the file (high confidence)
            if ((line == "else" || line == "else {") && idx == 0) {
                issues.add(Issue(Issue.Severity.WARNING, rel, ln, "Orphan `else` at top of file — incomplete control flow?"))
            }
            // NOTE: "fun without body" heuristics removed — multi-line signatures, expect/actual,
            // interface members, and abstract methods caused constant false positives.
        }
        // Trailing incomplete tokens (skip pure comments / annotations)
        val lastCode = lines.lastOrNull {
            val t = it.trim()
            t.isNotEmpty() && !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*")
        }?.trim().orEmpty()
        // `...` trailing on a comment-only end is fine; also allow `}` `)` `]` `,` `;`
        if (lastCode.endsWith("=") || lastCode.endsWith("->") ||
            (lastCode.endsWith(".") && !lastCode.endsWith("..") && !lastCode.endsWith("..."))
        ) {
            issues.add(
                Issue(
                    Issue.Severity.ERROR, rel, lines.size,
                    "File ends with incomplete expression (`$lastCode`) — truncated write?"
                )
            )
        }
    }

    private fun jsRedFlags(rel: String, text: String, issues: MutableList<Issue>) {
        val last = text.lines().lastOrNull { it.trim().isNotEmpty() && !it.trim().startsWith("//") }?.trim().orEmpty()
        if (last.endsWith("=") || last.endsWith("=>") || last.endsWith(".")) {
            issues.add(Issue(Issue.Severity.ERROR, rel, text.lines().size, "File ends mid-expression — truncated?"))
        }
    }

    private fun pythonRedFlags(rel: String, text: String, issues: MutableList<Issue>) {
        // Indentation mix tabs/spaces
        val hasTab = text.lines().any { it.startsWith("\t") }
        val hasSpaceIndent = text.lines().any { it.startsWith("    ") || it.startsWith("  ") }
        if (hasTab && hasSpaceIndent) {
            issues.add(Issue(Issue.Severity.WARNING, rel, 0, "Mixed tabs and spaces for indentation."))
        }
        // Unclosed triple quotes simple
        val tripleDouble = Regex("\"\"\"").findAll(text).count()
        if (tripleDouble % 2 != 0) {
            issues.add(Issue(Issue.Severity.ERROR, rel, 0, "Odd number of \"\"\" — unclosed triple-quoted string."))
        }
    }

    private fun jsonIssues(rel: String, text: String, issues: MutableList<Issue>) {
        try {
            org.json.JSONTokener(text.trim()).nextValue()
        } catch (e: Exception) {
            issues.add(Issue(Issue.Severity.ERROR, rel, 0, "Invalid JSON: ${e.message?.take(120)}"))
        }
    }

    private fun xmlIssues(rel: String, text: String, issues: MutableList<Issue>) {
        // Very light: count <tag vs </tag for common tags; skip self-closing
        if (!text.contains("<")) return
        // Strip comments / CDATA for BOTH angle-count and tag stack — comments often contain
        // literal `</foo>` or `=>` and otherwise produce hard false positives.
        val stripped = text
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<!\[CDATA\[.*?]]>""", RegexOption.DOT_MATCHES_ALL), "")
        val openAngles = stripped.count { it == '<' }
        val closeAngles = stripped.count { it == '>' }
        if (openAngles != closeAngles) {
            issues.add(Issue(Issue.Severity.WARNING, rel, 0, "Mismatched < > count — XML may be truncated."))
        }
        // Find tags left open (stack) — scan stripped text only
        val stack = ArrayDeque<Pair<String, Int>>()
        val tagRe = Regex("""</?([A-Za-z_][\w:.-]*)\b[^>]*?/?>""")
        // Map offsets in stripped → approximate line in original (good enough for messages)
        var line = 1
        var last = 0
        for (m in tagRe.findAll(stripped)) {
            val chunk = stripped.substring(last, m.range.first)
            line += chunk.count { it == '\n' }
            last = m.range.first
            val full = m.value
            val name = m.groupValues[1]
            if (full.startsWith("<?") || full.startsWith("<!")) continue
            if (full.startsWith("</")) {
                if (stack.isEmpty()) {
                    // Extra closing tags are common in partial agent edits; only hard-error when
                    // we had an open stack of a different name (real mismatch).
                    continue
                }
                if (stack.last().first != name) {
                    issues.add(
                        Issue(
                            Issue.Severity.ERROR, rel, line,
                            "Unexpected closing </$name> (open <${stack.last().first}>) — XML tag mismatch."
                        )
                    )
                    return
                }
                stack.removeLast()
            } else if (!full.endsWith("/>")) {
                stack.addLast(name to line)
            }
        }
        if (stack.isNotEmpty()) {
            val (name, ln) = stack.last()
            issues.add(Issue(Issue.Severity.ERROR, rel, ln, "Unclosed XML tag <$name>."))
        }
    }

    private fun gradleScriptFlags(rel: String, text: String, issues: MutableList<Issue>) {
        // Only the module that actually CONFIGURES Android (has an `android { }` block) needs
        // compileSdk. Root / settings build files merely DECLARE the plugin — often with
        // `apply false` — and must not be flagged (that was a false positive).
        val configuresAndroid = text.contains("com.android.application") &&
            Regex("""(?m)^\s*android\s*\{""").containsMatchIn(text)
        if (configuresAndroid) {
            if (!text.contains("compileSdk") && !text.contains("compileSdkVersion")) {
                issues.add(
                    Issue(
                        Issue.Severity.WARNING, rel, 0,
                        "Android application module without compileSdk — build may fail."
                    )
                )
            }
        }
    }

    // ── Imports ───────────────────────────────────────────────────────────────

    private data class ImportLine(
        val full: String,       // full import path without "import "
        val simple: String,     // last segment or alias
        val lineNo: Int,
        val raw: String,
        val isStar: Boolean
    )

    private fun parseKotlinJavaImports(text: String): List<ImportLine> {
        val out = mutableListOf<ImportLine>()
        text.lines().forEachIndexed { idx, raw ->
            val t = raw.trim()
            val m = Regex("""^import\s+(?:static\s+)?([A-Za-z0-9_.*]+)(?:\s+as\s+([A-Za-z0-9_]+))?\s*;?\s*$""")
                .find(t) ?: return@forEachIndexed
            val path = m.groupValues[1]
            val alias = m.groupValues[2]
            val star = path.endsWith(".*")
            val simple = when {
                alias.isNotBlank() -> alias
                star -> path.removeSuffix(".*").substringAfterLast('.')
                else -> path.substringAfterLast('.')
            }
            out.add(ImportLine(path, simple, idx + 1, raw, star))
        }
        return out
    }

    /** Body after last import / package — for unused-import scans. */
    private fun codeBodyAfterImports(text: String): String {
        val lines = text.lines()
        var lastHeader = -1
        lines.forEachIndexed { i, raw ->
            val t = raw.trim()
            when {
                t.startsWith("package ") || t.startsWith("import ") -> lastHeader = i
                t.isEmpty() || t.startsWith("//") || t.startsWith("/*") || t.startsWith("*") || t.startsWith("@file:") -> { }
                lastHeader >= 0 -> return lines.drop(i).joinToString("\n")
            }
        }
        return if (lastHeader >= 0) lines.drop(lastHeader + 1).joinToString("\n") else text
    }

    /**
     * Names that are often imported for side-effects / delegates and rarely appear as bare
     * identifiers — flagging them as "unused" is almost always a false positive.
     */
    private val IMPLICIT_USE_IMPORTS = setOf(
        // Kotlin property delegates: `var x by mutableStateOf()` needs these but never writes the names
        "getValue", "setValue", "provideDelegate",
        // Destructuring / operators
        "component1", "component2", "component3", "component4", "component5",
        "iterator", "hasNext", "next", "compareTo", "contains", "invoke",
        "plus", "minus", "times", "div", "rem", "rangeTo", "rangeUntil",
        "inc", "dec", "unaryPlus", "unaryMinus", "not",
        // Serialization / Compose compiler annotations sometimes only on files
        "SerialName", "Serializable",
        // Common OptIn markers referenced as Foo::class inside @OptIn — still matched by \b usually
    )

    /** True if any import covers [fullPath] exactly, via simple name, or a star import of its parent. */
    private fun List<ImportLine>.covers(fullPath: String, simple: String = fullPath.substringAfterLast('.')): Boolean {
        if (any { it.full == fullPath || it.simple == simple && !it.isStar }) return true
        if (any { !it.isStar && it.full.endsWith(".$simple") }) return true
        val parent = fullPath.substringBeforeLast('.', "")
        if (parent.isNotEmpty() && any { it.isStar && it.full.removeSuffix(".*") == parent }) return true
        // Broader star: androidx.compose.runtime.* covers Composable
        return any { it.isStar && fullPath.startsWith(it.full.removeSuffix(".*") + ".") }
    }

    private fun kotlinImportIssues(
        rel: String,
        text: String,
        issues: MutableList<Issue>,
        includeNoise: Boolean = true
    ) {
        val imports = parseKotlinJavaImports(text)
        val body = codeBodyAfterImports(text)
        // Strip strings/comments so docs like "@Composable is used" in StaticVerifier/ApkPreflight
        // never false-trigger a missing-import ERROR (blocked GH_BUILD_APK for pure data files).
        val codeOnly = stripStringsAndCommentsForScan(body)
        fun codeHas(re: String) = Regex(re).containsMatchIn(codeOnly)
        fun bodyHas(re: String) = Regex(re).containsMatchIn(body)

        // Critical only: real @Composable *annotation in code*, not string/comment mentions
        // Require a typical usage shape: @Composable before fun / on its own line context.
        val realComposableUse = Regex(
            """@Composable\b(?:\s*\([^)]*\))?\s*(?:\n\s*)?(?:@\w+\b|fun\s|private\s|internal\s|public\s|override\s|suspend\s|inline\s|actual\s|expect\s)"""
        ).containsMatchIn(codeOnly) ||
            // single-line: @Composable fun Foo
            Regex("""@Composable\b[^\n]{0,40}\bfun\b""").containsMatchIn(codeOnly)

        if (realComposableUse &&
            !imports.covers("androidx.compose.runtime.Composable", "Composable") &&
            !imports.any { it.simple == "Composable" || it.full.endsWith(".Composable") }
        ) {
            issues.add(
                Issue(
                    Issue.Severity.ERROR, rel, 0,
                    "Missing import for @Composable — add `import androidx.compose.runtime.Composable`."
                )
            )
        }

        if (!includeNoise) return // android/apk preflight: stop here (no unused-import spam)

        // Duplicates
        val seen = HashMap<String, Int>()
        for (imp in imports) {
            val key = imp.full
            if (seen.containsKey(key)) {
                issues.add(
                    Issue(
                        Issue.Severity.WARNING, rel, imp.lineNo,
                        "Duplicate import `${imp.full}` (also line ${seen[key]})."
                    )
                )
            } else seen[key] = imp.lineNo
        }

        val bodyForUse = stripStringsAndCommentsForScan(body)
        val usesByDelegate = Regex("""\bby\s+""").containsMatchIn(bodyForUse)

        // Unused non-star imports — noisy; only for quick/full modes
        for (imp in imports) {
            if (imp.isStar) continue
            if (imp.simple.isBlank() || imp.simple.length < 2) continue
            val name = imp.simple
            if (name in IMPLICIT_USE_IMPORTS) {
                if (name == "getValue" || name == "setValue" || name == "provideDelegate") {
                    if (usesByDelegate) continue
                } else {
                    continue
                }
            }
            // Compose / Android type names often used only as type args or extensions
            if (name in setOf(
                    "Modifier", "Color", "Dp", "PaddingValues", "Alignment", "Arrangement",
                    "FontWeight", "FontFamily", "TextAlign", "ContentScale", "ImageVector",
                    "Brush", "BorderStroke", "CircleShape", "RoundedCornerShape",
                    "LocalContext", "LocalDensity", "LocalUriHandler", "LocalClipboardManager"
                )
            ) {
                // High false-positive rate when only used as nested types / defaults
                val usedLoose = bodyForUse.contains(name)
                if (usedLoose) continue
            }
            val used = when (name) {
                "R" -> Regex("""\bR\.""").containsMatchIn(bodyForUse)
                else -> Regex("""\b${Regex.escape(name)}\b""").containsMatchIn(bodyForUse) ||
                    Regex("""@${Regex.escape(name)}\b""").containsMatchIn(bodyForUse)
            }
            if (!used) {
                issues.add(
                    Issue(
                        Issue.Severity.WARNING, rel, imp.lineNo,
                        "Unused import `${imp.full}` — `$name` not referenced in file body."
                    )
                )
            }
        }

        if (bodyHas("""\bremember\s*[\({]""") &&
            !imports.covers("androidx.compose.runtime.remember", "remember") &&
            !imports.any { it.simple.startsWith("remember") }
        ) {
            issues.add(
                Issue(
                    Issue.Severity.WARNING, rel, 0,
                    "Uses `remember` — ensure `import androidx.compose.runtime.remember`."
                )
            )
        }
        if (bodyHas("""\bmutableStateOf\s*\(""") &&
            !imports.covers("androidx.compose.runtime.mutableStateOf", "mutableStateOf")
        ) {
            issues.add(
                Issue(
                    Issue.Severity.WARNING, rel, 0,
                    "Uses `mutableStateOf` — ensure `import androidx.compose.runtime.mutableStateOf`."
                )
            )
        }
        if (bodyHas("""@Composable""") && bodyHas(""":\s*Modifier\b|\bModifier\.""")) {
            if (!imports.covers("androidx.compose.ui.Modifier.Modifier", "Modifier")) {
                issues.add(
                    Issue(
                        Issue.Severity.WARNING, rel, 0,
                        "Uses Compose `Modifier` — ensure `import androidx.compose.ui.Modifier.Modifier`."
                    )
                )
            }
        }
    }

    /** Best-effort strip of comments + string literals so symbol scans ignore noise. */
    private fun stripStringsAndCommentsForScan(src: String): String {
        val sb = StringBuilder(src.length)
        var i = 0
        val n = src.length
        while (i < n) {
            val c = src[i]
            when {
                c == '/' && i + 1 < n && src[i + 1] == '/' -> {
                    while (i < n && src[i] != '\n') i++
                }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                }
                c == '"' && i + 2 < n && src[i + 1] == '"' && src[i + 2] == '"' -> {
                    i += 3
                    while (i + 2 < n && !(src[i] == '"' && src[i + 1] == '"' && src[i + 2] == '"')) {
                        if (src[i] == '\n') sb.append('\n')
                        i++
                    }
                    i = (i + 3).coerceAtMost(n)
                    sb.append("\"\"") // placeholder so word boundaries still work nearby
                }
                c == '"' -> {
                    i++
                    while (i < n && src[i] != '"') {
                        if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                        if (src[i] == '$' && i + 1 < n && src[i + 1] == '{') {
                            // keep ${} expression text — real code references live there
                            i += 2
                            var depth = 1
                            sb.append("  ")
                            while (i < n && depth > 0) {
                                when (src[i]) {
                                    '{' -> { depth++; sb.append(' '); i++ }
                                    '}' -> { depth--; sb.append(' '); i++ }
                                    '\n' -> { sb.append('\n'); i++ }
                                    else -> { sb.append(src[i]); i++ }
                                }
                            }
                            continue
                        }
                        if (src[i] == '\n') sb.append('\n')
                        i++
                    }
                    if (i < n && src[i] == '"') i++
                    sb.append("\"\"")
                }
                c == '\'' -> {
                    i++
                    while (i < n && src[i] != '\'') {
                        if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                        i++
                    }
                    if (i < n && src[i] == '\'') i++
                    sb.append("'x'")
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    private fun javaImportIssues(rel: String, text: String, issues: MutableList<Issue>) {
        val imports = parseKotlinJavaImports(text)
        val seen = HashMap<String, Int>()
        for (imp in imports) {
            if (seen.containsKey(imp.full)) {
                issues.add(Issue(Issue.Severity.WARNING, rel, imp.lineNo, "Duplicate import `${imp.full}`."))
            } else seen[imp.full] = imp.lineNo
        }
        val body = stripStringsAndCommentsForScan(codeBodyAfterImports(text))
        for (imp in imports) {
            if (imp.isStar) continue
            if (imp.simple in IMPLICIT_USE_IMPORTS) continue
            if (imp.simple.isBlank() || imp.simple.length < 2) continue
            if (!Regex("""\b${Regex.escape(imp.simple)}\b""").containsMatchIn(body)) {
                issues.add(
                    Issue(
                        Issue.Severity.WARNING, rel, imp.lineNo,
                        "Unused import `${imp.full}`."
                    )
                )
            }
        }
    }

    // ── Auto-fix helpers ────────────────────────────────────────────────────

    private fun removeDuplicateImports(text: String): Pair<String, Int> {
        val lines = text.lines().toMutableList()
        val seen = HashSet<String>()
        var removed = 0
        val out = ArrayList<String>(lines.size)
        for (line in lines) {
            val t = line.trim()
            val m = Regex("""^import\s+(?:static\s+)?([A-Za-z0-9_.*]+)""").find(t)
            if (m != null) {
                val key = m.groupValues[1]
                if (!seen.add(key)) {
                    removed++
                    continue
                }
            }
            out.add(line)
        }
        return out.joinToString("\n") to removed
    }

    private fun ensureKotlinImports(text: String): Pair<String, List<String>> {
        val imports = parseKotlinJavaImports(text)
        val have = imports.map { it.full }.toHashSet()
        val haveSimple = imports.map { it.simple }.toHashSet()
        val body = codeBodyAfterImports(text)
        val need = linkedMapOf<String, String>() // full -> reason unused

        fun want(full: String, simple: String, trigger: Boolean) {
            if (!trigger) return
            if (full in have || simple in haveSimple) return
            if (have.any { it.endsWith(".$simple") }) return
            // Star import of parent package already covers it
            val parent = full.substringBeforeLast('.', "")
            if (parent.isNotEmpty() && have.any { it == "$parent.*" }) return
            if (imports.covers(full, simple)) return
            need[full] = simple
        }

        want("androidx.compose.runtime.Composable", "Composable", Regex("""@Composable\b""").containsMatchIn(body))
        want("androidx.compose.runtime.remember", "remember", Regex("""\bremember\s*[\({]""").containsMatchIn(body))
        want("androidx.compose.runtime.mutableStateOf", "mutableStateOf", Regex("""\bmutableStateOf\s*\(""").containsMatchIn(body))
        want("androidx.compose.runtime.getValue", "getValue", Regex("""\bby\s+remember\b|\bby\s+mutableState""").containsMatchIn(body))
        want("androidx.compose.runtime.setValue", "setValue", Regex("""\bby\s+remember\b|\bby\s+mutableState""").containsMatchIn(body))
        want(
            "androidx.compose.ui.Modifier.Modifier", "Modifier",
            Regex("""@Composable""").containsMatchIn(body) &&
                Regex("""\bModifier\b""").containsMatchIn(body)
        )
        want(
            "androidx.compose.foundation.layout.Column", "Column",
            Regex("""\bColumn\s*\(""").containsMatchIn(body)
        )
        want(
            "androidx.compose.foundation.layout.Row", "Row",
            Regex("""\bRow\s*\(""").containsMatchIn(body)
        )
        want(
            "androidx.compose.foundation.layout.Box", "Box",
            Regex("""\bBox\s*\(""").containsMatchIn(body)
        )
        want(
            "androidx.compose.material3.Text", "Text",
            Regex("""\bText\s*\(""").containsMatchIn(body) &&
                Regex("""@Composable""").containsMatchIn(body)
        )
        want(
            "androidx.compose.runtime.Composable", "Composable",
            Regex("""@Preview\b""").containsMatchIn(body)
        )

        if (need.isEmpty()) return text to emptyList()

        val lines = text.lines().toMutableList()
        var insertAt = 0
        var lastImport = -1
        var packageLine = -1
        lines.forEachIndexed { i, raw ->
            val t = raw.trim()
            if (t.startsWith("package ")) packageLine = i
            if (t.startsWith("import ")) lastImport = i
        }
        insertAt = when {
            lastImport >= 0 -> lastImport + 1
            packageLine >= 0 -> packageLine + 1
            else -> 0
        }
        // blank line after package if no imports yet
        if (lastImport < 0 && packageLine >= 0 && insertAt < lines.size && lines[insertAt].isNotBlank()) {
            lines.add(insertAt, "")
            insertAt++
        }
        val added = mutableListOf<String>()
        for ((full, _) in need) {
            lines.add(insertAt, "import $full")
            insertAt++
            added.add(full)
        }
        return lines.joinToString("\n") to added
    }

    /**
     * If the file only has unclosed opens (no mid-file surplus closers, no unclosed strings),
     * append the missing closers at EOF.
     */
    private fun tryCloseDelimiters(src: String): Pair<String, String?> {
        // Refuse if unclosed strings — fixing braces would make it worse
        val stringIssues = mutableListOf<Issue>()
        stringStateIssues("_", src, "kt", stringIssues)
        if (stringIssues.any { it.severity == Issue.Severity.ERROR }) return src to null

        var curly = 0; var paren = 0; var square = 0
        var i = 0
        val n = src.length
        var sawNegative = false
        while (i < n) {
            val c = src[i]
            when {
                c == '/' && i + 1 < n && src[i + 1] == '/' -> {
                    while (i < n && src[i] != '\n') i++
                }
                c == '/' && i + 1 < n && src[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(src[i] == '*' && src[i + 1] == '/')) i++
                    i += 2; continue
                }
                c == '"' && i + 2 < n && src[i + 1] == '"' && src[i + 2] == '"' -> {
                    i += 3
                    while (i + 2 < n && !(src[i] == '"' && src[i + 1] == '"' && src[i + 2] == '"')) i++
                    i += 3; continue
                }
                c == '"' -> {
                    i++
                    while (i < n && src[i] != '"') {
                        if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                        // skip ${}
                        if (src[i] == '$' && i + 1 < n && src[i + 1] == '{') {
                            i += 2
                            var d = 1
                            while (i < n && d > 0) {
                                when (src[i]) { '{' -> d++; '}' -> d-- }
                                i++
                            }
                            continue
                        }
                        i++
                    }
                }
                c == '\'' -> {
                    i++
                    while (i < n && src[i] != '\'') {
                        if (src[i] == '\\' && i + 1 < n) { i += 2; continue }
                        i++
                    }
                }
                c == '{' -> curly++
                c == '}' -> { curly--; if (curly < 0) sawNegative = true }
                c == '(' -> paren++
                c == ')' -> { paren--; if (paren < 0) sawNegative = true }
                c == '[' -> square++
                c == ']' -> { square--; if (square < 0) sawNegative = true }
            }
            if (sawNegative) return src to null
            i++
        }
        if (curly == 0 && paren == 0 && square == 0) return src to null
        if (curly < 0 || paren < 0 || square < 0) return src to null
        // Cap auto-close to avoid swallowing huge structural damage
        if (curly + paren + square > 40) return src to null

        // Append missing closers at EOF (truncated agent writes)
        val fixed = src.trimEnd() + "\n" + buildString {
            repeat(paren) { append(')') }
            repeat(square) { append(']') }
            if (paren > 0 || square > 0) append('\n')
            repeat(curly) { append("}\n") }
        }
        val parts = mutableListOf<String>()
        if (paren > 0) parts.add("$paren×')'")
        if (square > 0) parts.add("$square×']'")
        if (curly > 0) parts.add("$curly×'}'")
        return fixed to "Closed unclosed delimiters at EOF (${parts.joinToString(", ")})"
    }
}
