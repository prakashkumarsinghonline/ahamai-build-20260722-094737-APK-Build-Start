package com.ahamai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Cursor-style Mission Control task registry.
 * Tracks agent runs across projects: status, diff stats, pin, PR readiness.
 */
object AgentTaskStore {

    enum class Status(val label: String) {
        WORKING("Working"),
        NEEDS_YOU("Needs you"),
        FINISHED("Finished"),
        READY_MERGE("Ready to merge"),
        MERGED("Merged"),
        IDLE("No changes"),
        FAILED("Failed")
    }

    data class Task(
        val id: String = UUID.randomUUID().toString(),
        val projectDir: String,
        val title: String,
        val projectName: String,
        val repoLabel: String = "",
        val status: Status = Status.IDLE,
        val added: Int = 0,
        val removed: Int = 0,
        val fileCount: Int = 0,
        val pinned: Boolean = false,
        val elapsedSec: Int = 0,
        val prUrl: String = "",
        val checksPassed: Boolean = false,
        val lastPrompt: String = "",
        val updatedAt: Long = System.currentTimeMillis()
    )

    private const val PREFS = "ahamai_mission_tasks"
    private const val KEY = "tasks_v1"
    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile private var cache: List<Task>? = null

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { listeners.forEach { runCatching { it() } } }

    fun list(context: Context): List<Task> {
        cache?.let { return it }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i -> parse(arr.getJSONObject(i)) }
                .also { cache = it }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun pinned(context: Context): List<Task> =
        list(context).filter { it.pinned }.sortedByDescending { it.updatedAt }

    fun recent(context: Context, limit: Int = 40): List<Task> =
        list(context).sortedByDescending { it.updatedAt }.take(limit)

    fun active(context: Context): List<Task> =
        list(context).filter { it.status == Status.WORKING || it.status == Status.NEEDS_YOU }

    fun get(context: Context, id: String): Task? = list(context).find { it.id == id }

    fun getByProject(context: Context, projectDir: String): Task? =
        list(context).filter { it.projectDir == projectDir }.maxByOrNull { it.updatedAt }

    /** Upsert a run for a project directory. */
    fun upsert(
        context: Context,
        projectDir: String,
        title: String? = null,
        projectName: String? = null,
        repoLabel: String? = null,
        status: Status? = null,
        added: Int? = null,
        removed: Int? = null,
        fileCount: Int? = null,
        elapsedSec: Int? = null,
        prUrl: String? = null,
        checksPassed: Boolean? = null,
        lastPrompt: String? = null,
        pinned: Boolean? = null
    ): Task {
        val all = list(context).toMutableList()
        val idx = all.indexOfFirst { it.projectDir == projectDir }
        val base = if (idx >= 0) all[idx] else Task(
            projectDir = projectDir,
            title = title ?: projectName ?: "New task",
            projectName = projectName ?: projectDir.substringAfterLast('/'),
            repoLabel = repoLabel.orEmpty()
        )
        val next = base.copy(
            title = title?.takeIf { it.isNotBlank() } ?: base.title,
            projectName = projectName?.takeIf { it.isNotBlank() } ?: base.projectName,
            repoLabel = repoLabel ?: base.repoLabel,
            status = status ?: base.status,
            added = added ?: base.added,
            removed = removed ?: base.removed,
            fileCount = fileCount ?: base.fileCount,
            elapsedSec = elapsedSec ?: base.elapsedSec,
            prUrl = prUrl ?: base.prUrl,
            checksPassed = checksPassed ?: base.checksPassed,
            lastPrompt = lastPrompt ?: base.lastPrompt,
            pinned = pinned ?: base.pinned,
            updatedAt = System.currentTimeMillis()
        )
        if (idx >= 0) all[idx] = next else all.add(0, next)
        // Cap history
        val trimmed = all.sortedByDescending { it.updatedAt }.take(80)
        save(context, trimmed)
        return next
    }

    fun togglePin(context: Context, projectDir: String) {
        val t = getByProject(context, projectDir) ?: return
        upsert(context, projectDir, pinned = !t.pinned)
    }

    fun markFinished(
        context: Context,
        projectDir: String,
        added: Int,
        removed: Int,
        fileCount: Int,
        elapsedSec: Int,
        title: String? = null
    ) {
        val hasPr = getByProject(context, projectDir)?.prUrl?.isNotBlank() == true
        // Only pass title when caller wants to rename; null keeps the last prompt title
        // (previously projectName overwrote user query and looked like a stuck run).
        upsert(
            context,
            projectDir,
            title = title?.takeIf { it.isNotBlank() },
            // Completed runs → FINISHED (Completed section), not Idle
            status = if (hasPr) Status.READY_MERGE else Status.FINISHED,
            added = added,
            removed = removed,
            fileCount = fileCount,
            elapsedSec = elapsedSec
        )
    }

    fun markWorking(context: Context, projectDir: String, title: String, projectName: String, prompt: String) {
        val provisional = humanTitleHint(prompt).ifBlank {
            title.takeIf { it.isNotBlank() && !isGenericProjectLabel(it, projectName) }.orEmpty()
        }.ifBlank { "New task" }
        upsert(
            context,
            projectDir,
            title = provisional,
            projectName = projectName,
            status = Status.WORKING,
            lastPrompt = prompt
        )
    }

    /** Agent is blocked on user input (ASK_USER / permission prompt) → Action Required. */
    fun markNeedsYou(context: Context, projectDir: String, reason: String = "Waiting for your answer") {
        if (projectDir.isBlank()) return
        // Keep existing AI/user title; only flip status so the row stays readable
        upsert(context, projectDir, status = Status.NEEDS_YOU)
        // Stash reason in lastPrompt only if empty — don't wipe the real prompt
        val cur = getByProject(context, projectDir)
        if (cur != null && cur.lastPrompt.isBlank() && reason.isNotBlank()) {
            upsert(context, projectDir, lastPrompt = reason)
        }
    }

    /** Title shown in All tasks list — never fall back to bare folder label when we have better text. */
    fun displayTitle(task: Task): String {
        val t = task.title.trim()
        val pn = task.projectName.trim()
        if (t.isNotBlank() && !isGenericProjectLabel(t, pn)) return t
        val lp = task.lastPrompt.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (lp.isNotBlank()) return humanTitleHint(lp).ifBlank { lp.take(56) }
        return t.ifBlank { pn.ifBlank { "Untitled task" } }
    }

    private fun isGenericProjectLabel(title: String, projectName: String): Boolean {
        val t = title.trim()
        if (t.equals("Project", true) || t.equals("New task", true) || t.equals("Workspace", true)) return true
        if (projectName.isNotBlank() && t.equals(projectName, true)) return true
        if (t.equals(projectName.substringAfterLast('/'), true)) return true
        return false
    }

    /** Cheap local title before AI returns — first meaningful line, clipped. */
    fun humanTitleHint(prompt: String): String {
        val line = prompt.lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it.isNotBlank() &&
                    !it.startsWith("USER") &&
                    !it.startsWith("[[") &&
                    !it.startsWith("http")
            }
            .orEmpty()
            .removePrefix("Task:")
            .removePrefix("TASK:")
            .trim()
        if (line.isBlank()) return ""
        val cut = if (line.length <= 52) line else line.take(49).trimEnd() + "…"
        return cut.replace(Regex("\\s+"), " ")
    }

    /**
     * Ask the chat model for a short mission title (3–7 words). Best-effort;
     * keeps [humanTitleHint] if the call fails.
     */
    suspend fun generateAndApplyTitle(
        context: Context,
        projectDir: String,
        userTask: String,
        baseUrl: String,
        apiKey: String,
        model: String
    ) {
        if (projectDir.isBlank() || userTask.isBlank()) return
        if (baseUrl.isBlank() || apiKey.isBlank()) {
            val hint = humanTitleHint(userTask)
            if (hint.isNotBlank()) upsert(context, projectDir, title = hint)
            return
        }
        val title = withContext(Dispatchers.IO) {
            runCatching {
                val msgs = listOf(
                    "system" to (
                        "You name coding-agent tasks. Reply with ONLY a short title (3–7 words). " +
                            "No quotes, no trailing punctuation, no folder/project labels like \"Project\". " +
                            "Capture the user's goal, not the tech stack alone."
                        ),
                    "user" to userTask.take(600)
                )
                val raw = ApiClient.sendChatMessage(baseUrl, apiKey, model, msgs).getOrNull().orEmpty()
                raw.lineSequence()
                    .map {
                        it.trim()
                            .trim('"', '\'', '`', '“', '”', '.', '!')
                            .removePrefix("Title:")
                            .removePrefix("title:")
                            .trim()
                    }
                    .firstOrNull { it.length in 3..72 }
            }.getOrNull()
        }
        val finalTitle = title?.takeIf { it.isNotBlank() } ?: humanTitleHint(userTask)
        if (finalTitle.isNotBlank()) {
            upsert(context, projectDir, title = finalTitle.take(72))
        }
    }

    /**
     * Clear stale WORKING / NEEDS_YOU when the process is no longer running
     * (app killed mid-run, finished without markFinished, etc.).
     * Keeps history truthful so "In Progress" doesn't stick after completion.
     */
    fun reconcileStale(context: Context) {
        val all = list(context)
        var changed = false
        val next = all.map { t ->
            val live = com.ahamai.app.service.RunningTasks.isRunning(t.projectDir)
            if (!live && (t.status == Status.WORKING || t.status == Status.NEEDS_YOU)) {
                changed = true
                t.copy(
                    status = when {
                        t.added > 0 || t.removed > 0 || t.fileCount > 0 -> Status.FINISHED
                        else -> Status.IDLE
                    }
                    // keep updatedAt so sort order doesn't jump
                )
            } else t
        }
        if (changed) save(context, next)
    }

    fun remove(context: Context, projectDir: String) {
        save(context, list(context).filterNot { it.projectDir == projectDir })
    }

    private fun save(context: Context, tasks: List<Task>) {
        cache = tasks
        val arr = JSONArray()
        tasks.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("projectDir", t.projectDir)
                put("title", t.title)
                put("projectName", t.projectName)
                put("repoLabel", t.repoLabel)
                put("status", t.status.name)
                put("added", t.added)
                put("removed", t.removed)
                put("fileCount", t.fileCount)
                put("pinned", t.pinned)
                put("elapsedSec", t.elapsedSec)
                put("prUrl", t.prUrl)
                put("checksPassed", t.checksPassed)
                put("lastPrompt", t.lastPrompt)
                put("updatedAt", t.updatedAt)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
        notifyListeners()
    }

    private fun parse(o: JSONObject): Task? = try {
        Task(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            projectDir = o.getString("projectDir"),
            title = o.optString("title", "Task"),
            projectName = o.optString("projectName", ""),
            repoLabel = o.optString("repoLabel", ""),
            status = runCatching { Status.valueOf(o.optString("status", "IDLE")) }.getOrDefault(Status.IDLE),
            added = o.optInt("added"),
            removed = o.optInt("removed"),
            fileCount = o.optInt("fileCount"),
            pinned = o.optBoolean("pinned"),
            elapsedSec = o.optInt("elapsedSec"),
            prUrl = o.optString("prUrl", ""),
            checksPassed = o.optBoolean("checksPassed"),
            lastPrompt = o.optString("lastPrompt", ""),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
        )
    } catch (_: Exception) { null }

    fun formatElapsed(sec: Int): String = when {
        sec < 60 -> "${sec}s"
        sec < 3600 -> "${sec / 60}m"
        else -> "${sec / 3600}h ${(sec % 3600) / 60}m"
    }
}
