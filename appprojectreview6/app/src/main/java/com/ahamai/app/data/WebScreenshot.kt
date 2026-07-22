package com.ahamai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Fast webpage screenshots for agent + chat.
 *
 * The old provider (`mini.s-shot.ru`) frequently hung / timed out on mobile networks.
 * We try several free, no-key providers that return a real PNG/JPEG image, short-circuit
 * on the first valid one, and never spin up the full Playwright browser for a simple shot.
 */
object WebScreenshot {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    /** Normalize user input to an absolute http(s) URL. */
    fun normalizeUrl(raw: String): String {
        val t = raw.trim().trim('"', '\'', '`', '<', '>')
        if (t.isBlank()) return ""
        return if (t.startsWith("http://", true) || t.startsWith("https://", true)) t else "https://$t"
    }

    /**
     * Capture [url] into the project at [destRelPath]. Returns OK/ERROR message for the agent.
     * Tries multiple public screenshot services with short timeouts — typically 1–4s total.
     */
    suspend fun captureToProject(
        projectDir: String,
        url: String,
        destRelPath: String = "",
        onHeartbeat: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val full = normalizeUrl(url)
        if (full.isBlank()) return@withContext "ERROR: no URL provided to screenshot."

        val enc = URLEncoder.encode(full, "UTF-8")
        // Prefer providers verified live (2026-07): thum.io ~1.3s, microlink ~1.5s, mshots ~0.6s.
        // Do NOT use mini.s-shot.ru first — it hangs often.
        val providers = listOf(
            "thum.io" to "https://image.thum.io/get/width/1280/noanimate/$full",
            "thum.io-crop" to "https://image.thum.io/get/width/1280/crop/900/$full",
            "microlink" to "https://api.microlink.io/?url=$enc&screenshot=true&meta=false&embed=screenshot.url",
            "wordpress-mshots" to "https://s0.wp.com/mshots/v1/$enc?w=1280",
            "s-shot" to "https://mini.s-shot.ru/1280x800/PNG/1280/Z100/?$full"
        )

        val dest = destRelPath.trim().ifBlank {
            val host = try {
                java.net.URI(full).host?.replace(Regex("[^a-zA-Z0-9._-]"), "_")?.take(40)
            } catch (_: Exception) { null }
            "screenshot_${host ?: "page"}_${System.currentTimeMillis()}.png"
        }.removePrefix("./").removePrefix("/")

        val errors = mutableListOf<String>()
        for ((name, shotUrl) in providers) {
            onHeartbeat("Screenshot via $name…")
            val bytes = withTimeoutOrNull(16_000L) { fetchImageBytes(shotUrl) }
            if (bytes == null) {
                errors += "$name: timeout"
                continue
            }
            if (bytes.size < 800) {
                errors += "$name: too small (${bytes.size} B)"
                continue
            }
            if (!looksLikeImage(bytes)) {
                errors += "$name: not an image (${bytes.size} B)"
                continue
            }
            // Pick extension from magic / provider
            val ext = when {
                bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() -> "png"
                bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "jpg"
                bytes.size >= 12 && bytes.copyOfRange(0, 4).contentEquals("RIFF".toByteArray()) -> "webp"
                dest.contains('.') -> dest.substringAfterLast('.').lowercase()
                else -> "png"
            }
            val finalRel = if (dest.substringAfterLast('.', "").lowercase() in setOf("png", "jpg", "jpeg", "webp"))
                dest
            else
                "${dest.removeSuffix(".png").removeSuffix(".jpg")}.$ext"

            return@withContext try {
                val out = File(projectDir, finalRel)
                out.parentFile?.mkdirs()
                out.writeBytes(bytes)
                "OK: Screenshot saved to $finalRel (${fmtSize(bytes.size)}) via $name\n" +
                    "URL: $full\n" +
                    "[[FILE]]$finalRel[[/FILE]]"
            } catch (e: Exception) {
                "ERROR: could not write screenshot: ${e.message}"
            }
        }
        "ERROR: screenshot failed for $full\nTried: ${errors.joinToString("; ")}"
    }

    /**
     * Best public screenshot image URL for chat Markdown embeds (no download).
     * Prefer thum.io — fast and stable on mobile.
     */
    fun markdownImageUrl(pageUrl: String): String {
        val full = normalizeUrl(pageUrl)
        if (full.isBlank()) return ""
        return "https://image.thum.io/get/width/1280/noanimate/$full"
    }

    private fun fetchImageBytes(url: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.bytes() ?: return null
                body
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun looksLikeImage(b: ByteArray): Boolean {
        if (b.size < 12) return false
        // PNG
        if (b[0] == 0x89.toByte() && b[1] == 0x50.toByte() && b[2] == 0x4E.toByte() && b[3] == 0x47.toByte()) return true
        // JPEG
        if (b[0] == 0xFF.toByte() && b[1] == 0xD8.toByte() && b[2] == 0xFF.toByte()) return true
        // WEBP (RIFF....WEBP)
        if (b[0] == 'R'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte() && b[3] == 'F'.code.toByte()
            && b[8] == 'W'.code.toByte() && b[9] == 'E'.code.toByte()
        ) return true
        // GIF
        if (b[0] == 'G'.code.toByte() && b[1] == 'I'.code.toByte() && b[2] == 'F'.code.toByte()) return true
        return false
    }

    private fun fmtSize(n: Int): String = when {
        n >= 1_048_576 -> String.format("%.1f MB", n / 1_048_576.0)
        n >= 1024 -> String.format("%.0f KB", n / 1024.0)
        else -> "$n B"
    }
}
