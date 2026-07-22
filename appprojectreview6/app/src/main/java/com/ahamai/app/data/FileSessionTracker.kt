package com.ahamai.app.data

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-agent-run set of paths the model has successfully READ this turn/session.
 * Used to allow full WRITE rewrites only after a read (avoids blind large overwrites).
 */
object FileSessionTracker {
    private val readByProject = ConcurrentHashMap<String, MutableSet<String>>()

    private fun key(projectDir: String) = projectDir.trim().ifBlank { "_" }

    private fun norm(path: String): String =
        ProjectManager.cleanRelPath(path).lowercase()

    fun clear(projectDir: String) {
        readByProject.remove(key(projectDir))
    }

    fun clearAll() {
        readByProject.clear()
    }

    fun markRead(projectDir: String, relPath: String) {
        val p = norm(relPath)
        if (p.isBlank()) return
        readByProject.getOrPut(key(projectDir)) { ConcurrentHashMap.newKeySet() }.add(p)
    }

    fun markReadMany(projectDir: String, paths: Collection<String>) {
        paths.forEach { markRead(projectDir, it) }
    }

    fun wasRead(projectDir: String, relPath: String): Boolean {
        val p = norm(relPath)
        if (p.isBlank()) return false
        val set = readByProject[key(projectDir)] ?: return false
        if (p in set) return true
        // basename fallback (model sometimes uses short names after fuzzy read)
        val base = p.substringAfterLast('/')
        return set.any { it == base || it.endsWith("/$base") }
    }
}
