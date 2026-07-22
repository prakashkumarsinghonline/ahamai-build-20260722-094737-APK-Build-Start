package com.ahamai.app.data

/**
 * Big-change mode: when the user asks for large refactors / multi-file features,
 * raise read caps and push MULTI_EDIT / full WRITE / APPLY_PATCH over tiny EDIT loops.
 *
 * Thread-local so ProjectManager read caps pick it up without bloating the UI.
 */
object BigChangePolicy {

    data class Caps(
        val softLines: Int,
        val hardBytes: Long,
        val readLinesMax: Int,
        val batchMaxFiles: Int,
        val batchLinesPerFile: Int,
        val recentMessages: Int,
        /**
         * Soft consecutive-identical threshold used only for model hints.
         * The agent is never hard-stopped for repeats (see CodeAgentScreen).
         */
        val loopRepeatLimit: Int
    )

    private val DEFAULT = Caps(
        softLines = 400,
        hardBytes = 600_000L,
        readLinesMax = 500,
        batchMaxFiles = 12,
        batchLinesPerFile = 250,
        recentMessages = 36,
        loopRepeatLimit = 8
    )

    private val BIG = Caps(
        softLines = 2000,
        hardBytes = 2_000_000L,
        readLinesMax = 1500,
        batchMaxFiles = 24,
        batchLinesPerFile = 500,
        recentMessages = 48,
        loopRepeatLimit = 12
    )

    private val tl = ThreadLocal<Boolean>()

    @Volatile private var sessionBig: Boolean = false

    fun isActive(): Boolean = sessionBig || tl.get() == true

    fun caps(): Caps = if (isActive()) BIG else DEFAULT

    fun beginSession(task: String) {
        sessionBig = detect(task)
        if (sessionBig) tl.set(true)
    }

    fun endSession() {
        sessionBig = false
        tl.remove()
    }

    fun enterScope() {
        if (sessionBig) tl.set(true)
    }

    fun leaveScope() {
        tl.remove()
    }

    /** Keyword / shape heuristics for “this needs a big hammer”. */
    fun detect(task: String): Boolean {
        val t = task.lowercase()
        if (t.length > 400) return true
        val keys = listOf(
            "refactor", "rewrite", "migrate", "redesign", "overhaul", "entire",
            "whole app", "whole project", "all files", "every file", "codebase",
            "implement feature", "full stack", "from scratch", "scaffold",
            "multi-file", "multiple files", "architecture", "rename across",
            "convert all", "upgrade to", "add authentication", "add auth",
            "build me", "create a complete", "complete app", "full app",
            "large change", "big change", "major change"
        )
        if (keys.any { t.contains(it) }) return true
        // Many path-like tokens or numbered requirements → likely multi-file
        val pathish = Regex("""[\w.-]+\.(kt|java|ts|tsx|js|py|go|rs|xml|gradle)""").findAll(t).count()
        if (pathish >= 3) return true
        val bullets = t.lines().count { it.trim().startsWith("-") || it.trim().matches(Regex("""^\d+[.)].+""")) }
        if (bullets >= 5) return true
        return false
    }

    /**
     * Extra system/user guidance injected when big-change mode is on.
     * Keep short — full tool docs stay in agent prompt.
     */
    fun promptAddon(): String {
        if (!isActive()) return ""
        return """

[BIG-CHANGE MODE — active for this task]
You are allowed and EXPECTED to make large multi-file changes efficiently:
- Prefer MULTI_EDIT (many hunks, one file; atomic all-or-nothing) and APPLY_PATCH over many tiny EDIT_FILE turns.
- Prefer WRITE_FILE / CREATE_FILE with COMPLETE content for new modules or full rewrites — WRITE on an existing large file is allowed after you READ it this session.
- READ_FILES / READ_LINES in batch; large files may return more lines than usual.
- One response: edit MANY files at once (parallel). Do not nibble 1 line per turn.
- PLAN once with a file list, COMPLETE_STEP as you go, then ANSWER + DONE — do not re-start finished steps.
- Do NOT redo work already in the fix ledger. After DONE, stop.
"""
    }

    fun noteForUi(): String =
        if (isActive()) "Big-change mode · larger reads, multi-file edits preferred" else ""
}
