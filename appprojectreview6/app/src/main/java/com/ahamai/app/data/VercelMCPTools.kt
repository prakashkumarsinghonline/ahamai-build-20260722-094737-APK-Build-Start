package com.ahamai.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Vercel MCP tool definitions and execution logic.
 * Separated from VercelMCPManager to keep files manageable.
 */
object VercelMCPTools {

    data class VercelTool(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val action: String
    )

    private val TOOLS = listOf(
        VercelTool(
            name = "vercel_list_projects",
            description = "List all Vercel projects",
            parameters = JSONObject("""{"type":"object","properties":{},"required":[]}"""),
            action = "vercel_list_projects"
        ),
        VercelTool(
            name = "vercel_get_project",
            description = "Get project details",
            parameters = JSONObject("""{"type":"object","properties":{"projectId":{"type":"string"}},"required":["projectId"]}"""),
            action = "vercel_get_project"
        ),
        VercelTool(
            name = "vercel_list_deployments",
            description = "List deployments for a project",
            parameters = JSONObject("""{"type":"object","properties":{"projectId":{"type":"string","description":"optional"}},"required":[]}"""),
            action = "vercel_list_deployments"
        ),
        VercelTool(
            name = "vercel_get_deployment",
            description = "Get deployment details",
            parameters = JSONObject("""{"type":"object","properties":{"deploymentId":{"type":"string"}},"required":["deploymentId"]}"""),
            action = "vercel_get_deployment"
        ),
        VercelTool(
            name = "vercel_create_deployment",
            description = "Deploy a Vercel project. To REDEPLOY, pass a deploymentId (get it from vercel_list_deployments) — most reliable. To deploy a git branch, pass branch (needs a git-connected project).",
            parameters = JSONObject("""{"type":"object","properties":{"projectId":{"type":"string"},"deploymentId":{"type":"string","description":"Existing deployment to redeploy (recommended)"},"branch":{"type":"string","description":"Git branch to deploy (optional)"}},"required":["projectId"]}"""),
            action = "vercel_create_deployment"
        ),
        VercelTool(
            name = "vercel_list_domains",
            description = "List domains for a project",
            parameters = JSONObject("""{"type":"object","properties":{"projectId":{"type":"string"}},"required":["projectId"]}"""),
            action = "vercel_list_domains"
        ),
        VercelTool(
            name = "vercel_list_env",
            description = "List environment variables",
            parameters = JSONObject("""{"type":"object","properties":{"projectId":{"type":"string"}},"required":["projectId"]}"""),
            action = "vercel_list_env"
        ),
        VercelTool(
            name = "vercel_get_logs",
            description = "Get deployment logs",
            parameters = JSONObject("""{"type":"object","properties":{"deploymentId":{"type":"string"}},"required":["deploymentId"]}"""),
            action = "vercel_get_logs"
        )
    )

    /** Check if a tool name is a Vercel MCP tool */
    fun isVercelTool(toolName: String): Boolean {
        return TOOLS.any { it.name.equals(toolName, ignoreCase = true) }
    }

    /** Get the action name for a Vercel tool */
    fun getAction(toolName: String): String {
        return TOOLS.find { it.name.equals(toolName, ignoreCase = true) }?.action ?: toolName
    }

    /** Build structured tool definitions for OpenAI function calling */
    fun buildToolsJson(): JSONArray {
        val arr = JSONArray()
        for (tool in TOOLS) {
            arr.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parameters)
                })
            })
        }
        return arr
    }

    /** Build human-readable tools list for UI */
    fun getToolsList(): List<Pair<String, String>> {
        return TOOLS.map { it.name.removePrefix("vercel_").replace("_", " ").capitalizeWords() to it.description }
    }

    /** Build prompt description for the agent */
    fun buildPromptDescription(): String {
        if (!VercelMCPManager.isConnected) return ""
        return """
=== VERCEL MCP TOOLS ===
You have access to the connected Vercel account. Use these tools:

1. **vercel_list_projects** — List all Vercel projects. No args.
2. **vercel_get_project** — Get project details. Args: projectId
3. **vercel_list_deployments** — List deployments. Args: projectId (optional)
4. **vercel_get_deployment** — Get deployment details. Args: deploymentId
5. **vercel_create_deployment** — Deploy a project. Args: projectId, branch (optional)
6. **vercel_list_domains** — List domains for a project. Args: projectId
7. **vercel_list_env** — List env vars for a project. Args: projectId
8. **vercel_get_logs** — Get deployment logs. Args: deploymentId

Usage: <tool_call>TOOL_NAME<arg_value>args</arg_value></tool_call>
""".trimIndent()
    }

    /** Execute a Vercel MCP tool */
    suspend fun executeTool(toolName: String, args: Map<String, String>): String {
        if (!VercelMCPManager.isConnected) {
            return """{"error":"Vercel not connected. Go to Profile → Vercel MCP to connect."}"""
        }

        return when (toolName.lowercase()) {
            "vercel_list_projects" -> VercelMCPManager.listProjects()
            "vercel_get_project" -> {
                val projectId = args["projectid"] ?: args["project_id"] ?: return """{"error":"projectId required"}"""
                VercelMCPManager.getProject(projectId)
            }
            "vercel_list_deployments" -> {
                val projectId = args["projectid"] ?: args["project_id"]
                VercelMCPManager.listDeployments(projectId)
            }
            "vercel_get_deployment" -> {
                val deploymentId = args["deploymentid"] ?: args["deployment_id"] ?: return """{"error":"deploymentId required"}"""
                VercelMCPManager.getDeployment(deploymentId)
            }
            "vercel_create_deployment" -> {
                val projectId = args["projectid"] ?: args["project_id"] ?: return """{"error":"projectId required"}"""
                val branch = args["branch"] ?: ""
                val deploymentId = args["deploymentid"] ?: args["deployment_id"] ?: ""
                VercelMCPManager.createDeployment(projectId, branch, deploymentId)
            }
            "vercel_list_domains" -> {
                val projectId = args["projectid"] ?: args["project_id"] ?: return """{"error":"projectId required"}"""
                VercelMCPManager.listDomains(projectId)
            }
            "vercel_list_env" -> {
                val projectId = args["projectid"] ?: args["project_id"] ?: return """{"error":"projectId required"}"""
                VercelMCPManager.listEnvVars(projectId)
            }
            "vercel_get_logs" -> {
                val deploymentId = args["deploymentid"] ?: args["deployment_id"] ?: return """{"error":"deploymentId required"}"""
                VercelMCPManager.getLogs(deploymentId)
            }
            else -> """{"error":"Unknown Vercel tool: $toolName"}"""
        }
    }

    private fun String.capitalizeWords(): String {
        return split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { it.uppercase() }
        }
    }
}
