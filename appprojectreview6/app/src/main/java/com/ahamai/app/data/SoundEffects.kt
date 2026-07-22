package com.ahamai.app.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Subtle, ChatGPT-style UI sound effects, synthesized in code (no bundled audio assets):
 *  - [playSearch]: a soft two-note ascending "whoosh" played when a web/image search starts.
 *  - [playRead]:   a gentle short tick played when the AI opens/reads a URL.
 *
 * Tones are generated once into 16-bit PCM and replayed via a one-shot [AudioTrack], so
 * playback is instant and never blocks the UI thread.
 */
object SoundEffects {

    private const val SAMPLE_RATE = 44100

    @Volatile private var searchPcm: ByteArray? = null
    @Volatile private var readPcm: ByteArray? = null
    @Volatile private var switchPcm: ByteArray? = null
    @Volatile var enabled: Boolean = true

    private val executor = Executors.newSingleThreadExecutor()

    /** Pre-generate the tones so the first playback has no lag. Safe to call repeatedly. */
    fun init(@Suppress("UNUSED_PARAMETER") context: Context? = null) {
        if (searchPcm == null) searchPcm = buildSearchTone()
        if (readPcm == null) readPcm = buildReadTone()
        if (switchPcm == null) switchPcm = buildSwitchTone()
    }

    fun playSearch() = play(searchPcm ?: buildSearchTone().also { searchPcm = it })

    fun playRead() = play(readPcm ?: buildReadTone().also { readPcm = it })

    /** Bright ascending three-note flourish played when handing off to the Agent. */
    fun playSwitch() = play(switchPcm ?: buildSwitchTone().also { switchPcm = it })

    private fun play(pcm: ByteArray) {
        if (!enabled) return
        executor.execute {
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Static one-shot: wait for it to finish, then release.
                val durationMs = (pcm.size / 2L) * 1000L / SAMPLE_RATE + 120L
                Thread.sleep(durationMs)
            } catch (_: Exception) {
                // ignore — sound is non-critical
            } finally {
                try { track?.stop() } catch (_: Exception) {}
                try { track?.release() } catch (_: Exception) {}
            }
        }
    }

    // --- Tone synthesis -------------------------------------------------------------------

    /** Soft, airy two-note ascending chime (~340 ms). */
    private fun buildSearchTone(): ByteArray {
        val a = tone(freq = 587.33, ms = 150, amp = 0.16, attackMs = 8, releaseMs = 90) // D5
        val b = tone(freq = 880.0, ms = 190, amp = 0.16, attackMs = 8, releaseMs = 140) // A5
        return concat(a, b)
    }

    /** Gentle short "tick" (~110 ms) used when opening/reading a page. */
    private fun buildReadTone(): ByteArray {
        return tone(freq = 740.0, ms = 110, amp = 0.13, attackMs = 4, releaseMs = 80) // F#5
    }

    /**
     * A polished ascending arpeggio (A5 → C#6 → E6, ~430 ms) with a soft tail — a satisfying
     * "lift-off" cue for switching into the Agent.
     */
    private fun buildSwitchTone(): ByteArray {
        val a = tone(freq = 880.0, ms = 110, amp = 0.15, attackMs = 6, releaseMs = 70)   // A5
        val b = tone(freq = 1108.73, ms = 110, amp = 0.15, attackMs = 6, releaseMs = 70) // C#6
        val c = tone(freq = 1318.51, ms = 210, amp = 0.16, attackMs = 6, releaseMs = 170) // E6
        return concat(a, b, c)
    }

    /**
     * Generates a single sine tone with a smooth attack and an exponential release so it sounds
     * soft (no clicks). [amp] is 0..1 of full scale (kept low for a subtle effect).
     */
    private fun tone(freq: Double, ms: Int, amp: Double, attackMs: Int, releaseMs: Int): ByteArray {
        val total = SAMPLE_RATE * ms / 1000
        val attack = SAMPLE_RATE * attackMs / 1000
        val release = SAMPLE_RATE * releaseMs / 1000
        val out = ByteArray(total * 2)
        for (n in 0 until total) {
            val t = n.toDouble() / SAMPLE_RATE
            // Base sine + a quiet octave harmonic for a slightly richer, glassy tone.
            var s = sin(2.0 * PI * freq * t) + 0.25 * sin(2.0 * PI * freq * 2 * t)
            s /= 1.25
            // Envelope.
            val env = when {
                n < attack -> n.toDouble() / attack
                n > total - release -> exp(-3.0 * (n - (total - release)).toDouble() / release)
                else -> 1.0
            }
            val v = (s * env * amp * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            out[n * 2] = (v and 0xFF).toByte()
            out[n * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val size = arrays.sumOf { it.size }
        val out = ByteArray(size)
        var pos = 0
        for (a in arrays) {
            System.arraycopy(a, 0, out, pos, a.size)
            pos += a.size
        }
        return out
    }
}
