package com.ahamai.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Agent Skills manager — [agentskills.io](https://agentskills.io/specification) / Claude Code / Codex parity.
 *
 * ## Progressive disclosure (3 levels)
 * 1. **Advertise** — only `name` + `description` (~100 tokens/skill) in the agent catalog.
 * 2. **Load** — full SKILL.md body when LOAD_SKILL / pin / `/skill-name` slash invoke.
 * 3. **Resources** — `references/`, `scripts/`, `assets/` via READ_SKILL_RESOURCE (on demand).
 *
 * ## Save / replace
 * The agent may call SAVE_SKILL to create or replace a custom skill. The app always asks the
 * user for permission first (see ToolPermission + AgentPermissionGate).
 */
object SkillManager {

    data class Skill(
        val id: String,
        val name: String,
        val description: String,
        val content: String,
        val isCustom: Boolean = false,
        val isBuiltin: Boolean = true,
        val category: String = "Built-in",
        val license: String? = null,
        val compatibility: String? = null,
        val metadata: Map<String, String> = emptyMap(),
        val iconSvg: String = "",
        /** Level-3 package files: relative path → text content (scripts/, references/, assets/). */
        val resources: Map<String, String> = emptyMap(),
        /** Extra trigger text (Claude `when_to_use`). Appended to catalog description. */
        val whenToUse: String = "",
        /** Claude: hide from model auto-invoke catalog; user slash still works. */
        val disableModelInvocation: Boolean = false,
        /** Claude: hide from `/` menu when false. Default true. */
        val userInvocable: Boolean = true,
        /** Experimental allowed-tools list (space-separated). */
        val allowedTools: String = "",
        val argumentHint: String = ""
    ) {
        fun catalogLine(): String {
            val whenPart = whenToUse.trim().takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
            val desc = (description + whenPart).take(1536)
            return "- **$id**: $desc"
        }

        fun toSkillMd(): String = buildString {
            appendLine("---")
            appendLine("name: ${sanitizeName(id)}")
            appendLine("description: >")
            description.lines().forEach { line -> appendLine("  ${line.trim()}") }
            if (whenToUse.isNotBlank()) {
                appendLine("when_to_use: >")
                whenToUse.lines().forEach { line -> appendLine("  ${line.trim()}") }
            }
            if (disableModelInvocation) appendLine("disable-model-invocation: true")
            if (!userInvocable) appendLine("user-invocable: false")
            if (allowedTools.isNotBlank()) appendLine("allowed-tools: $allowedTools")
            if (argumentHint.isNotBlank()) appendLine("argument-hint: $argumentHint")
            if (!license.isNullOrBlank()) appendLine("license: $license")
            if (!compatibility.isNullOrBlank()) appendLine("compatibility: $compatibility")
            if (metadata.isNotEmpty()) {
                appendLine("metadata:")
                metadata.forEach { (k, v) -> appendLine("  $k: \"$v\"") }
            }
            appendLine("---")
            appendLine()
            if (!content.trimStart().startsWith("#")) {
                appendLine("# $name")
                appendLine()
            }
            append(content.trim())
            appendLine()
            if (resources.isNotEmpty()) {
                appendLine()
                appendLine("## Package resources (load with READ_SKILL_RESOURCE)")
                resources.keys.sorted().forEach { path ->
                    appendLine("- `$path`")
                }
            }
        }
    }

    data class CustomSkill(
        val id: String,
        val name: String,
        val description: String,
        val content: String,
        val enabled: Boolean = true,
        val resources: Map<String, String> = emptyMap(),
        val iconSvg: String = "",
        val whenToUse: String = "",
        val disableModelInvocation: Boolean = false,
        val userInvocable: Boolean = true,
        val allowedTools: String = "",
        val argumentHint: String = "",
        val license: String? = null,
        val compatibility: String? = null,
        val metadata: Map<String, String> = emptyMap()
    ) {
        fun toSkill(): Skill = Skill(
            id = id,
            name = name,
            description = description,
            content = content,
            isCustom = true,
            isBuiltin = false,
            category = "My Skills",
            license = license,
            compatibility = compatibility,
            metadata = metadata.ifEmpty { mapOf("author" to "user", "version" to "1.0") },
            iconSvg = iconSvg,
            resources = resources,
            whenToUse = whenToUse,
            disableModelInvocation = disableModelInvocation,
            userInvocable = userInvocable,
            allowedTools = allowedTools,
            argumentHint = argumentHint
        )
    }

    data class AdminSkillEdit(
        val id: String,
        val name: String = "",
        val description: String = "",
        val content: String = "",
        val iconSvg: String = "",
        val isAdminAuthored: Boolean = false,
        val enabled: Boolean = true,
        val resources: Map<String, String> = emptyMap(),
        val whenToUse: String = "",
        val disableModelInvocation: Boolean = false,
        val userInvocable: Boolean = true
    )

    /** Result of parsing `/skill-name args…` from the agent composer. */
    data class SlashInvoke(
        val skillId: String,
        val arguments: String,
        /** Task text to send to the agent after loading the skill. */
        val expandedTask: String
    )

    /** Result of SAVE_SKILL after user approval. */
    data class SaveResult(
        val ok: Boolean,
        val message: String,
        val skillId: String = "",
        val replaced: Boolean = false
    )

    private const val PREFS = "ahamai_skills"
    private const val KEY_CUSTOM = "custom_skills_json"
    private const val KEY_DISABLED_BUILTINS = "disabled_builtins_json"

    private var prefs: SharedPreferences? = null

    private val syncScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    private val loadedSkills = LinkedHashMap<String, Skill>()

    // ── Admin remote policy ──────────────────────────────────────────────────
    @Volatile private var adminDisabledIds: Set<String> = emptySet()
    @Volatile private var adminDeletedIds: Set<String> = emptySet()
    @Volatile private var adminWhitelist: Set<String>? = null
    @Volatile private var adminOverrides: Map<String, AdminSkillEdit> = emptyMap()
    @Volatile private var adminAuthored: List<AdminSkillEdit> = emptyList()
    @Volatile private var adminIcons: Map<String, String> = emptyMap()
    @Volatile var adminConfigVersion: Int = 0
        private set

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            // Materialize packages so scripts/references exist on disk (Claude/Codex layout)
            runCatching { SkillRuntime.materializeAllCustom(context) }
        }
    }

    fun reset() {
        loadedSkills.clear()
        SkillRuntime.clearTurnGrant()
    }

    fun unloadSkill(skillId: String) { loadedSkills.remove(normalizeId(skillId)) }

    fun isLoaded(skillId: String): Boolean = loadedSkills.containsKey(normalizeId(skillId))

    fun getContent(skillId: String): String? = loadedSkills[normalizeId(skillId)]?.content

    fun getLoadedSkillNames(): List<String> = loadedSkills.values.map { it.name }

    fun getLoadedSkills(): List<Skill> = loadedSkills.values.toList()

    fun loadSkill(skillId: String): Skill? {
        val id = normalizeId(skillId)
        loadedSkills[id]?.let { return it }
        findSkill(skillId)?.let {
            if (!it.isCustom) {
                if (!isAdminAllowed(it.id) && it.id != BUNDLED_SKILL_CREATOR.id) return null
                if (normalizeId(it.id) in disabledBuiltins() && it.id != BUNDLED_SKILL_CREATOR.id) return null
            }
            loadedSkills[normalizeId(it.id)] = it
            return it
        }
        return null
    }

    fun findSkill(query: String): Skill? {
        val q = query.trim().removePrefix("/")
        val n = normalizeId(q)
        if (n in adminDeletedIds && n != BUNDLED_SKILL_CREATOR.id) return null

        // Always-available bundled skill-creator (Claude/Codex parity)
        if (n == BUNDLED_SKILL_CREATOR.id || q.equals("skill-creator", true)) {
            return applyOverride(BUNDLED_SKILL_CREATOR)
        }

        ALL_SKILLS.find { it.id == n || normalizeId(it.name) == n }?.let { return applyOverride(it) }
        customSkills().find {
            it.id == n || normalizeId(it.name) == n || it.name.equals(q, true)
        }?.let { return it.toSkill() }
        // Project-local packages (.claude/skills, .codex/skills, .ahamai/skills)
        SkillRuntime.projectSkillsSnapshot().find {
            it.id == n || normalizeId(it.name) == n || it.name.equals(q, true)
        }?.let { return it }
        // On-disk global package
        val disk = File(SkillRuntime.packageDir(n), "SKILL.md")
        if (disk.isFile) {
            SkillRuntime.loadPackageFromDir(SkillRuntime.packageDir(n))?.let { return it }
        }
        val alias = ALIASES[n]
        if (alias != null) return findSkill(alias)
        return ALL_SKILLS.find { it.name.equals(q, true) }?.let { applyOverride(it) }
    }

    /**
     * Level 1 catalog for the model (excludes disable-model-invocation skills, like Claude).
     * User-invocable slash skills remain loadable via /name even when not listed.
     */
    fun buildSkillsCatalog(): String {
        val skills = availableSkills().filter { !it.disableModelInvocation }
        val sb = StringBuilder()
        sb.append("=== AVAILABLE SKILLS (Agent Skills / Claude Code / Codex — SKILL.md) ===\n")
        sb.append("Progressive disclosure (Claude Code / Codex):\n")
        sb.append("  L1) names+descriptions listed here only (~100 tokens each)\n")
        sb.append("  L2) LOAD_SKILL <name> [arguments] → rendered SKILL.md (\$ARGUMENTS, !`cmd` inject)\n")
        sb.append("  L3) READ_SKILL_RESOURCE / RUN_SKILL_SCRIPT for package files under skills/<name>/\n")
        sb.append("User can type /skill-name args in the composer. Packages: skills/<id>/{SKILL.md,scripts,references,assets}.\n")
        sb.append("Project skills: .claude/skills, .codex/skills, .ahamai/skills.\n")
        sb.append("allowed-tools grants clear on the next user message. context:fork → prefer TASK sub-agent.\n")
        sb.append("When a task matches a skill, call LOAD_SKILL. To create/update: SAVE_SKILL (always user-approved).\n")
        sb.append("Do NOT invent skill names.\n\n")
        if (skills.isEmpty()) {
            sb.append("(no skills enabled — skill-creator is still available via LOAD_SKILL skill-creator)\n")
        } else {
            for (s in skills) sb.append(s.catalogLine()).append('\n')
        }
        sb.append("\nSKILL TOOLS:\n")
        sb.append("LOAD_SKILL skill_name=<name> arguments=<optional>\n")
        sb.append("LIST_SKILL_RESOURCES skill_name=<name>\n")
        sb.append("READ_SKILL_RESOURCE skill_name=<name> path=<relative>\n")
        sb.append("RUN_SKILL_SCRIPT skill_name=<name> path=scripts/foo.py arguments=<optional>\n")
        sb.append("SAVE_SKILL skill_md=<SKILL.md> replace=<true|false> resources_json=<optional>\n")
        return sb.toString()
    }

    fun buildSkillInjection(): String {
        if (loadedSkills.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("═══ LOADED SKILLS (full SKILL.md — follow strictly) ═══\n\n")
        for ((_, skill) in loadedSkills) {
            sb.append(skill.toSkillMd())
            sb.append("\n")
        }
        sb.append("═══ END LOADED SKILLS ═══\n\n")
        return sb.toString()
    }

    fun availableSkills(): List<Skill> {
        val disabled = disabledBuiltins()
        val out = ArrayList<Skill>()
        // Bundled skill-creator first
        if (BUNDLED_SKILL_CREATOR.id !in disabled) {
            out.add(applyOverride(BUNDLED_SKILL_CREATOR))
        }
        ALL_SKILLS
            .filter { it.id != BUNDLED_SKILL_CREATOR.id }
            .filter { isAdminAllowed(it.id) && normalizeId(it.id) !in disabled }
            .forEach { out.add(applyOverride(it)) }
        customSkills().filter { it.enabled }.forEach { out.add(it.toSkill()) }
        // Project packages
        SkillRuntime.projectSkillsSnapshot().forEach { out.add(it) }
        return out.distinctBy { it.id }
    }

    fun availableSkillIds(): List<String> = availableSkills().map { it.id }

    // ── Slash invoke (Claude `/skill-name`) ──────────────────────────────────

    /**
     * Parse `/skill-name optional args` from the composer.
     * Returns null if the text is not a slash skill invoke.
     */
    fun parseSlashInvoke(raw: String): SlashInvoke? {
        val t = raw.trim()
        if (!t.startsWith("/")) return null
        if (t.equals("/stop", ignoreCase = true)) return null
        val body = t.removePrefix("/").trim()
        if (body.isBlank()) return null
        val space = body.indexOfFirst { it.isWhitespace() }
        val namePart = if (space < 0) body else body.substring(0, space)
        val args = if (space < 0) "" else body.substring(space).trim()
        val skill = findSkill(namePart) ?: return null
        if (!skill.userInvocable) return null
        loadSkill(skill.id)
        // Full runtime activate happens in CodeAgent LOAD_SKILL / submit path with Context.
        val expanded = buildString {
            append("User invoked skill `/${skill.id}`")
            if (args.isNotBlank()) append(" with arguments: $args")
            append(".\n\n")
            append("Call LOAD_SKILL with skill_name=${skill.id}")
            if (args.isNotBlank()) append(" and arguments=$args")
            append(" immediately (or treat the skill as already requested), then follow its rendered SKILL.md.\n")
            if (skill.id == BUNDLED_SKILL_CREATOR.id) {
                append("\n")
                append(SKILL_CREATOR_RUNTIME_HINT)
                if (args.isNotBlank()) append("\nThe user wants this skill to: $args\n")
            }
        }
        return SlashInvoke(skill.id, args, expanded)
    }

    /** Detect natural-language “create a skill…” and force skill-creator load. */
    fun maybeExpandSkillCreatorIntent(raw: String): String? {
        val t = raw.trim()
        if (t.startsWith("/")) return null
        val lower = t.lowercase()
        val triggers = listOf(
            "create a skill", "create skill", "make a skill", "new skill",
            "write a skill", "generate a skill", "build a skill", "skill creator",
            "help me create a skill", "using skill-creator", "using /skill-creator"
        )
        if (triggers.none { lower.contains(it) }) return null
        loadSkill(BUNDLED_SKILL_CREATOR.id)
        return buildString {
            append(SKILL_CREATOR_PROMPT)
            append(t)
            append("\n\n")
            append(SKILL_CREATOR_RUNTIME_HINT)
        }
    }

    // ── Level 3 resources ────────────────────────────────────────────────────

    fun listResources(skillId: String): List<String> {
        val skill = findSkill(skillId) ?: return emptyList()
        return skill.resources.keys.sorted()
    }

    fun readResource(skillId: String, path: String): String? {
        val skill = findSkill(skillId) ?: return null
        val p = path.trim().removePrefix("./").replace('\\', '/')
        skill.resources[p]?.let { return it }
        // fuzzy: filename match
        skill.resources.entries.find { it.key.equals(p, true) || it.key.endsWith("/$p") }?.let {
            return it.value
        }
        return null
    }

    // ── SAVE / REPLACE (after user permission) ───────────────────────────────

    /**
     * Create or replace a **custom** skill from a full SKILL.md (+ optional resources).
     * Call only after AgentPermissionGate allows `saveskill`.
     *
     * - Cannot overwrite admin-remote skills unless [forceReplaceRemote] (never from agent).
     * - If id exists as custom and [replace] is false → error (ask replace=true).
     */
    fun saveSkillFromAgent(
        skillMd: String,
        replace: Boolean = false,
        resourcesJson: String? = null
    ): SaveResult {
        val parsed = parseSkillMd(skillMd)
            ?: return SaveResult(false, "ERROR: Invalid SKILL.md — need YAML frontmatter with name + description.")
        val resources = parseResourcesJson(resourcesJson) + parsed.resources
        val id = parsed.id
        if (id == BUNDLED_SKILL_CREATOR.id) {
            return SaveResult(false, "ERROR: Cannot overwrite the built-in skill-creator. Pick another name.")
        }
        // Admin remote catalog: only create a user custom shadow if replace, else block
        val remoteHit = ALL_SKILLS.any { it.id == id }
        val existingCustom = customSkills().find { it.id == id }
        if (remoteHit && existingCustom == null && !replace) {
            return SaveResult(
                false,
                "ERROR: Skill `$id` exists in the app library. Set replace=true to save a personal copy that overrides it for you, or choose a new name."
            )
        }
        if (existingCustom != null && !replace) {
            return SaveResult(
                false,
                "ERROR: Custom skill `$id` already exists. Set replace=true to overwrite, or use a new name."
            )
        }
        val skill = CustomSkill(
            id = id,
            name = parsed.name,
            description = parsed.description,
            content = parsed.content,
            enabled = true,
            resources = resources,
            iconSvg = existingCustom?.iconSvg.orEmpty(),
            whenToUse = parsed.whenToUse,
            disableModelInvocation = parsed.disableModelInvocation,
            userInvocable = parsed.userInvocable,
            allowedTools = parsed.allowedTools,
            argumentHint = parsed.argumentHint,
            license = parsed.license,
            compatibility = parsed.compatibility,
            metadata = parsed.metadata
        )
        saveCustomSkill(skill)
        // Materialize Claude/Codex package on disk
        runCatching { SkillRuntime.materializePackage(skill.toSkill()) }
        // Hot-reload into session if already loaded
        if (isLoaded(id) || replace) {
            loadedSkills[id] = skill.toSkill()
        }
        val replaced = existingCustom != null || (remoteHit && replace)
        return SaveResult(
            ok = true,
            message = if (replaced) {
                "SKILL REPLACED: `$id` updated. It is enabled and available via LOAD_SKILL / /$id."
            } else {
                "SKILL SAVED: `$id` created. It is enabled and available via LOAD_SKILL / /$id."
            },
            skillId = id,
            replaced = replaced
        )
    }

    private fun parseResourcesJson(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val o = JSONObject(raw.trim())
            val out = mutableMapOf<String, String>()
            val keys = o.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = o.optString(k, "")
                if (k.isNotBlank() && v.isNotBlank()) {
                    out[k.trim().removePrefix("./").replace('\\', '/')] = v
                }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ── Admin ────────────────────────────────────────────────────────────────

    fun applyAdminConfig(
        disabled: List<String>,
        whitelist: List<String>? = null,
        deleted: List<String> = emptyList(),
        overrides: Map<String, AdminSkillEdit> = emptyMap(),
        authored: List<AdminSkillEdit> = emptyList(),
        icons: Map<String, String> = emptyMap()
    ) {
        adminDisabledIds = disabled.map { normalizeId(it) }.filter { it.isNotBlank() }.toSet()
        adminDeletedIds = deleted.map { normalizeId(it) }.filter { it.isNotBlank() }.toSet()
        adminWhitelist = whitelist
            ?.map { normalizeId(it) }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        adminOverrides = overrides.mapKeys { normalizeId(it.key) }
        adminAuthored = authored.map { it.copy(id = normalizeId(it.id)) }.filter { it.id.isNotBlank() }
        adminIcons = icons.mapKeys { normalizeId(it.key) }.filterValues { it.isNotBlank() }
        adminConfigVersion++
        loadedSkills.keys.toList().forEach { id ->
            if (id != BUNDLED_SKILL_CREATOR.id && (!isAdminAllowed(id) || id in adminDeletedIds)) {
                unloadSkill(id)
            }
        }
        loadedSkills.keys.toList().forEach { id ->
            findSkill(id)?.let { loadedSkills[normalizeId(it.id)] = it }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun applyAdminConfigFromFirestore(data: Map<String, Any?>) {
        fun listField(name: String): List<String> =
            (data[name] as? List<*>)?.filterIsInstance<String>()?.map { it.trim() }?.filter { it.isNotBlank() }
                ?: emptyList()

        val disabled = listField("disabled")
        val deleted = listField("deleted")
        val whitelist = listField("whitelist").ifEmpty { null }

        val icons = mutableMapOf<String, String>()
        (data["icons"] as? Map<*, *>)?.forEach { (k, v) ->
            val key = k?.toString()?.trim().orEmpty()
            val svg = v?.toString().orEmpty()
            if (key.isNotBlank() && svg.isNotBlank()) icons[key] = svg
        }

        val overrides = mutableMapOf<String, AdminSkillEdit>()
        (data["overrides"] as? Map<*, *>)?.forEach { (k, v) ->
            val id = k?.toString()?.trim().orEmpty()
            val m = v as? Map<*, *> ?: return@forEach
            if (id.isBlank()) return@forEach
            overrides[id] = AdminSkillEdit(
                id = id,
                name = m["name"]?.toString().orEmpty(),
                description = m["description"]?.toString().orEmpty(),
                content = m["content"]?.toString().orEmpty(),
                iconSvg = m["iconSvg"]?.toString().orEmpty(),
                isAdminAuthored = false,
                enabled = (m["enabled"] as? Boolean) ?: true,
                resources = (m["resources"] as? Map<*, *>)?.mapNotNull { (rk, rv) ->
                    val key = rk?.toString() ?: return@mapNotNull null
                    val value = rv?.toString() ?: return@mapNotNull null
                    key to value
                }?.toMap().orEmpty(),
                whenToUse = m["when_to_use"]?.toString().orEmpty(),
                disableModelInvocation = (m["disable_model_invocation"] as? Boolean) ?: false,
                userInvocable = (m["user_invocable"] as? Boolean) ?: true
            )
            m["iconSvg"]?.toString()?.takeIf { it.isNotBlank() }?.let { icons[id] = it }
        }

        val authored = mutableListOf<AdminSkillEdit>()
        (data["adminSkills"] as? List<*>)?.forEach { item ->
            val m = item as? Map<*, *> ?: return@forEach
            val id = m["id"]?.toString()?.trim().orEmpty()
            if (id.isBlank()) return@forEach
            val svg = m["iconSvg"]?.toString().orEmpty()
            if (svg.isNotBlank()) icons[id] = svg
            authored.add(
                AdminSkillEdit(
                    id = id,
                    name = m["name"]?.toString().orEmpty(),
                    description = m["description"]?.toString().orEmpty(),
                    content = m["content"]?.toString().orEmpty(),
                    iconSvg = svg,
                    isAdminAuthored = true,
                    enabled = (m["enabled"] as? Boolean) ?: true,
                    resources = (m["resources"] as? Map<*, *>)?.mapNotNull { (rk, rv) ->
                        val key = rk?.toString() ?: return@mapNotNull null
                        val value = rv?.toString() ?: return@mapNotNull null
                        key to value
                    }?.toMap().orEmpty(),
                    whenToUse = m["when_to_use"]?.toString().orEmpty(),
                    disableModelInvocation = (m["disable_model_invocation"] as? Boolean) ?: false,
                    userInvocable = (m["user_invocable"] as? Boolean) ?: true
                )
            )
        }

        applyAdminConfig(disabled, whitelist, deleted, overrides, authored, icons)
    }

    fun isAdminAllowed(id: String): Boolean {
        val n = normalizeId(id)
        if (n == BUNDLED_SKILL_CREATOR.id) return true
        if (n in adminDeletedIds) return false
        if (n in adminDisabledIds) return false
        val ov = adminOverrides[n]
        if (ov != null && !ov.enabled) return false
        val authored = adminAuthored.find { it.id == n }
        if (authored != null && !authored.enabled) return false
        val wl = adminWhitelist
        if (wl != null && n !in wl && authored == null) return false
        return true
    }

    fun adminDisabledSnapshot(): Set<String> = adminDisabledIds
    fun adminDeletedSnapshot(): Set<String> = adminDeletedIds
    fun adminWhitelistSnapshot(): Set<String>? = adminWhitelist
    fun adminOverridesSnapshot(): Map<String, AdminSkillEdit> = adminOverrides
    fun adminAuthoredSnapshot(): List<AdminSkillEdit> = adminAuthored
    fun adminIconsSnapshot(): Map<String, String> = adminIcons

    fun getIconSvg(id: String): String {
        val n = normalizeId(id)
        adminIcons[n]?.takeIf { it.isNotBlank() }?.let { return it }
        adminOverrides[n]?.iconSvg?.takeIf { it.isNotBlank() }?.let { return it }
        adminAuthored.find { it.id == n }?.iconSvg?.takeIf { it.isNotBlank() }?.let { return it }
        return findSkill(n)?.iconSvg.orEmpty()
    }

    fun shouldHideSkillBodyInUi(id: String): Boolean {
        val n = normalizeId(id)
        if (customSkills().any { it.id == n }) return false
        return true
    }

    private fun applyOverride(skill: Skill): Skill {
        val n = normalizeId(skill.id)
        val ov = adminOverrides[n]
        val svg = adminIcons[n].orEmpty().ifBlank { ov?.iconSvg.orEmpty() }.ifBlank { skill.iconSvg }
        if (ov == null && svg == skill.iconSvg) return skill
        return skill.copy(
            name = ov?.name?.takeIf { it.isNotBlank() } ?: skill.name,
            description = ov?.description?.takeIf { it.isNotBlank() } ?: skill.description,
            content = ov?.content?.takeIf { it.isNotBlank() } ?: skill.content,
            iconSvg = svg,
            resources = ov?.resources?.takeIf { it.isNotEmpty() } ?: skill.resources,
            whenToUse = ov?.whenToUse?.takeIf { it.isNotBlank() } ?: skill.whenToUse,
            disableModelInvocation = ov?.disableModelInvocation ?: skill.disableModelInvocation,
            userInvocable = ov?.userInvocable ?: skill.userInvocable
        )
    }

    // ── Built-in enable/disable ──────────────────────────────────────────────

    fun disabledBuiltins(): Set<String> {
        val raw = prefs?.getString(KEY_DISABLED_BUILTINS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun isBuiltinEnabled(id: String): Boolean =
        isAdminAllowed(id) && normalizeId(id) !in disabledBuiltins()

    fun setBuiltinEnabled(id: String, enabled: Boolean) {
        val n = normalizeId(id)
        if (enabled && !isAdminAllowed(n)) return
        val set = disabledBuiltins().toMutableSet()
        if (enabled) set.remove(n) else set.add(n)
        prefs?.edit()?.putString(KEY_DISABLED_BUILTINS, JSONArray(set.toList()).toString())?.apply()
        if (!enabled) unloadSkill(n)
        scheduleSync()
    }

    fun isEnabled(id: String): Boolean {
        val n = normalizeId(id)
        if (n == BUNDLED_SKILL_CREATOR.id) return n !in disabledBuiltins()
        ALL_SKILLS.find { it.id == n }?.let {
            return isAdminAllowed(n) && n !in disabledBuiltins()
        }
        return customSkills().find { it.id == n }?.enabled ?: false
    }

    // ── Custom CRUD ──────────────────────────────────────────────────────────

    fun customSkills(): List<CustomSkill> {
        val raw = prefs?.getString(KEY_CUSTOM, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val skillMd = o.optString("skill_md", "")
                val resources = jsonObjectToStringMap(o.optJSONObject("resources"))
                if (skillMd.isNotBlank()) {
                    parseSkillMd(skillMd)?.let { parsed ->
                        return@mapNotNull CustomSkill(
                            id = o.optString("id").ifBlank { parsed.id },
                            name = o.optString("display_name").ifBlank { humanize(parsed.id) },
                            description = parsed.description,
                            content = parsed.content,
                            enabled = o.optBoolean("enabled", true),
                            resources = resources.ifEmpty { parsed.resources },
                            iconSvg = o.optString("iconSvg", ""),
                            whenToUse = parsed.whenToUse,
                            disableModelInvocation = parsed.disableModelInvocation,
                            userInvocable = parsed.userInvocable,
                            allowedTools = parsed.allowedTools,
                            argumentHint = parsed.argumentHint,
                            license = parsed.license,
                            compatibility = parsed.compatibility,
                            metadata = parsed.metadata
                        )
                    }
                }
                CustomSkill(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    description = o.optString("description"),
                    content = o.optString("content"),
                    enabled = o.optBoolean("enabled", true),
                    resources = resources,
                    iconSvg = o.optString("iconSvg", ""),
                    whenToUse = o.optString("when_to_use", ""),
                    disableModelInvocation = o.optBoolean("disable_model_invocation", false),
                    userInvocable = o.optBoolean("user_invocable", true),
                    allowedTools = o.optString("allowed_tools", ""),
                    argumentHint = o.optString("argument_hint", "")
                ).takeIf { it.id.isNotBlank() && it.name.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun jsonObjectToStringMap(o: JSONObject?): Map<String, String> {
        if (o == null) return emptyMap()
        val out = mutableMapOf<String, String>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = o.optString(k, "")
            if (k.isNotBlank() && v.isNotBlank()) out[k] = v
        }
        return out
    }

    fun saveCustomSkill(skill: CustomSkill) {
        val list = customSkills().toMutableList()
        val normalized = skill.copy(
            id = sanitizeName(skill.id.ifBlank { slugify(skill.name) }),
            description = skill.description.take(1024),
            content = skill.content.trim()
        )
        val idx = list.indexOfFirst { it.id == normalized.id }
        if (idx >= 0) list[idx] = normalized else list.add(0, normalized)
        persistCustom(list)
    }

    fun setCustomEnabled(id: String, enabled: Boolean) {
        persistCustom(customSkills().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        })
        if (!enabled) unloadSkill(id)
    }

    fun deleteCustomSkill(id: String) {
        persistCustom(customSkills().filter { it.id != id })
        unloadSkill(id)
    }

    fun createCustomSkill(
        name: String,
        description: String,
        content: String,
        enabled: Boolean = true,
        resources: Map<String, String> = emptyMap()
    ): CustomSkill {
        val id = sanitizeName(slugify(name))
        val finalId = if (
            ALL_SKILLS.any { it.id == id } ||
            customSkills().any { it.id == id } ||
            id == BUNDLED_SKILL_CREATOR.id
        ) {
            sanitizeName("$id-${UUID.randomUUID().toString().take(4)}")
        } else id
        val skill = CustomSkill(
            id = finalId,
            name = name.trim().ifBlank { humanize(finalId) },
            description = description.trim().ifBlank {
                "Custom skill: ${name.trim()}. Use when the user asks about this topic."
            }.take(1024),
            content = content.trim().ifBlank {
                """
                # ${name.trim()}

                You are an expert for this skill. Follow the user's request carefully.
                Produce high-quality, structured results.
                """.trimIndent()
            },
            enabled = enabled,
            resources = resources
        )
        saveCustomSkill(skill)
        return skill
    }

    fun importSkillMd(skillMd: String, enabled: Boolean = true): CustomSkill? {
        val parsed = parseSkillMd(skillMd) ?: return null
        val skill = CustomSkill(
            id = parsed.id,
            name = humanize(parsed.id),
            description = parsed.description,
            content = parsed.content,
            enabled = enabled,
            resources = parsed.resources,
            whenToUse = parsed.whenToUse,
            disableModelInvocation = parsed.disableModelInvocation,
            userInvocable = parsed.userInvocable,
            allowedTools = parsed.allowedTools,
            argumentHint = parsed.argumentHint,
            license = parsed.license,
            compatibility = parsed.compatibility,
            metadata = parsed.metadata
        )
        saveCustomSkill(skill)
        return skill
    }

    private fun persistCustom(list: List<CustomSkill>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("display_name", s.name)
                put("description", s.description)
                put("content", s.content)
                put("enabled", s.enabled)
                put("iconSvg", s.iconSvg)
                put("when_to_use", s.whenToUse)
                put("disable_model_invocation", s.disableModelInvocation)
                put("user_invocable", s.userInvocable)
                put("allowed_tools", s.allowedTools)
                put("argument_hint", s.argumentHint)
                put("skill_md", s.toSkill().toSkillMd())
                put("resources", JSONObject().also { jo ->
                    s.resources.forEach { (k, v) -> jo.put(k, v) }
                })
            })
        }
        prefs?.edit()?.putString(KEY_CUSTOM, arr.toString())?.apply()
        // Keep on-disk packages in sync (scripts/references/assets)
        list.forEach { runCatching { SkillRuntime.materializePackage(it.toSkill()) } }
        scheduleSync()
    }

    // ── Cloud sync ───────────────────────────────────────────────────────────

    fun scheduleSync() {
        val uid = AuthManager.uid() ?: return
        val customJson = prefs?.getString(KEY_CUSTOM, "[]") ?: "[]"
        val disabledJson = prefs?.getString(KEY_DISABLED_BUILTINS, "[]") ?: "[]"
        syncScope.launch {
            runCatching {
                com.google.android.gms.tasks.Tasks.await(
                    com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(uid)
                        .collection("data").document("skills")
                        .set(
                            mapOf(
                                "custom" to customJson,
                                "disabledBuiltins" to disabledJson,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        ),
                    6, java.util.concurrent.TimeUnit.SECONDS
                )
            }
        }
    }

    fun pullFromCloudAsync() {
        syncScope.launch { runCatching { pullFromCloud() } }
    }

    suspend fun pullFromCloud() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val uid = AuthManager.uid() ?: return@withContext
        runCatching {
            val snap = com.google.android.gms.tasks.Tasks.await(
                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(uid)
                    .collection("data").document("skills").get(),
                6, java.util.concurrent.TimeUnit.SECONDS
            )
            if (snap != null && snap.exists()) {
                val custom = snap.getString("custom")
                val disabled = snap.getString("disabledBuiltins")
                prefs?.edit()?.apply {
                    if (!custom.isNullOrBlank()) putString(KEY_CUSTOM, custom)
                    if (!disabled.isNullOrBlank()) putString(KEY_DISABLED_BUILTINS, disabled)
                }?.apply()
            } else {
                scheduleSync()
            }
        }
        Unit
    }

    // ── SKILL.md parse ───────────────────────────────────────────────────────

    fun parseSkillMd(raw: String): Skill? {
        val text = raw.trim()
        if (!text.startsWith("---")) {
            val title = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(text)?.groupValues?.get(1)?.trim()
                ?: return null
            val id = sanitizeName(slugify(title))
            return Skill(
                id = id,
                name = title,
                description = "Custom skill: $title. Use when relevant to the user's task.",
                content = text,
                isCustom = true,
                isBuiltin = false
            )
        }
        val end = text.indexOf("---", 3)
        if (end < 0) return null
        val fm = text.substring(3, end).trim()
        val body = text.substring(end + 3).trim()

        var name = ""
        var description = ""
        var whenToUse = ""
        var license: String? = null
        var compatibility: String? = null
        var disableModelInvocation = false
        var userInvocable = true
        var allowedTools = ""
        var argumentHint = ""
        val meta = mutableMapOf<String, String>()
        var inDesc = false
        var inWhen = false
        var inMeta = false
        val descLines = mutableListOf<String>()
        val whenLines = mutableListOf<String>()

        fun flushDesc() {
            if (descLines.isNotEmpty()) description = descLines.joinToString(" ").trim()
            descLines.clear()
            inDesc = false
        }
        fun flushWhen() {
            if (whenLines.isNotEmpty()) whenToUse = whenLines.joinToString(" ").trim()
            whenLines.clear()
            inWhen = false
        }

        for (line in fm.lines()) {
            val t = line.trimEnd()
            when {
                t.startsWith("name:") -> {
                    flushDesc(); flushWhen(); inMeta = false
                    name = t.removePrefix("name:").trim().trim('"', '\'')
                }
                t.startsWith("description:") -> {
                    flushWhen(); inMeta = false; inDesc = true
                    val rest = t.removePrefix("description:").trim()
                    when {
                        rest == ">" || rest == "|" -> descLines.clear()
                        rest.isNotBlank() -> {
                            description = rest.trim('"', '\'')
                            inDesc = false
                        }
                        else -> descLines.clear()
                    }
                }
                t.startsWith("when_to_use:") || t.startsWith("when-to-use:") -> {
                    flushDesc(); inMeta = false; inWhen = true
                    val rest = t.substringAfter(":").trim()
                    when {
                        rest == ">" || rest == "|" -> whenLines.clear()
                        rest.isNotBlank() -> {
                            whenToUse = rest.trim('"', '\'')
                            inWhen = false
                        }
                        else -> whenLines.clear()
                    }
                }
                t.startsWith("disable-model-invocation:") || t.startsWith("disable_model_invocation:") -> {
                    flushDesc(); flushWhen(); inMeta = false
                    disableModelInvocation = t.substringAfter(":").trim().equals("true", true)
                }
                t.startsWith("user-invocable:") || t.startsWith("user_invocable:") -> {
                    flushDesc(); flushWhen(); inMeta = false
                    userInvocable = !t.substringAfter(":").trim().equals("false", true)
                }
                t.startsWith("allowed-tools:") || t.startsWith("allowed_tools:") -> {
                    flushDesc(); flushWhen(); inMeta = false
                    allowedTools = t.substringAfter(":").trim().trim('"', '\'')
                }
                t.startsWith("argument-hint:") || t.startsWith("argument_hint:") -> {
                    flushDesc(); flushWhen(); inMeta = false
                    argumentHint = t.substringAfter(":").trim().trim('"', '\'')
                }
                t.startsWith("license:") -> {
                    flushDesc(); flushWhen(); inMeta = false
                    license = t.removePrefix("license:").trim().trim('"', '\'')
                }
                t.startsWith("compatibility:") -> {
                    flushDesc(); flushWhen(); inMeta = false
                    compatibility = t.removePrefix("compatibility:").trim().trim('"', '\'')
                }
                t.startsWith("metadata:") -> {
                    flushDesc(); flushWhen(); inMeta = true
                }
                inDesc && (t.startsWith("  ") || t.startsWith("\t")) -> descLines.add(t.trim())
                inWhen && (t.startsWith("  ") || t.startsWith("\t")) -> whenLines.add(t.trim())
                inMeta && t.contains(":") -> {
                    val k = t.substringBefore(":").trim()
                    val v = t.substringAfter(":").trim().trim('"', '\'')
                    if (k.isNotBlank()) meta[k] = v
                }
                inDesc && t.isBlank() -> { }
                inWhen && t.isBlank() -> { }
                inDesc -> flushDesc()
                inWhen -> flushWhen()
            }
        }
        flushDesc(); flushWhen()

        name = sanitizeName(name)
        if (name.isBlank() || description.isBlank()) return null
        description = description.take(1024)

        val display = Regex("^#\\s+(.+)$", RegexOption.MULTILINE).find(body)?.groupValues?.get(1)?.trim()
            ?: humanize(name)

        // Optional embedded resource fences: <!-- resource:path/file.md --> ... <!-- /resource -->
        val resources = mutableMapOf<String, String>()
        var cleanBody = body
        val resRe = Regex(
            "<!--\\s*resource:([^>]+?)\\s*-->\\s*([\\s\\S]*?)\\s*<!--\\s*/resource\\s*-->",
            RegexOption.IGNORE_CASE
        )
        resRe.findAll(body).forEach { m ->
            val path = m.groupValues[1].trim().removePrefix("./")
            val content = m.groupValues[2].trim()
            if (path.isNotBlank() && content.isNotBlank()) resources[path] = content
        }
        if (resources.isNotEmpty()) {
            cleanBody = resRe.replace(body, "").trim()
        }

        return Skill(
            id = name,
            name = display,
            description = description,
            content = cleanBody.ifBlank { "# $display\n" },
            isCustom = true,
            isBuiltin = false,
            license = license,
            compatibility = compatibility,
            metadata = meta,
            resources = resources,
            whenToUse = whenToUse,
            disableModelInvocation = disableModelInvocation,
            userInvocable = userInvocable,
            allowedTools = allowedTools,
            argumentHint = argumentHint
        )
    }

    fun validateSkillName(name: String): String? {
        val n = name.trim()
        if (n.isEmpty()) return "Name is required"
        if (n.length > 64) return "Name max 64 characters"
        if (!n.matches(Regex("^[a-z0-9]+(-[a-z0-9]+)*$"))) {
            return "Use lowercase letters, numbers, hyphens only (e.g. my-skill)"
        }
        return null
    }

    fun sanitizeName(raw: String): String {
        val s = raw.trim().lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(64)
        return s.ifBlank { "skill" }
    }

    fun slugify(raw: String): String = sanitizeName(raw)

    fun humanize(id: String): String =
        id.split('-').joinToString(" ") { part ->
            part.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

    private fun normalizeId(raw: String): String = sanitizeName(raw)

    // ── Skill creator (always bundled) ───────────────────────────────────────

    const val SKILL_CREATOR_PROMPT =
        "Help me create a skill using skill-creator (Agent Skills / SKILL.md standard from agentskills.io, " +
            "same as Claude Code and OpenAI Codex). " +
            "Ask follow-up questions if needed. Output a complete SKILL.md with YAML frontmatter " +
            "(required: name as kebab-case, description that says WHAT + WHEN to use it) and a Markdown body " +
            "with step-by-step instructions. When the skill is ready, call SAVE_SKILL with the full markdown " +
            "(and replace=true if updating an existing skill). This skill should: "

    private const val SKILL_CREATOR_RUNTIME_HINT =
        """After drafting SKILL.md:
1. Show the user a short summary (name, when to use, key steps).
2. Call SAVE_SKILL with skill_md set to the COMPLETE SKILL.md document.
3. Set replace=true only when updating an existing skill id; otherwise replace=false.
4. Optional: pass resources_json as a JSON object of path→file text for references/ or scripts/.
5. The app will ask the user to Allow before saving — wait for that; do not claim it is saved until SAVE_SKILL succeeds.
"""

    val BUNDLED_SKILL_CREATOR: Skill = Skill(
        id = "skill-creator",
        name = "Skill Creator",
        description = "Create, refine, and save Agent Skills (SKILL.md) for Claude Code / Codex style progressive disclosure. " +
            "Use when the user wants a new skill, to improve a skill, or types /skill-creator.",
        content = """
# Skill Creator

You create high-quality Agent Skills compatible with Claude Code, OpenAI Codex, and AhamAI.

## Package layout (on disk)
```
skills/<name>/SKILL.md
skills/<name>/scripts/      # RUN_SKILL_SCRIPT
skills/<name>/references/   # READ_SKILL_RESOURCE
skills/<name>/assets/
```

## Output format
```markdown
---
name: kebab-case-name
description: >-
  WHAT it does and WHEN to use it.
when_to_use: >-
  Optional triggers.
allowed-tools: Read Grep
# context: fork
---

# Title
## Instructions
1. Steps… Use ${'$'}ARGUMENTS / ${'$'}0 for slash args.
2. Dynamic data line: !`command` is expanded before the model sees it.

<!-- resource:scripts/helper.py -->
print("ok")
<!-- /resource -->
```

## Rules
- kebab-case name; description = WHAT + WHEN
- Prefer scripts/ for code; references/ for long docs
- Call **SAVE_SKILL** when ready (user must Allow)

## SAVE_SKILL
skill_md=<full SKILL.md> replace=false resources_json={"scripts/x.py":"..."}
""".trimIndent(),
        isCustom = false,
        isBuiltin = true,
        category = "Built-in",
        metadata = mapOf("author" to "ahamai", "version" to "3.0"),
        whenToUse = "User asks to create/make/write a skill, or types /skill-creator.",
        userInvocable = true,
        disableModelInvocation = false
    )

    private val ALIASES = mapOf(
        "word-documents" to "docx",
        "word" to "docx",
        "pdfs" to "pdf",
        "presentations" to "pptx",
        "powerpoint" to "pptx",
        "spreadsheets" to "xlsx",
        "excel" to "xlsx",
        "fullstack-dev" to "fullstack",
        "full-stack" to "fullstack",
        "image-generation" to "icon-logo",
        "skill-creator" to "skill-creator",
        "cybersecurity" to "sql",
        "cyber-security" to "sql",
        "cyber" to "sql",
        "sqli" to "sql",
        "resume-builder" to "resume",
        "video-generation" to "video"
    )

    /**
     * Remote catalog skills (admin) — no code-shipped bodies except [BUNDLED_SKILL_CREATOR].
     */
    val ALL_SKILLS: List<Skill>
        get() {
            val overrideOnly = adminOverrides.values
                .filter { it.id !in adminDeletedIds && it.enabled }
                .filter { ov -> adminAuthored.none { it.id == ov.id } }
                .filter { it.id != BUNDLED_SKILL_CREATOR.id }
                .map { ov ->
                    Skill(
                        id = ov.id,
                        name = ov.name.ifBlank { humanize(ov.id) },
                        description = ov.description.ifBlank { "Remote skill: ${ov.name.ifBlank { ov.id }}" },
                        content = ov.content,
                        isCustom = false,
                        isBuiltin = false,
                        category = "Library",
                        metadata = mapOf("author" to "admin", "version" to "1.0"),
                        iconSvg = ov.iconSvg.ifBlank { adminIcons[ov.id].orEmpty() },
                        resources = ov.resources,
                        whenToUse = ov.whenToUse,
                        disableModelInvocation = ov.disableModelInvocation,
                        userInvocable = ov.userInvocable
                    )
                }
            val authored = adminAuthored
                .filter { it.id !in adminDeletedIds }
                .filter { it.id != BUNDLED_SKILL_CREATOR.id }
                .map { a ->
                    val ov = adminOverrides[a.id]
                    Skill(
                        id = a.id,
                        name = (ov?.name?.takeIf { it.isNotBlank() } ?: a.name).ifBlank { humanize(a.id) },
                        description = (ov?.description?.takeIf { it.isNotBlank() } ?: a.description)
                            .ifBlank { "Admin skill: ${a.name.ifBlank { a.id }}" },
                        content = ov?.content?.takeIf { it.isNotBlank() } ?: a.content,
                        isCustom = false,
                        isBuiltin = false,
                        category = "Library",
                        metadata = mapOf("author" to "admin", "version" to "1.0"),
                        iconSvg = a.iconSvg.ifBlank {
                            ov?.iconSvg.orEmpty()
                        }.ifBlank { adminIcons[a.id].orEmpty() },
                        resources = ov?.resources?.takeIf { it.isNotEmpty() } ?: a.resources,
                        whenToUse = ov?.whenToUse?.takeIf { it.isNotBlank() } ?: a.whenToUse,
                        disableModelInvocation = ov?.disableModelInvocation ?: a.disableModelInvocation,
                        userInvocable = ov?.userInvocable ?: a.userInvocable
                    )
                }
            return authored + overrideOnly
        }

    fun byId(id: String): Skill? = findSkill(id)
}
