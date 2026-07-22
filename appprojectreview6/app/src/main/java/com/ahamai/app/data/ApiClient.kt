package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class StreamDelta(val text: String?, val reasoning: String?)

/** A chat message that may include images (base64 data URLs) for vision models. */
data class ApiMessage(val role: String, val text: String, val images: List<String> = emptyList())

object ApiClient {
    /**
     * LLM stream client via [ProxyManager]:
     *  - no hard callTimeout (long agent / reasoning turns)
     *  - 90s idle read between tokens (slow models)
     *  - outbound proxy when admin enables Network tab
     */
    private fun client(): OkHttpClient = ProxyManager.llmClient(
        connectSec = 15, readSec = 90, writeSec = 30, callSec = 0
    )

    /** Short-timeout client for model-list fetches — picker must feel instant. */
    private fun modelsClient(): OkHttpClient = ProxyManager.shortClient(
        connectSec = 4, readSec = 6, callSec = 8
    )

    /** Returns true when the base URL is a Freebuff/Codebuff endpoint. */
    private fun isFreebuffUrl(baseUrl: String): Boolean =
        baseUrl.contains("codebuff.com", ignoreCase = true) ||
        baseUrl.contains("freebuff", ignoreCase = true) ||
        baseUrl.contains("freebuff2api", ignoreCase = true)

    fun fetchModels(baseUrl: String, apiKey: String): Result<List<String>> {
        // Native Gemini (OAuth) — but NOT the OpenAI-compatible endpoint (.../v1beta/openai),
        // which must flow through the generic OpenAI path so an AI Studio API key works.
        if (baseUrl.contains("generativelanguage.googleapis.com") && !baseUrl.contains("/openai")) return Result.success(listOf("gemini-2.5-pro", "gemini-2.5-flash", "gemini-2.0-flash"))
        // Freebuff / Codebuff — return all available Freebuff models (instant, no network)
        if (isFreebuffUrl(baseUrl)) return Result.success(FreebuffModels.allModelIds())
        return try {
            val url = "${baseUrl.trimEnd('/')}/models"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = modelsClient().newCall(request).execute()
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: json.optJSONArray("models")
                ?: return Result.failure(Exception("No models array"))
            val models = ArrayList<String>(data.length())
            for (i in 0 until data.length()) {
                val item = data.opt(i)
                val id = when (item) {
                    is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                    is String -> item
                    else -> ""
                }
                if (id.isNotBlank()) models.add(id)
            }
            models.sort()
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Lightweight key health check for the admin panel. Verifies a key can actually reach the
     * provider — does a real (but tiny, max_tokens=1) chat completion when a model is known,
     * otherwise falls back to a cheap GET /models. Returns "OK" on success or a short status.
     * Uses a short-timeout client so the parallel "Test All" finishes quickly.
     */
    fun pingKey(baseUrl: String, key: String, model: String = ""): String {
        val shortClient = ProxyManager.shortClient(8, 12, 15)
        return try {
            val req = if (model.isNotBlank() && !baseUrl.contains("generativelanguage")) {
                val payload = JSONObject()
                    .put("model", model)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "ping")))
                    .put("max_tokens", 1)
                    .put("stream", false)
                Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/chat/completions")
                    .addHeader("Authorization", "Bearer $key")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()
            } else {
                Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .addHeader("Authorization", "Bearer $key")
                    .get().build()
            }
            shortClient.newCall(req).execute().use { resp ->
                val code = resp.code
                when {
                    code in 200..299 -> "OK"
                    code == 401 || code == 403 -> "HTTP $code (bad key)"
                    code == 429 -> "HTTP 429 (rate-limited)"
                    else -> "HTTP $code"
                }
            }
        } catch (e: Exception) {
            "ERR: ${e.message?.take(40)}"
        }
    }

    fun sendChatMessage(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>, // role to content
        cache: Boolean = true
    ): Result<String> {
        // Response-cache fast path (chat only; agent callers pass cache=false).
        val cacheKey = if (cache) ResponseCache.keyFor(model, messages) else null
        if (cacheKey != null) ResponseCache.get(cacheKey)?.let { return Result.success(it) }
        // Combo virtual model — try each member until one returns content
        if (ComboRouter.isCombo(model)) {
            var last: Result<String> = Result.failure(Exception("empty combo"))
            for ((member, resolved) in ComboRouter.resolveChain(model)) {
                last = sendChatMessage(resolved.baseUrl, resolved.apiKey, member.model, messages, cache = false)
                if (last.isSuccess && !last.getOrNull().isNullOrBlank()) {
                    ComboRouter.markMemberOk(model, 0)
                    return last
                }
                val err = last.exceptionOrNull()?.message ?: ""
                ComboRouter.markMemberFail(model, member, rateLimited = err.contains("429") || err.contains("rate"))
            }
            return last
        }
        // Freebuff / Codebuff — native Kotlin client, no key rotation needed.
        if (isFreebuffUrl(baseUrl)) {
            return kotlinx.coroutines.runBlocking {
                FreebuffManager.complete(apiKey, model, messages)
            }
        }
        if (baseUrl.contains("generativelanguage.googleapis.com") && !baseUrl.contains("/openai")) {
            return geminiComplete(apiKey, model, messages)
        }
        // Silent key rotation — try every key before surfacing any error to the user.
        // Budget = max(3, keyCount) so even single-key configs get a few retries.
        val budget = maxOf(3, ApiConfig.getKeyCount(baseUrl))
        var lastError: Exception? = null
        var currentKey = apiKey
        repeat(budget) { attempt ->
            try {
                val url = "${baseUrl.trimEnd('/')}/chat/completions"
                val messagesJson = JSONArray()
                for ((role, content) in messages) {
                    messagesJson.put(JSONObject().put("role", role).put("content", content))
                }
                val requestBody = JSONObject()
                    .put("model", model).put("messages", messagesJson).put("stream", false)
                val body = requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder().url(url)
                    .addHeader("Authorization", "Bearer $currentKey")
                    .addHeader("Content-Type", "application/json").post(body).build()

                val response = client().newCall(request).execute()
                val code = response.code
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    // Permanent key failure → blacklist + rotate silently
                    if (code == 401 || code == 403 || code == 422) {
                        ApiConfig.markKeyBad(baseUrl, currentKey)
                        currentKey = ApiConfig.getNextKey(baseUrl) ?: currentKey
                        lastError = Exception("auth_fail"); return@repeat
                    }
                    // Rate limit or server error → rotate + brief back-off
                    if (code == 429 || code in 500..599 ||
                        errorBody.contains("NO_VALID_RESPONSE", ignoreCase = true)) {
                        // 429 specifically → park this key in cooldown so future requests skip it.
                        if (code == 429) ApiConfig.markKeyCoolDown(baseUrl, currentKey)
                        val next = ApiConfig.getNextKey(baseUrl)
                        if (next != null && next != currentKey) currentKey = next
                        Thread.sleep(500L * (attempt / 2 + 1).coerceAtMost(3))
                        lastError = Exception("transient"); return@repeat
                    }
                    lastError = Exception("HTTP $code"); return@repeat
                }

                val responseBody = response.body?.string()
                    ?: return Result.failure(Exception("The model returned an empty response. Please try again."))
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    lastError = Exception("no_choices"); return@repeat
                }
                val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content","") ?: ""
                if (content.isBlank()) { lastError = Exception("empty"); return@repeat }
                if (cacheKey != null) ResponseCache.put(cacheKey, content)
                return Result.success(content)
            } catch (e: Exception) {
                lastError = e
            }
        }
        // Primary provider exhausted — try the cross-provider fallback chain (LiteLLM-style failover).
        for (alt in ApiConfig.fallbackChain(baseUrl, agent = false)) {
            val r = openAiComplete(alt.baseUrl, alt.apiKey, alt.model, messages)
            val txt = r.getOrNull().orEmpty()
            if (txt.isNotBlank()) {
                if (cacheKey != null) ResponseCache.put(cacheKey, txt)
                return Result.success(txt)
            }
        }
        // All attempts failed — show a clean message, never expose HTTP codes
        return Result.failure(Exception(
            "Couldn't reach the AI service. Please try again shortly."
        ))
    }

    /** Single non-streaming OpenAI-compatible completion — used by the cross-provider fallback chain. */
    private fun openAiComplete(
        baseUrl: String, apiKey: String, model: String, messages: List<Pair<String, String>>
    ): Result<String> {
        return try {
            val url = "${baseUrl.trimEnd('/')}/chat/completions"
            val messagesJson = JSONArray()
            for ((role, content) in messages) messagesJson.put(JSONObject().put("role", role).put("content", content))
            val requestBody = JSONObject().put("model", model).put("messages", messagesJson).put("stream", false)
            val body = requestBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json").post(body).build()
            client().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))
                val responseBody = response.body?.string() ?: return Result.failure(Exception("empty"))
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices") ?: return Result.failure(Exception("no_choices"))
                if (choices.length() == 0) return Result.failure(Exception("no_choices"))
                val content = choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                if (content.isBlank()) Result.failure(Exception("empty")) else Result.success(content)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    fun streamChat(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
    ): Flow<StreamDelta> = streamChatVision(
        baseUrl, apiKey, model, messages.map { ApiMessage(it.first, it.second) }
    )

    /**
     * Agent turn WITH live streaming. Streams the model output, invoking [onPartial] with the
     * full accumulated text so far (so the UI can render progress in real time), and returns
     * the COMPLETE response text for tool parsing.
     *
     * If the stream dies before producing a parseable response, it falls back to a single
     * non-streaming request so a turn is never lost to a flaky connection.
     */
    suspend fun streamAgentResponse(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        onPartial: suspend (String) -> Unit
    ): Result<String> {
        // Try streaming up to 2 times to ride out transient network errors
        // transparently. streamChat already rotates keys internally,
        // so 2 outer attempts × N-key inner rotation is plenty before the non-stream fallback.
        var attempts = 0
        val maxAttempts = 1
        while (attempts < maxAttempts) {
            attempts++
            val sb = StringBuilder()
            var lastEmit = 0L
            try {
                streamChat(baseUrl, apiKey, model, messages).collect { delta ->
                    val piece = delta.text
                    if (!piece.isNullOrEmpty()) {
                        sb.append(piece)
                        // Throttle live-preview callbacks: the agent UI does heavy work per update
                        // (narration regexes + markdown parse + list copy). Coalescing to ~60ms
                        // keeps streaming butter-smooth without losing any content (final flush below).
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 60L) {
                            lastEmit = now
                            onPartial(sb.toString())
                        }
                    }
                }
                val full = sb.toString()
                if (full.isNotBlank()) {
                    onPartial(full) // final flush — guarantees the complete text is shown
                    return Result.success(full)
                }
                // Stream ended empty — fall back to non-stream once.
                if (attempts == maxAttempts) return withContext(Dispatchers.IO) { sendChatMessage(baseUrl, apiKey, model, messages, cache = false) }
            } catch (e: Exception) {
                val partial = sb.toString()
                // Partial already contains a complete tool call / answer → trust it, no retry.
                val looksComplete = partial.contains("</tool_call>", ignoreCase = true) ||
                    Regex("\\nEND_FILE", RegexOption.IGNORE_CASE).containsMatchIn(partial) ||
                    Regex("(?m)^\\s*(DONE|ANSWER)\\b", RegexOption.IGNORE_CASE).containsMatchIn(partial)
                if (looksComplete) return Result.success(partial)
                // On the final attempt, fall back to non-stream so the turn isn't lost.
                if (attempts == maxAttempts) return withContext(Dispatchers.IO) { sendChatMessage(baseUrl, apiKey, model, messages, cache = false) }
                // Otherwise: brief pause and retry the stream.
                kotlinx.coroutines.delay(400L)
            }
        }
        // Unreachable, but keeps the compiler happy.
        return withContext(Dispatchers.IO) { sendChatMessage(baseUrl, apiKey, model, messages, cache = false) }
    }

    /**
     * Z-AI LEVEL: Agent turn with structured tool definitions.
     * Sends tool definitions alongside messages. Providers that support function calling
     * will return structured `tool_calls` in the response, which are more reliable than
     * XML parsing. The response text is returned as-is for the existing XML parser to handle
     * as a fallback (some providers return tool calls in content instead of the structured format).
     *
     * Usage in CodeAgentScreen.runAgent():
     *   val toolsArray = StructuredTools.buildToolsArray()
     *   val response = ApiClient.streamAgentResponseWithTools(baseUrl, apiKey, model, messages, toolsArray) { ... }
     */
    suspend fun streamAgentResponseWithTools(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<Pair<String, String>>,
        tools: JSONArray,
        onPartial: suspend (String) -> Unit
    ): Result<String> {
        // Try streaming with tools. The SSE response may contain tool_calls delta.
        var attempts = 0
        val maxAttempts = 1
        while (attempts < maxAttempts) {
            attempts++
            val sb = StringBuilder()
            var lastEmit = 0L
            var toolCallsContent = StringBuilder() // accumulate tool_call deltas
            try {
                streamChatWithTools(baseUrl, apiKey, model, messages.map { ApiMessage(it.first, it.second) }, tools).collect { delta ->
                    val piece = delta.text
                    if (!piece.isNullOrEmpty()) {
                        sb.append(piece)
                        val now = System.currentTimeMillis()
                        if (now - lastEmit >= 60L) {
                            lastEmit = now
                            onPartial(sb.toString())
                        }
                    }
                }
                val full = sb.toString()
                if (full.isNotBlank()) {
                    onPartial(full)
                    // Check if the response contains structured tool_calls (some providers
                    // return them in content as JSON). If so, the XML parser will handle it.
                    return Result.success(full)
                }
                if (attempts == maxAttempts) return withContext(Dispatchers.IO) { sendChatMessage(baseUrl, apiKey, model, messages, cache = false) }
            } catch (e: Exception) {
                val partial = sb.toString()
                // If we already have a complete tool call / answer in the partial (e.g. the stream
                // reset AFTER the model finished its action), trust it instead of failing the turn.
                if (partial.contains("</tool_call>", ignoreCase = true) ||
                    Regex("\\nEND_FILE", RegexOption.IGNORE_CASE).containsMatchIn(partial) ||
                    Regex("(?m)^\\s*(DONE|ANSWER)\\b", RegexOption.IGNORE_CASE).containsMatchIn(partial)) {
                    return Result.success(partial)
                }
                if (attempts == maxAttempts) return withContext(Dispatchers.IO) { sendChatMessage(baseUrl, apiKey, model, messages, cache = false) }
                kotlinx.coroutines.delay(400L)
            }
        }
        return withContext(Dispatchers.IO) { sendChatMessage(baseUrl, apiKey, model, messages, cache = false) }
    }

    /** Stream chat with structured tools parameter. Routes through the same provider logic. */
    private fun streamChatWithTools(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        tools: JSONArray
    ): Flow<StreamDelta> {
        // Skip structured tools for providers that don't support function calling
        if (isFreebuffUrl(baseUrl) ||
            (baseUrl.contains("generativelanguage.googleapis.com") && !baseUrl.contains("/openai"))) {
            // Fallback to regular stream for unsupported providers
            return streamChatVision(baseUrl, apiKey, model, messages)
        }
        return channelFlow {
            val budget = maxOf(2, ApiConfig.getKeyCount(baseUrl))
            var currentKey = apiKey
            var lastEx: Exception? = null
            val full = StringBuilder()
            var emittedAny = false
            for (attempt in 0 until budget) {
                try {
                    streamChatVisionRaw(baseUrl, currentKey, model, messages, tools).collect {
                        if (!it.text.isNullOrEmpty()) { emittedAny = true; full.append(it.text) }
                        send(it)
                    }
                    return@channelFlow
                } catch (e: Exception) {
                    lastEx = e
                    if (emittedAny) break
                    val msg = e.message ?: ""
                    if (msg.contains("auth_fail")) ApiConfig.markKeyBad(baseUrl, currentKey)
                    if (msg.contains("rate_fail")) ApiConfig.markKeyCoolDown(baseUrl, currentKey)
                    val next = ApiConfig.getNextKey(baseUrl)
                    if (next != null && next != currentKey) currentKey = next else break
                    if (msg.contains("rate_fail")) delay(500)
                }
            }
            if (!emittedAny) {
                for (alt in ApiConfig.fallbackChain(baseUrl)) {
                    try {
                        streamChatVisionRaw(alt.baseUrl, alt.apiKey, alt.model, messages, tools).collect {
                            if (!it.text.isNullOrEmpty()) { emittedAny = true; full.append(it.text) }
                            send(it)
                        }
                        return@channelFlow
                    } catch (e: Exception) {
                        lastEx = e
                        if (emittedAny) break
                    }
                }
            }
            throw lastEx ?: Exception("Couldn't reach the AI service. Please try again.")
        }
    }

    /**
     * Synthesize a human-readable narration line from a native tool call (like Cursor/Kiro do).
     * E.g. WEB_SEARCH({"query":"who is cm of delhi"}) → "Searching the web for 'who is cm of delhi'..."
     */
    private fun synthesizeNarration(toolName: String, argsJson: String): String? {
        val args = try { JSONObject(argsJson) } catch (_: Exception) { null }
        return when (toolName.uppercase()) {
            "READ_FILE" -> "Let me read ${args?.optString("path", "the file") ?: "the file"}."
            "READ_LINES" -> "Reading lines from ${args?.optString("path", "a file") ?: "a file"}."
            "EDIT_FILE" -> "Editing ${args?.optString("path", "the file") ?: "the file"}."
            "WRITE_FILE", "CREATE_FILE" -> "Creating ${args?.optString("path", "a file") ?: "a file"}."
            "DELETE_FILE" -> "Deleting ${args?.optString("path", "a file") ?: "a file"}."
            "WEB_SEARCH" -> "Searching the web for '${args?.optString("query", "") ?: ""}'."
            "IMAGE_SEARCH" -> "Searching for images of '${args?.optString("query", "") ?: ""}'."
            "READ_URL" -> "Reading ${args?.optString("url", "a page") ?: "a page"}."
            "FETCH_URL" -> "Fetching ${args?.optString("url", "a URL") ?: "a URL"}."
            "GREP" -> "Searching code for '${args?.optString("pattern", "") ?: ""}'."
            "SEARCH_CODE", "CODEBASE_SEARCH" -> "Searching the codebase."
            "LIST_FILES" -> "Checking the project structure."
            "RUN_PYTHON" -> "Running Python code."
            "CLOUD_SHELL" -> "Running a command in the cloud."
            "BROWSER_OPEN" -> "Opening ${args?.optString("url", "a page") ?: "a page"} in the browser."
            "BROWSER_CLICK" -> "Clicking an element."
            "BROWSER_TYPE" -> "Typing into the page."
            "PDF_CREATE" -> "Creating a PDF document."
            "GENERATE_IMAGE" -> "Generating an image."
            "SCREENSHOT" -> "Taking a screenshot."
            "GH_PUSH" -> "Pushing to GitHub."
            "GH_BUILD_APK" -> "Starting a cloud build."
            "SCAFFOLD_ANDROID" -> "Setting up the Android project."
            "PLAN" -> null // Plan is shown as its own card
            "ANSWER" -> null
            "DONE" -> null
            "MULTI_EDIT" -> "Making multiple edits to ${args?.optString("path", "a file") ?: "a file"}."
            "BULK_EDIT" -> "Replacing text across all files."
            "SYMBOL_SEARCH" -> "Searching for symbol '${args?.optString("symbol", "") ?: ""}'."
            "HTTP_REQUEST" -> "Testing an HTTP endpoint."
            "EXPORT_TO_DEVICE" -> "Saving to Downloads."
            else -> "Running ${toolName.lowercase().replace("_", " ")}."
        }
    }

    /** Builds the OpenAI-compatible messages array, using the vision content format when images exist. */
    private fun buildMessagesArray(messages: List<ApiMessage>): JSONArray {
        val arr = JSONArray()
        for (m in messages) {
            val obj = JSONObject()
            obj.put("role", m.role)
            if (m.images.isEmpty()) {
                obj.put("content", m.text)
            } else {
                val content = JSONArray()
                // Text part first (even if blank, some APIs require it)
                content.put(JSONObject().put("type", "text").put("text", m.text.ifBlank { "What's in this image?" }))
                for (img in m.images) {
                    val imageUrl = JSONObject().put("url", img)
                    // Add detail field for better compatibility
                    imageUrl.put("detail", "auto")
                    content.put(
                        JSONObject()
                            .put("type", "image_url")
                            .put("image_url", imageUrl)
                    )
                }
                obj.put("content", content)
            }
            arr.put(obj)
        }
        return arr
    }

    fun streamChatVision(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        cache: Boolean = false,
    ): Flow<StreamDelta> {
        if (baseUrl.contains("generativelanguage.googleapis.com") && !baseUrl.contains("/openai")) {
            return streamGemini(apiKey, model, messages.map { it.role to it.text })
        }
        // Freebuff / Codebuff — native Kotlin client.
        if (isFreebuffUrl(baseUrl)) {
            return FreebuffManager.stream(apiKey, model, messages.map { it.role to it.text })
        }
        // 9Router-style combo: multi-provider members with automatic failover
        if (ComboRouter.isCombo(model)) {
            return streamComboChat(model, messages, cache)
        }
        // Wrap in silent key-rotation: retry the stream with the next key on auth/rate errors.
        // After the primary provider is exhausted (with NO output yet), fail over to other
        // providers via the cross-provider fallback chain. Only throws after everything fails.
        // Response cache fast-path: plain (no-image) requests can be served instantly from memory.
        val cacheKey = if (cache && messages.all { it.images.isEmpty() })
            ResponseCache.keyFor(model, messages.map { it.role to it.text }) else null
        if (cacheKey != null) {
            ResponseCache.get(cacheKey)?.let { hit ->
                return flow { emit(StreamDelta(text = hit, reasoning = null)) }
            }
        }
        return channelFlow {
            val budget = maxOf(2, ApiConfig.getKeyCount(baseUrl))
            var currentKey = apiKey
            var lastEx: Exception? = null
            val full = StringBuilder()
            var emittedAny = false
            for (attempt in 0 until budget) {
                var authFail = false
                var rateFail = false
                try {
                    streamChatVisionRaw(baseUrl, currentKey, model, messages).collect {
                        if (!it.text.isNullOrEmpty()) { emittedAny = true; full.append(it.text) }
                        send(it)
                    }
                    if (cacheKey != null) ResponseCache.put(cacheKey, full.toString())
                    return@channelFlow // success
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    authFail = msg.contains("auth_fail")
                    rateFail = msg.contains("rate_fail")
                    lastEx = e
                }
                // Once tokens have been emitted we can't safely switch keys/providers mid-stream.
                if (emittedAny) break
                if (authFail) ApiConfig.markKeyBad(baseUrl, currentKey)
                if (rateFail) ApiConfig.markKeyCoolDown(baseUrl, currentKey)
                val next = ApiConfig.getNextKey(baseUrl)
                if (next != null && next != currentKey) {
                    currentKey = next
                } else {
                    break
                }
                if (rateFail) delay(500)
            }
            // Cross-provider fallback — only when the primary produced no output at all.
            if (!emittedAny) {
                for (alt in ApiConfig.fallbackChain(baseUrl)) {
                    try {
                        streamChatVisionRaw(alt.baseUrl, alt.apiKey, alt.model, messages).collect {
                            if (!it.text.isNullOrEmpty()) { emittedAny = true; full.append(it.text) }
                            send(it)
                        }
                        if (cacheKey != null) ResponseCache.put(cacheKey, full.toString())
                        return@channelFlow
                    } catch (e: Exception) {
                        lastEx = e
                        if (emittedAny) break
                    }
                }
            }
            throw lastEx ?: Exception("Couldn't reach the AI service. Please try again.")
        }
    }

    /**
     * Combo virtual model: try each member (provider+model) in strategy order.
     * On auth/rate/stream failure with no tokens, advance to the next member.
     */
    private fun streamComboChat(
        comboId: String,
        messages: List<ApiMessage>,
        cache: Boolean
    ): Flow<StreamDelta> = channelFlow {
        val chain = ComboRouter.resolveChain(comboId)
        if (chain.isEmpty()) {
            throw Exception("Combo '$comboId' has no healthy members. Check Admin → Combos.")
        }
        val cacheKey = if (cache && messages.all { it.images.isEmpty() })
            ResponseCache.keyFor(comboId, messages.map { it.role to it.text }) else null
        if (cacheKey != null) {
            ResponseCache.get(cacheKey)?.let { hit ->
                send(StreamDelta(text = hit, reasoning = null))
                return@channelFlow
            }
        }
        var lastEx: Exception? = null
        val full = StringBuilder()
        chain.forEachIndexed { idx, (member, resolved) ->
            var emittedAny = false
            try {
                // Full key rotation on this member's provider, then next combo member
                streamChatVision(
                    resolved.baseUrl, resolved.apiKey, member.model, messages, cache = false
                ).collect {
                    if (!it.text.isNullOrEmpty()) {
                        emittedAny = true
                        full.append(it.text)
                    }
                    send(it)
                }
                if (full.isNotBlank() || emittedAny) {
                    ComboRouter.markMemberOk(comboId, idx)
                    if (cacheKey != null && full.isNotBlank()) ResponseCache.put(cacheKey, full.toString())
                    return@channelFlow
                }
                // Empty success → try next member
                ComboRouter.markMemberFail(comboId, member, rateLimited = false)
            } catch (e: Exception) {
                lastEx = e
                val msg = e.message ?: ""
                if (emittedAny) {
                    // Partial output — stop (can't safely switch mid-stream)
                    if (full.isNotBlank()) return@channelFlow
                }
                val rate = msg.contains("rate_fail") || msg.contains("429")
                ComboRouter.markMemberFail(comboId, member, rateLimited = rate)
                if (rate) delay(400)
            }
        }
        // Exhausted combo members → optional cross-provider fallback using last base
        if (full.isEmpty()) {
            val lastBase = chain.lastOrNull()?.second?.baseUrl ?: ""
            for (alt in ApiConfig.fallbackChain(lastBase, agent = ComboRouter.find(comboId)?.agentMode == true)) {
                try {
                    streamChatVisionRaw(alt.baseUrl, alt.apiKey, alt.model, messages).collect {
                        if (!it.text.isNullOrEmpty()) full.append(it.text)
                        send(it)
                    }
                    if (cacheKey != null && full.isNotBlank()) ResponseCache.put(cacheKey, full.toString())
                    return@channelFlow
                } catch (e: Exception) {
                    lastEx = e
                }
            }
        }
        throw lastEx ?: Exception("Combo '$comboId' failed on all members.")
    }

    private fun streamChatVisionRaw(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<ApiMessage>,
        tools: JSONArray? = null  // Z-AI LEVEL: structured function calling support
    ): Flow<StreamDelta> = callbackFlow {
        val url = "${baseUrl.trimEnd('/')}/chat/completions"

        val requestBody = JSONObject()
        requestBody.put("model", model)
        requestBody.put("messages", buildMessagesArray(messages))
        requestBody.put("stream", true)
        // Z-AI LEVEL: Send structured tool definitions when provided.
        // Providers that support function calling will return tool_calls in the response.
        // Providers that don't support it will ignore the `tools` parameter.
        if (tools != null && tools.length() > 0) {
            requestBody.put("tools", tools)
            requestBody.put("tool_choice", "auto")
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = requestBody.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        val listener = object : EventSourceListener() {
            val toolCallsAccumulator = HashMap<Int, Pair<StringBuilder, StringBuilder>>() // index -> (name, args)
            @Volatile var flushed = false
            @Volatile var emittedToolXml = false   // did we emit a complete <tool_call> block?

            /** Build synthetic <tool_call> XML from accumulated native tool_calls, emit it, and
             *  clear the accumulator. MUST run whether the stream ends via an explicit [DONE]
             *  sentinel, a bare connection close (onClosed), OR a mid-flight reset (onFailure) —
             *  otherwise providers that send the tool call and then drop/reset the connection lose
             *  the tool call entirely, and the agent either stalls or (for the browser) never runs
             *  the action. Wrapped so it can NEVER throw back into the OkHttp listener. */
            @Synchronized fun flushToolCalls() {
                if (flushed || toolCallsAccumulator.isEmpty()) return
                flushed = true
                try {
                    val sb = StringBuilder()
                    val narrations = toolCallsAccumulator.entries.sortedBy { it.key }.mapNotNull { (_, pair) ->
                        synthesizeNarration(pair.first.toString().trim(), pair.second.toString().trim())
                    }.distinct()
                    if (narrations.isNotEmpty()) {
                        sb.append(narrations.take(3).joinToString("\n\n"))
                        if (narrations.size > 3) sb.append("\n\n(+${narrations.size - 3} more actions)")
                        sb.append("\n\n")
                    }
                    for ((_, pair) in toolCallsAccumulator.entries.sortedBy { it.key }) {
                        val toolName = pair.first.toString().trim()
                        val argsStr = pair.second.toString().trim()
                        if (toolName.isBlank()) continue
                        sb.append("<tool_call>$toolName")
                        try {
                            val argsObj = JSONObject(argsStr)
                            val keys = argsObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                sb.append("<$k>${argsObj.optString(k, "")}</$k>")
                            }
                        } catch (_: Exception) {
                            if (argsStr.isNotBlank()) sb.append("<arg_value>$argsStr</arg_value>")
                        }
                        sb.append("</tool_call>\n")
                        emittedToolXml = true
                    }
                    toolCallsAccumulator.clear()
                    if (sb.isNotEmpty()) trySend(StreamDelta(text = sb.toString(), reasoning = null))
                } catch (_: Exception) { /* never break the stream on flush */ }
            }

            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (data.trim() == "[DONE]") {
                    flushToolCalls()
                    close()
                    return
                }
                try {
                    val json = JSONObject(data.trim())
                    val choices = json.optJSONArray("choices")
                    if (choices != null && choices.length() > 0) {
                        val choice = choices.getJSONObject(0)
                        val delta = choice.optJSONObject("delta") ?: return

                        // Handle native tool_calls delta (function calling)
                        val toolCalls = delta.optJSONArray("tool_calls")
                        if (toolCalls != null) {
                            for (i in 0 until toolCalls.length()) {
                                val tc = toolCalls.getJSONObject(i)
                                val idx = tc.optInt("index", i)
                                val fn = tc.optJSONObject("function")
                                if (fn != null) {
                                    val pair = toolCallsAccumulator.getOrPut(idx) { StringBuilder() to StringBuilder() }
                                    // Name only comes in the first delta for each tool call
                                    if (fn.has("name") && !fn.isNull("name")) {
                                        val name = fn.getString("name")
                                        if (name.isNotBlank()) pair.first.append(name)
                                    }
                                    // Arguments come as incremental string chunks across many deltas
                                    if (fn.has("arguments") && !fn.isNull("arguments")) {
                                        val arguments = fn.getString("arguments")
                                        pair.second.append(arguments)
                                    }
                                }
                            }
                            return // tool_calls deltas don't have content
                        }

                        val reasoningContent = if (delta.isNull("reasoning_content")) null
                            else delta.optString("reasoning_content", "").let {
                                if (it.isEmpty() || it == "null") null else it
                            }
                        val textContent = if (delta.isNull("content")) null
                            else delta.optString("content", "").let {
                                if (it.isEmpty() || it == "null") null else it
                            }
                        if (reasoningContent != null || textContent != null) {
                            trySend(StreamDelta(text = textContent, reasoning = reasoningContent))
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // The model may stream a COMPLETE tool call (e.g. BROWSER_OPEN) and then the
                // connection resets ("stream was reset: INTERNAL_ERROR") instead of closing
                // cleanly. Don't lose it: flush what we accumulated first. If that produced a
                // real tool call, finish NORMALLY so the agent runs the action (this is why the
                // browser sometimes "wouldn't open"). Only surface the error if we got nothing.
                flushToolCalls()
                if (emittedToolXml) { close(); return }
                val code = response?.code
                val ex = when {
                    code == 401 || code == 403 || code == 422 -> Exception("auth_fail:$code")
                    code == 429 -> Exception("rate_fail:$code")
                    else -> t ?: Exception("stream_fail:$code")
                }
                close(ex)
            }

            override fun onClosed(eventSource: EventSource) { flushToolCalls(); close() }
        }

        val eventSource = EventSources.createFactory(client()).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    // ---------------------------------------------------------------- Gemini (Google OAuth, native format)

    /** Builds the native Gemini generateContent request body from chat messages. */
    private fun buildGeminiBody(messages: List<Pair<String, String>>, model: String): JSONObject {
        val contents = JSONArray()
        var systemInstruction = ""
        for ((role, text) in messages) {
            if (role == "system") { systemInstruction += (if (systemInstruction.isNotEmpty()) "\n\n" else "") + text; continue }
            val geminiRole = if (role == "assistant") "model" else "user"
            contents.put(JSONObject()
                .put("role", geminiRole)
                .put("parts", JSONArray().put(JSONObject().put("text", text)))
            )
        }
        return JSONObject().apply {
            put("contents", contents)
            if (systemInstruction.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemInstruction))))
            put("generationConfig", JSONObject().put("temperature", 1.0))
        }
    }

    /** Streams Gemini generateContent (SSE via streamGenerateContent, native format). */
    fun streamGemini(token: String, model: String, messages: List<Pair<String, String>>): Flow<StreamDelta> = callbackFlow {
        val body = buildGeminiBody(messages, model)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse"
        val request = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .build()
        val call = client().newCall(request)
        val worker = launch(Dispatchers.IO) {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        val b = try { resp.body?.string()?.take(400) ?: "" } catch (_: Exception) { "" }
                        close(Exception("Gemini HTTP ${resp.code}: $b")); return@launch
                    }
                    val source = resp.body?.source()
                    if (source == null) { close(Exception("Empty Gemini response")); return@launch }
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.substringAfter("data:").trim()
                        if (data.isEmpty()) continue
                        try {
                            val j = JSONObject(data)
                            val candidates = j.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                                if (parts != null) for (k in 0 until parts.length()) {
                                    val text = parts.getJSONObject(k).optString("text", "")
                                    if (text.isNotEmpty()) trySend(StreamDelta(text = text, reasoning = null))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    close()
                }
            } catch (e: Exception) { close(e) }
        }
        awaitClose { call.cancel(); worker.cancel() }
    }

    /** Non-streaming Gemini completion. */
    private fun geminiComplete(token: String, model: String, messages: List<Pair<String, String>>): Result<String> {
        return try {
            val body = buildGeminiBody(messages, model)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
            val request = Request.Builder().url(url)
                .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()
            client().newCall(request).execute().use { r ->
                val s = r.body?.string() ?: ""
                if (!r.isSuccessful) return Result.failure(Exception("Gemini HTTP ${r.code}: ${s.take(200)}"))
                val j = JSONObject(s)
                val candidates = j.optJSONArray("candidates")
                val sb = StringBuilder()
                if (candidates != null && candidates.length() > 0) {
                    val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                    if (parts != null) for (k in 0 until parts.length()) {
                        sb.append(parts.getJSONObject(k).optString("text", ""))
                    }
                }
                val text = sb.toString()
                if (text.isBlank()) Result.failure(Exception("Gemini returned empty response."))
                else Result.success(text)
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    
}
