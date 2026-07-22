package com.ahamai.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Real **E2B** cloud sandbox client (Playwright browser + shell for AhamAI).
 *
 * Replaces the previous Daytona facade. Public methods keep the same names so
 * CloudBrowser / CloudTools / FastSync keep compiling.
 *
 * API:
 *  - Create: `POST https://api.e2b.dev/sandboxes` with `X-API-KEY: e2b_…`
 *  - Commands / files: envd on `https://49983-{sandboxId}.e2b.app`
 *  - Public ports: `https://{port}-{sandboxId}.e2b.app` (live Playwright viewer on 3000)
 */
object E2BClient {

    private const val API = "https://api.e2b.dev"
    private const val DOMAIN = "e2b.app"
    /** envd default port inside every E2B sandbox. */
    private const val ENVD_PORT = 49983
    /**
     * Domains that serve the STABLE envd control host `https://sandbox.<domain>`.
     * On these, the edge proxy routes to the right sandbox via the `E2b-Sandbox-Id` /
     * `E2b-Sandbox-Port` request headers (this replaced the old per-sandbox
     * `{port}-{id}.<domain>` subdomain, which now 404s at the edge — the bug this fixes).
     */
    private val SUPPORTED_DOMAINS = setOf("e2b.app", "e2b.dev", "e2b.pro", "e2b-staging.dev")
    /** Default template: full Linux suitable for apt + Playwright. */
    const val DEFAULT_TEMPLATE = "base"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()

    private val active = AtomicReference<ActiveSandbox?>(null)
    private val sandboxMutex = Mutex()

    @Volatile private var lastApiKey: String = ""
    @Volatile private var lastEnvdToken: String = ""

    data class ActiveSandbox(
        val sandboxId: String,
        val clientID: String,
        val projectDir: String,
        val template: String,
        val envdAccessToken: String = "",
        /** Per-sandbox domain from the create response (nullable in API; blank = default). */
        val domain: String = "",
        val envdVersion: String = ""
    )

    data class ShellResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val isSuccess: Boolean
    ) {
        fun formatted(maxLen: Int = 5000): String = buildString {
            if (stdout.isNotBlank()) append(stdout.take(maxLen))
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("[stderr]\n").append(stderr.take(2000))
            }
            append("\n[exit $exitCode]")
            val total = stdout.length + stderr.length
            if (total > maxLen) append(" (truncated from $total chars)")
        }
    }

    fun lastUsedApiKey(): String = lastApiKey

    private fun rememberKey(apiKey: String) {
        if (apiKey.isNotBlank()) lastApiKey = apiKey
    }

    private fun sandboxDomain(sb: ActiveSandbox): String = sb.domain.ifBlank { DOMAIN }

    /**
     * Base URL for the envd control plane (exec + files). On supported domains this is the
     * STABLE host `https://sandbox.<domain>`; the edge proxy routes to this sandbox via the
     * `E2b-Sandbox-Id` / `E2b-Sandbox-Port` headers set by [envdAuth]. Falls back to the old
     * per-sandbox subdomain for any custom/self-hosted domain.
     */
    private fun envdApiBase(sb: ActiveSandbox): String {
        val d = sandboxDomain(sb)
        return if (d in SUPPORTED_DOMAINS) "https://sandbox.$d"
        else "https://$ENVD_PORT-${sb.sandboxId}.$d"
    }

    /** Attach the envd routing + auth headers required by the current E2B edge/envd. */
    private fun Request.Builder.envdAuth(sb: ActiveSandbox, apiKey: String): Request.Builder = apply {
        header("E2b-Sandbox-Id", sb.sandboxId)
        header("E2b-Sandbox-Port", ENVD_PORT.toString())
        val token = sb.envdAccessToken.ifBlank { lastEnvdToken }
        if (token.isNotBlank()) header("X-Access-Token", token)
        // Older sandboxes still accept the raw API key.
        if (apiKey.isNotBlank()) header("X-API-KEY", apiKey)
    }

    /**
     * Public host for any sandbox PORT the user's own service listens on (browser live view
     * uses 3000). These stay on the per-sandbox subdomain because a browser navigation can't
     * set the `E2b-Sandbox-*` routing headers. Uses the active sandbox's domain when known.
     */
    fun publicHost(sandboxId: String, port: Int): String {
        val d = active.get()?.takeIf { it.sandboxId == sandboxId }?.domain?.ifBlank { DOMAIN } ?: DOMAIN
        return "https://$port-$sandboxId.$d"
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    private suspend fun ensureSandbox(projectDir: String, apiKey: String, template: String): ActiveSandbox =
        sandboxMutex.withLock {
            rememberKey(apiKey)
            val tpl = template.ifBlank { DEFAULT_TEMPLATE }
            val current = active.get()
            if (current != null && current.projectDir == projectDir) {
                // Keep reusing the warm sandbox (browser warm depends on this).
                return@withLock current
            }
            current?.let { killQuiet(it.sandboxId, apiKey) }
            val created = createSandbox(apiKey, tpl)
            execOn(created, apiKey, sandboxInitScript(), 45)
            active.set(created.copy(projectDir = projectDir))
            active.get()!!
        }

    /**
     * One-shot sandbox bootstrap, run right after create:
     *  1. Create a writable `/workspace` (the non-root user can't mkdir at the FS root, so sudo).
     *  2. Add a 2 GB swapfile. The base template only has ~512 MB RAM, which is NOT enough to
     *     launch Chromium (it OOM-hangs) or run memory-heavy Python. Swap gives the kernel the
     *     headroom it needs — verified: with swap on, headless Chromium launches and loads pages;
     *     without it, launch hangs. All steps are best-effort (`|| true`) so a locked-down
     *     template still boots.
     */
    private fun sandboxInitScript(): String = buildString {
        append("sudo -n mkdir -p /workspace 2>/dev/null || mkdir -p /workspace 2>/dev/null; ")
        append("sudo -n chmod 777 /workspace 2>/dev/null; mkdir -p /home/user 2>/dev/null; ")
        append("if ! sudo -n swapon --show 2>/dev/null | grep -q /swapfile; then ")
        append("  (sudo -n fallocate -l 2G /swapfile 2>/dev/null || sudo -n dd if=/dev/zero of=/swapfile bs=1M count=2048 2>/dev/null) && ")
        append("  sudo -n chmod 600 /swapfile 2>/dev/null && sudo -n mkswap /swapfile 2>/dev/null && sudo -n swapon /swapfile 2>/dev/null; ")
        append("fi; true")
    }

    private suspend fun createSandbox(apiKey: String, template: String): ActiveSandbox =
        withContext(Dispatchers.IO) {
            val payload = JSONObject().apply {
                put("templateID", template.ifBlank { DEFAULT_TEMPLATE })
                put("timeout", 3600) // 1h lifetime — browser sessions need time
                put("metadata", JSONObject().put("app", "ahamai").put("role", "browser"))
            }
            val req = Request.Builder()
                .url("$API/sandboxes")
                .header("X-API-KEY", apiKey)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw RuntimeException(
                        "E2B create failed HTTP ${resp.code}: ${body.take(400)}. " +
                            "Use an E2B API key (starts with e2b_)."
                    )
                }
                val j = JSONObject(body)
                val id = j.optString("sandboxID")
                    .ifBlank { j.optString("sandboxId") }
                    .ifBlank { j.optString("id") }
                if (id.isBlank()) throw RuntimeException("E2B create: no sandboxID in ${body.take(200)}")
                val token = j.optString("envdAccessToken")
                    .ifBlank { j.optString("envd_access_token") }
                if (token.isNotBlank()) lastEnvdToken = token
                val clientId = j.optString("clientID").ifBlank { j.optString("clientId") }
                // Per-sandbox domain (nullable in the API) drives the stable envd host.
                val domain = j.optString("domain").ifBlank { j.optString("sandboxDomain") }
                val envdVersion = j.optString("envdVersion").ifBlank { j.optString("envd_version") }
                // Brief wait for envd to come up
                delay(800)
                ActiveSandbox(id, clientId, "", template, token, domain, envdVersion)
            }
        }

    suspend fun killAll(apiKey: String): Int = withContext(Dispatchers.IO) {
        rememberKey(apiKey)
        val cur = active.getAndSet(null)
        var n = 0
        if (cur != null) {
            killQuiet(cur.sandboxId, apiKey); n++
        }
        // Best-effort list+kill (API may 404 on older plans)
        try {
            val req = Request.Builder().url("$API/sandboxes")
                .header("X-API-KEY", apiKey).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext n
                val raw = resp.body?.string().orEmpty()
                val arr = when {
                    raw.trimStart().startsWith("[") -> JSONArray(raw)
                    else -> JSONObject(raw).optJSONArray("sandboxes")
                        ?: JSONObject(raw).optJSONArray("items")
                        ?: JSONArray()
                }
                for (i in 0 until arr.length()) {
                    val id = arr.optJSONObject(i)?.optString("sandboxID")
                        ?.ifBlank { arr.optJSONObject(i)?.optString("sandboxId") }
                        ?.ifBlank { arr.optJSONObject(i)?.optString("id") }
                        .orEmpty()
                    if (id.isNotBlank()) {
                        killQuiet(id, apiKey); n++
                    }
                }
            }
        } catch (_: Exception) {}
        n
    }

    private suspend fun killQuiet(sandboxId: String, apiKey: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$API/sandboxes/$sandboxId")
                .header("X-API-KEY", apiKey)
                .delete()
                .build()
            client.newCall(req).execute().close()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    suspend fun shutdown(apiKey: String) {
        rememberKey(apiKey)
        val cur = active.getAndSet(null) ?: return
        killQuiet(cur.sandboxId, apiKey)
    }

    fun activeSandboxId(): String? = active.get()?.sandboxId

    // ── Exec ──────────────────────────────────────────────────────────────────

    suspend fun exec(
        projectDir: String, apiKey: String, template: String, command: String,
        cwd: String = "/workspace", timeoutSec: Int = 60
    ): ShellResult = withContext(Dispatchers.IO) {
        val sb = ensureSandbox(projectDir, apiKey, template)
        val workdir = cwd.ifBlank { "/workspace" }
        val full = "mkdir -p '$workdir' 2>/dev/null; cd '$workdir' 2>/dev/null || cd /home/user 2>/dev/null; $command"
        execOn(sb, apiKey, full, timeoutSec)
    }

    suspend fun execResilient(
        projectDir: String, apiKey: String, template: String, command: String,
        cwd: String = "/workspace", timeoutSec: Int = 300, onHeartbeat: (String) -> Unit = {}
    ): ShellResult = withContext(Dispatchers.IO) {
        onHeartbeat("cloud shell…")
        val sb = ensureSandbox(projectDir, apiKey, template)
        val workdir = cwd.ifBlank { "/workspace" }
        val full = "mkdir -p '$workdir' 2>/dev/null; cd '$workdir' 2>/dev/null || true; $command"
        // Heartbeat loop for long installs
        val start = System.currentTimeMillis()
        val result = execOn(sb, apiKey, full, timeoutSec)
        onHeartbeat("done (${(System.currentTimeMillis() - start) / 1000}s)")
        result
    }

    private fun execOn(sb: ActiveSandbox, apiKey: String, command: String, timeoutSec: Int): ShellResult {
        rememberKey(apiKey)
        if (sb.envdAccessToken.isNotBlank()) lastEnvdToken = sb.envdAccessToken

        // Current envd: Connect-RPC Process/Start on the stable host (header-routed).
        val connect = runCatching { execConnectProcess(sb, apiKey, command, timeoutSec) }
            .getOrElse { ShellResult("", "envd Process/Start error: ${it.message?.take(200)}", -1, false) }
        if (connect.isSuccess || connect.exitCode != -1 || connect.stdout.isNotBlank()) return connect

        // Fallback: legacy POST /command (very old sandboxes only).
        val legacy = runCatching { execLegacyCommand(sb, apiKey, command, timeoutSec) }.getOrNull()
        if (legacy != null && (legacy.isSuccess || legacy.exitCode != -1 || legacy.stdout.isNotBlank())) return legacy

        // Prefer the primary (Connect) error — it's the meaningful one.
        return connect
    }

    /**
     * Frame a single message as a Connect streaming envelope: `[flags:1][len:4 BE][payload]`.
     * `Process/Start` is a SERVER-STREAMING RPC, so it must be called with the streaming
     * content-type `application/connect+json` and an enveloped body — NOT plain unary
     * `application/json` (which connect-go rejects, forcing the old code onto the dead
     * `/command` path and the bogus "404 page not found").
     */
    private fun connectEnvelope(json: String): okhttp3.RequestBody {
        val payload = json.toByteArray(Charsets.UTF_8)
        val framed = ByteArray(5 + payload.size)
        framed[0] = 0 // flags: uncompressed data message
        framed[1] = ((payload.size ushr 24) and 0xFF).toByte()
        framed[2] = ((payload.size ushr 16) and 0xFF).toByte()
        framed[3] = ((payload.size ushr 8) and 0xFF).toByte()
        framed[4] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, framed, 5, payload.size)
        return framed.toRequestBody("application/connect+json".toMediaType())
    }

    /**
     * envd Connect-RPC Process/Start (server-streaming, used by official SDKs). POSTs an
     * enveloped Connect message to the stable envd host and parses the streamed events.
     */
    private fun execConnectProcess(
        sb: ActiveSandbox,
        apiKey: String,
        command: String,
        timeoutSec: Int
    ): ShellResult {
        val url = "${envdApiBase(sb)}/process.Process/Start"
        val body = JSONObject().apply {
            put("process", JSONObject().apply {
                // Match the official SDK: login shell so PATH/profile is set up.
                put("cmd", "/bin/bash")
                put("args", JSONArray().put("-l").put("-c").put(command))
                // envd rejects a non-existent cwd, and /workspace may not exist yet (and the
                // non-root user can't create it at init time). Anchor at the always-present home;
                // the wrapped command does its own `cd` into the real workdir.
                put("cwd", "/home/user")
            })
        }
        val callClient = client.newBuilder()
            .readTimeout((timeoutSec + 45).toLong(), TimeUnit.SECONDS)
            .callTimeout((timeoutSec + 60).toLong(), TimeUnit.SECONDS)
            .build()
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Connect-Protocol-Version", "1")
            .envdAuth(sb, apiKey)
            .post(connectEnvelope(body.toString()))

        return callClient.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return ShellResult("", "envd Process/Start HTTP ${resp.code} @ $url: ${raw.take(300)}", -1, false)
            }
            parseProcessOutput(raw)
        }
    }

    /** Legacy envd-style POST /command (kept only as a fallback for very old sandboxes). */
    private fun execLegacyCommand(
        sb: ActiveSandbox,
        apiKey: String,
        command: String,
        timeoutSec: Int
    ): ShellResult {
        val url = "${envdApiBase(sb)}/command"
        val body = JSONObject().apply {
            put("command", command)
            put("timeout", timeoutSec)
            put("cwd", "/home/user")
        }
        val callClient = client.newBuilder()
            .readTimeout((timeoutSec + 45).toLong(), TimeUnit.SECONDS)
            .build()
        val reqBuilder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .envdAuth(sb, apiKey)
            .post(body.toString().toRequestBody("application/json".toMediaType()))

        return callClient.newCall(reqBuilder.build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                return ShellResult("", "envd /command HTTP ${resp.code}: ${raw.take(300)}", -1, false)
            }
            parseProcessOutput(raw)
        }
    }

    /**
     * Pull every top-level JSON object out of [raw], tolerating Connect's binary length-prefix
     * envelopes (we just scan for balanced, string-aware `{…}` blocks and ignore the framing
     * bytes between them). Works for both a unary reply and a server-streamed sequence.
     */
    private fun extractJsonObjects(raw: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        val n = raw.length
        while (i < n) {
            if (raw[i] == '{') {
                var depth = 0
                var j = i
                var inStr = false
                var esc = false
                var done = false
                while (j < n) {
                    val c = raw[j]
                    if (inStr) {
                        when {
                            esc -> esc = false
                            c == '\\' -> esc = true
                            c == '"' -> inStr = false
                        }
                    } else {
                        when (c) {
                            '"' -> inStr = true
                            '{' -> depth++
                            '}' -> {
                                depth--
                                if (depth == 0) { out.add(raw.substring(i, j + 1)); i = j; done = true }
                            }
                        }
                    }
                    if (done) break
                    j++
                }
            }
            i++
        }
        return out
    }

    /** envd sends process stdout/stderr as `bytes` → base64 in JSON. Decode, else pass through. */
    private fun decodeMaybeB64(s: String): String {
        if (s.isEmpty()) return ""
        return try {
            String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)
        } catch (_: Exception) {
            s
        }
    }

    private fun parseProcessOutput(raw: String): ShellResult {
        // 1) Simple unary JSON {stdout, stderr, exitCode}
        runCatching {
            val j = JSONObject(raw.trim())
            if (j.has("stdout") || j.has("stderr") || j.has("exitCode") ||
                j.has("exit_code") || j.has("result") || j.has("output")
            ) {
                val stdout = j.optString("stdout").ifBlank { j.optString("result") }.ifBlank { j.optString("output") }
                val stderr = j.optString("stderr")
                val code = when {
                    j.has("exitCode") -> j.optInt("exitCode")
                    j.has("exit_code") -> j.optInt("exit_code")
                    j.has("code") -> j.optInt("code")
                    else -> 0
                }
                return ShellResult(stdout, stderr, code, code == 0)
            }
        }

        // 2) Connect server-stream: ProcessEvent objects — data.stdout/stderr (base64) + end.exitCode
        val out = StringBuilder()
        val err = StringBuilder()
        var code = 0
        var sawEnd = false
        var sawAny = false
        var connectError: String? = null
        for (obj in extractJsonObjects(raw)) {
            val j = runCatching { JSONObject(obj) }.getOrNull() ?: continue
            // Connect end-of-stream error frame: {"error":{"code":..,"message":..}}
            j.optJSONObject("error")?.let { e ->
                val msg = e.optString("message").ifBlank { e.optString("code") }
                if (msg.isNotBlank()) connectError = msg
            }
            val ev = j.optJSONObject("event")
                ?: j.optJSONObject("result")?.optJSONObject("event")
                ?: j
            // envd shape: { event: { data: { stdout|stderr: <base64> } } }
            ev.optJSONObject("data")?.let { d ->
                decodeMaybeB64(d.optString("stdout")).let { if (it.isNotEmpty()) { out.append(it); sawAny = true } }
                decodeMaybeB64(d.optString("stderr")).let { if (it.isNotEmpty()) { err.append(it); sawAny = true } }
            }
            ev.optJSONObject("end")?.let { e ->
                code = e.optInt("exitCode", e.optInt("exit_code", code)); sawEnd = true; sawAny = true
            }
            // Flat / alternate shapes
            if (ev.optJSONObject("data") == null && ev.has("stdout")) { out.append(ev.optString("stdout")); sawAny = true }
            if (ev.optJSONObject("data") == null && ev.has("stderr")) { err.append(ev.optString("stderr")); sawAny = true }
            if (ev.has("exitCode") || ev.has("exit_code")) {
                code = ev.optInt("exitCode", ev.optInt("exit_code", code)); sawEnd = true; sawAny = true
            }
            ev.optJSONObject("stdout")?.optString("content")?.let { if (it.isNotEmpty()) { out.append(it); sawAny = true } }
            ev.optJSONObject("stderr")?.optString("content")?.let { if (it.isNotEmpty()) { err.append(it); sawAny = true } }
        }
        if (connectError != null && !sawAny) {
            return ShellResult("", "envd error: $connectError", -1, false)
        }
        if (sawAny) {
            val stderrOut = buildString {
                append(err)
                if (connectError != null && code != 0) {
                    if (isNotEmpty()) append("\n")
                    append(connectError)
                }
            }
            val success = if (sawEnd) code == 0 else err.isEmpty() && connectError == null
            return ShellResult(out.toString(), stderrOut, code, success)
        }
        // 3) Unknown gateway body — hand back as stdout.
        return ShellResult(raw.take(8000), "", 0, true)
    }

    // ── Files ─────────────────────────────────────────────────────────────────

    suspend fun uploadFile(
        projectDir: String, apiKey: String, template: String,
        localFile: File, remotePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        val sb = ensureSandbox(projectDir, apiKey, template)
        uploadTo(sb, apiKey, localFile, remotePath)
    }

    private fun uploadTo(sb: ActiveSandbox, apiKey: String, localFile: File, remotePath: String): Boolean {
        if (!localFile.exists()) return false
        val path = if (remotePath.startsWith("/")) remotePath else "/workspace/$remotePath"
        val parent = path.substringBeforeLast('/', "")
        if (parent.isNotBlank()) {
            execOn(sb, apiKey, "mkdir -p '$parent'", 20)
        }
        // Multipart to envd files endpoint (stable host, header-routed)
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "file", localFile.name,
                localFile.asRequestBody("application/octet-stream".toMediaType())
            )
            .addFormDataPart("path", path)
            .build()
        val url = "${envdApiBase(sb)}/files?path=${URLEncoder.encode(path, "UTF-8")}"
        val reqBuilder = Request.Builder().url(url).post(body).envdAuth(sb, apiKey)
        return try {
            client.newCall(reqBuilder.build()).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            // Fallback: base64 pipe via shell (small files)
            if (localFile.length() > 1_500_000) return false
            val b64 = android.util.Base64.encodeToString(localFile.readBytes(), android.util.Base64.NO_WRAP)
            val r = execOn(
                sb, apiKey,
                "echo '$b64' | base64 -d > '$path' && echo UP_OK",
                60
            )
            r.stdout.contains("UP_OK")
        }
    }

    suspend fun downloadFile(apiKey: String, remotePath: String, localFile: File): Boolean =
        withContext(Dispatchers.IO) {
            val sb = active.get() ?: return@withContext false
            rememberKey(apiKey)
            val path = if (remotePath.startsWith("/")) remotePath else "/workspace/$remotePath"
            val url = "${envdApiBase(sb)}/files?path=${URLEncoder.encode(path, "UTF-8")}"
            val reqBuilder = Request.Builder().url(url).get().envdAuth(sb, apiKey)
            try {
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    val bytes = resp.body?.bytes() ?: return@withContext false
                    localFile.parentFile?.mkdirs()
                    localFile.writeBytes(bytes)
                    true
                }
            } catch (_: Exception) {
                // Fallback base64
                val r = execOn(sb, apiKey, "base64 -w0 '$path' 2>/dev/null || base64 '$path' 2>/dev/null", 60)
                if (r.stdout.isBlank()) return@withContext false
                return@withContext try {
                    val bytes = android.util.Base64.decode(r.stdout.trim(), android.util.Base64.DEFAULT)
                    localFile.parentFile?.mkdirs()
                    localFile.writeBytes(bytes)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }

    /** Incremental project sync. */
    suspend fun syncProjectToSandbox(
        projectDir: String, apiKey: String, template: String, rootDir: File
    ): Int = withContext(Dispatchers.IO) {
        rememberKey(apiKey)
        val summary = FastSync.syncProjectAtOnce(projectDir, apiKey, template, rootDir)
        Regex("(\\d+) uploaded").find(summary)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    /**
     * Public URL for [port] on the active sandbox (Playwright live viewer).
     * E2B format: `https://{port}-{sandboxId}.e2b.app`
     */
    suspend fun sandboxPortUrl(port: Int): String? {
        val id = active.get()?.sandboxId ?: return null
        return publicHost(id, port)
    }

    suspend fun sandboxPortUrl(apiKey: String, port: Int): String? {
        if (apiKey.isNotBlank()) lastApiKey = apiKey
        return sandboxPortUrl(port)
    }

    fun shouldSkipForSync(relPath: String): Boolean {
        val p = relPath.lowercase()
        return p.contains("/.git/") || p.startsWith(".git/") ||
            p.contains("/node_modules/") || p.contains("/build/") ||
            p.contains("/.gradle/") || p.contains("/.cxx/") ||
            p.endsWith(".apk") || p.endsWith(".aab") || p.endsWith(".dex") ||
            p.endsWith(".so") || p.contains("/.browser/profile")
    }

    // ── Warm helpers (browser) ────────────────────────────────────────────────

    /** Force-create sandbox now so first browser open is fast. */
    suspend fun warmSandbox(projectDir: String, apiKey: String, template: String = DEFAULT_TEMPLATE): String =
        withContext(Dispatchers.IO) {
            try {
                val sb = ensureSandbox(projectDir, apiKey, template)
                "E2B sandbox warm: ${sb.sandboxId}"
            } catch (e: Exception) {
                "E2B warm failed: ${e.message?.take(200)}"
            }
        }

    fun isE2bKey(key: String): Boolean =
        key.trim().startsWith("e2b_", ignoreCase = true)
}
