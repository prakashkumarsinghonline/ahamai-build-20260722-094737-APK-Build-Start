package com.ahamai.app.data

import java.security.MessageDigest

/**
 * Lightweight in-memory response cache (LiteLLM-style "simple cache").
 *
 * Caches successful chat completions keyed by (model + full message list) so that an identical
 * request is served instantly from memory instead of paying for another upstream call. This is an
 * access-ordered LRU with a TTL per entry; both are driven by the `cache` block in the
 * api_providers JSON (see ApiConfig.update()).
 *
 * Safety rules (enforced by callers via [isCacheable]):
 *  - Never cache responses that contain tool-call syntax — agent turns must always run live.
 *  - Never cache blank responses.
 *  - Vision/image requests are not cached (callers skip them).
 *
 * Everything is in-memory only: nothing is written to disk, so prompts/responses never persist
 * past process death. Thread-safe via coarse synchronization (the map is small and hits are cheap).
 */
object ResponseCache {

    @Volatile var enabled: Boolean = false
    @Volatile var ttlMs: Long = 30 * 60_000L      // default 30 min
    @Volatile var maxEntries: Int = 200

    private data class Entry(val text: String, val expiry: Long)

    // accessOrder = true → most-recently-used moves to the tail, eldest is evicted first.
    private val map = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    /** Stable key for a request = sha256(model + each role/content). */
    fun keyFor(model: String, messages: List<Pair<String, String>>): String {
        val sb = StringBuilder(model).append('\u0000')
        for ((role, content) in messages) {
            sb.append(role).append('\u0002').append(content).append('\u0001')
        }
        return sha256(sb.toString())
    }

    /** A response is cacheable only if it's non-blank and carries no tool-call / agent markers. */
    fun isCacheable(text: String): Boolean {
        if (text.isBlank()) return false
        if (text.contains("</tool_call>", ignoreCase = true)) return false
        if (text.contains("<tool_call", ignoreCase = true)) return false
        if (text.contains("END_FILE", ignoreCase = true)) return false
        if (Regex("(?m)^\\s*(SWITCH_AGENT|GENERATE_IMAGE)\\b", RegexOption.IGNORE_CASE)
                .containsMatchIn(text)) return false
        return true
    }

    @Synchronized
    fun get(key: String): String? {
        if (!enabled) return null
        val e = map[key] ?: return null
        if (System.currentTimeMillis() >= e.expiry) {
            map.remove(key)
            return null
        }
        return e.text
    }

    @Synchronized
    fun put(key: String, text: String) {
        if (!enabled) return
        if (!isCacheable(text)) return
        map[key] = Entry(text, System.currentTimeMillis() + ttlMs)
    }

    @Synchronized
    fun clear() = map.clear()

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xff
            hex.append("0123456789abcdef"[i shr 4])
            hex.append("0123456789abcdef"[i and 0x0f])
        }
        return hex.toString()
    }
}
