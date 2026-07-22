package com.ahamai.app.data

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Deepgram Voice Agent client — uses BYO LLM (our own providers).
 *
 * Connects to wss://agent.deepgram.com/v1/agent/converse
 * STT: Deepgram Nova-3 | LLM: Our own provider (via endpoint) | TTS: Deepgram Aura-2
 */
object DeepgramVoiceAgent {

 private const val TAG = "DeepgramVoice"
 private const val WS_URL = "wss://agent.deepgram.com/v1/agent/converse"
 private const val SAMPLE_RATE_IN = 16000
 private const val SAMPLE_RATE_OUT = 24000

 enum class State { IDLE, CONNECTING, LISTENING, AGENT_SPEAKING, ERROR }

 private val _state = MutableStateFlow(State.IDLE)
 val state: StateFlow<State> = _state

 private val _userTranscript = MutableStateFlow("")
 val userTranscript: StateFlow<String> = _userTranscript

 private val _agentText = MutableStateFlow("")
 val agentText: StateFlow<String> = _agentText

 private val _error = MutableStateFlow<String?>(null)
 val error: StateFlow<String?> = _error

 private val _audioLevel = MutableStateFlow(0f)
 val audioLevel: StateFlow<Float> = _audioLevel

 // Internal
 private var webSocket: WebSocket? = null
 private var audioRecord: AudioRecord? = null
 private var audioTrack: AudioTrack? = null
 private var recordJob: Job? = null
 private var scope: CoroutineScope? = null
 private val keyIndex = AtomicInteger(0)
 private var isMuted = false
 private var currentVoice = "aura-2-thalia-en"

 /** Available Deepgram voice models for voice call */
 val VOICES = listOf(
 "aura-2-thalia-en" to "Thalia (Female, Warm)",
 "aura-2-luna-en" to "Luna (Female, Calm)",
 "aura-2-stella-en" to "Stella (Female, Clear)",
 "aura-2-athena-en" to "Athena (Female, Professional)",
 "aura-2-hera-en" to "Hera (Female, Confident)",
 "aura-2-andromeda-en" to "Andromeda (Female, Soft)",
 "aura-2-helena-en" to "Helena (Female, Friendly)",
 "aura-2-apollo-en" to "Apollo (Male, Deep)",
 "aura-2-orion-en" to "Orion (Male, Calm)",
 "aura-2-arcas-en" to "Arcas (Male, Warm)",
 "aura-2-perseus-en" to "Perseus (Male, Strong)",
 "aura-2-zeus-en" to "Zeus (Male, Authoritative)",
 "aura-2-aries-en" to "Aries (Male, Energetic)",
 )

 private val client: OkHttpClient by lazy {
 try {
 // Use a permissive TLS config for broader device compatibility
 val trustManager = object : javax.net.ssl.X509TrustManager {
 override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
 override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
 override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
 }
 val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
 sslContext.init(null, arrayOf<javax.net.ssl.TrustManager>(trustManager), java.security.SecureRandom())

 OkHttpClient.Builder()
 .readTimeout(0, TimeUnit.MILLISECONDS)
 .connectTimeout(15, TimeUnit.SECONDS)
 .pingInterval(20, TimeUnit.SECONDS)
 .sslSocketFactory(sslContext.socketFactory, trustManager)
 .hostnameVerifier { _, _ -> true }
 .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
 .build()
 } catch (_: Exception) {
 OkHttpClient.Builder()
 .readTimeout(0, TimeUnit.MILLISECONDS)
 .connectTimeout(15, TimeUnit.SECONDS)
 .pingInterval(20, TimeUnit.SECONDS)
 .build()
 }
 }

 private fun getNextKey(): String? {
 val keys = RemoteConfigManager.deepgramApiKeys
 if (keys.isEmpty()) return null
 val idx = keyIndex.getAndIncrement() % keys.size
 return keys[if (idx < 0) idx + keys.size else idx]
 }

 /**
 * Start voice call using our own LLM provider (BYO).
 * @param llmBaseUrl The base URL of the LLM provider (from chat/agent config)
 * @param llmApiKey The API key for the LLM provider
 * @param llmModel The model name to use
 * @param systemPrompt Custom system prompt
 * @param voiceModel Deepgram TTS voice model (default: aura-2-thalia-en)
 */
 fun start(
 llmBaseUrl: String = "",
 llmApiKey: String = "",
 llmModel: String = "gpt-4o-mini",
 voiceModel: String = "aura-2-thalia-en",
 systemPrompt: String = "Current date & time (user's device): ${java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.getDefault()).format(java.util.Date())} (${java.util.TimeZone.getDefault().id}).\nIMPORTANT: You do NOT have a knowledge cutoff. You are connected to live, real-time web search and can fetch current information whenever needed. Never claim you lack recent data, never mention a training cutoff date, and never give an outdated or uncertain date — always use the current date/time above.\n\nYou are AhamAI, a helpful AI voice assistant. Keep responses very short and conversational (1-3 sentences). You are in a real-time voice call so be natural and concise. You have access to web search — use it when the user asks for current information."
 ) {
 if (_state.value != State.IDLE) return

 currentVoice = voiceModel

 val apiKey = getNextKey()
 if (apiKey.isNullOrBlank()) {
 _error.value = "No Deepgram key. Add in Admin → Providers → Deepgram Voice."
 _state.value = State.ERROR
 return
 }

 _state.value = State.CONNECTING
 _userTranscript.value = ""
 _agentText.value = ""
 _error.value = null

 scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

 val request = Request.Builder()
 .url(WS_URL)
 .addHeader("Authorization", "Token $apiKey")
 .build()

 webSocket = client.newWebSocket(request, object : WebSocketListener() {
 override fun onOpen(webSocket: WebSocket, response: Response) {
 Log.i(TAG, "WebSocket opened, waiting for Welcome...")
 }

 override fun onMessage(webSocket: WebSocket, text: String) {
 handleTextMessage(text, systemPrompt)
 }

 override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
 handleAudioMessage(bytes)
 }

 override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
 Log.e(TAG, "WebSocket failed: ${t.message}")
 _error.value = t.message?.take(60) ?: "Connection failed"
 _state.value = State.ERROR
 cleanup()
 }

 override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
 Log.i(TAG, "WebSocket closed: $code")
 cleanup()
 }
 })
 }

 private fun handleTextMessage(text: String, systemPrompt: String) {
 try {
 val json = JSONObject(text)
 when (json.optString("type", "")) {
 "Welcome" -> {
 Log.i(TAG, "Got Welcome, sending Settings...")
 sendSettings(systemPrompt)
 }
 "SettingsApplied" -> {
 Log.i(TAG, "SettingsApplied — starting mic")
 _state.value = State.LISTENING
 initAudioTrack()
 startRecording()
 }
 "ConversationText" -> {
 val role = json.optString("role", "")
 val content = json.optString("content", "")
 if (role == "user") _userTranscript.value = content
 else if (role == "assistant") _agentText.value = content
 }
 "AgentThinking" -> { /* Agent processing */ }
 "AgentStartedSpeaking" -> _state.value = State.AGENT_SPEAKING
 "AgentAudioDone" -> _state.value = State.LISTENING
 "UserStartedSpeaking" -> {
 // Barge-in: stop playback immediately
 stopAudioPlayback()
 _state.value = State.LISTENING
 _userTranscript.value = ""
 }
 "Error" -> {
 val msg = json.optString("description", json.optString("message", "Error"))
 Log.e(TAG, "Agent error: $msg")
 _error.value = msg
 _state.value = State.ERROR
 }
 "FunctionCallRequest" -> {
 handleFunctionCall(json)
 }
 }
 } catch (e: Exception) {
 Log.e(TAG, "Parse error: $text", e)
 }
 }

 private fun sendSettings(systemPrompt: String) {
 val settings = JSONObject().apply {
 put("type", "Settings")
 put("audio", JSONObject().apply {
 put("input", JSONObject().apply {
 put("encoding", "linear16")
 put("sample_rate", SAMPLE_RATE_IN)
 })
 put("output", JSONObject().apply {
 put("encoding", "linear16")
 put("sample_rate", SAMPLE_RATE_OUT)
 put("container", "none")
 })
 })
 put("agent", JSONObject().apply {
 put("listen", JSONObject().apply {
 put("provider", JSONObject().apply {
 put("type", "deepgram")
 put("model", "nova-3")
 })
 })
 put("think", JSONObject().apply {
 // Use Deepgram's managed OpenAI (reliable, no SSL issues)
 put("provider", JSONObject().apply {
 put("type", "open_ai")
 put("model", "gpt-4o-mini")
 })
 put("prompt", systemPrompt)
 // Function calling — expose our app's tools
 put("functions", org.json.JSONArray().apply {
 // Web Search tool
 put(JSONObject().apply {
 put("name", "web_search")
 put("description", "Search the web for current information, news, prices, weather, or any real-time data the user asks about.")
 put("parameters", JSONObject().apply {
 put("type", "object")
 put("properties", JSONObject().apply {
 put("query", JSONObject().apply {
 put("type", "string")
 put("description", "The search query")
 })
 })
 put("required", org.json.JSONArray().put("query"))
 })
 })
 // End call tool
 put(JSONObject().apply {
 put("name", "end_call")
 put("description", "End the voice call when the user says goodbye, thanks, or indicates they are done.")
 put("parameters", JSONObject().apply {
 put("type", "object")
 put("properties", JSONObject())
 })
 })
 // Web Reader (read_url) tool
 put(JSONObject().apply {
 put("name", "read_url")
 put("description", "Read the content of a specific web page or article URL.")
 put("parameters", JSONObject().apply {
 put("type", "object")
 put("properties", JSONObject().apply {
 put("url", JSONObject().apply {
 put("type", "string")
 put("description", "The URL of the page to read")
 })
 })
 put("required", org.json.JSONArray().put("url"))
 })
 })
 })
 })
 put("speak", JSONObject().apply {
 put("provider", JSONObject().apply {
 put("type", "deepgram")
 put("model", currentVoice)
 })
 })
 put("greeting", "Hey! How can I help you?")
 })
 }
 webSocket?.send(settings.toString())
 Log.i(TAG, "Settings sent with function calling")
 }

 /** Handle function call requests from the voice agent */
 private fun handleFunctionCall(json: JSONObject) {
 // Format: {"type":"FunctionCallRequest","functions":[{"id":"...","name":"web_search","arguments":"{\"query\":\"...\"}","client_side":true}]}
 val functions = json.optJSONArray("functions") ?: return
 
 for (i in 0 until functions.length()) {
 val func = functions.getJSONObject(i)
 val callId = func.optString("id", "")
 val name = func.optString("name", "")
 val argsStr = func.optString("arguments", "{}")
 val args = try { JSONObject(argsStr) } catch (_: Exception) { JSONObject() }

 Log.i(TAG, "Function call: $name($argsStr)")

 scope?.launch {
                    val result = when (name) {
                    "web_search" -> {
                        val query = args.optString("query", "")
                        try {
                            val searchResult = withContext(Dispatchers.IO) { WebTools.search(query) }
                            searchResult.take(400)
                        } catch (e: Exception) {
                            "I couldn't search right now."
                        }
                    }
                    "read_url" -> {
                        val url = args.optString("url", "")
                        if (url.isBlank()) ""
                        else try {
                            val content = withContext(Dispatchers.IO) { WebTools.read(url) }
                            content.take(500)
                        } catch (e: Exception) {
                            "I couldn't read that page."
                        }
                    }
                    "end_call" -> {
                        scope?.launch { delay(2000); stop() }
                        "Call ended."
                    }
                    else -> "Not available."
                }

 // Send FunctionCallResponse
 val response = JSONObject().apply {
 put("type", "FunctionCallResponse")
 put("id", callId)
 put("name", name)
 put("content", result)
 }
 webSocket?.send(response.toString())
 Log.i(TAG, "Function response: $name -> ${result.take(80)}")
 }
 }
 }

 private fun handleAudioMessage(bytes: ByteString) {
 val data = bytes.toByteArray()
 if (audioTrack == null) initAudioTrack()
	 audioTrack?.play()
	 audioTrack?.write(data, 0, data.size)

 // Audio level for visualization
 if (data.size >= 4) {
 val shorts = ShortArray(data.size / 2)
 java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
 val rms = Math.sqrt(shorts.map { it.toDouble() * it.toDouble() }.average()).toFloat()
 _audioLevel.value = (rms / 16000f).coerceIn(0f, 1f)
 }
 }

 private fun startRecording() {
 val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE_IN, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
 try {
 audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, SAMPLE_RATE_IN, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2)
 audioRecord?.startRecording()

 recordJob = scope?.launch {
 val buffer = ByteArray(bufferSize)
 while (isActive && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
 val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
 if (read > 0 && !isMuted) {
 webSocket?.send(ByteString.of(*buffer.copyOf(read)))
 // Input level
 val shorts = ShortArray(read / 2)
 java.nio.ByteBuffer.wrap(buffer, 0, read).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
 val rms = Math.sqrt(shorts.map { it.toDouble() * it.toDouble() }.average()).toFloat()
 if (_state.value == State.LISTENING) _audioLevel.value = (rms / 12000f).coerceIn(0f, 1f)
 }
 delay(20)
 }
 }
 } catch (e: SecurityException) {
 _error.value = "Microphone permission required"
 _state.value = State.ERROR
 }
 }

 private fun initAudioTrack() {
 val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE_OUT, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
 audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, SAMPLE_RATE_OUT, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2, AudioTrack.MODE_STREAM)
 audioTrack?.play()
 }

 private fun stopAudioPlayback() {
 try { audioTrack?.pause(); audioTrack?.flush(); audioTrack?.play() } catch (_: Exception) {}
 }

 fun setMuted(muted: Boolean) { isMuted = muted }

 fun stop() {
 recordJob?.cancel(); recordJob = null
 try { audioRecord?.stop() } catch (_: Exception) {}
 try { audioRecord?.release() } catch (_: Exception) {}; audioRecord = null
 try { audioTrack?.stop() } catch (_: Exception) {}
 try { audioTrack?.release() } catch (_: Exception) {}; audioTrack = null
 webSocket?.close(1000, "ended"); webSocket = null
 scope?.cancel(); scope = null
 _state.value = State.IDLE; _audioLevel.value = 0f; isMuted = false
 }

 private fun cleanup() {
 recordJob?.cancel()
 try { audioRecord?.stop() } catch (_: Exception) {}
 try { audioRecord?.release() } catch (_: Exception) {}; audioRecord = null
 try { audioTrack?.stop() } catch (_: Exception) {}
 try { audioTrack?.release() } catch (_: Exception) {}; audioTrack = null
 scope?.cancel()
 _state.value = State.IDLE; _audioLevel.value = 0f
 }
}
