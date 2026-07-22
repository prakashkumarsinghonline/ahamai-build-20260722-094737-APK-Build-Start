package com.ahamai.app.data

/**
 * Decides whether a model DONE should actually end the run, or one soft nudge is needed.
 * Fixes “kaam complete karke phir se shuru” from:
 *  - sticky incomplete PLAN
 *  - over-broad deliverable keywords (e.g. word “file”)
 *  - double verify after success
 */
object AgentCompletionGuard {

    enum class Decision {
        /** End the agent loop now. */
        FINISH,
        /** One nudge only; [nudgeMessage] goes back to the model. */
        NUDGE
    }

    data class Result(
        val decision: Decision,
        val nudgeMessage: String = "",
        /** Clear durable plan when finishing so it never blocks the next DONE. */
        val clearPlan: Boolean = false
    )

    /**
     * @param alreadyNudged [verified] flag in the loop — at most one nudge per run
     * @param turn current agent turn index
     */
    fun evaluateDone(
        projectDir: String,
        alreadyNudged: Boolean,
        turn: Int,
        hasAnswer: Boolean,
        didEdits: Boolean,
        sessionFiles: Set<String>,
        hasFileChip: Boolean,
        userTask: String
    ): Result {
        // Second DONE (or after any nudge) → always finish. Prevents restart loops.
        if (alreadyNudged) {
            return Result(Decision.FINISH, clearPlan = true)
        }

        val incomplete = if (projectDir.isNotBlank()) {
            ContextMemoryManager.incompletePlanSteps(projectDir)
        } else emptyList()

        val hasOutput = hasAnswer || sessionFiles.isNotEmpty() || didEdits

        // Plan steps open but real work already done → auto-finish (don't force re-work).
        if (incomplete.isNotEmpty() && hasOutput) {
            return Result(Decision.FINISH, clearPlan = true)
        }

        // Plan open and nothing produced yet → one nudge
        if (incomplete.isNotEmpty() && !hasOutput && turn < 6) {
            val list = incomplete.take(6).mapIndexed { i, t -> "  ${i + 1}. $t" }.joinToString("\n")
            return Result(
                Decision.NUDGE,
                nudgeMessage = "[PLAN OPEN]\nStill open:\n$list\n" +
                    "Do the remaining work OR call DONE again to abandon leftover plan steps. " +
                    "Do NOT restart work that is already done.\n\n"
            )
        }

        // No output at all early in the run
        if (!hasOutput && turn < 4) {
            return Result(
                Decision.NUDGE,
                nudgeMessage = "[SELF-CHECK] DONE with no files/answer yet. Do the real work, then ANSWER + DONE. " +
                    "Do not loop empty DONE calls.\n\n"
            )
        }

        // Deliverable: only strong signals (avoid matching every task with the word "file")
        if (wantsStrongDeliverable(userTask) && !hasDeliverable(sessionFiles) && turn < 8) {
            return Result(
                Decision.NUDGE,
                nudgeMessage = "[DELIVERABLE] User asked for a document/artifact (pdf/docx/xlsx/pptx/zip/image). " +
                    "Create it with the proper tool, then DONE. Do not restart unrelated edits.\n\n"
            )
        }

        // Edits present: optional static verify once (caller may still run StaticVerifier).
        // We finish by default; caller handles FAILED verify separately.
        return Result(Decision.FINISH, clearPlan = true)
    }

    /**
     * Mark run complete in memory so follow-ups don't re-expand old goals.
     * Also auto-appends [Worklog] so the project worklog updates even when the model
     * never called the WORKLOG tool (the common case).
     */
    fun markRunComplete(
        projectDir: String,
        summary: String,
        task: String = "",
        filesTouched: Collection<String> = emptyList()
    ) {
        if (projectDir.isBlank()) return
        val mem = ContextMemoryManager.get(projectDir)
        mem.lastDoneSummary = summary.take(800).ifBlank { "Task completed." }
        mem.openLoops.clear()
        // Drop active plan so next DONE never re-opens old checklist
        mem.activePlan.clear()
        // Pin so follow-ups see "already done"
        val pin = "COMPLETED: ${summary.take(200).ifBlank { "last user request" }}"
        mem.facts.removeAll { it.text.startsWith("COMPLETED:") }
        mem.facts.add(ContextMemoryManager.Fact(pin, source = "auto"))
        ContextMemoryManager.save(projectDir)
        // Durable human-readable trail in project root worklog.md
        Worklog.appendRunComplete(
            projectDir = projectDir,
            task = task.ifBlank { mem.lastTask },
            summary = summary,
            filesTouched = filesTouched.ifEmpty {
                mem.fixes.takeLast(16).map { it.path }.filter { it.isNotBlank() }
            },
            tag = "Agent"
        )
    }

    fun wantsStrongDeliverable(userTask: String): Boolean {
        val t = userTask.lowercase()
        // Intentionally NO bare "file" / "document" alone — too many false positives.
        val keys = listOf(
            "pdf", "docx", "xlsx", "pptx", ".pdf", ".docx", ".xlsx", ".pptx",
            "spreadsheet", "powerpoint", "slide deck", "presentation",
            "invoice", "resume", "export zip", "make a zip", "generate pdf",
            "create pdf", "create excel", "create powerpoint", "create word"
        )
        return keys.any { t.contains(it) }
    }

    fun hasDeliverable(sessionFiles: Set<String>): Boolean {
        val exts = setOf("pdf", "docx", "xlsx", "pptx", "zip", "png", "jpg", "jpeg", "webp", "csv")
        return sessionFiles.any { f ->
            val e = f.substringAfterLast('.', "").lowercase()
            e in exts
        }
    }

    /** Extra line on follow-up prompts when last run completed. */
    fun followUpStopRedoBlock(projectDir: String): String {
        if (projectDir.isBlank()) return ""
        val mem = ContextMemoryManager.get(projectDir)
        val done = mem.lastDoneSummary.trim()
        if (done.isBlank()) return ""
        return "\n[PREVIOUS RUN COMPLETED]\n$done\n" +
            "Do NOT re-implement, re-search, or re-edit that work unless this follow-up explicitly asks to change it.\n" +
            "Do NOT re-open a discovery phase (LIST/GREP/CODEBASE_SEARCH) for finished work.\n" +
            "Only do what THIS message newly requires, then ANSWER + DONE.\n"
    }
}
