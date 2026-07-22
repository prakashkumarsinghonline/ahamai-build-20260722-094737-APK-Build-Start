package com.ahamai.app.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Edge-TTS client with LOW LATENCY streaming.
 * Splits text into sentences, synthesizes & plays them sequentially
 * so playback starts almost immediately (first sentence only).
 */
object EdgeTtsClient {

    private const val TRUSTED_CLIENT_TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4"
    private const val BASE_URL = "speech.platform.bing.com/consumer/speech/synthesize/readaloud"
    private const val CHROMIUM_FULL_VERSION = "143.0.3650.75"
    private const val CHROMIUM_MAJOR_VERSION = "143"
    private const val SEC_MS_GEC_VERSION = "1-$CHROMIUM_FULL_VERSION"
    private const val DEFAULT_VOICE = "en-US-EmmaMultilingualNeural"
    private const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"

    private const val WIN_EPOCH = 11644473600L
    private const val S_TO_NS = 1_000_000_000.0

    private val client = OkHttpClient.Builder().build()

    private var mediaPlayer: MediaPlayer? = null
    @Volatile
    private var isPlaying = false
    private var playbackJob: Job? = null

    private fun generateSecMsGec(): String {
        var ticks = System.currentTimeMillis() / 1000.0
        ticks += WIN_EPOCH
        ticks -= ticks % 300
        ticks *= S_TO_NS / 100.0
        val strToHash = "${ticks.toLong()}$TRUSTED_CLIENT_TOKEN"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(strToHash.toByteArray(Charsets.US_ASCII))
        return hash.joinToString("") { "%02x".format(it) }.uppercase()
    }

    private fun connectId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun generateMuid(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02X".format(it) }
    }

    private fun buildWssUrl(): String {
        val connectionId = connectId()
        val secMsGec = generateSecMsGec()
        return "wss://$BASE_URL/edge/v1" +
                "?TrustedClientToken=$TRUSTED_CLIENT_TOKEN" +
                "&ConnectionId=$connectionId" +
                "&Sec-MS-GEC=$secMsGec" +
                "&Sec-MS-GEC-Version=$SEC_MS_GEC_VERSION"
    }

    private fun dateToString(): String {
        val sdf = java.text.SimpleDateFormat(
            "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
            java.util.Locale.US
        )
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date())
    }

    private fun buildConfigMessage(): String {
        val timestamp = dateToString()
        return "X-Timestamp:$timestamp\r\n" +
                "Content-Type:application/json; charset=utf-8\r\n" +
                "Path:speech.config\r\n\r\n" +
                """{"context":{"synthesis":{"audio":{"metadataoptions":{"sentenceBoundaryEnabled":"false","wordBoundaryEnabled":"false"},"outputFormat":"$OUTPUT_FORMAT"}}}}"""
    }

    private fun buildSsmlMessage(text: String, voice: String): String {
        val requestId = connectId()
        val timestamp = dateToString()
        val escapedText = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

        val ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>" +
                "<voice name='$voice'>" +
                "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>" +
                escapedText +
                "</prosody></voice></speak>"

        return "X-RequestId:$requestId\r\n" +
                "Content-Type:application/ssml+xml\r\n" +
                "X-Timestamp:${timestamp}Z\r\n" +
                "Path:ssml\r\n\r\n" + ssml
    }

    private fun buildWsRequest(): Request {
        return Request.Builder()
            .url(buildWssUrl())
            .addHeader("Pragma", "no-cache")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Origin", "chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold")
            .addHeader("Accept-Encoding", "gzip, deflate, br, zstd")
            .addHeader("Accept-Language", "en-US,en;q=0.9")
            .addHeader("Cookie", "muid=${generateMuid()};")
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${CHROMIUM_MAJOR_VERSION}.0.0.0 Safari/537.36 Edg/${CHROMIUM_MAJOR_VERSION}.0.0.0")
            .build()
    }

    /**
     * Split text into sentence chunks for faster TTS.
     * First chunk is kept SHORT for minimum latency to first sound.
     */
    private fun splitIntoChunks(text: String): List<String> {
        if (text.length < 60) return listOf(text)

        val sentences = text.split(Regex("(?<=[.!?;:\\n])\\s+"))
            .filter { it.isNotBlank() }

        if (sentences.size <= 1) return listOf(text)

        // First chunk: just 1 short sentence for fastest start
        val chunks = mutableListOf<String>()
        chunks.add(sentences.first())

        // Rest: group 3-4 sentences per chunk for efficiency (fewer WS connections)
        var buffer = ""
        for (i in 1 until sentences.size) {
            buffer += (if (buffer.isEmpty()) "" else " ") + sentences[i]
            if (buffer.length > 200 || i == sentences.size - 1) {
                chunks.add(buffer)
                buffer = ""
            }
        }
        if (buffer.isNotBlank()) chunks.add(buffer)

        return chunks
    }

    /**
     * Synthesize a single text chunk to MP3 file.
     */
    suspend fun synthesize(
        text: String,
        cacheDir: File,
        voice: String = DEFAULT_VOICE
    ): File = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val outputFile = File(cacheDir, "tts_${System.currentTimeMillis()}.mp3")
            val audioData = mutableListOf<ByteArray>()

            val ws = client.newWebSocket(buildWsRequest(), object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(buildConfigMessage())
                    webSocket.send(buildSsmlMessage(text, voice))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (text.contains("Path:turn.end")) {
                        try {
                            FileOutputStream(outputFile).use { fos ->
                                for (chunk in audioData) fos.write(chunk)
                            }
                            webSocket.close(1000, "Done")
                            if (continuation.isActive) continuation.resume(outputFile)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    val data = bytes.toByteArray()
                    if (data.size < 2) return
                    val headerLength = ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                    val headerEnd = 2 + headerLength
                    if (headerEnd >= data.size) return
                    val headerStr = String(data, 2, headerLength, Charsets.US_ASCII)
                    if (headerStr.contains("Path:audio") && headerStr.contains("Content-Type:audio")) {
                        val audioBytes = data.copyOfRange(headerEnd, data.size)
                        if (audioBytes.isNotEmpty()) audioData.add(audioBytes)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("Edge-TTS failed: ${t.message}", t))
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
            })

            continuation.invokeOnCancellation { ws.cancel() }
        }
    }

    /**
     * LOW LATENCY streaming TTS: Writes audio chunks to file progressively
     * and starts playback after first few chunks arrive (~100ms).
     */
    suspend fun speakStreaming(
        text: String,
        cacheDir: File,
        voice: String = DEFAULT_VOICE,
        onStart: () -> Unit = {},
        onComplete: () -> Unit = {}
    ) {
        stopAudio()
        isPlaying = true

        playbackJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val chunks = splitIntoChunks(text)

                for ((index, chunk) in chunks.withIndex()) {
                    if (!isPlaying || !isActive) break
                    try {
                        val file = synthesize(chunk, cacheDir, voice)
                        if (index == 0) {
                            withContext(Dispatchers.Main) { onStart() }
                        }
                        playFileSync(file)
                        file.delete()
                    } catch (e: Exception) {
                        if (!isActive) break
                    }
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    isPlaying = false
                    onComplete()
                }
            }
        }
    }

    /**
     * Plays a single audio file synchronously (blocks until playback ends).
     */
    private suspend fun playFileSync(file: File) {
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { cont ->
                try {
                    val player = MediaPlayer()
                    player.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    player.setDataSource(file.absolutePath)
                    player.setOnCompletionListener {
                        it.release()
                        if (cont.isActive) cont.resume(Unit)
                    }
                    player.setOnErrorListener { mp, _, _ ->
                        mp.release()
                        if (cont.isActive) cont.resume(Unit)
                        true
                    }
                    player.prepare()
                    player.start()
                    mediaPlayer = player

                    cont.invokeOnCancellation {
                        try { player.stop(); player.release() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    /**
     * Simple play (non-streaming, for short texts).
     */
    suspend fun playAudio(file: File, onComplete: () -> Unit = {}) {
        withContext(Dispatchers.Main) {
            stopAudio()
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.setOnCompletionListener {
                isPlaying = false
                onComplete()
                it.release()
                mediaPlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                isPlaying = false
                onComplete()
                mp.release()
                mediaPlayer = null
                true
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            isPlaying = true
        }
    }

    fun stopAudio() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        isPlaying = false
    }

    fun isCurrentlyPlaying(): Boolean = isPlaying
}
