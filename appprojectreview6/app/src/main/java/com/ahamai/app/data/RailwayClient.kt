package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Railway GraphQL API client — projects, services, deployments & environment.
 * API docs: https://docs.railway.app/reference/public-api
 *
 * Auth: team token issued at https://railway.com/account/tokens
 * Endpoint: https://backboard.railway.app/graphql
 */
object RailwayClient {

    private const val GRAPHQL = "https://backboard.railway.app/graphql"

    private fun token(): String = ConnectorsManager.getState(ConnectorsManager.RAILWAY).token

    data class RailwayTool(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val action: String
    )

    private val TOOLS = listOf(
        RailwayTool(
            "railway_list_projects",
            "List all Railway projects visible to the connected token.",
            JSONObject("""{"type":"object","properties":{},"required":[]}"""),
            "railway_list_projects"
        ),
        RailwayTool(
            "railway_get_project",
            "Get details of a single Railway project.",
            JSONObject("""{"type":"object","properties":{"projectId":{"type":"string"}},"required":["projectId"]}"""),
            "railway_get_project"
        ),
        RailwayTool(
            "railway_list_services",
            "List services inside a Railway project.",
            JSONObject("""{"type":"object","properties":{"projectId":{"type":"string"}},"required":["projectId"]}"""),
            "railway_list_services"
        ),
        RailwayTool(
            "railway_list_deployments",
            "List recent deployments for a Railway project (optionally per-service).",
            JSONObject("""{"type":"object","properties":{"projectId":{"type":"string"},"serviceId":{"type":"string","description":"Optional service filter"}},"required":["projectId"]}"""),
            "railway_list_deployments"
        ),
        RailwayTool(
            "railway_get_deployment",
            "Get details of a single Railway deployment.",
            JSONObject("""{"type":"object","properties":{"deploymentId":{"type":"string"}},"required":["deploymentId"]}"""),
            "railway_get_deployment"
        ),
        RailwayTool(
            "railway_get_logs",
            "Fetch the most recent logs for a Railway deployment.",
            JSONObject("""{"type":"object","properties":{"deploymentId":{"type":"string"},"limit":{"type":"string","description":"Max lines (default 100)"}},"required":["deploymentId"]}"""),
            "railway_get_logs"
        ),
        RailwayTool(
            "railway_redeploy",
            "Redeploy an existing Railway deployment by ID (re-runs the latest build for that service).",
            JSONObject("""{"type":"object","properties":{"deploymentId":{"type":"string"}},"required":["deploymentId"]}"""),
            "railway_redeploy"
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
=== RAILWAY CONNECTOR ===
You have access to the connected Railway account. Use these tools:

1. **railway_list_projects** — List projects. No args.
2. **railway_get_project** — Get project details. Args: projectId
3. **railway_list_services** — List services. Args: projectId
4. **railway_list_deployments** — List deployments. Args: projectId, serviceId (optional)
5. **railway_get_deployment** — Get deployment details. Args: deploymentId
6. **railway_get_logs** — Get deployment logs. Args: deploymentId, limit (optional)
7. **railway_redeploy** — Redeploy an existing deployment. Args: deploymentId

Usage: <tool_call>TOOL_NAME<arg_value>args</arg_value></tool_call>
""".trimIndent()

    // ─────────────── GraphQL helper ───────────────

    private suspend fun gql(query: String, variables: JSONObject = JSONObject()): String =
        withContext(Dispatchers.IO) {
            try {
                val u = java.net.URI(GRAPHQL).toURL()
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer ${token()}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val body = JSONObject().put("query", query).put("variables", variables).toString()
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

    suspend fun listProjects(): String = gql(
        """query { projects { edges { node { id name } } } }"""
    )

    suspend fun getProject(projectId: String): String = gql(
        """query(${'$'}id: String!) { project(id: ${'$'}id) { id name description createdAt } }""",
        JSONObject().put("id", projectId)
    )

    suspend fun listServices(projectId: String): String = gql(
        """query(${'$'}id: String!) { project(id: ${'$'}id) { services { edges { node { id name } } } } }""",
        JSONObject().put("id", projectId)
    )

    suspend fun listDeployments(projectId: String, serviceId: String? = null): String {
        val vars = JSONObject().put("id", projectId)
        val q = if (serviceId.isNullOrBlank()) {
            """query(${'$'}id: String!) { project(id: ${'$'}id) { deployments { edges { node { id status createdAt } } } } }"""
        } else {
            vars.put("serviceId", serviceId)
            """query(${'$'}id: String!, ${'$'}serviceId: String!) {
                 project(id: ${'$'}id) {
                   service(id: ${'$'}serviceId) {
                     deployments { edges { node { id status createdAt } } }
                   }
                 }
               }"""
        }
        return gql(q, vars)
    }

    suspend fun getDeployment(deploymentId: String): String = gql(
        """query(${'$'}id: String!) { deployment(id: ${'$'}id) { id status createdAt meta } }""",
        JSONObject().put("id", deploymentId)
    )

    suspend fun getLogs(deploymentId: String, limit: Int = 100): String = gql(
        """query(${'$'}id: String!, ${'$'}limit: Int!) { deployment(id: ${'$'}id) { logs(limit: ${'$'}limit) } }""",
        JSONObject().put("id", deploymentId).put("limit", limit)
    )

    suspend fun redeploy(deploymentId: String): String = gql(
        """mutation(${'$'}id: String!) { deploymentRedeploy(id: ${'$'}id) { id status } }""",
        JSONObject().put("id", deploymentId)
    )

    // ─────────────── Dispatcher ───────────────

    suspend fun executeTool(name: String, args: Map<String, String>): String {
        if (!ConnectorsManager.isConnected(ConnectorsManager.RAILWAY)) {
            return """{"error":"Railway not connected. Go to Profile → Connectors to connect."}"""
        }
        return when (name.lowercase()) {
            "railway_list_projects"  -> listProjects()
            "railway_get_project"    -> {
                val id = args["projectid"] ?: args["project_id"] ?: return """{"error":"projectId required"}"""
                getProject(id)
            }
            "railway_list_services"  -> {
                val id = args["projectid"] ?: args["project_id"] ?: return """{"error":"projectId required"}"""
                listServices(id)
            }
            "railway_list_deployments" -> {
                val id = args["projectid"] ?: args["project_id"] ?: return """{"error":"projectId required"}"""
                listDeployments(id, args["serviceid"] ?: args["service_id"])
            }
            "railway_get_deployment" -> {
                val id = args["deploymentid"] ?: args["deployment_id"] ?: return """{"error":"deploymentId required"}"""
                getDeployment(id)
            }
            "railway_get_logs"       -> {
                val id = args["deploymentid"] ?: args["deployment_id"] ?: return """{"error":"deploymentId required"}"""
                getLogs(id, (args["limit"] ?: "100").toIntOrNull() ?: 100)
            }
            "railway_redeploy"       -> {
                val id = args["deploymentid"] ?: args["deployment_id"] ?: return """{"error":"deploymentId required"}"""
                redeploy(id)
            }
            else -> """{"error":"Unknown Railway tool: $name"}"""
        }
    }
}
