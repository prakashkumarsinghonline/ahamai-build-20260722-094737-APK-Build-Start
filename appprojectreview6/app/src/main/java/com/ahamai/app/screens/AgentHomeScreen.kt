package com.ahamai.app.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.PushPin
import com.ahamai.app.ui.icons.AdminIcons.ZipIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Check
import com.ahamai.app.data.AgentTaskStore
import com.ahamai.app.data.GitHubClient
import com.ahamai.app.data.PreferencesManager
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.ahamai.app.data.ProjectManager
import com.ahamai.app.data.AuthManager
import com.ahamai.app.ui.components.ActiveAgentsChip
import com.ahamai.app.ui.components.AgentTaskBucket
import com.ahamai.app.ui.components.AgentTaskListRow
import com.ahamai.app.ui.components.AgentTaskStatusGlyph
import com.ahamai.app.ui.components.AgentTasksSectionHeader
import com.ahamai.app.ui.components.IosBottomSheet
import com.ahamai.app.ui.components.IosContextMenuAction
import com.ahamai.app.ui.components.IosDialog
import com.ahamai.app.ui.components.IosEllipsisButton
import com.ahamai.app.ui.components.IosFloatingContextMenu
import com.ahamai.app.ui.components.IosRenameDialog
import com.ahamai.app.ui.components.MissionQuickPill
import com.ahamai.app.ui.components.MissionSectionLabel
import com.ahamai.app.ui.components.MissionTaskRow
import com.ahamai.app.ui.components.toBucket
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.ChatPalette
import com.ahamai.app.ui.theme.RalewayFamily
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import com.ahamai.app.ui.agent.AgentEnter
import com.ahamai.app.ui.agent.agentPressable
import com.ahamai.app.ui.agent.rememberAgentHaptics
import com.ahamai.app.ui.chat.HapticOnPress

// iOS system palette
private val iosBlue = Color(0xFF0A84FF)
// Match agent screen Synara-inspired chrome
private val agDarkBg = Color(0xFF0C0C0E)
private val agDarkRow = Color(0xFF161618)
private val agDarkBorder = Color(0xFF2A2A2E)
private val agLightRow = Color(0xFFF5F5F7)
private val agLightBorder = Color(0xFFE4E4E7)

/** Shared segmented Chat / Agent toggle used on the home + agent screens. */
@Composable
fun ModeToggle(
    selected: String, // "Chat" or "Agent"
    isDark: Boolean,
    onChat: () -> Unit,
    onAgent: () -> Unit
) {
    val haptics = rememberAgentHaptics()
    val trackColor = if (isDark) agDarkRow else Color(0xFFEDEDED)
    val activeColor = if (isDark) Color(0xFF2E2E2E) else Color(0xFFF5F5F7)
    val activeText = if (isDark) Color.White else Color(0xFF111111)
    val inactiveText = Color(0xFF8E8E93)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = trackColor,
        border = BorderStroke(0.5.dp, if (isDark) agDarkBorder else agLightBorder)
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            listOf("Chat", "Agent").forEach { label ->
                val isSel = label == selected
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSel) activeColor else Color.Transparent)
                        .agentPressable(pressedScale = 0.96f, haptics = haptics) {
                            haptics.select()
                            if (label == "Chat") onChat() else onAgent()
                        }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 11.sp,
                        fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = InterFamily,
                        color = if (isSel) activeText else inactiveText
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentHomeScreen(
    modelName: String,
    avatarBase64: String = "",
    onChatMode: () -> Unit,
    onProfile: () -> Unit,
    onOpenProject: (dir: String, name: String, initialPrompt: String?) -> Unit,
    onSpecBuilder: () -> Unit = {},
    onOpenConnectors: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }
    val agentHaptics = rememberAgentHaptics()

    val muted = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkSecondary
    val primaryText = if (isDark) ChatPalette.DarkInk else ChatPalette.LightInk
    val pageBg = if (isDark) ChatPalette.DarkBg else ChatPalette.LightSurfaceElevated
    val border = if (isDark) ChatPalette.DarkBorder else ChatPalette.LightBorder
    val fabGreen = Color(0xFF34C759) // system green — matches reference + button, fits app greens

    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var showRepoSheet by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var connectedRepo by remember { mutableStateOf(prefs.getConnectedRepo()) }
    var repos by remember { mutableStateOf<List<GitHubClient.GhRepo>>(emptyList()) }
    var reposLoading by remember { mutableStateOf(false) }
    var reposError by remember { mutableStateOf<String?>(null) }
    var showBranchSheet by remember { mutableStateOf(false) }
    var branches by remember { mutableStateOf<List<String>>(emptyList()) }
    var branchesLoading by remember { mutableStateOf(false) }
    var showConnectorsSheet by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    /** null = All tasks; else filter by bucket */
    var filterBucket by remember { mutableStateOf<AgentTaskBucket?>(null) }
    var menuTask by remember { mutableStateOf<AgentTaskStore.Task?>(null) }
    var renameTask by remember { mutableStateOf<AgentTaskStore.Task?>(null) }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = "Extracting project..."
            val dir = withContext(Dispatchers.IO) { ProjectManager.extractZip(context, uri) }
            busy = null
            if (dir != null) onOpenProject(dir, dir.substringAfterLast('/'), null)
        }
    }

    // Offline workspace history — local only (import/export, no cloud)
    var historyToast by remember { mutableStateOf<String?>(null) }
    var confirmDeleteDir by remember { mutableStateOf<String?>(null) }
    var confirmDeleteName by remember { mutableStateOf("") }
    var historyRefresh by remember { mutableStateOf(0) }
    val historyImportPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = "Importing workspace..."
            val dir = withContext(Dispatchers.IO) { ProjectManager.importWorkspaceHistory(context, uri) }
            // Register every restored workspace in mission control
            withContext(Dispatchers.IO) {
                ProjectManager.listProjects(context).forEach { p ->
                    AgentTaskStore.upsert(
                        context, p.path,
                        title = p.name, projectName = p.name,
                        status = AgentTaskStore.Status.IDLE,
                        fileCount = p.fileCount
                    )
                }
            }
            busy = null
            val n = ProjectManager.lastImportCount
            historyToast = when {
                dir == null -> "Import failed"
                n > 1 -> "Imported $n workspaces · offline"
                else -> "Imported · offline"
            }
            historyRefresh++
        }
    }

    fun loadRepos() = scope.launch {
        reposLoading = true; reposError = null
        val list = withContext(Dispatchers.IO) { GitHubClient.listRepos(prefs.getGithubToken()) }
        repos = list
        if (list.isEmpty()) reposError = "No repos found (token needs 'repo' scope)."
        reposLoading = false
    }

    fun openRepoFlow() {
        if (prefs.isGithubConnected()) { showRepoSheet = true; loadRepos() } else showTokenDialog = true
    }

    fun loadBranches() = scope.launch {
        branchesLoading = true
        branches = withContext(Dispatchers.IO) { GitHubClient.listBranches(prefs.getGithubToken(), prefs.getConnectedRepo()) }
        branchesLoading = false
    }

    fun startScratch(prompt: String?, useTemplate: Boolean, label: String) {
        val dir = if (useTemplate) ProjectManager.createFromTemplate(context, label)
                  else ProjectManager.createEmptyProject(context, label)
        if (!prompt.isNullOrBlank()) {
            AgentTaskStore.markWorking(context, dir, "", label, prompt)
            // AI title in background (same as CodeAgentScreen run start)
            scope.launch {
                val ep = runCatching { com.ahamai.app.data.ApiConfig.chat(context) }.getOrNull()
                if (ep != null) {
                    AgentTaskStore.generateAndApplyTitle(
                        context, dir, prompt,
                        ep.baseUrl, ep.apiKey, ep.model
                    )
                }
            }
        } else {
            AgentTaskStore.upsert(
                context, dir,
                title = "New task",
                projectName = label,
                status = AgentTaskStore.Status.IDLE
            )
        }
        onOpenProject(dir, label, prompt)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                navigationIcon = { Spacer(Modifier.width(48.dp)) },
                title = {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ModeToggle(selected = "Agent", isDark = isDark, onChat = onChatMode, onAgent = {})
                    }
                },
                actions = {
                    IconButton(onClick = onProfile) {
                        val avatarBmp = remember(avatarBase64) {
                            com.ahamai.app.data.ImageUtils.decodeBase64(avatarBase64)
                        }
                        if (avatarBmp != null) {
                            androidx.compose.foundation.Image(
                                bitmap = avatarBmp.asImageBitmap(),
                                contentDescription = "Profile",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                        } else {
                            Icon(Icons.Outlined.AccountCircle, "Profile", tint = muted, modifier = Modifier.size(26.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = pageBg,
                    scrolledContainerColor = pageBg
                )
            )
        },
        floatingActionButton = {
            // Butter-smooth iOS FAB — spring press + soft shadow
            val fabInteraction = remember { MutableInteractionSource() }
            val fabPressed by fabInteraction.collectIsPressedAsState()
            HapticOnPress(fabInteraction, agentHaptics)
            val fabScale by animateFloatAsState(
                targetValue = if (fabPressed) 0.88f else 1f,
                animationSpec = com.ahamai.app.ui.agent.AgentFeel.fabSpring,
                label = "fabScale"
            )
            // Green FAB restored (same #34C759 as app system green)
            Box(
                modifier = Modifier
                    .padding(end = 6.dp, bottom = 6.dp)
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    }
                    .shadow(
                        elevation = if (isDark) 4.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = fabGreen.copy(0.28f),
                        spotColor = fabGreen.copy(0.35f)
                    )
                    .clip(CircleShape)
                    .background(fabGreen)
                    .clickable(
                        interactionSource = fabInteraction,
                        indication = null
                    ) {
                        agentHaptics.tick()
                        startScratch(null, false, "Project")
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "New task",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        containerColor = pageBg
    ) { padding ->
        var taskTick by remember { mutableStateOf(0) }
        DisposableEffect(Unit) {
            val l: () -> Unit = { taskTick++ }
            AgentTaskStore.addListener(l)
            onDispose { AgentTaskStore.removeListener(l) }
        }
        // Fix stuck "In Progress" after runs that never got markFinished
        LaunchedEffect(Unit, historyRefresh) {
            withContext(Dispatchers.IO) { AgentTaskStore.reconcileStale(context) }
            taskTick++
        }
        val listKey = taskTick + historyRefresh
        val missionTasks = remember(listKey) { AgentTaskStore.recent(context, limit = 80) }
        val diskProjects = remember(listKey) {
            runCatching { ProjectManager.listProjects(context) }.getOrDefault(emptyList())
        }
        val knownDirs = remember(missionTasks) { missionTasks.map { it.projectDir }.toSet() }
        val orphanTasks = remember(diskProjects, knownDirs) {
            diskProjects.filter { it.path !in knownDirs }.map { proj ->
                AgentTaskStore.Task(
                    projectDir = proj.path,
                    title = proj.name,
                    projectName = proj.name,
                    status = AgentTaskStore.Status.IDLE,
                    fileCount = proj.fileCount,
                    updatedAt = proj.lastModified
                )
            }
        }
        val allTasks = remember(missionTasks, orphanTasks) {
            (missionTasks + orphanTasks).distinctBy { it.projectDir }
                .sortedByDescending { it.updatedAt }
        }
        val filtered = remember(allTasks, filterBucket) {
            if (filterBucket == null) allTasks
            else allTasks.filter { it.status.toBucket() == filterBucket }
        }
        val actionTasks = remember(filtered) {
            filtered.filter { it.status.toBucket() == AgentTaskBucket.ACTION_REQUIRED }
        }
        val progressTasks = remember(filtered) {
            filtered.filter { it.status.toBucket() == AgentTaskBucket.IN_PROGRESS }
        }
        val idleTasks = remember(filtered) {
            filtered.filter { it.status.toBucket() == AgentTaskBucket.IDLE }
        }
        val filterLabel = when (filterBucket) {
            null -> "All tasks"
            AgentTaskBucket.ACTION_REQUIRED -> "Action Required"
            AgentTaskBucket.IN_PROGRESS -> "In Progress"
            AgentTaskBucket.COMPLETED -> "Completed"
            AgentTaskBucket.IDLE -> "Idle"
        }
        val completedTasks = remember(filtered) {
            filtered.filter { it.status.toBucket() == AgentTaskBucket.COMPLETED }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            agentHaptics.select()
                            showFilterMenu = true
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        filterLabel,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = InterFamily,
                        color = primaryText,
                        letterSpacing = (-0.2f).sp
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                // iOS-style white circle + ellipsis
                IosEllipsisButton(
                    isDark = isDark,
                    onClick = {
                        agentHaptics.select()
                        showOverflowMenu = true
                    }
                )
            }

            // All tasks filter — same floating card + blur + icon rows as ⋮ menu
            if (showFilterMenu) {
                IosFloatingContextMenu(
                    isDark = isDark,
                    title = null,
                    onDismissRequest = { showFilterMenu = false },
                    actions = listOf(
                        IosContextMenuAction(
                            label = "All tasks",
                            icon = com.ahamai.app.ui.icons.AdminIcons.AllTasks,
                            onClick = {
                                filterBucket = null
                                showFilterMenu = false
                            }
                        ),
                        IosContextMenuAction(
                            label = "Action Required",
                            icon = Lucide.AlertTriangle,
                            onClick = {
                                filterBucket = AgentTaskBucket.ACTION_REQUIRED
                                showFilterMenu = false
                            }
                        ),
                        IosContextMenuAction(
                            label = "In Progress",
                            icon = Lucide.Activity,
                            onClick = {
                                filterBucket = AgentTaskBucket.IN_PROGRESS
                                showFilterMenu = false
                            }
                        ),
                        IosContextMenuAction(
                            label = "Completed",
                            icon = Lucide.Check,
                            onClick = {
                                filterBucket = AgentTaskBucket.COMPLETED
                                showFilterMenu = false
                            }
                        ),
                        IosContextMenuAction(
                            label = "Idle",
                            icon = Lucide.Circle,
                            onClick = {
                                filterBucket = AgentTaskBucket.IDLE
                                showFilterMenu = false
                            }
                        )
                    )
                )
            }

            // ⋮ overflow — same floating card + blur as long-press menu
            if (showOverflowMenu) {
                IosFloatingContextMenu(
                    isDark = isDark,
                    title = null, // actions-only card (no title pill)
                    onDismissRequest = { showOverflowMenu = false },
                    actions = listOf(
                        IosContextMenuAction(
                            label = "Import project",
                            icon = com.ahamai.app.ui.icons.AdminIcons.ImportProject,
                            onClick = {
                                showOverflowMenu = false
                                zipPicker.launch("application/zip")
                            }
                        ),
                        IosContextMenuAction(
                            label = if (connectedRepo.isNotBlank()) connectedRepo else "Connect repository",
                            icon = Lucide.Github,
                            onClick = {
                                showOverflowMenu = false
                                openRepoFlow()
                            }
                        )
                    )
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp)
            ) {
                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp, start = 8.dp, end = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "No tasks yet",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFamily,
                                color = primaryText
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap + to start a workspace. Agent history lives here — separate from chat.",
                                fontSize = 14.sp,
                                fontFamily = InterFamily,
                                color = muted,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                if (actionTasks.isNotEmpty() && (filterBucket == null || filterBucket == AgentTaskBucket.ACTION_REQUIRED)) {
                    item(key = "sec-action") {
                        AgentTasksSectionHeader("Action Required", AgentTaskBucket.ACTION_REQUIRED)
                    }
                    items(actionTasks, key = { "a-${it.projectDir}" }) { task ->
                        AgentTaskListRow(
                            title = AgentTaskStore.displayTitle(task),
                            status = task.status,
                            primaryText = primaryText,
                            muted = muted,
                            onClick = {
                                onOpenProject(task.projectDir, task.projectName, null)
                            },
                            onLongPress = { menuTask = task }
                        )
                    }
                }

                if (progressTasks.isNotEmpty() && (filterBucket == null || filterBucket == AgentTaskBucket.IN_PROGRESS)) {
                    item(key = "sec-progress") {
                        AgentTasksSectionHeader("In Progress", AgentTaskBucket.IN_PROGRESS)
                    }
                    items(progressTasks, key = { "w-${it.projectDir}" }) { task ->
                        AgentTaskListRow(
                            title = AgentTaskStore.displayTitle(task),
                            status = task.status,
                            primaryText = primaryText,
                            muted = muted,
                            onClick = {
                                onOpenProject(task.projectDir, task.projectName, null)
                            },
                            onLongPress = { menuTask = task }
                        )
                    }
                }

                // Finished agent runs — separate from empty Idle workspaces
                if (completedTasks.isNotEmpty() && (filterBucket == null || filterBucket == AgentTaskBucket.COMPLETED)) {
                    item(key = "sec-done") {
                        AgentTasksSectionHeader("Completed", AgentTaskBucket.COMPLETED)
                    }
                    items(completedTasks, key = { "c-${it.projectDir}" }) { task ->
                        AgentTaskListRow(
                            title = AgentTaskStore.displayTitle(task),
                            status = task.status,
                            primaryText = primaryText,
                            muted = muted,
                            onClick = {
                                onOpenProject(task.projectDir, task.projectName, null)
                            },
                            onLongPress = { menuTask = task }
                        )
                    }
                }

                if (idleTasks.isNotEmpty() && (filterBucket == null || filterBucket == AgentTaskBucket.IDLE)) {
                    item(key = "sec-idle") {
                        AgentTasksSectionHeader("Idle", AgentTaskBucket.IDLE)
                    }
                    items(idleTasks, key = { "i-${it.projectDir}" }) { task ->
                        val dim = task.status == AgentTaskStore.Status.MERGED ||
                            (System.currentTimeMillis() - task.updatedAt) > 7L * 24 * 60 * 60 * 1000
                        AgentTaskListRow(
                            title = AgentTaskStore.displayTitle(task),
                            status = task.status,
                            primaryText = primaryText,
                            muted = muted,
                            dimmed = dim,
                            onClick = {
                                onOpenProject(task.projectDir, task.projectName, null)
                            },
                            onLongPress = { menuTask = task }
                        )
                    }
                }
            }
        }

        // Long-press: Rename · Pin/Unpin · Export · Delete
        menuTask?.let { task ->
            IosFloatingContextMenu(
                isDark = isDark,
                title = AgentTaskStore.displayTitle(task),
                onDismissRequest = { menuTask = null },
                actions = listOf(
                    IosContextMenuAction(
                        label = "Rename",
                        icon = Lucide.Edit,
                        onClick = {
                            renameTask = task
                            menuTask = null
                        }
                    ),
                    IosContextMenuAction(
                        label = if (task.pinned) "Unpin" else "Pin",
                        icon = if (task.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        onClick = {
                            AgentTaskStore.togglePin(context, task.projectDir)
                            historyRefresh++
                            menuTask = null
                        }
                    ),
                    IosContextMenuAction(
                        label = "Export",
                        icon = Lucide.Download,
                        onClick = {
                            menuTask = null
                            scope.launch {
                                busy = "Exporting…"
                                val result = withContext(Dispatchers.IO) {
                                    ProjectManager.exportWorkspaceHistory(context, task.projectDir)
                                }
                                busy = null
                                historyToast = if (result.startsWith("OK")) "Exported to Downloads/AhamAI" else result
                            }
                        }
                    ),
                    IosContextMenuAction(
                        label = "Delete",
                        icon = Lucide.Trash2,
                        destructive = true,
                        onClick = {
                            confirmDeleteDir = task.projectDir
                            confirmDeleteName = AgentTaskStore.displayTitle(task)
                            menuTask = null
                        }
                    )
                )
            )
        }

        // Rename — same blur + floating card as context menu
        renameTask?.let { task ->
            IosRenameDialog(
                isDark = isDark,
                initialName = AgentTaskStore.displayTitle(task),
                onDismissRequest = { renameTask = null },
                onSave = { name ->
                    ProjectManager.setSessionTitle(task.projectDir, name)
                    AgentTaskStore.upsert(
                        context,
                        task.projectDir,
                        title = name,
                        projectName = task.projectName
                    )
                    historyRefresh++
                    renameTask = null
                }
            )
        }

    }

    busy?.let { msg ->
        IosDialog(isDark = isDark, onDismissRequest = {}) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = primaryText)
                Spacer(Modifier.width(16.dp))
                Text(msg, color = primaryText, fontSize = 14.sp)
            }
        }
    }

    // Soft toast for export/import feedback
    historyToast?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            historyToast = null
        }
    }

    // Delete workspace confirm
    confirmDeleteDir?.let { dir ->
        IosDialog(isDark = isDark, onDismissRequest = { confirmDeleteDir = null }) {
            Text(
                "Delete workspace?",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primaryText
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "“$confirmDeleteName” will be removed from this device. Export first if you want a backup.",
                fontSize = 13.sp,
                fontFamily = InterFamily,
                color = muted,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { confirmDeleteDir = null }) {
                    Text("Cancel", fontSize = 13.sp, fontFamily = InterFamily, color = muted)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ProjectManager.deleteProject(dir)
                            AgentTaskStore.remove(context, dir)
                        }
                        confirmDeleteDir = null
                        historyRefresh++
                        historyToast = "Workspace deleted"
                    }
                }) {
                    Text(
                        "Delete",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = Color(0xFFFF3B30)
                    )
                }
            }
        }
    }

    if (showTokenDialog) {
        var token by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        IosDialog(isDark = isDark, onDismissRequest = { showTokenDialog = false }) {
            Text("Connect GitHub", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText)
            Spacer(Modifier.height(4.dp))
            Text(
                "Paste a token with repo + workflow + delete_repo scopes (needed to auto-delete temp APK build repos).",
                fontSize = 11.sp, color = muted, lineHeight = 15.sp
            )
            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(8.dp), color = if (isDark) Color(0xFF1F1F1F) else Color(0xFFF3F3F6)) {
                BasicTextField(
                    value = token, onValueChange = { token = it; err = null },
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = primaryText, fontFamily = JetBrainsMonoFamily),
                    cursorBrush = SolidColor(primaryText), singleLine = true,
                    decorationBox = { inner -> Box { if (token.isEmpty()) Text("ghp_...", fontSize = 13.sp, color = muted, fontFamily = JetBrainsMonoFamily); inner() } }
                )
            }
            if (err != null) { Spacer(Modifier.height(6.dp)); Text(err!!, fontSize = 11.sp, color = Color(0xFFEF4444)) }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showTokenDialog = false }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("Cancel", color = muted, fontSize = 13.sp)
                }
                val ok = token.isNotBlank() && !checking
                val btnBg = when {
                    !ok -> if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                    isDark -> Color(0xFFECECEC)
                    else -> Color(0xFF111111)
                }
                val btnFg = when {
                    !ok -> muted
                    isDark -> Color(0xFF111111)
                    else -> Color.White
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(btnBg)
                        .then(
                            if (ok) Modifier.clickable {
                                scope.launch {
                                    checking = true; err = null
                                    val user = withContext(Dispatchers.IO) { GitHubClient.getUser(token.trim()) }
                                    checking = false
                                    if (user != null) {
                                        prefs.saveGithubToken(token.trim())
                                        AuthManager.uid()?.let { prefs.saveGithubOwner(it) }
                                        showTokenDialog = false
                                        AuthManager.backupGithubToken(context)
                                        showRepoSheet = true; loadRepos()
                                    } else err = "Invalid token. Check scopes and try again."
                                }
                            } else Modifier
                        )
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    if (checking) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = btnFg)
                    } else {
                        coil.compose.AsyncImage(
                            model = "https://www.google.com/s2/favicons?domain=github.com&sz=64",
                            contentDescription = "GitHub",
                            modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Connect", color = btnFg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showRepoSheet) {
        IosBottomSheet(isDark = isDark, onDismissRequest = { showRepoSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(4.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Select repository", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
                        if (connectedRepo.isNotBlank()) {
                            Text(connectedRepo, fontSize = 10.sp, color = muted, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    TextButton(
                        onClick = {
                            scope.launch { AuthManager.clearGithubBackup(context) }
                            connectedRepo = ""; showRepoSheet = false
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text("Disconnect", fontSize = 11.sp, color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium, fontFamily = InterFamily)
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA)))
                when {
                    reposLoading -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        IOSSpinner(size = 18.dp, color = muted)
                    }
                    reposError != null -> Text(reposError!!, color = muted, fontSize = 12.sp, fontFamily = InterFamily, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(repos) { idx, repo ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            showRepoSheet = false
                                            busy = "Cloning ${repo.fullName}..."
                                            val dir = withContext(Dispatchers.IO) {
                                                GitHubClient.downloadRepo(context, prefs.getGithubToken(), repo.fullName, repo.defaultBranch)
                                            }
                                            busy = null
                                            if (dir != null) {
                                                prefs.saveConnectedRepo(repo.fullName, repo.defaultBranch)
                                                AuthManager.backupGithubToken(context)
                                                connectedRepo = repo.fullName
                                                onOpenProject(dir, repo.fullName.substringAfterLast('/'), null)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(repo.fullName.substringAfterLast('/'), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryText, fontFamily = InterFamily, maxLines = 1, modifier = Modifier.weight(1f))
                                if (repo.private) {
                                    Spacer(Modifier.width(5.dp))
                                    Icon(com.ahamai.app.ui.icons.AdminIcons.Lock, "private", tint = muted.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                                }
                                Spacer(Modifier.width(8.dp))
                                if (repo.language != null) {
                                    Text(repo.language, fontSize = 10.sp, color = muted.copy(alpha = 0.7f), fontFamily = InterFamily)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Icon(com.ahamai.app.ui.icons.Lucide.ChevronRight, null, tint = muted.copy(alpha = 0.2f), modifier = Modifier.size(14.dp))
                            }
                            if (idx < repos.size - 1) {
                                Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp).height(0.5.dp).background(if (isDark) Color(0xFF1F1F1F).copy(alpha = 0.4f) else Color(0xFFE5E5EA).copy(alpha = 0.5f)))
                            }
                        }
                    }
                }
            }
        }
    }

    // Branch picker sheet
    if (showBranchSheet) {
        IosBottomSheet(isDark = isDark, onDismissRequest = { showBranchSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFEFEFEF)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(com.ahamai.app.R.drawable.trae_git_branches),
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(primaryText)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Switch branch", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
                        Text(
                            connectedRepo.ifBlank { "" },
                            fontSize = 10.sp, color = muted, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA)))
                if (branchesLoading) {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        IOSSpinner(size = 18.dp, color = muted)
                    }
                } else if (branches.isEmpty()) {
                    Text("No branches found.", color = muted, fontSize = 12.sp, fontFamily = InterFamily, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        itemsIndexed(branches) { idx, br ->
                            val isCur = br == prefs.getConnectedBranch()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            showBranchSheet = false
                                            busy = "Switching to $br..."
                                            val dir = withContext(Dispatchers.IO) {
                                                GitHubClient.downloadRepo(context, prefs.getGithubToken(), connectedRepo, br)
                                            }
                                            busy = null
                                            if (dir != null) {
                                                prefs.saveConnectedRepo(connectedRepo, br)
                                                AuthManager.backupGithubToken(context)
                                                onOpenProject(dir, connectedRepo.substringAfterLast('/'), null)
                                            }
                                        }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isCur) primaryText else Color.Transparent.copy(alpha = 0f), shape = RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCur) {
                                        Icon(com.ahamai.app.ui.icons.Lucide.Check, null, tint = if (isDark) Color.Black else Color.White, modifier = Modifier.size(10.dp))
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    br, fontSize = 13.sp,
                                    fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
                                    fontFamily = InterFamily,
                                    color = if (isCur) primaryText else primaryText,
                                    modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                if (!isCur) {
                                    Icon(com.ahamai.app.ui.icons.Lucide.ChevronRight, null, tint = muted.copy(alpha = 0.3f), modifier = Modifier.size(13.dp))
                                }
                            }
                            if (idx < branches.size - 1) {
                                Box(Modifier.fillMaxWidth().padding(start = 44.dp, end = 20.dp).height(0.5.dp).background(if (isDark) Color(0xFF1F1F1F).copy(alpha = 0.4f) else Color(0xFFE5E5EA).copy(alpha = 0.5f)))
                            }
                        }
                    }
                }
            }
        }
    }

    // Connectors attachment sheet — opened from the + menu / footer chip
    if (showConnectorsSheet) {
        com.ahamai.app.screens.ConnectorsSheet(
            isDark = isDark,
            onManageAll = onOpenConnectors,
            onDismiss = { showConnectorsSheet = false }
        )
    }
}

@Composable
private fun FeatureCard(
    icon: ImageVector, title: String, desc: String,
    accent: Color, primaryText: Color, muted: Color, isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) Color(0xFF141414).copy(alpha = 0.5f) else Color(0xFFF5F5F7).copy(alpha = 0.7f)
    val borderC = if (isDark) Color(0xFF1F1F1F).copy(alpha = 0.4f) else Color(0xFFE5E5EA).copy(alpha = 0.6f)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, borderC)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
                Spacer(Modifier.height(2.dp))
                Text(desc, fontSize = 11.sp, color = muted, fontFamily = InterFamily, maxLines = 2, lineHeight = 14.sp)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = muted.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun FooterChip(icon: ImageVector, label: String, muted: Color, primaryText: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = muted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, color = primaryText, fontFamily = JetBrainsMonoFamily, maxLines = 1)
    }
}
