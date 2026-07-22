package com.ahamai.app.data

/**
 * Keeps the coding agent from burning 20–30 turns re-searching files after work is done,
 * and steers file discovery toward few high-signal tools (tree / map / scoped grep).
 */
object AgentDiscipline {

    /** Tools that only hunt for files/code (not mutations). */
    val EXPLORE_ACTIONS = setOf(
        "list", "grep", "search", "glob", "codebasesearch", "symbolsearch",
        "gotodefinition", "findreferences", "documentsymbols", "workspacesymbols",
        "hover", "repomap", "diffhistory", "read", "readlines", "readfiles", "memory"
    )

    /** Real work that advances the task. */
    val WORK_ACTIONS = setOf(
        "edit", "write", "create", "delete", "multiedit", "bulkedit", "applypatch",
        "insertlines", "cloudshell", "runpython", "runapp", "ghpush", "ghbuild",
        "pdfcreate", "createxlsx", "createpptx", "createdocx", "generateimage",
        "answer", "done", "completestep"
    )

    /**
     * Injected on every agent run — search efficiently + stop when finished.
     */
    fun efficiencyPolicyBlock(): String = """
[FILE FIND + FINISH DISCIPLINE — mandatory]
You waste turns when you re-scan the project after the work is already done. Follow this strictly:

FIND FILES FAST (usually 1–3 tools total, never 20+):
1. The PROJECT FILE TREE lists FULL relative paths (e.g. `app/src/main/java/.../ChatScreen.kt`) — copy them exactly. Do NOT re-LIST the whole project.
2. FIND_FILES is smart: pass a bare name like `ChatScreen` or `ChatScreen.kt` (no need for **/* globs). One call returns ranked exact paths.
3. If you need content: GREP with a tight path= folder scope, not the whole tree.
4. CODEBASE_SEARCH once for concepts; SYMBOL_SEARCH for a class/function name.
5. READ_FILE only the 1–3 paths you will edit (use the exact path from tree/FIND_FILES).
6. Batch READ_FILES when you need several known paths — never 10 greps to rediscover them.

STOP WHEN DONE (critical):
• After successful edits/creates that satisfy the user request → ANSWER briefly + DONE immediately.
• Do NOT start a second "find the file again / double-check everything / re-grep the tree" phase.
• Do NOT re-open files you just wrote "to verify" unless STATIC VERIFY or the tool returned ERROR.
• Do NOT re-implement finished work from the fix ledger or last DONE summary.
• Ritual re-search after success is a bug — if the task is done, stop.

IF YOU ARE LOST (max budget):
• At most 2 explore tools to re-locate a path, then edit or ask. Never 10+ greps.
• Prefer REPO_MAP or GLOB over reading random folders.

ANDROID / APK (mandatory — sandbox cannot build):
• NEVER run ./gradlew, assemble, compileDebugKotlin, sdkmanager, or install JDK/Android SDK in CLOUD_SHELL.
• Instant checks only: VERIFY / VERIFY_BUILD (static offline — braces, package, manifest, Compose flags).
• Real APK: GH_BUILD_APK → GH_BUILD_STATUS only (GitHub Actions has the full toolchain).
• After edits: VERIFY the files, fix errors, then GH_BUILD_APK — not sandbox Gradle.
""".trimIndent()

    /**
     * After real mutations this turn — keep the model from spinning into re-search.
     */
    fun postEditStopExploreNudge(files: Collection<String>): String {
        val list = files.map { it.trim().removePrefix("./") }.filter { it.isNotBlank() }.distinct().take(10)
        val f = if (list.isEmpty()) "your edits" else list.joinToString(", ") { "`$it`" }
        return "[STOP EXPLORE] You already changed $f this run. " +
            "Do NOT re-LIST / re-GREP / re-search the project to re-find those files. " +
            "If the user request is satisfied → ANSWER + DONE now. " +
            "Only READ a path if the next edit needs content you do not have, or a tool ERROR requires a targeted fix.\n\n"
    }

    /**
     * When the model is thrashing on explore tools after work already landed.
     */
    fun exploreThrashNudge(exploreCount: Int, filesTouched: Collection<String>): String {
        val f = filesTouched.take(8).joinToString(", ").ifBlank { "(session files)" }
        return "[EXPLORE THRASH — $exploreCount search/read tools with no new work]\n" +
            "You already have session files: $f\n" +
            "Stop hunting. Either:\n" +
            "  • EDIT the known path(s) if something is still wrong, then ANSWER + DONE, OR\n" +
            "  • ANSWER + DONE if the user request is already complete.\n" +
            "Do NOT call LIST_FILES / GREP / CODEBASE_SEARCH again unless you lack a path for a concrete next edit.\n\n"
    }

    /**
     * Static-verify failures: fix listed files only, no whole-tree search.
     */
    fun staticVerifyFixNudge(report: String): String {
        val paths = Regex("""`([^`]+)`|([A-Za-z0-9_./-]+\.(kt|java|xml|kts|gradle|ts|tsx|js|py|go|rs))""")
            .findAll(report)
            .map { it.groupValues[1].ifBlank { it.groupValues[2] } }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
            .toList()
        val pathHint = if (paths.isEmpty()) "the file(s) named in the report"
        else paths.joinToString(", ") { "`$it`" }
        return "[FIX NEEDED — targeted only]\n" +
            "Static verify found errors. Fix ONLY $pathHint with EDIT/MULTI_EDIT.\n" +
            "Do NOT re-search the whole project or open unrelated files.\n" +
            "After the fix, call DONE (do not start a new discovery phase).\n\n"
    }

    fun isExplore(action: String): Boolean = action.lowercase() in EXPLORE_ACTIONS
    fun isWork(action: String): Boolean = action.lowercase() in WORK_ACTIONS
}
