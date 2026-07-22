package com.ahamai.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.tasks.await

/**
 * Import admin remote config from a ZIP (or a single JSON/txt file).
 *
 * Expected ZIP layout (from gofile bundle / export) — names are flexible:
 * ```
 * chat_system_prompt.txt          → config/systemPrompts.chat
 * agent_system_prompt.txt         → config/systemPrompts.agent
 * skills_adminSkills.json         → full config/skills document
 * skills_list_only.json           → adminSkills array only
 * skills_md/<id>.md               → fallback skills if no JSON present
 * ```
 */
object AdminConfigImporter {

    private const val TAG = "AdminConfigImporter"

    data class Parsed(
        val chatPrompt: String? = null,
        val agentPrompt: String? = null,
        /** Full Firestore payload for config/skills (adminSkills, disabled, icons, …). */
        val skillsDoc: Map<String, Any?>? = null,
        val notes: List<String> = emptyList(),
        val errors: List<String> = emptyList()
    ) {
        val hasAnything: Boolean
            get() = chatPrompt != null || agentPrompt != null || skillsDoc != null
    }

    data class ApplyResult(
        val ok: Boolean,
        val message: String,
        val chatApplied: Boolean = false,
        val agentApplied: Boolean = false,
        val skillsCount: Int = 0
    )

    fun parseUri(context: Context, uri: Uri): Parsed {
        return try {
            val name = uri.displayName(context).lowercase()
            context.contentResolver.openInputStream(uri)?.use { raw ->
                val bytes = raw.readBytes() // Kotlin stdlib
                when {
                    name.endsWith(".zip") || isZip(bytes) -> parseZip(bytes)
                    name.endsWith(".json") -> parseJsonFile(String(bytes, Charsets.UTF_8), name)
                    name.endsWith(".txt") || name.endsWith(".md") -> parseTextFile(String(bytes, Charsets.UTF_8), name)
                    else -> {
                        if (isZip(bytes)) parseZip(bytes)
                        else if (bytes.isNotEmpty() && bytes[0].toInt().toChar() in setOf('{', '[')) {
                            parseJsonFile(String(bytes, Charsets.UTF_8), name)
                        } else Parsed(errors = listOf("Unsupported file type. Upload the config ZIP, or a .json / .txt file."))
                    }
                }
            } ?: Parsed(errors = listOf("Could not open file"))
        } catch (e: Exception) {
            Log.e(TAG, "parseUri failed", e)
            Parsed(errors = listOf(e.message ?: "Parse failed"))
        }
    }

    fun parseZip(bytes: ByteArray): Parsed {
        val files = linkedMapOf<String, ByteArray>()
        ZipInputStream(BufferedInputStream(bytes.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    // skip macOS junk
                    if (!path.contains("__MACOSX") && !path.substringAfterLast('/').startsWith(".")) {
                        files[path] = zis.readBytes() // Kotlin extension on InputStream
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        if (files.isEmpty()) return Parsed(errors = listOf("ZIP is empty"))

        val notes = mutableListOf<String>()
        notes.add("ZIP entries: ${files.size}")

        // ── Prompts ──────────────────────────────────────────────────────────
        val chat = findText(files, listOf(
            "chat_system_prompt.txt", "chat_prompt.txt", "chat.txt",
            "system_prompts/chat.txt", "systemPrompts/chat.txt", "prompts/chat.txt"
        ))
        val agent = findText(files, listOf(
            "agent_system_prompt.txt", "agent_prompt.txt", "agent.txt",
            "system_prompts/agent.txt", "systemPrompts/agent.txt", "prompts/agent.txt",
            "code_agent_prompt.txt"
        ))
        // combined systemPrompts.json { "chat": "...", "agent": "..." }
        var chatFromJson: String? = null
        var agentFromJson: String? = null
        findBytes(files, listOf("system_prompts.json", "systemPrompts.json", "prompts.json"))?.let { b ->
            runCatching {
                val o = JSONObject(String(b, Charsets.UTF_8))
                chatFromJson = o.optString("chat").takeIf { it.isNotBlank() }
                agentFromJson = o.optString("agent").takeIf { it.isNotBlank() }
            }
        }

        val chatFinal = chat ?: chatFromJson
        val agentFinal = agent ?: agentFromJson
        if (chatFinal != null) notes.add("Found chat prompt (${chatFinal.length} chars)")
        if (agentFinal != null) notes.add("Found agent prompt (${agentFinal.length} chars)")

        // ── Skills ───────────────────────────────────────────────────────────
        val skillsDoc = parseSkillsFromFiles(files, notes)

        if (chatFinal == null && agentFinal == null && skillsDoc == null) {
            return Parsed(
                notes = notes,
                errors = listOf(
                    "No known config found in ZIP. Expected chat_system_prompt.txt, " +
                        "agent_system_prompt.txt, and/or skills_adminSkills.json"
                )
            )
        }
        return Parsed(
            chatPrompt = chatFinal,
            agentPrompt = agentFinal,
            skillsDoc = skillsDoc,
            notes = notes
        )
    }

    private fun parseJsonFile(text: String, name: String): Parsed {
        val notes = mutableListOf("Parsed JSON file: $name")
        return try {
            val trimmed = text.trim()
            if (trimmed.startsWith("[")) {
                // skills array
                val arr = JSONArray(trimmed)
                val doc = skillsArrayToDoc(arr)
                notes.add("adminSkills array: ${arr.length()} skills")
                Parsed(skillsDoc = doc, notes = notes)
            } else {
                val o = JSONObject(trimmed)
                when {
                    o.has("adminSkills") || o.has("disabled") || o.has("overrides") -> {
                        val doc = jsonObjectToMap(o)
                        val n = (o.optJSONArray("adminSkills")?.length() ?: 0)
                        notes.add("skills document with $n adminSkills")
                        Parsed(skillsDoc = doc, notes = notes)
                    }
                    o.has("chat") || o.has("agent") -> {
                        Parsed(
                            chatPrompt = o.optString("chat", "").takeIf { it.isNotBlank() },
                            agentPrompt = o.optString("agent", "").takeIf { it.isNotBlank() },
                            notes = notes
                        )
                    }
                    else -> Parsed(errors = listOf("JSON not recognized as skills or systemPrompts"))
                }
            }
        } catch (e: Exception) {
            Parsed(errors = listOf("Invalid JSON: ${e.message}"))
        }
    }

    private fun parseTextFile(text: String, name: String): Parsed {
        val n = name.lowercase()
        return when {
            n.contains("chat") -> Parsed(chatPrompt = text, notes = listOf("Chat prompt from $name"))
            n.contains("agent") || n.contains("code") -> Parsed(agentPrompt = text, notes = listOf("Agent prompt from $name"))
            else -> Parsed(errors = listOf("Ambiguous .txt name — use chat_system_prompt.txt or agent_system_prompt.txt"))
        }
    }

    private fun parseSkillsFromFiles(
        files: Map<String, ByteArray>,
        notes: MutableList<String>
    ): Map<String, Any?>? {
        // Prefer full document
        findBytes(files, listOf(
            "skills_adminSkills.json", "skills_config.json", "config_skills.json",
            "skills.json", "config/skills.json"
        ))?.let { b ->
            return try {
                val text = String(b, Charsets.UTF_8).trim()
                if (text.startsWith("[")) {
                    val arr = JSONArray(text)
                    notes.add("skills JSON array: ${arr.length()}")
                    skillsArrayToDoc(arr)
                } else {
                    val o = JSONObject(text)
                    if (o.has("adminSkills") || o.has("disabled") || o.has("overrides")) {
                        notes.add("skills document (${o.optJSONArray("adminSkills")?.length() ?: 0} adminSkills)")
                        jsonObjectToMap(o)
                    } else if (o.has("skills") && o.get("skills") is JSONArray) {
                        skillsArrayToDoc(o.getJSONArray("skills")).also {
                            notes.add("skills wrapper array")
                        }
                    } else {
                        // treat whole object values as? no
                        null
                    }
                }
            } catch (e: Exception) {
                notes.add("skills JSON parse error: ${e.message}")
                null
            }
        }

        // skills_list_only.json
        findBytes(files, listOf("skills_list_only.json", "admin_skills.json", "adminSkills.json"))?.let { b ->
            return try {
                val arr = JSONArray(String(b, Charsets.UTF_8).trim().let {
                    if (it.startsWith("[")) it else JSONObject(it).optJSONArray("adminSkills")?.toString() ?: it
                })
                notes.add("skills list only: ${arr.length()}")
                skillsArrayToDoc(arr)
            } catch (e: Exception) {
                notes.add("skills_list parse error: ${e.message}")
                null
            }
        }

        // skills_md/*.md fallback
        val mdSkills = files.filter { (path, _) ->
            val p = path.lowercase()
            (p.contains("skills_md/") || p.contains("skills/") || p.endsWith(".md")) &&
                p.endsWith(".md") && !p.endsWith("readme.md")
        }
        if (mdSkills.isNotEmpty()) {
            val list = mutableListOf<Map<String, Any?>>()
            mdSkills.forEach { (path, bytes) ->
                val text = String(bytes, Charsets.UTF_8)
                val parsed = SkillManager.parseSkillMd(text)
                if (parsed != null) {
                    list.add(
                        mapOf(
                            "id" to parsed.id,
                            "name" to parsed.name,
                            "description" to parsed.description,
                            "content" to parsed.content,
                            "iconSvg" to "",
                            "enabled" to true
                        )
                    )
                } else {
                    val id = path.substringAfterLast('/').removeSuffix(".md")
                        .lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    if (id.isNotBlank()) {
                        list.add(
                            mapOf(
                                "id" to id,
                                "name" to SkillManager.humanize(id),
                                "description" to "Imported skill: $id",
                                "content" to text.trim(),
                                "iconSvg" to "",
                                "enabled" to true
                            )
                        )
                    }
                }
            }
            if (list.isNotEmpty()) {
                notes.add("skills from markdown: ${list.size}")
                return mapOf(
                    "disabled" to emptyList<String>(),
                    "deleted" to emptyList<String>(),
                    "whitelist" to emptyList<String>(),
                    "overrides" to emptyMap<String, Any>(),
                    "adminSkills" to list,
                    "icons" to emptyMap<String, String>()
                )
            }
        }
        return null
    }

    private fun skillsArrayToDoc(arr: JSONArray): Map<String, Any?> {
        val list = mutableListOf<Map<String, Any?>>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").trim().ifBlank {
                SkillManager.sanitizeName(o.optString("name"))
            }
            if (id.isBlank()) continue
            list.add(
                mapOf(
                    "id" to SkillManager.sanitizeName(id),
                    "name" to o.optString("name").ifBlank { SkillManager.humanize(id) },
                    "description" to o.optString("description"),
                    "content" to o.optString("content"),
                    "iconSvg" to o.optString("iconSvg", o.optString("icon", "")),
                    "enabled" to if (o.has("enabled")) o.optBoolean("enabled", true) else true
                )
            )
        }
        return mapOf(
            "disabled" to emptyList<String>(),
            "deleted" to emptyList<String>(),
            "whitelist" to emptyList<String>(),
            "overrides" to emptyMap<String, Any>(),
            "adminSkills" to list,
            "icons" to emptyMap<String, String>()
        )
    }

    private fun jsonObjectToMap(o: JSONObject): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        // Preserve known skill fields with proper types
        fun listStr(key: String): List<String> {
            val a = o.optJSONArray(key) ?: return emptyList()
            return (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
        }
        if (o.has("disabled")) out["disabled"] = listStr("disabled")
        if (o.has("deleted")) out["deleted"] = listStr("deleted")
        if (o.has("whitelist")) out["whitelist"] = listStr("whitelist")

        if (o.has("adminSkills")) {
            val a = o.getJSONArray("adminSkills")
            val list = mutableListOf<Map<String, Any?>>()
            for (i in 0 until a.length()) {
                val s = a.optJSONObject(i) ?: continue
                list.add(
                    mapOf(
                        "id" to SkillManager.sanitizeName(s.optString("id")),
                        "name" to s.optString("name"),
                        "description" to s.optString("description"),
                        "content" to s.optString("content"),
                        "iconSvg" to s.optString("iconSvg", s.optString("icon", "")),
                        "enabled" to if (s.has("enabled")) s.optBoolean("enabled", true) else true
                    )
                )
            }
            out["adminSkills"] = list
        }
        if (o.has("icons")) {
            val icons = linkedMapOf<String, String>()
            val io = o.optJSONObject("icons")
            io?.keys()?.forEach { k ->
                icons[k] = io.optString(k)
            }
            out["icons"] = icons
        }
        if (o.has("overrides")) {
            val ov = linkedMapOf<String, Map<String, Any?>>()
            val oo = o.optJSONObject("overrides")
            oo?.keys()?.forEach { k ->
                val m = oo.optJSONObject(k) ?: return@forEach
                ov[k] = mapOf(
                    "name" to m.optString("name"),
                    "description" to m.optString("description"),
                    "content" to m.optString("content"),
                    "iconSvg" to m.optString("iconSvg"),
                    "enabled" to if (m.has("enabled")) m.optBoolean("enabled", true) else true
                )
            }
            out["overrides"] = ov
        }
        // defaults
        if (!out.containsKey("disabled")) out["disabled"] = emptyList<String>()
        if (!out.containsKey("deleted")) out["deleted"] = emptyList<String>()
        if (!out.containsKey("whitelist")) out["whitelist"] = emptyList<String>()
        if (!out.containsKey("overrides")) out["overrides"] = emptyMap<String, Any>()
        if (!out.containsKey("adminSkills")) out["adminSkills"] = emptyList<Map<String, Any?>>()
        if (!out.containsKey("icons")) out["icons"] = emptyMap<String, String>()
        return out
    }

    /**
     * Write parsed config to Firestore + in-memory RemoteConfig / SkillManager.
     * @param replaceSkills when true, skills document is fully replaced (default).
     */
    suspend fun applyToFirestore(
        parsed: Parsed,
        replaceSkills: Boolean = true
    ): ApplyResult {
        if (parsed.errors.isNotEmpty() && !parsed.hasAnything) {
            return ApplyResult(false, parsed.errors.joinToString("; "))
        }
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val notes = mutableListOf<String>()
        var chatApplied = false
        var agentApplied = false
        var skillsCount = 0

        try {
            // Prompts
            if (parsed.chatPrompt != null || parsed.agentPrompt != null) {
                val currentChat = if (parsed.chatPrompt != null) parsed.chatPrompt
                else RemoteConfigManager.chatSystemPrompt
                val currentAgent = if (parsed.agentPrompt != null) parsed.agentPrompt
                else RemoteConfigManager.agentSystemPrompt
                val payload = hashMapOf(
                    "chat" to currentChat,
                    "agent" to currentAgent
                )
                // If only one field provided, merge with existing Firestore values
                if (parsed.chatPrompt == null || parsed.agentPrompt == null) {
                    runCatching {
                        val existing = db.collection("config").document("systemPrompts").get().await()
                        if (existing.exists()) {
                            if (parsed.chatPrompt == null) {
                                payload["chat"] = existing.getString("chat") ?: currentChat
                            }
                            if (parsed.agentPrompt == null) {
                                payload["agent"] = existing.getString("agent") ?: currentAgent
                            }
                        }
                    }
                }
                db.collection("config").document("systemPrompts").set(payload).await()
                RemoteConfigManager.chatSystemPrompt = payload["chat"] as String
                RemoteConfigManager.agentSystemPrompt = payload["agent"] as String
                if (parsed.chatPrompt != null) {
                    chatApplied = true
                    notes.add("chat prompt (${parsed.chatPrompt.length} chars)")
                }
                if (parsed.agentPrompt != null) {
                    agentApplied = true
                    notes.add("agent prompt (${parsed.agentPrompt.length} chars)")
                }
            }

            // Skills
            parsed.skillsDoc?.let { doc ->
                @Suppress("UNCHECKED_CAST")
                val adminList = doc["adminSkills"] as? List<*>
                skillsCount = adminList?.size ?: 0
                val payload = HashMap<String, Any?>()
                payload.putAll(doc)
                payload["updatedAt"] = System.currentTimeMillis()
                payload["updatedBy"] = AuthManager.email().ifBlank { "import" }
                payload["importedFromZip"] = true
                if (replaceSkills) {
                    db.collection("config").document("skills").set(payload).await()
                } else {
                    db.collection("config").document("skills")
                        .set(payload, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                }
                SkillManager.applyAdminConfigFromFirestore(payload)
                notes.add("$skillsCount skills")
            }

            val msg = if (notes.isEmpty()) "Nothing applied"
            else "Imported: ${notes.joinToString(", ")}"
            return ApplyResult(
                ok = true,
                message = msg,
                chatApplied = chatApplied,
                agentApplied = agentApplied,
                skillsCount = skillsCount
            )
        } catch (e: Exception) {
            Log.e(TAG, "apply failed", e)
            return ApplyResult(false, "ERROR: ${e.message}")
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() // PK

    private fun findBytes(files: Map<String, ByteArray>, names: List<String>): ByteArray? {
        // exact basename match (case-insensitive), prefer shortest path
        val lowered = names.map { it.lowercase() }
        val hits = files.entries.filter { (path, _) ->
            val base = path.substringAfterLast('/').lowercase()
            val full = path.lowercase()
            lowered.any { it == base || it == full || full.endsWith("/$it") }
        }
        return hits.minByOrNull { it.key.length }?.value
    }

    private fun findText(files: Map<String, ByteArray>, names: List<String>): String? =
        findBytes(files, names)?.let { String(it, Charsets.UTF_8) }?.takeIf { it.isNotBlank() }

    private fun Uri.displayName(context: Context): String {
        return try {
            context.contentResolver.query(this, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && idx >= 0) c.getString(idx) else null
            } ?: lastPathSegment ?: "file"
        } catch (_: Exception) {
            lastPathSegment ?: "file"
        }
    }
}
