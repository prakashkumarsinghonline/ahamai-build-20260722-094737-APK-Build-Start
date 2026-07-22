package com.ahamai.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Vercel connector — bridge into the unified ConnectorsManager.
 * Tool definitions and execution logic are in VercelMCPTools.kt.
 *
 * Connection state is now persisted by ConnectorsManager (PREFS_NAME="ahamai_connectors",
 * key="connector_vercel"). The legacy `ahamai_mcp`/`vercel_connection` key is migrated
 * on first run — see ConnectorsManager.init().
 *
 * The public surface below mirrors the old API so existing callers
 * (StructuredTools, CodeAgent, the new ConnectorsScreen) keep compiling.
 */
object VercelMCPManager {

    data class VercelConnection(
        val enabled: Boolean = false,
        val token: String = "",
        val verified: Boolean = false
    )

    /** No-op now; kept for back-compat. ConnectorsManager.init() does the real init. */
    fun init(context: Context) { ConnectorsManager.init(context) }

    val isConnected: Boolean
        get() = ConnectorsManager.isConnected(ConnectorsManager.VERCEL)

    val isVerified: Boolean
        get() = ConnectorsManager.getState(ConnectorsManager.VERCEL).verified

    val currentToken: String
        get() = ConnectorsManager.getState(ConnectorsManager.VERCEL).token

    val current: VercelConnection
        get() {
            val s = ConnectorsManager.getState(ConnectorsManager.VERCEL)
            return VercelConnection(s.enabled, s.token, s.verified)
        }

    fun save(conn: VercelConnection) =
        ConnectorsManager.saveState(ConnectorsManager.VERCEL,
            ConnectorsManager.ConnectorState(conn.enabled, conn.token, conn.verified))

    fun setEnabled(enabled: Boolean) =
        ConnectorsManager.setEnabled(ConnectorsManager.VERCEL, enabled)

    fun setToken(token: String) =
        ConnectorsManager.setToken(ConnectorsManager.VERCEL, token)

    fun setVerified(verified: Boolean) =
        ConnectorsManager.setVerified(ConnectorsManager.VERCEL, verified)

    fun disconnect() = ConnectorsManager.disconnect(ConnectorsManager.VERCEL)

    fun getVercelAuthUrl(): String = "https://vercel.com/account/tokens"

    /** Verify a candidate Vercel token WITHOUT saving it. */
    suspend fun verifyToken(token: String): String? =
        ConnectorsManager.verify(ConnectorsManager.VERCEL, token)

    // ===== Individual API methods for VercelMCPTools =====

    suspend fun listProjects(): String =
        vercelApiGet("https://api.vercel.com/v9/projects")

    suspend fun getProject(projectId: String): String =
        vercelApiGet("https://api.vercel.com/v9/projects/$projectId")

    suspend fun listDeployments(projectId: String? = null): String {
        val url = if (projectId.isNullOrBlank()) "https://api.vercel.com/v6/deployments"
        else "https://api.vercel.com/v6/deployments?projectId=$projectId"
        return vercelApiGet(url)
    }

    suspend fun getDeployment(deploymentId: String): String =
        vercelApiGet("https://api.vercel.com/v13/deployments/$deploymentId")

    suspend fun createDeployment(projectId: String, branch: String = "", deploymentId: String = ""): String {
        // Vercel's POST /v13/deployments REQUIRES a "name" field (the project name/slug) — omitting
        // it returns HTTP 400, which is why deploys used to silently fail. We pass the project id as
        // the name (Vercel accepts the project slug/id here). Two supported modes:
        //  • redeploy an existing deployment  -> pass deploymentId (most reliable)
        //  • deploy a git branch              -> pass branch (needs a git-connected project)
        val body = JSONObject().apply {
            put("name", projectId)
            put("project", projectId)
            put("target", "production")
            when {
                deploymentId.isNotBlank() -> put("deploymentId", deploymentId)
                branch.isNotBlank() -> put("gitSource", JSONObject().apply {
                    put("type", "github")
                    put("ref", branch)
                })
            }
        }.toString()
        return vercelApiPost("https://api.vercel.com/v13/deployments", body)
    }

    suspend fun listDomains(projectId: String): String =
        vercelApiGet("https://api.vercel.com/v9/projects/$projectId/domains")

    suspend fun listEnvVars(projectId: String): String =
        vercelApiGet("https://api.vercel.com/v9/projects/$projectId/env")

    suspend fun getLogs(deploymentId: String): String =
        vercelApiGet("https://api.vercel.com/v1/deployments/$deploymentId/logs?limit=50")

    // ===== HTTP helpers =====

    private suspend fun vercelApiGet(url: String): String {
        return try {
            withContext(Dispatchers.IO) {
                val u = java.net.URI(url).toURL()
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("Authorization", "Bearer ${currentToken}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                }
                conn.disconnect()
                JSONObject().apply {
                    put("status", code)
                    put("body", body.ifBlank { "{}" })
                }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", e.message?.take(100) ?: "Unknown error")
            }.toString()
        }
    }

    private suspend fun vercelApiPost(url: String, jsonBody: String): String {
        return try {
            withContext(Dispatchers.IO) {
                val u = java.net.URI(url).toURL()
                val conn = u.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Authorization", "Bearer ${currentToken}")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.outputStream.write(jsonBody.toByteArray())
                val code = conn.responseCode
                val body = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                }
                conn.disconnect()
                JSONObject().apply {
                    put("status", code)
                    put("body", body.ifBlank { "{}" })
                }.toString()
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", e.message?.take(100) ?: "Unknown error")
            }.toString()
        }
    }
}