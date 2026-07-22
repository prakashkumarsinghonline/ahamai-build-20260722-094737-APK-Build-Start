package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Supabase API client — projects, API keys & SQL queries.
 * API docs: https://supabase.com/docs/reference/api/introduction
 *
 * Auth: personal access token (sbp_…) issued at
 *   https://supabase.com/dashboard/account/tokens
 *
 * NOTE: SQL queries are executed against the REST `/rest/v1/rpc/` endpoint
 * via the project's anon key, NOT the management API. For read-only SQL,
 * the agent should pass the project's connection string directly to a
 * `read_only_sql` tool. To keep this surface simple and safe, we expose
 * only the management endpoints + a single SQL RPC runner that requires
 * both projectRef and serviceKey.
 */
object SupabaseClient {

    private const val BASE = "https://api.supabase.com/v1"

    private fun token(): String = ConnectorsManager.getState(ConnectorsManager.SUPABASE).token

    data class SupabaseTool(
        val name: String,
        val description: String,
        val parameters: JSONObject,
        val action: String
    )

    private val TOOLS = listOf(
        SupabaseTool(
            "supabase_list_projects",
            "List all Supabase projects in the connected account.",
            JSONObject("""{"type":"object","properties":{},"required":[]}"""),
            "supabase_list_projects"
        ),
        SupabaseTool(
            "supabase_get_project",
            "Get details (region, status, db host) of a single Supabase project.",
            JSONObject("""{"type":"object","properties":{"projectRef":{"type":"string"}},"required":["projectRef"]}"""),
            "supabase_get_project"
        ),
        SupabaseTool(
            "supabase_get_api_keys",
            "Get the anon + service_role API keys for a Supabase project.",
            JSONObject("""{"type":"object","properties":{"projectRef":{"type":"string"}},"required":["projectRef"]}"""),
            "supabase_get_api_keys"
        ),
        SupabaseTool(
            "supabase_list_tables",
            "List tables & views in the Supabase Postgres database via the introspection schema.",
            JSONObject("""{"type":"object","properties":{"projectRef":{"type":"string"},"serviceKey":{"type":"string","description":"service_role key (from supabase_get_api_keys)"}},"required":["projectRef","serviceKey"]}"""),
            "supabase_list_tables"
        ),
        SupabaseTool(
            "supabase_run_sql",
            "Run a read-only SQL query against the Supabase database via the /pg/query endpoint.",
            JSONObject("""{"type":"object","properties":{"projectRef":{"type":"string"},"query":{"type":"string","description":"SQL query (prefer read-only SELECT)"}},"required":["projectRef","query"]}"""),
            "supabase_run_sql"
        ),
        SupabaseTool(
            "supabase_list_functions",
            "List Edge Functions deployed on a Supabase project.",
            JSONObject("""{"type":"object","properties":{"projectRef":{"type":"string"}},"required":["projectRef"]}"""),
            "supabase_list_functions"
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
=== SUPABASE CONNECTOR ===
You have access to the connected Supabase account. Use these tools:

1. **supabase_list_projects** — List projects. No args.
2. **supabase_get_project** — Get project details. Args: projectRef
3. **supabase_get_api_keys** — Get anon + service_role keys. Args: projectRef
4. **supabase_list_tables** — List tables via introspection. Args: projectRef, serviceKey
5. **supabase_run_sql** — Run a read-only SQL query. Args: projectRef, query
6. **supabase_list_functions** — List Edge Functions. Args: projectRef

Usage: <tool_call>TOOL_NAME<arg_value>args</arg_value></tool_call>
""".trimIndent()

    // ─────────────── HTTP ───────────────

    private suspend fun get(url: String, authHeader: String): String =
        withContext(Dispatchers.IO) {
            try {
                val u = java.net.URI(url).toURL()
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", authHeader)
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

    private suspend fun postJson(url: String, body: String, authHeader: String): String =
        withContext(Dispatchers.IO) {
            try {
                val u = java.net.URI(url).toURL()
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", authHeader)
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

    suspend fun listProjects(): String = get("$BASE/projects", "Bearer ${token()}")
    suspend fun getProject(ref: String): String = get("$BASE/projects/$ref", "Bearer ${token()}")
    suspend fun getApiKeys(ref: String): String = get("$BASE/projects/$ref/api-keys", "Bearer ${token()}")
    suspend fun listFunctions(ref: String): String = get("$BASE/projects/$ref/functions", "Bearer ${token()}")

    /** List tables via the Postgres introspection REST endpoint (requires service_role key). */
    suspend fun listTables(ref: String, serviceKey: String): String {
        val sql = "SELECT table_schema, table_name, table_type FROM information_schema.tables WHERE table_schema NOT IN ('pg_catalog','information_schema') ORDER BY table_schema, table_name;"
        return runSql(ref, sql, serviceKey)
    }

    /** Run a SQL query via the management API's /pg/query endpoint. */
    suspend fun runSql(ref: String, query: String, serviceKey: String = ""): String {
        // Prefer the management /pg/query endpoint (bearer = Supabase PAT).
        return postJson(
            "$BASE/projects/$ref/pg/query",
            JSONObject().put("query", query).toString(),
            "Bearer ${token()}"
        )
    }

    // ─────────────── Dispatcher ───────────────

    suspend fun executeTool(name: String, args: Map<String, String>): String {
        if (!ConnectorsManager.isConnected(ConnectorsManager.SUPABASE)) {
            return """{"error":"Supabase not connected. Go to Profile → Connectors to connect."}"""
        }
        return when (name.lowercase()) {
            "supabase_list_projects" -> listProjects()
            "supabase_get_project"   -> {
                val r = args["projectref"] ?: args["project_ref"] ?: args["ref"] ?: return """{"error":"projectRef required"}"""
                getProject(r)
            }
            "supabase_get_api_keys"  -> {
                val r = args["projectref"] ?: args["project_ref"] ?: args["ref"] ?: return """{"error":"projectRef required"}"""
                getApiKeys(r)
            }
            "supabase_list_tables"   -> {
                val r = args["projectref"] ?: args["project_ref"] ?: args["ref"] ?: return """{"error":"projectRef required"}"""
                val k = args["servicekey"] ?: args["service_key"] ?: return """{"error":"serviceKey required"}"""
                listTables(r, k)
            }
            "supabase_run_sql"       -> {
                val r = args["projectref"] ?: args["project_ref"] ?: args["ref"] ?: return """{"error":"projectRef required"}"""
                val q = args["query"] ?: return """{"error":"query required"}"""
                runSql(r, q)
            }
            "supabase_list_functions" -> {
                val r = args["projectref"] ?: args["project_ref"] ?: args["ref"] ?: return """{"error":"projectRef required"}"""
                listFunctions(r)
            }
            else -> """{"error":"Unknown Supabase tool: $name"}"""
        }
    }
}
