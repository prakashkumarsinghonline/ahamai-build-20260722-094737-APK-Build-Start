package com.ahamai.app.screens

import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AccountCircle
import com.ahamai.app.ui.icons.AdminIcons
import com.ahamai.app.ui.icons.Lucide
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.ApiClient
import com.ahamai.app.data.ApiMessage
import com.ahamai.app.data.EdgeTtsClient
import com.ahamai.app.data.ImageUtils
import com.ahamai.app.data.RemoteConfigManager
import com.ahamai.app.data.UsageTracker
import com.ahamai.app.data.WebTools
import com.ahamai.app.ui.components.IosBottomSheet
import com.ahamai.app.ui.components.MarkdownText
import com.ahamai.app.ui.chat.ChatHomeWelcome
import com.ahamai.app.ui.chat.ChatMotion
import com.ahamai.app.ui.chat.ChatRelatedQueries
import com.ahamai.app.ui.chat.ChatSourcesBar
import com.ahamai.app.ui.chat.ChatThinkingPanel
import com.ahamai.app.ui.chat.ChatToolStatusIndicator
import com.ahamai.app.ui.chat.ChatTypingIndicator
import com.ahamai.app.ui.chat.chatPressable
import com.ahamai.app.ui.chat.HapticOnPress
import com.ahamai.app.ui.chat.rememberChatHaptics
import androidx.compose.material.icons.filled.Image
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Table
import com.itextpdf.layout.element.Cell
import com.itextpdf.layout.element.Image
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.io.image.ImageDataFactory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


/**
 * Scira.ai-inspired warm palette (converted from their oklch design tokens).
 * Light theme uses a warm brown primary; dark theme uses a warm cream. Used as the
 * single accent across the chat screen (send button, cursor, citations, sources, pixel anim).
 */
internal object Scira {
    // Monochrome accent — near-black in light, near-white in dark (iOS-style, no colour).
    fun accent(isDark: Boolean) = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    fun onAccent(isDark: Boolean) = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F7)
    fun accentSoft(isDark: Boolean) =
        if (isDark) Color(0xFFECECEC).copy(alpha = 0.14f) else Color(0xFF141414).copy(alpha = 0.06f)
    fun bg(isDark: Boolean) = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
}

@Immutable
data class ToolStatus(
    val type: String,   // "SEARCH" or "READ"
    val query: String,  // search query or URL
    val domain: String? = null  // domain for favicon
)
@Immutable
data class SourceInfo(
    val title: String,
    val url: String,
    val domain: String,
    val favicon: String? = null,
    val snippet: String? = null,
    val imageUrl: String? = null
)

@Immutable
data class ChatMessage(
    val role: String,
    val content: String,
    val reasoning: String? = null,
    val isStreaming: Boolean = false,
    val reasoningStartTime: Long? = null,
    val reasoningDurationMs: Long? = null,
    val images: List<String> = emptyList(),
    val suggestedActions: List<String> = emptyList(),
    val toolStatus: ToolStatus? = null,
    val generatingImagePrompt: String? = null,
    val sources: List<SourceInfo> = emptyList(),
    val processingSteps: List<String> = emptyList(),
    val currentStep: Int = 0,
    // Stable unique id for LazyColumn keys. copy() preserves it, so a streaming message
    // keeps the SAME id across all its token updates → no re-layout/flicker of the whole list.
    val id: String = java.util.UUID.randomUUID().toString()
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(
    modelName: String,
    baseUrl: String,
    apiKey: String,
    selectedVoice: String,
    initialSession: com.ahamai.app.data.ChatSession? = null,
    avatarBase64: String = "",
    onModelChange: (String) -> Unit,
    onProfile: () -> Unit,
    onBack: () -> Unit = {},
    onOpenProject: (android.net.Uri) -> Unit = {},
    onSwitchToAgent: (String) -> Unit = {},
    visionBaseUrl: String = "",
    visionApiKey: String = "",
    visionModel: String = ""
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Hardware/gesture back returns to the chat history list instead of exiting the app.
    androidx.activity.compose.BackHandler(enabled = true) { onBack() }

    // Session tracking for history
    val sessionId = remember { initialSession?.id ?: "chat_${System.currentTimeMillis()}" }
    var sessionTitle by remember { mutableStateOf(initialSession?.title) }

    var messages by remember {
        val initial: List<ChatMessage> = initialSession?.messages?.map { sm ->
            ChatMessage(
                role = sm.role,
                content = sm.content,
                reasoning = sm.reasoning,
                images = sm.images,
                sources = sm.sources.map { s ->
                    SourceInfo(title = s.title, url = s.url, domain = s.domain, snippet = s.snippet, imageUrl = s.imageUrl)
                }
            )
        } ?: emptyList()
        mutableStateOf(initial)
    }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var relatedQueries by remember { mutableStateOf<List<String>>(emptyList()) }

    // Streaming job holder — captured so the user can STOP a response mid-stream by
    // tapping the send button (which becomes a stop button while loading). Cancelling
    // this job aborts the in-flight API stream and finalises whatever was received.
    var streamingJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Live voice mode
    var isLiveMode by remember { mutableStateOf(false) }
    var isVoiceListening by remember { mutableStateOf(false) }
    var showVoiceCall by remember { mutableStateOf(false) }
    var partialSpeechText by remember { mutableStateOf("") }
    var liveSendPending by remember { mutableStateOf<String?>(null) }

    // Model picker state
    var showModelSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelsLoading by remember { mutableStateOf(false) }
    var modelsError by remember { mutableStateOf<String?>(null) }

    // Attachment / image state
    val context = LocalContext.current
    val chatHaptics = rememberChatHaptics()
    val speechHelper = remember { com.ahamai.app.data.SpeechHelper(context) }
    var attachments by remember { mutableStateOf<List<String>>(emptyList()) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var activeAgent by remember { mutableStateOf<com.ahamai.app.data.Agent?>(null) }
    val attachSheetState = rememberModalBottomSheetState()
    var recentPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(6)
    ) { uris ->
        if (uris.isNotEmpty()) attachments = (attachments + uris.map { it.toString() }).distinct().take(6)
        showAttachSheet = false
    }

    // Any-file picker (docs, PDFs, CSV, code…) — like the agent's Files option. The picked
    // file is copied into app storage immediately (URIs from GetContent are short-lived), so
    // it's still readable when the message is sent and when its content is extracted for the LLM.
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        showAttachSheet = false
        if (uri != null) scope.launch {
            val path = withContext(Dispatchers.IO) { copyUriToChatCache(context, uri) }
            if (path != null) attachments = (attachments + path).distinct().take(6)
            else android.widget.Toast.makeText(context, "Couldn't attach file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Audio permission launcher for live mode
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isLiveMode = true
            speechHelper.startListening()
        }
    }

    // Camera permission launcher
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) cameraUri?.let { attachments = (attachments + it.toString()).take(6) }
        showAttachSheet = false
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val uri = createCameraUri(context)
                cameraUri = uri
                takePicture.launch(uri)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Camera not available", android.widget.Toast.LENGTH_SHORT).show()
                showAttachSheet = false
            }
        } else {
            android.widget.Toast.makeText(context, "Camera permission required", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Check for background task results
    LaunchedEffect(Unit) {
        // Collect speech recognition events
        launch {
            speechHelper.events.collect { event ->
                when (event) {
                    is com.ahamai.app.data.SpeechHelper.SpeechEvent.Partial -> {
                        partialSpeechText = event.text
                    }
                    is com.ahamai.app.data.SpeechHelper.SpeechEvent.Final -> {
                        partialSpeechText = ""
                        if (event.text.isNotBlank() && !isLoading) {
                            inputText = event.text
                            if (isLiveMode) {
                                liveSendPending = event.text
                            }
                        }
                    }
                    is com.ahamai.app.data.SpeechHelper.SpeechEvent.Started -> {
                        isVoiceListening = true
                    }
                    is com.ahamai.app.data.SpeechHelper.SpeechEvent.Stopped -> {
                        isVoiceListening = false
                        // In live mode: re-listen after AI finishes responding
                    }
                    is com.ahamai.app.data.SpeechHelper.SpeechEvent.Error -> {
                        isVoiceListening = false
                        partialSpeechText = ""
                    }
                }
            }
        }

    }

    // Gentle success haptic when a response finishes
    var wasLoading by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (wasLoading && !isLoading && messages.lastOrNull()?.role == "assistant") {
            chatHaptics.success()
        }
        wasLoading = isLoading
    }

    // Sticky bottom — user drag unsticks; only re-stick when user is truly at bottom.
    // Image/WebView size changes must NOT yank the list (no auto-scroll unless stick).
    fun listNearEnd(thresholdPx: Int = 120): Boolean {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull() ?: return true
        if (info.totalItemsCount == 0) return true
        if (last.index < info.totalItemsCount - 1) return false
        return (last.offset + last.size) <= (info.viewportEndOffset + thresholdPx)
    }
    var stickToBottom by remember { mutableStateOf(true) }
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listState) {
        snapshotFlow { isDragged }.collect { dragging ->
            if (dragging) stickToBottom = false
        }
    }
    // Re-stick ONLY when user finishes a gesture near the bottom (never mid-content).
    LaunchedEffect(listState) {
        snapshotFlow {
            Triple(isDragged, listState.isScrollInProgress, listNearEnd(100))
        }.collect { (dragging, scrolling, near) ->
            if (!dragging && !scrolling && near) stickToBottom = true
        }
    }

    // ──────────────────────────────────────────────
    // persistSession must be defined BEFORE it is used in LaunchedEffect below
    // ──────────────────────────────────────────────
    fun persistSession() {
        val msgs = messages
            .filter { it.content.isNotBlank() || it.images.isNotEmpty() }
            .map {
                com.ahamai.app.data.StoredMessage(
                    it.role, it.content, it.reasoning, it.images,
                    it.sources.map { s -> com.ahamai.app.data.StoredSource(s.title, s.url, s.domain, s.snippet, s.imageUrl) }
                )
            }
        if (msgs.isEmpty()) return
        val title = sessionTitle
            ?: msgs.firstOrNull { it.role == "user" }?.content?.trim()?.take(40)?.ifBlank { null }
            ?: "New Chat"
        com.ahamai.app.data.ChatHistoryManager.saveSession(
            context,
            com.ahamai.app.data.ChatSession(sessionId, title, System.currentTimeMillis(), msgs)
        )
    }

    // Opening a history chat: jump to bottom once (no fight with user later)
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) return@LaunchedEffect
        delay(16)
        val last = (messages.size - 1).coerceAtLeast(0)
        runCatching { listState.scrollToItem(last) }
        stickToBottom = true
    }

    // Related: only scroll if user is still following the bottom
    LaunchedEffect(relatedQueries) {
        if (relatedQueries.isEmpty() || !stickToBottom) return@LaunchedEffect
        delay(100)
        if (!stickToBottom) return@LaunchedEffect
        val last = listState.layoutInfo.totalItemsCount - 1
        if (last >= 0) runCatching { listState.animateScrollToItem(last) }
    }

    // New messages: soft scroll only while stick-to-bottom.
    // During active streaming (isLoading) we let the streaming effect handle ALL scroll
    // so there is no fight between animateScrollToItem and scrollBy.
    LaunchedEffect(Unit) {
        var prev = messages.size
        snapshotFlow { messages.size }.collect { size ->
            if (size == 0 || size == prev) { prev = size; return@collect }
            prev = size
            if (!stickToBottom || isLoading) return@collect  // skip during stream — streaming effect handles it
            delay(24)
            if (!stickToBottom || isLoading) return@collect
            val last = (size - 1).coerceAtLeast(0)
            val info = listState.layoutInfo
            val lastVis = info.visibleItemsInfo.lastOrNull()
            if (lastVis != null && lastVis.index >= last - 1) {
                runCatching { listState.animateScrollToItem(last) }
            } else if (stickToBottom && !isLoading) {
                runCatching { listState.scrollToItem(last) }
            }
        }
    }
    // Streaming tokens: ONLY scrollBy overshoot — smooth, never yank to top of item.
    // Ignores layout thrash from images/WebView (length only, not full size).
    LaunchedEffect(isLoading) {
        if (!isLoading) return@LaunchedEffect
        var lastLen = 0
        snapshotFlow {
            messages.lastOrNull()?.content?.length ?: 0
        }.collect { len ->
            if (!stickToBottom || isDragged) return@collect
            // A new item arrived (len reset to 0) — just update lastLen and skip
            if (len < lastLen) { lastLen = len; return@collect }
            if (len == lastLen) return@collect
            if (lastLen > 0 && len - lastLen < 4) return@collect
            lastLen = len
            val info = listState.layoutInfo
            val lastIdx = info.totalItemsCount - 1
            if (lastIdx < 0) return@collect
            val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@collect
            if (lastVisible.index < lastIdx) {
                // Don't jump during stream if item not visible — user scrolled away
                return@collect
            }
            val overshoot = (lastVisible.offset + lastVisible.size) - info.viewportEndOffset
            if (overshoot > 6) {
                runCatching { listState.scrollBy(overshoot.toFloat()) }
            }
        }
    }

    // Persist messages periodically during streaming to prevent data loss
    LaunchedEffect(messages.size) {
        if (isLoading && messages.isNotEmpty()) {
            delay(2000) // debounce: save at most every 2s during streaming
            withContext(Dispatchers.IO) { persistSession() }
        }
    }

    // Save chat session to history whenever a response completes
    LaunchedEffect(messages.size, isLoading) {
        if (!isLoading && messages.size >= 2) {
            val lastMsg = messages.lastOrNull()
            if (lastMsg != null && lastMsg.role == "assistant" && lastMsg.content.isNotBlank() && !lastMsg.isStreaming) {
                val firstUserMsg = messages.firstOrNull { it.role == "user" }?.content ?: ""

                // Save immediately with current/fallback title (so chat is never lost)
                withContext(Dispatchers.IO) { persistSession() }

                // Generate AI title (only once) then re-save with proper title
                if (sessionTitle == null && firstUserMsg.isNotBlank()) {
                    val title = withContext(Dispatchers.IO) {
                        com.ahamai.app.data.ChatHistoryManager.generateTitle(
                            baseUrl, apiKey, modelName, firstUserMsg
                        )
                    }
                    sessionTitle = title
                    withContext(Dispatchers.IO) {
                        com.ahamai.app.data.ChatHistoryManager.saveSession(
                            context,
                            com.ahamai.app.data.ChatSession(
                                id = sessionId,
                                title = title,
                                lastUpdated = System.currentTimeMillis(),
                                messages = messages.map { m ->
                                    com.ahamai.app.data.StoredMessage(
                                        role = m.role, content = m.content, reasoning = m.reasoning, images = m.images,
                                        sources = m.sources.map { s ->
                                            com.ahamai.app.data.StoredSource(s.title, s.url, s.domain, s.snippet, s.imageUrl)
                                        }
                                    )
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    // Sends a message (with optional images) and streams the assistant reply.
    fun send(text: String, imgs: List<String> = attachments) {
        val trimmed = text.trim()
        if ((trimmed.isBlank() && imgs.isEmpty()) || isLoading) return
        stickToBottom = true
        messages = messages + ChatMessage(role = "user", content = trimmed, images = imgs)
        inputText = ""
        attachments = emptyList()
        relatedQueries = emptyList()
        isLoading = true
        com.ahamai.app.service.RunningTasks.start(
            id = sessionId,
            type = com.ahamai.app.service.RunningTasks.Type.CHAT,
            title = sessionTitle ?: trimmed.take(40).ifBlank { "Chat" }
        )
        // Save right away so the chat appears in History (with a "Running" status) the
        // instant it starts — even before the first token arrives.
        persistSession()

        // Launch on the process-wide scope (not the composition scope) so the response
        // keeps streaming and gets saved even if the user leaves this screen or minimizes.
        // The job is captured in `streamingJob` so the stop button can cancel it.
        streamingJob = com.ahamai.app.AppScope.scope.launch {
            // Build the API transcript, converting any image Uris to base64 data URLs (vision).
            val convo = withContext(Dispatchers.IO) {
                messages.filter { it.content.isNotBlank() || it.images.isNotEmpty() }.map { msg ->
                    buildApiMessageForChat(context, msg)
                }
            }
            messages = messages + ChatMessage(role = "assistant", content = "", reasoning = null, isStreaming = true)

            val dateHeader = realtimeContextHeader()
            val agentPrompt = if (activeAgent != null) {
                val toolDescriptions = activeAgent!!.toolIds.mapNotNull { id ->
                    com.ahamai.app.data.ToolsManager.getToolById(id)?.let { tool ->
                        "- ${tool.name}: ${tool.description}"
                    }
                }.joinToString("\n")
                "${activeAgent!!.systemPrompt}\n\nYou have access to these tools:\n$toolDescriptions\n\nWhen you need to use a tool, write Python code that implements it. The user's app can execute Python code directly."
            } else null
            val customPrompt = if (agentPrompt == null) {
                com.ahamai.app.data.PreferencesManager(context).getCustomPrompt()
            } else ""
            // Chat system prompt is fully remote (Admin → Control / Firestore config/systemPrompts.chat).
            // No hardcoded fallback — paste the full prompt in the admin panel.
            val basePrompt = agentPrompt ?: RemoteConfigManager.chatSystemPrompt
            val systemPrompt = buildString {
                append(dateHeader)
                if (basePrompt.isNotBlank()) {
                    append("\n\n")
                    append(basePrompt)
                }
                append("\n\n")
                append(MATH_LATEX_MANDATE)
                if (customPrompt.isNotBlank()) {
                    append("\n\nAdditional instructions:\n")
                    append(customPrompt)
                }
            }
            val working = mutableListOf<ApiMessage>()
            working.add(ApiMessage("system", systemPrompt))
            working.addAll(convo)

            // ── Smart context trimming ──
            // If the conversation is very long, sending every message would blow past
            // the model's context window. We estimate token count (~4 chars/token) and
            // if it exceeds a safe budget, we keep the system message + the first user
            // message (for topic context) + the most recent messages, dropping middle
            // ones with a brief note so the model knows context was trimmed.
            val maxChars = 100_000 // ~25k tokens — safe for most models
            val totalChars = working.sumOf { it.text.length }
            if (totalChars > maxChars && working.size > 6) {
                val systemMsg = working.first()
                val firstUser = working.firstOrNull { it.role == "user" }
                val budget = maxChars - systemMsg.text.length - (firstUser?.text?.length ?: 0) - 200 // reserve for trim note
                // Walk backwards from the end, keeping messages until we hit the budget
                val tail = mutableListOf<ApiMessage>()
                var tailChars = 0
                for (i in working.indices.reversed()) {
                    val msg = working[i]
                    if (msg === systemMsg || msg === firstUser) continue
                    if (tailChars + msg.text.length > budget) break
                    tail.add(0, msg)
                    tailChars += msg.text.length
                }
                val trimmed = mutableListOf<ApiMessage>()
                trimmed.add(systemMsg)
                if (firstUser != null) trimmed.add(firstUser)
                trimmed.add(ApiMessage("system", "[Note: Some earlier messages were trimmed to fit the context window. The conversation's key context is preserved.]"))
                trimmed.addAll(tail)
                working.clear()
                working.addAll(trimmed)
            }

            // Keep the process alive if the app is minimized mid-stream (handled by
            // BackgroundTaskManager via the RunningTasks registry set from isLoading).

            // Route image-bearing turns to the dedicated vision endpoint when configured.
            // When no dedicated vision endpoint is set, fall back to the configured vision
            // provider from ApiConfig (same logic as the agent's analyzeImageVisionFirst),
            // so images are always sent to a model that supports vision — even if the user's
            // selected chat model is text-only. If no vision endpoint exists at all, use the
            // regular chat model (some models like GPT-4o handle both text and images).
            val hasImages = imgs.isNotEmpty() || messages.any { it.images.isNotEmpty() }
            val useVision = visionBaseUrl.isNotBlank() && hasImages
            val routed = if (useVision) null
                else if (hasImages) {
                    // Fallback: use the vision endpoint from config, then the chat endpoint
                    val visionEp = com.ahamai.app.data.ApiConfig.vision(context)
                    if (visionEp != null) visionEp
                    else com.ahamai.app.data.ApiConfig.resolveForModel(context, modelName, agent = false)
                } else com.ahamai.app.data.ApiConfig.resolveForModel(context, modelName, agent = false)
            val reqBase  = if (useVision) visionBaseUrl else (routed?.baseUrl?.takeIf { it.isNotBlank() } ?: baseUrl)
            val reqModel = if (useVision) visionModel.ifBlank { modelName } else (routed?.model?.takeIf { it.isNotBlank() } ?: modelName)
            // Always get the freshest key from ApiConfig so blacklisted/rate-limited keys
            // are automatically skipped. Falls back to the session key for custom endpoints.
            val sessionKey = if (useVision) visionApiKey else (routed?.apiKey?.takeIf { it.isNotBlank() } ?: apiKey)
            val reqKey = com.ahamai.app.data.ApiConfig.getNextKey(reqBase)
                .takeIf { !it.isNullOrBlank() } ?: sessionKey

            try {
                var answered = false
                var toolTurns = 0
                var carryReasoning = ""
                var reasoningStart: Long? = null
                var visiblePrefix = "" // text already shown from earlier (tool) turns
                // Tool calls already executed this message — so the same search/read is never
                // repeated across turns (fixes "does the search again after the response").
                val executedCalls = mutableSetOf<Pair<String, String>>()

                while (!answered) {
                    val buffer = StringBuilder()
                    var turnReasoning = ""
                    // THROTTLE: cap UI state updates to ~every 50ms during streaming. The model
                    // can emit 100+ tokens/sec; pushing a new messages-list on each one floods
                    // recomposition and causes jank. 50ms (~20fps of text) still feels live but
                    // is butter-smooth. The final content is always flushed after collect ends.
                    var lastEmit = 0L

                    // ── Streaming with retry + non-stream fallback ──
                    // Mirrors the agent-side pattern (streamAgentResponse): retry the stream
                    // once on transient errors, then fall back to a non-streaming request so
                    // a flaky connection never leaves the user with a raw error message.
                    var streamSuccess = false
                    var streamAttempts = 0
                    val streamMaxAttempts = 2
                    while (streamAttempts < streamMaxAttempts && !streamSuccess) {
                        streamAttempts++
                        try {
                            ApiClient.streamChatVision(reqBase, reqKey, reqModel, working, cache = !hasImages).collect { delta ->
                                if (delta.reasoning != null) {
                                    if (reasoningStart == null) reasoningStart = System.currentTimeMillis()
                                    turnReasoning += delta.reasoning
                                }
                                if (delta.text != null) {
                                    buffer.append(delta.text)
                                }
                                // Single throttled emit covers both reasoning + text deltas.
                                val now = System.currentTimeMillis()
                                if (now - lastEmit < 50L) return@collect
                                lastEmit = now
                                val visible = visiblePrefix + stripToolSyntax(buffer.toString())
                                val lastMsg = messages.lastOrNull()
                                if (lastMsg != null && lastMsg.role == "assistant") {
                                    messages = messages.dropLast(1) + lastMsg.copy(
                                        content = visible,
                                        reasoning = (carryReasoning + turnReasoning).ifBlank { null },
                                        reasoningStartTime = reasoningStart
                                    )
                                }
                            }
                            streamSuccess = true
                        } catch (e: Exception) {
                            if (buffer.isNotEmpty()) {
                                // Partial content already received — use it, no retry.
                                streamSuccess = true
                                break
                            }
                            // Show a brief "retrying" status so the user knows what's happening.
                            val statusMsg = messages.lastOrNull()
                            if (statusMsg != null && statusMsg.role == "assistant") {
                                messages = messages.dropLast(1) + statusMsg.copy(
                                    content = if (statusMsg.content.isBlank()) "… retrying …" else statusMsg.content,
                                    isStreaming = true,
                                    toolStatus = if (streamAttempts < streamMaxAttempts)
                                        ToolStatus(type = "SEARCH", query = "retrying…", domain = null)
                                    else null
                                )
                            }
                            if (streamAttempts < streamMaxAttempts) {
                                delay(400L)
                            }
                        }
                    }
                    // If streaming ultimately failed, fall back to a single non-streaming request
                    // so the turn is never completely lost.
                    if (!streamSuccess) {
                        try {
                            val textMessages = working.map { it.role to it.text }
                            val nonStreamResult = withContext(Dispatchers.IO) {
                                ApiClient.sendChatMessage(reqBase, reqKey, reqModel, textMessages, cache = false)
                            }
                            nonStreamResult.getOrNull()?.let { fullText ->
                                buffer.append(fullText)
                            }
                        } catch (_: Exception) {
                            // Non-stream also failed; let the outer catch handle it.
                        }
                    }

                    val raw = buffer.toString()
                    // Token usage (billing): estimate from prompt + response characters.
                    com.ahamai.app.data.UsageTracker.recordChatTokens(
                        context, working.sumOf { it.text.length }, raw.length
                    )
                    val toolCalls = extractToolCalls(raw)
                    val cleanedVisible = visiblePrefix + stripToolSyntax(raw)
                    val imagePrompt = extractImagePrompt(raw)
                    val switchTask = extractSwitchAgent(raw)
                    // Only run tool calls that are genuinely NEW (dedup), and — after the model
                    // has already written a real answer — ignore directive-style false positives
                    // (prose lines that merely begin with "Search:"/"Read:") unless they're the
                    // canonical <tool_call> form. This stops mixed/duplicate searches.
                    val newCalls = toolCalls.filterNot { it in executedCalls }
                    val hasXmlTool = raw.contains("<tool_call", ignoreCase = true)
                    val shouldRunTools = newCalls.isNotEmpty() &&
                        (hasXmlTool || stripToolSyntax(raw).trim().length <= 160)

                    if (switchTask != null) {
                        // The task is beyond chat's capabilities — hand it off to the Agent.
                        val l0 = messages.lastOrNull()
                        if (l0 != null && l0.role == "assistant") {
                            messages = messages.dropLast(1) + l0.copy(
                                content = "This needs the full Agent — opening it for you…",
                                isStreaming = false,
                                toolStatus = null
                            )
                        }
                        answered = true
                        isLoading = false
                        com.ahamai.app.service.RunningTasks.finish(sessionId)
                        com.ahamai.app.data.SoundEffects.playSwitch()
                        onSwitchToAgent(switchTask)
                    } else if (imagePrompt != null) {
                        // The model asked to generate an image. Show the pixel-mockup animation,
                        // generate it, attach the result, and finish.
                        val caption = cleanedVisible.trim()
                        val l1 = messages.lastOrNull()
                        if (l1 != null && l1.role == "assistant") {
                            messages = messages.dropLast(1) + l1.copy(
                                content = caption,
                                isStreaming = true,
                                toolStatus = null,
                                generatingImagePrompt = imagePrompt
                            )
                        }
                        val imgModelId = withContext(Dispatchers.IO) {
                            com.ahamai.app.data.PreferencesManager(context).getImageModel()
                        }
                        val gen = com.ahamai.app.data.ImageGenClient.generate(context, imagePrompt, imgModelId)
                        val path = gen.getOrNull()
                        val l2 = messages.lastOrNull()
                        if (l2 != null && l2.role == "assistant") {
                            messages = messages.dropLast(1) + l2.copy(
                                content = if (path != null) caption
                                          else (caption + "\n\n_Couldn't generate the image right now. Please try again._").trim(),
                                images = path?.let { listOf(it) } ?: emptyList(),
                                generatingImagePrompt = null,
                                isStreaming = false,
                                toolStatus = null
                            )
                        }
                        answered = true
                    } else if (shouldRunTools && toolTurns < 6) {
                        toolTurns++
                        carryReasoning += turnReasoning
                        visiblePrefix = cleanedVisible // keep any natural preamble the model wrote
                        // Run only the NEW tool calls in PARALLEL (each web/image search uses a
                        // different rotating Brave key), and remember them so they never repeat.
                        val batch = newCalls.take(12)
                        executedCalls.addAll(batch)
                        val anyRead = batch.any { it.first == "READ" }
                        if (anyRead) com.ahamai.app.data.SoundEffects.playRead()
                        else com.ahamai.app.data.SoundEffects.playSearch()
                        val first = batch.first()
                        // When multiple tool calls run in parallel, show a clearer status
                        // that reflects the mixed batch (e.g. "3 searches in parallel").
                        val statusType = if (batch.size > 1) "SEARCH" else first.first
                        val statusQuery = if (batch.size > 1) {
                            // Summarise the parallel batch
                            val searchCount = batch.count { it.first == "SEARCH" }
                            val imageCount = batch.count { it.first == "IMAGE_SEARCH" }
                            val readCount = batch.count { it.first == "READ" }
                            buildString {
                                if (searchCount > 0) append("$searchCount search${if (searchCount > 1) "es" else ""}")
                                if (imageCount > 0) { if (isNotEmpty()) append(" + "); append("$imageCount image${if (imageCount > 1) "s" else ""}") }
                                if (readCount > 0) { if (isNotEmpty()) append(" + "); append("$readCount page${if (readCount > 1) "s" else ""}") }
                                append(" in parallel")
                            }
                        } else first.second
                        val statusDomain = if (batch.size > 1) null else extractDomain(first.second, first.first)
                        val lastMsg = messages.lastOrNull()
                        if (lastMsg != null && lastMsg.role == "assistant") {
                            messages = messages.dropLast(1) + lastMsg.copy(
                                content = visiblePrefix,
                                isStreaming = true,
                                toolStatus = ToolStatus(type = statusType, query = statusQuery, domain = statusDomain)
                            )
                        }
                        val toolStart = System.currentTimeMillis()
                        val results = withContext(Dispatchers.IO) {
                            coroutineScope {
                                batch.map { (type, arg) ->
                                    async {
                                        val r = when (type) {
                                            "READ" -> WebTools.read(arg)
                                            "IMAGE_SEARCH" -> WebTools.imageSearch(arg)
                                            else -> WebTools.search(arg)
                                        }
                                        "[$type] $arg\n$r\n\n"
                                    }
                                }.awaitAll().joinToString("")
                            }
                        }
                        // Brief status flash only — keep instant tools snappy (was 900ms artificial lag).
                        val toolElapsed = System.currentTimeMillis() - toolStart
                        if (toolElapsed < 180L) delay(180L - toolElapsed)
                        // Extract sources from search results
                        val extractedSources = extractSourcesFromResults(results, batch)
                        // Update message with sources
                        val lastMsg2 = messages.lastOrNull()
                        if (lastMsg2 != null && lastMsg2.role == "assistant") {
                            messages = messages.dropLast(1) + lastMsg2.copy(
                                content = visiblePrefix,
                                isStreaming = true,
                                toolStatus = null,
                                sources = (lastMsg2.sources + extractedSources).distinctBy { it.domain }
                            )
                        }
                        working.add(ApiMessage("assistant", raw))
                        working.add(ApiMessage("user", "TOOL RESULTS:\n$results\nNow continue answering the user's question using these results. Add inline citations as bracketed numbers [1], [2]... right after the claims they support, where the number matches the position of the source in the numbered results above. Do NOT repeat any tool-call syntax in your reply."))
                    } else {
                        val lastMsg = messages.lastOrNull()
                        if (lastMsg != null && lastMsg.role == "assistant") {
                            val duration = reasoningStart?.let { System.currentTimeMillis() - it }
                            val finalContent = cleanedVisible.trim().ifBlank { lastMsg.content }
                            val smartActions = detectSmartActions(finalContent)
                            messages = messages.dropLast(1) + lastMsg.copy(
                                content = finalContent,
                                reasoning = (carryReasoning + turnReasoning).ifBlank { null },
                                reasoningStartTime = reasoningStart,
                                isStreaming = false,
                                reasoningDurationMs = duration,
                                suggestedActions = smartActions
                            )
                        }
                        answered = true
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    com.ahamai.app.service.RunningTasks.finish(sessionId)
                    throw e
                }
                val lastMsg = messages.lastOrNull()
                if (lastMsg != null && lastMsg.role == "assistant") {
                    val duration = lastMsg.reasoningStartTime?.let { System.currentTimeMillis() - it }
                    messages = messages.dropLast(1) + lastMsg.copy(
                        content = if (lastMsg.content.isEmpty()) "Error: ${e.message}" else lastMsg.content,
                        isStreaming = false,
                        reasoningDurationMs = duration
                    )
                }
            }
            isLoading = false
            streamingJob = null
            com.ahamai.app.service.RunningTasks.finish(sessionId)
            // Ensure the finished response is saved even if we're in the background now.
            withContext(Dispatchers.IO) { persistSession() }
            // 10-second delayed check: if a message is still flagged as streaming, mark it complete.
            com.ahamai.app.AppScope.scope.launch {
             delay(10_000L)
             val stuckMsg = messages.lastOrNull()
             if (stuckMsg != null && stuckMsg.role == "assistant" && stuckMsg.isStreaming) {
              messages = messages.dropLast(1) + stuckMsg.copy(
               isStreaming = false,
               content = stuckMsg.content.ifBlank { "⚠️ Response interrupted — the message may be incomplete." }
              )
             }
            }
            // Best-effort cloud backup of chat history (no-op if signed out).
            com.ahamai.app.AppScope.scope.launch { com.ahamai.app.data.AuthManager.backupHistory(context) }
            // Track chat usage locally + sync to Firestore for admin dashboard.
            UsageTracker.recordCall(context, "chat")
            com.ahamai.app.AppScope.scope.launch { UsageTracker.syncToFirestore(context) }

            // Live mode: auto-speak the response and re-listen
            // IMPORTANT: Always restart listening after the AI turn finishes, even if TTS
            // had nothing to speak (e.g. the response was all tool calls with no text).
            // Previously, startListening was only called inside the TTS onComplete callback,
            // which never fired when cleanText was blank, leaving the mic permanently dead
            // after web-search-heavy turns.
            if (isLiveMode) {
                try {
                    val lastResponse = messages.lastOrNull()
                    if (lastResponse != null && lastResponse.role == "assistant" && lastResponse.content.isNotBlank() && !lastResponse.content.startsWith("Error:")) {
                        val cleanText = lastResponse.content
                            .replace(Regex("```[\\s\\S]*?```"), " code block ")
                            .replace(Regex("`[^`]+`"), "")
                            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                            .replace(Regex("[#*_~>|]"), "")
                            .replace(Regex("\\n{2,}"), ". ")
                            .replace("\n", " ")
                            .replace(Regex("[\\p{So}\\p{Cn}]"), "")
                            .trim()
                        if (cleanText.isNotBlank()) {
                            EdgeTtsClient.speakStreaming(
                                text = cleanText,
                                cacheDir = context.cacheDir,
                                voice = selectedVoice,
                                onComplete = {
                                    // Re-listen AFTER TTS finishes
                                    if (isLiveMode) {
                                        speechHelper.destroy() // fresh recognizer every time
                                        speechHelper.startListening()
                                    }
                                }
                            )
                        } else {
                            // Text went blank after cleaning → still restart listening
                            if (isLiveMode) {
                                speechHelper.destroy()
                                speechHelper.startListening()
                            }
                        }
                    } else {
                        // No valid assistant response → still restart listening
                        if (isLiveMode) {
                            speechHelper.destroy()
                            speechHelper.startListening()
                        }
                    }
                } catch (_: Exception) {
                    // Always restart listening even on error
                    if (isLiveMode) {
                        try { speechHelper.destroy() } catch (_: Exception) {}
                        try { speechHelper.startListening() } catch (_: Exception) {}
                    }
                }
            }

            // Generate related follow-up queries (best-effort, non-blocking for the user).
            val last = messages.lastOrNull()
            if (last != null && last.role == "assistant" && last.content.isNotBlank() && !last.content.startsWith("Error:")) {
                val convo2 = messages.filter { it.content.isNotBlank() }.map { it.role to it.content }
                val res = withContext(Dispatchers.IO) {
                    ApiClient.sendChatMessage(baseUrl, apiKey, modelName, convo2 + ("user" to FOLLOWUP_PROMPT))
                }
                relatedQueries = res.getOrNull()?.let { parseFollowUps(it) } ?: emptyList()
            }
        }
    }

    // Auto-send when live mode gets voice input
    LaunchedEffect(liveSendPending) {
        liveSendPending?.let {
            send(it)
            liveSendPending = null
            inputText = ""
        }
    }

    // Stop an in-flight streaming response. Cancels the coroutine (which raises
    // CancellationException inside `send`, finalising whatever was received so far)
    // and marks the message as no longer streaming so the UI updates immediately.
    fun stopStreaming() {
        streamingJob?.let { job ->
            job.cancel()
            streamingJob = null
        }
        // Finalise the last assistant message — freeze whatever was streamed so far.
        val lastMsg = messages.lastOrNull()
        if (lastMsg != null && lastMsg.role == "assistant" && lastMsg.isStreaming) {
            val duration = lastMsg.reasoningStartTime?.let { System.currentTimeMillis() - it }
            messages = messages.dropLast(1) + lastMsg.copy(
                isStreaming = false,
                toolStatus = null,
                generatingImagePrompt = null,
                reasoningDurationMs = duration ?: lastMsg.reasoningDurationMs,
                content = lastMsg.content.ifBlank { "" }
            )
        }
        isLoading = false
        com.ahamai.app.service.RunningTasks.finish(sessionId)
        // Persist on a background thread (can't use withContext here — not a suspend fn).
        com.ahamai.app.AppScope.scope.launch(Dispatchers.IO) { persistSession() }
        // 10-second delayed check: if this stop didn't finalise the message, clean it up.
        com.ahamai.app.AppScope.scope.launch {
         delay(10_000L)
         val stuckMsg = messages.lastOrNull()
         if (stuckMsg != null && stuckMsg.role == "assistant" && stuckMsg.isStreaming) {
          messages = messages.dropLast(1) + stuckMsg.copy(
           isStreaming = false,
           content = stuckMsg.content.ifBlank { "⚠️ Response interrupted." }
          )
         }
        }
    }

    // Single solid surface — no translucent top bar (was causing two-tone flash)
    val chatBg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val topBarFg = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val topBarMuted = if (isDark) Color(0xFF9A9AA2) else Color(0xFF6B6B6B)

    Scaffold(
        containerColor = chatBg,
        contentColor = topBarFg,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets.statusBars,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = com.ahamai.app.ui.icons.Phosphor.ChevronLeft,
                            contentDescription = "Back to chats",
                            tint = topBarMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                title = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        com.ahamai.app.ui.components.AhamaiLogo(
                            color = topBarFg,
                            fontSize = 20.sp
                        )
                        Row(
                            modifier = Modifier
                                .offset(y = (-1).dp)
                                .chatPressable(pressedScale = 0.97f) { showModelSheet = true }
                                .padding(vertical = 2.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = modelName.ifBlank { "Select model" }
                                    .substringAfterLast('/')
                                    .ifBlank { modelName.ifBlank { "Select model" } },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = com.ahamai.app.ui.theme.InterFamily,
                                color = topBarMuted,
                                letterSpacing = (-0.1f).sp,
                                maxLines = 1
                            )
                            Icon(
                                imageVector = com.ahamai.app.ui.icons.Phosphor.CaretDown,
                                contentDescription = "Change model",
                                tint = topBarMuted,
                                modifier = Modifier.size(12.dp)
                            )
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
                                    .size(28.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = com.ahamai.app.ui.icons.Phosphor.UserCircle,
                                contentDescription = "Profile",
                                tint = topBarMuted,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = chatBg,
                    scrolledContainerColor = chatBg
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(chatBg)
                // Scaffold already applies nav-bar insets via innerPadding — do NOT
                // also call navigationBarsPadding() here (that floated the composer too high).
                .imePadding()
        ) {
            if (messages.isEmpty() && !isLoading) {
                // resetKey = empty session id so every New Chat reshuffles suggestions + re-animates
                ChatHomeWelcome(
                    isDark = isDark,
                    onSuggestion = { chatHaptics.tick(); send(it) },
                    modifier = Modifier.weight(1f),
                    resetKey = sessionId
                )
            } else {
                // Wrap the list in a Box so we can overlay scroll-up / scroll-down FABs
                // (matching the agent screen's nav chevrons). The FABs appear only when
                // the user has scrolled away from the respective end, so they don't
                // clutter the screen when already at the top or bottom.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // ── Chat-only sponsored ads (ChatGPT-style) ──
                // Compute which assistant messages get a "Sponsored" card after them.
                // Chat mode only (this screen is never used for Agent), free plan only,
                // admin master-switch aware. Every AD_INTERVAL-th completed answer.
                // adSettingsVersion bumps on admin Save + Firestore live listener so
                // re-enabling ads after an off→on cycle always re-opens the gate.
                val adVer by com.ahamai.app.data.RemoteConfigManager.adSettingsVersionFlow.collectAsState()
                // Recompute gates every recomposition of adVer + messages (don't cache
                // adsOn only on context — plan / RC can change without context identity change).
                val adsOn = com.ahamai.app.ui.components.AdMobAds.chatAdsOn(context)
                val adInterval = com.ahamai.app.ui.components.AdMobAds.chatInterval
                val adAfterIds = remember(messages, adsOn, adInterval, adVer) {
                    if (!adsOn) emptySet<String>()
                    else {
                        val ids = mutableSetOf<String>()
                        var count = 0
                        messages.forEach { m ->
                            if (m.role == "assistant" && !m.isStreaming && m.content.isNotBlank()) {
                                count++
                                if (count % adInterval == 0) ids.add(m.id)
                            }
                        }
                        ids
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        // More side breathing room — content was edge-to-edge / cramped
                        .padding(horizontal = 20.dp),
                    state = listState,
                    // Tight, iOS Messages-like rhythm — related + answer share the same small gap
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // Extra bottom gap so last message floats clearly above the composer
                    contentPadding = PaddingValues(top = 8.dp, bottom = 136.dp),
                    userScrollEnabled = true
                ) {
                    items(messages, key = { it.id }, contentType = { it.role }) { message ->
                      Column {
                        MessageBubble(
                            message = message,
                            isDark = isDark,
                            onCopy = { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", text))
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            onExport = { format, content ->
                                // Find the user question that preceded this assistant message
                                val msgIdx = messages.indexOf(message)
                                val userQuestion = messages.take(msgIdx).lastOrNull { it.role == "user" }?.content ?: ""
                                when (format) {
                                    "txt" -> exportAsTxt(context, userQuestion, content)
                                    "pdf" -> scope.launch { exportAsPdf(context, userQuestion, content) }
                                }
                            },
                            onRegenerate = {
                                // Find the last user message and resend
                                val lastUserMsg = messages.lastOrNull { it.role == "user" }
                                if (lastUserMsg != null && !isLoading) {
                                    // Remove last assistant message and regenerate
                                    messages = messages.dropLastWhile { it.role == "assistant" }
                                    relatedQueries = emptyList()
                                    isLoading = true
                                    scope.launch {
                                        val convo = withContext(Dispatchers.IO) {
                                            messages.filter { it.content.isNotBlank() || it.images.isNotEmpty() }.map { msg ->
                                                buildApiMessageForChat(context, msg)
                                            }
                                        }
                                        messages = messages + ChatMessage(role = "assistant", content = "", reasoning = null, isStreaming = true)

                                        val dateHeader = realtimeContextHeader()
                                        val agentPrompt2 = if (activeAgent != null) {
                                            val toolDescriptions = activeAgent!!.toolIds.mapNotNull { id ->
                                                com.ahamai.app.data.ToolsManager.getToolById(id)?.let { tool -> "- ${tool.name}: ${tool.description}" }
                                            }.joinToString("\n")
                                            "${activeAgent!!.systemPrompt}\n\nYou have access to these tools:\n$toolDescriptions\n\nWhen you need to use a tool, write Python code that implements it."
                                        } else null
                                        val customPrompt2 = if (agentPrompt2 == null) {
                                            com.ahamai.app.data.PreferencesManager(context).getCustomPrompt()
                                        } else ""
                                        val basePrompt2 = agentPrompt2 ?: RemoteConfigManager.chatSystemPrompt
                                        val systemPrompt = buildString {
                                            append(dateHeader)
                                            if (basePrompt2.isNotBlank()) {
                                                append("\n\n")
                                                append(basePrompt2)
                                            }
                                            append("\n\n")
                                            append(MATH_LATEX_MANDATE)
                                            if (customPrompt2.isNotBlank()) {
                                                append("\n\nAdditional instructions:\n")
                                                append(customPrompt2)
                                            }
                                        }

                                        val working = mutableListOf<ApiMessage>()
                                        working.add(ApiMessage("system", systemPrompt))
                                        working.addAll(convo)

                                        try {
                                            var answered = false
                                            var toolTurns = 0
                                            var carryReasoning = ""
                                            var reasoningStart: Long? = null
                                            var visiblePrefix = ""

                                            while (!answered) {
                                                val buffer = StringBuilder()
                                                var turnReasoning = ""

                                                ApiClient.streamChatVision(baseUrl, apiKey, modelName, working).collect { delta ->
                                                    if (delta.reasoning != null) {
                                                        if (reasoningStart == null) reasoningStart = System.currentTimeMillis()
                                                        turnReasoning += delta.reasoning
                                                        val lastMsg = messages.lastOrNull()
                                                        if (lastMsg != null && lastMsg.role == "assistant") {
                                                            messages = messages.dropLast(1) + lastMsg.copy(
                                                                reasoning = (carryReasoning + turnReasoning).ifBlank { null },
                                                                reasoningStartTime = reasoningStart
                                                            )
                                                        }
                                                    }
                                                    if (delta.text != null) {
                                                        buffer.append(delta.text)
                                                        val visible = visiblePrefix + stripToolSyntax(buffer.toString())
                                                        val lastMsg = messages.lastOrNull()
                                                        if (lastMsg != null && lastMsg.role == "assistant") {
                                                            messages = messages.dropLast(1) + lastMsg.copy(
                                                                content = visible,
                                                                reasoning = (carryReasoning + turnReasoning).ifBlank { null },
                                                                reasoningStartTime = reasoningStart
                                                            )
                                                        }
                                                    }
                                                }

                                                val raw = buffer.toString()
                                                val toolCalls = extractToolCalls(raw)
                                                val cleanedVisible = visiblePrefix + stripToolSyntax(raw)

                                                if (toolCalls.isNotEmpty() && toolTurns < 6) {
                                                    toolTurns++
                                                    carryReasoning += turnReasoning
                                                    visiblePrefix = cleanedVisible
                                                    val batch = toolCalls.take(12)
                                                    if (batch.any { it.first == "READ" }) com.ahamai.app.data.SoundEffects.playRead()
                                                    else com.ahamai.app.data.SoundEffects.playSearch()
                                                    val results = withContext(Dispatchers.IO) {
                                                        coroutineScope {
                                                            batch.map { (type, arg) ->
                                                                async {
                                                                    val r = when (type) {
                                                                        "READ" -> WebTools.read(arg)
                                                                        "IMAGE_SEARCH" -> WebTools.imageSearch(arg)
                                                                        else -> WebTools.search(arg)
                                                                    }
                                                                    "[$type] $arg\n$r\n\n"
                                                                }
                                                            }.awaitAll().joinToString("")
                                                        }
                                                    }
                                                    val extractedSources2 = extractSourcesFromResults(results, batch)
                                                    val lastMsgR = messages.lastOrNull()
                                                    if (lastMsgR != null && lastMsgR.role == "assistant") {
                                                        messages = messages.dropLast(1) + lastMsgR.copy(
                                                            sources = (lastMsgR.sources + extractedSources2).distinctBy { it.domain }
                                                        )
                                                    }
                                                    working.add(ApiMessage("assistant", raw))
                                                    working.add(ApiMessage("user", "TOOL RESULTS:\n$results\nNow continue answering the user's question using these results. Add inline citations as bracketed numbers [1], [2]... right after the claims they support, where the number matches the position of the source in the numbered results above. Do NOT repeat any tool-call syntax in your reply."))
                                                } else {
                                                    val lastMsg = messages.lastOrNull()
                                                    if (lastMsg != null && lastMsg.role == "assistant") {
                                                        val duration = reasoningStart?.let { System.currentTimeMillis() - it }
                                                        messages = messages.dropLast(1) + lastMsg.copy(
                                                            content = cleanedVisible.trim().ifBlank { lastMsg.content },
                                                            reasoning = (carryReasoning + turnReasoning).ifBlank { null },
                                                            reasoningStartTime = reasoningStart,
                                                            isStreaming = false,
                                                            reasoningDurationMs = duration
                                                        )
                                                    }
                                                    answered = true
                                                }
                                            }
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            val lastMsg = messages.lastOrNull()
                                            if (lastMsg != null && lastMsg.role == "assistant") {
                                                val duration = lastMsg.reasoningStartTime?.let { System.currentTimeMillis() - it }
                                                messages = messages.dropLast(1) + lastMsg.copy(
                                                    content = if (lastMsg.content.isEmpty()) "Error: ${e.message}" else lastMsg.content,
                                                    isStreaming = false,
                                                    reasoningDurationMs = duration
                                                )
                                            }
                                        }
                                        isLoading = false
                                    }
                                }
                            },
                            onListen = { text ->
                                scope.launch {
                                    try {
                                        val cleanText = text
                                            .replace(Regex("```[\\s\\S]*?```"), " code block ")
                                            .replace(Regex("`[^`]+`"), "")
                                            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
                                            .replace(Regex("[#*_~>|]"), "")
                                            .replace(Regex("\\n{2,}"), ". ")
                                            .replace("\n", " ")
                                            .replace(Regex("[\\x{1F600}-\\x{1F64F}\\x{1F300}-\\x{1F5FF}\\x{1F680}-\\x{1F6FF}\\x{1F1E0}-\\x{1F1FF}\\x{2600}-\\x{27BF}\\x{2300}-\\x{23FF}\\x{2B50}\\x{2B55}\\x{FE00}-\\x{FEFF}\\x{200D}\\x{20E3}\\x{E0020}-\\x{E007F}]", RegexOption.COMMENTS), "")
                                            .trim()
                                        if (cleanText.isBlank()) return@launch

                                        // Use streaming TTS for low latency
                                        EdgeTtsClient.speakStreaming(
                                            text = cleanText,
                                            cacheDir = context.cacheDir,
                                            voice = selectedVoice
                                        )
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(context, "TTS Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            onStopListening = {
                                EdgeTtsClient.stopAudio()
                            },
                            onSmartAction = { action ->
                                val prompt = when (action) {
                                    "Visualize as Chart" -> "Now create a visual chart (use ```chart with Chart.js JSON config) to represent the data/information from your previous response."
                                    "Draw Diagram" -> "Now draw a diagram (use ```mermaid) to visually represent the flow/process from your previous response."
                                    "Explain Code" -> "Please explain the code from your previous response step by step in simple terms."
                                    "Summarize" -> "Please provide a brief summary of your previous response in 2-3 sentences."
                                    "Make Table" -> "Please organize the information from your previous response into a clean markdown table for easy comparison."
                                    else -> action
                                }
                                if (!isLoading) {
                                    send(prompt)
                                }
                            },
                            onSourceClick = { url ->
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            },
                            // Inherit sources from the most recent preceding assistant
                            // message so [n] citation markers in follow-up responses
                            // (which didn't trigger their own web search) still resolve
                            // to favicon chips instead of showing as raw "[1]" text.
                            inheritedSources = run {
                                val msgIdx = messages.indexOf(message)
                                if (msgIdx > 0) {
                                    messages.take(msgIdx).lastOrNull { it.role == "assistant" }?.sources ?: emptyList()
                                } else emptyList()
                            }
                        )
                        if (message.id in adAfterIds) {
                            com.ahamai.app.ui.components.ChatAdCard(isDark = isDark)
                        }
                      }
                    }
                    if (isLoading && (messages.isEmpty() || messages.last().role != "assistant")) {
                        item { ChatTypingIndicator(isDark = isDark) }
                    }
                    val last = messages.lastOrNull()
                    if (!isLoading && relatedQueries.isNotEmpty() && last?.role == "assistant" && last.content.isNotBlank()) {
                        item { ChatRelatedQueries(queries = relatedQueries, isDark = isDark, onClick = { chatHaptics.tick(); send(it) }) }
                    }
                }

                    // ── Scroll nav FABs (agent-screen style) ──
                    // Show "scroll to top" when not at the top, "scroll to bottom" when
                    // not at the bottom. Both sit at the end-side of the list, stacked.
                    val firstVisible = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 50
                    val lastVisibleIdx = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val atBottom = totalItems == 0 || lastVisibleIdx >= totalItems - 1
                    val fabColor = if (isDark) Color(0xF2141414) else Color(0xF2FFFFFF)
                    val fabBorder = if (isDark) Color(0x28FFFFFF) else Color(0x18000000)
                    val fabIconColor = if (isDark) Color(0xFFAEAEB2) else Color(0xFF6B7280)

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 12.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!firstVisible) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(fabColor)
                                    .border(0.5.dp, fabBorder, CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        stickToBottom = false
                                        scope.launch { listState.animateScrollToItem(0) }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AdminIcons.BootstrapArrow90degUp,
                                    contentDescription = "Scroll to top",
                                    tint = fabIconColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        if (!atBottom) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(fabColor)
                                    .border(0.5.dp, fabBorder, CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        stickToBottom = true
                                        scope.launch {
                                            listState.animateScrollToItem((messages.size - 1).coerceAtLeast(0))
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AdminIcons.BootstrapArrow90degDown,
                                    contentDescription = "Scroll to bottom",
                                    tint = fabIconColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } // close Box
            }

            // Active agent indicator (chip in input area)
            if (activeAgent != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)),
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDark) Color(0xFF1C1C1C) else Color(0xFFF3F3F6)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(activeAgent!!.emoji, fontSize = 12.sp)
                            Text(
                                text = activeAgent!!.name.take(12) + if (activeAgent!!.name.length > 12) "..." else "",
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)
                            )
                            Text(
                                text = "\u2715",
                                fontSize = 11.sp,
                                color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF),
                                modifier = Modifier.clickable { activeAgent = null }.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }

            ChatInputBar(
                inputText = if (isVoiceListening && partialSpeechText.isNotBlank()) partialSpeechText else inputText,
                onInputChange = { inputText = it },
                // Haptics live in ChatInputBar (press + send/stop) — don't double-fire here.
                onSend = { send(inputText) },
                onStop = { stopStreaming() },
                onAttach = {
                    scope.launch {
                        recentPhotos = withContext(Dispatchers.IO) { queryRecentImages(context) }
                    }
                    showAttachSheet = true
                },
                attachments = attachments,
                onRemoveAttachment = { idx -> attachments = attachments.toMutableList().also { it.removeAt(idx) } },
                isLoading = isLoading,
                isDark = isDark,
                isLiveMode = isLiveMode,
                isVoiceListening = isVoiceListening,
                partialText = partialSpeechText,
                onMicTap = {
                    if (isVoiceListening) {
                        speechHelper.stopListening()
                        EdgeTtsClient.stopAudio()
                        isLiveMode = false
                    } else {
                        speechHelper.startListening()
                    }
                },
                onLiveToggle = {
                    if (isLiveMode) {
                        // Turn off live mode
                        isLiveMode = false
                        speechHelper.stopListening()
                        EdgeTtsClient.stopAudio()
                    } else {
                        // Check audio permission first
                        val hasPermission = context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            isLiveMode = true
                            speechHelper.startListening()
                        } else {
                            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onVoiceCall = { showVoiceCall = true }
            )
        }
    }

    if (showAttachSheet) {
        AttachmentSheet(
            recentPhotos = recentPhotos,
            isDark = isDark,
            onCamera = {
                cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            },
            onAllPhotos = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onFiles = { filePicker.launch("*/*") },
            onPhotoPicked = { uri ->
                attachments = (attachments + uri).distinct().take(6)
                showAttachSheet = false
            },
            onDismiss = { showAttachSheet = false }
        )
    }

    // Preload models as soon as chat opens — sheet opens with list already warm
    LaunchedEffect(Unit) {
        // 1) Instant: memory cache / Remote Config models (no network wait)
        val instant = com.ahamai.app.data.ApiConfig.cachedChatModels()
            .ifEmpty { com.ahamai.app.data.ApiConfig.listChatModelsFromConfig(context) }
        if (instant.isNotEmpty()) models = instant
        // 2) Background refresh (parallel + short timeouts)
        modelsLoading = models.isEmpty()
        val res = withContext(Dispatchers.IO) { com.ahamai.app.data.ApiConfig.listChatModels(context) }
        res.fold(
            onSuccess = { models = it },
            onFailure = {
                if (models.isEmpty()) modelsError = it.message ?: "Failed to load models"
            }
        )
        modelsLoading = false
    }

    if (showModelSheet) {
        // Single drag handle only (IosBottomSheet). ModelPickerContent has no second pill.
        IosBottomSheet(
            isDark = isDark,
            onDismissRequest = { showModelSheet = false },
            sheetState = sheetState,
            noHandle = false
        ) {
            ModelPickerContent(
                models = models,
                loading = modelsLoading && models.isEmpty(),
                error = modelsError,
                current = modelName,
                isDark = isDark,
                onSelect = { m ->
                    onModelChange(m)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { showModelSheet = false }
                }
            )
        }
        // Soft refresh when sheet opens — never blank the list if we already have models
        LaunchedEffect(showModelSheet) {
            if (!showModelSheet) return@LaunchedEffect
            modelsError = null
            if (models.isEmpty()) {
                // Try cache/config first so UI isn't stuck on spinner
                val instant = com.ahamai.app.data.ApiConfig.cachedChatModels()
                    .ifEmpty { com.ahamai.app.data.ApiConfig.listChatModelsFromConfig(context) }
                if (instant.isNotEmpty()) models = instant
            }
            modelsLoading = models.isEmpty()
            val res = withContext(Dispatchers.IO) { com.ahamai.app.data.ApiConfig.listChatModels(context) }
            res.fold(
                onSuccess = { models = it },
                onFailure = {
                    if (models.isEmpty()) modelsError = it.message ?: "Failed to load models"
                }
            )
            modelsLoading = false
        }
    }

    // ── Voice Call overlay ──
    if (showVoiceCall) {
        VoiceCallScreen(
            onDismiss = { showVoiceCall = false },
            llmBaseUrl = baseUrl,
            llmApiKey = apiKey,
            llmModel = modelName
        )
    }
}

/** Prompt used to ask the model for short follow-up suggestions. */
private const val FOLLOWUP_PROMPT =
    "Based on our conversation so far, suggest exactly 3 short follow-up questions I might ask next. " +
    "Output ONLY the questions, each on its own line, max 9 words each, no numbering, no quotes, no extra text."

/**
 * Always appended to the chat system prompt (even when Remote Config overrides it).
 * Keeps models from dumping raw ASCII math unless the user asked for plain text.
 */
private const val MATH_LATEX_MANDATE =
    "## Math / formulas (always on)\n" +
    "For ANY equation, formula, expression, integral, sum, fraction, matrix, chemical equation, " +
    "or physics/math/chemistry notation: wrap it in LaTeX delimiters so it renders — never raw plain text.\n" +
    "Inline: \\( E = mc^2 \\)   |   Display: \\[ \\int_0^1 x^2\\,dx = \\frac{1}{3} \\]\n" +
    "Also ok: \$...\$ and \$\$...\$\$. Prefer \\( \\) / \\[ \\]. " +
    "The user must NOT need to ask \"show in LaTeX\" — do it by default every time."

// Tool-call detection. Delegates to the centralised [ToolCallParser] which accepts every
// variant a model might emit: <tool_call>, <toolcall>, <tool call>, <|tool_call|>,
// <function_call>, JSON form, and bare SEARCH:/READ: line directives. The old strict regex
// let malformed calls (e.g. `<tool call>` with a space) leak through as raw text — that was
// the bug where "AI stops automatically by giving <tool call< like this and message as plain
// text instead of doing execution".
private val TOOLCALL_XML_REGEX = Regex("<tool_call>([\\s\\S]*?)</tool_call>", RegexOption.IGNORE_CASE)
private val TOOLCALL_XML_OPEN_REGEX = Regex("<tool_call>[\\s\\S]*$", RegexOption.IGNORE_CASE)
private val ARG_VALUE_REGEX = Regex("<arg_value>([\\s\\S]*?)</arg_value>", RegexOption.IGNORE_CASE)
private val DIRECTIVE_LINE_REGEX = Regex("(?im)^[ \\t>*-]*(SEARCH|READ|IMAGE_SEARCH|IMAGESEARCH|IMG_SEARCH)\\s*:\\s*(.+)$")
private val IMAGE_DIRECTIVE_REGEX = Regex("(?im)^[ \\t>*-]*(?:IMAGE|GENERATE_IMAGE|DRAW)\\s*:\\s*(.+)$")
private val SWITCH_AGENT_REGEX = Regex("(?im)^[ \\t>*-]*SWITCH_AGENT\\s*:\\s*(.+)$")

/** Detects the chat → Agent handoff directive and returns the task to pass to the Agent. */
private fun extractSwitchAgent(raw: String): String? {
    val m = SWITCH_AGENT_REGEX.find(raw) ?: return null
    return m.groupValues[1].trim().trim('"', '`', '\'').ifBlank { null }
}
private val STRAY_TOOL_TAG_REGEX =
    Regex("</?(tool_call|tool_response|arg_key|arg_value|function_call|tool_code|invoke|parameter)[^>]*>", RegexOption.IGNORE_CASE)

/**
 * Builds a header injected into every system prompt so the model always knows the
 * real current date/time and that it is NOT limited by a knowledge cutoff.
 */
private fun realtimeContextHeader(): String {
    val now = java.text.SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", java.util.Locale.getDefault())
        .format(java.util.Date())
    val tz = java.util.TimeZone.getDefault().id
    return "Current date & time (user's device): $now ($tz).\n" +
        "IMPORTANT: You do NOT have a knowledge cutoff. You are connected to live, real-time web search and " +
        "can fetch current information whenever needed. Never claim you lack recent data, never mention a training " +
        "cutoff date, and never give an outdated or uncertain date — always use the current date/time above."
}

/** Maps a tool name to SEARCH, READ, or IMAGE_SEARCH. */
private fun toolType(name: String): String {
    val n = name.lowercase()
    return when {
        (n.contains("image") || n.contains("img") || n.contains("photo") || n.contains("picture")) &&
            (n.contains("search") || n.contains("find")) -> "IMAGE_SEARCH"
        n.contains("read") || n.contains("open") || n.contains("fetch") ||
            n.contains("browse") || n.contains("visit") || n.contains("url") || n.contains("scrape") -> "READ"
        else -> "SEARCH"
    }
}

private fun parseXmlToolBlock(block: String): Pair<String, String>? {
    val name = Regex("^\\s*([a-zA-Z_][a-zA-Z0-9_]*)").find(block)?.groupValues?.get(1) ?: "search"
    val arg = ARG_VALUE_REGEX.find(block)?.groupValues?.get(1)?.trim()
        ?: Regex("^\\s*[a-zA-Z_][a-zA-Z0-9_]*\\s*([\\s\\S]+)").find(block)?.groupValues?.get(1)
            ?.replace(Regex("</?arg_key>|</?arg_value>", RegexOption.IGNORE_CASE), "")?.trim()
        ?: ""
    val clean = arg.trim().trim('"', '\'', '`')
    return if (clean.isBlank()) null else toolType(name) to clean
}

/**
 * Extracts tool calls from a model turn, supporting both the native <tool_call> XML
 * format and the plain SEARCH:/READ: directive format — AND every variant the central
 * [ToolCallParser] knows about (so malformed `<tool call>` / `<toolcall>` / JSON-form
 * tool calls are still recovered instead of leaking to the user as raw text).
 */
private fun extractToolCalls(text: String): List<Pair<String, String>> {
    val calls = mutableListOf<Pair<String, String>>()

    // 1. Strict closed XML form first (so we keep the canonical parsing for well-behaved models).
    val closed = TOOLCALL_XML_REGEX.findAll(text).toList()
    for (m in closed) parseXmlToolBlock(m.groupValues[1])?.let { calls.add(it) }

    // 2. Unclosed XML (stream cut off mid tool call).
    if (closed.isEmpty()) {
        TOOLCALL_XML_OPEN_REGEX.find(text)?.let { m ->
            val inner = m.value.removePrefix("<tool_call>").removePrefix("<TOOL_CALL>")
            parseXmlToolBlock(inner)?.let { calls.add(it) }
        }
    }

    // 3. Plain SEARCH:/READ: directives.
    for (m in DIRECTIVE_LINE_REGEX.findAll(text)) {
        var type = m.groupValues[1].uppercase()
        if (type == "IMAGESEARCH" || type == "IMG_SEARCH") type = "IMAGE_SEARCH"
        val arg = m.groupValues[2].trim().trimEnd(']').trim().trim('"', '\'', '`', '<', '>')
        if (arg.isNotBlank() && !arg.equals("none", ignoreCase = true)) calls.add(type to arg)
    }

    // 4. Fallback: delegate to ToolCallParser — it knows about <tool call>, <toolcall>,
    //    <|tool_call|>, <function_call>, JSON form, and bare line directives. This is the
    //    safety net that catches malformed calls the strict regexes above missed.
    if (calls.isEmpty()) {
        for (c in com.ahamai.app.data.ToolCallParser.extract(text)) {
            val t = c.name.uppercase()
            if (t == "SEARCH" || t == "READ" || t.contains("IMAGE") || t.contains("IMG")) {
                val arg = c.args.firstOrNull()?.trim().orEmpty()
                if (arg.isNotBlank()) calls.add(toolType(c.name) to arg)
            }
        }
    }

    return calls.distinct()
}

/**
 * Removes any tool-call syntax (XML or directive form, including partial/streaming
 * fragments) so the user never sees raw tool calls. As a final safety net, also delegates
 * to [ToolCallParser.stripAll] which catches variant tags the regexes above might miss.
 */
private fun stripToolSyntax(text: String): String {
    var t = text
    t = TOOLCALL_XML_REGEX.replace(t, "")
    t = TOOLCALL_XML_OPEN_REGEX.replace(t, "") // unclosed, while streaming
    t = DIRECTIVE_LINE_REGEX.replace(t, "")
    t = IMAGE_DIRECTIVE_REGEX.replace(t, "")
    t = STRAY_TOOL_TAG_REGEX.replace(t, "")
    // A dangling partial opening tag at the very end (e.g. "<too") while streaming.
    t = Regex("<[a-zA-Z_/]*$").replace(t, "")
    t = Regex("\\n{3,}").replace(t, "\n\n")
    // Safety-net: hand the result to the central parser for anything we missed (e.g.
    // `<tool call>` with a space, `<|tool_call|>`, JSON-only tool calls).
    t = com.ahamai.app.data.ToolCallParser.stripAll(t)
    return t
}

/**
 * Extracts the first IMAGE/GENERATE_IMAGE/DRAW directive prompt, or null.
 */
private fun extractImagePrompt(text: String): String? {
    val m = IMAGE_DIRECTIVE_REGEX.find(text) ?: return null
    return m.groupValues[1].trim().trim('"', '\'', '`', '<', '>', ']').ifBlank { null }
}

/**
 * Extracts a domain from a search query or URL for favicon display.
 * For READ: extracts domain from URL. For SEARCH: returns google.com (search engine).
 */
private fun extractDomain(arg: String, type: String): String? {
    return if (type == "READ") {
        try {
            val url = if (arg.startsWith("http")) arg else "https://$arg"
            val host = java.net.URI(url).host
            host?.removePrefix("www.")
        } catch (_: Exception) {
            null
        }
    } else if (type == "IMAGE_SEARCH") {
        // Image search shows a spinner rather than a favicon.
        null
    } else {
        // For search, use Google favicon
        "google.com"
    }
}

/**
 * Extracts source information from web search results.
 * Parses URLs and domains from the result text to create SourceInfo objects.
 */
private fun extractSourcesFromResults(results: String, batch: List<Pair<String, String>>): List<SourceInfo> {
    val sources = mutableListOf<SourceInfo>()

    // Parse a block of result items formatted as:
    //   N. Title
    //   Description (optional)
    //   Image: <thumbnail url> (optional)
    //   Source: <url>
    // Line-based parsing is more robust than one mega-regex and preserves the image thumbnail.
    fun parseBlock(block: String) {
        var title: String? = null
        var desc: String? = null
        var image: String? = null
        fun flush(url: String) {
            val t = title?.trim()?.trim('*', '#')?.trim()
            if (!t.isNullOrBlank() && url.isNotBlank()) {
                val domain = try {
                    val full = if (url.startsWith("http")) url else "https://$url"
                    java.net.URI(full).host?.removePrefix("www.") ?: url
                } catch (_: Exception) { url }
                sources.add(SourceInfo(title = t, url = url, domain = domain, snippet = desc?.trim(), imageUrl = image?.trim()))
            }
            title = null; desc = null; image = null
        }
        for (rawLine in block.lines()) {
            val line = rawLine.trim()
            when {
                line.isBlank() -> {}
                line.startsWith("Source:", ignoreCase = true) -> {
                    flush(line.substringAfter(":").trim())
                }
                line.startsWith("Image:", ignoreCase = true) -> {
                    image = line.substringAfter(":").trim()
                }
                // Extract image URL from markdown image syntax: ![alt](url)
                line.startsWith("![") && line.contains("](") -> {
                    val imgMatch = Regex("!\\[.*?]\\((https?://[^)]+)\\)").find(line)
                    if (imgMatch != null && image == null) {
                        image = imgMatch.groupValues[1]
                    }
                }
                Regex("^\\d+\\.\\s+").containsMatchIn(line) -> {
                    // New item starts — set its title.
                    title = line.replaceFirst(Regex("^\\d+\\.\\s+"), "")
                    desc = null; image = null
                }
                title != null && desc == null -> {
                    desc = line
                }
            }
        }
    }

    for ((type, arg) in batch) {
        when (type) {
            "SEARCH" -> {
                val blockRegex = Regex(Regex.escape("[SEARCH] $arg") + "\\n([\\s\\S]*?)(?=\\n\\[[A-Z_]+\\]|$)")
                val block = blockRegex.find(results)?.groupValues?.get(1) ?: results
                parseBlock(block)
            }
            "IMAGE_SEARCH" -> {
                // Parse image search results - extract image URLs from markdown syntax
                val blockRegex = Regex(Regex.escape("[IMAGE_SEARCH] $arg") + "\\n([\\s\\S]*?)(?=\\n\\[[A-Z_]+\\]|$)")
                val block = blockRegex.find(results)?.groupValues?.get(1) ?: results
                // Extract markdown images and their sources
                val imgRegex = Regex("!\\[([^\\]]*)]\\((https?[^)]+)\\)")
                val sourceRegex = Regex("Source:\\s*(https?://\\S+)")
                val imgMatches = imgRegex.findAll(block).toList()
                val sourceMatches = sourceRegex.findAll(block).toList()
                
                for ((idx, imgMatch) in imgMatches.withIndex()) {
                    val imgTitle = imgMatch.groupValues[1].ifBlank { "Image" }
                    val imgUrl = imgMatch.groupValues[2]
                    val sourceUrl = sourceMatches.getOrNull(idx)?.groupValues?.get(1) ?: ""
                    val domain = try {
                        if (sourceUrl.isNotBlank()) java.net.URI(sourceUrl).host?.removePrefix("www.") ?: "image"
                        else java.net.URI(imgUrl).host?.removePrefix("www.") ?: "image"
                    } catch (_: Exception) { "image" }
                    sources.add(SourceInfo(
                        title = imgTitle.take(60),
                        url = sourceUrl.ifBlank { imgUrl },
                        domain = domain,
                        imageUrl = imgUrl
                    ))
                }
            }
            "READ" -> {
                try {
                    val url = if (arg.startsWith("http")) arg else "https://$arg"
                    val host = java.net.URI(url).host?.removePrefix("www.") ?: arg
                    sources.add(SourceInfo(title = host, url = url, domain = host))
                } catch (_: Exception) {}
            }
        }
    }

    // Fallback: if nothing parsed, scan for bare URLs so sources still appear.
    if (sources.isEmpty()) {
        val urlPattern = Regex("https?://([^/\\s]+)([^\\s]*)")
        for (match in urlPattern.findAll(results)) {
            val domain = match.groupValues[1].removePrefix("www.")
            if (!domain.contains("google.com") && !domain.contains("brave.com") && !domain.contains("bing.com")) {
                sources.add(SourceInfo(title = domain, url = match.value, domain = domain))
            }
        }
    }

    return sources.distinctBy { it.domain }.take(8)
}

/** Parses the model's follow-up output into a clean list of up to 3 suggestions. */
private fun parseFollowUps(raw: String): List<String> {
    return raw.lines()
        .map { it.trim().removePrefix("-").removePrefix("*").trim() }
        .map { it.replace(Regex("^\\d+[.)]\\s*"), "") }
        .map { it.trim().trim('"') }
        .filter { it.length in 3..120 && it.contains(' ') }
        .distinct()
        .take(3)
}

/**
 * Detects smart contextual actions the AI might suggest based on its response content.
 * Returns a list of actionable button labels.
 */
private fun detectSmartActions(content: String): List<String> {
    val actions = mutableListOf<String>()
    val lower = content.lowercase()

    // If response discusses data/numbers that could be visualized
    if ((lower.contains("data") || lower.contains("statistics") || lower.contains("numbers") ||
        lower.contains("percentage") || lower.contains("growth") || lower.contains("revenue") ||
        lower.contains("comparison")) && !lower.contains("```chart") && !lower.contains("```mermaid")) {
        actions.add("Visualize as Chart")
    }

    // If response describes a process/flow/steps
    if ((lower.contains("step 1") || lower.contains("first,") || lower.contains("process") ||
        lower.contains("workflow") || lower.contains("flow") || lower.contains("sequence")) &&
        !lower.contains("```mermaid")) {
        actions.add("Draw Diagram")
    }

    // If response contains code that could have an explanation
    if (lower.contains("```") && (lower.contains("function") || lower.contains("class") ||
        lower.contains("def ") || lower.contains("import "))) {
        actions.add("Explain Code")
    }

    // If response is a long explanation that could be summarized
    if (content.length > 800 && !lower.contains("summary") && !lower.contains("in short")) {
        actions.add("Summarize")
    }

    // If response mentions something that could be converted to a table
    if ((lower.contains("compare") || lower.contains("versus") || lower.contains(" vs ") ||
        lower.contains("difference between") || lower.contains("features")) &&
        !lower.contains("|---")) {
        actions.add("Make Table")
    }

    return actions.take(3)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeWelcome(isDark: Boolean, onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    ChatHomeWelcome(isDark = isDark, onSuggestion = onSuggestion, modifier = modifier)
}


@Composable
private fun RelatedQueries(queries: List<String>, isDark: Boolean, onClick: (String) -> Unit) {
    ChatRelatedQueries(queries = queries, isDark = isDark, onClick = onClick)
}


@Composable
internal fun ModelPickerContent(
    models: List<String>,
    loading: Boolean,
    error: String?,
    current: String,
    isDark: Boolean,
    onSelect: (String) -> Unit,
    /** Optional subtitle under title — agent vs chat */
    subtitle: String = "Choose a model for this chat"
) {
    var query by remember { mutableStateOf("") }
    val grouped = remember(models, query) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) models
            else models.filter { it.contains(q, ignoreCase = true) }
        filtered.groupBy { m ->
            val slash = m.indexOf('/')
            if (slash > 0) m.substring(0, slash) else "Models"
        }
    }
    // New iOS card language — monochrome, soft materials (matches floating menus)
    val textPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1A1A1A)
    val textMuted = Color(0xFF8E8E93)
    val groupBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
    val searchBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val sep = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
    // Monochrome check — no blue accent
    val checkColor = textPrimary

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 540.dp)
            .navigationBarsPadding()
            .padding(bottom = 8.dp)
    ) {
        // iOS large title
        Text(
            text = "Model",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = (-0.5f).sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = textMuted,
            fontFamily = com.ahamai.app.ui.theme.InterFamily,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
        )
        
        // iOS search field — rounded, no heavy border
        if (!loading && error == null && models.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(searchBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    com.ahamai.app.ui.icons.Phosphor.MagnifyingGlass,
                    contentDescription = null,
                    tint = textMuted,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Search",
                            fontSize = 16.sp,
                            color = textMuted,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 16.sp,
                            color = textPrimary,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily
                        ),
                        cursorBrush = SolidColor(textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        com.ahamai.app.ui.icons.Phosphor.X,
                        contentDescription = "Clear",
                        tint = textMuted,
                        modifier = Modifier
                            .size(16.dp)
                            .chatPressable { query = "" }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = textMuted
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading models…",
                            fontSize = 14.sp,
                            color = textMuted,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily
                        )
                    }
                }
            }
            error != null -> {
                Text(
                    error,
                    fontSize = 14.sp,
                    color = com.ahamai.app.ui.theme.ChatPalette.Danger,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            grouped.isEmpty() -> {
                Text(
                    "No models match \"${query.trim()}\"",
                    fontSize = 14.sp,
                    color = textMuted,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily,
                    modifier = Modifier.padding(40.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                grouped.forEach { (provider, providerModels) ->
                    item(key = "header_$provider") {
                        Text(
                            text = provider.uppercase(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                            color = textMuted,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                        )
                    }
                    item(key = "group_$provider") {
                        // Floating iOS card (22dp) — soft material like context menus
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = if (isDark) 0.dp else 2.dp,
                                    shape = RoundedCornerShape(16.dp),
                                    ambientColor = Color.Black.copy(0.04f),
                                    spotColor = Color.Black.copy(0.04f)
                                )
                                .clip(RoundedCornerShape(16.dp))
                                .background(groupBg)
                        ) {
                            providerModels.forEachIndexed { index, m ->
                                val selected = m == current
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .chatPressable(pressedScale = 0.985f) { onSelect(m) }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = m.substringAfterLast('/').ifBlank { m },
                                        fontSize = 16.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontFamily = com.ahamai.app.ui.theme.InterFamily,
                                        color = textPrimary,
                                        letterSpacing = (-0.2f).sp,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (selected) {
                                        Icon(
                                            com.ahamai.app.ui.icons.Phosphor.Check,
                                            contentDescription = "Selected",
                                            tint = checkColor,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                if (index < providerModels.lastIndex) {
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = sep,
                                        modifier = Modifier.padding(start = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Perplexity-style monochrome pixel-dot "processing" animation — a 3×3 grid of dots
 * with a soft traveling sweep, replacing the old "..." dots + "Thinking..." text.
 * Pure monochrome (uses the current text-muted colour), no accent tint.
 */
/**
 * Perplexity-style "Answer" header — a small 2×2 pixel icon (scira accent) + "Answer" label,
 * shown above the assistant's answer body.
 */
@Composable
private fun AnswerLabel(isDark: Boolean) {
    // Minimal — no bulky pixel block
    Spacer(Modifier.height(2.dp))
}

@Composable
private fun TypingIndicator(isDark: Boolean) {
    ChatTypingIndicator(isDark = isDark)
}


@Composable
private fun ThinkingPanel(
    reasoning: String,
    isStreaming: Boolean,
    isDark: Boolean,
    reasoningStartTime: Long? = null,
    reasoningDurationMs: Long? = null
) {
    val h = rememberChatHaptics()
    ChatThinkingPanel(
        reasoning = reasoning,
        isStreaming = isStreaming,
        isDark = isDark,
        reasoningStartTime = reasoningStartTime,
        reasoningDurationMs = reasoningDurationMs,
        onToggle = { h.tick() }
    )
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourcesBar(
    sources: List<SourceInfo>,
    isDark: Boolean,
    onSourceClick: (String) -> Unit
) {
    val h = rememberChatHaptics()
    ChatSourcesBar(
        sources = sources,
        isDark = isDark,
        onSourceClick = { h.tick(); onSourceClick(it) },
        onOpenSheet = { h.select() }
    )
}




@Composable
private fun ToolStatusIndicator(status: ToolStatus, isDark: Boolean) {
    ChatToolStatusIndicator(status = status, isDark = isDark)
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageBubble(
    message: ChatMessage,
    isDark: Boolean,
    onCopy: (String) -> Unit = {},
    onRegenerate: () -> Unit = {},
    onListen: (String) -> Unit = {},
    onStopListening: () -> Unit = {},
    onSmartAction: (String) -> Unit = {},
    onSourceClick: (String) -> Unit = {},
    onExport: (String, String) -> Unit = { _, _ -> },
    // Sources inherited from the previous assistant message — used when THIS message
    // contains [n] citation markers but did not itself trigger a web search (so it has
    // no sources of its own). Without this, citations render as raw "[1]" text.
    inheritedSources: List<SourceInfo> = emptyList()
) {
    val isUser = message.role == "user"
    var isSpeaking by remember { mutableStateOf(false) }
    // AI actions always visible (Copy / Retry / Listen / Export) — no tap-to-reveal
    var showActions by remember { mutableStateOf(true) }
    var showExportSheet by remember { mutableStateOf(false) }
    // Double-tap user bubble → copy + green flash (same as agent mode)
    var userCopied by remember { mutableStateOf(false) }
    val bubbleHaptics = rememberChatHaptics()
    val context = LocalContext.current

    // Effective sources: if this message has its own sources, use them; otherwise
    // fall back to sources inherited from the previous assistant message. This makes
    // [n] citation markers in follow-up responses resolve to the correct sources
    // even when the follow-up itself didn't trigger a web search.
    val effectiveSources = if (message.sources.isNotEmpty()) message.sources else inheritedSources

    // iOS Messages-style enter — butter spring, no jank on stream tokens
    var appeared by remember(message.id) { mutableStateOf(false) }
    LaunchedEffect(message.id) { appeared = true }
    LaunchedEffect(userCopied) {
        if (userCopied) {
            kotlinx.coroutines.delay(1100)
            userCopied = false
        }
    }

    AnimatedVisibility(
        visible = appeared,
        enter = ChatMotion.messageEnter(),
        exit = ChatMotion.messageExit()
    ) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        if (isUser) {
            // Agent-matched user bubble — soft elevated surface, double-tap to copy
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // User attachments: images as thumbnails, files as full previews (same as agent).
                val imgAttach = message.images.filter { !isFileAttachment(it) }
                val fileAttach = message.images.filter { isFileAttachment(it) }
                if (imgAttach.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        imgAttach.take(3).forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "attached image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(if (imgAttach.size == 1) 168.dp else 84.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )
                        }
                    }
                }
                fileAttach.forEach { path ->
                    val f = remember(path) { java.io.File(path) }
                    Box(modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.End)) {
                        com.ahamai.app.ui.components.FilePreviewCard(
                            projectDir = f.parent ?: "",
                            relPath = f.name,
                            isDark = isDark,
                            showActions = false   // user upload — no download/save actions
                        )
                    }
                }
                if (message.content.isNotBlank()) {
                    val hasAttachment = message.images.isNotEmpty()
                    // Same colors as agent mode user bubble (+ green flash on double-tap copy)
                    val bubbleColor = when {
                        userCopied -> if (isDark) Color(0xFF1A3A1A) else Color(0xFFD4EDDA)
                        hasAttachment -> Color.Transparent
                        isDark -> com.ahamai.app.ui.theme.ChatPalette.DarkUserBubble
                        else -> com.ahamai.app.ui.theme.ChatPalette.LightUserBubble
                    }
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = bubbleColor,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .widthIn(max = maxWidth * 0.82f)
                                .wrapContentWidth(Alignment.End)
                                .pointerInput(message.content) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("message", message.content))
                                            userCopied = true
                                            bubbleHaptics.confirm()
                                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                        ) {
                            Text(
                                text = message.content,
                                modifier = Modifier.padding(
                                    horizontal = if (hasAttachment && !userCopied) 2.dp else 14.dp,
                                    vertical = if (hasAttachment && !userCopied) 2.dp else 10.dp
                                ),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = com.ahamai.app.ui.theme.InterFamily,
                                color = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk
                                        else com.ahamai.app.ui.theme.ChatPalette.LightInk,
                                lineHeight = 21.sp,
                                letterSpacing = (-0.15f).sp,
                                textAlign = if (hasAttachment) TextAlign.End else TextAlign.Start
                            )
                        }
                    }
                }
            }
        } else {
            // Assistant — iOS clean, full-bleed text, soft enter for answer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 0.dp, end = 8.dp)
            ) {
                if (message.reasoning != null && message.toolStatus == null) {
                    val h = rememberChatHaptics()
                    ChatThinkingPanel(
                        reasoning = message.reasoning,
                        isStreaming = message.isStreaming && message.content.isEmpty(),
                        isDark = isDark,
                        reasoningStartTime = message.reasoningStartTime,
                        reasoningDurationMs = message.reasoningDurationMs,
                        onToggle = { h.tick() }
                    )
                }

                // Tool status first (searching / reading) — iOS clean
                AnimatedVisibility(
                    visible = message.toolStatus != null,
                    enter = ChatMotion.panelExpand(),
                    exit = ChatMotion.panelCollapse()
                ) {
                    message.toolStatus?.let { ChatToolStatusIndicator(status = it, isDark = isDark) }
                }

                // Sources strip
                AnimatedVisibility(
                    visible = message.sources.isNotEmpty() && message.toolStatus == null,
                    enter = ChatMotion.panelExpand(),
                    exit = ChatMotion.panelCollapse()
                ) {
                    val hSrc = rememberChatHaptics()
                    ChatSourcesBar(
                        sources = message.sources,
                        isDark = isDark,
                        onSourceClick = { url -> hSrc.tick(); onSourceClick(url) },
                        onOpenSheet = { hSrc.select() }
                    )
                }

                val answerInk = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk
                    else com.ahamai.app.ui.theme.ChatPalette.LightInk

                // Answer body — soft iOS fade-in; stream without layout thrash
                if (message.content.isNotEmpty() && !message.isStreaming) {
                    var answerShown by remember(message.id) { mutableStateOf(false) }
                    LaunchedEffect(message.id) { answerShown = true }
                    AnimatedVisibility(
                        visible = answerShown,
                        enter = ChatMotion.answerEnter(),
                        exit = ChatMotion.messageExit()
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            MarkdownText(
                                text = message.content,
                                color = answerInk,
                                isStreaming = false,
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                citations = effectiveSources.mapIndexed { i, s ->
                                    com.ahamai.app.ui.components.MarkdownCitation(i + 1, s.domain, s.url)
                                },
                                onCitationClick = onSourceClick
                            )
                        }
                    }
                } else if (message.content.isNotEmpty() && message.isStreaming) {
                    MarkdownText(
                        text = message.content,
                        color = answerInk,
                        isStreaming = true,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .animateContentSize(
                                animationSpec = spring(
                                    dampingRatio = 0.92f,
                                    stiffness = 280f
                                )
                            ),
                        citations = effectiveSources.mapIndexed { i, s ->
                            com.ahamai.app.ui.components.MarkdownCitation(i + 1, s.domain, s.url)
                        },
                        onCitationClick = onSourceClick
                    )
                } else if (message.isStreaming && message.reasoning == null && message.generatingImagePrompt == null && message.toolStatus == null) {
                    ChatTypingIndicator(isDark = isDark)
                }

                if (message.generatingImagePrompt != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GeneratingImageBox(isDark = isDark)
                }

                if (message.images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    message.images.forEach { path ->
                        GeneratedImageView(path = path, isDark = isDark)
                    }
                }

                // Action buttons — always visible after answer finishes (iOS share row feel)
                AnimatedVisibility(
                    visible = showActions && !message.isStreaming && message.content.isNotBlank(),
                    enter = ChatMotion.panelExpand(),
                    exit = ChatMotion.panelCollapse()
                ) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        // Main action row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ActionChip(
                                icon = com.ahamai.app.ui.icons.Phosphor.Copy,
                                label = "Copy",
                                isDark = isDark,
                                onClick = { onCopy(message.content) }
                            )
                            ActionChip(
                                icon = com.ahamai.app.ui.icons.Phosphor.ArrowClockwise,
                                label = "Retry",
                                isDark = isDark,
                                onClick = { onRegenerate() }
                            )
                            ActionChip(
                                icon = if (isSpeaking) com.ahamai.app.ui.icons.Phosphor.Stop
                                    else com.ahamai.app.ui.icons.Phosphor.Waveform,
                                label = if (isSpeaking) "Stop" else "Listen",
                                isDark = isDark,
                                isActive = isSpeaking,
                                onClick = {
                                    if (isSpeaking) {
                                        onStopListening()
                                        isSpeaking = false
                                    } else {
                                        isSpeaking = true
                                        onListen(message.content)
                                    }
                                }
                            )
                            ActionChip(
                                icon = com.ahamai.app.ui.icons.Phosphor.DownloadSimple,
                                label = "Export",
                                isDark = isDark,
                                onClick = { showExportSheet = true }
                            )
                        }

                        // Smart actions — text-only chips, no fill behind icons/labels
                        if (message.suggestedActions.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                message.suggestedActions.forEach { action ->
                                    val chipMuted = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInkSecondary
                                        else com.ahamai.app.ui.theme.ChatPalette.LightInkSecondary
                                    Text(
                                        text = action,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = chipMuted,
                                        fontFamily = com.ahamai.app.ui.theme.InterFamily,
                                        modifier = Modifier
                                            .chatPressable(pressedScale = 0.96f) { onSmartAction(action) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    } // AnimatedVisibility — message enter

    // Export format picker — iOS-style bottom sheet
    if (showExportSheet) {
        val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
        val muted = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B6B6B)
        val rowBg = if (isDark) Color(0xFF141414) else Color(0xFFF4F4F4)
        IosBottomSheet(isDark = isDark, onDismissRequest = { showExportSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) {
                Text("Export", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily, color = primary, letterSpacing = (-0.5f).sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                Text("Choose export format", fontSize = 13.sp,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily, color = muted,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp))

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                showExportSheet = false
                                onExport("txt", message.content)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = rowBg
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AdminIcons.TextFileIcon, null,
                                tint = muted, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Text File", fontSize = 16.sp, fontWeight = FontWeight.Normal,
                                    fontFamily = com.ahamai.app.ui.theme.InterFamily, color = primary)
                                Text("Plain text format", fontSize = 12.sp,
                                    fontFamily = com.ahamai.app.ui.theme.InterFamily, color = muted)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                showExportSheet = false
                                onExport("pdf", message.content)
                            },
                        shape = RoundedCornerShape(14.dp),
                        color = rowBg
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(AdminIcons.PdfExportIcon, null,
                                tint = muted, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("PDF Document", fontSize = 16.sp, fontWeight = FontWeight.Normal,
                                    fontFamily = com.ahamai.app.ui.theme.InterFamily, color = primary)
                                Text("Formatted document", fontSize = 12.sp,
                                    fontFamily = com.ahamai.app.ui.theme.InterFamily, color = muted)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}



/** iOS Messages action — icon + label, zero fill / zero border behind icons. */
@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val h = rememberChatHaptics()
    val tint = when {
        isActive -> if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk
            else com.ahamai.app.ui.theme.ChatPalette.LightInk
        isDark -> com.ahamai.app.ui.theme.ChatPalette.DarkInkSecondary
        else -> com.ahamai.app.ui.theme.ChatPalette.LightInkSecondary
    }

    Row(
        modifier = Modifier
            .chatPressable(pressedScale = 0.94f, haptics = h) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            fontFamily = com.ahamai.app.ui.theme.InterFamily,
            letterSpacing = (-0.1f).sp
        )
    }
}

/** Pixel-mockup shimmer animation shown while an image is being generated. */
@Composable
private fun GeneratingImageBox(isDark: Boolean) {
    val transition = rememberInfiniteTransition(label = "gen")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * kotlin.math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "phase"
    )
    val base = if (isDark) Color(0xFF242424) else Color(0xFFE4E4EC)
    val hi = if (isDark) Color(0xFF4D4D55) else Color(0xFFBFBFCF)
    Box(
        modifier = Modifier
            .size(240.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (isDark) Color(0xFF161616) else Color(0xFFF0F0F4)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            val n = 10
            val cell = size.width / n
            val gap = cell * 0.12f
            for (r in 0 until n) {
                for (c in 0 until n) {
                    val wave = (kotlin.math.sin(phase + (r + c) * 0.55f) + 1f) / 2f
                    val color = lerp(base, hi, wave)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(c * cell + gap, r * cell + gap),
                        size = Size(cell - 2 * gap, cell - 2 * gap),
                        cornerRadius = CornerRadius(cell * 0.18f, cell * 0.18f)
                    )
                }
            }
        }
        Surface(color = Color.Black.copy(alpha = 0.4f), shape = RoundedCornerShape(10.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = Color.White)
                Text("Generating image\u2026", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** A generated image with rounded corners and a small save-to-device icon. */
@Composable
private fun GeneratedImageView(path: String, isDark: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Box(modifier = Modifier.widthIn(max = 300.dp)) {
        AsyncImage(
            model = path,
            contentDescription = "generated image",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable {
                    scope.launch {
                        val ok = saveGeneratedImage(context, path)
                        Toast.makeText(
                            context,
                            if (ok) "Saved to Downloads" else "Couldn't save image",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = com.ahamai.app.ui.icons.Phosphor.DownloadSimple,
                contentDescription = "Save",
                tint = Color.White,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

private suspend fun saveGeneratedImage(context: Context, path: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val f = java.io.File(path)
            if (!f.exists()) return@withContext false
            val bytes = f.readBytes()
            val ext = f.extension.ifBlank { "jpg" }
            val mime = if (ext == "webp") "image/webp" else "image/jpeg"
            val name = "AhamAI_${System.currentTimeMillis()}.$ext"
            com.ahamai.app.data.DeviceStorage.saveBytesToDownloads(context, bytes, name, mime).startsWith("OK")
        } catch (e: Exception) {
            false
        }
    }

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    attachments: List<String>,
    onRemoveAttachment: (Int) -> Unit,
    isLoading: Boolean,
    isDark: Boolean,
    isLiveMode: Boolean = false,
    isVoiceListening: Boolean = false,
    partialText: String = "",
    onMicTap: () -> Unit = {},
    onLiveToggle: () -> Unit = {},
    onVoiceCall: () -> Unit = {},
    onStop: () -> Unit = {}
) {
    val haptics = rememberChatHaptics()
    // Clean, flat composer — solid soft surface, no glass/heavy shadow (iOS-style, monochrome)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF4F4F4)
    val cardBorder = if (isDark) Color(0x14FFFFFF) else Color(0x0D000000)
    val textPrimary = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val textSecondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val placeholderColor = if (isDark) Color(0xFF6B7280).copy(alpha = 0.75f) else Color(0xFF9CA3AF).copy(alpha = 0.9f)
    // Action circles sit on the soft field — a touch lighter/darker so they read cleanly.
    val chipBg = if (isDark) Color(0xFF2A2A2A) else Color(0xFFF5F5F7)

    // Bottom composer — sit closer to the system nav edge (was floating too high).
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 6.dp)
    ) {
        if (attachments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 8.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                attachments.forEachIndexed { idx, uri ->
                    val removeBadge: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit = {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0D0D0D).copy(alpha = 0.75f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onRemoveAttachment(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                com.ahamai.app.ui.icons.Phosphor.X,
                                "Remove",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                    if (isFileAttachment(uri)) {
                        // Non-image file → DIRECT preview in the composer (xlsx table, pdf page,
                        // doc text, csv table, or clean card) — same as it appears in the message.
                        val f = remember(uri) { java.io.File(uri) }
                        Box(modifier = Modifier.padding(top = 4.dp, end = 4.dp).width(240.dp)) {
                            Box(modifier = Modifier.heightIn(max = 190.dp).clip(RoundedCornerShape(14.dp))) {
                                com.ahamai.app.ui.components.FilePreviewCard(
                                    projectDir = f.parent ?: "",
                                    relPath = f.name,
                                    isDark = isDark,
                                    compact = true,
                                    showActions = false   // composer attachment — no actions
                                )
                            }
                            removeBadge()
                        }
                    } else {
                        Box(modifier = Modifier.size(52.dp)) {
                            AsyncImage(
                                model = uri,
                                contentDescription = "attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                            )
                            removeBadge()
                        }
                    }
                }
            }
        }

        // iOS Messages–style pill composer
        val inputScroll = rememberScrollState()
        LaunchedEffect(inputText) { inputScroll.scrollTo(inputScroll.maxValue) }
        val canSend = inputText.isNotBlank() || attachments.isNotEmpty()
        val sendState = if (isLoading) "stop" else if (canSend) "send" else "idle"
        val composerBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        val composerBorder = if (isDark) Color(0x22FFFFFF) else Color(0x14000000)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(0.5.dp, composerBorder, RoundedCornerShape(24.dp))
                .then(
                    if (!isDark) Modifier.shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black.copy(alpha = 0.06f),
                        spotColor = Color.Black.copy(alpha = 0.08f)
                    ) else Modifier
                ),
            shape = RoundedCornerShape(24.dp),
            color = composerBg,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Attach — soft iOS circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                        .chatPressable(enabled = !isLoading, pressedScale = 0.90f, haptics = haptics) {
                            onAttach()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = com.ahamai.app.ui.icons.Phosphor.Plus,
                        contentDescription = "Attach",
                        tint = textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Text field
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    if (inputText.isEmpty()) {
                        val placeholder = if (isVoiceListening) {
                            androidx.compose.ui.text.buildAnnotatedString { append("Listening…") }
                        } else {
                            androidx.compose.ui.text.buildAnnotatedString {
                                append("Message ")
                                withStyle(
                                    com.ahamai.app.ui.components.ahamaiLogoSpanStyle(
                                        fontSize = 16.sp,
                                        color = placeholderColor
                                    )
                                ) { append("ahamai") }
                            }
                        }
                        Text(
                            text = placeholder,
                            fontSize = 16.sp,
                            color = placeholderColor,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily,
                            lineHeight = 22.sp,
                            maxLines = 1
                        )
                    }
                    BasicTextField(
                        value = inputText,
                        onValueChange = onInputChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 22.dp, max = 120.dp)
                            .verticalScroll(inputScroll),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 16.sp,
                            color = textPrimary,
                            fontFamily = com.ahamai.app.ui.theme.InterFamily,
                            lineHeight = 22.sp
                        ),
                        cursorBrush = SolidColor(if (isDark) Color(0xFFECECEC) else Color(0xFF141414))
                    )
                }

                // Voice call — flat SF phone, only when nothing to send
                if (!isLoading && inputText.isBlank() && attachments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .chatPressable(pressedScale = 0.90f, haptics = haptics) {
                                onVoiceCall()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = com.ahamai.app.ui.icons.Phosphor.Phone,
                            contentDescription = "Voice Call",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                }

                // Send / Stop — iOS filled circle only when active
                val sendBg by animateColorAsState(
                    targetValue = when (sendState) {
                        "stop" -> com.ahamai.app.ui.theme.ChatPalette.Danger
                        "send" -> if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk
                            else com.ahamai.app.ui.theme.ChatPalette.LightInk
                        else -> Color.Transparent
                    },
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    label = "sendBg"
                )
                val sendInteraction = remember { MutableInteractionSource() }
                val sendPressed by sendInteraction.collectIsPressedAsState()
                HapticOnPress(
                    interactionSource = sendInteraction,
                    haptics = haptics,
                    enabled = sendState != "idle"
                )
                val sendScale by animateFloatAsState(
                    targetValue = if (sendPressed) 0.90f else 1f,
                    animationSpec = ChatMotion.pressSpring,
                    label = "sendScale"
                )
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .scale(sendScale)
                        .clip(CircleShape)
                        .background(sendBg)
                        .clickable(
                            interactionSource = sendInteraction,
                            indication = null,
                            enabled = sendState != "idle"
                        ) {
                            when (sendState) {
                                "send" -> { haptics.send(); onSend() }
                                "stop" -> { haptics.reject(); onStop() }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = sendState,
                        transitionSpec = {
                            (fadeIn(tween(140)) +
                                scaleIn(initialScale = 0.6f, animationSpec = ChatMotion.butterSpring)) togetherWith
                                (fadeOut(tween(90)) +
                                    scaleOut(targetScale = 0.6f, animationSpec = tween(90)))
                        },
                        label = "sendIcon"
                    ) { state ->
                        when (state) {
                            "stop" -> Icon(
                                imageVector = com.ahamai.app.ui.icons.Phosphor.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            "send" -> Icon(
                                imageVector = com.ahamai.app.ui.icons.Phosphor.ArrowUp,
                                contentDescription = "Send",
                                tint = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkBg
                                    else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            else -> Icon(
                                imageVector = com.ahamai.app.ui.icons.Phosphor.ArrowUp,
                                contentDescription = "Send",
                                tint = textSecondary.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Queries the device's MediaStore for the 10 most recent images.
 */
/** True if an attachment entry is a local file (copied via the Files picker), not an image URI. */
private fun isFileAttachment(s: String): Boolean = s.startsWith("/")

/** Copies a picked content URI into app cache and returns the local absolute path (or null). */
private fun copyUriToChatCache(context: Context, uri: Uri): String? = try {
    val name = (queryAttachmentName(context, uri) ?: "file_${System.currentTimeMillis()}").replace("/", "_")
    // filesDir (not cacheDir) so attachments survive in saved chat history.
    val dir = java.io.File(context.filesDir, "chat_attachments").apply { mkdirs() }
    val dest = java.io.File(dir, "${System.currentTimeMillis()}_$name")
    context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
    if (dest.exists() && dest.length() > 0) dest.absolutePath else null
} catch (_: Exception) { null }

private fun queryAttachmentName(context: Context, uri: Uri): String? = try {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
} catch (_: Exception) { null }

/** Best-effort text of an attached file, so the chat LLM can "read" it (agent-style). */
private fun extractFileTextForLlm(path: String, maxChars: Int = 12000): String {
    val file = java.io.File(path)
    if (!file.exists()) return ""
    val kit = com.ahamai.app.ui.components.FilePreviewKit
    return try {
        when (file.extension.lowercase()) {
            "docx" -> kit.readDocxText(file, maxChars)
            "pptx" -> kit.readPptxText(file, maxChars)
            "xlsx" -> kit.readXlsxGrid(file).joinToString("\n") { row -> row.joinToString(" | ") }.take(maxChars)
            "csv", "tsv" -> file.readText().take(maxChars)
            "pdf", "png", "jpg", "jpeg", "webp", "gif", "bmp", "mp4", "mov", "webm", "zip", "apk" -> ""
            else -> file.readText().take(maxChars)
        }
    } catch (_: Exception) { "" }
}

/** Builds the API message for a chat message: images → vision data URLs, files → extracted text. */
private fun buildApiMessageForChat(context: Context, msg: ChatMessage): ApiMessage {
    if (msg.role != "user") return ApiMessage(msg.role, msg.content, emptyList())
    val imgData = msg.images.filter { !isFileAttachment(it) }.mapNotNull { ImageUtils.uriToDataUrl(context, it) }
    val fileBlock = msg.images.filter { isFileAttachment(it) }.joinToString("\n\n") { p ->
        val name = java.io.File(p).name
        val body = extractFileTextForLlm(p)
        if (body.isBlank()) "[Attached file: $name — binary/preview only, not extractable as text]"
        else "[Attached file: $name]\n$body"
    }
    val content = if (fileBlock.isNotBlank()) (msg.content + "\n\n" + fileBlock).trim() else msg.content
    return ApiMessage(msg.role, content, imgData)
}

private fun queryRecentImages(context: Context): List<String> {
    val images = mutableListOf<String>()
    try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            var count = 0
            while (cursor.moveToNext() && count < 10) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                images.add(uri.toString())
                count++
            }
        }
    } catch (_: Exception) {}
    return images
}

/**
 * Creates a temp file and returns a FileProvider content:// URI for camera capture.
 */
private fun createCameraUri(context: Context): Uri {
    val file = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
    file.createNewFile()
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentSheet(
    recentPhotos: List<String>,
    isDark: Boolean,
    onCamera: () -> Unit,
    onAllPhotos: () -> Unit,
    onFiles: () -> Unit,
    onPhotoPicked: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Pure iOS action sheet — app palette, SF-style Phosphor icons, no filled icon tiles
    val textColor = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk
        else com.ahamai.app.ui.theme.ChatPalette.LightInk
    val subtextColor = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInkSecondary
        else com.ahamai.app.ui.theme.ChatPalette.LightInkSecondary
    val groupBg = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkSurface
        else com.ahamai.app.ui.theme.ChatPalette.LightSurfaceElevated
    val sep = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkBorder
        else com.ahamai.app.ui.theme.ChatPalette.LightBorder
    val accent = com.ahamai.app.ui.theme.ChatPalette.Accent

    IosBottomSheet(
        isDark = isDark,
        onDismissRequest = onDismiss,
        noHandle = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Text(
                text = "Add to Chat",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                fontFamily = com.ahamai.app.ui.theme.InterFamily,
                letterSpacing = (-0.5f).sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                text = "Camera, photos, or files",
                fontSize = 13.sp,
                color = subtextColor,
                fontFamily = com.ahamai.app.ui.theme.InterFamily,
modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 14.dp)
        )
        
        // iOS grouped list — horizontal compact tiles
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IosAttachTile(
                    label = "Take Photo",
                    icon = com.ahamai.app.ui.icons.Phosphor.Camera,
                    textColor = textColor,
                    iconTint = accent,
                    onClick = onCamera,
                    modifier = Modifier.weight(1f)
                )
                IosAttachTile(
                    label = "Photo Library",
                    icon = com.ahamai.app.ui.icons.Phosphor.Image,
                    textColor = textColor,
                    iconTint = accent,
                    onClick = onAllPhotos,
                    modifier = Modifier.weight(1f)
                )
                IosAttachTile(
                    label = "Choose File",
                    icon = com.ahamai.app.ui.icons.Phosphor.File,
                    textColor = textColor,
                    iconTint = accent,
                    onClick = onFiles,
                    modifier = Modifier.weight(1f)
                )
            }

            if (recentPhotos.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = "RECENT",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                    color = subtextColor,
                    fontFamily = com.ahamai.app.ui.theme.InterFamily,
                    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(recentPhotos.take(8)) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .chatPressable(pressedScale = 0.95f) { onPhotoPicked(uri) }
                        )
                    }
                }
            }
        }
    }
}

/** iOS Settings-style attach row — icon flat (no circle fill). */
@Composable
private fun IosAttachRow(
    label: String,
    icon: ImageVector,
    textColor: Color,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .chatPressable(pressedScale = 0.99f) { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            color = textColor,
            fontFamily = com.ahamai.app.ui.theme.InterFamily,
            letterSpacing = (-0.2f).sp,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = com.ahamai.app.ui.icons.Phosphor.CaretDown,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.28f),
            modifier = Modifier
                .size(12.dp)
                .rotate(-90f)
        )
    }
}

/** Compact horizontal tile for the attachment sheet — icon above label, card-style. */
@Composable
private fun IosAttachTile(
    label: String,
    icon: ImageVector,
    textColor: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val groupBg = if (isSystemInDarkTheme()) com.ahamai.app.ui.theme.ChatPalette.DarkSurface
        else com.ahamai.app.ui.theme.ChatPalette.LightSurfaceElevated
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(groupBg)
            .chatPressable(pressedScale = 0.97f) { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            fontFamily = com.ahamai.app.ui.theme.InterFamily,
            maxLines = 1
        )
    }
}

// ── Export helpers ──────────────────────────────────────────────────────────────

/** Exports the AI response as a plain .txt file to the Downloads folder. */
private fun exportAsTxt(context: Context, question: String, answer: String) {
    try {
        val name = "AhamAI_Export_${System.currentTimeMillis()}.txt"
        val content = buildString {
            if (question.isNotBlank()) {
                appendLine(question)
                appendLine()
            }
            append(answer)
        }
        val bytes = content.toByteArray(Charsets.UTF_8)
        val result = com.ahamai.app.data.DeviceStorage.saveBytesToDownloads(context, bytes, name, "text/plain")
        val msg = if (result.startsWith("OK")) "Saved to Downloads" else "Export failed"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Exports the conversation as a clean PDF — question in lighter color, answer in darker color.
 * No header, no labels, just the content.
 */
private suspend fun exportAsPdf(context: Context, question: String, answer: String) = withContext(Dispatchers.IO) {
    try {
        val name = "AhamAI_Export_${System.currentTimeMillis()}.pdf"
        val file = java.io.File(context.cacheDir, name)

        val writer = PdfWriter(java.io.FileOutputStream(file))
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc, PageSize.A4)
        document.setMargins(36f, 36f, 36f, 36f)

        // ── Embed Inter font from app resources for proper Unicode coverage ──
        // The built-in Helvetica only covers Latin-1; Inter covers a much wider
        // range of Unicode (accented chars, CJK punctuation, special symbols).
        // Color emoji aren't supported by iText7 (bitmap fonts), but at least
        // text-level special characters render correctly.
        val fontReg = try {
            val bytes = context.resources.openRawResource(com.ahamai.app.R.font.inter_regular).use { it.readBytes() }
            PdfFontFactory.createFont(bytes, com.itextpdf.io.font.PdfEncodings.IDENTITY_H)
        } catch (_: Exception) {
            PdfFontFactory.createFont(StandardFonts.HELVETICA)
        }
        val fontBold = try {
            val bytes = context.resources.openRawResource(com.ahamai.app.R.font.inter_bold).use { it.readBytes() }
            PdfFontFactory.createFont(bytes, com.itextpdf.io.font.PdfEncodings.IDENTITY_H)
        } catch (_: Exception) {
            PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
        }
        val fontMono = try {
            val bytes = context.resources.openRawResource(com.ahamai.app.R.font.jetbrainsmono_regular).use { it.readBytes() }
            PdfFontFactory.createFont(bytes, com.itextpdf.io.font.PdfEncodings.IDENTITY_H)
        } catch (_: Exception) {
            PdfFontFactory.createFont(StandardFonts.COURIER)
        }

        val questionColor = DeviceRgb(130, 130, 130)
        val answerColor = DeviceRgb(30, 30, 30)
        val codeBg = DeviceRgb(245, 245, 245)
        val borderColor = DeviceRgb(220, 220, 220)
        val linkColor = DeviceRgb(0, 122, 255)

        // Question (lighter color)
        if (question.isNotBlank()) {
            val qPara = Paragraph()
                .add(question)
                .setFont(fontReg)
                .setFontSize(12f)
                .setFontColor(questionColor)
                .setMarginBottom(20f)
            document.add(qPara)
        }

        // ── Helper: download an image from a URL and return an iText Image, or null ──
        suspend fun downloadImage(urlStr: String): Image? {
            return try {
                val conn = java.net.URL(urlStr).openConnection()
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bytes = conn.getInputStream().use { it.readBytes() }
                val img = Image(ImageDataFactory.create(bytes))
                // Scale to fit page width
                val maxW = PageSize.A4.width - 72f
                if (img.imageWidth > maxW) img.scaleToFit(maxW, Float.MAX_VALUE)
                img.setMarginBottom(10f)
                img
            } catch (_: Exception) { null }
        }

        // ── Helper: strip inline markdown markers and return a Paragraph ──
        // Keeps the text content clean — no raw ** or ` showing in the PDF. We don't
        // attempt per-segment styling (iText7's Text API is finicky); instead we strip
        // markers and use the paragraph's font. This is robust and avoids showing raw
        // markdown syntax in the exported PDF.
        fun renderInline(text: String, baseFont: com.itextpdf.kernel.font.PdfFont, boldFont: com.itextpdf.kernel.font.PdfFont, monoFont: com.itextpdf.kernel.font.PdfFont): Paragraph {
            val cleaned = text
                .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")        // **bold** → bold
                .replace(Regex("__([^_]+?)__"), "$1")             // __bold__ → bold
                .replace(Regex("\\*([^*\\s][^*]*[^*\\s]?)\\*"), "$1") // *italic* → italic
                .replace(Regex("`([^`]+?)`"), "$1")               // `code` → code
                .replace(Regex("~~(.+?)~~"), "$1")                // ~~strike~~ → strike
                .replace(Regex("\\[([^\\[\\]]+?)\\]\\(([^)]+)\\)"), "$1") // [text](url) → text
                .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "$1")  // ![alt](url) → alt
            return Paragraph(cleaned).setFont(baseFont)
        }

        val lines = answer.split("\n")
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                // ── Image: ![alt](url) — download and embed ──
                trimmed.startsWith("![") && trimmed.contains("](") && trimmed.endsWith(")") -> {
                    val imgMatch = Regex("!\\[([^]]*)]\\(([^)]+)\\)").find(trimmed)
                    if (imgMatch != null) {
                        val imgUrl = imgMatch.groupValues[2]
                        val img = downloadImage(imgUrl)
                        if (img != null) {
                            document.add(img)
                        } else {
                            // Can't download — show the alt text as a caption
                            val alt = imgMatch.groupValues[1].ifBlank { imgUrl }
                            document.add(Paragraph("[Image: $alt]").setFont(fontReg).setFontSize(9f).setFontColor(questionColor).setMarginBottom(8f))
                        }
                    } else {
                        document.add(renderInline(trimmed, fontReg, fontBold, fontMono).setMarginBottom(8f))
                    }
                }
                // Code block start
                trimmed.startsWith("```") -> {
                    val lang = trimmed.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    i++ // skip closing ```

                    if (codeLines.isNotEmpty()) {
                        val codeContent = codeLines.joinToString("\n")
                        val codeTable = Table(UnitValue.createPercentArray(floatArrayOf(100f)))
                            .useAllAvailableWidth()
                            .setBackgroundColor(codeBg)
                            .setBorder(SolidBorder(borderColor, 0.5f))
                            .setMarginBottom(12f)

                        if (lang.isNotBlank()) {
                            val langPara = Paragraph(lang)
                                .setFont(fontReg).setFontSize(8f).setFontColor(DeviceRgb(150, 150, 150))
                                .setMarginBottom(4f)
                            codeTable.addCell(Cell().add(langPara).setBorder(Border.NO_BORDER).setPaddingBottom(0f))
                        }

                        val codePara = Paragraph(codeContent)
                            .setFont(fontMono)
                            .setFontSize(9f)
                            .setFontColor(answerColor)
                        codeTable.addCell(Cell().add(codePara).setBorder(Border.NO_BORDER).setPadding(8f))
                        document.add(codeTable)
                    }
                }
                // Heading
                trimmed.startsWith("# ") -> {
                    val text = trimmed.removePrefix("# ").trim()
                    val para = renderInline(text, fontBold, fontBold, fontMono).setFontSize(16f).setFontColor(answerColor).setMarginBottom(8f)
                    document.add(para)
                }
                trimmed.startsWith("## ") -> {
                    val text = trimmed.removePrefix("## ").trim()
                    val para = renderInline(text, fontBold, fontBold, fontMono).setFontSize(14f).setFontColor(answerColor).setMarginBottom(6f)
                    document.add(para)
                }
                trimmed.startsWith("### ") -> {
                    val text = trimmed.removePrefix("### ").trim()
                    val para = renderInline(text, fontBold, fontBold, fontMono).setFontSize(12f).setFontColor(answerColor).setMarginBottom(4f)
                    document.add(para)
                }
                // Blockquote
                trimmed.startsWith("> ") -> {
                    val text = trimmed.removePrefix("> ").trim()
                    val quoteTable = Table(UnitValue.createPercentArray(floatArrayOf(3f, 97f)))
                        .useAllAvailableWidth()
                        .setMarginBottom(8f)
                    quoteTable.setBorder(Border.NO_BORDER)
                    quoteTable.addCell(Cell().setBorder(Border.NO_BORDER).setBackgroundColor(linkColor).setPadding(0f))
                    quoteTable.addCell(Cell().add(renderInline(text, fontReg, fontBold, fontMono).setFontColor(questionColor)).setBorder(Border.NO_BORDER).setPaddingLeft(8f))
                    document.add(quoteTable)
                }
                // List item
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    val text = trimmed.removePrefix("- ").removePrefix("* ").trim()
                    val cleaned = text
                        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                        .replace(Regex("`([^`]+?)`"), "$1")
                        .replace(Regex("\\[([^\\[\\]]+?)\\]\\(([^)]+)\\)"), "$1")
                    val para = Paragraph("  \u2022  $cleaned").setFont(fontReg).setFontSize(11f).setFontColor(answerColor).setMarginBottom(4f)
                    document.add(para)
                }
                // Numbered list
                trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                    val num = trimmed.substringBefore(".")
                    val text = trimmed.substringAfter(". ").trim()
                    val cleaned = text
                        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
                        .replace(Regex("`([^`]+?)`"), "$1")
                        .replace(Regex("\\[([^\\[\\]]+?)\\]\\(([^)]+)\\)"), "$1")
                    val para = Paragraph("  $num.  $cleaned").setFont(fontReg).setFontSize(11f).setFontColor(answerColor).setMarginBottom(4f)
                    document.add(para)
                }
                // Markdown table — detect header row + separator
                trimmed.startsWith("|") && trimmed.endsWith("|") && i + 1 < lines.size && lines[i + 1].trim().matches(Regex("^\\|?[\\s:|-]+\\|?$")) -> {
                    val tableLines = mutableListOf<String>()
                    tableLines.add(trimmed)
                    i++
                    tableLines.add(lines[i].trim()) // separator
                    i++
                    while (i < lines.size && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    i-- // will be incremented by the loop's i++

                    val rows = tableLines.filter { !it.matches(Regex("^\\|?[\\s:|-]+\\|?$")) }
                    if (rows.isNotEmpty()) {
                        val colCount = rows[0].split("|").filter { it.isNotBlank() }.size
                        val pdfTable = Table(UnitValue.createPercentArray(FloatArray(colCount) { 100f / colCount }))
                            .useAllAvailableWidth()
                            .setMarginBottom(12f)
                            .setBorder(SolidBorder(borderColor, 0.5f))
                        rows.forEachIndexed { rowIdx, row ->
                            val cells = row.split("|").filter { it.isNotBlank() }.map { it.trim() }
                            cells.forEachIndexed { _, cellText ->
                                val cellPara = renderInline(cellText, if (rowIdx == 0) fontBold else fontReg, fontBold, fontMono)
                                    .setFontSize(10f)
                                    .setFontColor(answerColor)
                                val cell = Cell().add(cellPara).setPadding(6f)
                                if (rowIdx == 0) cell.setBackgroundColor(codeBg)
                                pdfTable.addCell(cell)
                            }
                        }
                        document.add(pdfTable)
                    }
                }
                // Horizontal rule
                trimmed.matches(Regex("^-{3,}$")) || trimmed.matches(Regex("^\\*{3,}$")) -> {
                    val sepPara = Paragraph()
                    sepPara.setBorderBottom(SolidBorder(borderColor, 0.5f))
                    sepPara.setMarginBottom(8f)
                    document.add(sepPara)
                }
                // Empty line
                trimmed.isBlank() -> {
                    // Skip empty lines
                }
                // Regular paragraph — render with inline formatting
                else -> {
                    val para = renderInline(trimmed, fontReg, fontBold, fontMono)
                        .setFontColor(answerColor)
                        .setMarginBottom(8f)
                    document.add(para)
                }
            }
            i++
        }

        document.close()

        // Save to Downloads
        val bytes = file.readBytes()
        val result = com.ahamai.app.data.DeviceStorage.saveBytesToDownloads(context, bytes, name, "application/pdf")
        withContext(Dispatchers.Main) {
            val msg = if (result.startsWith("OK")) "PDF saved to Downloads" else "PDF export failed"
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "PDF export failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
