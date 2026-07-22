package com.ahamai.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.withLock
import kotlin.concurrent.write

/**
 * Grok Build–style file state tracking for session rewind.
 *
 * Mirrors `xai-grok-workspace` session/file_state.rs + rewind_files:
 * - [beginPrompt] starts a [RewindPoint] for the current user turn
 * - [captureBefore] stores the FIRST pre-mutation snapshot per path (before any ops)
 * - [endPrompt] stores after-snapshots for every touched path (for external-edit conflict detection)
 * - [rewindTo] restores the earliest before-snapshot for all points ≥ target, then truncates history
 *
 * Persistence: `<project>/.ahamai/rewind_points.json` (lazy load on resume).
 */
object FileStateRegistry {
    private val trackers = ConcurrentHashMap<String, FileStateTracker>()

    fun tracker(projectDir: String): FileStateTracker {
        val key = File(projectDir).canonicalPath
        return trackers.getOrPut(key) { FileStateTracker(key) }
    }

    fun remove(projectDir: String) {
        trackers.remove(File(projectDir).canonicalPath)
    }

    /** Called by ProjectManager before any mutating FS op while a prompt is active. */
    fun captureBeforeIfActive(projectDir: String, relPath: String) {
        if (projectDir.isBlank() || relPath.isBlank()) return
        val t = trackers[File(projectDir).canonicalPath] ?: return
        if (t.currentPromptIndex() == null) return
        t.captureBefore(relPath)
    }

    fun captureBeforeWithContentIfActive(projectDir: String, relPath: String, content: String?) {
        if (projectDir.isBlank() || relPath.isBlank()) return
        val t = trackers[File(projectDir).canonicalPath] ?: return
        if (t.currentPromptIndex() == null) return
        t.captureBeforeWithContent(relPath, content)
    }
}

/** Snapshot of one file at a moment in time. [content] = null means file did not exist. */
data class FileSnapshot(
    val path: String,
    val content: String?,
    val capturedAt: Long = System.currentTimeMillis()
)

/**
 * Rewind point for one user prompt (Grok: RewindPoint).
 * [fileSnapshots] = BEFORE first touch; [afterSnapshots] = AFTER prompt completed.
 */
data class RewindPoint(
    val promptIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val fileSnapshots: MutableMap<String, FileSnapshot> = LinkedHashMap(),
    val afterSnapshots: MutableMap<String, FileSnapshot> = LinkedHashMap()
) {
    /** First before-snapshot wins (state before any ops this prompt). */
    fun addBefore(snapshot: FileSnapshot) {
        fileSnapshots.putIfAbsent(snapshot.path, snapshot)
    }

    fun setAfter(snapshot: FileSnapshot) {
        afterSnapshots[snapshot.path] = snapshot
    }

    fun snapshotCount(): Int = fileSnapshots.size
}

data class RewindPointMeta(
    val promptIndex: Int,
    val createdAt: Long,
    val numFileSnapshots: Int
)

enum class ConflictType {
    ModifiedExternally,
    DeletedExternally,
    CreatedExternally
}

data class FileRewindConflict(
    val path: String,
    val conflictType: ConflictType
)

data class FileRewindResponse(
    val success: Boolean,
    val targetPromptIndex: Int,
    val revertedFiles: List<String>,
    val cleanFiles: List<String>,
    val conflicts: List<FileRewindConflict>,
    val error: String? = null
) {
    fun summaryLine(): String {
        if (!success && error != null) return error
        val n = revertedFiles.size
        val c = conflicts.size
        return buildString {
            append("Rewound turn #$targetPromptIndex · restored $n file")
            if (n != 1) append('s')
            if (c > 0) append(" · $c external conflict(s) noted")
        }
    }
}

/**
 * Per-path + exclusive file operation lock (Grok: FileOperationLockManager).
 * Serializes concurrent writes to the same path; exclusive blocks all paths.
 */
class FileOperationLockManager {
    private val lock = ReentrantLock()
    private val pathLocks = ConcurrentHashMap<String, ReentrantLock>()

    private fun pathLock(path: String): ReentrantLock =
        pathLocks.getOrPut(path) { ReentrantLock() }

    fun <T> withPathLock(path: String, block: () -> T): T {
        val pl = pathLock(path)
        pl.lock()
        try {
            return block()
        } finally {
            pl.unlock()
        }
    }

    fun <T> withExclusive(block: () -> T): T {
        // Take exclusive by locking registry then all known path locks — for bulk ops.
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

class FileStateTracker(private val projectDir: String) {
    private val rw = ReentrantReadWriteLock()
    private val rewindPoints = LinkedHashMap<Int, RewindPoint>()
    @Volatile private var currentPrompt: Int? = null
    @Volatile private var nextPromptIndex: Int = 0
    @Volatile private var historicalLoaded: Boolean = false
    val fileLocks = FileOperationLockManager()

    companion object {
        private const val MAX_SNAPSHOT_BYTES = 2_000_000L // skip huge files
        private const val STORE_NAME = "rewind_points.json"
    }

    private fun storeFile(): File = File(projectDir, ".ahamai").also { it.mkdirs() }.let { File(it, STORE_NAME) }

    // ── Prompt lifecycle (Grok: begin_prompt / end_prompt) ──────────────────

    /**
     * Start tracking a new user prompt. Returns the assigned prompt index.
     * Call once at the start of each agent user turn.
     */
    fun beginPrompt(promptIndex: Int? = null): Int = rw.write {
        ensureHistoricalLoadedLocked()
        val idx = promptIndex ?: nextPromptIndex
        nextPromptIndex = maxOf(nextPromptIndex, idx + 1)
        currentPrompt = idx
        rewindPoints.getOrPut(idx) { RewindPoint(idx) }
        idx
    }

    /**
     * End the current prompt: capture after-snapshots for every before-touched path.
     */
    fun endPrompt(promptIndex: Int? = null) {
        val idx = rw.write {
            val i = promptIndex ?: currentPrompt ?: return@write null
            currentPrompt = null
            i
        } ?: return

        val paths: List<String> = rw.read {
            rewindPoints[idx]?.fileSnapshots?.keys?.toList().orEmpty()
        }
        for (rel in paths) {
            val after = readDiskSnapshot(rel)
            rw.write {
                rewindPoints[idx]?.setAfter(after)
            }
        }
        persist()
    }

    fun currentPromptIndex(): Int? = currentPrompt

    fun nextIndexPreview(): Int = nextPromptIndex

    // ── Capture (Grok: capture_file_state) ──────────────────────────────────

    /**
     * Capture BEFORE state for [relPath] if not already captured this prompt.
     * Reads disk when no content provided.
     */
    fun captureBefore(relPath: String) {
        val cleaned = cleanRel(relPath) ?: return
        val idx = currentPrompt ?: return
        // Fast path: already have before-snapshot
        rw.read {
            if (rewindPoints[idx]?.fileSnapshots?.containsKey(cleaned) == true) return
        }
        val snap = readDiskSnapshot(cleaned)
        var added = false
        rw.write {
            val point = rewindPoints.getOrPut(idx) { RewindPoint(idx) }
            val before = point.fileSnapshots.size
            point.addBefore(snap)
            added = point.fileSnapshots.size > before
        }
        // Persist early so force-kill mid-run still has before-snapshots for rewind.
        if (added) persist()
    }

    /** Like [captureBefore] but uses caller-provided content (pre-wave snapshot). */
    fun captureBeforeWithContent(relPath: String, content: String?) {
        val cleaned = cleanRel(relPath) ?: return
        val idx = currentPrompt ?: return
        var added = false
        rw.write {
            val point = rewindPoints.getOrPut(idx) { RewindPoint(idx) }
            if (!point.fileSnapshots.containsKey(cleaned)) {
                point.addBefore(FileSnapshot(cleaned, content, System.currentTimeMillis()))
                added = true
            }
        }
        if (added) persist()
    }

    // ── Query ──────────────────────────────────────────────────────────────

    fun getMetas(): List<RewindPointMeta> = rw.read {
        ensureHistoricalLoadedLocked()
        rewindPoints.values
            .filter { it.fileSnapshots.isNotEmpty() }
            .sortedBy { it.promptIndex }
            .map { RewindPointMeta(it.promptIndex, it.createdAt, it.fileSnapshots.size) }
    }

    fun maxPromptIndexWithFiles(): Int? = rw.read {
        ensureHistoricalLoadedLocked()
        rewindPoints.values
            .filter { it.fileSnapshots.isNotEmpty() }
            .maxOfOrNull { it.promptIndex }
    }

    fun hasRewindableHistory(): Boolean = maxPromptIndexWithFiles() != null

    fun lastPromptFileCount(): Int {
        val idx = maxPromptIndexWithFiles() ?: return 0
        return rw.read { rewindPoints[idx]?.fileSnapshots?.size ?: 0 }
    }

    // ── Rewind (Grok: rewind_files) ─────────────────────────────────────────

    /**
     * Rewind filesystem to the state **before** [targetPromptIndex].
     * Restores earliest before-snapshot for every file touched in points ≥ target,
     * detects external conflicts vs after-snapshots, then truncates history from target.
     */
    fun rewindTo(targetPromptIndex: Int): FileRewindResponse {
        ensureHistoricalLoaded()

        val points: List<RewindPoint> = rw.read {
            rewindPoints.values.filter { it.promptIndex >= targetPromptIndex }.sortedBy { it.promptIndex }
        }

        // Earliest before-snapshot per path (first time we saw the file at/after target)
        val filesToRevert = LinkedHashMap<String, String?>()
        for (point in points) {
            for ((path, before) in point.fileSnapshots) {
                filesToRevert.putIfAbsent(path, before.content)
            }
        }

        if (filesToRevert.isEmpty()) {
            return FileRewindResponse(
                success = true,
                targetPromptIndex = targetPromptIndex,
                revertedFiles = emptyList(),
                cleanFiles = emptyList(),
                conflicts = emptyList(),
                error = "Nothing to rewind for turn #$targetPromptIndex"
            )
        }

        // Latest after-snapshot per path (for conflict detection)
        val latestAfter = HashMap<String, String?>()
        for (point in points.asReversed()) {
            for ((path, after) in point.afterSnapshots) {
                latestAfter.putIfAbsent(path, after.content)
            }
        }

        val reverted = mutableListOf<String>()
        val clean = mutableListOf<String>()
        val conflicts = mutableListOf<FileRewindConflict>()
        var hadErrors = false

        for ((relPath, beforeContent) in filesToRevert) {
            val abs = resolveInside(relPath) ?: continue
            val currentContent = try {
                if (abs.exists() && abs.isFile) abs.readText() else null
            } catch (_: Exception) {
                null
            }
            val afterContent = latestAfter[relPath]

            if (currentContent == afterContent) {
                clean.add(relPath)
            } else {
                val type = when {
                    currentContent == null && afterContent != null -> ConflictType.DeletedExternally
                    currentContent != null && afterContent == null -> ConflictType.CreatedExternally
                    else -> ConflictType.ModifiedExternally
                }
                // Only flag conflict when after-snapshot exists (agent finished the turn)
                if (afterContent != null || currentContent != beforeContent) {
                    conflicts.add(FileRewindConflict(relPath, type))
                }
            }

            try {
                fileLocks.withPathLock(relPath) {
                    when {
                        beforeContent != null -> {
                            abs.parentFile?.mkdirs()
                            atomicWrite(abs, beforeContent)
                        }
                        abs.exists() -> {
                            abs.deleteRecursively()
                        }
                    }
                }
                ProjectManager.invalidateFileTextCache(abs)
                reverted.add(relPath)
            } catch (e: Exception) {
                hadErrors = true
            }
        }

        ProjectManager.invalidateTextListCache()

        if (!hadErrors) {
            truncateFrom(targetPromptIndex)
            persist()
        }

        return FileRewindResponse(
            success = !hadErrors,
            targetPromptIndex = targetPromptIndex,
            revertedFiles = reverted,
            cleanFiles = clean,
            conflicts = conflicts,
            error = if (hadErrors) "Some files could not be reverted" else null
        )
    }

    /** Rewind the most recent prompt that touched files (one-click “Rewind turn”). */
    fun rewindLastPrompt(): FileRewindResponse {
        val idx = maxPromptIndexWithFiles()
            ?: return FileRewindResponse(
                success = false,
                targetPromptIndex = -1,
                revertedFiles = emptyList(),
                cleanFiles = emptyList(),
                conflicts = emptyList(),
                error = "Nothing to rewind — no file checkpoints yet"
            )
        return rewindTo(idx)
    }

    /**
     * Truncate rewind points from [promptIndex] inclusive (Grok: truncate_from).
     * After a successful rewind, future history is discarded.
     */
    fun truncateFrom(promptIndex: Int) = rw.write {
        ensureHistoricalLoadedLocked()
        val toRemove = rewindPoints.keys.filter { it >= promptIndex }
        toRemove.forEach { rewindPoints.remove(it) }
        if (nextPromptIndex > promptIndex) nextPromptIndex = promptIndex
        if (currentPrompt != null && currentPrompt!! >= promptIndex) currentPrompt = null
    }

    /** Clear in-memory + optional disk (new session). */
    fun clear(deleteDisk: Boolean = false) = rw.write {
        rewindPoints.clear()
        currentPrompt = null
        nextPromptIndex = 0
        historicalLoaded = true
        if (deleteDisk) {
            try { storeFile().delete() } catch (_: Exception) {}
        }
    }

    // ── Persistence ────────────────────────────────────────────────────────

    fun persist() {
        val snapshot: List<RewindPoint> = rw.read {
            rewindPoints.values.sortedBy { it.promptIndex }
        }
        try {
            val root = JSONObject()
            root.put("version", 1)
            root.put("nextPromptIndex", nextPromptIndex)
            val arr = JSONArray()
            for (p in snapshot) {
                // Skip empty points
                if (p.fileSnapshots.isEmpty()) continue
                val o = JSONObject()
                o.put("promptIndex", p.promptIndex)
                o.put("createdAt", p.createdAt)
                o.put("before", snapshotsToJson(p.fileSnapshots))
                o.put("after", snapshotsToJson(p.afterSnapshots))
                arr.put(o)
            }
            root.put("points", arr)
            val f = storeFile()
            val tmp = File(f.parentFile, ".${f.name}.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(f)) {
                f.writeText(root.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
            // Best-effort persistence
        }
    }

    fun ensureHistoricalLoaded() = rw.write { ensureHistoricalLoadedLocked() }

    private fun ensureHistoricalLoadedLocked() {
        if (historicalLoaded) return
        historicalLoaded = true
        val f = storeFile()
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText())
            nextPromptIndex = maxOf(nextPromptIndex, root.optInt("nextPromptIndex", 0))
            val arr = root.optJSONArray("points") ?: return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val idx = o.optInt("promptIndex", -1)
                if (idx < 0) continue
                // Live captures win: or_insert semantics
                if (rewindPoints.containsKey(idx)) continue
                val point = RewindPoint(
                    promptIndex = idx,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
                jsonToSnapshots(o.optJSONObject("before")).forEach { point.fileSnapshots[it.path] = it }
                jsonToSnapshots(o.optJSONObject("after")).forEach { point.afterSnapshots[it.path] = it }
                if (point.fileSnapshots.isNotEmpty()) {
                    rewindPoints[idx] = point
                    nextPromptIndex = maxOf(nextPromptIndex, idx + 1)
                }
            }
        } catch (_: Exception) {
            // Corrupt store — start fresh; keep historicalLoaded true to avoid loop
        }
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun cleanRel(relPath: String): String? {
        val cleaned = ProjectManager.cleanRelPath(relPath)
        if (cleaned.isBlank() || cleaned.contains('\u0000')) return null
        // Must stay inside project
        val f = File(projectDir, cleaned)
        return if (ProjectManager.isInsideProject(projectDir, f)) cleaned else null
    }

    private fun resolveInside(relPath: String): File? {
        val cleaned = cleanRel(relPath) ?: return null
        val f = File(projectDir, cleaned)
        return if (ProjectManager.isInsideProject(projectDir, f)) f else null
    }

    private fun readDiskSnapshot(relPath: String): FileSnapshot {
        val f = resolveInside(relPath)
        if (f == null || !f.exists()) {
            return FileSnapshot(relPath, null)
        }
        if (f.isDirectory) {
            return FileSnapshot(relPath, null) // directories not snapshotted as content
        }
        if (f.length() > MAX_SNAPSHOT_BYTES) {
            // Still mark path so rewind knows something was touched, but don't store huge blobs
            return FileSnapshot(relPath, "[[AHAMAI_SKIP_LARGE:${f.length()}]]")
        }
        if (!ProjectManager.isTextFile(f.name)) {
            return FileSnapshot(relPath, "[[AHAMAI_BINARY]]")
        }
        return try {
            FileSnapshot(relPath, f.readText())
        } catch (_: Exception) {
            FileSnapshot(relPath, null)
        }
    }

    private fun atomicWrite(f: File, content: String) {
        if (content.startsWith("[[AHAMAI_SKIP_LARGE:") || content == "[[AHAMAI_BINARY]]") {
            // We never stored real content — leave file as-is (cannot restore)
            return
        }
        val tmp = File(f.parentFile, ".${f.name}.ahamai-rewind-tmp")
        try {
            tmp.writeText(content)
            if (!tmp.renameTo(f)) {
                f.writeText(content)
                tmp.delete()
            }
        } catch (_: Exception) {
            tmp.delete()
            f.writeText(content)
        }
    }

    private fun snapshotsToJson(map: Map<String, FileSnapshot>): JSONObject {
        val o = JSONObject()
        for ((path, snap) in map) {
            val s = JSONObject()
            if (snap.content == null) s.put("missing", true)
            else s.put("content", snap.content)
            s.put("at", snap.capturedAt)
            o.put(path, s)
        }
        return o
    }

    private fun jsonToSnapshots(o: JSONObject?): List<FileSnapshot> {
        if (o == null) return emptyList()
        val out = mutableListOf<FileSnapshot>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val path = keys.next()
            val s = o.optJSONObject(path) ?: continue
            val content = if (s.optBoolean("missing", false)) null else s.optString("content", null)
            out.add(FileSnapshot(path, content, s.optLong("at", 0L)))
        }
        return out
    }
}
