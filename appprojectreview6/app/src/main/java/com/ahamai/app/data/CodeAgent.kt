package com.ahamai.app.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import com.ahamai.app.data.RemoteConfigManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Code editing agent that uses an LLM to explore and modify a project.
 * Enhanced to be as powerful as Mistral AI with full tool support.
 */
object CodeAgent {

    data class AgentStep(
        val action: String,
        val path: String = "",
        val arg2: String = "",   // dest / url / old text / method
        val detail: String = "", // content / new text / query / answer / body
        val arg3: String = "",   // extra: http headers, etc.
        val explanation: String = "" // one-line "why" the model gave for this action (Cursor-style)
    )

    /**
     * Agent system prompt — remote first (Admin → Control / Firestore systemPrompts.agent),
     * else bundled assets/agent_system_prompt.txt so tools (GH_*, RUN_APP, artifacts) stay documented.
     */
    @Volatile private var cachedAssetPrompt: String? = null

    fun systemPrompt(): String {
        val remote = RemoteConfigManager.agentSystemPrompt.trim()
        if (remote.isNotBlank()) return remote
        val asset = cachedAssetPrompt?.trim().orEmpty()
        return asset // may be blank only if warmAssetPrompt not called yet / asset missing
    }

    /** Call from MainActivity once so asset fallback is warm without Context on every turn. */
    fun warmAssetPrompt(context: android.content.Context) {
        if (cachedAssetPrompt == null) {
            cachedAssetPrompt = try {
                context.assets.open("agent_system_prompt.txt").bufferedReader().use { it.readText() }
            } catch (_: Exception) { "" }
        }
        // If remote is empty, seed RemoteConfig so admin UI / sub-agents see the bundled prompt.
        if (RemoteConfigManager.agentSystemPrompt.isBlank() && !cachedAssetPrompt.isNullOrBlank()) {
            RemoteConfigManager.agentSystemPrompt = cachedAssetPrompt!!
        }
    }

    /**
     * Token-aware output truncation: preserves the HEAD (first N lines) and TAIL (last M lines)
     * of a long tool output, with a clear "… (X lines omitted) …" marker in between.
     * Mirrors OpenAI Codex's approach so the model sees the beginning and end of error logs /
     * file listings without overflowing the context window or losing the critical last lines.
     *
     * @param output raw tool output text
     * @param headLines number of lines to keep from the start (default 40)
     * @param tailLines number of lines to keep from the end (default 40)
     * @param minOutputLines minimum total lines before truncation kicks in (default 60)
     * @return truncated string with head + "…" + tail, or the original if shorter than minOutputLines
     */
    fun truncateOutput(output: String, headLines: Int = 40, tailLines: Int = 40, minOutputLines: Int = 60): String {
        val lines = output.split("\n")
        if (lines.size <= minOutputLines) return output
        val head = lines.take(headLines)
        val tail = lines.takeLast(tailLines)
        val omitted = lines.size - headLines - tailLines
        return head.joinToString("\n") +
            "\n… ($omitted lines omitted) …\n" +
            tail.joinToString("\n")
    }

    /**
     * Sub-agent (TASK tool). Runs a focused, bounded nested agent loop with its OWN fresh
     * context for a delegated task, and returns a single consolidated text result. It can
     * read/search/edit via executeStep, but cannot spawn further sub-agents (no recursion).
     */
    suspend fun runSubAgent(
        context: android.content.Context,
        projectDir: String,
        baseUrl: String,
        apiKey: String,
        model: String,
        task: String,
        maxTurns: Int = 8
    ): String = withContext(Dispatchers.IO) {
        BigChangePolicy.enterScope()
        val turns = if (BigChangePolicy.isActive()) maxOf(maxTurns, 20) else maxTurns
        val tree = runCatching { ProjectManager.buildTreeString(projectDir) }.getOrDefault("(tree unavailable)")
        val subSystem = systemPrompt() + "\n\nYOU ARE A SUB-AGENT. You were spawned to handle ONE focused task " +
            "autonomously and report back. Do the work with tools, then finish with a SINGLE " +
            "<tool_call>ANSWER<arg_value>concise findings / what you did</arg_value></tool_call> followed by " +
            "<tool_call>DONE</tool_call>. Do NOT use the TASK tool (no nested sub-agents). Be efficient — you have a limited turn budget." +
            BigChangePolicy.promptAddon()
        val convo = mutableListOf<Pair<String, String>>()
        convo.add("system" to subSystem)
        convo.add("user" to "PROJECT FILE TREE:\n$tree\n\nSUB-TASK: $task")

        val collected = StringBuilder()
        var answer = ""
        var turn = 0
        while (turn < turns) {
            turn++
            val resp = ApiClient.streamAgentResponse(baseUrl, apiKey, model, convo) {}
            val raw = resp.getOrNull() ?: break
            convo.add("assistant" to raw)
            val steps = parseActions(raw)
            if (steps.isEmpty()) { answer = ToolCallParser.stripAll(raw).trim(); break }
            val results = StringBuilder()
            var finished = false
            // Control steps first. Run READ-ONLY tools in PARALLEL; mutations stay ordered
            // so same-file edits don't race.
            val readOnly = setOf(
                "read", "readlines", "readfiles", "grep", "search", "glob", "list",
                "codebasesearch", "symbolsearch", "gotodefinition", "findreferences",
                "documentsymbols", "workspacesymbols", "hover", "fetch", "http",
                "websearch", "imagesearch", "readurl", "repomap", "diffhistory", "memory"
            )
            val reads = mutableListOf<AgentStep>()
            val mutates = mutableListOf<AgentStep>()
            for (s in steps) {
                when (s.action) {
                    "answer" -> { answer = s.detail; finished = true }
                    "done" -> { finished = true }
                    "task" -> results.append("[TASK] Nested sub-agents are not allowed.\n")
                    "plan", "completestep" -> { /* ignore planning inside sub-agent */ }
                    else -> if (s.action in readOnly) reads.add(s) else mutates.add(s)
                }
            }
            if (reads.isNotEmpty() && !finished) {
                val outs = coroutineScope {
                    reads.map { s ->
                        async(Dispatchers.IO) {
                            val r = runCatching { executeStep(context, projectDir, s) }
                                .getOrElse { "ERROR: ${it.message}" }
                            "[${s.action.uppercase()} ${s.path}]\n${truncateOutput(r, headLines = 30, tailLines = 20, minOutputLines = 40)}\n\n"
                        }
                    }.map { it.await() }
                }
                outs.forEach { results.append(it) }
            }
            if (mutates.isNotEmpty() && !finished) {
                for (s in mutates) {
                    val r = runCatching { executeStep(context, projectDir, s) }
                        .getOrElse { "ERROR: ${it.message}" }
                    results.append("[${s.action.uppercase()} ${s.path}]\n${truncateOutput(r, headLines = 30, tailLines = 20, minOutputLines = 40)}\n\n")
                }
            }
            if (finished) break
            convo.add("user" to (results.toString().ifBlank { "(no output) Continue or finish with ANSWER then DONE." }))
            if (results.isNotBlank()) collected.append(results)
        }
        when {
            answer.isNotBlank() -> "SUB-AGENT result:\n$answer"
            collected.isNotBlank() -> "SUB-AGENT gathered (no explicit answer):\n${collected.toString().take(2000)}"
            else -> "SUB-AGENT finished with no result."
        }
    }


    // ===== Additive guidance for the next-level upgrade (named params, new tools, lazy loading) =====
    // ===== On-demand tool category docs (returned by LOAD_TOOLS) =====
    private const val PDF_TOOLS_DOC = """PDF TOOLS:
PDF_CREATE <output.pdf> <markdown content> <style?> <watermark?> — full styled PDF in one call.
PDF_READ <input.pdf> — extract text. PDF_EDIT_TEXT <pdf> <old> <new> — in-place text edit keeping layout.
PDF_MERGE <a.pdf> <b.pdf> <out.pdf> — combine. PDF_SPLIT <in.pdf> <1-3,5> <out.pdf> — extract pages.
PDF_ADD_PAGE <pdf> <content> · PDF_ADD_IMAGE <pdf> <img> <page> · PDF_ADD_CHART <pdf> <bar|pie|line> <json>
PDF_FILL_FORM <pdf> <json fields> · PDF_COMPRESS <pdf> <low|medium|high> <out.pdf> · PDF_COLLAGE <imgs> <out.pdf> <cols>"""

    private const val OFFICE_TOOLS_DOC = """OFFICE TOOLS (one call each, auto-verified):
CREATE_XLSX <out.xlsx> <json sheets/rows/chart> — real Excel workbook.
CREATE_PPTX <out.pptx> <json slides> — real PowerPoint (bullets/chart/diagram/image slides).
CREATE_DOCX <out.docx> <json sections> — real Word doc (headings/text/bullets/table/image).
CREATE_CSV <out.csv> <json rows> — instant native CSV."""

    private const val SECURITY_TOOLS_DOC = """SECURITY / BUG-BOUNTY (authorized targets only) — NO prebuilt wrappers anymore.
The old prebuilt scanners (RECON/VULN_SCAN/WEB_SCAN/NIKTO/SQLI etc.) were unreliable and are REMOVED.
Instead you have FULL ROOT control of a Debian cloud box — do security work YOURSELF via CLOUD_SHELL:
  • Install whatever you need: CLOUD_SHELL `apt-get install -y nmap nikto whatweb` / `pipx install ...` /
    `go install ...` / `nuclei -update` etc. (you are root, no limits).
  • Run the real tools directly and read their stdout, e.g. one batched call:
    CLOUD_SHELL `subfinder -d TARGET -silent | httpx -silent | nuclei -silent` etc.
  • Chain everything into ONE CLOUD_SHELL command (use && / pipes) to avoid slow round-trips.
  • CLOUD_PULL any report files you generate into the project, or just summarise stdout.
Only test targets the user owns or is explicitly authorized to test."""

    private const val APK_TOOLS_DOC = """APK ANALYSIS / REBUILD — NO prebuilt wrappers. Do it YOURSELF in the cloud box (you are root):
The old APK_DECOMPILE/APK_INFO/SCAN_SECRETS/SECURITY_AUDIT wrappers were unreliable and are REMOVED.
First CLOUD_PUSH the .apk into /workspace, then use CLOUD_SHELL — install + run real tools, batched in one call:
  • Decompile:  `apt-get install -y apktool && apktool d -f app.apk -o out` , or jadx:
                `wget -q <jadx-zip> -O j.zip && unzip -q j.zip && ./jadx*/bin/jadx -d src app.apk`
  • Metadata/permissions/certs: `aapt dump badging app.apk` / `apksigner verify --print-certs app.apk` (Android build-tools are on PATH).
  • Secret scan: `pipx install apkleaks && apkleaks -f app.apk`  (or `grep -rEi "api[_-]?key|secret|token" out`).
  • Static audit: `pipx install mobsfscan && mobsfscan out --json`.
  • Rebuild+sign: `apktool b out -o new.apk && zipalign -p 4 new.apk a.apk && apksigner sign --ks <keystore> a.apk`.
CLOUD_PULL the decompiled tree / report back into the project, then EXPORT_TO_DEVICE the final APK."""

    private const val MEDIA_TOOLS_DOC = """MEDIA TOOLS:
GENERATE_IMAGE <prompt> <out?> <model?> — text→image (Pollinations, free).
IMAGE_EDIT <in> <op> <args> <out> — resize/crop/rotate/filter/text/watermark (Pillow).
SCREENSHOT <url> <out?> — full-page PNG. ANALYZE_IMAGE <img> — OCR + dimensions + colours (free vision).
MAKE_SHORTS <url|video> <json opts> <outdir> — long video → vertical viral shorts w/ karaoke captions."""

    private const val OSINT_TOOLS_DOC = """OSINT / WEB TOOLS (authorized use only):
SOCIAL_OSINT <username> — public profiles across 3000+ sites. PHONE_OSINT <+number> — carrier/region metadata only.
SITE_CLONE <url> <outdir?> <depth?> — download a site's source into the project."""

    private const val CLOUD_TOOLS_DOC = """CLOUD / DEVOPS TOOLS:
CLOUD_LS <path?> · CLOUD_PULL <cloudpath> <dest> · CLOUD_PUSH <projpath> <dest?>
CLOUD_INSTALL <apt|pip|npm|go|gh> <packages> · RUN_JOB <cmd> (background) · JOB_STATUS <id>
DEPS_ADD <npm|pip|cargo|go|maven|gradle> <package> · FORMAT_CODE <path?>"""


    /**
     * Extracts the model's natural-language narration (the prose it writes around its
     * tool calls) so the UI can show "what it's doing" like a running commentary.
     */
    fun extractNarration(text: String): String {
        var t = text
        // Strip reasoning/thinking blocks some models emit inline (keeps narration clean).
        t = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip WRITE_FILE / CREATE_FILE ... END_FILE blocks
        t = Regex("(WRITE_FILE|CREATE_FILE)\\s*:?\\s*([^\\n<]+?)\\s*\\n([\\s\\S]*?)\\nEND_FILE", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip XML tool_call blocks (closed)
        t = Regex("<\\s*/?\\s*tool[_\\s-]*call[^>]*>[\\s\\S]*?<\\s*/\\s*tool[_\\s-]*call[^>]*>", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip an unclosed/trailing tool_call (while streaming or malformed)
        t = Regex("<\\s*/?\\s*tool[_\\s-]*call[^>]*>[\\s\\S]*$", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip ALL XML-like tags aggressively (covers <arg_value>, <target_file>, <old_text>, etc.)
        t = Regex("<\\s*/?\\s*[a-zA-Z_|][a-zA-Z0-9_|\\s-]*(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip stray markers / directives
        t = t.replace(Regex("(?m)^\\s*END_FILE\\s*$"), "")
        t = t.replace(Regex("(?m)^\\s*(DONE|ANSWER)\\s*:?.*$", RegexOption.IGNORE_CASE), "")
        // Final cleanup: dangling angle brackets / partial tags at the end
        t = Regex("<[^>]{0,30}$").replace(t, "")
        t = Regex("^[^<]*>").replace(t, "") // stray closing > at the start
        // NOTE: this used to hard-cut at 800 chars, which chopped narration off mid-sentence —
        // and since livePreview() (below) shows up to 6000 chars WHILE streaming, the text would
        // visibly SHRINK the moment a turn finished. Cap raised to match so the final narration
        // is never shorter than what was already on screen a moment earlier.
        return t.trim().take(6000)
    }

    /**
     * Builds a readable preview from a PARTIAL (still-streaming) response so the UI can show
     * live progress like Cursor. Completed file writes/tool calls collapse away; the model's
     * prose stays; and if it is mid-way through an ANSWER, that answer text is surfaced live.
     */
    fun livePreview(partial: String): String {
        var t = partial
        // Strip reasoning/thinking blocks (closed + trailing open) so they don't show as narration.
        t = Regex("<think>[\\s\\S]*?</think>", RegexOption.IGNORE_CASE).replace(t, "")
        t = Regex("<think>[\\s\\S]*$", RegexOption.IGNORE_CASE).replace(t, "")
        // Collapse finished WRITE_FILE / CREATE_FILE blocks.
        t = Regex("(WRITE_FILE|CREATE_FILE)\\s*:?\\s*([^\\n<]+?)\\s*\\n([\\s\\S]*?)\\nEND_FILE", RegexOption.IGNORE_CASE)
            .replace(t) { "\n(wrote ${it.groupValues[2].trim()})\n" }
        // Collapse finished tool_call blocks (match all variants: tool_call, toolcall, tool call, etc.)
        t = Regex("<\\s*/?\\s*tool[_\\s-]*call[^>]*>[\\s\\S]*?<\\s*/\\s*tool[_\\s-]*call[^>]*>", RegexOption.IGNORE_CASE).replace(t, "")
        // Handle a trailing, still-open tool_call: surface ANSWER text, drop everything else.
        Regex("<\\s*tool[_\\s-]*call[^>]*>\\s*([A-Za-z_]+)([\\s\\S]*)$", RegexOption.IGNORE_CASE).find(t)?.let { m ->
            val tool = m.groupValues[1].uppercase()
            val rest = m.groupValues[2]
            t = t.substring(0, m.range.first)
            if (tool == "ANSWER") {
                val ans = Regex("<\\s*arg_value\\s*>([\\s\\S]*)$", RegexOption.IGNORE_CASE).find(rest)?.groupValues?.get(1) ?: ""
                t += ans
            }
        }
        // Collapse a half-streamed WRITE_FILE that hasn't hit END_FILE yet.
        t = Regex("(WRITE_FILE|CREATE_FILE)\\s*:?\\s*([^\\n<]+?)\\s*\\n[\\s\\S]*$", RegexOption.IGNORE_CASE)
            .replace(t) { "\n(writing ${it.groupValues[2].trim()}...)\n" }
        // Strip ALL remaining XML-like tags (covers arg_value, target_file, old_text, explanation, etc.)
        t = Regex("<\\s*/?\\s*[a-zA-Z_|][a-zA-Z0-9_|\\s-]*(?:\\s+[^>]*)?>", RegexOption.IGNORE_CASE).replace(t, "")
        // Strip stray markers
        t = t.replace(Regex("(?m)^\\s*(END_FILE|DONE)\\s*:?.*$", RegexOption.IGNORE_CASE), "")
        // Final cleanup: dangling partial tags
        t = Regex("<[^>]{0,30}$").replace(t, "")
        // Cap raised from 1200 -> 6000 to match extractNarration's cap (see note there); a low
        // cap here made long narration visibly truncate WHILE the model was still mid-sentence.
        return t.trim().take(6000)
    }

    /**
     * Defensive final pass: removes any remaining open tool/arg tag (and everything after it) plus a
     * dangling partial tag start at the very end — so XML brackets never leak into the visible
     * narration (e.g. "<arg_name", "<tool_cal", a lone trailing "<") while planning or streaming.
     */
    private fun stripStrayToolTags(input: String): String {
        var t = input
        // Cut from the first still-open known tag through the end.
        t = Regex(
            "<\\s*/?\\s*(tool_call|toolcall|arg_value|arg_key|arg_name|old_text|new_text|param|parameter|function_call|invoke|tool_response|target_file)\\b[\\s\\S]*$",
            RegexOption.IGNORE_CASE
        ).replace(t, "")
        // Drop a dangling partial tag start at the very end (e.g. "<too", "</to", or a lone "<").
        t = Regex("(<\\s*/?[a-zA-Z_]+|<)\\s*$").replace(t, "")
        return t
    }

    fun attachmentPrompt(attachments: List<String>): String {
        if (attachments.isEmpty()) return ""
        val lines = StringBuilder()
        var hasArchive = false
        for (att in attachments) {
            val ext = att.substringAfterLast('.', "").lowercase()
            val tool = when (ext) {
                "png", "jpg", "jpeg", "webp", "gif", "bmp" -> "ANALYZE_IMAGE"
                "pdf" -> "PDF_READ"
                "csv", "tsv" -> "READ_FILE"
                "xlsx", "xls", "docx", "doc", "pptx", "ppt" -> "READ_FILE (will describe the file) or export/convert it"
                "zip", "rar", "7z", "tar", "gz" -> { hasArchive = true; "UNZIP (destination = project root, i.e. leave the destination argument BLANK)" }
                else -> "READ_FILE"
            }
            lines.append("- $att  →  use $tool\n")
        }
        val archiveNote = if (hasArchive)
            "\nARCHIVE ATTACHMENT — DO THIS IN ONE STEP, do not overthink it: call UNZIP with destination BLANK so it " +
            "extracts straight into the project root — do NOT invent a subfolder name, do NOT ask which folder to use. " +
            "After that ONE call, LIST_FILES / the next file tree tells you what's inside — that's it, no further " +
            "exploration needed before continuing. If it's an Android/Gradle project (has gradlew / settings.gradle / " +
            "build.gradle ANYWHERE inside, even nested), you do NOT need to move or flatten anything: just proceed to " +
            "edit and call GH_BUILD_APK directly — the cloud build finds the real Gradle root itself, no matter how " +
            "deep it's nested. Never MOVE_FILE the extracted contents around to 'fix' the layout.\n"
        else ""
        return """
ATTACHED FILES (already copied into the project workspace):
$lines
IMPORTANT — you MUST actually access each relevant attachment using the tool listed above:
• Images (png/jpg/webp/gif): use ANALYZE_IMAGE <path> — this sends the image to vision so you can see it.
• PDFs: use PDF_READ <path> — extracts all text content from the PDF.
• Text/code/CSV: use READ_FILE <path> — reads the file contents.
• Office (docx/xlsx/pptx): use READ_FILE <path> — returns a text description of its contents.
• Zip/archive: use UNZIP <path> with a BLANK destination (extracts into project root) — see note below.
Do NOT skip this step or say you "cannot access" the file. The files exist at the paths above.
$archiveNote""".trimIndent() + "\n\n"
    }

    fun buildInitialPrompt(
        treeString: String,
        userTask: String,
        projectRules: String? = null,
        attachments: List<String> = emptyList(),
        buildRoot: String? = null,
        projectDir: String? = null
    ): String {
        val rulesBlock = if (!projectRules.isNullOrBlank())
            "PROJECT RULES & MEMORY (you MUST follow these):\n$projectRules\n\n" else ""
        val now = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        val tz = java.util.TimeZone.getDefault().id
        val dateBlock = "CURRENT DATE & TIME: $now ($tz).\n" +
            "You do NOT have a knowledge cutoff and are connected to LIVE web tools (WEB_SEARCH / READ_URL). " +
            "Never say you don't know today's date/year or claim a training cutoff — always use the date/time above, " +
            "and WEB_SEARCH for anything current.\n\n"
        // Agent Skills (SKILL.md): catalog (level 1) + any session-loaded bodies (level 2)
        val skillBlock = buildString {
            append(SkillManager.buildSkillsCatalog())
            append('\n')
            append(SkillManager.buildSkillInjection())
        }
        // When the user uploads their own source, the real build root is often a SUBFOLDER of
        // /workspace. Tell the agent exactly where to run build/install/test commands so it does
        // not waste turns hunting for it (or run them in the wrong dir and fail).
        val rootBlock = if (!buildRoot.isNullOrBlank())
            "PROJECT / BUILD ROOT: this project's manifest/build files live in the subfolder `$buildRoot` " +
            "(e.g. an uploaded zip whose real project root wasn't the top level). This ONLY matters if you use " +
            "CLOUD_SHELL — there, `cd /workspace/$buildRoot` before running npm/cargo/go commands; local file tools " +
            "still use normal project-relative paths (e.g. `$buildRoot/...`). " +
            "If this is an ANDROID project, IGNORE this for building — do NOT move/copy any files and do NOT cd " +
            "anywhere for GH_BUILD_APK; it pushes the project as-is and the cloud CI finds `$buildRoot` itself " +
            "automatically. Just call GH_BUILD_APK directly.\n\n"
        else ""
        // Grok-style context memory: catalog (goals/facts/hot files/episodes/worklog) — not raw dumps.
        val memoryBlock = if (!projectDir.isNullOrBlank()) {
            ContextMemoryManager.setGoal(projectDir, userTask)
            ContextMemoryManager.initialInjection(projectDir)
        } else ""
        val memSection = if (memoryBlock.isNotBlank()) "$memoryBlock\n" else ""
        val mcpTools = com.ahamai.app.data.ConnectorsManager.buildAllPromptDescriptions()
        val mcpBlock = if (mcpTools.isNotBlank()) "\n\n$mcpTools\n\n" else ""
        // Aider-style REPO MAP: a bounded, ranked map of key declarations so the model grasps the
        // codebase shape up-front without reading every file. Refreshable on demand via REPO_MAP.
        val repoMap = if (!projectDir.isNullOrBlank())
            runCatching { ProjectManager.buildRepoMap(projectDir, maxDecls = 120) }.getOrNull() else null
        val repoMapBlock = if (!repoMap.isNullOrBlank() && !repoMap.startsWith("No ") && !repoMap.startsWith("ERROR"))
            "REPO MAP (key declarations — navigate with this instead of reading every file; re-READ for full bodies):\n$repoMap\n\n"
        else ""
        val timeoutBlock = ToolTimeouts.agentPolicyBlock() + "\n\n"
        val disciplineBlock = AgentDiscipline.efficiencyPolicyBlock() + "\n\n"
        return "${rulesBlock}${dateBlock}${memSection}${skillBlock}${mcpBlock}${rootBlock}${timeoutBlock}${disciplineBlock}" +
                "PROJECT FILE TREE (each line is a FULL project-relative path — copy it exactly for READ_FILE/EDIT_FILE):\n" +
                "$treeString\n\n${repoMapBlock}${attachmentPrompt(attachments)}TASK: $userTask\n\n" +
                "CONTEXT DISCIPLINE: Prefer CONTEXT MEMORY + this tree + REPO MAP. " +
                "To locate a file: one FIND_FILES with a short name (e.g. ChatScreen) returns ranked full paths — do not burn 20 greps. " +
                "Re-READ only when you lack content or an edit failed. " +
                "When done → ANSWER + DONE immediately (no second discovery phase). " +
                "If an Active plan is listed, CONTINUE and COMPLETE_STEP as you go.\n\n" +
                "Decide first: is this genuinely COMPLEX (3+ distinct phases)? If NO — do it directly and DONE. " +
                "If YES, PLAN once, execute, COMPLETE_STEP, DONE. Use LOCAL tools for files; one batched CLOUD_SHELL for shell work."
    }

    /**
     * Parses the AI response. Extracts content-heavy WRITE/CREATE (END_FILE format) first,
     * then XML <tool_call> blocks for other tools, then line directives as fallback.
     */
    /** Parse "x,y" (also "x y" or "(x, y)") into a coordinate pair for Computer Use actions. */
    private fun parseXY(s: String): Pair<Int?, Int?> {
        val nums = Regex("-?\\d+").findAll(s).map { it.value.toIntOrNull() }.toList()
        return if (nums.size >= 2) nums[0] to nums[1] else null to null
    }

    fun parseActions(text: String): List<AgentStep> {
        val steps = mutableListOf<AgentStep>()
        // --- 1. WRITE_FILE / CREATE_FILE with END_FILE delimiter (content-heavy) ---
        var remaining = text
        val writeRegex = Regex(
            "(WRITE_FILE|CREATE_FILE)\\s*:?\\s*([^\\n<]+?)\\s*\\n([\\s\\S]*?)\\nEND_FILE",
            RegexOption.IGNORE_CASE
        )
        for (m in writeRegex.findAll(text)) {
            val action = if (m.groupValues[1].uppercase().startsWith("CREATE")) "create" else "write"
            val path = m.groupValues[2].trim().trim('"', '`', '\'')
            var content = m.groupValues[3]
            content = content.replace(Regex("^```\\w*\\n"), "").replace(Regex("\\n```$"), "")
            steps.add(AgentStep(action, path, detail = content))
        }
        // Remove matched write blocks so XML parser doesn't double-process write content.
        remaining = writeRegex.replace(text, "")

        // --- 2. XML tool-call blocks — ALWAYS parse remaining text.
        //      Previously we skipped ALL XML if any WRITE_FILE matched, so a turn with
        //      WRITE_FILE + EDIT_FILE + READ_FILE only kept the write and dropped edits/reads.
        //      Still strip write bodies first so half-streamed WRITE path lines aren't misparsed.
        val calls = ToolCallParser.extract(remaining)
        for (c in calls) {
            // Skip WRITE/CREATE XML if we already got the same path via END_FILE form
            val name = c.name.uppercase().replace(Regex("[\\s_-]+"), "_")
            if ((name == "WRITE_FILE" || name == "CREATE_FILE") &&
                steps.any { (it.action == "write" || it.action == "create") &&
                    it.path.equals(c.args.firstOrNull()?.trim().orEmpty(), ignoreCase = true) }
            ) continue
            addStep(steps, c.name, c.args, c.named, c.explanation)
        }

        // --- 3. Fallback line directives (only when nothing structured was found) ---
        if (steps.isEmpty()) {
            for (m in Regex("READ_FILE:\\s*([^\\n]+)", RegexOption.IGNORE_CASE).findAll(remaining))
                steps.add(AgentStep("read", m.groupValues[1].trim().trim('"', '`', '\'')))
            Regex("ANSWER:\\s*\\n?([\\s\\S]*?)(?:END_ANSWER|$)", RegexOption.IGNORE_CASE).find(remaining)?.let {
                if (it.groupValues[1].isNotBlank()) steps.add(AgentStep("answer", detail = it.groupValues[1].trim()))
            }
            Regex("(?m)^\\s*DONE:?\\s*(.*)$", RegexOption.IGNORE_CASE).find(remaining)?.let {
                steps.add(AgentStep("done", detail = it.groupValues[1].trim()))
            }
        }

        // If response had NO recognizable tool at all, treat as final answer
        if (steps.isEmpty() && text.trim().length > 5) {
            steps.add(AgentStep("answer", detail = text.trim()))
            steps.add(AgentStep("done"))
        }

        return steps
    }

    /** True if the model's reply actually contains a tool call (XML, WRITE_FILE, or a line directive). */
    fun containsToolCall(text: String): Boolean {
        // Use the centralised, fault-tolerant ToolCallParser — it accepts <tool_call>, <toolcall>,
        // <tool call>, <|tool_call|>, <function_call>, JSON form, and bare line directives.
        if (ToolCallParser.containsToolCall(text)) return true
        if (Regex("(WRITE_FILE|CREATE_FILE)\\s*:?\\s*[^\\n<]+\\n[\\s\\S]*?\\nEND_FILE", RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
        return false
    }

    private fun addStep(
        steps: MutableList<AgentStep>,
        tool: String,
        args: List<String>,
        named: Map<String, String> = emptyMap(),
        explanation: String = ""
    ) {
        fun a(i: Int) = args.getOrNull(i) ?: ""
        // Read a value by any of the given named keys, falling back to positional index [pos].
        fun n(vararg keys: String, pos: Int): String {
            for (k in keys) { val v = named[k.lowercase()]; if (!v.isNullOrBlank()) return v }
            return a(pos)
        }
        val ex = explanation
        when (tool) {
            "PLAN" -> steps.add(AgentStep("plan", detail = n("steps", "plan", "content", pos = 0), explanation = ex))
            "COMPLETE_STEP" -> steps.add(AgentStep("completestep", detail = n("step_number", "step", "number", pos = 0)))
            "READ_FILE" -> steps.add(AgentStep("read", n("target_file", "path", "file", pos = 0), explanation = ex))
            "READ_FILES", "BATCH_READ", "READ_MANY" -> steps.add(
                AgentStep(
                    "readfiles",
                    detail = n("paths", "files", "path", "file", "target_files", pos = 0),
                    explanation = ex
                )
            )
            "READ_LINES" -> steps.add(AgentStep("readlines", n("target_file", "path", "file", pos = 0), arg2 = n("start", "start_line", pos = 1), detail = n("end", "end_line", pos = 2), explanation = ex))
            "EDIT_FILE" -> steps.add(
                AgentStep(
                    "edit",
                    n("target_file", "path", "file", pos = 0),
                    arg2 = n("old_text", "old", "search", pos = 1),
                    detail = n("new_text", "new", "replace", "code_edit", "code", pos = 2),
                    // replace_all flag lives in arg3 ("true" / "all" / "replace_all")
                    arg3 = n("replace_all", "replaceAll", "all", pos = 3),
                    explanation = ex
                )
            )
            "INSERT_LINES", "INSERT_LINE", "INSERT" -> steps.add(
                AgentStep(
                    "insertlines",
                    path = n("target_file", "path", "file", pos = 0),
                    arg2 = n("after_line", "line", "after", "at", "line_number", pos = 1),
                    detail = n("text", "content", "code", "new_text", pos = 2),
                    explanation = ex
                )
            )
            "WRITE_FILE" -> steps.add(AgentStep("write", n("target_file", "path", "file", pos = 0), detail = n("content", "code", "text", pos = 1), explanation = ex))
            "CREATE_FILE" -> steps.add(AgentStep("create", n("target_file", "path", "file", pos = 0), detail = n("content", "code", "text", pos = 1), explanation = ex))
            "DELETE_FILE" -> steps.add(AgentStep("delete", n("target_file", "path", "file", pos = 0), explanation = ex))
            "COPY_FILE" -> steps.add(AgentStep("copy", n("source", "from", "path", pos = 0), arg2 = n("destination", "to", "dest", pos = 1)))
            "MOVE_FILE" -> steps.add(AgentStep("move", n("source", "from", "path", pos = 0), arg2 = n("destination", "to", "dest", pos = 1)))
            "DOWNLOAD" -> steps.add(AgentStep("download", n("destination", "path", "output", pos = 1), arg2 = n("url", "source", pos = 0)))
            "DOWNLOAD_TO_DEVICE" -> steps.add(AgentStep("downloaddevice", n("filename", "name", pos = 1), arg2 = n("url", "source", pos = 0)))
            "LIST_DOWNLOADS" -> steps.add(AgentStep("listdownloads"))
            "IMPORT_TO_PROJECT" -> steps.add(AgentStep("importdownload", n("filename", "file", "name", pos = 0), arg2 = n("destination", "folder", pos = 1)))
            "FETCH_URL" -> steps.add(AgentStep("fetch", arg2 = n("url", "target", pos = 0)))
            "WEB_SEARCH" -> steps.add(AgentStep("websearch", detail = n("query", "q", "search", "topic", pos = 0)))
            "IMAGE_SEARCH" -> steps.add(AgentStep("imagesearch", detail = n("query", "q", "search", "topic", pos = 0)))
            "READ_URL" -> steps.add(AgentStep("readurl", arg2 = n("url", "target", "page", pos = 0)))
            "HTTP_REQUEST" -> steps.add(AgentStep("http", path = n("url", "endpoint", pos = 1), arg2 = n("method", pos = 0), detail = n("body", "data", "content", pos = 2), arg3 = n("headers", pos = 3)))
            "CHECK_HTML" -> steps.add(AgentStep("checkhtml", n("path", "file", pos = 0)))
            "SEARCH_CODE" -> steps.add(
                AgentStep(
                    "search",
                    path = n("path", "scope", "folder", "dir", pos = 1),
                    detail = n("query", "q", "search", pos = 0),
                    arg2 = n("context", "context_lines", "ctx", pos = 2)
                )
            )
            "CODEBASE_SEARCH" -> steps.add(AgentStep("codebasesearch", detail = n("query", "q", "search", pos = 0), explanation = ex))
            "FIND_FILES", "GLOB", "FIND_FILE" -> steps.add(
                AgentStep(
                    "glob",
                    path = n("path", "scope", "folder", "dir", pos = 1),
                    detail = n("pattern", "glob", "name", "query", pos = 0),
                    explanation = ex
                )
            )
            "DIFF_HISTORY", "RECENT_CHANGES", "CHANGES" -> steps.add(
                AgentStep(
                    "diffhistory",
                    path = n("path", "file", "filter", pos = 0),
                    detail = n("limit", "max", pos = 1),
                    explanation = ex
                )
            )
            "REPO_MAP", "REPOMAP", "OUTLINE" -> steps.add(
                AgentStep("repomap", path = n("path", "scope", "folder", pos = 0), explanation = ex)
            )
            "CODEBASE_WIKI", "WIKI", "GENERATE_WIKI" -> steps.add(
                AgentStep("codebasewiki", path = n("output", "path", "file", pos = 0), explanation = ex)
            )
            "TASK" -> steps.add(AgentStep("task", path = n("agent", "agent_type", "type", pos = 1), detail = n("prompt", "task", "description", pos = 0), explanation = ex))
            "LOAD_TOOLS" -> steps.add(AgentStep("loadtools", detail = n("category", "categories", "name", pos = 0)))
            "BULK_EDIT" -> steps.add(
                AgentStep(
                    "bulkedit",
                    path = n("path", "scope", "folder", pos = 2),
                    arg2 = n("old_text", "old", "search", pos = 0),
                    detail = n("new_text", "new", "replace", pos = 1)
                )
            )
            // GREP: path=scope, detail=pattern, arg2=glob, arg3=options "ci=true;ctx=2;max=80"
            "GREP" -> steps.add(
                AgentStep(
                    "grep",
                    path = n("path", "scope", "folder", "dir", "file", pos = 1),
                    detail = n("pattern", "query", "regex", "search", pos = 0),
                    arg2 = n("glob", "include", "file_glob", pos = 2),
                    arg3 = buildString {
                        val ci = n("case_insensitive", "ignore_case", "ci", pos = 3)
                        val ctx = n("context", "context_lines", "ctx", "A", "B", "C", pos = 4)
                        val max = n("max_results", "max", "head_limit", "limit", pos = 5)
                        val outMode = n("output_mode", "output", "mode", pos = 6)
                        val ml = n("multiline", "dotall", pos = 7)
                        if (ci.isNotBlank()) append("ci=$ci;")
                        if (ctx.isNotBlank()) append("ctx=$ctx;")
                        if (max.isNotBlank()) append("max=$max;")
                        if (outMode.isNotBlank()) append("mode=$outMode;")
                        if (ml.isNotBlank()) append("multiline=$ml;")
                        // Also accept a single options blob in named "options"
                        val opt = named["options"] ?: named["flags"]
                        if (!opt.isNullOrBlank()) append(opt)
                    }
                )
            )
            "APPLY_PATCH" -> steps.add(
                AgentStep(
                    "applypatch",
                    path = n("path", "file", "target_file", pos = 0),
                    detail = n("patch", "diff", "content", "patch_text", pos = 1),
                    explanation = ex
                )
            )
            "LIST_FILES" -> steps.add(AgentStep("list"))
            "RUN_PYTHON" -> steps.add(AgentStep("runpython", detail = n("code", "script", "python", pos = 0)))
            "CLOUD_SHELL" -> steps.add(AgentStep("cloudshell", detail = n("command", "cmd", "code", "script", pos = 0)))
            "APK_DECOMPILE" -> steps.add(AgentStep("apkdecompile", path = n("path", "file", "apk", pos = 0), arg2 = n("output", "destination", pos = 1)))
            "APK_INFO" -> steps.add(AgentStep("apkinfo", path = n("path", "file", "apk", pos = 0)))
            "SCAN_SECRETS" -> steps.add(AgentStep("scansecrets", path = n("path", "target", pos = 0)))
            "SECURITY_AUDIT" -> steps.add(AgentStep("securityaudit", path = n("path", "target", pos = 0)))
            "WEB_SCAN" -> steps.add(AgentStep("webscan", arg2 = n("url", "target", pos = 0)))
            "CLOUD_INSTALL" -> steps.add(AgentStep("cloudinstall", path = n("manager", "type", pos = 0), detail = n("packages", "package", "name", pos = 1)))
            "RECON" -> steps.add(AgentStep("recon", arg2 = n("target", "domain", "url", pos = 0)))
            "PORT_SCAN" -> steps.add(AgentStep("portscan", arg2 = n("target", "host", "ip", pos = 0), detail = n("flags", "options", pos = 1)))
            "VULN_SCAN" -> steps.add(AgentStep("vulnscan", arg2 = n("target", "url", pos = 0), detail = n("flags", "options", pos = 1)))
            "DIR_FUZZ" -> steps.add(AgentStep("dirfuzz", arg2 = n("target", "url", pos = 0), detail = n("wordlist", pos = 1)))
            "NIKTO_SCAN" -> steps.add(AgentStep("niktoscan", arg2 = n("target", "url", pos = 0)))
            "SQLI_TEST" -> steps.add(AgentStep("sqlitest", arg2 = n("target", "url", pos = 0), detail = n("flags", "options", pos = 1)))
            "SSL_SCAN" -> steps.add(AgentStep("sslscan", arg2 = n("target", "host", pos = 0)))
            "URL_HARVEST" -> steps.add(AgentStep("urlharvest", arg2 = n("target", "domain", pos = 0)))
            "XSS_SCAN" -> steps.add(AgentStep("xssscan", arg2 = n("target", "url", pos = 0)))
            "CMDI_TEST" -> steps.add(AgentStep("cmditest", arg2 = n("target", "url", pos = 0), detail = n("flags", "options", pos = 1)))
            "CRLF_SCAN" -> steps.add(AgentStep("crlfscan", arg2 = n("target", "url", pos = 0)))
            "PARAM_DISCOVER" -> steps.add(AgentStep("paramdiscover", arg2 = n("target", "url", pos = 0)))
            "CONTENT_DISCOVER" -> steps.add(AgentStep("contentdiscover", arg2 = n("target", "url", pos = 0)))
            "FAST_PORTSCAN" -> steps.add(AgentStep("fastportscan", arg2 = n("target", "host", pos = 0)))
            "DEEP_SECRETS" -> steps.add(AgentStep("deepsecrets", path = n("path", "target", pos = 0)))
            "WP_SCAN" -> steps.add(AgentStep("wpscan", arg2 = n("target", "url", pos = 0)))
            "SAST" -> steps.add(AgentStep("sast", path = n("path", "target", pos = 0)))
            "REPO_SECRETS" -> steps.add(AgentStep("reposecrets", path = n("path", "target", pos = 0)))
            "SOCIAL_OSINT" -> steps.add(AgentStep("socialosint", arg2 = n("username", "target", pos = 0)))
            "PHONE_OSINT" -> steps.add(AgentStep("phoneosint", arg2 = n("number", "phone", "target", pos = 0)))
            "RUN_JOB" -> steps.add(AgentStep("runjob", detail = n("command", "cmd", pos = 0)))
            "JOB_STATUS" -> steps.add(AgentStep("jobstatus", arg2 = n("id", "job_id", pos = 0)))
            "SITE_CLONE" -> steps.add(AgentStep("siteclone", arg2 = n("url", "target", pos = 0), path = n("output", "folder", pos = 1), detail = n("depth", pos = 2)))
            "APK_REBUILD" -> steps.add(AgentStep("apkrebuild", path = n("path", "folder", "input", pos = 0), arg2 = n("output", pos = 1)))
            "IMAGE_EDIT" -> steps.add(AgentStep("imageedit", path = n("input", "path", "file", pos = 0), arg2 = n("operation", "op", pos = 1), detail = n("args", "options", pos = 2), arg3 = n("output", pos = 3)))
            "CLOUD_LS" -> steps.add(AgentStep("cloudls", path = n("path", pos = 0)))
            "CLOUD_PULL" -> steps.add(AgentStep("cloudpull", path = n("source", "path", "cloud_path", pos = 0), arg2 = n("destination", "dest", pos = 1)))
            "CLOUD_PUSH" -> steps.add(AgentStep("cloudpush", path = n("source", "path", pos = 0), arg2 = n("destination", "dest", pos = 1)))
            "EXPORT_TO_DEVICE" -> steps.add(AgentStep("exporttodevice", path = n("path", "file", "source", pos = 0), arg2 = n("name", "filename", pos = 1)))
            "GENERATE_IMAGE" -> steps.add(AgentStep("generateimage", detail = n("prompt", "description", "text", pos = 0), path = n("output", "path", pos = 1), arg2 = n("model", pos = 2)))
            "SCREENSHOT" -> steps.add(AgentStep("screenshot", arg2 = n("url", "target", pos = 0), path = n("output", "path", pos = 1)))
            "BROWSER_OPEN", "BROWSER_GOTO" -> steps.add(AgentStep("browseropen", arg2 = n("url", "target", "page", pos = 0)))
            "BROWSER_CLICK" -> steps.add(AgentStep("browserclick", arg2 = n("element_index", "index", "element", "idx", "element_id", "selector", pos = 0)))
            "BROWSER_TYPE" -> steps.add(AgentStep("browsertype", arg2 = n("element_index", "index", "element", "idx", "element_id", pos = 0), detail = n("text", "value", "content", pos = 1)))
            "BROWSER_PRESS" -> steps.add(AgentStep("browserpress", arg2 = n("key", "button", pos = 0)))
            "COMPUTER_OPEN", "COMPUTER_START", "COMPUTER_DESKTOP" -> steps.add(AgentStep("computeropen"))
            "COMPUTER_CLICK" -> steps.add(AgentStep("computerclick", arg2 = n("coords", "position", "xy", "target", "point", pos = 0), detail = n("mode", "click", pos = 1)))
            "COMPUTER_MOVE" -> steps.add(AgentStep("computermove", arg2 = n("coords", "position", "xy", "target", "point", pos = 0)))
            "COMPUTER_TYPE" -> steps.add(AgentStep("computertype", detail = n("text", "value", "content", pos = 0)))
            "COMPUTER_KEY" -> steps.add(AgentStep("computerkey", arg2 = n("key", "button", pos = 0)))
            "COMPUTER_SCREENSHOT" -> steps.add(AgentStep("computerscreenshot"))
            "COMPUTER_SCROLL" -> steps.add(AgentStep("computerscroll", arg2 = n("direction", "dir", pos = 0)))
            "BROWSER_SCROLL" -> steps.add(AgentStep("browserscroll", arg2 = n("direction", pos = 0)))
            "BROWSER_UPLOAD" -> steps.add(AgentStep("browserupload", arg2 = n("element_index", "index", "element", "idx", pos = 0), detail = n("path", "file", "files", "document", pos = 1)))
            "BROWSER_SELECT" -> steps.add(AgentStep("browserselect", arg2 = n("element_index", "index", "element", "idx", pos = 0), detail = n("option", "value", "label", "text", pos = 1)))
            "BROWSER_BACK" -> steps.add(AgentStep("browserback"))
            "BROWSER_EXTRACT", "BROWSER_READ" -> steps.add(AgentStep("browserextract"))
            "BROWSER_VIEW" -> steps.add(AgentStep("browserview"))
            "BROWSER_ROTATE", "BROWSER_FLIP" -> steps.add(AgentStep("browserrotate"))
            "BROWSER_RESIZE", "BROWSER_VIEWPORT" -> steps.add(AgentStep("browserresize", arg2 = n("size", "viewport", "dimensions", pos = 0)))
            "ANALYZE_IMAGE" -> steps.add(AgentStep("analyzeimage", path = n("path", "image", "file", pos = 0)))
            "UNZIP" -> steps.add(AgentStep("unzip", path = n("path", "file", "archive", pos = 0), arg2 = n("destination", "output", pos = 1)))
            "ZIP" -> steps.add(AgentStep("zip", path = n("path", "source", "input", pos = 0), arg2 = n("output", "destination", pos = 1)))
            "PDF_CREATE" -> steps.add(AgentStep("pdfcreate", path = n("output", "path", "filename", pos = 0), detail = n("content", "markdown", "text", pos = 1), arg2 = n("style", "theme", pos = 2), arg3 = n("watermark", pos = 3)))
            "CREATE_XLSX", "CREATE_EXCEL" -> steps.add(AgentStep("createxlsx", path = n("output", "path", "filename", pos = 0), detail = n("data", "content", "json", pos = 1)))
            "CREATE_CSV" -> steps.add(AgentStep("createcsv", path = n("output", "path", "filename", pos = 0), detail = n("data", "content", "json", pos = 1)))
            "CREATE_PPTX", "CREATE_PPT" -> steps.add(AgentStep("createpptx", path = n("output", "path", "filename", pos = 0), detail = n("data", "content", "json", pos = 1)))
            "MAKE_SHORTS" -> steps.add(AgentStep("makeshorts", path = n("source", "video", "url", pos = 0), detail = n("options", "config", "json", pos = 1), arg2 = n("output", pos = 2)))
            "CREATE_DOCX", "CREATE_WORD" -> steps.add(AgentStep("createdocx", path = n("output", "path", "filename", pos = 0), detail = n("data", "content", "json", pos = 1)))
            "PDF_EDIT_TEXT" -> steps.add(AgentStep("pdfedit", path = n("path", "file", "pdf", pos = 0), arg2 = n("old_text", "old", "find", pos = 1), detail = n("new_text", "new", "replace", pos = 2)))
            "PDF_ADD_PAGE" -> steps.add(AgentStep("pdfaddpage", path = n("path", "file", "pdf", pos = 0), detail = n("content", "text", pos = 1)))
            "PDF_ADD_IMAGE" -> steps.add(AgentStep("pdfaddimage", path = n("path", "file", "pdf", pos = 0), arg2 = n("image", "image_path", pos = 1), detail = n("page", "page_number", pos = 2)))
            "PDF_ADD_CHART" -> steps.add(AgentStep("pdfaddchart", path = n("output", "path", pos = 0), arg2 = n("chart_type", "type", pos = 1), detail = n("data", "json", "data_json", pos = 2)))
            "PDF_MERGE" -> steps.add(AgentStep("pdfmerge", path = n("file1", "input1", pos = 0), arg2 = n("file2", "input2", pos = 1), detail = n("output", pos = 2)))
            "PDF_SPLIT" -> steps.add(AgentStep("pdfsplit", path = n("path", "file", "input", pos = 0), arg2 = n("pages", "page_range", pos = 1), detail = n("output", pos = 2)))
            "PDF_READ" -> steps.add(AgentStep("pdfread", path = n("path", "file", "pdf", pos = 0)))
            "PDF_FILL_FORM" -> steps.add(AgentStep("pdffillform", path = n("path", "file", "pdf", pos = 0), detail = n("fields", "data", "json", pos = 1)))
            "PDF_COMPRESS" -> steps.add(AgentStep("pdfcompress", path = n("path", "file", "input", pos = 0), arg2 = n("quality", pos = 1), detail = n("output", pos = 2)))
            "PDF_COLLAGE" -> steps.add(AgentStep("pdfcollage", path = n("images", "image_list", pos = 0), arg2 = n("output", pos = 1), detail = n("columns", "cols", pos = 2)))
            "SCAFFOLD_ANDROID" -> steps.add(AgentStep("scaffoldandroid"))
            "MULTI_EDIT" -> {
                val filePath = n("path", "file", "target_file", pos = 0)
                val pairsJson = named["pairs_json"] ?: named["pairs"] ?: ""
                if (pairsJson.isNotBlank()) {
                    // Native JSON format: {"path":"file", "pairs_json":"[[\"old\",\"new\"],[\"old2\",\"new2\"]]"}
                    try {
                        val pairsArr = org.json.JSONArray(pairsJson)
                        val flatArgs = mutableListOf<String>()
                        for (i in 0 until pairsArr.length()) {
                            val pair = pairsArr.getJSONArray(i)
                            flatArgs.add(pair.getString(0))
                            flatArgs.add(pair.getString(1))
                        }
                        steps.add(AgentStep("multiedit", path = filePath, detail = encodeMultiEditArgs(flatArgs)))
                    } catch (_: Exception) {
                        steps.add(AgentStep("multiedit", path = filePath, detail = encodeMultiEditArgs(args.drop(1))))
                    }
                } else {
                    steps.add(AgentStep("multiedit", path = filePath, detail = encodeMultiEditArgs(args.drop(1))))
                }
            }
            "SYMBOL_SEARCH" -> steps.add(
                AgentStep(
                    "symbolsearch",
                    path = n("path", "scope", "folder", pos = 1),
                    detail = n("symbol", "name", "query", pos = 0),
                    arg2 = n("glob", "include", pos = 2)
                )
            )
            "GO_TO_DEFINITION", "GOTO_DEFINITION", "DEFINITION", "LSP_DEFINITION" -> steps.add(
                AgentStep(
                    "gotodefinition",
                    path = n("path", "scope", "folder", "file", pos = 1),
                    detail = n("symbol", "name", "query", pos = 0),
                    explanation = ex
                )
            )
            "FIND_REFERENCES", "REFERENCES", "LSP_REFERENCES" -> steps.add(
                AgentStep(
                    "findreferences",
                    path = n("path", "scope", "folder", "file", pos = 1),
                    detail = n("symbol", "name", "query", pos = 0),
                    explanation = ex
                )
            )
            "DOCUMENT_SYMBOLS", "FILE_SYMBOLS", "LSP_DOCUMENT_SYMBOLS" -> steps.add(
                AgentStep(
                    "documentsymbols",
                    path = n("path", "file", "target_file", pos = 0),
                    explanation = ex
                )
            )
            "WORKSPACE_SYMBOLS", "LSP_WORKSPACE_SYMBOLS" -> steps.add(
                AgentStep(
                    "workspacesymbols",
                    path = n("path", "scope", "folder", pos = 1),
                    detail = n("query", "symbol", "name", "q", pos = 0),
                    explanation = ex
                )
            )
            "HOVER", "LSP_HOVER" -> steps.add(
                AgentStep(
                    "hover",
                    path = n("path", "scope", "folder", "file", pos = 1),
                    detail = n("symbol", "name", "query", pos = 0),
                    explanation = ex
                )
            )
            "IMPORT_ADD" -> steps.add(AgentStep("importadd", path = n("path", "file", pos = 0), detail = n("import", "statement", "import_statement", pos = 1)))
            "REFACTOR_RENAME" -> steps.add(AgentStep("refactorrename", arg2 = n("old_name", "old", "from", pos = 0), detail = n("new_name", "new", "to", pos = 1), path = n("scope", "path", "file", pos = 2)))
            "RUN_TESTS" -> steps.add(AgentStep("runtests", detail = n("args", "extra", "options", pos = 0)))
            "LINT" -> steps.add(AgentStep("lint", path = n("path", "target", pos = 0)))
            // Instant offline static verify (braces/syntax/package/android) — no SDK/cloud required
            "VERIFY" -> steps.add(
                AgentStep(
                    "verify",
                    path = n("path", "scope", "target", "file", pos = 0),
                    detail = n("mode", "level", "type", pos = 1) // quick | full | android | apk
                )
            )
            "FORMAT_CODE" -> steps.add(AgentStep("formatcode", path = n("path", "target", pos = 0)))
            "DEPS_ADD" -> steps.add(AgentStep("depsadd", path = n("manager", "package_manager", pos = 0), detail = n("package", "spec", "name", pos = 1)))
            "GIT_DIFF" -> steps.add(AgentStep("gitdiff"))
            "GIT_COMMIT" -> steps.add(AgentStep("gitcommit", detail = n("message", "msg", "commit_message", pos = 0)))
            "SNIPPET_SAVE" -> steps.add(AgentStep("snippetsave", path = n("name", "snippet_name", pos = 0), arg2 = n("description", "desc", "language", pos = 1), detail = n("code", "content", "snippet", pos = 2)))
            "SNIPPET_LIST" -> steps.add(AgentStep("snippetlist"))
            "SNIPPET_LOAD" -> steps.add(AgentStep("snippetload", path = n("name", "snippet_name", pos = 0), arg2 = n("destination", "dest", "file", pos = 1)))
            "GH_PUSH" -> steps.add(AgentStep("ghpush", detail = n("message", "commit_message", "msg", pos = 0), arg2 = n("branch", pos = 1)))
            "GH_PR" -> steps.add(AgentStep("ghpr", path = n("title", pos = 0), arg2 = n("head", "head_branch", pos = 1), arg3 = n("base", "base_branch", pos = 2), detail = n("body", "description", pos = 3)))
            "GH_LIST_ISSUES" -> steps.add(AgentStep("ghissues"))
            "GH_CREATE_ISSUE" -> steps.add(AgentStep("ghcreateissue", path = n("title", pos = 0), detail = n("body", "description", "content", pos = 1)))
            "GH_BUILD_APK" -> steps.add(AgentStep("ghbuild"))
            "VERIFY_BUILD" -> steps.add(AgentStep("verifybuild"))
            "GH_BUILD_STATUS" -> steps.add(AgentStep("ghbuildstatus"))
            "GH_BUILD_LOGS" -> steps.add(AgentStep("ghlogs"))
            // Remote browse + repo/branch management
            "GH_LIST_REPOS" -> steps.add(AgentStep("ghrepos", detail = n("query", "search", "filter", pos = 0)))
            "GH_LIST_BRANCHES" -> steps.add(AgentStep("ghbranches", path = n("repo", "repository", "full_name", pos = 0)))
            "GH_READ_REMOTE" -> steps.add(AgentStep(
                "ghreadremote",
                path = n("path", "file", "filepath", pos = 0),
                arg2 = n("repo", "repository", "full_name", pos = 1),
                arg3 = n("ref", "branch", "sha", pos = 2)
            ))
            "GH_LIST_REMOTE" -> steps.add(AgentStep(
                "ghlistremote",
                path = n("path", "dir", "directory", pos = 0),
                arg2 = n("repo", "repository", "full_name", pos = 1),
                arg3 = n("ref", "branch", "sha", pos = 2)
            ))
            "GH_CREATE_REPO" -> steps.add(AgentStep(
                "ghcreaterepo",
                path = n("name", "repo", "repo_name", pos = 0),
                arg2 = n("private", "visibility", pos = 1),
                detail = n("description", "desc", pos = 2)
            ))
            "GH_CREATE_BRANCH" -> steps.add(AgentStep(
                "ghcreatebranch",
                path = n("name", "branch", "new_branch", pos = 0),
                arg2 = n("from", "from_branch", "base", "source", pos = 1),
                arg3 = n("switch", "switch_local", "checkout", pos = 2),
                detail = n("repo", "repository", pos = 3)
            ))
            "GH_SWITCH_BRANCH" -> steps.add(AgentStep(
                "ghswitchbranch",
                path = n("branch", "name", "new_branch", pos = 0),
                arg2 = n("repo", "repository", pos = 1)
            ))
            "GH_OPEN_REPO", "GH_CLONE_REPO" -> steps.add(AgentStep(
                "ghopenrepo",
                path = n("repo", "repository", "full_name", "name", pos = 0),
                arg2 = n("branch", "ref", pos = 1)
            ))
            "GH_STATUS" -> steps.add(AgentStep("ghstatus"))
            "PREVIEW_WEB_APP" -> steps.add(AgentStep("previewweb", path = a(0)))
            "RENDER_DIAGRAM" -> steps.add(AgentStep("renderdiagram", arg2 = n("kind", "type", "diagram_type", pos = 0), detail = n("source", "code", "content", "diagram_source", pos = 1)))
            "RENDER_CHART" -> steps.add(AgentStep("renderchart", arg2 = n("kind", "type", "chart_type", pos = 0), detail = n("data", "data_json", "content", pos = 1), path = n("title", pos = 2)))
            // Artifacts: run multi-lang apps + proof (screenshot / logs)
            "RUN_APP", "RUN_AND_CAPTURE", "CAPTURE_PROOF" -> steps.add(AgentStep(
                "runapp",
                detail = n("command", "cmd", "shell", pos = 0),
                arg2 = n("no_screenshot", "skip_shot", pos = 1)
            ))
            "LIST_ARTIFACTS" -> steps.add(AgentStep("listartifacts"))
            "CAPTURE_ARTIFACT" -> steps.add(AgentStep(
                "captureartifact",
                path = n("path", "file", pos = 0),
                arg2 = n("kind", "type", pos = 1),
                detail = n("title", "name", "label", pos = 2)
            ))
            "ASK_USER" -> steps.add(AgentStep("askuser", detail = n("questions", "questions_json", "json", pos = 0)))
            "PARALLEL_TASKS" -> steps.add(AgentStep("paralleltasks", detail = n("tasks", "tasks_json", "json", pos = 0)))
            "LOAD_SKILL" -> steps.add(
                AgentStep(
                    "loadskill",
                    detail = n("skill_name", "skill", "skill_id", "name", pos = 0),
                    arg2 = n("arguments", "args", "\$ARGUMENTS", "arg", pos = 1)
                )
            )
            "LIST_SKILL_RESOURCES" -> steps.add(
                AgentStep("listskillresources", detail = n("skill_name", "skill", "skill_id", "name", pos = 0))
            )
            "READ_SKILL_RESOURCE" -> steps.add(
                AgentStep(
                    "readskillresource",
                    detail = n("skill_name", "skill", "skill_id", "name", pos = 0),
                    path = n("path", "file", "resource", "resource_path", pos = 1)
                )
            )
            "RUN_SKILL_SCRIPT" -> steps.add(
                AgentStep(
                    "runskillscript",
                    detail = n("skill_name", "skill", "skill_id", "name", pos = 0),
                    path = n("path", "script", "file", pos = 1),
                    arg2 = n("arguments", "args", "arg", pos = 2)
                )
            )
            // Create/replace custom skill (always user-permission gated)
            "SAVE_SKILL", "CREATE_SKILL", "REPLACE_SKILL" -> steps.add(
                AgentStep(
                    "saveskill",
                    path = n("skill_name", "name", "id", "skill_id", pos = 2),
                    detail = n("skill_md", "content", "markdown", "skillmd", "body", pos = 0),
                    arg2 = n("replace", "overwrite", "force", pos = 1),
                    arg3 = n("resources_json", "resources", "files", pos = 3)
                )
            )
            "WORKLOG" -> steps.add(AgentStep("worklog", detail = n("entry", "summary", "content", "text", pos = 0)))
            "REMEMBER" -> steps.add(AgentStep("remember", detail = n("fact", "memory", "text", "content", "note", pos = 0)))
            "FORGET" -> steps.add(AgentStep("forget", detail = n("query", "fact", "text", "match", pos = 0)))
            "MEMORY" -> steps.add(AgentStep("memory", detail = n("query", "filter", "text", pos = 0)))
            "DIAGNOSE" -> steps.add(AgentStep("diagnose", path = n("path", "file", "target", pos = 0)))
            "ANSWER" -> steps.add(AgentStep("answer", detail = n("answer", "content", "text", "response", "message", pos = 0)))
            "DONE" -> steps.add(AgentStep("done", detail = n("summary", "message", "text", pos = 0)))
            "UNDO", "UNDO_LAST" -> steps.add(AgentStep("undo", detail = a(0)))
            "REDO" -> steps.add(AgentStep("redo", detail = a(0)))
            "CONFIRM" -> steps.add(AgentStep("confirm", arg2 = a(0), detail = a(1)))
            // Vercel MCP tools
            "VERCEL_LIST_PROJECTS" -> steps.add(AgentStep("vercel_list_projects"))
            "VERCEL_GET_PROJECT" -> steps.add(AgentStep("vercel_get_project", path = n("projectid", "project_id", "id", pos = 0)))
            "VERCEL_LIST_DEPLOYMENTS" -> steps.add(AgentStep("vercel_list_deployments", path = n("projectid", "project_id", "id", pos = 0)))
            "VERCEL_GET_DEPLOYMENT" -> steps.add(AgentStep("vercel_get_deployment", arg2 = n("deploymentid", "deployment_id", "id", pos = 0)))
            "VERCEL_CREATE_DEPLOYMENT" -> steps.add(AgentStep("vercel_create_deployment", path = n("projectid", "project_id", "id", pos = 0), detail = n("branch", pos = 1)))
            "VERCEL_LIST_DOMAINS" -> steps.add(AgentStep("vercel_list_domains", path = n("projectid", "project_id", "id", pos = 0)))
            "VERCEL_LIST_ENV" -> steps.add(AgentStep("vercel_list_env", path = n("projectid", "project_id", "id", pos = 0)))
            "VERCEL_GET_LOGS" -> steps.add(AgentStep("vercel_get_logs", arg2 = n("deploymentid", "deployment_id", "id", pos = 0)))
            // Render connector tools
            "RENDER_LIST_SERVICES"  -> steps.add(AgentStep("render_list_services",  path = n("limit", pos = 0)))
            "RENDER_GET_SERVICE"    -> steps.add(AgentStep("render_get_service",    path = n("serviceid", "service_id", "id", pos = 0)))
            "RENDER_LIST_DEPLOYS"   -> steps.add(AgentStep("render_list_deploys",   path = n("serviceid", "service_id", "id", pos = 0), detail = n("limit", pos = 1)))
            "RENDER_TRIGGER_DEPLOY" -> steps.add(AgentStep("render_trigger_deploy", path = n("serviceid", "service_id", "id", pos = 0), detail = n("commitid", "commit_id", pos = 1)))
            "RENDER_LIST_ENV"       -> steps.add(AgentStep("render_list_env",       path = n("serviceid", "service_id", "id", pos = 0)))
            "RENDER_GET_LOGS"       -> steps.add(AgentStep("render_get_logs",       path = n("serviceid", "service_id", "id", pos = 0), detail = n("limit", pos = 1)))
            // Cloudflare connector tools
            "CLOUDFLARE_VERIFY"            -> steps.add(AgentStep("cloudflare_verify"))
            "CLOUDFLARE_LIST_ACCOUNTS"     -> steps.add(AgentStep("cloudflare_list_accounts"))
            "CLOUDFLARE_LIST_ZONES"        -> steps.add(AgentStep("cloudflare_list_zones", path = n("limit", pos = 0)))
            "CLOUDFLARE_LIST_WORKERS"      -> steps.add(AgentStep("cloudflare_list_workers", path = n("accountid", "account_id", pos = 0)))
            "CLOUDFLARE_GET_WORKER"        -> steps.add(AgentStep("cloudflare_get_worker", path = n("accountid", "account_id", pos = 0), arg2 = n("scriptname", "script_name", pos = 1)))
            "CLOUDFLARE_LIST_KV_NAMESPACES"-> steps.add(AgentStep("cloudflare_list_kv_namespaces", path = n("accountid", "account_id", pos = 0)))
            "CLOUDFLARE_LIST_DNS_RECORDS"  -> steps.add(AgentStep("cloudflare_list_dns_records", path = n("zoneid", "zone_id", pos = 0), arg2 = n("type", pos = 1)))
            // Netlify connector tools
            "NETLIFY_LIST_SITES"     -> steps.add(AgentStep("netlify_list_sites",     path = n("limit", pos = 0)))
            "NETLIFY_GET_SITE"       -> steps.add(AgentStep("netlify_get_site",       path = n("siteid", "site_id", "id", pos = 0)))
            "NETLIFY_LIST_DEPLOYS"   -> steps.add(AgentStep("netlify_list_deploys",   path = n("siteid", "site_id", "id", pos = 0), detail = n("limit", pos = 1)))
            "NETLIFY_GET_DEPLOY"     -> steps.add(AgentStep("netlify_get_deploy",     path = n("siteid", "site_id", pos = 0), arg2 = n("deployid", "deploy_id", pos = 1)))
            "NETLIFY_LIST_ENV"       -> steps.add(AgentStep("netlify_list_env",       path = n("siteid", "site_id", "id", pos = 0)))
            "NETLIFY_LIST_FORMS"     -> steps.add(AgentStep("netlify_list_forms",     path = n("siteid", "site_id", "id", pos = 0)))
            "NETLIFY_LIST_FUNCTIONS" -> steps.add(AgentStep("netlify_list_functions", path = n("siteid", "site_id", "id", pos = 0)))
            // Supabase connector tools
            "SUPABASE_LIST_PROJECTS"  -> steps.add(AgentStep("supabase_list_projects"))
            "SUPABASE_GET_PROJECT"    -> steps.add(AgentStep("supabase_get_project",   path = n("projectref", "project_ref", "ref", "id", pos = 0)))
            "SUPABASE_GET_API_KEYS"   -> steps.add(AgentStep("supabase_get_api_keys",  path = n("projectref", "project_ref", "ref", "id", pos = 0)))
            "SUPABASE_LIST_TABLES"    -> steps.add(AgentStep("supabase_list_tables",   path = n("projectref", "project_ref", "ref", pos = 0), arg2 = n("servicekey", "service_key", pos = 1)))
            "SUPABASE_RUN_SQL"        -> steps.add(AgentStep("supabase_run_sql",       path = n("projectref", "project_ref", "ref", pos = 0), detail = n("query", "sql", pos = 1)))
            "SUPABASE_LIST_FUNCTIONS" -> steps.add(AgentStep("supabase_list_functions",path = n("projectref", "project_ref", "ref", "id", pos = 0)))
            // Railway connector tools
            "RAILWAY_LIST_PROJECTS"   -> steps.add(AgentStep("railway_list_projects"))
            "RAILWAY_GET_PROJECT"     -> steps.add(AgentStep("railway_get_project",    path = n("projectid", "project_id", "id", pos = 0)))
            "RAILWAY_LIST_SERVICES"   -> steps.add(AgentStep("railway_list_services",  path = n("projectid", "project_id", "id", pos = 0)))
            "RAILWAY_LIST_DEPLOYMENTS"-> steps.add(AgentStep("railway_list_deployments",path = n("projectid", "project_id", "id", pos = 0), detail = n("serviceid", "service_id", pos = 1)))
            "RAILWAY_GET_DEPLOYMENT"  -> steps.add(AgentStep("railway_get_deployment", arg2 = n("deploymentid", "deployment_id", "id", pos = 0)))
            "RAILWAY_GET_LOGS"        -> steps.add(AgentStep("railway_get_logs",       arg2 = n("deploymentid", "deployment_id", "id", pos = 0), detail = n("limit", pos = 1)))
            // Structured tool call fallback: any unrecognized tool with named args
            else -> {
                // Check if this came from structured tool_calls (has named args)
                if (named.isNotEmpty()) {
                    val pathVal = named["path"] ?: named["target_file"] ?: named["url"] ?: named["source"] ?: ""
                    val arg2Val = named["old_text"] ?: named["destination"] ?: named["method"] ?: named["query"] ?: ""
                    val detailVal = named["content"] ?: named["new_text"] ?: named["command"] ?: named["text"] ?: named["prompt"] ?: named["description"] ?: named["code"] ?: named["query"] ?: ""
                    val arg3Val = named["headers"] ?: named["args"] ?: named["extra"] ?: ""
                    steps.add(AgentStep(tool.lowercase(), path = pathVal, arg2 = arg2Val, detail = detailVal, arg3 = arg3Val, explanation = ex))
                } else if (args.isNotEmpty()) {
                    steps.add(AgentStep(tool.lowercase(), path = a(0), arg2 = a(1), detail = a(2), arg3 = a(3), explanation = ex))
                }
            }
        }
    }

    /**
     * Executes a single agent step against the project filesystem / network.
     */
    suspend fun executeStep(context: android.content.Context, projectDir: String, step: AgentStep): String = withContext(Dispatchers.IO) {
        when (step.action) {
            "read" -> "CONTENT of ${step.path}:\n${ProjectManager.readFile(projectDir, step.path)}"
            "readfiles" -> ProjectManager.readFiles(
                projectDir,
                step.detail.ifBlank { step.path.ifBlank { step.arg2 } }
            )
            "readlines" -> ProjectManager.readFileLines(
                projectDir, step.path,
                step.arg2.trim().toIntOrNull() ?: 1,
                step.detail.trim().toIntOrNull() ?: 200
            )
            "edit" -> {
                val replaceAll = step.arg3.trim().let { v ->
                    v.equals("true", true) || v.equals("1") || v.equals("all", true) ||
                        v.equals("replace_all", true) || v.equals("yes", true)
                }
                val editResult = ProjectManager.editFile(
                    projectDir, step.path, step.arg2, step.detail, replaceAll = replaceAll
                )
                if (editResult.startsWith("ERROR")) editResult
                else {
                    FileSessionTracker.markRead(projectDir, step.path)
                    appendDiagnostics(editResult, context, projectDir, step.path)
                }
            }
            "insertlines" -> {
                val after = step.arg2.trim().toIntOrNull()
                    ?: return@withContext "ERROR: INSERT_LINES needs after_line (integer, 0 = start of file)."
                val insertResult = ProjectManager.insertLines(
                    projectDir, step.path, after, step.detail
                )
                if (insertResult.startsWith("ERROR")) insertResult
                else {
                    FastSync.invalidate(projectDir)
                    appendDiagnostics(insertResult, context, projectDir, step.path)
                }
            }
            "write", "create" -> {
                val writeResult = ProjectManager.writeFile(projectDir, step.path, step.detail)
                if (writeResult.startsWith("ERROR")) writeResult
                else appendDiagnostics(writeResult, context, projectDir, step.path)
            }
            "applypatch" -> {
                val patchBody = step.detail.ifBlank { step.arg2 }
                val r = ProjectManager.applyPatch(projectDir, patchBody, defaultPath = step.path)
                if (r.startsWith("ERROR") || r.startsWith("PATCH partial")) r
                else appendDiagnostics(r, context, projectDir, step.path)
            }
            "delete" -> ProjectManager.deleteFile(projectDir, step.path)
            "copy" -> ProjectManager.copyFile(projectDir, step.path, step.arg2)
            "move" -> ProjectManager.moveFile(projectDir, step.path, step.arg2)
            "download" -> ProjectManager.downloadUrl(projectDir, step.arg2, step.path)
            // Vercel MCP tools
            "vercel_list_projects", "vercel_get_project", "vercel_list_deployments",
            "vercel_get_deployment", "vercel_create_deployment", "vercel_list_domains",
            "vercel_list_env", "vercel_get_logs" -> {
                val args = mutableMapOf<String, String>()
                if (step.path.isNotBlank()) args["projectId"] = step.path
                if (step.arg2.isNotBlank()) args["deploymentId"] = step.arg2
                if (step.detail.isNotBlank()) args["branch"] = step.detail
                VercelMCPTools.executeTool(step.action, args)
            }
            // Render connector tools
            "render_list_services", "render_get_service", "render_list_deploys",
            "render_trigger_deploy", "render_list_env", "render_get_logs" -> {
                val args = mutableMapOf<String, String>()
                if (step.path.isNotBlank()) args["serviceId"] = step.path
                if (step.detail.isNotBlank()) args["limit"] = step.detail
                // For render_trigger_deploy, detail holds commitId (parsed via step.detail)
                if (step.action == "render_trigger_deploy" && step.detail.isNotBlank())
                    args["commitId"] = step.detail
                com.ahamai.app.data.RenderClient.executeTool(step.action, args)
            }
            // Cloudflare connector tools
            "cloudflare_verify", "cloudflare_list_accounts", "cloudflare_list_zones",
            "cloudflare_list_workers", "cloudflare_get_worker",
            "cloudflare_list_kv_namespaces", "cloudflare_list_dns_records" -> {
                val args = mutableMapOf<String, String>()
                when (step.action) {
                    "cloudflare_list_zones" -> { if (step.path.isNotBlank()) args["limit"] = step.path }
                    "cloudflare_list_dns_records" -> {
                        if (step.path.isNotBlank()) args["zoneId"] = step.path
                        if (step.arg2.isNotBlank()) args["type"] = step.arg2
                    }
                    "cloudflare_list_workers", "cloudflare_list_kv_namespaces" -> {
                        if (step.path.isNotBlank()) args["accountId"] = step.path
                    }
                    "cloudflare_get_worker" -> {
                        if (step.path.isNotBlank()) args["accountId"] = step.path
                        if (step.arg2.isNotBlank()) args["scriptName"] = step.arg2
                    }
                    else -> { /* no args */ }
                }
                com.ahamai.app.data.CloudflareClient.executeTool(step.action, args)
            }
            // Netlify connector tools
            "netlify_list_sites", "netlify_get_site", "netlify_list_deploys",
            "netlify_get_deploy", "netlify_list_env", "netlify_list_forms",
            "netlify_list_functions" -> {
                val args = mutableMapOf<String, String>()
                if (step.path.isNotBlank()) args["siteId"] = step.path
                if (step.arg2.isNotBlank()) args["deployId"] = step.arg2
                if (step.detail.isNotBlank()) args["limit"] = step.detail
                com.ahamai.app.data.NetlifyClient.executeTool(step.action, args)
            }
            // Supabase connector tools
            "supabase_list_projects", "supabase_get_project", "supabase_get_api_keys",
            "supabase_list_tables", "supabase_run_sql", "supabase_list_functions" -> {
                val args = mutableMapOf<String, String>()
                if (step.path.isNotBlank()) args["projectRef"] = step.path
                if (step.arg2.isNotBlank()) args["serviceKey"] = step.arg2
                if (step.detail.isNotBlank()) args["query"] = step.detail
                com.ahamai.app.data.SupabaseClient.executeTool(step.action, args)
            }
            // Railway connector tools
            "railway_list_projects", "railway_get_project", "railway_list_services",
            "railway_list_deployments", "railway_get_deployment", "railway_get_logs" -> {
                val args = mutableMapOf<String, String>()
                if (step.path.isNotBlank()) args["projectId"] = step.path
                if (step.arg2.isNotBlank()) args["deploymentId"] = step.arg2
                if (step.detail.isNotBlank()) {
                    if (step.action == "railway_list_deployments") args["serviceId"] = step.detail
                    else args["limit"] = step.detail
                }
                com.ahamai.app.data.RailwayClient.executeTool(step.action, args)
            }
            "downloaddevice" -> DeviceStorage.downloadUrlToDevice(context, step.arg2, step.path)
            "listdownloads" -> {
                val items = DeviceStorage.listDownloads(context)
                if (items.isEmpty()) "Downloads folder is empty (or not accessible)."
                else "DEVICE DOWNLOADS:\n" + items.joinToString("\n") { "  ${it.name} (${it.sizeBytes} bytes)" }
            }
            "importdownload" -> ProjectManager.importDownloadToProject(context, projectDir, step.path, step.arg2)
            "fetch" -> {
                SoundEffects.playRead()
                "WEB CONTENT from ${step.arg2}:\n${ProjectManager.fetchUrl(step.arg2)}"
            }
            "websearch" -> {
                SoundEffects.playSearch()
                "WEB SEARCH RESULTS for '${step.detail}':\n${WebTools.search(step.detail)}"
            }
            "imagesearch" -> {
                SoundEffects.playSearch()
                "IMAGE SEARCH RESULTS for '${step.detail}':\n${WebTools.imageSearch(step.detail)}"
            }
            "readurl" -> {
                SoundEffects.playRead()
                "WEB PAGE ${step.arg2}:\n${WebTools.read(step.arg2)}"
            }
            "http" -> "HTTP ${step.arg2.ifBlank { "GET" }} ${step.path}:\n${ProjectManager.httpRequest(step.arg2, step.path, step.detail, step.arg3)}"
            "checkhtml" -> ProjectManager.checkHtml(projectDir, step.path)
            "search" -> {
                val ctx = step.arg2.trim().toIntOrNull() ?: 0
                "SEARCH RESULTS for '${step.detail}':\n" +
                    ProjectManager.searchCode(
                        projectDir, step.detail,
                        pathScope = step.path,
                        contextLines = ctx
                    )
            }
            "bulkedit" -> ProjectManager.bulkEdit(
                projectDir, step.arg2, step.detail, pathScope = step.path
            )
            "grep" -> {
                val opts = step.arg3.lowercase()
                fun opt(key: String): String? =
                    Regex("""\b$key\s*=\s*([^;,\s]+)""").find(opts)?.groupValues?.get(1)
                val ci = opt("ci")?.let {
                    it == "1" || it == "true" || it == "yes"
                } ?: (opts.contains("ignore_case") || opts.contains("-i"))
                val ctx = opt("ctx")?.toIntOrNull()
                    ?: opt("context")?.toIntOrNull()
                    ?: 0
                val max = opt("max")?.toIntOrNull() ?: 50
                val outMode = opt("mode") ?: opt("output") ?: "content"
                val ml = opt("multiline")?.let { it == "1" || it == "true" || it == "yes" }
                    ?: opts.contains("multiline")
                "GREP RESULTS for '${step.detail}':\n" +
                    ProjectManager.grepSearch(
                        projectDir = projectDir,
                        pattern = step.detail,
                        pathScope = step.path,
                        glob = step.arg2,
                        caseInsensitive = ci,
                        contextLines = ctx,
                        maxResults = max,
                        outputMode = outMode,
                        multiline = ml
                    )
            }
            "glob" -> "FIND_FILES '${step.detail}':\n" +
                ProjectManager.findFiles(projectDir, step.detail, pathScope = step.path)
            "diffhistory" -> ContextMemoryManager.diffHistory(
                projectDir,
                pathFilter = step.path,
                limit = step.detail.trim().toIntOrNull() ?: 20
            )
            "repomap" -> "REPO MAP:\n" + ProjectManager.buildRepoMap(
                projectDir, maxDecls = if (step.path.isBlank()) 160 else 260, pathScope = step.path
            )
            "codebasewiki" -> {
                val wiki = ProjectManager.buildCodebaseWiki(projectDir)
                if (wiki.startsWith("ERROR")) wiki
                else {
                    val out = step.path.ifBlank { "PROJECT_WIKI.md" }
                    val saved = ProjectManager.writeFile(projectDir, out, wiki)
                    if (saved.startsWith("ERROR")) "Generated wiki (not saved: $saved)\n\n$wiki"
                    else "Wiki written to $out\n\n$wiki"
                }
            }
            "list" -> "PROJECT FILE TREE:\n${ProjectManager.buildTreeString(projectDir)}"
            "runpython" -> {
                // Always run Python in the cloud shell — local Wandbox is unreliable and can't
                // access project files. If cloud isn't configured, give a clear error.
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Python runs in the cloud sandbox. Open Profile → Cloud Engine to set it up."
                } else {
                    val scriptPath = "/workspace/.ahamai_run_${System.currentTimeMillis()}.py"
                    val scriptFile = File(projectDir, scriptPath.substringAfterLast('/'))
                    scriptFile.writeText(step.detail)
                    try {
                        E2BClient.syncProjectToSandbox(
                            projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(),
                            File(projectDir)
                        )
                        val res = E2BClient.exec(
                            projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(),
                            "python3 '$scriptPath'", "/workspace", 90
                        )
                        scriptFile.delete()
                        runCatching {
                            E2BClient.exec(projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(),
                                "rm -f '$scriptPath'", "/workspace", 5)
                        }
                        "PYTHON OUTPUT:\n${res.formatted()}"
                    } catch (e: CancellationException) {
                        scriptFile.delete()
                        throw e
                    } catch (e: Exception) {
                        scriptFile.delete()
                        "ERROR: Python execution failed: ${e.message?.take(200)}"
                    }
                }
            }
            "cloudshell" -> {
                // HARD GUARD (not just a prompt hint): the CLOUD_SHELL box is bare Debian with no
                // JDK/Android SDK, so any gradlew/gradle Android-build attempt here ALWAYS fails
                // (JAVA_HOME, missing SDK, missing licenses...) after burning many turns. Refuse it
                // deterministically and redirect to GH_BUILD_APK — which runs the real toolchain on
                // GitHub Actions — instead of relying on the model to remember not to do this.
                val cmdLower = step.detail.lowercase()
                // Catches the gradle build invocation itself AND setting up a full Android SDK
                // (sdkmanager/platforms;android/build-tools; — always full-SDK setup, never needed
                // just to run apktool/jadx on an existing .apk, so this can't collide with the
                // legitimate CLOUD_SHELL apk-modding flow which only needs a bare JVM).
                // Any Gradle/Android compile path in sandbox is forbidden — including unit-test
                // and ktlint via gradlew (still need full JDK/AGP and burn turns). Static VERIFY
                // + GH_BUILD_APK cover quality and real APKs.
                val looksLikeAndroidBuildToolchain = Regex(
                    "\\bgradlew\\b|\\bgradle\\b\\s|gradle\\s+wrapper|assembledebug|assemblerelease|bundlerelease|" +
                        "assemble|bundleRelease|compiledebug|compileDebug|compileRelease|" +
                        "ktlint|detekt|lintDebug|lintVital|" +
                        "sdkmanager|android_home\\s*=|android_sdk|JAVA_HOME|javac\\b|" +
                        "platforms;android|build-tools;|cmdline-tools"
                ).containsMatchIn(cmdLower)
                if (looksLikeAndroidBuildToolchain) {
                    return@withContext "ERROR: Do not build, compile, or run Gradle/Android tooling here. " +
                        "CLOUD_SHELL / sandbox has NO reliable JDK+Android SDK for app builds — " +
                        "./gradlew assemble/test/lint will fail or waste many turns.\n\n" +
                        "Use instead:\n" +
                        "  • VERIFY or VERIFY_BUILD — instant offline static checks (braces, package, manifest…)\n" +
                        "  • GH_BUILD_APK — real APK on GitHub Actions (full toolchain), then GH_BUILD_STATUS\n" +
                        "Do not install sdkmanager/Java or probe JAVA_HOME here."
                }
                // Same problem, different shape: raw `git push`/`git remote`/`git clone` against
                // GitHub inside CLOUD_SHELL. That sandbox has NO GitHub credentials configured at
                // all, so these always fail or hang with a confusing "GitHub not connected" even
                // though the app's own GitHub connection (used by GH_PUSH/GH_BUILD_APK) is fine.
                val looksLikeManualGithubGit = Regex(
                    "git\\s+push|git\\s+remote\\s+add|git\\s+clone\\s+\\S*github\\.com|git\\s+pull\\s+\\S*github\\.com"
                ).containsMatchIn(cmdLower)
                if (looksLikeManualGithubGit) {
                    return@withContext "ERROR: Do not push/pull/clone GitHub with raw git commands here. CLOUD_SHELL " +
                        "has NO GitHub credentials configured — a manual git push/remote/clone against github.com " +
                        "will always fail or hang here, unrelated to whether GitHub is actually connected in the app. " +
                        "Use the dedicated tools instead, which already have the real connection wired in: GH_PUSH to " +
                        "commit and push your changes, or GH_BUILD_APK to push AND build in one step. Call one of " +
                        "those directly now."
                }
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to set it up, or fall back to RUN_PYTHON for simple Python."
                } else {
                    try {
                        val apiKey = prefs.getE2bApiKey()
                        val template = prefs.getE2bTemplate()
                        E2BClient.syncProjectToSandbox(
                            projectDir, apiKey, template, File(projectDir)
                        )
                        val res = E2BClient.exec(
                            projectDir, apiKey, template,
                            step.detail, "/workspace", 120
                        )
                        // Auto reverse-sync: pull new/changed cloud files into the phone project
                        // so READ_FILE / file cards work without a manual CLOUD_PULL.
                        val pull = FastSync.pullBackFromSandbox(
                            projectDir, apiKey, template, File(projectDir)
                        )
                        val pullNote = when {
                            pull.startsWith("pull: 0") ->
                                "\n[auto-sync] Project ↔ cloud up to date ($pull)."
                            pull.startsWith("pull:") ->
                                "\n[auto-sync] New/changed cloud files are now IN THE PROJECT: $pull. " +
                                    "You can READ_FILE them with project-relative paths (no CLOUD_PULL needed)."
                            else -> "\n[auto-sync] $pull"
                        }
                        "CLOUD SHELL:\n$ ${step.detail.take(500)}\n${res.formatted()}$pullNote"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        "ERROR: Cloud shell failed: ${e.message?.take(300)}"
                    }
                }
            }
            "apkdecompile" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to enable APK decompilation."
                } else {
                    CloudTools.decompileApk(context, projectDir, step.path, step.arg2.ifBlank { "decompiled_app" })
                }
            }
            "apkinfo" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to enable APK analysis."
                } else {
                    CloudTools.apkInfo(context, projectDir, step.path)
                }
            }
            "scansecrets" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to enable secret scanning."
                } else {
                    CloudTools.scanSecrets(context, projectDir, step.path)
                }
            }
            "securityaudit" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to enable security audits."
                } else {
                    CloudTools.securityAudit(context, projectDir, step.path)
                }
            }
            "webscan" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to enable web scanning."
                } else {
                    CloudTools.webScan(context, projectDir, step.arg2)
                }
            }
            // Image understanding: try the configured vision model first, OCR as fallback.
            "analyzeimage" -> MediaTools.analyzeImageVisionFirst(context, projectDir, step.path)
            // --- New cloud-powered tools (all need the E2B cloud engine) ---
            "cloudinstall", "recon", "portscan", "vulnscan", "dirfuzz", "niktoscan", "sqlitest",
            "sslscan", "urlharvest", "sast", "reposecrets", "siteclone", "apkrebuild", "imageedit",
            "cloudls", "cloudpull", "cloudpush", "socialosint", "phoneosint", "runjob", "jobstatus",
            "browseropen", "browserclick", "browsertype", "browserpress", "browserscroll", "browserupload", "browserselect", "browserback", "browserextract", "browserview",
            "computeropen", "computerclick", "computermove", "computertype", "computerkey", "computerscreenshot", "computerscroll",
            "xssscan", "cmditest", "crlfscan", "paramdiscover", "contentdiscover", "fastportscan", "deepsecrets", "wpscan" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to set it up."
                } else when (step.action) {
                    "cloudinstall" -> CloudTools.installPackages(context, projectDir, step.path, step.detail)
                    "recon" -> CyberTools.recon(context, projectDir, step.arg2)
                    "portscan" -> CyberTools.portScan(context, projectDir, step.arg2, step.detail)
                    "vulnscan" -> CyberTools.vulnScan(context, projectDir, step.arg2, step.detail)
                    "dirfuzz" -> CyberTools.dirFuzz(context, projectDir, step.arg2, step.detail)
                    "niktoscan" -> CyberTools.niktoScan(context, projectDir, step.arg2)
                    "sqlitest" -> CyberTools.sqliTest(context, projectDir, step.arg2, step.detail)
                    "sslscan" -> CyberTools.sslScan(context, projectDir, step.arg2)
                    "urlharvest" -> CyberTools.urlHarvest(context, projectDir, step.arg2)
                    "sast" -> CyberTools.sast(context, projectDir, step.path)
                    "reposecrets" -> CyberTools.repoSecrets(context, projectDir, step.path)
                    "xssscan" -> CyberTools.xssScan(context, projectDir, step.arg2)
                    "cmditest" -> CyberTools.cmdiTest(context, projectDir, step.arg2, step.detail)
                    "crlfscan" -> CyberTools.crlfScan(context, projectDir, step.arg2)
                    "paramdiscover" -> CyberTools.paramDiscover(context, projectDir, step.arg2)
                    "contentdiscover" -> CyberTools.contentDiscover(context, projectDir, step.arg2)
                    "fastportscan" -> CyberTools.fastPortScan(context, projectDir, step.arg2)
                    "deepsecrets" -> CyberTools.deepSecrets(context, projectDir, step.path)
                    "wpscan" -> CyberTools.wpScan(context, projectDir, step.arg2)
                    "socialosint" -> CyberTools.socialOsint(context, projectDir, step.arg2)
                    "phoneosint" -> CyberTools.phoneOsint(context, projectDir, step.arg2)
                    "runjob" -> CloudTools.runJob(context, projectDir, step.detail)
                    "jobstatus" -> CloudTools.jobStatus(context, projectDir, step.arg2)
                    "siteclone" -> CloudTools.cloneSite(context, projectDir, step.arg2, step.path, step.detail.trim().toIntOrNull() ?: 2)
                    "apkrebuild" -> CloudTools.rebuildApk(context, projectDir, step.path, step.arg2)
                    "imageedit" -> MediaTools.imageEdit(context, projectDir, step.path, step.arg2, step.detail, step.arg3)
                    "cloudls" -> CloudTools.cloudList(context, projectDir, step.path)
                    "cloudpull" -> CloudTools.cloudPull(context, projectDir, step.path, step.arg2)
                    "cloudpush" -> CloudTools.cloudPush(context, projectDir, step.path, step.arg2)
                    // ── Agentic BROWSER = Playwright headless Chromium (CloudBrowser) ──
                    // This is the path that was verified working (signed preview + /shot stream).
                    // Desktop (ComputerUse) is separate — only COMPUTER_* tools use it.
                    // Do NOT route BROWSER_* through desktop: wrong DISPLAY / empty wallpaper.
                    "browseropen" -> CloudBrowser.open(context, projectDir, step.arg2.ifBlank { "https://example.com" })
                    "browserclick" -> {
                        val idx = step.arg2.trim()
                        // Allow "x,y" only as a convenience → ignore and require index for Playwright
                        if (idx.isBlank() || idx.toIntOrNull() == null)
                            "ERROR: BROWSER_CLICK needs a valid element index (number). Call BROWSER_VIEW first, then click e.g. 3. You passed: '${step.arg2}'"
                        else CloudBrowser.click(context, projectDir, idx)
                    }
                    "browsertype" -> {
                        val idx = step.arg2.trim()
                        if (idx.isBlank() || idx.toIntOrNull() == null)
                            "ERROR: BROWSER_TYPE needs element index + text. Call BROWSER_VIEW first."
                        else CloudBrowser.type(context, projectDir, idx, step.detail)
                    }
                    "browserpress" -> CloudBrowser.press(context, projectDir, step.arg2)
                    "browserscroll" -> CloudBrowser.scroll(context, projectDir, step.arg2)
                    "browserupload" -> {
                        val idx = step.arg2.trim()
                        if (step.detail.isBlank())
                            "ERROR: BROWSER_UPLOAD needs an element index + file path(s). Call BROWSER_VIEW first."
                        else CloudBrowser.upload(context, projectDir, idx, step.detail)
                    }
                    "browserselect" -> {
                        val idx = step.arg2.trim()
                        if (idx.isBlank() || step.detail.isBlank())
                            "ERROR: BROWSER_SELECT needs an element index + option. Call BROWSER_VIEW first."
                        else CloudBrowser.select(context, projectDir, idx, step.detail)
                    }
                    "browserback" -> CloudBrowser.back(context, projectDir)
                    "browserextract" -> CloudBrowser.extract(context, projectDir)
                    "browserview" -> CloudBrowser.view(context, projectDir)
                    "browserrotate" -> CloudBrowser.rotate(context, projectDir)
                    "browserresize" -> {
                        val parts = step.arg2.split(Regex("[x,\\s]+"), limit = 2)
                        if (parts.size == 2) {
                            val w = parts[0].trim().toIntOrNull() ?: 1280
                            val h = parts[1].trim().toIntOrNull() ?: 820
                            CloudBrowser.setViewport(context, projectDir, w, h)
                        } else {
                            "ERROR: BROWSER_RESIZE needs dimensions like \"800x600\""
                        }
                    }
                    // ── Desktop computer-use (XFCE + noVNC) — explicit only ──
                    "computeropen" -> ComputerUse.open(context)
                    "computerclick" -> {
                        val (x, y) = parseXY(step.arg2)
                        val dbl = step.arg2.contains("double", ignoreCase = true) ||
                            step.detail.equals("double", ignoreCase = true)
                        if (x == null || y == null)
                            "ERROR: COMPUTER_CLICK needs coordinates like \"420,300\" (screen ${ComputerUse.SCREEN_W}x${ComputerUse.SCREEN_H})."
                        else ComputerUse.click(context, x, y, doubleClick = dbl)
                    }
                    "computermove" -> {
                        val (x, y) = parseXY(step.arg2)
                        if (x == null || y == null) "ERROR: COMPUTER_MOVE needs coordinates like \"420,300\"."
                        else ComputerUse.move(context, x, y)
                    }
                    "computertype" -> ComputerUse.type(context, step.detail.ifBlank { step.arg2 })
                    "computerkey" -> ComputerUse.key(context, step.arg2.ifBlank { "enter" })
                    "computerscreenshot" -> ComputerUse.screenshot(context)
                    "computerscroll" -> ComputerUse.scroll(context, step.arg2.ifBlank { "down" })
                    else -> "OK"
                }
            }
            "exporttodevice" -> {
                val f = ProjectManager.resolveFile(projectDir, step.path)
                if (f == null || !f.exists() || !f.isFile) "ERROR: project file not found: ${step.path}"
                else DeviceStorage.saveBytesToDownloads(
                    context, f.readBytes(), step.arg2.ifBlank { f.name }
                )
            }
            "generateimage" -> {
                val model = step.arg2.ifBlank { PreferencesManager(context).getImageModel() }
                ImageGenClient.generateToProject(context, projectDir, step.detail, step.path, model)
            }
            "screenshot" -> {
                // Fast multi-provider screenshot (thum.io / microlink / mshots).
                // Never fall back to full Playwright boot — that was causing 60–90s timeouts.
                val url = step.arg2.trim().ifBlank { step.path.trim() }
                val dest = if (step.path.isNotBlank() &&
                    (step.path.contains('.') || step.path.contains('/')) &&
                    !step.path.startsWith("http", ignoreCase = true)
                ) step.path else ""
                val shotDest = dest.ifBlank {
                    ".ahamai/artifacts/shot_${System.currentTimeMillis()}.png"
                }
                val result = WebScreenshot.captureToProject(projectDir, url, shotDest)
                if (result.startsWith("OK")) {
                    val path = Regex("""saved to (\S+)""").find(result)?.groupValues?.getOrNull(1)
                        ?: shotDest
                    val art = ArtifactStore.register(
                        projectDir, ArtifactStore.Kind.SCREENSHOT,
                        title = "Screenshot",
                        path = path,
                        url = WebScreenshot.normalizeUrl(url),
                        detail = result.take(200)
                    )
                    result + "\n" + ArtifactStore.marker(art)
                } else result
            }
            "runapp" -> {
                val skipShot = step.arg2.trim().equals("true", true) ||
                    step.arg2.trim().equals("1") ||
                    step.arg2.contains("skip", true)
                AppRunner.runAndCapture(context, projectDir, step.detail.trim(), skipShot)
            }
            "listartifacts" -> ArtifactStore.formatList(projectDir)
            "captureartifact" -> {
                val p = step.path.trim().removePrefix("./")
                if (p.isBlank()) "ERROR: CAPTURE_ARTIFACT needs a project-relative path."
                else if (!File(projectDir, p).exists()) "ERROR: file not found: $p"
                else {
                    val kind = when (step.arg2.trim().lowercase()) {
                        "screenshot", "image", "shot" -> ArtifactStore.Kind.SCREENSHOT
                        "log", "run", "run_log" -> ArtifactStore.Kind.RUN_LOG
                        "preview" -> ArtifactStore.Kind.PREVIEW
                        "walkthrough", "walk" -> ArtifactStore.Kind.WALKTHROUGH
                        "build" -> ArtifactStore.Kind.BUILD
                        "diff" -> ArtifactStore.Kind.DIFF
                        else -> if (p.endsWith(".png") || p.endsWith(".jpg") || p.endsWith(".webp"))
                            ArtifactStore.Kind.SCREENSHOT else ArtifactStore.Kind.OUTPUT
                    }
                    val art = ArtifactStore.register(
                        projectDir, kind,
                        title = step.detail.ifBlank { p.substringAfterLast('/') },
                        path = p
                    )
                    "OK: Registered artifact [${art.kind.label}] ${art.title}\n${ArtifactStore.marker(art)}"
                }
            }
            "unzip" -> ProjectManager.unzipInProject(projectDir, step.path, step.arg2)
            "zip" -> ProjectManager.zipInProject(projectDir, step.path, step.arg2)
            "pdfcreate" -> {
                // Guard: if path looks like content was passed as path (common model confusion),
                // swap so the PDF still gets created with a generated filename.
                val rawPath = step.path.trim()
                val rawContent = step.detail.trim()
                val (pdfPath, pdfContent) = if (rawPath.length > 100 || rawPath.contains("\n") || rawPath.startsWith("#")) {
                    "document.pdf" to rawPath
                } else if (rawContent.isBlank() && rawPath.isNotBlank() && !rawPath.lowercase().endsWith(".pdf")) {
                    "document.pdf" to rawPath
                } else {
                    rawPath to rawContent
                }
                PdfEngine.createPdf(projectDir, pdfPath, pdfContent, step.arg2, step.arg3)
            }
            "createxlsx" -> DocTools.createXlsx(context, projectDir, step.path, step.detail)
            "createcsv" -> DocTools.createCsv(projectDir, step.path, step.detail)
            "createpptx" -> DocTools.createPptx(context, projectDir, step.path, step.detail)
            "makeshorts" -> VideoTools.makeShorts(context, projectDir, step.path, step.detail, step.arg2)
            "createdocx" -> DocTools.createDocx(context, projectDir, step.path, step.detail)
            "pdfedit" -> PdfEngine.editText(projectDir, step.path, step.arg2, step.detail)
            "pdfaddpage" -> PdfEngine.addPage(projectDir, step.path, step.detail)
            "pdfaddimage" -> PdfEngine.addImage(projectDir, step.path, step.arg2, step.detail.toIntOrNull() ?: 1)
            "pdfaddchart" -> PdfEngine.addChart(projectDir, step.path, step.arg2, step.detail)
            "pdfmerge" -> PdfEngine.mergePdfs(projectDir, step.path, step.arg2, step.detail)
            "pdfsplit" -> PdfEngine.splitPdf(projectDir, step.path, step.arg2, step.detail)
            "pdfread" -> PdfEngine.readPdf(projectDir, step.path)
            "pdffillform" -> PdfEngine.fillForm(projectDir, step.path, step.detail)
            "pdfcompress" -> PdfEngine.compress(projectDir, step.path, step.detail, step.arg2)
            "pdfcollage" -> PdfEngine.collage(projectDir, step.path, step.arg2, step.detail)
            "scaffoldandroid" -> ProjectManager.applyAndroidTemplate(context, projectDir)
            // --- Next-level coder power-tools ---
            "multiedit" -> {
                val pairs = decodeMultiEditArgs(step.detail)
                if (pairs.isEmpty()) "ERROR: MULTI_EDIT needs at least one (old, new) pair."
                else {
                    // Atomic all-or-nothing: one disk write only if every pair succeeds.
                    val r = ProjectManager.multiEditFile(projectDir, step.path, pairs)
                    FastSync.invalidate(projectDir)
                    if (!r.startsWith("ERROR")) FileSessionTracker.markRead(projectDir, step.path)
                    r
                }
            }
            "symbolsearch" -> {
                val sym = step.detail.trim()
                if (sym.isBlank()) "ERROR: no symbol name provided."
                // Prefer the richer LSP-style index (same engine as GO_TO_DEFINITION)
                else LspTools.goToDefinition(projectDir, sym, pathScope = step.path)
            }
            "gotodefinition" -> {
                val sym = step.detail.trim()
                if (sym.isBlank()) "ERROR: GO_TO_DEFINITION needs a symbol name."
                else LspTools.goToDefinition(projectDir, sym, pathScope = step.path)
            }
            "findreferences" -> {
                val sym = step.detail.trim()
                if (sym.isBlank()) "ERROR: FIND_REFERENCES needs a symbol name."
                else LspTools.findReferences(projectDir, sym, pathScope = step.path)
            }
            "documentsymbols" -> {
                val p = step.path.trim()
                if (p.isBlank()) "ERROR: DOCUMENT_SYMBOLS needs a file path."
                else LspTools.documentSymbols(projectDir, p)
            }
            "workspacesymbols" -> {
                val q = step.detail.trim()
                if (q.isBlank()) "ERROR: WORKSPACE_SYMBOLS needs a query."
                else LspTools.workspaceSymbols(projectDir, q, pathScope = step.path)
            }
            "hover" -> {
                val sym = step.detail.trim()
                if (sym.isBlank()) "ERROR: HOVER needs a symbol name."
                else LspTools.hover(projectDir, sym, pathScope = step.path)
            }
            "codebasesearch" -> {
                val query = step.detail.trim()
                if (query.isBlank()) "ERROR: no search query provided."
                else semanticCodebaseSearch(projectDir, query)
            }
            "diagnose" -> {
                val target = step.path.ifBlank { "" }
                if (target.isBlank()) {
                    "ERROR: DIAGNOSE needs a file path (e.g. DIAGNOSE src/Main.kt)."
                } else {
                    // 1) Instant offline static (always)
                    val local = StaticVerifier.verifyFile(projectDir, target).format()
                    // 2) Optional cloud compiler diagnostics
                    val cloud = LspManager.diagnose(context, projectDir, target).formatted()
                    buildString {
                        append(local)
                        if (cloud.isNotBlank() && !cloud.contains("no issues") &&
                            !cloud.contains("skipping diagnostics") && !cloud.contains("cloud engine not")
                        ) {
                            append("\n\n").append(cloud)
                        } else if (cloud.contains("cloud engine not") || cloud.contains("skipping")) {
                            append("\n\n(cloud compiler diagnostics skipped — static verify above is enough for braces/syntax)")
                        }
                    }
                }
            }
            "verify" -> {
                val path = step.path.trim()
                // Single file: try safe auto-fix (braces/imports) then report
                if (path.isNotBlank() && !path.endsWith("/") &&
                    path.substringAfterLast('.').lowercase() in setOf("kt", "kts", "java", "js", "jsx", "ts", "tsx")
                ) {
                    StaticVerifier.verifyAndAutoFixFile(projectDir, path).format()
                } else {
                    val mode = step.detail.trim().ifBlank {
                        val br = ProjectManager.detectBuildRoot(projectDir)
                        val root = if (br.isBlank()) File(projectDir) else File(projectDir, br)
                        if (File(root, "build.gradle.kts").exists() || File(root, "build.gradle").exists() ||
                            root.walkTopDown().any { it.name == "AndroidManifest.xml" }
                        ) "android" else "quick"
                    }
                    StaticVerifier.verify(projectDir, pathScope = path, mode = mode).format()
                }
            }
            // VERIFY_BUILD / verifybuild — INSTANT offline APK preflight ONLY.
            // Never runs Gradle, never uses sandbox toolchain. Real APK = GH_BUILD_APK only.
            "verifybuild" -> {
                val br = ProjectManager.detectBuildRoot(projectDir)
                // Auto-fix Kotlin/Java under build root (instant, safe mechanical fixes only)
                val fixNotes = mutableListOf<String>()
                val root = if (br.isBlank()) File(projectDir) else File(projectDir, br)
                val candidates = root.walkTopDown()
                    .filter { it.isFile && it.extension.lowercase() in setOf("kt", "kts", "java") }
                    .filter { !it.path.contains("/build/") && !it.path.contains("/.gradle/") }
                    .take(50)
                    .toList()
                for (f in candidates) {
                    val rel = runCatching { f.relativeTo(File(projectDir)).path }.getOrElse { f.name }
                    val af = runCatching { StaticVerifier.verifyAndAutoFixFile(projectDir, rel) }.getOrNull()
                        ?: continue
                    if (af.applied) fixNotes.add("$rel: ${af.fixes.joinToString("; ")}")
                }
                val pre = ApkPreflight.check(projectDir, br)
                val full = StaticVerifier.verify(projectDir, pathScope = "", mode = "android")
                buildString {
                    if (fixNotes.isNotEmpty()) {
                        append("[static AUTO-FIX]\n")
                        fixNotes.forEach { append("  • $it\n") }
                        append("\n")
                    }
                    append(pre.format())
                    append("\n\n")
                    append(full.format(maxLines = 30))
                    append("\n\n[NOTE] STATIC-ONLY (no ./gradlew). Real APK → GH_BUILD_APK only.")
                    if (!pre.ok || !full.ok) {
                        append("\nFix remaining ERROR/BLOCKING items, re-run VERIFY_BUILD, then GH_BUILD_APK.")
                    }
                }
            }
            "loadtools" -> {
                val cat = step.detail.trim().lowercase()
                val docs = toolCategoryDocs(cat)
                if (docs.isBlank())
                    "No tool category '$cat'. Available: ${TOOL_CATEGORIES.keys.joinToString(", ")}"
                else "LOADED tools for '$cat'. You can now use these:\n\n$docs"
            }
            "importadd" -> {
                val target = ProjectManager.resolveFile(projectDir, step.path)
                    ?: File(projectDir, ProjectManager.cleanRelPath(step.path)).takeIf {
                        ProjectManager.isInsideProject(projectDir, it) && it.exists()
                    }
                if (target == null || !target.exists()) "ERROR: file not found (or outside sandbox): ${step.path}"
                else {
                    val imp = step.detail.trim()
                    val text = target.readText()
                    if (text.contains(imp)) "Import already present in ${step.path}."
                    else {
                        val ext = target.extension.lowercase()
                        val insertAt = computeImportInsertOffset(text, ext)
                        val newText = buildString {
                            append(text.substring(0, insertAt))
                            append(imp).append('\n')
                            append(text.substring(insertAt))
                        }
                        ProjectManager.writeFile(projectDir, step.path, newText)
                        FastSync.invalidate(projectDir)
                        "Added import to ${step.path}: $imp"
                    }
                }
            }
            "refactorrename" -> {
                val oldName = step.arg2.trim()
                val newName = step.detail.trim()
                val scopeFile = step.path.trim()
                if (oldName.isBlank() || newName.isBlank()) "ERROR: REFACTOR_RENAME needs old and new names."
                else {
                    val wordRegex = Regex("\\b${Regex.escape(oldName)}\\b")
                    val candidates = if (scopeFile.isBlank()) {
                        File(projectDir).walkTopDown().filter { it.isFile && !it.path.contains("/.git/") && !it.path.contains("/build/") && !it.path.contains("/node_modules/") }.toList()
                    } else listOf(File(projectDir, scopeFile))
                    var touched = 0
                    val sb = StringBuilder()
                    for (f in candidates) {
                        val text = runCatching { f.readText() }.getOrNull() ?: continue
                        if (!wordRegex.containsMatchIn(text)) continue
                        val newText = wordRegex.replace(text, newName)
                        if (newText != text) {
                            f.writeText(newText)
                            val rel = f.relativeTo(File(projectDir)).path
                            sb.append("  $rel\n")
                            touched++
                        }
                    }
                    FastSync.invalidate(projectDir)
                    if (touched == 0) "No occurrences of '$oldName' found."
                    else "Renamed '$oldName' → '$newName' in $touched file(s):\n$sb"
                }
            }
            "runtests", "lint", "formatcode" -> {
                // Always run instant offline static first (works with zero cloud/SDK).
                val isAndroidGradle = isAndroidGradleProject(projectDir)
                val staticMode = when {
                    step.action == "lint" && step.path.isNotBlank() -> {
                        StaticVerifier.verifyFile(projectDir, step.path).format()
                    }
                    isAndroidGradle -> StaticVerifier.verify(
                        projectDir, pathScope = step.path, mode = "android"
                    ).format()
                    else -> StaticVerifier.verify(
                        projectDir,
                        pathScope = step.path,
                        mode = if (step.action == "runtests") "full" else "quick"
                    ).format()
                }
                // Android/Gradle APK projects: NEVER launch ./gradlew in sandbox — no SDK support.
                // Static verify only; real compile/APK is GH_BUILD_APK.
                if (isAndroidGradle && step.action != "formatcode") {
                    return@withContext buildString {
                        append(staticMode)
                        append("\n\n[NOTE] Android project — sandbox does NOT run Gradle tests/lint/build. ")
                        append("Used INSTANT static verify only. For a real APK: VERIFY_BUILD (static) then GH_BUILD_APK (cloud).")
                        if (staticMode.contains("FAILED")) {
                            append("\nFix ERROR lines above before GH_BUILD_APK or DONE.")
                        }
                    }
                }
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    // Offline path: static verify IS the verification when cloud isn't set up.
                    buildString {
                        append(staticMode)
                        append("\n\n[NOTE] Cloud engine not configured — ran INSTANT offline static verify ")
                        append("(braces, strings, package/path, JSON/XML, Android preflight signals). ")
                        append("Configure Profile → Cloud Engine for non-Android test runners only. ")
                        append("APK builds always use GH_BUILD_APK, never sandbox Gradle.")
                        if (staticMode.contains("FAILED")) {
                            append("\nFix the ERROR lines above before calling DONE.")
                        }
                    }
                } else {
                    val cmd = when (step.action) {
                        "runtests" -> detectTestCommand(projectDir, step.detail)
                        "lint" -> detectLintCommand(projectDir, step.path)
                        "formatcode" -> detectFormatCommand(projectDir, step.path)
                        else -> ""
                    }
                    // Safety: never pass gradlew Android commands to sandbox even if detector returns them
                    if (cmd.contains("gradlew") || cmd.contains("assemble") || cmd.contains("compileDebug")) {
                        return@withContext staticMode +
                            "\n\n[BLOCKED] Refusing sandbox Gradle. Use VERIFY_BUILD + GH_BUILD_APK for Android."
                    }
                    if (cmd.isBlank()) {
                        staticMode + "\n\n(No cloud test/lint command auto-detected for this project type — static results above.)"
                    } else {
                        val apiKey = prefs.getE2bApiKey()
                        val template = prefs.getE2bTemplate()
                        FastSync.syncProjectAtOnce(projectDir, apiKey, template, File(projectDir))
                        val res = E2BClient.exec(projectDir, apiKey, template, cmd, "/workspace", 180)
                        "$staticMode\n\n${step.action.uppercase()} cloud ($cmd):\n${res.formatted(6000)}"
                    }
                }
            }
            "depsadd" -> {
                val manager = step.path.trim().lowercase()
                val spec = step.detail.trim()
                if (manager.isBlank() || spec.isBlank()) "ERROR: DEPS_ADD needs manager and package spec."
                else addDependency(projectDir, manager, spec, context)
            }
            "gitdiff" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured for git diff."
                } else {
                    val apiKey = prefs.getE2bApiKey()
                    val template = prefs.getE2bTemplate()
                    FastSync.syncProjectAtOnce(projectDir, apiKey, template, File(projectDir))
                    val init = E2BClient.exec(projectDir, apiKey, template,
                        "cd /workspace && git rev-parse --is-inside-work-tree 2>/dev/null || (git init && git add -A && git commit -m initial -q); git status --short; echo '---DIFF---'; git diff --stat; git diff",
                        "/workspace", 60)
                    "GIT DIFF:\n${init.formatted(8000)}"
                }
            }
            "gitcommit" -> {
                val prefs = PreferencesManager(context)
                if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                    "ERROR: Cloud engine not configured for git commit."
                } else {
                    val apiKey = prefs.getE2bApiKey()
                    val template = prefs.getE2bTemplate()
                    FastSync.syncProjectAtOnce(projectDir, apiKey, template, File(projectDir))
                    val msg = step.detail.ifBlank { "AhamAI checkpoint" }.replace("'", "'\\''")
                    val res = E2BClient.exec(projectDir, apiKey, template,
                        "cd /workspace && (git rev-parse --is-inside-work-tree 2>/dev/null || git init) && git add -A && git commit -m '$msg' -q && git log --oneline -3",
                        "/workspace", 60)
                    "GIT COMMIT '$msg':\n${res.formatted(3000)}"
                }
            }
            "snippetsave" -> {
                val dir = File(projectDir, ".ahamai/snippets").apply { mkdirs() }
                val name = step.path.trim().replace(Regex("[^A-Za-z0-9_.-]"), "_")
                if (name.isBlank()) "ERROR: snippet needs a name."
                else {
                    File(dir, "$name.txt").writeText("<!-- ${step.arg2} -->\n${step.detail}")
                    "Saved snippet '$name'."
                }
            }
            "snippetlist" -> {
                val dir = File(projectDir, ".ahamai/snippets")
                if (!dir.exists()) "No snippets saved yet."
                else {
                    val items = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
                    if (items.isEmpty()) "No snippets saved yet."
                    else "SNIPPETS (${items.size}):\n" + items.joinToString("\n") { "  ${it.name}" }
                }
            }
            "snippetload" -> {
                val name = step.path.trim().replace(Regex("[^A-Za-z0-9_.-]"), "_")
                val f = File(projectDir, ".ahamai/snippets/$name.txt")
                if (!f.exists()) "ERROR: snippet '$name' not found."
                else {
                    val content = f.readText().substringAfter("\n", "") // strip the description line
                    val dest = step.arg2.trim()
                    if (dest.isBlank()) "SNIPPET '$name':\n$content"
                    else {
                        File(projectDir, dest).writeText(content)
                        FastSync.invalidate(projectDir)
                        "Loaded snippet '$name' → $dest"
                    }
                }
            }
            "ghpr", "ghissues", "ghcreateissue", "ghbuild", "ghbuildstatus", "ghlogs",
            "ghrepos", "ghbranches", "ghreadremote", "ghlistremote", "ghcreaterepo",
            "ghcreatebranch", "ghswitchbranch", "ghopenrepo", "ghstatus" -> {
                val prefs = PreferencesManager(context)
                val token = prefs.getGithubToken()
                val repo = prefs.getConnectedRepo()
                val branch = prefs.getConnectedBranch()
                // Tools that only need a token (not a connected repo)
                when (step.action) {
                    "ghrepos" -> return@withContext GitHubClient.listReposText(token, step.detail)
                    "ghstatus" -> {
                        if (token.isBlank()) return@withContext "GitHub: NOT connected. Ask user to open Repository on Agent home and login/paste a token (scopes: repo, workflow)."
                        val user = GitHubClient.getUser(token)
                        return@withContext buildString {
                            appendLine("GitHub: connected as ${user?.login ?: "?"} (${user?.name ?: ""})")
                            if (repo.isNotBlank()) {
                                appendLine("Connected repo: $repo")
                                appendLine("Connected branch: ${branch.ifBlank { "(default)" }}")
                                appendLine("Local project: $projectDir")
                            } else {
                                appendLine("No repo connected yet. Use GH_LIST_REPOS then GH_OPEN_REPO owner/name.")
                            }
                        }
                    }
                    "ghcreaterepo" -> {
                        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                        val name = step.path.trim().ifBlank { "ahamai-project" }
                        val priv = !step.arg2.trim().equals("public", ignoreCase = true) &&
                            !step.arg2.trim().equals("false", ignoreCase = true)
                        return@withContext GitHubClient.createRepoAndConnect(
                            context, token, name, priv, step.detail.ifBlank { "Created with AhamAI" }
                        )
                    }
                    "ghopenrepo" -> {
                        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                        return@withContext GitHubClient.openRepoLocally(
                            context, token, step.path.trim(), step.arg2.trim()
                        )
                    }
                    "ghbranches" -> {
                        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                        val r = step.path.trim().ifBlank { repo }
                        if (r.isBlank()) return@withContext "ERROR: No repo. Pass owner/name or connect one first."
                        return@withContext GitHubClient.listBranchesText(token, r)
                    }
                    "ghreadremote" -> {
                        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                        val r = step.arg2.trim().ifBlank { repo }
                        if (r.isBlank()) return@withContext "ERROR: No repo. Pass repo as 2nd arg or connect one."
                        return@withContext GitHubClient.readRemoteFile(
                            token, r, step.path.trim(), step.arg3.trim().ifBlank { branch }
                        )
                    }
                    "ghlistremote" -> {
                        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                        val r = step.arg2.trim().ifBlank { repo }
                        if (r.isBlank()) return@withContext "ERROR: No repo. Pass repo as 2nd arg or connect one."
                        return@withContext GitHubClient.listRemotePath(
                            token, r, step.path.trim(), step.arg3.trim().ifBlank { branch }
                        )
                    }
                    "ghcreatebranch" -> {
                        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                        val r = step.detail.trim().ifBlank { repo }
                        if (r.isBlank()) return@withContext "ERROR: No repo connected. GH_OPEN_REPO or GH_CREATE_REPO first."
                        val switchLocal = !step.arg3.trim().equals("false", ignoreCase = true) &&
                            !step.arg3.trim().equals("no", ignoreCase = true) &&
                            !step.arg3.trim().equals("remote-only", ignoreCase = true)
                        return@withContext GitHubClient.createBranchAndMaybeSwitch(
                            context, token, step.path.trim(), step.arg2.trim(), switchLocal, r
                        )
                    }
                    "ghswitchbranch" -> {
                        if (token.isBlank()) return@withContext "ERROR: GitHub not connected."
                        return@withContext GitHubClient.switchBranchLocally(
                            context, token, step.path.trim(), step.arg2.trim().ifBlank { repo }
                        )
                    }
                }
                // Remaining tools need connected repo
                if (token.isBlank() || repo.isBlank()) "ERROR: No GitHub repo connected. Connect one first (or GH_OPEN_REPO / GH_CREATE_REPO)."
                else when (step.action) {
                    "ghpr" -> GitHubClient.createPullRequest(token, repo, step.path,
                        step.arg2.ifBlank { branch }, step.arg3.ifBlank { branch }, step.detail)
                    "ghissues" -> GitHubClient.listIssues(token, repo)
                    "ghcreateissue" -> GitHubClient.createIssue(token, repo, step.path, step.detail)
                    "ghlogs" -> GitHubClient.buildLogs(token, repo)
                    "ghbuild" -> {
                        // NEVER build from the user's permanent connected repo — always a
                        // throwaway ahamai-build-* public repo that is wiped after the run.
                        // (UI path uses buildApkFlow; this is a safety net if executeStep is hit.)
                        runCatching { GitHubClient.cleanupOrphanTempBuildRepos(token) }
                        "Use the agent GH_BUILD_APK path (creates a temporary public repo that is " +
                            "auto-deleted after success OR failure). If you already called GH_BUILD_APK, " +
                            "poll with GH_BUILD_STATUS. Connected repo=$repo is not used for cloud APK builds."
                    }
                    "ghbuildstatus" -> {
                        val prefs = PreferencesManager(context)
                        val buildRepo = prefs.getConnectedRepo().ifBlank { repo }
                        val st = GitHubClient.latestBuildState(token, buildRepo)
                        if (st == null) {
                            // Still wipe orphans if nothing is running
                            runCatching { GitHubClient.cleanupOrphanTempBuildRepos(token) }
                            "No build runs found yet."
                        } else if (st.status != "completed") {
                            "Build still ${st.status}. Call GH_BUILD_STATUS again to keep waiting. (run: ${st.htmlUrl})"
                        } else if (st.conclusion == "success") {
                            val saved = runCatching {
                                GitHubClient.downloadBuildArtifact(context, token, buildRepo, st.runId)
                            }.getOrElse { "APK download: ${it.message}" }
                            val del = wipeBuildRepoAlways(token, buildRepo, prefs)
                            runCatching { GitHubClient.cleanupOrphanTempBuildRepos(token) }
                            "Build SUCCESS. $saved\nTemp build repo cleanup: $del"
                        } else {
                            val logs = runCatching { GitHubClient.buildLogs(token, buildRepo) }.getOrDefault("(no logs)")
                            val del = wipeBuildRepoAlways(token, buildRepo, prefs)
                            runCatching { GitHubClient.cleanupOrphanTempBuildRepos(token) }
                            "Build ${st.conclusion}.\n--- build errors ---\n$logs\n---\nTemp build repo cleanup: $del\nFix locally, then build again."
                        }
                    }
                    else -> "OK"
                }
            }
            // ── Live preview tools: emit a sentinel token the UI scans for and renders inline.
            "previewweb" -> {
                val target = java.io.File(projectDir, step.path)
                if (!target.exists()) "ERROR: ${step.path} not found. List files first."
                else {
                    val art = ArtifactStore.register(
                        projectDir, ArtifactStore.Kind.PREVIEW,
                        title = "Web preview",
                        path = step.path,
                        language = "html"
                    )
                    // Emit a special marker that CodeAgentScreen parses to show the WebView inline.
                    "[[PREVIEW_WEB_APP]]${step.path}[[/PREVIEW_WEB_APP]]\n" +
                        "Preview of ${step.path} is now visible above.\n" +
                        ArtifactStore.marker(art)
                }
            }
            "renderdiagram" -> renderDiagram(projectDir, step.arg2, step.detail)
            "renderchart" -> {
                val kind = step.arg2.lowercase().ifBlank { "bar" }
                val title = step.path.ifBlank { "" }
                "[[RENDER_CHART]]$kind|$title\n${step.detail}[[/RENDER_CHART]]\nChart rendered above."
            }
            // ===== NEW Z-AI LEVEL TOOLS =====
            "loadskill" -> {
                val skillId = step.detail.trim()
                val arguments = step.arg2.trim()
                SkillRuntime.setProjectContext(projectDir)
                val activated = SkillRuntime.activate(context, skillId, arguments, projectDir)
                if (activated != null) {
                    StructuredTools.markCategoryLoaded(activated.skill.id)
                    var msg = SkillRuntime.formatLoadResponse(activated, arguments)
                    // context: fork — run skill body as isolated sub-agent (Claude-style)
                    if (activated.forked) {
                        val forkTask = activated.rendered +
                            if (arguments.isNotBlank()) "\n\nARGUMENTS: $arguments" else ""
                        val sub = runCatching {
                            val prefs = PreferencesManager(context)
                            val model = prefs.getAgentModel().ifBlank { prefs.getModel() }
                            val ep = ApiConfig.resolveForModel(context, model, agent = true)
                            val base = ep.baseUrl.ifBlank { prefs.getBaseUrl() }
                            val key = ep.apiKey.ifBlank { prefs.getApiKey() }
                            if (base.isBlank() || key.isBlank() || model.isBlank()) {
                                "FORK skipped: model/API not configured."
                            } else {
                                runSubAgent(
                                    context, projectDir, base, key, model,
                                    task = forkTask,
                                    maxTurns = 10
                                )
                            }
                        }.getOrElse { "FORK sub-agent error: ${it.message}" }
                        msg += "\n\n═══ FORKED SUB-AGENT RESULT ═══\n$sub"
                    }
                    msg
                } else {
                    val avail = SkillManager.availableSkillIds().joinToString(", ")
                    "ERROR: Unknown skill '$skillId'. Available: $avail"
                }
            }
            "listskillresources" -> {
                val skillId = step.detail.trim()
                SkillRuntime.setProjectContext(projectDir)
                val skill = SkillRuntime.findAnywhere(skillId)
                    ?: return@withContext "ERROR: Unknown skill '$skillId'."
                val dir = SkillRuntime.materializePackage(skill)
                val fromDisk = SkillRuntime.loadPackageFromDir(dir)
                val keys = (fromDisk?.resources ?: skill.resources).keys.sorted()
                if (keys.isEmpty()) {
                    "Skill `${skill.id}` has no package resources. Dir: ${dir.absolutePath}"
                } else {
                    "Resources for `${skill.id}` (${dir.absolutePath}):\n" +
                        keys.joinToString("\n") { "  - $it" } +
                        "\nREAD_SKILL_RESOURCE / RUN_SKILL_SCRIPT as needed."
                }
            }
            "readskillresource" -> {
                val skillId = step.detail.trim()
                val path = step.path.trim()
                if (skillId.isBlank() || path.isBlank()) {
                    return@withContext "ERROR: READ_SKILL_RESOURCE needs skill_name and path."
                }
                SkillRuntime.setProjectContext(projectDir)
                val skill = SkillRuntime.findAnywhere(skillId)
                    ?: return@withContext "ERROR: Unknown skill '$skillId'."
                val dir = SkillRuntime.materializePackage(skill)
                val file = java.io.File(dir, path.removePrefix("./"))
                val body = when {
                    file.isFile -> runCatching { file.readText() }.getOrNull()
                    else -> SkillManager.readResource(skillId, path)
                        ?: skill.resources[path]
                        ?: skill.resources.entries.find {
                            it.key.endsWith(path) || it.key.equals(path, true)
                        }?.value
                }
                if (body == null) {
                    val keys = SkillRuntime.loadPackageFromDir(dir)?.resources?.keys?.sorted().orEmpty()
                    "ERROR: Resource '$path' not found. Available: ${keys.joinToString(", ").ifBlank { "(none)" }}"
                } else {
                    "SKILL RESOURCE `$skillId` → `$path`:\n\n$body"
                }
            }
            "runskillscript" -> {
                val skillId = step.detail.trim()
                val script = step.path.trim()
                val args = step.arg2.trim()
                if (skillId.isBlank() || script.isBlank()) {
                    return@withContext "ERROR: RUN_SKILL_SCRIPT needs skill_name and path (e.g. scripts/run.py)."
                }
                SkillRuntime.setProjectContext(projectDir)
                SkillRuntime.runSkillScript(context, skillId, script, args, projectDir)
            }
            "saveskill" -> {
                val skillMd = step.detail.trim()
                if (skillMd.isBlank()) {
                    return@withContext "ERROR: SAVE_SKILL requires skill_md (full SKILL.md with frontmatter)."
                }
                val replaceFlag = step.arg2.trim().lowercase().let {
                    it in setOf("true", "1", "yes", "replace", "overwrite") ||
                        step.action.equals("replaceskill", true)
                }
                val result = SkillManager.saveSkillFromAgent(
                    skillMd = skillMd,
                    replace = replaceFlag,
                    resourcesJson = step.arg3.takeIf { it.isNotBlank() }
                )
                if (result.ok) {
                    // Pin newly saved skill into session so agent can use it immediately
                    SkillManager.loadSkill(result.skillId)
                    result.message + "\nYou may LOAD_SKILL ${result.skillId} or the user can type /${result.skillId}."
                } else {
                    result.message
                }
            }
            "worklog" -> Worklog.append(projectDir, step.detail, tag = "Agent")
            "remember" -> ContextMemoryManager.remember(projectDir, step.detail, source = "remember")
            "forget" -> ContextMemoryManager.forget(projectDir, step.detail)
            "memory" -> {
                val block = ContextMemoryManager.buildMemoryBlock(projectDir, worklogExtra = true)
                val q = step.detail.trim()
                if (block.isBlank()) "Context memory is empty for this project."
                else if (q.isBlank()) "CONTEXT MEMORY:\n$block"
                else {
                    val lines = block.lines().filter { it.contains(q, ignoreCase = true) }
                    if (lines.isEmpty()) "No memory lines matched \"$q\". Full catalog:\n${block.take(2000)}"
                    else "MEMORY matches for \"$q\":\n" + lines.joinToString("\n")
                }
            }
            "askuser" -> {
                // This is handled at the SCREEN level (CodeAgentScreen) — the agent loop
                // pauses and shows the question UI. If it reaches executeStep, it means
                // the screen didn't intercept it, so return a sentinel for the screen to catch.
                "[[ASK_USER]]${step.detail}[[/ASK_USER]]"
            }
            "paralleltasks" -> {
                // Parsed and handled at the screen level. If it reaches here, it's an error.
                "ERROR: PARALLEL_TASKS must be handled at the agent loop level (CodeAgentScreen). This should not reach executeStep."
            }
            else -> {
                // Connector tools (Vercel / Render / Cloudflare / Netlify / Supabase / Railway):
                // route ANY connector tool generically so newly-added tools work without being
                // whitelisted here. Arg keys are normalised to lowercase because the connector
                // clients look them up in lowercase (e.g. args["projectid"]), while the model
                // emits schema-cased keys (projectId / serviceId) — that mismatch used to make
                // every id-taking tool (including deploys) fail with "<x> required".
                val action = step.action.lowercase()
                if (com.ahamai.app.data.ConnectorsManager.isConnectorTool(action)) {
                    val args = parseConnectorArgs(listOf(step.path, step.arg2, step.detail))
                    com.ahamai.app.data.ConnectorsManager.executeTool(action, args)
                } else "OK"
            }
        }
    }

    /**
     * Parse connector-tool arguments out of the raw step fields. Accepts BOTH a JSON object
     * (e.g. {"serviceId":"srv_x"}) and comma/newline-separated key:value pairs
     * (e.g. serviceId: srv_x, commitId: abc). All keys are lowercased and surrounding quotes
     * stripped so the connector clients — which read args["serviceid"] / args["service_id"] —
     * find them regardless of the casing the model used.
     */
    private fun parseConnectorArgs(raw: List<String>): Map<String, String> {
        val args = mutableMapOf<String, String>()
        fun clean(v: String) = v.trim().trim('"', '\'', '`').trim()
        for (s0 in raw) {
            val s = s0.trim()
            if (s.isBlank()) continue
            val jsonStr = when {
                s.startsWith("{") -> s
                s.startsWith("```") -> s.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                else -> ""
            }
            if (jsonStr.startsWith("{")) {
                try {
                    val o = org.json.JSONObject(jsonStr)
                    for (k in o.keys()) args[k.lowercase()] = clean(o.get(k).toString())
                    continue
                } catch (_: Exception) { /* fall through to KV parsing */ }
            }
            if (s.contains(":")) {
                // Split on commas or newlines into key:value pairs.
                s.split(",", "\n").forEach { p ->
                    val kv = p.trim().split(":", limit = 2)
                    if (kv.size == 2 && kv[0].isNotBlank()) args[clean(kv[0]).lowercase()] = clean(kv[1])
                }
            }
        }
        return args
    }

    /**
     * Render a diagram to a PNG DIRECTLY from the app (no cloud / E2B needed) — one step, instant.
     * Encodes the source and fetches a rendered PNG from Kroki (primary) with a mermaid.ink fallback.
     * Saves the image into the project and returns a sentinel the UI parses to show it inline.
     */
    private fun renderDiagram(projectDir: String, typeArg: String, rawSource: String): String {
        // Clean the source: strip any ```mermaid / ``` fences the model may have wrapped it in.
        val source = rawSource.trim()
            .removePrefix("```mermaid").removePrefix("```graphviz").removePrefix("```dot")
            .removePrefix("```plantuml").removePrefix("```")
            .removeSuffix("```").trim()
        if (source.isBlank()) return "ERROR: No diagram source provided. Pass valid Mermaid (e.g. 'flowchart TD\\n A-->B')."

        // Map the requested type to a Kroki renderer. Mermaid is the default (flowchart/graph/sequence…).
        val t = typeArg.lowercase().trim()
        val krokiType = when {
            t.contains("graphviz") || t == "dot" -> "graphviz"
            t.contains("plantuml") || t == "puml" -> "plantuml"
            else -> "mermaid"
        }
        val outPath = "diagram_${System.currentTimeMillis()}.png"

        // 1) Kroki: deflate + base64url of the raw source -> GET /<type>/png/<encoded>
        val krokiUrl = "https://kroki.io/$krokiType/png/${deflateBase64Url(source)}"
        var res = ProjectManager.downloadUrl(projectDir, krokiUrl, outPath)
        if (res.startsWith("OK")) {
            return "OK: Diagram rendered as image at $outPath (renders inline for the user).\n" +
                "[[DIAGRAM_IMAGE]]$outPath[[/DIAGRAM_IMAGE]]"
        }

        // 2) Fallback (mermaid only): mermaid.ink expects base64url of a JSON {code, mermaid:{...}}.
        if (krokiType == "mermaid") {
            val json = org.json.JSONObject()
                .put("code", source)
                .put("mermaid", org.json.JSONObject().put("theme", "default"))
                .toString()
            val b64 = android.util.Base64.encodeToString(
                json.toByteArray(Charsets.UTF_8),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            val inkUrl = "https://mermaid.ink/img/$b64?type=png&scale=2&bgColor=FFFFFF"
            res = ProjectManager.downloadUrl(projectDir, inkUrl, outPath)
            if (res.startsWith("OK")) {
                return "OK: Diagram rendered as image at $outPath (renders inline for the user).\n" +
                    "[[DIAGRAM_IMAGE]]$outPath[[/DIAGRAM_IMAGE]]"
            }
        }

        // Both failed — almost always a diagram-syntax error. Surface it so the model can fix + retry.
        return "ERROR: Could not render the diagram (the syntax is likely invalid). " +
            "Fix the Mermaid syntax and call RENDER_DIAGRAM again.\nSource was:\n$source"
    }

    /** deflate (zlib) + base64url (no padding) — the encoding Kroki expects for GET requests. */
    private fun deflateBase64Url(s: String): String {
        val input = s.toByteArray(Charsets.UTF_8)
        val deflater = java.util.zip.Deflater(java.util.zip.Deflater.BEST_COMPRESSION)
        deflater.setInput(input); deflater.finish()
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!deflater.finished()) { val n = deflater.deflate(buf); out.write(buf, 0, n) }
        deflater.end()
        return android.util.Base64.encodeToString(
            out.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
        )
    }

    /** Human-readable label for an action (for the UI log). */
    fun actionLabel(step: AgentStep): Pair<String, String> = when (step.action) {
        "read" -> "Reading" to step.path
        "readfiles" -> "Batch reading" to step.detail.ifBlank { step.path }.take(80)
        "readlines" -> "Reading" to "${step.path} (${step.arg2}-${step.detail})"
        "edit" -> "Editing" to step.path
        "insertlines" -> "Inserting lines" to "${step.path} @${step.arg2}"
        "applypatch" -> "Applying patch" to step.path.ifBlank { "patch" }
        "write" -> "Writing" to step.path
        "create" -> "Creating" to step.path
        "delete" -> "Deleting" to step.path
        "copy" -> "Copying" to "${step.path} → ${step.arg2}"
        "move" -> "Moving" to "${step.path} → ${step.arg2}"
        "download" -> "Downloading" to step.arg2
        "downloaddevice" -> "Saving to Downloads" to step.arg2
        "listdownloads" -> "Listing Downloads" to ""
        "importdownload" -> "Importing from Downloads" to step.path
        "fetch" -> "Fetching" to step.arg2
        "websearch" -> "Searching the web" to step.detail
        "imagesearch" -> "Searching images" to step.detail
        "readurl" -> "Reading" to step.arg2
        "http" -> "Testing endpoint" to "${step.arg2.ifBlank { "GET" }} ${step.path}"
        "checkhtml" -> "Checking HTML" to step.path
        "search" -> "Searching" to step.detail
        "bulkedit" -> "Bulk replacing" to "\"${step.arg2}\" → \"${step.detail}\""
        "grep" -> "Grep" to step.detail
        "glob" -> "Finding files" to step.detail
        "diffhistory" -> "Recent changes" to step.path
        "repomap" -> "Mapping codebase" to step.path
        "codebasewiki" -> "Generating wiki" to ""
        "list" -> "Listing files" to ""
        "runpython" -> "Running Python" to ""
        "cloudshell" -> "Cloud shell" to step.detail.take(80)
        "apkdecompile" -> "Decompiling APK" to step.path
        "apkinfo" -> "Reading APK info" to step.path
        "scansecrets" -> "Scanning secrets" to step.path
        "securityaudit" -> "Running security audit" to step.path
        "webscan" -> "Scanning web app" to step.arg2
        "cloudinstall" -> "Installing in cloud" to "${step.path} ${step.detail}".trim()
        "recon" -> "Recon" to step.arg2
        "browseropen" -> "Opening page" to step.arg2
        "browserclick" -> "Clicking" to step.arg2
        "browsertype" -> "Typing" to (step.detail.ifBlank { step.arg2 }).take(40)
        "browserpress" -> "Pressing key" to step.arg2
        "browserscroll" -> "Scrolling" to step.arg2
        "browserupload" -> "Uploading" to (step.detail.substringAfterLast('/').ifBlank { step.detail }).take(40)
        "browserselect" -> "Selecting" to step.detail.take(40)
        "browserback" -> "Going back" to ""
        "browserextract" -> "Reading page" to ""
        "browserview" -> "Looking at page" to ""
        "computeropen" -> "Opening desktop" to ""
        "computerclick" -> "Clicking" to step.arg2
        "computermove" -> "Moving pointer" to step.arg2
        "computertype" -> "Typing" to (step.detail.ifBlank { step.arg2 }).take(40)
        "computerkey" -> "Key" to step.arg2
        "computerscreenshot" -> "Screenshot" to ""
        "computerscroll" -> "Scrolling" to step.arg2
        "portscan" -> "Port scan" to step.arg2
        "vulnscan" -> "Vuln scan (nuclei)" to step.arg2
        "dirfuzz" -> "Fuzzing paths" to step.arg2
        "niktoscan" -> "Nikto scan" to step.arg2
        "sqlitest" -> "SQLi test" to step.arg2
        "sslscan" -> "TLS scan" to step.arg2
        "urlharvest" -> "Harvesting URLs" to step.arg2
        "xssscan" -> "XSS scan (dalfox)" to step.arg2
        "cmditest" -> "Cmd-injection test" to step.arg2
        "crlfscan" -> "CRLF scan" to step.arg2
        "paramdiscover" -> "Param discovery" to step.arg2
        "contentdiscover" -> "Content discovery" to step.arg2
        "fastportscan" -> "Fast port scan" to step.arg2
        "deepsecrets" -> "Deep secret scan" to step.path
        "wpscan" -> "WordPress scan" to step.arg2
        "sast" -> "Static analysis" to step.path
        "reposecrets" -> "Scanning for secrets" to step.path
        "socialosint" -> "Username OSINT" to step.arg2
        "phoneosint" -> "Phone OSINT" to step.arg2
        "runjob" -> "Started background job" to step.detail.take(60)
        "jobstatus" -> "Checking job" to step.arg2
        "siteclone" -> "Cloning website" to step.arg2
        "apkrebuild" -> "Rebuilding APK" to step.path
        "imageedit" -> "Editing image" to "${step.path} (${step.arg2})"
        "cloudls" -> "Listing cloud files" to step.path
        "cloudpull" -> "Pulling from cloud" to "${step.path} → ${step.arg2}"
        "cloudpush" -> "Pushing to cloud" to "${step.path} → ${step.arg2}"
        "exporttodevice" -> "Saving to Downloads" to step.path
        "generateimage" -> "Generating image" to step.detail.take(60)
        "screenshot" -> "Screenshotting" to step.arg2
        "analyzeimage" -> "Analyzing image (vision)" to step.path
        "unzip" -> "Unzipping" to step.path
        "zip" -> "Zipping" to step.path
        "pdfcreate" -> "Creating PDF" to step.path
        "createxlsx" -> "Creating Excel" to step.path
        "createcsv" -> "Creating CSV" to step.path
        "createpptx" -> "Creating PowerPoint" to step.path
        "makeshorts" -> "Creating viral shorts" to step.path
        "createdocx" -> "Creating Word doc" to step.path
        "pdfedit" -> "Editing PDF text" to step.path
        "pdfaddpage" -> "Adding page to PDF" to step.path
        "pdfaddimage" -> "Adding image to PDF" to step.path
        "pdfaddchart" -> "Creating chart PDF" to step.path
        "pdfmerge" -> "Merging PDFs" to step.detail
        "pdfsplit" -> "Splitting PDF" to step.path
        "pdfread" -> "Reading PDF" to step.path
        "pdffillform" -> "Filling PDF form" to step.path
        "pdfcompress" -> "Compressing PDF" to step.path
        "pdfcollage" -> "Creating collage" to step.arg2
        "scaffoldandroid" -> "Adding Android template" to ""
        "previewweb" -> "Previewing web app" to step.path
        "runapp" -> "Running app + capture" to step.detail.take(40)
        "listartifacts" -> "Listing artifacts" to ""
        "captureartifact" -> "Saving artifact" to step.path
        "renderdiagram" -> "Rendering diagram" to step.arg2
        "renderchart" -> "Rendering chart" to step.arg2
        "ghpush" -> "Pushing to GitHub" to step.arg2
        "ghpr" -> "Opening pull request" to step.path
        "ghissues" -> "Listing issues" to ""
        "ghcreateissue" -> "Creating issue" to step.path
        "ghbuild" -> "Building APK (cloud)" to ""
        "ghbuildstatus" -> "Checking build status" to ""
        "ghlogs" -> "Reading build logs" to ""
        "ghrepos" -> "Listing GitHub repos" to step.detail
        "ghbranches" -> "Listing branches" to step.path
        "ghreadremote" -> "Reading remote file" to step.path
        "ghlistremote" -> "Browsing remote tree" to step.path
        "ghcreaterepo" -> "Creating repository" to step.path
        "ghcreatebranch" -> "Creating branch" to step.path
        "ghswitchbranch" -> "Switching branch" to step.path
        "ghopenrepo" -> "Opening repository" to step.path
        "ghstatus" -> "GitHub status" to ""
        "multiedit" -> "Multi-editing" to step.path
        "symbolsearch" -> "Searching symbol" to step.detail
        "gotodefinition" -> "Go to definition" to step.detail
        "findreferences" -> "Finding references" to step.detail
        "documentsymbols" -> "Document symbols" to step.path
        "workspacesymbols" -> "Workspace symbols" to step.detail
        "hover" -> "Hover" to step.detail
        "codebasesearch" -> "Searching codebase" to step.detail
        "task" -> "Delegating to sub-agent" to step.detail.take(60)
        "loadtools" -> "Loading tools" to step.detail
        "importadd" -> "Adding import" to step.path
        "refactorrename" -> "Renaming" to "${step.arg2} → ${step.detail}"
        "runtests" -> "Running tests" to step.detail
        "lint" -> "Linting" to step.path
        "verify" -> "Static verify" to step.path.ifBlank { step.detail.ifBlank { "project" } }
        "verifybuild" -> "APK preflight" to ""
        "formatcode" -> "Formatting" to step.path
        "depsadd" -> "Adding dependency" to step.detail
        "gitdiff" -> "Showing git diff" to ""
        "gitcommit" -> "Committing" to step.detail
        "snippetsave" -> "Saving snippet" to step.path
        "snippetlist" -> "Listing snippets" to ""
        "snippetload" -> "Loading snippet" to step.path
        "diagnose" -> "Running diagnostics" to step.path
        "askuser" -> "Waiting for your input" to ""
        "loadskill" -> "Loading skill" to step.detail
        "listskillresources" -> "Listing skill resources" to step.detail
        "readskillresource" -> "Reading skill resource" to step.path.ifBlank { step.detail }
        "runskillscript" -> "Running skill script" to step.path.ifBlank { step.detail }
        "saveskill" -> "Saving skill" to (step.path.ifBlank { "custom skill" })
        "worklog" -> "Updating worklog" to ""
        "remember" -> "Remembering" to step.detail.take(60)
        "forget" -> "Forgetting" to step.detail.take(60)
        "memory" -> "Reading memory" to step.detail.take(40)
        "paralleltasks" -> "Launching parallel agents" to ""
        else -> "Working" to ""
    }

    /** Past-tense label shown AFTER an action finishes (e.g. "Reading" -> "Read"). */
    fun actionLabelPast(step: AgentStep): String = when (step.action) {
        "read", "readlines", "readfiles" -> "Read"
        "edit" -> "Edited"
        "insertlines" -> "Inserted lines"
        "applypatch" -> "Applied patch"
        "write" -> "Wrote"
        "create" -> "Created"
        "delete" -> "Deleted"
        "copy" -> "Copied"
        "move" -> "Moved"
        "download" -> "Downloaded"
        "downloaddevice" -> "Saved to Downloads"
        "listdownloads" -> "Listed Downloads"
        "importdownload" -> "Imported"
        "fetch" -> "Fetched"
        "websearch" -> "Searched the web"
        "imagesearch" -> "Searched images"
        "readurl" -> "Read"
        "http" -> "Tested endpoint"
        "checkhtml" -> "Checked HTML"
        "search" -> "Searched"
        "bulkedit" -> "Bulk replaced"
        "grep" -> "Searched"
        "glob" -> "Found files"
        "diffhistory" -> "Reviewed changes"
        "repomap" -> "Mapped codebase"
        "codebasewiki" -> "Generated wiki"
        "list" -> "Listed files"
        "runpython" -> "Ran Python"
        "cloudshell" -> "Cloud command"
        "apkdecompile" -> "Decompiled APK"
        "apkinfo" -> "Read APK info"
        "scansecrets" -> "Scanned secrets"
        "securityaudit" -> "Ran security audit"
        "webscan" -> "Scanned web app"
        "cloudinstall" -> "Installed in cloud"
        "recon" -> "Recon done"
        "browseropen" -> "Opened page"
        "browserclick" -> "Clicked"
        "browsertype" -> "Typed"
        "browserpress" -> "Key pressed"
        "browserscroll" -> "Scrolled"
        "browserupload" -> "Uploaded"
        "browserselect" -> "Selected"
        "browserback" -> "Went back"
        "browserextract" -> "Read page"
        "browserview" -> "Viewed page"
        "portscan" -> "Port scan done"
        "vulnscan" -> "Vuln scan done"
        "dirfuzz" -> "Fuzzing done"
        "niktoscan" -> "Nikto scan done"
        "sqlitest" -> "SQLi test done"
        "sslscan" -> "TLS scan done"
        "urlharvest" -> "URLs harvested"
        "xssscan" -> "XSS scan done"
        "cmditest" -> "Cmd-injection test done"
        "crlfscan" -> "CRLF scan done"
        "paramdiscover" -> "Param discovery done"
        "contentdiscover" -> "Content discovery done"
        "fastportscan" -> "Fast port scan done"
        "deepsecrets" -> "Deep secret scan done"
        "wpscan" -> "WordPress scan done"
        "sast" -> "Static analysis done"
        "reposecrets" -> "Secret scan done"
        "socialosint" -> "Username OSINT done"
        "phoneosint" -> "Phone OSINT done"
        "runjob" -> "Started background job"
        "jobstatus" -> "Checked job"
        "siteclone" -> "Website cloned"
        "apkrebuild" -> "APK rebuilt"
        "imageedit" -> "Image edited"
        "cloudls" -> "Listed cloud files"
        "cloudpull" -> "Pulled from cloud"
        "cloudpush" -> "Pushed to cloud"
        "exporttodevice" -> "Saved to Downloads"
        "generateimage" -> "Generated image"
        "screenshot" -> "Captured screenshot"
        "analyzeimage" -> "Analyzed image"
        "unzip" -> "Unzipped"
        "zip" -> "Zipped"
        "pdfcreate" -> "Created PDF"
        "createxlsx" -> "Created Excel"
        "createcsv" -> "Created CSV"
        "createpptx" -> "Created PowerPoint"
        "makeshorts" -> "Created viral shorts"
        "createdocx" -> "Created Word doc"
        "pdfedit" -> "Edited PDF text"
        "pdfaddpage" -> "Added page to PDF"
        "pdfaddimage" -> "Added image to PDF"
        "pdfaddchart" -> "Created chart PDF"
        "pdfmerge" -> "Merged PDFs"
        "pdfsplit" -> "Split PDF"
        "pdfread" -> "Read PDF"
        "pdffillform" -> "Filled PDF form"
        "pdfcompress" -> "Compressed PDF"
        "pdfcollage" -> "Created collage"
        "scaffoldandroid" -> "Added Android template"
        "previewweb" -> "Previewed web app"
        "runapp" -> "Ran app & captured proof"
        "listartifacts" -> "Listed artifacts"
        "captureartifact" -> "Registered artifact"
        "renderdiagram" -> "Rendered diagram"
        "renderchart" -> "Rendered chart"
        "ghpush" -> "Pushed to GitHub"
        "ghpr" -> "Opened pull request"
        "ghissues" -> "Listed issues"
        "ghcreateissue" -> "Created issue"
        "ghbuild" -> "Started APK build"
        "ghbuildstatus" -> "Checked build status"
        "ghlogs" -> "Read build logs"
        "ghrepos" -> "Listed repositories"
        "ghbranches" -> "Listed branches"
        "ghreadremote" -> "Read remote file"
        "ghlistremote" -> "Listed remote path"
        "ghcreaterepo" -> "Created repository"
        "ghcreatebranch" -> "Created branch"
        "ghswitchbranch" -> "Switched branch"
        "ghopenrepo" -> "Opened repository"
        "ghstatus" -> "Showed GitHub status"
        "multiedit" -> "Multi-edited"
        "symbolsearch" -> "Searched symbol"
        "gotodefinition" -> "Found definition"
        "findreferences" -> "Found references"
        "documentsymbols" -> "Listed document symbols"
        "workspacesymbols" -> "Listed workspace symbols"
        "hover" -> "Hovered symbol"
        "codebasesearch" -> "Searched codebase"
        "task" -> "Sub-agent finished"
        "loadtools" -> "Tools loaded"
        "importadd" -> "Added import"
        "refactorrename" -> "Renamed"
        "runtests" -> "Ran tests"
        "lint" -> "Linted"
        "verify" -> "Static verified"
        "verifybuild" -> "APK preflight done"
        "formatcode" -> "Formatted"
        "depsadd" -> "Added dependency"
        "gitdiff" -> "Showed git diff"
        "gitcommit" -> "Committed"
        "snippetsave" -> "Saved snippet"
        "snippetlist" -> "Listed snippets"
        "snippetload" -> "Loaded snippet"
        "diagnose" -> "Ran diagnostics"
        "askuser" -> "Waiting for your answer"
        "loadskill" -> "Loaded skill"
        "listskillresources" -> "Listed skill resources"
        "readskillresource" -> "Read skill resource"
        "runskillscript" -> "Ran skill script"
        "saveskill" -> "Saved skill"
        "worklog" -> "Updated worklog"
        "remember" -> "Remembered"
        "forget" -> "Forgot"
        "memory" -> "Read memory"
        "paralleltasks" -> "Launched parallel agents"
        else -> "Done"
    }

    /**
     * Pack a list of (old, new) arg pairs into a single string we can carry in AgentStep.detail.
     * Uses a delimiter that's extremely unlikely to appear in real code: \u0001 between pairs,
     * \u0002 between old and new within a pair. Reversed by [decodeMultiEditArgs].
     */
    fun encodeMultiEditArgs(pairs: List<String>): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < pairs.size) {
            if (sb.isNotEmpty()) sb.append('\u0001')
            sb.append(pairs[i].replace('\u0001', ' ').replace('\u0002', ' '))
            sb.append('\u0002')
            sb.append(pairs[i + 1].replace('\u0001', ' ').replace('\u0002', ' '))
            i += 2
        }
        return sb.toString()
    }

    fun decodeMultiEditArgs(packed: String): List<Pair<String, String>> {
        if (packed.isBlank()) return emptyList()
        return packed.split('\u0001').map { pair ->
            val parts = pair.split('\u0002', limit = 2)
            (parts.getOrElse(0) { "" }) to (parts.getOrElse(1) { "" })
        }
    }

    // ===== Semantic codebase search (multi-signal ranking, no embeddings needed) =====
    private fun semanticCodebaseSearch(projectDir: String, query: String): String {
        val terms = query.lowercase()
            .split(Regex("[^a-z0-9_]+"))
            .filter { it.length >= 2 }
            .distinct()
        if (terms.isEmpty()) return "ERROR: query too short."

        data class Hit(val file: String, val line: Int, val text: String, val score: Double)
        data class FileResult(val file: String, val score: Double, val hits: List<Hit>)
        val declRe = Regex("\\b(fun|def|function|class|interface|object|val|var|const|public|private)\\b")

        // 1) Fast path hits: FIND_FILES-style name/path matches first (often enough alone)
        val nameHits = ProjectManager.findFiles(projectDir, query, maxResults = 20)
        // 2) Parallel content scan — multi-core
        val fileResults = java.util.concurrent.CopyOnWriteArrayList<FileResult>()
        ProjectManager.listTextFiles(projectDir).parallelStream().forEach { rel ->
            val f = File(projectDir, rel)
            if (!ProjectManager.isInsideProject(projectDir, f)) return@forEach
            val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
            if (text.length > 400_000) return@forEach

            val relLower = rel.lowercase()
            val base = relLower.substringAfterLast('/')
            var fileScore = 0.0
            for (t in terms) {
                if (relLower.contains(t)) fileScore += 8.0
                if (base.contains(t)) fileScore += 12.0
                if (base.startsWith(t)) fileScore += 6.0
            }
            if (terms.all { relLower.contains(it) }) fileScore += 15.0
            val hits = mutableListOf<Hit>()
            text.lines().forEachIndexed { i, lineRaw ->
                val line = lineRaw.lowercase()
                var lineScore = 0.0
                for (t in terms) {
                    if (line.contains(t)) {
                        lineScore += 1.0
                        if (declRe.containsMatchIn(line)) lineScore += 1.5
                        val ts = line.trimStart()
                        if (ts.startsWith("//") || ts.startsWith("*") || ts.startsWith("#")) lineScore += 0.5
                    }
                }
                if (terms.all { line.contains(it) }) lineScore += 3.0
                if (lineScore > 0) {
                    hits.add(Hit(rel, i + 1, lineRaw.trim().take(160), lineScore))
                    fileScore += lineScore
                }
            }
            if (fileScore > 0) fileResults.add(FileResult(rel, fileScore, hits))
        }

        if (fileResults.isEmpty() && !nameHits.startsWith("Found")) {
            return "No matches for \"$query\". Try FIND_FILES with a filename fragment, or GREP for an exact symbol."
        }

        val top = fileResults.sortedByDescending { it.score }.take(10)
        val sb = StringBuilder("SEMANTIC SEARCH for \"$query\" (paths are exact — use for READ/EDIT):\n")
        if (nameHits.startsWith("Found")) {
            sb.append("\n## Path / filename matches\n")
            sb.append(nameHits.lines().drop(1).take(12).joinToString("\n"))
            sb.append("\n")
        }
        if (top.isNotEmpty()) {
            sb.append("\n## Content matches\n")
            for (fr in top) {
                sb.append("\n• ${fr.file}\n")
                for (h in fr.hits.sortedByDescending { it.score }.take(3)) {
                    sb.append("    ${h.line}: ${h.text}\n")
                }
            }
        }
        return sb.toString().take(6000)
    }

    private fun isLikelyText(name: String): Boolean {
        val bad = setOf("png","jpg","jpeg","gif","webp","ico","ttf","otf","woff","woff2","zip","jar",
            "apk","aab","so","class","dex","mp3","mp4","mov","avi","pdf","wav","bin","keystore","jks")
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext.isEmpty() || ext !in bad
    }

    // ===== Lazy tool loading — on-demand tool category docs =====
    val TOOL_CATEGORIES: Map<String, String> by lazy {
        mapOf(
            "pdf" to PDF_TOOLS_DOC,
            "office" to OFFICE_TOOLS_DOC,
            "security" to SECURITY_TOOLS_DOC,
            "apk" to APK_TOOLS_DOC,
            "media" to MEDIA_TOOLS_DOC,
            "osint" to OSINT_TOOLS_DOC,
            "cloud" to CLOUD_TOOLS_DOC
        )
    }

    fun toolCategoryDocs(category: String): String {
        if (category.isBlank()) return ""
        // Allow comma-separated multiple categories.
        val cats = category.split(Regex("[,\\s]+")).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val out = StringBuilder()
        for (c in cats) TOOL_CATEGORIES[c]?.let { out.append(it).append("\n\n") }
        return out.toString().trim()
    }

    // ----- Helper functions for the next-level coder tools -----
    /** Where to insert an import line, by language. */
    private fun computeImportInsertOffset(text: String, ext: String): Int {
        val lines = text.lines()
        return when (ext) {
            "kt", "java" -> {
                // After `package` line (and any blank line after it).
                var i = 0
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("package ")) {
                        i++
                        while (i < lines.size && lines[i].isBlank()) i++
                        return lines.subList(0, i).joinToString("\n").length + 1
                    }
                    i++
                }
                0
            }
            "py" -> {
                // After the module docstring + __future__ imports, before any other code.
                var i = 0
                while (i < lines.size) {
                    val l = lines[i].trim()
                    if (l.startsWith("from __future__") || l.startsWith("#!") || l.startsWith("#") || l.isBlank() ||
                        (l.startsWith("\"\"\"") && i == 0) || (l.startsWith("'''") && i == 0)) {
                        i++; continue
                    }
                    break
                }
                lines.subList(0, i).joinToString("\n").let { if (it.isBlank()) 0 else it.length + 1 }
            }
            "js", "ts", "jsx", "tsx" -> {
                // After the last existing import / require.
                var lastImport = -1
                for ((i, l) in lines.withIndex()) {
                    val t = l.trim()
                    if (t.startsWith("import ") || t.startsWith("const ") && t.contains("require(") ||
                        t.startsWith("\"use strict\"") || t.startsWith("'use strict'")) lastImport = i
                }
                if (lastImport >= 0) lines.subList(0, lastImport + 1).joinToString("\n").length + 1 else 0
            }
            "go" -> {
                // After `package X` line.
                for ((i, l) in lines.withIndex()) {
                    if (l.trim().startsWith("package ")) {
                        return lines.subList(0, i + 1).joinToString("\n").length + 1
                    }
                }
                0
            }
            "rs" -> {
                // After `extern crate` / `use` block at the top.
                var lastUse = -1
                for ((i, l) in lines.withIndex()) {
                    val t = l.trim()
                    if (t.startsWith("use ") || t.startsWith("extern crate ")) lastUse = i
                    else if (t.isNotEmpty() && !t.startsWith("//") && lastUse >= 0) break
                }
                if (lastUse >= 0) lines.subList(0, lastUse + 1).joinToString("\n").length + 1 else 0
            }
            else -> 0
        }
    }

    /** True if this looks like an Android/Gradle app project (APK path). */
    private fun isAndroidGradleProject(projectDir: String): Boolean {
        val br = ProjectManager.detectBuildRoot(projectDir)
        val root = if (br.isBlank()) File(projectDir) else File(projectDir, br)
        if (!root.isDirectory) return false
        val hasGradle = File(root, "build.gradle.kts").exists() || File(root, "build.gradle").exists() ||
            File(root, "settings.gradle.kts").exists() || File(root, "settings.gradle").exists()
        if (!hasGradle) return false
        // Android signal: manifest or com.android plugin
        if (root.walkTopDown().maxDepth(6).any { it.name == "AndroidManifest.xml" }) return true
        return root.walkTopDown().maxDepth(4)
            .filter { it.isFile && (it.name == "build.gradle.kts" || it.name == "build.gradle") }
            .any { runCatching { it.readText() }.getOrNull()?.contains("com.android") == true }
    }

    /** Auto-detect the right test command for the project in [projectDir]. */
    private fun detectTestCommand(projectDir: String, extra: String): String {
        val has = { name: String -> File(projectDir, name).exists() }
        val args = extra.trim()
        // Never suggest gradlew for Android — sandbox cannot run AGP builds/tests.
        if (isAndroidGradleProject(projectDir)) return ""
        return when {
            has("pytest.ini") || has("setup.py") || File(projectDir, "pyproject.toml").exists() &&
                File(projectDir, "pyproject.toml").readText().contains("pytest") ->
                "python -m pytest ${if (args.isBlank()) "" else "$args"}"
            has("package.json") -> {
                val pkg = File(projectDir, "package.json").readText()
                when {
                    pkg.contains("\"jest\"") -> "npx jest $args".trim()
                    pkg.contains("\"vitest\"") -> "npx vitest run $args".trim()
                    pkg.contains("\"mocha\"") -> "npx mocha $args".trim()
                    else -> "npm test $args".trim()
                }
            }
            has("go.mod") -> "go test ${if (args.isBlank()) "./..." else args}".trim()
            has("Cargo.toml") -> "cargo test $args".trim()
            // Non-Android Gradle only (library without Android plugin)
            has("build.gradle.kts") || has("build.gradle") -> "./gradlew test $args".trim()
            has("pom.xml") -> "mvn test $args".trim()
            has("*.csproj") || File(projectDir).listFiles()?.any { it.extension == "csproj" } == true ->
                "dotnet test $args".trim()
            else -> ""
        }
    }

    /** Auto-detect linter command. */
    private fun detectLintCommand(projectDir: String, path: String): String {
        val target = if (path.isBlank()) "." else path
        val has = { name: String -> File(projectDir, name).exists() }
        if (isAndroidGradleProject(projectDir)) return "" // static VERIFY only
        return when {
            has(".eslintrc.js") || has(".eslintrc.json") || has(".eslintrc") || has("eslint.config.js") ->
                "npx eslint $target"
            has("pyproject.toml") || has("setup.py") || has("requirements.txt") ->
                "pip install -q ruff 2>/dev/null; ruff check $target"
            has("go.mod") -> "go vet ./... 2>&1; which golangci-lint >/dev/null && golangci-lint run $target || echo golangci-lint not installed"
            has("Cargo.toml") -> "cargo clippy --all-targets 2>&1"
            has("build.gradle.kts") || has("build.gradle") -> "./gradlew ktlintCheck 2>&1 || (curl -sSLO https://github.com/pinterest/ktlint/releases/latest/download/ktlint && chmod +x ktlint && ./ktlint $target)"
            else -> ""
        }
    }

    /** Auto-detect formatter command. */
    private fun detectFormatCommand(projectDir: String, path: String): String {
        val target = if (path.isBlank()) "." else path
        val has = { name: String -> File(projectDir, name).exists() }
        return when {
            has(".prettierrc") || has(".prettierrc.json") || has(".prettierrc.js") || has("prettier.config.js") ->
                "npx prettier --write $target"
            has("pyproject.toml") || has("setup.py") ->
                "pip install -q black 2>/dev/null; black $target"
            has("go.mod") -> "gofmt -w $target"
            has("Cargo.toml") -> "cargo fmt"
            has("build.gradle.kts") || has("build.gradle") ->
                "curl -sSLO https://github.com/pinterest/ktlint/releases/latest/download/ktlint && chmod +x ktlint && ./ktlint -F $target"
            else -> ""
        }
    }

    /** Add a dependency to the right manifest + install it in the sandbox. */
    private suspend fun addDependency(projectDir: String, manager: String, spec: String, context: android.content.Context): String = withContext(Dispatchers.IO) {
        when (manager) {
            "npm" -> {
                val pkgFile = File(projectDir, "package.json")
                if (!pkgFile.exists()) "ERROR: package.json not found."
                else {
                    val res = CloudTools.execProv(context, projectDir, "npm install $spec", 180)
                    "npm install $spec:\n${res.formatted(3000)}"
                }
            }
            "pip" -> {
                val req = File(projectDir, "requirements.txt")
                req.appendText("\n$spec\n")
                FastSync.invalidate(projectDir)
                val res = CloudTools.execProv(context, projectDir, "pip install --break-system-packages $spec", 180)
                "pip added + installed: $spec\n${res.formatted(2000)}"
            }
            "cargo" -> {
                val cargo = File(projectDir, "Cargo.toml")
                if (!cargo.exists()) "ERROR: Cargo.toml not found."
                else {
                    val res = CloudTools.execProv(context, projectDir, "cargo add $spec", 120)
                    "cargo add $spec:\n${res.formatted(2000)}"
                }
            }
            "go" -> {
                val res = CloudTools.execProv(context, projectDir, "go get $spec", 120)
                "go get $spec:\n${res.formatted(2000)}"
            }
            "maven", "gradle" -> {
                val res = CloudTools.execProv(context, projectDir,
                    if (File(projectDir, "build.gradle.kts").exists() || File(projectDir, "build.gradle").exists())
                        "./gradlew --refresh-dependencies 2>&1"
                    else "mvn validate 2>&1", 180)
                "Gradle/Maven refresh for $spec:\n${res.formatted(2000)}"
            }
            else -> "ERROR: unknown package manager '$manager'."
        }
    }

    /**
     * After a successful write/edit:
     *  1) Instant offline [StaticVerifier] (braces/strings/package — no SDK/cloud)
     *  2) Optional cloud compiler diagnostics when E2B is configured
     */
    private suspend fun appendDiagnostics(
        result: String,
        context: android.content.Context,
        projectDir: String,
        filePath: String
    ): String {
        if (filePath.isBlank() || result.startsWith("ERROR")) return result
        val sb = StringBuilder(result)

        // 1) Instant static + safe auto-fix (braces at EOF, missing Compose imports, dupe imports)
        val auto = runCatching {
            StaticVerifier.verifyAndAutoFixFile(projectDir, filePath)
        }.getOrNull()
        if (auto != null) {
            if (auto.applied && auto.fixes.isNotEmpty()) {
                sb.append("\n\n").append(auto.format())
            } else {
                val note = StaticVerifier.quickFileNote(projectDir, filePath)
                if (!note.isNullOrBlank()) sb.append("\n\n").append(note)
            }
            // Still failing after auto-fix → agent must edit; skip slow cloud
            if (!auto.report.ok) return sb.toString()
            if (auto.applied) {
                // Fixed and clean — no need for cloud
                return sb.toString()
            }
        } else {
            val localNote = runCatching { StaticVerifier.quickFileNote(projectDir, filePath) }.getOrNull()
            if (!localNote.isNullOrBlank()) {
                sb.append("\n\n").append(localNote)
                if (localNote.contains("FAILED")) return sb.toString()
            }
        }

        // 2) Optional cloud diagnostics (non-Android path / when E2B available)
        val ext = filePath.substringAfterLast('.').lowercase()
        if (ext !in LspManager.supportedExtensions()) return sb.toString()
        val prefs = PreferencesManager(context)
        if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) return sb.toString()
        // Android projects: static is enough (sandbox has no real compiler)
        if (isAndroidGradleProject(projectDir)) return sb.toString()
        val diag = runCatching { LspManager.diagnose(context, projectDir, filePath).formatted() }
            .getOrNull() ?: return sb.toString()
        if (diag.isNotBlank() && !diag.contains("no issues", ignoreCase = true)) {
            sb.append("\n\n").append(diag)
        }
        return sb.toString()
    }

    /**
     * Always attempt to delete a temp APK-build repo (success, failure, cancel).
     * Clears local connected-repo pointer when it pointed at the wipe target.
     */
    private suspend fun wipeBuildRepoAlways(
        token: String,
        repo: String,
        prefs: PreferencesManager
    ): String {
        if (repo.isBlank()) return "skip: empty"
        val del = if (GitHubClient.isTempBuildRepo(repo)) {
            GitHubClient.deleteTempBuildRepo(token, repo)
        } else {
            // Safety: if connected pointer still looks like a build throwaway, force delete
            val name = repo.substringAfter('/')
            if (name.contains("ahamai-build", ignoreCase = true) ||
                name.contains("-build-", ignoreCase = true)
            ) {
                GitHubClient.deleteRepo(token, repo)
            } else {
                "skip: permanent repo ($repo)"
            }
        }
        if (prefs.getConnectedRepo() == repo) prefs.saveConnectedRepo("", "")
        return del
    }
}
