package com.ahamai.app.data

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Central access point for all remote configuration:
 *   • Firebase Remote Config  — Brave/Sarvam keys, ai_endpoint, browser_agent, api_providers (seed)
 *   • Firestore config/providers  — api_providers JSON (admin-writable, wins over RC)
 *   • Firestore config/settings   — kill_enabled, maintenance_text, e2b_free_enabled
 *   • Firestore config/systemPrompts — chat & agent system prompts (admin-editable)
 *
 * Reactive StateFlows let Compose UIs observe kill/maintenance changes without polling.
 */
object RemoteConfigManager {

    private const val TAG = "RemoteConfigManager"

    const val KEY_BRAVE         = "brave_api_keys"
    const val KEY_SARVAM        = "sarvam_api_keys"
    const val KEY_AI_ENDPOINT   = "ai_endpoint"
    const val KEY_BROWSER_AGENT = "browser_agent"
    const val KEY_API_PROVIDERS = "api_providers"
    const val KEY_GITHUB_CLIENT_ID = "github_client_id"

    // ── Reactive state (observed by Compose via collectAsState()) ──────────────
    private val _killEnabled     = MutableStateFlow(false)
    private val _maintenanceText = MutableStateFlow("")
    private val _maintenanceHtml = MutableStateFlow("")
    private val _useCustomHtml   = MutableStateFlow(false)
    val killFlow:             StateFlow<Boolean> = _killEnabled
    val maintenanceFlow:      StateFlow<String>  = _maintenanceText
    val maintenanceHtmlFlow:  StateFlow<String>  = _maintenanceHtml
    val useCustomHtmlFlow:    StateFlow<Boolean> = _useCustomHtml

    // Ad settings change version — incremented on every Firestore settings snapshot.
    // Compose UIs can observe this via .collectAsState() to recompute ad gates.
    private val _adSettingsVersion = MutableStateFlow(0)
    val adSettingsVersionFlow: StateFlow<Int> = _adSettingsVersion

    /** Real-time Firestore listener handle (prevents double registration). */
    private var settingsListener: com.google.firebase.firestore.ListenerRegistration? = null

    // ── In-memory snapshots ────────────────────────────────────────────────────
    @Volatile private var braveKeysCache:   List<String> = emptyList()
    @Volatile private var sarvamKeysCache:  List<String> = emptyList()
    @Volatile private var aiEndpointCache:  String       = ""
    @Volatile private var browserAgentCache: String      = ""
    @Volatile private var githubClientIdCache: String    = ""

    // Settings (also readable via killFlow / maintenanceFlow)
    @Volatile var killEnabled:      Boolean = false
    @Volatile var maintenanceText:  String  = ""
    @Volatile var maintenanceHtml:  String  = ""
    @Volatile var useCustomHtml:    Boolean = false
    @Volatile var e2bFreeEnabled:   Boolean = true
    @Volatile var webSearchEnabled: Boolean = true
    /** Inbox for in-app Feedback — set from Admin → Control (`feedback_email` in config/settings). */
    @Volatile var feedbackEmail:    String  = ""
    // ── AdMob ads: full admin control (config/settings doc) ──────────────────────
    @Volatile var adsEnabled:                Boolean = true   // master switch (all ads)
    @Volatile var adsFreeOnly:               Boolean = true   // true = free plan only (like ChatGPT)
    @Volatile var chatAdsEnabled:            Boolean = true   // inline native card in chat
    // Defaults favour visibility: show chat card after every answer, agent interstitial
    // after every completion. Admin / Firestore can raise these for production density.
    @Volatile var chatAdInterval:            Int     = 1      // card after every Nth answer
    @Volatile var agentAdsEnabled:           Boolean = true   // video ads in agent (master)
    @Volatile var agentBuildAdEnabled:       Boolean = true   // rewarded on cloud build
    @Volatile var agentCompletionAdEnabled:  Boolean = true   // interstitial on task complete
    @Volatile var agentCompletionInterval:   Int     = 1      // every Nth completion
    @Volatile var agentOverLimitAdEnabled:   Boolean = true   // rewarded when over token allowance
    @Volatile var adMinGapSeconds:           Int     = 60     // min gap between full-screen ads
    // Ad unit id overrides — blank / invalid = use Google test units.
    @Volatile var chatNativeAdUnit:          String  = ""
    @Volatile var agentRewardedAdUnit:       String  = ""
    @Volatile var agentInterstitialAdUnit:   String  = ""

    /**
     * Apply a full ads config snapshot (admin Save or Firestore live listener) and bump
     * [adSettingsVersionFlow] so chat/agent UIs recompute gates immediately.
     * Without the version bump, Compose can keep a stale "ads off" state after re-enable.
     */
    fun applyAdSettings(
        enabled: Boolean = adsEnabled,
        freeOnly: Boolean = adsFreeOnly,
        chatOn: Boolean = chatAdsEnabled,
        chatInterval: Int = chatAdInterval,
        agentOn: Boolean = agentAdsEnabled,
        buildOn: Boolean = agentBuildAdEnabled,
        completionOn: Boolean = agentCompletionAdEnabled,
        completionInterval: Int = agentCompletionInterval,
        overLimitOn: Boolean = agentOverLimitAdEnabled,
        minGapSeconds: Int = adMinGapSeconds,
        nativeUnit: String = chatNativeAdUnit,
        rewardedUnit: String = agentRewardedAdUnit,
        interstitialUnit: String = agentInterstitialAdUnit,
    ) {
        adsEnabled = enabled
        adsFreeOnly = freeOnly
        chatAdsEnabled = chatOn
        chatAdInterval = chatInterval.coerceAtLeast(1)
        agentAdsEnabled = agentOn
        agentBuildAdEnabled = buildOn
        agentCompletionAdEnabled = completionOn
        agentCompletionInterval = completionInterval.coerceAtLeast(1)
        agentOverLimitAdEnabled = overLimitOn
        adMinGapSeconds = minGapSeconds.coerceAtLeast(0)
        // Sanitize unit IDs: blank OR invalid → empty (callers fall back to Google test units).
        chatNativeAdUnit = sanitizeAdUnit(nativeUnit)
        agentRewardedAdUnit = sanitizeAdUnit(rewardedUnit)
        agentInterstitialAdUnit = sanitizeAdUnit(interstitialUnit)
        bumpAdSettingsVersion()
        Log.i(
            TAG,
            "Ad settings applied: master=$enabled chat=$chatOn agent=$agentOn freeOnly=$freeOnly " +
                "interval=$chatAdInterval native=${chatNativeAdUnit.ifBlank { "(test)" }}"
        )
    }

    fun bumpAdSettingsVersion() {
        _adSettingsVersion.value = _adSettingsVersion.value + 1
    }

    /**
     * Accept only well-formed AdMob unit ids (`ca-app-pub-XXXXXXXXXXXXXXXX/YYYYYYYYYY`).
     * Placeholders, ellipses, typos, or production units pasted wrong caused silent load
     * failures after admin Save — those must fall back to Google test units.
     */
    fun sanitizeAdUnit(raw: String?): String {
        val u = raw?.trim().orEmpty()
        if (u.isEmpty()) return ""
        // Reject common placeholder junk that used to slip through ifBlank checks.
        if (u.contains("…") || u.contains("...") || u.contains("XXXX", ignoreCase = true)) return ""
        val ok = Regex("""^ca-app-pub-\d{16}/\d{4,}$""").matches(u)
        return if (ok) u else ""
    }

    /** Robust bool read — Firestore can return Boolean, Long 0/1, or string from console edits. */
    fun firestoreBool(doc: com.google.firebase.firestore.DocumentSnapshot, key: String, default: Boolean): Boolean {
        return when (val v = doc.get(key)) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> when (v.trim().lowercase()) {
                "true", "1", "yes", "on" -> true
                "false", "0", "no", "off" -> false
                else -> default
            }
            null -> default
            else -> default
        }
    }

    // System prompts — fully remote (Admin → Control). Blank = empty (no APK hardcode fallback).
    @Volatile var chatSystemPrompt:  String = ""
    @Volatile var agentSystemPrompt: String = ""

    // Brave Search keys — set by admin from Providers tab, overrides RC brave_api_keys
    @Volatile var braveApiKeys: List<String> = emptyList()

    // Deepgram Voice Agent keys — set by admin from Providers tab
    @Volatile var deepgramApiKeys: List<String> = emptyList()

    // AssemblyAI Voice keys — set by admin from Providers tab
    @Volatile var assemblyAiApiKeys: List<String> = emptyList()

    // Daytona cloud sandbox keys (dtn_…) — set by admin from Providers tab.
    // Used by the cloud browser / terminal / tools (PreferencesManager.getE2bApiKey).
    @Volatile var daytonaApiKeys: List<String> = emptyList()

    private val daytonaKeyCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val daytonaBadKeys =
        java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    // ── Init ──────────────────────────────────────────────────────────────────
    fun init(context: Context) {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context)

            val rc = FirebaseRemoteConfig.getInstance()
            rc.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .setFetchTimeoutInSeconds(20)
                    .build()
            )
            rc.setDefaultsAsync(
                mapOf<String, Any>(
                    KEY_BRAVE         to "",
                    KEY_SARVAM        to "",
                    KEY_AI_ENDPOINT   to "",
                    KEY_BROWSER_AGENT to "",
                    KEY_API_PROVIDERS to "",
                    KEY_GITHUB_CLIENT_ID to ""
                )
            )

            // Use already-activated values immediately (offline-safe)
            refreshCacheFromRC(rc)

            // Fetch latest RC values in background
            rc.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    refreshCacheFromRC(rc)
                    Log.i(TAG, "Remote Config activated.")
                } else {
                    Log.w(TAG, "Remote Config fetch failed", task.exception)
                }
                // Always reload Firestore config after RC (Firestore wins over RC for providers)
                loadFirestoreConfig()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remote Config init failed: ${e.message}")
        }
    }

    /**
     * Load providers JSON, settings and system prompts from Firestore config collection.
     * Must be called AFTER the user is authenticated (Firestore rules require auth).
     * Called automatically after RC fetch, and explicitly after sign-in in MainActivity.
     */
    fun loadFirestoreConfig() {
        try {
            val db = FirebaseFirestore.getInstance()

            // config/providers  { json: "<api_providers JSON>", braveKeys: ["key1",...] }
            db.collection("config").document("providers").get()
                .addOnSuccessListener { doc ->
                    val json = doc.getString("json")
                    if (!json.isNullOrBlank()) {
                        ApiConfig.update(json)
                        Log.i(TAG, "api_providers loaded from Firestore")
                    }
                    // Brave Search keys stored alongside the providers JSON in the same doc
                    @Suppress("UNCHECKED_CAST")
                    val keys = doc.get("braveKeys") as? List<String>
                    if (!keys.isNullOrEmpty()) {
                        braveApiKeys = keys
                        Log.i(TAG, "Brave keys loaded from Firestore (${keys.size} keys)")
                    }
                    val dgKeys = doc.get("deepgramKeys") as? List<String>
                    if (!dgKeys.isNullOrEmpty()) {
                        deepgramApiKeys = dgKeys
                        Log.i(TAG, "Deepgram keys loaded from Firestore (${dgKeys.size} keys)")
                    }
                    val aaKeys = doc.get("assemblyAiKeys") as? List<String>
                    if (!aaKeys.isNullOrEmpty()) {
                        assemblyAiApiKeys = aaKeys
                        Log.i(TAG, "AssemblyAI keys loaded from Firestore (${aaKeys.size} keys)")
                    }
                    // Cloud sandbox keys: prefer e2bKeys (E2B Playwright), merge legacy daytonaKeys
                    if (doc.contains("e2bKeys") || doc.contains("daytonaKeys")) {
                        fun listField(name: String): List<String> {
                            @Suppress("UNCHECKED_CAST")
                            return (doc.get(name) as? List<*>)
                                ?.filterIsInstance<String>()
                                ?.map { it.trim() }
                                ?.filter { it.isNotBlank() }
                                ?: emptyList()
                        }
                        val merged = (listField("e2bKeys") + listField("daytonaKeys")).distinct()
                        daytonaApiKeys = merged
                        daytonaBadKeys.clear()
                        Log.i(TAG, "Cloud sandbox keys loaded (${merged.size}; e2b=${merged.count { it.startsWith("e2b_") }})")
                    }
                }
                .addOnFailureListener { Log.w(TAG, "config/providers read failed: ${it.message}") }

            // config/settings — REAL-TIME listener (not one-shot), so ad/switch changes
            // propagate instantly to all live devices without a restart.
            settingsListener?.remove() // remove any prior listener before registering a new one
            settingsListener = db.collection("config").document("settings")
                .addSnapshotListener { doc, error ->
                    if (error != null) {
                        Log.w(TAG, "config/settings listener error: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (doc == null || !doc.exists()) return@addSnapshotListener
                    val kill    = firestoreBool(doc, "maintenance_enabled", false)
                    val maint   = doc.getString("maintenance_text")     ?: ""
                    val html    = doc.getString("maintenance_html")     ?: ""
                    val useHtml = firestoreBool(doc, "use_custom_html", false)
                    val wsearch = firestoreBool(doc, "web_search_enabled", true)
                    val e2b     = firestoreBool(doc, "e2b_free_enabled", true)
                    doc.getString("github_client_id")?.let { if (it.isNotBlank()) githubClientIdCache = it }
                    doc.getString("feedback_email")?.trim()?.let { feedbackEmail = it }
                    applyMaintenanceSettings(kill, maint, html, useHtml)
                    e2bFreeEnabled   = e2b
                    webSearchEnabled = wsearch
                    // Ads — single apply path so version always bumps after off→on
                    val chatIv = when (val v = doc.get("chat_ad_interval")) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 1
                        else -> 1
                    }.coerceAtLeast(1)
                    val compIv = when (val v = doc.get("agent_completion_interval")) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 1
                        else -> 1
                    }.coerceAtLeast(1)
                    val gapSec = when (val v = doc.get("ad_min_gap_seconds")) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 60
                        else -> 60
                    }.coerceAtLeast(0)
                    applyAdSettings(
                        enabled = firestoreBool(doc, "ads_enabled", true),
                        freeOnly = firestoreBool(doc, "ads_free_only", true),
                        chatOn = firestoreBool(doc, "chat_ads_enabled", true),
                        chatInterval = chatIv,
                        agentOn = firestoreBool(doc, "agent_ads_enabled", true),
                        buildOn = firestoreBool(doc, "agent_build_ad", true),
                        completionOn = firestoreBool(doc, "agent_completion_ad", true),
                        completionInterval = compIv,
                        overLimitOn = firestoreBool(doc, "agent_overlimit_ad", true),
                        minGapSeconds = gapSec,
                        nativeUnit = doc.getString("chat_native_unit") ?: "",
                        rewardedUnit = doc.getString("agent_rewarded_unit") ?: "",
                        interstitialUnit = doc.getString("agent_interstitial_unit") ?: "",
                    )
                    Log.i(TAG, "Settings live-updated. maintenance=$kill ads=$adsEnabled webSearch=$wsearch")
                }

            // config/systemPrompts  { chat: "...", agent: "..." }
            db.collection("config").document("systemPrompts").get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        chatSystemPrompt  = doc.getString("chat")  ?: ""
                        agentSystemPrompt = doc.getString("agent") ?: ""
                        Log.i(TAG, "System prompts loaded from Firestore")
                    }
                }
                .addOnFailureListener { Log.w(TAG, "config/systemPrompts read failed: ${it.message}") }

            // config/skills — admin controls availability, icons, overrides, admin-authored skills
            db.collection("config").document("skills").get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        SkillManager.applyAdminConfigFromFirestore(doc.data ?: emptyMap())
                        Log.i(TAG, "Skills config loaded from Firestore")
                    } else {
                        SkillManager.applyAdminConfig(emptyList(), null)
                    }
                }
                .addOnFailureListener { Log.w(TAG, "config/skills read failed: ${it.message}") }

        } catch (e: Exception) {
            Log.w(TAG, "loadFirestoreConfig error: ${e.message}")
        }
    }

    /** Push maintenance fields into memory + reactive flows (admin panel + Firestore loader). */
    fun applyMaintenanceSettings(
        enabled: Boolean,
        text: String,
        html: String = maintenanceHtml,
        useHtml: Boolean = useCustomHtml
    ) {
        killEnabled = enabled
        maintenanceText = text
        maintenanceHtml = html
        useCustomHtml = useHtml
        _killEnabled.value = enabled
        _maintenanceText.value = text
        _maintenanceHtml.value = html
        _useCustomHtml.value = useHtml
    }

    private fun refreshCacheFromRC(rc: FirebaseRemoteConfig) {
        braveKeysCache    = splitKeys(rc.getString(KEY_BRAVE))
        sarvamKeysCache   = splitKeys(rc.getString(KEY_SARVAM))
        aiEndpointCache   = rc.getString(KEY_AI_ENDPOINT).trim()
        browserAgentCache = rc.getString(KEY_BROWSER_AGENT).trim()
        rc.getString(KEY_GITHUB_CLIENT_ID).trim().let { if (it.isNotBlank()) githubClientIdCache = it }
        // Feed RC providers as fallback — Firestore will override via loadFirestoreConfig()
        val rcProviders = rc.getString(KEY_API_PROVIDERS)
        if (rcProviders.isNotBlank()) ApiConfig.update(rcProviders)
    }

    private fun splitKeys(raw: String): List<String> =
        raw.split(',', '\n', ';').map { it.trim() }.filter { it.isNotEmpty() }

    /** Brave keys — Firestore (admin-managed) takes priority, RC brave_api_keys is fallback. */
    fun braveKeys(): List<String> = braveApiKeys.ifEmpty { braveKeysCache }.distinct()
    fun sarvamKeys():   List<String> = sarvamKeysCache.distinct()
    fun aiEndpoint():   String       = aiEndpointCache
    fun browserAgent(): String       = browserAgentCache
    fun githubClientId(): String     = githubClientIdCache
    fun hasBraveKeys(): Boolean      = braveKeysCache.isNotEmpty()

    /** All admin-managed Daytona keys (legacy), de-duplicated. */
    fun daytonaKeys(): List<String> = daytonaApiKeys.map { it.trim() }.filter { it.isNotBlank() }.distinct()

    fun hasDaytonaKeys(): Boolean = daytonaKeys().isNotEmpty()

    /**
     * Admin E2B keys (`e2b_…`) for cloud shell + Playwright browser.
     * Sources: firestore `e2bKeys` / `daytonaKeys` entries that start with e2b_.
     */
    fun e2bKeys(): List<String> = daytonaKeys().filter { it.startsWith("e2b_", ignoreCase = true) }

    fun hasE2bKeys(): Boolean = e2bKeys().isNotEmpty()

    fun nextE2bKey(): String {
        val all = e2bKeys()
        if (all.isEmpty()) return ""
        val valid = all.filter { it !in daytonaBadKeys }.ifEmpty { all }
        if (valid.size == 1) return valid[0]
        var idx = daytonaKeyCounter.getAndIncrement() % valid.size
        if (idx < 0) idx += valid.size
        return valid[idx]
    }

    /**
     * Round-robin a key from the admin pool (legacy name). Prefers e2b_ keys.
     */
    fun nextDaytonaKey(): String {
        nextE2bKey().takeIf { it.isNotBlank() }?.let { return it }
        val all = daytonaKeys()
        if (all.isEmpty()) return ""
        val valid = all.filter { it !in daytonaBadKeys }.ifEmpty { all }
        if (valid.size == 1) return valid[0]
        var idx = daytonaKeyCounter.getAndIncrement() % valid.size
        if (idx < 0) idx += valid.size
        return valid[idx]
    }

    /** Mark a Daytona key as permanently invalid for this process (e.g. HTTP 401). */
    fun markDaytonaKeyBad(key: String) {
        if (key.isBlank()) return
        daytonaBadKeys.add(key)
        Log.w(TAG, "Marked Daytona key bad: ${key.take(8)}…")
    }

    fun clearDaytonaBadKeys() {
        daytonaBadKeys.clear()
    }
}
