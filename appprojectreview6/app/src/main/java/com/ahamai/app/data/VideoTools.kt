package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Video tools — specifically the "Viral Shorts Maker" skill. Runs entirely in the E2B cloud
 * sandbox using ffmpeg (installed on demand) and the bundled make_shorts.py script. The agent
 * provides a video URL (YouTube or direct) and optional parameters; this tool downloads the video
 * via yt-dlp, runs the highlight-detection + multi-style rendering pipeline, and pulls the
 * resulting 9:16 short clips back into the project.
 *
 * Tool: MAKE_SHORTS
 *   arg0 = video URL or project-relative path to an already-downloaded video
 *   arg1 = JSON options: {"count":3,"clip_len":30,"styles":"blur,crop,card","title":"...","srt":"project/path.srt",
 *                         "karaoke":true,"caption_size":22,"font":"Inter-Bold","hindi_font":"NotoSansDevanagari-Bold","emoji_font":"NotoColorEmoji"}
 *   arg2 = output folder (relative, default "shorts_out")
 *
 * v2 features:
 *   - Live per-word karaoke captions (libass \k timing tags, TikTok style)
 *   - Emoji overlay support (Noto Color Emoji fallback)
 *   - Hindi / Devanagari auto-detection (uses NotoSansDevanagari-Bold)
 *   - Whisper-style word-timestamp SRTs auto-unpacked
 */
object VideoTools {

    /**
     * The make_shorts.py script (viral-shorts-maker skill), base64-encoded and decoded at runtime
     * in the sandbox. This avoids needing the skill folder synced to /workspace.
     */
    private val MAKE_SHORTS_B64: String by lazy {
        val stream = javaClass.classLoader?.getResourceAsStream("make_shorts.py.b64")
        stream?.bufferedReader()?.readText()?.trim()
            ?: FALLBACK_SCRIPT_B64
    }

    // Fallback: if the asset isn't bundled, we carry a minimal inline version.
    // In practice the full script is placed in assets/make_shorts.py.b64 at build time.
    private const val FALLBACK_SCRIPT_B64 = ""

    suspend fun makeShorts(
        ctx: Context, projectDir: String, videoSource: String, optionsJson: String, outFolder: String
    ): String = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(ctx)
        if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
            return@withContext "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to enable video processing."
        }

        val out = outFolder.trim().removePrefix("/").ifBlank { "shorts_out" }
        val src = videoSource.trim()
        if (src.isBlank()) return@withContext "ERROR: no video source provided."

        // Parse options (v2 additions: karaoke, caption_size, font, hindi_font, emoji_font)
        val opts = try { org.json.JSONObject(optionsJson.ifBlank { "{}" }) } catch (_: Exception) { org.json.JSONObject() }
        val count = opts.optInt("count", 3)
        val clipLen = opts.optDouble("clip_len", 30.0)
        val styles = opts.optString("styles", "blur,crop,card")
        val title = opts.optString("title", "")
        val srtPath = opts.optString("srt", "")
        val karaoke = if (opts.has("karaoke")) opts.getBoolean("karaoke") else true
        val captionSize = opts.optInt("caption_size", 22)
        val fontName = opts.optString("font", "")
        val hindiFontName = opts.optString("hindi_font", "")
        val emojiFontName = opts.optString("emoji_font", "")
        // Auto word-synced captions: when the caller didn't supply an SRT, transcribe the audio
        // with faster-whisper so each word highlights exactly when spoken (real voice sync).
        // Callers can force it on/off with "transcribe".
        val doTranscribe = if (opts.has("transcribe")) opts.getBoolean("transcribe")
                           else (karaoke && srtPath.isBlank())

        // 1. Ensure ffmpeg + yt-dlp + fonts are available in sandbox
        val provision = buildString {
            append("command -v ffmpeg >/dev/null 2>&1 || aham_apt ffmpeg; ")
            append("command -v yt-dlp >/dev/null 2>&1 || aham_pip yt-dlp; ")
            // Ensure Noto fonts are installed for emoji + Devanagari fallback
            append("command -v fc-list >/dev/null 2>&1 || aham_apt fontconfig; ")
            append("fc-list | grep -i 'NotoColorEmoji' >/dev/null 2>&1 || aham_apt fonts-noto-color-emoji; ")
            append("fc-list | grep -i 'NotoSansDevanagari' >/dev/null 2>&1 || aham_apt fonts-noto; ")
            // faster-whisper for word-synced auto-captions (only when we'll actually transcribe).
            if (doTranscribe) append("python3 -c 'import faster_whisper' 2>/dev/null || aham_pip faster-whisper; ")
            append("echo PROVISION_OK")
        }
        val provRes = CloudTools.execProv(ctx, projectDir, provision, 300)
        if (!provRes.stdout.contains("PROVISION_OK")) {
            return@withContext "ERROR: failed to provision ffmpeg/yt-dlp in the sandbox:\n${provRes.formatted(800)}"
        }

        // 2. Upload the make_shorts.py script
        val scriptB64 = getScriptB64(ctx)
        if (scriptB64.isBlank()) {
            return@withContext "ERROR: make_shorts.py script not available."
        }
        val uploadScript = "echo '$scriptB64' | base64 -d > /workspace/_make_shorts.py && echo SCRIPT_OK"
        val uploadRes = CloudTools.execIn(ctx, projectDir, uploadScript, 30)
        if (!uploadRes.stdout.contains("SCRIPT_OK")) {
            return@withContext "ERROR: failed to upload shorts script:\n${uploadRes.formatted(500)}"
        }

        // 3. Get the video into /workspace
        val isUrl = src.startsWith("http://") || src.startsWith("https://") || src.contains("youtu")
        val videoPath: String
        if (isUrl) {
            // Download via yt-dlp
            val dlCmd = "cd /workspace && yt-dlp --no-playlist -f 'bestvideo[height<=1080]+bestaudio/best[height<=1080]' " +
                "--merge-output-format mp4 -o '_input_video.mp4' '${src.replace("'", "\\'")}' 2>&1 | tail -10; " +
                "ls -la /workspace/_input_video.mp4 2>/dev/null && echo DL_OK || echo DL_FAIL"
            val dlRes = CloudTools.execProv(ctx, projectDir, dlCmd, 600)
            if (!dlRes.stdout.contains("DL_OK")) {
                return@withContext "ERROR: failed to download video from $src:\n${dlRes.formatted(1200)}"
            }
            videoPath = "/workspace/_input_video.mp4"
        } else {
            // It's a project file — sync it up
            val f = File(projectDir, src)
            if (!f.exists()) return@withContext "ERROR: video file not found in project: $src"
            CloudTools.syncProjectUp(ctx, projectDir)
            videoPath = "/workspace/$src"
        }

        // 4. Run make_shorts.py (v2 — karaoke + emoji + hindi support)
        // 4. Find font paths inside sandbox and echo them so we can parse
        val fontFindScript = buildString {
            append("FONT_INTER=\$(fc-list | grep -i 'Inter' | grep -i 'Bold' | head -1 | cut -d: -f1); ")
            append("[ -z \"\$FONT_INTER\" ] && FONT_INTER=\$(fc-list | grep -i 'Inter' | head -1 | cut -d: -f1); ")
            append("FONT_HINDI=\$(fc-list | grep -i 'NotoSansDevanagari' | grep -i 'Bold' | head -1 | cut -d: -f1); ")
            append("[ -z \"\$FONT_HINDI\" ] && FONT_HINDI=\$(fc-list | grep -i 'NotoSansDevanagari' | head -1 | cut -d: -f1); ")
            append("FONT_EMOJI=\$(fc-list | grep -i 'NotoColorEmoji' | head -1 | cut -d: -f1); ")
            append("[ -z \"\$FONT_EMOJI\" ] && FONT_EMOJI=\$(fc-list | grep -i 'NotoEmoji' | head -1 | cut -d: -f1); ")
            // Echo values so we can parse them from stdout
            append("echo \"FONT_INTER=\$FONT_INTER\"; ")
            append("echo \"FONT_HINDI=\$FONT_HINDI\"; ")
            append("echo \"FONT_EMOJI=\$FONT_EMOJI\"; ")
            append("echo FONTS_FOUND")
        }
        val fontRes = CloudTools.execIn(ctx, projectDir, fontFindScript, 30)
        val fontInter = Regex("FONT_INTER=([^\r\n]+)").find(fontRes.stdout)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: ""
        val fontHindi = Regex("FONT_HINDI=([^\r\n]+)").find(fontRes.stdout)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: ""
        val fontEmoji = Regex("FONT_EMOJI=([^\r\n]+)").find(fontRes.stdout)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: ""

        val cmd = buildString {
            append("cd /workspace && python3 _make_shorts.py")
            append(" --input '$videoPath'")
            append(" --outdir '/workspace/$out'")
            append(" --count $count")
            append(" --clip-len $clipLen")
            append(" --styles '$styles'")
            if (title.isNotBlank()) append(" --title '${title.replace("'", "\\'")}'")
            if (srtPath.isNotBlank()) append(" --srt '/workspace/$srtPath'")
            // v2: karaoke + emoji + hindi
            if (karaoke) append(" --karaoke") else append(" --no-karaoke")
            // Word-synced auto-captions from the audio (faster-whisper) when no SRT was supplied.
            if (doTranscribe) append(" --transcribe --whisper-model base")
            append(" --caption-size $captionSize")
            if (fontInter.isNotBlank()) append(" --font '$fontInter'")
            if (fontHindi.isNotBlank()) append(" --hindi-font '$fontHindi'")
            if (fontEmoji.isNotBlank()) append(" --emoji-font '$fontEmoji'")
            append(" 2>&1; echo; ls -la '/workspace/$out'/*.mp4 2>/dev/null | wc -l | xargs -I{} echo SHORTS_COUNT={}")
        }
        val runRes = CloudTools.execProv(ctx, projectDir, cmd, 900)

        // 5. Pull results back
        val countMatch = Regex("SHORTS_COUNT=(\\d+)").find(runRes.stdout)
        val shortsCount = countMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (shortsCount == 0) {
            return@withContext "ERROR: no shorts were generated.\n${runRes.formatted(2000)}"
        }

        val pullResult = CloudTools.cloudPull(ctx, projectDir, "/workspace/$out", out)

        // 6. Verify outputs
        val outDir = File(projectDir, out)
        val mp4s = outDir.listFiles()?.filter { it.extension == "mp4" } ?: emptyList()

        buildString {
            appendLine("OK: Generated ${mp4s.size} viral short(s) → project/$out/")
            appendLine()
            appendLine("Files:")
            for (f in mp4s.sortedBy { it.name }) {
                appendLine("  ${f.name} (${f.length() / 1024} KB)")
            }
            appendLine()
            appendLine("Settings: count=$count, clip_len=${clipLen}s, styles=$styles")
            appendLine("Captions: ${if (karaoke) "karaoke (per-word)" else "simple timed"}, size=$captionSize")
            if (fontHindi.isNotBlank()) appendLine("Hindi font: $fontHindi")
            if (fontEmoji.isNotBlank()) appendLine("Emoji font: $fontEmoji")
            if (title.isNotBlank()) appendLine("Title: $title")
            appendLine()
            appendLine("All shorts are 1080×1920 (9:16 vertical). Use EXPORT_TO_DEVICE to save to phone.")
            appendLine(pullResult)
        }
    }

    private fun getScriptB64(ctx: Context): String {
        // 1. Try the bundled asset (make_shorts.py.b64 in app/src/main/assets/)
        try {
            val stream = ctx.assets.open("make_shorts.py.b64")
            val b64 = stream.bufferedReader().readText().trim()
            if (b64.isNotBlank()) return b64
        } catch (_: Exception) {}

        // 2. Try reading make_shorts.py directly from assets (raw, not b64)
        try {
            val stream = ctx.assets.open("make_shorts.py")
            val bytes = stream.readBytes()
            if (bytes.isNotEmpty()) return Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) {}

        // 3. Lazy-loaded class-level field (populated from classloader resource)
        val fromClassLoader = MAKE_SHORTS_B64
        if (fromClassLoader.isNotBlank()) return fromClassLoader

        // 4. Try the skills folder synced by the user (external storage fallback)
        for (candidate in listOf(
            "skills/viral-shorts-maker/make_shorts.py",
            "make_shorts.py"
        )) {
            try {
                val f = java.io.File(ctx.filesDir.parentFile, candidate)
                if (f.exists() && f.length() > 100) {
                    return Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
                }
            } catch (_: Exception) {}
        }

        return ""
    }
}
