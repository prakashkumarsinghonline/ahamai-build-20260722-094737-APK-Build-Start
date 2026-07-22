package com.ahamai.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * **DEPRECATED** — cloud engine is back on real [E2BClient] (Playwright browser + shell).
 * This file is kept only for reference / ComputerUse desktop experiments that still hit
 * Daytona's signed-preview URLs. Do **not** wire new agent browser code here.
 *
 * Agent browser path: [CloudBrowser] → [E2BClient] + Playwright daemon on port 3000.
 */
@Deprecated("Use E2BClient + CloudBrowser for agent browser; Daytona is no longer the cloud engine")
object DaytonaClient {

    private const val BASE = "https://app.daytona.io/api"

    /** Default snapshot for lightweight sandboxes (browser/shorts/shell). 1 vCPU / 1 GB. */
    const val DEFAULT_SNAPSHOT = "daytonaio/sandbox:0.6.0"

    /**
     * Default sandbox is built from this image (NOT the stock snapshot) because:
     *  - it runs as ROOT (the stock daytona snapshot runs as unprivileged `daytona`, but all of
     *    CloudTools/CyberTools assume root + apt with no sudo), and
     *  - buildInfo lets us request real resources (snapshots forbid that) so cloud work has enough
     *    RAM, and
     *  - the base tools every task needs are baked in, so first-run provisioning is fast.
     * Daytona caches the built image by content, so repeated creates are quick.
     */
    private const val DEFAULT_DOCKERFILE =
        "FROM ubuntu:24.04\n" +
        "ENV DEBIAN_FRONTEND=noninteractive\n" +
        "RUN apt-get update -qq && apt-get install -y --no-install-recommends " +
        "ca-certificates curl wget git unzip zip jq xz-utils python3 python3-pip python3-venv " +
        "build-essential procps && rm -rf /var/lib/apt/lists/* && update-ca-certificates || true\n" +
        "WORKDIR /workspace\n"

    private const val DEFAULT_CPU = 2
    private const val DEFAULT_MEM_GB = 4
    private const val DEFAULT_DISK_GB = 10

    /** Heavier sandboxes (e.g. build verification) are created from an image with explicit
     *  resources — Daytona forbids setting resources together with a snapshot. */
    private const val BUILD_DOCKERFILE = "FROM ubuntu:24.04\n"

    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val active = AtomicReference<ActiveSandbox?>(null)
    private val sandboxMutex = Mutex()

    /** Last API key used for any sandbox call — used by preview-URL helpers that don't get a key. */
    @Volatile private var lastApiKey: String = ""

    /** Remember the key used for cloud calls so [sandboxPortUrl] never fires with a blank Bearer. */
    private fun rememberKey(apiKey: String) {
        if (apiKey.isNotBlank()) lastApiKey = apiKey
    }

    fun lastUsedApiKey(): String = lastApiKey

    data class ActiveSandbox(
        val sandboxId: String,
        val clientID: String,   // kept for API-compat with old callers (unused by Daytona)
        val projectDir: String,
        val template: String
    )

    data class ShellResult(
        val stdout: String,
        val stderr: String,
        val exitCode: Int,
        val isSuccess: Boolean
    ) {
        fun formatted(maxLen: Int = 5000): String = buildString {
            if (stdout.isNotBlank()) append(stdout)
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append("[stderr]\n").append(stderr)
            }
            append("\n[exit $exitCode]")
            val total = stdout.length + stderr.length
            if (total > maxLen) append(" (truncated from $total chars)")
        }
    }

    private fun bearer(key: String) = "Bearer $key"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    // ───────────────────────────── sandbox lifecycle ─────────────────────────────

    private suspend fun ensureSandbox(projectDir: String, apiKey: String, template: String): ActiveSandbox =
        sandboxMutex.withLock {
            rememberKey(apiKey)
            val current = active.get()
            if (current != null && current.projectDir == projectDir && current.template == template) {
                return@withLock current
            }
            current?.let { killQuiet(it.sandboxId, apiKey) }
            val id = createSandbox(apiKey, template)
            val next = ActiveSandbox(id, "", projectDir, template)
            active.set(next)
            next
        }

    private class ConcurrencyLimitException(msg: String) : RuntimeException(msg)

    private suspend fun createSandbox(apiKey: String, template: String): String = withContext(Dispatchers.IO) {
        try {
            createSandboxOnce(apiKey, template)
        } catch (e: ConcurrencyLimitException) {
            killAll(apiKey)
            createSandboxOnce(apiKey, template)
        }
    }

    /** Create a sandbox from the default ROOT Ubuntu image (with base tools + resources). The
     *  [template] param is accepted for API-compat; a custom image ref (containing '/') is used
     *  as the base, otherwise the default Ubuntu image is built. */
    private suspend fun createSandboxOnce(apiKey: String, template: String): String = withContext(Dispatchers.IO) {
        val dockerfile = if (template.contains("/") && !template.startsWith("daytonaio/"))
            "FROM $template\nWORKDIR /workspace\n" else DEFAULT_DOCKERFILE
        val payload = JSONObject().apply {
            put("buildInfo", JSONObject().put("dockerfileContent", dockerfile))
            put("cpu", DEFAULT_CPU); put("memory", DEFAULT_MEM_GB); put("disk", DEFAULT_DISK_GB)
            put("autoStopInterval", 30)      // auto-stop after 30 min idle (safety net)
            put("autoDeleteInterval", 60)    // auto-delete an hour after stop
        }
        val (id, _) = postCreate(apiKey, payload)
        waitUntilStarted(apiKey, id, maxSeconds = 180)   // image build can take a bit on first use
        id
    }

    /**
     * Create a heavier sandbox from a base image with explicit resources (for build verification).
     * Returns the sandbox id. Not part of the reuse pool — caller manages its lifecycle.
     */
    suspend fun createBuildSandbox(apiKey: String, cpu: Int = 4, memoryGb: Int = 8, diskGb: Int = 10,
                                   dockerfile: String = BUILD_DOCKERFILE): String = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("buildInfo", JSONObject().put("dockerfileContent", dockerfile))
            put("cpu", cpu); put("memory", memoryGb); put("disk", diskGb)
            put("autoStopInterval", 30); put("autoDeleteInterval", 60)
        }
        val (id, _) = postCreate(apiKey, payload)
        waitUntilStarted(apiKey, id, maxSeconds = 180)
        id
    }

    private fun postCreate(apiKey: String, payload: JSONObject): Pair<String, String> {
        val req = Request.Builder().url("$BASE/sandbox")
            .header("Authorization", bearer(apiKey))
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                if (resp.code == 429) throw ConcurrencyLimitException("daytona create 429 — ${body.take(300)}")
                throw RuntimeException("daytona create failed: HTTP ${resp.code} — ${body.take(300)}")
            }
            val json = JSONObject(body)
            val id = json.optString("id")
            if (id.isBlank()) throw RuntimeException("daytona create: no sandbox id in response")
            return id to json.optString("state")
        }
    }

    private suspend fun waitUntilStarted(apiKey: String, id: String, maxSeconds: Int = 90) {
        for (i in 0 until maxSeconds) {
            val st = getState(apiKey, id)
            if (st == "started" || st == "running") return
            if (st == "error" || st == "build_failed") throw RuntimeException("sandbox $id entered state=$st")
            delay(1000)
        }
        // don't hard-fail; the first exec will surface any real problem
    }

    private fun getState(apiKey: String, id: String): String = try {
        val req = Request.Builder().url("$BASE/sandbox/$id")
            .header("Authorization", bearer(apiKey)).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return "" else JSONObject(resp.body?.string().orEmpty()).optString("state")
        }
    } catch (_: Exception) { "" }

    private suspend fun listRunningIds(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$BASE/sandbox")
                .header("Authorization", bearer(apiKey)).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val root = JSONObject(resp.body?.string().orEmpty())
                val arr = root.optJSONArray("items") ?: JSONArray()
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { s -> s.isNotBlank() } }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
    }

    suspend fun killAll(apiKey: String): Int = withContext(Dispatchers.IO) {
        active.set(null)
        val ids = listRunningIds(apiKey)
        if (ids.isEmpty()) return@withContext 0
        coroutineScope { ids.map { id -> async { killQuiet(id, apiKey) } }.awaitAll() }
        ids.size
    }

    private suspend fun killQuiet(sandboxId: String, apiKey: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$BASE/sandbox/$sandboxId")
                .header("Authorization", bearer(apiKey)).delete().build()
            client.newCall(req).execute().close()
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    suspend fun shutdown(apiKey: String) {
        val cur = active.getAndSet(null) ?: return
        killQuiet(cur.sandboxId, apiKey)
    }

    fun activeSandboxId(): String? = active.get()?.sandboxId

    /**
     * Public preview URL for [port] on the active sandbox (used for the live browser view).
     *
     * Prefer the **signed** preview URL (token embedded in the hostname) so a plain Android
     * WebView can load it with no extra headers. A non-signed preview URL redirects to Auth0
     * login and looks like "browser never opens" inside the app.
     *
     * Falls back to the regular preview-url + token query/cookie form if signed fails.
     * [apiKey] may be blank — we reuse the last key from create/exec.
     */
    suspend fun sandboxPortUrl(apiKey: String, port: Int): String? = withContext(Dispatchers.IO) {
        val id = active.get()?.sandboxId ?: return@withContext null
        val key = apiKey.ifBlank { lastApiKey }
        if (key.isBlank()) {
            android.util.Log.w("DaytonaClient", "sandboxPortUrl: no API key for port $port")
            return@withContext null
        }
        rememberKey(key)
        // 1) Signed preview — hostname embeds the token (works in WebView with no headers).
        try {
            val req = Request.Builder()
                .url("$BASE/sandbox/$id/ports/$port/signed-preview-url?expiresInSeconds=86400")
                .header("Authorization", bearer(key)).get().build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.isSuccessful) {
                    val url = JSONObject(body).optString("url").trim().trimEnd('/')
                    if (url.isNotBlank()) return@withContext url
                } else {
                    android.util.Log.w("DaytonaClient", "signed-preview-url HTTP ${resp.code}: ${body.take(200)}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DaytonaClient", "signed-preview-url failed: ${e.message}")
        }
        // 2) Fallback: regular preview-url. Append token as query so some proxies accept it;
        // WebView may still hit Auth0 for non-public sandboxes — signed is strongly preferred.
        try {
            val req = Request.Builder()
                .url("$BASE/sandbox/$id/ports/$port/preview-url")
                .header("Authorization", bearer(key)).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val j = JSONObject(resp.body?.string().orEmpty())
                val url = j.optString("url").trim().trimEnd('/')
                val token = j.optString("token").trim()
                if (url.isBlank()) return@withContext null
                return@withContext if (token.isNotBlank() && !url.contains(token)) {
                    // Some Daytona edge builds accept ?DAYTONA_SANDBOX_AUTH_KEY= / X-Daytona-Preview-Token
                    // via query on first load (sets cookie). Harmless if ignored.
                    "$url?DAYTONA_SANDBOX_AUTH_KEY=${enc(token)}"
                } else url
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("DaytonaClient", "preview-url failed: ${e.message}")
            null
        }
    }

    /** Preview URL for an arbitrary sandbox id (Computer Use desktop sandbox is not in [active]). */
    suspend fun previewUrlFor(apiKey: String, sandboxId: String, port: Int): String? = withContext(Dispatchers.IO) {
        val key = apiKey.ifBlank { lastApiKey }
        if (key.isBlank() || sandboxId.isBlank()) return@withContext null
        rememberKey(key)
        try {
            val req = Request.Builder()
                .url("$BASE/sandbox/$sandboxId/ports/$port/signed-preview-url?expiresInSeconds=86400")
                .header("Authorization", bearer(key)).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty()).optString("url").trim().trimEnd('/')
                    .takeIf { it.isNotBlank() }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    // ───────────────────────────── command execution ─────────────────────────────

    /**
     * Run [command] in the sandbox. Short jobs use the synchronous execute endpoint; long ones
     * (timeoutSec > SYNC_MAX) use a background session so we never hit HTTP read-timeouts.
     */
    suspend fun exec(projectDir: String, apiKey: String, template: String, command: String,
                     cwd: String = "/workspace", timeoutSec: Int = 60): ShellResult = withContext(Dispatchers.IO) {
        val sb = ensureSandbox(projectDir, apiKey, template)
        val workdir = cwd.ifBlank { "/workspace" }
        val full = "mkdir -p '${workdir}' 2>/dev/null; cd '${workdir}' 2>/dev/null; $command"
        if (timeoutSec <= SYNC_MAX) execSync(apiKey, sb.sandboxId, full, timeoutSec)
        else execSession(apiKey, sb.sandboxId, full, timeoutSec)
    }

    /** Resilient variant with a heartbeat callback (long installs/builds). Uses a session. */
    suspend fun execResilient(projectDir: String, apiKey: String, template: String, command: String,
                              cwd: String = "/workspace", timeoutSec: Int = 300,
                              onHeartbeat: (String) -> Unit = {}): ShellResult = withContext(Dispatchers.IO) {
        val sb = ensureSandbox(projectDir, apiKey, template)
        val workdir = cwd.ifBlank { "/workspace" }
        val full = "mkdir -p '${workdir}' 2>/dev/null; cd '${workdir}' 2>/dev/null; $command"
        execSession(apiKey, sb.sandboxId, full, timeoutSec, onHeartbeat)
    }

    private const val SYNC_MAX = 150

    private fun execSync(apiKey: String, id: String, command: String, timeoutSec: Int): ShellResult {
        val body = JSONObject().apply { put("command", command); put("timeout", timeoutSec) }
        val callClient = client.newBuilder()
            .readTimeout((timeoutSec + 30).toLong(), TimeUnit.SECONDS)
            .callTimeout((timeoutSec + 45).toLong(), TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url("$BASE/toolbox/$id/toolbox/process/execute")
            .header("Authorization", bearer(apiKey))
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            callClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return ShellResult("", "HTTP ${resp.code}: ${raw.take(300)}", -1, false)
                val j = JSONObject(raw)
                val out = j.optString("result", j.optString("output", ""))
                val code = j.optInt("exitCode", 0)
                ShellResult(out, "", code, code == 0)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ShellResult("", "exec error: ${e.message}", -1, false) }
    }

    /** Long-running command via a background session, polled until it exits. */
    private suspend fun execSession(apiKey: String, id: String, command: String, timeoutSec: Int,
                                    onHeartbeat: (String) -> Unit = {}): ShellResult = withContext(Dispatchers.IO) {
        val sess = "aham" + System.currentTimeMillis()
        val tb = "$BASE/toolbox/$id/toolbox/process/session"
        fun auth(b: Request.Builder) = b.header("Authorization", bearer(apiKey)).header("Content-Type", "application/json")
        try {
            // create session
            auth(Request.Builder().url(tb).post(JSONObject().put("sessionId", sess).toString()
                .toRequestBody("application/json".toMediaType()))).build().let { client.newCall(it).execute().close() }
            // start async command
            val startReq = auth(Request.Builder().url("$tb/$sess/exec")
                .post(JSONObject().apply { put("command", command); put("runAsync", true) }.toString()
                    .toRequestBody("application/json".toMediaType()))).build()
            val cmdId = client.newCall(startReq).execute().use { r ->
                val b = r.body?.string().orEmpty()
                if (r.code !in intArrayOf(200, 201, 202)) return@withContext ShellResult("", "session start HTTP ${r.code}: ${b.take(200)}", -1, false)
                JSONObject(b).let { it.optString("cmdId").ifBlank { it.optString("id") } }
            }
            // poll
            val deadline = System.currentTimeMillis() + timeoutSec * 1000L
            var lastLogLen = 0
            while (System.currentTimeMillis() < deadline) {
                delay(2500)
                val stReq = auth(Request.Builder().url("$tb/$sess/command/$cmdId").get()).build()
                val exit = client.newCall(stReq).execute().use { r ->
                    if (!r.isSuccessful) return@use Int.MIN_VALUE
                    val j = JSONObject(r.body?.string().orEmpty())
                    if (j.isNull("exitCode")) Int.MIN_VALUE else j.optInt("exitCode")
                }
                // Heartbeat: surface new log output as it appears (default callback is a no-op).
                val log0 = fetchSessionLogs(apiKey, tb, sess, cmdId)
                if (log0.length > lastLogLen) { onHeartbeat(log0.substring(lastLogLen).take(400)); lastLogLen = log0.length }
                if (exit != Int.MIN_VALUE) {                    val log = fetchSessionLogs(apiKey, tb, sess, cmdId)
                    killSession(apiKey, tb, sess)
                    return@withContext ShellResult(log, "", exit, exit == 0)
                }
            }
            killSession(apiKey, tb, sess)
            ShellResult(fetchSessionLogs(apiKey, tb, sess, cmdId), "timed out after ${timeoutSec}s", -1, false)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ShellResult("", "session exec error: ${e.message}", -1, false) }
    }

    private fun fetchSessionLogs(apiKey: String, tb: String, sess: String, cmdId: String): String = try {
        val req = Request.Builder().url("$tb/$sess/command/$cmdId/logs")
            .header("Authorization", bearer(apiKey)).get().build()
        client.newCall(req).execute().use { it.body?.string().orEmpty() }
    } catch (_: Exception) { "" }

    private fun killSession(apiKey: String, tb: String, sess: String) {
        try {
            val req = Request.Builder().url("$tb/$sess").header("Authorization", bearer(apiKey)).delete().build()
            client.newCall(req).execute().close()
        } catch (_: Exception) {}
    }

    // ───────────────────────────── file transfer ─────────────────────────────

    /** Upload a local [file] to [remotePath] in the active sandbox. */
    suspend fun uploadFile(projectDir: String, apiKey: String, template: String, file: File, remotePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val sb = ensureSandbox(projectDir, apiKey, template)
            uploadTo(apiKey, sb.sandboxId, file, remotePath)
        }

    private fun uploadTo(apiKey: String, id: String, file: File, remotePath: String): Boolean {
        return try {
            val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("application/octet-stream".toMediaType()))
                .build()
            val budgetSec = (30 + file.length() / 1_000_000L).coerceIn(60, 600)
            val up = client.newBuilder()
                .writeTimeout(budgetSec, TimeUnit.SECONDS).callTimeout(budgetSec + 20, TimeUnit.SECONDS).build()
            val req = Request.Builder()
                .url("$BASE/toolbox/$id/toolbox/files/upload?path=${enc(remotePath)}")
                .header("Authorization", bearer(apiKey)).post(multipart).build()
            up.newCall(req).execute().use { it.isSuccessful }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
    }

    /** Download [remotePath] from the active sandbox to [dest]. Returns true on success. */
    suspend fun downloadFile(projectDir: String, apiKey: String, template: String, remotePath: String, dest: File): Boolean =
        withContext(Dispatchers.IO) {
            val id = active.get()?.sandboxId ?: return@withContext false
            try {
                val req = Request.Builder()
                    .url("$BASE/toolbox/$id/toolbox/files/download?path=${enc(remotePath)}")
                    .header("Authorization", bearer(apiKey)).get().build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext false
                    dest.parentFile?.mkdirs()
                    resp.body?.byteStream()?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                    true
                }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { false }
        }

    /** Files that shouldn't be synced to the sandbox (mirrors old E2B behavior). */
    fun shouldSkipForSync(relPath: String): Boolean {
        val l = relPath.lowercase()
        return l.endsWith(".apk") || l.endsWith(".aab") || l.endsWith(".zip") ||
            l.contains("/build/") || l.contains("/.gradle/") || l.contains("/node_modules/")
    }
}
