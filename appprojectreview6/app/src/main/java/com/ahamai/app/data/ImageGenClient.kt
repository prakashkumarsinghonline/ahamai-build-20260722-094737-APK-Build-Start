package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Config-driven text-to-image generation.
 *
 * The available image providers/models come from the `imageGeneration` block of the
 * api_providers JSON (see ApiConfig.parseImageProviders). Each provider declares a `format`:
 *   - "pollinations" : free GET endpoint, no key (image.pollinations.ai style)
 *   - "gemini"       : Google AI Studio image models (e.g. gemini-2.5-flash-image / Imagen) via
 *                      generateContent?key=API_KEY, image returned as base64 inlineData
 *   - "openai"       : any OpenAI-compatible POST /images/generations endpoint (b64_json or url)
 *
 * This means image generation is now controllable from the same config (and admin) surface as
 * chat — add a key, toggle enabled, pick the default model. If no config is loaded, it falls back
 * to the legacy keyless Pollinations endpoint so nothing breaks.
 */
object ImageGenClient {

    data class ImageModelOption(val id: String, val label: String, val sub: String)

    // Legacy fallback list used only when no imageGeneration providers are configured.
    private val FALLBACK_MODELS = listOf(
        ImageModelOption("flux", "Pollinations · Flux", "Fast, no wait (default)"),
        ImageModelOption("flux-realistic", "Pollinations · Realistic", "Photoreal style"),
    )

    const val DEFAULT_MODEL = "flux"

    /** Config-driven model list for pickers. Falls back to Pollinations when config is empty. */
    fun availableModels(): List<ImageModelOption> {
        val provs = ApiConfig.imageProvidersEnabled()
        if (provs.isEmpty()) return FALLBACK_MODELS
        val out = ArrayList<ImageModelOption>()
        for (p in provs) {
            for (m in p.models) {
                out.add(ImageModelOption(m, "${p.name} · $m", p.format))
            }
        }
        return out.ifEmpty { FALLBACK_MODELS }
    }

    /** Kept as a property so existing UI (ProfileScreen) keeps working; now config-driven. */
    val MODELS: List<ImageModelOption> get() = availableModels()

    fun labelFor(id: String): String =
        availableModels().firstOrNull { it.id == id }?.label ?: id.ifBlank { "Image" }

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private const val UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val JSON_MT = "application/json; charset=utf-8"

    suspend fun generate(context: Context, prompt: String, modelId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanPrompt = prompt.trim()
                if (cleanPrompt.isBlank()) return@withContext Result.failure(Exception("Empty prompt"))

                val prov = ApiConfig.imageProviderForModel(modelId)
                val bytes: ByteArray? = when (prov?.format) {
                    "gemini" -> generateGemini(prov, cleanPrompt, modelId)
                    "openai" -> generateOpenAiImage(prov, cleanPrompt, modelId)
                    "pollinations" -> generatePollinations(prov.baseUrl, cleanPrompt, modelId, prov.defaultSize)
                    else -> generatePollinations(null, cleanPrompt, modelId, "1024x1024") // legacy fallback
                }
                if (bytes == null || bytes.size < 1000)
                    return@withContext Result.failure(Exception("Image service returned no image. Try again."))
                val ext = sniffExt(bytes)
                val dir = File(context.cacheDir, "generated_images").apply { mkdirs() }
                val out = File(dir, "img_${System.currentTimeMillis()}.$ext")
                out.writeBytes(bytes)
                Result.success(out.absolutePath)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Generate an image and save it straight into the project at [outRel] (relative to
     * [projectDir]). Returns a human-readable result string for the agent.
     */
    suspend fun generateToProject(
        context: Context, projectDir: String, prompt: String, outRel: String, modelId: String
    ): String = withContext(Dispatchers.IO) {
        val res = generate(context, prompt, modelId)
        val cachePath = res.getOrNull()
            ?: return@withContext "ERROR: image generation failed: ${res.exceptionOrNull()?.message ?: "unknown"}"
        return@withContext try {
            val src = File(cachePath)
            val rel = outRel.trim().removePrefix("/").ifBlank {
                "image_${System.currentTimeMillis()}.${src.extension.ifBlank { "jpg" }}"
            }
            val dest = File(projectDir, rel)
            dest.parentFile?.mkdirs()
            src.copyTo(dest, overwrite = true)
            "OK: Generated image (${labelFor(modelId)}) saved to $rel"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    // ── Pollinations (free, keyless) ──────────────────────────────────────────
    private fun generatePollinations(baseUrl: String?, prompt: String, modelId: String, size: String): ByteArray? {
        val seed = (0..999999).random()
        val (w, h) = parseSize(size)
        // For the legacy fallback path modelId may be a provider id; default the actual model to flux.
        val model = if (modelId.isBlank() || modelId == "pollinations") "flux" else modelId
        val style = if (modelId.contains("realistic")) ", photorealistic, high detail, 4k" else ""
        val finalPrompt = java.net.URLEncoder.encode(prompt + style, "UTF-8").replace("+", "%20")
        val root = (baseUrl?.ifBlank { null } ?: "https://image.pollinations.ai").trimEnd('/')
        val url = "$root/prompt/$finalPrompt?width=$w&height=$h&nologo=true&model=$model&seed=$seed"
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val ct = resp.header("Content-Type") ?: ""
            if (!ct.startsWith("image")) return null
            return resp.body?.bytes()
        }
    }

    // ── Google AI Studio (Gemini image models, e.g. gemini-2.5-flash-image / Imagen) ──
    private fun generateGemini(prov: ApiConfig.ImageProvider, prompt: String, modelId: String): ByteArray? {
        val key = ApiConfig.nextImageKey(prov).ifBlank {
            ApiConfig.keyForProviderId("google_aistudio") ?: ""
        }
        if (key.isBlank() || key.startsWith("PASTE_")) return null
        val base = prov.baseUrl.ifBlank { "https://generativelanguage.googleapis.com" }.trimEnd('/')
        val model = modelId.ifBlank { "gemini-2.5-flash-image" }

        // Imagen models use the :predict endpoint; Gemini image models use :generateContent.
        return if (model.startsWith("imagen")) {
            val url = "$base/v1beta/models/$model:predict?key=$key"
            val body = JSONObject()
                .put("instances", JSONArray().put(JSONObject().put("prompt", prompt)))
                .put("parameters", JSONObject().put("sampleCount", 1))
            postForImage(url, body) { json ->
                json.optJSONArray("predictions")?.optJSONObject(0)?.optString("bytesBase64Encoded")
            }
        } else {
            val url = "$base/v1beta/models/$model:generateContent?key=$key"
            val body = JSONObject().put(
                "contents",
                JSONArray().put(
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                )
            )
            postForImage(url, body) { json ->
                val parts = json.optJSONArray("candidates")
                    ?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val inline = parts.optJSONObject(i)?.optJSONObject("inlineData")
                            ?: parts.optJSONObject(i)?.optJSONObject("inline_data")
                        val data = inline?.optString("data")
                        if (!data.isNullOrBlank()) return@postForImage data
                    }
                }
                null
            }
        }
    }

    // ── Generic OpenAI-compatible image endpoint (/images/generations) ─────────
    private fun generateOpenAiImage(prov: ApiConfig.ImageProvider, prompt: String, modelId: String): ByteArray? {
        val key = ApiConfig.nextImageKey(prov)
        if (key.isBlank() || key.startsWith("PASTE_")) return null
        val url = "${prov.baseUrl.trimEnd('/')}/images/generations"
        val body = JSONObject()
            .put("model", modelId)
            .put("prompt", prompt)
            .put("n", 1)
            .put("size", prov.defaultSize.ifBlank { "1024x1024" })
            .put("response_format", "b64_json")
        val req = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", JSON_MT)
            .post(body.toString().toRequestBody(JSON_MT.toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val s = resp.body?.string() ?: return null
            val data = JSONObject(s).optJSONArray("data")?.optJSONObject(0) ?: return null
            val b64 = data.optString("b64_json")
            if (b64.isNotBlank()) return decodeB64(b64)
            val imgUrl = data.optString("url")
            if (imgUrl.isNotBlank()) return downloadBytes(imgUrl)
            return null
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private inline fun postForImage(url: String, body: JSONObject, extract: (JSONObject) -> String?): ByteArray? {
        val req = Request.Builder().url(url)
            .addHeader("Content-Type", JSON_MT)
            .post(body.toString().toRequestBody(JSON_MT.toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val s = resp.body?.string() ?: return null
            val b64 = extract(JSONObject(s)) ?: return null
            return decodeB64(b64)
        }
    }

    private fun downloadBytes(url: String): ByteArray? {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.bytes()
        }
    }

    private fun decodeB64(b64: String): ByteArray? = try {
        Base64.decode(b64, Base64.DEFAULT)
    } catch (_: Exception) { null }

    private fun parseSize(size: String): Pair<Int, Int> {
        val parts = size.lowercase().split("x")
        val w = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1024
        val h = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1024
        return w to h
    }

    private fun sniffExt(bytes: ByteArray): String = when {
        bytes.size >= 12 && bytes[8].toInt() == 'W'.code && bytes[9].toInt() == 'E'.code -> "webp"
        bytes.size >= 8 && (bytes[0].toInt() and 0xff) == 0x89 && bytes[1].toInt() == 'P'.code -> "png"
        else -> "jpg"
    }
}
