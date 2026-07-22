package com.ahamai.app.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("ahamai_prefs", Context.MODE_PRIVATE)

    fun save(providerName: String, baseUrl: String, apiKey: String, model: String) {
        prefs.edit()
            .putString(KEY_PROVIDER_NAME, providerName)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL, model)
            .apply()
    }

    fun saveProvider(providerName: String, baseUrl: String, apiKey: String) {
        prefs.edit()
            .putString(KEY_PROVIDER_NAME, providerName)
            .putString(KEY_BASE_URL, baseUrl)
            .putString(KEY_API_KEY, apiKey)
            .apply()
    }

    fun saveModel(model: String) {
        prefs.edit()
            .putString(KEY_MODEL, model)
            .apply()
    }

    // ---- Custom API endpoint (Settings toggle) ----
    // When OFF (default) the app uses the Remote Config providers. When ON the app uses the
    // user's own provider/baseUrl/apiKey/model configured via the provider screens.
    // ---- Profile avatar local cache (base64 data URL) so the DP shows instantly,
    // independent of the Firestore round-trip. ----
    fun getAvatar(): String = prefs.getString(KEY_AVATAR, "") ?: ""
    fun saveAvatar(dataUrl: String) {
        prefs.edit().putString(KEY_AVATAR, dataUrl).apply()
    }

    // ---- Pixel Art Avatar ----
    fun getPixelAvatarId(): String = prefs.getString(KEY_PIXEL_AVATAR, "") ?: ""
    fun savePixelAvatarId(id: String) {
        prefs.edit().putString(KEY_PIXEL_AVATAR, id).apply()
    }

    fun isCustomEndpointEnabled(): Boolean = prefs.getBoolean(KEY_CUSTOM_ENABLED, false)
    fun setCustomEndpointEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CUSTOM_ENABLED, enabled).apply()
    }

    // Chat model selection used in DEFAULT (Remote Config) mode — kept separate from the
    // custom-endpoint model (KEY_MODEL) so the two modes don't clobber each other.
    fun getChatModel(): String = prefs.getString(KEY_CHAT_MODEL, "") ?: ""
    fun saveChatModel(model: String) {
        prefs.edit().putString(KEY_CHAT_MODEL, model).apply()
    }

    fun saveVoice(voiceId: String) {
        prefs.edit()
            .putString(KEY_VOICE, voiceId)
            .apply()
    }

    fun saveImageModel(modelId: String) {
        prefs.edit()
            .putString(KEY_IMAGE_MODEL, modelId)
            .apply()
    }

    fun saveGithubToken(token: String) {
        prefs.edit().putString(KEY_GH_TOKEN, token.trim()).apply()
        // Keep Connectors "GitHub" row in sync so agent + connectors don't diverge.
        runCatching {
            if (token.isNotBlank()) {
                ConnectorsManager.setToken(ConnectorsManager.GITHUB, token.trim())
                ConnectorsManager.setVerified(ConnectorsManager.GITHUB, true)
                ConnectorsManager.setEnabled(ConnectorsManager.GITHUB, true)
            }
        }
    }

    fun saveConnectedRepo(fullName: String, branch: String) {
        prefs.edit()
            .putString(KEY_GH_REPO, fullName)
            .putString(KEY_GH_BRANCH, branch)
            .apply()
    }

    // Which signed-in account's UID the cached GitHub token/repo above belongs to. Since this
    // SharedPreferences file is shared by every account that has ever signed in on this device,
    // this stamp is what lets us detect "the cached token belongs to the PREVIOUS user" and wipe
    // it instead of silently handing user B a connection user A set up. See AuthManager.restoreGithubToken.
    fun saveGithubOwner(uid: String) {
        prefs.edit().putString(KEY_GH_OWNER_UID, uid).apply()
    }
    fun getGithubOwner(): String = prefs.getString(KEY_GH_OWNER_UID, "") ?: ""

    fun clearGithub() {
        prefs.edit()
            .remove(KEY_GH_TOKEN).remove(KEY_GH_REPO).remove(KEY_GH_BRANCH).remove(KEY_GH_OWNER_UID)
            .apply()
        runCatching { ConnectorsManager.disconnect(ConnectorsManager.GITHUB) }
    }

    // ---- E2B Cloud Engine ----
    fun saveE2bApiKey(key: String) {
        prefs.edit().putString(KEY_E2B_API_KEY, key).apply()
    }
    fun saveE2bTemplate(template: String) {
        prefs.edit().putString(KEY_E2B_TEMPLATE, template).apply()
    }
    fun saveE2bEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_E2B_ENABLED, enabled).apply()
    }
    /**
     * Cloud engine key — **E2B** (`e2b_…`) for shell + Playwright browser.
     * Resolution:
     *  1. User-saved personal key (Profile / BYOK)
     *  2. Sticky admin-pool E2B key for this process
     *  3. Empty (no hardcoded secrets shipped)
     *
     * Daytona `dtn_…` keys are no longer used for browser; CloudBrowser rejects them.
     */
    fun getE2bApiKey(): String {
        val user = (prefs.getString(KEY_E2B_API_KEY, "") ?: "").trim()
        if (user.isNotBlank()) return user
        stickyAdminKey?.let { if (it.isNotBlank()) return it }
        // Prefer E2B admin pool; fall back to legacy Daytona pool only if no E2B keys
        val adminE2b = runCatching { RemoteConfigManager.nextE2bKey() }.getOrNull().orEmpty()
        if (adminE2b.isNotBlank()) {
            stickyAdminKey = adminE2b
            return adminE2b
        }
        val adminLegacy = runCatching { RemoteConfigManager.nextDaytonaKey() }.getOrNull().orEmpty()
        if (adminLegacy.isNotBlank() && adminLegacy.startsWith("e2b_", ignoreCase = true)) {
            stickyAdminKey = adminLegacy
            return adminLegacy
        }
        return DEFAULT_E2B_API_KEY
    }

    /** True when key looks like Daytona (browser will refuse). */
    fun isDaytonaCloudKey(): Boolean {
        val k = getE2bApiKey()
        return k.startsWith("dtn_", ignoreCase = true)
    }

    fun isUsingAdminDaytonaKey(): Boolean {
        val user = (prefs.getString(KEY_E2B_API_KEY, "") ?: "").trim()
        return user.isBlank() && (
            runCatching { RemoteConfigManager.hasE2bKeys() }.getOrDefault(false) ||
                runCatching { RemoteConfigManager.hasDaytonaKeys() }.getOrDefault(false)
            )
    }
    fun getE2bTemplate(): String {
        // E2B template id — default "base" (full Linux for apt + Playwright).
        val v = (prefs.getString(KEY_E2B_TEMPLATE, DEFAULT_E2B_TEMPLATE) ?: DEFAULT_E2B_TEMPLATE).trim()
        return v.ifBlank { E2BClient.DEFAULT_TEMPLATE }
    }
    fun isE2bEnabled(): Boolean = prefs.getBoolean(KEY_E2B_ENABLED, true)
    fun isE2bConfigured(): Boolean {
        val k = getE2bApiKey()
        return k.isNotBlank() && !k.startsWith("dtn_", ignoreCase = true)
    }
    fun clearE2b() {
        prefs.edit()
            .remove(KEY_E2B_API_KEY)
            .remove(KEY_E2B_TEMPLATE)
            .remove(KEY_E2B_ENABLED)
            .apply()
    }

    // ---- Plan Management ----
    fun getPlan(): String = prefs.getString(KEY_PLAN, "free") ?: "free"
    fun setPlan(plan: String) { prefs.edit().putString(KEY_PLAN, plan).apply() }

    // Subscription plan id (free / pro / plus / enterprise) + billing currency toggle.
    // Legacy "starter" is remapped via Plans.byId → Pro.
    fun getPlanId(): String = prefs.getString(KEY_PLAN, "free") ?: "free"
    fun setPlanId(id: String) { prefs.edit().putString(KEY_PLAN, id).apply() }
    fun isCurrencyInr(): Boolean = prefs.getBoolean(KEY_CURRENCY_INR, false)
    fun setCurrencyInr(inr: Boolean) { prefs.edit().putBoolean(KEY_CURRENCY_INR, inr).apply() }

    fun getCredits(): Int = prefs.getInt(KEY_CREDITS, 0)
    fun setCredits(c: Int) { prefs.edit().putInt(KEY_CREDITS, c).apply() }
    fun useCredit(): Boolean {
        val c = getCredits()
        if (c <= 0) return false
        setCredits(c - 1)
        return true
    }
    fun addCredits(n: Int) { setCredits(getCredits() + n) }
    fun getBanned(): Boolean = prefs.getBoolean(KEY_BANNED, false)
    fun setBanned(b: Boolean) { prefs.edit().putBoolean(KEY_BANNED, b).apply() }

    // ---- Sign in with Google (Gemini free) ----
    fun saveGemini(access: String, refresh: String, expiresAt: Long, email: String) {
        prefs.edit()
            .putString(KEY_GEMINI_ACCESS, access)
            .putString(KEY_GEMINI_REFRESH, refresh)
            .putLong(KEY_GEMINI_EXPIRES, expiresAt)
            .putString(KEY_GEMINI_EMAIL, email)
            .apply()
    }

    fun getGeminiAccess(): String = prefs.getString(KEY_GEMINI_ACCESS, "") ?: ""
    fun getGeminiRefresh(): String = prefs.getString(KEY_GEMINI_REFRESH, "") ?: ""
    fun getGeminiExpires(): Long = prefs.getLong(KEY_GEMINI_EXPIRES, 0L)
    fun getGeminiEmail(): String = prefs.getString(KEY_GEMINI_EMAIL, "") ?: ""
    fun isGeminiSignedIn(): Boolean = getGeminiRefresh().isNotBlank()
    fun clearGemini() {
        prefs.edit()
            .remove(KEY_GEMINI_ACCESS).remove(KEY_GEMINI_REFRESH)
            .remove(KEY_GEMINI_EXPIRES).remove(KEY_GEMINI_EMAIL)
            .apply()
    }

    // ---- Freebuff / Codebuff ----
    fun getFreebuffToken(): String = prefs.getString(KEY_FREEBUFF_TOKEN, "") ?: ""
    fun saveFreebuffToken(token: String) {
        prefs.edit().putString(KEY_FREEBUFF_TOKEN, token).apply()
    }
    fun getFreebuffBaseUrl(): String = prefs.getString(KEY_FREEBUFF_BASE_URL, "https://www.codebuff.com") ?: "https://www.codebuff.com"
    fun saveFreebuffBaseUrl(url: String) {
        prefs.edit().putString(KEY_FREEBUFF_BASE_URL, url).apply()
    }

    fun isFreebuffConfigured(): Boolean = getFreebuffToken().isNotBlank()
    fun clearFreebuff() {
        prefs.edit()
            .remove(KEY_FREEBUFF_TOKEN).remove(KEY_FREEBUFF_BASE_URL)
            .apply()
    }

    fun getAgentModel(): String = prefs.getString(KEY_AGENT_MODEL, "") ?: ""
    fun setAgentModel(m: String) { prefs.edit().putString(KEY_AGENT_MODEL, m).apply() }

    fun getProviderName(): String = prefs.getString(KEY_PROVIDER_NAME, "") ?: ""
    fun getBaseUrl(): String = prefs.getString(KEY_BASE_URL, "") ?: ""
    fun getApiKey(): String = prefs.getString(KEY_API_KEY, "") ?: ""
    fun getModel(): String = prefs.getString(KEY_MODEL, "") ?: ""
    fun getVoice(): String = prefs.getString(KEY_VOICE, DEFAULT_VOICE) ?: DEFAULT_VOICE

    // ---- Custom system prompt for chat mode ----
    fun getCustomPrompt(): String = prefs.getString(KEY_CUSTOM_PROMPT, "") ?: ""
    fun saveCustomPrompt(prompt: String) {
        prefs.edit().putString(KEY_CUSTOM_PROMPT, prompt).apply()
    }

    fun getImageModel(): String = prefs.getString(KEY_IMAGE_MODEL, "pollinations") ?: "pollinations"
    fun getGithubToken(): String = prefs.getString(KEY_GH_TOKEN, "") ?: ""
    fun getConnectedRepo(): String = prefs.getString(KEY_GH_REPO, "") ?: ""
    fun getConnectedBranch(): String = prefs.getString(KEY_GH_BRANCH, "main") ?: "main"
    fun isGithubConnected(): Boolean = getGithubToken().isNotBlank()

    fun isConfigured(): Boolean {
        return getBaseUrl().isNotBlank() && getApiKey().isNotBlank() && getModel().isNotBlank()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    // ---- Onboarding ----
    fun isOnboardingCompleted(): Boolean = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    fun setOnboardingCompleted() {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, true).apply()
    }

    // ---- Theme mode: "system" | "light" | "dark" ----
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
    fun saveThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    // ---- Agent tool permission mode (Grok-style) ----
    // "readonly" | "ask" | "accept_edits" | "auto"
    fun getAgentPermissionMode(): String =
        prefs.getString(KEY_AGENT_PERM_MODE, "auto") ?: "auto"
    fun saveAgentPermissionMode(mode: String) {
        prefs.edit().putString(KEY_AGENT_PERM_MODE, mode).apply()
    }

    companion object {
        /** Process-wide sticky admin Daytona key (see [getE2bApiKey]). */
        @Volatile private var stickyAdminKey: String? = null

        /** Call after admin rotates the pool so the next [getE2bApiKey] re-picks. */
        fun clearStickyAdminDaytonaKey() { stickyAdminKey = null }

        private const val KEY_AGENT_MODEL = "agent_model"
        private const val KEY_PROVIDER_NAME = "provider_name"
        private const val KEY_FREEBUFF_TOKEN = "freebuff_token"
        private const val KEY_FREEBUFF_BASE_URL = "freebuff_base_url"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_CUSTOM_ENABLED = "custom_endpoint_enabled"
        private const val KEY_CHAT_MODEL = "chat_model_sel"
        private const val KEY_AVATAR = "avatar_data_url"
        private const val KEY_PIXEL_AVATAR = "pixel_avatar_id"
        private const val KEY_VOICE = "voice"
        private const val KEY_CUSTOM_PROMPT = "custom_prompt"
        private const val KEY_IMAGE_MODEL = "image_model"
        private const val KEY_GH_TOKEN = "gh_token"
        private const val KEY_GH_REPO = "gh_repo"
        private const val KEY_GH_BRANCH = "gh_branch"
        private const val KEY_GH_OWNER_UID = "gh_owner_uid"
        private const val KEY_E2B_API_KEY = "e2b_api_key"
        private const val KEY_E2B_TEMPLATE = "e2b_template"
        private const val KEY_E2B_ENABLED = "e2b_enabled"
        // Migrated to Daytona — the cloud engine key + default image ref.
        // No hardcoded cloud secrets. User / Remote Config must supply e2b_… key.
        private const val DEFAULT_E2B_API_KEY = ""
        private const val DEFAULT_E2B_TEMPLATE = "base"
        private const val KEY_GEMINI_ACCESS = "gemini_access"
        private const val KEY_GEMINI_REFRESH = "gemini_refresh"
        private const val KEY_GEMINI_EXPIRES = "gemini_expires"
        private const val KEY_GEMINI_EMAIL = "gemini_email"
        private const val KEY_PLAN = "plan"
        private const val KEY_CREDITS = "credits"
        private const val KEY_CURRENCY_INR = "currency_inr"
        private const val KEY_BANNED = "banned"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_AGENT_PERM_MODE = "agent_permission_mode"
        private const val DEFAULT_VOICE = "en-IN-PrabhatNeural"
    }
}
