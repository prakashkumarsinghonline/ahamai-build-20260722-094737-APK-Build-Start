package com.ahamai.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

/**
 * Single source of truth for AI endpoints, models and keys — driven entirely by the
 * Firebase Remote Config `api_providers` JSON (see api_providers.example.json).
 *
 * NOTHING here is hardcoded: if Remote Config hasn't loaded yet, resolution falls back
 * to the user's custom endpoint (Settings) and otherwise returns blanks.
 *
 * Modes:
 *  - chat  : text chat default provider/model
 *  - agent : workspace/agent default provider/model (only agent-enabled models)
 *  - vision: dedicated endpoint for image-understanding (falls back to chat if unset)
 *
 * Multiple keys per provider are rotated round-robin so rate limits / dead keys are
 * spread out and skipped.
 */
object ApiConfig {

    data class ModelInfo(
        val id: String,
        val agentMode: Boolean,
        val chatMode: Boolean = true,
        val explicit: Boolean,
        /** Empty = visible on ALL plans. Else only these plan ids: free / pro / plus / enterprise. */
        val plans: Set<String> = emptySet()
    )

    data class Provider(
        val id: String,
        val name: String,
        val baseUrl: String,
        val keys: List<String>,
        val enabled: Boolean,
        val fetchModels: Boolean,
        val agentModeDefault: Boolean,
        val agentModeAllow: Set<String>,
        val agentModeDeny: Set<String>,
        val models: List<ModelInfo>
    )

    data class Defaults(val providerId: String, val model: String)

    /** A configurable image-generation provider (config-driven, like chat providers). */
    data class ImageProvider(
        val id: String,
        val name: String,
        val baseUrl: String,
        val format: String,          // "pollinations" | "gemini" | "openai"
        val enabled: Boolean,
        val keys: List<String>,
        val models: List<String>,
        val defaultSize: String
    )

    /** A fully resolved endpoint ready for a request. */
    data class Resolved(val providerId: String, val baseUrl: String, val apiKey: String, val model: String)

    // Compose-observable version counter — bumps whenever config is (re)loaded so the UI re-resolves.
    var version by mutableIntStateOf(0)
        private set

    @Volatile private var chatProviders: List<Provider> = emptyList()
    @Volatile private var visionProviders: List<Provider> = emptyList()
    @Volatile private var visionEnabled = false
    @Volatile private var defChat = Defaults("", "")
    @Volatile private var defAgent = Defaults("", "")
    @Volatile private var defVision = Defaults("", "")

    // ── Cross-provider fallback config (parsed from the `fallback` block) ──────
    @Volatile private var fallbackEnabled = true
    @Volatile private var fallbackMaxProviders = 4

    // ── Image generation (config-driven, parsed from the `imageGeneration` block) ──
    @Volatile private var imageEnabled = false
    @Volatile private var imageProviders: List<ImageProvider> = emptyList()
    @Volatile private var defImage = Defaults("", "")

    @Volatile var loaded = false
        private set

    private val counters = HashMap<String, AtomicInteger>()

    /** modelId -> providerId, so a selected model is always routed to the provider that OWNS it
     *  (not the default provider). Populated from explicit config + live-fetched model lists. */
    private val modelOwnerIndex = java.util.concurrent.ConcurrentHashMap<String, String>()

    // ── Bad-key blacklist — same pattern as WebTools ──────────────────────────
    private val badKeys = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()

    // ── Rate-limit cooldown — keys that hit 429 are skipped for a short window ──
    // Map<providerId, Map<key, expiryEpochMillis>>. Unlike badKeys this is TEMPORARY:
    // a 429'd key is parked for ~60s so every subsequent request stops paying the full
    // round-trip + back-off tax on a key we already know is throttled.
    private val coolKeys = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.ConcurrentHashMap<String, Long>>()
    private const val COOLDOWN_MS = 60_000L

    /** Mark a specific (baseUrl, key) pair as rate-limited; it is skipped until the cooldown expires. */
    fun markKeyCoolDown(baseUrl: String, key: String, durationMs: Long = COOLDOWN_MS) {
        if (key.isBlank()) return
        val p = resolveProvider(baseUrl) ?: return
        coolKeys.getOrPut(p.id) { java.util.concurrent.ConcurrentHashMap() }[key] = System.currentTimeMillis() + durationMs
        android.util.Log.w("ApiConfig", "Cooling down key for ${p.id}: ${key.take(8)}… for ${durationMs}ms")
    }

    private fun isCooling(providerId: String, key: String): Boolean {
        val exp = coolKeys[providerId]?.get(key) ?: return false
        if (System.currentTimeMillis() >= exp) { coolKeys[providerId]?.remove(key); return false }
        return true
    }

    /** Match a baseUrl to its configured chat provider (exact or prefix). */
    private fun resolveProvider(baseUrl: String): Provider? {
        val url = baseUrl.trimEnd('/')
        return chatProviders.firstOrNull { it.baseUrl == url }
            ?: chatProviders.firstOrNull { url.startsWith(it.baseUrl) || it.baseUrl.startsWith(url) }
    }

    /** Mark a specific (baseUrl, key) pair as permanently invalid (401 / 422). */
    fun markKeyBad(baseUrl: String, key: String) {
        if (key.isBlank()) return
        val p = resolveProvider(baseUrl) ?: return
        badKeys.getOrPut(p.id) {
            java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap())
        }.add(key)
        android.util.Log.w("ApiConfig", "Marked key bad for ${p.id}: ${key.take(8)}…")
    }

    /** Returns the next valid key for the provider serving this baseUrl, or null if unknown. */
    fun getNextKey(baseUrl: String): String? {
        if (!loaded) return null
        val p = resolveProvider(baseUrl) ?: return null
        return nextKey(p).ifBlank { null }
    }

    /** Returns a rotated key for a provider identified by its config id (e.g. "google_aistudio").
     *  Used by non-chat features (image generation) that need a specific provider's credentials. */
    fun keyForProviderId(id: String): String? {
        if (!loaded) return null
        val p = chatProviders.firstOrNull { it.id == id } ?: return null
        return nextKey(p).ifBlank { null }
    }

    /** The configured baseUrl for a provider id, or null. */
    fun baseUrlForProviderId(id: String): String? =
        chatProviders.firstOrNull { it.id == id }?.baseUrl?.ifBlank { null }

    // ── Image generation accessors (config-driven) ────────────────────────────
    fun imageGenEnabled(): Boolean = imageEnabled
    fun imageProvidersEnabled(): List<ImageProvider> = imageProviders.filter { it.enabled }
    fun imageDefault(): Defaults = defImage
    fun imageProviderById(id: String): ImageProvider? = imageProviders.firstOrNull { it.id == id }

    /** Find the enabled image provider that owns [modelId], else the default, else the first enabled. */
    fun imageProviderForModel(modelId: String): ImageProvider? {
        val enabled = imageProviders.filter { it.enabled }
        if (enabled.isEmpty()) return null
        return enabled.firstOrNull { modelId in it.models }
            ?: enabled.firstOrNull { it.id == defImage.providerId }
            ?: enabled.first()
    }

    /** Round-robin / blacklist-aware key for an image provider (uses the shared counters/badKeys). */
    fun nextImageKey(p: ImageProvider): String {
        if (p.keys.isEmpty()) return ""
        val bad = badKeys[p.id]
        val valid = p.keys.filter { (bad == null || it !in bad) && !isCooling(p.id, it) }
        val keys = valid.ifEmpty { p.keys.filter { bad == null || it !in bad }.ifEmpty { p.keys } }
        if (keys.size == 1) return keys[0]
        val c = counters.getOrPut("img:" + p.id) { AtomicInteger(0) }
        var idx = c.getAndIncrement() % keys.size
        if (idx < 0) idx += keys.size
        return keys[idx]
    }

    /** How many keys are configured for this baseUrl — used to size the rotation retry budget. */
    fun getKeyCount(baseUrl: String): Int {
        val p = resolveProvider(baseUrl) ?: return 1
        return p.keys.size.coerceAtLeast(1)
    }

    /** Native (non-generic) integrations that have their own request format — excluded from
     *  cross-provider fallback because they can't accept a plain OpenAI chat/completions call. */
    private fun isNativeSpecial(baseUrl: String): Boolean {
        val u = baseUrl.lowercase()
        return u.contains("generativelanguage.googleapis.com") ||
            u.contains("codebuff.com") || u.contains("freebuff")
    }

    /**
     * Cross-provider fallback chain (LiteLLM-style failover). Given the primary provider's baseUrl,
     * returns an ordered list of ALTERNATE generic OpenAI-compatible endpoints to try when the
     * primary fails to produce any output. Each entry carries a freshly-rotated key + a concrete
     * model for that provider. Context-free: relies only on the in-memory provider list.
     *
     * Skips: the primary provider itself, disabled providers, native special integrations, providers
     * with keys-required-but-none-valid, and providers with no resolvable model.
     */
    fun fallbackChain(primaryBaseUrl: String, agent: Boolean = false): List<Resolved> {
        if (!fallbackEnabled || !loaded) return emptyList()
        val primary = resolveProvider(primaryBaseUrl)
        val out = ArrayList<Resolved>()
        for (p in chatProviders) {
            if (!p.enabled) continue
            if (primary != null && p.id == primary.id) continue
            if (p.baseUrl.isBlank() || isNativeSpecial(p.baseUrl)) continue
            val key = nextKey(p)
            // Providers that REQUIRE a key but have none valid right now are skipped.
            if (p.keys.isNotEmpty() && key.isBlank()) continue
            val model = firstModel(p, agent).ifBlank { firstModel(p, false) }
            if (model.isBlank()) continue   // can't fall back without a concrete model id
            out.add(Resolved(p.id, p.baseUrl, key, model))
            if (out.size >= fallbackMaxProviders) break
        }
        return out
    }

    /** Clear all bad-key state — call when admin saves fresh keys. */
    fun clearAllBadKeys() {
        badKeys.clear()
        coolKeys.clear()
        counters.values.forEach { it.set(0) }
    }

    /** All chat providers (enabled + disabled) — for admin combo builders. */
    fun allProviders(): List<Provider> = chatProviders

    fun enabledProviders(): List<Provider> = chatProviders.filter { it.enabled }

    fun providerById(id: String): Provider? =
        chatProviders.firstOrNull { it.id == id }

    /** Public key rotation for a known provider (used by ComboRouter). */
    fun nextKeyForProvider(p: Provider): String = nextKey(p)

    /** Empty plans set/list = all tiers. Otherwise user plan id must be listed. */
    fun planAllows(allowed: Collection<String>, planId: String): Boolean {
        if (allowed.isEmpty()) return true
        val pid = planId.trim().lowercase().ifBlank { "free" }
        return allowed.any { it.equals(pid, ignoreCase = true) }
    }

    /** Whether a model/combo id is visible for the signed-in user's plan. */
    fun isVisibleForPlan(modelId: String, planId: String): Boolean {
        if (modelId.isBlank()) return true
        val combo = ComboRouter.find(modelId)
        if (combo != null) return planAllows(combo.plans, planId)
        for (p in chatProviders) {
            val m = p.models.firstOrNull { it.id == modelId }
            if (m != null) return planAllows(m.plans, planId)
        }
        // Unconfigured / live-only model id → allow (admin can lock via explicit config entry)
        return true
    }

    fun filterModelsForUser(context: Context, models: List<String>): List<String> {
        val planId = PreferencesManager(context).getPlanId()
        return models.filter { isVisibleForPlan(it, planId) }
    }



    @Volatile private var lastHealthCheck = 0L

    /**
     * AUTO key health check — pings every key of every enabled provider in parallel and updates the
     * bad-key blacklist / cooldown automatically, so dead keys are skipped without the admin having
     * to run "Test All" manually. Throttled to once per 10 min unless [force]. Recovered keys are
     * un-blacklisted. Safe to call from a composition scope (does its work on IO).
     */
    suspend fun autoHealthCheck(context: Context, force: Boolean = false) {
        if (isCustom(context) || !loaded) return
        val now = System.currentTimeMillis()
        if (!force && now - lastHealthCheck < 10 * 60_000L) return
        lastHealthCheck = now
        val providers = chatProviders.filter { it.enabled }
        if (providers.isEmpty()) return
        withContext(Dispatchers.IO) {
            coroutineScope {
                providers.flatMap { p ->
                    val model = p.models.firstOrNull()?.id ?: ""
                    p.keys.map { key ->
                        async {
                            val r = ApiClient.pingKey(p.baseUrl, key, model)
                            val low = r.lowercase()
                            when {
                                r == "OK" -> badKeys[p.id]?.remove(key)
                                low.contains("401") || low.contains("403") || low.contains("422") ||
                                    low.contains("invalid") || low.contains("unauthor") -> markKeyBad(p.baseUrl, key)
                                low.contains("429") || low.contains("rate") -> markKeyCoolDown(p.baseUrl, key)
                            }
                        }
                    }
                }.awaitAll()
            }
        }
        version++  // nudge the UI so a recovered/blacklisted key set is reflected
    }

    /** Parse the raw Remote Config JSON. Safe to call repeatedly. */
    fun update(rawJson: String?) {
        if (rawJson.isNullOrBlank()) return
        try {
            val root = JSONObject(rawJson)
            val d = root.optJSONObject("defaults")
            defChat = parseDefaults(d?.optJSONObject("chat"))
            defAgent = parseDefaults(d?.optJSONObject("agent"))
            defVision = parseDefaults(d?.optJSONObject("vision"))
            defImage = parseDefaults(d?.optJSONObject("imageGeneration"))
            chatProviders = parseProviders(root.optJSONObject("chat")?.optJSONArray("providers"))
            // Rebuild the model→provider index from explicit config (first enabled provider wins).
            modelOwnerIndex.clear()
            for (p in chatProviders) for (m in p.models) modelOwnerIndex.putIfAbsent(m.id, p.id)
            val vis = root.optJSONObject("vision")
            visionEnabled = vis?.optBoolean("enabled", false) ?: false
            visionProviders = parseProviders(vis?.optJSONArray("providers"))

            // Image generation providers (config-driven)
            val img = root.optJSONObject("imageGeneration")
            imageEnabled = img?.optBoolean("enabled", false) ?: false
            imageProviders = parseImageProviders(img?.optJSONArray("providers"))

            // Cross-provider fallback config
            val fb = root.optJSONObject("fallback")
            fallbackEnabled = fb?.optBoolean("enabled", true) ?: true
            fallbackMaxProviders = (fb?.optInt("maxProviders", 4) ?: 4).coerceAtLeast(1)

            // 9Router-style combos + outbound proxy pools
            ComboRouter.updateFromJson(root)
            ProxyManager.updateFromJson(root)

            // Response cache config (drives the ResponseCache singleton)
            val ca = root.optJSONObject("cache")
            ResponseCache.enabled = ca?.optBoolean("enabled", false) ?: false
            ResponseCache.ttlMs = ((ca?.optLong("ttlMinutes", 30) ?: 30L).coerceAtLeast(1L)) * 60_000L
            ResponseCache.maxEntries = (ca?.optInt("maxEntries", 200) ?: 200).coerceAtLeast(1)

            loaded = true
            version++
        } catch (_: Exception) {
            // Leave previous config intact on parse error.
        }
    }

    private fun parseDefaults(o: JSONObject?): Defaults =
        if (o == null) Defaults("", "") else Defaults(o.optString("providerId", ""), o.optString("model", ""))

    private fun parseStringSet(arr: JSONArray?): Set<String> =
        if (arr == null) emptySet() else (0 until arr.length()).map { arr.optString(it).trim() }.filter { it.isNotEmpty() }.toSet()

    /** Parse the `imageGeneration.providers` array into ImageProvider entries. */
    private fun parseImageProviders(arr: JSONArray?): List<ImageProvider> {
        if (arr == null) return emptyList()
        val out = ArrayList<ImageProvider>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val keys = p.optJSONArray("keys")?.let { ka ->
                (0 until ka.length()).map { ka.optString(it).trim() }.filter { it.isNotEmpty() }
            } ?: emptyList()
            val models = p.optJSONArray("models")?.let { ma ->
                (0 until ma.length()).map { ma.optString(it).trim() }.filter { it.isNotEmpty() }
            } ?: emptyList()
            out.add(
                ImageProvider(
                    id = p.optString("id"),
                    name = p.optString("name", p.optString("id")),
                    baseUrl = p.optString("baseUrl").trim().trimEnd('/'),
                    format = p.optString("format", "pollinations").trim().lowercase(),
                    enabled = p.optBoolean("enabled", true),
                    keys = keys,
                    models = models,
                    defaultSize = p.optString("defaultSize", "1024x1024")
                )
            )
        }
        return out
    }

    private fun parseProviders(arr: JSONArray?): List<Provider> {
        if (arr == null) return emptyList()
        val out = ArrayList<Provider>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            val keys = p.optJSONArray("keys")?.let { ka ->
                (0 until ka.length()).map { ka.optString(it).trim() }.filter { it.isNotEmpty() }
            } ?: emptyList()
            val models = ArrayList<ModelInfo>()
            p.optJSONArray("models")?.let { ma ->
                for (j in 0 until ma.length()) {
                    when (val mv = ma.opt(j)) {
                        is JSONObject -> models.add(ModelInfo(
                            id         = mv.optString("id").trim(),
                            agentMode  = mv.optBoolean("agentMode", false),
                            chatMode   = mv.optBoolean("chatMode", true),
                            explicit   = true,
                            plans      = parseStringSet(mv.optJSONArray("plans"))
                        ))
                        is String -> if (mv.isNotBlank()) models.add(ModelInfo(mv.trim(), agentMode = false, chatMode = true, explicit = false, plans = emptySet()))
                    }
                }
            }
            out.add(
                Provider(
                    id = p.optString("id"),
                    name = p.optString("name", p.optString("id")),
                    baseUrl = p.optString("baseUrl").trim().trimEnd('/'),
                    keys = keys,
                    enabled = p.optBoolean("enabled", true),
                    fetchModels = p.optBoolean("fetchModels", false),
                    agentModeDefault = p.optBoolean("agentModeDefault", false),
                    agentModeAllow = parseStringSet(p.optJSONArray("agentModeAllow")),
                    agentModeDeny = parseStringSet(p.optJSONArray("agentModeDeny")),
                    models = models
                )
            )
        }
        return out
    }

    private fun nextKey(p: Provider): String {
        if (p.keys.isEmpty()) return ""
        val bad = badKeys[p.id]
        // Exclude permanently-bad keys AND keys currently in rate-limit cooldown.
        val valid = p.keys.filter { (bad == null || it !in bad) && !isCooling(p.id, it) }
        // Fallbacks: if everything is cooling, allow non-bad keys; if all bad too, allow all.
        val keys = valid.ifEmpty {
            p.keys.filter { bad == null || it !in bad }.ifEmpty { p.keys }
        }
        if (keys.size == 1) return keys[0]
        val c = counters.getOrPut(p.id) { AtomicInteger(0) }
        var idx = c.getAndIncrement() % keys.size
        if (idx < 0) idx += keys.size
        return keys[idx]
    }

    private fun pick(list: List<Provider>, id: String): Provider? =
        list.firstOrNull { it.id == id && it.enabled } ?: list.firstOrNull { it.enabled }

    fun isAgentEnabled(p: Provider, modelId: String): Boolean {
        if (modelId in p.agentModeDeny) return false
        if (modelId in p.agentModeAllow) return true
        val m = p.models.firstOrNull { it.id == modelId }
        if (m != null && m.explicit) return m.agentMode
        return p.agentModeDefault
    }

    private fun firstModel(p: Provider, agentOnly: Boolean): String {
        val list = when {
            agentOnly -> p.models.filter { isAgentEnabled(p, it.id) }
            else      -> p.models.filter { it.chatMode }
        }
        return list.firstOrNull()?.id ?: p.models.firstOrNull()?.id ?: ""
    }

    private fun isCustom(context: Context) = PreferencesManager(context).isCustomEndpointEnabled()

    private fun customResolved(context: Context): Resolved {
        val pm = PreferencesManager(context)
        return Resolved("custom", pm.getBaseUrl().trimEnd('/'), pm.getApiKey(), pm.getModel())
    }

    /** Resolve the active CHAT endpoint. Falls back to first enabled provider if no default set. */
    fun chat(context: Context): Resolved {
        if (isCustom(context)) return customResolved(context)
        val p = chatProviders.firstOrNull { it.id == defChat.providerId && it.enabled }
            ?: chatProviders.firstOrNull { it.enabled }
            ?: return customResolved(context)
        val model = defChat.model.ifBlank { firstModel(p, false) }
        return Resolved(p.id, p.baseUrl, nextKey(p), model)
    }

    /** Resolve the active AGENT endpoint. Prefers agent-mode providers, then any enabled. */
    fun agent(context: Context): Resolved {
        if (isCustom(context)) return customResolved(context)
        val p = chatProviders.firstOrNull { it.id == defAgent.providerId && it.enabled }
            ?: chatProviders.firstOrNull { it.enabled && it.agentModeDefault }
            ?: chatProviders.firstOrNull { it.id == defChat.providerId && it.enabled }
            ?: chatProviders.firstOrNull { it.enabled }
            ?: return customResolved(context)
        val model = defAgent.model.ifBlank {
            firstModel(p, true).ifBlank { firstModel(p, false) }
        }
        return Resolved(p.id, p.baseUrl, nextKey(p), model)
    }

    /**
     * Resolve the endpoint for a SPECIFIC model id, routing to the provider that OWNS that model
     * (the #1 fix for "model from provider B sent to provider A's endpoint/key"). Falls back to the
     * default provider only when the model can't be matched.
     */
    fun resolveForModel(context: Context, modelId: String, agent: Boolean): Resolved {
        if (isCustom(context)) return customResolved(context)
        if (!loaded) return customResolved(context)
        // Virtual combo models (Auto / Auto-Agent / custom) — multi-provider rotation
        if (modelId.isNotBlank() && ComboRouter.isCombo(modelId)) {
            ComboRouter.resolveCombo(modelId)?.let { return it }
        }
        val enabled = chatProviders.filter { it.enabled }
        if (enabled.isEmpty()) return customResolved(context)
        val owner: Provider? = if (modelId.isBlank()) null else
            enabled.firstOrNull { p -> p.models.any { it.id == modelId } }
                ?: modelOwnerIndex[modelId]?.let { pid -> enabled.firstOrNull { it.id == pid } }
        val p = owner ?: if (agent)
            (enabled.firstOrNull { it.id == defAgent.providerId } ?: enabled.firstOrNull { it.agentModeDefault } ?: enabled.first())
        else
            (enabled.firstOrNull { it.id == defChat.providerId } ?: enabled.first())
        val model = modelId.ifBlank { firstModel(p, agent) }
        return Resolved(p.id, p.baseUrl, nextKey(p), model)
    }

    /** Resolve the dedicated VISION endpoint, or null to fall back to the chat provider. */
    fun vision(context: Context): Resolved? {
        if (isCustom(context)) return null
        if (!visionEnabled) return null
        val p = pick(visionProviders, defVision.providerId) ?: return null
        if (p.baseUrl.isBlank()) return null
        return Resolved(p.id, p.baseUrl, nextKey(p), defVision.model.ifBlank { firstModel(p, false) })
    }

    /**
     * Model list for the AGENT model picker.
     * ONLY shows models that have agentMode enabled (chatMode models disabled for agent are excluded).
     * Aggregates models from ALL enabled providers so nothing is hidden when
     * multiple providers are active.  Respects fetchModels flag — fetches live lists where needed.
     */
    // ── Model picker caches (chat + agent) ───────────────────────────────────
    @Volatile private var chatModelsCache: List<String> = emptyList()
    @Volatile private var chatModelsCacheAt: Long = 0L
    @Volatile private var agentModelsCache: List<String> = emptyList()
    @Volatile private var agentModelsCacheAt: Long = 0L
    private const val CHAT_MODELS_TTL_MS = 10 * 60_000L  // 10 min

    /** Last successful chat-model list. Safe to show immediately (may be stale). */
    fun cachedChatModels(): List<String> = chatModelsCache

    /** Last successful agent-model list. Safe to show immediately. */
    fun cachedAgentModels(): List<String> = agentModelsCache

    /**
     * Config-only model list — **no network**. Use to paint the picker instantly,
     * then refresh with [listChatModels] in the background.
     */
    fun listChatModelsFromConfig(context: Context): List<String> {
        if (isCustom(context)) return emptyList()
        val enabled = chatProviders.filter { it.enabled }
        val all = ArrayList<String>()
        val planId = PreferencesManager(context).getPlanId()
        // Combos first so Auto appears at the top of the picker
        all.addAll(ComboRouter.chatComboIds(planId))
        for (p in enabled) {
            val cm = p.models.filter { it.chatMode && planAllows(it.plans, planId) }.map { it.id }
            cm.forEach { modelOwnerIndex.putIfAbsent(it, p.id) }
            all.addAll(cm)
        }
        return all.distinct()
    }

    /** Config-only agent models — **no network**. */
    fun listAgentModelsFromConfig(context: Context): List<String> {
        if (isCustom(context)) return emptyList()
        val enabled = chatProviders.filter { it.enabled }
        val all = ArrayList<String>()
        val planId = PreferencesManager(context).getPlanId()
        all.addAll(ComboRouter.agentComboIds(planId))
        for (p in enabled) {
            val agentModels = p.models.filter { isAgentEnabled(p, it.id) && planAllows(it.plans, planId) }.map { it.id }
            agentModels.forEach { modelOwnerIndex.putIfAbsent(it, p.id) }
            all.addAll(agentModels)
        }
        return all.distinct()
    }

    fun listAgentModels(context: Context): Result<List<String>> {
        // Hot cache — reopen agent model sheet without waiting
        val cached = agentModelsCache
        if (cached.isNotEmpty() && System.currentTimeMillis() - agentModelsCacheAt < 30_000L) {
            return Result.success(filterModelsForUser(context, cached))
        }
        if (isCustom(context)) {
            val pm = PreferencesManager(context)
            return ApiClient.fetchModels(pm.getBaseUrl(), pm.getApiKey()).also { r ->
                r.getOrNull()?.let { storeAgentModelsCache(it) }
            }
        }
        val enabled = chatProviders.filter { it.enabled }
        val allModels = java.util.Collections.synchronizedList(mutableListOf<String>())
        var lastError: String? = null
        val planId = PreferencesManager(context).getPlanId()
        // Combos first (virtual Auto / Auto-Agent models)
        allModels.addAll(ComboRouter.agentComboIds(planId))
        if (enabled.isEmpty()) {
            val only = allModels.distinct()
            if (only.isNotEmpty()) {
                storeAgentModelsCache(only)
                return Result.success(only)
            }
            return Result.success(emptyList())
        }
        // Config models first (instant)
        for (p in enabled) {
            val agentModels = p.models.filter { isAgentEnabled(p, it.id) && planAllows(it.plans, planId) }.map { it.id }
            agentModels.forEach { modelOwnerIndex.putIfAbsent(it, p.id) }
            allModels.addAll(agentModels)
        }
        // Live fetch in parallel
        val toFetch = enabled.filter { it.fetchModels }
        if (toFetch.isNotEmpty()) {
            runBlocking {
                coroutineScope {
                    toFetch.map { p ->
                        async(Dispatchers.IO) {
                            ApiClient.fetchModels(p.baseUrl, nextKey(p)).fold(
                                onSuccess = { fetchedModels ->
                                    val agentModels = fetchedModels.filter { modelId -> isAgentEnabled(p, modelId) }
                                    agentModels.forEach { modelOwnerIndex[it] = p.id }
                                    allModels.addAll(agentModels)
                                },
                                onFailure = { lastError = it.message }
                            )
                        }
                    }.awaitAll()
                }
            }
        }
        val distinct = allModels.distinct().filter { isVisibleForPlan(it, planId) }
        if (distinct.isNotEmpty()) {
            storeAgentModelsCache(distinct)
            return Result.success(distinct)
        }
        return if (lastError != null) Result.failure(Exception(lastError))
        else Result.success(emptyList())
    }

    private fun storeAgentModelsCache(list: List<String>) {
        agentModelsCache = list
        agentModelsCacheAt = System.currentTimeMillis()
    }

    /**
     * Model list for the CHAT model picker.
     * Aggregates models from ALL enabled providers so nothing is hidden when
     * multiple providers are active.  Respects fetchModels flag — fetches live
     * lists where needed.
     *
     * Network fetches run **in parallel** with short timeouts; config models are
     * always included immediately. Results are cached for fast re-open.
     */
    fun listChatModels(context: Context): Result<List<String>> {
        // Hot cache — reopen picker without waiting on network
        val cached = cachedChatModels()
        if (cached.isNotEmpty() && System.currentTimeMillis() - chatModelsCacheAt < 30_000L) {
            return Result.success(filterModelsForUser(context, cached))
        }

        if (isCustom(context)) {
            val pm = PreferencesManager(context)
            return ApiClient.fetchModels(pm.getBaseUrl(), pm.getApiKey()).also { r ->
                r.getOrNull()?.let { storeChatModelsCache(it) }
            }
        }
        val enabled = chatProviders.filter { it.enabled }
        val allModels = java.util.Collections.synchronizedList(mutableListOf<String>())
        var lastError: String? = null

        val planId = PreferencesManager(context).getPlanId()
        // Combos first (virtual Auto models)
        allModels.addAll(ComboRouter.chatComboIds(planId))
        if (enabled.isEmpty()) {
            val only = allModels.distinct()
            if (only.isNotEmpty()) {
                storeChatModelsCache(only)
                return Result.success(only)
            }
            return Result.success(emptyList())
        }
        // Instant: config models (no network)
        for (p in enabled) {
            val cm = p.models.filter { it.chatMode && planAllows(it.plans, planId) }.map { it.id }
            cm.forEach { modelOwnerIndex.putIfAbsent(it, p.id) }
            allModels.addAll(cm)
        }

        // Live fetch only where needed — parallel
        val toFetch = enabled.filter { it.fetchModels }
        if (toFetch.isNotEmpty()) {
            runBlocking {
                coroutineScope {
                    toFetch.map { p ->
                        async(Dispatchers.IO) {
                            ApiClient.fetchModels(p.baseUrl, nextKey(p)).fold(
                                onSuccess = { fetched ->
                                    fetched.forEach { modelOwnerIndex[it] = p.id }
                                    allModels.addAll(fetched)
                                },
                                onFailure = { e -> lastError = e.message }
                            )
                        }
                    }.awaitAll()
                }
            }
        }

        val distinct = allModels.distinct().filter { isVisibleForPlan(it, planId) }
        if (distinct.isNotEmpty()) {
            storeChatModelsCache(distinct)
            return Result.success(distinct)
        }
        return if (lastError != null) Result.failure(Exception(lastError))
        else Result.success(emptyList())
    }

    private fun storeChatModelsCache(list: List<String>) {
        chatModelsCache = list
        chatModelsCacheAt = System.currentTimeMillis()
    }
}
