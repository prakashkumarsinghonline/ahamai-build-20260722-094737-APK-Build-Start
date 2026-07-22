package com.ahamai.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Unified Connectors manager — built-in services (Vercel, Render, …) plus
 * **user custom connectors** (Grok/ChatGPT-style: name + remote server URL + auth).
 *
 * Custom connectors follow the same idea as MCP custom connectors:
 * user pastes a public API/MCP base URL + optional bearer/API key; when
 * connected, the agent gets HTTP tools (`custom_<id>_request`) to call that API.
 *
 * Per-connector state is persisted in SharedPreferences.
 */
object ConnectorsManager {

    private const val PREFS_NAME = "ahamai_connectors"
    private const val KEY_PREFIX = "connector_"
    private const val KEY_CUSTOM = "custom_connectors_json"

    /** Stable connector IDs — never rename (used as SharedPreferences keys). */
    const val VERCEL = "vercel"
    const val RENDER = "render"
    const val CLOUDFLARE = "cloudflare"
    const val NETLIFY = "netlify"
    const val SUPABASE = "supabase"
    const val RAILWAY = "railway"
    const val GITHUB = "github"

    data class ConnectorDef(
        val id: String,
        val name: String,
        val description: String,
        val tagline: String,
        val emoji: String,
        val brandColor: Long,         // ARGB
        val faviconUrl: String,       // Google favicon URL for this connector
        val tokenPlaceholder: String,
        val tokenHint: String,
        val authUrl: String,
        val suggestedPrompts: List<String>,
        /** User-defined custom connector (remote API / MCP base URL). */
        val isCustom: Boolean = false,
        /** Base URL for custom connectors (MCP or REST). */
        val serverUrl: String = "",
        /** none | bearer | api_key */
        val authType: String = "bearer"
    )

    data class ConnectorState(
        val enabled: Boolean = false,
        val token: String = "",
        val verified: Boolean = false
    )

    /**
     * User-added custom connector (like Grok "New Connector → Custom"):
     * name, description, public server URL, auth.
     */
    data class CustomConnector(
        val id: String,
        val name: String,
        val description: String,
        val serverUrl: String,
        val authType: String = "bearer", // none | bearer | api_key
        val faviconDomain: String = "",
        val brandColor: Long = 0xFF2563EB
    ) {
        fun toDef(): ConnectorDef {
            val domain = faviconDomain.ifBlank { domainOf(serverUrl) }
            return ConnectorDef(
                id = id,
                name = name,
                description = description.ifBlank { "Custom connector · $serverUrl" },
                tagline = "Custom · $serverUrl",
                emoji = "⚡",
                brandColor = brandColor,
                faviconUrl = if (domain.isNotBlank())
                    "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                else "https://www.google.com/s2/favicons?domain=example.com&sz=64",
                tokenPlaceholder = when (authType) {
                    "none" -> "(no token needed)"
                    "api_key" -> "API key…"
                    else -> "Bearer token…"
                },
                tokenHint = when (authType) {
                    "none" -> "No auth configured for this connector"
                    "api_key" -> "Paste your API key (sent as X-Api-Key)"
                    else -> "Paste a bearer / access token for this API"
                },
                authUrl = serverUrl,
                suggestedPrompts = listOf(
                    "Call my $name API and summarize the response",
                    "GET the root of my $name connector",
                    "Show what $name can do"
                ),
                isCustom = true,
                serverUrl = serverUrl,
                authType = authType
            )
        }
    }

    /** Built-in catalog only. */
    val BUILTIN: List<ConnectorDef> = listOf(
        ConnectorDef(
            id = VERCEL,
            name = "Vercel",
            description = "Deployments, projects, domains & logs.",
            tagline = "Ship web apps to the edge in seconds.",
            emoji = "\u25B2",                                // ▲
            brandColor = 0xFF000000,
            faviconUrl = "https://www.google.com/s2/favicons?domain=vercel.com&sz=64",
            tokenPlaceholder = "vcr_...",
            tokenHint = "Create a token at vercel.com/account/tokens",
            authUrl = "https://vercel.com/account/tokens",
            suggestedPrompts = listOf(
                "List my Vercel deployments",
                "Show logs for the latest deployment",
                "Trigger a new production deployment"
            )
        ),
        ConnectorDef(
            id = RENDER,
            name = "Render",
            description = "Services, deploys & env vars.",
            tagline = "Host web services, workers & databases.",
            emoji = "R",
            brandColor = 0xFF46E3B7,
            faviconUrl = "https://www.google.com/s2/favicons?domain=render.com&sz=64",
            tokenPlaceholder = "rnd_...",
            tokenHint = "Create an API key at dashboard.render.com/u/settings/api-keys",
            authUrl = "https://dashboard.render.com/u/settings/api-keys",
            suggestedPrompts = listOf(
                "List my Render services",
                "Trigger a deploy on the latest service",
                "Show environment variables for my service"
            )
        ),
        ConnectorDef(
            id = CLOUDFLARE,
            name = "Cloudflare",
            description = "Workers, zones, KV & account.",
            tagline = "Edge compute, DNS & storage on Cloudflare.",
            emoji = "\u2601",                                // ☁
            brandColor = 0xFFF38020,
            faviconUrl = "https://www.google.com/s2/favicons?domain=cloudflare.com&sz=64",
            tokenPlaceholder = "CF API token",
            tokenHint = "Create an API token at dash.cloudflare.com/profile/api-tokens",
            authUrl = "https://dash.cloudflare.com/profile/api-tokens",
            suggestedPrompts = listOf(
                "List my Cloudflare Workers",
                "Show zones in my Cloudflare account",
                "List KV namespaces"
            )
        ),
        ConnectorDef(
            id = NETLIFY,
            name = "Netlify",
            description = "Sites, deploys & env vars.",
            tagline = "Deploy static sites & Jamstack apps.",
            emoji = "N",
            brandColor = 0xFF32AD6C,
            faviconUrl = "https://www.google.com/s2/favicons?domain=netlify.com&sz=64",
            tokenPlaceholder = "nfp_...",
            tokenHint = "Create a personal access token at app.netlify.com/user/applications",
            authUrl = "https://app.netlify.com/user/applications#personal-access-tokens",
            suggestedPrompts = listOf(
                "List my Netlify sites",
                "Show the latest deploys for a site",
                "List environment variables for a site"
            )
        ),
        ConnectorDef(
            id = SUPABASE,
            name = "Supabase",
            description = "Projects, tables & SQL queries.",
            tagline = "Postgres, auth & storage in a single backend.",
            emoji = "\u26A1",                                // ⚡
            brandColor = 0xFF3ECF8E,
            faviconUrl = "https://www.google.com/s2/favicons?domain=supabase.com&sz=64",
            tokenPlaceholder = "sbp_...",
            tokenHint = "Create an access token at supabase.com/dashboard/account/tokens",
            authUrl = "https://supabase.com/dashboard/account/tokens",
            suggestedPrompts = listOf(
                "List my Supabase projects",
                "Get the API keys for a project",
                "Run a read-only SQL query"
            )
        ),
        ConnectorDef(
            id = RAILWAY,
            name = "Railway",
            description = "Projects, services & deployments.",
            tagline = "Deploy apps & databases from a dashboard.",
            emoji = "\u25C9",                                // ◉
            brandColor = 0xFF9859F7,
            faviconUrl = "https://www.google.com/s2/favicons?domain=railway.com&sz=64",
            tokenPlaceholder = "railway token",
            tokenHint = "Create a team token at railway.com/account/tokens",
            authUrl = "https://railway.com/account/tokens",
            suggestedPrompts = listOf(
                "List my Railway projects",
                "Show recent deployments",
                "List services in a project"
            )
        ),
        ConnectorDef(
            id = GITHUB,
            name = "GitHub",
            description = "Repos, branches & issues.",
            tagline = "Connect your code & ship to GitHub.",
            emoji = "\u25C8",                                // ◈
            brandColor = 0xFF24292F,
            faviconUrl = "https://www.google.com/s2/favicons?domain=github.com&sz=64",
            tokenPlaceholder = "ghp_...",
            tokenHint = "Create a token at github.com/settings/tokens (repo scope)",
            authUrl = "https://github.com/settings/tokens",
            suggestedPrompts = listOf(
                "List my GitHub repositories",
                "Show recent commits on a branch",
                "List open issues in a repo"
            )
        )
    )

    /**
     * Full catalog = built-ins + user custom connectors (Grok-style).
     * UI and agent iterate this list.
     */
    val ALL: List<ConnectorDef>
        get() = BUILTIN + customConnectors().map { it.toDef() }

    fun byId(id: String): ConnectorDef =
        ALL.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Unknown connector: $id")

    fun findById(id: String): ConnectorDef? = ALL.firstOrNull { it.id == id }

    fun isCustomId(id: String): Boolean = id.startsWith("custom_") || findById(id)?.isCustom == true

    // ─────────── Custom connectors (Add Connector) ───────────

    fun customConnectors(): List<CustomConnector> {
        val raw = prefs?.getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                CustomConnector(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    description = o.optString("description"),
                    serverUrl = o.optString("serverUrl"),
                    authType = o.optString("authType", "bearer"),
                    faviconDomain = o.optString("faviconDomain"),
                    brandColor = o.optLong("brandColor", 0xFF2563EB)
                ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.serverUrl.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveCustomConnector(c: CustomConnector) {
        val list = customConnectors().toMutableList()
        val idx = list.indexOfFirst { it.id == c.id }
        if (idx >= 0) list[idx] = c else list.add(0, c)
        persistCustom(list)
    }

    fun deleteCustomConnector(id: String) {
        persistCustom(customConnectors().filter { it.id != id })
        disconnect(id)
    }

    /**
     * Create a custom connector (user implements / hosts the server).
     * [serverUrl] should be a public HTTPS base (REST or MCP endpoint).
     */
    fun createCustomConnector(
        name: String,
        description: String,
        serverUrl: String,
        authType: String = "bearer",
        faviconDomain: String = ""
    ): CustomConnector {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val slug = name.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "api" }
            .take(24)
        val id = "custom_${slug}_${System.currentTimeMillis().toString(36).takeLast(4)}"
        val c = CustomConnector(
            id = id,
            name = name.trim(),
            description = description.trim().ifBlank { "Custom connector at $cleanUrl" },
            serverUrl = cleanUrl,
            authType = when (authType.lowercase()) {
                "none", "api_key" -> authType.lowercase()
                else -> "bearer"
            },
            faviconDomain = faviconDomain.ifBlank { domainOf(cleanUrl) }
        )
        saveCustomConnector(c)
        return c
    }

    private fun persistCustom(list: List<CustomConnector>) {
        val arr = org.json.JSONArray()
        list.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("description", c.description)
                put("serverUrl", c.serverUrl)
                put("authType", c.authType)
                put("faviconDomain", c.faviconDomain)
                put("brandColor", c.brandColor)
            })
        }
        prefs?.edit()?.putString(KEY_CUSTOM, arr.toString())?.apply()
    }

    fun domainOf(url: String): String = try {
        val u = java.net.URI(url)
        u.host?.removePrefix("www.") ?: ""
    } catch (_: Exception) {
        url.removePrefix("https://").removePrefix("http://").substringBefore("/").substringBefore(":")
    }

    // ─────────────── Persistence ───────────────

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Backward-compat: migrate the old Vercel-only MCP prefs into the new keyspace.
        val legacy = context.getSharedPreferences("ahamai_mcp", Context.MODE_PRIVATE)
        val legacyRaw = legacy.getString("vercel_connection", null)
        if (legacyRaw != null && prefs?.getString("${KEY_PREFIX}$VERCEL", null) == null) {
            prefs?.edit()?.putString("${KEY_PREFIX}$VERCEL", legacyRaw)?.apply()
        }
        // Also seed GitHub token from PreferencesManager if present so the agent
        // surface treats GitHub as "connected" without re-auth.
        val gh = context.getSharedPreferences("ahamai_prefs", Context.MODE_PRIVATE)
        val ghToken = gh.getString("github_token", null)
        if (!ghToken.isNullOrBlank() && prefs?.getString("${KEY_PREFIX}$GITHUB", null) == null) {
            val obj = JSONObject().apply {
                put("enabled", true)
                put("token", ghToken)
                put("verified", true)
            }
            prefs?.edit()?.putString("${KEY_PREFIX}$GITHUB", obj.toString())?.apply()
        }
    }

    fun getState(id: String): ConnectorState {
        val raw = prefs?.getString("${KEY_PREFIX}$id", null) ?: return ConnectorState()
        return try {
            val o = JSONObject(raw)
            ConnectorState(
                enabled = o.optBoolean("enabled", false),
                token = o.optString("token", ""),
                verified = o.optBoolean("verified", false)
            )
        } catch (_: Exception) { ConnectorState() }
    }

    fun saveState(id: String, state: ConnectorState) {
        val obj = JSONObject().apply {
            put("enabled", state.enabled)
            put("token", state.token)
            put("verified", state.verified)
        }
        prefs?.edit()?.putString("${KEY_PREFIX}$id", obj.toString())?.apply()
    }

    fun setEnabled(id: String, enabled: Boolean) =
        saveState(id, getState(id).copy(enabled = enabled))

    fun setToken(id: String, token: String) =
        saveState(id, getState(id).copy(token = token.trim(), verified = false))

    fun setVerified(id: String, verified: Boolean) =
        saveState(id, getState(id).copy(verified = verified))

    fun disconnect(id: String) = saveState(id, ConnectorState())

    fun isConnected(id: String): Boolean {
        val s = getState(id)
        if (!s.enabled || !s.verified) return false
        val def = findById(id)
        // Custom connectors with authType=none are connected without a token.
        if (def?.isCustom == true && def.authType == "none") return true
        return s.token.isNotBlank()
    }

    /** All currently-connected connectors (in catalog order). */
    fun connected(): List<ConnectorDef> = ALL.filter { isConnected(it.id) }

    // ─────────────── Verification ───────────────

    /**
     * Verify the user's token for the given connector by hitting a cheap
     * authenticated endpoint. Returns null on success, an error string otherwise.
     */
    suspend fun verify(id: String, token: String): String? = withContext(Dispatchers.IO) {
        try {
            val def = findById(id)
            val code: Int = when {
                def?.isCustom == true -> customVerify(def, token)
                id == VERCEL      -> httpGet("https://api.vercel.com/v2/user", "Bearer $token")
                id == RENDER      -> httpGet("https://api.render.com/v1/services?limit=1", "Bearer $token")
                id == CLOUDFLARE  -> httpGet("https://api.cloudflare.com/client/v4/user/tokens/verify", "Bearer $token")
                id == NETLIFY     -> httpGet("https://api.netlify.com/api/v1/user", "Bearer $token")
                id == SUPABASE    -> httpGet("https://api.supabase.com/v1/projects", "Bearer $token")
                id == RAILWAY     -> railwayVerify(token)
                // GitHub: classic PATs used "token ", fine-grained/OAuth need "Bearer "
                id == GITHUB      -> githubVerify(token)
                else              -> 200
            }
            // For custom: any reachable response (incl. 401/404) proves the host exists;
            // 2xx or 401 with token means "URL ok". Auth=none only needs host reachability.
            if (def?.isCustom == true) {
                when {
                    code in 200..499 -> null
                    code == -1 -> "Server unreachable (network error)"
                    else -> "Server unreachable (HTTP $code)"
                }
            } else {
                if (code in 200..299) null else "Verification failed (HTTP $code). Check the token and try again."
            }
        } catch (e: Exception) {
            "Connection error: ${e.message?.take(60)}"
        }
    }

    private fun customVerify(def: ConnectorDef, token: String): Int {
        val auth = when (def.authType) {
            "none" -> null
            "api_key" -> if (token.isBlank()) null else "ApiKey $token"
            else -> if (token.isBlank()) null else "Bearer $token"
        }
        // Prefer GET; fall back to HEAD if GET fails hard
        return try {
            if (auth != null && def.authType == "api_key") {
                httpGetWithHeader(def.serverUrl, "X-Api-Key", token)
            } else if (auth != null) {
                httpGet(def.serverUrl, auth)
            } else {
                httpGetNoAuth(def.serverUrl)
            }
        } catch (_: Exception) {
            -1
        }
    }

    private fun httpGet(url: String, authHeader: String): Int {
        return try {
            val u = java.net.URI(url).toURL()
            val conn = u.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", authHeader)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "AhamAI-Connectors/1.0")
            // GitHub requires a UA
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val code = conn.responseCode
            conn.disconnect()
            code
        } catch (_: Exception) {
            -1
        }
    }

    /** GitHub accepts Bearer (fine-grained / OAuth) and legacy "token " classic PATs. */
    private fun githubVerify(token: String): Int {
        val t = token.trim()
        if (t.isBlank()) return 401
        val bearer = httpGet("https://api.github.com/user", "Bearer $t")
        if (bearer in 200..299) return bearer
        // Fallback for older classic PATs
        return httpGet("https://api.github.com/user", "token $t")
    }

    /** Railway uses a GraphQL endpoint, so verification is a tiny introspection query. */
    private fun railwayVerify(token: String): Int {
        val u = java.net.URI("https://backboard.railway.app/graphql").toURL()
        val conn = u.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        conn.outputStream.write("""{"query":"{ me { id email } }"}""".toByteArray())
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    // ─────────────── Tool aggregation ───────────────

    /**
     * Build the merged JSON-tools array for every connected connector.
     * Called by StructuredTools.buildToolsArray().
     */
    fun buildAllToolsJson(): org.json.JSONArray {
        val arr = org.json.JSONArray()
        if (isConnected(VERCEL))     for (j in 0 until VercelMCPTools.buildToolsJson().length())     arr.put(VercelMCPTools.buildToolsJson().getJSONObject(j))
        if (isConnected(RENDER))     for (j in 0 until RenderClient.buildToolsJson().length())        arr.put(RenderClient.buildToolsJson().getJSONObject(j))
        if (isConnected(CLOUDFLARE)) for (j in 0 until CloudflareClient.buildToolsJson().length())    arr.put(CloudflareClient.buildToolsJson().getJSONObject(j))
        if (isConnected(NETLIFY))    for (j in 0 until NetlifyClient.buildToolsJson().length())       arr.put(NetlifyClient.buildToolsJson().getJSONObject(j))
        if (isConnected(SUPABASE))   for (j in 0 until SupabaseClient.buildToolsJson().length())      arr.put(SupabaseClient.buildToolsJson().getJSONObject(j))
        if (isConnected(RAILWAY))    for (j in 0 until RailwayClient.buildToolsJson().length())       arr.put(RailwayClient.buildToolsJson().getJSONObject(j))
        // Custom connectors — generic HTTP tools for the agent
        for (def in ALL.filter { it.isCustom && isConnected(it.id) }) {
            arr.put(customToolDef(def))
        }
        return arr
    }

    private fun customToolDef(def: ConnectorDef): JSONObject {
        val safe = def.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val name = "custom_${safe}_request"
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put(
                    "description",
                    "Call custom connector «${def.name}» (base ${def.serverUrl}). " +
                        "Use for any API request to this user's remote service / MCP-style endpoint. " +
                        "${def.description}"
                )
                put(
                    "parameters",
                    JSONObject(
                        """{"type":"object","properties":{
                          "method":{"type":"string","description":"HTTP method: GET, POST, PUT, PATCH, DELETE"},
                          "path":{"type":"string","description":"Path relative to base URL (e.g. /v1/items) or absolute https URL under same host"},
                          "query":{"type":"string","description":"Optional query string without leading ?"},
                          "body":{"type":"string","description":"Optional raw JSON/text body for POST/PUT/PATCH"}
                        },"required":["method","path"]}"""
                    )
                )
            })
        }
    }

    /**
     * Build the merged prompt-description block describing every connected
     * connector to the agent. Called by CodeAgent.buildSystemPrompt().
     */
    fun buildAllPromptDescriptions(): String {
        val sb = StringBuilder()
        if (isConnected(VERCEL))     sb.append(VercelMCPTools.buildPromptDescription()).append('\n')
        if (isConnected(RENDER))     sb.append(RenderClient.buildPromptDescription()).append('\n')
        if (isConnected(CLOUDFLARE)) sb.append(CloudflareClient.buildPromptDescription()).append('\n')
        if (isConnected(NETLIFY))    sb.append(NetlifyClient.buildPromptDescription()).append('\n')
        if (isConnected(SUPABASE))   sb.append(SupabaseClient.buildPromptDescription()).append('\n')
        if (isConnected(RAILWAY))    sb.append(RailwayClient.buildPromptDescription()).append('\n')
        for (def in ALL.filter { it.isCustom && isConnected(it.id) }) {
            val safe = def.id.replace(Regex("[^a-zA-Z0-9_]"), "_")
            sb.append(
                "CUSTOM CONNECTOR «${def.name}» (id=${def.id}): base URL ${def.serverUrl}. " +
                    "Call tool custom_${safe}_request with method + path (+ optional query/body). " +
                    "Auth is applied automatically. ${def.description}\n"
            )
        }
        return sb.toString().trim()
    }

    /**
     * Route a tool call to the right connector and execute it.
     * Tool names are prefixed (e.g. `render_list_services`).
     */
    suspend fun executeTool(toolName: String, args: Map<String, String>): String {
        return when {
            toolName.startsWith("vercel_")     -> VercelMCPTools.executeTool(toolName, args)
            toolName.startsWith("render_")     -> RenderClient.executeTool(toolName, args)
            toolName.startsWith("cloudflare_") -> CloudflareClient.executeTool(toolName, args)
            toolName.startsWith("netlify_")    -> NetlifyClient.executeTool(toolName, args)
            toolName.startsWith("supabase_")   -> SupabaseClient.executeTool(toolName, args)
            toolName.startsWith("railway_")    -> RailwayClient.executeTool(toolName, args)
            toolName.startsWith("custom_") && toolName.endsWith("_request") ->
                executeCustomRequest(toolName, args)
            else -> """{"error":"Unknown connector tool: $toolName"}"""
        }
    }

    private suspend fun executeCustomRequest(toolName: String, args: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            // custom_<id>_request — id may contain underscores
            val mid = toolName.removePrefix("custom_").removeSuffix("_request")
            val def = ALL.firstOrNull { it.isCustom && it.id.replace(Regex("[^a-zA-Z0-9_]"), "_") == mid }
                ?: ALL.firstOrNull { it.isCustom && it.id == mid }
                ?: return@withContext """{"error":"Unknown custom connector for tool $toolName"}"""
            if (!isConnected(def.id)) return@withContext """{"error":"Connector ${def.name} is not connected"}"""

            val method = (args["method"] ?: args["http_method"] ?: "GET").uppercase()
            val path = args["path"] ?: args["url"] ?: "/"
            val query = args["query"].orEmpty()
            val body = args["body"] ?: args["data"].orEmpty()
            val state = getState(def.id)

            val url = buildCustomUrl(def.serverUrl, path, query)
            try {
                val result = httpRequest(
                    method = method,
                    url = url,
                    authType = def.authType,
                    token = state.token,
                    body = body
                )
                result
            } catch (e: Exception) {
                """{"error":"Request failed: ${e.message?.take(120)}"}"""
            }
        }

    private fun buildCustomUrl(base: String, path: String, query: String): String {
        val p = path.trim()
        val url = when {
            p.startsWith("http://") || p.startsWith("https://") -> p
            p.startsWith("/") -> base.removeSuffix("/") + p
            else -> base.removeSuffix("/") + "/" + p
        }
        return if (query.isBlank()) url
        else if (url.contains("?")) "$url&${query.removePrefix("?")}"
        else "$url?${query.removePrefix("?")}"
    }

    private fun httpRequest(
        method: String,
        url: String,
        authType: String,
        token: String,
        body: String
    ): String {
        val u = java.net.URI(url).toURL()
        val conn = u.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = if (method == "PATCH") "POST" else method // some stacks need override
        if (method == "PATCH") conn.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("Accept", "application/json, text/plain, */*")
        when (authType) {
            "bearer" -> if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            "api_key" -> if (token.isNotBlank()) conn.setRequestProperty("X-Api-Key", token)
        }
        if (method in listOf("POST", "PUT", "PATCH") && body.isNotBlank()) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = conn.responseCode
        val stream = try {
            if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
        } catch (_: Exception) {
            null
        }
        val text = stream?.bufferedReader()?.readText()?.take(8000) ?: ""
        conn.disconnect()
        return """{"status":$code,"url":${JSONObject.quote(url)},"body":${JSONObject.quote(text)}}"""
    }

    private fun httpGetNoAuth(url: String): Int {
        val u = java.net.URI(url).toURL()
        val conn = u.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    private fun httpGetWithHeader(url: String, header: String, value: String): Int {
        val u = java.net.URI(url).toURL()
        val conn = u.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty(header, value)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 10000
        conn.readTimeout = 10000
        val code = conn.responseCode
        conn.disconnect()
        return code
    }

    /** True if the tool name belongs to any connector (used by CodeAgent dispatcher). */
    fun isConnectorTool(toolName: String): Boolean =
        toolName.startsWith("vercel_")     ||
        toolName.startsWith("render_")     ||
        toolName.startsWith("cloudflare_") ||
        toolName.startsWith("netlify_")    ||
        toolName.startsWith("supabase_")   ||
        toolName.startsWith("railway_")    ||
        (toolName.startsWith("custom_") && toolName.endsWith("_request"))
}
