package com.ahamai.app.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * FastSync — drop-in replacement for E2BClient.syncProjectToSandbox that actually scales.
 *
 * The old sync uploaded EVERY file in the project on EVERY cloud command. A 60-file project
 * meant 60 sequential HTTP POSTs before any cloud tool could even start — that is the single
 * biggest reason cloud tools felt slow while coding tools (which run on Wandbox with no sync)
 * felt fast.
 *
 * FastSync keeps a per-project manifest of (relativePath → mtime+size) and only re-uploads
 * files whose mtime or size has changed since the last successful sync. Uploads also run in
 * PARALLEL (8 concurrent) instead of sequentially. On a typical incremental sync this cuts
 * network round-trips from ~60 down to 0–3, making cloud tools feel as instant as local ones.
 *
 * Thread-safe. Multiple tools calling syncProjectAtOnce() concurrently share ONE sync pass.
 */
object FastSync {

    private const val TAG = "FastSync"
    private const val PARALLELISM = 8

    /** Max size of a single file we sync to the cloud sandbox. Raised from 2 MB so LARGER assets
     *  (bigger source bundles, models, media, sample data) actually reach /workspace instead of
     *  being silently skipped — which used to make the agent think the cloud was "missing" files.
     *  The incremental manifest means each big file is uploaded ONCE and then skipped, so this
     *  does not slow down later syncs. */
    private const val MAX_SYNC_BYTES = 25_000_000L

    /** Per-project sync state: projectDir -> Snapshot. */
    private val snapshots = ConcurrentHashMap<String, Snapshot>()
    private val syncMutex = Mutex()

    data class FileEntry(val relPath: String, val mtime: Long, val size: Long)
    data class Snapshot(
        val projectDir: String,
        val files: MutableMap<String, FileEntry> = mutableMapOf()
    ) {
        fun diff(filesNow: List<FileEntry>): List<String> {
            val out = mutableListOf<String>()
            val seen = HashSet<String>()
            for (f in filesNow) {
                seen.add(f.relPath)
                val prev = files[f.relPath]
                if (prev == null || prev.mtime != f.mtime || prev.size != f.size) {
                    out.add(f.relPath)
                }
            }
            // Files that vanished locally are removed remotely (best-effort).
            for ((k, _) in files) if (k !in seen) out.add(k) // we mark for delete in executeSync
            return out
        }
    }

    /** Same skip rules as E2BClient.shouldSkipForSync — keep them in sync. */
    private fun shouldSkip(relPath: String): Boolean {
        val lower = relPath.lowercase()
        if (lower.startsWith(".git/")) return true
        if (lower.startsWith("node_modules/")) return true
        if (lower.startsWith("build/")) return true
        if (lower.startsWith(".gradle/")) return true
        if (lower.startsWith("__pycache__/")) return true
        if (lower.startsWith(".idea/")) return true
        if (lower.endsWith(".apk")) return true
        if (lower.endsWith(".class")) return true
        if (lower.endsWith(".so")) return true
        if (lower.endsWith(".zip") || lower.endsWith(".tar.gz") || lower.endsWith(".7z")) return true
        // Keep media files up to the sync cap (below); only skip very large ones via scanProject.
        // Keep .jar files — gradle-wrapper.jar is essential for builds
        return false
    }

    private fun scanProject(rootDir: File): List<FileEntry> {
        if (!rootDir.exists() || !rootDir.isDirectory) return emptyList()
        val out = ArrayList<FileEntry>(64)
        rootDir.walkTopDown().forEach { f ->
            if (!f.isFile) return@forEach
            val rel = f.relativeTo(rootDir).path
            if (shouldSkip(rel)) return@forEach
            if (f.length() > MAX_SYNC_BYTES) return@forEach
            out.add(FileEntry(rel, f.lastModified(), f.length()))
        }
        return out
    }

    /**
     * Perform an incremental, parallel sync. Returns a short human-readable summary that the
     * caller can log/show. Safe to call on every cloud command — usually does 0 work after the
     * first sync.
     */
    suspend fun syncProjectAtOnce(
        projectDir: String,
        apiKey: String,
        template: String,
        rootDir: File
    ): String = syncMutex.withLock {
        try {
            val now = scanProject(rootDir)
            val snap = snapshots.getOrPut(projectDir) { Snapshot(projectDir) }
            val changed = snap.diff(now)

            // First sync ever: ensure /workspace exists.
            if (snap.files.isEmpty()) {
                runCatching {
                    E2BClient.exec(projectDir, apiKey, template, "mkdir -p /workspace", "/workspace", 10)
                }
            }

            if (changed.isEmpty()) {
                return@withLock "sync: 0 files (up to date)"
            }

            // Update snapshot optimistically — if any upload fails we'll reset that entry's mtime
            // by removing it, so the next sync retries it.
            val deleted = mutableListOf<String>()
            val toUpload = mutableListOf<Pair<FileEntry, File>>()
            for (rel in changed) {
                val local = File(rootDir, rel)
                if (!local.exists() || !local.isFile) {
                    deleted.add(rel)
                    snap.files.remove(rel)
                    continue
                }
                val entry = now.first { it.relPath == rel }
                toUpload.add(entry to local)
                snap.files[rel] = entry
            }

            // Delete vanished files in the sandbox (best-effort, batched).
            if (deleted.isNotEmpty()) {
                val rmCmd = deleted.joinToString(" ") { "'/workspace/${it.replace("'", "'\\''")}'" }
                runCatching {
                    E2BClient.exec(projectDir, apiKey, template, "rm -f $rmCmd 2>/dev/null", "/workspace", 15)
                }
            }

            // Parallel upload — at most PARALLELISM concurrent POSTs.
            val ok = java.util.concurrent.atomic.AtomicInteger(0)
            val fail = java.util.concurrent.atomic.AtomicInteger(0)
            coroutineScope {
                toUpload.chunked(PARALLELISM).forEach { batch ->
                    batch.map { (entry, local) ->
                        async(Dispatchers.IO) {
                            val remote = "/workspace/${entry.relPath.replace("'", "'\\''")}"
                            val sent = try {
                                E2BClient.uploadFile(projectDir, apiKey, template, local, remote)
                            } catch (_: CancellationException) {
                                throw CancellationException()
                            } catch (e: Exception) {
                                Log.w(TAG, "upload failed: ${entry.relPath}: ${e.message}")
                                false
                            }
                            if (sent) ok.incrementAndGet()
                            else {
                                fail.incrementAndGet()
                                // Roll back the snapshot entry so we retry next time.
                                snapshots[projectDir]?.files?.remove(entry.relPath)
                            }
                        }
                    }.awaitAll()
                }
            }

            "sync: ${ok.get()} uploaded, ${fail.get()} failed, ${deleted.size} removed"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "sync error: ${e.message?.take(120)}"
        }
    }

    /** Forget the cached snapshot for a project (used after a rebuild / project switch). */
    fun invalidate(projectDir: String) {
        snapshots.remove(projectDir)
        remoteSnapshots.remove(projectDir)
    }

    /** True if we've ever successfully synced this project (so callers can show "first sync…"). */
    fun hasSnapshot(projectDir: String): Boolean = snapshots.containsKey(projectDir)

    // ── Reverse sync (cloud → phone) ──────────────────────────────────────────

    /**
     * After CLOUD_SHELL / cloud jobs, pull NEW or CHANGED files from `/workspace` back into
     * the local project. Fast when nothing changed: **one** remote listing only; otherwise
     * list + parallel download of only the diffs (max [MAX_PULL_FILES], [MAX_SYNC_BYTES] each).
     */
    suspend fun pullBackFromSandbox(
        projectDir: String,
        apiKey: String,
        template: String,
        rootDir: File = File(projectDir)
    ): String = withContext(Dispatchers.IO) {
        try {
            // Portable listing script (base64 — no shell heredoc).
            val listPy = buildString {
                appendLine("import os")
                appendLine("SKIP_DIR={'.git','node_modules','__pycache__','.browser','.jobs','.cache','ms-playwright','.npm','.gradle','build','.idea','.local'}")
                appendLine("root='/workspace'")
                appendLine("max_sz=$MAX_SYNC_BYTES")
                appendLine("n=0")
                appendLine("for dirpath, dirnames, filenames in os.walk(root):")
                appendLine("  dirnames[:] = [d for d in dirnames if d not in SKIP_DIR and not d.startswith('.')]")
                appendLine("  for name in filenames:")
                appendLine("    if name.startswith('.') and name not in ('.env','.gitignore','.editorconfig'): continue")
                appendLine("    p=os.path.join(dirpath,name)")
                appendLine("    try:")
                appendLine("      st=os.stat(p)")
                appendLine("      if st.st_size<=0 or st.st_size>max_sz: continue")
                appendLine("      rel=os.path.relpath(p, root).replace(chr(92),'/')")
                appendLine("      print('%d\\t%s' % (st.st_size, rel))")
                appendLine("      n+=1")
                appendLine("      if n>=400: raise SystemExit")
                appendLine("    except Exception:")
                appendLine("      pass")
            }
            val b64 = android.util.Base64.encodeToString(
                listPy.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP
            )
            val listOut = E2BClient.exec(
                projectDir, apiKey, template,
                "echo '$b64' | base64 -d > /tmp/_aham_ls.py && python3 /tmp/_aham_ls.py",
                "/workspace", 45
            ).stdout

            val remote = LinkedHashMap<String, Long>()
            for (line in listOut.lines()) {
                val t = line.trim()
                if (t.isEmpty()) continue
                val tab = t.indexOf('\t')
                if (tab <= 0) continue
                val size = t.substring(0, tab).toLongOrNull() ?: continue
                val rel = t.substring(tab + 1).trim().removePrefix("./")
                if (rel.isBlank() || rel.contains("..")) continue
                if (shouldSkipReverse(rel)) continue
                remote[rel] = size
            }
            if (remote.isEmpty()) return@withContext "pull: 0 files (cloud empty or listing failed)"

            val prevRemote = remoteSnapshots.getOrPut(projectDir) { ConcurrentHashMap() }
            val toPull = ArrayList<Pair<String, Long>>(32)
            for ((rel, size) in remote) {
                if (size > MAX_SYNC_BYTES) continue
                val local = File(rootDir, rel)
                val localSize = if (local.isFile) local.length() else -1L
                // Pull if missing locally or size differs from cloud (created/changed in shell).
                if (localSize != size) toPull.add(rel to size)
                if (toPull.size >= MAX_PULL_FILES) break
            }

            // Remember remote sizes so repeated pullBack after no-op shells is cheap to reason about.
            prevRemote.clear()
            prevRemote.putAll(remote)

            if (toPull.isEmpty()) {
                return@withContext "pull: 0 files (up to date)"
            }

            val ok = java.util.concurrent.atomic.AtomicInteger(0)
            val fail = java.util.concurrent.atomic.AtomicInteger(0)
            val pulledNames = java.util.concurrent.ConcurrentLinkedQueue<String>()
            coroutineScope {
                toPull.chunked(PARALLELISM).forEach { batch ->
                    batch.map { (rel, _) ->
                        async(Dispatchers.IO) {
                            val local = File(rootDir, rel)
                            local.parentFile?.mkdirs()
                            val remotePath = "/workspace/$rel"
                            val got = try {
                                E2BClient.downloadFile(apiKey, remotePath, local)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG, "pull failed: $rel: ${e.message}")
                                false
                            }
                            if (got && local.isFile && local.length() > 0) {
                                ok.incrementAndGet()
                                pulledNames.add(rel)
                                snapshots.getOrPut(projectDir) { Snapshot(projectDir) }
                                    .files[rel] = FileEntry(rel, local.lastModified(), local.length())
                            } else {
                                fail.incrementAndGet()
                            }
                        }
                    }.awaitAll()
                }
            }

            val names = pulledNames.take(12).joinToString(", ")
            val more = if (pulledNames.size > 12) "…" else ""
            "pull: ${ok.get()} downloaded, ${fail.get()} failed" +
                if (names.isNotBlank()) " ($names$more)" else ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "pull error: ${e.message?.take(120)}"
        }
    }

    /**
     * Reverse-pull skip rules. Forward sync still skips apk/zip; reverse allows them so cloud
     * build outputs can land in the project (under [MAX_SYNC_BYTES]).
     */
    private fun shouldSkipReverse(relPath: String): Boolean {
        val lower = relPath.lowercase()
        if (lower.startsWith(".browser/") || lower.startsWith(".jobs/")) return true
        if (lower.contains("/ms-playwright/") || lower.contains("/.cache/")) return true
        if (lower.endsWith(".pyc")) return true
        if (lower.startsWith(".git/") || lower.startsWith("node_modules/") ||
            lower.startsWith("build/") || lower.startsWith(".gradle/") ||
            lower.startsWith("__pycache__/") || lower.startsWith(".idea/")
        ) return true
        if (lower.endsWith(".class") || lower.endsWith(".so")) return true
        // apk/zip allowed on reverse only
        return false
    }

    private val remoteSnapshots = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()

    private const val MAX_PULL_FILES = 40
}
