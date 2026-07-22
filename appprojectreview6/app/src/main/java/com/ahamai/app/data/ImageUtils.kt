package com.ahamai.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Helpers for turning picked/captured images into vision-API data URLs. */
object ImageUtils {

    /**
     * Reads an image from a content/file Uri, downscales it (longest side <= [maxDim]),
     * JPEG-compresses it and returns a `data:image/jpeg;base64,...` URL usable by
     * OpenAI-compatible vision endpoints. Returns null on failure.
     */
    fun uriToDataUrl(context: Context, uriString: String, maxDim: Int = 1024, quality: Int = 80): String? {
        return try {
            val uri = Uri.parse(uriString)

            // 1) read bounds (decodeStream returns null in bounds-mode by design — that's fine,
            //    we only care about the dimensions it writes into `bounds`).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
                sample *= 2
            }

            // 2) decode downsampled
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Reads an image from a local file path, downscales it and returns a JPEG data URL.
     * Used by the agent to send an image to the vision model.
     */
    fun fileToDataUrl(path: String, maxDim: Int = 1024, quality: Int = 80): String? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(path, opts) ?: return null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bitmap.recycle()
            "data:image/jpeg;base64,${Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)}"
        } catch (e: Exception) {
            null
        }
    }

    /** Decodes a base64 string or `data:...;base64,xxx` data URL into a Bitmap (or null). */
    fun decodeBase64(value: String?): Bitmap? {
        if (value.isNullOrBlank()) return null
        return try {
            val b64 = value.substringAfter("base64,", value)
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
