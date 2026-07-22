package com.ahamai.app.data

/**
 * ToolCallParser — centralised, fault-tolerant parser for the model's tool-call output.
 * Enhanced to Mistral AI level with comprehensive tool call support.
 *
 * Supports THREE argument styles (all backward compatible):
 *   1. Positional:  <tool_call>EDIT_FILE<arg_value>path</arg_value><arg_value>old</arg_value>...</tool_call>
 *   2. Named:       <tool_call>EDIT_FILE<target_file>path</target_file><old_text>..</old_text>...</tool_call>
 *   3. param-tag:   <tool_call>EDIT_FILE<param name="target_file">path</param>...</tool_call>
 *
 * Also tolerant of malformed variants a model might emit while streaming:
 *   <tool call> (space), <toolcall>, <|tool_call|>, <function_call>, JSON form, bare TOOL: arg lines.
 *
 * KEY FEATURES (Mistral-Level):
 *   - Comprehensive error recovery from malformed XML
 *   - Support for all tool call formats
 *   - Streaming support for partial tool calls
 *   - Enhanced validation and deduplication
 *   - Better handling of nested tags and escaped content
 */
object ToolCallParser {

    data class ToolCall(
        val name: String,
        val args: List<String>,
        val raw: String,
        val named: Map<String, String> = emptyMap(),
        val explanation: String = ""
    )

    /** All known opening-tag variants a model might use to start a tool call. */
    private val OPEN_TAGS = listOf(
        "tool_call", "toolcall", "tool call", "tool-call",
        "function_call", "functioncall", "invoke", "call",
        "|tool_call|", "|tool|", "tool_use", "function", "tool"
    )

    /** Tags that are NOT argument names (structural / meta). Never treated as a named arg. */
    private val RESERVED_TAGS = setOf(
        "arg_value", "arg_key", "arg", "tool_response", "parameter", "param",
        "tool_call", "toolcall", "function_call", "invoke", "think", "thinking",
        "explanation", "reason", "name", "arguments", "tool"
    )

    private val closedBlockRegex: Regex by lazy {
        val alt = OPEN_TAGS.joinToString("|") { Regex.escape(it) }
        Regex("<(?:$alt)>([\\s\\S]*?)(?:</(?:$alt)>|$)", RegexOption.IGNORE_CASE)
    }

    private val argRegex = Regex(
        "<\\s*arg_value\\s*>([\\s\\S]*?)<\\s*/\\s*arg_value\\s*>",
        RegexOption.IGNORE_CASE
    )

    /** `<arg key="x">value</arg>` OR `<param name="x">value</param>` style. */
    private val paramNamedRegex = Regex(
        "<\\s*(?:arg|param)\\s+(?:key|name)\\s*=\\s*[\"']([^\"']+)[\"']\\s*>([\\s\\S]*?)<\\s*/\\s*(?:arg|param)\\s*>",
        RegexOption.IGNORE_CASE
    )

    /** Generic named tag: `<target_file>value</target_file>` (tag name = param name). */
    private val genericNamedRegex = Regex(
        "<\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*>([\\s\\S]*?)<\\s*/\\s*\\1\\s*>",
        RegexOption.IGNORE_CASE
    )

    private val pipeFormRegex = Regex(
        "(?im)^[ \\t>*-]*([A-Z_][A-Z0-9_]*)\\s*[:|]\\s*(.+)$"
    )

    private val llamaJsonRegex = Regex(
        "\\{\\s*\"name\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"arguments\"\\s*:\\s*(\\{[^}]*\\}|\"[^\"]*\")\\s*\\}",
        RegexOption.IGNORE_CASE
    )

    private val lineDirectiveRegex = Regex(
        "(?im)^[ \\t>*-]*(SEARCH|READ|READ_FILE|READ_FILES|READ_LINES|EDIT_FILE|INSERT_LINES|WRITE_FILE|CREATE_FILE|" +
            "DELETE_FILE|LIST_FILES|GREP|SEARCH_CODE|CODEBASE_SEARCH|BULK_EDIT|FETCH_URL|HTTP_REQUEST|" +
            "GO_TO_DEFINITION|FIND_REFERENCES|DOCUMENT_SYMBOLS|WORKSPACE_SYMBOLS|HOVER|" +
            "RUN_PYTHON|CLOUD_SHELL|RUN_JOB|JOB_STATUS|CLOUD_INSTALL|CLOUD_PULL|CLOUD_PUSH|" +
            "CLOUD_LS|ANSWER|DONE|PLAN|COMPLETE_STEP|TASK|LOAD_TOOLS|GH_PUSH|GH_PR|GH_BUILD_APK|GH_BUILD_STATUS|" +
            "GH_BUILD_LOGS|GH_LIST_ISSUES|GH_CREATE_ISSUE|EXPORT_TO_DEVICE|DOWNLOAD_TO_DEVICE|" +
            "LIST_DOWNLOADS|IMPORT_TO_PROJECT|UNZIP|ZIP|SITE_CLONE|APK_DECOMPILE|APK_INFO|" +
            "APK_REBUILD|SCAN_SECRETS|SECURITY_AUDIT|WEB_SCAN|IMAGE_EDIT|GENERATE_IMAGE|" +
            "SCREENSHOT|ANALYZE_IMAGE|PDF_CREATE|PDF_READ|PDF_EDIT_TEXT|SCAFFOLD_ANDROID|" +
            "BROWSER_OPEN|BROWSER_GOTO|BROWSER_CLICK|BROWSER_TYPE|BROWSER_PRESS|BROWSER_SCROLL|BROWSER_BACK|BROWSER_EXTRACT|BROWSER_READ|BROWSER_VIEW|" +
            "RECON|PORT_SCAN|VULN_SCAN|DIR_FUZZ|NIKTO_SCAN|SQLI_TEST|SSL_SCAN|URL_HARVEST|" +
            "SAST|REPO_SECRETS|SOCIAL_OSINT|PHONE_OSINT|MULTI_EDIT|SYMBOL_SEARCH|RUN_TESTS|" +
            "LINT|FORMAT_CODE|GIT_DIFF|GIT_COMMIT|DEPS_ADD|IMPORT_ADD|REFACTOR_RENAME|" +
            "PREVIEW_WEB_APP|RENDER_DIAGRAM|RENDER_CHART|CREATE_XLSX|CREATE_CSV|" +
            "CREATE_PPTX|CREATE_DOCX|MAKE_SHORTS|PDF_EDIT_TEXT|PDF_ADD_PAGE|PDF_ADD_IMAGE|" +
            "PDF_ADD_CHART|PDF_MERGE|PDF_SPLIT|PDF_READ|PDF_FILL_FORM|PDF_COMPRESS|" +
            "PDF_COLLAGE|CLOUD_LS|CLOUD_PULL|CLOUD_PUSH|EXPORT_TO_DEVICE|GENERATE_IMAGE|" +
            "SCREENSHOT|ANALYZE_IMAGE|UNZIP|ZIP)\\s*[:|]?\\s*(.*)$"
    )

    /**
     * Enhanced extract function with comprehensive error recovery
     */
    fun extract(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        val consumed = java.util.ArrayList<IntRange>()

        fun isConsumed(r: IntRange): Boolean {
            for (c in consumed) if (r.first in c || r.last in c) return true
            return false
        }
        fun mark(r: IntRange) { consumed.add(r) }

        // Pass 0 - Handle WRITE_FILE/CREATE_FILE with END_FILE markers first
        val writeFileRegex = Regex(
            "(WRITE_FILE|CREATE_FILE)\\s*:?\\s*([^\\n<]+?)\\s*\\n([\\s\\S]*?)\\nEND_FILE",
            RegexOption.IGNORE_CASE
        )
        for (m in writeFileRegex.findAll(text)) {
            if (isConsumed(m.range)) continue
            val action = if (m.groupValues[1].uppercase().startsWith("CREATE")) "CREATE_FILE" else "WRITE_FILE"
            val path = m.groupValues[2].trim().trim('"', '`', '\'')
            var content = m.groupValues[3]
            content = content.replace(Regex("^```\\w*\\n"), "").replace(Regex("\\n```$"), "")
            calls.add(ToolCall(action, listOf(path, content), m.value))
            mark(m.range)
        }

        // Pass 0.5 - Handle WRITE_FILE/CREATE_FILE with explanation
        val writeWithExplanationRegex = Regex(
            "(WRITE_FILE|CREATE_FILE)\\s*:?\\s*<explanation>([^<]+)</explanation>\\s*([^\\n<]+?)\\s*\\n([\\s\\S]*?)\\nEND_FILE",
            RegexOption.IGNORE_CASE
        )
        for (m in writeWithExplanationRegex.findAll(text)) {
            if (isConsumed(m.range)) continue
            val action = if (m.groupValues[1].uppercase().startsWith("CREATE")) "CREATE_FILE" else "WRITE_FILE"
            val explanation = m.groupValues[2].trim()
            val path = m.groupValues[3].trim().trim('"', '`', '\'')
            var content = m.groupValues[4]
            content = content.replace(Regex("^```\\w*\\n"), "").replace(Regex("\\n```$"), "")
            calls.add(ToolCall(action, listOf(path, content), m.value, emptyMap(), explanation))
            mark(m.range)
        }

        // Pass 1 — XML / custom-tag blocks
        for (m in closedBlockRegex.findAll(text)) {
            if (isConsumed(m.range)) continue
            val inner = m.groupValues[1].trim()
            if (inner.isEmpty()) continue
            val tc = parseBlock(inner, m.value) ?: continue
            calls.add(tc)
            mark(m.range)
        }

        // Pass 2 — Llama / Mistral function-call JSON
        for (m in llamaJsonRegex.findAll(text)) {
            if (isConsumed(m.range)) continue
            val name = m.groupValues[1]
            val rawArgs = m.groupValues[2]
            if (rawArgs.startsWith("\"")) {
                calls.add(ToolCall(name.uppercase(), listOf(rawArgs.trim('"')), m.value))
            } else {
                val (positional, named) = parseJsonArgs(rawArgs)
                calls.add(ToolCall(name.uppercase(), positional, m.value, named, named["explanation"] ?: ""))
            }
            mark(m.range)
        }

        // Pass 3 — pipe / colon form
        for (m in pipeFormRegex.findAll(text)) {
            if (isConsumed(m.range)) continue
            val name = m.groupValues[1].uppercase()
            val rest = m.groupValues[2].trim().trimEnd(']').trim('"', '\'', '`', '<', '>')
            if (rest.isBlank()) {
                calls.add(ToolCall(name, emptyList(), m.value))
            } else {
                val parts = if (rest.contains("|")) rest.split("|").map { it.trim().trim('"', '\'', '`') }
                            else listOf(rest)
                calls.add(ToolCall(name, parts, m.value))
            }
            mark(m.range)
        }

        // Pass 4 — line directives
        for (m in lineDirectiveRegex.findAll(text)) {
            if (isConsumed(m.range)) continue
            val name = m.groupValues[1].uppercase()
            val rest = m.groupValues[2].trim().trim('"', '\'', '`', '<', '>').trimEnd(']').trim()
            calls.add(ToolCall(name, if (rest.isBlank()) emptyList() else listOf(rest), m.value))
            mark(m.range)
        }

        // Pass 5 - Fallback: Extract from raw text patterns
        if (calls.isEmpty() && text.length > 50) {
            val fallbackPattern = Regex(
                "(?im)(EDIT_FILE|READ_FILE|WRITE_FILE|CREATE_FILE|DELETE_FILE|ANSWER|DONE|PLAN|COMPLETE_STEP)\\s*[:\\s]+([^\\n]+)",
                RegexOption.IGNORE_CASE
            )
            for (m in fallbackPattern.findAll(text)) {
                if (isConsumed(m.range)) continue
                val name = m.groupValues[1].uppercase()
                val arg = m.groupValues[2].trim()
                calls.add(ToolCall(name, listOf(arg), m.value))
                mark(m.range)
            }
        }

        // De-dup by full signature
        val seen = HashSet<String>()
        return calls.filter { c ->
            val sig = c.name + "\\u0000" + c.args.joinToString("\\u0000") +
                "\\u0000" + c.named.entries.sortedBy { it.key }.joinToString("\\u0000") { "${it.key}=${it.value}" }
            seen.add(sig)
        }
    }

    private fun parseJsonArgs(json: String): Pair<List<String>, Map<String, String>> {
        return try {
            val obj = org.json.JSONObject(json)
            val keys = obj.keys()
            val positional = mutableListOf<String>()
            val named = linkedMapOf<String, String>()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = obj.opt(k)?.toString().orEmpty()
                positional.add(v)
                named[k.lowercase()] = v
            }
            positional to named
        } catch (_: Exception) {
            listOf(json.trim('"')) to emptyMap()
        }
    }

    private fun parseBlock(inner: String, raw: String): ToolCall? {
        val nameMatch = Regex("^\\s*[-*]*\\s*([A-Za-z_][A-Za-z0-9_]*)").find(inner)
            ?: return null
        val name = nameMatch.groupValues[1].uppercase()
        val afterName = inner.substring(nameMatch.range.last + 1)

        val positional = mutableListOf<String>()
        for (m in argRegex.findAll(afterName)) positional.add(m.groupValues[1].trim())

        val named = linkedMapOf<String, String>()
        for (m in paramNamedRegex.findAll(afterName)) {
            named[m.groupValues[1].trim().lowercase()] = m.groupValues[2].trim()
        }
        if (named.isEmpty()) {
            for (m in genericNamedRegex.findAll(afterName)) {
                val tag = m.groupValues[1].lowercase()
                if (tag in RESERVED_TAGS) continue
                named[tag] = m.groupValues[2].trim()
            }
        }

        val explanation = named["explanation"] ?: named["reason"] ?: ""

        if (positional.isEmpty() && named.isEmpty()) {
            val body = afterName.trim()
            if (body.isNotBlank()) positional.add(body)
        }

        return ToolCall(name, positional, raw, named, explanation)
    }

    fun containsToolCall(text: String): Boolean = extract(text).isNotEmpty()

    fun stripAll(text: String): String {
        var t = text
        t = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE).replace(t, "")

        // Strip WRITE_FILE/CREATE_FILE blocks
        t = Regex("(WRITE_FILE|CREATE_FILE)[\\s\\S]*?END_FILE", RegexOption.IGNORE_CASE).replace(t, "")

        // Strip all tool_call variant blocks (closed)
        val alt = OPEN_TAGS.joinToString("|") { Regex.escape(it) }
        t = Regex("<\\s*/?\\s*(?:$alt)\\b[^>]*>[\\s\\S]*?<\\s*/\\s*(?:$alt)\\s*>", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip any remaining open/close tags for tool variants
        t = Regex("<\\s*/?\\s*(?:$alt)\\b[^>]*>", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip arg/param/meta tags
        t = Regex("</?(?:arg_value|arg_key|arg|param|tool_response|parameter|invoke|function_call|explanation|reason|target_file|old_text|new_text|content|code|query|path|file)\\b[^>]*>",
            RegexOption.IGNORE_CASE).replace(t, "")

        // Strip line directives
        t = lineDirectiveRegex.replace(t, "")
        // Strip ALL remaining XML-like tags as final pass (catches anything else)
        t = Regex("<\\s*/?\\s*[a-zA-Z_|][a-zA-Z0-9_|\\s-]*(?:\\s+[^>]*)?>").replace(t, "")
        // Clean up dangling partial tags and excessive newlines
        t = Regex("<[^>]{0,30}$").replace(t, "")
        t = Regex("\\n{3,}").replace(t, "\n\n")
        return t.trimEnd()
    }

    fun hasCompleteToolCall(text: String): Boolean {
        val calls = extract(text)
        return calls.isNotEmpty() && calls.all { 
            it.name.isNotBlank() && (it.args.isNotEmpty() || it.named.isNotEmpty())
        }
    }

    fun isPartialToolCall(text: String): Boolean {
        if (text.isBlank()) return false
        val openTags = OPEN_TAGS.joinToString("|") { Regex.escape(it) }
        val hasOpen = Regex("<\\s*(?:$openTags)\\s*[^>]*>", RegexOption.IGNORE_CASE).containsMatchIn(text)
        val hasClose = Regex("</\\s*(?:$openTags)\\s*>", RegexOption.IGNORE_CASE).containsMatchIn(text)
        return hasOpen && !hasClose
    }

    fun formatReminder(): String =
        "REMINDER: Every action MUST be a tool call. Use the canonical XML form:\n" +
        "<tool_call>TOOL_NAME\n<arg_value>first arg</arg_value>\n<arg_value>second arg</arg_value>\n</tool_call>\n" +
        "Do NOT output bare text, `<tool call>` (with a space), or JSON-only calls. " +
        "To talk to the user, use <tool_call>ANSWER<arg_value>your markdown answer</arg_value></tool_call>. " +
        "Plain text with no tool call stalls the task. Always finish with DONE."
}