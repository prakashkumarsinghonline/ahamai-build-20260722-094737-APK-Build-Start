package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URI

/**
 * Universal connector discovery — how Claude / Grok surface connectors:
 *
 * | Source | How Claude/Grok use it | How we use it |
 * |--------|------------------------|---------------|
 * | Built-in catalog | First-party OAuth apps | [ConnectorsManager.BUILTIN] |
 * | Connectors Directory | Browse/search curated MCP | Official MCP Registry + Smithery |
 * | Custom / BYO MCP | Paste remote server URL | [ConnectorsManager.createCustomConnector] |
 *
 * Search merges:
 * 1. Local (built-in + already-added custom)
 * 2. Official MCP Registry — registry.modelcontextprotocol.io
 * 3. Smithery directory — registry.smithery.ai (remote-hosted MCPs)
 *
 * Adding a web result creates a custom connector pointing at its remote URL,
 * then the existing Connect flow + `custom_*_request` tools make it work.
 */
object ConnectorCatalog {

    data class CatalogHit(
        val id: String,
        val name: String,
        val description: String,
        val serverUrl: String,
        val source: Source,
        val category: String = "Web",
        val faviconUrl: String = "",
        val homepage: String = "",
        val authHint: String = "bearer",
        /** true if already in app catalog (built-in or custom) */
        val alreadyInstalled: Boolean = false,
        val localConnectorId: String? = null
    )

    enum class Source { LOCAL, MCP_REGISTRY, SMITHERY, CURATED }

    /**
     * Universal search. Empty query → popular curated + local featured.
     * Aliases: "google workspace" expands to gmail/calendar/drive/docs/sheets/…
     */
    suspend fun search(query: String, limit: Int = 30): List<CatalogHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        val queries = expandQuery(q)
        coroutineScope {
            val local = async { searchLocal(q) }
            val registry = async {
                if (q.length >= 2) {
                    queries.flatMap { searchOfficialRegistry(it, limit = 10) }.distinctBy { it.serverUrl }
                } else emptyList()
            }
            val smithery = async {
                if (q.length >= 2) {
                    queries.flatMap { searchSmithery(it, limit = 10) }.distinctBy { it.serverUrl }
                } else emptyList()
            }
            val curated = async {
                if (q.isEmpty()) curatedPopular()
                else curatedPopular().filter { hit ->
                    queries.any { term ->
                        hit.name.contains(term, true) ||
                            hit.description.contains(term, true) ||
                            hit.category.contains(term, true) ||
                            hit.id.contains(term.replace(" ", ""), true)
                    }
                }
            }

            mergeHits(
                local = local.await(),
                web = curated.await() + registry.await() + smithery.await(),
                limit = limit
            )
        }
    }

    /** Expand user phrases so "google workspace" finds Gmail/Drive/Calendar/etc. */
    private fun expandQuery(q: String): List<String> {
        if (q.isBlank()) return emptyList()
        val lower = q.lowercase()
        val extras = when {
            (lower.contains("workspace") && lower.contains("google")) ||
                lower == "gws" || lower == "gsuite" || lower == "g suite" ->
                listOf(
                    "gmail", "google calendar", "google drive", "google docs",
                    "google sheets", "google slides", "google tasks", "workspace"
                )
            lower == "google" ->
                listOf("gmail", "google calendar", "google drive", "google docs", "google sheets")
            else -> emptyList()
        }
        return (listOf(q) + extras).distinct()
    }

    // ── Local (built-in + user's custom) ─────────────────────────────

    private fun searchLocal(q: String): List<CatalogHit> {
        val terms = expandQuery(q).ifEmpty { listOf(q) }
        return ConnectorsManager.ALL.map { def ->
            CatalogHit(
                id = "local:${def.id}",
                name = def.name,
                description = def.description,
                serverUrl = def.serverUrl.ifBlank { def.authUrl },
                source = Source.LOCAL,
                category = if (def.isCustom) "My Connectors" else "Built-in",
                faviconUrl = def.faviconUrl,
                homepage = def.authUrl,
                authHint = def.authType.ifBlank { "bearer" },
                alreadyInstalled = true,
                localConnectorId = def.id
            )
        }.filter { hit ->
            q.isEmpty() || terms.any { term ->
                term.isBlank() ||
                    hit.name.contains(term, true) ||
                    hit.description.contains(term, true) ||
                    hit.category.contains(term, true) ||
                    hit.localConnectorId.orEmpty().contains(term.replace(" ", ""), true) ||
                    hit.serverUrl.contains(term, true)
            }
        }
    }

    // ── Official MCP Registry ────────────────────────────────────────

    private fun searchOfficialRegistry(q: String, limit: Int): List<CatalogHit> {
        return try {
            val enc = URLEncoder.encode(q, "UTF-8")
            val url = "https://registry.modelcontextprotocol.io/v0/servers?search=$enc&limit=$limit"
            val raw = httpGet(url) ?: return emptyList()
            val root = JSONObject(raw)
            val servers = root.optJSONArray("servers") ?: return emptyList()
            val out = ArrayList<CatalogHit>()
            val seen = HashSet<String>()
            for (i in 0 until servers.length()) {
                val wrap = servers.optJSONObject(i) ?: continue
                val server = wrap.optJSONObject("server") ?: continue
                val meta = wrap.optJSONObject("_meta")
                    ?.optJSONObject("io.modelcontextprotocol.registry/official")
                if (meta != null && meta.optString("status") == "deleted") continue
                // Prefer latest only when flag present
                if (meta != null && meta.has("isLatest") && !meta.optBoolean("isLatest", true)) continue

                val name = server.optString("title").ifBlank {
                    server.optString("name").substringAfterLast('/')
                }
                val description = server.optString("description")
                val qualified = server.optString("name")
                val remotes = server.optJSONArray("remotes")
                val remoteUrl = firstRemoteUrl(remotes) ?: continue
                if (!seen.add(remoteUrl)) continue

                val domain = ConnectorsManager.domainOf(remoteUrl)
                out.add(
                    CatalogHit(
                        id = "mcp:$qualified",
                        name = humanize(name),
                        description = description.ifBlank { "MCP server from official registry" },
                        serverUrl = remoteUrl,
                        source = Source.MCP_REGISTRY,
                        category = "MCP Registry",
                        faviconUrl = if (domain.isNotBlank())
                            "https://www.google.com/s2/favicons?domain=$domain&sz=64" else "",
                        homepage = server.optJSONObject("repository")?.optString("url")
                            ?: server.optString("websiteUrl"),
                        authHint = if (hasAuthHeaders(remotes)) "bearer" else "none"
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun firstRemoteUrl(remotes: JSONArray?): String? {
        if (remotes == null) return null
        for (i in 0 until remotes.length()) {
            val r = remotes.optJSONObject(i) ?: continue
            val u = r.optString("url")
            if (u.startsWith("http")) return u
        }
        return null
    }

    private fun hasAuthHeaders(remotes: JSONArray?): Boolean {
        if (remotes == null) return false
        for (i in 0 until remotes.length()) {
            val r = remotes.optJSONObject(i) ?: continue
            val headers = r.optJSONArray("headers")
            if (headers != null && headers.length() > 0) return true
        }
        return false
    }

    // ── Smithery ─────────────────────────────────────────────────────

    private fun searchSmithery(q: String, limit: Int): List<CatalogHit> {
        return try {
            val enc = URLEncoder.encode(q, "UTF-8")
            val url = "https://registry.smithery.ai/servers?q=$enc&pageSize=$limit"
            val raw = httpGet(url) ?: return emptyList()
            val root = JSONObject(raw)
            val servers = root.optJSONArray("servers") ?: return emptyList()
            val out = ArrayList<CatalogHit>()
            for (i in 0 until servers.length()) {
                val s = servers.optJSONObject(i) ?: continue
                val remote = s.optBoolean("remote", false) || s.optBoolean("isDeployed", false)
                val qn = s.optString("qualifiedName").ifBlank { s.optString("id") }
                val display = s.optString("displayName").ifBlank { qn }
                val description = s.optString("description")
                val icon = s.optString("iconUrl")
                val homepage = s.optString("homepage")
                // Prefer deployed remote URL; fall back to smithery hosted path
                var serverUrl = s.optString("deploymentUrl")
                if (serverUrl.isBlank()) {
                    val conns = s.optJSONArray("connections")
                    if (conns != null) {
                        for (j in 0 until conns.length()) {
                            val c = conns.optJSONObject(j) ?: continue
                            val u = c.optString("deploymentUrl").ifBlank { c.optString("url") }
                            if (u.startsWith("http")) {
                                serverUrl = u
                                break
                            }
                        }
                    }
                }
                if (serverUrl.isBlank() && remote) {
                    // Smithery hosted pattern used widely in the wild
                    serverUrl = "https://server.smithery.ai/${qn.trimStart('@')}/mcp"
                }
                if (serverUrl.isBlank()) continue

                out.add(
                    CatalogHit(
                        id = "smithery:$qn",
                        name = display,
                        description = description.ifBlank { "Remote MCP via Smithery" },
                        serverUrl = serverUrl,
                        source = Source.SMITHERY,
                        category = "Smithery",
                        faviconUrl = icon.ifBlank {
                            val d = ConnectorsManager.domainOf(homepage.ifBlank { serverUrl })
                            if (d.isNotBlank()) "https://www.google.com/s2/favicons?domain=$d&sz=64" else ""
                        },
                        homepage = homepage,
                        authHint = "bearer"
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── Curated popular (shown when search empty — like Featured directories) ─

    private fun curatedPopular(): List<CatalogHit> = listOf(
        // Google Workspace suite (remote MCP — Smithery run.tools)
        hit(
            "gmail", "Gmail",
            "Google Workspace · send, draft, labels, search mail",
            "https://gmail.run.tools", "Google Workspace", "gmail.com"
        ),
        hit(
            "google-calendar", "Google Calendar",
            "Google Workspace · events, availability, meetings",
            "https://googlecalendar.run.tools", "Google Workspace", "calendar.google.com"
        ),
        hit(
            "google-drive", "Google Drive",
            "Google Workspace · files, folders, sharing",
            "https://googledrive.run.tools", "Google Workspace", "drive.google.com"
        ),
        hit(
            "google-docs", "Google Docs",
            "Google Workspace · create and edit documents",
            "https://googledocs.run.tools", "Google Workspace", "docs.google.com"
        ),
        hit(
            "google-sheets", "Google Sheets",
            "Google Workspace · spreadsheets, formulas, ranges",
            "https://googlesheets.run.tools", "Google Workspace", "sheets.google.com"
        ),
        hit(
            "google-slides", "Google Slides",
            "Google Workspace · presentations and decks",
            "https://googleslides.run.tools", "Google Workspace", "slides.google.com"
        ),
        hit(
            "google-tasks", "Google Tasks",
            "Google Workspace · to-dos integrated with Gmail/Calendar",
            "https://googletasks.run.tools", "Google Workspace", "tasks.google.com"
        ),
        hit(
            "workspace-developer", "Google Workspace Developer Tools",
            "Official Google Workspace developer MCP tools",
            "https://workspace-developer.goog/mcp", "Google Workspace", "developers.google.com"
        ),
        // Other popular
        hit("notion", "Notion", "Notes, databases, and pages", "https://server.smithery.ai/@smithery/notion/mcp", "Productivity"),
        hit("slack", "Slack", "Channels, messages, and team communication", "https://server.smithery.ai/slackbot/mcp", "Communication"),
        hit("linear", "Linear", "Issues, projects, and product workflows", "https://mcp.linear.app/sse", "Productivity"),
        hit("github", "GitHub MCP", "Repos, PRs, issues, and Actions via remote MCP", "https://github.run.tools", "Developer"),
        hit("jira", "Jira", "Atlassian issues and projects", "https://mcp.atlassian.com/v1/sse", "Productivity"),
        hit("stripe", "Stripe", "Payments, customers, and invoices", "https://mcp.stripe.com", "Business"),
        hit("supabase", "Supabase MCP", "Postgres, auth, and storage via MCP", "https://mcp.supabase.com/mcp", "Developer"),
        hit("cloudflare", "Cloudflare MCP", "Workers, DNS, and edge tools", "https://bindings.mcp.cloudflare.com/mcp", "Developer"),
        hit("sentry", "Sentry", "Errors and performance monitoring", "https://mcp.sentry.dev/mcp", "Developer"),
        hit("figma", "Figma", "Design files and comments", "https://mcp.figma.com/mcp", "Design"),
        hit("hubspot", "HubSpot", "CRM contacts and deals", "https://mcp.hubspot.com", "Business"),
        hit("asana", "Asana", "Tasks and project management", "https://mcp.asana.com/sse", "Productivity"),
    )

    private fun hit(
        id: String,
        name: String,
        desc: String,
        url: String,
        category: String,
        faviconDomain: String = ""
    ): CatalogHit {
        val domain = faviconDomain.ifBlank {
            ConnectorsManager.domainOf(url).ifBlank {
                name.lowercase().replace(" ", "") + ".com"
            }
        }
        return CatalogHit(
            id = "curated:$id",
            name = name,
            description = desc,
            serverUrl = url,
            source = Source.CURATED,
            category = category,
            faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64",
            homepage = "https://$domain",
            authHint = "bearer"
        )
    }

    // ── Merge / install helpers ──────────────────────────────────────

    private fun mergeHits(
        local: List<CatalogHit>,
        web: List<CatalogHit>,
        limit: Int
    ): List<CatalogHit> {
        val out = ArrayList<CatalogHit>()
        // Connected first, then other local, then web
        val localSorted = local.sortedByDescending {
            it.localConnectorId?.let { id -> ConnectorsManager.isConnected(id) } == true
        }
        out.addAll(localSorted)

        val seenUrl = local.map { normalizeUrl(it.serverUrl) }.filter { it.isNotBlank() }.toMutableSet()
        val seenName = local.map { it.name.lowercase() }.toMutableSet()
        val seenId = local.mapNotNull { it.localConnectorId }.toMutableSet()

        for (h in web) {
            val nu = normalizeUrl(h.serverUrl)
            val nn = h.name.lowercase()
            // Same URL or exact name as a local built-in → skip duplicate row
            if ((nu.isNotBlank() && nu in seenUrl) || nn in seenName) continue

            val customMatch = ConnectorsManager.customConnectors().find {
                normalizeUrl(it.serverUrl) == nu || it.name.equals(h.name, true)
            }
            val localMatch = ConnectorsManager.BUILTIN.find {
                it.name.equals(h.name, true) || normalizeUrl(it.authUrl) == nu
            }

            if (customMatch != null) {
                if (customMatch.id in seenId) continue
                seenId.add(customMatch.id)
                out.add(
                    h.copy(
                        alreadyInstalled = true,
                        localConnectorId = customMatch.id
                    )
                )
            } else if (localMatch != null) {
                if (localMatch.id in seenId) continue
                seenId.add(localMatch.id)
                out.add(
                    h.copy(
                        alreadyInstalled = true,
                        localConnectorId = localMatch.id,
                        source = Source.LOCAL,
                        category = "Built-in"
                    )
                )
            } else {
                if (nu.isNotBlank()) seenUrl.add(nu)
                seenName.add(nn)
                out.add(h)
            }
            if (out.size >= limit + local.size) break
        }
        return out.take(limit + local.size)
    }

    /**
     * Install a web catalog hit as a custom connector (does not auto-verify).
     * Returns the new/existing connector id for the Connect sheet.
     */
    fun installFromCatalog(hit: CatalogHit): String {
        hit.localConnectorId?.let { return it }
        // Already have same URL?
        ConnectorsManager.customConnectors().find {
            normalizeUrl(it.serverUrl) == normalizeUrl(hit.serverUrl)
        }?.let { return it.id }

        val created = ConnectorsManager.createCustomConnector(
            name = hit.name,
            description = hit.description,
            serverUrl = hit.serverUrl,
            authType = hit.authHint.ifBlank { "bearer" },
            faviconDomain = ConnectorsManager.domainOf(hit.serverUrl)
                .ifBlank { ConnectorsManager.domainOf(hit.homepage) }
        )
        return created.id
    }

    private fun normalizeUrl(u: String): String =
        u.trim().lowercase().removeSuffix("/").removeSuffix("/mcp").removeSuffix("/sse")

    private fun humanize(raw: String): String =
        raw.replace(Regex("[-_/]+"), " ")
            .split(" ")
            .joinToString(" ") { w ->
                w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            .trim()
            .ifBlank { raw }

    private fun httpGet(url: String): String? {
        return try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 12000
            conn.readTimeout = 15000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "AhamAI-ConnectorCatalog/1.0")
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.readText()
            conn.disconnect()
            if (code in 200..299) text else null
        } catch (_: Exception) {
            null
        }
    }

    /** Lightweight health check used by the UI to show directory status. */
    suspend fun directoryHealth(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        mapOf(
            "mcp_registry" to (httpGet("https://registry.modelcontextprotocol.io/v0/servers?search=test&limit=1") != null),
            "smithery" to (httpGet("https://registry.smithery.ai/servers?q=test&pageSize=1") != null)
        )
    }
}
