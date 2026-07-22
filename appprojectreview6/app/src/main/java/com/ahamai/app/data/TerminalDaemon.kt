package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cloud terminal daemon — a PTY + WebSocket server inside the Daytona sandbox.
 *
 * Gives the user a real interactive bash terminal in the cloud sandbox where they can
 * install codex, claude, cowsay, sl, htop, neofetch, or anything else.
 *
 * Design follows the same daemon-in-sandbox pattern as [CloudBrowser]:
 * 1. Install `websockets` (pip) on first run
 * 2. Write the daemon script to the sandbox
 * 3. Launch it detached (setsid) so it survives the exec session
 * 4. The daemon creates a PTY, spawns bash, and serves a WebSocket + HTTP terminal UI
 * 5. The app loads the daemon's URL in a WebView
 */
object TerminalDaemon {

    private const val DIR = "/workspace/.terminal"
    private const val PORT = 3001

    @Volatile private var readyFor: String? = null

    // ── The Python daemon script ─────────────────────────────────────────────────

    /**
     * Embedded Python script that:
     *  - Creates a PTY and spawns /bin/bash with a fun PS1
     *  - Serves an xterm.js HTML page on GET /
     *  - Accepts WebSocket connections on /ws for bidirectional terminal I/O
     *  - Shows a colourful welcome message with ASCII art
     */
    private val DAEMON_PY = """
import asyncio, base64, json, os, pty, signal, struct, termios, fcntl, sys, time, threading

PORT = 3001
DIR = "/workspace/.terminal"
os.makedirs(DIR, exist_ok=True)

# ── Colourful welcome ──────────────────────────────────────────────────────────
WELCOME = (
    "\r\n"
    "\x1b[38;5;45m  ╔════════════════════════════════════════════════════════╗\x1b[0m\r\n"
    "\x1b[38;5;45m  ║   \x1b[38;5;46m █████╗ ██╗  ██╗ █████╗ ███╗   ███╗  █████╗ ██╗ \x1b[38;5;45m   ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ║   \x1b[38;5;46m██╔══██╗██║  ██║██╔══██╗████╗ ████║ ██╔══██╗██║ \x1b[38;5;45m   ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ║   \x1b[38;5;46m███████║███████║███████║██╔████╔██║ ███████║██║ \x1b[38;5;45m   ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ║   \x1b[38;5;46m██╔══██║██╔══██║██╔══██║██║╚██╔╝██║ ██╔══██║██║ \x1b[38;5;45m   ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ║   \x1b[38;5;46m██║  ██║██║  ██║██║  ██║██║ ╚═╝ ██║ ██║  ██║██║ \x1b[38;5;45m   ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ║   \x1b[38;5;46m╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝     ╚═╝ ╚═╝  ╚═╝╚═╝ \x1b[38;5;45m   ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ║     \x1b[38;5;39mAhamAI Cloud Terminal — root@ahamai\x1b[38;5;45m            ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ║     \x1b[38;5;33m\"With great power comes great sudo\"\x1b[38;5;45m            ║\x1b[0m\r\n"
    "\x1b[38;5;45m  ╚════════════════════════════════════════════════════════╝\x1b[0m\r\n"
    "\r\n"
    "\x1b[38;5;240mTry: \x1b[38;5;39mcowsay\x1b[0m \x1b[38;5;244m\"hello\"\x1b[0m  \x1b[38;5;240m|  \x1b[38;5;39msl\x1b[0m  \x1b[38;5;240m|  \x1b[38;5;39mhtop\x1b[0m  \x1b[38;5;240m|  \x1b[38;5;39mneofetch\x1b[0m  \x1b[38;5;240m|  \x1b[38;5;39mapt install codex\x1b[0m\r\n"
    "\x1b[38;5;240mFun:  \x1b[38;5;39mfortune | cowsay\x1b[0m  \x1b[38;5;240m|  \x1b[38;5;39mfiglet \"wow\"\x1b[0m  \x1b[38;5;240m|  \x1b[38;5;39mlolcat\x1b[0m\r\n"
    "\r\n"
)

# ── PTY management ──────────────────────────────────────────────────────────────
class PTY:
    def __init__(self):
        self.fd = None
        self.pid = None
        self._spawn()

    def _spawn(self):
        pid, fd = pty.fork()
        if pid == 0:
            os.environ.update({
                "TERM": "xterm-256color",
                "PS1": "\\[\\e[38;5;39m\\]\\u@ahamai\\[\\e[0m\\]:\\[\\e[38;5;33m\\]\\w\\[\\e[0m\\]\\${'$'} ",
                "PROMPT_COMMAND": "",
                "HOME": "/root",
                "LANG": "en_US.UTF-8",
                "LC_ALL": "C.UTF-8",
            })
            os.execve("/bin/bash", ["/bin/bash", "--login"], os.environ)
        self.pid = pid
        self.fd = fd
        # Set raw mode on the PTY
        attr = termios.tcgetattr(fd)
        attr[3] = attr[3] & ~(termios.ECHO | termios.ICANON | termios.ISIG | termios.IEXTEN)
        attr[1] = attr[1] & ~termios.OPOST
        termios.tcsetattr(fd, termios.TCSANOW, attr)
        # Default size: 80x24
        self.resize(80, 24)

    def resize(self, cols, rows):
        try:
            s = struct.pack("HHHH", rows, cols, 0, 0)
            fcntl.ioctl(self.fd, termios.TIOCSWINSZ, s)
        except Exception:
            pass

    def read(self):
        try:
            return os.read(self.fd, 4096)
        except (OSError, BlockingIOError):
            return b""

    def write(self, data):
        try:
            os.write(self.fd, data)
        except OSError:
            pass

    def close(self):
        try:
            os.close(self.fd)
        except Exception:
            pass
        try:
            os.kill(self.pid, signal.SIGKILL)
        except Exception:
            pass

# ── Minimal WebSocket server using asyncio ────────────────────────────────────
# We avoid the `websockets` pip dependency by implementing the WebSocket
# protocol over raw TCP. This handles text frames and ping/pong.

import hashlib, struct as _struct

WS_MAGIC = b"258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

def _ws_accept(key):
    return base64.b64encode(hashlib.sha1(key.encode() + WS_MAGIC).digest()).decode()

def _encode_frame(data, opcode=0x1):
    b = bytearray()
    payload = data.encode("utf-8") if isinstance(data, str) else data
    b.append(0x80 | opcode)  # FIN + opcode
    if len(payload) < 126:
        b.append(len(payload))
    elif len(payload) < 65536:
        b.append(126)
        b.extend(_struct.pack(">H", len(payload)))
    else:
        b.append(127)
        b.extend(_struct.pack(">Q", len(payload)))
    b.extend(payload)
    return bytes(b)

def _encode_pong(payload):
    return _encode_frame(payload, opcode=0xA)

class WSServer:
    def __init__(self, pty):
        self.pty = pty
        self.clients = set()

    async def handle(self, r, w):
        try:
            data = await self._http_read(r)
            if not data:
                return
            # Check if this is a WebSocket upgrade
            if b"Upgrade: websocket" not in data and b"upgrade: websocket" not in data:
                # Regular HTTP request — serve the HTML page
                path = data.split(b" ")[1].decode() if b" " in data else "/"
                if path == "/health":
                    resp = b"HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"
                else:
                    body = self._html_page().encode("utf-8")
                    resp = (
                        b"HTTP/1.1 200 OK\r\n"
                        b"Content-Type: text/html; charset=utf-8\r\n"
                        b"Access-Control-Allow-Origin: *\r\n"
                        b"Content-Length: " + str(len(body)).encode() + b"\r\n\r\n" + body
                    )
                w.write(resp); await w.drain()
                return

            # WebSocket upgrade
            key = None
            for line in data.decode().split("\r\n"):
                if line.lower().startswith("sec-websocket-key:"):
                    key = line.split(":", 1)[1].strip()
            if not key:
                w.write(b"HTTP/1.1 400 Bad Request\r\n\r\n"); await w.drain(); return

            accept = _ws_accept(key)
            upgrade = (
                "HTTP/1.1 101 Switching Protocols\r\n"
                "Upgrade: websocket\r\n"
                "Connection: Upgrade\r\n"
                f"Sec-WebSocket-Accept: {accept}\r\n"
                "\r\n"
            )
            w.write(upgrade.encode()); await w.drain()

            self.clients.add(w)
            # Send welcome message
            w.write(_encode_frame(WELCOME + "\r\n\x1b[38;5;39mroot@ahamai\x1b[0m:\x1b[38;5;33m~${'$'}\x1b[0m "))
            await w.drain()

            # Read loop: client → PTY
            buf = b""
            while True:
                byte = await r.read(1)
                if not byte:
                    break
                buf += byte
                # Parse WebSocket frames (unmasked text frames from client)
                if len(buf) < 2:
                    continue
                opcode = buf[0] & 0x0F
                masked = bool(buf[1] & 0x80)
                length = buf[1] & 0x7F
                offset = 2
                if length == 126:
                    if len(buf) < 4: continue
                    length = _struct.unpack(">H", buf[2:4])[0]
                    offset = 4
                elif length == 127:
                     if len(buf) < 10: continue
                     length = _struct.unpack(">Q", buf[2:10])[0]
                     offset = 10
                mask_key_len = 4 if masked else 0
                header_len = offset + mask_key_len
                if len(buf) < header_len + length:
                    continue
                payload = buf[header_len:header_len + length]
                if masked:
                    key_bytes = buf[offset:offset + 4]
                    payload = bytes(b ^ key_bytes[i % 4] for i, b in enumerate(payload))
                buf = buf[header_len + length:]

                if opcode == 0x8:  # Close
                    break
                elif opcode == 0x9:  # Ping — respond with Pong
                    w.write(_encode_pong(payload))
                    await w.drain()
                elif opcode == 0xA:  # Pong — ignore
                    pass
                elif opcode == 0x1:  # Text
                    text = payload.decode("utf-8", errors="replace")
                    try:
                        j = json.loads(text)
                        if j.get("type") == "resize":
                            self.pty.resize(j.get("cols", 80), j.get("rows", 24))
                            continue
                    except json.JSONDecodeError:
                        pass
                    self.pty.write(payload)
                elif opcode == 0x2:  # Binary
                    self.pty.write(payload)
        except (ConnectionResetError, BrokenPipeError, OSError):
            pass
        except asyncio.CancelledError:
            pass
        finally:
            self.clients.discard(w)
            try: w.close()
            except Exception: pass

    def broadcast(self, data):
        frame = _encode_frame(data)
        dead = set()
        for w in self.clients:
            try:
                w.write(frame)
            except Exception:
                dead.add(w)
        self.clients -= dead

    async def _http_read(self, r):
        buf = b""
        while b"\r\n\r\n" not in buf:
            byte = await r.read(1)
            if not byte:
                return None
            buf += byte
            if len(buf) > 65536:
                return None
        # Read Content-Length if present
        head = buf.decode("utf-8", errors="replace")
        cl = 0
        for line in head.split("\r\n"):
            if line.lower().startswith("content-length:"):
                cl = int(line.split(":", 1)[1].strip())
        while len(buf) < len(head.split("\r\n\r\n", 1)[0]) + 4 + cl:
            byte = await r.read(1)
            if not byte: break
            buf += byte
        return buf

    def _html_page(self):
        return r'''<!DOCTYPE html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<title>Terminal — AhamAI</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/xterm@5.3.0/css/xterm.min.css">
<style>html,body{margin:0;padding:0;height:100%;width:100%;background:#0d1117;overflow:hidden}#term{height:100%;width:100%}.xterm-viewport{scrollbar-width:thin;scrollbar-color:#30363d transparent}</style>
</head><body><div id="term"></div>
<script src="https://cdn.jsdelivr.net/npm/xterm@5.3.0/lib/xterm.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/xterm-addon-fit@0.8.0/lib/xterm-addon-fit.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/xterm-addon-web-links@0.9.0/lib/xterm-addon-web-links.min.js"></script>
<script>
(function(){
  var term = new Terminal({
    cursorBlink: true,
    cursorStyle: 'block',
    fontSize: 14,
    lineHeight: 1.35,
    fontFamily: "'Menlo','Monaco','Courier New','JetBrains Mono',monospace",
    allowTransparency: true,
    theme: {
      background: '#0d1117',
      foreground: '#c9d1d9',
      cursor: '#58a6ff',
      cursorAccent: '#0d1117',
      selectionBackground: '#264f78',
      black: '#484f58', red: '#ff7b72', green: '#3fb950', yellow: '#d29922',
      blue: '#58a6ff', magenta: '#bc8cff', cyan: '#39c5cf', white: '#b1bac4',
      brightBlack: '#6e7681', brightRed: '#ffa198', brightGreen: '#56d364',
      brightYellow: '#e3b341', brightBlue: '#79c0ff', brightMagenta: '#d2a8ff',
      brightCyan: '#56d4dd', brightWhite: '#f0f6fc'
    }
  });
  var fit = new FitAddon.FitAddon();
  term.loadAddon(fit);
  var links = new WebLinksAddon.WebLinksAddon();
  term.loadAddon(links);
  var ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
  var connected = false;
  var queue = [];
  function sendToTerm(data){ if (connected) { ws.send(data); } else { queue.push(data); } }
  // Exposed so the native app shell can send control sequences (Clear, ^C, arrows, etc.)
  // through Android's WebView.evaluateJavascript without needing its own WebSocket.
  window.termSend = sendToTerm;
  window.termClear = function(){ term.clear(); };
  window.termFocus = function(){ term.focus(); };
  term.onData(sendToTerm);
  ws.onopen = function(){
    term.open(document.getElementById('term'));
    fit.fit();
    connected = true;
    for (var i = 0; i < queue.length; i++) ws.send(queue[i]);
    queue = [];
    ws.send(JSON.stringify({type:'resize',cols:term.cols,rows:term.rows}));
  };
  ws.onmessage = function(ev){ term.write(ev.data); };
  ws.onclose = function(){ term.write('\r\n\x1b[38;5;196mConnection closed — tap the terminal icon to reconnect\x1b[0m\r\n'); };
  var resizeTimer;
  window.addEventListener('resize', function(){
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(function(){ fit.fit(); if (connected) ws.send(JSON.stringify({type:'resize',cols:term.cols,rows:term.rows})); }, 200);
  });
  // Keep the WebSocket alive
  setInterval(function(){ if (connected) try { ws.send(''); } catch(e){} }, 30000);
})();
</script></body></html>'''

# ── Main ────────────────────────────────────────────────────────────────────────
async def main():
    pty_inst = PTY()

    # Write ready marker
    try:
        with open(os.path.join(DIR, "ready"), "w") as f:
            f.write("ok")
    except Exception:
        pass

    print("terminal-daemon-ready", flush=True)

    ws_srv = WSServer(pty_inst)

    async def tcp_handler(r, w):
        await ws_srv.handle(r, w)

    # PTY → broadcast loop
    async def pty_reader():
        loop = asyncio.get_event_loop()
        while True:
            data = await loop.run_in_executor(None, pty_inst.read)
            if data:
                ws_srv.broadcast(data)
            else:
                await asyncio.sleep(0.01)

    # Log rotation: truncate daemon.log if it exceeds 512KB to prevent disk fill
    async def log_rotation():
        log_path = os.path.join(DIR, "daemon.log")
        while True:
            await asyncio.sleep(60)  # Check every minute
            try:
                if os.path.exists(log_path) and os.path.getsize(log_path) > 512 * 1024:
                    # Keep only the last 100KB of the log
                    with open(log_path, 'r') as f:
                        lines = f.readlines()
                    if len(lines) > 200:
                        with open(log_path, 'w') as f:
                            f.writelines(lines[-200:])
            except Exception:
                pass

    # Keepalive: send a Ping every 25 seconds to prevent idle disconnection
    async def keepalive():
        while True:
            await asyncio.sleep(25)
            ping_frame = bytes([0x89, 0x00])  # FIN + Ping opcode, no payload
            dead = set()
            for w in ws_srv.clients:
                try:
                    w.write(ping_frame)
                except Exception:
                    dead.add(w)
            ws_srv.clients -= dead

    server = await asyncio.start_server(tcp_handler, "0.0.0.0", PORT)
    print(f"listening on {PORT}", flush=True)

    await asyncio.gather(
        server.serve_forever(),
        pty_reader(),
        keepalive(),
        log_rotation(),
    )

asyncio.run(main())
""".trimIndent()

    /**
     * Ensure the terminal daemon is running in the current sandbox.
     * Returns null on success or an error message.
     */
    suspend fun ensureDaemon(ctx: Context, projectDir: String, onHeartbeat: (String) -> Unit = {}): String? {
        val sid = E2BClient.activeSandboxId()
        if (sid != null && sid == readyFor) return null

        // Quick health check — maybe a detached daemon is already running
        if (sid != null) {
            val h = CloudTools.execIn(ctx, projectDir,
                "curl -sf -m 3 http://127.0.0.1:$PORT/health 2>/dev/null || echo DOWN", 20)
            if (h.stdout.trim() == "ok") { readyFor = sid; return null }
        }

        onHeartbeat("Starting cloud terminal\u2026")

        // Step 0: Check disk space — need at least 500MB free for the daemon + logs
        val diskCheck = CloudTools.execIn(ctx, projectDir,
            "df -BM /workspace 2>/dev/null | awk 'NR==2{print int(\$4)}' || echo 9999", 10)
        val freeMB = diskCheck.stdout.trim().toIntOrNull() ?: 9999
        if (freeMB < 500) {
            // Clean up old terminal files to free space
            CloudTools.execIn(ctx, projectDir,
                "rm -rf $DIR/daemon.log $DIR/terminal_daemon.py $DIR/terminal_daemon.py.b64 $DIR/ready 2>/dev/null; " +
                "pkill -f 'python3.*terminal_daemon' 2>/dev/null; " +
                "sync", 15)
            // Re-check after cleanup
            val recheck = CloudTools.execIn(ctx, projectDir,
                "df -BM /workspace 2>/dev/null | awk 'NR==2{print int(\$4)}' || echo 9999", 10)
            val afterMB = recheck.stdout.trim().toIntOrNull() ?: 9999
            if (afterMB < 200) {
                return "Insufficient disk space (${afterMB}MB free). Close the terminal and try again later."
            }
        }

        // Step 1: Kill any stale daemon and clean up old files
        CloudTools.execIn(ctx, projectDir,
            "pkill -f 'python3.*terminal_daemon.py' 2>/dev/null; sleep 0.3; " +
            "rm -f $DIR/ready $DIR/daemon.log $DIR/terminal_daemon.py.b64; " +
            "sync", 10)

        // Step 2: Write the daemon script (clean up old first)
        val b64 = Base64.encodeToString(DAEMON_PY.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val chunkSize = 8000
        val chunks = b64.chunked(chunkSize)
        val writeCmd = buildString {
            append("rm -f $DIR/terminal_daemon.py $DIR/terminal_daemon.py.b64 2>/dev/null; ")
            append("mkdir -p $DIR; ")
            for (chunk in chunks) {
                append("echo -n '$chunk' >> $DIR/terminal_daemon.py.b64; ")
            }
            append("base64 -d $DIR/terminal_daemon.py.b64 > $DIR/terminal_daemon.py; chmod +x $DIR/terminal_daemon.py; ")
            append("rm -f $DIR/terminal_daemon.py.b64; ")
            append("python3 -c 'import ast; ast.parse(open(\"$DIR/terminal_daemon.py\").read()); print(\"SYNTAX_OK\")'")
        }
        val writeResult = CloudTools.execIn(ctx, projectDir, writeCmd, 30)
        if (!writeResult.stdout.contains("SYNTAX_OK"))
            return "failed to write terminal daemon script:\n${writeResult.formatted(500)}"

        // Step 3: Launch the daemon detached (truncate old log to prevent disk fill)
        val launch = CloudTools.execIn(ctx, projectDir,
            "cd $DIR; " +
            "> $DIR/daemon.log; " +  // Truncate log file
            "if command -v setsid >/dev/null 2>&1; then " +
            "  setsid python3 -u terminal_daemon.py > $DIR/daemon.log 2>&1 < /dev/null & " +
            "else " +
            "  nohup python3 -u terminal_daemon.py > $DIR/daemon.log 2>&1 < /dev/null & " +
            "fi; " +
            "sleep 1; echo LAUNCHED", 30)
        if (!launch.stdout.contains("LAUNCHED"))
            return "failed to launch terminal daemon:\n${launch.formatted(500)}"

        // Step 5: Wait for health
        val wait = CloudTools.execIn(ctx, projectDir,
            "for i in \$(seq 1 30); do curl -sf -m 2 http://127.0.0.1:$PORT/health 2>/dev/null | grep -q ok && { echo __HEALTHY__; exit 0; }; sleep 1; done; " +
            "echo TIMEOUT; echo '--- daemon.log (tail) ---'; tail -25 $DIR/daemon.log 2>/dev/null; " +
            "echo '--- ps ---'; ps aux 2>/dev/null | grep -i '[t]erminal_daemon' || echo 'daemon process NOT running'", 60)
        if (wait.stdout.contains("__HEALTHY__")) {
            readyFor = E2BClient.activeSandboxId()
            return null
        }
        return "terminal daemon did not start in time:\n${wait.stdout.take(900)}"
    }

    /** Get the WebSocket URL for the terminal, or null if the daemon isn't up yet. */
    suspend fun wsUrl(ctx: Context): String? {
        val id = E2BClient.activeSandboxId() ?: return null
        if (id != readyFor) return null
        val apiKey = PreferencesManager(ctx).getE2bApiKey()
        val portUrl = E2BClient.sandboxPortUrl(apiKey, PORT) ?: return null
        return portUrl
            .replace("http://", "ws://")
            .replace("https://", "wss://") + "/ws"
    }

    /** Get the HTTP URL for the terminal HTML page. */
    suspend fun httpUrl(ctx: Context): String? {
        val id = E2BClient.activeSandboxId() ?: return null
        if (id != readyFor) return null
        val apiKey = PreferencesManager(ctx).getE2bApiKey()
        return E2BClient.sandboxPortUrl(apiKey, PORT)
    }

    /** Kill the terminal daemon and clean up all terminal files. */
    suspend fun killDaemon(ctx: Context, projectDir: String) {
        readyFor = null
        CloudTools.execIn(ctx, projectDir,
            "pkill -f 'python3.*terminal_daemon.py' 2>/dev/null; " +
            "rm -rf $DIR/daemon.log $DIR/ready $DIR/terminal_daemon.py $DIR/terminal_daemon.py.b64; " +
            "sync", 10)
    }
}
