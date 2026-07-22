package com.ahamai.app.service

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * App-wide, Compose-observable registry of tasks currently doing work
 * (a chat that is streaming, or an agent/workspace run in progress).
 *
 * The Chat History screen reads [tasks] so it can show a live "Running" pill,
 * and [BackgroundTaskManager] reads [hasAny] to decide whether to keep the
 * process alive with a foreground service when the app is minimized.
 *
 * The backing list is a snapshot state list, so it is safe to mutate from any
 * thread and any read inside a @Composable will recompose automatically.
 */
object RunningTasks {

    enum class Type { CHAT, WORKSPACE }

    data class Task(
        val id: String,
        val type: Type,
        val title: String,
        val startedAt: Long
    )

    val tasks: SnapshotStateList<Task> = mutableStateListOf()

    val hasAny: Boolean get() = tasks.isNotEmpty()

    fun isRunning(id: String): Boolean = tasks.any { it.id == id }

    /** Marks [id] as running (or refreshes its title if already running). */
    fun start(id: String, type: Type, title: String) {
        if (id.isBlank()) return
        val idx = tasks.indexOfFirst { it.id == id }
        if (idx >= 0) {
            // Keep original start time, just refresh the title.
            val existing = tasks[idx]
            if (existing.title != title) tasks[idx] = existing.copy(title = title.ifBlank { existing.title })
        } else {
            tasks.add(Task(id, type, title.ifBlank { if (type == Type.CHAT) "Chat" else "Workspace" }, System.currentTimeMillis()))
        }
    }

    /** Removes [id] from the running set (work finished, failed or cancelled). */
    fun finish(id: String) {
        if (id.isBlank()) return
        tasks.removeAll { it.id == id }
    }
}
