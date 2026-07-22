package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.math.max

/**
 * Kotlin port of freebuff2api Python client — interacts DIRECTLY with codebuff.com
 * without needing the Python server. Manages sessions, ads, agent runs, and streams
 * chat completions in SSE format.
 *
 * Thread-safe. Designed to be instantiated once and reused across the app.
 *
 * USAGE:
 * val fb = FreebuffClient(token = "your-freebuff-bearer-token")
 * fb.streamChat(messages, model = "deepseek/deepseek-v4-flash").collect { delta -> ... }
 */
class FreebuffClient(
 private val bearerToken: String,
 private val apiBaseUrl: String = "https://www.codebuff.com",
 private val sessionId: String = UUID.randomUUID().toString(),
 private val clientId: String = UUID.randomUUID().toString().replace("-", "").take(11),
 private val debug: Boolean = false
) {
 // ── Companion / Constants ──
 companion object {
 // Codebuff gates the chat endpoint on the CLI version embedded in this User-Agent:
 // "ai-sdk/openai-compatible/<codebuff-cli-version>/codebuff". An out-of-date version is
 // rejected with HTTP 426 `freebuff_update_required`. We therefore resolve the version
 // DYNAMICALLY at runtime (npm registry) and cache it, falling back to a known-good recent
 // version so the app keeps working even if the lookup fails or there's no network yet.
 private const val FALLBACK_CLI_VERSION = "1.0.682"
 @Volatile private var cachedCliVersion: String? = null

 /** Current codebuff CLI version (cached → fallback). */
 fun cliVersion(): String = cachedCliVersion ?: FALLBACK_CLI_VERSION

 /** The canonical User-Agent for all codebuff.com requests, version-aware. */
 fun cbUserAgent(): String = "ai-sdk/openai-compatible/${cliVersion()}/codebuff"

 private val versionClient = OkHttpClient.Builder()
 .connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()

 /** Fetch the latest codebuff CLI version from the npm registry and cache it.
 *  Best-effort and safe to call repeatedly; failures keep the existing value. */
 fun refreshCliVersion() {
 try {
 val req = Request.Builder()
 .url("https://registry.npmjs.org/codebuff/latest")
 .header("Accept", "application/json")
 .build()
 versionClient.newCall(req).execute().use { resp ->
 val body = resp.body?.string()
 if (resp.isSuccessful && body != null) {
 val v = JSONObject(body).optString("version", "")
 if (v.isNotBlank()) cachedCliVersion = v
 }
 }
 } catch (_: Exception) { /* keep fallback */ }
 }

 private const val UA_BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

 // Ad providers tried in order
 private val AD_PROVIDERS = listOf("gravity", "zeroclick")

 // Upstream chat keys forwarded to the payload
 private val UPSTREAM_CHAT_KEYS = setOf(
 "frequency_penalty", "logit_bias", "logprobs",
 "max_completion_tokens", "max_tokens", "metadata", "modalities",
 "parallel_tool_calls", "presence_penalty", "reasoning_effort",
 "response_format", "seed", "service_tier", "stop", "store",
 "stream_options", "temperature", "tool_choice", "tools",
 "top_logprobs", "top_p", "user"
 )

 @Volatile private var versionRefreshStarted = false
 /** Kick off a one-time background refresh of the codebuff CLI version. */
 fun ensureVersionRefreshed() {
 if (versionRefreshStarted) return
 synchronized(this) {
 if (versionRefreshStarted) return
 versionRefreshStarted = true
 }
 Thread {
 try { refreshCliVersion() } catch (_: Exception) {}
 }.start()
 }
 }

 init {
 // Resolve the current codebuff CLI version in the background so the chat
 // endpoint's version gate (HTTP 426 freebuff_update_required) is satisfied.
 ensureVersionRefreshed()
 }

 private val client = OkHttpClient.Builder()
 .connectTimeout(15, TimeUnit.SECONDS)
 .readTimeout(90, TimeUnit.SECONDS) // long reads for SSE
 .writeTimeout(30, TimeUnit.SECONDS)
 .build()

 private val shortClient = OkHttpClient.Builder()
 .connectTimeout(10, TimeUnit.SECONDS)
 .readTimeout(20, TimeUnit.SECONDS)
 .writeTimeout(10, TimeUnit.SECONDS)
 .build()

 // ── Session cache ──
 @Volatile private var cachedSession: FreebuffSessionData? = null
 private val sessionLock = Any()

 // ── Agent validation ──
 @Volatile private var agentsValidated = false
 private val validateLock = Any()

 // ── Ad impression cooldown ──
 @Volatile private var lastAdAttempt: Long = 0L
 private val AD_COOLDOWN_MS = 30_000L

 // ── Internal helpers ──

 private val apiUrl: String get() = apiBaseUrl.trimEnd('/')

 private fun headers(jsonBody: Boolean = false, requireAuth: Boolean = true,
 extra: Map<String, String>? = null, ua: String = cbUserAgent()): Map<String, String> {
 // IMPORTANT: do NOT set Accept-Encoding manually. OkHttp adds `Accept-Encoding: gzip`
 // itself and then transparently decompresses the response. If we set it by hand,
 // OkHttp assumes the caller will handle decoding and returns the raw gzip bytes —
 // JSONObject() then fails and every call (session, runs, chat) silently returns null
 // ("Failed to create session"). Likewise we let OkHttp manage the Host header.
 val h = mutableMapOf(
 "Accept" to "*/*",
 "Connection" to "keep-alive",
 "User-Agent" to ua
 )
 if (requireAuth) h["Authorization"] = "Bearer $bearerToken"
 if (jsonBody) h["Content-Type"] = "application/json"
 extra?.let { h.putAll(it) }
 return h
 }

 /** Generic JSON request to the codebuff API. Returns the response body as JSONObject or JSONArray. */
 private fun jsonRequest(method: String, path: String, body: JSONObject? = null,
 hdrs: Map<String, String>? = null, useShort: Boolean = false): JSONObject? {
 val url = "$apiUrl$path"
 val finalHeaders = hdrs ?: headers(jsonBody = body != null)
 val requestBuilder = Request.Builder().url(url)
 for ((k, v) in finalHeaders) requestBuilder.addHeader(k, v)
 if (body != null) {
 val reqBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 requestBuilder.method(method, reqBody)
 } else {
 requestBuilder.method(method, null)
 }
 val httpClient = if (useShort) shortClient else client
 try {
 val response = httpClient.newCall(requestBuilder.build()).execute()
 val code = response.code
 val responseBody = response.body?.string() ?: ""
 if (debug) {
 android.util.Log.d("Freebuff", "$method $url → $code")
 if (responseBody.length < 2000) android.util.Log.d("Freebuff", "Response: $responseBody")
 }
 if (code >= 400) {
 if (debug) android.util.Log.e("Freebuff", "HTTP $code: $responseBody")
 return null
 }
 if (responseBody.isBlank()) return null
 return JSONObject(responseBody)
 } catch (e: Exception) {
 if (debug) android.util.Log.e("Freebuff", "Request error: ${e.message}")
 return null
 }
 }

 // ── Public API: Session Management ──

 /**
 * Get or create a Freebuff session for the given model.
 * Returns the session data or null on failure.
 */
 fun ensureSession(model: String, forceNew: Boolean = false): FreebuffSessionData? {
 synchronized(sessionLock) {
 // Try cached fresh session
 if (!forceNew && cachedSession != null && cachedSession!!.isFresh) {
 // Verify it's still active upstream
 val check = jsonRequest("GET", "/api/v1/freebuff/session",
 hdrs = headers(extra = mapOf("x-freebuff-instance-id" to cachedSession!!.instanceId)))
 if (check?.optString("status") == "active" &&
 (check.isNull("model") || check.optString("model") == model)) {
 cachedSession!!.remainingMs = check.optInt("remainingMs", -1).let { if (it < 0) null else it }
 return cachedSession
 }
 // Mismatch or inactive — clear cache
 cachedSession = null
 }

 // Delete any active session with wrong model
 deleteLockedSession(model)

 // Request ads and streak to satisfy the Freebuff quota system
 requestAdsAndStreak(surface = "waiting_room")

 // Create new session
 val body = JSONObject()
 val data = jsonRequest("POST", "/api/v1/freebuff/session",
 body = body,
 hdrs = headers(jsonBody = true, extra = mapOf("x-freebuff-model" to model)))
 if (data == null) return null

 // Handle queued sessions
 if (data.optString("status") == "queued") {
 return waitForActiveSession(data, model)
 }

 val instanceId = data.optString("instanceId", "")
 if (data.optString("status") != "active" || instanceId.isBlank()) {
 if (debug) android.util.Log.e("Freebuff", "Session not active: $data")
 return null
 }
 val session = FreebuffSessionData(
 instanceId = instanceId,
 model = data.optString("model", model),
 expiresAt = data.optString("expiresAt", null),
 remainingMs = if (data.has("remainingMs")) data.optInt("remainingMs", -1).let { if (it < 0) null else it } else null
 )
 cachedSession = session
 return session
 }
 }

 private fun waitForActiveSession(data: JSONObject, model: String): FreebuffSessionData? {
 val instanceId = data.optString("instanceId", "")
 if (instanceId.isBlank()) return null

 val deadline = System.currentTimeMillis() + 60_000L
 var current = data
 var attempts = 0

 while (current.optString("status") == "queued") {
 if (System.currentTimeMillis() >= deadline) {
 if (debug) android.util.Log.e("Freebuff", "Session queue timeout")
 return null
 }
 if (attempts > 0) {
 val estWait = current.optInt("estimatedWaitMs", 0)
 val sleepMs = min(max(estWait.toLong(), 250L), 2000L)
 try { Thread.sleep(sleepMs) } catch (_: InterruptedException) {}
 }
 current = jsonRequest("GET", "/api/v1/freebuff/session",
 hdrs = headers(extra = mapOf("x-freebuff-instance-id" to instanceId))) ?: return null
 attempts++
 }

 val finalId = current.optString("instanceId", instanceId)
 if (current.optString("status") != "active" || finalId.isBlank()) return null
 val session = FreebuffSessionData(
 instanceId = finalId,
 model = current.optString("model", model),
 expiresAt = current.optString("expiresAt", null),
 remainingMs = current.optInt("remainingMs", -1).let { if (it < 0) null else it }
 )
 cachedSession = session
 return session
 }

 private fun deleteLockedSession(requestedModel: String): FreebuffSessionData? {
 val data = jsonRequest("GET", "/api/v1/freebuff/session") ?: return null
 if (data.optString("status") != "active") return null

 val currentModel = data.optString("model", "")
 val instanceId = data.optString("instanceId", "")
 if (currentModel == requestedModel && instanceId.isNotBlank()) {
 val session = FreebuffSessionData(
 instanceId = instanceId, model = currentModel,
 expiresAt = data.optString("expiresAt", null),
 remainingMs = data.optInt("remainingMs", -1).let { if (it < 0) null else it }
 )
 cachedSession = session
 return session
 }
 if (currentModel.isNotBlank() && currentModel != requestedModel) {
 // Delete the mismatched session
 jsonRequest("DELETE", "/api/v1/freebuff/session")
 cachedSession = null
 }
 return null
 }

 // ── Ads & Streak ──

 private fun requestAdsAndStreak(messages: List<Pair<String, String>>? = null, surface: String? = null) {
 // Rate-limit ad requests
 val now = System.currentTimeMillis()
 if (now - lastAdAttempt < AD_COOLDOWN_MS) return
 lastAdAttempt = now

 for (provider in AD_PROVIDERS) {
 try {
 val adsData = requestAds(provider, messages, surface)
 val ads = adsData?.optJSONArray("ads")
 val ad = ads?.optJSONObject(0)
 if (ad == null) continue

 // Get streak info
 getStreak()

 // Report impressions
 val impressionIds = ad.optJSONArray("impressionIds")
 if (impressionIds != null) {
 reportZeroclickImpressions(impressionIds)
 }
 reportCodebuffImpression(ad.optString("impUrl", ""))
 return // Success with first provider
 } catch (_: Exception) {}
 }
 }

 private fun requestAds(provider: String, messages: List<Pair<String, String>>?,
 surface: String?): JSONObject? {
 val body = JSONObject()
 body.put("provider", provider)
 body.put("messages", buildAdMessages(messages))
 body.put("sessionId", sessionId)
 body.put("device", JSONObject().apply {
 put("os", "android")
 put("timezone", java.util.TimeZone.getDefault().id)
 put("locale", java.util.Locale.getDefault().toLanguageTag())
 })
 body.put("userAgent", UA_BROWSER)
 if (surface != null) body.put("surface", surface)

 return jsonRequest("POST", "/api/v1/ads", body = body,
 hdrs = headers(jsonBody = true, ua = cbUserAgent()))
 }

 private fun buildAdMessages(messages: List<Pair<String, String>>?): JSONArray {
 val arr = JSONArray()
 messages?.forEach { (role, content) ->
 arr.put(JSONObject().apply {
 put("role", if (role == "developer") "system" else role)
 put("content", content)
 })
 }
 return arr
 }

 private fun reportZeroclickImpressions(ids: JSONArray) {
 val idList = mutableListOf<String>()
 for (i in 0 until ids.length()) idList.add(ids.optString(i))
 if (idList.isEmpty()) return
 val body = JSONObject().put("ids", JSONArray(idList))
 // Zeroclick has a separate base URL
 try {
 val url = "https://zeroclick.dev/api/v2/impressions"
 val reqBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
 val request = Request.Builder().url(url)
 .post(reqBody)
 .addHeader("Content-Type", "application/json")
 .addHeader("Accept", "*/*")
 .addHeader("User-Agent", cbUserAgent())
 .build()
 shortClient.newCall(request).execute().use { resp ->
 if (debug) android.util.Log.d("Freebuff", "Zeroclick impression → ${resp.code}")
 }
 } catch (_: Exception) {}
 }

 private fun reportCodebuffImpression(impUrl: String) {
 if (impUrl.isBlank()) return
 jsonRequest("POST", "/api/v1/ads/impression",
 body = JSONObject().apply {
 put("impUrl", impUrl)
 put("mode", "LITE")
 },
 hdrs = headers(jsonBody = true, ua = cbUserAgent()))
 }

 private fun getStreak(): JSONObject? {
 val data = jsonRequest("GET", "/api/v1/freebuff/streak")
  if (debug && data != null) {
  android.util.Log.d("Freebuff", "Streak: ${data.optInt("streak", 0)}, today=${data.optInt("todayUsed", 0)}")
  }
  return data
 }

 // ── Agent Runs ──

  fun startRun(agentId: String, ancestorRunIds: List<String>? = null): String? {
 val body = JSONObject().apply {
 put("action", "START")
 put("agentId", agentId)
 put("ancestorRunIds", JSONArray(ancestorRunIds ?: emptyList<String>()))
 }
 val data = jsonRequest("POST", "/api/v1/agent-runs", body = body,
 hdrs = headers(jsonBody = true))
 val runId = data?.optString("runId", "")
 return if (runId.isNullOrBlank()) null else runId
 }

 fun recordRunStep(runId: String, stepNumber: Int, childRunIds: List<String>? = null,
 messageId: String? = null, startTime: String? = null) {
 val body = JSONObject().apply {
 put("stepNumber", stepNumber)
 put("credits", 0)
 put("childRunIds", JSONArray(childRunIds ?: emptyList<String>()))
 put("messageId", messageId ?: JSONObject.NULL)
 put("status", "completed")
 put("startTime", startTime ?: "")
 }
 jsonRequest("POST", "/api/v1/agent-runs/$runId/steps", body = body,
 hdrs = headers(jsonBody = true), useShort = true)
 }

 fun finishRun(runId: String, totalSteps: Int = 2) {
 val body = JSONObject().apply {
 put("action", "FINISH")
 put("runId", runId)
 put("status", "completed")
 put("totalSteps", totalSteps)
 put("directCredits", 0)
 put("totalCredits", 0)
 }
 jsonRequest("POST", "/api/v1/agent-runs", body = body,
 hdrs = headers(jsonBody = true), useShort = true)
 }

 // ── Agent validation ──

 fun ensureAgentsValidated() {
 if (agentsValidated) return
 synchronized(validateLock) {
 if (agentsValidated) return
 val payload = buildAgentValidationPayload()
 try {
 jsonRequest("POST", "/api/api/agents/validate", body = payload,
 hdrs = headers(jsonBody = true, requireAuth = false))
 } catch (_: Exception) {}
 agentsValidated = true
 }
 }

 private fun buildAgentValidationPayload(): JSONObject {
 val modelsByAgent = mutableMapOf<String, FreebuffModel>()
 val spawnableByAgent = mutableMapOf<String, MutableSet<String>>()
 for (model in FreebuffModels.ALL_MODELS) {
 if (!modelsByAgent.containsKey(model.agentId)) modelsByAgent[model.agentId] = model
 spawnableByAgent.getOrPut(model.agentId) { mutableSetOf() }
 .add(FreebuffModels.contextPrunerAgentId())
 model.parentAgentId?.let {
 spawnableByAgent.getOrPut(it) { mutableSetOf() }.add(model.agentId)
 }
 }
 val definitions = JSONArray()
 for ((agentId, model) in modelsByAgent) {
 definitions.put(JSONObject().apply {
 put("id", agentId)
 put("publisher", "codebuff")
 put("model", model.upstreamId)
 put("displayName", "Freebuff ${model.upstreamId}")
 put("spawnerPrompt", "Freebuff OpenAI-compatible orchestrator")
 put("inputSchema", JSONObject().apply {
 put("prompt", JSONObject().apply {
 put("type", "string")
 put("description", "A coding task to complete")
 })
 put("params", JSONObject().apply {
 put("type", "object")
 put("properties", JSONObject())
 put("required", JSONArray())
 })
 })
 put("outputMode", "last_message")
 put("includeMessageHistory", true)
 val spawnable = spawnableByAgent[agentId]?.sorted() ?: emptyList()
 put("toolNames", JSONArray(spawnable.map { "spawn_agents" }))
 put("spawnableAgents", JSONArray(spawnable))
 put("systemPrompt", "Act as a helpful coding assistant.")
 })
 }
 definitions.put(JSONObject().apply {
 put("id", FreebuffModels.contextPrunerAgentId())
 put("publisher", "codebuff")
 put("model", FreebuffModels.DEFAULT_MODEL.id)
 put("displayName", "Context Pruner")
 put("spawnerPrompt", "Freebuff context pruner")
 put("inputSchema", JSONObject())
 put("outputMode", "last_message")
 put("includeMessageHistory", false)
 put("toolNames", JSONArray())
 put("spawnableAgents", JSONArray())
 put("systemPrompt", "Reduce context usage.")
 })
 return JSONObject().put("agentDefinitions", definitions)
 }

 // ── Chat Completions (Streaming) ──

 /**
 * Main entry point for streaming chat completions through Freebuff.
 * Manages the full lifecycle: session → ads → agent validation → agent runs → SSE stream.
 *
 * Returns a Flow<StreamDelta> compatible with AhamAI's ChatScreen.
 */
 fun streamChat(
 modelId: String,
 messages: List<Pair<String, String>>,
 extraParams: Map<String, Any?>? = null
 ): Flow<StreamDelta> {
 val modelConfig = FreebuffModels.resolve(modelId)

 return channelFlow {
 // 1. Ensure we have a session
 val session = ensureSession(modelConfig.sessionId) ?: run {
 send(StreamDelta(text = "Failed to create Freebuff session", reasoning = null))
 return@channelFlow
 }

 // 2. Request ads for the session quota
 requestAdsAndStreak(messages)

 // 3. Validate agents
 ensureAgentsValidated()

 // 4. Start agent run chain
 val run = runFreebuffChain(modelConfig) ?: run {
 send(StreamDelta(text = "Failed to start Freebuff agent run", reasoning = null))
 return@channelFlow
 }

 // 5. Build upstream payload
 val payload = buildUpstreamPayload(
 messages = messages,
 session = session,
 runId = run.payloadRunId,
 upstreamModelId = modelConfig.upstreamId,
 extra = extraParams
 )

 if (debug) {
 android.util.Log.d("Freebuff", "Stream payload model=${modelConfig.id} session=${session.instanceId} run=${run.runId}")
 }

 // 6. Stream the SSE response
 try {
 streamChatSSE(payload, run).collect { delta -> send(delta) }
 } catch (e: Exception) {
 if (debug) android.util.Log.e("Freebuff", "Stream error: ${e.message}")
 send(StreamDelta(text = "\n\nStream error: ${e.message}", reasoning = null))
 } finally {
 // 7. Finalize the run asynchronously — reference does a single FinishRun.
 launch(Dispatchers.IO) {
 try {
 finishRun(run.runId, 2)
 } catch (_: Exception) {}
 }
 }
 }
 }

 /**
 * Builds the run chain: optionally a parent run → context pruner → chat run.
 * Returns the FreebuffRunData required for the streaming request.
 */
 private fun runFreebuffChain(modelConfig: FreebuffModel): FreebuffRunData? {
 val startedAt = isoNow()
 // Match the reference proxy (Quorinex/Freebuff2API) exactly: a single START run on
 // the model's root agent. No context-pruner child and no ancestorRunIds — those
 // agents are NOT on the free-mode allowlist (see free-agents.ts) and only add
 // failure points. FinishRun is sent once after the stream ends.
 val runId = startRun(modelConfig.agentId) ?: return null
 return FreebuffRunData(
 runId = runId,
 agentId = modelConfig.agentId,
 startedAt = startedAt
 )
 }

 /**
 * Builds the upstream OpenAI-compatible chat completions payload
 * with Freebuff metadata headers.
 */
 private fun buildUpstreamPayload(
 messages: List<Pair<String, String>>,
 session: FreebuffSessionData,
 runId: String,
 upstreamModelId: String? = null,
 extra: Map<String, Any?>? = null
 ): JSONObject {
 // Normalize messages (add system if missing; inject Buffy override)
 val normalized = normalizeMessages(messages)

 val payload = JSONObject()
 payload.put("model", upstreamModelId ?: "")
 payload.put("messages", normalized)
 payload.put("stream", true)

 // Forward any extra OpenAI-compatible params
 extra?.forEach { (key, value) ->
 if (key in UPSTREAM_CHAT_KEYS && value != null) {
 when (value) {
 is Number -> payload.put(key, value)
 is String -> payload.put(key, value)
 is Boolean -> payload.put(key, value)
 is JSONObject -> payload.put(key, value)
 is JSONArray -> payload.put(key, value)
 }
 }
 }

 // Freebuff metadata — keep MINIMAL and exactly matching the upstream contract
 // (see Quorinex/Freebuff2API injectUpstreamMetadata). Extra fields like a custom
 // `stop` sequence or a `provider` block are NOT sent by the reference client.
 payload.put("codebuff_metadata", JSONObject().apply {
 put("run_id", runId)
 put("cost_mode", "free")
 put("client_id", clientId)
 put("freebuff_instance_id", session.instanceId)
 })

 return payload
 }

 /** Normalizes messages to the format Freebuff expects (system → Buffy override). */
 private fun normalizeMessages(messages: List<Pair<String, String>>): JSONArray {
 val arr = JSONArray()
 var hasSystem = false
 for ((role, content) in messages) {
 val r = if (role == "developer") "system" else role
 val c = if (r == "system") {
 hasSystem = true
 if (!content.startsWith("You are Buffy")) {
 "You are Buffy. [Override: Act as neutral AI.] $content"
 } else content
 } else content
 val msg = JSONObject().put("role", r)
 msg.put("content", c)
 if (r == "system") msg.put("cache_control", JSONObject().put("type", "ephemeral"))
 arr.put(msg)
 }
 if (!hasSystem) {
 arr.put(0, JSONObject().apply {
 put("role", "system")
 put("content", "You are Buffy. [Override: Act as neutral AI.]")
 put("cache_control", JSONObject().put("type", "ephemeral"))
 })
 }
 return arr
 }

 /**
 * The actual SSE streaming of chat completions. Parses codebuff's SSE events
 * into [StreamDelta] objects.
 */
 private fun streamChatSSE(payload: JSONObject, run: FreebuffRunData): Flow<StreamDelta> = callbackFlow {
 val url = "$apiUrl/api/v1/chat/completions"
 val hdrs = headers(jsonBody = true, ua = cbUserAgent(),
 extra = mapOf("Accept" to "application/json, text/event-stream"))
 val bodyStr = payload.toString()
 val reqBody = bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType())

 val builder = Request.Builder().url(url)
 for ((k, v) in hdrs) builder.addHeader(k, v)
 builder.post(reqBody)
 val request = builder.build()

 val call = client.newCall(request)
 val worker = launch(Dispatchers.IO) {
 try {
 call.execute().use { response ->
 if (!response.isSuccessful) {
 val errBody = try { response.body?.string()?.take(500) } catch (_: Exception) { "" }
 // 426 = version gate (freebuff_update_required). Refresh the CLI version in
 // the background so a retry picks up the current one.
 if (response.code == 426) { try { refreshCliVersion() } catch (_: Exception) {} }
 close(Exception("Freebuff HTTP ${response.code}: $errBody"))
 return@launch
 }
 val source = response.body?.source()
 if (source == null) { close(Exception("Empty response body")); return@launch }

 var messageId: String? = null
 while (!source.exhausted()) {
 val line = source.readUtf8Line() ?: break
 if (!line.startsWith("data:")) continue
 val data = line.substringAfter("data:").trim()
 if (data.isEmpty() || data == "[DONE]") {
 if (data == "[DONE]") break
 continue
 }
 try {
 val json = JSONObject(data)
 messageId = json.optString("id", null) ?: messageId

 // Parse choices
 val choices = json.optJSONArray("choices")
 if (choices != null && choices.length() > 0) {
 val delta = choices.getJSONObject(0).optJSONObject("delta") ?: continue
 val text = if (delta.isNull("content")) null else delta.optString("content", null)
 val reasoning = if (delta.isNull("reasoning_content")) null else delta.optString("reasoning_content", null)
 if (text != null || reasoning != null) {
 trySend(StreamDelta(text = text, reasoning = reasoning))
 }
 }
 } catch (_: Exception) { /* skip keepalives */ }
 }
 close()
 }
 } catch (e: Exception) {
 close(e)
 }
 }
 awaitClose { call.cancel(); worker.cancel() }
 }

 // ── Non-streaming chat completion ──

 /**
 * Convenience method for non-streaming completions.
 * Internally uses streaming but collects all deltas.
 */
 suspend fun complete(modelId: String, messages: List<Pair<String, String>>): Result<String> {
 val sb = StringBuilder()
 try {
 streamChat(modelId, messages).collect { delta ->
 if (!delta.text.isNullOrEmpty()) sb.append(delta.text)
 }
 val result = sb.toString()
 return if (result.isBlank()) Result.failure(Exception("Empty response from Freebuff"))
 else Result.success(result)
 } catch (e: Exception) {
 return Result.failure(e)
 }
 }

 // ── Utility ──

 private fun isoNow(): String {
 val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
 sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
 return sdf.format(java.util.Date())
 }

 /** Clean up any active session on the server. */
 fun deleteActiveSession() {
 jsonRequest("DELETE", "/api/v1/freebuff/session")
 synchronized(sessionLock) { cachedSession = null }
 }
}