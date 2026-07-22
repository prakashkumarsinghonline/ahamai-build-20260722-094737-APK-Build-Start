package com.ahamai.app

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import com.ahamai.app.ui.flow.FlowMotion
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.WindowCompat
import androidx.lifecycle.ProcessLifecycleOwner
import com.ahamai.app.data.ApiConfig
import com.ahamai.app.data.AuthManager
import com.ahamai.app.data.ChatHistoryManager
import com.ahamai.app.data.ChatSession
import com.ahamai.app.data.PreferencesManager
import com.ahamai.app.data.ProjectManager
import com.ahamai.app.data.RemoteConfigManager
import com.ahamai.app.data.ThemeRefresh
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ahamai.app.screens.ApiKeyScreen
import com.ahamai.app.screens.SplashScreen
import com.ahamai.app.screens.AgentHomeScreen
// ChatScreen + ChatHistoryScreen — removed duplicate comment marker
import com.ahamai.app.screens.ChatHistoryScreen
import com.ahamai.app.screens.ChatScreen
import com.ahamai.app.screens.CodeAgentScreen
import com.ahamai.app.screens.ModelSelectionScreen
import com.ahamai.app.screens.OnboardingScreen
import com.ahamai.app.screens.ProfileScreen
import com.ahamai.app.screens.ProviderSelectionScreen
import com.ahamai.app.screens.SpecBuilderScreen
import com.ahamai.app.screens.AdminScreen
import com.ahamai.app.screens.ConnectorsScreen
import com.ahamai.app.screens.SkillsScreen
import com.ahamai.app.service.BackgroundTaskManager
import com.ahamai.app.ui.theme.HelloKotlinTheme

data class ProviderItem(
    val name: String,
    val description: String,
    val domain: String? = null,
    val defaultBaseUrl: String = "",
    val isCustom: Boolean = false
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // No Android system splash — we use only the custom Compose SplashScreen
        // with logo + "Bihar, India" watermark.
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // ── Critical path only: paint first frame ASAP ─────────────────────────
        // FirebaseApp is needed before AuthManager.isSignedIn() in composition.
        // Everything else (RC fetch, skills, connectors, sounds, cloud warm) runs
        // AFTER the first frame so cold start stays butter-smooth.
        runCatching {
            if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                com.google.firebase.FirebaseApp.initializeApp(applicationContext)
            }
        }

        // AdMob must start on the MAIN thread BEFORE any screen can load ads.
        // Pass the Activity (not only applicationContext) so bootstrap can resolve app id
        // and later full-screen ads can find an Activity from composition contexts.
        runCatching { com.ahamai.app.ui.components.AdMobBootstrap.start(this) }

        val prefs = PreferencesManager(applicationContext)
        // Sync theme before first composition — avoids a light/dark flash after splash.
        val initialForceDark: Boolean? = when (prefs.getThemeMode()) {
            "dark" -> true
            "light" -> false
            else -> null
        }

        setContent {
            // Reactive theme — re-reads whenever ThemeRefresh.signal bumps.
            // ProfileScreen bumps it when user picks a theme option.
            var forceDarkMode by remember { mutableStateOf(initialForceDark) }

            // Subsequent reads when ThemeRefresh bumps (profile appearance change)
            val themeTick by ThemeRefresh.signalFlow.collectAsState()
            LaunchedEffect(themeTick) {
                if (themeTick == 0) return@LaunchedEffect
                val mode = prefs.getThemeMode()
                forceDarkMode = when (mode) {
                    "dark" -> true
                    "light" -> false
                    else -> null // system
                }
            }

            HelloKotlinTheme(forceDarkMode = forceDarkMode) {
                // No Scaffold insets padding — screens own their system bars.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }

        // ── Post-frame warmups (never block first paint) ───────────────────────
        window.decorView.post {
            val lifecycleObserver = BackgroundTaskManager.createLifecycleObserver(applicationContext)
            ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)

            com.ahamai.app.AppScope.scope.launch(Dispatchers.IO) {
                // Mobile Ads is initialized on the main thread in onCreate (AdMobBootstrap).
                // Do NOT re-init here — racing loads against a late background init caused
                // "test ads enabled but never visible".

                // Local / fast managers first
                runCatching { com.ahamai.app.data.RemoteConfigManager.init(applicationContext) }
                // Restore GitHub token (fixed owner-stamp wipe) + scrub leftover public build repos
                runCatching {
                    if (com.ahamai.app.data.AuthManager.isSignedIn()) {
                        com.ahamai.app.data.AuthManager.restoreGithubToken(applicationContext)
                    }
                    val ghToken = com.ahamai.app.data.PreferencesManager(applicationContext).getGithubToken()
                    if (ghToken.isNotBlank()) {
                        com.ahamai.app.data.GitHubClient.cleanupOrphanTempBuildRepos(ghToken)
                    }
                }
                // Bundled agent prompt (GH_*, RUN_APP, artifacts) when Firestore admin prompt is empty
                runCatching { com.ahamai.app.data.CodeAgent.warmAssetPrompt(applicationContext) }
                runCatching { com.ahamai.app.data.SkillManager.init(applicationContext) }
                runCatching { com.ahamai.app.data.ConnectorsManager.init(applicationContext) }
                runCatching { com.ahamai.app.data.VercelMCPManager.init(applicationContext) }
                runCatching { com.ahamai.app.data.SoundEffects.init(applicationContext) }
                runCatching { com.ahamai.app.data.SkillManager.pullFromCloudAsync() }

                // Housekeeping + optional cloud restore (can take a while — never on main)
                runCatching {
                    com.ahamai.app.data.ProjectManager.cleanupLegacyWorkspaceDownloads(applicationContext)
                }
                // Warm local history cache first so Chat History can paint immediately.
                runCatching { com.ahamai.app.data.ChatHistoryManager.loadSessions(applicationContext) }
                com.ahamai.app.data.HistoryRefresh.bump()

                if (com.ahamai.app.data.AuthManager.isSignedIn()) {
                    // Cold-start cloud restore (was only on AuthScreen before → empty history/DP
                    // until user opened Profile and came back).
                    runCatching { com.ahamai.app.data.AuthManager.restoreHistory(applicationContext) }
                    runCatching { com.ahamai.app.data.AuthManager.restoreWorkspaces(applicationContext) }
                    runCatching {
                        val p = com.ahamai.app.data.AuthManager.loadProfile()
                        if (p.avatar.isNotBlank()) {
                            PreferencesManager(applicationContext).saveAvatar(p.avatar)
                        }
                    }
                    runCatching { com.ahamai.app.data.AuthManager.backupWorkspaces(applicationContext) }
                    // Notify UI: history list + avatar prefs are ready
                    com.ahamai.app.data.HistoryRefresh.bump()
                }

                // Heavy sandbox warm — lowest priority
                runCatching {
                    com.ahamai.app.data.CloudBrowser.warmUp(applicationContext)
                }
            }
        }
    }

    /** Theme changes (dark/light) handled internally by Compose — prevents Activity restart. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Compose recomposes with isSystemInDarkTheme() reflecting the new config automatically.
        // No manual theme override needed — we just suppress the default Activity recreation.
    }
}

enum class Screen {
    SPLASH,
    ONBOARDING,
    AUTH,
    PROVIDER_SELECTION,
    API_KEY,
    MODEL_SELECTION,
    CHAT_HISTORY,
    CHAT,
    PROFILE,
    AGENT_HOME,
    CODE_AGENT,
    SPEC_BUILDER,
    ADMIN,
    CONNECTORS,
    SKILLS,
    USAGE,
    PRICING,
    DATA_CONTROLS
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val preferencesManager = remember { PreferencesManager(context) }
    val scope = rememberCoroutineScope()
    // ── Kill switch + maintenance (reactive — updates as soon as Firestore responds) ──
    val killEnabled      by RemoteConfigManager.killFlow.collectAsState()
    val maintenanceText  by RemoteConfigManager.maintenanceFlow.collectAsState()
    val maintenanceHtml  by RemoteConfigManager.maintenanceHtmlFlow.collectAsState()
    val useCustomHtml    by RemoteConfigManager.useCustomHtmlFlow.collectAsState()
    var isAdminUser      by remember { mutableStateOf(false) }
    var banError         by remember { mutableStateOf<String?>(null) }

    // Your branded splash first (icon + watermark), then onboarding / auth / home.
    val nextAfterSplash = remember {
        if (!preferencesManager.isOnboardingCompleted()) Screen.ONBOARDING
        else if (AuthManager.isSignedIn()) Screen.CHAT_HISTORY
        else Screen.AUTH
    }
    var currentScreen by remember { mutableStateOf(Screen.SPLASH) }
    // Long enough for icon settle + watermark fade; short enough to feel snappy.
    LaunchedEffect(Unit) {
        delay(720)
        currentScreen = nextAfterSplash
    }
    var selectedProvider by remember { mutableStateOf<ProviderItem?>(null) }
    var currentBaseUrl by remember { mutableStateOf(preferencesManager.getBaseUrl()) }
    var currentApiKey by remember { mutableStateOf(preferencesManager.getApiKey()) }
    // Signed-in user profile (name / email / base64 avatar) loaded from Firebase.
    var profileName by remember { mutableStateOf(AuthManager.displayName()) }
    var profileEmail by remember { mutableStateOf(AuthManager.email()) }
    var profileAvatar by remember { mutableStateOf(preferencesManager.getAvatar()) }
    var currentModel by remember {
        mutableStateOf(
            if (preferencesManager.isCustomEndpointEnabled()) preferencesManager.getModel()
            else preferencesManager.getChatModel()
        )
    }
    var currentVoice by remember { mutableStateOf(preferencesManager.getVoice()) }
    var providerDomain by remember { mutableStateOf<String?>(null) }
    // The session to load into ChatScreen (null = brand new chat)
    var activeSession by remember { mutableStateOf<ChatSession?>(null) }
    // Used to force ChatScreen to reset for a new chat
    var chatKey by remember { mutableStateOf(0) }
    // Code project state
    var codeProjectDir by remember { mutableStateOf("") }
    var codeProjectName by remember { mutableStateOf("") }
    var codeInitialPrompt by remember { mutableStateOf<String?>(null) }
    var codeReturnScreen by remember { mutableStateOf(Screen.AGENT_HOME) }
    // True only when the AI (from chat) triggers the switch to the agent — drives a
    // distinct "page-turn" transition + handoff sound. Manual toggles stay seamless.
    var agentSwitchByAi by remember { mutableStateOf(false) }

    // Resolve the active endpoints from Remote Config (or the user's custom endpoint when the
    // Settings toggle is ON). Reading ApiConfig.version makes this re-resolve once config loads.
    val cfgVersion = ApiConfig.version
    val customEnabled = preferencesManager.isCustomEndpointEnabled()
    val effectiveChatModel = currentModel.ifBlank { ApiConfig.chat(context).model }
    // Route by the SELECTED model so a model from provider B is sent to provider B's URL+key
    // (not the default provider). Fixes "some models don't respond" + key rotation per provider.
    val chatCfg = ApiConfig.resolveForModel(context, effectiveChatModel, agent = false)
    val agentCfg = ApiConfig.agent(context)
    val visionCfg = ApiConfig.vision(context)
    val onChatModelChange: (String) -> Unit = { model ->
        currentModel = model
        if (customEnabled) preferencesManager.saveModel(model) else preferencesManager.saveChatModel(model)
    }
    // Once config is available, fill the default chat model (nothing is hardcoded in the app).
    LaunchedEffect(cfgVersion, customEnabled) {
        if (currentModel.isBlank()) currentModel = ApiConfig.chat(context).model
    }
    // Auto key-health-check: ping all provider keys in the background and blacklist dead ones,
    // so the admin never has to run it manually. Throttled internally to once / 10 min.
    LaunchedEffect(cfgVersion) {
        if (ApiConfig.loaded) runCatching { ApiConfig.autoHealthCheck(context) }
    }
    // ── After sign-in: load Firestore config, check admin + ban ──────────────
    LaunchedEffect(currentScreen) {
        if (currentScreen != Screen.AUTH && AuthManager.isSignedIn()) {
            RemoteConfigManager.loadFirestoreConfig()
            isAdminUser = AuthManager.isAdmin()
            if (AuthManager.checkBanned()) {
                banError = "Your account has been suspended. Please contact support at support@ahamai.com for assistance."
                // Don't sign out - just show banned message
            }
        }
    }

    // Observe history/profile restore signal so DP + list update without navigating away
    val historyRefreshTick by com.ahamai.app.data.HistoryRefresh.signalFlow.collectAsState()

    // Warm history cache ASAP so Chat History opens without JSON-parse lag
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { ChatHistoryManager.loadSessions(context) }
        }
    }

    // Profile + avatar: local first, then cloud. Re-runs when HistoryRefresh bumps
    // (cold-start restore finished) so DP shows without visiting Profile first.
    LaunchedEffect(historyRefreshTick, AuthManager.isSignedIn()) {
        if (!AuthManager.isSignedIn()) return@LaunchedEffect
        // Instant: local cache / Firebase Auth name
        val localAvatar = preferencesManager.getAvatar()
        if (localAvatar.isNotBlank()) profileAvatar = localAvatar
        if (profileName.isBlank()) profileName = AuthManager.displayName()
        if (profileEmail.isBlank()) profileEmail = AuthManager.email()
        // Background: Firestore profile (avatar often lives here for email/password users)
        runCatching {
            val p = AuthManager.loadProfile()
            if (p.name.isNotBlank()) profileName = p.name
            if (p.email.isNotBlank()) profileEmail = p.email
            val local = preferencesManager.getAvatar()
            val best = p.avatar.ifBlank { local }
            if (best.isNotBlank()) {
                profileAvatar = best
                if (p.avatar.isNotBlank() && p.avatar != local) preferencesManager.saveAvatar(p.avatar)
            }
        }
    }
    // Re-sync avatar when landing on screens that show the DP
    LaunchedEffect(currentScreen, historyRefreshTick) {
        if (!AuthManager.isSignedIn()) return@LaunchedEffect
        if (currentScreen == Screen.PROFILE || currentScreen == Screen.CHAT_HISTORY ||
            currentScreen == Screen.CHAT || currentScreen == Screen.CODE_AGENT
        ) {
            val local = preferencesManager.getAvatar()
            if (local.isNotBlank() && local != profileAvatar) profileAvatar = local
            if (currentScreen == Screen.PROFILE || currentScreen == Screen.CHAT_HISTORY) {
                runCatching {
                    val p = AuthManager.loadProfile()
                    if (p.name.isNotBlank()) profileName = p.name
                    if (p.email.isNotBlank()) profileEmail = p.email
                    val best = p.avatar.ifBlank { local }
                    if (best.isNotBlank()) {
                        profileAvatar = best
                        if (p.avatar.isNotBlank() && p.avatar != local) preferencesManager.saveAvatar(p.avatar)
                    }
                }
            }
        }
    }

    // Keep status / nav bar icons readable; bars stay transparent so each screen's
    // full-bleed bg shows through (same colour as content — no mismatched strip).
    val isDarkTheme = isSystemInDarkTheme()
    val rootView = LocalView.current
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(isDarkTheme, currentScreen) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, rootView).apply {
            isAppearanceLightStatusBars = !isDarkTheme
            isAppearanceLightNavigationBars = !isDarkTheme
        }
        onDispose { }
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            // Splash (icon + watermark) → next: soft dissolve
            if (initialState == Screen.SPLASH || targetState == Screen.SPLASH) {
                return@AnimatedContent FlowMotion.splashExit()
            }
            // First-run funnel: onboarding → auth → home (elevated “rise” enter)
            val flow = setOf(Screen.ONBOARDING, Screen.AUTH, Screen.CHAT_HISTORY)
            if (initialState in flow && targetState in flow) {
                return@AnimatedContent FlowMotion.forward()
            }
            // History ↔ Chat: very fast crossfade (opening a chat must feel instant)
            if ((initialState == Screen.CHAT_HISTORY && targetState == Screen.CHAT) ||
                (initialState == Screen.CHAT && targetState == Screen.CHAT_HISTORY)
            ) {
                return@AnimatedContent FlowMotion.softCrossfade()
            }
            // AI handoff into agent — soft drift, not a full swipe
            if (targetState == Screen.CODE_AGENT && agentSwitchByAi) {
                return@AnimatedContent FlowMotion.agentHandoff()
            }
            // Chat ⇄ Agent (ModeToggle, back, swipe): iOS-smooth, no big jump
            val agentish = setOf(Screen.CODE_AGENT, Screen.AGENT_HOME, Screen.CHAT_HISTORY, Screen.CHAT)
            if (initialState in agentish && targetState in agentish &&
                (initialState == Screen.CODE_AGENT || targetState == Screen.CODE_AGENT ||
                    initialState == Screen.AGENT_HOME || targetState == Screen.AGENT_HOME)
            ) {
                return@AnimatedContent FlowMotion.modeSwitch()
            }
            // Default in-app switches
            FlowMotion.softCrossfade()
        },
        label = "screen"
    ) { screen ->
    when (screen) {
        Screen.SPLASH -> {
            SplashScreen()
        }
        Screen.ONBOARDING -> {
            OnboardingScreen(
                onComplete = {
                    currentScreen = if (AuthManager.isSignedIn()) Screen.CHAT_HISTORY else Screen.AUTH
                }
            )
        }
        Screen.AUTH -> {
            com.ahamai.app.screens.AuthScreen(
                onAuthed = {
                    profileName = AuthManager.displayName()
                    profileEmail = AuthManager.email()
                    profileAvatar = preferencesManager.getAvatar()
                    currentScreen = Screen.CHAT_HISTORY
                }
            )
        }
        Screen.PROVIDER_SELECTION -> {
            ProviderSelectionScreen(
                onProviderSelected = { provider ->
                    selectedProvider = provider
                    providerDomain = provider.domain
                    currentScreen = Screen.API_KEY
                }
            )
        }
        Screen.API_KEY -> {
            selectedProvider?.let { provider ->
                ApiKeyScreen(
                    provider = provider,
                    onBack = { currentScreen = Screen.PROVIDER_SELECTION },
                    onConnect = { baseUrl, apiKey ->
                        currentBaseUrl = baseUrl
                        currentApiKey = apiKey
                        preferencesManager.saveProvider(provider.name, baseUrl, apiKey)
                        preferencesManager.setCustomEndpointEnabled(true)
                        currentScreen = Screen.MODEL_SELECTION
                    }
                )
            }
        }
        Screen.MODEL_SELECTION -> {
            ModelSelectionScreen(
                baseUrl = currentBaseUrl,
                apiKey = currentApiKey,
                providerDomain = providerDomain,
                onBack = { currentScreen = if (preferencesManager.isConfigured()) Screen.CHAT_HISTORY else Screen.API_KEY },
                onModelSelected = { model ->
                    currentModel = model
                    preferencesManager.saveModel(model)
                    currentScreen = Screen.CHAT_HISTORY
                }
            )
        }
        Screen.CHAT_HISTORY -> {
            ChatHistoryScreen(
                modelName = effectiveChatModel,
                baseUrl = chatCfg.baseUrl,
                apiKey = chatCfg.apiKey,
                avatarBase64 = profileAvatar,
                onModelChange = onChatModelChange,
                onNewChat = {
                    activeSession = null
                    chatKey++
                    currentScreen = Screen.CHAT
                },
                onOpenChat = { session ->
                    // Set session first, then navigate — ChatScreen gets data on first frame
                    activeSession = session
                    chatKey++
                    currentScreen = Screen.CHAT
                },
                onProfile = {
                    // Refresh avatar from disk before opening profile (instant DP)
                    val local = preferencesManager.getAvatar()
                    if (local.isNotBlank()) profileAvatar = local
                    currentScreen = Screen.PROFILE
                },
                onNewWorkspace = {
                    // Agent mode → dedicated Agent home (workspace history lives there).
                    agentSwitchByAi = false
                    codeProjectDir = ""
                    codeProjectName = ""
                    codeInitialPrompt = null
                    codeReturnScreen = Screen.AGENT_HOME
                    currentScreen = Screen.AGENT_HOME
                },
                onOpenWorkspace = { dir, name ->
                    // Legacy path — open workspace run from Agent home.
                    agentSwitchByAi = false
                    codeProjectDir = dir
                    codeProjectName = name
                    codeInitialPrompt = null
                    codeReturnScreen = Screen.AGENT_HOME
                    currentScreen = Screen.CODE_AGENT
                }
            )
        }
        Screen.CHAT -> {
            key(chatKey) {
                ChatScreen(
                    modelName = effectiveChatModel,
                    baseUrl = chatCfg.baseUrl,
                    apiKey = chatCfg.apiKey,
                    visionBaseUrl = visionCfg?.baseUrl ?: "",
                    visionApiKey = visionCfg?.apiKey ?: "",
                    visionModel = visionCfg?.model ?: "",
                    selectedVoice = currentVoice,
                    initialSession = activeSession,
                    avatarBase64 = profileAvatar,
                    onModelChange = onChatModelChange,
                    onProfile = {
                        val local = preferencesManager.getAvatar()
                        if (local.isNotBlank()) profileAvatar = local
                        currentScreen = Screen.PROFILE
                    },
                    onBack = {
                        // Pull latest avatar when returning to history (profile may have updated it)
                        val local = preferencesManager.getAvatar()
                        if (local.isNotBlank()) profileAvatar = local
                        currentScreen = Screen.CHAT_HISTORY
                    },
                    onOpenProject = { uri ->
                        val dir = ProjectManager.extractZip(context, uri)
                        if (dir != null) {
                            codeProjectDir = dir
                            codeProjectName = dir.substringAfterLast('/')
                            codeInitialPrompt = null
                            codeReturnScreen = Screen.CHAT
                            currentScreen = Screen.CODE_AGENT
                        }
                    },
                    onSwitchToAgent = { task ->
                        // Chat decided this task needs the Agent — open a fresh workspace with it.
                        com.ahamai.app.screens.AgentRuntime.reset("workspace")
                        agentSwitchByAi = true
                        codeProjectDir = ""
                        codeProjectName = ""
                        codeInitialPrompt = task
                        codeReturnScreen = Screen.CHAT_HISTORY
                        currentScreen = Screen.CODE_AGENT
                    }
                )
            }
        }
        Screen.AGENT_HOME -> {
            AgentHomeScreen(
                modelName = currentModel,
                avatarBase64 = profileAvatar,
                onChatMode = { currentScreen = Screen.CHAT_HISTORY },
                onProfile = { currentScreen = Screen.PROFILE },
                onOpenProject = { dir, name, prompt ->
                    codeProjectDir = dir
                    codeProjectName = name
                    codeInitialPrompt = prompt
                    codeReturnScreen = Screen.AGENT_HOME
                    currentScreen = Screen.CODE_AGENT
                },
                onSpecBuilder = { currentScreen = Screen.SPEC_BUILDER },
                onOpenConnectors = { currentScreen = Screen.CONNECTORS }
            )
        }
        Screen.SPEC_BUILDER -> {
            SpecBuilderScreen(
                onBack = { currentScreen = Screen.CODE_AGENT },
                onSpecApproved = { spec ->
                    val dir = ProjectManager.createEmptyProject(context, "SpecProject")
                    codeProjectDir = dir
                    codeProjectName = "SpecProject"
                    codeInitialPrompt = spec
                    codeReturnScreen = Screen.CHAT_HISTORY
                    currentScreen = Screen.CODE_AGENT
                }
            )
        }
        Screen.CODE_AGENT -> {
            CodeAgentScreen(
                modelName = agentCfg.model,
                baseUrl = agentCfg.baseUrl,
                apiKey = agentCfg.apiKey,
                initialProjectDir = codeProjectDir.ifBlank { null },
                initialProjectName = codeProjectName.ifBlank { null },
                initialPrompt = codeInitialPrompt,
                avatarBase64 = profileAvatar,
                userName = profileName,
                // Return to Agent home (workspace history) unless we came from chat.
                onChatMode = {
                    currentScreen = when (codeReturnScreen) {
                        Screen.CHAT -> Screen.CHAT
                        Screen.CHAT_HISTORY -> Screen.CHAT_HISTORY
                        else -> Screen.AGENT_HOME
                    }
                },
                onProfile = { currentScreen = Screen.PROFILE },
                onSpecBuilder = { currentScreen = Screen.SPEC_BUILDER }
            )
        }
        Screen.PROFILE -> {
            ProfileScreen(
                providerName = if (customEnabled) preferencesManager.getProviderName().ifBlank { "Custom endpoint" } else chatCfg.providerId.ifBlank { "Default (cloud)" },
                baseUrl = if (customEnabled) currentBaseUrl else chatCfg.baseUrl,
                apiKey = if (customEnabled) currentApiKey else chatCfg.apiKey,
                model = effectiveChatModel,
                currentVoice = currentVoice,
                customEnabled = customEnabled,
                onToggleCustom = { enabled ->
                    preferencesManager.setCustomEndpointEnabled(enabled)
                    currentModel = if (enabled) preferencesManager.getModel() else preferencesManager.getChatModel()
                },
                userName = profileName,
                userEmail = profileEmail,
                avatarBase64 = profileAvatar,
                onSignOut = {
                    AuthManager.signOut(context)
                    preferencesManager.saveAvatar("")
                    profileName = ""; profileEmail = ""; profileAvatar = ""
                    currentScreen = Screen.AUTH
                },
                onBack = {
                    // Instant DP on history after leaving profile
                    val local = preferencesManager.getAvatar()
                    if (local.isNotBlank()) profileAvatar = local
                    if (profileName.isBlank()) profileName = AuthManager.displayName()
                    currentScreen = Screen.CHAT_HISTORY
                },
                onChangeProvider = {
                    currentScreen = Screen.PROVIDER_SELECTION
                },
                onVoiceChange = { voiceId ->
                    currentVoice = voiceId
                    preferencesManager.saveVoice(voiceId)
                },
                onClearChats = {
                    ChatHistoryManager.clearAll(context)
                },
                onAdmin = {
                    currentScreen = Screen.ADMIN
                },
                onUsage = { currentScreen = Screen.USAGE },
                onMCP = { currentScreen = Screen.CONNECTORS },
                onSkills = { currentScreen = Screen.SKILLS },
                onPlans = { currentScreen = Screen.PRICING },
                onDataControls = { currentScreen = Screen.DATA_CONTROLS }
            )
        }
        Screen.DATA_CONTROLS -> {
            com.ahamai.app.screens.DataControlsScreen(
                onBack = { currentScreen = Screen.PROFILE },
                onClearChats = { ChatHistoryManager.clearAll(context) }
            )
        }
        Screen.ADMIN -> {
            AdminScreen(
                onBack = { currentScreen = Screen.PROFILE }
            )
        }
        Screen.CONNECTORS -> {
            ConnectorsScreen(
                onBack = { currentScreen = Screen.PROFILE }
            )
        }
        Screen.SKILLS -> {
            SkillsScreen(
                onBack = { currentScreen = Screen.PROFILE },
                onSkillCreator = {
                    // Stay on Skills; user can create via + / Add Skill form.
                    // Skill Creator conversation is available from the agent + menu.
                }
            )
        }
        Screen.USAGE -> {
            com.ahamai.app.screens.UsageScreen(
                onBack = { currentScreen = Screen.PROFILE },
                onUpgrade = { currentScreen = Screen.PRICING }
            )
        }
        Screen.PRICING -> {
            com.ahamai.app.screens.PricingScreen(
                onBack = { currentScreen = Screen.PROFILE }
            )
        }
    }
    }

    // ── Full-screen maintenance — blocks non-admin users when enabled ─────────
    // Supports plain text OR custom HTML (admin Control → Custom HTML).
    // Banned users see their ban dialog instead.
    if (killEnabled && !isAdminUser && banError == null) {
        val isDarkMaint = isSystemInDarkTheme()
        val bg = if (isDarkMaint) Color(0xFF0C0C0E) else Color(0xFFF4F4F4)
        val ink = if (isDarkMaint) Color(0xFFF3F3F5) else Color(0xFF121214)
        val muted = if (isDarkMaint) Color(0xFF9A9AA3) else Color(0xFF6B6B76)
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false
            )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(bg)
            ) {
                if (useCustomHtml && maintenanceHtml.isNotBlank()) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webViewClient = WebViewClient()
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webView ->
                            val html = if (maintenanceHtml.contains("<html", ignoreCase = true) ||
                                maintenanceHtml.contains("<!DOCTYPE", ignoreCase = true)
                            ) {
                                maintenanceHtml
                            } else {
                                """
                                <!DOCTYPE html>
                                <html>
                                <head>
                                  <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"/>
                                  <style>
                                    html,body{margin:0;padding:0;min-height:100%;background:${if (isDarkMaint) "#0B0B0D" else "#F7F7F8"};color:${if (isDarkMaint) "#F3F3F5" else "#121214"};font-family:system-ui,-apple-system,sans-serif;}
                                    body{display:flex;align-items:center;justify-content:center;padding:24px;box-sizing:border-box;}
                                  </style>
                                </head>
                                <body>$maintenanceHtml</body>
                                </html>
                                """.trimIndent()
                            }
                            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                        }
                    )
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Under Maintenance", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = ink)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            maintenanceText.ifBlank { "We'll be back shortly. Thanks for your patience." },
                            fontSize = 15.sp,
                            color = muted,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }

    // ── Ban dialog — shows INSTEAD of maintenance for banned users ────────────────────────────────────
    banError?.let { msg ->
        Dialog(onDismissRequest = {}) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("🚫", fontSize = 32.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(12.dp))
                    Text("Account Suspended", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                    Text(msg, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { 
                            banError = null
                            AuthManager.signOut(context)
                            currentScreen = Screen.AUTH
                        }) { Text("Sign Out", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
