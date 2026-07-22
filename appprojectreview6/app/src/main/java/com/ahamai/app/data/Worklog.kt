package com.ahamai.app.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Project worklog (`worklog.md` at project root).
 *
 * The model *can* call WORKLOG, but often forgets. The agent loop therefore auto-appends
 * on DONE / ANSWER and after real edit turns so the file always reflects work that happened.
 */
object Worklog {

    private const val HEADER = "# AhamAI Worklog\n\n"
    private val locks = ConcurrentHashMap<String, Any>()

    private fun lockFor(projectDir: String): Any =
        locks.getOrPut(projectDir) { Any() }

    fun file(projectDir: String): File = File(projectDir, "worklog.md")

    fun read(projectDir: String, maxChars: Int = 4000): String {
        if (projectDir.isBlank()) return ""
        return try {
            val f = file(projectDir)
            if (!f.exists()) "" else f.readText().take(maxChars)
        } catch (_: Exception) {
            ""
        }
    }

    // Last body fingerprint per project (dedupe ANSWER+DONE double-complete).
    private val lastBody = ConcurrentHashMap<String, Pair<String, Long>>()

    /**
     * Append a timestamped entry. Returns a short status for the model / UI.
     * [tag] is shown like `[Agent]` / `[Sub-agent]` / `[auto]`.
     */
    fun append(projectDir: String, entry: String, tag: String = "Agent"): String {
        if (projectDir.isBlank()) return "ERROR: no project directory for worklog"
        val body = entry.trim()
        if (body.isBlank()) return "ERROR: empty worklog entry"
        val safeTag = tag.trim().ifBlank { "Agent" }.take(40)
        val fp = body.take(120).lowercase()
        val now = System.currentTimeMillis()
        val prev = lastBody[projectDir]
        if (prev != null && prev.first == fp && now - prev.second < 45_000L) {
            return "Worklog already has this entry."
        }
        return try {
            synchronized(lockFor(projectDir)) {
                val f = file(projectDir)
                f.parentFile?.mkdirs()
                if (!f.exists()) f.writeText(HEADER)
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                val line = "\n---\n**[$ts]** [$safeTag] ${body.take(2000)}\n"
                f.appendText(line)
                lastBody[projectDir] = fp to now
            }
            // Keep memory in sync so next turns see the tail without re-reading the whole file.
            runCatching {
                ContextMemoryManager.observe(
                    projectDir,
                    listOf(CodeAgent.AgentStep("worklog", detail = body.take(400)))
                )
            }
            "Worklog updated."
        } catch (e: Exception) {
            "ERROR writing worklog: ${e.message?.take(120)}"
        }
    }

    /** Called when a run finishes (DONE / ANSWER). Idempotent-ish: skips empty summaries. */
    fun appendRunComplete(
        projectDir: String,
        task: String,
        summary: String,
        filesTouched: Collection<String> = emptyList(),
        tag: String = "Agent"
    ) {
        if (projectDir.isBlank()) return
        val sum = summary.trim().ifBlank { task.trim() }.ifBlank { "Task completed." }
        val files = filesTouched.map { it.trim().removePrefix("./") }.filter { it.isNotBlank() }.distinct()
        val filePart = if (files.isEmpty()) "" else " · files: ${files.take(12).joinToString(", ")}${if (files.size > 12) " +" + (files.size - 12) else ""}"
        val taskPart = task.trim().take(120)
        val entry = buildString {
            append("Completed")
            if (taskPart.isNotBlank()) append(": ").append(taskPart)
            if (sum.isNotBlank() && !sum.equals(taskPart, ignoreCase = true)) {
                append(" — ").append(sum.take(400))
            }
            append(filePart)
        }
        append(projectDir, entry, tag = tag)
    }

    /** Mid-run progress after real file mutations (not pure reads). */
    fun appendProgress(
        projectDir: String,
        turn: Int,
        actions: Collection<String>,
        files: Collection<String>
    ) {
        if (projectDir.isBlank()) return
        if (actions.isEmpty() && files.isEmpty()) return
        val a = actions.map { it.uppercase() }.distinct().take(8).joinToString(", ")
        val f = files.map { it.removePrefix("./") }.filter { it.isNotBlank() }.distinct().take(10)
        val entry = "Turn $turn · $a" +
            if (f.isEmpty()) "" else " · ${f.joinToString(", ")}"
        append(projectDir, entry, tag = "progress")
    }
}
