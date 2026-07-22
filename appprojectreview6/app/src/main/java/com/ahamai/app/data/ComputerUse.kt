package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * "AhamAI's Computer" — Daytona Computer Use desktop (XFCE + noVNC).
 *
 * Preferred path for **web tasks** as well as GUI work: boots in ~30–60s (stock snapshot,
 * no Playwright/lib install), and the user can watch the live desktop.
 *
 * Clicks use pixel coordinates on a fixed **1024×768** screen. After each click/type we
 * return a short screenshot summary so the model can aim the next action.
 *
 * Web browsing on desktop:
 *  - [browse] opens Chromium/Firefox to a URL (replaces slow CloudBrowser first-boot)
 *  - [click]/[type]/[key]/[scroll] drive the focused window
 *  - [screenshot] returns size + saves a JPEG the agent can reason about
 */
object ComputerUse {

    private const val API = "https://app.daytona.io/api"
    private const val PROXY_DEFAULT = "https://proxy.app.daytona.io"
    private const val DESKTOP_SNAPSHOT = "daytonaio/sandbox:0.6.0"
    private const val NOVNC_PORT = 6080
    /** Fixed display size used by Daytona computer-use (keep model coords in this space). */
    const val SCREEN_W = 1024
    const val SCREEN_H = 768

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private val desktopId = AtomicReference<String?>(null)
    @Volatile private var liveUrl: String? = null
    @Volatile private var toolboxProxyBase: String = PROXY_DEFAULT
    @Volatile private var lastBrowseUrl: String = ""

    private fun apiKey(ctx: Context) = PreferencesManager(ctx).getE2bApiKey()
    private fun reqB(url: String, key: String) =
        Request.Builder().url(url).header("Authorization", "Bearer $key").header("Content-Type", "application/json")
    private fun jsonBody(o: JSONObject) = o.toString().toRequestBody("application/json".toMediaType())

    private fun computerUseUrl(id: String, path: String): String {
        val base = toolboxProxyBase.trimEnd('/')
        val root = if (base.endsWith("/toolbox")) base else "$base/toolbox"
        return "$root/$id/computeruse/$path".trimEnd('/')
    }

    fun currentLiveUrl(): String? = liveUrl
    fun lastUrl(): String = lastBrowseUrl

    // ───────────────────────────── lifecycle ─────────────────────────────

    private suspend fun ensure(ctx: Context, onHeartbeat: (String) -> Unit = {}): String? = mutex.withLock {
        val key = apiKey(ctx)
        if (key.isBlank()) return "Cloud engine not configured."
        if (key.startsWith("e2b_", ignoreCase = true)) {
            return "This build uses Daytona (not E2B). Set a Daytona API key (dtn_…) in Settings."
        }
        val existing = desktopId.get()
        if (existing != null && getState(key, existing).let { it == "started" || it == "running" }) {
            if (liveUrl == null) liveUrl = previewUrl(key, existing, NOVNC_PORT)
            return null
        }
        onHeartbeat("Booting cloud desktop…")
        val id = try { createDesktop(key) } catch (e: CancellationException) { throw e }
        catch (e: Exception) { return "could not create the desktop sandbox: ${e.message}" }
        desktopId.set(id)
        var up = false
        for (i in 0 until 60) {
            val st = getState(key, id)
            if (st == "started" || st == "running") { up = true; break }
            if (st == "error" || st == "build_failed") return "desktop sandbox failed (state=$st)"
            delay(1000)
        }
        if (!up) return "desktop sandbox did not start in time"
        onHeartbeat("Starting desktop environment…")
        var started = false
        var lastErr = ""
        for (i in 0 until 15) {
            val r = post(key, computerUseUrl(id, "start"), JSONObject())
            if (r.first == 200 || r.first == 201) { started = true; break }
            lastErr = "HTTP ${r.first}: ${r.second.take(160)}"
            delay(1500)
        }
        if (!started) return "computer-use failed to start ($lastErr)"
        delay(1500)
        liveUrl = previewUrl(key, id, NOVNC_PORT)
        if (liveUrl == null) return "desktop is up but live noVNC URL is unavailable"
        return null
    }

    suspend fun open(ctx: Context, onHeartbeat: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val err = ensure(ctx, onHeartbeat)
        if (err != null) return@withContext "ERROR: $err"
        // Open a real browser window so the noVNC stream is not empty wallpaper.
        // (Websites should still use BROWSER_* / Playwright — this is only the full desktop.)
        runCatching {
            browse(ctx, "https://www.google.com", onHeartbeat)
        }
        val url = liveUrl ?: return@withContext "ERROR: live view URL unavailable"
        buildString {
            append("Live desktop ready (${SCREEN_W}x${SCREEN_H}). ")
            append("Use COMPUTER_CLICK x,y · COMPUTER_TYPE text · COMPUTER_KEY key · COMPUTER_SCROLL dir · COMPUTER_SCREENSHOT. ")
            append("For normal websites prefer BROWSER_OPEN (Playwright live stream — not this desktop).\n")
            append("[[BROWSER_LIVE]]$url[[/BROWSER_LIVE]]")
        }
    }

    /**
     * Open [url] on the SAME desktop the user watches in noVNC.
     *
     * Strategy (most reliable first):
     *  1) Shell-launch browser on the Xvfb/x11vnc DISPLAY
     *  2) If no browser window appears, click the panel browser icon (same X session as
     *     computer-use mouse API) and type the URL into the address bar
     *  3) Maximize the window so the live view is full-frame
     */
    suspend fun browse(ctx: Context, url: String, onHeartbeat: (String) -> Unit = {}): String =
        withContext(Dispatchers.IO) {
            val err = ensure(ctx, onHeartbeat)
            if (err != null) return@withContext "ERROR: $err"
            val key = apiKey(ctx)
            val id = desktopId.get() ?: return@withContext "ERROR: no desktop"
            val target = url.trim().ifBlank { "https://www.google.com" }
            val safe = target.replace("'", "'\\''")
            onHeartbeat("Opening $target…")

            // Resolve DISPLAY used by x11vnc/Xvfb (the stream the user sees)
            val displayProbe = execOnDesktop(
                key, id,
                """
                DISP=""; 
                for pid in ${'$'}(pgrep -f 'x11vnc|Xvfb' 2>/dev/null); do
                  tr '\0' '\n' < /proc/${'$'}pid/environ 2>/dev/null | grep -E '^DISPLAY=' | head -1
                done | head -1
                ps -eo args 2>/dev/null | grep -E 'Xvfb|x11vnc' | head -3
                ls /tmp/.X11-unix/ 2>/dev/null
                """.trimIndent().replace("\n", "; "),
                15
            )
            val display = Regex("DISPLAY=([^\\s]+)").find(displayProbe)?.groupValues?.get(1)
                ?: Regex("Xvfb\\s+(:[0-9]+)").find(displayProbe)?.groupValues?.get(1)
                ?: ":0"

            // Path A: launch browser on the correct DISPLAY
            val launch = """
                export DISPLAY='$display'
                export XAUTHORITY=${'$'}{XAUTHORITY:-/home/daytona/.Xauthority}
                [ -f /root/.Xauthority ] && export XAUTHORITY=/root/.Xauthority
                echo "USING_DISPLAY=${'$'}DISPLAY"
                # close stale browsers on this display only
                if command -v xdotool >/dev/null 2>&1; then
                  for w in ${'$'}(xdotool search --class 'chromium|Chrome|firefox|Navigator|Google-chrome' 2>/dev/null); do
                    xdotool windowclose ${'$'}w 2>/dev/null || true
                  done
                fi
                sleep 0.3
                BIN=""
                command -v chromium-browser >/dev/null && BIN=chromium-browser
                [ -z "${'$'}BIN" ] && command -v chromium >/dev/null && BIN=chromium
                [ -z "${'$'}BIN" ] && command -v google-chrome >/dev/null && BIN=google-chrome
                [ -z "${'$'}BIN" ] && command -v firefox >/dev/null && BIN=firefox
                if [ -z "${'$'}BIN" ]; then echo NO_BROWSER; exit 0; fi
                if [ "${'$'}BIN" = firefox ]; then
                  nohup ${'$'}BIN --new-window '$safe' >/tmp/aham-browser.log 2>&1 &
                else
                  nohup ${'$'}BIN --no-sandbox --disable-gpu --disable-dev-shm-usage \
                    --window-size=$SCREEN_W,$SCREEN_H --window-position=0,0 --start-maximized \
                    '$safe' >/tmp/aham-browser.log 2>&1 &
                fi
                echo "LAUNCHED_BIN=${'$'}BIN pid=${'$'}!"
                sleep 3
                WID=""
                if command -v xdotool >/dev/null 2>&1; then
                  for i in 1 2 3 4 5 6 7 8 9 10; do
                    WID=${'$'}(xdotool search --onlyvisible --class 'chromium|Chrome|firefox|Navigator|Google-chrome' 2>/dev/null | tail -1)
                    [ -n "${'$'}WID" ] && break
                    sleep 0.4
                  done
                  if [ -n "${'$'}WID" ]; then
                    xdotool windowactivate --sync ${'$'}WID
                    xdotool windowmove ${'$'}WID 0 0
                    xdotool windowsize ${'$'}WID $SCREEN_W $SCREEN_H
                    xdotool windowraise ${'$'}WID
                    wmctrl -i -r ${'$'}WID -b add,maximized_vert,maximized_horz 2>/dev/null || true
                    echo "BROWSER_WID=${'$'}WID"
                  else
                    echo "BROWSER_WID=NONE"
                    tail -5 /tmp/aham-browser.log 2>/dev/null
                  fi
                fi
            """.trimIndent().replace("\n", "; ")
            val shell = execOnDesktop(key, id, launch, 55)
            var opened = shell.contains("BROWSER_WID=") && !shell.contains("BROWSER_WID=NONE")

            // Path B: open via dock icon + address bar (same X session as computer-use mouse)
            if (!opened) {
                onHeartbeat("Opening browser via desktop UI…")
                // Xubuntu bottom panel browser icon is roughly center-right of the dock
                // Panel y ≈ 740–755 on 1024x768; globe icon ≈ x 500–560
                for (xy in listOf(548 to 748, 512 to 748, 580 to 748, 480 to 748)) {
                    post(key, computerUseUrl(id, "mouse/move"),
                        JSONObject().put("x", xy.first).put("y", xy.second))
                    delay(40)
                    post(key, computerUseUrl(id, "mouse/click"),
                        JSONObject().put("x", xy.first).put("y", xy.second).put("button", "left"))
                    delay(900)
                    // Check if a browser window appeared
                    val check = execOnDesktop(
                        key, id,
                        "export DISPLAY='$display'; xdotool search --onlyvisible --class 'chromium|Chrome|firefox|Navigator|Google-chrome' 2>/dev/null | tail -1",
                        10
                    ).trim()
                    if (check.isNotBlank() && check.all { it.isDigit() }) {
                        opened = true
                        break
                    }
                }
                // Type URL into address bar (Ctrl+L then URL + Enter) on whatever browser is focused
                execOnDesktop(
                    key, id,
                    "export DISPLAY='$display'; xdotool key --clearmodifiers ctrl+l; sleep 0.35; " +
                        "xdotool type --delay 6 -- '$safe'; sleep 0.15; xdotool key Return; echo TYPED",
                    30
                )
                delay(1200)
                execOnDesktop(
                    key, id,
                    "export DISPLAY='$display'; WID=${'$'}(xdotool search --onlyvisible --class 'chromium|Chrome|firefox|Navigator|Google-chrome' 2>/dev/null | tail -1); " +
                        "[ -n \"${'$'}WID\" ] && xdotool windowactivate ${'$'}WID && xdotool windowmove ${'$'}WID 0 0 && " +
                        "xdotool windowsize ${'$'}WID $SCREEN_W $SCREEN_H; echo MAX",
                    15
                )
                opened = true
            } else {
                // Path A already launched with URL — just ensure maximized on the live display
                execOnDesktop(
                    key, id,
                    "export DISPLAY='$display'; WID=${'$'}(xdotool search --onlyvisible --class 'chromium|Chrome|firefox|Navigator|Google-chrome' 2>/dev/null | tail -1); " +
                        "[ -n \"${'$'}WID\" ] && xdotool windowactivate ${'$'}WID && xdotool windowmove ${'$'}WID 0 0 && " +
                        "xdotool windowsize ${'$'}WID $SCREEN_W $SCREEN_H; echo MAX",
                    15
                )
            }

            lastBrowseUrl = target
            delay(800)
            // Force visible activity so noVNC flushes frames
            post(key, computerUseUrl(id, "mouse/move"),
                JSONObject().put("x", SCREEN_W / 2).put("y", SCREEN_H / 2))
            delay(100)
            post(key, computerUseUrl(id, "mouse/move"),
                JSONObject().put("x", SCREEN_W / 2 + 20).put("y", SCREEN_H / 2 + 10))
            val shot = captureShot(ctx, key, id)
            liveUrl = previewUrl(key, id, NOVNC_PORT) ?: liveUrl
            buildString {
                if (opened) append("Opened browser to: $target\n")
                else append("Tried to open browser to: $target (verify live view)\n")
                append("display=$display\n")
                append(shell.lines().filter { it.contains("DISPLAY") || it.contains("BROWSER") || it.contains("LAUNCHED") }.joinToString(" | ").take(200))
                append("\nScreen ${SCREEN_W}x${SCREEN_H}. Use COMPUTER_CLICK x,y.\n")
                append(shot)
                liveUrl?.let { append("\n[[BROWSER_LIVE]]$it[[/BROWSER_LIVE]]") }
            }
        }

    // ───────────────────────────── input ─────────────────────────────

    /**
     * Reliable click: move → short settle → click. Optional [doubleClick].
     * Coords are clamped to the 1024×768 desktop.
     */
    suspend fun click(
        ctx: Context, x: Int, y: Int, button: String = "left", doubleClick: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val err = ensure(ctx); if (err != null) return@withContext "ERROR: $err"
        val key = apiKey(ctx); val id = desktopId.get() ?: return@withContext "ERROR: no desktop"
        val cx = x.coerceIn(0, SCREEN_W - 1)
        val cy = y.coerceIn(0, SCREEN_H - 1)
        // Move first so hover states fire and click lands accurately
        post(key, computerUseUrl(id, "mouse/move"), JSONObject().put("x", cx).put("y", cy))
        delay(60)
        val body = JSONObject().put("x", cx).put("y", cy).put("button", button.ifBlank { "left" })
        val (c1, r1) = post(key, computerUseUrl(id, "mouse/click"), body)
        if (c1 !in 200..201) return@withContext "ERROR: click failed (HTTP $c1): ${r1.take(200)}"
        if (doubleClick) {
            delay(80)
            post(key, computerUseUrl(id, "mouse/click"), body)
        }
        delay(250)
        val shot = captureShot(ctx, key, id)
        "OK: clicked ($cx,$cy)${if (doubleClick) " double" else ""}\n$shot" +
            (liveUrl?.let { "\n[[BROWSER_LIVE]]$it[[/BROWSER_LIVE]]" } ?: "")
    }

    suspend fun move(ctx: Context, x: Int, y: Int): String =
        action(ctx, "mouse/move",
            JSONObject().put("x", x.coerceIn(0, SCREEN_W - 1)).put("y", y.coerceIn(0, SCREEN_H - 1)),
            "moved to (${x.coerceIn(0, SCREEN_W - 1)},${y.coerceIn(0, SCREEN_H - 1)})")

    suspend fun type(ctx: Context, text: String): String = withContext(Dispatchers.IO) {
        val err = ensure(ctx); if (err != null) return@withContext "ERROR: $err"
        val key = apiKey(ctx); val id = desktopId.get() ?: return@withContext "ERROR: no desktop"
        // Chunk long text so the API doesn't drop it
        val chunks = text.chunked(80)
        for ((i, ch) in chunks.withIndex()) {
            val (code, resp) = post(key, computerUseUrl(id, "keyboard/type"), JSONObject().put("text", ch))
            if (code !in 200..201) return@withContext "ERROR: type failed (HTTP $code): ${resp.take(200)}"
            if (i < chunks.lastIndex) delay(40)
        }
        delay(200)
        val shot = captureShot(ctx, key, id)
        "OK: typed \"${text.take(80)}${if (text.length > 80) "…" else ""}\"\n$shot" +
            (liveUrl?.let { "\n[[BROWSER_LIVE]]$it[[/BROWSER_LIVE]]" } ?: "")
    }

    suspend fun key(ctx: Context, k: String): String = withContext(Dispatchers.IO) {
        val err = ensure(ctx); if (err != null) return@withContext "ERROR: $err"
        val key = apiKey(ctx); val id = desktopId.get() ?: return@withContext "ERROR: no desktop"
        val raw = k.ifBlank { "Return" }
        val low = raw.lowercase().trim()
        // Chords / browser navigation → xdotool when available (more reliable than API for combos)
        if (low == "browser_back" || low == "alt+left" || low.startsWith("ctrl+") || low.startsWith("alt+")) {
            val xd = when (low) {
                "browser_back", "alt+left" -> "alt+Left"
                else -> low.replace("control+", "ctrl+")
            }
            execOnDesktop(key, id,
                "export DISPLAY=\${DISPLAY:-:0}; xdotool key --clearmodifiers $xd 2>/dev/null || xdotool key $xd 2>/dev/null; echo KEYED", 12)
        } else {
            val mapped = when (low) {
                "enter", "return" -> "Return"
                "esc", "escape" -> "Escape"
                "tab" -> "Tab"
                "backspace", "bksp" -> "BackSpace"
                "delete", "del" -> "Delete"
                "space" -> "space"
                "up" -> "Up"
                "down" -> "Down"
                "left" -> "Left"
                "right" -> "Right"
                else -> raw
            }
            val (code, resp) = post(key, computerUseUrl(id, "keyboard/key"),
                JSONObject().put("key", mapped))
            if (code !in 200..201) {
                // Fallback: xdotool
                execOnDesktop(key, id,
                    "export DISPLAY=\${DISPLAY:-:0}; xdotool key $mapped 2>/dev/null; echo KEYED", 10)
            }
        }
        delay(200)
        val shot = captureShot(ctx, key, id)
        "OK: pressed $raw\n$shot" + (liveUrl?.let { "\n[[BROWSER_LIVE]]$it[[/BROWSER_LIVE]]" } ?: "")
    }

    /** Scroll the focused window. [dir] = up|down|left|right. */
    suspend fun scroll(ctx: Context, dir: String, amount: Int = 4): String = withContext(Dispatchers.IO) {
        val err = ensure(ctx); if (err != null) return@withContext "ERROR: $err"
        val key = apiKey(ctx); val id = desktopId.get() ?: return@withContext "ERROR: no desktop"
        val d = dir.lowercase().trim()
        // Prefer mouse wheel API if present; else xdotool
        val wheelBody = when (d) {
            "up" -> JSONObject().put("x", SCREEN_W / 2).put("y", SCREEN_H / 2).put("deltaY", -amount * 120)
            "down" -> JSONObject().put("x", SCREEN_W / 2).put("y", SCREEN_H / 2).put("deltaY", amount * 120)
            "left" -> JSONObject().put("x", SCREEN_W / 2).put("y", SCREEN_H / 2).put("deltaX", -amount * 120)
            "right" -> JSONObject().put("x", SCREEN_W / 2).put("y", SCREEN_H / 2).put("deltaX", amount * 120)
            else -> JSONObject().put("x", SCREEN_W / 2).put("y", SCREEN_H / 2).put("deltaY", amount * 120)
        }
        var ok = false
        for (path in listOf("mouse/scroll", "mouse/wheel", "scroll")) {
            val (code, _) = post(key, computerUseUrl(id, path), wheelBody)
            if (code in 200..201) { ok = true; break }
        }
        if (!ok) {
            val btn = if (d == "up") "4" else "5"
            execOnDesktop(key, id,
                "export DISPLAY=\${DISPLAY:-:0}; " +
                    "for i in \$(seq 1 $amount); do xdotool click $btn 2>/dev/null; sleep 0.05; done; echo SCROLLED", 15)
        }
        delay(300)
        val shot = captureShot(ctx, key, id)
        "OK: scrolled $d\n$shot" + (liveUrl?.let { "\n[[BROWSER_LIVE]]$it[[/BROWSER_LIVE]]" } ?: "")
    }

    suspend fun screenshot(ctx: Context): String = withContext(Dispatchers.IO) {
        val err = ensure(ctx); if (err != null) return@withContext "ERROR: $err"
        val key = apiKey(ctx); val id = desktopId.get() ?: return@withContext "ERROR: no desktop"
        val shot = captureShot(ctx, key, id)
        "OK: desktop screenshot (${SCREEN_W}x${SCREEN_H})\n$shot" +
            (liveUrl?.let { "\n[[BROWSER_LIVE]]$it[[/BROWSER_LIVE]]" } ?: "")
    }

    suspend fun shutdown(ctx: Context) {
        val id = desktopId.getAndSet(null) ?: return
        liveUrl = null
        lastBrowseUrl = ""
        runCatching {
            val req = Request.Builder().url("$API/sandbox/$id")
                .header("Authorization", "Bearer ${apiKey(ctx)}").delete().build()
            client.newCall(req).execute().close()
        }
    }

    // ───────────────────────────── internals ─────────────────────────────

    private suspend fun action(ctx: Context, path: String, body: JSONObject, desc: String): String =
        withContext(Dispatchers.IO) {
            val err = ensure(ctx); if (err != null) return@withContext "ERROR: $err"
            val key = apiKey(ctx); val id = desktopId.get() ?: return@withContext "ERROR: no desktop"
            val (code, resp) = post(key, computerUseUrl(id, path), body)
            val live = liveUrl?.let { "\n[[BROWSER_LIVE]]$it[[/BROWSER_LIVE]]" } ?: ""
            if (code in 200..201) "OK: $desc$live"
            else "ERROR: $desc failed (HTTP $code): ${resp.take(200)}"
        }

    /**
     * Capture compressed screenshot; save under project if possible and return a model-readable
     * summary with dimensions so the agent can plan clicks.
     */
    private fun captureShot(ctx: Context, key: String, id: String): String {
        // Try compressed endpoint first, then full screenshot
        val endpoints = listOf(
            computerUseUrl(id, "screenshot/compressed") + "?format=jpeg&quality=55&scale=0.6",
            computerUseUrl(id, "screenshot")
        )
        for (ep in endpoints) {
            val (code, body) = get(key, ep)
            if (code != 200 || body.isBlank()) continue
            val b64 = try {
                val j = JSONObject(body)
                j.optString("screenshot").ifBlank {
                    j.optString("data").ifBlank { j.optString("image") }
                }
            } catch (_: Exception) { "" }
            if (b64.isBlank()) continue
            // Persist a small preview for debugging / optional vision attach
            runCatching {
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val dir = File(ctx.cacheDir, "computer_shots").apply { mkdirs() }
                val f = File(dir, "desk_${System.currentTimeMillis()}.jpg")
                f.writeBytes(bytes)
                return "SHOT: ${bytes.size} bytes · screen ${SCREEN_W}x${SCREEN_H} · saved ${f.name}\n" +
                    "HINT: address bar ~center-top (${SCREEN_W / 2}, 48); tabs near y=10–40; page content y=80+."
            }
            return "SHOT: captured · screen ${SCREEN_W}x${SCREEN_H}\n" +
                "HINT: click with COMPUTER_CLICK x,y in 0..$SCREEN_W , 0..$SCREEN_H"
        }
        return "SHOT: unavailable (live view still shows the desktop)"
    }

    private fun execOnDesktop(key: String, id: String, command: String, timeoutSec: Int): String {
        return try {
            val body = JSONObject().put("command", command).put("timeout", timeoutSec)
            val call = client.newBuilder()
                .readTimeout((timeoutSec + 20).toLong(), TimeUnit.SECONDS)
                .build()
            val req = reqB("$API/toolbox/$id/toolbox/process/execute", key)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            call.newCall(req).execute().use { r ->
                val raw = r.body?.string().orEmpty()
                if (!r.isSuccessful) return "HTTP ${r.code}: ${raw.take(200)}"
                JSONObject(raw).optString("result", raw)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "exec error"
        }
    }

    private fun createDesktop(key: String): String {
        val payload = JSONObject()
            .put("snapshot", DESKTOP_SNAPSHOT)
            .put("autoStopInterval", 30)
            .put("autoDeleteInterval", 60)
        val req = reqB("$API/sandbox", key).post(jsonBody(payload)).build()
        client.newCall(req).execute().use { r ->
            val b = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw RuntimeException("HTTP ${r.code}: ${b.take(200)}")
            val j = JSONObject(b)
            val proxy = j.optString("toolboxProxyUrl").trim()
            if (proxy.isNotBlank()) {
                toolboxProxyBase = proxy.removeSuffix("/").removeSuffix("/toolbox").ifBlank { PROXY_DEFAULT }
            }
            return j.optString("id").ifBlank { throw RuntimeException("no id") }
        }
    }

    private fun getState(key: String, id: String): String = try {
        client.newCall(reqB("$API/sandbox/$id", key).get().build()).execute().use {
            if (!it.isSuccessful) "" else {
                val j = JSONObject(it.body?.string().orEmpty())
                val proxy = j.optString("toolboxProxyUrl").trim()
                if (proxy.isNotBlank()) {
                    toolboxProxyBase = proxy.removeSuffix("/").removeSuffix("/toolbox")
                        .ifBlank { toolboxProxyBase }
                }
                j.optString("state")
            }
        }
    } catch (_: Exception) { "" }

    private fun previewUrl(key: String, id: String, port: Int): String? = try {
        client.newCall(
            reqB("$API/sandbox/$id/ports/$port/signed-preview-url?expiresInSeconds=86400", key).get().build()
        ).execute().use {
            if (!it.isSuccessful) return null
            val base = JSONObject(it.body?.string().orEmpty()).optString("url").trim().trimEnd('/')
            if (base.isBlank()) null
            else "$base/vnc.html?autoconnect=1&reconnect=1&resize=scale&show_dot=0&quality=6&compression=2&view_only=0"
        }
    } catch (_: Exception) { null }

    private fun post(key: String, url: String, body: JSONObject): Pair<Int, String> = try {
        client.newCall(reqB(url, key).post(jsonBody(body)).build()).execute()
            .use { it.code to (it.body?.string().orEmpty()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        -1 to (e.message ?: "err")
    }

    private fun get(key: String, url: String): Pair<Int, String> = try {
        client.newCall(reqB(url, key).get().build()).execute()
            .use { it.code to (it.body?.string().orEmpty()) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        -1 to (e.message ?: "err")
    }
}
