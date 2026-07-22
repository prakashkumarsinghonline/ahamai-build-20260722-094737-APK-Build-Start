package com.ahamai.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Structured JSON tool definitions for OpenAI-compatible function calling.
 *
 * When a provider supports the `tools` parameter, the agent sends these definitions
 * alongside the messages. The model then returns `tool_calls` in a structured JSON
 * format instead of XML — eliminating the need for regex parsing.
 *
 * For providers that DON'T support function calling (Freebuff, some custom endpoints),
 * the XML/regex ToolCallParser remains as the fallback.
 *
 * Usage:
 *   val toolsArray = StructuredTools.buildForContext(loadedCategories, hasSkillLoaded)
 *   // pass to request body as: requestBody.put("tools", toolsArray)
 *
 * The tool definitions follow the OpenAI function-calling schema:
 *   { "type": "function", "function": { "name": "...", "description": "...", "parameters": { ... } } }
 */
object StructuredTools {

    /**
     * Represents a structured tool definition.
     */
    data class ToolDef(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val isCore: Boolean = true,   // true = always included; false = loaded on demand
        val category: String = "core"
    )

    // ===== CORE TOOLS (always sent when structured mode is active) =====

    private val CORE_TOOLS = listOf(
        ToolDef(
            name = "PLAN",
            description = "Create a step-by-step plan for a complex task (3+ phases). Each step on its own line. User sees this as a live checklist.",
            parameters = JSONObject("""{"type":"object","properties":{"steps":{"type":"string","description":"One step per line, numbered 1. 2. 3. etc."}},"required":["steps"]}""")
        ),
        ToolDef(
            name = "COMPLETE_STEP",
            description = "Mark plan step N as done (1-based).",
            parameters = JSONObject("""{"type":"object","properties":{"step_number":{"type":"string","description":"The 1-based step number to mark as done"}},"required":["step_number"]}""")
        ),
        ToolDef(
            name = "READ_FILE",
            description = "Read a file with numbered lines (soft-capped for large files). Prefer this over guessing. Paths are sandboxed to the project.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"File path relative to project root"}},"required":["path"]}""")
        ),
        ToolDef(
            name = "READ_LINES",
            description = "Read a 1-based line range (max 500 lines/call). Use after READ_FILE notes a large file.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"start":{"type":"string","description":"Start line (1-based)"},"end":{"type":"string","description":"End line (1-based)"}},"required":["path","start","end"]}""")
        ),
        ToolDef(
            name = "READ_FILES",
            description = "Batch-read MULTIPLE files in ONE call (cheaper than many READ_FILE). Pass paths as a JSON array or comma-separated list. Each file is soft-capped (~250 lines); use READ_LINES for more of a large file.",
            parameters = JSONObject("""{"type":"object","properties":{"paths":{"type":"string","description":"JSON array e.g. [\"a.kt\",\"b.kt\"] or comma-separated paths"}},"required":["paths"]}""")
        ),
        ToolDef(
            name = "EDIT_FILE",
            description = "Targeted search-and-replace on ONE file. old_text should be unique (or set replace_all=true). Flexible indent/whitespace match, but never rewrites whole-file whitespace. PREFERRED over WRITE for existing files.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"old_text":{"type":"string","description":"Text to find (unique unless replace_all)"},"new_text":{"type":"string","description":"Replacement text"},"replace_all":{"type":"string","description":"true to replace every exact occurrence"},"explanation":{"type":"string"}},"required":["path","old_text","new_text"]}""")
        ),
        ToolDef(
            name = "INSERT_LINES",
            description = "Insert text AFTER a 1-based line number (after_line=0 inserts at file start). Best for adding imports, new functions, or config lines without search-replace. Prefer this over WRITE_FILE for small additions.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"after_line":{"type":"string","description":"1-based line to insert after (0 = beginning)"},"text":{"type":"string","description":"Text/lines to insert"}},"required":["path","after_line","text"]}""")
        ),
        ToolDef(
            name = "APPLY_PATCH",
            description = "Apply a unified diff or *** Begin Patch *** multi-hunk patch. Best for multi-hunk structural edits. Prefer EDIT_FILE for simple single replacements.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Default file path if patch has no headers"},"patch":{"type":"string","description":"Unified diff or Begin Patch body"}},"required":["patch"]}""")
        ),
        ToolDef(
            name = "WRITE_FILE",
            description = "Write/overwrite a file with full content. Use for NEW files only. Paths sandboxed under project root.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string","description":"Full file content"},"explanation":{"type":"string"}},"required":["path","content"]}""")
        ),
        ToolDef(
            name = "CREATE_FILE",
            description = "Create a new file. Alias for WRITE_FILE.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"},"explanation":{"type":"string"}},"required":["path","content"]}""")
        ),
        ToolDef(
            name = "DELETE_FILE",
            description = "Delete a file from the project (sandbox-enforced).",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}""")
        ),
        ToolDef(
            name = "COPY_FILE",
            description = "Copy a file to a new location inside the project.",
            parameters = JSONObject("""{"type":"object","properties":{"source":{"type":"string"},"destination":{"type":"string"}},"required":["source","destination"]}""")
        ),
        ToolDef(
            name = "MOVE_FILE",
            description = "Move/rename a file inside the project.",
            parameters = JSONObject("""{"type":"object","properties":{"source":{"type":"string"},"destination":{"type":"string"}},"required":["source","destination"]}""")
        ),
        ToolDef(
            name = "LIST_FILES",
            description = "Get the current project file tree (includes .github; skips build caches).",
            parameters = JSONObject("""{"type":"object","properties":{}}""")
        ),
        ToolDef(
            name = "GREP",
            description = "Ripgrep-style search. output_mode: 'content' (matching lines, default), 'files' (just file paths — cheap for 'which files have X'), or 'count' (per-file counts). multiline='true' lets the pattern span lines (DOT matches newlines).",
            parameters = JSONObject("""{"type":"object","properties":{"pattern":{"type":"string","description":"Regex pattern"},"path":{"type":"string","description":"Subfolder or file to limit search"},"glob":{"type":"string","description":"File glob e.g. *.kt or **/*Screen.kt"},"case_insensitive":{"type":"string","description":"true for -i"},"context":{"type":"string","description":"Context lines 0-5"},"max_results":{"type":"string","description":"Cap matches (default 50)"},"output_mode":{"type":"string","description":"content | files | count"},"multiline":{"type":"string","description":"true = pattern may span lines"}},"required":["pattern"]}""")
        ),
        ToolDef(
            name = "SEARCH_CODE",
            description = "Case-insensitive keyword search (optionally scoped). For natural 'where is X?' also try CODEBASE_SEARCH.",
            parameters = JSONObject("""{"type":"object","properties":{"query":{"type":"string"},"path":{"type":"string","description":"Optional subfolder scope"},"context":{"type":"string","description":"Optional context lines"}},"required":["query"]}""")
        ),
        ToolDef(
            name = "FIND_FILES",
            description = "Locate files by NAME/PATH glob (fast — no tree dump, no content scan). Use this to find where a file lives, e.g. pattern=*Screen.kt, **/AndroidManifest.xml, app/src/**/*.kt. Matches both the relative path and the bare filename.",
            parameters = JSONObject("""{"type":"object","properties":{"pattern":{"type":"string","description":"Name/path glob e.g. *Screen.kt or **/*.xml"},"path":{"type":"string","description":"Optional subfolder to limit the search"}},"required":["pattern"]}""")
        ),
        ToolDef(
            name = "DIFF_HISTORY",
            description = "Review the RECENT file changes made this session (edits/writes/creates/deletes) with +/- line stats and how long ago — so you know what was already done and avoid redoing it. Optional path filters to one file/folder.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Optional file/folder to filter changes"},"limit":{"type":"string","description":"Max entries (default 20)"}}}""")
        ),
        ToolDef(
            name = "REPO_MAP",
            description = "Get a ranked map of the codebase's key declarations (classes, functions, interfaces with signatures) — grasp the project's shape without reading every file. Optional path scopes to a subfolder for a deeper map. A bounded repo map is already in your initial context; call this to refresh or zoom in.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Optional subfolder to map in more detail"}}}""")
        ),
        ToolDef(
            name = "CODEBASE_WIKI",
            description = "Auto-generate a project overview (languages, stack, folder structure, entry points, key files) and save it as PROJECT_WIKI.md. Great as a first step on an unfamiliar codebase.",
            parameters = JSONObject("""{"type":"object","properties":{"output":{"type":"string","description":"Output path (default PROJECT_WIKI.md)"}}}""")
        ),
        ToolDef(
            name = "BULK_EDIT",
            description = "Exact project-wide search & replace. Optional path scope. Prefer EDIT_FILE for single-file changes.",
            parameters = JSONObject("""{"type":"object","properties":{"old_text":{"type":"string"},"new_text":{"type":"string"},"path":{"type":"string","description":"Optional folder scope"}},"required":["old_text","new_text"]}""")
        ),
        ToolDef(
            name = "MULTI_EDIT",
            description = "Multiple search/replace ops on ONE file in order. Each pair re-reads the file after the previous apply.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"pairs_json":{"type":"string","description":"JSON array of [old, new] pairs"}},"required":["path","pairs_json"]}""")
        ),
        ToolDef(
            name = "SYMBOL_SEARCH",
            description = "Find definitions of a symbol (fun/class/val/etc). Optional path + glob. Prefer GO_TO_DEFINITION / WORKSPACE_SYMBOLS for richer LSP-style results.",
            parameters = JSONObject("""{"type":"object","properties":{"symbol":{"type":"string"},"path":{"type":"string"},"glob":{"type":"string"}},"required":["symbol"]}""")
        ),
        ToolDef(
            name = "GO_TO_DEFINITION",
            description = "LSP-style go-to-definition: locate where a class/function/type/variable is defined (ranked). Optional path scopes the search.",
            parameters = JSONObject("""{"type":"object","properties":{"symbol":{"type":"string","description":"Symbol name e.g. AuthManager or UserService.login"},"path":{"type":"string","description":"Optional subfolder/file scope"}},"required":["symbol"]}""")
        ),
        ToolDef(
            name = "FIND_REFERENCES",
            description = "LSP-style find-references: all usages of a symbol (word-boundary), marking def vs ref. Optional path scope.",
            parameters = JSONObject("""{"type":"object","properties":{"symbol":{"type":"string"},"path":{"type":"string","description":"Optional subfolder scope"}},"required":["symbol"]}""")
        ),
        ToolDef(
            name = "DOCUMENT_SYMBOLS",
            description = "LSP-style document symbols: list classes/functions/properties in ONE file with line numbers.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"File path"}},"required":["path"]}""")
        ),
        ToolDef(
            name = "WORKSPACE_SYMBOLS",
            description = "LSP-style workspace symbol search by name fragment across the project.",
            parameters = JSONObject("""{"type":"object","properties":{"query":{"type":"string","description":"Name fragment to match"},"path":{"type":"string","description":"Optional subfolder scope"}},"required":["query"]}""")
        ),
        ToolDef(
            name = "HOVER",
            description = "LSP-style hover: signature, kind, definition location, doc comment, and a few lines of context for a symbol.",
            parameters = JSONObject("""{"type":"object","properties":{"symbol":{"type":"string"},"path":{"type":"string","description":"Optional scope"}},"required":["symbol"]}""")
        ),
        ToolDef(
            name = "WEB_SEARCH",
            description = "Search the LIVE web (Brave Search). Send SEVERAL in ONE reply — they run in PARALLEL.",
            parameters = JSONObject("""{"type":"object","properties":{"query":{"type":"string","description":"Search query"}},"required":["query"]}""")
        ),
        ToolDef(
            name = "READ_URL",
            description = "Read a web page as clean readable text. Send several in one reply for parallel reads.",
            parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string","description":"URL to read"}},"required":["url"]}""")
        ),
        ToolDef(
            name = "FETCH_URL",
            description = "Fetch a URL's raw content (headers, body snippet).",
            parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}""")
        ),
        ToolDef(
            name = "HTTP_REQUEST",
            description = "Make real HTTP requests — use like curl for API testing, security probing, endpoint verification. Send custom headers, auth tokens, crafted payloads. Returns status code, content-type, size, body snippet.",
            parameters = JSONObject("""{"type":"object","properties":{"method":{"type":"string","description":"HTTP method: GET, POST, PUT, DELETE, HEAD"},"url":{"type":"string"},"body":{"type":"string","description":"Request body for POST/PUT"},"headers":{"type":"string","description":"Headers as 'Key: Value' per line"}},"required":["method","url"]}""")
        ),
        ToolDef(
            name = "IMAGE_SEARCH",
            description = "Search the web for IMAGES. Returns Markdown image lines.",
            parameters = JSONObject("""{"type":"object","properties":{"query":{"type":"string","description":"What images to find"}},"required":["query"]}""")
        ),
        ToolDef(
            name = "RUN_PYTHON",
            description = "Execute Python code. Tries cloud sandbox first; falls back to Wandbox.",
            parameters = JSONObject("""{"type":"object","properties":{"code":{"type":"string","description":"Python code to execute"}},"required":["code"]}""")
        ),
        ToolDef(
            name = "CLOUD_SHELL",
            description = "Run ANY shell command in the cloud Ubuntu sandbox. Project files synced to /workspace. Batch with && for multiple commands.",
            parameters = JSONObject("""{"type":"object","properties":{"command":{"type":"string","description":"Bash command to run"}},"required":["command"]}""")
        ),
        ToolDef(
            name = "DOWNLOAD",
            description = "Download a file from a URL into the project.",
            parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string"},"destination":{"type":"string","description":"Path in project"}},"required":["url","destination"]}""")
        ),
        ToolDef(
            name = "ANSWER",
            description = "Present your final answer/explanation to the user. Always follow with DONE.",
            parameters = JSONObject("""{"type":"object","properties":{"text":{"type":"string","description":"Your answer in markdown"}},"required":["text"]}""")
        ),
        ToolDef(
            name = "DONE",
            description = "Signal that the task is complete.",
            parameters = JSONObject("""{"type":"object","properties":{"note":{"type":"string","description":"Optional completion note"}}}""")
        ),
        ToolDef(
            name = "UNZIP",
            description = "Extract a .zip file inside the project.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"destination":{"type":"string","description":"Output folder (blank = project root)"}},"required":["path"]}""")
        ),
        ToolDef(
            name = "ZIP",
            description = "Compress a file/folder into a .zip.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"output":{"type":"string","description":"Output .zip path"}},"required":["path","output"]}""")
        ),
        ToolDef(
            name = "EXPORT_TO_DEVICE",
            description = "Save a project file to the phone's Downloads folder.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"filename":{"type":"string","description":"Name in Downloads (optional)"}},"required":["path"]}""")
        ),
        ToolDef(
            name = "IMPORT_TO_PROJECT",
            description = "Import a file from Downloads into the project. Zips are auto-extracted.",
            parameters = JSONObject("""{"type":"object","properties":{"filename":{"type":"string"},"destination":{"type":"string","description":"Subfolder (blank = project root)"}},"required":["filename"]}""")
        ),
        // ===== NEW TOOLS (Features 1, 3, 4) =====
        ToolDef(
            name = "ASK_USER",
            description = "CRITICAL: Before starting ANY non-trivial deliverable (report, deck, spreadsheet, poster, multi-file project), you MUST call this to clarify requirements. Batch 4-8 questions. The agent PAUSES and waits for the user's answers before continuing. Skip ONLY if the user's request already explicitly pinned audience AND style AND length.",
            parameters = JSONObject("""{"type":"object","properties":{"questions_json":{"type":"string","description":"JSON array of question objects: [{\"header\":\"Audience\",\"question\":\"Who is this for?\",\"options\":[\"students\",\"clients\",\"investors\",\"general public\"],\"type\":\"single\"}]  type is 'single' or 'multi'. max 8 questions."}},"required":["questions_json"]}"""),
            category = "core"
        ),
        ToolDef(
            name = "TASK",
            description = "Spawn a focused SUB-AGENT for a self-contained task. The sub-agent has its own fresh context and returns a consolidated result. Use for parallelizable work or to keep your own context clean.",
            parameters = JSONObject("""{"type":"object","properties":{"description":{"type":"string","description":"Clear, self-contained task description"},"agent_type":{"type":"string","description":"Specialized agent type: 'general-purpose', 'explore', 'plan', 'frontend-styling-expert', 'full-stack-developer', 'ppt-expert'","enum":["general-purpose","explore","plan","frontend-styling-expert","full-stack-developer","ppt-expert"]}},"required":["description"]}"""),
            category = "core"
        ),
        ToolDef(
            name = "PARALLEL_TASKS",
            description = "Launch MULTIPLE sub-agents IN PARALLEL for independent tasks. Each runs concurrently and returns results. Much faster than sequential TASK calls for independent work.",
            parameters = JSONObject("""{"type":"object","properties":{"tasks_json":{"type":"string","description":"JSON array of task objects: [{\"description\":\"...\",\"agent_type\":\"explore\"},{\"description\":\"...\",\"agent_type\":\"plan\"}]. Max 4 parallel tasks."}},"required":["tasks_json"]}"""),
            category = "core"
        ),
        ToolDef(
            name = "LOAD_SKILL",
            description = "Load a full SKILL.md body (Agent Skills progressive disclosure). Use when the task matches a skill's description from the AVAILABLE SKILLS catalog. Pass the skill `name` (kebab-case id, e.g. pdf, docx, pptx, xlsx, skill-creator).",
            parameters = JSONObject("""{"type":"object","properties":{"skill_name":{"type":"string","description":"Skill name (kebab-case id from the AVAILABLE SKILLS catalog)"}},"required":["skill_name"]}"""),
            category = "core"
        ),
        ToolDef(
            name = "WORKLOG",
            description = "Append an entry to the shared worklog (worklog.md). All agents (main + sub) read this for continuity. Use at the end of significant work.",
            parameters = JSONObject("""{"type":"object","properties":{"entry":{"type":"string","description":"Worklog entry text (will be prefixed with timestamp and agent ID)"}},"required":["entry"]}"""),
            category = "core"
        ),
        ToolDef(
            name = "REMEMBER",
            description = "Pin a durable fact/preference/decision into project context memory (.ahamai). Survives compaction and app restarts. Use for lasting rules (e.g. 'prefer Compose Material3', 'package is com.foo').",
            parameters = JSONObject("""{"type":"object","properties":{"fact":{"type":"string","description":"Short durable fact to remember"}},"required":["fact"]}"""),
            category = "core"
        ),
        ToolDef(
            name = "FORGET",
            description = "Remove durable memory facts matching a query string.",
            parameters = JSONObject("""{"type":"object","properties":{"query":{"type":"string","description":"Substring to match against stored facts"}},"required":["query"]}"""),
            category = "core"
        ),
        ToolDef(
            name = "MEMORY",
            description = "Read the Grok-style context memory catalog for this project (goals, facts, hot files, episodes, worklog). Optional query filters lines.",
            parameters = JSONObject("""{"type":"object","properties":{"query":{"type":"string","description":"Optional filter substring"}}}"""),
            category = "core"
        ),
        ToolDef(
            name = "GENERATE_IMAGE",
            description = "Generate an image from a text prompt using AI.",
            parameters = JSONObject("""{"type":"object","properties":{"prompt":{"type":"string","description":"Text prompt describing the image"},"output_path":{"type":"string","description":"Output path in project (optional)"},"model":{"type":"string","description":"Model: pollinations (default) or pollinations-realistic"}},"required":["prompt"]}""")
        ),
        ToolDef(
            name = "ANALYZE_IMAGE",
            description = "Analyze an image using OCR + metadata extraction (no paid API needed).",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Image path in project"}},"required":["path"]}""")
        ),
        ToolDef(
            name = "SCREENSHOT",
            description = "Capture a full webpage screenshot as PNG.",
            parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string"},"output_path":{"type":"string","description":"Output path (default: screenshot.png)"}},"required":["url"]}""")
        ),
        ToolDef(
            name = "CLOUD_INSTALL",
            description = "Install packages in the cloud sandbox on demand.",
            parameters = JSONObject("""{"type":"object","properties":{"manager":{"type":"string","description":"Package manager: apt, pip, npm, go, gh"},"packages":{"type":"string","description":"Packages to install"}},"required":["manager","packages"]}""")
        ),
        ToolDef(
            name = "RUN_JOB",
            description = "Run a slow command in the BACKGROUND in the cloud sandbox. Returns a job ID immediately.",
            parameters = JSONObject("""{"type":"object","properties":{"command":{"type":"string","description":"Shell command to run in background"}},"required":["command"]}""")
        ),
        ToolDef(
            name = "JOB_STATUS",
            description = "Check a background job's status and output.",
            parameters = JSONObject("""{"type":"object","properties":{"job_id":{"type":"string","description":"Job ID from RUN_JOB"}},"required":["job_id"]}""")
        ),
        ToolDef(
            name = "VERIFY",
            description = "INSTANT offline static verify — braces/parens, unclosed strings, Kotlin package vs folder, JSON/XML, Android preflight signals. No SDK, no cloud, milliseconds. Modes: quick | full | android | apk. ALWAYS run after code edits and before DONE / GH_BUILD_APK.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Optional file/folder scope"},"mode":{"type":"string","description":"quick | full | android | apk"}},"required":[]}""")
        ),
        ToolDef(
            name = "RUN_TESTS",
            description = "Verify changes: always runs instant offline static first; if Cloud Engine is configured also runs the real test runner (pytest/jest/gradle test/…).",
            parameters = JSONObject("""{"type":"object","properties":{"extra_args":{"type":"string","description":"Extra args e.g. '-k TestLogin'"}}}""")
        ),
        ToolDef(
            name = "LINT",
            description = "Instant offline static on path (always) + optional cloud linter when Cloud Engine is set.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Path to lint (blank = whole project)"}}}""")
        ),
        ToolDef(
            name = "FORMAT_CODE",
            description = "Auto-detect and run the project's code formatter.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Path to format (blank = whole project)"}}}""")
        ),
        ToolDef(
            name = "GIT_DIFF",
            description = "Show git diff of uncommitted changes.",
            parameters = JSONObject("""{"type":"object","properties":{}}""")
        ),
        ToolDef(
            name = "GIT_COMMIT",
            description = "Stage all changes and commit with a message.",
            parameters = JSONObject("""{"type":"object","properties":{"message":{"type":"string","description":"Commit message"}},"required":["message"]}""")
        ),
        ToolDef(
            name = "REFACTOR_RENAME",
            description = "Project-wide safe rename of a symbol across all files.",
            parameters = JSONObject("""{"type":"object","properties":{"old_name":{"type":"string"},"new_name":{"type":"string"},"scope":{"type":"string","description":"Limit to a specific file (blank = whole project)"}},"required":["old_name","new_name"]}""")
        ),
        ToolDef(
            name = "IMPORT_ADD",
            description = "Add an import statement to a file (idempotent — skips if already present).",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"import_statement":{"type":"string","description":"The import line to add"}},"required":["path","import_statement"]}""")
        ),
        ToolDef(
            name = "DEPS_ADD",
            description = "Add a dependency and install it in the cloud sandbox.",
            parameters = JSONObject("""{"type":"object","properties":{"manager":{"type":"string","description":"Package manager: npm, pip, cargo, go, maven, gradle"},"spec":{"type":"string","description":"Package specification"}},"required":["manager","spec"]}""")
        ),
        // ===== BROWSER TOOLS =====
        ToolDef(
            name = "BROWSER_OPEN",
            description = "Open a URL in the cloud browser. Returns numbered interactive elements.",
            parameters = JSONObject("""{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}"""),
            category = "browser"
        ),
        ToolDef(
            name = "BROWSER_CLICK",
            description = "Click an element by its index from the latest snapshot.",
            parameters = JSONObject("""{"type":"object","properties":{"element_index":{"type":"string","description":"Element index number"}},"required":["element_index"]}"""),
            category = "browser"
        ),
        ToolDef("COMPUTER_OPEN", "Boot/reuse a live full Linux desktop (Daytona Computer Use) the user can watch. Use for GUI apps beyond a headless browser.", JSONObject("""{"type":"object","properties":{}}"""), category = "browser"),
        ToolDef("COMPUTER_CLICK", "Click at pixel coordinates on the live desktop (1024x768).", JSONObject("""{"type":"object","properties":{"coords":{"type":"string","description":"x,y e.g. 420,300"}},"required":["coords"]}"""), category = "browser"),
        ToolDef("COMPUTER_TYPE", "Type text on the live desktop.", JSONObject("""{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""), category = "browser"),
        ToolDef("COMPUTER_KEY", "Press a key/combo on the live desktop (enter, tab, ctrl+c, …).", JSONObject("""{"type":"object","properties":{"key":{"type":"string"}},"required":["key"]}"""), category = "browser"),
        ToolDef("COMPUTER_SCREENSHOT", "Refresh the live desktop view.", JSONObject("""{"type":"object","properties":{}}"""), category = "browser"),
        ToolDef(
            name = "BROWSER_TYPE",
            description = "Type text into an input field by element index.",
            parameters = JSONObject("""{"type":"object","properties":{"element_index":{"type":"string"},"text":{"type":"string"}},"required":["element_index","text"]}"""),
            category = "browser"
        ),
        ToolDef(
            name = "BROWSER_PRESS",
            description = "Press a keyboard key (e.g. Enter, Tab, Escape).",
            parameters = JSONObject("""{"type":"object","properties":{"key":{"type":"string"}},"required":["key"]}"""),
            category = "browser"
        ),
        ToolDef(
            name = "BROWSER_SCROLL",
            description = "Scroll the page (up or down).",
            parameters = JSONObject("""{"type":"object","properties":{"direction":{"type":"string","description":"'up' or 'down'"}},"required":["direction"]}"""),
            category = "browser"
        ),
        ToolDef(
            name = "BROWSER_EXTRACT",
            description = "Extract all visible text from the current page.",
            parameters = JSONObject("""{"type":"object","properties":{}}"""),
            category = "browser"
        ),
        ToolDef(
            name = "BROWSER_VIEW",
            description = "Re-screenshot and re-list the current page's elements.",
            parameters = JSONObject("""{"type":"object","properties":{}}"""),
            category = "browser"
        ),
        ToolDef(
            name = "BROWSER_BACK",
            description = "Go back to the previous page.",
            parameters = JSONObject("""{"type":"object","properties":{}}"""),
            category = "browser"
        ),
        // ===== CLOUD FILE TOOLS =====
        ToolDef(
            name = "CLOUD_LS",
            description = "List files in the cloud sandbox.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"Path (default: /workspace)"}}}"""),
            category = "cloud"
        ),
        ToolDef(
            name = "CLOUD_PULL",
            description = "Copy a file/folder from cloud sandbox into the project.",
            parameters = JSONObject("""{"type":"object","properties":{"cloud_path":{"type":"string"},"destination":{"type":"string"}},"required":["cloud_path","destination"]}"""),
            category = "cloud"
        ),
        ToolDef(
            name = "CLOUD_PUSH",
            description = "Copy a project file/folder into the cloud sandbox.",
            parameters = JSONObject("""{"type":"object","properties":{"project_path":{"type":"string"},"destination":{"type":"string"}},"required":["project_path"]}"""),
            category = "cloud"
        ),
        // ===== GITHUB TOOLS =====
        ToolDef(
            name = "GH_PUSH",
            description = "Push changed project files to GitHub.",
            parameters = JSONObject("""{"type":"object","properties":{"branch":{"type":"string"},"commit_message":{"type":"string"}},"required":["branch"]}"""),
            category = "github"
        ),
        ToolDef(
            name = "GH_PR",
            description = "Create a pull request on GitHub.",
            parameters = JSONObject("""{"type":"object","properties":{"title":{"type":"string"},"body":{"type":"string"},"head_branch":{"type":"string"},"base_branch":{"type":"string"}},"required":["title"]}"""),
            category = "github"
        ),
        ToolDef(
            name = "GH_BUILD_APK",
            description = "Trigger an APK build via GitHub Actions.",
            parameters = JSONObject("""{"type":"object","properties":{}}"""),
            category = "github"
        ),
        ToolDef(
            name = "GH_BUILD_STATUS",
            description = "Check the latest GitHub Actions build status.",
            parameters = JSONObject("""{"type":"object","properties":{}}"""),
            category = "github"
        ),
        ToolDef(
            name = "GH_LIST_ISSUES",
            description = "List GitHub issues.",
            parameters = JSONObject("""{"type":"object","properties":{}}"""),
            category = "github"
        ),
        // ===== VERCEL MCP TOOLS (added when Vercel is connected) =====
        // These are added conditionally in buildToolsArray()
        // ===== PREVIEW TOOLS =====
        ToolDef(
            name = "PREVIEW_WEB_APP",
            description = "Show a live web app preview inline.",
            parameters = JSONObject("""{"type":"object","properties":{"path":{"type":"string","description":"HTML file path to preview"}},"required":["path"]}"""),
            category = "preview"
        ),
        ToolDef(
            name = "RENDER_DIAGRAM",
            description = "Render a diagram (Mermaid, flowchart, etc.) inline.",
            parameters = JSONObject("""{"type":"object","properties":{"kind":{"type":"string","description":"Diagram type (mermaid, etc.)"},"source":{"type":"string","description":"Diagram source code"}},"required":["source"]}"""),
            category = "preview"
        ),
        ToolDef(
            name = "RENDER_CHART",
            description = "Render a chart (bar, line, pie) inline.",
            parameters = JSONObject("""{"type":"object","properties":{"kind":{"type":"string","description":"Chart type: bar, line, pie"},"title":{"type":"string"},"data_json":{"type":"string","description":"Chart data as JSON"}},"required":["kind","data_json"]}"""),
            category = "preview"
        ),
    )

    // ===== LAZY-LOADED TOOL CATEGORIES =====
    // These are only included when LOAD_TOOLS is called, to keep the tool list small.

    private val PDF_TOOLS = listOf(
        ToolDef("PDF_CREATE", "Create a professionally styled PDF in one call from markdown content.", JSONObject("""{"type":"object","properties":{"output_path":{"type":"string"},"content":{"type":"string","description":"Markdown content for the PDF"},"style":{"type":"string","description":"Style preset: modern, dark, classic, corporate, minimal, or custom overrides"},"watermark":{"type":"string","description":"Watermark text (leave empty by default)"}},"required":["output_path","content"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_READ", "Extract all text from a PDF.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_EDIT_TEXT", "Find and replace text in an existing PDF while keeping original layout.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"old_text":{"type":"string"},"new_text":{"type":"string"}},"required":["path","old_text","new_text"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_MERGE", "Merge two or more PDFs into one.", JSONObject("""{"type":"object","properties":{"file1":{"type":"string"},"file2":{"type":"string"},"output":{"type":"string"}},"required":["file1","file2","output"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_SPLIT", "Extract specific pages from a PDF.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"page_ranges":{"type":"string","description":"e.g. '1-3,5,7-10'"},"output":{"type":"string"}},"required":["path","page_ranges","output"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_ADD_PAGE", "Add a new page with content to an existing PDF.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_ADD_IMAGE", "Insert an image into a PDF at a specific page.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"image_path":{"type":"string"},"page_number":{"type":"string"}},"required":["path","image_path","page_number"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_ADD_CHART", "Render a chart into a PDF.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"chart_type":{"type":"string","description":"bar, pie, or line"},"data_json":{"type":"string","description":"JSON: {\"labels\":[...],\"values\":[...],\"title\":\"...\"}"}},"required":["path","chart_type","data_json"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_FILL_FORM", "Fill form fields in a PDF.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"fields_json":{"type":"string","description":"JSON: {\"field_name\": \"value\"}"}},"required":["path","fields_json"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_COMPRESS", "Change PDF quality/size.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"quality":{"type":"string","description":"low, medium, or high"},"output":{"type":"string"}},"required":["path","quality","output"]}"""), isCore = false, category = "pdf"),
        ToolDef("PDF_COLLAGE", "Build an image collage PDF.", JSONObject("""{"type":"object","properties":{"images":{"type":"string","description":"Comma-separated image paths"},"output":{"type":"string"},"columns":{"type":"string","description":"Number of columns"}},"required":["images","output"]}"""), isCore = false, category = "pdf"),
    )

    private val OFFICE_TOOLS = listOf(
        ToolDef("CREATE_XLSX", "Create an Excel workbook with sheets, data, and optional charts.", JSONObject("""{"type":"object","properties":{"output_path":{"type":"string"},"spec_json":{"type":"string","description":"JSON spec with sheets, rows, optional chart config"}},"required":["output_path","spec_json"]}"""), isCore = false, category = "office"),
        ToolDef("CREATE_CSV", "Create a CSV file.", JSONObject("""{"type":"object","properties":{"output_path":{"type":"string"},"data_json":{"type":"string","description":"JSON: {\"rows\":[[...]]} or raw CSV text"}},"required":["output_path","data_json"]}"""), isCore = false, category = "office"),
        ToolDef("CREATE_PPTX", "Create a PowerPoint deck with bullets, charts, diagrams, images.", JSONObject("""{"type":"object","properties":{"output_path":{"type":"string"},"spec_json":{"type":"string","description":"JSON spec with title, slides (bullets/chart/diagram/image)"}},"required":["output_path","spec_json"]}"""), isCore = false, category = "office"),
        ToolDef("CREATE_DOCX", "Create a Word document with headings, text, bullets, tables, images.", JSONObject("""{"type":"object","properties":{"output_path":{"type":"string"},"spec_json":{"type":"string","description":"JSON spec with title, sections (heading/text/bullets/table/image)"}},"required":["output_path","spec_json"]}"""), isCore = false, category = "office"),
    )

    private val SECURITY_TOOLS = listOf(
        ToolDef("HTTP_REQUEST", "Make HTTP requests to test endpoints, APIs, check responses. Use for security testing: send crafted headers, payloads, check status codes. Supports GET/POST/PUT/DELETE/HEAD.", JSONObject("""{"type":"object","properties":{"method":{"type":"string","description":"HTTP method"},"url":{"type":"string"},"body":{"type":"string","description":"Request body (for POST/PUT)"},"headers":{"type":"string","description":"Headers as Key: Value per line"}},"required":["method","url"]}"""), isCore = false, category = "security"),
        ToolDef("CLOUD_SHELL", "Run ANY security tool in the cloud. You are ROOT — install nmap, nuclei, sqlmap, nikto, subfinder, httpx, ffuf, wpscan, etc. with apt/pip/go. Batch install+scan in ONE command with &&. Use curl for HTTP testing.", JSONObject("""{"type":"object","properties":{"command":{"type":"string","description":"Shell command to run (install tools with apt/pip, then run them)"}},"required":["command"]}"""), isCore = false, category = "security"),
        ToolDef("SCAN_SECRETS", "Scan for hardcoded secrets/API keys in project files.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""), isCore = false, category = "security"),
        ToolDef("SECURITY_AUDIT", "Full mobile security audit using MobSF.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""), isCore = false, category = "security"),
    )

    private val MEDIA_TOOLS = listOf(
        ToolDef("IMAGE_EDIT", "Edit/manipulate an image (resize, crop, rotate, filter, watermark, etc.).", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"operation":{"type":"string","description":"resize, scale, thumbnail, crop, rotate, flip, grayscale, invert, blur, brightness, contrast, convert, compress, text, watermark"},"args":{"type":"string","description":"Operation arguments"},"output_path":{"type":"string"}},"required":["path","operation"]}"""), isCore = false, category = "media"),
        ToolDef("MAKE_SHORTS", "Turn a long video into vertical viral shorts with karaoke captions.", JSONObject("""{"type":"object","properties":{"source":{"type":"string","description":"YouTube URL or video path"},"options_json":{"type":"string","description":"JSON: {count,clip_len,styles,title,srt,karaoke}"},"output_dir":{"type":"string"}},"required":["source","options_json"]}"""), isCore = false, category = "media"),
    )

    private val APK_TOOLS = listOf(
        ToolDef("APK_DECOMPILE", "Decompile an APK into readable Java source + resources.", JSONObject("""{"type":"object","properties":{"path":{"type":"string"},"output_folder":{"type":"string","description":"Output folder (default: decompiled_app)"}},"required":["path"]}"""), isCore = false, category = "apk"),
        ToolDef("APK_INFO", "Extract metadata from an APK (permissions, activities, signing certs, etc.).", JSONObject("""{"type":"object","properties":{"path":{"type":"string"}},"required":["path"]}"""), isCore = false, category = "apk"),
        ToolDef("APK_REBUILD", "Rebuild an APK from a decompiled folder, then zipalign + sign it.", JSONObject("""{"type":"object","properties":{"folder":{"type":"string","description":"Decompiled folder containing apktool.yml"},"output":{"type":"string","description":"Output APK path (default: rebuilt.apk)"}},"required":["folder"]}"""), isCore = false, category = "apk"),
        ToolDef("SCAFFOLD_ANDROID", "Drop a ready Android (Kotlin + Compose) app template into the project.", JSONObject("""{"type":"object","properties":{}}"""), isCore = false, category = "apk"),
        ToolDef("VERIFY_BUILD", "INSTANT offline Android/APK static preflight (no SDK): braces, package/folder, gradle wrapper, manifest components, Compose flag. Run before GH_BUILD_APK — GH_BUILD_APK also auto-runs this and refuses to build on ERROR.", JSONObject("""{"type":"object","properties":{}}"""), isCore = false, category = "apk"),
    )

    // ===== CATEGORY REGISTRY =====
    private val CATEGORY_TOOLS = mapOf(
        "pdf" to PDF_TOOLS,
        "office" to OFFICE_TOOLS,
        "security" to SECURITY_TOOLS,
        "media" to MEDIA_TOOLS,
        "apk" to APK_TOOLS,
    )

    /** Categories that have been loaded via LOAD_TOOLS. */
    private val loadedCategories = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Reset loaded categories (e.g. on new session). */
    fun resetLoadedCategories() { loadedCategories.clear() }

    /** Mark a category as loaded. */
    fun markCategoryLoaded(category: String) { loadedCategories[category.lowercase()] = true }

    /** Check if a category is loaded. */
    fun isCategoryLoaded(category: String): Boolean = loadedCategories.containsKey(category.lowercase())

    /**
     * Build the full `tools` JSON array for the API request.
     * Includes all core tools + any lazily-loaded categories that have been activated.
     */
    fun buildToolsArray(): JSONArray {
        val arr = JSONArray()
        // Core tools
        for (tool in CORE_TOOLS) {
            arr.put(toolToJson(tool))
        }
        // Loaded categories
        for ((cat, tools) in CATEGORY_TOOLS) {
            if (loadedCategories.containsKey(cat)) {
                for (tool in tools) {
                    arr.put(toolToJson(tool))
                }
            }
        }
        // Connector tools (conditionally added when each connector is connected)
        val connectorTools = com.ahamai.app.data.ConnectorsManager.buildAllToolsJson()
        for (i in 0 until connectorTools.length()) {
            arr.put(connectorTools.getJSONObject(i))
        }
        return arr
    }

    /**
     * Build a lightweight tools array with only specific categories included.
     * Used by sub-agents that don't need the full toolset.
     */
    fun buildMinimalToolsArray(categories: Set<String>): JSONArray {
        val arr = JSONArray()
        val cats = categories.map { it.lowercase() }
        for (tool in CORE_TOOLS) {
            if (tool.category == "core" || tool.category in cats) {
                arr.put(toolToJson(tool))
            }
        }
        for ((cat, tools) in CATEGORY_TOOLS) {
            if (cat in cats) {
                for (tool in tools) {
                    arr.put(toolToJson(tool))
                }
            }
        }
        return arr
    }

    /**
     * Parse structured `tool_calls` from an OpenAI-compatible response.
     * Returns a list of ToolCall compatible with the existing ToolCallParser.ToolCall format.
     */
    fun parseToolCalls(choices: JSONArray): List<ToolCallParser.ToolCall> {
        val results = mutableListOf<ToolCallParser.ToolCall>()
        if (choices.length() == 0) return results
        val message = choices.getJSONObject(0).optJSONObject("message") ?: return results

        // Check for structured tool_calls
        val toolCalls = message.optJSONArray("tool_calls")
        if (toolCalls != null) {
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.getJSONObject(i)
                val function = tc.getJSONObject("function")
                val name = function.getString("name").uppercase()
                val argsStr = function.getString("arguments")
                val args = mutableListOf<String>()
                val named = linkedMapOf<String, String>()
                try {
                    val argsObj = JSONObject(argsStr)
                    val keys = argsObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = argsObj.optString(k, "")
                        named[k.lowercase()] = v
                        args.add(v)
                    }
                } catch (_: Exception) {
                    args.add(argsStr)
                }
                results.add(ToolCallParser.ToolCall(
                    name = name,
                    args = args,
                    raw = tc.toString(),
                    named = named,
                    explanation = named["explanation"] ?: ""
                ))
            }
            return results
        }

        // No tool_calls — return empty (the caller will check content text)
        return results
    }

    /**
     * Build a `tool` role message from tool call results.
     * Format: { "role": "tool", "tool_call_id": "...", "content": "..." }
     */
    fun buildToolResultMessage(toolCallId: String, result: String): JSONObject {
        return JSONObject().apply {
            put("role", "tool")
            put("tool_call_id", toolCallId)
            put("content", result)
        }
    }

    /**
     * Extract tool call IDs from a response (needed for the follow-up tool role message).
     */
    fun extractToolCallIds(choices: JSONArray): List<Pair<String, String>> {
        val ids = mutableListOf<Pair<String, String>>() // (id, toolName)
        val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return ids
        val toolCalls = message.optJSONArray("tool_calls") ?: return ids
        for (i in 0 until toolCalls.length()) {
            val tc = toolCalls.getJSONObject(i)
            val id = tc.getString("id")
            val name = tc.getJSONObject("function").getString("name")
            ids.add(id to name)
        }
        return ids
    }

    // ===== INTERNAL =====

    private fun toolToJson(tool: ToolDef): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", tool.name)
                put("description", tool.description)
                put("parameters", tool.parameters)
            })
        }
    }

    /** Total tool count (core + loaded categories). */
    fun totalToolCount(): Int {
        var count = CORE_TOOLS.size
        for ((cat, tools) in CATEGORY_TOOLS) {
            if (loadedCategories.containsKey(cat)) count += tools.size
        }
        return count
    }
}