package com.ahamai.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 9Router-style **Smart Combos**: one virtual model id (e.g. `Auto`, `Auto-Agent`) that
 * chains multiple real provider+model members with sticky round-robin / sequential fallback.
 *
 * On 429, auth, timeout, stream fail, or empty response the next member is tried.
 * Combos are stored in the same admin `api_providers` JSON under `"combos"`.
 *
 * Inspired by 9Router combos (sticky RR + automatic fallback across providers).
 */
object ComboRouter {

    enum class Strategy {
        /** Prefer last-good member; only advance on failure. */
        STICKY,
        /** Always walk members in order until one succeeds. */
        FALLBACK,
        /** Rotate starting index every successful request. */
        ROUND_ROBIN;

        companion object {
            fun parse(s: String?): Strategy = when (s?.lowercase()?.trim()) {
                "sticky", "sticky_rr", "sticky_round_robin" -> STICKY
                "round_robin", "rr", "rotate" -> ROUND_ROBIN
                else -> FALLBACK
            }
        }

        fun wire(): String = when (this) {
            STICKY -> "sticky"
            ROUND_ROBIN -> "round_robin"
            FALLBACK -> "fallback"
        }
    }

    data class Member(
        val providerId: String,
        val model: String,
        /** Optional label for admin UI. */
        val label: String = ""
    ) {
        fun key(): String = "$providerId::$model"
        fun display(): String = label.ifBlank { "$providerId / $model" }
    }

    data class Combo(
        val id: String,
        val name: String,
        val enabled: Boolean = true,
        val chatMode: Boolean = true,
        val agentMode: Boolean = false,
        val strategy: Strategy = Strategy.FALLBACK,
        val members: List<Member> = emptyList(),
        /** Optional note shown in admin. */
        val note: String = "",
        /** Empty = all plans. Else free / pro / plus / enterprise. */
        val plans: List<String> = emptyList()
    )

    @Volatile private var combos: List<Combo> = emptyList()
    @Volatile private var combosEnabled: Boolean = true

    /** comboId → last successful member index */
    private val stickyIndex = ConcurrentHashMap<String, AtomicInteger>()
    /** comboId → RR cursor */
    private val rrCursor = ConcurrentHashMap<String, AtomicInteger>()
    /** memberKey → cooldown until (ms) after 429/fail */
    private val memberCool = ConcurrentHashMap<String, Long>()
    private const val MEMBER_COOLDOWN_MS = 45_000L

    fun isEnabled(): Boolean = combosEnabled

    fun all(): List<Combo> = combos

    fun find(id: String): Combo? {
        if (!combosEnabled || id.isBlank()) return null
        return combos.firstOrNull { it.enabled && it.id.equals(id, ignoreCase = true) }
    }

    fun isCombo(modelId: String): Boolean = find(modelId) != null

    fun chatComboIds(planId: String? = null): List<String> {
        if (!combosEnabled) return emptyList()
        return combos.filter { it.enabled && it.chatMode && planOk(it.plans, planId) }.map { it.id }
    }

    fun agentComboIds(planId: String? = null): List<String> {
        if (!combosEnabled) return emptyList()
        return combos.filter { it.enabled && it.agentMode && planOk(it.plans, planId) }.map { it.id }
    }

    private fun planOk(plans: List<String>, planId: String?): Boolean {
        if (plans.isEmpty()) return true
        val pid = planId?.trim()?.lowercase()?.ifBlank { "free" } ?: return true
        return plans.any { it.equals(pid, ignoreCase = true) }
    }

    fun updateFromJson(root: JSONObject?) {
        if (root == null) {
            // leave previous if partial parse
            return
        }
        val block = root.optJSONObject("combos") ?: run {
            combos = emptyList()
            combosEnabled = true
            return
        }
        combosEnabled = block.optBoolean("enabled", true)
        val arr = block.optJSONArray("items") ?: JSONArray()
        val list = ArrayList<Combo>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id", "").trim()
            if (id.isBlank()) continue
            val mArr = o.optJSONArray("members") ?: JSONArray()
            val members = ArrayList<Member>()
            for (j in 0 until mArr.length()) {
                val m = mArr.optJSONObject(j) ?: continue
                val pid = m.optString("providerId", "").trim()
                val mid = m.optString("model", "").trim()
                if (pid.isBlank() || mid.isBlank()) continue
                members.add(Member(pid, mid, m.optString("label", "").trim()))
            }
            val planArr = o.optJSONArray("plans")
            val plans = if (planArr == null) emptyList() else
                (0 until planArr.length()).map { planArr.optString(it).trim().lowercase() }.filter { it.isNotBlank() }
            list.add(
                Combo(
                    id = id,
                    name = o.optString("name", id).ifBlank { id },
                    enabled = o.optBoolean("enabled", true),
                    chatMode = o.optBoolean("chatMode", true),
                    agentMode = o.optBoolean("agentMode", false),
                    strategy = Strategy.parse(o.optString("strategy", "fallback")),
                    members = members,
                    note = o.optString("note", ""),
                    plans = plans
                )
            )
        }
        combos = list
    }

    fun toJsonObject(): JSONObject {
        val items = JSONArray()
        for (c in combos) {
            val mArr = JSONArray()
            for (m in c.members) {
                mArr.put(
                    JSONObject()
                        .put("providerId", m.providerId)
                        .put("model", m.model)
                        .put("label", m.label)
                )
            }
            val pArr = JSONArray()
            c.plans.forEach { pArr.put(it) }
            items.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("enabled", c.enabled)
                    .put("chatMode", c.chatMode)
                    .put("agentMode", c.agentMode)
                    .put("strategy", c.strategy.wire())
                    .put("note", c.note)
                    .put("members", mArr)
                    .put("plans", pArr)
            )
        }
        return JSONObject().put("enabled", combosEnabled).put("items", items)
    }

    fun replaceAll(enabled: Boolean, items: List<Combo>) {
        combosEnabled = enabled
        combos = items.toList()
    }

    fun markMemberFail(comboId: String, member: Member, rateLimited: Boolean = false) {
        val ms = if (rateLimited) MEMBER_COOLDOWN_MS else MEMBER_COOLDOWN_MS / 2
        memberCool[member.key()] = System.currentTimeMillis() + ms
        // Advance sticky so next attempt skips this member
        stickyIndex.getOrPut(comboId) { AtomicInteger(0) }.incrementAndGet()
    }

    fun markMemberOk(comboId: String, memberIndex: Int) {
        stickyIndex.getOrPut(comboId) { AtomicInteger(0) }.set(memberIndex)
        rrCursor.getOrPut(comboId) { AtomicInteger(0) }.set(memberIndex + 1)
    }

    private fun isCooling(member: Member): Boolean {
        val exp = memberCool[member.key()] ?: return false
        if (System.currentTimeMillis() >= exp) {
            memberCool.remove(member.key())
            return false
        }
        return true
    }

    /**
     * Ordered list of members to try for this request (cooling members last).
     */
    fun orderedMembers(combo: Combo): List<Member> {
        if (combo.members.isEmpty()) return emptyList()
        val n = combo.members.size
        val start = when (combo.strategy) {
            Strategy.STICKY -> {
                val idx = stickyIndex.getOrPut(combo.id) { AtomicInteger(0) }.get()
                ((idx % n) + n) % n
            }
            Strategy.ROUND_ROBIN -> {
                val c = rrCursor.getOrPut(combo.id) { AtomicInteger(0) }
                ((c.get() % n) + n) % n
            }
            Strategy.FALLBACK -> 0
        }
        val rotated = (0 until n).map { combo.members[(start + it) % n] }
        val hot = rotated.filterNot { isCooling(it) }
        val cold = rotated.filter { isCooling(it) }
        return hot + cold
    }

    /**
     * Resolve a combo member to a concrete [ApiConfig.Resolved] endpoint.
     */
    fun resolveMember(member: Member): ApiConfig.Resolved? {
        val p = ApiConfig.providerById(member.providerId) ?: return null
        if (!p.enabled || p.baseUrl.isBlank()) return null
        val key = ApiConfig.nextKeyForProvider(p)
        if (p.keys.isNotEmpty() && key.isBlank()) return null
        return ApiConfig.Resolved(p.id, p.baseUrl, key, member.model)
    }

    /**
     * First healthy resolution for a combo model id, or null.
     */
    fun resolveCombo(modelId: String): ApiConfig.Resolved? {
        val combo = find(modelId) ?: return null
        for (m in orderedMembers(combo)) {
            val r = resolveMember(m)
            if (r != null) return r
        }
        return null
    }

    /**
     * Full failover chain for a combo (already ordered, with keys).
     */
    fun resolveChain(modelId: String): List<Pair<Member, ApiConfig.Resolved>> {
        val combo = find(modelId) ?: return emptyList()
        val out = ArrayList<Pair<Member, ApiConfig.Resolved>>()
        for (m in orderedMembers(combo)) {
            // Never nest combos inside combos
            if (isCombo(m.model)) continue
            val r = resolveMember(m) ?: continue
            out.add(m to r)
        }
        return out
    }

    /** Seed a sensible Auto / Auto-Agent template from current providers. */
    fun suggestedDefaults(): List<Combo> {
        val enabled = ApiConfig.enabledProviders()
        if (enabled.isEmpty()) {
            return listOf(
                Combo(
                    id = "Auto",
                    name = "Auto",
                    chatMode = true,
                    agentMode = false,
                    strategy = Strategy.FALLBACK,
                    note = "Chat combo — add provider members (9Router-style fallback)",
                    members = emptyList()
                ),
                Combo(
                    id = "Auto-Agent",
                    name = "Auto Agent",
                    chatMode = false,
                    agentMode = true,
                    strategy = Strategy.STICKY,
                    note = "Agent combo — stronger models first, free/cheap last",
                    members = emptyList()
                )
            )
        }
        val chatMembers = enabled.flatMap { p ->
            p.models.filter { it.chatMode }.take(2).map { m ->
                Member(p.id, m.id, "${p.name} · ${m.id}")
            }
        }.take(6)
        val agentMembers = enabled.flatMap { p ->
            p.models.filter { it.agentMode || p.agentModeDefault }.take(2).map { m ->
                Member(p.id, m.id, "${p.name} · ${m.id}")
            }
        }.ifEmpty { chatMembers }.take(6)
        return listOf(
            Combo(
                id = "Auto",
                name = "Auto",
                chatMode = true,
                agentMode = false,
                strategy = Strategy.FALLBACK,
                note = "Multi-provider chat · auto-rotate on 429 / timeout / fail",
                members = chatMembers
            ),
            Combo(
                id = "Auto-Agent",
                name = "Auto Agent",
                chatMode = false,
                agentMode = true,
                strategy = Strategy.STICKY,
                note = "Multi-provider agent · sticky last-good + fallback",
                members = agentMembers
            )
        )
    }
}
