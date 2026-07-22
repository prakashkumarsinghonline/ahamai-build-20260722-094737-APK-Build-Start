package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Render API client — list services, list deploys, trigger deploys, get logs,
 * list env vars. API docs: https://api-docs.render.com/
 *
 * Auth: bearer token (rnd_…) issued at
 *   https://dashboard.render.com/u/settings/api-keys
 */
object RenderClient {

    private const val BASE = "https://api.render.com/v1"

    private fun token(): String = ConnectorsManager.getState(ConnectorsManager.RENDER).token

    // ─────────────── Tool catalog ───────────────

    data class RenderTool(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val action: String
    )

    private val TOOLS = listOf(
        RenderTool(
            "render_list_services",
            "List all Render services (web services, background workers, cron jobs, databases).",
            JSONObject("""{"type":"object","properties":{"limit":{"type":"string","description":"Max results (default 20)"}},"required":[]}"""),
            "render_list_services"
        ),
        RenderTool(
            "render_get_service",
            "Get details of a single Render service by ID.",
            JSONObject("""{"type":"object","properties":{"serviceId":{"type":"string"}},"required":["serviceId"]}"""),
            "render_get_service"
        ),
        RenderTool(
            "render_list_deploys",
            "List deploys for a Render service.",
            JSONObject("""{"type":"object","properties":{"serviceId":{"type":"string"},"limit":{"type":"string","description":"Max results (default 20)"}},"required":["serviceId"]}"""),
            "render_list_deploys"
        ),
        RenderTool(
            "render_trigger_deploy",
            "Trigger a new deploy for a Render service (optionally from a branch/commit).",
            JSONObject("""{"type":"object","properties":{"serviceId":{"type":"string"},"commitId":{"type":"string","description":"Optional commit SHA"}},"required":["serviceId"]}"""),
            "render_trigger_deploy"
        ),
        RenderTool(
            "render_list_env",
            "List environment variables for a Render service (values are masked).",
            JSONObject("""{"type":"object","properties":{"serviceId":{"type":"string"}},"required":["serviceId"]}"""),
            "render_list_env"
        ),
        RenderTool(
            "render_get_logs",
            "Fetch the most recent logs for a Render service.",
            JSONObject("""{"type":"object","properties":{"serviceId":{"type":"string"},"limit":{"type":"string","description":"Max lines (default 100)"}},"required":["serviceId"]}"""),
            "render_get_logs"
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
=== RENDER CONNECTOR ===
You have access to the connected Render account. Use these tools:

1. **render_list_services** — List Render services. Args: limit (optional)
2. **render_get_service** — Get service details. Args: serviceId
3. **render_list_deploys** — List deploys for a service. Args: serviceId, limit (optional)
4. **render_trigger_deploy** — Trigger a deploy. Args: serviceId, commitId (optional)
5. **render_list_env** — List env vars (masked). Args: serviceId
6. **render_get_logs** — Get recent logs. Args: serviceId, limit (optional)

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

    suspend fun listServices(limit: Int = 20): String =
        get("/services", mapOf("limit" to limit.toString()))

    suspend fun getService(serviceId: String): String =
        get("/services/$serviceId")

    suspend fun listDeploys(serviceId: String, limit: Int = 20): String =
        get("/services/$serviceId/deploys", mapOf("limit" to limit.toString()))

    suspend fun triggerDeploy(serviceId: String, commitId: String? = null): String {
        val body = if (commitId.isNullOrBlank()) "{}"
                   else JSONObject().put("commitId", commitId).toString()
        return post("/services/$serviceId/deploys", body)
    }

    suspend fun listEnv(serviceId: String): String =
        get("/services/$serviceId/env-vars")

    suspend fun getLogs(serviceId: String, limit: Int = 100): String =
        get("/services/$serviceId/logs", mapOf("limit" to limit.toString()))

    // ─────────────── Dispatcher ───────────────

    suspend fun executeTool(name: String, args: Map<String, String>): String {
        if (!ConnectorsManager.isConnected(ConnectorsManager.RENDER)) {
            return """{"error":"Render not connected. Go to Profile → Connectors to connect."}"""
        }
        return when (name.lowercase()) {
            "render_list_services" -> listServices((args["limit"] ?: "20").toIntOrNull() ?: 20)
            "render_get_service"   -> {
                val id = args["serviceid"] ?: args["service_id"] ?: return """{"error":"serviceId required"}"""
                getService(id)
            }
            "render_list_deploys"  -> {
                val id = args["serviceid"] ?: args["service_id"] ?: return """{"error":"serviceId required"}"""
                listDeploys(id, (args["limit"] ?: "20").toIntOrNull() ?: 20)
            }
            "render_trigger_deploy" -> {
                val id = args["serviceid"] ?: args["service_id"] ?: return """{"error":"serviceId required"}"""
                triggerDeploy(id, args["commitid"] ?: args["commit_id"])
            }
            "render_list_env" -> {
                val id = args["serviceid"] ?: args["service_id"] ?: return """{"error":"serviceId required"}"""
                listEnv(id)
            }
            "render_get_logs" -> {
                val id = args["serviceid"] ?: args["service_id"] ?: return """{"error":"serviceId required"}"""
                getLogs(id, (args["limit"] ?: "100").toIntOrNull() ?: 100)
            }
            else -> """{"error":"Unknown Render tool: $name"}"""
        }
    }
}
