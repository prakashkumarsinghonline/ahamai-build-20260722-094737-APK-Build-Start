package com.ahamai.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Antigravity-style **Artifacts** — tangible proof of agent work the user can review
 * without scrolling tool logs: screenshots, run logs, previews, walkthrough notes.
 *
 * Stored under `{project}/.ahamai/artifacts/` + `index.json`.
 */
object ArtifactStore {

    enum class Kind(val label: String) {
        SCREENSHOT("Screenshot"),
        RUN_LOG("Run log"),
        PREVIEW("Preview"),
        WALKTHROUGH("Walkthrough"),
        BUILD("Build"),
        DIFF("Diff"),
        OUTPUT("Output")
    }

    data class Artifact(
        val id: String = UUID.randomUUID().toString().take(8),
        val kind: Kind,
        val title: String,
        /** Project-relative path (image, log, html, …). */
        val path: String = "",
        val detail: String = "",
        val url: String = "",
        val language: String = "",
        val success: Boolean = true,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun abs(projectDir: String): File? =
            if (path.isBlank()) null else File(projectDir, path).takeIf { it.exists() }
    }

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyListeners() { listeners.forEach { runCatching { it() } } }

    private fun root(projectDir: String): File =
        File(projectDir, ".ahamai/artifacts").apply { mkdirs() }

    private fun indexFile(projectDir: String): File = File(root(projectDir), "index.json")

    fun list(projectDir: String): List<Artifact> {
        if (projectDir.isBlank()) return emptyList()
        val f = indexFile(projectDir)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).mapNotNull { i -> parse(arr.getJSONObject(i)) }
                .sortedByDescending { it.createdAt }
        } catch (_: Exception) { emptyList() }
    }

    fun screenshots(projectDir: String): List<Artifact> =
        list(projectDir).filter { it.kind == Kind.SCREENSHOT && it.path.isNotBlank() }

    fun hasWalkthrough(projectDir: String): Boolean =
        list(projectDir).any { it.kind == Kind.WALKTHROUGH || it.kind == Kind.SCREENSHOT }

    fun register(
        projectDir: String,
        kind: Kind,
        title: String,
        path: String = "",
        detail: String = "",
        url: String = "",
        language: String = "",
        success: Boolean = true
    ): Artifact {
        val art = Artifact(
            kind = kind,
            title = title.take(120),
            path = path.removePrefix("./").removePrefix("/"),
            detail = detail.take(4000),
            url = url,
            language = language,
            success = success
        )
        val all = list(projectDir).toMutableList()
        all.add(0, art)
        save(projectDir, all.take(80))
        notifyListeners()
        return art
    }

    /** Write text content into artifacts dir and register. */
    fun registerText(
        projectDir: String,
        kind: Kind,
        title: String,
        body: String,
        language: String = "",
        success: Boolean = true,
        url: String = ""
    ): Artifact {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val rel = ".ahamai/artifacts/${kind.name.lowercase()}_$stamp.txt"
        val f = File(projectDir, rel)
        f.parentFile?.mkdirs()
        f.writeText(body)
        return register(projectDir, kind, title, rel, body.take(500), url, language, success)
    }

    fun formatList(projectDir: String): String {
        val items = list(projectDir)
        if (items.isEmpty()) return "No artifacts yet. After RUN_APP / SCREENSHOT / PREVIEW_WEB_APP they appear here."
        return buildString {
            appendLine("ARTIFACTS (${items.size}):")
            items.take(20).forEach { a ->
                val ok = if (a.success) "ok" else "fail"
                val p = if (a.path.isNotBlank()) " → ${a.path}" else ""
                val u = if (a.url.isNotBlank()) " · ${a.url}" else ""
                appendLine("• [${a.kind.label}] ${a.title} ($ok)$p$u")
            }
        }
    }

    /** Agent-facing marker so UI can highlight new artifact. */
    fun marker(art: Artifact): String =
        "[[ARTIFACT]]${art.id}|${art.kind.name}|${art.path}|${art.title}[[/ARTIFACT]]"

    private fun save(projectDir: String, items: List<Artifact>) {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("kind", a.kind.name)
                put("title", a.title)
                put("path", a.path)
                put("detail", a.detail)
                put("url", a.url)
                put("language", a.language)
                put("success", a.success)
                put("createdAt", a.createdAt)
            })
        }
        indexFile(projectDir).writeText(arr.toString())
    }

    private fun parse(o: JSONObject): Artifact? = try {
        Artifact(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
            kind = runCatching { Kind.valueOf(o.optString("kind", "OUTPUT")) }.getOrDefault(Kind.OUTPUT),
            title = o.optString("title", "Artifact"),
            path = o.optString("path", ""),
            detail = o.optString("detail", ""),
            url = o.optString("url", ""),
            language = o.optString("language", ""),
            success = o.optBoolean("success", true),
            createdAt = o.optLong("createdAt", System.currentTimeMillis())
        )
    } catch (_: Exception) { null }
}
