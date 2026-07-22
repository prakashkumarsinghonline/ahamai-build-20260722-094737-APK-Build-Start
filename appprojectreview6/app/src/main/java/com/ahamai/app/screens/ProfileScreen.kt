package com.ahamai.app.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.ahamai.app.ui.icons.Lucide
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.components.IosBottomSheet
import com.ahamai.app.ui.components.IosChromeIconButton
import com.ahamai.app.ui.components.IosDialog
import com.ahamai.app.ui.agent.AgentFeel
import com.ahamai.app.ui.chat.HapticOnPress
import com.ahamai.app.ui.agent.rememberAgentHaptics
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ahamai.app.ui.theme.UnicaOneRegular
import com.ahamai.app.data.ProjectManager
import com.ahamai.app.data.ChatHistoryManager
import com.ahamai.app.data.ThemeRefresh
import java.io.File

private fun accent(isDark: Boolean) = if (isDark) Color(0xFFFFFFFF) else com.ahamai.app.ui.theme.ChatPalette.Accent
private fun onBg(isDark: Boolean) = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1A1A1A)
private fun sub(isDark: Boolean) = if (isDark) Color(0xFF9A9AA2) else Color(0xFF888888)
// Soft hairline dividers — HTML #f0f0f0
private fun separator(isDark: Boolean) = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0F0F0)
// Page #f5f5f7 + white cards (Qoder settings look)
private fun groupedBg(isDark: Boolean) = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
private fun cellBg(isDark: Boolean) = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
private fun pillBg(isDark: Boolean) = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
private fun pillFg(isDark: Boolean) = if (isDark) Color(0xFF0C0C0E) else Color(0xFFFFFFFF)
private val iosRed = Color(0xFFFF3B30)
private val freeBadgeBg = Color(0xFFE8F5E9)
private val freeBadgeFg = Color(0xFF4CAF50)

private val sectionShape = RoundedCornerShape(14.dp)
private val cardShape = RoundedCornerShape(14.dp)

data class VoiceOption(
    val id: String,
    val name: String,
    val description: String,
    val testText: String = "Welcome to AhamAI"
)

val AVAILABLE_VOICES = listOf(
    VoiceOption("en-IN-PrabhatNeural", "Prabhat", "Indian English Male"),
    VoiceOption("en-IN-NeerjaNeural", "Neerja", "Indian English Female"),
    VoiceOption("en-US-AriaNeural", "Aria", "US Female"),
    VoiceOption("en-US-JennyNeural", "Jenny", "US Female, Friendly"),
    VoiceOption("en-US-GuyNeural", "Guy", "US Male, Casual"),
    VoiceOption("en-US-EmmaMultilingualNeural", "Emma", "US Female, Warm"),
    VoiceOption("en-US-AvaMultilingualNeural", "Ava", "US Female, Clear"),
    VoiceOption("en-US-AndrewMultilingualNeural", "Andrew", "US Male, Calm"),
    VoiceOption("en-US-BrianMultilingualNeural", "Brian", "US Male, Deep"),
    VoiceOption("en-GB-SoniaNeural", "Sonia", "UK Female"),
    VoiceOption("en-GB-RyanNeural", "Ryan", "UK Male"),
    VoiceOption("hi-IN-SwaraNeural", "Swara", "Hindi Female"),
    VoiceOption("hi-IN-MadhurNeural", "Madhur", "Hindi Male"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    providerName: String,
    baseUrl: String,
    apiKey: String,
    model: String,
    currentVoice: String,
    customEnabled: Boolean = false,
    onToggleCustom: (Boolean) -> Unit = {},
    userName: String = "",
    userEmail: String = "",
    avatarBase64: String = "",
    onSignOut: () -> Unit = {},
    onBack: () -> Unit,
    onChangeProvider: () -> Unit,
    onVoiceChange: (String) -> Unit,
    onClearChats: () -> Unit,
    onUsage: () -> Unit = {},
    onPlans: () -> Unit = {},
    onMCP: () -> Unit = {},
    onSkills: () -> Unit = {},
    onAdmin: () -> Unit = {},
    onDataControls: () -> Unit = {}
) {
    BackHandler(enabled = true) { onBack() }

    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showVoiceSheet by remember { mutableStateOf(false) }
    val voiceSheetState = rememberModalBottomSheetState()
    val prefsManager = remember { com.ahamai.app.data.PreferencesManager(context) }
    var currentThemeMode by remember { mutableStateOf(prefsManager.getThemeMode()) }
    var showAppearanceSheet by remember { mutableStateOf(false) }
    var customOn by remember { mutableStateOf(customEnabled) }
    var showCustomPromptDialog by remember { mutableStateOf(false) }
    var customPromptText by remember { mutableStateOf(context.getSharedPreferences("ahamai_prefs", android.content.Context.MODE_PRIVATE).getString("custom_prompt", "") ?: "") }
    var isAdmin by remember { mutableStateOf(false) }

    // ── Data control state ──
    var showDataControlSheet by remember { mutableStateOf(false) }
    var showClearChatsConfirm by remember { mutableStateOf(false) }
    var showClearAllDataConfirm by remember { mutableStateOf(false) }
    var showWorkspaceList by remember { mutableStateOf(false) }
    var workspaceInfos by remember { mutableStateOf<List<ProjectManager.ProjectInfo>>(emptyList()) }
    var chatCount by remember { mutableStateOf(0) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var feedbackText by remember { mutableStateOf("") }
    var feedbackImages by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val haptics = rememberAgentHaptics()
    val uriHandler = LocalUriHandler.current
    val feedbackImagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) {
            feedbackImages = (feedbackImages + uris).distinct().take(6)
        }
    }
    val appVersion = remember {
        runCatching {
            val pm = context.packageManager
            val p = if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
            }
            p.versionName ?: "1.0"
        }.getOrDefault("1.0")
    }

    // Load workspace + chat stats
    LaunchedEffect(refreshTrigger) {
        workspaceInfos = withContext(Dispatchers.IO) { ProjectManager.listProjects(context) }
        chatCount = withContext(Dispatchers.IO) { ChatHistoryManager.loadSessions(context).size }
    }

    LaunchedEffect(Unit) {
        isAdmin = com.ahamai.app.data.AuthManager.isAdmin()
    }

    val currentVoiceName = AVAILABLE_VOICES.find { it.id == currentVoice }?.name ?: "Prabhat"
    val accentC = accent(isDark)
    val bgC = groupedBg(isDark)
    val cellC = cellBg(isDark)
    val primary = onBg(isDark)
    val secondary = sub(isDark)
    val sepC = separator(isDark)

    // Total workspace files count
    val totalWsFiles = remember(workspaceInfos) { workspaceInfos.sumOf { it.fileCount } }

    val plan = remember(context) {
        com.ahamai.app.data.UsageTracker.currentPlan(context)
    }
    // Short plan name for Manus-style hero row ("Free" / plan name)
    val planShort = remember(plan) {
        if (plan.id == "free") "Free" else plan.name
    }
    val planLabel = remember(plan) {
        if (plan.id == "free") "Free plan" else plan.name
    }
    val displayName = userName.ifBlank { "AhamAI User" }
    val displayEmail = userEmail.ifBlank { "user@ahamai.com" }
    val themeLabel = remember(currentThemeMode) {
        when (currentThemeMode) {
            "light" -> "Light"
            "dark" -> "Dark"
            else -> "System"
        }
    }
    val initialLetter = remember(displayName) {
        displayName.trim().firstOrNull { it.isLetter() }?.uppercaseChar()?.toString() ?: "A"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgC)
    ) {
        // ── Header: circular back + centered Settings ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(44.dp)
        ) {
            IosChromeIconButton(
                isDark = isDark,
                onClick = { haptics.select(); onBack() },
                modifier = Modifier.align(Alignment.CenterStart),
                contentDescription = "Back"
            ) {
                Icon(
                    Lucide.ChevronRight, // rotated for back chevron look
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
            Text(
                "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif,
                color = primary,
                letterSpacing = (-0.3f).sp,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .navigationBarsPadding()
        ) {
            // ── Profile: DP + name + Free badge + email (flat, no card) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFE8E8E8)),
                    contentAlignment = Alignment.Center
                ) {
                    if (avatarBase64.isNotBlank()) {
                        val avatarBmp = remember(avatarBase64) {
                            com.ahamai.app.data.ImageUtils.decodeBase64(avatarBase64)
                        }
                        if (avatarBmp != null) {
                            Image(
                                bitmap = avatarBmp.asImageBitmap(),
                                contentDescription = "Profile",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(CircleShape)
                            )
                        } else {
                            Text(
                                initialLetter,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.SansSerif,
                                color = primary
                            )
                        }
                    } else {
                        Text(
                            initialLetter,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = primary
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            color = primary,
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isDark) Color(0xFF1B3A1F) else freeBadgeBg)
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                planShort,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFamily,
                                color = if (isDark) Color(0xFF81C784) else freeBadgeFg
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        displayEmail,
                        fontSize = 14.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        lineHeight = 20.sp,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }

            // ── Menu cards ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                // Usage (+ upgrade via plans)
                GroupedSection(cellC = cellC) {
                    SettingsRow(
                        icon = Lucide.Activity,
                        title = "Usage",
                        trailing = if (chatCount > 0) "$chatCount" else null,
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); onUsage() },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Diamond,
                        title = "Plans",
                        trailing = planShort,
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); onPlans() },
                        showDivider = false,
                        sepC = sepC
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Preferences group
                GroupedSection(cellC = cellC) {
                    SettingsRow(
                        icon = Lucide.Palette,
                        title = "Appearance",
                        trailing = themeLabel,
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); showAppearanceSheet = true },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.AudioLines,
                        title = "Voice",
                        trailing = currentVoiceName,
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); showVoiceSheet = true },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Wand,
                        title = "Customize",
                        trailing = if (customPromptText.isNotBlank()) "On" else null,
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); showCustomPromptDialog = true },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Sliders,
                        title = "Data controls",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); onDataControls() },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Plug,
                        title = "Connectors",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); onMCP() },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Skills,
                        title = "Skills",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); onSkills() },
                        showDivider = false,
                        sepC = sepC
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Clear cache
                GroupedSection(cellC = cellC) {
                    SettingsRow(
                        icon = Lucide.Trash2,
                        title = "Clear Cache",
                        trailing = if (chatCount > 0) "$chatCount chats" else null,
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); showClearChatsConfirm = true },
                        showDivider = false,
                        sepC = sepC
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Feedback · About · Update
                GroupedSection(cellC = cellC) {
                    SettingsRow(
                        icon = Lucide.MessageSquare,
                        title = "Feedback",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); showFeedbackDialog = true },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Info,
                        title = "About AhamAI",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); showAboutDialog = true },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.RotateCw,
                        title = "Check Update",
                        trailing = "V$appVersion",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = { haptics.tick(); showUpdateDialog = true },
                        showDivider = false,
                        sepC = sepC
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Sign out
                GroupedSection(cellC = cellC) {
                    val soInteraction = remember { MutableInteractionSource() }
                    val soPressed by soInteraction.collectIsPressedAsState()
                    HapticOnPress(soInteraction, haptics)
                    val soScale by animateFloatAsState(
                        targetValue = if (soPressed) 0.98f else 1f,
                        animationSpec = AgentFeel.pressSpring,
                        label = "signOut"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { scaleX = soScale; scaleY = soScale }
                            .clickable(
                                interactionSource = soInteraction,
                                indication = null
                            ) { haptics.select(); onSignOut() }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Lucide.LogOut,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            "Sign out",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = primary
                        )
                    }
                }

                if (isAdmin) {
                    Spacer(Modifier.height(18.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { haptics.select(); onAdmin() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Switch to Admin",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = UnicaOneRegular,
                            letterSpacing = 0.5.sp,
                            color = primary
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ═══════════════════════════════════════════════
    // SHEETS & DIALOGS
    // ═══════════════════════════════════════════════

    // ── Data controls (all data-related actions live here) ──
    if (showDataControlSheet) {
        IosBottomSheet(
            isDark = isDark,
            onDismissRequest = { showDataControlSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
            ) {
                Text(
                    "Data controls",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = primary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
                GroupedSection(cellC = cellC) {
                    SettingsRow(
                        icon = Lucide.Layers,
                        title = "Chat History",
                        trailing = if (chatCount > 0) "$chatCount chats" else "Empty",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = {
                            showDataControlSheet = false
                            showClearChatsConfirm = true
                        },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Folder,
                        title = "Workspaces",
                        trailing = if (workspaceInfos.isEmpty()) "Offline"
                        else "${workspaceInfos.size} · offline",
                        primary = primary,
                        secondary = secondary,
                        iconTint = primary,
                        onClick = {
                            showDataControlSheet = false
                            showWorkspaceList = true
                        },
                        showDivider = true,
                        sepC = sepC
                    )
                    SettingsRow(
                        icon = Lucide.Trash2,
                        title = "Delete All Data",
                        trailing = null,
                        primary = iosRed,
                        secondary = secondary,
                        iconTint = iosRed,
                        onClick = {
                            showDataControlSheet = false
                            showClearAllDataConfirm = true
                        },
                        showDivider = false,
                        sepC = sepC,
                        showChevron = false
                    )
                }
            }
        }
    }

    // ── Workspace history sheet (offline · cute cards · import/export) ──
    if (showWorkspaceList) {
        var wsBusy by remember { mutableStateOf<String?>(null) }
        var wsToast by remember { mutableStateOf<String?>(null) }
        val importWs = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                wsBusy = "Importing…"
                val dir = withContext(Dispatchers.IO) {
                    ProjectManager.importWorkspaceHistory(context, uri)
                }
                workspaceInfos = withContext(Dispatchers.IO) { ProjectManager.listProjects(context) }
                withContext(Dispatchers.IO) {
                    ProjectManager.listProjects(context).forEach { p ->
                        com.ahamai.app.data.AgentTaskStore.upsert(
                            context, p.path,
                            title = p.name, projectName = p.name,
                            status = com.ahamai.app.data.AgentTaskStore.Status.IDLE,
                            fileCount = p.fileCount
                        )
                    }
                }
                refreshTrigger++
                wsBusy = null
                val n = ProjectManager.lastImportCount
                wsToast = when {
                    dir == null -> "Import failed"
                    n > 1 -> "Imported $n workspaces · offline"
                    else -> "Imported · offline"
                }
            }
        }

        IosBottomSheet(
            isDark = isDark,
            onDismissRequest = { showWorkspaceList = false }
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Workspaces",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFamily,
                            color = primary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Offline history · on this device only",
                            fontSize = 12.sp,
                            fontFamily = InterFamily,
                            color = secondary
                        )
                    }
                    // Offline pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isDark) Color(0xFF2A2A30) else Color(0xFFEEF0F3))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            "Offline",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = secondary
                        )
                    }
                }

                // Import / Export all (full offline backup — every workspace)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cellC)
                            .clickable { importWs.launch("application/zip") }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Lucide.Download, null, tint = primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Import",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = primary
                        )
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cellC)
                            .clickable(enabled = workspaceInfos.isNotEmpty()) {
                                scope.launch {
                                    wsBusy = "Exporting all…"
                                    val result = withContext(Dispatchers.IO) {
                                        ProjectManager.exportAllWorkspaceHistory(context)
                                    }
                                    wsBusy = null
                                    wsToast = if (result.startsWith("OK"))
                                        "All workspaces → Downloads/AhamAI"
                                    else result
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Lucide.Layers,
                            null,
                            tint = if (workspaceInfos.isEmpty()) secondary else primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Export all",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = if (workspaceInfos.isEmpty()) secondary else primary
                        )
                    }
                }
                Text(
                    if (workspaceInfos.isEmpty()) "0 workspaces · offline only"
                    else "${workspaceInfos.size} workspaces · $totalWsFiles files · export all before uninstall",
                    fontSize = 11.sp,
                    fontFamily = InterFamily,
                    color = secondary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )

                Spacer(Modifier.height(10.dp))

                if (workspaceInfos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(cellC)
                            .padding(vertical = 36.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Lucide.Folder,
                                null,
                                tint = secondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "No workspaces yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFamily,
                                color = primary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Create on Agent home, or import a backup zip.",
                                fontSize = 12.sp,
                                fontFamily = InterFamily,
                                color = secondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(workspaceInfos, key = { it.path }) { info ->
                            var deleting by remember { mutableStateOf(false) }
                            var exporting by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(cellC)
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isDark) Color(0xFF2A2A30) else Color(0xFFF0F0F5)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Lucide.Folder,
                                        null,
                                        tint = primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        info.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = InterFamily,
                                        color = primary,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "${info.fileCount} files · ${ProjectManager.formatWorkspaceAge(info.lastModified)}",
                                        fontSize = 11.sp,
                                        fontFamily = InterFamily,
                                        color = secondary,
                                        maxLines = 1
                                    )
                                }
                                if (exporting || deleting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = secondary
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                exporting = true
                                                val result = withContext(Dispatchers.IO) {
                                                    ProjectManager.exportWorkspaceHistory(context, info.path)
                                                }
                                                exporting = false
                                                wsToast = if (result.startsWith("OK"))
                                                    "Exported to Downloads/AhamAI"
                                                else result
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Lucide.Download,
                                            "Export",
                                            tint = primary,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                deleting = true
                                                withContext(Dispatchers.IO) {
                                                    ProjectManager.deleteProject(info.path)
                                                    com.ahamai.app.data.AgentTaskStore.remove(context, info.path)
                                                }
                                                workspaceInfos = withContext(Dispatchers.IO) {
                                                    ProjectManager.listProjects(context)
                                                }
                                                refreshTrigger++
                                                deleting = false
                                            }
                                        },
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Icon(
                                            Lucide.Trash2,
                                            "Delete",
                                            tint = iosRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                wsBusy?.let { msg ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        msg,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        color = secondary
                    )
                }
                wsToast?.let { msg ->
                    LaunchedEffect(msg) {
                        kotlinx.coroutines.delay(2200)
                        wsToast = null
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        msg,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        color = primary
                    )
                }
            }
        }
    }

    // ── Clear chats confirm ──
    if (showClearChatsConfirm) {
        IosDialog(isDark = isDark, onDismissRequest = { showClearChatsConfirm = false }) {
            Text(
                "Delete Chat History",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This will permanently delete all $chatCount chat sessions. This action cannot be undone.",
                fontSize = 13.sp,
                fontFamily = InterFamily,
                color = secondary
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showClearChatsConfirm = false }) {
                    Text("Cancel", fontSize = 14.sp, fontFamily = InterFamily, color = secondary)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    onClearChats()
                    showClearChatsConfirm = false
                    chatCount = 0
                    refreshTrigger++
                }) {
                    Text("Delete", fontSize = 14.sp, fontFamily = InterFamily, color = iosRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // ── Clear all data confirm ──
    if (showClearAllDataConfirm) {
        IosDialog(isDark = isDark, onDismissRequest = { showClearAllDataConfirm = false }) {
            Text(
                "Delete All Data",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This will permanently delete:\n" +
                "• $chatCount chat sessions\n" +
                "• ${workspaceInfos.size} workspace sessions ($totalWsFiles files)\n" +
                "• All workspace metadata\n\n" +
                "This action cannot be undone.",
                fontSize = 13.sp,
                fontFamily = InterFamily,
                color = secondary,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showClearAllDataConfirm = false }) {
                    Text("Cancel", fontSize = 14.sp, fontFamily = InterFamily, color = secondary)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            // Delete all chats
                            ChatHistoryManager.clearAll(context)
                            // Delete all workspace projects
                            for (info in workspaceInfos) {
                                ProjectManager.deleteProject(info.path)
                            }
                            // Delete workspace session files (agent transcripts)
                            val uid = com.ahamai.app.data.AuthManager.uid()?.takeIf { it.isNotBlank() } ?: "guest"
                            val sessionsDir = File(context.filesDir, "agent_sessions_$uid")
                            if (sessionsDir.exists()) sessionsDir.deleteRecursively()
                            // Delete the cloud workspace backup too (manifest + all chunks), so a
                            // later reinstall/sign-in doesn't restore the data the user just cleared.
                            try { com.ahamai.app.data.AuthManager.deleteCloudWorkspaces(context) } catch (_: Exception) {}
                            // Delete any leftover metadata from older builds (Downloads/AhamAI/workspaces/)
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    val projection = arrayOf(android.provider.MediaStore.Downloads._ID, android.provider.MediaStore.Downloads.DISPLAY_NAME)
                                    context.contentResolver.query(
                                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                        projection,
                                        "${android.provider.MediaStore.Downloads.RELATIVE_PATH} = ?",
                                        arrayOf(android.os.Environment.DIRECTORY_DOWNLOADS + "/AhamAI/workspaces/"),
                                        null
                                    )?.use { c ->
                                        val idIdx = c.getColumnIndexOrThrow(android.provider.MediaStore.Downloads._ID)
                                        while (c.moveToNext()) {
                                            val id = c.getLong(idIdx)
                                            val uri = android.content.ContentUris.withAppendedId(
                                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
                                            context.contentResolver.delete(uri, null, null)
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        showClearAllDataConfirm = false
                        chatCount = 0
                        workspaceInfos = emptyList()
                        refreshTrigger++
                    }
                }) {
                    Text("Delete All", fontSize = 14.sp, fontFamily = InterFamily, color = iosRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    // ── Appearance Sheet ──
    if (showAppearanceSheet) {
        IosBottomSheet(
            isDark = isDark,
            onDismissRequest = { showAppearanceSheet = false }
        ) {
            AppearancePickerContent(
                currentMode = currentThemeMode,
                isDark = isDark,
                primary = primary,
                secondary = secondary,
                accent = accentC,
                cellC = cellC,
                onSelect = { mode ->
                    currentThemeMode = mode
                    prefsManager.saveThemeMode(mode)
                    ThemeRefresh.bump()  // bump so MainActivity re-reads immediately
                    showAppearanceSheet = false
                }
            )
        }
    }

    // ── Voice Sheet ──
    if (showVoiceSheet) {
        IosBottomSheet(
            isDark = isDark,
            onDismissRequest = { showVoiceSheet = false },
            sheetState = voiceSheetState
        ) {
            VoicePickerContent(
                currentVoice = currentVoice,
                isDark = isDark,
                primary = primary,
                secondary = secondary,
                accent = accentC,
                onSelect = { voiceId ->
                    onVoiceChange(voiceId)
                    showVoiceSheet = false
                }
            )
        }
    }

    // ── Custom Prompt Dialog — iOS style ──
    if (showCustomPromptDialog) {
        IosDialog(
            isDark = isDark,
            onDismissRequest = { showCustomPromptDialog = false }
        ) {
            // Title
            Text(
                text = "System Instructions",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Custom instructions the AI follows during chat. Only applied in chat mode, not agent mode.",
                fontSize = 13.sp,
                fontFamily = InterFamily,
                color = secondary
            )
            Spacer(Modifier.height(14.dp))

            // Text field
            BasicTextField(
                value = customPromptText,
                onValueChange = { customPromptText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 240.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F5))
                    .padding(12.dp),
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 14.sp,
                    fontFamily = InterFamily,
                    color = primary,
                    lineHeight = 20.sp
                ),
                cursorBrush = SolidColor(accentC)
            )

            Spacer(Modifier.height(20.dp))

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (customPromptText.isNotBlank()) {
                    TextButton(onClick = {
                        customPromptText = ""
                        context.getSharedPreferences("ahamai_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString("custom_prompt", "").apply()
                        showCustomPromptDialog = false
                    }) {
                        Text("Clear", fontSize = 15.sp, fontFamily = InterFamily, color = iosRed)
                    }
                    Spacer(Modifier.width(4.dp))
                }
                TextButton(onClick = { showCustomPromptDialog = false }) {
                    Text("Cancel", fontSize = 15.sp, fontFamily = InterFamily, color = secondary)
                }
                TextButton(onClick = {
                    context.getSharedPreferences("ahamai_prefs", android.content.Context.MODE_PRIVATE)
                        .edit().putString("custom_prompt", customPromptText).apply()
                    showCustomPromptDialog = false
                }) {
                    Text("Save", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = accentC)
                }
            }
        }
    }

    // ── Feedback (iOS floating card + blur, multi-photo, green submit) ──
    if (showFeedbackDialog) {
        com.ahamai.app.ui.components.IosFeedbackDialog(
            isDark = isDark,
            message = feedbackText,
            onMessageChange = { feedbackText = it },
            imageUris = feedbackImages,
            onAddImages = {
                feedbackImagePicker.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            },
            onRemoveImage = { uri -> feedbackImages = feedbackImages.filter { it != uri } },
            onDismissRequest = {
                showFeedbackDialog = false
                feedbackText = ""
                feedbackImages = emptyList()
            },
            onSubmit = {
                val body = buildString {
                    append(feedbackText.trim())
                    append("\n\n——\n")
                    append("App: AhamAI v$appVersion\n")
                    if (displayName.isNotBlank()) append("User: $displayName\n")
                    if (displayEmail.isNotBlank()) append("Email: $displayEmail\n")
                    if (feedbackImages.isNotEmpty()) {
                        append("Attachments: ${feedbackImages.size} image(s)\n")
                    }
                }
                val to = com.ahamai.app.data.RemoteConfigManager.feedbackEmail.trim()
                if (to.isBlank() || !to.contains("@")) {
                    android.widget.Toast.makeText(
                        context,
                        "Feedback email not set. Ask admin to add it in Admin → Control.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    val ok = runCatching {
                        if (feedbackImages.isEmpty()) {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:")
                                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(to))
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "AhamAI Feedback v$appVersion")
                                putExtra(android.content.Intent.EXTRA_TEXT, body)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Send feedback"))
                        } else {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND_MULTIPLE).apply {
                                type = "message/rfc822"
                                putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(to))
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "AhamAI Feedback v$appVersion")
                                putExtra(android.content.Intent.EXTRA_TEXT, body)
                                putParcelableArrayListExtra(
                                    android.content.Intent.EXTRA_STREAM,
                                    ArrayList(feedbackImages)
                                )
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Send feedback"))
                        }
                    }.isSuccess
                    if (!ok) {
                        runCatching {
                            val subject = android.net.Uri.encode("AhamAI Feedback v$appVersion")
                            val encoded = android.net.Uri.encode(body)
                            uriHandler.openUri("mailto:$to?subject=$subject&body=$encoded")
                        }
                    }
                    showFeedbackDialog = false
                    feedbackText = ""
                    feedbackImages = emptyList()
                }
            }
        )
    }

    // ── About AhamAI ──
    if (showAboutDialog) {
        IosDialog(
            isDark = isDark,
            onDismissRequest = { showAboutDialog = false }
        ) {
            Text(
                "About AhamAI",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "AhamAI is your on-device AI companion for chat and agent workflows — build, code, and ship from your phone.",
                fontSize = 14.sp,
                fontFamily = InterFamily,
                color = secondary,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F5))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    "Version",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = secondary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "V$appVersion",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFamily,
                    color = primary
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text(
                        "Done",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = accentC
                    )
                }
            }
        }
    }

    // ── Check Update ──
    if (showUpdateDialog) {
        IosDialog(
            isDark = isDark,
            onDismissRequest = { showUpdateDialog = false }
        ) {
            Text(
                "Check Update",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "You're on the latest build.",
                fontSize = 14.sp,
                fontFamily = InterFamily,
                color = secondary,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F5))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Lucide.RotateCw,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Current version",
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        color = secondary
                    )
                    Text(
                        "V$appVersion",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = primary
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text(
                        "OK",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = accentC
                    )
                }
            }
        }
    }
}

// ── Grouped card (no outer horizontal pad — parent already pads 16dp) ──
@Composable
private fun GroupedSection(cellC: Color, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(sectionShape)
            .background(cellC)
    ) {
        content()
    }
}

// ── Qoder settings row: outline icon · title · trailing · chevron + smooth press ──
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    trailing: String? = null,
    // legacy alias used by sheets — maps to trailing if set
    subtitle: String? = null,
    primary: Color,
    secondary: Color,
    iconTint: Color,
    onClick: () -> Unit,
    showDivider: Boolean,
    sepC: Color,
    showChevron: Boolean = true
) {
    val trailingText = trailing ?: subtitle
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isDark = isSystemInDarkTheme()
    val pressBg = if (isDark) Color(0xFF3A3A3C) else Color(0xFFF0F0F0)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = AgentFeel.pressSpring,
        label = "settingsRow"
    )
    val rowBg by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = AgentFeel.snappySpring,
        label = "settingsRowBg"
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(pressBg.copy(alpha = 0.55f * rowBg))
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                color = primary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            if (!trailingText.isNullOrBlank()) {
                Text(
                    trailingText,
                    fontSize = 15.sp,
                    fontFamily = InterFamily,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF999999),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
            }
            if (showChevron) {
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF636366) else Color(0xFFCCCCCC),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp)
                    .height(1.dp)
                    .background(sepC)
            )
        }
    }
}

// ── Voice Picker (Cupertino-style bottom sheet content) ──
@Composable
private fun VoicePickerContent(
    currentVoice: String,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    accent: Color,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var playingVoice by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Choose Voice",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA)))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(scrollState)
                .padding(bottom = 20.dp)
        ) {
            AVAILABLE_VOICES.forEachIndexed { index, voice ->
                val isSelected = voice.id == currentVoice
                val isPlaying = playingVoice == voice.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(voice.id) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Lucide.AudioLines,
                        contentDescription = null,
                        tint = if (isSelected) accent else secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = voice.name,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = InterFamily,
                            color = primary
                        )
                        Text(
                            text = voice.description,
                            fontSize = 10.sp,
                            fontFamily = InterFamily,
                            color = secondary
                        )
                    }
                    if (isSelected) {
                        Icon(
                            imageVector = Lucide.Check,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.15f))
                            .clickable {
                                scope.launch {
                                    try {
                                        com.ahamai.app.data.EdgeTtsClient.stopAudio()
                                        playingVoice = voice.id
                                        val file = com.ahamai.app.data.EdgeTtsClient.synthesize(
                                            voice.testText, context.cacheDir, voice.id
                                        )
                                        com.ahamai.app.data.EdgeTtsClient.playAudio(file)
                                        while (com.ahamai.app.data.EdgeTtsClient.isCurrentlyPlaying()) {
                                            kotlinx.coroutines.delay(150)
                                        }
                                        playingVoice = null
                                    } catch (_: Exception) { playingVoice = null }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) com.ahamai.app.ui.icons.AdminIcons.X else Lucide.Play,
                            contentDescription = "Preview",
                            tint = if (isPlaying) primary else accent,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                if (index < AVAILABLE_VOICES.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 50.dp)
                            .height(0.5.dp)
                            .background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA))
                    )
                }
            }
        }
    }
}

// ── Appearance Picker (theme mode bottom sheet content) ──
@Composable
private fun AppearancePickerContent(
    currentMode: String,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    accent: Color,
    cellC: Color,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Appearance",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            // System
            ThemeOptionItem(
                label = "System",
                description = "Match device setting",
                icon = Lucide.Monitor,
                isSelected = currentMode == "system",
                accent = accent,
                primary = primary,
                cellC = cellC,
                onClick = { onSelect("system") }
            )
            ThemeOptionItem(
                label = "Light",
                description = "Always light mode",
                icon = Lucide.Sun,
                isSelected = currentMode == "light",
                accent = accent,
                primary = primary,
                cellC = cellC,
                onClick = { onSelect("light") }
            )
            ThemeOptionItem(
                label = "Dark",
                description = "Always dark mode",
                icon = Lucide.Moon,
                isSelected = currentMode == "dark",
                accent = accent,
                primary = primary,
                cellC = cellC,
                onClick = { onSelect("dark") }
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ThemeOptionItem(
    label: String,
    description: String,
    icon: ImageVector,
    isSelected: Boolean,
    accent: Color,
    primary: Color,
    cellC: Color,
    onClick: () -> Unit
) {
    val bg = if (isSelected) accent.copy(alpha = 0.08f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) accent else primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                color = if (isSelected) accent else primary
            )
            Text(
                description,
                fontSize = 12.sp,
                fontFamily = InterFamily,
                color = primary.copy(alpha = 0.6f)
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Lucide.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
    Spacer(Modifier.height(4.dp))
}