package com.ahamai.app.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.ahamai.app.ui.icons.AdminIcons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ModeEdit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.ahamai.app.data.StoredMessage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ahamai.app.data.ApiClient
import com.ahamai.app.ui.components.IosBottomSheet
import com.ahamai.app.ui.components.IosContextMenuAction
import com.ahamai.app.ui.components.IosDialog
import com.ahamai.app.ui.components.IosFloatingContextMenu
import com.ahamai.app.ui.components.IosRenameDialog
import com.ahamai.app.data.ApiConfig
import com.ahamai.app.data.ChatHistoryManager
import com.ahamai.app.data.ChatSession
import com.ahamai.app.data.ProjectManager
import com.ahamai.app.service.RunningTasks
import com.ahamai.app.ui.icons.Lucide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * Chat-history list item only. Workspace / agent tasks live on [AgentHomeScreen]
 * so chat and agent history stay separate.
 */
private sealed class HistoryItem {
    abstract val id: String
    abstract val title: String
    abstract val time: Long
    abstract val pinned: Boolean
    abstract val matchedMessage: String?

    data class Chat(val session: ChatSession, override val matchedMessage: String? = null) : HistoryItem() {
        override val id get() = session.id
        override val title get() = session.title
        override val time get() = session.lastUpdated
        override val pinned get() = session.pinned
    }

    /** A chat currently streaming that isn't saved to history yet. */
    data class Running(
        override val id: String,
        val type: RunningTasks.Type,
        override val title: String,
        override val time: Long,
        override val matchedMessage: String? = null
    ) : HistoryItem() {
        override val pinned get() = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatHistoryScreen(
    modelName: String,
    baseUrl: String,
    apiKey: String,
    avatarBase64: String = "",
    onModelChange: (String) -> Unit,
    onNewChat: () -> Unit,
    onOpenChat: (ChatSession) -> Unit,
    onNewWorkspace: () -> Unit,
    onOpenWorkspace: (dir: String, name: String) -> Unit,
    onProfile: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // App palette — same scheme, iOS Settings / Messages feel
    val primaryText = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk else com.ahamai.app.ui.theme.ChatPalette.LightInk
    val mutedText = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInkSecondary else com.ahamai.app.ui.theme.ChatPalette.LightInkSecondary
    val faintText = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInkTertiary else com.ahamai.app.ui.theme.ChatPalette.LightInkTertiary
    val rowSurface = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkSurface else com.ahamai.app.ui.theme.ChatPalette.LightSurfaceElevated
    val pageBg = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkBg else com.ahamai.app.ui.theme.ChatPalette.LightBg
    val separator = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkBorder else com.ahamai.app.ui.theme.ChatPalette.LightBorder

    // Reload when local edits, cloud restore (StateFlow), or running tasks change.
    var refresh by remember { mutableIntStateOf(0) }
    val runningCount = RunningTasks.tasks.size
    val historyRefreshSignal by com.ahamai.app.data.HistoryRefresh.signalFlow.collectAsState()
    // Keep last list while a background recompute runs (no blank flash)
    var items by remember { mutableStateOf(buildMergedList(context)) }
    LaunchedEffect(refresh, runningCount, historyRefreshSignal) {
        val next = withContext(Dispatchers.Default) { buildMergedList(context) }
        items = next
    }
    // First paint + anytime restore finishes: force a local load (covers cold cache)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            runCatching { ChatHistoryManager.loadSessions(context) }
        }
        items = withContext(Dispatchers.Default) { buildMergedList(context) }
    }

    // Auto-refresh when the screen resumes (e.g. after clearing chats from Profile).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                refresh++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Model picker
    var showModelSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }

    // Context menu / rename
    var menuItem by remember { mutableStateOf<HistoryItem?>(null) }
    var renameItem by remember { mutableStateOf<HistoryItem?>(null) }

    // Search state
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // Strips markdown syntax so search snippets read as clean plain text (no **, #, `, links, etc.)
    fun stripMarkdown(s: String): String = s
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), " ")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
        .replace(Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("[*_~>|#]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
    // Helper: find the first occurrence of query in content and return a compact snippet
    fun snippetAround(content0: String, query: String, maxLen: Int): String {
        val content = stripMarkdown(content0)
        val idx = content.indexOf(query, ignoreCase = true)
        if (idx < 0) return content.take(maxLen).replace('\n', ' ').trim()
        val start = maxOf(0, idx - (maxLen / 4))
        val end = minOf(content.length, idx + query.length + (maxLen / 3))
        val raw = content.substring(start, end).replace('\n', ' ').trim()
        return if (start > 0) "...$raw" else raw
    }
    // Helper: copy the item with a matchedMessage, preserving the concrete type
    fun HistoryItem.copyWithMatch(snippet: String?): HistoryItem = when (this) {
        is HistoryItem.Chat -> copy(matchedMessage = snippet)
        is HistoryItem.Running -> copy(matchedMessage = snippet)
    }
    // Search through both titles AND message content — extract a matching snippet when found.
    val filteredItems = if (searchQuery.isBlank()) items
                        else items.mapNotNull { item ->
        val q = searchQuery.trim()
        // Title match
        if (item.title.contains(q, ignoreCase = true)) {
            item.copyWithMatch(null)
        }
        // Message content match (only for Chat items)
        else if (item is HistoryItem.Chat) {
            val firstMatch = item.session.messages.firstOrNull { msg ->
                msg.content.contains(q, ignoreCase = true)
            }
            if (firstMatch != null) {
                // Extract a concise snippet around the match
                val snippet = snippetAround(firstMatch.content, q, 120)
                item.copyWithMatch(snippet)
            } else null
        } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Filled.Close else com.ahamai.app.ui.icons.AdminIcons.BootstrapSearch,
                            contentDescription = if (searchActive) "Close search" else "Search chats",
                            tint = mutedText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = searchActive,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.98f, animationSpec = tween(200)))
                                .togetherWith(fadeOut(tween(120)))
                        },
                        label = "search_bar"
                    ) { active ->
                        if (active) {
                            // iOS-style search field
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(rowSurface)
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search",
                                        fontSize = 16.sp,
                                        color = mutedText,
                                        fontFamily = com.ahamai.app.ui.theme.InterFamily
                                    )
                                }
                                BasicTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        fontSize = 16.sp,
                                        color = primaryText,
                                        fontFamily = com.ahamai.app.ui.theme.InterFamily
                                    ),
                                    cursorBrush = SolidColor(com.ahamai.app.ui.theme.ChatPalette.Accent),
                                    singleLine = true
                                )
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                ModeToggle(
                                    selected = "Chat",
                                    isDark = isDark,
                                    onChat = {},
                                    onAgent = { onNewWorkspace() }
                                )
                            }
                        }
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AccountCircle,
                                contentDescription = "Profile",
                                tint = mutedText,
                                modifier = Modifier.size(28.dp)
                            )
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
            // iOS-style circular compose button
            val fabBg = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
            val fabIcon = if (isDark) Color(0xFF141414) else Color(0xFFECECEC)
            Box(
                modifier = Modifier
                    .padding(end = 10.dp, bottom = 10.dp)
                    .size(56.dp)
                    .shadow(
                        elevation = if (isDark) 0.dp else 8.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.10f),
                        spotColor = Color.Black.copy(alpha = 0.16f)
                    )
                    .clip(CircleShape)
                    .background(fabBg)
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onNewChat() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AdminIcons.NewChatIcon,
                    contentDescription = "New chat",
                    tint = fabIcon,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        containerColor = pageBg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(padding)
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total <= -140f) onNewWorkspace() }
                    ) { _, dragAmount -> total += dragAmount }
                }
        ) {
            // Model chip — compact iOS secondary control under the nav
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(20.dp))
                    .background(rowSurface)
                    .clickable { showModelSheet = true }
                    .padding(vertical = 6.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelName.ifBlank { "Select model" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = mutedText,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Change model",
                    tint = mutedText,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (searchActive && searchQuery.isNotBlank()) "No Results"
                                   else "No Chats Yet",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryText,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (searchActive && searchQuery.isNotBlank()) "Try a different keyword"
                                   else "Tap + to start a new conversation",
                            fontSize = 14.sp,
                            color = mutedText,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily
                        )
                    }
                }
            } else {
                // iOS Settings-style inset grouped list (smooth LazyColumn)
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    userScrollEnabled = true
                ) {
                    item(key = "header_recents") {
                        Text(
                            text = "RECENTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = mutedText,
                            letterSpacing = 0.4.sp,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 4.dp)
                        )
                    }
                    // Real lazy items for smooth scroll; iOS grouped card corners per position
                    items(
                        items = filteredItems,
                        key = { it.id },
                        contentType = { it::class.simpleName }
                    ) { item ->
                        val index = filteredItems.indexOfFirst { it.id == item.id }
                        val isFirst = index == 0
                        val isLast = index == filteredItems.lastIndex
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isFirst && isLast) Modifier.clip(RoundedCornerShape(14.dp))
                                    else if (isFirst) Modifier.clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                                    else if (isLast) Modifier.clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                                    else Modifier
                                )
                                .background(rowSurface)
                                .animateItem(
                                    fadeInSpec = tween(200, easing = FastOutSlowInEasing),
                                    fadeOutSpec = tween(120, easing = FastOutSlowInEasing)
                                )
                        ) {
                            HistoryRow(
                                item = item,
                                running = RunningTasks.isRunning(item.id),
                                primaryText = primaryText,
                                mutedText = mutedText,
                                faintText = faintText,
                                searchQuery = searchQuery,
                                onClick = {
                                    when (item) {
                                        is HistoryItem.Chat -> onOpenChat(item.session)
                                        is HistoryItem.Running -> { /* streaming chat opens from session when saved */ }
                                    }
                                },
                                onLongPress = { if (item !is HistoryItem.Running) menuItem = item }
                            )
                            if (!isLast) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp)
                                        .height(0.5.dp)
                                        .background(separator.copy(alpha = 0.55f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Long-press: Rename · Pin/Unpin · Delete (chat has no export)
    if (menuItem != null) {
        val item = menuItem!!
        val actions = buildList {
            add(
                IosContextMenuAction(
                    label = "Rename",
                    icon = Lucide.Edit,
                    onClick = {
                        renameItem = item
                        menuItem = null
                    }
                )
            )
            if (item is HistoryItem.Chat) {
                add(
                    IosContextMenuAction(
                        label = if (item.pinned) "Unpin" else "Pin",
                        icon = if (item.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        onClick = {
                            ChatHistoryManager.togglePin(context, item.id)
                            refresh++
                            menuItem = null
                        }
                    )
                )
            }
            add(
                IosContextMenuAction(
                    label = "Delete",
                    icon = Lucide.Trash2,
                    destructive = true,
                    onClick = {
                        when (item) {
                            is HistoryItem.Chat -> ChatHistoryManager.deleteSession(context, item.id)
                            is HistoryItem.Running -> {}
                        }
                        refresh++
                        menuItem = null
                    }
                )
            )
        }
        IosFloatingContextMenu(
            isDark = isDark,
            title = item.title.ifBlank { "Untitled" },
            onDismissRequest = { menuItem = null },
            actions = actions
        )
    }

    // Rename — same blur + floating card style as context menu
    if (renameItem != null) {
        val item = renameItem!!
        IosRenameDialog(
            isDark = isDark,
            initialName = item.title,
            onDismissRequest = { renameItem = null },
            onSave = { name ->
                when (item) {
                    is HistoryItem.Chat -> ChatHistoryManager.renameSession(context, item.id, name)
                    is HistoryItem.Running -> {}
                }
                refresh++
                renameItem = null
            }
        )
    }

    // Model picker sheet
    if (showModelSheet) {
        IosBottomSheet(isDark = isDark, onDismissRequest = { showModelSheet = false }, sheetState = sheetState) {
            ModelPickerContent(
                models = models,
                loading = modelsLoading,
                error = modelsError,
                current = modelName,
                isDark = isDark,
                onSelect = { m ->
                    onModelChange(m)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showModelSheet = false }
                }
            )
        }
        LaunchedEffect(showModelSheet) {
            if (!showModelSheet) return@LaunchedEffect
            modelsError = null
            // Instant paint from cache / config
            val instant = ApiConfig.cachedChatModels()
                .ifEmpty { ApiConfig.listChatModelsFromConfig(context) }
            if (instant.isNotEmpty()) models = instant
            modelsLoading = models.isEmpty()
            val res = withContext(Dispatchers.IO) { ApiConfig.listChatModels(context) }
            res.fold(
                onSuccess = { models = it },
                onFailure = {
                    if (models.isEmpty()) modelsError = it.message ?: "Failed to load models"
                }
            )
            modelsLoading = false
        }
    }
}

/** Chat sessions only (+ live streaming chats). Workspaces are on Agent home. */
private fun buildMergedList(context: android.content.Context): List<HistoryItem> {
    val chats = ChatHistoryManager.loadSessions(context).map { HistoryItem.Chat(it) }
    val knownIds = chats.map { it.id }.toSet()
    // Only CHAT runs here — workspace runs belong on the Agent screen.
    val orphanRunning = RunningTasks.tasks
        .filter { it.type == RunningTasks.Type.CHAT && it.id !in knownIds }
        .map { HistoryItem.Running(it.id, it.type, it.title, it.startedAt) }
    return (orphanRunning + chats).sortedWith(
        compareByDescending<HistoryItem> { RunningTasks.isRunning(it.id) }
            .thenByDescending { it.pinned }
            .thenByDescending { it.time }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryRow(
    item: HistoryItem,
    running: Boolean,
    primaryText: Color,
    mutedText: Color,
    faintText: Color,
    searchQuery: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    // iOS list row — title + subtitle, chevron affordance via spacing
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        "Pinned",
                        tint = mutedText,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = item.title.ifBlank { "Untitled" },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily,
                    letterSpacing = (-0.2f).sp,
                    modifier = Modifier.weight(1f, fill = false)
                )
            }
            Spacer(Modifier.height(3.dp))
            val snippet = item.matchedMessage
            if (snippet != null) {
                HighlightedText(
                    text = snippet,
                    highlight = searchQuery,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        color = faintText,
                        fontFamily = com.ahamai.app.ui.theme.InterFamily
                    ),
                    maxLines = 1
                )
            } else {
                Text(
                    text = when (item) {
                        is HistoryItem.Running -> "Working…"
                        else -> formatTime(item.time)
                    },
                    fontSize = 13.sp,
                    color = faintText,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily
                )
            }
        }
        if (running) {
            Spacer(Modifier.width(10.dp))
            RunningPill()
        }
    }
}

/** A small green circular spinner shown while a task is running. */
@Composable
private fun RunningPill() {
    val green = Color(0xFF34C759)
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(13.dp),
            strokeWidth = 1.5.dp,
            color = green,
            strokeCap = StrokeCap.Round
        )
        Spacer(Modifier.width(6.dp))
        Text("Running", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = green)
    }
}

/** Renders text with the search query substring highlighted in blue (iOS-style). */
@Composable
private fun HighlightedText(
    text: String,
    highlight: String,
    style: androidx.compose.ui.text.TextStyle,
    maxLines: Int = 2,
    isDark: Boolean = isSystemInDarkTheme()
) {
    if (highlight.isBlank()) {
        Text(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        return
    }
    val annotated = buildAnnotatedString {
        var remaining = text
        while (true) {
            val idx = remaining.indexOf(highlight, ignoreCase = true)
            if (idx < 0) {
                append(remaining)
                break
            }
            if (idx > 0) append(remaining.substring(0, idx))
            pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            )
            append(remaining.substring(idx, idx + highlight.length))
            pop()
            remaining = remaining.substring(idx + highlight.length)
        }
    }
    Text(annotated, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestamp))
    }
}
