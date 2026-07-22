package com.ahamai.app.screens

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahamai.app.data.ApiClient
import com.ahamai.app.data.ApiConfig
import com.ahamai.app.data.AuthManager
import com.ahamai.app.data.PreferencesManager
import com.ahamai.app.data.RemoteConfigManager
import com.ahamai.app.data.SkillManager
import com.ahamai.app.data.UsageTracker
import com.ahamai.app.data.WebTools
import com.ahamai.app.ui.icons.AdminIcons
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import com.ahamai.app.ui.theme.OswaldFamily
import com.ahamai.app.ui.theme.UnicaOneRegular
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ── Data models ──────────────────────────────────────────────────────────────

data class AdminUser(
    val uid: String, val name: String, val email: String,
    val plan: String, val banned: Boolean,
    val usageToday: UsageTracker.DailyUsage = UsageTracker.DailyUsage(date = "")
)

data class ModelConfig(
    val id: String,
    val enabledChat: Boolean = true,
    val enabledAgent: Boolean = false,
    /** Empty = all plans. Else free / pro / plus / enterprise. */
    val plans: List<String> = emptyList()
)

data class ProviderEntry(
    val id: String, val name: String, val baseUrl: String,
    val enabled: Boolean, val keys: List<String>,
    val models: List<ModelConfig>,
    val agentModeDefault: Boolean, val fetchModels: Boolean
)

data class AuditEntry(val ts: Long, val action: String, val by: String, val detail: String)
data class DailyStats(val date: String, val users: Int, val calls: Int, val tokens: Int, val chat: Int, val agent: Int, val search: Int)

private enum class AdminTab(val key: String, val label: String) {
    Overview("overview", "Home"),
    Users("users", "People"),
    Providers("providers", "API"),
    Combos("combos", "Combos"),
    Network("network", "Network"),
    Skills("skills", "Skills"),
    Analytics("analytics", "Insights"),
    Control("control", "Settings"),
    Access("access", "Access");

    val icon: ImageVector
        get() = when (this) {
            Overview -> Lucide.Activity
            Users -> Lucide.Users
            Providers -> Lucide.Server
            Combos -> Lucide.Layers
            Network -> Lucide.Globe
            Skills -> Lucide.Skills
            Analytics -> Lucide.BarChart
            Control -> Lucide.Sliders
            Access -> Lucide.AccessCard
        }
}

// ── Palette (iOS-style, matches app ChatPalette) ─────────────────────────────

private data class AdminColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val accent: Color,
    val accentInk: Color,
    val ok: Color,
    val danger: Color,
    val warn: Color,
    val chipOn: Color,
    val chipOff: Color,
    val chart: Color,
)

@Composable
private fun rememberAdminColors(isDark: Boolean): AdminColors = remember(isDark) {
    val p = com.ahamai.app.ui.theme.chatColors(isDark)
    AdminColors(
        bg = p.bg,
        // Cleaner cards: elevated white / soft charcoal, no harsh contrast
        surface = if (isDark) Color(0xFF1A1A1E) else Color(0xFFFFFFFF),
        surface2 = if (isDark) Color(0xFF242428) else Color(0xFFF2F2F5),
        ink = p.ink,
        muted = p.inkSecondary,
        line = if (isDark) Color(0xFF2E2E34) else Color(0xFFE8E8ED),
        accent = p.accent,
        accentInk = Color.White,
        ok = Color(0xFF34C759),
        danger = p.danger,
        warn = Color(0xFFFF9F0A),
        // Selected tab: soft blue fill (not solid neon bar)
        chipOn = p.accent.copy(alpha = 0.14f),
        chipOff = Color.Transparent,
        chart = p.accent,
    )
}

// ── JSON helpers ─────────────────────────────────────────────────────────────

private fun parseProviders(json: String): Triple<List<ProviderEntry>, JSONObject, Int> = try {
    val root = JSONObject(json)
    // Load combos + proxy into runtime managers (safe if blocks missing)
    com.ahamai.app.data.ComboRouter.updateFromJson(root)
    com.ahamai.app.data.ProxyManager.updateFromJson(root)
    val arr = root.optJSONObject("chat")?.optJSONArray("providers") ?: JSONArray()
    val list = (0 until arr.length()).map { i ->
        val p = arr.getJSONObject(i)
        val kArr = p.optJSONArray("keys") ?: JSONArray()
        val mArr = p.optJSONArray("models") ?: JSONArray()
        ProviderEntry(
            id = p.optString("id", ""), name = p.optString("name", ""),
            baseUrl = p.optString("baseUrl", ""), enabled = p.optBoolean("enabled", true),
            keys = (0 until kArr.length()).map { kArr.getString(it) },
            models = (0 until mArr.length()).mapNotNull { j ->
                val m = mArr.optJSONObject(j)
                if (m != null) {
                    val pa = m.optJSONArray("plans")
                    val plans = if (pa == null) emptyList() else
                        (0 until pa.length()).map { pa.optString(it).trim().lowercase() }.filter { it.isNotBlank() }
                    ModelConfig(
                        id = m.optString("id", ""),
                        enabledChat = m.optBoolean("chatMode", true),
                        enabledAgent = m.optBoolean("agentMode", false),
                        plans = plans
                    ).takeIf { it.id.isNotBlank() }
                } else mArr.optString(j, "").takeIf { it.isNotBlank() }?.let { ModelConfig(it, true, false) }
            },
            agentModeDefault = p.optBoolean("agentModeDefault", false),
            fetchModels = p.optBoolean("fetchModels", false)
        )
    }
    Triple(list, root.optJSONObject("defaults") ?: JSONObject(), root.optInt("version", 7))
} catch (_: Exception) {
    Triple(emptyList(), JSONObject(), 7)
}

/** Full providers JSON including combos + proxy (9Router-style). */
private fun buildProvidersJson(
    providers: List<ProviderEntry>,
    defaults: JSONObject,
    version: Int,
    preserveRoot: JSONObject? = null
): String {
    val root = JSONObject()
    // Preserve unknown top-level keys (vision, imageGeneration, cache, fallback…) when possible
    if (preserveRoot != null) {
        val keys = preserveRoot.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k !in setOf("chat", "combos", "proxy", "version", "rotation", "defaults")) {
                root.put(k, preserveRoot.get(k))
            }
        }
    }
    root.put("version", version); root.put("rotation", "round_robin"); root.put("defaults", defaults)
    val arr = JSONArray()
    providers.forEach { p ->
        val obj = JSONObject()
        obj.put("id", p.id); obj.put("name", p.name); obj.put("baseUrl", p.baseUrl)
        obj.put("enabled", p.enabled); obj.put("auth", "bearer"); obj.put("authHeader", "Authorization")
        obj.put("fetchModels", p.fetchModels); obj.put("agentModeDefault", p.agentModeDefault)
        obj.put("agentModeAllow", JSONArray()); obj.put("agentModeDeny", JSONArray())
        val kArr = JSONArray(); p.keys.forEach { kArr.put(it) }; obj.put("keys", kArr)
        val mArr = JSONArray()
        p.models.forEach { m ->
            val mObj = JSONObject(); mObj.put("id", m.id); mObj.put("chatMode", m.enabledChat)
            mObj.put("agentMode", m.enabledAgent)
            val pArr = JSONArray(); m.plans.forEach { pArr.put(it) }; mObj.put("plans", pArr)
            mArr.put(mObj)
        }
        obj.put("models", mArr); arr.put(obj)
    }
    root.put("chat", JSONObject().put("providers", arr))
    root.put("combos", com.ahamai.app.data.ComboRouter.toJsonObject())
    root.put("proxy", com.ahamai.app.data.ProxyManager.toJsonObject())
    if (!root.has("fallback")) {
        root.put("fallback", JSONObject().put("enabled", true).put("maxProviders", 4))
    }
    return root.toString(2)
}

private fun fmtTok(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> "$n"
}

private fun fmtTs(ts: Long): String =
    if (ts <= 0) "" else java.text.SimpleDateFormat("MMM d · HH:mm", java.util.Locale.US).format(java.util.Date(ts))

private val analyticsCache = mutableMapOf<String, Pair<Long, List<DailyStats>>>()
private const val CACHE_DURATION_MS = 5 * 60 * 1000L

private suspend fun fetchAnalyticsData(days: Int): List<DailyStats> = withContext(Dispatchers.IO) {
    val cacheKey = "analytics_$days"
    val cached = analyticsCache[cacheKey]
    if (cached != null && System.currentTimeMillis() - cached.first < CACHE_DURATION_MS) return@withContext cached.second

    val stats = mutableListOf<DailyStats>()
    try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        val db = FirebaseFirestore.getInstance()
        val allUsers = db.collection("users").get().await().documents.map { it.id }

        for (i in days - 1 downTo 0) {
            cal.time = java.util.Date()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -i)
            val dateStr = sdf.format(cal.time)
            var dayUsers = 0; var dayChat = 0; var dayAgent = 0; var daySearch = 0; var dayTokens = 0
            val usageDocs = allUsers.chunked(10).flatMap { chunk ->
                chunk.map { uid ->
                    async {
                        try {
                            db.collection("users").document(uid).collection("usage").document(dateStr).get().await()
                        } catch (_: Exception) {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            for (doc in usageDocs) {
                if (doc.exists()) {
                    dayUsers++
                    dayChat += (doc.getLong("chat") ?: 0).toInt()
                    dayAgent += (doc.getLong("agent") ?: 0).toInt()
                    daySearch += (doc.getLong("search") ?: 0).toInt()
                    dayTokens += (doc.getLong("tokens") ?: 0).toInt()
                }
            }
            stats.add(
                DailyStats(
                    date = dateStr, users = dayUsers, calls = dayChat + dayAgent + daySearch,
                    tokens = dayTokens, chat = dayChat, agent = dayAgent, search = daySearch
                )
            )
        }
        analyticsCache[cacheKey] = System.currentTimeMillis() to stats
    } catch (e: Exception) {
        android.util.Log.e("AdminScreen", "Analytics fetch error: ${e.message}")
    }
    stats
}

// ── AdminScreen ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val c = rememberAdminColors(isDark)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(AdminTab.Overview) }

    // Overview
    var totalUsers by remember { mutableIntStateOf(0) }
    var callsToday by remember { mutableIntStateOf(0) }
    var tokensToday by remember { mutableIntStateOf(0) }
    var recentUsers by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var dashLoading by remember { mutableStateOf(true) }

    // Users
    var allUsers by remember { mutableStateOf<List<AdminUser>>(emptyList()) }
    var usersLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Providers
    var providers by remember { mutableStateOf<List<ProviderEntry>>(emptyList()) }
    var defaults by remember { mutableStateOf(JSONObject()) }
    var jsonVersion by remember { mutableIntStateOf(7) }
    var provLoading by remember { mutableStateOf(false) }
    var provSaveMsg by remember { mutableStateOf<String?>(null) }
    var showAddProv by remember { mutableStateOf(false) }
    var braveKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var braveSaveMsg by remember { mutableStateOf<String?>(null) }
    var deepgramKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var deepgramSaveMsg by remember { mutableStateOf<String?>(null) }
    var assemblyAiKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var assemblyAiSaveMsg by remember { mutableStateOf<String?>(null) }
    var daytonaKeys by remember { mutableStateOf<List<String>>(emptyList()) }
    var daytonaSaveMsg by remember { mutableStateOf<String?>(null) }
    var testingKeys by remember { mutableStateOf(false) }
    var testLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var badKeys by remember { mutableStateOf<Set<Pair<String, String>>>(emptySet()) }
    var providersRawJson by remember { mutableStateOf<JSONObject?>(null) }
    // Combos / Network
    var comboList by remember { mutableStateOf(com.ahamai.app.data.ComboRouter.all()) }
    var combosEnabled by remember { mutableStateOf(com.ahamai.app.data.ComboRouter.isEnabled()) }
    var comboSaveMsg by remember { mutableStateOf<String?>(null) }
    var comboTestLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var comboTesting by remember { mutableStateOf(false) }
    var proxyCfg by remember { mutableStateOf(com.ahamai.app.data.ProxyManager.config()) }
    var proxySaveMsg by remember { mutableStateOf<String?>(null) }
    var proxyTestMsg by remember { mutableStateOf<String?>(null) }
    var proxyTesting by remember { mutableStateOf(false) }

    // Skills (admin global)
    var skillTick by remember { mutableIntStateOf(0) }
    var skillSaveMsg by remember { mutableStateOf<String?>(null) }
    var skillLoading by remember { mutableStateOf(false) }
    var skillSearch by remember { mutableStateOf("") }

    // Control
    var maintenance by remember { mutableStateOf("") }
    var maintenanceOn by remember { mutableStateOf(false) }
    var maintenanceHtml by remember { mutableStateOf("") }
    var useCustomHtml by remember { mutableStateOf(false) }
    var showHtmlPreview by remember { mutableStateOf(false) }
    var webSearchOn by remember { mutableStateOf(true) }
    var feedbackEmail by remember { mutableStateOf(RemoteConfigManager.feedbackEmail) }
    val ads = remember { AdsAdminState() }
    var settingsMsg by remember { mutableStateOf<String?>(null) }
    var chatPrompt by remember { mutableStateOf("") }
    var agentPrompt by remember { mutableStateOf("") }
    var promptsMsg by remember { mutableStateOf<String?>(null) }

    // Access
    var adminEmails by remember { mutableStateOf<List<String>>(emptyList()) }
    var newAdmin by remember { mutableStateOf("") }
    var adminMsg by remember { mutableStateOf<String?>(null) }
    var adminsLoading by remember { mutableStateOf(false) }
    var auditLogs by remember { mutableStateOf<List<AuditEntry>>(emptyList()) }
    var auditLoading by remember { mutableStateOf(false) }

    // Analytics
    var analyticsRange by remember { mutableStateOf("7") }
    var analyticsData by remember { mutableStateOf<List<DailyStats>>(emptyList()) }
    var analyticsLoading by remember { mutableStateOf(false) }
    var totalUsersCount by remember { mutableIntStateOf(0) }
    var totalCallsCount by remember { mutableIntStateOf(0) }
    var totalTokensCount by remember { mutableIntStateOf(0) }

    fun writeAudit(action: String, detail: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("audit").collection("logs")
                        .add(
                            mapOf(
                                "ts" to System.currentTimeMillis(),
                                "action" to action,
                                "detail" to detail,
                                "by" to AuthManager.email().ifBlank { "unknown" }
                            )
                        ).await()
                } catch (_: Exception) {
                }
            }
        }
    }


    fun loadDashboard() {
        dashLoading = true
        scope.launch {
            val u = withContext(Dispatchers.IO) { UsageTracker.fetchAllUsage(context) }
            totalUsers = u.size
            callsToday = u.sumOf { it.second.total }
            tokensToday = u.sumOf { it.second.totalTokens }
            recentUsers = u.map { (e, ud) ->
                AdminUser(uid = e, name = e.substringBefore('@'), email = e, plan = "free", banned = false, usageToday = ud)
            }.sortedByDescending { it.usageToday.total }.take(10)
            dashLoading = false
        }
    }

    fun loadUsers() {
        usersLoading = true
        scope.launch {
            val list = mutableListOf<AdminUser>()
            try {
                val snap = withContext(Dispatchers.IO) {
                    FirebaseFirestore.getInstance().collection("users").get().await()
                }
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                for (doc in snap.documents) {
                    val uid = doc.id
                    val name = doc.getString("name") ?: ""
                    val email = doc.getString("email") ?: uid
                    val plan = doc.getString("plan") ?: "free"
                    val banned = doc.getBoolean("banned") ?: false
                    val ud = withContext(Dispatchers.IO) {
                        FirebaseFirestore.getInstance().collection("users").document(uid)
                            .collection("usage").document(today).get().await()
                    }
                    val usage = if (ud.exists()) UsageTracker.DailyUsage(
                        date = today,
                        chat = (ud.getLong("chat") ?: 0).toInt(),
                        agent = (ud.getLong("agent") ?: 0).toInt(),
                        search = (ud.getLong("search") ?: 0).toInt(),
                        image = (ud.getLong("image") ?: 0).toInt(),
                        totalTokens = (ud.getLong("tokens") ?: 0).toInt()
                    ) else UsageTracker.DailyUsage(date = "")
                    list.add(AdminUser(uid, name, email, plan, banned, usage))
                }
            } catch (_: Exception) {
            }
            allUsers = list.sortedByDescending { it.usageToday.total }
            usersLoading = false
        }
    }

    fun loadProviders() {
        provLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val doc = FirebaseFirestore.getInstance().collection("config").document("providers").get().await()
                    val json = doc.getString("json")
                    if (!json.isNullOrBlank()) {
                        val (l, d, v) = parseProviders(json)
                        providers = l; defaults = d; jsonVersion = v
                        providersRawJson = try { JSONObject(json) } catch (_: Exception) { null }
                        comboList = com.ahamai.app.data.ComboRouter.all()
                        combosEnabled = com.ahamai.app.data.ComboRouter.isEnabled()
                        proxyCfg = com.ahamai.app.data.ProxyManager.config()
                    }
                    @Suppress("UNCHECKED_CAST")
                    fun listField(name: String): List<String> =
                        (doc.get(name) as? List<*>)?.filterIsInstance<String>()?.map { it.trim() }?.filter { it.isNotBlank() }
                            ?: emptyList()
                    braveKeys = listField("braveKeys")
                    deepgramKeys = listField("deepgramKeys")
                    assemblyAiKeys = listField("assemblyAiKeys")
                    val e2b = listField("e2bKeys")
                    val dt = listField("daytonaKeys")
                    daytonaKeys = if (e2b.isNotEmpty()) e2b else dt
                } catch (_: Exception) {
                }
            }
            provLoading = false
        }
    }

    fun saveProviders(list: List<ProviderEntry>) {
        provSaveMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val json = buildProvidersJson(list, defaults, jsonVersion, providersRawJson)
                    FirebaseFirestore.getInstance().collection("config").document("providers")
                        .set(mapOf("json" to json), SetOptions.merge()).await()
                    ApiConfig.update(json)
                    ApiConfig.clearAllBadKeys()
                    providersRawJson = try { JSONObject(json) } catch (_: Exception) { null }
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            provSaveMsg = r
            if (r == "OK") {
                providers = list
                writeAudit("providers_saved", "${list.size} providers")
            }
        }
    }

    fun persistFullConfig(list: List<ProviderEntry> = providers): String {
        return try {
            val json = buildProvidersJson(list, defaults, jsonVersion, providersRawJson)
            FirebaseFirestore.getInstance().collection("config").document("providers")
                .set(mapOf("json" to json), SetOptions.merge())
            // firestore set is Task - use runBlocking style via await outside
            json
        } catch (e: Exception) {
            throw e
        }
    }

    fun saveCombos(enabled: Boolean, items: List<com.ahamai.app.data.ComboRouter.Combo>) {
        comboSaveMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    com.ahamai.app.data.ComboRouter.replaceAll(enabled, items)
                    val json = buildProvidersJson(providers, defaults, jsonVersion, providersRawJson)
                    FirebaseFirestore.getInstance().collection("config").document("providers")
                        .set(mapOf("json" to json), SetOptions.merge()).await()
                    ApiConfig.update(json)
                    providersRawJson = try { JSONObject(json) } catch (_: Exception) { null }
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            comboSaveMsg = r
            if (r == "OK") {
                comboList = items
                combosEnabled = enabled
                writeAudit("combos_saved", "${items.size} combos")
            }
        }
    }

    fun saveProxy(cfg: com.ahamai.app.data.ProxyManager.Config) {
        proxySaveMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    com.ahamai.app.data.ProxyManager.replaceConfig(cfg)
                    val json = buildProvidersJson(providers, defaults, jsonVersion, providersRawJson)
                    FirebaseFirestore.getInstance().collection("config").document("providers")
                        .set(mapOf("json" to json), SetOptions.merge()).await()
                    ApiConfig.update(json)
                    providersRawJson = try { JSONObject(json) } catch (_: Exception) { null }
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            proxySaveMsg = r
            if (r == "OK") {
                proxyCfg = com.ahamai.app.data.ProxyManager.config()
                writeAudit("proxy_saved", "enabled=${cfg.enabled} active=${cfg.activeId}")
            }
        }
    }

    fun removeBadKeys() {
        if (badKeys.isEmpty()) return
        val removed = badKeys.size
        val cleaned = providers.map { p ->
            val keep = p.keys.filterNot { badKeys.contains(p.id to it) }
            if (keep.size != p.keys.size) p.copy(keys = keep) else p
        }
        saveProviders(cleaned)
        badKeys = emptySet()
        testLogs = testLogs + "Removed $removed failed key${if (removed != 1) "s" else ""}."
        writeAudit("bad_keys_removed", "$removed keys")
    }

    fun saveBraveKeys(keys: List<String>) {
        braveSaveMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("providers")
                        .set(mapOf("braveKeys" to keys), SetOptions.merge()).await()
                    RemoteConfigManager.braveApiKeys = keys
                    WebTools.clearInvalidKeys()
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            braveSaveMsg = r
            if (r == "OK") {
                braveKeys = keys; writeAudit("brave_keys", "${keys.size} keys")
            }
        }
    }

    fun saveDeepgramKeys(keys: List<String>) {
        deepgramSaveMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("providers")
                        .set(mapOf("deepgramKeys" to keys), SetOptions.merge()).await()
                    RemoteConfigManager.deepgramApiKeys = keys
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            deepgramSaveMsg = r
            if (r == "OK") {
                deepgramKeys = keys; writeAudit("deepgram_keys", "${keys.size} keys")
            }
        }
    }

    fun saveAssemblyAiKeys(keys: List<String>) {
        assemblyAiSaveMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("providers")
                        .set(mapOf("assemblyAiKeys" to keys), SetOptions.merge()).await()
                    RemoteConfigManager.assemblyAiApiKeys = keys
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            assemblyAiSaveMsg = r
            if (r == "OK") {
                assemblyAiKeys = keys; writeAudit("assemblyai_keys", "${keys.size} keys")
            }
        }
    }

    fun saveDaytonaKeys(keys: List<String>) {
        daytonaSaveMsg = null
        scope.launch {
            val cleaned = keys.map { it.trim() }.filter { it.isNotBlank() }
            val r = withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("providers")
                        .set(mapOf("e2bKeys" to cleaned, "daytonaKeys" to cleaned), SetOptions.merge()).await()
                    RemoteConfigManager.daytonaApiKeys = cleaned
                    RemoteConfigManager.clearDaytonaBadKeys()
                    PreferencesManager.clearStickyAdminDaytonaKey()
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            daytonaSaveMsg = r
            if (r == "OK") {
                daytonaKeys = cleaned; writeAudit("e2b_keys", "${cleaned.size} E2B keys")
            }
        }
    }

    fun loadSkillsConfig() {
        skillLoading = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val doc = FirebaseFirestore.getInstance().collection("config").document("skills").get().await()
                    if (doc.exists()) {
                        SkillManager.applyAdminConfigFromFirestore(doc.data ?: emptyMap())
                    } else {
                        SkillManager.applyAdminConfig(emptyList(), null)
                    }
                } catch (_: Exception) {
                }
            }
            skillTick = SkillManager.adminConfigVersion
            skillLoading = false
        }
    }

    /**
     * Persist full admin skills policy:
     * disabled toggles, soft-deletes, content overrides, SVG icons, admin-authored skills.
     */
    fun saveSkillsConfig(
        disabled: Set<String> = SkillManager.adminDisabledSnapshot(),
        deleted: Set<String> = SkillManager.adminDeletedSnapshot(),
        overrides: Map<String, SkillManager.AdminSkillEdit> = SkillManager.adminOverridesSnapshot(),
        authored: List<SkillManager.AdminSkillEdit> = SkillManager.adminAuthoredSnapshot(),
        icons: Map<String, String> = SkillManager.adminIconsSnapshot()
    ) {
        skillSaveMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val overridesMap = overrides.mapValues { (_, v) ->
                        mapOf(
                            "name" to v.name,
                            "description" to v.description,
                            "content" to v.content,
                            "iconSvg" to v.iconSvg,
                            "enabled" to v.enabled
                        )
                    }
                    val adminSkillsList = authored.map { a ->
                        mapOf(
                            "id" to a.id,
                            "name" to a.name,
                            "description" to a.description,
                            "content" to a.content,
                            "iconSvg" to a.iconSvg,
                            "enabled" to a.enabled
                        )
                    }
                    FirebaseFirestore.getInstance().collection("config").document("skills")
                        .set(
                            mapOf(
                                "disabled" to disabled.toList().sorted(),
                                "deleted" to deleted.toList().sorted(),
                                "whitelist" to emptyList<String>(),
                                "overrides" to overridesMap,
                                "adminSkills" to adminSkillsList,
                                "icons" to icons,
                                "updatedAt" to System.currentTimeMillis(),
                                "updatedBy" to AuthManager.email().ifBlank { "unknown" }
                            )
                        ).await()
                    SkillManager.applyAdminConfig(
                        disabled = disabled.toList(),
                        whitelist = null,
                        deleted = deleted.toList(),
                        overrides = overrides,
                        authored = authored,
                        icons = icons
                    )
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            skillSaveMsg = r
            skillTick = SkillManager.adminConfigVersion
            if (r == "OK") writeAudit("skills_updated", "disabled=${disabled.size} deleted=${deleted.size}")
        }
    }

    fun loadSettings() {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val db = FirebaseFirestore.getInstance()
                    val s = db.collection("config").document("settings").get().await()
                    if (s.exists()) {
                        maintenance = s.getString("maintenance_text") ?: ""
                        maintenanceOn = s.getBoolean("maintenance_enabled") ?: false
                        webSearchOn = s.getBoolean("web_search_enabled") ?: true
                        feedbackEmail = s.getString("feedback_email")?.trim().orEmpty()
                            .ifBlank { RemoteConfigManager.feedbackEmail }
                        maintenanceHtml = s.getString("maintenance_html") ?: ""
                        useCustomHtml = s.getBoolean("use_custom_html") ?: false
                        // Ads — robust bool/number parse (same helpers as live listener)
                        ads.enabled            = RemoteConfigManager.firestoreBool(s, "ads_enabled", true)
                        ads.freeOnly           = RemoteConfigManager.firestoreBool(s, "ads_free_only", true)
                        ads.chatOn             = RemoteConfigManager.firestoreBool(s, "chat_ads_enabled", true)
                        ads.chatInterval       = when (val v = s.get("chat_ad_interval")) {
                            is Number -> v.toInt().coerceAtLeast(1).toString()
                            is String -> (v.toIntOrNull() ?: 1).coerceAtLeast(1).toString()
                            else -> "1"
                        }
                        ads.agentOn            = RemoteConfigManager.firestoreBool(s, "agent_ads_enabled", true)
                        ads.buildOn            = RemoteConfigManager.firestoreBool(s, "agent_build_ad", true)
                        ads.completionOn       = RemoteConfigManager.firestoreBool(s, "agent_completion_ad", true)
                        ads.completionInterval = when (val v = s.get("agent_completion_interval")) {
                            is Number -> v.toInt().coerceAtLeast(1).toString()
                            is String -> (v.toIntOrNull() ?: 1).coerceAtLeast(1).toString()
                            else -> "1"
                        }
                        ads.overLimitOn        = RemoteConfigManager.firestoreBool(s, "agent_overlimit_ad", true)
                        ads.minGap             = when (val v = s.get("ad_min_gap_seconds")) {
                            is Number -> v.toInt().coerceAtLeast(0).toString()
                            is String -> (v.toIntOrNull() ?: 60).coerceAtLeast(0).toString()
                            else -> "60"
                        }
                        // Clear invalid units so UI shows blank (= Google test ads)
                        ads.nativeUnit         = RemoteConfigManager.sanitizeAdUnit(s.getString("chat_native_unit"))
                        ads.rewardedUnit       = RemoteConfigManager.sanitizeAdUnit(s.getString("agent_rewarded_unit"))
                        ads.interstitialUnit   = RemoteConfigManager.sanitizeAdUnit(s.getString("agent_interstitial_unit"))
                    }
                    val p = db.collection("config").document("systemPrompts").get().await()
                    if (p.exists()) {
                        chatPrompt = p.getString("chat") ?: ""
                        agentPrompt = p.getString("agent") ?: ""
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun saveSettings() {
        settingsMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    val chatIv = ads.chatInterval.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val compIv = ads.completionInterval.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val gapSec = ads.minGap.toIntOrNull()?.coerceAtLeast(0) ?: 60
                    // Sanitize unit fields before write so invalid/placeholder IDs never stick.
                    val nativeUnit = RemoteConfigManager.sanitizeAdUnit(ads.nativeUnit)
                    val rewardedUnit = RemoteConfigManager.sanitizeAdUnit(ads.rewardedUnit)
                    val interstitialUnit = RemoteConfigManager.sanitizeAdUnit(ads.interstitialUnit)
                    val feedbackMail = feedbackEmail.trim()
                    FirebaseFirestore.getInstance().collection("config").document("settings").set(
                        hashMapOf(
                            "maintenance_text" to maintenance,
                            "maintenance_enabled" to maintenanceOn,
                            "web_search_enabled" to webSearchOn,
                            "feedback_email" to feedbackMail,
                            "maintenance_html" to maintenanceHtml,
                            "use_custom_html" to useCustomHtml,
                            // ── Ads ──
                            "ads_enabled" to ads.enabled,
                            "ads_free_only" to ads.freeOnly,
                            "chat_ads_enabled" to ads.chatOn,
                            "chat_ad_interval" to chatIv.toLong(),
                            "agent_ads_enabled" to ads.agentOn,
                            "agent_build_ad" to ads.buildOn,
                            "agent_completion_ad" to ads.completionOn,
                            "agent_completion_interval" to compIv.toLong(),
                            "agent_overlimit_ad" to ads.overLimitOn,
                            "ad_min_gap_seconds" to gapSec.toLong(),
                            "chat_native_unit" to nativeUnit,
                            "agent_rewarded_unit" to rewardedUnit,
                            "agent_interstitial_unit" to interstitialUnit
                        ),
                        SetOptions.merge()
                    ).await()
                    RemoteConfigManager.applyMaintenanceSettings(
                        enabled = maintenanceOn,
                        text = maintenance,
                        html = maintenanceHtml,
                        useHtml = useCustomHtml
                    )
                    RemoteConfigManager.webSearchEnabled = webSearchOn
                    RemoteConfigManager.feedbackEmail = feedbackMail
                    // Apply ads + bump version so ChatScreen re-gates immediately (off→on fix).
                    RemoteConfigManager.applyAdSettings(
                        enabled = ads.enabled,
                        freeOnly = ads.freeOnly,
                        chatOn = ads.chatOn,
                        chatInterval = chatIv,
                        agentOn = ads.agentOn,
                        buildOn = ads.buildOn,
                        completionOn = ads.completionOn,
                        completionInterval = compIv,
                        overLimitOn = ads.overLimitOn,
                        minGapSeconds = gapSec,
                        nativeUnit = nativeUnit,
                        rewardedUnit = rewardedUnit,
                        interstitialUnit = interstitialUnit,
                    )
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            settingsMsg = r
            if (r == "OK") writeAudit("settings", "maintenance=$maintenanceOn web=$webSearchOn html=$useCustomHtml")
        }
    }

    fun savePrompts() {
        promptsMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("systemPrompts")
                        .set(hashMapOf("chat" to chatPrompt, "agent" to agentPrompt)).await()
                    RemoteConfigManager.chatSystemPrompt = chatPrompt
                    RemoteConfigManager.agentSystemPrompt = agentPrompt
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            promptsMsg = r
            if (r == "OK") writeAudit("prompts", "updated")
        }
    }

    fun setUserPlan(uid: String, plan: String) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("users").document(uid).update("plan", plan).await()
                } catch (_: Exception) {
                }
            }
            allUsers = allUsers.map { if (it.uid == uid) it.copy(plan = plan) else it }
            writeAudit("user_plan", "$uid → $plan")
        }
    }

    fun toggleBan(uid: String, banned: Boolean) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("users").document(uid).update("banned", !banned).await()
                } catch (_: Exception) {
                }
            }
            allUsers = allUsers.map { if (it.uid == uid) it.copy(banned = !banned) else it }
            writeAudit("user_ban", "$uid → ${!banned}")
        }
    }

    fun loadAdmins() {
        adminsLoading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    val d = FirebaseFirestore.getInstance().collection("config").document("admins").get().await()
                    (d.get("emails") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }
            }
            adminEmails = list
            adminsLoading = false
        }
    }

    fun saveAdmins(list: List<String>) {
        adminMsg = null
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("admins")
                        .set(mapOf("emails" to list), SetOptions.merge()).await()
                    "OK"
                } catch (e: Exception) {
                    "ERROR: ${e.message}"
                }
            }
            adminMsg = r
            if (r == "OK") {
                adminEmails = list; writeAudit("admins_updated", "${list.size} admins")
            }
        }
    }

    fun loadAudit() {
        auditLoading = true
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                try {
                    FirebaseFirestore.getInstance().collection("config").document("audit").collection("logs")
                        .orderBy("ts", Query.Direction.DESCENDING).limit(60).get().await()
                        .documents.map {
                            AuditEntry(
                                it.getLong("ts") ?: 0L,
                                it.getString("action") ?: "",
                                it.getString("by") ?: "",
                                it.getString("detail") ?: ""
                            )
                        }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            auditLogs = list
            auditLoading = false
        }
    }

    fun loadAnalytics(days: Int) {
        analyticsLoading = true
        scope.launch {
            val data = withContext(Dispatchers.IO) { fetchAnalyticsData(days) }
            analyticsData = data
            totalUsersCount = data.sumOf { it.users }
            totalCallsCount = data.sumOf { it.calls }
            totalTokensCount = data.sumOf { it.tokens }
            analyticsLoading = false
        }
    }

    LaunchedEffect(tab) {
        when (tab) {
            AdminTab.Overview -> loadDashboard()
            AdminTab.Users -> loadUsers()
            AdminTab.Providers -> loadProviders()
            AdminTab.Combos -> loadProviders()
            AdminTab.Network -> loadProviders()
            AdminTab.Skills -> loadSkillsConfig()
            AdminTab.Access -> {
                loadAdmins(); loadAudit()
            }
            AdminTab.Analytics -> loadAnalytics(analyticsRange.toInt())
            AdminTab.Control -> {
                loadSettings()
                chatPrompt = RemoteConfigManager.chatSystemPrompt
                agentPrompt = RemoteConfigManager.agentSystemPrompt
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar (flush, no chrome boxes) ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Lucide.ArrowLeft, null,
                    tint = c.ink,
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onBack() }
                        .padding(10.dp)
                )
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    com.ahamai.app.ui.components.AhamaiLogo(
                        color = c.ink,
                        fontSize = 20.sp
                    )
                    Text(
                        "control centre",
                        fontFamily = UnicaOneRegular,
                        fontWeight = FontWeight.Normal,
                        fontSize = 13.sp,
                        letterSpacing = 0.5.sp,
                        color = c.muted
                    )
                }
                Text(
                    AuthManager.email().ifBlank { "admin" }.substringBefore('@').take(14),
                    fontSize = 11.sp,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Medium,
                    color = c.muted,
                    maxLines = 1
                )
            }

            // ── Tab chips ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AdminTab.entries.forEach { t ->
                    val selected = tab == t
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) c.chipOn else c.chipOff)
                            .clickable { tab = t }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            t.icon, null,
                            tint = if (selected) c.accent else c.muted,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            t.label,
                            fontSize = 13.sp,
                            fontFamily = InterFamily,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) c.accent else c.muted
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Content ──
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                when (tab) {
                    AdminTab.Overview -> OverviewPane(
                        c, dashLoading, totalUsers, callsToday, tokensToday, recentUsers
                    )
                    AdminTab.Users -> UsersPane(
                        c, usersLoading, searchQuery, { searchQuery = it }, allUsers,
                        onSetPlan = { u, p -> setUserPlan(u, p) },
                        onToggleBan = { u, b -> toggleBan(u, b) }
                    )
                    AdminTab.Providers -> ProvidersPane(
                        c = c,
                        loading = provLoading,
                        providers = providers,
                        provSaveMsg = provSaveMsg,
                        testingKeys = testingKeys,
                        testLogs = testLogs,
                        badKeys = badKeys,
                        braveKeys = braveKeys, braveSaveMsg = braveSaveMsg,
                        deepgramKeys = deepgramKeys, deepgramSaveMsg = deepgramSaveMsg,
                        assemblyAiKeys = assemblyAiKeys, assemblyAiSaveMsg = assemblyAiSaveMsg,
                        daytonaKeys = daytonaKeys, daytonaSaveMsg = daytonaSaveMsg,
                        onTestAll = {
                            testingKeys = true; testLogs = emptyList(); badKeys = emptySet()
                            scope.launch {
                                val enabledProviders = providers.filter { it.enabled }
                                testLogs = listOf("Testing ${enabledProviders.size} providers…")
                                val results = withContext(Dispatchers.IO) {
                                    enabledProviders.map { p ->
                                        val model = p.models.firstOrNull()?.id ?: ""
                                        p.keys.mapIndexed { ki, key ->
                                            async {
                                                val r = ApiClient.pingKey(p.baseUrl, key, model)
                                                Triple(p.name, ki, key) to r
                                            }
                                        }
                                    }.flatten().awaitAll()
                                }
                                val logs = mutableListOf<String>()
                                val bad = mutableSetOf<Pair<String, String>>()
                                enabledProviders.forEach { p ->
                                    logs.add("${p.name} (${p.keys.size}):")
                                    results.filter { it.first.first == p.name }.sortedBy { it.first.second }.forEach { (meta, res) ->
                                        val (_, ki, key) = meta
                                        val ok = res == "OK"
                                        if (!ok) bad.add(p.id to key)
                                        logs.add("  #${ki + 1} ${key.take(8)}…  ${if (ok) "✓" else "✗"} $res")
                                    }
                                }
                                val passed = results.count { it.second == "OK" }
                                logs.add("Done — $passed ok, ${results.size - passed} failed")
                                testLogs = logs; badKeys = bad; testingKeys = false
                            }
                        },
                        onRemoveBad = { removeBadKeys() },
                        onSaveProviders = { saveProviders(it) },
                        onAddProvider = { showAddProv = true },
                        onSaveBrave = { saveBraveKeys(it) },
                        onSaveDeepgram = { saveDeepgramKeys(it) },
                        onSaveAssembly = { saveAssemblyAiKeys(it) },
                        onSaveE2b = { saveDaytonaKeys(it) },
                    )
                    AdminTab.Combos -> CombosPane(
                        c = c,
                        loading = provLoading,
                        combosEnabled = combosEnabled,
                        combos = comboList,
                        providers = providers,
                        saveMsg = comboSaveMsg,
                        testing = comboTesting,
                        testLogs = comboTestLogs,
                        onToggleEnabled = { en -> saveCombos(en, comboList) },
                        onSave = { items -> saveCombos(combosEnabled, items) },
                        onTestCombo = { combo ->
                            comboTesting = true
                            comboTestLogs = listOf("Testing combo «${combo.id}» chain…")
                            scope.launch {
                                val logs = mutableListOf<String>()
                                withContext(Dispatchers.IO) {
                                    // Prefer saved members in order (full health check of YOUR chain)
                                    val members = if (combo.members.isNotEmpty()) combo.members
                                    else com.ahamai.app.data.ComboRouter.find(combo.id)?.members.orEmpty()
                                    if (members.isEmpty()) {
                                        logs += "✗ Chain empty — add provider + model members first"
                                    } else {
                                        logs += "Checking ${members.size} member(s) in order:"
                                        members.forEachIndexed { i, member ->
                                            val resolved = com.ahamai.app.data.ComboRouter.resolveMember(member)
                                            if (resolved == null) {
                                                logs += "✗ #${i + 1} ${member.display()} → provider offline / no key"
                                            } else {
                                                val r = ApiClient.pingKey(resolved.baseUrl, resolved.apiKey, member.model)
                                                val ok = r == "OK"
                                                logs += "${if (ok) "✓" else "✗"} #${i + 1} ${member.providerId} → ${member.model} · $r"
                                            }
                                        }
                                    }
                                }
                                comboTestLogs = logs + "Done."
                                comboTesting = false
                            }
                        }
                    )
                    AdminTab.Network -> NetworkPane(
                        c = c,
                        cfg = proxyCfg,
                        saveMsg = proxySaveMsg,
                        testMsg = proxyTestMsg,
                        testing = proxyTesting,
                        onSave = { saveProxy(it) },
                        onTest = { entry ->
                            proxyTesting = true
                            proxyTestMsg = "Testing…"
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    com.ahamai.app.data.ProxyManager.testProxy(entry)
                                }
                                proxyTestMsg = r
                                proxyTesting = false
                            }
                        }
                    )
                    AdminTab.Skills -> SkillsAdminPane(
                        c = c,
                        loading = skillLoading,
                        tick = skillTick,
                        search = skillSearch,
                        onSearch = { skillSearch = it },
                        saveMsg = skillSaveMsg,
                        onToggle = { id, enabled ->
                            val disabled = SkillManager.adminDisabledSnapshot().toMutableSet()
                            if (enabled) disabled.remove(id) else disabled.add(id)
                            saveSkillsConfig(disabled = disabled)
                        },
                        onEnableAll = {
                            saveSkillsConfig(disabled = emptySet())
                        },
                        onDisableRisky = {
                            saveSkillsConfig(disabled = setOf("sql"))
                        },
                        onSaveEdit = { edit ->
                            val overrides = SkillManager.adminOverridesSnapshot().toMutableMap()
                            val authored = SkillManager.adminAuthoredSnapshot().toMutableList()
                            val icons = SkillManager.adminIconsSnapshot().toMutableMap()
                            if (edit.iconSvg.isNotBlank()) icons[edit.id] = edit.iconSvg
                            else icons.remove(edit.id)
                            if (edit.isAdminAuthored) {
                                val i = authored.indexOfFirst { it.id == edit.id }
                                if (i >= 0) authored[i] = edit else authored.add(edit)
                            } else {
                                overrides[edit.id] = edit
                            }
                            saveSkillsConfig(overrides = overrides, authored = authored, icons = icons)
                        },
                        onDelete = { id ->
                            val deleted = SkillManager.adminDeletedSnapshot().toMutableSet()
                            deleted.add(id)
                            val overrides = SkillManager.adminOverridesSnapshot().toMutableMap().apply { remove(id) }
                            val authored = SkillManager.adminAuthoredSnapshot().filter { it.id != id }
                            val icons = SkillManager.adminIconsSnapshot().toMutableMap().apply { remove(id) }
                            val disabled = SkillManager.adminDisabledSnapshot().toMutableSet().apply { add(id) }
                            saveSkillsConfig(
                                disabled = disabled,
                                deleted = deleted,
                                overrides = overrides,
                                authored = authored,
                                icons = icons
                            )
                        },
                        onAddSkill = { edit ->
                            val authored = SkillManager.adminAuthoredSnapshot().toMutableList()
                            val icons = SkillManager.adminIconsSnapshot().toMutableMap()
                            authored.add(0, edit)
                            if (edit.iconSvg.isNotBlank()) icons[edit.id] = edit.iconSvg
                            saveSkillsConfig(authored = authored, icons = icons)
                        }
                    )
                    AdminTab.Analytics -> AnalyticsPane(
                        c, analyticsLoading, analyticsRange, {
                            analyticsRange = it; loadAnalytics(it.toInt())
                        }, analyticsData, totalUsersCount, totalCallsCount, totalTokensCount, context
                    )
                    AdminTab.Control -> ControlPane(
                        c = c,
                        ads = ads,
                        maintenanceOn = maintenanceOn, onMaint = { maintenanceOn = it },
                        webSearchOn = webSearchOn, onWeb = { webSearchOn = it },
                        feedbackEmail = feedbackEmail, onFeedbackEmail = { feedbackEmail = it },
                        useCustomHtml = useCustomHtml, onHtmlMode = { useCustomHtml = it },
                        maintenance = maintenance, onMaintText = { maintenance = it },
                        maintenanceHtml = maintenanceHtml, onHtml = { maintenanceHtml = it },
                        chatPrompt = chatPrompt, onChat = { chatPrompt = it },
                        agentPrompt = agentPrompt, onAgent = { agentPrompt = it },
                        settingsMsg = settingsMsg, promptsMsg = promptsMsg,
                        onSaveSettings = { saveSettings() },
                        onSavePrompts = { savePrompts() },
                        onPreview = { showHtmlPreview = true }
                    )
                    AdminTab.Access -> AccessPane(
                        c = c,
                        adminsLoading = adminsLoading,
                        adminEmails = adminEmails,
                        newAdmin = newAdmin,
                        onNewAdmin = { newAdmin = it },
                        adminMsg = adminMsg,
                        onAddAdmin = {
                            val e = newAdmin.trim().lowercase()
                            if (e.contains("@") && e !in adminEmails) {
                                saveAdmins(adminEmails + e); newAdmin = ""
                            }
                        },
                        onRemoveAdmin = { em -> saveAdmins(adminEmails.filter { it != em }) },
                        auditLoading = auditLoading,
                        auditLogs = auditLogs,
                        onReloadAudit = { loadAudit() }
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }

    if (showAddProv) {
        AddProviderDialog(c, onDismiss = { showAddProv = false }) {
            showAddProv = false
            saveProviders(providers + it)
        }
    }


    if (showHtmlPreview) {
        Dialog(onDismissRequest = { showHtmlPreview = false }) {
            Surface(shape = RoundedCornerShape(14.dp), color = c.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Preview", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink, modifier = Modifier.weight(1f))
                        Text("Close", fontSize = 13.sp, color = c.accent, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, modifier = Modifier.clickable { showHtmlPreview = false })
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                webViewClient = WebViewClient()
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            val html = if (maintenanceHtml.isNotBlank()) maintenanceHtml
                            else "<!DOCTYPE html><html><body style='font-family:system-ui;text-align:center;padding:40px;background:#0b0b0d;color:#f3f3f5'><h1>Maintenance</h1><p>Preview your custom HTML here</p></body></html>"
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    )
                }
            }
        }
    }
}

// ── Panes ────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String, subtitle: String? = null, c: AdminColors) {
    Text(
        title.lowercase(),
        fontFamily = UnicaOneRegular,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        color = c.ink,
        letterSpacing = 0.5.sp
    )
    if (subtitle != null) {
        Spacer(Modifier.height(3.dp))
        Text(subtitle, fontFamily = InterFamily, fontSize = 12.sp, color = c.muted, lineHeight = 16.sp)
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun AdminCard(c: AdminColors, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.line.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) { content() }
}

@Composable
private fun LoadingDots(color: Color) {
    val t = rememberInfiniteTransition(label = "pulse")
    val a1 by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "d1")
    val a2 by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 200), RepeatMode.Reverse), label = "d2")
    val a3 by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600, delayMillis = 400), RepeatMode.Reverse), label = "d3")
    Box(Modifier.fillMaxWidth().padding(40.dp), Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(a1, a2, a3).forEach { a ->
                Box(Modifier.size(7.dp).clip(CircleShape).background(color.copy(alpha = a)))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, icon: ImageVector, c: AdminColors, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface2)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Icon(icon, null, tint = c.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.height(8.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, fontFamily = InterFamily, color = c.muted)
    }
}

@Composable
private fun OverviewPane(
    c: AdminColors, loading: Boolean, users: Int, calls: Int, tokens: Int, recent: List<AdminUser>
) {
    SectionTitle("Overview", "Live pulse of usage today", c)
    if (loading) LoadingDots(c.muted)
    else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Users", "$users", Lucide.Users, c, Modifier.weight(1f))
            StatTile("Calls", "$calls", Lucide.Activity, c, Modifier.weight(1f))
            StatTile("Tokens", fmtTok(tokens), Lucide.Zap, c, Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        AdminCard(c) {
            Text("TOP ACTIVITY", fontFamily = OswaldFamily, fontSize = 12.sp, letterSpacing = 1.2.sp, color = c.muted, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(12.dp))
            if (recent.isEmpty()) {
                Text("No activity recorded today.", fontSize = 13.sp, color = c.muted, fontFamily = InterFamily)
            } else {
                recent.forEachIndexed { idx, u ->
                    if (idx > 0) HorizontalDivider(color = c.line, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.surface2),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (u.email.firstOrNull() ?: '?').uppercaseChar().toString(),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.email.substringBefore('@').take(18), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = c.ink, fontFamily = InterFamily, maxLines = 1)
                            Text("${u.usageToday.total} calls · ${fmtTok(u.usageToday.totalTokens)} tokens", fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersPane(
    c: AdminColors, loading: Boolean, query: String, onQuery: (String) -> Unit,
    users: List<AdminUser>, onSetPlan: (String, String) -> Unit, onToggleBan: (String, Boolean) -> Unit
) {
    SectionTitle("Users", "Search, plans, and bans", c)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.Search, null, tint = c.muted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search name or email…", fontSize = 14.sp, color = c.muted.copy(0.7f), fontFamily = InterFamily)
            BasicTextField(
                value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 14.sp, color = c.ink, fontFamily = InterFamily),
                cursorBrush = SolidColor(c.accent), singleLine = true
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    if (loading) LoadingDots(c.muted)
    else {
        val filtered = users.filter {
            query.isBlank() || it.email.contains(query, true) || it.name.contains(query, true)
        }
        if (filtered.isEmpty()) {
            Text(if (query.isBlank()) "No users yet." else "No match.", fontSize = 13.sp, color = c.muted, fontFamily = InterFamily)
        } else {
            filtered.forEach { u ->
                UserCard(c, u, onSetPlan = { onSetPlan(u.uid, it) }, onToggleBan = { onToggleBan(u.uid, u.banned) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun UserCard(c: AdminColors, user: AdminUser, onSetPlan: (String) -> Unit, onToggleBan: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    AdminCard(c, Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.surface2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (user.name.firstOrNull() ?: user.email.firstOrNull() ?: '?').uppercaseChar().toString(),
                    fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name.ifBlank { user.email.substringBefore('@') }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(user.email, fontSize = 12.sp, color = c.muted, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (user.banned) {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.danger.copy(0.15f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text("BANNED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.danger, fontFamily = InterFamily)
                }
            } else {
                Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.surface2).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(user.plan.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.muted, fontFamily = InterFamily)
                }
            }
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Today · ${user.usageToday.total} calls · ${fmtTok(user.usageToday.totalTokens)} tokens",
                    fontSize = 12.sp, color = c.muted, fontFamily = InterFamily
                )
                Spacer(Modifier.height(10.dp))
                Text("PLAN", fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted, fontFamily = OswaldFamily)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("free", "pro", "plus", "enterprise").forEach { p ->
                        val on = user.plan.equals(p, true)
                        Text(
                            p.replaceFirstChar { it.uppercase() },
                            fontSize = 12.sp,
                            fontFamily = InterFamily,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) c.accentInk else c.muted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) c.chipOn else c.surface2)
                                .clickable { onSetPlan(p) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (user.banned) c.ok.copy(0.12f) else c.danger.copy(0.12f))
                        .clickable { onToggleBan() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (user.banned) Lucide.Check else Lucide.Shield,
                        null,
                        tint = if (user.banned) c.ok else c.danger,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (user.banned) "Unban user" else "Ban user",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (user.banned) c.ok else c.danger,
                        fontFamily = InterFamily
                    )
                }
            }
        }
    }
}

@Composable
private fun ProvidersPane(
    c: AdminColors,
    loading: Boolean,
    providers: List<ProviderEntry>,
    provSaveMsg: String?,
    testingKeys: Boolean,
    testLogs: List<String>,
    badKeys: Set<Pair<String, String>>,
    braveKeys: List<String>, braveSaveMsg: String?,
    deepgramKeys: List<String>, deepgramSaveMsg: String?,
    assemblyAiKeys: List<String>, assemblyAiSaveMsg: String?,
    daytonaKeys: List<String>, daytonaSaveMsg: String?,
    onTestAll: () -> Unit,
    onRemoveBad: () -> Unit,
    onSaveProviders: (List<ProviderEntry>) -> Unit,
    onAddProvider: () -> Unit,
    onSaveBrave: (List<String>) -> Unit,
    onSaveDeepgram: (List<String>) -> Unit,
    onSaveAssembly: (List<String>) -> Unit,
    onSaveE2b: (List<String>) -> Unit,
) {
    SectionTitle("api", "Endpoints · service keys · health checks", c)
    if (loading) LoadingDots(c.muted)
    else {
        // ── Health ──
        AdminCard(c) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Key health", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
                    Text("Ping all enabled provider keys", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
                }
                SmallBtn(c, if (testingKeys) "Testing…" else "Test all", accent = !testingKeys, enabled = !testingKeys, onClick = onTestAll)
            }
            if (testLogs.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface2)
                        .heightIn(max = 140.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    testLogs.forEach { log ->
                        Text(
                            log, fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily, lineHeight = 15.sp,
                            color = when {
                                log.contains("✓") -> c.ok
                                log.contains("✗") -> c.danger
                                log.startsWith("Done") -> c.ink
                                else -> c.muted
                            }
                        )
                    }
                }
            }
            if (badKeys.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                SmallBtn(c, "Remove ${badKeys.size} bad key${if (badKeys.size != 1) "s" else ""}", accent = false) { onRemoveBad() }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("SERVICE KEYS", fontFamily = OswaldFamily, fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        KeyEditorCard(c, "E2B Cloud", "Sandbox · browser", "e2b.dev", daytonaKeys, daytonaSaveMsg, onSaveE2b)
        Spacer(Modifier.height(5.dp))
        KeyEditorCard(c, "Brave Search", "Web search", "brave.com", braveKeys, braveSaveMsg, onSaveBrave)
        Spacer(Modifier.height(5.dp))
        KeyEditorCard(c, "Deepgram", "Voice agent", "deepgram.com", deepgramKeys, deepgramSaveMsg, onSaveDeepgram)
        Spacer(Modifier.height(5.dp))
        KeyEditorCard(c, "AssemblyAI", "Speech-to-text", "assemblyai.com", assemblyAiKeys, assemblyAiSaveMsg, onSaveAssembly)

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CHAT PROVIDERS",
                fontFamily = OswaldFamily, fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted,
                fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
            )
            SmallBtn(c, "+ Add", accent = true, onClick = onAddProvider)
        }
        provSaveMsg?.let {
            Spacer(Modifier.height(4.dp))
            Text(if (it == "OK") "Saved" else it, fontSize = 11.sp, color = if (it == "OK") c.ok else c.danger, fontFamily = InterFamily)
        }
        Spacer(Modifier.height(6.dp))
        if (providers.isEmpty()) {
            AdminCard(c) {
                Text("No providers yet", fontSize = 13.sp, color = c.ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Add an OpenAI-compatible endpoint + keys + models.", fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily)
            }
        } else {
            providers.forEachIndexed { idx, p ->
                ProviderCard(
                    c = c, provider = p,
                    onToggle = {
                        val l = providers.toMutableList()
                        l[idx] = p.copy(enabled = !p.enabled)
                        onSaveProviders(l)
                    },
                    onUpdate = { u ->
                        val l = providers.toMutableList()
                        l[idx] = u
                        onSaveProviders(l)
                    },
                    onDelete = { onSaveProviders(providers.filterIndexed { i, _ -> i != idx }) }
                )
                Spacer(Modifier.height(5.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun KeyEditorCard(
    c: AdminColors,
    title: String,
    subtitle: String,
    faviconDomain: String,
    keys: List<String>,
    saveMsg: String?,
    onSave: (List<String>) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var edit by remember(keys) { mutableStateOf(keys.joinToString("\n")) }
    AdminCard(c) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.surface2),
                contentAlignment = Alignment.Center
            ) {
                // Real site favicon (same source as chat providers)
                SiteFavicon("https://$faviconDomain", 18)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
                Text(
                    if (keys.isEmpty()) subtitle else "${keys.size} key${if (keys.size != 1) "s" else ""}",
                    fontSize = 11.sp, color = c.muted, fontFamily = InterFamily
                )
            }
            saveMsg?.let {
                Text(if (it == "OK") "✓" else "!", fontSize = 12.sp, color = if (it == "OK") c.ok else c.danger)
                Spacer(Modifier.width(6.dp))
            }
            Icon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, null, tint = c.muted, modifier = Modifier.size(15.dp))
        }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                Field(
                    c,
                    "API keys (one per line)",
                    edit,
                    minLines = 3,
                    placeholder = "Paste keys, one per line…"
                ) { edit = it }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SmallBtn(c, "Save keys", accent = true) {
                        onSave(edit.split('\n').map { it.trim() }.filter { it.isNotBlank() })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderCard(
    c: AdminColors, provider: ProviderEntry,
    onToggle: () -> Unit, onUpdate: (ProviderEntry) -> Unit, onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var editName by remember(provider) { mutableStateOf(provider.name) }
    var editUrl by remember(provider) { mutableStateOf(provider.baseUrl) }
    var editKeys by remember(provider) { mutableStateOf(provider.keys.joinToString("\n")) }
    var editModels by remember(provider) { mutableStateOf(provider.models.toMutableList()) }
    var newModelId by remember { mutableStateOf("") }
    var editFetch by remember(provider) { mutableStateOf(provider.fetchModels) }
    var confirmDel by remember { mutableStateOf(false) }
    val fetchScope = rememberCoroutineScope()
    var fetching by remember { mutableStateOf(false) }
    var fetchErr by remember { mutableStateOf<String?>(null) }
    var fetchedList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showModelSheet by remember { mutableStateOf(false) }
    var selectedModels by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun doFetchModels() {
        val key = editKeys.split('\n').map { it.trim() }.firstOrNull { it.isNotBlank() } ?: ""
        if (editUrl.isBlank()) { fetchErr = "Set Base URL first"; return }
        fetching = true; fetchErr = null
        fetchScope.launch {
            val res = withContext(Dispatchers.IO) { ApiClient.fetchModels(editUrl.trim(), key) }
            fetching = false
            res.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) fetchErr = "Provider returned no models"
                    else {
                        fetchedList = list
                        selectedModels = editModels.map { it.id }.toSet()
                        showModelSheet = true
                    }
                },
                onFailure = { fetchErr = "Fetch failed: ${it.message?.take(60)}" }
            )
        }
    }

    AdminCard(c) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
            SiteFavicon(provider.baseUrl, 18)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    provider.name.ifBlank { provider.id },
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val hostHint = provider.baseUrl
                    .removePrefix("https://")
                    .removePrefix("http://")
                    .take(28)
                Text(
                    "${provider.models.size} models · ${provider.keys.size} keys · $hostHint",
                    fontSize = 10.5.sp, color = c.muted, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TickToggle(c, provider.enabled) { onToggle() }
            Spacer(Modifier.width(4.dp))
            Icon(if (expanded) Lucide.ChevronUp else Lucide.ChevronDown, null, tint = c.muted, modifier = Modifier.size(15.dp))
        }
        AnimatedVisibility(expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Spacer(Modifier.height(10.dp))
                Field(c, "Name", editName, placeholder = "Provider name") { editName = it }
                Spacer(Modifier.height(8.dp))
                Field(c, "Base URL", editUrl, placeholder = "https://api.example.com/v1") { editUrl = it }
                Spacer(Modifier.height(8.dp))
                Field(c, "API keys (one per line)", editKeys, minLines = 2, placeholder = "sk-…") { editKeys = it }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Models", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    SmallBtn(c, if (fetching) "…" else "Fetch", enabled = !fetching) { doFetchModels() }
                }
                Spacer(Modifier.height(6.dp))
                if (editModels.isEmpty()) {
                    Text("No models — fetch or add manually", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
                }
                editModels.forEachIndexed { idx, m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.surface2)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.id, fontSize = 11.5.sp, color = c.ink, fontFamily = JetBrainsMonoFamily, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Chat", fontSize = 10.sp, color = c.muted, fontFamily = InterFamily)
                            Spacer(Modifier.width(4.dp))
                            TickToggle(c, m.enabledChat) {
                                val l = editModels.toMutableList()
                                l[idx] = m.copy(enabledChat = !m.enabledChat)
                                editModels = l
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Agent", fontSize = 10.sp, color = c.muted, fontFamily = InterFamily)
                            Spacer(Modifier.width(4.dp))
                            TickToggle(c, m.enabledAgent) {
                                val l = editModels.toMutableList()
                                l[idx] = m.copy(enabledAgent = !m.enabledAgent)
                                editModels = l
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("✕", color = c.danger, fontSize = 12.sp, modifier = Modifier.clickable {
                                editModels = editModels.filterIndexed { i, _ -> i != idx }.toMutableList()
                            }.padding(4.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        PlanChipsRow(c, m.plans) { next ->
                            val l = editModels.toMutableList()
                            l[idx] = m.copy(plans = next)
                            editModels = l
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Field(c, "Add model id", newModelId, placeholder = "gpt-4o / ocg/…") { newModelId = it }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallBtn(c, "+ Add model") {
                        if (newModelId.isNotBlank()) {
                            editModels = (editModels + ModelConfig(newModelId.trim(), true, false)).toMutableList()
                            newModelId = ""
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow(c, "Auto-fetch /models", "Refresh list when picker opens", editFetch) { editFetch = it }
                fetchErr?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = c.danger, fontFamily = InterFamily)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    if (!confirmDel) {
                        Text("Delete", fontSize = 12.sp, color = c.danger, fontFamily = InterFamily, modifier = Modifier.clickable { confirmDel = true })
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Sure?", fontSize = 12.sp, color = c.danger, fontFamily = InterFamily)
                            Text("No", fontSize = 12.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.clickable { confirmDel = false })
                            Text("Yes", fontSize = 12.sp, color = c.danger, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, modifier = Modifier.clickable { onDelete() })
                        }
                    }
                    SmallBtn(c, "Save", accent = true) {
                        onUpdate(
                            provider.copy(
                                name = editName.trim(),
                                baseUrl = editUrl.trim(),
                                keys = editKeys.split('\n').map { it.trim() }.filter { it.isNotBlank() },
                                models = editModels,
                                fetchModels = editFetch
                            )
                        )
                        expanded = false
                    }
                }
                if (showModelSheet) {
                    ModalBottomSheet(onDismissRequest = { showModelSheet = false }, containerColor = c.surface) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
                            Text("Select models", fontFamily = UnicaOneRegular, fontSize = 20.sp, color = c.ink, letterSpacing = 0.4.sp)
                            Text("${selectedModels.size} of ${fetchedList.size} selected", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SmallBtn(c, "All") { selectedModels = fetchedList.toSet() }
                                SmallBtn(c, "Clear") { selectedModels = emptySet() }
                            }
                            Spacer(Modifier.height(8.dp))
                            Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                                fetchedList.forEach { mid ->
                                    val checked = mid in selectedModels
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedModels = if (checked) selectedModels - mid else selectedModels + mid
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(mid, fontSize = 12.sp, color = c.ink, fontFamily = JetBrainsMonoFamily, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        TickToggle(c, checked) {
                                            selectedModels = if (checked) selectedModels - mid else selectedModels + mid
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            SmallBtn(c, "Add ${selectedModels.size} selected", accent = true) {
                                val existing = editModels.associateBy { it.id }
                                val fetchedSet = fetchedList.toSet()
                                val chosen = fetchedList.filter { it in selectedModels }
                                    .map { id -> existing[id] ?: ModelConfig(id, true, provider.agentModeDefault) }
                                val manual = editModels.filter { it.id !in fetchedSet }
                                editModels = (chosen + manual).toMutableList()
                                showModelSheet = false
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Stable labeled input — label stays fixed above the field (no floating jump).
 * Single-line fields use a fixed height so typing doesn't resize the row.
 */
@Composable
private fun Field(
    c: AdminColors,
    label: String,
    value: String,
    minLines: Int = 1,
    placeholder: String = "",
    onChange: (String) -> Unit
) {
    val multi = minLines > 1
    val minH = if (multi) (minLines * 22 + 20).dp else 40.dp
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = c.muted,
            fontFamily = InterFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(5.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = !multi,
            maxLines = if (multi) minLines.coerceAtLeast(4) else 1,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = c.ink,
                fontFamily = InterFamily,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(c.accent),
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = minH)
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface2)
                        .padding(horizontal = 12.dp, vertical = if (multi) 10.dp else 0.dp),
                    contentAlignment = if (multi) Alignment.TopStart else Alignment.CenterStart
                ) {
                    if (value.isEmpty() && placeholder.isNotBlank()) {
                        Text(
                            placeholder,
                            fontSize = 13.sp,
                            color = c.muted.copy(alpha = 0.45f),
                            fontFamily = InterFamily,
                            maxLines = if (multi) 2 else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .then(if (!multi) Modifier else Modifier.padding(top = 0.dp))
                    ) { inner() }
                }
            }
        )
    }
}

/** Compact tick control (replaces large Switch). */
@Composable
private fun TickToggle(c: AdminColors, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (checked) c.accent else Color.Transparent)
            .border(
                width = 1.5.dp,
                color = if (checked) c.accent else c.line,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable { onToggle(!checked) },
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Text("✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = InterFamily)
        }
    }
}

@Composable
private fun SkillsAdminPane(
    c: AdminColors,
    loading: Boolean,
    tick: Int,
    search: String,
    onSearch: (String) -> Unit,
    saveMsg: String?,
    onToggle: (String, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onDisableRisky: () -> Unit,
    onSaveEdit: (SkillManager.AdminSkillEdit) -> Unit,
    onDelete: (String) -> Unit,
    onAddSkill: (SkillManager.AdminSkillEdit) -> Unit,
) {
    val skills = remember(tick) { SkillManager.ALL_SKILLS }
    val disabled = remember(tick) { SkillManager.adminDisabledSnapshot() }
    val enabledCount = skills.count { SkillManager.isAdminAllowed(it.id) }
    var editTarget by remember { mutableStateOf<SkillManager.Skill?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }

    SectionTitle(
        "Skills",
        "Toggle, edit, delete, or add skills for every user. Paste an SVG from lucide.dev (or pick a built-in Lucide glyph). Icons render with the same stroke/size/tint as built-ins. Body content never appears in the agent timeline for catalog skills.",
        c
    )

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Total", "${skills.size}", Lucide.Skills, c, Modifier.weight(1f))
        StatTile("Live", "$enabledCount", Lucide.Check, c, Modifier.weight(1f))
        StatTile("Off", "${disabled.size}", Lucide.X, c, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Enable all",
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.accentInk, fontFamily = InterFamily,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.chipOn).clickable(onClick = onEnableAll).padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Text(
            "Disable risky",
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.surface2).clickable(onClick = onDisableRisky).padding(horizontal = 12.dp, vertical = 8.dp)
        )
        Text(
            "+ Add",
            fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.accent, fontFamily = InterFamily,
            modifier = Modifier.clickable { showAdd = true }.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        saveMsg?.let {
            Text(if (it == "OK") "Saved" else it, fontSize = 12.sp, color = if (it == "OK") c.ok else c.danger, fontFamily = InterFamily)
        }
    }
    Spacer(Modifier.height(12.dp))

    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Lucide.Search, null, tint = c.muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (search.isEmpty()) Text("Filter skills…", fontSize = 13.sp, color = c.muted.copy(0.7f), fontFamily = InterFamily)
            BasicTextField(
                value = search, onValueChange = onSearch, modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 13.sp, color = c.ink, fontFamily = InterFamily),
                cursorBrush = SolidColor(c.accent), singleLine = true
            )
        }
    }
    HorizontalDivider(color = c.line)
    Spacer(Modifier.height(8.dp))

    if (loading) LoadingDots(c.muted)
    else {
        val filtered = skills.filter {
            search.isBlank() || it.name.contains(search, true) || it.id.contains(search, true) || it.description.contains(search, true)
        }
        filtered.forEach { skill ->
            val on = SkillManager.isAdminAllowed(skill.id)
            val storedIcon = SkillManager.getIconSvg(skill.id).ifBlank { skill.iconSvg }
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.ahamai.app.ui.icons.SkillIconGlyph(
                        skillId = skill.id,
                        storedIcon = storedIcon,
                        tint = if (on) c.accent else c.muted,
                        size = 22.dp
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(skill.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
                        Text(skill.id, fontSize = 11.sp, color = c.muted, fontFamily = JetBrainsMonoFamily)
                        Spacer(Modifier.height(2.dp))
                        Text(skill.description, fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
                    }
                    TickToggle(c, on) { onToggle(skill.id, it) }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Edit", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.accent, fontFamily = InterFamily, modifier = Modifier.clickable { editTarget = skill })
                    Text(
                        if (confirmDelete == skill.id) "Confirm delete" else "Delete",
                        fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.danger, fontFamily = InterFamily,
                        modifier = Modifier.clickable {
                            if (confirmDelete == skill.id) {
                                onDelete(skill.id); confirmDelete = null
                            } else confirmDelete = skill.id
                        }
                    )
                    if (confirmDelete == skill.id) {
                        Text("Cancel", fontSize = 12.5.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.clickable { confirmDelete = null })
                    }
                }
            }
            HorizontalDivider(color = c.line)
        }
        if (filtered.isEmpty()) {
            Text("No skills match.", fontSize = 13.sp, color = c.muted, fontFamily = InterFamily)
        }
    }

    editTarget?.let { skill ->
        val stored = SkillManager.getIconSvg(skill.id).ifBlank { skill.iconSvg }
        SkillEditDialog(
            c = c,
            title = "Edit skill",
            initialId = skill.id,
            initialName = skill.name,
            initialDesc = skill.description,
            initialContent = skill.content,
            initialIconStored = stored,
            idEditable = false,
            onDismiss = { editTarget = null },
            onSave = { id, name, desc, content, iconStored ->
                onSaveEdit(
                    SkillManager.AdminSkillEdit(
                        id = id,
                        name = name,
                        description = desc,
                        content = content,
                        iconSvg = iconStored.trim(),
                        isAdminAuthored = !skill.isBuiltin,
                        enabled = true
                    )
                )
                editTarget = null
            }
        )
    }

    if (showAdd) {
        SkillEditDialog(
            c = c,
            title = "Add skill",
            initialId = "",
            initialName = "",
            initialDesc = "",
            initialContent = "",
            initialIconStored = "",
            idEditable = true,
            onDismiss = { showAdd = false },
            onSave = { id, name, desc, content, iconStored ->
                val finalId = SkillManager.sanitizeName(id.ifBlank { name })
                onAddSkill(
                    SkillManager.AdminSkillEdit(
                        id = finalId,
                        name = name.ifBlank { SkillManager.humanize(finalId) },
                        description = desc,
                        content = content,
                        iconSvg = iconStored.trim(),
                        isAdminAuthored = true,
                        enabled = true
                    )
                )
                showAdd = false
            }
        )
    }
}

@Composable
private fun SkillEditDialog(
    c: AdminColors,
    title: String,
    initialId: String,
    initialName: String,
    initialDesc: String,
    initialContent: String,
    /** Raw Lucide SVG paste and/or `lucide:key` from quick picker. */
    initialIconStored: String,
    idEditable: Boolean,
    onDismiss: () -> Unit,
    onSave: (id: String, name: String, desc: String, content: String, iconStored: String) -> Unit,
) {
    var id by remember { mutableStateOf(initialId) }
    var name by remember { mutableStateOf(initialName) }
    var desc by remember { mutableStateOf(initialDesc) }
    var content by remember { mutableStateOf(initialContent) }
    var iconStored by remember { mutableStateOf(initialIconStored) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = c.surface, modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Column(Modifier.fillMaxSize().padding(18.dp)) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OswaldFamily, color = c.ink)
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    if (idEditable) {
                        Field(c, "Skill id (kebab-case)", id) { id = it }
                        Spacer(Modifier.height(8.dp))
                    } else {
                        Text("id · $initialId", fontSize = 11.sp, color = c.muted, fontFamily = JetBrainsMonoFamily)
                        Spacer(Modifier.height(8.dp))
                    }
                    Field(c, "Display name", name) { name = it }
                    Spacer(Modifier.height(8.dp))
                    Field(c, "Description (what + when)", desc, minLines = 2) { desc = it }
                    Spacer(Modifier.height(8.dp))
                    Field(c, "Skill body (SKILL.md content)", content, minLines = 6) { content = it }
                    Spacer(Modifier.height(14.dp))
                    // Unified skill icon for every skill (default / custom / admin)
                    Text(
                        "Icon",
                        fontSize = 11.sp,
                        color = c.muted,
                        fontFamily = InterFamily,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surface2)
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accent.copy(0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            com.ahamai.app.ui.icons.SkillIconGlyph(
                                skillId = id.ifBlank { initialId }.ifBlank { "skill" },
                                storedIcon = com.ahamai.app.ui.icons.SkillIcons.encodeKey("skills"),
                                tint = c.accent,
                                size = 22.dp
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Unified skill mark", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
                            Text(
                                "Same icon for all skills in the app",
                                fontSize = 12.sp,
                                color = c.muted,
                                fontFamily = InterFamily
                            )
                        }
                    }
                    // Always store unified skill key
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        iconStored = com.ahamai.app.ui.icons.SkillIcons.encodeKey("skills")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Cancel", fontSize = 13.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                    Spacer(Modifier.width(8.dp))
                    val ok = name.isNotBlank() || id.isNotBlank() || initialId.isNotBlank()
                    Text(
                        "Save",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (ok) Color.White else c.muted, fontFamily = InterFamily,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (ok) c.accent else c.surface2)
                            .clickable(enabled = ok) {
                                onSave(
                                    id.ifBlank { initialId },
                                    name,
                                    desc,
                                    content,
                                    com.ahamai.app.ui.icons.SkillIcons.encodeKey("skills")
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnalyticsPane(
    c: AdminColors, loading: Boolean, range: String, onRange: (String) -> Unit,
    data: List<DailyStats>, users: Int, calls: Int, tokens: Int,
    context: android.content.Context
) {
    SectionTitle("Analytics", "Usage trends and export", c)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("7" to "7 days", "30" to "30 days").forEach { (k, label) ->
            val on = range == k
            Text(
                label,
                fontSize = 12.5.sp,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                color = if (on) c.accent else c.muted,
                fontFamily = InterFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.chipOn else c.surface2)
                    .clickable { onRange(k) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
    Spacer(Modifier.height(14.dp))
    if (loading) LoadingDots(c.muted)
    else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Users", "$users", Lucide.Users, c, Modifier.weight(1f))
            StatTile("Calls", fmtTok(calls), Lucide.Activity, c, Modifier.weight(1f))
            StatTile("Tokens", fmtTok(tokens), Lucide.Zap, c, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        AdminCard(c) {
            Text("API calls", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
            Spacer(Modifier.height(12.dp))
            if (data.isEmpty()) Text("No data", fontSize = 12.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), textAlign = TextAlign.Center)
            else SimpleBarChart(
                data = data.map { it.calls.toFloat() },
                labels = data.map {
                    val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it.date)
                    java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(d ?: java.util.Date())
                },
                maxValue = (data.maxOfOrNull { it.calls } ?: 1).toFloat().coerceAtLeast(1f),
                barColor = c.chart,
                textColor = c.muted,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        AdminCard(c) {
            Text("Tokens", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
            Spacer(Modifier.height(12.dp))
            if (data.isEmpty()) Text("No data", fontSize = 12.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.fillMaxWidth().padding(vertical = 30.dp), textAlign = TextAlign.Center)
            else SimpleBarChart(
                data = data.map { it.tokens.toFloat() },
                labels = data.map {
                    val d = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it.date)
                    java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(d ?: java.util.Date())
                },
                maxValue = (data.maxOfOrNull { it.tokens } ?: 1).toFloat().coerceAtLeast(1f),
                barColor = c.chart,
                textColor = c.muted,
                modifier = Modifier.fillMaxWidth().height(160.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        AdminCard(c) {
            Text("Usage by type", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
            Spacer(Modifier.height(12.dp))
            val totalChat = data.sumOf { it.chat }
            val totalAgent = data.sumOf { it.agent }
            val totalSearch = data.sumOf { it.search }
            val total = (totalChat + totalAgent + totalSearch).coerceAtLeast(1)
            UsageBar("Chat", totalChat, total, c)
            UsageBar("Agent", totalAgent, total, c)
            UsageBar("Search", totalSearch, total, c)
        }
        Spacer(Modifier.height(10.dp))
        AdminCard(c) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface2)
                    .clickable {
                        shareText(context, "Analytics Export - ${range}d.csv", buildAnalyticsCsv(data))
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Lucide.Download, null, tint = c.ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(10.dp))
                Text("Export CSV", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
            }
        }
    }
}

@Composable
private fun UsageBar(name: String, value: Int, max: Int, c: AdminColors) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, fontSize = 12.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.width(64.dp))
        Box(Modifier.weight(1f).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.surface2)) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f))
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.chart)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$value", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

/** All admin-editable AdMob ad settings (backed by Compose state). Numeric fields are
 *  kept as strings for free text entry and parsed on save. */
private class AdsAdminState {
    var enabled by mutableStateOf(true)
    var freeOnly by mutableStateOf(true)
    var chatOn by mutableStateOf(true)
    var chatInterval by mutableStateOf("1")
    var agentOn by mutableStateOf(true)
    var buildOn by mutableStateOf(true)
    var completionOn by mutableStateOf(true)
    var completionInterval by mutableStateOf("1")
    var overLimitOn by mutableStateOf(true)
    var minGap by mutableStateOf("60")
    var nativeUnit by mutableStateOf("")
    var rewardedUnit by mutableStateOf("")
    var interstitialUnit by mutableStateOf("")
}

/** Compact single-line labelled input used in the ADS CONTROL card. */
@Composable
private fun AdsInput(c: AdminColors, label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = c.ink, fontFamily = InterFamily)
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface2)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) Text(placeholder, fontSize = 12.sp, color = c.muted.copy(0.5f), fontFamily = JetBrainsMonoFamily)
            BasicTextField(
                value = value, onValueChange = onChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(fontSize = 12.sp, color = c.ink, fontFamily = JetBrainsMonoFamily),
                cursorBrush = SolidColor(c.accent)
            )
        }
    }
}

@Composable
private fun ControlPane(
    c: AdminColors,
    ads: AdsAdminState,
    maintenanceOn: Boolean, onMaint: (Boolean) -> Unit,
    webSearchOn: Boolean, onWeb: (Boolean) -> Unit,
    feedbackEmail: String, onFeedbackEmail: (String) -> Unit,
    useCustomHtml: Boolean, onHtmlMode: (Boolean) -> Unit,
    maintenance: String, onMaintText: (String) -> Unit,
    maintenanceHtml: String, onHtml: (String) -> Unit,
    chatPrompt: String, onChat: (String) -> Unit,
    agentPrompt: String, onAgent: (String) -> Unit,
    settingsMsg: String?, promptsMsg: String?,
    onSaveSettings: () -> Unit,
    onSavePrompts: () -> Unit,
    onPreview: () -> Unit,
) {
    SectionTitle("Control", "Maintenance, search, and system prompts", c)

    AdminCard(c) {
        Text("APP SWITCHES", fontFamily = OswaldFamily, fontSize = 11.sp, letterSpacing = 1.2.sp, color = c.muted)
        Spacer(Modifier.height(10.dp))
        SwitchRow(c, "Maintenance mode", "Block non-admin users", maintenanceOn, onMaint)
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        SwitchRow(c, "Web search", "Allow Brave Search API", webSearchOn, onWeb)
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        SwitchRow(c, "Custom HTML page", "Use HTML for maintenance screen", useCustomHtml, onHtmlMode)
    }
    Spacer(Modifier.height(12.dp))

    AdminCard(c) {
        Text("FEEDBACK", fontFamily = OswaldFamily, fontSize = 11.sp, letterSpacing = 1.2.sp, color = c.muted)
        Spacer(Modifier.height(8.dp))
        Text(
            "Profile → Feedback sends to this inbox (with optional screenshots).",
            fontSize = 11.5.sp,
            color = c.muted,
            fontFamily = InterFamily,
            lineHeight = 15.sp
        )
        Spacer(Modifier.height(10.dp))
        Field(c, "Feedback email", feedbackEmail, placeholder = "support@yourdomain.com") { onFeedbackEmail(it) }
    }
    Spacer(Modifier.height(12.dp))

    // ── ADS CONTROL (AdMob) ─────────────────────────────────────────────────
    AdminCard(c) {
        Text("ADS CONTROL", fontFamily = OswaldFamily, fontSize = 11.sp, letterSpacing = 1.2.sp, color = c.muted)
        Spacer(Modifier.height(10.dp))
        SwitchRow(c, "Ads enabled", "Master switch for ALL ads", ads.enabled) { ads.enabled = it }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        SwitchRow(c, "Free plan only", "Paid plans stay ad-free (like ChatGPT)", ads.freeOnly) { ads.freeOnly = it }

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        Text("CHAT", fontFamily = OswaldFamily, fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted)
        Spacer(Modifier.height(6.dp))
        SwitchRow(c, "Chat sponsored card", "Native ad below answers", ads.chatOn) { ads.chatOn = it }
        AdsInput(c, "Show card after every N answers", ads.chatInterval, "1") { ads.chatInterval = it.filter { ch -> ch.isDigit() } }

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        Text("AGENT (video)", fontFamily = OswaldFamily, fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted)
        Spacer(Modifier.height(6.dp))
        SwitchRow(c, "Agent video ads", "Master switch for agent-mode ads", ads.agentOn) { ads.agentOn = it }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        SwitchRow(c, "Build rewarded ad", "When a cloud APK build starts", ads.buildOn) { ads.buildOn = it }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        SwitchRow(c, "Completion interstitial", "After an agent task finishes", ads.completionOn) { ads.completionOn = it }
        AdsInput(c, "Interstitial after every N completions", ads.completionInterval, "1") { ads.completionInterval = it.filter { ch -> ch.isDigit() } }
        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        SwitchRow(c, "Over-limit rewarded ad", "Free user over token allowance", ads.overLimitOn) { ads.overLimitOn = it }
        AdsInput(c, "Min seconds between full-screen ads", ads.minGap, "60") { ads.minGap = it.filter { ch -> ch.isDigit() } }

        HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
        Text("AD UNIT IDS", fontFamily = OswaldFamily, fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted)
        Text(
            "Leave blank for Google TEST ads (recommended while debugging). Invalid IDs are cleared on Save and fall back to test units. Real units need a real AdMob App ID in the manifest.",
            fontSize = 10.5.sp,
            color = c.muted,
            fontFamily = InterFamily,
            lineHeight = 14.sp
        )
        AdsInput(c, "Chat native unit", ads.nativeUnit, "blank = test ads") { ads.nativeUnit = it }
        AdsInput(c, "Agent rewarded unit", ads.rewardedUnit, "blank = test ads") { ads.rewardedUnit = it }
        AdsInput(c, "Agent interstitial unit", ads.interstitialUnit, "blank = test ads") { ads.interstitialUnit = it }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = c.line)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            settingsMsg?.let {
                Text(if (it == "OK") "Saved" else it, fontSize = 12.sp, color = if (it == "OK") c.ok else c.danger, fontFamily = InterFamily, modifier = Modifier.weight(1f))
            } ?: Spacer(Modifier.weight(1f))
            PrimaryBtn(c, "Save settings", onSaveSettings)
        }
    }
    Spacer(Modifier.height(12.dp))
    AdminCard(c) {
        Text(if (useCustomHtml) "Maintenance HTML" else "Maintenance message", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
        Spacer(Modifier.height(10.dp))
        if (useCustomHtml) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface2)
                    .heightIn(min = 140.dp, max = 220.dp)
                    .padding(12.dp)
            ) {
                if (maintenanceHtml.isEmpty()) {
                    Text("<html>…</html>", fontSize = 11.sp, color = c.muted.copy(0.4f), fontFamily = JetBrainsMonoFamily)
                }
                BasicTextField(
                    value = maintenanceHtml, onValueChange = onHtml, modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    textStyle = TextStyle(fontSize = 11.sp, color = c.ink, fontFamily = JetBrainsMonoFamily, lineHeight = 16.sp),
                    cursorBrush = SolidColor(c.accent)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Preview", fontSize = 13.sp, color = c.accent, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, modifier = Modifier.clickable(onClick = onPreview))
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface2)
                    .heightIn(min = 80.dp)
                    .padding(12.dp)
            ) {
                if (maintenance.isEmpty()) Text("We're upgrading our systems. Back soon!", fontSize = 13.sp, color = c.muted.copy(0.5f), fontFamily = InterFamily)
                BasicTextField(
                    value = maintenance, onValueChange = onMaintText, modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 13.sp, color = c.ink, fontFamily = InterFamily),
                    cursorBrush = SolidColor(c.accent)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            settingsMsg?.let {
                Text(if (it == "OK") "Saved" else it, fontSize = 12.sp, color = if (it == "OK") c.ok else c.danger, fontFamily = InterFamily, modifier = Modifier.weight(1f))
            } ?: Spacer(Modifier.weight(1f))
            PrimaryBtn(c, "Save settings", onSaveSettings)
        }
    }
    Spacer(Modifier.height(12.dp))
    AdminCard(c) {
        Text("System prompts", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
        Text("Fully remote — blank means empty. Paste prompts below and Save.", fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily, lineHeight = 15.sp)
        Spacer(Modifier.height(12.dp))
        Text("Chat", fontSize = 12.sp, color = c.muted, fontFamily = InterFamily)
        Spacer(Modifier.height(6.dp))
        PromptBox(c, chatPrompt, "Paste chat system prompt…", onChat)
        Spacer(Modifier.height(12.dp))
        Text("Agent", fontSize = 12.sp, color = c.muted, fontFamily = InterFamily)
        Spacer(Modifier.height(6.dp))
        PromptBox(c, agentPrompt, "Paste agent system prompt…", onAgent)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            promptsMsg?.let {
                Text(if (it == "OK") "Saved" else it, fontSize = 12.sp, color = if (it == "OK") c.ok else c.danger, fontFamily = InterFamily, modifier = Modifier.weight(1f))
            } ?: Spacer(Modifier.weight(1f))
            PrimaryBtn(c, "Save prompts", onSavePrompts)
        }
    }
}


private val PLAN_IDS = listOf("free", "pro", "plus", "enterprise")

/**
 * Multi-select plan chips. Empty selection = all plans (saved as empty list).
 * UI shows all selected when empty so admin sees "available to everyone".
 */
@Composable
private fun PlanChipsRow(
    c: AdminColors,
    selected: List<String>,
    onChange: (List<String>) -> Unit
) {
    val effective = if (selected.isEmpty()) PLAN_IDS else selected.map { it.lowercase() }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Plans", fontSize = 10.sp, color = c.muted, fontFamily = InterFamily)
        PLAN_IDS.forEach { pid ->
            val on = pid in effective
            Text(
                pid,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (on) Color.White else c.ink,
                fontFamily = InterFamily,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (on) c.accent else c.surface2)
                    .clickable {
                        val cur = if (selected.isEmpty()) PLAN_IDS.toMutableList() else selected.map { it.lowercase() }.toMutableList()
                        if (pid in cur) cur.remove(pid) else cur.add(pid)
                        // If all selected → store empty (= all). If none → keep empty too but force free at least
                        val next = when {
                            cur.isEmpty() -> listOf("free")
                            cur.size == PLAN_IDS.size -> emptyList()
                            else -> PLAN_IDS.filter { it in cur }
                        }
                        onChange(next)
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
        if (selected.isEmpty()) {
            Text("all", fontSize = 10.sp, color = c.ok, fontFamily = InterFamily)
        }
    }
}

@Composable
private fun SwitchRow(c: AdminColors, title: String, desc: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = c.ink, fontFamily = InterFamily)
            if (desc.isNotBlank()) {
                Text(desc, fontSize = 11.sp, color = c.muted, fontFamily = InterFamily, lineHeight = 14.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        TickToggle(c, checked, onToggle)
    }
}

@Composable
private fun PromptBox(c: AdminColors, value: String, placeholder: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .heightIn(min = 110.dp, max = 220.dp)
            .padding(12.dp)
    ) {
        if (value.isEmpty()) Text(placeholder, fontSize = 11.sp, color = c.muted.copy(0.5f), fontFamily = JetBrainsMonoFamily)
        BasicTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            textStyle = TextStyle(fontSize = 11.5.sp, color = c.ink, fontFamily = JetBrainsMonoFamily, lineHeight = 16.sp),
            cursorBrush = SolidColor(c.accent)
        )
    }
}

@Composable
private fun PrimaryBtn(c: AdminColors, label: String, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFamily = InterFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.accent)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp)
    )
}

@Composable
private fun SmallBtn(
    c: AdminColors,
    label: String,
    accent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        label,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = when {
            !enabled -> c.muted
            accent -> Color.White
            else -> c.accent
        },
        fontFamily = InterFamily,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (accent && enabled) c.accent else c.surface2)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@Composable
private fun AccessPane(
    c: AdminColors,
    adminsLoading: Boolean,
    adminEmails: List<String>,
    newAdmin: String,
    onNewAdmin: (String) -> Unit,
    adminMsg: String?,
    onAddAdmin: () -> Unit,
    onRemoveAdmin: (String) -> Unit,
    auditLoading: Boolean,
    auditLogs: List<AuditEntry>,
    onReloadAudit: () -> Unit,
) {
    SectionTitle("Access", "Admin emails and audit trail", c)

    AdminCard(c) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.AccessCard, null, tint = c.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Admin emails", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink)
        }
        Spacer(Modifier.height(6.dp))
        Text("These accounts can open this console.", fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
        if (adminsLoading) LoadingDots(c.muted)
        else {
            if (adminEmails.isEmpty()) {
                Text("No admins configured.", fontSize = 12.sp, color = c.muted, fontFamily = InterFamily)
            } else {
                adminEmails.forEach { em ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.AtSign, null, tint = c.accent, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(em, fontSize = 12.5.sp, color = c.ink, fontFamily = InterFamily, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Lucide.Trash2, null, tint = c.danger, modifier = Modifier.size(14.dp).clickable { onRemoveAdmin(em) })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    Field(c, "Email", newAdmin, placeholder = "admin@example.com") { onNewAdmin(it) }
                }
                Spacer(Modifier.width(8.dp))
                SmallBtn(c, "Add", accent = true, onClick = onAddAdmin)
            }
            adminMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(if (it == "OK") "Saved" else it, fontSize = 12.sp, color = if (it == "OK") c.ok else c.danger, fontFamily = InterFamily)
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    AdminCard(c) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.FileText, null, tint = c.accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Audit log", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = c.ink, modifier = Modifier.weight(1f))
            Text("Reload", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.accent, fontFamily = InterFamily, modifier = Modifier.clickable(onClick = onReloadAudit))
        }
        Spacer(Modifier.height(6.dp))
        Text("Newest admin actions first.", fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily)
        Spacer(Modifier.height(12.dp))
        if (auditLoading) LoadingDots(c.muted)
        else if (auditLogs.isEmpty()) Text("No audit entries yet.", fontSize = 13.sp, color = c.muted, fontFamily = InterFamily)
        else {
            auditLogs.forEachIndexed { idx, e ->
                if (idx > 0) HorizontalDivider(color = c.line, modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily, modifier = Modifier.weight(1f))
                    Text(fmtTs(e.ts), fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
                }
                if (e.detail.isNotBlank()) Text(e.detail, fontSize = 12.sp, color = c.muted, fontFamily = InterFamily)
                if (e.by.isNotBlank()) Text(e.by, fontSize = 11.sp, color = c.muted.copy(0.8f), fontFamily = InterFamily)
            }
        }
    }
}

// ── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun AddProviderDialog(c: AdminColors, onDismiss: () -> Unit, onAdd: (ProviderEntry) -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("https://") }
    var keys by remember { mutableStateOf("") }
    var models by remember { mutableStateOf("") }
    var fetch by remember { mutableStateOf(true) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = c.surface) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Add provider", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = OswaldFamily, color = c.ink)
                Spacer(Modifier.height(14.dp))
                Field(c, "Provider name", name) { name = it }
                Spacer(Modifier.height(8.dp))
                Field(c, "Base URL", url) { url = it }
                Spacer(Modifier.height(8.dp))
                Field(c, "API keys (one per line)", keys, minLines = 2) { keys = it }
                Spacer(Modifier.height(8.dp))
                Field(c, "Models (comma separated)", models) { models = it }
                Spacer(Modifier.height(8.dp))
                SwitchRow(c, "Auto-fetch models", "Call /models when available", fetch) { fetch = it }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Cancel", fontSize = 13.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
                    Spacer(Modifier.width(8.dp))
                    val ok = name.isNotBlank() && url.length > 10
                    Text(
                        "Add",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = if (ok) c.accentInk else c.muted, fontFamily = InterFamily,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (ok) c.accent else c.surface2)
                            .clickable(enabled = ok) {
                                val id = name.lowercase().replace(" ", "_").replace(Regex("[^a-z0-9_]"), "")
                                    .ifBlank { "p_${System.currentTimeMillis()}" }
                                onAdd(
                                    ProviderEntry(
                                        id = id, name = name.trim(), baseUrl = url.trim(), enabled = true,
                                        keys = keys.split('\n').map { it.trim() }.filter { it.isNotBlank() },
                                        models = if (models.isBlank()) emptyList()
                                        else models.split(',').map { ModelConfig(it.trim(), true, false) }.filter { it.id.isNotBlank() },
                                        agentModeDefault = false, fetchModels = fetch
                                    )
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteFavicon(baseUrl: String, size: Int, modifier: Modifier = Modifier) {
    val domain = baseUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .substringBefore("?")
        .ifBlank { "openai.com" }
    // Google s2 + duckduckgo icons as reliable real favicon sources (same as chat providers)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data("https://www.google.com/s2/favicons?domain=$domain&sz=128")
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier.size(size.dp).clip(RoundedCornerShape((size * 0.22f).dp)),
        contentScale = ContentScale.Crop
    )
}

@Composable
private fun SimpleBarChart(
    data: List<Float>, labels: List<String>, maxValue: Float,
    barColor: Color, textColor: Color, modifier: Modifier = Modifier
) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (data.isEmpty() || maxValue <= 0f) return@Canvas
            val barWidth = (size.width / data.size) * 0.65f
            val spacing = (size.width / data.size) * 0.35f
            data.forEachIndexed { i, value ->
                val barHeight = (value / maxValue) * size.height
                val x = i * (barWidth + spacing)
                drawRoundRect(
                    color = barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight.coerceAtLeast(2f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.take(5).forEach {
                Text(it, fontSize = 9.sp, color = textColor, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun buildAnalyticsCsv(data: List<DailyStats>): String {
    val sb = StringBuilder()
    sb.append("Date,Users,Calls,Tokens,Chat,Agent,Search\n")
    data.forEach {
        sb.append("${it.date},${it.users},${it.calls},${it.tokens},${it.chat},${it.agent},${it.search}\n")
    }
    return sb.toString()
}

private fun shareText(context: android.content.Context, title: String, text: String) {
    try {
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, title)
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Export Analytics"))
    } catch (_: Exception) {
    }
}

// ── Combos (9Router-style: custom multi-provider model chains) ───────────────
//
// 9Router: Combo name → ordered provider/model members.
// On 429 / error / empty → next member (fallback | sticky | round-robin).
// Admin builds combos manually: pick YOUR providers → Fetch /models → multi-select
// or type model id manually → order chain → Save.

@Composable
private fun CombosPane(
    c: AdminColors,
    loading: Boolean,
    combosEnabled: Boolean,
    combos: List<com.ahamai.app.data.ComboRouter.Combo>,
    providers: List<ProviderEntry>,
    saveMsg: String?,
    testing: Boolean,
    testLogs: List<String>,
    onToggleEnabled: (Boolean) -> Unit,
    onSave: (List<com.ahamai.app.data.ComboRouter.Combo>) -> Unit,
    onTestCombo: (com.ahamai.app.data.ComboRouter.Combo) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newId by remember { mutableStateOf("") }
    var expandId by remember { mutableStateOf<String?>(null) }
    var createErr by remember { mutableStateOf<String?>(null) }

    SectionTitle(
        "Combos",
        "Virtual models · multi-provider chain · auto-failover",
        c
    )
    if (loading) {
        LoadingDots(c.muted)
        return
    }

    if (providers.none { it.enabled }) {
        AdminCard(c) {
            Text("No enabled providers", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
            Spacer(Modifier.height(4.dp))
            Text("Enable providers in API first, then chain them here.", fontSize = 12.sp, color = c.muted, fontFamily = InterFamily)
        }
        return
    }

    AdminCard(c) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Routing", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
                Text("#1 primary · fail → next member", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
            }
            TickToggle(c, combosEnabled, onToggleEnabled)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallBtn(c, if (showCreate) "Cancel" else "+ New combo", accent = !showCreate) {
                showCreate = !showCreate
                createErr = null
            }
        }
        saveMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(if (it == "OK") "Saved" else it, fontSize = 11.sp, color = if (it == "OK") c.ok else c.danger, fontFamily = InterFamily)
        }
        if (testLogs.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface2)
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                testLogs.forEach { log ->
                    Text(
                        log, fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily, lineHeight = 15.sp,
                        color = when {
                            log.contains("✓") -> c.ok
                            log.contains("✗") -> c.danger
                            else -> c.muted
                        }
                    )
                }
            }
        }
    }

    AnimatedVisibility(showCreate) {
        Column {
            Spacer(Modifier.height(8.dp))
            AdminCard(c) {
                Text("New combo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
                Spacer(Modifier.height(8.dp))
                Field(c, "Display name", newName, placeholder = "premium-coding") { v ->
                    val prevSlug = slugifyComboId(newName)
                    newName = v
                    if (newId.isBlank() || newId == prevSlug) newId = slugifyComboId(v)
                }
                Spacer(Modifier.height(8.dp))
                Field(c, "Model id (picker)", newId, placeholder = "Auto") { v ->
                    newId = v.trim().filter { ch ->
                        ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '/' || ch == '.'
                    }
                }
                createErr?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, fontSize = 11.sp, color = c.danger, fontFamily = InterFamily)
                }
                Spacer(Modifier.height(10.dp))
                SmallBtn(c, "Create & add members", accent = true) {
                    val id = newId.ifBlank { slugifyComboId(newName) }.ifBlank {
                        createErr = "Enter a name or model id"
                        return@SmallBtn
                    }
                    if (combos.any { it.id.equals(id, ignoreCase = true) }) {
                        createErr = "Model id already exists"
                        return@SmallBtn
                    }
                    createErr = null
                    val created = com.ahamai.app.data.ComboRouter.Combo(
                        id = id,
                        name = newName.ifBlank { id },
                        enabled = true,
                        chatMode = true,
                        agentMode = false,
                        strategy = com.ahamai.app.data.ComboRouter.Strategy.FALLBACK,
                        members = emptyList(),
                        note = ""
                    )
                    onSave(combos + created)
                    expandId = id
                    showCreate = false
                    newName = ""
                    newId = ""
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Text(
        "YOUR COMBOS (${combos.size})",
        fontFamily = OswaldFamily, fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted
    )
    Spacer(Modifier.height(6.dp))

    if (combos.isEmpty()) {
        AdminCard(c) {
            Text("No combos yet", fontSize = 13.sp, color = c.ink, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "New combo → pick provider → Fetch / type model → chain order → Save",
                fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily
            )
        }
    } else {
        combos.forEachIndexed { idx, combo ->
            ComboEditorCard(
                c = c,
                combo = combo,
                providers = providers,
                testing = testing,
                forceExpand = expandId == combo.id,
                onTest = { snap -> onTestCombo(snap) },
                onUpdate = { updated ->
                    val list = combos.toMutableList()
                    list[idx] = updated
                    onSave(list)
                },
                onDelete = {
                    onSave(combos.filterIndexed { i, _ -> i != idx })
                    if (expandId == combo.id) expandId = null
                }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
    Spacer(Modifier.height(28.dp))
}

private fun slugifyComboId(name: String): String =
    name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(48)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComboEditorCard(
    c: AdminColors,
    combo: com.ahamai.app.data.ComboRouter.Combo,
    providers: List<ProviderEntry>,
    testing: Boolean,
    forceExpand: Boolean,
    onTest: (com.ahamai.app.data.ComboRouter.Combo) -> Unit,
    onUpdate: (com.ahamai.app.data.ComboRouter.Combo) -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var expanded by remember(combo.id) { mutableStateOf(forceExpand || combo.members.isEmpty()) }
    LaunchedEffect(forceExpand) { if (forceExpand) expanded = true }

    var editId by remember(combo.id) { mutableStateOf(combo.id) }
    var editName by remember(combo.id) { mutableStateOf(combo.name) }
    var editNote by remember(combo.id) { mutableStateOf(combo.note) }
    var members by remember(combo.id) { mutableStateOf(combo.members) }
    // Sync members when parent saves new list for same id
    LaunchedEffect(combo.members) { members = combo.members }

    var strategy by remember(combo.id) { mutableStateOf(combo.strategy) }
    var chatMode by remember(combo.id) { mutableStateOf(combo.chatMode) }
    var agentMode by remember(combo.id) { mutableStateOf(combo.agentMode) }
    var enabled by remember(combo.id) { mutableStateOf(combo.enabled) }
    var plans by remember(combo.id) { mutableStateOf(combo.plans) }
    LaunchedEffect(combo.plans) { plans = combo.plans }

    val enabledProviders = providers.filter { it.enabled }
    var pickProviderId by remember(combo.id) {
        mutableStateOf(enabledProviders.firstOrNull()?.id ?: "")
    }
    val pickProvider = enabledProviders.firstOrNull { it.id == pickProviderId }
        ?: enabledProviders.firstOrNull()

    var liveModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetching by remember { mutableStateOf(false) }
    var fetchErr by remember { mutableStateOf<String?>(null) }
    var modelQuery by remember { mutableStateOf("") }
    var selectedToAdd by remember { mutableStateOf<Set<String>>(emptySet()) }
    var manualModel by remember { mutableStateOf("") }

    // Clear live list when provider changes
    LaunchedEffect(pickProviderId) {
        liveModels = emptyList()
        selectedToAdd = emptySet()
        fetchErr = null
        modelQuery = ""
    }

    val configModels = pickProvider?.models?.map { it.id } ?: emptyList()
    val allModels = remember(configModels, liveModels) { (configModels + liveModels).distinct() }
    val filteredModels = remember(allModels, modelQuery) {
        val q = modelQuery.trim().lowercase()
        if (q.isEmpty()) allModels else allModels.filter { it.lowercase().contains(q) }
    }

    fun doFetchModels() {
        val p = pickProvider ?: return
        val key = p.keys.firstOrNull { it.isNotBlank() } ?: ""
        if (p.baseUrl.isBlank()) {
            fetchErr = "Provider has no base URL"
            return
        }
        fetching = true
        fetchErr = null
        scope.launch {
            val res = withContext(Dispatchers.IO) {
                ApiClient.fetchModels(p.baseUrl.trim(), key)
            }
            fetching = false
            res.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) fetchErr = "Empty model list — use manual add"
                    else {
                        liveModels = list
                        fetchErr = null
                    }
                },
                onFailure = { e -> fetchErr = "Fetch failed: ${e.message?.take(90)}" }
            )
        }
    }

    fun addModels(ids: List<String>) {
        val p = pickProvider ?: return
        val pName = p.name.ifBlank { p.id }
        val existing = members.map { it.providerId to it.model }.toSet()
        val extra = ids.map { it.trim() }.filter { it.isNotBlank() }
            .filter { (p.id to it) !in existing }
            .map { mid ->
                com.ahamai.app.data.ComboRouter.Member(
                    providerId = p.id,
                    model = mid,
                    label = "$pName · $mid"
                )
            }
        if (extra.isNotEmpty()) {
            members = members + extra
            selectedToAdd = emptySet()
            manualModel = ""
        }
    }

    fun snapshot(): com.ahamai.app.data.ComboRouter.Combo =
        combo.copy(
            id = editId.trim().ifBlank { combo.id },
            name = editName.trim().ifBlank { editId },
            note = editNote,
            strategy = strategy,
            chatMode = chatMode,
            agentMode = agentMode,
            enabled = enabled,
            members = members,
            plans = plans
        )

    fun commit() {
        onUpdate(snapshot())
    }

    AdminCard(c) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(c.accent.copy(0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.Layers, null, tint = c.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(combo.name.ifBlank { combo.id }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = InterFamily)
                Text(
                    "${combo.id} · ${members.size} member${if (members.size == 1) "" else "s"} · ${strategy.wire()}",
                    fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily, maxLines = 2
                )
            }
            TickToggle(c, enabled) {
                    enabled = it
                    onUpdate(combo.copy(enabled = it, members = members))
                }
        }

        if (!expanded && members.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            members.take(5).forEachIndexed { i, m ->
                Text(
                    "${i + 1}. ${m.providerId} → ${m.model}",
                    fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = c.muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (members.size > 5) {
                Text("+${members.size - 5} more · tap to edit", fontSize = 11.sp, color = c.muted)
            }
        }

        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column {
                Spacer(Modifier.height(14.dp))
                Field(c, "Display name", editName) { editName = it }
                Spacer(Modifier.height(8.dp))
                Field(c, "Model id (chat / agent pickers)", editId) {
                    editId = it.trim().filter { ch ->
                        ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '/' || ch == '.'
                    }
                }
                Spacer(Modifier.height(8.dp))
                Field(c, "Note (optional)", editNote) { editNote = it }

                Spacer(Modifier.height(12.dp))
                Text("STRATEGY", fontFamily = OswaldFamily, fontSize = 11.sp, letterSpacing = 1.sp, color = c.muted)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(
                        com.ahamai.app.data.ComboRouter.Strategy.FALLBACK to "Fallback",
                        com.ahamai.app.data.ComboRouter.Strategy.STICKY to "Sticky",
                        com.ahamai.app.data.ComboRouter.Strategy.ROUND_ROBIN to "Round-robin"
                    ).forEach { (s, label) ->
                        val on = strategy == s
                        Text(
                            label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (on) c.accentInk else c.ink, fontFamily = InterFamily,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (on) c.accent else c.surface2)
                                .clickable { strategy = s }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
                Text(
                    when (strategy) {
                        com.ahamai.app.data.ComboRouter.Strategy.FALLBACK -> "Always start at #1, then #2…"
                        com.ahamai.app.data.ComboRouter.Strategy.STICKY -> "Stay on last OK member until it fails"
                        com.ahamai.app.data.ComboRouter.Strategy.ROUND_ROBIN -> "Rotate start after each success"
                    },
                    fontSize = 11.sp, color = c.muted, fontFamily = InterFamily, modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { chatMode = !chatMode }) {
                        Text(if (chatMode) "✓" else "○", color = if (chatMode) c.accent else c.muted)
                        Spacer(Modifier.width(6.dp))
                        Text("Chat picker", fontSize = 12.sp, color = c.ink, fontFamily = InterFamily)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { agentMode = !agentMode }) {
                        Text(if (agentMode) "✓" else "○", color = if (agentMode) c.accent else c.muted)
                        Spacer(Modifier.width(6.dp))
                        Text("Agent picker", fontSize = 12.sp, color = c.ink, fontFamily = InterFamily)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Visible on plans", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
                Spacer(Modifier.height(6.dp))
                PlanChipsRow(c, plans) { plans = it }

                Spacer(Modifier.height(14.dp))
                Text("FALLBACK CHAIN", fontFamily = OswaldFamily, fontSize = 11.sp, letterSpacing = 1.sp, color = c.muted)
                Spacer(Modifier.height(8.dp))
                if (members.isEmpty()) {
                    Text("Empty chain — add models from a provider below.", fontSize = 12.sp, color = c.warn, fontFamily = InterFamily)
                    Spacer(Modifier.height(8.dp))
                }
                members.forEachIndexed { mi, m ->
                    val pName = providers.firstOrNull { it.id == m.providerId }?.name ?: m.providerId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.surface2)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(c.accent.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${mi + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = c.accent)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.model, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.ink, fontFamily = JetBrainsMonoFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(pName, fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
                        }
                        Text("↑", color = c.muted, fontSize = 16.sp, modifier = Modifier.clickable {
                            if (mi > 0) {
                                val l = members.toMutableList()
                                val t = l[mi]; l[mi] = l[mi - 1]; l[mi - 1] = t
                                members = l
                            }
                        }.padding(6.dp))
                        Text("↓", color = c.muted, fontSize = 16.sp, modifier = Modifier.clickable {
                            if (mi < members.lastIndex) {
                                val l = members.toMutableList()
                                val t = l[mi]; l[mi] = l[mi + 1]; l[mi + 1] = t
                                members = l
                            }
                        }.padding(6.dp))
                        Text("✕", color = c.danger, modifier = Modifier.clickable {
                            members = members.filterIndexed { i, _ -> i != mi }
                        }.padding(6.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(12.dp))
                Text("ADD FROM PROVIDER", fontFamily = OswaldFamily, fontSize = 11.sp, letterSpacing = 1.sp, color = c.muted)
                Spacer(Modifier.height(4.dp))
                Text("Pick provider → Fetch models / multi-select / or type id", fontSize = 11.5.sp, color = c.muted, fontFamily = InterFamily)
                Spacer(Modifier.height(10.dp))

                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    enabledProviders.forEach { p ->
                        val on = pickProviderId == p.id
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (on) c.accent.copy(0.12f) else c.surface2)
                                .border(1.dp, if (on) c.accent else Color.Transparent, RoundedCornerShape(10.dp))
                                .clickable { pickProviderId = p.id }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SiteFavicon(p.baseUrl, 14)
                            Spacer(Modifier.width(6.dp))
                            Column {
                                Text(
                                    p.name.ifBlank { p.id },
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (on) c.accent else c.ink,
                                    fontFamily = InterFamily,
                                    maxLines = 1
                                )
                                Text(
                                    "${p.models.size} models",
                                    fontSize = 10.sp,
                                    color = c.muted,
                                    fontFamily = InterFamily
                                )
                            }
                        }
                    }
                }

                if (pickProvider != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (fetching) "Fetching models…" else "↻ Fetch models from API",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (fetching) c.muted else c.accent, fontFamily = InterFamily,
                            modifier = Modifier.clickable(enabled = !fetching) { doFetchModels() }
                        )
                    }
                    Text(
                        pickProvider.baseUrl,
                        fontSize = 10.sp, color = c.muted, fontFamily = JetBrainsMonoFamily, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    fetchErr?.let {
                        Text(it, fontSize = 11.sp, color = c.danger, fontFamily = InterFamily, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (liveModels.isNotEmpty() || configModels.isNotEmpty()) {
                        Text(
                            "Showing ${(filteredModels.size)} · config ${configModels.size} · live ${liveModels.size}",
                            fontSize = 11.sp, color = c.ok, fontFamily = InterFamily, modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (allModels.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Field(c, "Search / filter models", modelQuery) { modelQuery = it }
                        Spacer(Modifier.height(8.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.surface2)
                                .verticalScroll(rememberScrollState())
                                .padding(6.dp)
                        ) {
                            filteredModels.take(250).forEach { mid ->
                                val on = mid in selectedToAdd
                                val already = members.any { it.providerId == pickProvider.id && it.model == mid }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                already -> c.ok.copy(0.12f)
                                                on -> c.accent.copy(0.16f)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .clickable(enabled = !already) {
                                            selectedToAdd = if (on) selectedToAdd - mid else selectedToAdd + mid
                                        }
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        when {
                                            already -> "✓ in chain"
                                            on -> "●"
                                            else -> "○"
                                        },
                                        fontSize = 11.sp,
                                        color = when {
                                            already -> c.ok
                                            on -> c.accent
                                            else -> c.muted
                                        },
                                        fontFamily = InterFamily,
                                        modifier = Modifier.width(64.dp)
                                    )
                                    Text(
                                        mid, fontSize = 12.sp, fontFamily = JetBrainsMonoFamily,
                                        color = if (already) c.muted else c.ink,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            if (filteredModels.size > 250) {
                                Text("…${filteredModels.size - 250} more — use search", fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(8.dp))
                            }
                        }
                        if (selectedToAdd.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Add ${selectedToAdd.size} selected → chain",
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accentInk, fontFamily = InterFamily,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.accent)
                                    .clickable { addModels(selectedToAdd.toList()) }
                                    .padding(vertical = 11.dp)
                            )
                        }
                    } else {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "No model list yet for this provider. Tap Fetch models, or type a model id manually.",
                            fontSize = 12.sp, color = c.muted, fontFamily = InterFamily
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text("MANUAL MODEL ID", fontFamily = OswaldFamily, fontSize = 11.sp, letterSpacing = 1.sp, color = c.muted)
                    Spacer(Modifier.height(6.dp))
                    Field(c, "Exact model id for this provider", manualModel) { manualModel = it }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "+ Add manual model to chain",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent, fontFamily = InterFamily,
                        modifier = Modifier.clickable {
                            if (manualModel.isNotBlank()) addModels(listOf(manualModel.trim()))
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Delete combo", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.danger, fontFamily = InterFamily, modifier = Modifier.clickable(onClick = onDelete))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Text(
                            if (testing) "Testing…" else "Test chain",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (testing) c.muted else c.ink, fontFamily = InterFamily,
                            modifier = Modifier.clickable(enabled = !testing) {
                                val snap = snapshot()
                                onUpdate(snap)
                                onTest(snap)
                            }
                        )
                        Text(
                            "Save combo",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.accent, fontFamily = InterFamily,
                            modifier = Modifier.clickable { commit() }
                        )
                    }
                }
            }
        }
    }
}

// ── Network / Proxy pools (compact) ──────────────────────────────────────────

@Composable
private fun NetworkPane(
    c: AdminColors,
    cfg: com.ahamai.app.data.ProxyManager.Config,
    saveMsg: String?,
    testMsg: String?,
    testing: Boolean,
    onSave: (com.ahamai.app.data.ProxyManager.Config) -> Unit,
    onTest: (com.ahamai.app.data.ProxyManager.ProxyEntry?) -> Unit
) {
    val scope = rememberCoroutineScope()
    var enabled by remember(cfg) { mutableStateOf(cfg.enabled) }
    var rotate by remember(cfg) { mutableStateOf(cfg.rotateOnFail) }
    var activeId by remember(cfg) { mutableStateOf(cfg.activeId.ifBlank { "direct" }) }
    var entries by remember(cfg) { mutableStateOf(cfg.entries) }
    var loadingFree by remember { mutableStateOf(false) }
    var freeMsg by remember { mutableStateOf<String?>(null) }

    // Compact add form
    var showAdd by remember { mutableStateOf(false) }
    var newHost by remember { mutableStateOf("") }
    var newPort by remember { mutableStateOf("8080") }
    var newName by remember { mutableStateOf("") }
    var newTypeHttp by remember { mutableStateOf(true) }

    // Inline edit for selected non-direct entry
    val active = entries.firstOrNull { it.id == activeId } ?: entries.firstOrNull()
    var editHost by remember(activeId, active?.host) { mutableStateOf(active?.host.orEmpty()) }
    var editPort by remember(activeId, active?.port) {
        mutableStateOf(active?.port?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var editUser by remember(activeId, active?.username) { mutableStateOf(active?.username.orEmpty()) }
    var editPass by remember(activeId, active?.password) { mutableStateOf(active?.password.orEmpty()) }

    fun commitSave() {
        // Apply inline edits to active entry before save
        val list = entries.toMutableList()
        val i = list.indexOfFirst { it.id == activeId }
        if (i >= 0 && list[i].type != com.ahamai.app.data.ProxyManager.ProxyType.NONE) {
            list[i] = list[i].copy(
                host = editHost.trim(),
                port = editPort.toIntOrNull() ?: list[i].port,
                username = editUser,
                password = editPass,
                enabled = true
            )
        }
        entries = list
        onSave(
            com.ahamai.app.data.ProxyManager.Config(
                enabled = enabled,
                activeId = activeId,
                rotateOnFail = rotate,
                entries = list
            )
        )
    }

    SectionTitle("Network", "Proxy for AI traffic · free pool or your own", c)

    // Master toggles + actions
    AdminCard(c) {
        SwitchRow(c, "Use proxy", "Route chat / agent through active proxy", enabled) { enabled = it }
        Spacer(Modifier.height(6.dp))
        SwitchRow(c, "Rotate on fail", "Try next proxy if connection fails", rotate) { rotate = it }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallBtn(c, if (testing) "Testing…" else "Test", accent = false, enabled = !testing) {
                commitSave()
                val e = entries.firstOrNull { it.id == activeId }
                onTest(e)
            }
            SmallBtn(c, "Save", accent = true) { commitSave() }
            SmallBtn(c, if (loadingFree) "Loading…" else "Load free", enabled = !loadingFree) {
                loadingFree = true
                freeMsg = null
                scope.launch {
                    val found = withContext(Dispatchers.IO) {
                        com.ahamai.app.data.ProxyManager.fetchPublicProxies(8)
                    }
                    loadingFree = false
                    if (found.isEmpty()) {
                        freeMsg = "No free proxies reachable right now — try later or add custom"
                    } else {
                        // Merge: keep direct/custom templates, drop old free_*, add new
                        val keep = entries.filter { !it.id.startsWith("free_") }
                        entries = keep + found
                        activeId = found.first().id
                        editHost = found.first().host
                        editPort = found.first().port.toString()
                        freeMsg = "Added ${found.size} free HTTP proxies · pick one & Test"
                    }
                }
            }
        }
        freeMsg?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
        }
        saveMsg?.let {
            Spacer(Modifier.height(4.dp))
            Text(if (it == "OK") "Saved" else it, fontSize = 11.sp, color = if (it == "OK") c.ok else c.danger)
        }
        testMsg?.let {
            Spacer(Modifier.height(4.dp))
            Text(it.take(120), fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = if (it.startsWith("OK")) c.ok else c.warn, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }

    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("POOL", fontFamily = OswaldFamily, fontSize = 10.sp, letterSpacing = 1.sp, color = c.muted, modifier = Modifier.weight(1f))
        Text(
            if (showAdd) "Cancel" else "+ Custom",
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.accent, fontFamily = InterFamily,
            modifier = Modifier.clickable { showAdd = !showAdd }
        )
    }
    Spacer(Modifier.height(6.dp))

    // Compact list
    AdminCard(c) {
        if (entries.isEmpty()) {
            Text("No proxies", fontSize = 12.sp, color = c.muted)
        } else {
            entries.forEachIndexed { idx, e ->
                val selected = activeId == e.id
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) c.accent.copy(0.10f) else Color.Transparent)
                        .clickable {
                            activeId = e.id
                            editHost = e.host
                            editPort = if (e.port > 0) e.port.toString() else ""
                            editUser = e.username
                            editPass = e.password
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, if (selected) c.accent else c.line, CircleShape)
                            .background(if (selected) c.accent else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            e.name.ifBlank { e.id },
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = c.ink,
                            fontFamily = InterFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            when {
                                e.type == com.ahamai.app.data.ProxyManager.ProxyType.NONE -> "no proxy"
                                e.host.isBlank() -> "${e.type.wire()} · set host"
                                else -> "${e.type.wire()}://${e.host}:${e.port}"
                            },
                            fontSize = 10.5.sp,
                            color = c.muted,
                            fontFamily = JetBrainsMonoFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!e.builtin && e.id.startsWith("free_") || !e.builtin) {
                        Text(
                            "✕",
                            fontSize = 12.sp,
                            color = c.danger,
                            modifier = Modifier
                                .clickable {
                                    entries = entries.filterIndexed { i, _ -> i != idx }
                                    if (activeId == e.id) {
                                        activeId = "direct"
                                        editHost = ""; editPort = ""
                                    }
                                }
                                .padding(6.dp)
                        )
                    }
                }
                if (idx < entries.lastIndex) {
                    HorizontalDivider(color = c.line.copy(0.6f), thickness = 0.5.dp)
                }
            }
        }
    }

    // Edit active proxy (if not direct)
    val sel = entries.firstOrNull { it.id == activeId }
    if (sel != null && sel.type != com.ahamai.app.data.ProxyManager.ProxyType.NONE) {
        Spacer(Modifier.height(8.dp))
        AdminCard(c) {
            Text("Active proxy", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
            Spacer(Modifier.height(8.dp))
            Field(c, "Host", editHost, placeholder = "1.2.3.4 or proxy.example.com") { editHost = it.trim() }
            Spacer(Modifier.height(8.dp))
            Field(c, "Port", editPort, placeholder = "8080") { editPort = it.filter { ch -> ch.isDigit() }.take(5) }
            Spacer(Modifier.height(8.dp))
            Field(c, "Username (optional)", editUser, placeholder = "optional") { editUser = it }
            Spacer(Modifier.height(8.dp))
            Field(c, "Password (optional)", editPass, placeholder = "optional") { editPass = it }
        }
    }

    if (showAdd) {
        Spacer(Modifier.height(8.dp))
        AdminCard(c) {
            Text("Add custom", fontSize = 11.sp, color = c.muted, fontFamily = InterFamily)
            Spacer(Modifier.height(8.dp))
            Field(c, "Name", newName, placeholder = "My VPS") { newName = it }
            Spacer(Modifier.height(8.dp))
            Field(c, "Host", newHost, placeholder = "proxy.example.com") { newHost = it.trim() }
            Spacer(Modifier.height(8.dp))
            Field(c, "Port", newPort, placeholder = "8080") { newPort = it.filter { ch -> ch.isDigit() }.take(5) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(true to "HTTP", false to "SOCKS5").forEach { (http, label) ->
                    val on = newTypeHttp == http
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (on) Color.White else c.ink,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) c.accent else c.surface2)
                            .clickable { newTypeHttp = http }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                SmallBtn(c, "Add", accent = true) {
                    if (newHost.isBlank()) return@SmallBtn
                    val id = "custom_${System.currentTimeMillis() % 100000}"
                    val e = com.ahamai.app.data.ProxyManager.ProxyEntry(
                        id = id,
                        name = newName.ifBlank { newHost },
                        type = if (newTypeHttp) com.ahamai.app.data.ProxyManager.ProxyType.HTTP
                        else com.ahamai.app.data.ProxyManager.ProxyType.SOCKS,
                        host = newHost.trim(),
                        port = newPort.toIntOrNull() ?: 8080,
                        enabled = true,
                        builtin = false
                    )
                    entries = entries + e
                    activeId = id
                    editHost = e.host
                    editPort = e.port.toString()
                    showAdd = false
                    newHost = ""; newName = ""; newPort = "8080"
                }
            }
        }
    }
    Spacer(Modifier.height(24.dp))
}
