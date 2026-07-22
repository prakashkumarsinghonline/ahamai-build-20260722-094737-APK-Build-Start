package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Netlify API client — sites, deploys, env vars, forms & functions.
 * API docs: https://docs.netlify.com/api/get-started/
 *
 * Auth: personal access token (nfp_…) issued at
 *   https://app.netlify.com/user/applications#personal-access-tokens
 */
object NetlifyClient {

    private const val BASE = "https://api.netlify.com/api/v1"

    private fun token(): String = ConnectorsManager.getState(ConnectorsManager.NETLIFY).token

    data class NetlifyTool(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val action: String
    )

    private val TOOLS = listOf(
        NetlifyTool(
            "netlify_list_sites",
            "List all Netlify sites.",
            JSONObject("""{"type":"object","properties":{"limit":{"type":"string"}},"required":[]}"""),
            "netlify_list_sites"
        ),
        NetlifyTool(
            "netlify_get_site",
            "Get details of a single Netlify site.",
            JSONObject("""{"type":"object","properties":{"siteId":{"type":"string"}},"required":["siteId"]}"""),
            "netlify_get_site"
        ),
        NetlifyTool(
            "netlify_list_deploys",
            "List deploys for a Netlify site.",
            JSONObject("""{"type":"object","properties":{"siteId":{"type":"string"},"limit":{"type":"string"}},"required":["siteId"]}"""),
            "netlify_list_deploys"
        ),
        NetlifyTool(
            "netlify_get_deploy",
            "Get details of a single Netlify deploy.",
            JSONObject("""{"type":"object","properties":{"siteId":{"type":"string"},"deployId":{"type":"string"}},"required":["siteId","deployId"]}"""),
            "netlify_get_deploy"
        ),
        NetlifyTool(
            "netlify_list_env",
            "List environment variables for a Netlify site.",
            JSONObject("""{"type":"object","properties":{"siteId":{"type":"string"}},"required":["siteId"]}"""),
            "netlify_list_env"
        ),
        NetlifyTool(
            "netlify_list_forms",
            "List form submissions for a Netlify site.",
            JSONObject("""{"type":"object","properties":{"siteId":{"type":"string"}},"required":["siteId"]}"""),
            "netlify_list_forms"
        ),
        NetlifyTool(
            "netlify_list_functions",
            "List serverless functions deployed on a Netlify site.",
            JSONObject("""{"type":"object","properties":{"siteId":{"type":"string"}},"required":["siteId"]}"""),
            "netlify_list_functions"
        ),
        NetlifyTool(
            "netlify_trigger_deploy",
            "Trigger a new build & deploy for a Netlify site (optionally clearing the build cache).",
            JSONObject("""{"type":"object","properties":{"siteId":{"type":"string"},"clearCache":{"type":"string","description":"Optional 'true' to clear the build cache"}},"required":["siteId"]}"""),
            "netlify_trigger_deploy"
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
=== NETLIFY CONNECTOR ===
You have access to the connected Netlify account. Use these tools:

1. **netlify_list_sites** — List Netlify sites. Args: limit (optional)
2. **netlify_get_site** — Get site details. Args: siteId
3. **netlify_list_deploys** — List deploys. Args: siteId, limit (optional)
4. **netlify_get_deploy** — Get deploy details. Args: siteId, deployId
5. **netlify_list_env** — List env vars. Args: siteId
6. **netlify_list_forms** — List form submissions. Args: siteId
7. **netlify_list_functions** — List serverless functions. Args: siteId
8. **netlify_trigger_deploy** — Trigger a new build & deploy. Args: siteId, clearCache (optional 'true')

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

    // ─────────────── API methods ───────────────

    suspend fun listSites(limit: Int = 50): String = get("/sites", mapOf("per_page" to limit.toString()))
    suspend fun getSite(siteId: String): String = get("/sites/$siteId")
    suspend fun listDeploys(siteId: String, limit: Int = 20): String =
        get("/sites/$siteId/deploys", mapOf("per_page" to limit.toString()))
    suspend fun getDeploy(siteId: String, deployId: String): String =
        get("/sites/$siteId/deploys/$deployId")
    suspend fun listEnv(siteId: String): String = get("/sites/$siteId/env")
    suspend fun listForms(siteId: String): String = get("/sites/$siteId/forms")
    suspend fun listFunctions(siteId: String): String = get("/sites/$siteId/functions")
    suspend fun triggerDeploy(siteId: String, clearCache: Boolean = false): String {
        val body = if (clearCache) JSONObject().put("clear_cache", true).toString() else "{}"
        return post("/sites/$siteId/builds", body)
    }

    // ─────────────── Dispatcher ───────────────

    suspend fun executeTool(name: String, args: Map<String, String>): String {
        if (!ConnectorsManager.isConnected(ConnectorsManager.NETLIFY)) {
            return """{"error":"Netlify not connected. Go to Profile → Connectors to connect."}"""
        }
        return when (name.lowercase()) {
            "netlify_list_sites"     -> listSites((args["limit"] ?: "50").toIntOrNull() ?: 50)
            "netlify_get_site"       -> {
                val s = args["siteid"] ?: args["site_id"] ?: return """{"error":"siteId required"}"""
                getSite(s)
            }
            "netlify_list_deploys"   -> {
                val s = args["siteid"] ?: args["site_id"] ?: return """{"error":"siteId required"}"""
                listDeploys(s, (args["limit"] ?: "20").toIntOrNull() ?: 20)
            }
            "netlify_get_deploy"     -> {
                val s = args["siteid"] ?: args["site_id"] ?: return """{"error":"siteId required"}"""
                val d = args["deployid"] ?: args["deploy_id"] ?: return """{"error":"deployId required"}"""
                getDeploy(s, d)
            }
            "netlify_list_env"       -> {
                val s = args["siteid"] ?: args["site_id"] ?: return """{"error":"siteId required"}"""
                listEnv(s)
            }
            "netlify_list_forms"     -> {
                val s = args["siteid"] ?: args["site_id"] ?: return """{"error":"siteId required"}"""
                listForms(s)
            }
            "netlify_list_functions" -> {
                val s = args["siteid"] ?: args["site_id"] ?: return """{"error":"siteId required"}"""
                listFunctions(s)
            }
            "netlify_trigger_deploy" -> {
                val s = args["siteid"] ?: args["site_id"] ?: return """{"error":"siteId required"}"""
                val clear = (args["clearcache"] ?: args["clear_cache"] ?: "false").equals("true", ignoreCase = true)
                triggerDeploy(s, clear)
            }
            else -> """{"error":"Unknown Netlify tool: $name"}"""
        }
    }
}
