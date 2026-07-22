package com.ahamai.app.data

/**
 * Per-tool wall-clock budgets for the agent loop.
 *
 * Goals:
 *  - Every tool has a finite timeout (nothing can hang the UI forever).
 *  - Long cloud/browser/build work gets a large budget so legitimate slow work is NOT
 *    treated as "stuck".
 *  - The model is told the budget + what to do on timeout vs. when to keep waiting / split work.
 */
object ToolTimeouts {

    /** Human label for UI: "60s", "3m", "10m". */
    fun formatBudget(ms: Long): String {
        if (ms < 1_000L) return "${ms}ms"
        val s = ms / 1000L
        return if (s < 60L) "${s}s"
        else if (s % 60L == 0L) "${s / 60L}m"
        else "${s / 60L}m ${s % 60L}s"
    }

    fun formatElapsed(ms: Long): String {
        if (ms < 1_000L) return "${ms}ms"
        val s = ms / 1000L
        return if (s < 60L) "${s}s"
        else "${s / 60L}m ${s % 60L}s"
    }

    /**
     * True when this tool is expected to take a long time when working correctly
     * (installs, builds, scans, browser first-open, sub-agents).
     */
    fun isLongRunning(action: String): Boolean {
        val a = action.lowercase().trim()
        return a in LONG_RUNNING || a.startsWith("browser") || a.startsWith("computer") ||
            a.startsWith("ghbuild") || a.startsWith("cloud") || a.startsWith("vuln") ||
            a.startsWith("recon") || a == "task" || a == "paralleltasks" || a == "runapp" ||
            a == "makeshorts" || a == "siteclone" || a == "apkdecompile" || a == "apkrebuild"
    }

    private val LONG_RUNNING = setOf(
        "cloudshell", "runjob", "jobstatus", "cloudinstall", "cloudpull", "cloudpush",
        "ghbuild", "ghbuildstatus", "ghlogs", "ghopenrepo", "ghpush",
        "apkdecompile", "apkrebuild", "securityaudit", "webscan",
        "recon", "portscan", "vulnscan", "dirfuzz", "niktoscan", "sqlitest",
        "sslscan", "urlharvest", "xssscan", "cmditest", "crlfscan",
        "paramdiscover", "contentdiscover", "fastportscan", "deepsecrets",
        "wpscan", "sast", "reposecrets", "socialosint", "siteclone",
        "task", "paralleltasks", "runapp", "makeshorts", "runpython",
        "browseropen", "generateimage", "screenshot", "imageedit"
    )

    /** Wall-clock budget in ms for [action]. Always finite. */
    fun ms(action: String): Long {
        val a = action.lowercase().trim()
        return when (a) {
            // Cloud shell / jobs — real work can be slow; do not cut off early.
            "cloudshell", "runjob" -> 180_000L
            "jobstatus" -> 90_000L
            // GitHub builds / logs / heavy repo ops
            "ghbuild" -> 180_000L
            "ghbuildstatus" -> 180_000L
            "ghlogs" -> 120_000L
            "ghpush", "ghpr" -> 90_000L
            "ghopenrepo", "ghswitchbranch", "ghcreatebranch", "ghcreaterepo" -> 120_000L
            "ghrepos", "ghbranches", "ghreadremote", "ghlistremote",
            "ghstatus", "ghissues", "ghcreateissue" -> 60_000L
            // APK / security scans
            "apkdecompile", "apkrebuild", "securityaudit", "webscan" -> 150_000L
            "recon", "portscan", "vulnscan", "dirfuzz", "niktoscan", "sqlitest",
            "sslscan", "urlharvest", "xssscan", "cmditest", "crlfscan",
            "paramdiscover", "contentdiscover", "fastportscan", "deepsecrets",
            "wpscan", "sast", "reposecrets", "socialosint" -> 120_000L
            // Browser: first open installs Playwright + Chromium (can take minutes).
            "browseropen" -> 600_000L
            "browserclick", "browsertype", "browserpress", "browserscroll",
            "browserback", "browserextract", "browserview", "browserupload",
            "browserselect", "browserrotate", "browserresize" -> 120_000L
            "computeropen" -> 600_000L
            "computerclick", "computermove", "computertype", "computerkey",
            "computerscreenshot", "computerscroll" -> 120_000L
            // Cloud install / clone
            "cloudinstall", "siteclone" -> 120_000L
            "cloudpull", "cloudpush", "cloudls" -> 90_000L
            // Sub-agents
            "task", "paralleltasks" -> 300_000L
            // Media / docs
            "createxlsx", "createpptx", "createdocx", "makeshorts",
            "imageedit", "generateimage", "screenshot", "renderdiagram",
            "renderchart" -> 90_000L
            "runpython" -> 90_000L
            "runapp" -> 240_000L
            // Network
            "fetch", "websearch", "imagesearch", "readurl", "http" -> 45_000L
            "download", "downloaddevice" -> 120_000L
            "listartifacts", "captureartifact" -> 20_000L
            // Local file ops — must stay snappy
            "read", "readlines", "readfiles", "edit", "insertlines", "write", "create",
            "delete", "copy", "move", "search", "grep", "glob", "list", "bulkedit",
            "multiedit", "applypatch", "checkhtml", "symbolsearch", "gotodefinition",
            "findreferences", "documentsymbols", "workspacesymbols", "hover",
            "codebasesearch", "importadd", "refactorrename", "diffhistory", "repomap",
            "codebasewiki", "snippetsave", "snippetlist", "snippetload", "unzip", "zip",
            "memory", "remember", "forget", "worklog", "verify", "verifybuild",
            "diagnose", "lint", "runtests", "formatcode", "depsadd", "gitdiff",
            "gitcommit", "loadskill", "loadtools", "plan", "completestep",
            "answer", "done", "askuser" -> 30_000L
            // PDF local
            "pdfcreate", "pdfedit", "pdfaddpage", "pdfaddimage", "pdfaddchart",
            "pdfmerge", "pdfsplit", "pdfread", "pdffillform", "pdfcompress",
            "pdfcollage", "createcsv" -> 45_000L
            else -> 90_000L // safe default — never infinite
        }
    }

    /** Compact UI line: "12s / 3m" or "timeout 3m". */
    fun progressLabel(elapsedMs: Long, budgetMs: Long, finished: Boolean): String {
        val budget = formatBudget(budgetMs)
        return if (finished) {
            val el = formatElapsed(elapsedMs)
            "$el · budget $budget"
        } else {
            val el = formatElapsed(elapsedMs)
            val longHint = if (budgetMs >= 90_000L && elapsedMs > 8_000L) " · working…" else ""
            "$el / $budget$longHint"
        }
    }

    /**
     * Message fed back to the model after a hard timeout.
     * Distinguishes long legitimate work from hung tools, and tells the agent how to recover.
     */
    fun timeoutMessageForModel(
        action: String,
        path: String,
        budgetMs: Long,
        attempt: Int = 1,
        maxAttempts: Int = 2
    ): String {
        val a = action.uppercase()
        val budget = formatBudget(budgetMs)
        val target = listOf(a, path).filter { it.isNotBlank() }.joinToString(" ")
        val long = isLongRunning(action)
        return buildString {
            append("[TIMEOUT] $target hit the wall-clock budget of $budget (attempt $attempt/$maxAttempts).\n")
            append("This is a HARD stop — the tool was cancelled. The overall task is NOT failed.\n")
            if (long) {
                append(
                    "This tool often takes a long time when it is WORKING correctly " +
                        "(cloud install, browser first-open, build, scan, sub-agent). " +
                        "A timeout usually means the command was too large or hung — NOT that you should give up.\n"
                )
                append("FIX now:\n")
                append("  1. Split the work: shorter CLOUD_SHELL commands, smaller scopes, one phase at a time.\n")
                append("  2. Prefer JOB_STATUS / GH_BUILD_STATUS polling over one mega-command.\n")
                append("  3. Do NOT re-run the exact same long command with the same args.\n")
                append("  4. Continue with a different approach; then ANSWER + DONE when the user request is met.\n")
            } else {
                append("FIX now:\n")
                append("  1. Retry only with DIFFERENT args (narrower path, smaller query).\n")
                append("  2. If it times out again, switch tools or skip this step.\n")
                append("  3. Do not loop the same call. Finish with ANSWER + DONE if blocked.\n")
            }
            append("\n")
        }
    }

    /** Interval for agent-facing progress pings while a tool is still running. */
    const val PING_EVERY_MS = 30_000L

    /**
     * One line the model sees every [PING_EVERY_MS] while a tool is alive.
     * Makes clear that work is still progressing (not stuck).
     */
    fun progressPingLine(
        action: String,
        path: String,
        elapsedMs: Long,
        budgetMs: Long,
        pingIndex: Int
    ): String {
        val a = action.uppercase().ifBlank { "TOOL" }
        val target = path.trim().take(80)
        val el = formatElapsed(elapsedMs)
        val budget = formatBudget(budgetMs)
        val remaining = (budgetMs - elapsedMs).coerceAtLeast(0L)
        val rem = formatBudget(remaining)
        val long = isLongRunning(action)
        return buildString {
            append("[PROGRESS PING #").append(pingIndex).append("] ")
            append(a)
            if (target.isNotBlank()) append(" · ").append(target)
            append(" still running · elapsed ").append(el)
            append(" / budget ").append(budget)
            append(" · ~").append(rem).append(" left")
            if (long) {
                append(" — long ops (cloud/browser/build/scan) often need this time; NOT stuck")
            } else {
                append(" — still working under budget")
            }
        }
    }

    /** Wrap final tool output with any collected 30s pings so the model can see the timeline. */
    fun attachPingsToResult(result: String, pings: List<String>): String {
        if (pings.isEmpty()) return result
        val header = buildString {
            appendLine("[PROGRESS] Tool was alive for multiple 30s heartbeats (healthy long work, not stuck):")
            pings.forEach { appendLine("  · $it") }
            appendLine()
        }
        return header + result
    }

    /**
     * Injected into the agent system/user context so the model knows budgets exist.
     */
    fun agentPolicyBlock(): String = """
[TOOL TIMEOUTS & 30s PROGRESS PINGS — read carefully]
Every tool call has a wall-clock budget. Slow cloud/browser/build/scan tools get several minutes; local file tools get ~30s.
• While a tool runs, you may see [PROGRESS PING #N] every 30 seconds. That means the tool is STILL WORKING under budget — treat it as healthy progress, NOT stuck. Do not abandon or restart just because time is passing.
• If a tool is still RUNNING under its budget, that is normal for long work (first browser open, apt install, APK build, nuclei, sub-agents).
• If you receive [TIMEOUT], the tool was hard-cancelled. Recover: split the command, narrow scope, poll status tools, or change approach — never blindly re-run the identical long call.
• Prefer one batched CLOUD_SHELL over many tiny ones, but if a single command may exceed ~2–3 minutes, break it into steps.
• After a timeout, keep going on the rest of the task; only stop when the user request is done or truly blocked.
""".trimIndent()
}
