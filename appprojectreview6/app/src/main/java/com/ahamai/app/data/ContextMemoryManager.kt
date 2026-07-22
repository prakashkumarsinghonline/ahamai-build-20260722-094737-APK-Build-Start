package com.ahamai.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Grok-style context memory for the coding agent.
 *
 * How Grok manages context (mirrored here):
 *  1. **Map first** — keep a small durable catalog (goals, facts, hot files), not raw history.
 *  2. **Hot path full fidelity** — recent turns stay complete so the model can act.
 *  3. **Cold path compress** — older tool dumps become short digests / episode summaries.
 *  4. **Token budget**, not message-count hacks — cut by estimated tokens.
 *  5. **On-demand detail** — full file bodies are never kept forever; re-read when needed.
 *  6. **Durable project memory** — survives app restarts via `.ahamai/MEMORY.md` + session JSON.
 *
 * The full local [convo] can still be large for UI/session restore; what we *send* (and what we
 * compact in-place when over budget) is prepared by this manager.
 */
object ContextMemoryManager {

    // ── Tunables ──────────────────────────────────────────────────────────────
    /** Soft budget for messages sent to the model (~chars/4 ≈ tokens). 200k context. */
    const val DEFAULT_BUDGET_TOKENS = 200_000
    /** Hard ceiling — emergency compaction if still over after soft pass. */
    const val HARD_BUDGET_TOKENS = 240_000
    /** Keep this many most-recent messages fully intact (assistant+user pairs). */
    private const val RECENT_MESSAGES = 36
    /** Max durable facts stored on disk. */
    private const val MAX_FACTS = 64
    /** Max hot-file entries. */
    private const val MAX_HOT_FILES = 64
    /** Max episode summaries retained. */
    private const val MAX_EPISODES = 24
    /** Max chars of a single tool result kept in recent context (action-dependent). */
    private const val TOOL_RESULT_SOFT_CAP = 12_000

    private val memoryMarker = "[[AHAMAI_CONTEXT_MEMORY]]"
    private val episodeMarker = "[[AHAMAI_EPISODE_SUMMARY]]"

    // In-process cache keyed by absolute projectDir
    private val cache = ConcurrentHashMap<String, ProjectMemory>()

    // ── Types ─────────────────────────────────────────────────────────────────

    data class Fact(
        val text: String,
        val source: String = "auto", // auto | user | agent | remember
        val ts: Long = System.currentTimeMillis()
    )

    data class HotFile(
        val path: String,
        var reads: Int = 0,
        var edits: Int = 0,
        var lastTouch: Long = System.currentTimeMillis(),
        var note: String = ""
    )

    data class Episode(
        val summary: String,
        val turnRange: String = "",
        val ts: Long = System.currentTimeMillis()
    )

    /**
     * One concrete change the agent already made — so later turns know
     * "pehla me kya fix kiya tha" without re-reading the whole convo.
     */
    data class FixEntry(
        val path: String,
        val action: String,          // edit | write | create | delete | patch | fix
        val summary: String,         // human: "fixed brace imbalance", "added VERIFY tool"
        val detail: String = "",     // optional short old→new / error that was fixed
        val added: Int = 0,
        val removed: Int = 0,
        val ts: Long = System.currentTimeMillis()
    ) {
        fun line(): String = buildString {
            append(action.uppercase())
            if (path.isNotBlank()) append(" `$path`")
            if (added > 0 || removed > 0) append(" (+$added/-$removed)")
            if (summary.isNotBlank()) append(": ").append(summary.take(180))
            if (detail.isNotBlank()) append(" — ").append(detail.take(100))
        }
    }

    /** One step of the agent's active multi-step PLAN (persisted across turns). */
    data class PlanStep(
        val text: String,
        var done: Boolean = false
    )

    data class ProjectMemory(
        val goals: MutableList<String> = mutableListOf(),
        val facts: MutableList<Fact> = mutableListOf(),
        val decisions: MutableList<String> = mutableListOf(),
        val openLoops: MutableList<String> = mutableListOf(),
        val hotFiles: MutableMap<String, HotFile> = linkedMapOf(),
        val episodes: MutableList<Episode> = mutableListOf(),
        /** Ordered log of concrete fixes/changes this project session (persisted). */
        val fixes: MutableList<FixEntry> = mutableListOf(),
        /** Current PLAN checklist (if any). Not the same as openLoops. */
        val activePlan: MutableList<PlanStep> = mutableListOf(),
        var lastTask: String = "",
        var lastDoneSummary: String = "",
        var updatedAt: Long = System.currentTimeMillis()
    ) {
        fun snapshotText(maxChars: Int = 3500): String {
            val sb = StringBuilder()
            sb.appendLine("## Working goals")
            if (goals.isEmpty()) sb.appendLine("- (none pinned)")
            else goals.takeLast(8).forEach { sb.appendLine("- $it") }

            // Active plan — first-class so model knows step progress without re-reading log
            if (activePlan.isNotEmpty()) {
                val doneN = activePlan.count { it.done }
                sb.appendLine("## Active plan ($doneN/${activePlan.size} done)")
                activePlan.forEachIndexed { i, s ->
                    val mark = if (s.done) "x" else " "
                    sb.appendLine("- [$mark] ${i + 1}. ${s.text}")
                }
                val next = activePlan.indexOfFirst { !it.done }
                if (next >= 0) sb.appendLine("Next: step ${next + 1} — ${activePlan[next].text}")
                else sb.appendLine("All plan steps marked done.")
            }

            // MOST IMPORTANT for multi-turn: what was already fixed
            if (fixes.isNotEmpty()) {
                sb.appendLine("## Already fixed / changed (DO NOT redo unless still broken)")
                fixes.takeLast(16).forEach { sb.appendLine("- ${it.line()}") }
            }

            if (openLoops.isNotEmpty()) {
                sb.appendLine("## Still open / unfinished")
                openLoops.takeLast(8).forEach { sb.appendLine("- $it") }
            }

            if (decisions.isNotEmpty()) {
                sb.appendLine("## Decisions")
                decisions.takeLast(10).forEach { sb.appendLine("- $it") }
            }

            if (facts.isNotEmpty()) {
                sb.appendLine("## Durable facts")
                facts.takeLast(20).forEach { sb.appendLine("- ${it.text}") }
            }

            val hot = hotFiles.values.sortedByDescending { it.lastTouch }.take(12)
            if (hot.isNotEmpty()) {
                sb.appendLine("## Hot files (this project)")
                hot.forEach { f ->
                    val touch = buildString {
                        if (f.edits > 0) append("${f.edits} edit(s) ")
                        if (f.reads > 0) append("${f.reads} read(s)")
                    }.trim()
                    val note = if (f.note.isNotBlank()) " — ${f.note}" else ""
                    sb.appendLine("- `${f.path}` ($touch)$note")
                }
            }

            if (lastDoneSummary.isNotBlank()) {
                sb.appendLine("## Last completed work")
                sb.appendLine(lastDoneSummary.take(600))
            }

            if (episodes.isNotEmpty()) {
                sb.appendLine("## Earlier episode digests (detail dropped — re-read files if needed)")
                episodes.takeLast(6).forEach { e ->
                    sb.appendLine("- ${e.summary.take(280)}")
                }
            }

            val out = sb.toString().trim()
            return if (out.length <= maxChars) out else out.take(maxChars) + "\n…(memory truncated)"
        }
    }

    private const val MAX_FIXES = 40

    // ── Disk paths ────────────────────────────────────────────────────────────

    private fun ahamaiDir(projectDir: String): File =
        File(projectDir, ".ahamai").apply { mkdirs() }

    private fun memoryMd(projectDir: String) = File(ahamaiDir(projectDir), "MEMORY.md")
    private fun sessionJson(projectDir: String) = File(ahamaiDir(projectDir), "session_memory.json")

    // ── Load / save ───────────────────────────────────────────────────────────

    fun get(projectDir: String): ProjectMemory {
        if (projectDir.isBlank()) return ProjectMemory()
        return cache.getOrPut(projectDir) { loadFromDisk(projectDir) }
    }

    fun resetSession(projectDir: String) {
        if (projectDir.isBlank()) return
        cache.remove(projectDir)
        // Keep durable MEMORY.md facts; wipe volatile session layer only
        val durable = loadDurableFacts(projectDir)
        val mem = ProjectMemory()
        durable.forEach { mem.facts.add(Fact(it, source = "durable")) }
        cache[projectDir] = mem
        save(projectDir)
    }

    fun loadFromDisk(projectDir: String): ProjectMemory {
        val mem = ProjectMemory()
        // 1) Durable markdown facts
        loadDurableFacts(projectDir).forEach { mem.facts.add(Fact(it, source = "durable")) }
        // 2) Session JSON
        try {
            val f = sessionJson(projectDir)
            if (f.exists()) {
                val o = JSONObject(f.readText())
                o.optJSONArray("goals")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { mem.goals.add(it) }
                }
                o.optJSONArray("decisions")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { mem.decisions.add(it) }
                }
                o.optJSONArray("openLoops")?.let { arr ->
                    for (i in 0 until arr.length()) arr.optString(i)?.takeIf { it.isNotBlank() }?.let { mem.openLoops.add(it) }
                }
                o.optJSONArray("facts")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val fo = arr.optJSONObject(i) ?: continue
                        val t = fo.optString("text")
                        if (t.isNotBlank() && mem.facts.none { it.text.equals(t, true) }) {
                            mem.facts.add(Fact(t, fo.optString("source", "auto"), fo.optLong("ts", 0L)))
                        }
                    }
                }
                o.optJSONArray("hotFiles")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val fo = arr.optJSONObject(i) ?: continue
                        val p = fo.optString("path")
                        if (p.isBlank()) continue
                        mem.hotFiles[p] = HotFile(
                            path = p,
                            reads = fo.optInt("reads"),
                            edits = fo.optInt("edits"),
                            lastTouch = fo.optLong("lastTouch", System.currentTimeMillis()),
                            note = fo.optString("note")
                        )
                    }
                }
                o.optJSONArray("episodes")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val eo = arr.optJSONObject(i) ?: continue
                        val s = eo.optString("summary")
                        if (s.isNotBlank()) mem.episodes.add(
                            Episode(s, eo.optString("turnRange"), eo.optLong("ts", 0L))
                        )
                    }
                }
                o.optJSONArray("fixes")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val fo = arr.optJSONObject(i) ?: continue
                        val path = fo.optString("path")
                        val summary = fo.optString("summary")
                        if (path.isBlank() && summary.isBlank()) continue
                        mem.fixes.add(
                            FixEntry(
                                path = path,
                                action = fo.optString("action", "edit"),
                                summary = summary,
                                detail = fo.optString("detail"),
                                added = fo.optInt("added"),
                                removed = fo.optInt("removed"),
                                ts = fo.optLong("ts", 0L)
                            )
                        )
                    }
                }
                o.optJSONArray("activePlan")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val po = arr.optJSONObject(i) ?: continue
                        val t = po.optString("text").trim()
                        if (t.isBlank()) continue
                        mem.activePlan.add(PlanStep(t, po.optBoolean("done", false)))
                    }
                }
                mem.lastTask = o.optString("lastTask")
                mem.lastDoneSummary = o.optString("lastDoneSummary")
                mem.updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            }
        } catch (_: Exception) { /* corrupt session — keep durable facts */ }

        // 3) Seed from worklog tail if we still have almost nothing
        if (mem.facts.size < 3 && mem.episodes.isEmpty()) {
            val wl = SpecializedAgents.readWorklog(projectDir)
            if (wl.isNotBlank()) {
                mem.episodes.add(Episode("Prior worklog: " + wl.take(500).replace("\n", " ")))
            }
        }
        return mem
    }

    private fun loadDurableFacts(projectDir: String): List<String> {
        val out = mutableListOf<String>()
        try {
            val f = memoryMd(projectDir)
            if (f.exists()) {
                f.readText().lines().forEach { line ->
                    val t = line.trim().removePrefix("-").removePrefix("*").trim()
                    if (t.isNotBlank() && !t.startsWith("#") && t.length in 3..400) out.add(t)
                }
            }
        } catch (_: Exception) {}
        return out.distinct().take(MAX_FACTS)
    }

    fun save(projectDir: String) {
        if (projectDir.isBlank()) return
        val mem = cache[projectDir] ?: return
        mem.updatedAt = System.currentTimeMillis()
        try {
            val o = JSONObject()
            o.put("goals", JSONArray(mem.goals.takeLast(12)))
            o.put("decisions", JSONArray(mem.decisions.takeLast(20)))
            o.put("openLoops", JSONArray(mem.openLoops.takeLast(12)))
            val factsArr = JSONArray()
            mem.facts.takeLast(MAX_FACTS).forEach { f ->
                factsArr.put(JSONObject().put("text", f.text).put("source", f.source).put("ts", f.ts))
            }
            o.put("facts", factsArr)
            val hotArr = JSONArray()
            mem.hotFiles.values.sortedByDescending { it.lastTouch }.take(MAX_HOT_FILES).forEach { h ->
                hotArr.put(
                    JSONObject()
                        .put("path", h.path)
                        .put("reads", h.reads)
                        .put("edits", h.edits)
                        .put("lastTouch", h.lastTouch)
                        .put("note", h.note)
                )
            }
            o.put("hotFiles", hotArr)
            val epArr = JSONArray()
            mem.episodes.takeLast(MAX_EPISODES).forEach { e ->
                epArr.put(JSONObject().put("summary", e.summary).put("turnRange", e.turnRange).put("ts", e.ts))
            }
            o.put("episodes", epArr)
            val fixArr = JSONArray()
            mem.fixes.takeLast(MAX_FIXES).forEach { f ->
                fixArr.put(
                    JSONObject()
                        .put("path", f.path)
                        .put("action", f.action)
                        .put("summary", f.summary)
                        .put("detail", f.detail)
                        .put("added", f.added)
                        .put("removed", f.removed)
                        .put("ts", f.ts)
                )
            }
            o.put("fixes", fixArr)
            val planArr = JSONArray()
            mem.activePlan.take(24).forEach { p ->
                planArr.put(JSONObject().put("text", p.text).put("done", p.done))
            }
            o.put("activePlan", planArr)
            o.put("lastTask", mem.lastTask.take(500))
            o.put("lastDoneSummary", mem.lastDoneSummary.take(1000))
            o.put("updatedAt", mem.updatedAt)
            sessionJson(projectDir).writeText(o.toString())
        } catch (_: Exception) {}

        // Durable human-readable MEMORY.md (facts the agent / user pinned + strong autos)
        try {
            val durable = mem.facts
                .filter { it.source == "durable" || it.source == "remember" || it.source == "user" }
                .takeLast(MAX_FACTS)
            if (durable.isNotEmpty()) {
                val sb = StringBuilder()
                sb.appendLine("# AhamAI Project Memory")
                sb.appendLine()
                sb.appendLine("_Auto-maintained durable facts. Agent MUST follow these. Edit freely._")
                sb.appendLine()
                durable.forEach { sb.appendLine("- ${it.text}") }
                memoryMd(projectDir).writeText(sb.toString())
            }
        } catch (_: Exception) {}
    }

    // ── Token math ────────────────────────────────────────────────────────────

    /** Cheap token estimate used across the agent stack. */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        // ~4 chars/token for English+code mix; slightly denser for pure code
        return (text.length / 4).coerceAtLeast(1)
    }

    fun estimateTokens(messages: List<Pair<String, String>>): Int =
        messages.sumOf { estimateTokens(it.second) + 4 } // role overhead

    // ── Public API: remember / forget ─────────────────────────────────────────

    fun remember(projectDir: String, fact: String, source: String = "remember"): String {
        val t = fact.trim()
        if (t.isBlank()) return "ERROR: empty fact"
        val mem = get(projectDir)
        mem.facts.removeAll { it.text.equals(t, true) }
        mem.facts.add(Fact(t.take(400), source = source))
        trimLists(mem)
        save(projectDir)
        return "Remembered: ${t.take(200)}"
    }

    fun forget(projectDir: String, query: String): String {
        val mem = get(projectDir)
        val q = query.trim()
        if (q.isBlank()) return "ERROR: empty query"
        val before = mem.facts.size
        mem.facts.removeAll { it.text.contains(q, true) }
        mem.goals.removeAll { it.contains(q, true) }
        mem.openLoops.removeAll { it.contains(q, true) }
        save(projectDir)
        val removed = before - mem.facts.size
        return if (removed > 0) "Forgot $removed fact(s) matching \"$q\"." else "No facts matched \"$q\"."
    }

    fun setGoal(projectDir: String, goal: String) {
        val mem = get(projectDir)
        val g = goal.trim().take(300)
        if (g.isBlank()) return
        mem.goals.removeAll { it.equals(g, true) }
        mem.goals.add(g)
        mem.lastTask = g
        trimLists(mem)
        save(projectDir)
    }

    /**
     * Record a concrete fix the agent just applied. Call after successful EDIT/WRITE/PATCH.
     * This is what later turns read as "pehla me kya fix kiya tha".
     */
    fun recordFix(
        projectDir: String,
        path: String,
        action: String,
        summary: String,
        detail: String = "",
        added: Int = 0,
        removed: Int = 0
    ) {
        if (projectDir.isBlank()) return
        val s = summary.trim()
        if (s.isBlank() && path.isBlank()) return
        val mem = get(projectDir)
        val entry = FixEntry(
            path = path.trim().removePrefix("./"),
            action = action.trim().ifBlank { "edit" },
            summary = s.take(220),
            detail = detail.trim().take(200),
            added = added,
            removed = removed
        )
        // Dedupe near-identical consecutive fixes
        val last = mem.fixes.lastOrNull()
        if (last != null &&
            last.path == entry.path &&
            last.action == entry.action &&
            last.summary.equals(entry.summary, true)
        ) {
            // refresh stats only
            mem.fixes[mem.fixes.lastIndex] = entry.copy(ts = System.currentTimeMillis())
        } else {
            mem.fixes.add(entry)
        }
        if (path.isNotBlank()) {
            touchFile(mem, path, edit = true)
            mem.hotFiles[path.trim().removePrefix("./")]?.note = s.take(80).ifBlank { action }
        }
        // Close open loops that this fix likely resolved
        if (s.length >= 8) {
            mem.openLoops.removeAll { loop ->
                s.contains(loop.take(24), true) || loop.contains(s.take(24), true) ||
                    (path.isNotBlank() && loop.contains(path.substringAfterLast('/'), true))
            }
        }
        trimLists(mem)
        save(projectDir)
    }

    /**
     * Model-callable "recent file changes" history (Cursor's diff_history). Formats the persisted
     * fix-ledger — every edit / write / create / delete this project session — with per-change
     * +/- line stats and how long ago it happened. Optional [pathFilter] narrows to one file/folder.
     */
    fun diffHistory(projectDir: String, pathFilter: String = "", limit: Int = 20): String {
        if (projectDir.isBlank()) return "No project loaded."
        val mem = get(projectDir)
        val filter = pathFilter.trim().removePrefix("./")
        val all = if (filter.isBlank()) mem.fixes.toList()
            else mem.fixes.filter { it.path.startsWith(filter) || it.path.contains(filter) }
        if (all.isEmpty()) return if (filter.isBlank())
            "No file changes recorded yet this session."
        else "No recorded changes for '$filter' this session."
        val recent = all.takeLast(limit.coerceIn(1, 100))
        val now = System.currentTimeMillis()
        fun ago(ts: Long): String {
            val s = (now - ts) / 1000
            return when {
                s < 60 -> "${s}s ago"
                s < 3600 -> "${s / 60}m ago"
                s < 86400 -> "${s / 3600}h ago"
                else -> "${s / 86400}d ago"
            }
        }
        val totalAdd = all.sumOf { it.added }
        val totalRem = all.sumOf { it.removed }
        val files = all.mapNotNull { it.path.ifBlank { null } }.distinct().size
        val sb = StringBuilder()
        sb.append("RECENT CHANGES (this session")
        if (filter.isNotBlank()) sb.append(" · filtered: $filter")
        sb.append(") — ${all.size} change(s), $files file(s), +$totalAdd/-$totalRem lines:\n")
        // Newest first so the most recent edit is at the top.
        recent.asReversed().forEach { f -> sb.append("• ${f.line()}  · ${ago(f.ts)}\n") }
        if (all.size > recent.size) sb.append("… (${all.size - recent.size} older change(s) not shown)\n")
        return sb.toString().trimEnd()
    }

    /** Compact block for follow-up turns: already-done vs now-do. */
    fun buildSessionContinuityBlock(
        projectDir: String,
        sessionFiles: Set<String> = emptySet(),
        currentTask: String = ""
    ): String {
        if (projectDir.isBlank()) return ""
        val mem = get(projectDir)
        val sb = StringBuilder()
        sb.appendLine("## SESSION CONTINUITY (read carefully — avoid redoing finished work)")
        if (mem.fixes.isNotEmpty()) {
            sb.appendLine("### Already fixed / changed earlier (DO NOT re-apply unless still broken)")
            mem.fixes.takeLast(14).forEach { sb.appendLine("- ${it.line()}") }
        } else if (sessionFiles.isNotEmpty()) {
            sb.appendLine("### Files already touched this session")
            sessionFiles.take(20).forEach { sb.appendLine("- `$it`") }
        } else {
            sb.appendLine("- (no recorded fixes yet this session)")
        }
        if (mem.lastDoneSummary.isNotBlank()) {
            sb.appendLine("### Last DONE summary")
            sb.appendLine(mem.lastDoneSummary.take(400))
        }
        if (mem.openLoops.isNotEmpty()) {
            sb.appendLine("### Still open from earlier")
            mem.openLoops.takeLast(6).forEach { sb.appendLine("- $it") }
        }
        if (mem.activePlan.isNotEmpty()) {
            val doneN = mem.activePlan.count { it.done }
            sb.appendLine("### Active plan ($doneN/${mem.activePlan.size})")
            mem.activePlan.forEachIndexed { i, s ->
                sb.appendLine("- [${if (s.done) "x" else " "}] ${i + 1}. ${s.text}")
            }
            val next = mem.activePlan.indexOfFirst { !it.done }
            if (next >= 0) sb.appendLine("Continue from step ${next + 1}; COMPLETE_STEP when a step finishes.")
        }
        if (currentTask.isNotBlank()) {
            sb.appendLine("### NOW DO (current user request)")
            sb.appendLine(currentTask.take(500))
            sb.appendLine(
                "Only do what is still needed. Reuse earlier fixes and known paths above. " +
                    "Do NOT re-LIST/GREP the whole tree to re-find files you already touched. " +
                    "Re-READ only if you lack content or an edit failed — never ritual re-search after success. " +
                    "If the request is complete → ANSWER + DONE immediately."
            )
        }
        return sb.toString().trim() + "\n"
    }

    // ── Active plan API ───────────────────────────────────────────────────────

    /** Replace the active plan with [steps] (from PLAN tool). Clears previous checklist. */
    fun setPlan(projectDir: String, steps: List<String>): String {
        if (projectDir.isBlank()) return "ERROR: no project"
        val cleaned = steps.map { it.trim().removePrefix("-").removePrefix("*").trim() }
            .map { it.replace(Regex("^\\d+[.)]\\s*"), "").trim() }
            .filter { it.length in 2..240 }
            .take(20)
        if (cleaned.isEmpty()) return "ERROR: empty plan"
        val mem = get(projectDir)
        mem.activePlan.clear()
        cleaned.forEach { mem.activePlan.add(PlanStep(it, done = false)) }
        // Do NOT dump plan lines into openLoops — that made COMPLETE_STEP fragile.
        save(projectDir)
        return "OK: plan set with ${cleaned.size} steps"
    }

    /**
     * Mark a plan step done. [spec] may be 1-based index ("1", "2") or unique substring of step text.
     * Returns human status line for the model + UI.
     */
    fun completePlanStep(projectDir: String, spec: String): Pair<Boolean, String> {
        if (projectDir.isBlank()) return false to "ERROR: no project"
        val mem = get(projectDir)
        if (mem.activePlan.isEmpty()) return false to "ERROR: no active plan — call PLAN first."
        val raw = spec.trim()
        val idx = raw.toIntOrNull()?.let { it - 1 }
            ?: mem.activePlan.indexOfFirst { it.text.contains(raw, ignoreCase = true) && raw.length >= 3 }
                .takeIf { it >= 0 }
        if (idx == null || idx !in mem.activePlan.indices) {
            return false to "ERROR: cannot match COMPLETE_STEP '$raw' to a plan step (1..${mem.activePlan.size})."
        }
        mem.activePlan[idx].done = true
        save(projectDir)
        val next = mem.activePlan.indexOfFirst { !it.done }
        val prog = if (next >= 0)
            "Step ${next + 1}/${mem.activePlan.size} · ${mem.activePlan[next].text}"
        else
            "All ${mem.activePlan.size} steps done"
        return true to "OK: marked step ${idx + 1} done. $prog"
    }

    fun getActivePlan(projectDir: String): List<PlanStep> {
        if (projectDir.isBlank()) return emptyList()
        return get(projectDir).activePlan.toList()
    }

    fun planProgressLine(projectDir: String): String? {
        val plan = getActivePlan(projectDir)
        if (plan.isEmpty()) return null
        val doneN = plan.count { it.done }
        val next = plan.indexOfFirst { !it.done }
        return if (next >= 0)
            "Step ${next + 1}/${plan.size} · ${plan[next].text}"
        else
            "Plan complete · $doneN/${plan.size}"
    }

    fun incompletePlanSteps(projectDir: String): List<String> {
        return getActivePlan(projectDir).filter { !it.done }.map { it.text }
    }

    fun clearPlan(projectDir: String) {
        if (projectDir.isBlank()) return
        val mem = get(projectDir)
        if (mem.activePlan.isEmpty()) return
        mem.activePlan.clear()
        save(projectDir)
    }

    fun addDecision(projectDir: String, decision: String) {
        val d = decision.trim().take(300)
        if (d.isBlank()) return
        val mem = get(projectDir)
        if (mem.decisions.none { it.equals(d, true) }) mem.decisions.add(d)
        trimLists(mem)
        save(projectDir)
    }

    fun addOpenLoop(projectDir: String, item: String) {
        val t = item.trim().take(240)
        if (t.isBlank()) return
        val mem = get(projectDir)
        if (mem.openLoops.none { it.equals(t, true) }) mem.openLoops.add(t)
        trimLists(mem)
        save(projectDir)
    }

    fun closeOpenLoop(projectDir: String, contains: String) {
        val mem = get(projectDir)
        mem.openLoops.removeAll { it.contains(contains, true) }
        save(projectDir)
    }

    // ── Observe tool / turn activity ──────────────────────────────────────────

    /**
     * Call after each assistant turn's tools run. Updates hot files, extracts light facts,
     * and records DONE summaries into durable memory.
     */
    fun observe(
        projectDir: String,
        steps: List<CodeAgent.AgentStep>,
        toolResultsByStep: List<String> = emptyList(),
        userTask: String? = null
    ) {
        if (projectDir.isBlank()) return
        val mem = get(projectDir)
        if (!userTask.isNullOrBlank()) {
            mem.lastTask = userTask.take(500)
            if (mem.goals.none { it.equals(userTask.trim(), true) } && userTask.length in 8..240) {
                // First user goal of the run — pin lightly
                if (mem.goals.isEmpty()) mem.goals.add(userTask.trim().take(240))
            }
        }

        steps.forEachIndexed { idx, step ->
            val path = step.path.trim()
            val res = toolResultsByStep.getOrNull(idx).orEmpty()
            val ok = res.isBlank() || (!res.startsWith("ERROR") && !res.startsWith("PATCH partial"))
            when (step.action) {
                "read", "readlines", "analyzeimage", "pdfread" -> touchFile(mem, path, read = true)
                "edit", "write", "create", "multiedit", "bulkedit", "importadd", "applypatch" -> {
                    touchFile(mem, path, edit = true)
                    if (path.isNotBlank()) {
                        val note = when (step.action) {
                            "create", "write" -> "written"
                            "applypatch" -> "patched"
                            else -> "edited"
                        }
                        mem.hotFiles[path]?.note = note
                    }
                    // Auto-log successful changes into the fix ledger
                    if (ok && (path.isNotBlank() || step.action == "bulkedit")) {
                        val summary = when {
                            step.explanation.isNotBlank() -> step.explanation.trim()
                            step.action == "create" || step.action == "write" ->
                                "wrote ${path.substringAfterLast('/')}".take(120)
                            step.action == "edit" || step.action == "multiedit" -> {
                                val oldHint = step.arg2.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(60).orEmpty()
                                if (oldHint.isNotBlank()) "replaced region near: $oldHint" else "edited file"
                            }
                            step.action == "applypatch" -> "applied patch"
                            step.action == "bulkedit" -> "bulk replaced across project"
                            else -> step.action
                        }
                        // Avoid double-recording if recordFix already called with richer stats
                        val already = mem.fixes.lastOrNull()?.let {
                            it.path == path && (System.currentTimeMillis() - it.ts) < 3_000
                        } == true
                        if (!already) {
                            mem.fixes.add(
                                FixEntry(
                                    path = path,
                                    action = step.action,
                                    summary = summary.take(220),
                                    detail = res.lineSequence().firstOrNull()?.take(120).orEmpty()
                                )
                            )
                        }
                    }
                }
                "delete", "move", "copy" -> {
                    touchFile(mem, path, edit = true)
                    if (ok) {
                        mem.fixes.add(
                            FixEntry(
                                path = path,
                                action = step.action,
                                summary = when (step.action) {
                                    "delete" -> "deleted"
                                    "move" -> "moved → ${step.arg2.take(80)}"
                                    else -> "copied → ${step.arg2.take(80)}"
                                }
                            )
                        )
                    }
                }
                "done" -> {
                    val summary = step.detail.ifBlank { "Task completed." }.take(800)
                    mem.lastDoneSummary = summary
                    mem.openLoops.clear()
                    // Mark remaining plan steps done if agent finished (or clear when all done)
                    if (mem.activePlan.isNotEmpty() && mem.activePlan.all { it.done }) {
                        // keep completed plan briefly in episodes
                        mem.episodes.add(
                            Episode("PLAN finished: " + mem.activePlan.joinToString("; ") { it.text }.take(300))
                        )
                        mem.activePlan.clear()
                    }
                    if (summary.length > 20) {
                        mem.episodes.add(Episode("DONE: $summary".take(400)))
                        // Pin a durable fact from the completion summary for next sessions
                        if (summary.length in 20..300) {
                            val fact = "Last completion: $summary"
                            if (mem.facts.none { it.text.startsWith("Last completion:") }) {
                                mem.facts.add(Fact(fact, source = "auto"))
                            } else {
                                mem.facts.removeAll { it.text.startsWith("Last completion:") }
                                mem.facts.add(Fact(fact, source = "auto"))
                            }
                        }
                    }
                }
                "answer" -> {
                    val a = step.detail.take(300)
                    if (a.length > 40) mem.episodes.add(Episode("ANSWER: $a"))
                }
                "plan" -> {
                    // Structured active plan (not openLoops)
                    val stepsIn = step.detail.lines().map { it.trim().removePrefix("-").removePrefix("*").trim() }
                        .map { it.replace(Regex("^\\d+[.)]\\s*"), "").trim() }
                        .filter { it.length in 2..240 }
                        .take(20)
                    if (stepsIn.isNotEmpty()) {
                        mem.activePlan.clear()
                        stepsIn.forEach { mem.activePlan.add(PlanStep(it, done = false)) }
                    }
                }
                "completestep" -> {
                    val raw = step.detail.trim()
                    val idx = raw.toIntOrNull()?.let { it - 1 }
                        ?: mem.activePlan.indexOfFirst { it.text.contains(raw, ignoreCase = true) && raw.length >= 3 }
                            .takeIf { it >= 0 }
                    if (idx != null && idx in mem.activePlan.indices) {
                        mem.activePlan[idx].done = true
                    }
                }
                "remember" -> remember(projectDir, step.detail, source = "remember")
                "worklog" -> {
                    val e = step.detail.trim()
                    if (e.length > 20) mem.episodes.add(Episode("WORKLOG: ${e.take(300)}"))
                }
            }

            // Errors → open loops + facts so next turn still "remembers" the failure
            if (res.startsWith("ERROR") && path.isNotBlank()) {
                val errLine = res.lineSequence().firstOrNull()?.take(160).orEmpty()
                val fact = "Recent error on `$path`: $errLine"
                mem.facts.removeAll { it.text.startsWith("Recent error on `$path`") }
                mem.facts.add(Fact(fact, source = "auto"))
                addOpenLoop(projectDir, "Fix error on $path: ${errLine.take(100)}")
            }
            // Static verify failures also become open loops
            if (res.contains("STATIC VERIFY") && res.contains("FAILED")) {
                addOpenLoop(projectDir, "Resolve static verify failures")
            }
        }
        trimLists(mem)
        save(projectDir)
    }

    private fun touchFile(mem: ProjectMemory, path: String, read: Boolean = false, edit: Boolean = false) {
        val p = path.trim().removePrefix("./")
        if (p.isBlank() || p.length > 240) return
        // Skip junk / generated noise
        if (p.contains("/build/") || p.endsWith(".map") || p.startsWith(".ahamai/")) return
        val h = mem.hotFiles.getOrPut(p) { HotFile(p) }
        if (read) h.reads++
        if (edit) h.edits++
        h.lastTouch = System.currentTimeMillis()
    }

    private fun trimLists(mem: ProjectMemory) {
        while (mem.facts.size > MAX_FACTS) mem.facts.removeAt(0)
        while (mem.goals.size > 12) mem.goals.removeAt(0)
        while (mem.decisions.size > 24) mem.decisions.removeAt(0)
        while (mem.openLoops.size > 16) mem.openLoops.removeAt(0)
        while (mem.episodes.size > MAX_EPISODES) mem.episodes.removeAt(0)
        while (mem.fixes.size > MAX_FIXES) mem.fixes.removeAt(0)
        if (mem.hotFiles.size > MAX_HOT_FILES) {
            val keep = mem.hotFiles.values.sortedByDescending { it.lastTouch }.take(MAX_HOT_FILES).map { it.path }.toSet()
            mem.hotFiles.keys.toList().forEach { if (it !in keep) mem.hotFiles.remove(it) }
        }
    }

    // ── Tool-result compression (on demand detail) ────────────────────────────

    /**
     * Compress a tool result the way Grok keeps tool output: head + tail for logs,
     * short digests for reads of huge files, full text when small.
     */
    fun compressToolResult(action: String, path: String, result: String): String {
        if (result.length <= TOOL_RESULT_SOFT_CAP) return result
        val a = action.lowercase()
        return when (a) {
            "read", "readlines" -> {
                // Keep header + first chunk + note to re-read with READ_LINES
                val head = result.lineSequence().take(80).joinToString("\n")
                "CONTENT of $path (compressed by context memory — full file still on disk):\n" +
                    head.take(TOOL_RESULT_SOFT_CAP) +
                    "\n… (${result.length} chars total; use READ_LINES or re-READ_FILE if you need more) …"
            }
            "list", "grep", "search", "codebasesearch", "symbolsearch" ->
                CodeAgent.truncateOutput(result, headLines = 50, tailLines = 30, minOutputLines = 70)
            "cloudshell", "runpython", "runjob", "jobstatus",
            "ghlogs", "ghbuild", "ghbuildstatus",
            "webscan", "recon", "portscan", "vulnscan", "sast" ->
                CodeAgent.truncateOutput(result, headLines = 35, tailLines = 45, minOutputLines = 60)
            "fetch", "readurl", "websearch", "http", "browserextract", "browserview" ->
                CodeAgent.truncateOutput(result, headLines = 40, tailLines = 20, minOutputLines = 50)
            else -> CodeAgent.truncateOutput(result, headLines = 40, tailLines = 30, minOutputLines = 60)
        }
    }

    // ── Build memory block for prompts ────────────────────────────────────────

    /**
     * Text block injected into the first user prompt / system adjacent context.
     * Catalog-first: enough to orient, not a dump of history.
     */
    fun buildMemoryBlock(projectDir: String, worklogExtra: Boolean = true): String {
        if (projectDir.isBlank()) return ""
        val mem = get(projectDir)
        val snap = mem.snapshotText(3200)
        if (snap.contains("(none pinned)") && mem.hotFiles.isEmpty() && mem.episodes.isEmpty() && mem.lastDoneSummary.isBlank()) {
            // Still include worklog if any
            if (worklogExtra) {
                val wl = SpecializedAgents.readWorklog(projectDir)
                if (wl.isNotBlank()) {
                    return "$memoryMarker\n# CONTEXT MEMORY (Grok-style)\n## Prior worklog\n${wl.take(1500)}\n"
                }
            }
            return ""
        }
        val wl = if (worklogExtra) {
            val w = SpecializedAgents.readWorklog(projectDir)
            if (w.isNotBlank()) "\n## Worklog tail\n${w.take(1200)}\n" else ""
        } else ""
        return buildString {
            appendLine(memoryMarker)
            appendLine("# CONTEXT MEMORY (auto-managed, Grok-style)")
            appendLine("This is your durable orientation layer. Prefer these facts over fuzzy recall.")
            appendLine("Full old tool dumps are NOT kept — re-READ_FILE / GREP when you need detail.")
            appendLine()
            appendLine(snap)
            append(wl)
        }.trim() + "\n"
    }

    // ── Compaction (the core) ─────────────────────────────────────────────────

    /**
     * Compacts [convo] in place when over the token budget.
     * Returns a short human note for the UI log, or null if no compaction ran.
     *
     * Strategy (matches how Grok keeps working on large trees):
     *  - Always keep: system message
     *  - Keep: first real user task (truncated if huge)
     *  - Keep: last [RECENT_MESSAGES] messages at full fidelity (tool results pre-compressed)
     *  - Middle → one episode summary message (catalog)
     *  - Inject / refresh the context-memory block after system
     */
    fun compactIfNeeded(
        projectDir: String,
        convo: MutableList<Pair<String, String>>,
        sessionFiles: Set<String> = emptySet(),
        budgetTokens: Int = DEFAULT_BUDGET_TOKENS
    ): String? {
        if (convo.isEmpty()) return null
        val before = estimateTokens(convo)
        // With 200k budget, allow a longer conversation before force-compact by message count.
        if (before <= budgetTokens && convo.size <= 96) {
            // Still refresh memory pin if missing
            ensureMemoryMessage(projectDir, convo, sessionFiles)
            return null
        }

        val system = convo.firstOrNull { it.first == "system" }
        val firstUser = convo.firstOrNull { (role, content) ->
            role == "user" &&
                !content.contains(memoryMarker) &&
                !content.contains(episodeMarker) &&
                !content.startsWith("FOLLOW-UP TASK:") // prefer original task if still present
        } ?: convo.firstOrNull { it.first == "user" && !it.second.contains(memoryMarker) }

        // Identity of messages we always keep (by index), plus a tail window.
        val keepIdx = linkedSetOf<Int>()
        system?.let { s -> convo.indexOfFirst { it === s || (it.first == s.first && it.second == s.second) }
            .takeIf { it >= 0 }?.let { keepIdx.add(it) } }
        firstUser?.let { u ->
            val i = convo.indexOfFirst { it.first == u.first && it.second == u.second }
            if (i >= 0) keepIdx.add(i)
        }
        val recentStart = (convo.size - RECENT_MESSAGES).coerceAtLeast(0)
        for (i in recentStart until convo.size) keepIdx.add(i)

        val middle = convo.filterIndexed { i, _ -> i !in keepIdx }
        val episode = summarizeEpisode(middle, sessionFiles)
        if (episode.isNotBlank() && projectDir.isNotBlank()) {
            val mem = get(projectDir)
            mem.episodes.add(Episode(episode.take(500)))
            trimLists(mem)
            save(projectDir)
        }

        val recent = convo.drop(recentStart).map { (role, content) ->
            role to compressMessageBody(content)
        }

        val rebuilt = mutableListOf<Pair<String, String>>()
        if (system != null) rebuilt.add(system)

        // Memory catalog right after system
        val memBlock = buildMemoryBlock(projectDir)
        val sessionNote = if (sessionFiles.isNotEmpty())
            "\n## Files touched this session\n" + sessionFiles.take(30).joinToString("\n") { "- `$it`" } + "\n"
        else ""
        if (memBlock.isNotBlank() || sessionNote.isNotBlank()) {
            rebuilt.add(
                "user" to (memBlock + sessionNote +
                    "\n(Context memory refreshed. Older raw turns were compacted into digests. " +
                    "Continue the same project — re-read files when you need full content.)\n")
            )
            // Acknowledge so role alternation stays valid for strict providers
            rebuilt.add("assistant" to "Understood. I'll use the context memory catalog and re-read files when I need details.")
        }

        if (firstUser != null) {
            val body = firstUser.second
            val trimmed = if (estimateTokens(body) > 8_000) {
                // Keep task + tree head, drop huge attachment dumps if any
                body.take(12_000) + "\n…(initial prompt trimmed by context memory)…"
            } else body
            // Avoid duplicating if recent already includes the exact same body
            if (recent.none { it.second == firstUser.second || it.second.startsWith(firstUser.second.take(200)) }) {
                rebuilt.add(firstUser.first to trimmed)
            }
        }

        if (episode.isNotBlank()) {
            rebuilt.add(
                "user" to "$episodeMarker\n# Compacted earlier turns (detail discarded)\n$episode\n"
            )
            rebuilt.add("assistant" to "Acknowledged compacted history. Proceeding from the catalog + recent turns.")
        }

        // Append recent, skipping exact duplicates of what we already added
        val existing = rebuilt.map { it.second.take(200) }.toHashSet()
        for (m in recent) {
            val key = m.second.take(200)
            if (key in existing) continue
            rebuilt.add(m)
            existing.add(key)
        }

        // Emergency: if still over hard budget, aggressively shrink tool-like user messages
        var guard = 0
        while (estimateTokens(rebuilt) > HARD_BUDGET_TOKENS && guard++ < 8) {
            val idx = rebuilt.indexOfFirst { (role, c) ->
                role == "user" && c.length > 2000 && !c.contains(memoryMarker)
            }
            if (idx < 0) break
            val (r, c) = rebuilt[idx]
            rebuilt[idx] = r to (c.take(1200) + "\n…(hard-compacted)…\n" + c.takeLast(800))
        }

        convo.clear()
        convo.addAll(rebuilt)
        val after = estimateTokens(convo)
        return "Context memory compacted: ~$before → ~$after tokens (${convo.size} msgs). Older detail is in digests — re-read files as needed."
    }

    private fun ensureMemoryMessage(
        projectDir: String,
        convo: MutableList<Pair<String, String>>,
        sessionFiles: Set<String>
    ) {
        if (projectDir.isBlank() || convo.isEmpty()) return
        val hasMem = convo.any { it.second.contains(memoryMarker) }
        if (hasMem) return
        val block = buildMemoryBlock(projectDir)
        if (block.isBlank() && sessionFiles.isEmpty()) return
        val sessionNote = if (sessionFiles.isNotEmpty())
            "\n## Files touched this session\n" + sessionFiles.take(20).joinToString("\n") { "- `$it`" } + "\n"
        else ""
        // Insert after system if present
        val insertAt = if (convo.firstOrNull()?.first == "system") 1 else 0
        convo.add(insertAt, "user" to (block + sessionNote).ifBlank { return })
        convo.add(insertAt + 1, "assistant" to "Context memory loaded. I'll follow durable facts and re-read files for details.")
    }

    private fun compressMessageBody(content: String): String {
        if (content.length <= TOOL_RESULT_SOFT_CAP) return content
        // Tool-result shaped messages often start with [ACTION path]
        if (content.contains("\n[") || content.startsWith("[") || content.contains("CONTENT of")) {
            return CodeAgent.truncateOutput(content, headLines = 35, tailLines = 25, minOutputLines = 50)
        }
        if (estimateTokens(content) > 4_000) {
            return content.take(5000) + "\n…\n" + content.takeLast(2000)
        }
        return content
    }

    private fun summarizeEpisode(
        middle: List<Pair<String, String>>,
        sessionFiles: Set<String>
    ): String {
        if (middle.isEmpty()) return ""
        val actions = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val files = linkedSetOf<String>()
        files.addAll(sessionFiles)

        val pathRe = Regex("""[`'\" ]([\w./\-]+?\.(?:kt|java|xml|gradle|kts|ts|tsx|js|jsx|py|md|json|toml|yml|yaml|css|html|swift|go|rs))[`'\"\s]""")
        val actionRe = Regex("""\[([A-Z_]+)(?:\s+([^\]]+))?\]""")

        for ((role, content) in middle) {
            if (role == "assistant") {
                // Collect tool names from XML-ish or structured text
                Regex("""<tool_call>\s*([A-Za-z_]+)""", RegexOption.IGNORE_CASE).findAll(content).forEach {
                    actions.add(it.groupValues[1].uppercase())
                }
                Regex("""(WRITE_FILE|CREATE_FILE|EDIT_FILE|READ_FILE|CLOUD_SHELL|GREP|DONE|ANSWER)""", RegexOption.IGNORE_CASE)
                    .findAll(content).forEach { actions.add(it.groupValues[1].uppercase()) }
            }
            actionRe.findAll(content).forEach { m ->
                actions.add(m.groupValues[1].uppercase())
                val p = m.groupValues.getOrNull(2)?.trim().orEmpty()
                if (p.contains('.')) files.add(p.take(120))
            }
            pathRe.findAll(content).forEach { files.add(it.groupValues[1]) }
            content.lineSequence().filter { it.contains("ERROR", true) }.take(3).forEach {
                errors.add(it.trim().take(160))
            }
        }

        val actionCounts = actions.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(12)
            .joinToString(", ") { "${it.key}×${it.value}" }

        val sb = StringBuilder()
        sb.appendLine("Turns compacted: ${middle.size} messages.")
        if (actionCounts.isNotBlank()) sb.appendLine("Actions seen: $actionCounts")
        if (files.isNotEmpty()) {
            sb.appendLine("Files mentioned/touched:")
            files.take(20).forEach { sb.appendLine("- `$it`") }
        }
        if (errors.isNotEmpty()) {
            sb.appendLine("Errors noted:")
            errors.take(5).forEach { sb.appendLine("- $it") }
        }
        sb.appendLine("If you need exact prior tool output, re-run the relevant READ/GREP/CLOUD_SHELL — do not invent it.")
        return sb.toString().trim()
    }

    // ── Initial prompt helper ─────────────────────────────────────────────────

    /**
     * Extra block for [CodeAgent.buildInitialPrompt] — worklog + durable memory catalog.
     */
    fun initialInjection(projectDir: String): String {
        if (projectDir.isBlank()) return ""
        // Warm cache from disk on first message of a session
        get(projectDir)
        return buildMemoryBlock(projectDir, worklogExtra = true)
    }

    /** One-line stats for UI debug/notes. */
    fun statsLine(projectDir: String, convo: List<Pair<String, String>>): String {
        val mem = if (projectDir.isNotBlank()) get(projectDir) else ProjectMemory()
        val tok = estimateTokens(convo)
        return "ctx ~${tok} tok · facts ${mem.facts.size} · hot ${mem.hotFiles.size} · episodes ${mem.episodes.size} · msgs ${convo.size}"
    }

    fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
}
