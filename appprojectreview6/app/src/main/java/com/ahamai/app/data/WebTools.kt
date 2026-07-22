package com.ahamai.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Web tools the AI can use on demand:
 * - [search]: web search — Brave Search API (key rotation) with DuckDuckGo fallback
 * - [imageSearch]: image search — Brave Images API, returns inline-renderable markdown images
 * - [read]: fetch a page as clean readable text — Jina Reader first, raw HTML fallback
 * - [searchMulti] / [imageSearchMulti]: run MANY queries in PARALLEL (each uses a different
 * Brave key) so the AI is never throttled and there is no practical limit on searches.
 *
 * Everything is best-effort: on any failure a short message is returned instead of throwing,
 * so the calling agent loop stays robust.
 */
object WebTools {
 private val client = OkHttpClient.Builder()
 .connectTimeout(20, TimeUnit.SECONDS)
 .readTimeout(25, TimeUnit.SECONDS)
 .build()

 private const val UA =
 "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

 private const val BRAVE_WEB = "https://api.search.brave.com/res/v1/web/search"
 private const val BRAVE_IMAGES = "https://api.search.brave.com/res/v1/images/search"

 // Round-robin pointer across the available Brave keys so concurrent searches spread the load.
 private val keyCursor = AtomicInteger(0)

 // Keys that Brave rejected with 422 (invalid/expired) — dropped from rotation so we never
 // waste an attempt on them again this session.
 private val invalidKeys = java.util.Collections.synchronizedSet(mutableSetOf<String>())

 private fun braveKeys(): List<String> = RemoteConfigManager.braveKeys()

 /** Brave keys that are still considered valid (all config keys minus any that returned 422). */
 private fun validBraveKeys(): List<String> = braveKeys().filter { it !in invalidKeys }

 /** Picks the next VALID Brave key in round-robin order, or null if none are configured/valid. */
 private fun nextKey(): String? {
 val keys = validBraveKeys()
 if (keys.isEmpty()) return null
 val i = keyCursor.getAndIncrement()
 return keys[Math.floorMod(i, keys.size)]
 }

 private fun markBad(key: String) { invalidKeys.add(key) }

 /** Clear all invalid-key cache — call when admin saves new keys so fresh keys get a clean start. */
 fun clearInvalidKeys() { invalidKeys.clear(); keyCursor.set(0) }

 // ---------------------------------------------------------------------------------------
 // WEB SEARCH
 // ---------------------------------------------------------------------------------------

 fun search(query: String): String {
 if (query.isBlank()) return "No query."
 if (!RemoteConfigManager.webSearchEnabled) return "Web search is currently disabled by the administrator."
 // Try Brave first across several VALID keys (rotating, in case some are rate-limited),
 // then fall back to the key-free DuckDuckGo scraper.
 val keys = validBraveKeys()
 if (keys.isNotEmpty()) {
 val attempts = minOf(keys.size, 4)
 repeat(attempts) {
 val key = nextKey() ?: return@repeat
 val res = braveWebSearch(query, key)
 if (res != null) return res
 }
 }
 return duckDuckGoSearch(query)
 }

 private fun braveWebSearch(query: String, key: String): String? {
 return try {
 val url = "$BRAVE_WEB?q=" + URLEncoder.encode(query, "UTF-8") + "&count=20"
 val request = Request.Builder()
 .url(url)
 .header("Accept", "application/json")
 .header("Accept-Encoding", "gzip")
 .header("X-Subscription-Token", key)
 .build()
 client.newCall(request).execute().use { resp ->
 if (resp.code == 422 || resp.code == 401) { markBad(key); return null }
 if (!resp.isSuccessful) return null
 val body = resp.body?.string() ?: return null
 val json = JSONObject(body)
 val results = json.optJSONObject("web")?.optJSONArray("results") ?: return null
 if (results.length() == 0) return null

 data class Item(val title: String, val desc: String, val link: String, val thumb: String?, val favicon: String?)
 val items = mutableListOf<Item>()
 val missingThumbUrls = mutableListOf<String>()
 var i = 0
 while (i < results.length() && i < 20) {
 val r = results.optJSONObject(i)
 if (r != null) {
 val title = stripTags(r.optString("title")).trim()
 val desc = stripTags(r.optString("description")).trim()
 val link = r.optString("url")
 val thumb = r.optJSONObject("thumbnail")?.optString("src")?.takeIf { it.isNotBlank() }
 // Also extract favicon from profile.img as last resort
 val favicon = r.optJSONObject("profile")?.optString("img")?.takeIf { it.isNotBlank() }
 items.add(Item(title, desc, link, thumb, favicon))
 if (thumb == null && link.isNotBlank() && link.startsWith("http")) {
 missingThumbUrls.add(link)
 }
 }
 i++
 }

 // Batch-fetch OG images for results missing a Brave thumbnail.
 val ogImageMap = if (missingThumbUrls.isNotEmpty()) {
 ogImageFetch(missingThumbUrls.take(8))
 } else emptyMap()

 val sb = StringBuilder()
 items.forEachIndexed { idx, item ->
 sb.append("${idx + 1}. ${item.title}\n")
 if (item.desc.isNotBlank()) sb.append("${item.desc}\n")
 // Priority: thumbnail > OG image > favicon
 val image = item.thumb ?: ogImageMap[item.link] ?: item.favicon
 if (image != null) sb.append("Image: $image\n")
 sb.append("Source: ${item.link}\n\n")
 }
 sb.toString().trim().ifBlank { null }
 }
 } catch (e: Exception) {
 null
 }
 }

 /**
 * Batch-fetch Open Graph images for a list of URLs.
 * Runs lightweight HEAD requests first to skip non-HTML URLs, then fetches < 4KB of the HTML
 * to extract og:image meta tags. All requests run in parallel and are capped at 4s each.
 * Returns a map of URL -> OG image URL for those that had one.
 */
 private fun ogImageFetch(urls: List<String>): Map<String, String> {
 val result = mutableMapOf<String, String>()
 val jobs = urls.map { url ->
 Thread {
 try {
 // Step 1: HEAD check — skip non-HTML responses
 val headReq = Request.Builder().url(url)
 .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
 .method("HEAD", null)
 .build()
 val headResp = client.newCall(headReq).execute()
 val contentType = headResp.header("Content-Type", "") ?: ""
 headResp.close()
 if (!contentType.contains("text/html", true) && !contentType.contains("text/plain", true)) return@Thread

 // Step 2: Fetch first 8KB of HTML with a 4s timeout to find og:image
 val bodyReq = Request.Builder().url(url)
 .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
 .header("Range", "bytes=0-8192")
 .build()
 val bodyResp = client.newCall(bodyReq).execute()
 val html = bodyResp.body?.string() ?: run { bodyResp.close(); return@Thread }
 bodyResp.close()

 // Try og:image first, then twitter:image, then first <img> with reasonable size
 val ogPattern = Regex("""<meta\s+[^>]*property=["']og:image["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
 val twPattern = Regex("""<meta\s+[^>]*name=["']twitter:image["'][^>]*content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
 val imgPattern = Regex("""<img[^>]+src=["']([^"']+\.(?:jpg|jpeg|png|webp))["'][^>]*>""", RegexOption.IGNORE_CASE)

 val ogMatch = ogPattern.find(html)
 val twMatch = twPattern.find(html)
 val firstImg = imgPattern.find(html)

 val rawImg = ogMatch?.groupValues?.get(1)
 ?: twMatch?.groupValues?.get(1)
 ?: firstImg?.groupValues?.get(1)
 if (rawImg != null) {
 // Resolve relative URLs against the page URL
 val absolute = try {
 val baseUri = java.net.URI(url)
 if (rawImg.startsWith("//")) "https:$rawImg"
 else if (rawImg.startsWith("/")) "${baseUri.scheme}://${baseUri.host}$rawImg"
 else if (rawImg.startsWith("http")) rawImg
 else "${baseUri.scheme}://${baseUri.host}/$rawImg"
 } catch (_: Exception) { rawImg }
 synchronized(result) { result[url] = absolute }
 }
 } catch (_: Exception) { /* skip — non-critical */ }
 }.apply { isDaemon = true; start() }
 }
 // Wait for all threads to finish with a 5s cap total
 val deadline = System.currentTimeMillis() + 5000
 for (t in jobs) {
 val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(1L)
 try { t.join(remaining) } catch (_: Exception) {}
 }
 return result
 }

 // ---------------------------------------------------------------------------------------
 // IMAGE SEARCH
 // ---------------------------------------------------------------------------------------

 /**
 * Image search. Returns markdown image lines (`![title](imageUrl)`) so the model can embed
 * the relevant ones straight into its answer and they render inline in chat AND the agent.
 */
 fun imageSearch(query: String): String {
 if (query.isBlank()) return "No query."
 if (!RemoteConfigManager.webSearchEnabled) return "Web search is currently disabled by the administrator."
 // Brave first across several VALID keys (rotating past any that are rate-limited). Brave's
 // free image tier is 1 req/sec PER KEY, so rotation + retry is what keeps it working under
 // load. Invalid (422) keys are dropped from rotation automatically.
 val keys = validBraveKeys()
 if (keys.isNotEmpty()) {
 val attempts = minOf(keys.size, 5)
 repeat(attempts) {
 val key = nextKey() ?: return@repeat
 val res = braveImageSearch(query, key)
 if (res != null) return res
 }
 }
 // Final safety net: key-free DuckDuckGo image search (works with zero Brave quota).
 duckDuckGoImageSearch(query)?.let { return it }
 return "No image results found for \"$query\"."
 }

 private fun braveImageSearch(query: String, key: String): String? {
 return try {
 val url = "$BRAVE_IMAGES?q=" + URLEncoder.encode(query, "UTF-8") + "&count=12"
 val request = Request.Builder()
 .url(url)
 .header("Accept", "application/json")
 .header("Accept-Encoding", "gzip")
 .header("X-Subscription-Token", key)
 .build()
 client.newCall(request).execute().use { resp ->
 if (resp.code == 422 || resp.code == 401) { markBad(key); return null } // invalid/expired/unauthorized
 if (!resp.isSuccessful) return null // 429 rate-limit etc. -> retry another key
 val body = resp.body?.string() ?: return null
 val json = JSONObject(body)
 val results = json.optJSONArray("results") ?: return null
 if (results.length() == 0) return null
 val sb = StringBuilder()
 sb.append("IMAGE RESULTS for \"$query\" (embed the relevant ones in your answer as Markdown images exactly as shown):\n\n")
 var i = 0
 var added = 0
 while (i < results.length() && added < 12) {
 val r = results.optJSONObject(i)
 // Prefer Brave's CDN thumbnail (imgs.search.brave.com) — it loads reliably
 // without a referer. The original host URL (properties.url) frequently blocks
 // hotlinking (403), which is what caused "Couldn't load image".
 val thumb = r?.optJSONObject("thumbnail")?.optString("src")?.takeIf { it.isNotBlank() }
 val full = r?.optJSONObject("properties")?.optString("url")?.takeIf { it.isNotBlank() }
 val imgUrl = thumb ?: full
 if (r != null && imgUrl != null) {
 val title = stripTags(r.optString("title"))
 .replace(Regex("[\\[\\]()\\r\\n]"), " ").trim().ifBlank { "image" }
 val page = r.optString("url")
 sb.append("![${title.take(80)}]($imgUrl)\n")
 if (page.isNotBlank()) sb.append("Source: $page\n")
 sb.append("\n")
 added++
 }
 i++
 }
 if (added == 0) null else sb.toString().trim()
 }
 } catch (e: Exception) {
 null
 }
 }

 // ---------------------------------------------------------------------------------------
 // PARALLEL MULTI-SEARCH (no practical limit — each query uses a different key)
 // ---------------------------------------------------------------------------------------

 suspend fun searchMulti(queries: List<String>): String = coroutineScope {
 val jobs = queries.filter { it.isNotBlank() }.map { q ->
 async(Dispatchers.IO) { "### Web search: $q\n" + search(q) }
 }
 jobs.map { it.await() }.joinToString("\n\n")
 }

 suspend fun imageSearchMulti(queries: List<String>): String = coroutineScope {
 val jobs = queries.filter { it.isNotBlank() }.map { q ->
 async(Dispatchers.IO) { "### Image search: $q\n" + imageSearch(q) }
 }
 jobs.map { it.await() }.joinToString("\n\n")
 }

 /** Reads several URLs in PARALLEL and concatenates their readable text. */
 suspend fun readMulti(urls: List<String>): String = coroutineScope {
 val jobs = urls.filter { it.isNotBlank() }.map { u ->
 async(Dispatchers.IO) { "### Read: $u\n" + read(u) }
 }
 jobs.map { it.await() }.joinToString("\n\n")
 }

 // ---------------------------------------------------------------------------------------
 // URL READING (Jina Reader -> raw HTML fallback)
 // ---------------------------------------------------------------------------------------

 fun read(url: String): String {
 if (url.isBlank()) return "No URL."
 val full = if (url.startsWith("http")) url else "https://$url"
 // 1. Jina Reader — returns clean, LLM-friendly markdown of the page.
 jinaRead(full)?.let { return it }
 // 2. Fallback: fetch the raw page and strip HTML.
 return try {
 val request = Request.Builder()
 .url(full)
 .header("User-Agent", UA)
 .build()
 val html = client.newCall(request).execute().use { it.body?.string() } ?: ""
 val text = htmlToText(html)
 if (text.isBlank()) "Could not extract readable content from $url"
 else text.take(6000)
 } catch (e: Exception) {
 "Failed to read $url: ${e.message}"
 }
 }

 /**
 * Jina AI Reader (https://r.jina.ai) — prepend the reader endpoint to any URL to get back
 * clean, readable markdown of the page (great for the AI). Free, no key required.
 */
 private fun jinaRead(url: String): String? {
 return try {
 val request = Request.Builder()
 .url("https://r.jina.ai/$url")
 .header("User-Agent", UA)
 .header("Accept", "text/plain")
 .header("X-Return-Format", "markdown")
 .build()
 client.newCall(request).execute().use { resp ->
 if (!resp.isSuccessful) return null
 val text = resp.body?.string()?.trim() ?: return null
 if (text.isBlank()) null else text.take(8000)
 }
 } catch (e: Exception) {
 null
 }
 }

 // ---------------------------------------------------------------------------------------
 // DUCKDUCKGO FALLBACK (key-free)
 // ---------------------------------------------------------------------------------------

 /**
 * Key-free image search via DuckDuckGo. Uses the vqd-token flow, then the i.js JSON endpoint.
 * Emits Markdown image lines using the Bing-CDN `thumbnail` URLs (which hotlink reliably), so
 * images actually render in chat AND the agent. Tested working with no API key.
 */
 private fun duckDuckGoImageSearch(query: String): String? {
 return try {
 // 1. Obtain the vqd token required by the image endpoint.
 val tokenReq = Request.Builder()
 .url("https://duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8") + "&iax=images&ia=images")
 .header("User-Agent", UA)
 .build()
 val html = client.newCall(tokenReq).execute().use { it.body?.string() } ?: return null
 val vqd = Regex("vqd=[\"']?([0-9-]+)").find(html)?.groupValues?.get(1) ?: return null

 // 2. Fetch the image results JSON.
 val req = Request.Builder()
 .url(
 "https://duckduckgo.com/i.js?l=us-en&o=json&q=" +
 URLEncoder.encode(query, "UTF-8") + "&vqd=" + vqd + "&f=,,,&p=1"
 )
 .header("User-Agent", UA)
 .header("Referer", "https://duckduckgo.com/")
 .header("Accept", "application/json")
 .build()
 val body = client.newCall(req).execute().use { it.body?.string() } ?: return null
 val results = JSONObject(body).optJSONArray("results") ?: return null
 if (results.length() == 0) return null

 val sb = StringBuilder()
 sb.append("IMAGE RESULTS for \"$query\" (embed the relevant ones in your answer as Markdown images exactly as shown):\n\n")
 var i = 0
 var added = 0
 while (i < results.length() && added < 12) {
 val r = results.optJSONObject(i)
 // Prefer the proxied thumbnail (Bing CDN) — it loads reliably without a referer.
 val thumb = r?.optString("thumbnail")?.takeIf { it.isNotBlank() }
 val full = r?.optString("image")?.takeIf { it.isNotBlank() }
 val imgUrl = thumb ?: full
 if (r != null && imgUrl != null) {
 val title = stripTags(r.optString("title"))
 .replace(Regex("[\\[\\]()\\r\\n]"), " ").trim().ifBlank { "image" }
 val page = r.optString("url")
 sb.append("![${title.take(80)}]($imgUrl)\n")
 if (page.isNotBlank()) sb.append("Source: $page\n")
 sb.append("\n")
 added++
 }
 i++
 }
 if (added == 0) null else sb.toString().trim()
 } catch (e: Exception) {
 null
 }
 }

 private fun duckDuckGoSearch(query: String): String {
 return try {
 val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
 val request = Request.Builder()
 .url(url)
 .header("User-Agent", UA)
 .build()
 val html = client.newCall(request).execute().use { it.body?.string() } ?: ""
 val results = parseDuckDuckGo(html)
                if (results.isNotEmpty()) {
                    results.take(8).mapIndexed { i, r ->
                        val sb = StringBuilder("${i + 1}. ${r.title}\n${r.snippet}")
                        if (!r.thumbnail.isNullOrBlank()) sb.append("\nImage: ${r.thumbnail}")
                        sb.append("\nSource: ${r.url}")
                        sb.toString()
                    }.joinToString("\n\n")
                } else {
 instantAnswer(query)
 }
 } catch (e: Exception) {
 "Search failed: ${e.message}"
 }
 }

 private data class SearchResult(val title: String, val snippet: String, val url: String, val thumbnail: String? = null)

 private fun parseDuckDuckGo(html: String): List<SearchResult> {
 val anchorRegex = Regex("class=\"result__a\"[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
 val snippetRegex = Regex("class=\"result__snippet\"[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
 val imageRegex = Regex("class=\"result__icon\"[^>]*>\\s*<img[^>]*src=\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)

 val anchors = anchorRegex.findAll(html).toList()
 val snippets = snippetRegex.findAll(html).toList()
 val images = imageRegex.findAll(html).toList()

 return anchors.mapIndexedNotNull { i, m ->
 val rawUrl = m.groupValues[1]
 val realUrl = decodeDdgUrl(rawUrl)
 val title = stripTags(m.groupValues[2]).trim()
 val snippet = snippets.getOrNull(i)?.let { stripTags(it.groupValues[1]).trim() } ?: ""
 val thumb = images.getOrNull(i)?.let { imgMatch ->
 val src = imgMatch.groupValues[1]
 if (src.startsWith("http")) src else null
 }
 if (title.isBlank()) null else SearchResult(title, snippet, realUrl, thumb)
 }
 }

 private fun decodeDdgUrl(raw: String): String {
 val uddg = Regex("uddg=([^&]+)").find(raw)?.groupValues?.get(1)
 return if (uddg != null) {
 try { URLDecoder.decode(uddg, "UTF-8") } catch (e: Exception) { raw }
 } else {
 if (raw.startsWith("//")) "https:$raw" else raw
 }
 }

 private fun instantAnswer(query: String): String {
 return try {
 val url = "https://api.duckduckgo.com/?q=" + URLEncoder.encode(query, "UTF-8") +
 "&format=json&no_html=1&skip_disambig=1"
 val request = Request.Builder().url(url).header("User-Agent", UA).build()
 val body = client.newCall(request).execute().use { it.body?.string() } ?: return "No results."
 val json = JSONObject(body)
 val abstract = json.optString("AbstractText", "")
 val sb = StringBuilder()
 if (abstract.isNotBlank()) {
 sb.append(abstract)
 val src = json.optString("AbstractURL", "")
 if (src.isNotBlank()) sb.append("\nSource: $src")
 }
 val related = json.optJSONArray("RelatedTopics")
 if (related != null) {
 var count = 0
 var i = 0
 while (i < related.length() && count < 4) {
 val topic = related.optJSONObject(i)
 val txt = topic?.optString("Text", "") ?: ""
 if (txt.isNotBlank()) {
 sb.append("\n\n- ").append(txt)
 count++
 }
 i++
 }
 }
 if (sb.isBlank()) "No useful results found for: $query" else sb.toString()
 } catch (e: Exception) {
 "Search failed: ${e.message}"
 }
 }

 // ---------------------------------------------------------------------------------------
 // HTML helpers
 // ---------------------------------------------------------------------------------------

 private fun stripTags(s: String): String = unescapeHtml(Regex("<[^>]+>").replace(s, ""))

 private fun htmlToText(html: String): String {
 var t = html
 t = Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE).replace(t, " ")
 t = Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE).replace(t, " ")
 t = Regex("<head[\\s\\S]*?</head>", RegexOption.IGNORE_CASE).replace(t, " ")
 t = Regex("<[^>]+>").replace(t, " ")
 t = unescapeHtml(t)
 t = Regex("[ \\t\\x0B\\f\\r]+").replace(t, " ")
 t = Regex("\\n{3,}").replace(t, "\n\n")
 return t.trim()
 }

 private fun unescapeHtml(s: String): String = s
 .replace("&amp;", "&")
 .replace("&lt;", "<")
 .replace("&gt;", ">")
 .replace("&quot;", "\"")
 .replace("&#39;", "'")
 .replace("&#x27;", "'")
 .replace("&nbsp;", " ")
}
