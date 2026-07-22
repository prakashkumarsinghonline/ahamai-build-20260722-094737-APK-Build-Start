package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Agentic browser — real **Chromium + Playwright** inside an **E2B** sandbox (not Daytona).
 *
 * Design:
 *  - Long-lived Python **Playwright daemon** keeps ONE browser + page alive across tool calls.
 *  - Daemon HTTP on port 3000 → live view at `https://3000-{sandboxId}.e2b.app`
 *  - Numbered interactive elements for model-friendly click/type-by-index
 *  - Synthetic cursor visible in the live WebView stream
 *  - **Warm path**: [warmUp] pre-creates the E2B sandbox, installs Playwright/Chromium, starts
 *    the daemon so the first BROWSER_OPEN is near-instant instead of a cold 1–2 min boot.
 */
object CloudBrowser {

    private const val DIR = "/workspace/.browser"

    /** Port the in-sandbox daemon HTTP server listens on (public via E2B). */
    private const val PORT = 3000

    /** Sandbox id whose daemon answered /health — hot path skips re-provision. */
    @Volatile private var readyFor: String? = null

    /** In-flight warm job so multiple screens don't double-install. */
    @Volatile private var warming = false
    @Volatile private var lastWarmMsg: String = ""

    /** The Playwright daemon script (kept in Kotlin, shipped to the sandbox base64-encoded).
     *  NOTE: no '$' may appear unescaped here (Kotlin string templates); the daemon avoids it. */
    private val DAEMON_PY = """
import json, os, time, threading, queue, base64, traceback
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from playwright.sync_api import sync_playwright

BD = "/workspace/.browser"
os.makedirs(BD, exist_ok=True)
PROFILE = os.path.join(BD, "profile")
PORT = 3000

cmd_q = queue.Queue()
latest = {"jpg": b""}
lk = threading.Lock()
last_view = {"t": 0.0}
# Bumped on every iteration of the main loop (below). If a blocking Playwright call inside
# do_action() ever wedges (page.evaluate() has no built-in timeout, unlike click/fill/goto),
# this stops advancing — /health uses that to tell "HTTP server up" apart from "browser actually
# responsive", so a hung page gets detected and self-healed via reprovision instead of silently
# stalling forever.
last_tick = {"t": time.time()}

SNAP_JS = r'''
() => {
  const out = [];
  const sel = 'a,button,input,textarea,select,summary,label,option,[role=button],[role=link],[role=tab],[role=option],[role=checkbox],[role=radio],[role=menuitem],[role=combobox],[role=listbox],[role=searchbox],[role=switch],[role=slider],[onclick],[tabindex]';
  const els = Array.from(document.querySelectorAll(sel));
  let i = 0;
  for (const el of els) {
    const r = el.getBoundingClientRect();
    if (r.width < 2 || r.height < 2) continue;
    const st = window.getComputedStyle(el);
    if (st.visibility === 'hidden' || st.display === 'none' || st.opacity === '0') continue;
    i++;
    el.setAttribute('data-ai-idx', String(i));
    let label = (el.getAttribute('aria-label') || el.placeholder || el.value || el.innerText || el.getAttribute('title') || '').trim().replace(/\s+/g,' ').slice(0,80);
    const tag = el.tagName.toLowerCase();
    const type = el.getAttribute('type') || '';
    out.push({i, tag, type, label, href: (el.getAttribute('href')||'').slice(0,120)});
    if (i >= 120) break;
  }
  return out;
}
'''

# Injected into every page: a synthetic cursor + click ripple so the LIVE view shows the
# pointer moving and clicking (headless Chromium does not paint a real OS cursor).
CURSOR_JS = r'''
(function(){
  function ensure() {
    if (!document.documentElement) return null;
    var c = document.getElementById('__ai_cursor');
    if (!c) {
      c = document.createElement('div');
      c.id = '__ai_cursor';
      c.style.cssText = 'position:fixed;left:0;top:0;width:24px;height:24px;z-index:2147483647;pointer-events:none;margin-left:-2px;margin-top:-1px;transition:left .16s cubic-bezier(.22,.61,.36,1),top .16s cubic-bezier(.22,.61,.36,1);filter:drop-shadow(0 1px 2px rgba(0,0,0,.45));';
      c.innerHTML = '<svg width="24" height="24" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M4 2 L4 20 L9 15 L12.5 22 L15 21 L11.5 14 L18 14 Z" fill="#111" stroke="#fff" stroke-width="1.4" stroke-linejoin="round"/></svg>';
      document.documentElement.appendChild(c);
    }
    return c;
  }
  window.__aimove = function(x, y){ var c = ensure(); if (c){ c.style.left = x + 'px'; c.style.top = y + 'px'; } };
  window.__aiclick = function(x, y){
    window.__aimove(x, y);
    if (!document.documentElement) return;
    var r = document.createElement('div');
    r.style.cssText = 'position:fixed;left:' + x + 'px;top:' + y + 'px;width:14px;height:14px;margin:-7px 0 0 -7px;border-radius:50%;background:rgba(56,132,255,.5);border:2px solid rgba(56,132,255,.9);z-index:2147483646;pointer-events:none;transform:scale(.4);opacity:1;transition:transform .45s ease,opacity .45s ease;';
    document.documentElement.appendChild(r);
    requestAnimationFrame(function(){ r.style.transform = 'scale(3.2)'; r.style.opacity = '0'; });
    setTimeout(function(){ try { r.remove(); } catch(e){} }, 480);
  };
})();
'''

CENTER_JS = r'''
(i) => {
  var e = document.querySelector('[data-ai-idx="' + i + '"]');
  if (!e) return null;
  var r = e.getBoundingClientRect();
  return [Math.round(r.left + r.width/2), Math.round(r.top + r.height/2)];
}
'''

LIVE_HTML = '''<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>AhamAI Live Browser</title>
<style>html,body{margin:0;background:#0d0d0d;height:100%;overflow:hidden}
#c{position:relative;width:100%;height:100%}
/* No opacity transition: the incoming frame is fully decoded before we show it, so an
   instant double-buffer swap is seamless. The old .12s crossfade caused the repeated blink. */
.f{position:absolute;top:0;left:0;width:100%;height:100%;object-fit:contain;background:#0d0d0d}
</style>
</head><body>
<div id="c"><img class="f" id="a" alt="live"><img class="f" id="b" alt="live" style="opacity:0"></div>
<script>
var a = document.getElementById('a'), b = document.getElementById('b');
var front = a, back = b;
var loading = false;
function poll(){
  if (loading) return;
  loading = true;
  var img = new Image();
  img.onload = function(){
    back.src = img.src;
    back.style.opacity = '1';
    front.style.opacity = '0';
    var tmp = front; front = back; back = tmp;
    loading = false;
  };
  img.onerror = function(){ loading = false; };
  img.src = '/shot?t=' + Date.now();
}
setInterval(poll, 140);
poll();
</script>
</body></html>'''

def snapshot(page):
    try:
        els = page.evaluate(SNAP_JS)
    except Exception:
        els = []
    lines = []
    for e in els:
        t = e['tag'] + ((':' + e['type']) if e['type'] else '')
        extra = ('  -> ' + e['href']) if e.get('href') else ''
        lines.append("[%d] %s  \"%s\"%s" % (e['i'], t, e['label'], extra))
    return "\n".join(lines)

def grab(page):
    try:
        b = page.screenshot(type="jpeg", quality=60)
        with lk:
            latest["jpg"] = b
        return b
    except Exception:
        with lk:
            return latest["jpg"]

def center(page, idx):
    try:
        return page.evaluate(CENTER_JS, str(idx))
    except Exception:
        return None

def move_to(page, xy):
    if not xy: return
    try: page.evaluate("(p) => window.__aimove && window.__aimove(p[0], p[1])", xy)
    except Exception: pass

def click_fx(page, xy):
    if not xy: return
    try: page.evaluate("(p) => window.__aiclick && window.__aiclick(p[0], p[1])", xy)
    except Exception: pass

def do_action(page, cmd):
    action = cmd.get("action", "")
    target = str(cmd.get("target", "") or "")
    value = cmd.get("value", "") or ""
    res = {"ok": True}
    try:
        if action == "goto":
            url = target if "://" in target else "https://" + target
            page.goto(url, timeout=45000, wait_until="domcontentloaded")
        elif action == "click":
            # Re-index elements NOW so data-ai-idx reflects the current DOM.
            try: page.evaluate(SNAP_JS)
            except Exception: pass
            xy = center(page, target)
            if xy:
                move_to(page, xy); grab(page); page.wait_for_timeout(80); grab(page)
                click_fx(page, xy); grab(page)
            # Try selector click first; fall back to coordinate click if element gone.
            try:
                page.click('[data-ai-idx="%s"]' % target, timeout=8000)
            except Exception:
                if xy:
                    # Coordinate fallback: the element existed moments ago, click its center.
                    page.mouse.click(xy[0], xy[1])
                else:
                    res['ok'] = False
                    res['error'] = 'Element with index "' + target + '" not found. Use BROWSER_VIEW to refresh.'
        elif action == "type":
            # Re-index elements NOW so data-ai-idx reflects the current DOM.
            try: page.evaluate(SNAP_JS)
            except Exception: pass
            xy = center(page, target)
            if xy:
                move_to(page, xy); grab(page); page.wait_for_timeout(80); click_fx(page, xy)
            # Try selector fill first; fall back to click + keyboard if element gone.
            try:
                page.fill('[data-ai-idx="%s"]' % target, value, timeout=8000)
            except Exception:
                if xy:
                    # Coordinate fallback: click field then type.
                    page.mouse.click(xy[0], xy[1])
                    page.wait_for_timeout(100)
                    page.keyboard.press("Control+a")
                    page.keyboard.type(value, delay=20)
                else:
                    res['ok'] = False
                    res['error'] = 'Element with index "' + target + '" not found. Use BROWSER_VIEW to refresh.'
        elif action == "press":
            page.keyboard.press(value or "Enter")
        elif action == "scroll":
            direction = (value or "down").strip().lower()
            amt = 600
            if direction == "up":
                page.mouse.wheel(0, -amt)
            elif direction == "left":
                page.mouse.wheel(-amt, 0)
            elif direction == "right":
                page.mouse.wheel(amt, 0)
            else:
                page.mouse.wheel(0, amt)
            page.wait_for_timeout(200)
        elif action == "back":
            page.go_back(timeout=20000)
        elif action == "wait":
            page.wait_for_timeout(int(float(value or "1")) * 1000)
        elif action == "viewport":
            try:
                w, h = target.split(",", 1)
                page.set_viewport_size({"width": int(w.strip()), "height": int(h.strip())})
                page.wait_for_timeout(300)
            except Exception as e:
                res["ok"] = False
                res["error"] = "viewport resize failed: " + (str(e) or repr(e))[:300]
        elif action == "rotate":
            try:
                cur = page.viewport_size
                w, h = cur["width"], cur["height"]
                page.set_viewport_size({"width": h, "height": w})
                page.wait_for_timeout(300)
            except Exception as e:
                res["ok"] = False
                res["error"] = "rotate failed: " + (str(e) or repr(e))[:300]
        elif action == "upload":
            # Set files on a file <input> (index in target, comma-separated sandbox paths in value).
            try: page.evaluate(SNAP_JS)
            except Exception: pass
            paths = [p.strip() for p in str(value).split(",") if p.strip()]
            try:
                page.set_input_files('[data-ai-idx="%s"]' % target, paths, timeout=15000)
            except Exception:
                # Fallback: attach to the first file input on the page.
                try:
                    page.set_input_files('input[type="file"]', paths, timeout=8000)
                except Exception as e:
                    res["ok"] = False
                    res["error"] = "upload failed (index %s): %s" % (target, (str(e) or repr(e))[:280])
        elif action == "select":
            # Choose an <option> in a <select> by value or visible label (index in target).
            try: page.evaluate(SNAP_JS)
            except Exception: pass
            try:
                try:
                    page.select_option('[data-ai-idx="%s"]' % target, value=value, timeout=6000)
                except Exception:
                    page.select_option('[data-ai-idx="%s"]' % target, label=value, timeout=6000)
            except Exception as e:
                res["ok"] = False
                res["error"] = "select failed (index %s): %s" % (target, (str(e) or repr(e))[:280])
        elif action == "extract":
            res["text"] = page.evaluate("() => document.body.innerText").strip()[:6000]
        elif action == "snapshot":
            pass
        else:
            res["ok"] = False; res["error"] = "unknown action: " + action
        # Only navigations need the network to settle; interactions do not (keeps steps snappy).
        if action in ("goto", "back"):
            try: page.wait_for_load_state("networkidle", timeout=1500)
            except Exception: pass
    except Exception as e:
        res["ok"] = False
        res["error"] = (str(e) or repr(e))[:400]
    try: res["url"] = page.url
    except Exception: res["url"] = ""
    try: res["title"] = page.title()[:140]
    except Exception: res["title"] = ""
    if "text" not in res:
        res["snapshot"] = snapshot(page)
    b = grab(page)
    try:
        res["shot_b64"] = base64.b64encode(b).decode("ascii") if b else ""
    except Exception:
        res["shot_b64"] = ""
    return res

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def _cors(self):
        self.send_header("Access-Control-Allow-Origin", "*")
    def do_GET(self):
        p = self.path.split("?")[0]
        if p == "/health":
            # Alive HTTP server != responsive browser. If the main loop hasn't ticked recently,
            # it's wedged inside a blocking Playwright call (a hung page) — report unhealthy so
            # the app kills and reprovisions this daemon instead of queuing commands forever.
            if time.time() - last_tick["t"] > 8.0:
                self.send_response(503); self._cors(); self.end_headers()
                try: self.wfile.write(b"stuck")
                except Exception: pass
                return
            self.send_response(200); self._cors(); self.end_headers()
            try: self.wfile.write(b"ok")
            except Exception: pass
            return
        if p == "/shot":
            last_view["t"] = time.time()
            with lk: b = latest["jpg"]
            self.send_response(200); self.send_header("Content-Type", "image/jpeg")
            self.send_header("Cache-Control", "no-store"); self._cors(); self.end_headers()
            try: self.wfile.write(b)
            except Exception: pass
            return
        if p == "/stream":
            last_view["t"] = time.time()
            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
            self.send_header("Cache-Control", "no-store"); self._cors(); self.end_headers()
            try:
                while True:
                    last_view["t"] = time.time()
                    with lk: b = latest["jpg"]
                    if b:
                        self.wfile.write(b"--frame\r\nContent-Type: image/jpeg\r\nContent-Length: " + str(len(b)).encode() + b"\r\n\r\n")
                        self.wfile.write(b)
                        self.wfile.write(b"\r\n")
                    time.sleep(0.2)
            except Exception:
                return
        if p == "/" or p == "/live":
            last_view["t"] = time.time()
            self.send_response(200); self.send_header("Content-Type", "text/html; charset=utf-8"); self._cors(); self.end_headers()
            try: self.wfile.write(LIVE_HTML.encode("utf-8"))
            except Exception: pass
            return
        self.send_response(404); self.end_headers()
    def do_POST(self):
        if self.path.split("?")[0] == "/cmd":
            try:
                n = int(self.headers.get("Content-Length", "0") or "0")
                body = self.rfile.read(n) if n > 0 else b"{}"
                cmd = json.loads(body.decode("utf-8") or "{}")
            except Exception:
                cmd = {}
            holder = {"ev": threading.Event(), "data": None}
            cmd_q.put((cmd, holder))
            holder["ev"].wait(timeout=58)
            data = holder["data"] or {"ok": False, "error": "browser action timed out"}
            out = json.dumps(data).encode("utf-8")
            self.send_response(200); self.send_header("Content-Type", "application/json"); self._cors(); self.end_headers()
            try: self.wfile.write(out)
            except Exception: pass
            return
        self.send_response(404); self.end_headers()

def main():
    with sync_playwright() as p:
        ctx = p.chromium.launch_persistent_context(
            PROFILE, headless=True, viewport={"width":1280,"height":820},
            args=[
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--disable-extensions",
                "--no-first-run",
            ],
            user_agent="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36",
        )
        try: ctx.add_init_script(CURSOR_JS)
        except Exception: pass
        page = ctx.pages[0] if ctx.pages else ctx.new_page()
        grab(page)
        srv = None
        for _ in range(20):
            try:
                srv = ThreadingHTTPServer(("0.0.0.0", PORT), H); break
            except OSError:
                time.sleep(0.5)
        if srv is None:
            srv = ThreadingHTTPServer(("0.0.0.0", PORT), H)
        threading.Thread(target=srv.serve_forever, daemon=True).start()
        try: open(os.path.join(BD, "ready"), "w").write("ok")
        except Exception: pass
        print("daemon-ready", flush=True)
        last_hb = time.time()
        last_grab = 0.0
        while True:
            # Marks the loop as alive for this iteration. If do_action() below hangs on a wedged
            # page, this timestamp goes stale and /health starts reporting unhealthy.
            last_tick["t"] = time.time()
            try:
                try:
                    # Snappier command pickup so agent actions fire with minimal queue latency.
                    item = cmd_q.get(timeout=0.03)
                except queue.Empty:
                    item = None
                if item is not None:
                    cmd, holder = item
                    holder["data"] = do_action(page, cmd)
                    holder["ev"].set()
                    last_grab = time.time()  # do_action already refreshed the frame
                    continue
                # Idle: keep the live frame fresh only while someone is watching, but throttle to
                # ~10fps so screenshotting doesn't starve the 2-core sandbox and slow real actions.
                now = time.time()
                if (now - last_view["t"]) < 3.0 and (now - last_grab) >= 0.10:
                    grab(page); last_grab = now
                # Heartbeat on stdout so the launcher's held connection never idles out.
                if now - last_hb > 15:
                    print("hb", flush=True); last_hb = now
            except Exception:
                time.sleep(0.2)

main()
""".trimIndent()

    /**
     * Install Playwright + Chromium and start the daemon if it isn't already running, then wait
     * until its HTTP server answers /health on the public port. Idempotent. Returns null on
     * success or a human-readable error.
     */
    /**
     * Background warm: E2B sandbox + Playwright + daemon.
     * Safe to call from MainActivity / Agent home — no-ops if already warm or warming.
     */
    suspend fun warmUp(ctx: Context, projectDir: String = ""): String = withContext(Dispatchers.IO) {
        if (isWarm()) return@withContext "Browser already warm (E2B + Playwright ready)."
        if (warming) return@withContext lastWarmMsg.ifBlank { "Browser warm already in progress…" }
        warming = true
        lastWarmMsg = "Warming E2B browser…"
        try {
            val prefs = PreferencesManager(ctx)
            if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
                lastWarmMsg = "Cloud engine not configured — set an E2B key (e2b_…) in Profile."
                return@withContext lastWarmMsg
            }
            val key = prefs.getE2bApiKey().trim()
            if (!E2BClient.isE2bKey(key) && key.startsWith("dtn_", ignoreCase = true)) {
                lastWarmMsg = "Daytona keys are no longer used for browser. Add an E2B API key (starts with e2b_)."
                return@withContext lastWarmMsg
            }
            val dir = projectDir.ifBlank {
                // Lightweight placeholder workspace for warm-only
                File(ctx.filesDir, "browser_warm").apply { mkdirs() }.absolutePath
            }
            lastWarmMsg = E2BClient.warmSandbox(dir, key, prefs.getE2bTemplate())
            val err = ensureDaemon(ctx, dir) { lastWarmMsg = it }
            lastWarmMsg = if (err == null) {
                "Browser warm ✓ — E2B Playwright daemon ready on port $PORT"
            } else {
                "Browser warm partial: $err"
            }
            lastWarmMsg
        } catch (e: Exception) {
            lastWarmMsg = "Browser warm failed: ${e.message?.take(180)}"
            lastWarmMsg
        } finally {
            warming = false
        }
    }

    fun isWarm(): Boolean {
        val sid = E2BClient.activeSandboxId()
        return sid != null && sid == readyFor
    }

    fun lastWarmStatus(): String = lastWarmMsg

    private suspend fun ensureDaemon(ctx: Context, projectDir: String, onHeartbeat: (String) -> Unit = {}): String? {
        // E2B only — Daytona (dtn_) is rejected for browser.
        val cloudKey = PreferencesManager(ctx).getE2bApiKey().trim()
        if (cloudKey.isBlank()) {
            return "E2B API key missing. Add a key starting with e2b_ in Profile → Cloud Engine."
        }
        if (cloudKey.startsWith("dtn_", ignoreCase = true)) {
            return "Browser uses E2B (Playwright), not Daytona. Replace dtn_… with an e2b_… API key."
        }

        val sid = E2BClient.activeSandboxId()
        if (sid != null && sid == readyFor) return null

        // Reconnect if daemon still healthy in this sandbox
        if (sid != null) {
            val h = CloudTools.execIn(ctx, projectDir,
                "curl -sf -m 3 http://127.0.0.1:$PORT/health 2>/dev/null || echo DOWN", 20)
            if (h.stdout.contains("ok")) { readyFor = sid; return null }
        }

        onHeartbeat("Warming E2B + Playwright browser…")

        // Ensure sandbox exists first (warm path)
        try {
            E2BClient.warmSandbox(projectDir, cloudKey, PreferencesManager(ctx).getE2bTemplate())
        } catch (e: Exception) {
            return "E2B sandbox create failed: ${e.message?.take(200)}"
        }

        // Step 1: Install Playwright + Chromium + system deps (idempotent / fast if cached)
        val smokePy = """
import sys
try:
  from playwright.sync_api import sync_playwright
  with sync_playwright() as p:
    b = p.chromium.launch(headless=True, args=['--no-sandbox','--disable-dev-shm-usage','--disable-gpu'])
    b.new_page().goto('about:blank', timeout=15000)
    b.close()
  print('SMOKE_OK')
except Exception as e:
  print('SMOKE_FAIL', type(e).__name__, str(e)[:400])
  sys.exit(1)
""".trimIndent()
        val smokeB64 = Base64.encodeToString(smokePy.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

        val install = CloudTools.execProv(ctx, projectDir,
            "export DEBIAN_FRONTEND=noninteractive; " +
            "export PATH=\"\$HOME/.local/bin:/usr/local/bin:\$PATH\"; " +
            "echo '[1/5] playwright package'; " +
            "python3 -c 'import playwright' 2>/dev/null || " +
            "  pip3 install --quiet playwright 2>/dev/null || " +
            "  pip3 install --break-system-packages --quiet playwright; " +
            "echo '[2/5] system libs'; " +
            "if ! ldconfig -p 2>/dev/null | grep -q 'libglib-2.0.so.0'; then " +
            "  apt-get update -qq >/dev/null 2>&1 || true; " +
            "  python3 -m playwright install-deps chromium >/tmp/pw-deps.log 2>&1 || true; " +
            "  apt-get install -y -qq --no-install-recommends " +
            "    libglib2.0-0t64 libglib2.0-0 libgobject-2.0-0 " +
            "    libatk1.0-0t64 libatk1.0-0 libatk-bridge2.0-0t64 libatk-bridge2.0-0 " +
            "    libcups2t64 libcups2 libdbus-1-3 libdrm2 libgbm1 libgtk-3-0t64 libgtk-3-0 " +
            "    libnspr4 libnss3 libpango-1.0-0 libcairo2 libasound2t64 libasound2 " +
            "    libx11-6 libx11-xcb1 libxcb1 libxcomposite1 libxdamage1 libxext6 " +
            "    libxfixes3 libxrandr2 libxrender1 libxkbcommon0 libxshmfence1 " +
            "    fonts-liberation ca-certificates wget >/dev/null 2>&1 || " +
            "  apt-get install -y -qq --no-install-recommends " +
            "    libglib2.0-0 libatk1.0-0 libatk-bridge2.0-0 libcups2 libdbus-1-3 " +
            "    libdrm2 libgbm1 libgtk-3-0 libnspr4 libnss3 libpango-1.0-0 libcairo2 " +
            "    libasound2 libx11-6 libxcomposite1 libxdamage1 libxext6 libxfixes3 " +
            "    libxrandr2 fonts-liberation ca-certificates >/dev/null 2>&1 || true; " +
            "fi; " +
            "echo '[3/5] chromium'; " +
            "if ! find \"\$HOME/.cache/ms-playwright\" -maxdepth 1 -type d " +
            "  \\( -name 'chromium-*' -o -name 'chromium_headless_shell-*' \\) 2>/dev/null | grep -q .; then " +
            "  python3 -m playwright install chromium 2>&1 | tail -12; " +
            "fi; " +
            "echo '[4/5] smoke-launch'; " +
            "echo '$smokeB64' | base64 -d > /tmp/pw_smoke.py; " +
            "python3 /tmp/pw_smoke.py; SMOKE=\$?; " +
            "echo \"[5/5] smoke_exit=\$SMOKE\"; " +
            "if [ \"\$SMOKE\" = \"0\" ]; then echo __OK__; else " +
            "  echo __FAIL__; ldconfig -p 2>/dev/null | grep -E 'glib|atk|nss' | head -10; " +
            "  ls -la \"\$HOME/.cache/ms-playwright\" 2>/dev/null | head -15; true; " +
            "fi", 600)
        if (!install.stdout.contains("__OK__") && !install.stdout.contains("SMOKE_OK"))
            return "Playwright/Chromium setup failed on E2B:\n${install.formatted(1200)}"

        // Step 2: Kill stale daemon; keep profile if healthy re-warm (faster) — only wipe if broken
        CloudTools.execIn(ctx, projectDir,
            "python3 -c \"import glob,os,signal; " +
            "[os.kill(int(p.split('/')[2]),signal.SIGTERM) for p in glob.glob('/proc/[0-9]*/cmdline') " +
            "if b'daemon.py' in open(p,'rb').read()]\" 2>/dev/null; sleep 0.3; " +
            "rm -f $DIR/ready; true", 15)

        // Step 3: Write daemon (base64 chunks — no heredoc)
        val b64 = Base64.encodeToString(DAEMON_PY.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val chunkSize = 8000
        val chunks = b64.chunked(chunkSize)
        val writeCmd = buildString {
            append("mkdir -p $DIR; rm -f $DIR/daemon.py.b64; ")
            for (chunk in chunks) {
                append("echo -n '$chunk' >> $DIR/daemon.py.b64; ")
            }
            append("base64 -d $DIR/daemon.py.b64 > $DIR/daemon.py; chmod +x $DIR/daemon.py; ")
            append("rm -f $DIR/daemon.py.b64; ")
            append("python3 -c 'import ast; ast.parse(open(\"$DIR/daemon.py\").read()); print(\"SYNTAX_OK\")'")
        }
        val writeResult = CloudTools.execIn(ctx, projectDir, writeCmd, 30)
        if (!writeResult.stdout.contains("SYNTAX_OK"))
            return "failed to write Playwright daemon:\n${writeResult.formatted(500)}"

        // Step 4: Detached launch (survives exec return — critical for warm + multi-step)
        val launch = CloudTools.execIn(ctx, projectDir,
            "cd $DIR; " +
            "if command -v setsid >/dev/null 2>&1; then " +
            "  setsid python3 -u daemon.py > $DIR/daemon.log 2>&1 < /dev/null & " +
            "else " +
            "  nohup python3 -u daemon.py > $DIR/daemon.log 2>&1 < /dev/null & " +
            "fi; " +
            "sleep 1; echo LAUNCHED", 30)
        if (!launch.stdout.contains("LAUNCHED"))
            return "failed to launch Playwright daemon:\n${launch.formatted(500)}"

        // Step 5: Wait for /health (cold Chromium can take 15–40s)
        val wait = CloudTools.execIn(ctx, projectDir,
            "for i in \$(seq 1 90); do curl -sf -m 2 http://127.0.0.1:$PORT/health 2>/dev/null | grep -q ok && { echo __HEALTHY__; exit 0; }; sleep 1; done; " +
            "echo TIMEOUT; echo '--- daemon.log ---'; tail -30 $DIR/daemon.log 2>/dev/null; " +
            "ps aux 2>/dev/null | grep -i '[d]aemon.py' || echo 'daemon NOT running'", 120)
        if (wait.stdout.contains("__HEALTHY__")) {
            readyFor = E2BClient.activeSandboxId()
            lastWarmMsg = "Browser warm ✓"
            return null
        }
        return "Playwright daemon did not become healthy:\n${wait.stdout.take(900)}"
    }

    /**
     * Send one command to the daemon and build a model-readable report. Commands travel over the
     * RELIABLE envd channel — we `curl` the daemon's localhost HTTP server from inside the sandbox
     * (one round-trip), instead of the public {port}-{id}.e2b.app host which may be unreachable.
     * The live-view WebView still uses the public host (best-effort; never blocks the agent).
     */
    private suspend fun command(
        ctx: Context, projectDir: String, action: String, target: String = "", value: String = "",
        onHeartbeat: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        ensureDaemon(ctx, projectDir, onHeartbeat)?.let { return@withContext "ERROR: $it" }

        val payload = JSONObject()
            .put("action", action).put("target", target).put("value", value)
            .toString()
        val pb64 = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val curl = "echo '$pb64' | base64 -d > $DIR/req.json; " +
            "curl -s -m 60 -X POST --data-binary @$DIR/req.json http://127.0.0.1:$PORT/cmd"

        var out = CloudTools.execIn(ctx, projectDir, curl, 80).stdout.trim()
        if (out.indexOf('{') < 0) {
            // Daemon may have been reaped; force a re-provision once and retry.
            readyFor = null
            ensureDaemon(ctx, projectDir, onHeartbeat)?.let { return@withContext "ERROR: $it" }
            out = CloudTools.execIn(ctx, projectDir, curl, 80).stdout.trim()
        }
        val start = out.indexOf('{')
        if (start < 0) return@withContext "ERROR: the live browser did not respond.\n${out.take(300)}"
        val res = runCatching { JSONObject(out.substring(start)) }.getOrNull()
            ?: return@withContext "ERROR: unexpected browser response: ${out.take(200)}"

        // The daemon's own action loop is single-threaded and synchronous (Playwright sync API):
        // if a page ever wedges (e.g. page.evaluate() has no built-in timeout, unlike click/fill),
        // every future command queues behind it and the /cmd handler eventually reports this same
        // timeout forever. Clearing readyFor forces the NEXT call through the real /health check
        // (now wedge-aware, see last_tick in the daemon script), which detects the stuck daemon
        // and relaunches a fresh one instead of retrying against the dead one in a loop.
        if (res.optString("error").contains("timed out")) readyFor = null

        // E2B public port host: https://3000-{sandboxId}.e2b.app
        val liveUrl = E2BClient.sandboxPortUrl(PreferencesManager(ctx).getE2bApiKey(), PORT)
            ?: E2BClient.sandboxPortUrl(PORT)

        val sb = StringBuilder()
        if (!res.optBoolean("ok", true)) sb.append("ACTION FAILED: ${res.optString("error")}\n")
        val url = res.optString("url"); val title = res.optString("title")
        if (url.isNotBlank()) sb.append("URL: $url\n")
        if (title.isNotBlank()) sb.append("TITLE: $title\n")
        if (isWarm()) sb.append("ENGINE: E2B Playwright (warm)\n")
        else sb.append("ENGINE: E2B Playwright\n")
        // Sentinel for live WebView — no trailing slash (parser-safe)
        if (!liveUrl.isNullOrBlank()) {
            sb.append("[[BROWSER_LIVE]]${liveUrl.trimEnd('/')}[[/BROWSER_LIVE]]\n")
        } else {
            android.util.Log.w("CloudBrowser", "live preview URL unavailable (daemon OK)")
            sb.append("NOTE: Browser running but live URL missing. Retry BROWSER_VIEW.\n")
        }
        if (res.has("text")) {
            sb.append("\nPAGE TEXT:\n").append(res.optString("text"))
        } else {
            val snap = res.optString("snapshot")
            sb.append("\nINTERACTIVE ELEMENTS (act by index):\n")
                .append(if (snap.isBlank()) "(none detected)" else snap)
        }
        sb.toString().take(7000)
    }

    // ── Public tool entry points ────────────────────────────────────────────────────────────
    suspend fun open(ctx: Context, projectDir: String, url: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "goto", url, onHeartbeat = hb)

    suspend fun click(ctx: Context, projectDir: String, index: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "click", index.trim(), onHeartbeat = hb)

    suspend fun type(ctx: Context, projectDir: String, index: String, text: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "type", index.trim(), text, hb)

    suspend fun press(ctx: Context, projectDir: String, key: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "press", value = key.ifBlank { "Enter" }, onHeartbeat = hb)

    suspend fun scroll(ctx: Context, projectDir: String, dir: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "scroll", value = dir.ifBlank { "down" }, onHeartbeat = hb)

    suspend fun back(ctx: Context, projectDir: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "back", onHeartbeat = hb)

    suspend fun extract(ctx: Context, projectDir: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "extract", onHeartbeat = hb)

    suspend fun setViewport(ctx: Context, projectDir: String, width: Int, height: Int, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "viewport", "$width, $height", onHeartbeat = hb)

    suspend fun rotate(ctx: Context, projectDir: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "rotate", onHeartbeat = hb)

    suspend fun view(ctx: Context, projectDir: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "snapshot", onHeartbeat = hb)

    /** Upload document(s) to a file input. [paths] are project-relative; resolved to /workspace. */
    suspend fun upload(ctx: Context, projectDir: String, index: String, paths: String, hb: (String) -> Unit = {}): String {
        val resolved = paths.split(",").map { it.trim() }.filter { it.isNotBlank() }.joinToString(",") { p ->
            if (p.startsWith("/")) p else "/workspace/${p.removePrefix("./")}"
        }
        return command(ctx, projectDir, "upload", index.trim(), resolved, hb)
    }

    /** Choose an option in a <select> dropdown by value or visible label. */
    suspend fun select(ctx: Context, projectDir: String, index: String, option: String, hb: (String) -> Unit = {}) =
        command(ctx, projectDir, "select", index.trim(), option, hb)
}
