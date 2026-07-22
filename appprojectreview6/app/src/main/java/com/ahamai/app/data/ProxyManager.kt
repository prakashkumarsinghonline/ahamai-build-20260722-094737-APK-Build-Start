package com.ahamai.app.data

import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Outbound HTTP/SOCKS proxy pools for AI requests (9Router-style proxy pools).
 *
 * Built-in presets + custom entries. Applied to the shared LLM OkHttp clients so
 * region-restricted providers can be reached via a relay or residential proxy.
 *
 * Config lives under `"proxy"` in the admin providers JSON.
 */
object ProxyManager {

    enum class ProxyType {
        NONE, HTTP, SOCKS;

        companion object {
            fun parse(s: String?): ProxyType = when (s?.lowercase()?.trim()) {
                "http", "https" -> HTTP
                "socks", "socks5", "socks4" -> SOCKS
                else -> NONE
            }
        }

        fun wire(): String = when (this) {
            NONE -> "none"
            HTTP -> "http"
            SOCKS -> "socks5"
        }
    }

    data class ProxyEntry(
        val id: String,
        val name: String,
        val type: ProxyType,
        val host: String,
        val port: Int,
        val username: String = "",
        val password: String = "",
        val enabled: Boolean = true,
        /** Built-in curated preset (not user-deletable by default). */
        val builtin: Boolean = false
    )

    data class Config(
        val enabled: Boolean = false,
        /** Active entry id, or blank for direct. */
        val activeId: String = "",
        /** Rotate through all enabled entries on failure. */
        val rotateOnFail: Boolean = true,
        val entries: List<ProxyEntry> = emptyList()
    )

    /** Built-in slots: direct + templates. Free live proxies are loaded via [fetchPublicProxies]. */
    fun builtinPresets(): List<ProxyEntry> = listOf(
        ProxyEntry(
            id = "direct",
            name = "Direct",
            type = ProxyType.NONE,
            host = "",
            port = 0,
            builtin = true
        ),
        ProxyEntry(
            id = "custom_http",
            name = "Custom HTTP",
            type = ProxyType.HTTP,
            host = "",
            port = 8080,
            builtin = true
        ),
        ProxyEntry(
            id = "custom_socks5",
            name = "Custom SOCKS5",
            type = ProxyType.SOCKS,
            host = "",
            port = 1080,
            builtin = true
        )
    )

    /**
     * Pull a small pool of free public HTTP proxies and keep ones that respond quickly.
     * Free proxies rotate often — re-run from Admin → Network when they die.
     */
    fun fetchPublicProxies(limit: Int = 8): List<ProxyEntry> {
        val urls = listOf(
            "https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=4000&country=all&ssl=all&anonymity=all",
            "https://www.proxy-list.download/api/v1/get?type=http"
        )
        val raw = LinkedHashSet<String>()
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .proxy(Proxy.NO_PROXY)
            .build()
        for (u in urls) {
            try {
                val req = Request.Builder().url(u).get().header("User-Agent", "AhamAI/1.0").build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful || body.isBlank()) return@use
                    body.lineSequence()
                        .map { it.trim() }
                        .filter { it.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}:\\d{2,5}$")) }
                        .forEach { raw.add(it) }
                }
            } catch (_: Exception) { /* try next source */ }
            if (raw.size >= limit * 4) break
        }
        if (raw.isEmpty()) return emptyList()

        // Quick TCP-ish check via CONNECT / GET through proxy is heavy; just map first N unique.
        // Prefer a short probe of ipify through each candidate (parallel-ish sequential cap).
        val ok = ArrayList<ProxyEntry>()
        for ((i, line) in raw.withIndex()) {
            if (ok.size >= limit) break
            val host = line.substringBefore(':')
            val port = line.substringAfter(':').toIntOrNull() ?: continue
            val entry = ProxyEntry(
                id = "free_${host.replace('.', '_')}_$port",
                name = "Free · $host",
                type = ProxyType.HTTP,
                host = host,
                port = port,
                enabled = true,
                builtin = false
            )
            val probe = try {
                val b = OkHttpClient.Builder()
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(6, TimeUnit.SECONDS)
                    .proxy(javaProxy(entry))
                    .build()
                val r = Request.Builder().url("https://api.ipify.org?format=json").get().build()
                b.newCall(r).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
            if (probe) ok.add(entry)
            // Don't spend forever if network is slow
            if (i >= 40) break
        }
        // If probes all fail (common on restricted networks), still return untested top candidates
        // so admin can try them manually.
        if (ok.isEmpty()) {
            return raw.take(limit).mapIndexed { idx, line ->
                val host = line.substringBefore(':')
                val port = line.substringAfter(':').toIntOrNull() ?: 80
                ProxyEntry(
                    id = "free_${host.replace('.', '_')}_$port",
                    name = "Free · $host",
                    type = ProxyType.HTTP,
                    host = host,
                    port = port,
                    enabled = true,
                    builtin = false
                )
            }
        }
        return ok
    }

    private val configRef = AtomicReference(Config(entries = builtinPresets()))
    private val rotateCursor = AtomicInteger(0)
    private val clientCache = AtomicReference<OkHttpClient?>(null)
    private val shortClientCache = AtomicReference<OkHttpClient?>(null)

    fun config(): Config = configRef.get()

    fun updateFromJson(root: JSONObject?) {
        val block = root?.optJSONObject("proxy")
        if (block == null) {
            configRef.set(Config(entries = mergeWithBuiltins(emptyList())))
            invalidateClients()
            return
        }
        val arr = block.optJSONArray("entries") ?: JSONArray()
        val custom = ArrayList<ProxyEntry>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim().ifBlank { "proxy_$i" }
            custom.add(
                ProxyEntry(
                    id = id,
                    name = o.optString("name", id),
                    type = ProxyType.parse(o.optString("type", "http")),
                    host = o.optString("host", "").trim(),
                    port = o.optInt("port", 0),
                    username = o.optString("username", ""),
                    password = o.optString("password", ""),
                    enabled = o.optBoolean("enabled", true),
                    builtin = o.optBoolean("builtin", false)
                )
            )
        }
        configRef.set(
            Config(
                enabled = block.optBoolean("enabled", false),
                activeId = block.optString("activeId", "direct"),
                rotateOnFail = block.optBoolean("rotateOnFail", true),
                entries = mergeWithBuiltins(custom)
            )
        )
        invalidateClients()
    }

    private fun mergeWithBuiltins(custom: List<ProxyEntry>): List<ProxyEntry> {
        val builtins = builtinPresets()
        val byId = LinkedHashMap<String, ProxyEntry>()
        for (b in builtins) byId[b.id] = b
        for (c in custom) {
            // User overrides fill host/port on builtin templates
            val prev = byId[c.id]
            if (prev != null && prev.builtin) {
                byId[c.id] = prev.copy(
                    host = c.host.ifBlank { prev.host },
                    port = if (c.port > 0) c.port else prev.port,
                    username = c.username.ifBlank { prev.username },
                    password = c.password.ifBlank { prev.password },
                    enabled = c.enabled,
                    name = c.name.ifBlank { prev.name },
                    type = if (c.type != ProxyType.NONE || c.id == "direct") c.type else prev.type
                )
            } else {
                byId[c.id] = c
            }
        }
        return byId.values.toList()
    }

    fun replaceConfig(cfg: Config) {
        val all = mergeWithBuiltins(cfg.entries)
        configRef.set(cfg.copy(entries = all))
        invalidateClients()
    }

    fun toJsonObject(): JSONObject {
        val c = config()
        val arr = JSONArray()
        for (e in c.entries) {
            // Persist non-empty customs + filled builtins
            if (e.id == "direct" && e.host.isBlank()) {
                arr.put(
                    JSONObject()
                        .put("id", e.id)
                        .put("name", e.name)
                        .put("type", "none")
                        .put("host", "")
                        .put("port", 0)
                        .put("enabled", e.enabled)
                        .put("builtin", true)
                )
                continue
            }
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("type", e.type.wire())
                    .put("host", e.host)
                    .put("port", e.port)
                    .put("username", e.username)
                    .put("password", e.password)
                    .put("enabled", e.enabled)
                    .put("builtin", e.builtin)
            )
        }
        return JSONObject()
            .put("enabled", c.enabled)
            .put("activeId", c.activeId)
            .put("rotateOnFail", c.rotateOnFail)
            .put("entries", arr)
    }

    private fun invalidateClients() {
        clientCache.set(null)
        shortClientCache.set(null)
    }

    fun activeEntry(): ProxyEntry? {
        val c = config()
        if (!c.enabled) return null
        val id = c.activeId
        val hit = c.entries.firstOrNull { it.id == id && it.enabled }
        if (hit != null && (hit.type == ProxyType.NONE || hit.host.isNotBlank())) return hit
        return c.entries.firstOrNull { it.enabled && it.type != ProxyType.NONE && it.host.isNotBlank() }
    }

    fun rotateToNext(): ProxyEntry? {
        val c = config()
        if (!c.enabled || !c.rotateOnFail) return activeEntry()
        val usable = c.entries.filter { it.enabled && it.type != ProxyType.NONE && it.host.isNotBlank() }
        if (usable.isEmpty()) return null
        val idx = ((rotateCursor.getAndIncrement() % usable.size) + usable.size) % usable.size
        val next = usable[idx]
        configRef.set(c.copy(activeId = next.id))
        invalidateClients()
        return next
    }

    private fun javaProxy(entry: ProxyEntry?): Proxy {
        if (entry == null || entry.type == ProxyType.NONE || entry.host.isBlank() || entry.port <= 0) {
            return Proxy.NO_PROXY
        }
        val type = if (entry.type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return Proxy(type, InetSocketAddress(entry.host, entry.port))
    }

    private fun authenticator(entry: ProxyEntry?): Authenticator {
        if (entry == null || entry.username.isBlank()) return Authenticator.NONE
        return Authenticator { _: Route?, response: Response ->
            if (response.request.header("Proxy-Authorization") != null) return@Authenticator null
            val cred = Credentials.basic(entry.username, entry.password)
            response.request.newBuilder().header("Proxy-Authorization", cred).build()
        }
    }

    /**
     * Shared long-lived client for chat/agent streams (respects proxy + sensible timeouts).
     */
    fun llmClient(
        connectSec: Long = 15,
        readSec: Long = 90,
        writeSec: Long = 30,
        callSec: Long = 0
    ): OkHttpClient {
        // callSec=0 means no hard call timeout (long agent streams)
        val cached = clientCache.get()
        if (cached != null && callSec == 0L && readSec >= 60) return cached
        val entry = activeEntry()
        val b = OkHttpClient.Builder()
            .connectTimeout(connectSec, TimeUnit.SECONDS)
            .readTimeout(readSec, TimeUnit.SECONDS)
            .writeTimeout(writeSec, TimeUnit.SECONDS)
            .proxy(javaProxy(entry))
            .proxyAuthenticator(authenticator(entry))
        if (callSec > 0) b.callTimeout(callSec, TimeUnit.SECONDS)
        val client = b.build()
        if (callSec == 0L && readSec >= 60) clientCache.set(client)
        return client
    }

    fun shortClient(connectSec: Long = 8, readSec: Long = 12, callSec: Long = 15): OkHttpClient {
        val cached = shortClientCache.get()
        if (cached != null) return cached
        val entry = activeEntry()
        val client = OkHttpClient.Builder()
            .connectTimeout(connectSec, TimeUnit.SECONDS)
            .readTimeout(readSec, TimeUnit.SECONDS)
            .callTimeout(callSec, TimeUnit.SECONDS)
            .proxy(javaProxy(entry))
            .proxyAuthenticator(authenticator(entry))
            .build()
        shortClientCache.set(client)
        return client
    }

    /** Quick connectivity check through current proxy (or direct). */
    fun testProxy(entry: ProxyEntry? = activeEntry()): String {
        return try {
            val b = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .proxy(javaProxy(entry))
                .proxyAuthenticator(authenticator(entry))
            val client = b.build()
            val req = Request.Builder()
                .url("https://api.ipify.org?format=json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string()?.take(200) ?: ""
                if (resp.isSuccessful) "OK · $body"
                else "HTTP ${resp.code}: ${body.take(80)}"
            }
        } catch (e: Exception) {
            "ERR: ${e.message?.take(80)}"
        }
    }
}
