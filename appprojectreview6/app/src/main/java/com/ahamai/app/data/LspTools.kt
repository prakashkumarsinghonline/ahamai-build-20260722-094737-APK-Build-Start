package com.ahamai.app.data

import java.io.File

/**
 * Real LSP-style code intelligence for the agent (works offline, no language-server process).
 *
 * This is stronger than simple [CodeAgent] SYMBOL_SEARCH regex:
 *  - structured symbol index (kind, signature, container, line)
 *  - go-to-definition with ranked candidates
 *  - find-references with word boundaries (defs marked separately)
 *  - document / workspace symbols
 *  - hover = signature + docstring + definition location
 *
 * Language coverage: Kotlin, Java, TypeScript/JavaScript, Python, Go, Rust, Swift, Ruby, PHP, C/C++/C#,
 * and generic fallbacks. Not a full type-checker — for compile errors use [LspManager.diagnose].
 */
object LspTools {

    data class Symbol(
        val name: String,
        val kind: String,           // class, function, method, interface, enum, variable, type, property
        val file: String,
        val line: Int,              // 1-based
        val signature: String,
        val container: String = "", // enclosing class/object if known
        val doc: String = ""
    )

    // ── Public tools ──────────────────────────────────────────────────────────

    fun goToDefinition(
        projectDir: String,
        symbol: String,
        pathScope: String = "",
        maxResults: Int = 12
    ): String {
        val name = symbol.trim().trim('`', '"', '\'')
        if (name.isBlank()) return "ERROR: GO_TO_DEFINITION needs a symbol name."
        val bare = name.substringAfterLast('.').substringAfterLast("::")
        val index = buildIndex(projectDir, pathScope)
        val defs = index.filter {
            it.name == bare || it.name == name ||
                (it.container.isNotBlank() && "${it.container}.${it.name}" == name)
        }
        if (defs.isEmpty()) {
            // Fuzzy: case-insensitive exact name
            val ci = index.filter { it.name.equals(bare, ignoreCase = true) }
            if (ci.isEmpty()) {
                return "No definition found for `$name`. Try WORKSPACE_SYMBOLS or GREP."
            }
            return formatDefs(name, ci.take(maxResults), fuzzyNote = " (case-insensitive)")
        }
        // Prefer exact file-scope matches and types over variables
        val ranked = defs.sortedWith(
            compareByDescending<Symbol> { kindRank(it.kind) }
                .thenBy { it.file.length }
                .thenBy { it.line }
        )
        return formatDefs(name, ranked.take(maxResults))
    }

    fun findReferences(
        projectDir: String,
        symbol: String,
        pathScope: String = "",
        maxResults: Int = 60
    ): String {
        val name = symbol.trim().trim('`', '"', '\'')
        if (name.isBlank()) return "ERROR: FIND_REFERENCES needs a symbol name."
        val bare = name.substringAfterLast('.').substringAfterLast("::")
        if (bare.length < 2) return "ERROR: symbol too short."

        val word = Regex("""\b${Regex.escape(bare)}\b""")
        val defLines = buildIndex(projectDir, pathScope)
            .filter { it.name == bare }
            .map { it.file to it.line }
            .toSet()

        val refs = mutableListOf<String>()
        val files = ProjectManager.listTextFiles(projectDir, pathScope)
        var total = 0
        for (rel in files) {
            val f = File(projectDir, rel)
            if (!ProjectManager.isInsideProject(projectDir, f)) continue
            val lines = runCatching { f.readLines() }.getOrNull() ?: continue
            lines.forEachIndexed { i, line ->
                if (!word.containsMatchIn(line)) return@forEachIndexed
                // Skip pure import-of-self noise lightly; still report
                val lineNo = i + 1
                val isDef = (rel to lineNo) in defLines
                total++
                if (refs.size < maxResults) {
                    val tag = if (isDef) "def" else "ref"
                    refs.add("$rel:$lineNo [$tag] ${line.trim().take(140)}")
                }
            }
        }
        if (total == 0) return "No references found for `$bare`."
        return buildString {
            append("FIND_REFERENCES `$bare` — $total hit(s)")
            if (total > refs.size) append(" (showing ${refs.size})")
            append(":\n")
            refs.forEach { append(it).append('\n') }
        }.trimEnd()
    }

    fun documentSymbols(projectDir: String, path: String): String {
        val rel = path.trim()
        if (rel.isBlank()) return "ERROR: DOCUMENT_SYMBOLS needs a file path."
        val f = ProjectManager.resolveFile(projectDir, rel)
            ?: return "ERROR: File not found (or outside sandbox): $rel"
        if (!ProjectManager.isInsideProject(projectDir, f)) return "ERROR: Path outside project sandbox"
        val shown = f.relativeTo(File(projectDir)).path.replace('\\', '/')
        val text = runCatching { f.readText() }.getOrNull()
            ?: return "ERROR: cannot read $shown"
        if (text.length > 1_500_000) return "ERROR: file too large for symbol scan."
        val syms = extractSymbolsFromFile(shown, text)
        if (syms.isEmpty()) return "No symbols found in $shown."
        return buildString {
            append("DOCUMENT_SYMBOLS $shown (${syms.size}):\n")
            for (s in syms) {
                append("  L${s.line}  [${s.kind}]")
                if (s.container.isNotBlank()) append(" ${s.container}.")
                else append(' ')
                append(s.name)
                if (s.signature.isNotBlank() && s.signature != s.name) {
                    append("  — ").append(s.signature.take(100))
                }
                append('\n')
            }
        }.trimEnd()
    }

    fun workspaceSymbols(
        projectDir: String,
        query: String,
        pathScope: String = "",
        maxResults: Int = 40
    ): String {
        val q = query.trim()
        if (q.isBlank()) return "ERROR: WORKSPACE_SYMBOLS needs a query (name fragment)."
        val qLower = q.lowercase()
        val bare = q.substringAfterLast('.').lowercase()
        val index = buildIndex(projectDir, pathScope)
        val hits = index.mapNotNull { s ->
            val score = when {
                s.name.equals(q, ignoreCase = true) -> 100
                s.name.equals(bare, ignoreCase = true) -> 90
                s.name.lowercase().startsWith(bare) -> 70
                s.name.lowercase().contains(bare) -> 50
                s.signature.lowercase().contains(qLower) -> 30
                "${s.container}.${s.name}".lowercase().contains(qLower) -> 40
                else -> 0
            }
            if (score > 0) s to score else null
        }.sortedWith(
            compareByDescending<Pair<Symbol, Int>> { it.second }
                .thenByDescending { kindRank(it.first.kind) }
                .thenBy { it.first.file }
        ).take(maxResults.coerceIn(1, 100))

        if (hits.isEmpty()) return "No workspace symbols matching `$q`."
        return buildString {
            append("WORKSPACE_SYMBOLS `$q` (${hits.size}):\n")
            for ((s, _) in hits) {
                append("  [${s.kind}] ")
                if (s.container.isNotBlank()) append("${s.container}.")
                append("${s.name}  @ ${s.file}:${s.line}\n")
                if (s.signature.isNotBlank()) append("      ${s.signature.take(120)}\n")
            }
        }.trimEnd()
    }

    fun hover(
        projectDir: String,
        symbol: String,
        pathScope: String = ""
    ): String {
        val name = symbol.trim().trim('`', '"', '\'')
        if (name.isBlank()) return "ERROR: HOVER needs a symbol name."
        val bare = name.substringAfterLast('.').substringAfterLast("::")
        val defs = buildIndex(projectDir, pathScope).filter {
            it.name == bare || it.name.equals(bare, ignoreCase = true)
        }.sortedWith(compareByDescending<Symbol> { kindRank(it.kind) }.thenBy { it.file })

        if (defs.isEmpty()) return "No hover info for `$name` — symbol not found in index."

        val primary = defs.first()
        // Pull a few lines of context around definition
        val ctx = runCatching {
            val f = File(projectDir, primary.file)
            val lines = f.readLines()
            val from = (primary.line - 1).coerceAtLeast(0)
            val to = (primary.line + 4).coerceAtMost(lines.size)
            lines.subList(from, to).mapIndexed { i, l ->
                "${from + i + 1}: $l"
            }.joinToString("\n")
        }.getOrDefault(primary.signature)

        return buildString {
            append("HOVER `$bare`\n")
            append("  kind: ${primary.kind}\n")
            if (primary.container.isNotBlank()) append("  container: ${primary.container}\n")
            append("  defined: ${primary.file}:${primary.line}\n")
            if (primary.signature.isNotBlank()) append("  signature: ${primary.signature}\n")
            if (primary.doc.isNotBlank()) append("  doc: ${primary.doc.take(400)}\n")
            if (defs.size > 1) {
                append("  other definitions: ${defs.size - 1}\n")
                defs.drop(1).take(5).forEach {
                    append("    - ${it.kind} @ ${it.file}:${it.line}\n")
                }
            }
            append("\n--- context ---\n").append(ctx)
        }.trimEnd()
    }

    // ── Index / extraction ────────────────────────────────────────────────────

    private fun buildIndex(projectDir: String, pathScope: String): List<Symbol> {
        val out = ArrayList<Symbol>(512)
        val files = ProjectManager.listTextFiles(projectDir, pathScope)
        for (rel in files) {
            val f = File(projectDir, rel)
            if (!ProjectManager.isInsideProject(projectDir, f)) continue
            if (f.length() > 800_000) continue
            val text = runCatching { f.readText() }.getOrNull() ?: continue
            out.addAll(extractSymbolsFromFile(rel, text))
            if (out.size > 8_000) break // hard cap
        }
        return out
    }

    private fun extractSymbolsFromFile(rel: String, text: String): List<Symbol> {
        val ext = rel.substringAfterLast('.').lowercase()
        val lines = text.lines()
        val out = mutableListOf<Symbol>()
        var container = ""
        var containerIndent = -1

        fun docAbove(lineIdx: Int): String {
            if (lineIdx <= 0) return ""
            val buf = mutableListOf<String>()
            var i = lineIdx - 1
            while (i >= 0) {
                val t = lines[i].trim()
                if (t.isEmpty()) { if (buf.isNotEmpty()) break; i--; continue }
                if (t.startsWith("//") || t.startsWith("#") || t.startsWith("*") ||
                    t.startsWith("/*") || t.startsWith("'''") || t.startsWith("\"\"\"") ||
                    t.startsWith("///") || t.startsWith("/**")
                ) {
                    buf.add(0, t.trimStart('/', '*', ' ', '#'))
                    i--
                } else break
            }
            return buf.joinToString(" ").trim().take(300)
        }

        // Patterns: group1 = kind-ish keyword context, capture name
        data class Pat(val kind: String, val regex: Regex, val nameGroup: Int = 1)

        val pats: List<Pat> = when (ext) {
            "kt", "kts" -> listOf(
                Pat("class", Regex("""^\s*(?:(?:public|private|internal|protected|open|abstract|sealed|data|inner|enum|annotation|value|fun)\s+)*class\s+([A-Za-z_][\w]*)""")),
                Pat("interface", Regex("""^\s*(?:(?:public|private|internal|protected|fun)\s+)*interface\s+([A-Za-z_][\w]*)""")),
                Pat("object", Regex("""^\s*(?:(?:public|private|internal|protected)\s+)*object\s+([A-Za-z_][\w]*)""")),
                Pat("enum", Regex("""^\s*(?:(?:public|private|internal|protected)\s+)*enum\s+class\s+([A-Za-z_][\w]*)""")),
                Pat("function", Regex("""^\s*(?:(?:public|private|internal|protected|open|abstract|override|suspend|inline|operator|infix|tailrec|external|actual|expect)\s+)*fun\s+(?:(?:[A-Za-z_][\w.]*\.)|`[^`]+`\.)?([A-Za-z_][\w]*|`[^`]+`)\s*[<(]""")),
                Pat("property", Regex("""^\s*(?:(?:public|private|internal|protected|const|lateinit|override)\s+)*(?:val|var)\s+([A-Za-z_][\w]*)\s*[:=]""")),
                Pat("type", Regex("""^\s*typealias\s+([A-Za-z_][\w]*)"""))
            )
            "java" -> listOf(
                Pat("class", Regex("""^\s*(?:(?:public|private|protected|static|final|abstract|sealed)\s+)*class\s+([A-Za-z_][\w]*)""")),
                Pat("interface", Regex("""^\s*(?:(?:public|private|protected)\s+)*interface\s+([A-Za-z_][\w]*)""")),
                Pat("enum", Regex("""^\s*(?:(?:public|private|protected)\s+)*enum\s+([A-Za-z_][\w]*)""")),
                Pat("method", Regex("""^\s*(?:(?:public|private|protected|static|final|synchronized|abstract|default|native)\s+)+(?:[\w.<>,\[\]?]+\s+)+([A-Za-z_][\w]*)\s*\(""")),
                Pat("field", Regex("""^\s*(?:(?:public|private|protected|static|final|volatile|transient)\s+)+(?:[\w.<>,\[\]]+)\s+([A-Za-z_][\w]*)\s*[=;]"""))
            )
            "ts", "tsx", "js", "jsx", "mjs", "cjs" -> listOf(
                Pat("class", Regex("""^\s*(?:export\s+)?(?:default\s+)?(?:abstract\s+)?class\s+([A-Za-z_$][\w$]*)""")),
                Pat("interface", Regex("""^\s*(?:export\s+)?interface\s+([A-Za-z_$][\w$]*)""")),
                Pat("type", Regex("""^\s*(?:export\s+)?type\s+([A-Za-z_$][\w$]*)\s*=""")),
                Pat("enum", Regex("""^\s*(?:export\s+)?enum\s+([A-Za-z_$][\w$]*)""")),
                Pat("function", Regex("""^\s*(?:export\s+)?(?:async\s+)?function\s*\*?\s*([A-Za-z_$][\w$]*)\s*\(""")),
                Pat("function", Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*=\s*(?:async\s*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)\s*=>""")),
                Pat("method", Regex("""^\s*(?:(?:public|private|protected|static|async|get|set)\s+)*([A-Za-z_$][\w$]*)\s*\([^;]*\)\s*[:{]""")),
                Pat("variable", Regex("""^\s*(?:export\s+)?(?:const|let|var)\s+([A-Za-z_$][\w$]*)\s*[=:]"""))
            )
            "py" -> listOf(
                Pat("class", Regex("""^\s*class\s+([A-Za-z_][\w]*)\s*[:(]""")),
                Pat("function", Regex("""^\s*(?:async\s+)?def\s+([A-Za-z_][\w]*)\s*\("""))
            )
            "go" -> listOf(
                Pat("function", Regex("""^func\s+(?:\([^)]+\)\s+)?([A-Za-z_][\w]*)\s*\(""")),
                Pat("type", Regex("""^type\s+([A-Za-z_][\w]*)\s+(?:struct|interface|func|[A-Za-z_])""")),
                Pat("variable", Regex("""^(?:var|const)\s+([A-Za-z_][\w]*)\s"""))
            )
            "rs" -> listOf(
                Pat("function", Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?(?:async\s+)?(?:const\s+)?(?:unsafe\s+)?fn\s+([A-Za-z_][\w]*)\s*[<(]""")),
                Pat("struct", Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?struct\s+([A-Za-z_][\w]*)""")),
                Pat("enum", Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?enum\s+([A-Za-z_][\w]*)""")),
                Pat("trait", Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?trait\s+([A-Za-z_][\w]*)""")),
                Pat("type", Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?type\s+([A-Za-z_][\w]*)""")),
                Pat("variable", Regex("""^\s*(?:pub(?:\([^)]*\))?\s+)?(?:static|const)\s+(?:mut\s+)?([A-Za-z_][\w]*)"""))
            )
            "swift" -> listOf(
                Pat("class", Regex("""^\s*(?:open\s+|public\s+|private\s+|fileprivate\s+|internal\s+)*(?:final\s+)?class\s+([A-Za-z_][\w]*)""")),
                Pat("struct", Regex("""^\s*(?:public\s+|private\s+|fileprivate\s+|internal\s+)*struct\s+([A-Za-z_][\w]*)""")),
                Pat("enum", Regex("""^\s*(?:public\s+|private\s+|fileprivate\s+|internal\s+)*enum\s+([A-Za-z_][\w]*)""")),
                Pat("protocol", Regex("""^\s*(?:public\s+|private\s+|fileprivate\s+|internal\s+)*protocol\s+([A-Za-z_][\w]*)""")),
                Pat("function", Regex("""^\s*(?:public\s+|private\s+|fileprivate\s+|internal\s+|open\s+)*(?:override\s+|static\s+|class\s+)*func\s+([A-Za-z_][\w]*)\s*\(""")),
                Pat("variable", Regex("""^\s*(?:public\s+|private\s+|fileprivate\s+|internal\s+)*(?:static\s+)?(?:let|var)\s+([A-Za-z_][\w]*)\s*[:=]"""))
            )
            "rb" -> listOf(
                Pat("class", Regex("""^\s*class\s+([A-Za-z_][\w:]*)""")),
                Pat("module", Regex("""^\s*module\s+([A-Za-z_][\w:]*)""")),
                Pat("function", Regex("""^\s*def\s+(?:self\.)?([A-Za-z_][\w?!]*)"""))
            )
            "php" -> listOf(
                Pat("class", Regex("""^\s*(?:abstract\s+|final\s+)?class\s+([A-Za-z_][\w]*)""")),
                Pat("interface", Regex("""^\s*interface\s+([A-Za-z_][\w]*)""")),
                Pat("function", Regex("""^\s*(?:public\s+|private\s+|protected\s+|static\s+)*function\s+([A-Za-z_][\w]*)\s*\(""")),
                Pat("function", Regex("""^\s*function\s+([A-Za-z_][\w]*)\s*\("""))
            )
            "c", "h", "cpp", "cc", "cxx", "hpp", "cs" -> listOf(
                Pat("class", Regex("""^\s*(?:public\s+|private\s+|protected\s+|internal\s+|static\s+)*class\s+([A-Za-z_][\w]*)""")),
                Pat("struct", Regex("""^\s*(?:typedef\s+)?struct\s+([A-Za-z_][\w]*)""")),
                Pat("function", Regex("""^\s*(?:static\s+|inline\s+|extern\s+|public\s+|private\s+|protected\s+|virtual\s+|async\s+)*(?:[\w:<>\*\&\s]+)\s+([A-Za-z_][\w]*)\s*\([^;]*\)\s*\{?""")),
                Pat("enum", Regex("""^\s*(?:typedef\s+)?enum\s+(?:class\s+)?([A-Za-z_][\w]*)"""))
            )
            else -> listOf(
                Pat("function", Regex("""^\s*(?:export\s+)?(?:async\s+)?(?:function|fun|def|fn|func)\s+([A-Za-z_][\w]*)""")),
                Pat("class", Regex("""^\s*(?:export\s+)?(?:class|interface|struct|trait|object|enum)\s+([A-Za-z_][\w]*)"""))
            )
        }

        lines.forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            // Track simple container from class/object/interface lines
            for (p in pats.filter { it.kind in setOf("class", "interface", "object", "struct", "enum", "trait", "protocol", "module", "type") }) {
                val m = p.regex.find(line) ?: continue
                val n = m.groupValues[p.nameGroup].trim('`')
                if (n.isBlank() || n in KEYWORDS) continue
                container = n
                containerIndent = line.length - line.trimStart().length
                out.add(
                    Symbol(
                        name = n,
                        kind = p.kind,
                        file = rel,
                        line = idx + 1,
                        signature = trimmed.take(160),
                        container = "",
                        doc = docAbove(idx)
                    )
                )
            }
            for (p in pats.filter { it.kind !in setOf("class", "interface", "object", "struct", "enum", "trait", "protocol", "module", "type") }) {
                val m = p.regex.find(line) ?: continue
                val n = m.groupValues[p.nameGroup].trim('`')
                if (n.isBlank() || n in KEYWORDS) continue
                // Reset container if indent went back out
                val indent = line.length - line.trimStart().length
                val cont = if (containerIndent >= 0 && indent > containerIndent) container else ""
                if (indent <= containerIndent && container.isNotBlank() &&
                    p.kind in setOf("function", "method", "property", "variable", "field")
                ) {
                    // still allow nested; keep last container if deeper, clear if shallower type line already handled
                }
                out.add(
                    Symbol(
                        name = n,
                        kind = p.kind,
                        file = rel,
                        line = idx + 1,
                        signature = trimmed.take(160),
                        container = cont,
                        doc = docAbove(idx)
                    )
                )
            }
        }
        return out.distinctBy { "${it.kind}:${it.name}:${it.line}:${it.file}" }
    }

    private fun formatDefs(query: String, defs: List<Symbol>, fuzzyNote: String = ""): String {
        return buildString {
            append("GO_TO_DEFINITION `$query`$fuzzyNote — ${defs.size} result(s):\n")
            for (d in defs) {
                append("  [${d.kind}] ")
                if (d.container.isNotBlank()) append("${d.container}.")
                append("${d.name}  @ ${d.file}:${d.line}\n")
                append("      ${d.signature.take(140)}\n")
                if (d.doc.isNotBlank()) append("      // ${d.doc.take(120)}\n")
            }
            append("\nTip: use READ_FILE / READ_LINES on the path above, or FIND_REFERENCES for usages.")
        }
    }

    private fun kindRank(kind: String): Int = when (kind) {
        "class", "interface", "object", "struct", "trait", "protocol", "enum", "module" -> 50
        "type" -> 40
        "function", "method" -> 30
        "property", "field", "variable" -> 10
        else -> 5
    }

    private val KEYWORDS = setOf(
        "if", "else", "for", "while", "when", "switch", "case", "return", "break", "continue",
        "try", "catch", "finally", "throw", "new", "this", "super", "null", "true", "false",
        "in", "is", "as", "of", "from", "import", "export", "package", "typealias", "typeof",
        "void", "var", "let", "const", "val", "fun", "function", "class", "interface", "object",
        "public", "private", "protected", "internal", "static", "final", "open", "abstract",
        "override", "suspend", "async", "await", "yield", "get", "set", "init", "constructor",
        "where", "with", "do", "done", "then", "elif", "except", "raise", "pass", "lambda",
        "self", "cls", "None", "True", "False", "fn", "mut", "pub", "impl", "use", "mod",
        "func", "struct", "enum", "trait", "type", "package", "map", "chan", "go", "defer",
        "select", "range", "match", "loop", "move", "ref", "dyn", "box", "unsafe"
    )
}
