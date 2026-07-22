package com.ahamai.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Gives the Code Agent controlled access to the device's public Downloads folder.
 * - Save a downloaded file into Downloads (so the user can find/use it).
 * - List files that live in Downloads.
 * - Read a file from Downloads back into the app (e.g. to import into a project).
 *
 * Uses MediaStore on Android 10+ (API 29) and the legacy public dir on older versions.
 */
object DeviceStorage {

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** Returns the legacy public Downloads directory (for API < 29 and for listing). */
    private fun legacyDownloadsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

    /**
     * Saves bytes into Downloads/AhamAI (a dedicated app folder) so user-saved files are grouped.
     */
    fun saveToAhamAIFolder(context: Context, bytes: ByteArray, fileName: String, mime: String = "application/octet-stream"): String {
        return try {
            val safeName = fileName.trim().replace("/", "_").ifBlank { "ahamai_${System.currentTimeMillis()}" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AhamAI")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "ERROR: Could not create file in Downloads/AhamAI"
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "OK: Saved to Downloads/AhamAI/$safeName"
            } else {
                val dir = File(legacyDownloadsDir(), "AhamAI").apply { mkdirs() }
                File(dir, safeName).writeBytes(bytes)
                "OK: Saved to Downloads/AhamAI/$safeName"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Writes raw bytes into the public Downloads folder under [fileName].
     * Returns a human-readable result string.
     *
     * Refuses to write files smaller than 32 bytes — these are almost always
     * accidental placeholder writes (empty zip overhead is 22 B, an empty JSON
     * is 2 B) that clutter the user's Downloads without delivering any value.
     */
    fun saveBytesToDownloads(context: Context, bytes: ByteArray, fileName: String, mime: String = "application/octet-stream"): String {
        // Guard against accidental blank/placeholder writes. Real user-facing exports
        // (a generated image, an exported chat, a built APK) are always >32 bytes;
        // an "empty" workspace export or a stub metadata file is not.
        if (bytes.size < 32) {
            return "ERROR: Refusing to write a ${(bytes.size)}-byte file to Downloads (looks empty/placeholder)."
        }
        if (bytes.size > 100_000_000) {
            // For files > 100 MB, route through the streaming version to avoid OOM.
            // Write bytes to a temp file first, then stream that.
            val tmp = java.io.File(context.cacheDir, "tmp_${System.currentTimeMillis()}_${fileName.replace("/","_")}")
            try {
                tmp.writeBytes(bytes)
                return saveFileToDownloads(context, tmp, fileName, mime)
            } finally {
                tmp.delete()
            }
        }
        return try {
            val safeName = fileName.trim().replace("/", "_").ifBlank { "ahamai_download_${System.currentTimeMillis()}" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "ERROR: Could not create file in Downloads"
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "OK: Saved ${bytes.size} bytes to Downloads/$safeName"
            } else {
                val dir = legacyDownloadsDir().apply { mkdirs() }
                val outFile = File(dir, safeName)
                outFile.writeBytes(bytes)
                "OK: Saved ${bytes.size} bytes to Downloads/$safeName"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Streams a [File] (potentially large) into the public Downloads folder without loading
     * it all into RAM. This is the OOM-safe path for 200MB+ exports.
     *
     * Refuses to write files smaller than 32 bytes — see [saveBytesToDownloads] for why.
     */
    fun saveFileToDownloads(context: Context, file: java.io.File, fileName: String, mime: String = "application/octet-stream"): String {
        // Guard against accidental blank/placeholder writes — same rationale as above.
        if (!file.exists() || file.length() < 32) {
            return "ERROR: Refusing to write a ${file.length()}-byte file to Downloads (looks empty/placeholder)."
        }
        return try {
            val safeName = fileName.trim().replace("/", "_").ifBlank { "ahamai_download_${System.currentTimeMillis()}" }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, mime)
                    put(android.provider.MediaStore.Downloads.SIZE, file.length())
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return "ERROR: Could not create file in Downloads"
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().buffered().use { `in` -> `in`.copyTo(out) }
                }
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "OK: Saved ${file.length()} bytes to Downloads/$safeName"
            } else {
                val dir = legacyDownloadsDir().apply { mkdirs() }
                val outFile = java.io.File(dir, safeName)
                file.copyTo(outFile, overwrite = true)
                "OK: Saved ${file.length()} bytes to Downloads/$safeName"
            }
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Downloads a URL directly into the device Downloads folder.
     */
    fun downloadUrlToDevice(context: Context, url: String, fileName: String): String {
        return try {
            val req = okhttp3.Request.Builder()
                .url(url.trim())
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) return "ERROR: HTTP ${resp.code} downloading $url"
            val bytes = resp.body?.bytes() ?: return "ERROR: Empty response"
            val name = fileName.ifBlank { url.trim().substringAfterLast('/').substringBefore('?').ifBlank { "download_${System.currentTimeMillis()}" } }
            val mime = when (name.substringAfterLast('.', "").lowercase()) {
                "zip" -> "application/zip"
                "json" -> "application/json"
                "txt", "md" -> "text/plain"
                "html", "htm" -> "text/html"
                "pdf" -> "application/pdf"
                else -> "application/octet-stream"
            }
            saveBytesToDownloads(context, bytes, name, mime)
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    data class DownloadEntry(val name: String, val sizeBytes: Long)

    /**
     * Lists files in the public Downloads folder.
     */
    fun listDownloads(context: Context): List<DownloadEntry> {
        val result = mutableListOf<DownloadEntry>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME, MediaStore.Downloads.SIZE)
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, null, null,
                    "${MediaStore.Downloads.DATE_ADDED} DESC"
                )?.use { c ->
                    val nameIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
                    while (c.moveToNext() && result.size < 200) {
                        result.add(DownloadEntry(c.getString(nameIdx) ?: continue, c.getLong(sizeIdx)))
                    }
                }
            } else {
                legacyDownloadsDir().listFiles()?.sortedByDescending { it.lastModified() }?.forEach {
                    if (it.isFile) result.add(DownloadEntry(it.name, it.length()))
                }
            }
        } catch (_: Exception) {}
        return result
    }

    /**
     * Reads the bytes of a file in Downloads by display name.
     */
    fun readDownloadBytes(context: Context, fileName: String): ByteArray? {
        return try {
            val target = fileName.trim()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.DISPLAY_NAME)
                context.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?", arrayOf(target), null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        val uri = android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }
                }
                // Fallback to legacy path
                val f = File(legacyDownloadsDir(), target)
                if (f.exists()) f.readBytes() else null
            } else {
                val f = File(legacyDownloadsDir(), target)
                if (f.exists()) f.readBytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }
}
