package com.ahamai.app.data

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * AssemblyAI Voice Agent client — uses AssemblyAI's full Voice Agent API.
 *
 * Connects to wss://agents.assemblyai.com/v1/ws
 * Handles: STT (Universal-3.5 Pro) → Managed LLM → TTS — all in one WebSocket.
 * Flat rate: $4.50/hr.
 *
 * The Voice Agent API supports 6 input languages with native code-switching:
 *   English, Spanish, French, German, Italian, Portuguese
 * Output (TTS) supports 11 languages including Hindi.
 *
 * Audio format: PCM16 mono, 24kHz, base64-encoded in JSON messages.
 */
object AssemblyAIVoiceAgent {

    private const val TAG = "AssemblyAIVoice"
    private const val WS_URL = "wss://agents.assemblyai.com/v1/ws"
    private const val SAMPLE_RATE = 24000  // Voice Agent API uses 24kHz

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
    private var playbackJob: Job? = null
    private var scope: CoroutineScope? = null
    private val keyIndex = AtomicInteger(0)
    private var isMuted = false
    private var sessionReady = false
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private var audioWriterActive = false
    // Bumped by every start()/stop() call. All of this object's mutable state (webSocket,
    // audioTrack, audioRecord, scope, …) is a SINGLETON — closing an old session (e.g. switching
    // voice models: stop() immediately followed by start()) doesn't happen instantly, since the
    // old WebSocket's onClosed/onFailure callback fires later on an OkHttp thread. Without this
    // guard, that stale callback's cleanup() would release the audioTrack/audioRecord the NEW
    // session is actively reading/writing, and the recording/playback loops would then crash with
    // IllegalStateException on a released AudioRecord/AudioTrack. Every callback and long-running
    // loop captures the generation it started under and checks it's still current before touching
    // shared state.
    private val generation = AtomicInteger(0)

    /**
     * AssemblyAI client-side tools: results must be sent on [reply.done], not mid-reply.
     * See docs: tool.call → (run tool) → hold until reply.done → tool.result
     */
    @Volatile private var lastServerEvent: String? = null
    private val pendingToolResults = ConcurrentLinkedQueue<PendingToolResult>()
    private data class PendingToolResult(val callId: String, val resultJson: String)

    /** Available AssemblyAI TTS voices for voice call */
    val VOICES = listOf(
        "ivy" to "Ivy (Female, English — safe default, 11 languages)",
        "james" to "James (Male, English)",
        "sophie" to "Sophie (Female, English)",
        "oliver" to "Oliver (Male, English)",
        "lucia" to "Lucia (Female, Spanish-native)",
        "mateo" to "Mateo (Male, Spanish-native)",
        "pierre" to "Pierre (Male, French-native)",
        "lukas" to "Lukas (Male, German-native)",
        "lena" to "Lena (Female, German-native)",
        "giulia" to "Giulia (Female, Italian-native)",
        "luca" to "Luca (Male, Italian-native)"
    )

    private val client: OkHttpClient by lazy {
        try {
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
        val keys = RemoteConfigManager.assemblyAiApiKeys
        if (keys.isEmpty()) return null
        val idx = keyIndex.getAndIncrement() % keys.size
        return keys[if (idx < 0) idx + keys.size else idx]
    }

    /**
     * Start a voice call using AssemblyAI's Voice Agent API.
     * Single WebSocket handles STT → LLM → TTS.
     *
     * @param voiceModel The TTS voice model to use (default: ivy)
     * @param systemPrompt Custom system prompt
     */
    fun start(
        voiceModel: String = "ivy",
        systemPrompt: String = buildSystemPrompt()
    ) {
        if (_state.value != State.IDLE) return

        val apiKey = getNextKey()
        if (apiKey.isNullOrBlank()) {
            _error.value = "No AssemblyAI key. Add in Admin → Providers → AssemblyAI Voice."
            _state.value = State.ERROR
            return
        }

        // New session — invalidates any still-in-flight callback/loop from a previous session
        // whose teardown (WebSocket close, coroutine cancellation) hasn't finished landing yet.
        val myGen = generation.incrementAndGet()

        _state.value = State.CONNECTING
        _userTranscript.value = ""
        _agentText.value = ""
        _error.value = null
        sessionReady = false
        lastServerEvent = null
        pendingToolResults.clear()
        audioQueue.clear()
        audioWriterActive = false

        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Build request with Authorization: Bearer <key>
        val request = Request.Builder()
            .url(WS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (myGen != generation.get()) return
                Log.i(TAG, "WebSocket opened, sending session.update...")
                sendSessionConfig(webSocket, voiceModel, systemPrompt)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (myGen != generation.get()) return
                handleTextMessage(text, myGen)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Not used — all messages are JSON text
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (myGen != generation.get()) return
                Log.e(TAG, "WebSocket failed: ${t.message}")
                _error.value = t.message?.take(60) ?: "Connection failed"
                _state.value = State.ERROR
                cleanup()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (myGen != generation.get()) return
                Log.i(TAG, "WebSocket closed: $code $reason")
                cleanup()
            }
        })
    }

    /** Client-side tools (run on device via WebTools / local control). */
    private fun buildTools(): JSONArray = JSONArray().apply {
        put(JSONObject().apply {
            put("type", "function")
            put("name", "web_search")
            put(
                "description",
                "Search the live web for current information, news, prices, sports scores, " +
                    "weather, stock quotes, or any real-time fact. " +
                    "Call this whenever the user asks about something that may change over time " +
                    "or that you are not 100% sure about. Prefer calling this over guessing. " +
                    "Do NOT call for pure chit-chat or simple math."
            )
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("query", JSONObject().apply {
                        put("type", "string")
                        put("description", "Short focused search query, e.g. 'weather Mumbai today' or 'latest iPhone price India'")
                        put("examples", JSONArray().put("weather Tokyo today").put("who won IPL final 2025"))
                    })
                })
                put("required", JSONArray().put("query"))
            })
            put("execution_mode", "interactive")
            put("timeout_seconds", 30)
        })
        put(JSONObject().apply {
            put("type", "function")
            put("name", "read_url")
            put(
                "description",
                "Read/extract the text content of a specific web page or article URL. " +
                    "Use after web_search when the user wants details from a particular link, " +
                    "or when they dictate a URL to open."
            )
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("url", JSONObject().apply {
                        put("type", "string")
                        put("description", "Full https URL of the page to read")
                        put("examples", JSONArray().put("https://en.wikipedia.org/wiki/AssemblyAI"))
                    })
                })
                put("required", JSONArray().put("url"))
            })
            put("execution_mode", "interactive")
            put("timeout_seconds", 45)
        })
        put(JSONObject().apply {
            put("type", "function")
            put("name", "end_call")
            put(
                "description",
                "End the voice call when the user clearly says goodbye, thanks and hangs up, " +
                    "or asks to stop/end the call. Do not call for a brief pause."
            )
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
            })
            put("execution_mode", "interactive")
            put("timeout_seconds", 10)
        })
    }

    private fun sendSessionConfig(ws: WebSocket, voiceModel: String, systemPrompt: String) {
        val config = JSONObject().apply {
            put("type", "session.update")
            put("session", JSONObject().apply {
                put("system_prompt", systemPrompt)
                put("greeting", "Hey! How can I help you today?")
                put("output", JSONObject().apply {
                    put("voice", voiceModel)
                })
                // Turn detection — adaptive, supports interruption
                put("input", JSONObject().apply {
                    put("turn_detection", JSONObject().apply {
                        put("interrupt_response", true)
                    })
                })
                // Real tools (not just a prompt claim) — web_search runs via WebTools on-device
                put("tools", buildTools())
            })
        }
        ws.send(config.toString())
        Log.i(TAG, "Session config sent (voice=$voiceModel, tools=web_search,read_url,end_call)")
    }

    /**
     * Run a client-side tool and queue [tool.result] for flush on the next idle (reply.done).
     * AssemblyAI requires results after reply.done of the transition phrase, not mid-reply.
     */
    private fun handleToolCall(json: JSONObject, gen: Int) {
        val callId = json.optString("call_id", "")
        val name = json.optString("name", "")
        if (callId.isBlank() || name.isBlank()) {
            Log.w(TAG, "tool.call missing call_id/name: $json")
            return
        }
        // arguments is a dict per AssemblyAI docs (not a JSON string)
        val args = json.optJSONObject("arguments") ?: JSONObject()
        Log.i(TAG, "tool.call name=$name call_id=$callId args=$args")

        scope?.launch {
            if (gen != generation.get()) return@launch
            val resultObj = try {
                when (name) {
                    "web_search" -> {
                        val query = args.optString("query", "").trim()
                        if (query.isBlank()) {
                            JSONObject().put("error", "Missing search query. Ask the user what to search for.")
                        } else if (!RemoteConfigManager.webSearchEnabled) {
                            JSONObject().put("error", "Web search is disabled by the administrator.")
                        } else {
                            // Audible feedback that a live web search is starting (same soft
                            // ascending chime the chat uses), so voice users know it's searching.
                            SoundEffects.playSearch()
                            val raw = withContext(Dispatchers.IO) { WebTools.search(query) }
                            // Voice needs short, speakable snippets — trim heavy markdown
                            val cleaned = raw
                                .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), "")
                                .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
                                .replace(Regex("#{1,6}\\s*"), "")
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(900)
                            JSONObject()
                                .put("query", query)
                                .put("results", cleaned.ifBlank { "No results found." })
                        }
                    }
                    "read_url" -> {
                        val url = args.optString("url", "").trim()
                        if (url.isBlank()) {
                            JSONObject().put("error", "Missing url. Ask the user for the page link.")
                        } else {
                            SoundEffects.playRead()
                            val content = withContext(Dispatchers.IO) { WebTools.read(url) }
                            val cleaned = content
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(1000)
                            JSONObject()
                                .put("url", url)
                                .put("content", cleaned.ifBlank { "Could not extract content from that page." })
                        }
                    }
                    "end_call" -> {
                        // Reply with acknowledgement first; hang up after agent can speak
                        scope?.launch {
                            delay(2500)
                            if (gen == generation.get()) stop()
                        }
                        JSONObject().put("status", "ending").put("message", "Call ended. Goodbye.")
                    }
                    else -> JSONObject().put("error", "Unknown tool: $name")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Tool $name failed: ${e.message}", e)
                JSONObject().put(
                    "error",
                    "Tool $name failed: ${e.message?.take(120) ?: "unknown error"}. Apologise briefly and offer to try again."
                )
            }

            if (gen != generation.get()) return@launch
            pendingToolResults.add(PendingToolResult(callId, resultObj.toString()))
            Log.i(TAG, "tool result queued for $name (${resultObj.toString().take(100)})")
            // Tool may finish after reply.done already fired — flush if idle
            flushPendingToolResultsIfIdle()
        }
    }

    /** Drain queued tool.result messages only when the agent is between turns (reply.done). */
    private fun flushPendingToolResultsIfIdle() {
        if (lastServerEvent != "reply.done") return
        val ws = webSocket ?: return
        while (true) {
            val pending = pendingToolResults.poll() ?: break
            val msg = JSONObject().apply {
                put("type", "tool.result")
                put("call_id", pending.callId)
                // Must be a JSON *string* per AssemblyAI events reference
                put("result", pending.resultJson)
            }
            val ok = ws.send(msg.toString())
            Log.i(TAG, "tool.result sent call_id=${pending.callId} ok=$ok")
        }
    }

    private fun handleTextMessage(text: String, gen: Int) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type", "")
            when (type) {
                "session.ready" -> {
                    val sessionId = json.optString("session_id", "")
                    Log.i(TAG, "Session ready: $sessionId")
                    sessionReady = true
                    lastServerEvent = "session.ready"
                    _state.value = State.LISTENING
                    initAudioTrack()
                    startRecording(gen)
                }
                "session.updated" -> {
                    Log.i(TAG, "Session updated")
                }
                "transcript.user" -> {
                    val content = json.optString("text", "").trim()
                    if (content.isNotEmpty()) {
                        _userTranscript.value = content
                        Log.i(TAG, "User transcript: $content")
                    }
                }
                "transcript.agent" -> {
                    val content = json.optString("text", "").trim()
                    if (content.isNotEmpty()) {
                        _agentText.value = content
                        Log.i(TAG, "Agent transcript: ${content.take(60)}")
                    }
                }
                "reply.audio" -> {
                    // Audio from the agent — base64 PCM16 at 24kHz, in the "data" field
                    val audioBase64 = json.optString("data", "")
                    if (audioBase64.isNotEmpty()) {
                        handleAgentAudio(audioBase64, gen)
                    }
                }
                "reply.started" -> {
                    lastServerEvent = "reply.started"
                    _state.value = State.AGENT_SPEAKING
                    Log.i(TAG, "Agent started speaking")
                }
                "reply.done" -> {
                    val status = json.optString("status", "")
                    lastServerEvent = "reply.done"
                    _state.value = State.LISTENING
                    Log.i(TAG, "Agent stopped speaking (status=$status)")
                    if (status == "interrupted") {
                        // User barged in — drop stale tool results from the interrupted turn
                        pendingToolResults.clear()
                    } else {
                        flushPendingToolResultsIfIdle()
                    }
                }
                "tool.call" -> {
                    handleToolCall(json, gen)
                }
                "input.speech.started" -> {
                    lastServerEvent = "input.speech.started"
                    // Barge-in: stop playback when user interrupts
                    stopAudioPlayback()
                    _state.value = State.LISTENING
                    Log.i(TAG, "User started speaking (barge-in)")
                }
                "session.ended" -> {
                    Log.i(TAG, "Session ended")
                    cleanup()
                }
                "session.error", "error" -> {
                    val code = json.optString("code", json.optString("error_code", ""))
                    val desc = json.optString("message", json.optString("description", "Unknown error"))
                    Log.e(TAG, "Agent error: $code - $desc")
                    _error.value = desc.take(80)
                    _state.value = State.ERROR
                }
                else -> {
                    // Log unknown events for debugging (e.g. tool-related variants)
                    if (type.isNotBlank() && type !in listOf("reply.audio", "transcript.user.delta")) {
                        Log.d(TAG, "Unhandled event type=$type")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: $text", e)
        }
    }

    private fun handleAgentAudio(audioBase64: String, gen: Int) {
        try {
            val audioBytes = Base64.decode(audioBase64, Base64.DEFAULT)
            if (audioBytes.isNotEmpty()) {
                // Enqueue on the WS thread (fast, non-blocking)
                audioQueue.add(audioBytes)

                // Launch the audio writer if not already running
                if (!audioWriterActive) {
                    audioWriterActive = true
                    scope?.launch {
                        if (audioTrack == null) {
                            withContext(Dispatchers.Main) { initAudioTrack() }
                        }
                        audioTrack?.play()
                        while (isActive && gen == generation.get()) {
                            val chunk = audioQueue.poll() ?: break
                            var written = 0
                            while (written < chunk.size && isActive && gen == generation.get()) {
                                // A stop()/start() on another thread can release audioTrack out from
                                // under this loop mid-chunk (generation check above narrows but can't
                                // fully close that window) — catch instead of crashing on a released track.
                                val result = try {
                                    audioTrack?.write(chunk, written, chunk.size - written) ?: -1
                                } catch (e: IllegalStateException) {
                                    Log.e(TAG, "AudioTrack write on released track: ${e.message}")
                                    -1
                                }
                                if (result < 0) {
                                    Log.e(TAG, "AudioTrack write error: $result")
                                    break
                                }
                                written += result
                            }
                            // Audio level for visualization
                            if (chunk.size >= 4) {
                                val shorts = ShortArray(chunk.size / 2)
                                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                                val rms = kotlin.math.sqrt(shorts.map { it.toDouble() * it.toDouble() }.average()).toFloat()
                                _audioLevel.value = (rms / 16000f).coerceIn(0f, 1f)
                            }
                        }
                        audioWriterActive = false
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio decode error: ${e.message}")
        }
    }

    private fun startRecording(gen: Int) {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )
            audioRecord?.startRecording()

            recordJob = scope?.launch {
                val buffer = ByteArray(bufferSize)
                while (isActive && gen == generation.get() && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    val read = try {
                        audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    } catch (e: IllegalStateException) {
                        Log.e(TAG, "AudioRecord read on released record: ${e.message}")
                        -1
                    }
                    if (read > 0 && !isMuted && sessionReady) {
                        // Send audio as base64 input.audio events
                        val chunk = buffer.copyOf(read)
                        val b64 = Base64.encodeToString(chunk, Base64.NO_WRAP)
                        val msg = JSONObject().apply {
                            put("type", "input.audio")
                            put("audio", b64)
                        }
                        webSocket?.send(msg.toString())

                        // Input audio level
                        val shorts = ShortArray(read / 2)
                        ByteBuffer.wrap(buffer, 0, read).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                        val rms = kotlin.math.sqrt(shorts.map { it.toDouble() * it.toDouble() }.average()).toFloat()
                        if (_state.value == State.LISTENING) {
                            _audioLevel.value = (rms / 12000f).coerceIn(0f, 1f)
                        }
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
        val bufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2,
            AudioTrack.MODE_STREAM
        )
        audioTrack?.play()
    }

    private fun stopAudioPlayback() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (_: Exception) {}
    }

    fun setMuted(muted: Boolean) { isMuted = muted }

    fun stop() {
        // Invalidate this session's generation FIRST, before touching any resource — any callback
        // or loop iteration still in flight (on the OkHttp thread, or a coroutine not yet cancelled)
        // sees the mismatch and backs off instead of racing this teardown or a following start().
        generation.incrementAndGet()
        recordJob?.cancel(); recordJob = null
        playbackJob?.cancel(); playbackJob = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}; audioRecord = null
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}; audioTrack = null
        pendingToolResults.clear()
        lastServerEvent = null
        if (sessionReady) {
            // Docs: session.end (not session.terminate) — ends billing immediately
            try {
                webSocket?.send(JSONObject().apply { put("type", "session.end") }.toString())
            } catch (_: Exception) {}
        }
        webSocket?.close(1000, "ended"); webSocket = null
        scope?.cancel(); scope = null
        _state.value = State.IDLE
        _audioLevel.value = 0f
        isMuted = false
        sessionReady = false
    }

    private fun cleanup() {
        recordJob?.cancel()
        playbackJob?.cancel()
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}; audioRecord = null
        try { audioTrack?.stop() } catch (_: Exception) {}
        try { audioTrack?.release() } catch (_: Exception) {}; audioTrack = null
        pendingToolResults.clear()
        lastServerEvent = null
        scope?.cancel()
        if (_state.value != State.IDLE) {
            _state.value = State.IDLE
            _audioLevel.value = 0f
        }
        sessionReady = false
    }

    private fun buildSystemPrompt(): String {
        val now = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date())
        val tz = java.util.TimeZone.getDefault().id
        return """
Current date & time: $now ($tz).

You are AhamAI, a helpful AI voice assistant on a live phone-style call.
Keep spoken answers short and natural (1–3 sentences). No markdown, no bullet lists, no URLs unless asked.

TOOLS — you have real tools. Use them instead of guessing:
- web_search: call for news, weather, prices, sports, stock, "who is", "what happened", live facts, or anything that may be outdated in training data. When in doubt, call web_search. A wasted search is fine; a wrong answer from memory is not.
- read_url: call when the user wants details from a specific link or after search points to a useful page.
- end_call: call only when the user clearly wants to hang up / says goodbye.

Example:
User: "What's the weather in Mumbai?"
You: [call web_search with query "weather Mumbai today"] then speak the answer from the tool result.

NEVER invent live numbers (scores, prices, temps, news) unless they came from a tool result in this call.
If a tool errors, say you couldn't check right now and offer to try again.

Language: the user may speak English, Spanish, French, German, Italian, Portuguese, or Hindi — reply in the same language they used.
        """.trimIndent()
    }
}