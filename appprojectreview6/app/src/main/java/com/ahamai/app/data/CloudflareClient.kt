package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cloudflare API client — Workers, Zones, KV namespaces & account info.
 * API docs: https://developers.cloudflare.com/api/
 *
 * Auth: bearer API token issued at
 *   https://dash.cloudflare.com/profile/api-tokens
 * The token must have permission to read Workers / Zones / KV as needed.
 */
object CloudflareClient {

    private const val BASE = "https://api.cloudflare.com/client/v4"

    private fun token(): String = ConnectorsManager.getState(ConnectorsManager.CLOUDFLARE).token

    data class CloudflareTool(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val action: String
    )

    private val TOOLS = listOf(
        CloudflareTool(
            "cloudflare_verify",
            "Verify the Cloudflare API token and return the associated account.",
            JSONObject("""{"type":"object","properties":{},"required":[]}"""),
            "cloudflare_verify"
        ),
        CloudflareTool(
            "cloudflare_list_zones",
            "List all zones (domains) in the Cloudflare account.",
            JSONObject("""{"type":"object","properties":{"limit":{"type":"string"}},"required":[]}"""),
            "cloudflare_list_zones"
        ),
        CloudflareTool(
            "cloudflare_list_workers",
            "List all Workers scripts deployed on the account.",
            JSONObject("""{"type":"object","properties":{"accountId":{"type":"string","description":"Cloudflare account ID"}},"required":["accountId"]}"""),
            "cloudflare_list_workers"
        ),
        CloudflareTool(
            "cloudflare_get_worker",
            "Get the source code / metadata of a single Worker.",
            JSONObject("""{"type":"object","properties":{"accountId":{"type":"string"},"scriptName":{"type":"string"}},"required":["accountId","scriptName"]}"""),
            "cloudflare_get_worker"
        ),
        CloudflareTool(
            "cloudflare_list_kv_namespaces",
            "List KV namespaces in the account.",
            JSONObject("""{"type":"object","properties":{"accountId":{"type":"string"}},"required":["accountId"]}"""),
            "cloudflare_list_kv_namespaces"
        ),
        CloudflareTool(
            "cloudflare_list_dns_records",
            "List DNS records for a zone.",
            JSONObject("""{"type":"object","properties":{"zoneId":{"type":"string"},"type":{"type":"string","description":"Optional filter: A, AAAA, CNAME, TXT, MX..."}},"required":["zoneId"]}"""),
            "cloudflare_list_dns_records"
        ),
        CloudflareTool(
            "cloudflare_list_accounts",
            "List accounts the API token has access to (use to discover accountId).",
            JSONObject("""{"type":"object","properties":{},"required":[]}"""),
            "cloudflare_list_accounts"
        ),
        CloudflareTool(
            "cloudflare_create_dns_record",
            "Create a DNS record in a zone.",
            JSONObject("""{"type":"object","properties":{"zoneId":{"type":"string"},"type":{"type":"string","description":"A, AAAA, CNAME, TXT, MX..."},"name":{"type":"string","description":"Record name e.g. www.example.com"},"content":{"type":"string","description":"Record value e.g. an IP or target host"},"ttl":{"type":"string","description":"Optional TTL seconds (1 = auto)"},"proxied":{"type":"string","description":"Optional 'true' to proxy through Cloudflare"}},"required":["zoneId","type","name","content"]}"""),
            "cloudflare_create_dns_record"
        ),
        CloudflareTool(
            "cloudflare_purge_cache",
            "Purge the Cloudflare cache for a zone (everything by default).",
            JSONObject("""{"type":"object","properties":{"zoneId":{"type":"string"}},"required":["zoneId"]}"""),
            "cloudflare_purge_cache"
        )
    )

    fun buildToolsJson(): JSONArray {
        val arr = JSONArray()
        for (t in TOOLS) {
            arr.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", t.name)
                    put("description", t.description)
                    put("parameters", t.parameters)
                })
            })
        }
        return arr
    }

    fun buildPromptDescription(): String = """
=== CLOUDFLARE CONNECTOR ===
You have access to the connected Cloudflare account. Use these tools:

1. **cloudflare_verify** — Verify token & show account. No args.
2. **cloudflare_list_accounts** — List accounts (to discover accountId). No args.
3. **cloudflare_list_zones** — List zones (domains). Args: limit (optional)
4. **cloudflare_list_workers** — List Workers. Args: accountId
5. **cloudflare_get_worker** — Get Worker source. Args: accountId, scriptName
6. **cloudflare_list_kv_namespaces** — List KV namespaces. Args: accountId
7. **cloudflare_list_dns_records** — List DNS records. Args: zoneId, type (optional)
8. **cloudflare_create_dns_record** — Create a DNS record. Args: zoneId, type, name, content, ttl (opt), proxied (opt)
9. **cloudflare_purge_cache** — Purge the zone cache. Args: zoneId

Usage: <tool_call>TOOL_NAME<arg_value>args</arg_value></tool_call>
""".trimIndent()

    // ─────────────── HTTP ───────────────

    private suspend fun get(path: String, query: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            try {
                val qs = if (query.isEmpty()) "" else "?" + query.entries.joinToString("&") {
                    "${it.key}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
                }
                val u = java.net.URI("$BASE$path$qs").toURL()
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${token()}")
                conn.setRequestProperty("Accept", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val code = conn.responseCode
                val body = if (code in 200..299) conn.inputStream.bufferedReader().readText()
                           else conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                conn.disconnect()
                JSONObject().put("status", code).put("body", body.ifBlank { "{}" }).toString()
            } catch (e: Exception) {
                JSONObject().put("error", e.message?.take(120) ?: "Unknown error").toString()
            }
        }

    // ─────────────── API methods ───────────────

    suspend fun verify(): String = get("/user/tokens/verify")
    suspend fun listAccounts(): String = get("/accounts")
    suspend fun listZones(limit: Int = 50): String = get("/zones", mapOf("per_page" to limit.toString()))
    suspend fun listWorkers(accountId: String): String = get("/accounts/$accountId/workers/scripts")
    suspend fun getWorker(accountId: String, scriptName: String): String =
        get("/accounts/$accountId/workers/scripts/$scriptName")
    suspend fun listKvNamespaces(accountId: String): String =
        get("/accounts/$accountId/storage/kv/namespaces")
    suspend fun listDnsRecords(zoneId: String, type: String? = null): String {
        val q = mutableMapOf("per_page" to "100")
        if (!type.isNullOrBlank()) q["type"] = type
        return get("/zones/$zoneId/dns_records", q)
    }

    suspend fun createDnsRecord(
        zoneId: String, type: String, recordName: String, content: String,
        ttl: Int = 1, proxied: Boolean = false
    ): String {
        val body = JSONObject().apply {
            put("type", type)
            put("name", recordName)
            put("content", content)
            put("ttl", ttl)
            put("proxied", proxied)
        }.toString()
        return post("/zones/$zoneId/dns_records", body)
    }

    suspend fun purgeCache(zoneId: String): String =
        post("/zones/$zoneId/purge_cache", JSONObject().put("purge_everything", true).toString())

    private suspend fun post(path: String, body: String): String =
        withContext(Dispatchers.IO) {
            try {
                val u = java.net.URI("$BASE$path").toURL()
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer ${token()}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.write(body.toByteArray())
                val code = conn.responseCode
                val resp = if (code in 200..299) conn.inputStream.bufferedReader().readText()
                           else conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                conn.disconnect()
                JSONObject().put("status", code).put("body", resp.ifBlank { "{}" }).toString()
            } catch (e: Exception) {
                JSONObject().put("error", e.message?.take(120) ?: "Unknown error").toString()
            }
        }

    // ─────────────── Dispatcher ───────────────

    suspend fun executeTool(name: String, args: Map<String, String>): String {
        if (!ConnectorsManager.isConnected(ConnectorsManager.CLOUDFLARE)) {
            return """{"error":"Cloudflare not connected. Go to Profile → Connectors to connect."}"""
        }
        return when (name.lowercase()) {
            "cloudflare_verify"           -> verify()
            "cloudflare_list_accounts"    -> listAccounts()
            "cloudflare_list_zones"       -> listZones((args["limit"] ?: "50").toIntOrNull() ?: 50)
            "cloudflare_list_workers"     -> {
                val a = args["accountid"] ?: args["account_id"] ?: return """{"error":"accountId required"}"""
                listWorkers(a)
            }
            "cloudflare_get_worker"       -> {
                val a = args["accountid"] ?: args["account_id"] ?: return """{"error":"accountId required"}"""
                val s = args["scriptname"] ?: args["script_name"] ?: return """{"error":"scriptName required"}"""
                getWorker(a, s)
            }
            "cloudflare_list_kv_namespaces" -> {
                val a = args["accountid"] ?: args["account_id"] ?: return """{"error":"accountId required"}"""
                listKvNamespaces(a)
            }
            "cloudflare_list_dns_records" -> {
                val z = args["zoneid"] ?: args["zone_id"] ?: return """{"error":"zoneId required"}"""
                listDnsRecords(z, args["type"])
            }
            "cloudflare_create_dns_record" -> {
                val z = args["zoneid"] ?: args["zone_id"] ?: return """{"error":"zoneId required"}"""
                val type = args["type"] ?: return """{"error":"type required (A, CNAME, TXT...)"}"""
                val recName = args["name"] ?: return """{"error":"name required"}"""
                val content = args["content"] ?: return """{"error":"content required"}"""
                val ttl = (args["ttl"] ?: "1").toIntOrNull() ?: 1
                val proxied = (args["proxied"] ?: "false").equals("true", ignoreCase = true)
                createDnsRecord(z, type, recName, content, ttl, proxied)
            }
            "cloudflare_purge_cache" -> {
                val z = args["zoneid"] ?: args["zone_id"] ?: return """{"error":"zoneId required"}"""
                purgeCache(z)
            }
            else -> """{"error":"Unknown Cloudflare tool: $name"}"""
        }
    }
}
