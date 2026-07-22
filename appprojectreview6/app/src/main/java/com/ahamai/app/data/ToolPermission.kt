package com.ahamai.app.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Grok-style tool permission modes (lean).
 *
 * [READONLY]  — only read/search tools
 * [ASK]       — safe auto; risky tools prompt (default)
 * [ACCEPT_EDITS] — file edits auto; shell/cloud/push/delete still ask
 * [AUTO]      — almost everything auto (secret-path deny still applies)
 * [FULL]      — zero restrictions: no asks, no denies (power user / trusted only)
 */
enum class PermissionMode(val id: String, val label: String, val blurb: String) {
    READONLY("readonly", "Read only", "Read & search only — no edits or shell"),
    ASK("ask", "Ask", "Confirm risky tools before they run"),
    ACCEPT_EDITS("accept_edits", "Accept edits", "File edits free; shell/push still ask"),
    AUTO("auto", "Auto", "Run freely (secret paths still blocked)"),
    FULL("full", "Full", "No restrictions — every tool runs with no prompts");

    companion object {
        fun fromId(raw: String?): PermissionMode {
            val s = raw?.trim()?.lowercase().orEmpty()
            // Aliases for full unrestricted mode
            if (s in setOf("full", "full_permission", "full_access", "unrestricted", "yolo", "bypass")) {
                return FULL
            }
            return entries.firstOrNull { it.id == s } ?: ASK
        }
    }
}

enum class ToolRisk { READ, EDIT, RISKY }

enum class PermissionVerdict { ALLOW, DENY, ASK }

data class PermissionRequest(
    val action: String,
    val path: String = "",
    val detail: String = "",
    val reason: String = "",
    val risk: ToolRisk = ToolRisk.RISKY
) {
    fun title(): String = when (risk) {
        ToolRisk.READ -> "Allow read?"
        ToolRisk.EDIT -> "Allow edit?"
        ToolRisk.RISKY -> "Allow risky action?"
    }

    fun body(): String = buildString {
        append(action.uppercase())
        if (path.isNotBlank()) append(" · ").append(path)
        if (detail.isNotBlank()) append("\n").append(detail.take(120))
        if (reason.isNotBlank()) append("\n").append(reason)
    }
}

/**
 * Stateless classify + decide. Remembered grants are per-project in [grants].
 */
object ToolPermission {
    private val grants = ConcurrentHashMap<String, MutableSet<String>>() // projectKey → action keys

    private val READ = setOf(
        "read", "readlines", "readfiles", "list", "grep", "search", "glob", "codebasesearch",
        "symbolsearch", "gotodefinition", "findreferences", "documentsymbols", "workspacesymbols",
        "hover", "fetch", "http", "websearch", "imagesearch", "readurl", "repomap", "diffhistory",
        "memory", "plan", "completestep", "answer", "done", "askuser", "loadtools", "loadskill",
        "listskillresources", "readskillresource",
        "worklog", "remember", "forget", "filechip", "previewweb", "analyzeimage", "screenshot",
        "jobstatus", "ghstatus", "ghbuildstatus", "ghlogs", "ghlistremote", "ghreadremote",
        "ghrepos", "ghbranches", "ghissues", "listartifacts", "listdownloads",
        // Offline static only — never local Gradle
        "verify", "verifybuild", "diagnose"
    )

    // Script execution is edit/risky depending on shell; treat as RISKY by default.

    private val EDIT = setOf(
        "edit", "write", "create", "insertlines", "multiedit", "applypatch", "importadd",
        "bulkedit", "copy", "move", "refactorrename", "formatcode", "depsadd"
    )

    /** Always ask (unless AUTO and not denied) — high blast radius. */
    private val RISKY = setOf(
        "delete", "cloudshell", "runpython", "runjob", "cloudinstall", "cloudpush", "cloudpull",
        "ghpush", "ghpr", "ghbuild", "ghcreaterepo", "ghcreatebranch", "ghswitchbranch",
        "ghopenrepo", "browseropen", "browserclick", "browsertype", "computeropen",
        "computerclick", "computertype", "siteclone", "unzip", "download", "http",
        "task", "paralleltasks",
        // Skills: writing/replacing a skill must get user permission (Claude-style trust boundary)
        "saveskill",
        // Package scripts can run arbitrary code
        "runskillscript"
    )

    private val SECRET_HINTS = listOf(
        ".env", "credentials", "secret", "keystore", ".pem", ".p12", "google-services.json",
        "id_rsa", "private_key", "api_key"
    )

    fun riskOf(action: String): ToolRisk {
        val a = action.lowercase().trim()
        return when {
            a in READ || a.startsWith("read") -> ToolRisk.READ
            a in EDIT -> ToolRisk.EDIT
            a in RISKY || a.startsWith("cloud") || a.startsWith("browser") ||
                a.startsWith("computer") || a.startsWith("gh") -> ToolRisk.RISKY
            else -> ToolRisk.EDIT // unknown mutators default to edit-level caution
        }
    }

    fun isSecretPath(path: String): Boolean {
        val p = path.lowercase()
        return SECRET_HINTS.any { p.contains(it) }
    }

    fun decide(
        mode: PermissionMode,
        action: String,
        path: String = "",
        projectKey: String = ""
    ): PermissionVerdict {
        // Full = zero policy: no deny list, no prompts, no grants needed.
        if (mode == PermissionMode.FULL) return PermissionVerdict.ALLOW

        val a = action.lowercase().trim()
        if (a.isEmpty() || a in setOf("answer", "done", "plan", "completestep")) {
            return PermissionVerdict.ALLOW
        }
        // SAVE_SKILL always requires explicit user Allow (except Full mode) — never silent write.
        if (a == "saveskill") {
            return if (mode == PermissionMode.FULL) PermissionVerdict.ALLOW else PermissionVerdict.ASK
        }
        // Claude-style skill disallowed-tools while a skill session is active
        if (SkillRuntime.isToolDisallowed(a)) {
            return PermissionVerdict.DENY
        }
        // Skill allowed-tools pre-approval for this user turn
        if (SkillRuntime.isToolPreapproved(a)) {
            return PermissionVerdict.ALLOW
        }
        // Hard deny: secret paths on mutate (skipped only in FULL)
        val risk = riskOf(a)
        if (risk != ToolRisk.READ && isSecretPath(path)) return PermissionVerdict.DENY
        if (a == "delete" && (path.isBlank() || path == "." || path == "/")) {
            return PermissionVerdict.DENY
        }

        val grantKey = "$a|${path.trim()}"
        if (projectKey.isNotBlank() && grants[projectKey]?.contains(grantKey) == true) {
            return PermissionVerdict.ALLOW
        }
        if (projectKey.isNotBlank() && grants[projectKey]?.contains(a) == true) {
            return PermissionVerdict.ALLOW
        }

        return when (mode) {
            PermissionMode.READONLY ->
                if (risk == ToolRisk.READ) PermissionVerdict.ALLOW else PermissionVerdict.DENY
            PermissionMode.ASK -> when (risk) {
                ToolRisk.READ -> PermissionVerdict.ALLOW
                ToolRisk.EDIT, ToolRisk.RISKY -> PermissionVerdict.ASK
            }
            PermissionMode.ACCEPT_EDITS -> when (risk) {
                ToolRisk.READ, ToolRisk.EDIT -> PermissionVerdict.ALLOW
                ToolRisk.RISKY -> PermissionVerdict.ASK
            }
            PermissionMode.AUTO -> PermissionVerdict.ALLOW
            PermissionMode.FULL -> PermissionVerdict.ALLOW // already handled above
        }
    }

    fun remember(projectKey: String, action: String, path: String = "", wholeAction: Boolean = false) {
        if (projectKey.isBlank()) return
        val set = grants.getOrPut(projectKey) { ConcurrentHashMap.newKeySet() }
        if (wholeAction) set.add(action.lowercase())
        else set.add("${action.lowercase()}|${path.trim()}")
    }

    fun clearGrants(projectKey: String) {
        grants.remove(projectKey)
    }

    fun denyMessage(action: String, path: String, mode: PermissionMode): String {
        return when {
            mode == PermissionMode.FULL ->
                "DENIED: unexpected block in Full mode." // should never hit
            isSecretPath(path) ->
                "DENIED: path looks like a secret ($path). Switch to Full mode to override, or use a different path."
            mode == PermissionMode.READONLY ->
                "DENIED: permission mode is Read only — cannot run ${action.uppercase()}."
            else ->
                "DENIED: ${action.uppercase()} blocked by permission policy."
        }
    }

    fun reasonForAsk(action: String, risk: ToolRisk): String {
        val a = action.lowercase().trim()
        if (a == "saveskill") {
            return "Save or replace a custom skill on this device (Agent Skills / SKILL.md)."
        }
        return when (risk) {
            ToolRisk.EDIT -> "This will change project files."
            ToolRisk.RISKY -> "This can run shell, delete, push, or open network/browser."
            ToolRisk.READ -> "Confirm read access."
        }
    }

    fun loadMode(context: Context): PermissionMode =
        PermissionMode.fromId(PreferencesManager(context).getAgentPermissionMode())

    fun saveMode(context: Context, mode: PermissionMode) {
        PreferencesManager(context).saveAgentPermissionMode(mode.id)
    }
}
