package com.ahamai.app.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ProjectFile(
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val depth: Int
)

object ProjectManager {

    // Directories to skip ONLY when listing files in the UI (not when zipping/downloading)
    private val SKIP_DIRS_LIST = setOf(
        ".git", "node_modules", "build", "dist", ".gradle", ".idea",
        "__pycache__", ".dart_tool", "venv", ".venv", "target", ".next",
        "vendor", "Pods", ".vscode", "coverage", ".kotlin", ".cache",
        ".cxx", "intermediates", ".ahamai"
    )
    // Dot-dirs that ARE useful for agents (CI, config) — included in tree despite leading '.'
    private val USEFUL_DOT_DIRS = setOf(
        ".github", ".gitlab", ".circleci", ".vscode", ".cursor", ".idea"
    )
    // Directories to skip when creating a zip for download — caches + heavy build outputs
    private val SKIP_DIRS_ZIP = setOf(
        ".git", "node_modules", "build", "dist", ".gradle", ".idea",
        "__pycache__", ".dart_tool", "venv", ".venv", "target", ".next",
        "Pods", ".vscode", "coverage", ".kotlin", "vendor",
        ".cache", "tmp", ".tmp", "out", "output", ".build",
        "captures", ".navigation", "cxx", ".externalNativeBuild",
        ".cxx", "reports", "lint", "intermediates", "generated",
        "data-binding-layout-output", "inspection",
        ".transforms", ".profiler", "merged_manifests", "shaders"
    )
    // Shared skip set for search/grep/symbol walks (one source of truth)
    private val SKIP_DIRS_SEARCH = SKIP_DIRS_LIST + setOf(
        "out", "output", ".build", "Pods", "vendor", "generated", "intermediates"
    )
    private val BINARY_EXTENSIONS = setOf(
        // NOTE: svg is TEXT (editable) — kept out of binary set on purpose
        "png", "jpg", "jpeg", "gif", "webp", "ico", "bmp",
        "mp3", "mp4", "wav", "avi", "mov", "zip", "tar", "gz", "rar", "7z",
        "jar", "war", "apk", "aab", "so", "dll", "exe", "bin", "class",
        "pdf", "ttf", "otf", "woff", "woff2", "eot", "dex", "keystore", "jks"
    )

    /** Soft cap for full READ_FILE body (lines). Beyond this → numbered head + READ_LINES nudge. */
    private const val READ_SOFT_LINE_CAP = 400
    private const val READ_HARD_BYTE_CAP = 600_000L
    // Big-change mode raises these via BigChangePolicy.caps() at call time.
    private const val SEARCH_MAX_FILE_BYTES = 2_000_000L

    /**
     * Short-lived cache of listTextFiles walks. GREP/SEARCH_CODE used to re-walk the whole tree
     * on every call in the same turn — that is the #1 reason search felt slow vs. instant agents.
     * Invalidated on any write/edit/delete and after a short TTL.
     */
    private data class TextListCache(
        val projectDir: String,
        val scope: String,
        val glob: String,
        val files: List<String>,
        val atMs: Long
    )
    @Volatile private var textListCache: TextListCache? = null
    private const val TEXT_LIST_CACHE_TTL_MS = 4_000L

    /**
     * In-memory file text cache (path → content + mtime). Same turn often re-reads a file
     * 3–5× (pre-diff, edit, post-diff, multi-edit pairs). Cap size so huge files stay uncached.
     */
    private data class FileTextCache(
        val mtime: Long,
        val length: Long,
        val raw: String
    )
    private val fileTextCache = ConcurrentHashMap<String, FileTextCache>(64)
    private const val FILE_TEXT_CACHE_MAX_ENTRIES = 80
    private const val FILE_TEXT_CACHE_MAX_BYTES = 1_500_000L

    /**
     * Path index for fast resolvePath (case-insensitive / basename / fuzzy).
     * Avoids a full listFiles() walk on every imperfect path the model invents.
     */
    private data class PathIndex(
        val projectDir: String,
        val builtAtMs: Long,
        val byLowerPath: Map<String, String>,
        val byBaseName: Map<String, List<String>>,
        val byNormBase: Map<String, List<String>>
    )
    @Volatile private var pathIndex: PathIndex? = null
    private const val PATH_INDEX_TTL_MS = 6_000L

    /** Call after any filesystem mutation so the next search sees fresh paths. */
    fun invalidateTextListCache() {
        textListCache = null
        pathIndex = null
    }

    /** Drop one file or the whole text cache (e.g. after external sync). */
    fun invalidateFileTextCache(file: File? = null) {
        if (file == null) {
            fileTextCache.clear()
            return
        }
        fileTextCache.remove(cacheKey(file))
    }

    private fun getPathIndex(projectDir: String): PathIndex {
        val now = System.currentTimeMillis()
        pathIndex?.let {
            if (it.projectDir == projectDir && now - it.builtAtMs < PATH_INDEX_TTL_MS) return it
        }
        val all = listFiles(projectDir)
        val byLower = HashMap<String, String>(all.size * 2)
        val byBase = HashMap<String, MutableList<String>>()
        val byNorm = HashMap<String, MutableList<String>>()
        for (pf in all) {
            if (pf.isDirectory) continue
            val rel = pf.relativePath
            byLower[rel.lowercase()] = rel
            val base = rel.substringAfterLast('/')
            val lowerBase = base.lowercase()
            byBase.getOrPut(lowerBase) { mutableListOf() }.add(rel)
            val norm = lowerBase.replace(Regex("[\\s()\\[\\]-]+"), "")
            if (norm.length >= 4) byNorm.getOrPut(norm) { mutableListOf() }.add(rel)
        }
        return PathIndex(projectDir, now, byLower, byBase, byNorm).also { pathIndex = it }
    }

    private fun cacheKey(f: File): String =
        try { f.canonicalPath } catch (_: Exception) { f.absolutePath }

    /** Read UTF-8 text with mtime-validated cache. Large files bypass cache. */
    private fun readTextCached(f: File): String {
        val len = f.length()
        if (len > FILE_TEXT_CACHE_MAX_BYTES) return f.readText()
        val key = cacheKey(f)
        val mtime = f.lastModified()
        val hit = fileTextCache[key]
        if (hit != null && hit.mtime == mtime && hit.length == len) return hit.raw
        val raw = f.readText()
        putFileTextCache(key, mtime, len, raw)
        return raw
    }

    private fun putFileTextCache(key: String, mtime: Long, length: Long, raw: String) {
        if (length > FILE_TEXT_CACHE_MAX_BYTES) return
        if (fileTextCache.size >= FILE_TEXT_CACHE_MAX_ENTRIES) {
            // Cheap eviction: drop ~25% of keys
            val drop = fileTextCache.keys.take((FILE_TEXT_CACHE_MAX_ENTRIES / 4).coerceAtLeast(8))
            drop.forEach { fileTextCache.remove(it) }
        }
        fileTextCache[key] = FileTextCache(mtime, length, raw)
    }

    private fun putFileTextCache(f: File, raw: String) {
        putFileTextCache(cacheKey(f), f.lastModified(), f.length().coerceAtLeast(raw.length.toLong()), raw)
    }

    /**
     * Projects live under a folder scoped to the SIGNED-IN account's UID. This method returns
     * the current user's preferred write-target directory.
     */
    private fun projectsRoot(context: Context): File {
        val uid = AuthManager.uid()?.takeIf { it.isNotBlank() } ?: "guest"
        val dir = File(context.filesDir, "projects_$uid")
        if (!dir.exists()) {
            // One-time migration for installs updating from before this fix: the first account
            // to open the app after updating inherits the old shared folder (nothing is lost);
            // every account after that starts with its own clean, isolated folder.
            val legacy = File(context.filesDir, "projects")
            if (legacy.isDirectory && legacy.listFiles()?.isNotEmpty() == true) {
                legacy.renameTo(dir)
            }
        }
        dir.mkdirs()
        return dir
    }

    /**
     * Returns ALL project root directories across every UID (including guest and the legacy
     * shared folder). Used by [listProjects] so workspaces created under any auth state are
     * always visible — switching accounts or loading before auth is ready no longer hides
     * previously-saved projects.
     */
    private fun allProjectRoots(context: Context): List<File> {
        val roots = mutableListOf<File>()
        val filesDir = context.filesDir
        filesDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("projects_") }?.forEach { roots.add(it) }
        // Legacy shared folder from before the UID isolation fix
        val legacy = File(filesDir, "projects")
        if (legacy.isDirectory && legacy.exists() && legacy !in roots) roots.add(legacy)
        return roots
    }

    /** Lightweight info about a saved project/session, for the workspace sidebar switcher. */
    data class ProjectInfo(
        val path: String,
        val name: String,
        val fileCount: Int,
        val lastModified: Long
    )

    /** Write a custom display title for a session (stored in a hidden file). */
    fun setSessionTitle(projectDir: String, title: String) {
        try {
            val dir = File(projectDir)
            if (dir.isDirectory) {
                val titleFile = File(dir, ".ahamai-title")
                titleFile.writeText(title.trim().take(60))
            }
        } catch (_: Exception) {}
    }

    /** Read the custom display title for a session, or null if none set. */
    fun getSessionTitle(projectDir: String): String? {
        return try {
            val titleFile = File(projectDir, ".ahamai-title")
            if (titleFile.exists()) titleFile.readText().trim().take(60).ifBlank { null } else null
        } catch (_: Exception) { null }
    }

    /** Lists all saved projects (sessions), most-recently-modified first. */
    fun listProjects(context: Context): List<ProjectInfo> {
        val roots = allProjectRoots(context)
        if (roots.isEmpty()) return emptyList()

        // NOTE: Workspaces wiped by an uninstall/reinstall (or a sign-in on a fresh device) are
        // restored from the cloud by AuthManager.restoreWorkspaces on sign-in — which pulls each
        // workspace's chunked backup and calls restoreWorkspaceFromPayload to recreate the project
        // WITH its real content and agent transcript. This replaces the old
        // Downloads/AhamAI/workspaces/*.meta.json copy, which cluttered the user's Downloads and
        // could only ever restore an EMPTY placeholder.

        // Aggregate directories from ALL uid roots (dedup by path)
        val seen = mutableSetOf<String>()
        return roots.flatMap { r -> r.listFiles()?.filter { it.isDirectory } ?: emptyList() }
            .filter { seen.add(it.absolutePath) }
            .map { d ->
            val customTitle = try {
                val titleFile = File(d, ".ahamai-title")
                if (titleFile.exists()) titleFile.readText().trim().take(60).ifBlank { null } else null
            } catch (_: Exception) { null }
            val name = customTitle ?: run {
                val raw = d.name.replace(Regex("^scratch_\\d+_"), "").replace('_', ' ').trim()
                raw.ifBlank { "Project" }.replaceFirstChar { it.uppercase() }
            }
            // Count files, excluding internal dot-files like .ahamai-title so the count
            // reflects REAL user-created content — not AhamAI internal metadata.
            val count = try {
                d.walkTopDown().count { it.isFile && !it.name.startsWith(".") }
            } catch (_: Exception) { 0 }
            ProjectInfo(d.absolutePath, name, count, d.lastModified())
        }.sortedByDescending { it.lastModified }
    }

    /** Creates a new empty project directory and returns its path. */
    fun createEmptyProject(context: Context, name: String): String {
        val safe = name.trim().ifBlank { "project" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = File(projectsRoot(context), "scratch_${System.currentTimeMillis()}_$safe").apply { mkdirs() }
        return dir.absolutePath
    }

    /**
     * Creates a new project pre-filled with the bundled, build-verified Android app template
     * (assets/app_template.zip). Gives the agent a working, GitHub-Actions-buildable starting point.
     * Falls back to an empty project if the template is missing.
     */
    fun createFromTemplate(context: Context, name: String): String {
        val dir = createEmptyProject(context, name)
        applyAndroidTemplate(context, dir)
        return dir
    }

    /** Extracts the bundled Android template into an EXISTING project dir (for SCAFFOLD_ANDROID). */
    fun applyAndroidTemplate(context: Context, projectDir: String): String {
        return try {
            var count = 0
            context.assets.open("app_template.zip").use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (entryName.isNotBlank() && !entryName.contains("..") && !entryName.startsWith(".kotlin")) {
                            val outFile = File(projectDir, entryName)
                            if (outFile.canonicalPath.startsWith(File(projectDir).canonicalPath)) {
                                if (entry.isDirectory) outFile.mkdirs()
                                else { outFile.parentFile?.mkdirs(); FileOutputStream(outFile).use { zis.copyTo(it) }; count++ }
                            }
                        }
                        zis.closeEntry(); entry = zis.nextEntry
                    }
                }
            }
            "OK: Added Android app template ($count files: gradle wrapper, app module, MainActivity). It builds to an APK in the cloud. Now customize it."
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    /** Extracts a .zip located inside the project into [destSubdir]. */
    fun unzipInProject(projectDir: String, zipRelPath: String, destSubdir: String): String {
        return try {
            val zipFile = resolvePath(projectDir, zipRelPath) ?: return "ERROR: zip not found: $zipRelPath"
            val cleanDest = destSubdir.trim().removePrefix("/").removePrefix("./")
            val targetRoot = if (cleanDest.isBlank()) File(projectDir) else File(projectDir, cleanDest).apply { mkdirs() }
            var count = 0
            ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val n = entry.name
                    if (n.isNotBlank() && !n.contains("..") && !n.startsWith("__MACOSX")) {
                        val outFile = File(targetRoot, n)
                        if (outFile.canonicalPath.startsWith(targetRoot.canonicalPath)) {
                            if (entry.isDirectory) outFile.mkdirs()
                            else { outFile.parentFile?.mkdirs(); FileOutputStream(outFile).use { zis.copyTo(it) }; count++ }
                        }
                    }
                    zis.closeEntry(); entry = zis.nextEntry
                }
            }
            "OK: Unzipped ${zipRelPath.trim()} into ${cleanDest.ifBlank { "project root" }} ($count files)"
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    /** Compresses a file/folder inside the project into a .zip inside the project. */
    fun zipInProject(projectDir: String, srcRelPath: String, destZipRelPath: String): String {
        return try {
            val src = resolvePath(projectDir, srcRelPath) ?: File(projectDir, srcRelPath.trim().removePrefix("/"))
            if (!src.exists()) return "ERROR: source not found: $srcRelPath"
            val destPath = destZipRelPath.trim().removePrefix("/").ifBlank { "${src.name}.zip" }
            val outFile = File(projectDir, destPath)
            outFile.parentFile?.mkdirs()
            var count = 0
            ZipOutputStream(FileOutputStream(outFile).buffered()).use { zos ->
                val baseParent = if (src.isDirectory) src else src.parentFile!!
                fun add(f: File) {
                    if (f.isDirectory) {
                        if (f.name in SKIP_DIRS_ZIP) return
                        f.listFiles()?.forEach { add(it) }
                    } else {
                        // Skip the output zip itself to prevent recursive inclusion
                        if (f.absolutePath == outFile.absolutePath) return
                        // Skip large binaries that bloat the zip
                        val ext = f.extension.lowercase()
                        if (ext == "apk" || ext == "aab" || ext == "so") return
                        if (f.name.startsWith(".ahamai")) return
                        if (f.length() > 10_000_000) return  // Skip files > 10MB
                        val rel = f.absolutePath.removePrefix(baseParent.absolutePath).removePrefix("/")
                        zos.putNextEntry(ZipEntry(rel)); f.inputStream().use { it.copyTo(zos) }; zos.closeEntry(); count++
                    }
                }
                add(src)
            }
            "OK: Zipped ${srcRelPath.trim()} -> $destPath ($count files)"
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    /**
     * Extracts a ZIP file from the given Uri into a new project directory.
     * Returns the project directory path, or null on failure.
     */
    fun extractZip(context: Context, uri: Uri): String? {
        return try {
            val projectId = "proj_${System.currentTimeMillis()}"
            val projectDir = File(projectsRoot(context), projectId).apply { mkdirs() }

            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        // Skip junk + protect against Zip Slip
                        if (!entryName.contains("..")) {
                            val outFile = File(projectDir, entryName)
                            if (outFile.canonicalPath.startsWith(projectDir.canonicalPath)) {
                                if (entry.isDirectory) {
                                    outFile.mkdirs()
                                } else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return null

            // If the zip extracted to a single root folder, use that as the project root
            val children = projectDir.listFiles()?.filter { !it.name.startsWith("__MACOSX") }
            if (children?.size == 1 && children[0].isDirectory) {
                children[0].absolutePath
            } else {
                projectDir.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lists all files (recursively) in the project as a flat list with depth info.
     */
    fun listFiles(projectDir: String): List<ProjectFile> {
        val root = File(projectDir)
        val result = mutableListOf<ProjectFile>()

        fun walk(dir: File, depth: Int) {
            val entries = dir.listFiles()?.sortedWith(
                compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() }
            ) ?: return
            for (f in entries) {
                if (f.isDirectory) {
                    // Skip junk; keep useful dot-dirs (.github etc.) and normal folders
                    if (f.name in SKIP_DIRS_LIST) continue
                    if (f.name.startsWith(".") && f.name !in USEFUL_DOT_DIRS) continue
                    result.add(ProjectFile(relativePathOf(root, f), true, 0, depth))
                    walk(f, depth + 1)
                } else {
                    result.add(ProjectFile(relativePathOf(root, f), false, f.length(), depth))
                }
            }
        }
        walk(root, 0)
        return result
    }

    /** Whether a relative path segment should be skipped during search walks. */
    private fun shouldSkipSearchDir(name: String): Boolean =
        name in SKIP_DIRS_SEARCH || (name.startsWith(".") && name !in USEFUL_DOT_DIRS)

    private fun relativePathOf(root: File, file: File): String {
        return file.absolutePath.removePrefix(root.absolutePath).removePrefix("/")
    }

    /**
     * Builds a compact text representation of the file tree for the AI.
     * **Always shows project-relative paths** (not basenames only) so the model can
     * READ/EDIT without 20+ FIND_FILES turns guessing where `ChatScreen.kt` lives.
     * Does NOT open every file to count lines — size is metadata only.
     */
    fun buildTreeString(projectDir: String): String {
        val files = listFiles(projectDir)
        if (files.isEmpty()) return "(empty project)"
        val sb = StringBuilder()
        val maxEntries = 1200
        var shown = 0
        // Prefer source-heavy folders first so key paths appear even on huge trees.
        val ranked = files.sortedWith(
            compareBy<ProjectFile> { f ->
                val p = f.relativePath.lowercase()
                when {
                    p.contains("/src/") || p.startsWith("src/") || p.startsWith("app/") -> 0
                    p.contains("/java/") || p.contains("/kotlin/") || p.contains("/main/") -> 1
                    p.endsWith(".kt") || p.endsWith(".java") || p.endsWith(".ts") ||
                        p.endsWith(".tsx") || p.endsWith(".py") -> 1
                    p.contains("build") || p.contains("node_modules") -> 9
                    else -> 5
                }
            }.thenBy { it.relativePath.lowercase() }
        )
        for (f in ranked) {
            if (shown >= maxEntries) {
                sb.append(
                    "... (${files.size - shown} more entries omitted — large project. " +
                        "Use FIND_FILES `**/*Name*` or GREP with a folder scope; full relative paths above are exact.)\n"
                )
                break
            }
            if (f.isDirectory) {
                sb.append(f.relativePath).append("/\n")
            } else {
                val info = when {
                    !isTextFile(f.relativePath) -> "  # binary ${formatBytes(f.sizeBytes)}"
                    f.sizeBytes < 1024 -> "  # ${f.sizeBytes}B"
                    f.sizeBytes < 1024 * 1024 -> "  # ${f.sizeBytes / 1024}KB"
                    else -> "  # ${"%.1f".format(f.sizeBytes / (1024.0 * 1024.0))}MB"
                }
                // Full relative path — the agent must use THIS string for READ/EDIT.
                sb.append(f.relativePath).append(info).append('\n')
            }
            shown++
        }
        return sb.toString().trim()
    }

    private fun formatBytes(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> "${n / 1024} KB"
        else -> "${"%.1f".format(n / (1024.0 * 1024.0))} MB"
    }

    fun isTextFile(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext.isEmpty() || ext !in BINARY_EXTENSIONS
    }

    /**
     * Manifest filenames that mark the ROOT of a buildable/runnable project, ranked by how
     * strongly they indicate "run build/install commands here".
     */
    private val BUILD_MANIFESTS = listOf(
        "package.json", "pnpm-workspace.yaml", "settings.gradle.kts", "settings.gradle",
        "build.gradle.kts", "build.gradle", "pom.xml", "Cargo.toml", "go.mod",
        "pyproject.toml", "requirements.txt", "composer.json", "Gemfile", "pubspec.yaml",
        "CMakeLists.txt", "Makefile", "gradlew", "index.html"
    )

    /**
     * Detects WHERE build/install/run commands should execute for the current project.
     *
     * The whole project dir is synced to /workspace, but users often upload source whose real
     * root (the folder holding package.json / build.gradle / etc.) is a SUBDIRECTORY — e.g. an
     * unzipped repo lands at /workspace/my-app/. Previously the agent would run `npm install` /
     * `gradle build` in /workspace (no manifest there), fail, and burn 10+ steps hunting for the
     * right directory. This returns the project-relative folder of the SHALLOWEST manifest so we
     * can tell the agent exactly where to cd. Returns "" when the manifest is already at the top
     * level (or none is found).
     */
    fun detectBuildRoot(projectDir: String): String {
        val files = try { listFiles(projectDir) } catch (_: Exception) { return "" }
        var best: String? = null
        var bestDepth = Int.MAX_VALUE
        var bestRank = Int.MAX_VALUE
        for (f in files) {
            if (f.isDirectory) continue
            val name = f.relativePath.substringAfterLast('/')
            val rank = BUILD_MANIFESTS.indexOf(name)
            if (rank < 0) continue
            val dir = if (f.relativePath.contains('/')) f.relativePath.substringBeforeLast('/') else ""
            val depth = if (dir.isEmpty()) 0 else dir.count { it == '/' } + 1
            // Prefer shallower manifests; break ties by manifest importance (rank order above).
            if (depth < bestDepth || (depth == bestDepth && rank < bestRank)) {
                best = dir; bestDepth = depth; bestRank = rank
            }
        }
        return best ?: ""
    }

    /** The manifest filename found at [detectBuildRoot], for a clearer hint. "" if none. */
    fun detectBuildManifest(projectDir: String): String {
        val root = detectBuildRoot(projectDir)
        val files = try { listFiles(projectDir) } catch (_: Exception) { return "" }
        for (m in BUILD_MANIFESTS) {
            val rel = if (root.isEmpty()) m else "$root/$m"
            if (files.any { !it.isDirectory && it.relativePath == rel }) return m
        }
        return ""
    }

    /**
     * Public resolver so other engines (e.g. PdfEngine) can locate a file from a fuzzy
     * relative path the model gave (leading slash, project-name prefix, or just a basename).
     * Returns the existing File or null.
     */
    fun resolveFile(projectDir: String, relPath: String): File? =
        resolvePath(projectDir, relPath, ResolveMode.READ)

    /** How aggressively to resolve imperfect paths the model gives. */
    enum class ResolveMode {
        /** Reads: allow case-insensitive + basename/fuzzy fallbacks (recover from typos). */
        READ,
        /** Writes/edits/deletes: exact or case-insensitive path only — never basename fuzzy (wrong-file risk). */
        WRITE
    }

    /**
     * Returns true iff [file] is strictly inside [projectDir] (or is the project root itself).
     * Blocks path traversal (`../`, absolute escapes, symlink escape).
     */
    fun isInsideProject(projectDir: String, file: File): Boolean {
        return try {
            val root = File(projectDir).canonicalFile
            val target = file.canonicalFile
            val rootPath = root.path
            val targetPath = target.path
            targetPath == rootPath || targetPath.startsWith(rootPath + File.separator)
        } catch (_: Exception) {
            false
        }
    }

    /** Clean model-provided relative path (no leading /, no quotes). Does NOT resolve existence. */
    fun cleanRelPath(relPath: String): String =
        relPath.trim().trim('"', '`', '\'')
            .removePrefix("/").removePrefix("./")
            .replace('\\', '/')
            .replace(Regex("/+"), "/")

    /**
     * Resolve a path for CREATE/write destinations. Must stay under project root.
     * Does not require the file to exist; creates parent dirs are caller's job.
     */
    fun resolveForWrite(projectDir: String, relPath: String): File? {
        val cleaned = cleanRelPath(relPath)
        if (cleaned.isBlank() || cleaned == "." || cleaned.contains('\u0000')) return null
        // Reject absolute and drive paths the model may invent
        if (cleaned.startsWith("/") || (cleaned.length >= 2 && cleaned[1] == ':')) return null
        val f = File(projectDir, cleaned)
        return if (isInsideProject(projectDir, f)) f else null
    }

    /**
     * Resolves a (possibly imperfect) path the AI gave to an actual file.
     * [ResolveMode.WRITE] never uses basename/fuzzy fallbacks (prevents editing the wrong file).
     * Always enforces project sandbox via canonical path check.
     */
    private fun resolvePath(projectDir: String, relPath: String, mode: ResolveMode = ResolveMode.READ): File? {
        val cleaned = cleanRelPath(relPath)
        if (cleaned.isBlank() || cleaned.contains('\u0000')) return null

        fun accept(f: File): File? =
            if (f.exists() && isInsideProject(projectDir, f)) f else null

        // 1. Direct match
        accept(File(projectDir, cleaned))?.let { return it }

        // 2. Strip first path segment (model may add project-name prefix)
        val withoutFirst = if (cleaned.contains('/')) cleaned.substringAfter('/') else ""
        if (withoutFirst.isNotBlank()) {
            accept(File(projectDir, withoutFirst))?.let { return it }
        }

        // 3–4. Case-insensitive / basename / fuzzy via cached path index (no full re-walk each miss)
        val index = getPathIndex(projectDir)
        val lowerCleaned = cleaned.lowercase()
        index.byLowerPath[lowerCleaned]?.let { rel ->
            accept(File(projectDir, rel))?.let { return it }
        }
        if (withoutFirst.isNotBlank()) {
            index.byLowerPath[withoutFirst.lowercase()]?.let { rel ->
                accept(File(projectDir, rel))?.let { return it }
            }
        }

        // Basename / fuzzy / path-suffix — READ only (writing to wrong file is worse than failing)
        if (mode == ResolveMode.READ) {
            val baseName = cleaned.substringAfterLast('/')
            if (baseName.isNotBlank()) {
                val lowerBase = baseName.lowercase()
                // Prefer exact basename (case-sensitive list first entry), then CI
                index.byBaseName[lowerBase]?.let { candidates ->
                    // If model gave "screens/ChatScreen.kt", prefer candidate ending with that suffix
                    val suffix = cleaned.lowercase()
                    val exact = candidates.firstOrNull { it.substringAfterLast('/') == baseName &&
                        it.lowercase().endsWith(suffix) }
                        ?: candidates.firstOrNull { it.substringAfterLast('/') == baseName }
                        ?: candidates.firstOrNull { it.lowercase().endsWith(suffix) }
                        ?: candidates.firstOrNull()
                    if (exact != null) accept(File(projectDir, exact))?.let { return it }
                }
                val normalized = lowerBase.replace(Regex("[\\s()\\[\\]-]+"), "")
                if (normalized.length >= 4) {
                    index.byNormBase[normalized]?.firstOrNull()?.let { rel ->
                        accept(File(projectDir, rel))?.let { return it }
                    }
                }
                if (baseName.length >= 4) {
                    // Contains match on basename — prefer unique hits
                    val hits = index.byBaseName.keys.filter { k ->
                        k.contains(lowerBase) || lowerBase.contains(k)
                    }.flatMap { index.byBaseName[it].orEmpty() }
                    if (hits.size == 1) {
                        accept(File(projectDir, hits[0]))?.let { return it }
                    } else if (hits.isNotEmpty()) {
                        // Prefer path ending with cleaned suffix (screens/Foo.kt)
                        val suffix = cleaned.lowercase()
                        hits.firstOrNull { it.lowercase().endsWith(suffix) }
                            ?.let { accept(File(projectDir, it))?.let { f -> return f } }
                        hits.firstOrNull { it.lowercase().contains(suffix) }
                            ?.let { accept(File(projectDir, it))?.let { f -> return f } }
                    }
                }
            }
            // Path-suffix match: "java/com/foo/Bar.kt" finds full rel path containing that suffix
            if (cleaned.contains('/') && cleaned.length >= 6) {
                val suffix = cleaned.lowercase()
                index.byLowerPath.entries.firstOrNull { (k, _) ->
                    k.endsWith(suffix) || k.contains("/$suffix") || k.endsWith("/$suffix")
                }?.value?.let { rel ->
                    accept(File(projectDir, rel))?.let { return it }
                }
            }
        }
        return null
    }

    fun readFile(projectDir: String, relPath: String): String {
        return try {
            val f = resolvePath(projectDir, relPath, ResolveMode.READ)
                ?: return "ERROR: File not found (or outside project sandbox): $relPath"
            val shownPath = relativePathOf(File(projectDir), f)
            if (!isTextFile(f.name)) {
                val ext = f.name.substringAfterLast('.', "").lowercase()
                val size = f.length()
                val hint = when (ext) {
                    "png", "jpg", "jpeg", "webp", "gif", "bmp" ->
                        "This is an IMAGE file. Use ANALYZE_IMAGE \"$shownPath\" to see its contents via vision."
                    "pdf" ->
                        "This is a PDF file. Use PDF_READ \"$shownPath\" to extract its text content."
                    "mp4", "mov", "webm", "mkv", "avi" ->
                        "This is a video file ($ext, ${size / 1024}KB). It cannot be read as text."
                    "xlsx", "xls" ->
                        "This is an Excel spreadsheet ($ext, ${size / 1024}KB). Cannot read as raw text."
                    "docx", "doc" ->
                        "This is a Word document ($ext, ${size / 1024}KB). Cannot read as raw text."
                    "pptx", "ppt" ->
                        "This is a PowerPoint file ($ext, ${size / 1024}KB). Cannot read as raw text."
                    else ->
                        "Binary file ($ext, ${size / 1024}KB). Cannot read as text."
                }
                return "NOTE: $shownPath is a binary file (${ext}, ${size} bytes).\n$hint"
            }

            // Soft line-cap + hard byte-cap (BigChangePolicy raises caps for multi-file tasks).
            // Full small-file reads stay UNNUMBERED so EDIT_FILE anchors paste cleanly.
            val caps = BigChangePolicy.caps()
            val hard = f.length() > caps.hardBytes
            val lines = try {
                readTextCached(f).replace("\r\n", "\n").replace('\r', '\n').lines()
            } catch (e: Exception) {
                return "ERROR: ${e.message}"
            }
            val total = lines.size
            val soft = caps.softLines
            val cap = if (hard) (soft / 4).coerceAtLeast(200) else soft
            if (total > cap || hard) {
                val head = lines.take(cap).mapIndexed { i, l -> "${i + 1}: $l" }.joinToString("\n")
                FileSessionTracker.markRead(projectDir, shownPath)
                return "NOTE: File `$shownPath` has $total lines (${f.length()} bytes). " +
                    "Showing first $cap numbered lines for navigation. " +
                    "Use READ_LINES `$shownPath` <start> <end> for more. " +
                    "When EDITing, copy text WITHOUT the line-number prefix. " +
                    "Full WRITE_FILE rewrite is allowed after this read.\n\n$head"
            }
            // Exact file body (no prefixes) — best for reliable EDIT_FILE search anchors
            FileSessionTracker.markRead(projectDir, shownPath)
            lines.joinToString("\n")
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Reads a specific inclusive line range (1-based) from a file. For large files.
     */
    fun readFileLines(projectDir: String, relPath: String, start: Int, end: Int): String {
        return try {
            val f = resolvePath(projectDir, relPath, ResolveMode.READ)
                ?: return "ERROR: File not found (or outside project sandbox): $relPath"
            if (!isTextFile(f.name)) return "ERROR: Binary file, cannot read as text"
            val shownPath = relativePathOf(File(projectDir), f)
            val s = maxOf(1, start)
            // Cap range size so a single call can't dump 50k lines into context
            val maxRange = BigChangePolicy.caps().readLinesMax
            val rawEnd = if (end < s) s + 200 else end
            val e = minOf(rawEnd, s + maxRange - 1)
            val allLines = try {
                readTextCached(f).replace("\r\n", "\n").replace('\r', '\n').lines()
            } catch (ex: Exception) {
                return "ERROR: ${ex.message}"
            }
            val n = allLines.size
            if (s > n) return "ERROR: No lines in range $s-$e (file has $n lines)"
            val sliceEnd = minOf(e, n)
            val sb = StringBuilder()
            for (i in (s - 1) until sliceEnd) {
                sb.append("${i + 1}: ${allLines[i]}\n")
            }
            if (sb.isEmpty()) "ERROR: No lines in range $s-$e (file has $n lines)"
            else {
                FileSessionTracker.markRead(projectDir, shownPath)
                val note = if (rawEnd > e) " (capped to $maxRange lines/call; ask for next range if needed)" else ""
                "Lines $s-$sliceEnd of $shownPath$note:\n${sb.toString().trimEnd()}"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Batch-read several project files in one call (Cline-style `read_files`).
     * [paths] may be comma/newline separated or a JSON array of strings.
     * Each text file is soft-capped so one call cannot flood context.
     */
    fun readFiles(projectDir: String, pathsRaw: String, maxFiles: Int = 12, maxLinesPerFile: Int = 250): String {
        val paths = parsePathList(pathsRaw)
        if (paths.isEmpty()) {
            return "ERROR: READ_FILES needs at least one path. Pass paths as comma-separated list or JSON array."
        }
        val bc = BigChangePolicy.caps()
        val cap = maxOf(maxFiles, bc.batchMaxFiles).coerceIn(1, 32)
        val lineCap = maxOf(maxLinesPerFile, bc.batchLinesPerFile).coerceIn(40, 800)
        val selected = paths.take(cap)
        val sb = StringBuilder()
        sb.append("BATCH READ (${selected.size} file(s)")
        if (paths.size > selected.size) sb.append(", ${paths.size - selected.size} skipped over cap")
        sb.append("):\n")
        for ((idx, raw) in selected.withIndex()) {
            sb.append("\n======== [${idx + 1}/${selected.size}] $raw ========\n")
            val body = readFile(projectDir, raw) // marks FileSessionTracker on success
            if (body.startsWith("ERROR:")) {
                sb.append(body).append("\n")
                continue
            }
            // NOTE: truncated previews also count as read (markRead inside readFile)
            if (body.startsWith("NOTE:")) {
                sb.append(body).append("\n")
                continue
            }
            val lines = body.lines()
            if (lines.size > lineCap) {
                sb.append("NOTE: $raw has ${lines.size} lines — showing first $lineCap. Use READ_LINES for more.\n")
                sb.append("EDIT tip: copy text WITHOUT line numbers.\n\n")
                // Number only truncated previews (navigation). Full small files stay unnumbered for clean EDIT anchors.
                sb.append(lines.take(lineCap).mapIndexed { i, l -> "${i + 1}: $l" }.joinToString("\n"))
            } else {
                // Exact body (no line-number prefixes) — same as READ_FILE, so EDIT_FILE anchors paste cleanly
                sb.append(body)
            }
            sb.append("\n")
        }
        return sb.toString().trimEnd().take(48_000)
    }

    /**
     * Insert [text] after 1-based line [afterLine] (0 = beginning of file).
     * Trae-style surgical insert — no full-file rewrite or search-replace needed.
     */
    fun insertLines(projectDir: String, relPath: String, afterLine: Int, text: String): String {
        return try {
            if (text.isEmpty()) return "ERROR: INSERT_LINES needs non-empty text to insert."
            val f = resolvePath(projectDir, relPath, ResolveMode.WRITE)
                ?: return "ERROR: File not found (or outside sandbox / ambiguous path): $relPath"
            if (!isTextFile(f.name)) return "ERROR: Binary file — cannot insert text"
            if (!isInsideProject(projectDir, f)) return "ERROR: Path outside project sandbox"
            // Grok-style: capture BEFORE first mutation this prompt (for turn rewind).
            FileStateRegistry.captureBeforeIfActive(projectDir, relativePathOf(File(projectDir), f))
            val shown = relativePathOf(File(projectDir), f)
            val raw = readTextCached(f)
            val preferCrlf = raw.contains("\r\n")
            val lines = raw.replace("\r\n", "\n").replace('\r', '\n').lines().toMutableList()
            // File.lines() drops a trailing empty line representation inconsistently;
            // work on content lines then rejoin with \n.
            val insertText = text.replace("\r\n", "\n").replace("\r", "\n")
            val toInsert = insertText.lines()
            val at = afterLine.coerceIn(0, lines.size)
            // afterLine is 1-based "after this line"; 0 means before first line
            val index = at // insert at list index = afterLine (lines 1..n map to indices 0..n-1; after line k => index k)
            lines.addAll(index, toInsert)
            val updatedLf = lines.joinToString("\n")
            val updated = if (preferCrlf) updatedLf.replace("\n", "\r\n") else updatedLf
            atomicWrite(f, updated)
            val startLine = at + 1
            val endLine = at + toInsert.size
            "OK: Inserted ${toInsert.size} line(s) into $shown after line $at (now lines $startLine–$endLine)"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /** Parse comma / newline / JSON-array path lists from model args. */
    fun parsePathList(raw: String): List<String> {
        val t = raw.trim()
        if (t.isEmpty()) return emptyList()
        if (t.startsWith("[")) {
            return try {
                val arr = org.json.JSONArray(t)
                (0 until arr.length()).mapNotNull { i ->
                    arr.optString(i, "").trim().trim('"', '\'', '`').takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                t.trim('[', ']').split(',').map { it.trim().trim('"', '\'', '`') }.filter { it.isNotBlank() }
            }
        }
        return t.split(Regex("[,\\n;]+"))
            .map { it.trim().trim('"', '\'', '`') }
            .filter { it.isNotBlank() }
    }

    /**
     * Full-file write / create.
     * Existing files longer than [WRITE_REQUIRE_READ_CHARS] must have been READ this
     * agent session (see [FileSessionTracker]) — then full rewrite is allowed.
     * New files and short files always allowed.
     */
    private const val WRITE_REQUIRE_READ_CHARS = 1200

    fun writeFile(projectDir: String, relPath: String, content: String): String {
        return try {
            // Prefer exact existing path (WRITE mode — no fuzzy basename)
            val existing = resolvePath(projectDir, relPath, ResolveMode.WRITE)
            val f = existing ?: resolveForWrite(projectDir, relPath)
                ?: return "ERROR: Path escapes project sandbox or is invalid: $relPath"
            val shown = relativePathOf(File(projectDir), f).ifBlank { cleanRelPath(relPath) }
            val exists = f.exists() && f.isFile
            if (exists && f.length() >= WRITE_REQUIRE_READ_CHARS) {
                if (!FileSessionTracker.wasRead(projectDir, shown) &&
                    !FileSessionTracker.wasRead(projectDir, relPath)
                ) {
                    return "ERROR: WRITE_FILE on existing file `$shown` (${f.length()} bytes) " +
                        "requires READ_FILE/READ_LINES first this session (prevents blind overwrite). " +
                        "Either: (1) READ then WRITE full content, or (2) use MULTI_EDIT / EDIT_FILE for partial changes."
                }
            }
            // Grok-style before-snapshot (null content if file is new)
            FileStateRegistry.captureBeforeIfActive(projectDir, shown)
            f.parentFile?.mkdirs()
            // Atomic-ish write: temp then rename when possible
            val tmp = File(f.parentFile, ".${f.name}.ahamai-tmp")
            try {
                tmp.writeText(content)
                if (!tmp.renameTo(f)) {
                    f.writeText(content)
                    tmp.delete()
                }
            } catch (e: Exception) {
                tmp.delete()
                f.writeText(content)
            }
            invalidateTextListCache()
            try { putFileTextCache(f, content) } catch (_: Exception) { invalidateFileTextCache(f) }
            FileSessionTracker.markRead(projectDir, shown)
            val mode = if (exists) "rewrote (read-ok)" else "created"
            "OK: Saved $shown (${content.lines().size} lines, $mode)"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun deleteFile(projectDir: String, relPath: String): String {
        return try {
            val f = resolvePath(projectDir, relPath, ResolveMode.WRITE)
            if (f != null && f.exists()) {
                if (!isInsideProject(projectDir, f)) return "ERROR: Path outside project sandbox"
                FileStateRegistry.captureBeforeIfActive(projectDir, relativePathOf(File(projectDir), f))
                invalidateFileTextCache(f)
                f.deleteRecursively()
                invalidateTextListCache()
                "OK: Deleted ${relativePathOf(File(projectDir), f)}"
            } else {
                "ERROR: File not found: $relPath"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Keyword search across text files. Optional [pathScope] limits to a subfolder/file.
     * Returns matching paths + line snippets with optional context lines.
     */
    fun searchCode(
        projectDir: String,
        query: String,
        pathScope: String = "",
        maxResults: Int = 40,
        contextLines: Int = 0
    ): String {
        if (query.isBlank()) return "ERROR: empty search query."
        return grepSearch(
            projectDir = projectDir,
            pattern = Regex.escape(query),
            pathScope = pathScope,
            glob = "",
            caseInsensitive = true,
            contextLines = contextLines,
            maxResults = maxResults,
            fixedStringLabel = query
        )
    }

    /**
     * Re-zips the project into a downloadable file in the cache dir.
     * Includes ALL project files (gradle wrapper, .github workflows, etc.)
     * Only skips truly unneeded caches (.git, node_modules, .gradle, .idea, build, etc.)
     *
     * Returns null for an EMPTY project (no eligible files) — so [DeviceStorage]
     * never gets asked to write a 22-byte "empty zip" into the user's Downloads.
     */
    fun createZip(context: Context, projectDir: String, projectName: String): File? {
        return try {
            val root = File(projectDir)
            if (!root.isDirectory) return null
            // Pre-count eligible files so we can refuse to create an empty zip rather
            // than hand the user a 22-byte broken archive in their Downloads folder.
            var eligible = 0
            fun countDir(d: File) {
                val entries = d.listFiles() ?: return
                for (f in entries) {
                    if (f.isDirectory) {
                        if (f.name in SKIP_DIRS_ZIP) continue
                        countDir(f)
                    } else {
                        val ext = f.extension.lowercase()
                        if (ext in setOf("apk", "aab", "so", "dex", "class", "zip")) continue
                        if (f.name.startsWith(".ahamai")) continue
                        if (f.length() > 30_000_000) continue
                        eligible++
                    }
                }
            }
            countDir(root)
            if (eligible == 0) return null
            val outFile = File(context.cacheDir, "${projectName}_edited_${System.currentTimeMillis()}.zip")
            ZipOutputStream(FileOutputStream(outFile).buffered()).use { zos ->
                fun addDir(dir: File) {
                    val entries = dir.listFiles() ?: return
                    for (f in entries) {
                        if (f.isDirectory) {
                            if (f.name in SKIP_DIRS_ZIP) continue
                            addDir(f)
                        } else {
                            // Skip prebuilt binary artifacts, caches, and the original uploaded zip.
                            // NOTE: .jar files are NOT excluded because gradle-wrapper.jar (~58KB)
                            // and libs/*.jar are essential for building the app. They're already
                            // tiny and skipping them would break the exported project.
                            val ext = f.extension.lowercase()
                            if (ext in setOf("apk", "aab", "so", "dex", "class", "zip")) continue
                            if (f.name.startsWith(".ahamai")) continue
                            // Skip files larger than 30MB — source projects are rarely larger.
                            // This prevents accidental inclusion of font files, mp3s, large
                            // lottie JSONs, or stray cached data from ballooning the export.
                            if (f.length() > 30_000_000) continue
                            val rel = relativePathOf(root, f)
                            zos.putNextEntry(ZipEntry(rel))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                        }
                    }
                }
                addDir(root)
            }
            outFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Search/replace edit within a file.
     *
     * Strategies (match may use normalized forms, but the **write always uses original content**
     * with the real span replaced — so we never rewrite the whole file's whitespace):
     *   1. Exact text match
     *   2. Trailing-whitespace-trimmed line match → map back to original span
     *   3. Whitespace-normalised match (tabs/spaces) → map back via indent-flex / exact region
     *   4. Indentation-flexible line match (verbatim original lines)
     *   5. Idempotency: already applied
     *
     * @param replaceAll when true, replace every exact occurrence (only exact strategy; requires ≥1 hit)
     */
    /**
     * Models often paste anchors from READ_LINES / numbered READ_FILES as "12: code".
     * Strip a leading `N:` / `N|` prefix on each line so EDIT still matches real source.
     */
    private fun stripLineNumberPrefixes(text: String): String {
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').lines()
        if (lines.isEmpty()) return text
        // Only strip when MOST lines look numbered (avoid breaking real code like "1: map")
        val numbered = lines.count { Regex("""^\s*\d+[:|]\s?""").containsMatchIn(it) }
        if (numbered < (lines.size + 1) / 2) return text.replace("\r\n", "\n").replace('\r', '\n')
        return lines.joinToString("\n") {
            it.replace(Regex("""^\s*\d+[:|]\s?"""), "")
        }
    }

    /**
     * In-memory result of one search/replace against LF-normalized content.
     * [contentOut] is the new body when [ok]; null when failed (content unchanged).
     */
    private data class InMemoryEdit(
        val ok: Boolean,
        val contentOut: String?,
        val message: String,
        val changed: Boolean
    )

    /**
     * Apply one search/replace purely in memory (no disk). Used by [editFile] and
     * [multiEditFile] so MULTI_EDIT can chain N pairs with a single read + single write.
     */
    private fun applyOneEditInMemory(
        content: String,
        oldText: String,
        newText: String,
        shown: String,
        replaceAll: Boolean = false
    ): InMemoryEdit {
        val old = stripLineNumberPrefixes(oldText).trimEnd()
        val newNorm = stripLineNumberPrefixes(newText).replace("\r\n", "\n").replace('\r', '\n')
        if (old.isBlank()) {
            return InMemoryEdit(false, null,
                "ERROR: SEARCH text is empty — provide the exact code to replace (include 2-3 surrounding lines so it's unique).",
                false)
        }

        if (replaceAll) {
            if (!content.contains(old)) {
                val msg = "ERROR: SEARCH text not found for replace_all in $shown. " +
                    (closestSnippet(content, old)?.let {
                        "Closest match:\n---\n$it\n---\nUse exact text, or call EDIT without replace_all for flexible match."
                    } ?: "Read the file and copy exact text.")
                return InMemoryEdit(false, null, msg, false)
            }
            val count = content.split(old).size - 1
            return InMemoryEdit(true, content.replace(old, newNorm),
                "OK: Edited $shown (replace_all: $count occurrence(s))", true)
        }

        fun applyExact(find: String, replace: String): InMemoryEdit? {
            if (find.isBlank() || !content.contains(find)) return null
            val cnt = content.split(find).size - 1
            if (cnt > 1) {
                return InMemoryEdit(false, null,
                    "ERROR: SEARCH text matches ${if (cnt > 50) "many (50+)" else cnt.toString()} places in $shown (ambiguous). " +
                        "Add 2-3 more surrounding lines so it matches exactly ONE place, or set replace_all=true.",
                    false)
            }
            return InMemoryEdit(true, content.replaceFirst(find, replace), "OK: Edited $shown", true)
        }

        applyExact(old, newNorm)?.let { return it }

        val oldTrimmed = old.lines().joinToString("\n") { it.trimEnd() }
        val contentLines = content.lines()
        val trimmedLines = contentLines.map { it.trimEnd() }
        val region = findLineRegionMatch(trimmedLines, oldTrimmed.lines())
        if (region != null) {
            val (start, endExclusive) = region
            val originalSpan = contentLines.subList(start, endExclusive).joinToString("\n")
            applyExact(originalSpan, newNorm)?.let { return it }
        }

        val flexMatch = findIndentFlexibleMatch(content, old)
        if (flexMatch != null) {
            applyExact(flexMatch, newNorm)?.let { return it }
        }

        if (old.trim().length >= 8) {
            val normLine = { s: String -> s.replace('\t', ' ').replace(Regex(" {2,}"), " ").trimEnd() }
            val normOldLines = old.lines().map(normLine)
            val normContentLines = contentLines.map(normLine)
            val normRegion = findLineRegionMatch(normContentLines, normOldLines)
            if (normRegion != null) {
                val (start, endExclusive) = normRegion
                val originalSpan = contentLines.subList(start, endExclusive).joinToString("\n")
                applyExact(originalSpan, newNorm)?.let { return it }
            }
        }

        val newTrimmed = newNorm.trim()
        if (newTrimmed.isNotEmpty() && content.contains(newTrimmed) && !content.contains(old)) {
            return InMemoryEdit(true, content, "OK: already applied (no change needed) — $shown", false)
        }

        val err = "ERROR: SEARCH text not found in $shown. " + (closestSnippet(content, old)?.let {
            "The closest matching text currently in the file is:\n--- closest match ---\n$it\n---------------------\nCopy the EXACT text above (with its real indentation) as your SEARCH, then retry."
        } ?: "Read the file again and copy the exact text from it.")
        return InMemoryEdit(false, null, err, false)
    }

    fun editFile(
        projectDir: String,
        relPath: String,
        oldText: String,
        newText: String,
        replaceAll: Boolean = false
    ): String {
        return try {
            val f = resolvePath(projectDir, relPath, ResolveMode.WRITE)
                ?: return "ERROR: File not found (or outside sandbox / ambiguous path — use full project-relative path): $relPath"
            if (!isTextFile(f.name)) return "ERROR: Binary file"
            if (!isInsideProject(projectDir, f)) return "ERROR: Path outside project sandbox"
            FileStateRegistry.captureBeforeIfActive(projectDir, relativePathOf(File(projectDir), f))
            val raw = readTextCached(f)
            val preferCrlf = raw.contains("\r\n")
            val content = raw.replace("\r\n", "\n").replace('\r', '\n')
            val shown = relativePathOf(File(projectDir), f)
            val result = applyOneEditInMemory(content, oldText, newText, shown, replaceAll)
            if (result.ok && result.changed && result.contentOut != null) {
                val out = if (preferCrlf) result.contentOut.replace("\n", "\r\n") else result.contentOut
                atomicWrite(f, out)
            }
            result.message
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Apply many (old→new) pairs to ONE file with a single disk read + single write.
     * **All-or-nothing:** if any pair fails, the file is left unchanged (no partial apply).
     * Pairs that are already applied (no-op) count as success.
     */
    fun multiEditFile(
        projectDir: String,
        relPath: String,
        pairs: List<Pair<String, String>>
    ): String {
        if (pairs.isEmpty()) return "ERROR: MULTI_EDIT needs at least one (old, new) pair."
        return try {
            val f = resolvePath(projectDir, relPath, ResolveMode.WRITE)
                ?: return "ERROR: File not found (or outside sandbox / ambiguous path — use full project-relative path): $relPath"
            if (!isTextFile(f.name)) return "ERROR: Binary file"
            if (!isInsideProject(projectDir, f)) return "ERROR: Path outside project sandbox"
            FileStateRegistry.captureBeforeIfActive(projectDir, relativePathOf(File(projectDir), f))
            val raw = readTextCached(f)
            val preferCrlf = raw.contains("\r\n")
            val original = raw.replace("\r\n", "\n").replace('\r', '\n')
            var content = original
            val shown = relativePathOf(File(projectDir), f)
            val log = StringBuilder()
            var changedCount = 0
            for ((i, pair) in pairs.withIndex()) {
                val r = applyOneEditInMemory(content, pair.first, pair.second, shown, replaceAll = false)
                if (!r.ok) {
                    // Atomic abort: do NOT write any partial result.
                    return buildString {
                        append("ERROR: MULTI_EDIT aborted — no changes written (all-or-nothing).\n")
                        append("  Failed pair [").append(i + 1).append('/').append(pairs.size).append("]: ")
                        append(r.message.take(500)).append('\n')
                        if (log.isNotEmpty()) {
                            append("  Prior pairs in this call would have been:\n").append(log)
                        }
                        append("Fix the failed SEARCH text (READ_FILE first), then resend the FULL MULTI_EDIT with all pairs.")
                    }
                }
                if (r.changed && r.contentOut != null) {
                    content = r.contentOut
                    changedCount++
                    log.append("  [").append(i + 1).append("] OK changed\n")
                } else {
                    log.append("  [").append(i + 1).append("] OK already-applied\n")
                }
            }
            if (content != original) {
                val out = if (preferCrlf) content.replace("\n", "\r\n") else content
                atomicWrite(f, out)
            }
            FileSessionTracker.markRead(projectDir, shown) // treat as known content after multi-edit
            "MULTI_EDIT $shown: all ${pairs.size} pair(s) OK ✓ atomic" +
                (if (changedCount > 0) " ($changedCount changed, ${pairs.size - changedCount} no-op)" else " (all no-op)") +
                "\n$log"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /** Atomic-ish text write via temp file. */
    private fun atomicWrite(f: File, content: String) {
        val tmp = File(f.parentFile, ".${f.name}.ahamai-tmp")
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
        invalidateTextListCache()
        // Refresh content cache with what we just wrote (avoid stale re-reads in same turn)
        try {
            putFileTextCache(f, content)
        } catch (_: Exception) {
            invalidateFileTextCache(f)
        }
    }

    /**
     * Find [patternLines] as a contiguous unique region inside [haystackLines].
     * Returns start index (inclusive) and end index (exclusive), or null if 0 or 2+ matches.
     */
    private fun findLineRegionMatch(haystackLines: List<String>, patternLines: List<String>): Pair<Int, Int>? {
        if (patternLines.isEmpty()) return null
        // Drop blank edges for matching but keep length for span? Keep full pattern lines.
        val hits = mutableListOf<Int>()
        outer@ for (i in 0..haystackLines.size - patternLines.size) {
            for (j in patternLines.indices) {
                if (haystackLines[i + j] != patternLines[j]) continue@outer
            }
            hits.add(i)
            if (hits.size > 1) return null // ambiguous
        }
        if (hits.size != 1) return null
        val start = hits[0]
        return start to (start + patternLines.size)
    }

    /**
     * Apply a unified diff / multi-hunk patch to a file (or multi-file patch).
     * Accepts simplified unified diff format:
     *   *** Begin Patch
     *   *** Update File: path/to/file
     *   @@
     *   -old
     *   +new
     *   *** End Patch
     * Or classic ---/+++/@@ hunks for a single file when [defaultPath] is set.
     */
    fun applyPatch(projectDir: String, patchText: String, defaultPath: String = ""): String {
        val patch = patchText.trim()
        if (patch.isBlank()) return "ERROR: empty patch"

        // Multi-file "Begin Patch" format
        if (patch.contains("*** Begin Patch") || patch.contains("*** Update File:") || patch.contains("*** Add File:")) {
            return applyBeginPatchFormat(projectDir, patch)
        }

        // Classic unified diff for one file
        // ${'$'} = literal end-anchor $ (raw strings can't use \$; bare $ can crash IR lowering)
        val pathFromDiff = Regex("""^\+\+\+\s+(?:b/)?(.+)${'$'}""", RegexOption.MULTILINE)
            .find(patch)?.groupValues?.get(1)?.trim()?.removePrefix("b/")
        val targetPath = pathFromDiff?.takeIf { it.isNotBlank() && it != "/dev/null" } ?: defaultPath
        if (targetPath.isBlank()) {
            return "ERROR: Could not determine file path from patch. Pass path= or use *** Update File: header."
        }

        val f = resolvePath(projectDir, targetPath, ResolveMode.WRITE)
            ?: resolveForWrite(projectDir, targetPath)
            ?: return "ERROR: Path outside sandbox or invalid: $targetPath"
        FileStateRegistry.captureBeforeIfActive(
            projectDir,
            relativePathOf(File(projectDir), f).ifBlank { cleanRelPath(targetPath) }
        )
        if (!f.exists()) {
            // Allow create via patch that is all additions
            f.parentFile?.mkdirs()
        }
        val original = if (f.exists()) f.readText() else ""
        val result = applyUnifiedHunks(original, patch)
            ?: return "ERROR: Failed to apply unified hunks to $targetPath — context didn't match. READ_FILE and retry with exact context."
        atomicWrite(f, result)
        return "OK: Applied patch to ${relativePathOf(File(projectDir), f)}"
    }

    private fun applyBeginPatchFormat(projectDir: String, patch: String): String {
        val reports = mutableListOf<String>()
        // Split on *** Update File: / *** Add File: / *** Delete File:
        val parts = Regex("\\*\\*\\*\\s+(Update File|Add File|Delete File):\\s*(.+)").findAll(patch).toList()
        if (parts.isEmpty()) return "ERROR: No *** Update/Add/Delete File: sections found in patch."

        for ((idx, m) in parts.withIndex()) {
            val kind = m.groupValues[1]
            val path = m.groupValues[2].trim()
            FileStateRegistry.captureBeforeIfActive(projectDir, cleanRelPath(path))
            val start = m.range.last + 1
            val end = parts.getOrNull(idx + 1)?.range?.first ?: patch.length
            val body = patch.substring(start, end).trim()
            when (kind) {
                "Delete File" -> {
                    reports.add(deleteFile(projectDir, path))
                }
                "Add File" -> {
                    val content = body.lines()
                        .filter { it.startsWith("+") }
                        .joinToString("\n") { it.removePrefix("+") }
                        .ifBlank {
                            // Also accept raw body without + prefixes
                            body.lineSequence()
                                .filterNot { it.startsWith("***") || it.startsWith("@@") }
                                .joinToString("\n")
                        }
                    reports.add(writeFile(projectDir, path, content))
                }
                else -> {
                    // Update: try line-based +/- without needing full unified headers
                    val f = resolvePath(projectDir, path, ResolveMode.WRITE)
                        ?: return "ERROR: File not found for patch update: $path".also { reports.add(it) }
                    val original = f.readText()
                    val applied = applyPlusMinusPatch(original, body)
                        ?: applyUnifiedHunks(original, body)
                    if (applied == null) {
                        reports.add("ERROR: Patch context mismatch for $path")
                    } else {
                        atomicWrite(f, applied)
                        reports.add("OK: Patched ${relativePathOf(File(projectDir), f)}")
                    }
                }
            }
        }
        val fails = reports.count { it.startsWith("ERROR") }
        return if (fails == 0) "OK: Applied patch (${reports.size} file op(s))\n" + reports.joinToString("\n")
        else "PATCH partial: $fails failed\n" + reports.joinToString("\n")
    }

    /**
     * Apply a simple block of context/old/new lines:
     *   space = context, - = remove, + = add (V4A / Cursor style without @@).
     */
    private fun applyPlusMinusPatch(original: String, body: String): String? {
        val lines = body.lines().filter {
            val t = it.trimEnd()
            t.isNotEmpty() && !t.startsWith("***") && !t.startsWith("@@") &&
                !t.startsWith("---") && !t.startsWith("+++") && !t.startsWith("diff ")
        }
        if (lines.isEmpty()) return null

        // Build old block (context + minus) and new block (context + plus)
        val oldLines = mutableListOf<String>()
        val newLines = mutableListOf<String>()
        for (raw in lines) {
            when {
                raw.startsWith("-") && !raw.startsWith("---") -> {
                    oldLines.add(raw.removePrefix("-"))
                }
                raw.startsWith("+") && !raw.startsWith("+++") -> {
                    newLines.add(raw.removePrefix("+"))
                }
                raw.startsWith(" ") -> {
                    val c = raw.removePrefix(" ")
                    oldLines.add(c)
                    newLines.add(c)
                }
                else -> {
                    // bare context line
                    oldLines.add(raw)
                    newLines.add(raw)
                }
            }
        }
        val oldBlock = oldLines.joinToString("\n")
        val newBlock = newLines.joinToString("\n")
        if (oldBlock.isBlank()) {
            // pure append?
            return if (original.isEmpty()) newBlock else null
        }
        if (!original.contains(oldBlock)) {
            // try trimEnd match via edit path
            val flex = findIndentFlexibleMatch(original, oldBlock) ?: return null
            if (!original.contains(flex)) return null
            return original.replaceFirst(flex, newBlock)
        }
        val cnt = original.split(oldBlock).size - 1
        if (cnt != 1) return null
        return original.replaceFirst(oldBlock, newBlock)
    }

    /** Minimal unified-diff hunk applier (@@ -l,s +l,s @@). */
    private fun applyUnifiedHunks(original: String, patch: String): String? {
        val origLines = original.lines().toMutableList()
        val hunkHeader = Regex("""^@@\s+-(\d+)(?:,(\d+))?\s+\+(\d+)(?:,(\d+))?\s+@@""")
        val hunks = mutableListOf<Pair<MatchResult, List<String>>>()
        val all = patch.lines()
        var i = 0
        while (i < all.size) {
            val m = hunkHeader.find(all[i])
            if (m != null) {
                i++
                val body = mutableListOf<String>()
                while (i < all.size && !all[i].startsWith("@@") && !all[i].startsWith("diff ") && !all[i].startsWith("***")) {
                    body.add(all[i]); i++
                }
                hunks.add(m to body)
            } else i++
        }
        if (hunks.isEmpty()) {
            // Fall back to plus/minus without headers
            return applyPlusMinusPatch(original, patch)
        }
        // Apply from bottom to top so line numbers stay valid
        for ((header, body) in hunks.asReversed()) {
            val startOld = header.groupValues[1].toInt() // 1-based
            val oldBlock = mutableListOf<String>()
            val newBlock = mutableListOf<String>()
            for (raw in body) {
                when {
                    raw.startsWith("-") -> oldBlock.add(raw.removePrefix("-"))
                    raw.startsWith("+") -> newBlock.add(raw.removePrefix("+"))
                    raw.startsWith(" ") -> {
                        val c = raw.removePrefix(" ")
                        oldBlock.add(c); newBlock.add(c)
                    }
                    raw.startsWith("\\") -> { /* "\ No newline" */ }
                    else -> { oldBlock.add(raw); newBlock.add(raw) }
                }
            }
            val idx = (startOld - 1).coerceAtLeast(0)
            // Verify context matches at idx
            if (idx + oldBlock.size > origLines.size && oldBlock.isNotEmpty()) {
                // try search for oldBlock join
                val joined = oldBlock.joinToString("\n")
                val full = origLines.joinToString("\n")
                if (!full.contains(joined)) return null
                val replaced = full.replaceFirst(joined, newBlock.joinToString("\n"))
                origLines.clear()
                origLines.addAll(replaced.lines())
            } else {
                for (j in oldBlock.indices) {
                    if (origLines.getOrNull(idx + j) != oldBlock[j]) {
                        // soft: try whole-block replace
                        val joined = oldBlock.joinToString("\n")
                        val full = origLines.joinToString("\n")
                        if (!full.contains(joined)) return null
                        val replaced = full.replaceFirst(joined, newBlock.joinToString("\n"))
                        origLines.clear()
                        origLines.addAll(replaced.lines())
                        continue
                    }
                }
                repeat(oldBlock.size) { if (idx < origLines.size) origLines.removeAt(idx) }
                newBlock.asReversed().forEach { origLines.add(idx, it) }
            }
        }
        return origLines.joinToString("\n")
    }

    /**
     * On a failed edit, find the region of [content] most similar to [pattern]'s first meaningful
     * line (token-overlap) and return ~6 lines around it — a Cursor/Claude-style helpful hint so the
     * model can correct its anchor in one shot instead of looping.
     */
    private fun closestSnippet(content: String, pattern: String): String? {
        val patFirst = pattern.lines().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        val patTokens = patFirst.split(Regex("\\W+")).filter { it.length > 1 }.toSet()
        if (patTokens.isEmpty()) return null
        val lines = content.lines()
        var bestIdx = -1; var bestScore = 0.0
        lines.forEachIndexed { i, line ->
            val toks = line.trim().split(Regex("\\W+")).filter { it.length > 1 }.toSet()
            if (toks.isEmpty()) return@forEachIndexed
            val score = patTokens.intersect(toks).size.toDouble() / patTokens.size
            if (score > bestScore) { bestScore = score; bestIdx = i }
        }
        if (bestIdx < 0 || bestScore < 0.4) return null
        val start = (bestIdx - 1).coerceAtLeast(0)
        val end = (bestIdx + 5).coerceAtMost(lines.size - 1)
        return lines.subList(start, end + 1).joinToString("\n").take(600)
    }

    /**
     * Finds the actual text in [content] whose lines, when left-stripped, equal
     * the lines of [pattern] when left-stripped. Returns the verbatim substring
     * from [content] that matches, or null if no match.
     */
    private fun findIndentFlexibleMatch(content: String, pattern: String): String? {
        val patLines = pattern.lines()
        if (patLines.isEmpty()) return null
        val patStripped = patLines.map { it.trimStart() }
        // Skip fully-blank pattern lines at start/end for matching purposes
        val contentLines = content.lines()
        for (startIdx in contentLines.indices) {
            if (startIdx + patLines.size > contentLines.size) break
            var allMatch = true
            for (i in patLines.indices) {
                val pLine = patStripped[i]
                val cLine = contentLines[startIdx + i].trimStart()
                if (pLine != cLine) { allMatch = false; break }
            }
            if (allMatch) {
                // Reconstruct using the ACTUAL content lines (preserving their original indent)
                return contentLines.subList(startIdx, startIdx + patLines.size).joinToString("\n")
            }
        }
        return null
    }

    fun copyFile(projectDir: String, src: String, dest: String): String {
        return try {
            val srcFile = resolvePath(projectDir, src, ResolveMode.WRITE)
                ?: return "ERROR: Source not found (or outside sandbox): $src"
            val destFile = resolveForWrite(projectDir, dest)
                ?: return "ERROR: Destination escapes project sandbox: $dest"
            FileStateRegistry.captureBeforeIfActive(
                projectDir,
                relativePathOf(File(projectDir), destFile).ifBlank { cleanRelPath(dest) }
            )
            destFile.parentFile?.mkdirs()
            srcFile.copyTo(destFile, overwrite = true)
            invalidateTextListCache()
            invalidateFileTextCache(srcFile)
            invalidateFileTextCache(destFile)
            "OK: Copied to ${relativePathOf(File(projectDir), destFile)}"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun moveFile(projectDir: String, src: String, dest: String): String {
        return try {
            val srcFile = resolvePath(projectDir, src, ResolveMode.WRITE)
                ?: return "ERROR: Source not found (or outside sandbox): $src"
            val destFile = resolveForWrite(projectDir, dest)
                ?: return "ERROR: Destination escapes project sandbox: $dest"
            // Capture both source (will be deleted) and dest (will be overwritten).
            FileStateRegistry.captureBeforeIfActive(projectDir, relativePathOf(File(projectDir), srcFile))
            FileStateRegistry.captureBeforeIfActive(
                projectDir,
                relativePathOf(File(projectDir), destFile).ifBlank { cleanRelPath(dest) }
            )
            destFile.parentFile?.mkdirs()
            srcFile.copyTo(destFile, overwrite = true)
            invalidateFileTextCache(srcFile)
            srcFile.delete()
            invalidateTextListCache()
            invalidateFileTextCache(destFile)
            "OK: Moved to ${relativePathOf(File(projectDir), destFile)}"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Downloads content from a URL and saves it into the project.
     */
    fun downloadUrl(projectDir: String, url: String, destPath: String): String {
        return try {
            val req = okhttp3.Request.Builder()
                .url(url.trim())
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return "ERROR: HTTP ${resp.code} downloading $url"
            val bytes = resp.body?.bytes() ?: return "ERROR: Empty response"
            val destFile = resolveForWrite(projectDir, destPath)
                ?: return "ERROR: Destination escapes project sandbox: $destPath"
            destFile.parentFile?.mkdirs()
            destFile.writeBytes(bytes)
            "OK: Downloaded ${bytes.size} bytes to ${relativePathOf(File(projectDir), destFile)}"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Fetches text content from a URL (for reference - docs, APIs).
     */
    fun fetchUrl(url: String): String {
        return try {
            val req = okhttp3.Request.Builder()
                .url(url.trim())
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return "ERROR: HTTP ${resp.code}"
            val text = resp.body?.string() ?: return "ERROR: Empty"
            text.take(8000)
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Basic HTML validation - checks balanced tags and common errors.
     */
    fun checkHtml(projectDir: String, relPath: String): String {
        return try {
            val f = resolvePath(projectDir, relPath, ResolveMode.READ) ?: return "ERROR: File not found"
            val html = f.readText()
            val issues = mutableListOf<String>()
            for (tag in listOf("html", "head", "body", "div", "script", "style", "ul", "table")) {
                val opens = Regex("<$tag[ >]", RegexOption.IGNORE_CASE).findAll(html).count()
                val closes = Regex("</$tag>", RegexOption.IGNORE_CASE).findAll(html).count()
                if (opens != closes) issues.add("<$tag>: $opens open, $closes close")
            }
            if (issues.isEmpty()) "OK: No structural HTML issues found in ${f.name}"
            else "HTML issues in ${f.name}:\n" + issues.joinToString("\n")
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Project-wide search & replace across text files.
     * Uses the same flexible matching as EDIT_FILE (CRLF normalize, line-number strip,
     * indent/whitespace strategies) via [applyOneEditInMemory], plus content cache.
     * Prefer exact replace_all when the anchor appears; fall back to one flexible edit per file.
     */
    fun bulkEdit(
        projectDir: String,
        oldText: String,
        newText: String,
        pathScope: String = ""
    ): String {
        if (oldText.isBlank()) return "ERROR: search text is empty"
        val files = listTextFiles(projectDir, pathScope, glob = "")
        val changed = mutableListOf<Pair<String, Int>>()
        var totalOccurrences = 0
        var flexibleFiles = 0
        for (pf in files) {
            try {
                val f = File(projectDir, pf)
                if (!isInsideProject(projectDir, f)) continue
                if (f.length() > 500_000) continue
                val raw = readTextCached(f)
                val preferCrlf = raw.contains("\r\n")
                val content = raw.replace("\r\n", "\n").replace('\r', '\n')
                // 1) Exact replace_all (after strip/normalize inside applyOneEditInMemory)
                var r = applyOneEditInMemory(content, oldText, newText, pf, replaceAll = true)
                var count = 1
                if (!r.ok) {
                    // 2) Flexible single match (indent / whitespace) — one occurrence per file
                    r = applyOneEditInMemory(content, oldText, newText, pf, replaceAll = false)
                    if (r.ok && r.changed) flexibleFiles++
                } else if (r.changed && r.contentOut != null) {
                    val oldNorm = stripLineNumberPrefixes(oldText).trimEnd()
                    if (oldNorm.isNotBlank() && content.contains(oldNorm)) {
                        count = (content.split(oldNorm).size - 1).coerceAtLeast(1)
                    }
                }
                if (r.ok && r.changed && r.contentOut != null) {
                    // Snapshot original disk bytes (raw) before first write this prompt.
                    FileStateRegistry.captureBeforeWithContentIfActive(projectDir, pf, raw)
                    val out = if (preferCrlf) r.contentOut.replace("\n", "\r\n") else r.contentOut
                    atomicWrite(f, out)
                    changed.add(pf to count)
                    totalOccurrences += count
                }
            } catch (_: Exception) {}
        }
        if (changed.isEmpty()) {
            return "No files matched '$oldText'" +
                (if (pathScope.isNotBlank()) " under $pathScope" else "") +
                ". Try a shorter unique string or GREP first."
        }
        val flexNote = if (flexibleFiles > 0) " ($flexibleFiles via flexible match)" else ""
        val sb = StringBuilder(
            "OK: Replaced in ${changed.size} file(s) ($totalOccurrences occurrence(s))$flexNote:\n"
        )
        changed.take(30).forEach { sb.append("  ${it.first}: ${it.second}x\n") }
        if (changed.size > 30) sb.append("  … and ${changed.size - 30} more\n")
        return sb.toString().trim()
    }

    /**
     * Reads multiple files at once. Returns combined content.
     */
    fun readMultiple(projectDir: String, paths: List<String>): String {
        val sb = StringBuilder()
        for (p in paths.take(10)) {
            sb.append("===== $p =====\n")
            sb.append(readFile(projectDir, p))
            sb.append("\n\n")
        }
        return sb.toString().trim()
    }

    /**
     * Next-level grep: regex (or fixed-string via escaped pattern), optional path scope,
     * glob filter, case-insensitive mode, context lines (±N), result cap.
     *
     * Output format (ripgrep-like):
     *   path:line: matched line
     *   path:line- context line  (when contextLines > 0)
     */
    fun grepSearch(
        projectDir: String,
        pattern: String,
        pathScope: String = "",
        glob: String = "",
        caseInsensitive: Boolean = false,
        contextLines: Int = 0,
        maxResults: Int = 50,
        fixedStringLabel: String? = null,
        outputMode: String = "content",   // content | files | count
        multiline: Boolean = false          // pattern may span lines (DOT matches \n)
    ): String {
        if (pattern.isBlank()) return "ERROR: empty pattern."
        val options = mutableSetOf<RegexOption>()
        if (caseInsensitive) options.add(RegexOption.IGNORE_CASE)
        // Multiline: let `.` cross newlines AND anchor ^/$ per line — so patterns like a whole
        // function body or a `{ ... }` block that spans several lines can be matched.
        if (multiline) { options.add(RegexOption.DOT_MATCHES_ALL); options.add(RegexOption.MULTILINE) }
        val regex = try {
            if (options.isEmpty()) Regex(pattern) else Regex(pattern, options)
        } catch (e: Exception) {
            return "ERROR: invalid regex: ${e.message}"
        }

        val files = listTextFiles(projectDir, pathScope, glob)
        if (files.isEmpty()) {
            return "No text files to search" +
                (if (pathScope.isNotBlank()) " under '$pathScope'" else "") +
                (if (glob.isNotBlank()) " matching glob '$glob'" else "") + "."
        }

        val label = fixedStringLabel ?: pattern
        val mode = outputMode.trim().lowercase()
        val cap = maxResults.coerceIn(1, 200)

        // ── files / count modes: aggregate per file, never dump matching lines ──
        // (Cheap way for the model to answer "which files contain X" / "how many hits"
        //  without spending tokens on the actual lines.)
        // Parallel file scan — multi-core search like instant CLI agents.
        if (mode == "files" || mode == "files_with_matches" || mode == "count") {
            val hits = java.util.concurrent.ConcurrentHashMap<String, Int>()
            files.parallelStream().forEach { rel ->
                try {
                    val file = File(projectDir, rel)
                    if (!isInsideProject(projectDir, file)) return@forEach
                    if (file.length() > SEARCH_MAX_FILE_BYTES) return@forEach
                    val text = readTextCached(file).replace("\r\n", "\n").replace('\r', '\n')
                    val cnt = if (multiline) regex.findAll(text).count()
                        else text.lineSequence().count { regex.containsMatchIn(it) }
                    if (cnt > 0) hits[rel] = cnt
                } catch (_: Exception) {}
            }
            if (hits.isEmpty()) return "No matches for '$label'" +
                (if (pathScope.isNotBlank()) " in $pathScope" else "") +
                (if (glob.isNotBlank()) " (glob $glob)" else "")
            val ordered = hits.entries.sortedBy { it.key }
            val grand = ordered.sumOf { it.value }
            return if (mode == "count")
                "Match counts for '$label' — $grand match(es) in ${hits.size} file(s):\n" +
                    ordered.take(cap).joinToString("\n") { "${it.value}\t${it.key}" }
            else
                "Files matching '$label' (${hits.size} file(s)):\n" +
                    ordered.map { it.key }.take(cap).joinToString("\n")
        }

        val ctx = contextLines.coerceIn(0, 5)
        data class FileMatch(val rel: String, val lines: List<String>, val matchIdx: List<Int>)
        val fileMatches = java.util.concurrent.CopyOnWriteArrayList<FileMatch>()
        files.parallelStream().forEach { rel ->
            try {
                val file = File(projectDir, rel)
                if (!isInsideProject(projectDir, file)) return@forEach
                if (file.length() > SEARCH_MAX_FILE_BYTES) return@forEach
                // Content cache: repeated GREP/SEARCH in the same session reuse mtime-valid text
                val text = readTextCached(file).replace("\r\n", "\n").replace('\r', '\n')
                val lines = text.lines()
                val matchIdx = mutableListOf<Int>()
                if (multiline) {
                    for (m in regex.findAll(text)) {
                        val lineNo = text.substring(0, m.range.first).count { it == '\n' }
                        if (matchIdx.isEmpty() || matchIdx.last() != lineNo) matchIdx.add(lineNo)
                    }
                } else {
                    lines.forEachIndexed { idx, line ->
                        if (regex.containsMatchIn(line)) matchIdx.add(idx)
                    }
                }
                if (matchIdx.isNotEmpty()) fileMatches.add(FileMatch(rel, lines, matchIdx))
            } catch (_: Exception) {}
        }

        // Stable order for deterministic agent results (parallel scan is unordered).
        val orderedMatches = fileMatches.sortedBy { it.rel }
        val sb = StringBuilder()
        var total = 0
        var fileHits = 0
        var shown = 0
        for (fm in orderedMatches) {
            fileHits++
            total += fm.matchIdx.size
            val emitted = mutableSetOf<Int>()
            for (mi in fm.matchIdx) {
                if (shown >= cap) break
                val from = (mi - ctx).coerceAtLeast(0)
                val to = (mi + ctx).coerceAtMost(fm.lines.lastIndex)
                if (ctx > 0 && shown > 0) sb.append("--\n")
                for (li in from..to) {
                    if (li in emitted && ctx > 0) continue
                    emitted.add(li)
                    val mark = if (li == mi) ":" else "-"
                    val lineText = fm.lines[li].take(200)
                    if (li == mi) {
                        if (shown >= cap) break
                        shown++
                    }
                    if (li == mi || ctx > 0) {
                        sb.append("${fm.rel}:${li + 1}$mark $lineText\n")
                    }
                }
            }
            if (shown >= cap) break
        }

        return if (total == 0) "No matches for '$label'" +
            (if (pathScope.isNotBlank()) " in $pathScope" else "") +
            (if (glob.isNotBlank()) " (glob $glob)" else "")
        else "Found $total match(es) in $fileHits file(s)" +
            (if (total > shown) " — showing first $shown" else "") +
            (if (ctx > 0) " with ±$ctx context" else "") + ":\n" + sb.toString().trim()
    }

    /**
     * List text file relative paths under optional [pathScope] and [glob]
     * (simple glob: star.kt, recursive Screen.kt, src recursive xml — avoid star-slash in KDoc).
     */
    fun listTextFiles(projectDir: String, pathScope: String = "", glob: String = ""): List<String> {
        val root = File(projectDir)
        if (!root.isDirectory) return emptyList()

        val scopeClean = cleanRelPath(pathScope)
        val globTrim = glob.trim()
        val now = System.currentTimeMillis()
        textListCache?.let { c ->
            if (c.projectDir == projectDir && c.scope == scopeClean && c.glob == globTrim &&
                now - c.atMs < TEXT_LIST_CACHE_TTL_MS
            ) return c.files
        }

        val scopeFile = if (scopeClean.isNotBlank()) {
            val f = File(projectDir, scopeClean)
            if (!isInsideProject(projectDir, f)) return emptyList()
            f
        } else root

        val out = mutableListOf<String>()
        fun walk(dir: File) {
            val entries = dir.listFiles() ?: return
            for (f in entries) {
                if (f.isDirectory) {
                    if (shouldSkipSearchDir(f.name)) continue
                    walk(f)
                } else {
                    if (!isTextFile(f.name)) continue
                    if (f.length() > SEARCH_MAX_FILE_BYTES) continue
                    val rel = relativePathOf(root, f)
                    if (globTrim.isNotBlank() && !matchGlob(rel, globTrim) && !matchGlob(f.name, globTrim)) continue
                    out.add(rel)
                }
            }
        }
        if (scopeFile.isFile) {
            if (isTextFile(scopeFile.name) && isInsideProject(projectDir, scopeFile)) {
                out.add(relativePathOf(root, scopeFile))
            }
        } else {
            walk(scopeFile)
        }
        textListCache = TextListCache(projectDir, scopeClean, globTrim, out, now)
        return out
    }

    /**
     * Minimal glob matcher: supports star, double-star, and question-mark.
     * Examples: star.kt, recursive Screen.kt, app/src recursive xml.
     */
    fun matchGlob(path: String, glob: String): Boolean {
        val g = glob.trim().replace('\\', '/')
        val p = path.replace('\\', '/')
        if (g.isEmpty()) return true
        // Convert glob to regex
        val sb = StringBuilder("^")
        var i = 0
        while (i < g.length) {
            when (val c = g[i]) {
                '*' -> {
                    if (i + 1 < g.length && g[i + 1] == '*') {
                        sb.append(".*"); i++ // **
                    } else {
                        sb.append("[^/]*")
                    }
                }
                '?' -> sb.append("[^/]")
                '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' -> {
                    sb.append('\\').append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        sb.append('$')
        return try {
            Regex(sb.toString(), RegexOption.IGNORE_CASE).matches(p)
        } catch (_: Exception) {
            p.endsWith(g.removePrefix("*"))
        }
    }

    /**
     * Find files by NAME / PATH — exact + smart fuzzy so the agent finds files in 1 call
     * instead of 20 greps. Accepts:
     *  - Globs: double-star name globs (e.g. any ChatScreen), `*.kt`, app/src xml trees
     *  - Bare names: `ChatScreen` / `ChatScreen.kt` → auto double-star name glob
     *  - Path fragments: `screens/ChatScreen` → path suffix / contains rank
     *  - Natural tokens: `chat screen compose` → multi-token path scoring
     * Returns full project-relative paths (use these for READ/EDIT).
     */
    fun findFiles(projectDir: String, pattern: String, pathScope: String = "", maxResults: Int = 100): String {
        val root = File(projectDir)
        if (!root.isDirectory) return "ERROR: project not found."
        val raw = pattern.trim().trim('"', '\'', '`')
        if (raw.isBlank()) return "ERROR: empty pattern — e.g. ChatScreen.kt or **/*Screen*.kt"
        val scopeClean = cleanRelPath(pathScope)
        val startDir = if (scopeClean.isNotBlank()) {
            val f = File(projectDir, scopeClean)
            if (!isInsideProject(projectDir, f)) return "ERROR: path outside project sandbox."
            if (f.isFile) {
                val rel = relativePathOf(root, f)
                return "Found 1 file (scope is a file):\n$rel"
            }
            f
        } else root
        val cap = maxResults.coerceIn(1, 500)

        // Collect all relative paths under scope (cheap walk; path index may be stale after unzip)
        val all = mutableListOf<String>()
        fun walk(dir: File) {
            val entries = dir.listFiles() ?: return
            for (f in entries) {
                if (f.isDirectory) {
                    if (shouldSkipSearchDir(f.name)) continue
                    walk(f)
                } else {
                    all.add(relativePathOf(root, f))
                }
            }
        }
        walk(startDir)
        if (all.isEmpty()) return "No files under project" +
            (if (scopeClean.isNotBlank()) " path '$scopeClean'" else "") + "."

        val hasGlobMeta = raw.contains('*') || raw.contains('?')
        data class Ranked(val path: String, val score: Double)
        val ranked = mutableListOf<Ranked>()

        if (hasGlobMeta) {
            for (rel in all) {
                val base = rel.substringAfterLast('/')
                if (matchGlob(rel, raw) || matchGlob(base, raw)) {
                    ranked.add(Ranked(rel, 100.0))
                }
            }
        } else {
            // Smart exact search: no * needed
            val tokens = raw.lowercase()
                .split(Regex("[^a-z0-9_.]+"))
                .filter { it.length >= 2 }
                .distinct()
            val needle = raw.lowercase().replace('\\', '/')
            val needleBase = needle.substringAfterLast('/')
            val needleNorm = needleBase.replace(Regex("[\\s()\\[\\]-]+"), "")

            for (rel in all) {
                val lower = rel.lowercase()
                val base = lower.substringAfterLast('/')
                val baseNoExt = base.substringBeforeLast('.', base)
                val normBase = base.replace(Regex("[\\s()\\[\\]-]+"), "")
                var score = 0.0

                // Exact path / basename (highest)
                if (lower == needle || base == needleBase) score += 200.0
                else if (lower.endsWith("/$needle") || lower.endsWith(needle)) score += 150.0
                else if (base == needleBase || baseNoExt == needleBase.removeSuffix(".${base.substringAfterLast('.', "")}")) score += 120.0
                else if (base.startsWith(needleBase) || baseNoExt.equals(needleNorm, ignoreCase = true)) score += 90.0
                else if (base.contains(needleBase) && needleBase.length >= 3) score += 70.0
                else if (normBase.contains(needleNorm) && needleNorm.length >= 4) score += 55.0
                else if (lower.contains(needle) && needle.length >= 4) score += 45.0

                // Multi-token: all tokens must appear in path (order free)
                if (tokens.size >= 2) {
                    val hit = tokens.count { lower.contains(it) || base.contains(it) }
                    if (hit == tokens.size) score += 80.0 + hit * 5.0
                    else if (hit >= (tokens.size + 1) / 2) score += hit * 12.0
                } else if (tokens.size == 1) {
                    val t = tokens[0]
                    if (base.contains(t)) score += 40.0
                    else if (lower.contains(t)) score += 25.0
                }

                // Prefer source files over build noise
                if (score > 0) {
                    if (lower.contains("/src/") || lower.contains("/main/") || lower.contains("/java/") ||
                        lower.contains("/kotlin/")
                    ) score += 8.0
                    if (lower.contains("/build/") || lower.contains("/.gradle/") || lower.contains("/generated/"))
                        score -= 30.0
                    ranked.add(Ranked(rel, score))
                }
            }
        }

        val top = ranked.sortedByDescending { it.score }.take(cap)
        if (top.isEmpty()) {
            // Helpful near-misses: top basenames sharing a prefix
            val hintTok = raw.lowercase().filter { it.isLetterOrDigit() }.take(6)
            val near = if (hintTok.length >= 3) {
                all.filter {
                    val b = it.substringAfterLast('/').lowercase()
                    b.contains(hintTok) || hintTok.contains(b.take(4))
                }.take(12)
            } else emptyList()
            return buildString {
                append("No files match '$raw'")
                if (scopeClean.isNotBlank()) append(" under '$scopeClean'")
                append(".\n")
                if (near.isNotEmpty()) {
                    append("Closest basenames (use exact path for READ_FILE):\n")
                    near.forEach { append("  $it\n") }
                } else {
                    append("Try: FIND_FILES `**/*PartOfName*` or GREP with path=app/src\n")
                }
            }.trim()
        }

        val best = top.first().score
        val strong = top.filter { it.score >= best * 0.55 || it.score >= 70.0 }.ifEmpty { top.take(15) }
        return buildString {
            append("Found ${strong.size} file(s) for '$raw'")
            if (scopeClean.isNotBlank()) append(" under '$scopeClean'")
            append(" (full project-relative paths — use EXACTLY for READ/EDIT):\n")
            strong.forEachIndexed { i, r ->
                append("  ${i + 1}. ${r.path}")
                if (i == 0 && strong.size > 1) append("  ← best")
                append('\n')
            }
            if (top.size > strong.size) append("  … ${top.size - strong.size} weaker match(es) omitted\n")
        }.trim()
    }

    private val CODE_EXTS = setOf(
        "kt", "java", "js", "jsx", "ts", "tsx", "py", "go", "rb", "rs",
        "swift", "c", "cc", "cpp", "h", "hpp", "cs", "php", "dart", "scala"
    )

    /**
     * Extract the top declarations (classes / interfaces / objects / functions) from one source
     * file, as compact signature lines — language-aware. Powers the Aider-style repo map.
     */
    private fun extractDecls(content: String, ext: String): List<String> {
        val out = LinkedHashSet<String>()
        val patterns: List<Regex> = when (ext) {
            "kt" -> listOf(
                Regex("""(?m)^\s*(?:(?:public|internal|private|protected|open|abstract|sealed|data|final|inner|enum|annotation)\s+)*(?:class|interface|object)\s+\w[\w<>., ]*"""),
                Regex("""(?m)^\s*(?:(?:public|internal|private|protected|open|override|suspend|inline|operator|infix|tailrec)\s+)*fun\s+[\w<>.]+\s*\([^)]*\)\s*(?::\s*[\w<>?., ]+)?""")
            )
            "java", "cs", "scala" -> listOf(
                Regex("""(?m)^\s*(?:(?:public|private|protected|static|final|abstract|sealed|internal)\s+)*(?:class|interface|enum)\s+\w+"""),
                Regex("""(?m)^\s*(?:(?:public|private|protected|static|final|abstract|synchronized|override|virtual)\s+)+[\w<>\[\]]+\s+\w+\s*\([^)]*\)""")
            )
            "js", "jsx", "ts", "tsx" -> listOf(
                Regex("""(?m)^\s*(?:export\s+)?(?:default\s+)?(?:async\s+)?function\s+\w+\s*\([^)]*\)"""),
                Regex("""(?m)^\s*(?:export\s+)?(?:abstract\s+)?class\s+\w+(?:\s+extends\s+\w+)?"""),
                Regex("""(?m)^\s*(?:export\s+)?(?:const|let)\s+\w+\s*(?::[^=]+)?=\s*(?:async\s*)?\([^)]*\)\s*=>"""),
                Regex("""(?m)^\s*(?:export\s+)?(?:interface|type|enum)\s+\w+""")
            )
            "py" -> listOf(
                Regex("""(?m)^\s*(?:async\s+)?def\s+\w+\s*\([^)]*\)"""),
                Regex("""(?m)^\s*class\s+\w+\s*(?:\([^)]*\))?""")
            )
            "go" -> listOf(
                Regex("""(?m)^\s*func\s+(?:\([^)]*\)\s*)?\w+\s*\([^)]*\)"""),
                Regex("""(?m)^\s*type\s+\w+\s+(?:struct|interface)""")
            )
            "rs" -> listOf(
                Regex("""(?m)^\s*(?:pub\s+)?(?:async\s+)?fn\s+\w+\s*(?:<[^>]*>)?\s*\([^)]*\)"""),
                Regex("""(?m)^\s*(?:pub\s+)?(?:struct|enum|trait|impl)\s+\w+""")
            )
            "swift" -> listOf(
                Regex("""(?m)^\s*(?:(?:public|private|internal|open|final|static|override)\s+)*func\s+\w+\s*\([^)]*\)"""),
                Regex("""(?m)^\s*(?:(?:public|private|internal|open|final)\s+)*(?:class|struct|enum|protocol|extension)\s+\w+""")
            )
            else -> listOf(
                Regex("""(?m)^\s*(?:public\s+)?(?:function|func|def|class|interface|struct|type)\s+\w+""")
            )
        }
        for (p in patterns) {
            for (m in p.findAll(content)) {
                var sig = m.value.trim().substringBefore("{").trim().trimEnd('=', ':').trim()
                sig = sig.replace(Regex("\\s+"), " ")
                if (sig.length in 3..150) out.add(sig)
                if (out.size >= 40) break
            }
        }
        return out.toList()
    }

    /**
     * Aider-style REPO MAP: a compact, ranked map of the project's key declarations (classes,
     * functions, interfaces) with signatures — so the agent grasps the codebase's shape without
     * reading every file. Files are ranked by declaration count (a proxy for importance) and the
     * whole map is capped so it never floods the context window.
     */
    fun buildRepoMap(projectDir: String, maxDecls: Int = 160, pathScope: String = ""): String {
        val root = File(projectDir)
        if (!root.isDirectory) return "ERROR: project not found."
        val files = listTextFiles(projectDir, pathScope, "")
            .filter { it.substringAfterLast('.', "").lowercase() in CODE_EXTS }
        if (files.isEmpty()) return "No source files to map."
        val perFile = ArrayList<Pair<String, List<String>>>()
        for (rel in files) {
            try {
                val f = File(projectDir, rel)
                if (f.length() > 400_000) continue
                val decls = extractDecls(f.readText(), rel.substringAfterLast('.', "").lowercase())
                if (decls.isNotEmpty()) perFile.add(rel to decls)
            } catch (_: Exception) {}
        }
        if (perFile.isEmpty()) return "No declarations found in ${files.size} source file(s)."
        perFile.sortWith(compareByDescending<Pair<String, List<String>>> { it.second.size }.thenBy { it.first })
        val sb = StringBuilder()
        var used = 0
        for ((rel, decls) in perFile) {
            if (used >= maxDecls) { sb.append("… (more files not shown — use REPO_MAP with a path scope)\n"); break }
            sb.append(rel).append(":\n")
            val take = decls.take((maxDecls - used).coerceAtMost(12))
            for (d in take) { sb.append("  ").append(d).append("\n"); used++ }
            if (decls.size > take.size) sb.append("  … (+${decls.size - take.size} more)\n")
        }
        return sb.toString().trimEnd()
    }

    /**
     * Devin-DeepWiki-style codebase overview generated ENTIRELY on-device (no LLM): languages +
     * file/line counts, detected frameworks, folder breakdown, key files (by declaration density)
     * and entry points. Returns Markdown; the caller may persist it as PROJECT_WIKI.md.
     */
    fun buildCodebaseWiki(projectDir: String): String {
        val root = File(projectDir)
        if (!root.isDirectory) return "ERROR: project not found."
        val allFiles = listTextFiles(projectDir, "", "")
        // Language tally (by extension) with line counts.
        val langLines = LinkedHashMap<String, Int>()
        val langFiles = LinkedHashMap<String, Int>()
        var totalLines = 0
        for (rel in allFiles) {
            val ext = rel.substringAfterLast('.', "").lowercase().ifBlank { "other" }
            if (ext !in CODE_EXTS && ext !in setOf("xml", "json", "gradle", "kts", "md", "yaml", "yml", "html", "css")) continue
            val lc = try { File(projectDir, rel).readLines().size } catch (_: Exception) { 0 }
            langLines[ext] = (langLines[ext] ?: 0) + lc
            langFiles[ext] = (langFiles[ext] ?: 0) + 1
            totalLines += lc
        }
        // Framework / stack detection from marker files.
        val names = allFiles.map { it.substringAfterLast('/').lowercase() }.toHashSet()
        val stack = mutableListOf<String>()
        fun any(vararg n: String) = n.any { it in names }
        if (any("androidmanifest.xml") || any("build.gradle", "build.gradle.kts")) stack.add("Android (Gradle)")
        if (allFiles.any { it.endsWith(".kt") } && allFiles.any { it.contains("compose", true) }) stack.add("Jetpack Compose")
        if (any("package.json")) stack.add("Node/JS")
        if (any("next.config.js", "next.config.mjs")) stack.add("Next.js")
        if (any("requirements.txt", "pyproject.toml", "setup.py")) stack.add("Python")
        if (any("cargo.toml")) stack.add("Rust/Cargo")
        if (any("go.mod")) stack.add("Go")
        if (any("pubspec.yaml")) stack.add("Flutter/Dart")
        // Top-level folder breakdown (file counts).
        val folderCounts = LinkedHashMap<String, Int>()
        for (rel in allFiles) {
            val top = rel.substringBefore('/', "(root)")
            folderCounts[top] = (folderCounts[top] ?: 0) + 1
        }
        // Key files by declaration density (reuse repo-map extraction).
        val keyFiles = allFiles
            .filter { it.substringAfterLast('.', "").lowercase() in CODE_EXTS }
            .mapNotNull { rel ->
                try {
                    val f = File(projectDir, rel)
                    if (f.length() > 400_000) null
                    else extractDecls(f.readText(), rel.substringAfterLast('.', "").lowercase()).size
                        .takeIf { it > 0 }?.let { rel to it }
                } catch (_: Exception) { null }
            }
            .sortedByDescending { it.second }
            .take(12)
        // Entry points.
        val entryPoints = allFiles.filter {
            val n = it.substringAfterLast('/').lowercase()
            n == "mainactivity.kt" || n == "main.kt" || n == "main.py" || n == "index.js" ||
                n == "index.ts" || n == "app.tsx" || n == "main.go" || n == "androidmanifest.xml" ||
                n == "main.dart"
        }.take(8)

        val sb = StringBuilder()
        sb.append("# Project Wiki\n\n")
        sb.append("_Auto-generated overview • ${allFiles.size} tracked files • ~$totalLines lines_\n\n")
        if (stack.isNotEmpty()) sb.append("## Stack\n").append(stack.joinToString(", ")).append("\n\n")
        sb.append("## Languages\n")
        langFiles.entries.sortedByDescending { langLines[it.key] ?: 0 }.take(12).forEach { (ext, fc) ->
            sb.append("- **.$ext** — $fc file(s), ${langLines[ext] ?: 0} lines\n")
        }
        sb.append("\n## Structure (top-level)\n")
        folderCounts.entries.sortedByDescending { it.value }.take(16).forEach { (folder, c) ->
            sb.append("- `$folder/` — $c file(s)\n")
        }
        if (entryPoints.isNotEmpty()) {
            sb.append("\n## Entry points\n")
            entryPoints.forEach { sb.append("- `$it`\n") }
        }
        if (keyFiles.isNotEmpty()) {
            sb.append("\n## Key files (by declaration density)\n")
            keyFiles.forEach { (rel, n) -> sb.append("- `$rel` — $n declarations\n") }
        }
        sb.append("\n_Tip: use REPO_MAP for signatures, FIND_FILES to locate, GREP to search._\n")
        return sb.toString()
    }

    fun deleteProject(projectDir: String) {
        try {
            val proj = File(projectDir)
            val key = proj.name
            // Drop offline agent transcript(s) for this workspace key
            try {
                val filesDir = proj.parentFile?.parentFile // .../files/projects_<uid> → files
                if (filesDir != null) {
                    filesDir.listFiles()
                        ?.filter { it.isDirectory && it.name.startsWith("agent_sessions") }
                        ?.forEach { File(it, "$key.json").takeIf { f -> f.exists() }?.delete() }
                }
            } catch (_: Exception) {}
            // Delete the project folder (or its projects_* parent wrap)
            var dir = proj
            while (dir.parentFile != null &&
                dir.parentFile?.name != "projects" &&
                dir.parentFile?.name?.startsWith("projects_") != true
            ) {
                dir = dir.parentFile!!
            }
            dir.deleteRecursively()
        } catch (_: Exception) {}
    }

    /**
     * Export a workspace (project files + agent chat transcript) to
     * Downloads/AhamAI as an offline history zip the user can re-import later.
     */
    fun exportWorkspaceHistory(context: Context, projectDir: String): String {
        return try {
            val dir = File(projectDir)
            if (!dir.isDirectory) return "ERROR: Workspace not found"
            val name = displayNameFor(dir)
            val safe = name.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(40).ifBlank { "workspace" }
            val bytes = buildWorkspaceHistoryZip(context, dir)
                ?: return "ERROR: Nothing to export (empty workspace)"
            if (bytes.size < 32) return "ERROR: Export too small — refused"
            // Unique name avoids MediaStore "duplicate" failures on re-export
            val fileName = "workspace_${safe}_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}.zip"
            DeviceStorage.saveToAhamAIFolder(context, bytes, fileName, "application/zip")
        } catch (e: Exception) {
            "ERROR: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Export **all** local workspaces (every project’s files + agent transcripts)
     * into one offline backup zip. Use before uninstall so the user can re-import everything.
     */
    fun exportAllWorkspaceHistory(context: Context): String {
        return try {
            val projects = listProjects(context)
            if (projects.isEmpty()) return "ERROR: No workspaces to export"
            val bos = java.io.ByteArrayOutputStream()
            var totalFiles = 0
            var totalWorkspaces = 0
            val items = org.json.JSONArray()
            val usedKeys = HashSet<String>()
            ZipOutputStream(bos).use { zos ->
                // Dedup helper — ZipOutputStream throws on duplicate entry names
                val usedEntries = HashSet<String>()
                fun putFile(entryName: String, write: (ZipOutputStream) -> Unit): Boolean {
                    val name = entryName.trimStart('/').replace('\\', '/')
                    if (name.isBlank() || !usedEntries.add(name)) return false
                    return try {
                        zos.putNextEntry(ZipEntry(name))
                        write(zos)
                        zos.closeEntry()
                        true
                    } catch (_: Exception) { false }
                }

                for ((idx, p) in projects.withIndex()) {
                    val dir = File(p.path)
                    if (!dir.isDirectory) continue
                    val rawKey = dir.name.ifBlank { "ws_${System.currentTimeMillis()}_$idx" }
                    var safeKey = rawKey.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
                        .ifBlank { "ws_$idx" }
                    // Unique keys if two folders normalize to the same name
                    if (!usedKeys.add(safeKey)) {
                        safeKey = "${safeKey}_$idx"
                        usedKeys.add(safeKey)
                    }
                    val name = displayNameFor(dir)
                    items.put(org.json.JSONObject().put("key", safeKey).put("name", name))
                    totalWorkspaces++

                    val meta = org.json.JSONObject()
                        .put("v", 1)
                        .put("name", name)
                        .put("exportedAt", System.currentTimeMillis())
                        .put("pathKey", safeKey)
                        .toString()
                    putFile("workspaces/$safeKey/$WORKSPACE_META_ENTRY") { z ->
                        z.write(meta.toByteArray(Charsets.UTF_8))
                    }

                    fun addDir(d: File) {
                        val entries = d.listFiles() ?: return
                        for (f in entries) {
                            if (f.isDirectory) {
                                if (f.name in SKIP_DIRS_ZIP) continue
                                addDir(f)
                            } else {
                                val ext = f.extension.lowercase()
                                if (ext in setOf("apk", "aab", "so", "dex", "class", "zip")) continue
                                // Skip internal metadata (title is still included via .ahamai-title below once)
                                if (f.name.startsWith(".ahamai") && f.name != ".ahamai-title") continue
                                if (f.length() > 30_000_000L) continue
                                val rel = relativePathOf(dir, f)
                                if (putFile("workspaces/$safeKey/$rel") { z ->
                                        f.inputStream().use { it.copyTo(z) }
                                    }) totalFiles++
                            }
                        }
                    }
                    addDir(dir)

                    val uid = activeUid()
                    // Prefer real dir name for transcript (matches agent sessions)
                    val transcript = File(agentSessionsDirFor(context, uid), "${dir.name}.json")
                    if (transcript.exists() && transcript.length() < 8_000_000L) {
                        putFile("workspaces/$safeKey/$TRANSCRIPT_ENTRY") { z ->
                            transcript.inputStream().use { it.copyTo(z) }
                        }
                    }
                }

                val full = org.json.JSONObject()
                    .put("v", 1)
                    .put("type", "ahamai_all_workspaces")
                    .put("exportedAt", System.currentTimeMillis())
                    .put("count", items.length())
                    .put("items", items)
                    .put("files", totalFiles)
                    .toString()
                putFile(ALL_WORKSPACES_MANIFEST) { z ->
                    z.write(full.toByteArray(Charsets.UTF_8))
                }
                putFile("manifest_full.json") { z ->
                    z.write(full.toByteArray(Charsets.UTF_8))
                }
            }
            // At least one workspace meta is enough (empty projects + transcripts still valid)
            if (totalWorkspaces == 0) return "ERROR: Nothing to export"
            val bytes = bos.toByteArray()
            if (bytes.size < 32) return "ERROR: Export too small — refused"
            val fileName = "ahamai_all_workspaces_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}.zip"
            DeviceStorage.saveToAhamAIFolder(context, bytes, fileName, "application/zip")
        } catch (e: Exception) {
            "ERROR: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    /**
     * Import a previously exported workspace history zip (single or **all-workspaces** backup).
     * Restores files + agent transcript when present. Returns the first restored project path, or null.
     * For multi-workspace zips, restores every workspace under [workspaces/].
     */
    fun importWorkspaceHistory(context: Context, uri: Uri): String? {
        return try {
            // Peek: is this an all-workspaces backup?
            val isAll = peekIsAllWorkspacesZip(context, uri)
            if (isAll) {
                val restored = importAllWorkspaceHistory(context, uri)
                return restored.firstOrNull()
            }
            importSingleWorkspaceHistory(context, uri)
        } catch (_: Exception) { null }
    }

    /** How many workspaces were restored by the last multi-import (UI toast helper). */
    @Volatile var lastImportCount: Int = 0
        private set

    private fun peekIsAllWorkspacesZip(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var e: ZipEntry? = zis.nextEntry
                    while (e != null) {
                        val n = e.name
                        if (n == ALL_WORKSPACES_MANIFEST || n == "manifest_full.json" ||
                            n.startsWith("workspaces/")
                        ) {
                            if (n == ALL_WORKSPACES_MANIFEST || n == "manifest_full.json") {
                                val raw = zis.readBytes().toString(Charsets.UTF_8)
                                if (raw.contains("ahamai_all_workspaces")) return true
                            }
                            if (n.startsWith("workspaces/")) return true
                        }
                        zis.closeEntry(); e = zis.nextEntry
                    }
                }
            }
            false
        } catch (_: Exception) { false }
    }

    private fun importAllWorkspaceHistory(context: Context, uri: Uri): List<String> {
        val uid = activeUid()
        val restored = mutableListOf<String>()
        lastImportCount = 0
        // Map: workspace key -> temp dir we extract into
        val buckets = LinkedHashMap<String, File>()
        val names = HashMap<String, String>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val n = entry.name
                        if (n.startsWith("workspaces/") && !n.contains("..") && !entry.isDirectory) {
                            val rest = n.removePrefix("workspaces/")
                            val key = rest.substringBefore('/')
                            val inner = rest.substringAfter('/', "")
                            if (key.isNotBlank() && inner.isNotBlank()) {
                                val bucket = buckets.getOrPut(key) {
                                    File(context.cacheDir, "ws_import_${System.currentTimeMillis()}_$key")
                                        .apply { mkdirs() }
                                }
                                when {
                                    inner == WORKSPACE_META_ENTRY -> {
                                        val raw = zis.readBytes().toString(Charsets.UTF_8)
                                        try {
                                            names[key] = org.json.JSONObject(raw).optString("name")
                                        } catch (_: Exception) {}
                                    }
                                    inner == TRANSCRIPT_ENTRY -> {
                                        // stash transcript next to bucket for later move
                                        File(bucket, TRANSCRIPT_ENTRY).outputStream().use { zis.copyTo(it) }
                                    }
                                    else -> {
                                        val out = File(bucket, inner)
                                        if (out.canonicalPath.startsWith(bucket.canonicalPath)) {
                                            out.parentFile?.mkdirs()
                                            FileOutputStream(out).use { zis.copyTo(it) }
                                        }
                                    }
                                }
                            }
                        }
                        zis.closeEntry(); entry = zis.nextEntry
                    }
                }
            }
            for ((key, bucket) in buckets) {
                val pathKey = "scratch_${System.currentTimeMillis()}_${key.take(24)}"
                val projectDir = File(projectsRoot(context), pathKey).apply { mkdirs() }
                // Move files (skip transcript marker)
                bucket.walkTopDown().forEach { f ->
                    if (!f.isFile) return@forEach
                    if (f.name == TRANSCRIPT_ENTRY) return@forEach
                    val rel = f.absolutePath.removePrefix(bucket.absolutePath).removePrefix("/")
                    if (rel.isBlank()) return@forEach
                    val dest = File(projectDir, rel)
                    dest.parentFile?.mkdirs()
                    f.copyTo(dest, overwrite = true)
                }
                val tFile = File(bucket, TRANSCRIPT_ENTRY)
                if (tFile.exists()) {
                    File(agentSessionsDirFor(context, uid), "$pathKey.json")
                        .outputStream().use { out -> tFile.inputStream().use { it.copyTo(out) } }
                }
                val title = names[key]?.takeIf { it.isNotBlank() }
                    ?: displayNameFor(projectDir)
                setSessionTitle(projectDir.absolutePath, title)
                restored.add(projectDir.absolutePath)
                bucket.deleteRecursively()
            }
        } catch (_: Exception) {}
        lastImportCount = restored.size
        return restored
    }

    private fun importSingleWorkspaceHistory(context: Context, uri: Uri): String? {
        return try {
            val uid = activeUid()
            val pathKey = "scratch_${System.currentTimeMillis()}_import"
            val projectDir = File(projectsRoot(context), pathKey).apply { mkdirs() }
            var displayName: String? = null
            var fileCount = 0
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val n = entry.name
                        if (n.isNotBlank() && !n.contains("..") && !n.startsWith("__MACOSX")) {
                            when {
                                n == WORKSPACE_META_ENTRY || n.endsWith("/$WORKSPACE_META_ENTRY") -> {
                                    val raw = zis.readBytes().toString(Charsets.UTF_8)
                                    displayName = try {
                                        org.json.JSONObject(raw).optString("name").takeIf { it.isNotBlank() }
                                    } catch (_: Exception) { null }
                                }
                                n == TRANSCRIPT_ENTRY || n.endsWith("/$TRANSCRIPT_ENTRY") -> {
                                    File(agentSessionsDirFor(context, uid), "$pathKey.json")
                                        .outputStream().use { zis.copyTo(it) }
                                }
                                n == ALL_WORKSPACES_MANIFEST || n == "manifest_full.json" -> {
                                    zis.readBytes() // skip
                                }
                                else -> {
                                    val outFile = File(projectDir, n)
                                    if (outFile.canonicalPath.startsWith(projectDir.canonicalPath)) {
                                        if (entry.isDirectory) outFile.mkdirs()
                                        else {
                                            outFile.parentFile?.mkdirs()
                                            FileOutputStream(outFile).use { zis.copyTo(it) }
                                            fileCount++
                                        }
                                    }
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            } ?: return null
            flattenSingleRootIfNeeded(projectDir)
            val title = displayName ?: run {
                val kids = projectDir.listFiles()?.filter { !it.name.startsWith(".") }
                if (kids?.size == 1 && kids[0].isDirectory) kids[0].name else "Imported"
            }
            setSessionTitle(projectDir.absolutePath, title.replaceFirstChar { it.uppercase() })
            if (fileCount == 0 && !File(agentSessionsDirFor(context, uid), "$pathKey.json").exists()) {
                projectDir.deleteRecursively()
                return null
            }
            lastImportCount = 1
            projectDir.absolutePath
        } catch (_: Exception) { null }
    }

    private const val WORKSPACE_META_ENTRY = "__ahamai_workspace_meta.json"
    private const val ALL_WORKSPACES_MANIFEST = "ahamai_all_workspaces.json"

    /** Build offline history zip: meta + project files + agent transcript. No 8MB cloud cap. */
    private fun buildWorkspaceHistoryZip(context: Context, dir: File): ByteArray? {
        return try {
            if (!dir.isDirectory) return null
            val uid = activeUid()
            val bos = java.io.ByteArrayOutputStream()
            var count = 0
            var hasTranscript = false
            ZipOutputStream(bos).use { zos ->
                val used = HashSet<String>()
                fun putOnce(entryName: String, write: (ZipOutputStream) -> Unit): Boolean {
                    val n = entryName.trimStart('/').replace('\\', '/')
                    if (n.isBlank() || !used.add(n)) return false
                    return try {
                        zos.putNextEntry(ZipEntry(n))
                        write(zos)
                        zos.closeEntry()
                        true
                    } catch (_: Exception) { false }
                }

                val meta = org.json.JSONObject()
                    .put("v", 1)
                    .put("name", displayNameFor(dir))
                    .put("exportedAt", System.currentTimeMillis())
                    .put("pathKey", dir.name)
                    .toString()
                putOnce(WORKSPACE_META_ENTRY) { z ->
                    z.write(meta.toByteArray(Charsets.UTF_8))
                }

                fun addDir(d: File) {
                    val entries = d.listFiles() ?: return
                    for (f in entries) {
                        if (f.isDirectory) {
                            if (f.name in SKIP_DIRS_ZIP) continue
                            addDir(f)
                        } else {
                            val ext = f.extension.lowercase()
                            if (ext in setOf("apk", "aab", "so", "dex", "class", "zip")) continue
                            if (f.name.startsWith(".ahamai") && f.name != ".ahamai-title") continue
                            if (f.length() > 30_000_000L) continue
                            if (putOnce(relativePathOf(dir, f)) { z ->
                                    f.inputStream().use { it.copyTo(z) }
                                }) count++
                        }
                    }
                }
                addDir(dir)
                // .ahamai-title already added once in addDir — do NOT add again (was ZipException: duplicate)

                val transcript = File(agentSessionsDirFor(context, uid), "${dir.name}.json")
                if (transcript.exists() && transcript.length() < 8_000_000L) {
                    if (putOnce(TRANSCRIPT_ENTRY) { z ->
                            transcript.inputStream().use { it.copyTo(z) }
                        }) hasTranscript = true
                }
            }
            // Meta-only workspaces (no files yet) still export so reinstall can restore the shell
            if (count == 0 && !hasTranscript) {
                // keep meta-only zip — user can re-import the named empty session
            }
            bos.toByteArray()
        } catch (_: Exception) { null }
    }

    /** If zip extracted as a single top-level folder, move children up (skip our meta/transcript). */
    private fun flattenSingleRootIfNeeded(projectDir: File) {
        try {
            val kids = projectDir.listFiles()?.filter {
                it.name != WORKSPACE_META_ENTRY && it.name != TRANSCRIPT_ENTRY
            } ?: return
            if (kids.size != 1 || !kids[0].isDirectory) return
            val root = kids[0]
            root.listFiles()?.forEach { child ->
                val dest = File(projectDir, child.name)
                if (!dest.exists()) child.renameTo(dest) else {
                    // rare name clash — leave nested
                }
            }
            if (root.listFiles().isNullOrEmpty()) root.delete()
        } catch (_: Exception) {}
    }

    /** Human-friendly relative time for workspace history rows. */
    fun formatWorkspaceAge(lastModified: Long): String {
        val d = System.currentTimeMillis() - lastModified
        if (d < 0) return "just now"
        val sec = d / 1000
        return when {
            sec < 60 -> "just now"
            sec < 3600 -> "${sec / 60}m ago"
            sec < 86_400 -> "${sec / 3600}h ago"
            sec < 86_400 * 7 -> "${sec / 86_400}d ago"
            else -> {
                val df = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                df.format(java.util.Date(lastModified))
            }
        }
    }

    /**
     * Makes a raw HTTP request and reports status, content-type, body size and a snippet.
     * Designed for the agent to TEST and VERIFY endpoints/APIs (GET/POST/etc), including
     * binary responses (images) and JSON APIs. Headers are "Key: Value" lines.
     */
    fun httpRequest(method: String, url: String, body: String?, headersRaw: String?): String {
        return try {
            val b = okhttp3.Request.Builder().url(url.trim())
            var hasUA = false
            headersRaw?.lines()?.forEach { line ->
                val idx = line.indexOf(':')
                if (idx > 0) {
                    val k = line.substring(0, idx).trim()
                    val v = line.substring(idx + 1).trim()
                    if (k.isNotBlank()) {
                        b.addHeader(k, v)
                        if (k.equals("User-Agent", true)) hasUA = true
                    }
                }
            }
            if (!hasUA) b.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            val m = method.trim().uppercase().ifBlank { "GET" }
            val reqBody = (body ?: "")
            val mediaType = (if (reqBody.trimStart().startsWith("{") || reqBody.trimStart().startsWith("["))
                "application/json" else "text/plain").toMediaTypeOrNull()
            when (m) {
                "GET" -> b.get()
                "DELETE" -> if (reqBody.isBlank()) b.delete() else b.delete(reqBody.toRequestBody(mediaType))
                "HEAD" -> b.head()
                "POST" -> b.post(reqBody.toRequestBody(mediaType))
                "PUT" -> b.put(reqBody.toRequestBody(mediaType))
                "PATCH" -> b.patch(reqBody.toRequestBody(mediaType))
                else -> b.method(m, if (reqBody.isBlank()) null else reqBody.toRequestBody(mediaType))
            }
            httpClient.newCall(b.build()).execute().use { resp ->
                val ct = resp.header("Content-Type") ?: ""
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                val isText = ct.startsWith("text") || ct.contains("json") || ct.contains("xml") ||
                        ct.contains("javascript") || ct.contains("html") || ct.isBlank()
                val snippet = if (isText) String(bytes).take(4000)
                else "[binary data: ${bytes.size} bytes, type=${ct.ifBlank { "unknown" }}]"
                "HTTP ${resp.code} ${resp.message}\nContent-Type: ${ct.ifBlank { "(none)" }}\nSize: ${bytes.size} bytes\n--- body ---\n$snippet"
            }
        } catch (e: Exception) {
            "ERROR: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    fun countFiles(projectDir: String): Int =
        listFiles(projectDir).count { !it.isDirectory }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Cloud backup / restore of workspaces (content + agent transcript).
    //
    //  Workspace files live in app-private storage (filesDir/projects_<uid>) which Android wipes
    //  on uninstall, and the agent transcript lives in filesDir/agent_sessions_<uid>. To make
    //  workspaces survive uninstall/reinstall and a new sign-in — like chat history — AuthManager
    //  mirrors each of the CURRENT user's workspaces to the cloud and restores anything missing
    //  locally on sign-in. Two free backends, chosen per workspace by size:
    //    • SMALL  → Firestore, the archive base64'd and chunked across docs (free Spark plan).
    //    • LARGE  → a private GitHub "ahamai-workspaces" repo (raw files under ws/<pathKey>/),
    //               used only when the user has connected GitHub. This clears Firestore's 1 MiB/doc
    //               limit for big projects without any paid plan.
    //  This object provides the (de)serialisation primitives; AuthManager does the network I/O.
    // ─────────────────────────────────────────────────────────────────────────────

    private const val WS_BACKUP_PER_FILE_CAP = 2_000_000L    // skip individual files larger than this in a backup
    private const val WS_BACKUP_TRANSCRIPT_CAP = 2_000_000L   // skip transcripts larger than this
    private const val WS_MAX_ZIP_BYTES = 8_000_000           // hard cap for the Firestore (base64+chunk) path

    /** A workspace of the current user eligible for backup. */
    data class WorkspaceMeta(val pathKey: String, val name: String, val lastModified: Long)

    private const val TRANSCRIPT_ENTRY = "__ahamai_session__.json"

    private fun activeUid(): String =
        AuthManager.uid()?.takeIf { it.isNotBlank() } ?: "guest"

    private fun agentSessionsDirFor(context: Context, uid: String): File =
        File(context.filesDir, "agent_sessions_$uid").apply { mkdirs() }

    /** True if [pathKey] is safe to use verbatim as a Firestore document-id fragment / repo path. */
    fun isValidPathKey(pathKey: String): Boolean =
        pathKey.isNotBlank() && pathKey.length <= 200 && pathKey.matches(Regex("[A-Za-z0-9_-]+"))

    /** pathKeys of every workspace present anywhere locally (across all uid roots). */
    fun localWorkspaceKeys(context: Context): Set<String> =
        allProjectRoots(context)
            .flatMap { r -> r.listFiles()?.filter { it.isDirectory }?.map { it.name } ?: emptyList() }
            .toSet()

    private fun displayNameFor(dir: File): String =
        getSessionTitle(dir.absolutePath)
            ?: dir.name.replace(Regex("^scratch_\\d+_"), "").replace('_', ' ').trim()
                .ifBlank { "Project" }.replaceFirstChar { it.uppercase() }

    /** True for files this backup should skip (heavy binaries, internal metadata, oversized). */
    private fun skipForBackup(f: File): Boolean {
        val ext = f.extension.lowercase()
        return ext in setOf("apk", "aab", "so", "dex", "class", "zip") ||
            f.name.startsWith(".ahamai") ||                 // internal metadata (title stored via .ahamai-title)
            f.length() > WS_BACKUP_PER_FILE_CAP
    }

    /** Every current-user workspace (newest first) with a valid key. */
    fun listExportableWorkspaces(context: Context): List<WorkspaceMeta> {
        return try {
            val root = File(context.filesDir, "projects_${activeUid()}")
            (root.listFiles()?.filter { it.isDirectory && isValidPathKey(it.name) } ?: emptyList())
                .sortedByDescending { it.lastModified() }
                .map { WorkspaceMeta(it.name, displayNameFor(it), it.lastModified()) }
        } catch (_: Exception) { emptyList() }
    }

    /** Total size of a workspace's backup-eligible content (cheap — no content reads). Drives the
     *  Firestore-vs-GitHub choice. Returns 0 for an empty workspace. */
    fun workspaceContentSize(context: Context, pathKey: String): Long {
        return try {
            if (!isValidPathKey(pathKey)) return 0
            val uid = activeUid()
            val dir = File(File(context.filesDir, "projects_$uid"), pathKey)
            if (!dir.isDirectory) return 0
            var total = 0L
            fun addDir(d: File) {
                for (f in d.listFiles() ?: return) {
                    if (f.isDirectory) { if (f.name !in SKIP_DIRS_ZIP) addDir(f) }
                    else if (!skipForBackup(f)) total += f.length()
                }
            }
            addDir(dir)
            val t = File(agentSessionsDirFor(context, uid), "$pathKey.json")
            if (t.exists() && t.length() < WS_BACKUP_TRANSCRIPT_CAP) total += t.length()
            total
        } catch (_: Exception) { 0 }
    }

    /** Base64 zip (content + transcript) for the Firestore path. null if empty or > [WS_MAX_ZIP_BYTES]. */
    fun workspacePayloadB64(context: Context, pathKey: String): String? {
        if (!isValidPathKey(pathKey)) return null
        val uid = activeUid()
        val dir = File(File(context.filesDir, "projects_$uid"), pathKey)
        val bytes = serializeWorkspace(context, dir, uid) ?: return null
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    /** Relative-path → bytes for the GitHub path (content + transcript under [TRANSCRIPT_ENTRY]).
     *  Reads file bytes into memory, so it's used only for the large-workspace GitHub backend. */
    fun workspaceFileMap(context: Context, pathKey: String): Map<String, ByteArray> {
        val map = LinkedHashMap<String, ByteArray>()
        try {
            if (!isValidPathKey(pathKey)) return map
            val uid = activeUid()
            val dir = File(File(context.filesDir, "projects_$uid"), pathKey)
            if (!dir.isDirectory) return map
            fun addDir(d: File) {
                for (f in d.listFiles() ?: return) {
                    if (f.isDirectory) { if (f.name !in SKIP_DIRS_ZIP) addDir(f) }
                    else if (!skipForBackup(f)) map[relativePathOf(dir, f)] = f.readBytes()
                }
            }
            addDir(dir)
            val t = File(agentSessionsDirFor(context, uid), "$pathKey.json")
            if (t.exists() && t.length() < WS_BACKUP_TRANSCRIPT_CAP) map[TRANSCRIPT_ENTRY] = t.readBytes()
        } catch (_: Exception) {}
        return map
    }

    /** Zips one workspace's content (text + small files) plus its agent transcript into memory.
     *  Returns null if the workspace has NO user content AND no transcript — i.e. truly empty.
     *  A workspace with only a transcript (no project files yet) IS backed up so the user's
     *  conversation with the agent survives an uninstall/reinstall. */
    private fun serializeWorkspace(context: Context, dir: File, uid: String): ByteArray? {
        return try {
            if (!dir.isDirectory) return null
            val bos = java.io.ByteArrayOutputStream()
            var count = 0
            var hasTranscript = false
            ZipOutputStream(bos).use { zos ->
                fun addDir(d: File) {
                    val entries = d.listFiles() ?: return
                    for (f in entries) {
                        if (f.isDirectory) {
                            if (f.name in SKIP_DIRS_ZIP) continue
                            addDir(f)
                        } else if (!skipForBackup(f)) {
                            zos.putNextEntry(ZipEntry(relativePathOf(dir, f)))
                            f.inputStream().use { it.copyTo(zos) }
                            zos.closeEntry()
                            count++
                        }
                    }
                }
                addDir(dir)
                // Fold the agent transcript in under a reserved name so the conversation/log restores too.
                val transcript = File(agentSessionsDirFor(context, uid), "${dir.name}.json")
                if (transcript.exists() && transcript.length() < WS_BACKUP_TRANSCRIPT_CAP) {
                    zos.putNextEntry(ZipEntry(TRANSCRIPT_ENTRY))
                    transcript.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    hasTranscript = true
                }
            }
            // Skip ONLY if there are no project files AND no transcript — i.e. truly empty.
            // Previously this returned null whenever count == 0, which silently dropped
            // workspaces whose only content was the agent conversation transcript — so after
            // an uninstall the workspace disappeared even though the user had been chatting.
            if (count == 0 && !hasTranscript) return null
            val bytes = bos.toByteArray()
            if (bytes.size > WS_MAX_ZIP_BYTES) null else bytes
        } catch (_: Exception) { null }
    }

    /** Recreates a single workspace from a backup [payloadB64] IF it isn't already present locally.
     *  Restores content, title and agent transcript. Never overwrites a live workspace. */
    fun restoreWorkspaceFromPayload(context: Context, pathKey: String, name: String, payloadB64: String) {
        try {
            if (!isValidPathKey(pathKey)) return
            if (pathKey in localWorkspaceKeys(context)) return
            val uid = activeUid()
            val activeRoot = File(context.filesDir, "projects_$uid").apply { mkdirs() }
            val restoredDir = File(activeRoot, pathKey)
            if (restoredDir.exists()) return
            restoredDir.mkdirs()
            val bytes = android.util.Base64.decode(payloadB64, android.util.Base64.NO_WRAP)
            ZipInputStream(bytes.inputStream().buffered()).use { zis ->
                var e: ZipEntry? = zis.nextEntry
                while (e != null) {
                    val n = e.name
                    if (n.isNotBlank() && !n.contains("..")) {
                        if (n == TRANSCRIPT_ENTRY) {
                            // Restore the agent transcript into the per-uid sessions dir, not the project.
                            File(agentSessionsDirFor(context, uid), "$pathKey.json")
                                .outputStream().use { zis.copyTo(it) }
                        } else {
                            val outFile = File(restoredDir, n)
                            if (outFile.canonicalPath.startsWith(restoredDir.canonicalPath)) {
                                if (e.isDirectory) outFile.mkdirs()
                                else { outFile.parentFile?.mkdirs(); FileOutputStream(outFile).use { zis.copyTo(it) } }
                            }
                        }
                    }
                    zis.closeEntry(); e = zis.nextEntry
                }
            }
            val title = name.trim()
            if (title.isNotEmpty()) File(restoredDir, ".ahamai-title").writeText(title.take(60))
        } catch (_: Exception) { /* skip one bad workspace, keep the rest */ }
    }

    /**
     * Restores GitHub-backed workspaces from the backup repo's zipball (a single download for all
     * of them). [items] maps pathKey -> display name for the workspaces to restore. Inside the
     * zipball, files live at `ws/<pathKey>/...` (the agent transcript as [TRANSCRIPT_ENTRY]); the
     * GitHub zipball also wraps everything under one `owner-repo-sha/` top folder, which we strip.
     * Workspaces already present locally are skipped.
     */
    fun restoreWorkspacesFromRepoZip(context: Context, repoZipBytes: ByteArray, items: Map<String, String>) {
        try {
            if (items.isEmpty()) return
            val uid = activeUid()
            val activeRoot = File(context.filesDir, "projects_$uid").apply { mkdirs() }
            val localKeys = localWorkspaceKeys(context)
            ZipInputStream(repoZipBytes.inputStream().buffered()).use { zis ->
                var e: ZipEntry? = zis.nextEntry
                while (e != null) {
                    val rel = e.name.substringAfter('/', "")   // strip the owner-repo-sha/ wrapper
                    if (rel.startsWith("ws/") && !rel.contains("..")) {
                        val after = rel.removePrefix("ws/")     // <pathKey>/inner...
                        val pathKey = after.substringBefore('/')
                        val inner = after.substringAfter('/', "")
                        if (isValidPathKey(pathKey) && pathKey in items.keys && pathKey !in localKeys && inner.isNotBlank()) {
                            if (inner == TRANSCRIPT_ENTRY) {
                                if (!e.isDirectory)
                                    File(agentSessionsDirFor(context, uid), "$pathKey.json")
                                        .outputStream().use { zis.copyTo(it) }
                            } else {
                                val dir = File(activeRoot, pathKey)
                                val outFile = File(dir, inner)
                                if (outFile.canonicalPath.startsWith(dir.canonicalPath)) {
                                    if (e.isDirectory) outFile.mkdirs()
                                    else { outFile.parentFile?.mkdirs(); FileOutputStream(outFile).use { zis.copyTo(it) } }
                                }
                            }
                        }
                    }
                    zis.closeEntry(); e = zis.nextEntry
                }
            }
            // Stamp titles for the workspaces we just recreated.
            for ((k, name) in items) {
                if (k in localKeys) continue
                val dir = File(activeRoot, k)
                if (dir.isDirectory) {
                    val t = name.trim()
                    if (t.isNotEmpty()) File(dir, ".ahamai-title").writeText(t.take(60))
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Reads a project rules / memory file from the project root if present.
     * Supports AGENTS.md, .ahamrules, .cursorrules, CLAUDE.md (first match wins).
     * Returns the trimmed content (capped) or null if none.
     */
    fun readProjectRules(projectDir: String): String? {
        val candidates = listOf("AGENTS.md", ".ahamrules", ".cursorrules", "CLAUDE.md", "agents.md")
        for (name in candidates) {
            val f = File(projectDir, name)
            if (f.exists() && f.isFile) {
                return try {
                    val txt = f.readText().trim()
                    if (txt.isBlank()) null else "(from $name)\n${txt.take(4000)}"
                } catch (_: Exception) { null }
            }
        }
        return null
    }

    /**
     * Sweeps the public Downloads folder and removes the legacy `workspace_*`
     * metadata placeholder files that older builds of the app wrote on every
     * navigate-away from the agent screen (each ~150-byte file was a stub that
     * could only ever restore an EMPTY workspace, and they piled up endlessly).
     *
     * The current build uses cloud backup instead, so these files are pure
     * clutter — call this once on app launch to clean them up silently.
     *
     * Matches by RELATIVE_PATH under Downloads/AhamAI/workspaces/ AND by name
     * pattern `workspace_*` in the Downloads root, to catch both layouts.
     */
    fun cleanupLegacyWorkspaceDownloads(context: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val base = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                // 1) Anything under Downloads/AhamAI/workspaces/ — the old layout.
                runCatching {
                    val projection = arrayOf(android.provider.MediaStore.Downloads._ID,
                        android.provider.MediaStore.Downloads.DISPLAY_NAME,
                        android.provider.MediaStore.Downloads.RELATIVE_PATH)
                    resolver.query(base, projection,
                        "${android.provider.MediaStore.Downloads.RELATIVE_PATH} = ?",
                        arrayOf(android.os.Environment.DIRECTORY_DOWNLOADS + "/AhamAI/workspaces/"),
                        null)?.use { c ->
                        val idIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID)
                        while (c.moveToNext()) {
                            val id = c.getLong(idIdx)
                            val uri = android.content.ContentUris.withAppendedId(base, id)
                            runCatching { resolver.delete(uri, null, null) }
                        }
                    }
                }
                // 2) Stray `workspace_*` files in the Downloads root itself (name starts with "workspace_").
                runCatching {
                    val projection = arrayOf(android.provider.MediaStore.Downloads._ID,
                        android.provider.MediaStore.Downloads.DISPLAY_NAME)
                    resolver.query(base, projection,
                        "${android.provider.MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                        arrayOf("workspace_%"),
                        null)?.use { c ->
                        val idIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID)
                        val nameIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads.DISPLAY_NAME)
                        while (c.moveToNext()) {
                            val name = c.getString(nameIdx) ?: continue
                            // Only delete SMALL files (<2 KB) — a real user export like
                            // "workspace.zip" would be much bigger; the legacy stubs were ~150 B.
                            // This protects any user-named file that happens to start with "workspace_".
                            if (name.startsWith("workspace_") && !name.endsWith(".zip") &&
                                !name.endsWith(".apk") && !name.endsWith(".pdf")) {
                                val id = c.getLong(idIdx)
                                val uri = android.content.ContentUris.withAppendedId(base, id)
                                runCatching { resolver.delete(uri, null, null) }
                            }
                        }
                    }
                }
            } else {
                // Legacy pre-Q path.
                val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val oldDir = File(dl, "AhamAI/workspaces")
                if (oldDir.isDirectory) oldDir.deleteRecursively()
                dl.listFiles()?.forEach { f ->
                    if (f.isFile && f.name.startsWith("workspace_") && f.length() < 2048 &&
                        !f.name.endsWith(".zip") && !f.name.endsWith(".apk") && !f.name.endsWith(".pdf")) {
                        runCatching { f.delete() }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Imports a file from the device Downloads folder into the project.
     * If it's a .zip, it is extracted into [destSubdir] (or project root). Otherwise copied as-is.
     */
    fun importDownloadToProject(context: Context, projectDir: String, fileName: String, destSubdir: String): String {
        return try {
            val bytes = DeviceStorage.readDownloadBytes(context, fileName)
                ?: return "ERROR: '$fileName' not found in Downloads. Use LIST_DOWNLOADS to see available files."
            val cleanDest = destSubdir.trim().removePrefix("/").removePrefix("./")
            if (fileName.trim().endsWith(".zip", ignoreCase = true)) {
                val targetRoot = if (cleanDest.isBlank()) File(projectDir)
                else File(projectDir, cleanDest).apply { mkdirs() }
                var count = 0
                ZipInputStream(bytes.inputStream().buffered()).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val entryName = entry.name
                        if (!entryName.contains("..") && !entryName.startsWith("__MACOSX")) {
                            val outFile = File(targetRoot, entryName)
                            if (outFile.canonicalPath.startsWith(targetRoot.canonicalPath)) {
                                if (entry.isDirectory) outFile.mkdirs()
                                else {
                                    outFile.parentFile?.mkdirs()
                                    FileOutputStream(outFile).use { zis.copyTo(it) }
                                    count++
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                "OK: Extracted '$fileName' from Downloads into ${if (cleanDest.isBlank()) "project root" else cleanDest} ($count files)"
            } else {
                val destPath = if (cleanDest.isBlank()) fileName.trim() else cleanDest
                val outFile = File(projectDir, destPath)
                outFile.parentFile?.mkdirs()
                outFile.writeBytes(bytes)
                "OK: Imported '$fileName' from Downloads to ${relativePathOf(File(projectDir), outFile)} (${bytes.size} bytes)"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }
}
