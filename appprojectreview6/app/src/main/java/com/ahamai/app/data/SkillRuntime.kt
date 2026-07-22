package com.ahamai.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Claude Code / OpenAI Codex skill **runtime** layer on top of [SkillManager].
 *
 * Adds what the catalog alone cannot do:
 *  - On-disk skill packages (`skills/<id>/SKILL.md` + scripts/ references/ assets/)
 *  - Project discovery (`.claude/skills`, `.codex/skills`, `.ahamai/skills`)
 *  - Progressive render: `$ARGUMENTS` / `$N` / named args / `${CLAUDE_SKILL_DIR}`
 *  - Dynamic inject: `` !`command` `` and fenced ` ```! ` blocks (shell preprocess)
 *  - `allowed-tools` turn grant (clears on next user message, Claude-style)
 *  - `RUN_SKILL_SCRIPT` for package scripts
 *  - `context: fork` → run skill body as a sub-agent task prompt
 */
object SkillRuntime {
    private const val TAG = "SkillRuntime"

    data class ActiveSession(
        val skillId: String,
        val allowedTools: Set<String>,
        val disallowedTools: Set<String>,
        val arguments: String,
        val skillDir: String,
        val projectDir: String,
        val contextFork: Boolean,
        val agentType: String,
        val renderedBody: String
    )

    /** Currently active skill grant (cleared on next user message). */
    private val session = AtomicReference<ActiveSession?>(null)

    /** Project-local discovered skills (refreshed when project changes). */
    private val projectSkills = ConcurrentHashMap<String, SkillManager.Skill>()

    @Volatile
    private var appFilesDir: File? = null

    @Volatile
    var activeProjectDir: String = ""
        private set

    fun init(context: Context) {
        if (appFilesDir == null) {
            appFilesDir = File(context.applicationContext.filesDir, "skills").also { it.mkdirs() }
        }
        SkillManager.init(context)
    }

    fun globalSkillsRoot(): File =
        appFilesDir ?: File("/data/local/tmp/ahamai_skills").also { it.mkdirs() }

    fun packageDir(skillId: String): File {
        val id = SkillManager.sanitizeName(skillId)
        return File(globalSkillsRoot(), id)
    }

    fun setProjectContext(projectDir: String) {
        activeProjectDir = projectDir.trim()
        refreshProjectSkills()
    }

    fun clearTurnGrant() {
        session.set(null)
    }

    fun currentSession(): ActiveSession? = session.get()

    fun isToolPreapproved(action: String): Boolean {
        val s = session.get() ?: return false
        val a = action.lowercase().trim()
        if (a in s.disallowedTools) return false
        if (s.allowedTools.isEmpty()) return false
        // Match "read", "READ_FILE", "Bash(git *)" style loosely
        return s.allowedTools.any { token ->
            val t = token.lowercase()
            a == t || a.startsWith(t) || t.contains(a) ||
                t.substringBefore('(').trim() == a ||
                (t.startsWith("bash") && (a == "cloudshell" || a == "runpython" || a == "runskillscript"))
        }
    }

    fun isToolDisallowed(action: String): Boolean {
        val s = session.get() ?: return false
        val a = action.lowercase().trim()
        return s.disallowedTools.any { token ->
            val t = token.lowercase()
            a == t || a.startsWith(t) || t.substringBefore('(').trim() == a
        }
    }

    // ── Discovery ────────────────────────────────────────────────────────────

    fun refreshProjectSkills() {
        projectSkills.clear()
        val root = activeProjectDir
        if (root.isBlank()) return
        val bases = listOf(
            File(root, ".claude/skills"),
            File(root, ".codex/skills"),
            File(root, ".ahamai/skills")
        )
        for (base in bases) {
            if (!base.isDirectory) continue
            base.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                loadPackageFromDir(dir)?.let { skill ->
                    projectSkills[skill.id] = skill.copy(category = "Project", isCustom = false, isBuiltin = false)
                }
            }
        }
        // Also: flat SKILL.md files directly under skills/
        for (base in bases) {
            val direct = File(base, "SKILL.md")
            if (direct.isFile) {
                loadPackageFromDir(base)?.let { skill ->
                    projectSkills[skill.id] = skill.copy(category = "Project")
                }
            }
        }
        Log.i(TAG, "Project skills discovered: ${projectSkills.size} under $root")
    }

    fun projectSkillsSnapshot(): List<SkillManager.Skill> = projectSkills.values.toList()

    fun findAnywhere(query: String): SkillManager.Skill? {
        SkillManager.findSkill(query)?.let { return it }
        val n = SkillManager.sanitizeName(query)
        projectSkills[n]?.let { return it }
        projectSkills.values.find {
            it.id == n || SkillManager.sanitizeName(it.name) == n || it.name.equals(query.trim(), true)
        }?.let { return it }
        // Disk package without being in memory yet
        val dir = packageDir(n)
        if (File(dir, "SKILL.md").isFile) return loadPackageFromDir(dir)
        return null
    }

    fun loadPackageFromDir(dir: File): SkillManager.Skill? {
        val md = File(dir, "SKILL.md")
        if (!md.isFile) return null
        val text = runCatching { md.readText() }.getOrNull() ?: return null
        val parsed = SkillManager.parseSkillMd(text) ?: return null
        val resources = mutableMapOf<String, String>()
        // Scan scripts/, references/, assets/
        listOf("scripts", "references", "assets").forEach { folder ->
            val f = File(dir, folder)
            if (f.isDirectory) {
                f.walkTopDown().filter { it.isFile }.forEach { file ->
                    val rel = file.relativeTo(dir).path.replace('\\', '/')
                    val body = runCatching { file.readText() }.getOrNull()
                    if (!body.isNullOrBlank() && body.length < 400_000) {
                        resources[rel] = body
                    }
                }
            }
        }
        // Merge embedded resources from parse
        resources.putAll(parsed.resources)
        val id = parsed.id.ifBlank { SkillManager.sanitizeName(dir.name) }
        return parsed.copy(
            id = id,
            resources = resources,
            isCustom = true,
            isBuiltin = false,
            category = "Package",
            metadata = parsed.metadata + mapOf("package_dir" to dir.absolutePath)
        )
    }

    /**
     * Write skill package to disk (Claude/Codex layout).
     * Returns the package directory.
     */
    fun materializePackage(skill: SkillManager.Skill): File {
        val dir = packageDir(skill.id)
        dir.mkdirs()
        File(dir, "SKILL.md").writeText(skill.toSkillMd())
        skill.resources.forEach { (rel, body) ->
            val safe = rel.trim().removePrefix("/").replace("..", "")
            if (safe.isBlank()) return@forEach
            val out = File(dir, safe)
            out.parentFile?.mkdirs()
            out.writeText(body)
        }
        // Ensure standard folders exist
        listOf("scripts", "references", "assets").forEach { File(dir, it).mkdirs() }
        return dir
    }

    fun materializeAllCustom(context: Context) {
        init(context)
        SkillManager.customSkills().forEach { materializePackage(it.toSkill()) }
        // Bundled skill-creator
        materializePackage(SkillManager.BUNDLED_SKILL_CREATOR)
    }

    // ── Activate / render (L2 with inject + args) ─────────────────────────────

    data class LoadResult(
        val skill: SkillManager.Skill,
        val rendered: String,
        val packageDir: String,
        val forked: Boolean
    )

    /**
     * Full Claude-style skill load:
     * 1. Resolve skill (catalog + project + disk package)
     * 2. Materialize package dir
     * 3. Expand args + shell injects
     * 4. Activate allowed-tools turn grant
     * 5. Return rendered body for the model
     */
    fun activate(
        context: Context,
        skillId: String,
        arguments: String = "",
        projectDir: String = activeProjectDir
    ): LoadResult? {
        init(context)
        if (projectDir.isNotBlank()) setProjectContext(projectDir)

        val skill = findAnywhere(skillId) ?: SkillManager.loadSkill(skillId) ?: return null
        // Session load into SkillManager
        SkillManager.loadSkill(skill.id)

        val dir = materializePackage(skill)
        // Re-read resources from disk (fresh)
        val fromDisk = loadPackageFromDir(dir) ?: skill
        val merged = fromDisk.copy(
            allowedTools = skill.allowedTools.ifBlank { fromDisk.allowedTools },
            disableModelInvocation = skill.disableModelInvocation,
            userInvocable = skill.userInvocable,
            whenToUse = skill.whenToUse.ifBlank { fromDisk.whenToUse }
        )

        val fm = parseExtraFrontmatter(File(dir, "SKILL.md").readText())
        val body = extractBody(File(dir, "SKILL.md").readText())
        val withArgs = expandSubstitutions(
            body = body,
            arguments = arguments,
            argumentNames = fm.argumentNames,
            skillDir = dir.absolutePath,
            projectDir = projectDir.ifBlank { activeProjectDir },
            skillId = merged.id
        )
        val rendered = expandShellInjects(
            text = withArgs,
            skillDir = dir.absolutePath,
            projectDir = projectDir.ifBlank { activeProjectDir },
            context = context
        )

        val allowed = parseToolList(merged.allowedTools.ifBlank { fm.allowedTools })
        val disallowed = parseToolList(fm.disallowedTools)
        session.set(
            ActiveSession(
                skillId = merged.id,
                allowedTools = allowed,
                disallowedTools = disallowed,
                arguments = arguments,
                skillDir = dir.absolutePath,
                projectDir = projectDir.ifBlank { activeProjectDir },
                contextFork = fm.contextFork,
                agentType = fm.agentType,
                renderedBody = rendered
            )
        )

        // Also pin tools that skill declares into ToolPermission whole-action grants for this turn
        val proj = projectDir.ifBlank { activeProjectDir }
        if (proj.isNotBlank() && allowed.isNotEmpty()) {
            allowed.forEach { token ->
                val action = token.lowercase().substringBefore('(').trim()
                    .replace("bash", "cloudshell")
                    .replace("read", "read")
                if (action.isNotBlank()) {
                    ToolPermission.remember(proj, mapToolToken(action), wholeAction = true)
                }
            }
        }

        return LoadResult(
            skill = merged,
            rendered = rendered,
            packageDir = dir.absolutePath,
            forked = fm.contextFork
        )
    }

    private fun mapToolToken(token: String): String {
        val t = token.lowercase()
        return when {
            t in setOf("bash", "shell", "zsh") -> "cloudshell"
            t in setOf("write", "edit", "create") -> t
            t.startsWith("git") -> "cloudshell"
            else -> t
        }
    }

    private fun parseToolList(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        // Space or comma separated; keep Bash(git *) as one token via simple split on spaces outside parens
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (ch in raw) {
            when {
                ch == '(' -> { depth++; sb.append(ch) }
                ch == ')' -> { depth--; sb.append(ch) }
                (ch == ' ' || ch == ',' || ch == '\n') && depth == 0 -> {
                    val t = sb.toString().trim()
                    if (t.isNotBlank()) out.add(t)
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        val last = sb.toString().trim()
        if (last.isNotBlank()) out.add(last)
        return out.map { it.lowercase() }.toSet()
    }

    data class ExtraFm(
        val contextFork: Boolean = false,
        val agentType: String = "general-purpose",
        val disallowedTools: String = "",
        val allowedTools: String = "",
        val argumentNames: List<String> = emptyList()
    )

    fun parseExtraFrontmatter(skillMd: String): ExtraFm {
        if (!skillMd.trimStart().startsWith("---")) return ExtraFm()
        val end = skillMd.indexOf("---", 3)
        if (end < 0) return ExtraFm()
        val fm = skillMd.substring(3, end)
        var contextFork = false
        var agent = "general-purpose"
        var disallowed = ""
        var allowed = ""
        val argNames = mutableListOf<String>()
        for (line in fm.lines()) {
            val t = line.trim()
            when {
                t.startsWith("context:") ->
                    contextFork = t.substringAfter(":").trim().equals("fork", true)
                t.startsWith("agent:") ->
                    agent = t.substringAfter(":").trim().trim('"', '\'')
                t.startsWith("disallowed-tools:") || t.startsWith("disallowed_tools:") ->
                    disallowed = t.substringAfter(":").trim().trim('"', '\'')
                t.startsWith("allowed-tools:") || t.startsWith("allowed_tools:") ->
                    allowed = t.substringAfter(":").trim().trim('"', '\'')
                t.startsWith("arguments:") -> {
                    val rest = t.substringAfter(":").trim()
                    if (rest.startsWith("[")) {
                        rest.trim('[', ']').split(',').map { it.trim().trim('"', '\'') }
                            .filter { it.isNotBlank() }.forEach { argNames.add(it) }
                    } else if (rest.isNotBlank() && rest != ">" && rest != "|") {
                        rest.split(Regex("\\s+")).forEach { argNames.add(it.trim('"', '\'')) }
                    }
                }
            }
        }
        return ExtraFm(contextFork, agent, disallowed, allowed, argNames)
    }

    private fun extractBody(skillMd: String): String {
        val t = skillMd.trim()
        if (!t.startsWith("---")) return t
        val end = t.indexOf("---", 3)
        if (end < 0) return t
        return t.substring(end + 3).trim()
    }

    // ── Substitutions ────────────────────────────────────────────────────────

    fun expandSubstitutions(
        body: String,
        arguments: String,
        argumentNames: List<String> = emptyList(),
        skillDir: String,
        projectDir: String,
        skillId: String
    ): String {
        var out = body
        // Escape \$ → keep literal later
        val dollarEsc = "\u0000DOLLAR\u0000"
        out = out.replace("\\$", dollarEsc)

        val args = shellSplit(arguments)
        out = out.replace("\$ARGUMENTS", arguments)
        out = out.replace("\${ARGUMENTS}", arguments)
        // $ARGUMENTS[N]
        out = Regex("${'$'}ARGUMENTS\\[(\\d+)]").replace(out) { m ->
            val i = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            args.getOrNull(i) ?: m.value
        }
        // $0 $1 $2 …
        out = Regex("""\$(\d+)""").replace(out) { m ->
            val i = m.groupValues[1].toIntOrNull() ?: return@replace m.value
            args.getOrNull(i) ?: m.value
        }
        // Named $arg from arguments: frontmatter
        argumentNames.forEachIndexed { i, name ->
            val v = args.getOrNull(i).orEmpty()
            out = out.replace("\$$name", v)
            out = out.replace("\${$name}", v)
        }

        out = out.replace("\${CLAUDE_SKILL_DIR}", skillDir)
        out = out.replace("\$CLAUDE_SKILL_DIR", skillDir)
        out = out.replace("\${CLAUDE_PROJECT_DIR}", projectDir)
        out = out.replace("\$CLAUDE_PROJECT_DIR", projectDir)
        out = out.replace("\${CLAUDE_SESSION_ID}", skillId)
        out = out.replace(dollarEsc, "$")

        // If skill had no $ARGUMENTS placeholder but user passed args, append them
        if (arguments.isNotBlank() && !body.contains("\$ARGUMENTS") && !body.contains("\$0")) {
            out = out.trimEnd() + "\n\nARGUMENTS: $arguments\n"
        }
        return out
    }

    /** Simple shell-style split with quotes. */
    fun shellSplit(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var quote: Char? = null
        while (i < raw.length) {
            val c = raw[i]
            when {
                quote != null && c == quote -> quote = null
                quote == null && (c == '"' || c == '\'') -> quote = c
                quote == null && c.isWhitespace() -> {
                    if (sb.isNotEmpty()) {
                        out.add(sb.toString()); sb.clear()
                    }
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    // ── Shell inject  `` !`cmd` `` and ```! fences ───────────────────────────

    fun expandShellInjects(
        text: String,
        skillDir: String,
        projectDir: String,
        context: Context
    ): String {
        var out = text
        // Fenced ```! ... ```
        out = Regex("```!\\s*\\n([\\s\\S]*?)```").replace(out) { m ->
            val script = m.groupValues[1].trim()
            "```\n" + runSafeShell(script, skillDir, projectDir, context) + "\n```"
        }
        // Inline !`command` at start of line or after whitespace
        out = Regex("""(?:^|\s)!`([^`]+)`""", RegexOption.MULTILINE).replace(out) { m ->
            val cmd = m.groupValues[1].trim()
            val result = runSafeShell(cmd, skillDir, projectDir, context)
            // Preserve leading whitespace from match
            val prefix = if (m.value.startsWith("!") || m.value.startsWith("\n!")) "" else m.value.takeWhile { it.isWhitespace() }
            prefix + result
        }
        return out
    }

    /**
     * Run a short shell/python command for skill inject or scripts.
     * Prefers cloud sandbox when configured; otherwise local ProcessBuilder with hard limits.
     */
    fun runSafeShell(
        command: String,
        skillDir: String,
        projectDir: String,
        context: Context,
        timeoutSec: Long = 25
    ): String {
        val cmd = command.trim()
        if (cmd.isBlank()) return ""
        // Block obviously destructive host commands in inject path
        val lower = cmd.lowercase()
        if (listOf("rm -rf /", "mkfs", ":(){", "dd if=", "shutdown", "reboot").any { lower.contains(it) }) {
            return "[shell blocked: dangerous command]"
        }

        // Prefer cloud if available (has real tools)
        val prefs = PreferencesManager(context)
        if (projectDir.isNotBlank() && prefs.isE2bEnabled() && prefs.isE2bConfigured()) {
            return runCatching {
                val full = "export CLAUDE_SKILL_DIR='$skillDir' CLAUDE_PROJECT_DIR='/workspace'; cd /workspace 2>/dev/null; $cmd"
                val res = runBlocking {
                    E2BClient.exec(
                        projectDir, prefs.getE2bApiKey(), prefs.getE2bTemplate(),
                        full, "/workspace", timeoutSec.toInt()
                    )
                }
                res.formatted().take(8000).ifBlank { "(no output)" }
            }.getOrElse { "[shell error: ${it.message?.take(200)}]" }
        }

        // Local fallback: /system/bin/sh -c
        return runCatching {
            val pb = ProcessBuilder("sh", "-c", cmd)
            pb.directory(File(if (projectDir.isNotBlank() && File(projectDir).isDirectory) projectDir else skillDir))
            pb.redirectErrorStream(true)
            pb.environment()["CLAUDE_SKILL_DIR"] = skillDir
            if (projectDir.isNotBlank()) pb.environment()["CLAUDE_PROJECT_DIR"] = projectDir
            val p = pb.start()
            val finished = p.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                p.destroyForcibly()
                return@runCatching "[shell timeout after ${timeoutSec}s]"
            }
            p.inputStream.bufferedReader().readText().take(8000).ifBlank { "(no output)" }
        }.getOrElse { "[shell error: ${it.message?.take(200)}]" }
    }

    /**
     * Run a script file from a skill package (scripts/foo.py or .sh).
     */
    fun runSkillScript(
        context: Context,
        skillId: String,
        scriptPath: String,
        arguments: String = "",
        projectDir: String = activeProjectDir
    ): String {
        init(context)
        val skill = findAnywhere(skillId) ?: return "ERROR: Unknown skill '$skillId'"
        val dir = materializePackage(skill)
        val rel = scriptPath.trim().removePrefix("./")
        val file = File(dir, rel)
        if (!file.isFile) {
            // try under scripts/
            val alt = File(dir, "scripts/${rel.removePrefix("scripts/")}")
            if (!alt.isFile) {
                val list = dir.walkTopDown().filter { it.isFile && it.extension in setOf("py", "sh", "js", "ts") }
                    .map { it.relativeTo(dir).path }.toList()
                return "ERROR: Script not found: $rel. Available: ${list.joinToString(", ").ifBlank { "(none)" }}"
            }
            return runSkillScriptFile(context, alt, dir, arguments, projectDir)
        }
        return runSkillScriptFile(context, file, dir, arguments, projectDir)
    }

    private fun runSkillScriptFile(
        context: Context,
        file: File,
        skillDir: File,
        arguments: String,
        projectDir: String
    ): String {
        val args = shellSplit(arguments).joinToString(" ") { shellQuote(it) }
        return when (file.extension.lowercase()) {
            "py" -> {
                val cmd = "python3 ${shellQuote(file.absolutePath)} $args"
                "SCRIPT ${file.name}:\n" + runSafeShell(cmd, skillDir.absolutePath, projectDir, context, 60)
            }
            "sh", "bash" -> {
                val cmd = "sh ${shellQuote(file.absolutePath)} $args"
                "SCRIPT ${file.name}:\n" + runSafeShell(cmd, skillDir.absolutePath, projectDir, context, 60)
            }
            "js" -> {
                val cmd = "node ${shellQuote(file.absolutePath)} $args"
                "SCRIPT ${file.name}:\n" + runSafeShell(cmd, skillDir.absolutePath, projectDir, context, 60)
            }
            else -> {
                // Treat as shell source
                val cmd = "sh ${shellQuote(file.absolutePath)} $args"
                "SCRIPT ${file.name}:\n" + runSafeShell(cmd, skillDir.absolutePath, projectDir, context, 60)
            }
        }
    }

    private fun shellQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    /**
     * Install a skill package from a directory or zip path into global skills.
     */
    fun installPackageFromDir(sourceDir: File): SkillManager.Skill? {
        val skill = loadPackageFromDir(sourceDir) ?: return null
        materializePackage(skill)
        // Persist as custom if not remote
        SkillManager.saveCustomSkill(
            SkillManager.CustomSkill(
                id = skill.id,
                name = skill.name,
                description = skill.description,
                content = skill.content,
                enabled = true,
                resources = skill.resources,
                whenToUse = skill.whenToUse,
                disableModelInvocation = skill.disableModelInvocation,
                userInvocable = skill.userInvocable,
                allowedTools = skill.allowedTools,
                argumentHint = skill.argumentHint,
                license = skill.license,
                compatibility = skill.compatibility,
                metadata = skill.metadata
            )
        )
        return skill
    }

    fun formatLoadResponse(result: LoadResult, arguments: String): String {
        val resNote = if (result.skill.resources.isNotEmpty()) {
            "\n\nPackage files (L3 — READ_SKILL_RESOURCE or RUN_SKILL_SCRIPT):\n" +
                result.skill.resources.keys.sorted().joinToString("\n") { "  - $it" }
        } else ""
        val forkNote = if (result.forked) {
            "\n\n[context: fork] This skill prefers an isolated sub-agent. " +
                "You may use TASK with the skill instructions as the prompt, or continue inline."
        } else ""
        val grant = session.get()
        val toolsNote = if (grant != null && grant.allowedTools.isNotEmpty()) {
            "\nPre-approved tools this turn: ${grant.allowedTools.joinToString(" ")}"
        } else ""
        return buildString {
            append("SKILL LOADED (Claude/Codex package): `${result.skill.id}`\n")
            append("Package dir: ${result.packageDir}\n")
            if (arguments.isNotBlank()) append("ARGUMENTS: $arguments\n")
            append(toolsNote)
            append(forkNote)
            append("\n─── SKILL.md (rendered) ───\n\n")
            append(result.rendered)
            append(resNote)
            append("\n\nFollow these instructions strictly.")
        }
    }
}
