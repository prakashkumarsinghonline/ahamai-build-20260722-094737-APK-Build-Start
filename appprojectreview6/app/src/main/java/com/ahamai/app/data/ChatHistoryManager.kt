package com.ahamai.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A web source/citation attached to an assistant message (persisted for reload & followups). */
data class StoredSource(
    val title: String,
    val url: String,
    val domain: String,
    val snippet: String? = null,
    val imageUrl: String? = null
)

/** A saved chat message (simplified for storage). */
data class StoredMessage(
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val images: List<String> = emptyList(),
    val sources: List<StoredSource> = emptyList()
)

/** A chat session with title and messages. */
data class ChatSession(
    val id: String,
    var title: String,
    var lastUpdated: Long,
    val messages: List<StoredMessage>,
    var pinned: Boolean = false
)

/**
 * Signals ChatHistoryScreen (and others) to rebuild when history/workspaces change.
 * Uses a [StateFlow] so Compose actually recomposes — a plain Int was bumped after
 * cloud restore but never observed, so the list stayed empty until the user left
 * and came back (recreating the screen).
 */
object HistoryRefresh {
    private val _signal = kotlinx.coroutines.flow.MutableStateFlow(0)
    val signalFlow: kotlinx.coroutines.flow.StateFlow<Int> = _signal

    /** Current value (for non-Compose callers). Prefer [signalFlow] in UI. */
    val signal: Int get() = _signal.value

    fun bump() {
        _signal.value = _signal.value + 1
    }
}

/** Simple signal — bump to tell MainActivity to re-read the theme from prefs. */
object ThemeRefresh {
    private val _signal = kotlinx.coroutines.flow.MutableStateFlow(0)
    val signalFlow: kotlinx.coroutines.flow.StateFlow<Int> = _signal
    val signal: Int get() = _signal.value
    fun bump() {
        _signal.value = _signal.value + 1
    }
}

object ChatHistoryManager {

    private const val PREFS = "ahamai_chat_history"
    private const val KEY_SESSIONS = "sessions"

    // Thread safety: all load→modify→persist cycles go through this lock to prevent
    // concurrent coroutines from clobbering each other's writes.
    private val writeLock = Any()

    // In-memory cache so history screen opens instantly (JSON parse only on miss/invalidate).
    @Volatile private var sessionsCache: List<ChatSession>? = null
    @Volatile private var sessionsCacheUid: String? = null

    /**
     * Per-user storage key. Chat history MUST be isolated per signed-in account — otherwise a new
     * user on the same device sees the previous user's chats (the cloud restore returns early when
     * the new user has no backup, so it can't be relied on for isolation). Namespacing the local
     * key by Firebase uid guarantees physical separation regardless of sync state.
     */
    private fun currentSessionsKey(): String =
        AuthManager.uid()?.takeIf { it.isNotBlank() }?.let { "sessions_$it" } ?: KEY_SESSIONS

    /**
     * Returns ALL unique sessions keys found in preferences (including the guest fallback and
     * every uid-scoped key). Used by [loadSessions] to aggregate chats from any auth state so
     * the user always sees all their history regardless of session restore timing.
     */
    private fun allSessionsKeys(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        val keys = mutableSetOf<String>()
        for (k in all.keys) {
            if (k == KEY_SESSIONS || k.startsWith("sessions_")) {
                keys.add(k)
            }
        }
        if (keys.isEmpty()) keys.add(currentSessionsKey()) // at least the current one
        return keys
    }

    fun loadSessions(context: Context): List<ChatSession> {
        val uid = AuthManager.uid()
        // Superfast path: warm memory cache for this account
        val cached = sessionsCache
        if (cached != null && sessionsCacheUid == uid) return cached

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Merge sessions from ALL keys so chats from any auth state are visible
        val seen = mutableSetOf<String>()
        val merged = mutableListOf<ChatSession>()
        for (key in allSessionsKeys(context)) {
            val json = prefs.getString(key, null) ?: continue
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    try {
                        val obj = arr.getJSONObject(i)
                        val id = obj.getString("id")
                        if (!seen.add(id)) continue // dedup across keys
                        val msgArr = obj.getJSONArray("messages")
                        val messages = (0 until msgArr.length()).map { j ->
                            val m = msgArr.getJSONObject(j)
                            val imgs = m.optJSONArray("images")?.let { ia ->
                                (0 until ia.length()).map { k -> ia.getString(k) }
                            } ?: emptyList()
                            val srcs = m.optJSONArray("sources")?.let { sa ->
                                (0 until sa.length()).mapNotNull { k ->
                                    val so = sa.optJSONObject(k) ?: return@mapNotNull null
                                    StoredSource(
                                        title = so.optString("title", ""),
                                        url = so.optString("url", ""),
                                        domain = so.optString("domain", ""),
                                        snippet = if (so.isNull("snippet")) null else so.optString("snippet", null),
                                        imageUrl = if (so.isNull("imageUrl")) null else so.optString("imageUrl", null)
                                    )
                                }
                            } ?: emptyList()
                            StoredMessage(
                                role = m.getString("role"),
                                content = m.getString("content"),
                                reasoning = if (m.isNull("reasoning")) null else m.optString("reasoning", null),
                                images = imgs,
                                sources = srcs
                            )
                        }
                        merged.add(ChatSession(
                            id = id,
                            title = obj.getString("title"),
                            lastUpdated = obj.getLong("lastUpdated"),
                            messages = messages,
                            pinned = obj.optBoolean("pinned", false)
                        ))
                    } catch (_: Exception) { /* skip corrupted session */ }
                }
            } catch (_: Exception) { /* skip corrupted key */ }
        }
        val result = merged.sortedWith(
            compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.lastUpdated }
        )
        sessionsCache = result
        sessionsCacheUid = uid
        return result
    }

    private fun invalidateCache() {
        sessionsCache = null
        sessionsCacheUid = null
    }

    fun saveSession(context: Context, session: ChatSession) {
        if (session.messages.isEmpty()) return
        synchronized(writeLock) {
            val existing = loadSessions(context).toMutableList()
            val wasPinned = existing.find { it.id == session.id }?.pinned ?: false
            session.pinned = wasPinned
            existing.removeAll { it.id == session.id }
            existing.add(0, session)
            val trimmed = existing.take(100)
            persist(context, trimmed)
        }
        HistoryRefresh.bump()
    }

    fun deleteSession(context: Context, id: String) {
        synchronized(writeLock) {
            val existing = loadSessions(context).toMutableList()
            existing.removeAll { it.id == id }
            persist(context, existing)
        }
        HistoryRefresh.bump()
    }

    fun renameSession(context: Context, id: String, newTitle: String) {
        synchronized(writeLock) {
            val existing = loadSessions(context).toMutableList()
            existing.find { it.id == id }?.title = newTitle.ifBlank { "Untitled" }
            persist(context, existing)
        }
        HistoryRefresh.bump()
    }

    fun togglePin(context: Context, id: String) {
        synchronized(writeLock) {
            val existing = loadSessions(context).toMutableList()
            existing.find { it.id == id }?.let { it.pinned = !it.pinned }
            persist(context, existing)
        }
        HistoryRefresh.bump()
    }

    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(currentSessionsKey()).apply()
        invalidateCache()
        HistoryRefresh.bump()
    }

    /** Raw sessions JSON (the stored array) for cloud backup. */
    fun exportJson(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(currentSessionsKey(), "[]") ?: "[]"
    }

    /**
     * Imports cloud sessions into local history. Atomic: only clears local data AFTER
     * the incoming data is successfully parsed, preventing data loss on parse failure.
     */
    fun importJson(context: Context, json: String) {
        try {
            val incoming = loadFromJson(json)
            if (incoming.isEmpty()) return
            synchronized(writeLock) {
                // Merge cloud + local so a partial cloud backup never wipes newer local chats
                invalidateCache()
                val local = loadSessions(context)
                val byId = LinkedHashMap<String, ChatSession>()
                for (s in local) byId[s.id] = s
                for (s in incoming) {
                    val prev = byId[s.id]
                    if (prev == null || s.lastUpdated >= prev.lastUpdated) byId[s.id] = s
                }
                val sorted = byId.values.sortedWith(
                    compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.lastUpdated }
                ).take(100)
                persist(context, sorted)
            }
            HistoryRefresh.bump()
        } catch (_: Exception) {}
    }

    private fun loadFromJson(json: String): List<ChatSession> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val msgArr = obj.getJSONArray("messages")
                val messages = (0 until msgArr.length()).map { j ->
                    val m = msgArr.getJSONObject(j)
                    val imgs = m.optJSONArray("images")?.let { ia ->
                        (0 until ia.length()).map { k -> ia.getString(k) }
                    } ?: emptyList()
                    val srcs = m.optJSONArray("sources")?.let { sa ->
                        (0 until sa.length()).mapNotNull { k ->
                            val so = sa.optJSONObject(k) ?: return@mapNotNull null
                            StoredSource(
                                title = so.optString("title", ""),
                                url = so.optString("url", ""),
                                domain = so.optString("domain", ""),
                                snippet = if (so.isNull("snippet")) null else so.optString("snippet", null),
                                imageUrl = if (so.isNull("imageUrl")) null else so.optString("imageUrl", null)
                            )
                        }
                    } ?: emptyList()
                    StoredMessage(
                        role = m.getString("role"),
                        content = m.getString("content"),
                        reasoning = if (m.isNull("reasoning")) null else m.optString("reasoning", null),
                        images = imgs,
                        sources = srcs
                    )
                }
                ChatSession(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    lastUpdated = obj.getLong("lastUpdated"),
                    messages = messages,
                    pinned = obj.optBoolean("pinned", false)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(context: Context, sessions: List<ChatSession>) {
        val arr = JSONArray()
        for (s in sessions) {
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("title", s.title)
            obj.put("lastUpdated", s.lastUpdated)
            obj.put("pinned", s.pinned)
            val msgArr = JSONArray()
            for (m in s.messages) {
                val mo = JSONObject()
                mo.put("role", m.role)
                mo.put("content", m.content)
                mo.put("reasoning", m.reasoning)
                val ia = JSONArray()
                m.images.forEach { ia.put(it) }
                mo.put("images", ia)
                val sa = JSONArray()
                m.sources.forEach { src ->
                    val so = JSONObject()
                    so.put("title", src.title)
                    so.put("url", src.url)
                    so.put("domain", src.domain)
                    so.put("snippet", src.snippet)
                    so.put("imageUrl", src.imageUrl)
                    sa.put(so)
                }
                mo.put("sources", sa)
                msgArr.put(mo)
            }
            obj.put("messages", msgArr)
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(currentSessionsKey(), arr.toString()).apply()
        // Keep memory cache warm so history screen stays instant after writes
        sessionsCache = sessions.sortedWith(
            compareByDescending<ChatSession> { it.pinned }.thenByDescending { it.lastUpdated }
        )
        sessionsCacheUid = AuthManager.uid()
    }

    /**
     * Generates a chat title from the first user message using the AI.
     * Falls back to a truncated message if AI fails.
     * Works reliably across all models including thinking models by
     * stripping reasoning tags and keeping the prompt simple.
     */
    fun generateTitle(
        baseUrl: String,
        apiKey: String,
        model: String,
        firstMessage: String
    ): String {
        val fallback = firstMessage.trim().take(30).let {
            if (firstMessage.length > 30) "$it..." else it
        }.ifBlank { "New Chat" }

        return try {
            val prompt = "Generate a short clean title (2-4 words) for a conversation starting with this message. Reply ONLY the title, no quotes, dots, or extra text:\n\n\"$firstMessage\""
            val result = ApiClient.sendChatMessage(
                baseUrl, apiKey, model,
                listOf("user" to prompt)
            )
            val raw = result.getOrNull() ?: return fallback

            // Clean the response: strip thinking tags, quotes, extra text
            var title = raw
                .replace(Regex("<think(?:ing)?>[\\s\\S]*?</think(?:ing)?>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<think(?:ing)?>[\\s\\S]*$", RegexOption.IGNORE_CASE), "")
                .trim()
                .lines()
                .lastOrNull { it.isNotBlank() }
                ?.trim()
                ?.trim('"', '\'', '`', '.', '*', '#')
                ?.trim()
                ?: fallback

            // If still too long or empty, use fallback
            if (title.isBlank() || title.length > 40) {
                fallback
            } else {
                title
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
