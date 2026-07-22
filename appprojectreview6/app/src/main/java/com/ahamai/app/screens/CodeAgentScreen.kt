package com.ahamai.app.screens

import androidx.compose.runtime.saveable.rememberSaveable
import com.ahamai.app.ui.components.IosBottomSheet
import com.ahamai.app.ui.components.IosDialog
import com.ahamai.app.ui.components.AgentStatusBanner

import com.ahamai.app.ui.agent.AgentBtwAside
import com.ahamai.app.ui.agent.AgentEnter
import com.ahamai.app.ui.agent.agentPressable
import com.ahamai.app.ui.agent.rememberAgentHaptics
import com.ahamai.app.ui.chat.HapticOnPress
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut


import com.ahamai.app.ui.components.FinishedToastCard
import com.ahamai.app.data.AgentTaskStore
import com.ahamai.app.data.AgentPermissionGate
import com.ahamai.app.data.AgentCompletionGuard
import com.ahamai.app.data.BigChangePolicy
import com.ahamai.app.data.PermissionMode
import com.ahamai.app.data.ToolPermission
import com.ahamai.app.ui.components.PermissionConfirmDialog
import com.ahamai.app.ui.components.PermissionModeSheet
import com.ahamai.app.ui.components.PermissionModeChip
import com.ahamai.app.ui.components.RewindPickerSheet
import com.ahamai.app.ui.components.formatRewindToast
import com.ahamai.app.data.ApiClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.verticalScroll
 import com.ahamai.app.ui.icons.Lucide
 import com.ahamai.app.ui.icons.AdminIcons
 import com.ahamai.app.ui.icons.Phosphor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import com.ahamai.app.R
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download

import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Menu
import com.ahamai.app.ui.icons.AdminIcons.ZipIcon
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.ahamai.app.data.*
import com.ahamai.app.ui.components.MarkdownText
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.RalewayFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import java.io.File
import java.net.URL
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@Immutable
data class PlanItem(val text: String, val done: Boolean = false)

@Immutable
data class AgentLogEntry(
 val type: String, // "user", "action", "note", "error", "answer", "narration", "diff", "plan", "planstep", "buildprogress", "filechip", "preview", "recovery"
 val text: String,
 val live: Boolean = false, // true while narration streams OR an action tool is still running
 val path: String = "",
 val diff: DiffUtil.DiffResult? = null,
 val result: String = "", // tool output, shown when an action row is expanded
 val plan: List<PlanItem>? = null,
 val buildSteps: List<GitHubClient.BuildStepInfo>? = null,
 val logTail: String = "", // live build log tail (advanced view)
 val buildProgress: Float = -1f, // 0..1 build completion; -1 = indeterminate
 // Live preview payload. previewKind: "webapp" | "diagram" | "chart"
 val previewKind: String = "",
 val previewPayload: String = "", // For webapp: project-relative path. For diagram: mermaid source. For chart: "<type>|<title>\n<json>".
 val attachments: List<String> = emptyList(), // project-relative paths attached to a "user" message
 val actionKind: String = "", // raw CodeAgent.AgentStep.action id for "action" entries — used to
 // bucket grouped rows into Ran/Edited/Read (see groupAgentLog below).
 // For type="recovery": "timeout" | "loop_stop" | "askuser"
 /** Wall-clock budget for this tool (ms). 0 = n/a. */
 val timeoutBudgetMs: Long = 0L,
 /** When the tool started (epoch ms). Used for live elapsed while [live]. */
 val startedAtMs: Long = 0L,
 val id: String = java.util.UUID.randomUUID().toString() // stable key for LazyColumn (copy() preserves it)
)

// Tools that only read state (no filesystem mutation) — safe to run concurrently within a turn.
private val READ_ONLY_ACTIONS = setOf(
    "read", "readlines", "readfiles", "grep", "search", "glob", "diffhistory", "repomap",
    "codebasesearch", "loadtools", "list", "fetch", "http", "checkhtml", "apkinfo",
    "scansecrets", "securityaudit", "webscan", "cloudls", "symbolsearch",
    "gotodefinition", "findreferences", "documentsymbols", "workspacesymbols", "hover",
    "snippetlist", "gitdiff", "websearch", "imagesearch", "readurl", "previewweb",
    "renderchart", "memory", "verify", "verifybuild", "diagnose", "lint"
)

/**
 * Local filesystem mutations that can run concurrently when they touch DIFFERENT paths.
 * Same-path ops stay ordered in waves so sequential edits on one file still compose correctly.
 * This is the main reason multi-file agent turns felt slow vs. instant CLI agents.
 */
private val PARALLEL_LOCAL_ACTIONS = setOf(
    "edit", "write", "create", "insertlines", "delete", "copy", "move",
    "multiedit", "applypatch", "importadd"
)

/** Whole-tree mutators — never share a wave with other file ops. */
private val EXCLUSIVE_LOCAL_ACTIONS = setOf("bulkedit", "refactorrename")

// Tools that change the project's file structure — after these we re-feed the tree to the model
// so it always knows exactly which files exist (no more "lost" created files or assumed files).
private val STRUCTURE_ACTIONS = setOf(
    "create", "write", "delete", "move", "copy", "unzip", "download", "importdownload",
    "scaffoldandroid", "bulkedit", "apkdecompile", "cloudpull", "siteclone", "apkrebuild",
    "imageedit", "generateimage", "screenshot", "multiedit", "importadd", "refactorrename",
    "snippetload", "pdfcreate", "createxlsx", "createpptx", "createdocx", "createcsv", "task",
    "applypatch", "codebasewiki", "insertlines"
)

/** Paths a local mutation touches (for conflict-aware parallel waves). */
private fun localPathKeys(s: CodeAgent.AgentStep): Set<String> = when (s.action) {
    in EXCLUSIVE_LOCAL_ACTIONS -> setOf("*")
    "copy", "move" -> linkedSetOf(s.path.trim(), s.arg2.trim()).filter { it.isNotBlank() }
        .ifEmpty { listOf("?${stepKey(s)}") }.toSet()
    else -> setOf(s.path.trim().ifBlank { "?${stepKey(s)}" })
}

/**
 * Pack local mutations into waves: each wave has at most one op per path (and exclusive ops alone).
 * Waves run sequentially; ops inside a wave run in parallel.
 */
private fun buildLocalParallelWaves(steps: List<CodeAgent.AgentStep>): List<List<CodeAgent.AgentStep>> {
    val remaining = steps.toMutableList()
    val waves = mutableListOf<List<CodeAgent.AgentStep>>()
    while (remaining.isNotEmpty()) {
        val wave = mutableListOf<CodeAgent.AgentStep>()
        val used = mutableSetOf<String>()
        val iter = remaining.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            val keys = localPathKeys(s)
            val exclusive = s.action in EXCLUSIVE_LOCAL_ACTIONS || "*" in keys
            if (exclusive) {
                if (wave.isEmpty()) {
                    wave.add(s)
                    iter.remove()
                }
                break
            }
            if (keys.any { it in used }) continue
            wave.add(s)
            used.addAll(keys)
            iter.remove()
        }
        if (wave.isEmpty()) wave.add(remaining.removeAt(0))
        waves.add(wave)
    }
    return waves
}

/**
 * Process-scoped LIVE state for an agent run, keyed by project. A run continues on the app scope
 * when the user navigates away / minimises the app, so its state must NOT live only in the
 * (disposable) composable — otherwise returning to the screen shows a blank page until the run
 * finishes. The screen binds to this holder, so it reflects the running agent in REAL TIME.
 */
object AgentRuntime {
 class Holder {
 val agentLog = androidx.compose.runtime.mutableStateOf<List<AgentLogEntry>>(emptyList())
 val isRunning = androidx.compose.runtime.mutableStateOf(false)
 val sessionFiles = androidx.compose.runtime.mutableStateOf(setOf<String>())
 val sessionAdded = androidx.compose.runtime.mutableStateOf(0)
 val sessionRemoved = androidx.compose.runtime.mutableStateOf(0)
 val convo = androidx.compose.runtime.mutableStateListOf<Pair<String, String>>()
 val pendingUserMessages = androidx.compose.runtime.mutableStateListOf<String>()
 /** Per-file structured diffs — map of relative path to DiffResult */
 val fileDiffs = androidx.compose.runtime.mutableStateOf<Map<String, DiffUtil.DiffResult>>(emptyMap())
 /**
  * Legacy LIFO single-file undo (kept as fallback). Primary recovery is Grok-style
  * multi-file turn rewind via [fileState] / [rewindLastTurn].
  */
 val undoStack = mutableListOf<Triple<String, String, String>>()
 /** Set of action types that need user confirmation before executing */
 val pendingConfirmations = mutableStateListOf<String>()
 /** Redo stack for re-applying undone changes */
 val undoSnapshots = mutableListOf<Triple<String, String, String>>()
 @Volatile var job: kotlinx.coroutines.Job? = null
 /** Active Grok-style prompt index for this run (set in beginPrompt). */
 @Volatile var currentPromptIndex: Int = -1
 /** Grok-style file state tracker (before/after snapshots, multi-file rewind). */
 @Volatile var fileState: com.ahamai.app.data.FileStateTracker? = null

 /** Bind / load tracker for this workspace (safe to call every run). */
 fun bindFileState(projectDir: String): com.ahamai.app.data.FileStateTracker {
  val t = com.ahamai.app.data.FileStateRegistry.tracker(projectDir)
  fileState = t
  return t
 }

 /** Start a Grok-style rewind point for this user turn. */
 fun beginPrompt(projectDir: String): Int {
  val t = bindFileState(projectDir)
  val idx = t.beginPrompt()
  currentPromptIndex = idx
  return idx
 }

 /** Capture after-snapshots and persist (end of agent run / stop). */
 fun endPrompt(projectDir: String) {
  val t = fileState ?: com.ahamai.app.data.FileStateRegistry.tracker(projectDir)
  val idx = currentPromptIndex
  if (idx >= 0) t.endPrompt(idx) else t.endPrompt()
  currentPromptIndex = -1
 }

 /** Apply rewind response to UI session state (shared by last-turn + picker). */
 private fun applyRewindUi(projectDir: String, resp: com.ahamai.app.data.FileRewindResponse) {
  if (!resp.success || resp.revertedFiles.isEmpty()) return
  val restored = sessionFiles.value.toMutableSet()
  for (path in resp.revertedFiles) {
   val f = java.io.File(projectDir, path)
   if (!f.exists()) restored.remove(path) else restored.add(path)
  }
  sessionFiles.value = restored
  val diffs = fileDiffs.value.toMutableMap()
  resp.revertedFiles.forEach { diffs.remove(it) }
  fileDiffs.value = diffs
  undoStack.removeAll { it.second in resp.revertedFiles }
 }

 suspend fun rewindLastTurn(projectDir: String): com.ahamai.app.data.FileRewindResponse =
  withContext(kotlinx.coroutines.Dispatchers.IO) {
   val t = fileState ?: com.ahamai.app.data.FileStateRegistry.tracker(projectDir)
   fileState = t
   val resp = t.rewindLastPrompt()
   applyRewindUi(projectDir, resp)
   resp
  }

 /** Grok-style rewind to a specific prompt index (picker). */
 suspend fun rewindToTurn(projectDir: String, promptIndex: Int): com.ahamai.app.data.FileRewindResponse =
  withContext(kotlinx.coroutines.Dispatchers.IO) {
   val t = fileState ?: com.ahamai.app.data.FileStateRegistry.tracker(projectDir)
   fileState = t
   val resp = t.rewindTo(promptIndex)
   applyRewindUi(projectDir, resp)
   resp
  }

 /** Undo the last state-changing action by restoring the previous content (single file). */
 suspend fun undoLast(projectDir: String): String = withContext(kotlinx.coroutines.Dispatchers.IO) {
 if (undoStack.isEmpty()) return@withContext "Nothing to undo — no state changes recorded."
 val (action, path, oldContent) = undoStack.removeAt(undoStack.lastIndex)
 val f = java.io.File(projectDir, path)
 if (f.exists() || oldContent.isNotBlank()) {
 f.writeText(oldContent)
 val currentFiles = sessionFiles.value.toMutableSet()
 if (oldContent.isBlank()) currentFiles.remove(path) else currentFiles.add(path)
 sessionFiles.value = currentFiles
 undoSnapshots += Triple(action, path, oldContent) // push onto redo
 "Undone: $action $path — previous content restored."
 } else {
 "ERROR: cannot undo $action $path — file does not exist and old content was empty."
 }
 }
 }
 private val holders = java.util.concurrent.ConcurrentHashMap<String, Holder>()
 fun holder(key: String): Holder = holders.getOrPut(key.ifBlank { "workspace" }) { Holder() }

 /** Clear a holder so a brand-new session starts fresh (never inherits a previous session's
 * log/convo). Skips clearing if a run is genuinely live so we don't wipe an active task. */
 fun reset(key: String) {
 val h = holders[key.ifBlank { "workspace" }] ?: return
 if (h.isRunning.value) return
 h.agentLog.value = emptyList()
 h.sessionFiles.value = emptySet(); h.sessionAdded.value = 0; h.sessionRemoved.value = 0
 h.fileDiffs.value = emptyMap(); h.undoStack.clear(); h.pendingConfirmations.clear()
 h.convo.clear(); h.pendingUserMessages.clear(); h.job = null
 h.currentPromptIndex = -1
 // Keep disk checkpoints (resume rewind) — only clear live pointer
 h.fileState = null
 }
}

// Short tips/facts rotated on the build-progress card so the wait feels productive, not idle.
private val BUILD_TIPS = listOf(
 "R8 is shrinking and obfuscating your code to keep the APK small.",
 "Your APK is signed so Android can verify it hasn't been tampered with.",
 "The first cloud build is slower — later builds reuse Gradle's cache.",
 "minSdk 24 means your app runs on Android 7.0 and every newer version.",
 "Resource shrinking strips out unused images and layouts automatically.",
 "Your app is compiled fresh on a clean cloud machine each time.",
 "You can change the app name and icon in res/ and AndroidManifest.xml.",
 "Keep your release keystore safe — you need it for every future update.",
 "Jetpack Compose draws the whole UI in Kotlin — no XML layouts needed.",
 "assembleRelease makes the store-ready APK; assembleDebug is for testing."
)

/** Stable key for a step, used to map a pre-computed (parallel) result back to its step. */
private fun stepKey(s: CodeAgent.AgentStep): String =
 "${s.action}|${s.path}|${s.arg2}|${s.detail.take(60)}"

/** Per-tool timeout — see [com.ahamai.app.data.ToolTimeouts] (every tool is finite). */
private fun toolTimeoutMs(action: String): Long =
    com.ahamai.app.data.ToolTimeouts.ms(action)

// iOS system palette
private val iosBlue = Color(0xFF0A84FF)
// Trae brand palette (extracted from decompiled Trae APK)
private val traeBrand = Color(0xFF7A5F45) // now uses chat Scira accent (light mode)
private val traeBrandActive = Color(0xFF3F31C6) // trae_bg_bg_brand_active — pressed state
// Synara / dpcode-inspired mobile chrome (style only — features unchanged)
private val cDarkBg = Color(0xFF0C0C0E)
private val cDarkRow = Color(0xFF161618)
private val cDarkBorder = Color(0xFFFFFFFF)
private val cDarkElevated = Color(0xFF1C1C1F)
private val cLightBg = Color(0xFFF5F5F7)
private val cLightRow = Color(0xFFF5F5F7)
private val cLightBorder = Color(0xFF999999)
private val cLightElevated = Color(0xFFFFFFFF)
private val cDiffGreen = Color(0xFF22C55E)
private val cDiffRed = Color(0xFFEF4444)
private val cAccentSoft = Color(0xFF0A84FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeAgentScreen(
 modelName: String,
 baseUrl: String,
 apiKey: String,
 initialProjectDir: String? = null,
 initialProjectName: String? = null,
 initialPrompt: String? = null,
 avatarBase64: String = "",
 userName: String = "",
 onChatMode: () -> Unit,
 onProfile: () -> Unit,
 onSpecBuilder: () -> Unit = {}
) {
 val isDark = isSystemInDarkTheme()
 val context = LocalContext.current
 val scope = rememberCoroutineScope()
 val prefs = remember { PreferencesManager(context) }
 val agentHaptics = rememberAgentHaptics()

 // Agent model — initialized from resolved modelName prop (from ApiConfig.agent()),
 // updated via LaunchedEffect so stale-remember never gets stuck on empty string.
 var agentModel by remember { mutableStateOf(prefs.getAgentModel().ifBlank { modelName }) }
 val agentModelSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
 var showAgentModelSheet by remember { mutableStateOf(false) }
 var showAgentVoiceCall by remember { mutableStateOf(false) }
 var agentModels by remember { mutableStateOf<List<String>>(emptyList()) }
 var agentModelsLoading by remember { mutableStateOf(false) }
 var agentModelsError by remember { mutableStateOf<String?>(null) }

 // Preload agent models like chat — sheet opens warm
 LaunchedEffect(Unit) {
  val instant = com.ahamai.app.data.ApiConfig.cachedAgentModels()
   .ifEmpty { com.ahamai.app.data.ApiConfig.listAgentModelsFromConfig(context) }
  if (instant.isNotEmpty()) agentModels = instant
  agentModelsLoading = agentModels.isEmpty()
  val res = withContext(Dispatchers.IO) {
   com.ahamai.app.data.ApiConfig.listAgentModels(context)
  }
  res.fold(
   onSuccess = { agentModels = it },
   onFailure = { if (agentModels.isEmpty()) agentModelsError = it.message }
  )
  agentModelsLoading = false
  // Scrub leftover public ahamai-build-* repos from prior crash/timeout.
  withContext(Dispatchers.IO) {
   val tok = prefs.getGithubToken()
   if (tok.isNotBlank()) {
    runCatching { com.ahamai.app.data.GitHubClient.cleanupOrphanTempBuildRepos(tok) }
   }
  }
 }

 // When the resolved agent model arrives (after Firestore loads), fill in if blank
 LaunchedEffect(modelName) {
 if (modelName.isNotBlank() && agentModel.isBlank()) agentModel = modelName
 }
 // When ApiConfig reloads (admin saves new providers), clear the cached model list
 // so the sheet picks up fresh models next time it opens
 val apiCfgVersion = com.ahamai.app.data.ApiConfig.version
 LaunchedEffect(apiCfgVersion) {
 agentModels = emptyList()
 if (agentModel.isBlank()) {
 val resolved = com.ahamai.app.data.ApiConfig.agent(context).model
 if (resolved.isNotBlank()) agentModel = resolved
 }
 }

 // Project identity is now internal state so this single screen can morph from the
 // "home" view into the working agent view without navigating anywhere.
 var projectDir by remember { mutableStateOf(initialProjectDir ?: "") }
 var projectName by remember { mutableStateOf(initialProjectName ?: "") }
 val started = projectDir.isNotBlank()

 // Live agent state lives in a process-scoped holder resolved from the CURRENT project. A
 // background run writes holder(projectDir); reopening that session from History also reads
 // holder(projectDir) → the running session shows in REAL TIME instead of a blank page.
 val rt = AgentRuntime.holder(projectDir)
 var fileCount by remember { mutableStateOf(if (projectDir.isNotBlank()) ProjectManager.countFiles(projectDir) else 0) }
 var inputText by remember { mutableStateOf("") }
 // /btw side-aside — answers without interrupting / queueing into the main agent run
 var btwOpen by remember { mutableStateOf(false) }
 var btwQuestion by remember { mutableStateOf("") }
 var btwAnswer by remember { mutableStateOf("") }
 var btwLoading by remember { mutableStateOf(false) }
 var btwToolStatus by remember { mutableStateOf<ToolStatus?>(null) }
 var btwJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
 // When a NEW project is created this frame, we run on the NEXT composition so runAgent binds
 // to holder(projectDir) (not the transient "workspace" holder) → correct live state on re-entry.
 var pendingStart by remember { mutableStateOf<String?>(null) }
 var pendingStartDisplay by remember { mutableStateOf<String?>(null) }
 var isRunning by rt.isRunning
 // Safety (logic: ToolPermission/AgentPermissionGate; UI: AgentSafetyUi)
 var permissionMode by remember {
  mutableStateOf(PermissionMode.fromId(prefs.getAgentPermissionMode()))
 }
 var showPermModeSheet by remember { mutableStateOf(false) }
var showRewindPicker by remember { mutableStateOf(false) }
  var showRewindConfirm by remember { mutableStateOf(false) }
  var permDialogReq by remember { mutableStateOf<com.ahamai.app.data.PermissionRequest?>(null) }
 LaunchedEffect(isRunning) {
  while (isRunning) {
   permDialogReq = AgentPermissionGate.promptState.get()?.request
   kotlinx.coroutines.delay(120)
  }
  permDialogReq = AgentPermissionGate.promptState.get()?.request
 }
 // Permission wait → Action Required on Mission Control
 LaunchedEffect(permDialogReq?.action, permDialogReq?.path, projectDir) {
  val req = permDialogReq
  if (req != null && projectDir.isNotBlank()) {
   AgentTaskStore.markNeedsYou(
    context,
    projectDir,
    reason = req.reason.ifBlank { "Permission: ${req.action}" }
   )
  }
 }
 // Agent-mode ad #2: throttled interstitial when a task finishes (isRunning true → false).
var prevRunning by remember { mutableStateOf(false) }
  var showFinishedToast by remember { mutableStateOf(false) }
  var showShipHint by remember { mutableStateOf(false) }
var runStartedAt by rememberSaveable { mutableStateOf(0L) }
  var lastRunElapsedSec by rememberSaveable { mutableStateOf(0) }
   // Hoisted action-elapsed timer — survives lazy-list recomposition (scroll/nav) so the
   // "Responding · 6.9s" label never resets.  Also survives Activity recreation (screen shift
   // / minimize) via rememberSaveable — the LaunchedEffect only resets startAt if it's 0.
   var actionElapsedSec by remember { mutableStateOf(0f) }
   LaunchedEffect(isRunning) {
    if (!isRunning) {
     if (runStartedAt > 0L) {
      lastRunElapsedSec = ((System.currentTimeMillis() - runStartedAt) / 1000L).toInt().coerceAtLeast(0)
     }
     actionElapsedSec = 0f
     return@LaunchedEffect
    }
    // Don't overwrite a saved timestamp — only set once when the run begins.
    if (runStartedAt == 0L) runStartedAt = System.currentTimeMillis()
    val start = runStartedAt
    while (true) {
     actionElapsedSec = (System.currentTimeMillis() - start) / 1000f
     kotlinx.coroutines.delay(100)
    }
   }
  var agentLog by rt.agentLog
 var sessionAdded by rt.sessionAdded
 var sessionRemoved by rt.sessionRemoved
 var sessionFiles by rt.sessionFiles
 var showFiles by remember { mutableStateOf(false) }
 var showEditedFilesSheet by remember { mutableStateOf(false) }
  LaunchedEffect(isRunning, sessionAdded, sessionRemoved, sessionFiles.size) {
   if (prevRunning && !isRunning) {
    com.ahamai.app.ui.components.AgentAds.onTaskComplete(context)
    if (projectDir.isNotBlank()) {
     val elapsed = lastRunElapsedSec.coerceAtLeast(0)
     AgentTaskStore.markFinished(
      context, projectDir,
      added = sessionAdded, removed = sessionRemoved,
      fileCount = sessionFiles.size, elapsedSec = elapsed,
      title = null // keep last user prompt as task title (not project folder name)
     )
     if (sessionFiles.isNotEmpty() || sessionAdded > 0) {
      showFinishedToast = true
      agentHaptics.success()
     }
    }
   }
   prevRunning = isRunning
  }
 val drawerState = rememberDrawerState(DrawerValue.Closed)
 var sessionsRefresh by remember { mutableStateOf(0) }

 // === Z-AI LEVEL: ASK_USER state ===
 // A CompletableDeferred that the agent loop awaits when ASK_USER is called.
 // The UI sets its result when the user answers.
 var askUserQuestions by remember { mutableStateOf<String?>(null) }
 var askUserDeferred by remember { mutableStateOf<kotlinx.coroutines.CompletableDeferred<String>?>(null) }
 // Persistent conversation transcript — survives across runs so the agent never forgets
 // previous steps/changes when it stops mid-task; follow-up messages continue the session.
 val convo = rt.convo
 // Messages the user sends WHILE the agent is running — folded into the next turn (live steering).
 val pendingUserMessages = rt.pendingUserMessages
 val logState = rememberLazyListState()
 // Sticky bottom without frame-pin fighting (images / WebView / mermaid remeasure used to
 // re-trigger near-bottom and cancel flings — made up/down scroll feel stuck).
 fun agentListNearEnd(thresholdPx: Int = 240): Boolean {
  val info = logState.layoutInfo
  val last = info.visibleItemsInfo.lastOrNull() ?: return true
  if (info.totalItemsCount == 0) return true
  if (last.index < info.totalItemsCount - 1) return false
  return (last.offset + last.size) <= (info.viewportEndOffset + thresholdPx)
 }
 var agentStickToBottom by remember { mutableStateOf(true) }
 val agentNearBottom by remember {
  derivedStateOf { agentListNearEnd(140) }
 }
 // User fling/drag unsticks; settle near bottom re-sticks — same DNA as chat
 LaunchedEffect(logState) {
  snapshotFlow { logState.isScrollInProgress }.collect { scrolling ->
   if (scrolling) {
    if (!agentListNearEnd(100)) agentStickToBottom = false
   } else {
    if (agentListNearEnd(140)) agentStickToBottom = true
   }
  }
 }

 // Hardware/gesture back: dismiss BTW panel first, else return to chat history.
 androidx.activity.compose.BackHandler(enabled = true) {
  if (btwOpen) {
   btwJob?.cancel()
   btwJob = null
   btwOpen = false
   btwLoading = false
   btwToolStatus = null
   btwQuestion = ""
   btwAnswer = ""
  } else onChatMode()
 }

 // ---- Home (project picker) state, ported from AgentHomeScreen ----
 var busy by remember { mutableStateOf<String?>(null) }
 var showTokenDialog by remember { mutableStateOf(false) }
 var showGithubLogin by remember { mutableStateOf(false) }
 // Path (project-relative) of a file the user tapped to preview in a bottom sheet.
 var previewPath by remember { mutableStateOf<String?>(null) }
 var showRepoSheet by remember { mutableStateOf(false) }
 var connectedRepo by remember { mutableStateOf(prefs.getConnectedRepo()) }
 var repos by remember { mutableStateOf<List<GitHubClient.GhRepo>>(emptyList()) }
 var reposLoading by remember { mutableStateOf(false) }
 var reposError by remember { mutableStateOf<String?>(null) }
 var showBranchSheet by remember { mutableStateOf(false) }
 var branches by remember { mutableStateOf<List<String>>(emptyList()) }
 var branchesLoading by remember { mutableStateOf(false) }
 // Attachment (camera / photo / file) state
 var showAttachSheet by remember { mutableStateOf(false) }
 var recentPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
 // Connectors: hero sheet (3 floating favicons) first, then full list overlay
 var showConnectorsSheet by remember { mutableStateOf(false) }
 var showConnectorsOverlay by remember { mutableStateOf(false) }
 var showSkillsSheet by remember { mutableStateOf(false) }
 var showSkillsOverlay by remember { mutableStateOf(false) }
 var showAddSkillDialog by remember { mutableStateOf(false) }
 var pendingCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }
 var pendingCameraName by remember { mutableStateOf("") }
 // Attachments staged in the composer before sending (shown as removable previews).
 val pendingAttachments = remember { mutableStateListOf<String>() }
 var pendingStartAtts by remember { mutableStateOf<List<String>>(emptyList()) }

 // Live agentic-browser stream URL. Set whenever a browser step emits [[BROWSER_LIVE]].
 var browserLiveUrl by remember { mutableStateOf<String?>(null) }
 // Full-screen Manus-style "AhamAI's computer" overlay.
 var browserFullScreenOpen by remember { mutableStateOf(false) }
 // Current page URL (address bar + favicon).
 var browserPageUrl by remember { mutableStateOf("") }
 // First-run engine setup (~40s–1min) → dock + overlay show a boot loader.
 var browserBooting by remember { mutableStateOf(false) }
 // Auto-timeout so the dock stays tappable even if boot stalls.
 val browserBootTimeout = remember { mutableStateOf(false) }
 // Elapsed seconds while a browser session is active (Manus dock timer).
 var browserElapsedSec by remember { mutableStateOf(0) }
 // Last human-readable browser action for the status line ("Scrolling down", …).
 var browserStatusDetail by remember { mutableStateOf("") }
 var browserStepLabel by remember { mutableStateOf("") }
 LaunchedEffect(browserBooting) {
 if (browserBooting) {
 browserBootTimeout.value = false
 delay(150_000L)
 if (browserBooting && browserLiveUrl == null) {
 browserBootTimeout.value = true
 }
 }
 }
 // Elapsed timer while browser session is live/booting (shown on Manus-style dock).
 LaunchedEffect(browserLiveUrl, browserBooting) {
 if (browserLiveUrl == null && !browserBooting) return@LaunchedEffect
 while (true) {
 delay(1_000L)
 browserElapsedSec++
 }
 }
 // Tracks whether the live browser mockup is in landscape (true) or portrait (false) mode.
 var browserLandscape by remember { mutableStateOf(true) }

 val muted = if (isDark) Color(0xFF8B8B93) else Color(0xFF71717A)
 val primaryText = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B)

 // Consecutive tool-call rows from one agent turn collapse into a single "Ran N commands,
 // edited M files, read K files" summary chip (see groupAgentLog) instead of a separate pill
 // per call — this is what the LazyColumn below and the scroll-to-top/bottom controls index into.
 val groupedLog = remember(agentLog) { groupAgentLog(agentLog) }


// Soft stick-to-bottom: smooth scrollBy-only — no animateScrollToItem or giant
  // scrollOffset which fought each other and caused up/down jitter during streaming.
  LaunchedEffect(isRunning) {
    if (!isRunning) return@LaunchedEffect
    snapshotFlow {
     val last = agentLog.lastOrNull()
     (last?.text?.length ?: 0) to agentLog.size
    }.collect { (len, _) ->
     if (!isRunning || !agentStickToBottom) return@collect
     if (len == 0) return@collect
     delay(16)
     val info = logState.layoutInfo
     val lastIdx = info.totalItemsCount - 1
     if (lastIdx < 0) return@collect
     val lastVisible = info.visibleItemsInfo.lastOrNull() ?: return@collect
     if (lastVisible.index != lastIdx) {
      // New row added — scrollBy instead of animateScrollToItem so we don't
      // fight with the streaming scroll (no animation cancellation jitter).
      runCatching { logState.scrollBy(info.viewportEndOffset.toFloat()) }
      return@collect
     }
     // Slight overshoot → smooth follow
     val overshoot = (lastVisible.offset + lastVisible.size) - info.viewportEndOffset
     if (overshoot > 0) runCatching { logState.scrollBy(overshoot.toFloat()) }
    }
   }


 // ── Per-UID root for agent session files (parallels projectsRoot in ProjectManager) ──
 // Session files live inside the user's scoped folder, NOT a shared global folder — without
 // this, signing in with a different account on the same device shows the previous account's
 // workspace sessions (broken isolation, identical to the project folder bug we fixed).
 fun agentSessionsDir(): java.io.File {
 val uid = com.ahamai.app.data.AuthManager.uid()?.takeIf { it.isNotBlank() } ?: "guest"
 val dir = java.io.File(context.filesDir, "agent_sessions_$uid").apply { mkdirs() }
 return dir
 }

 // --- Session persistence: save/restore the agent transcript + chat per project ---
 fun saveSession() {
 val pd = projectDir
 if (pd.isBlank()) return
 val pn = projectName
 val convoSnap = convo.toList()
 val logSnap = agentLog
 val sfSnap = sessionFiles.toList()
 com.ahamai.app.AppScope.scope.launch(Dispatchers.IO) {
 try {
 val dir = agentSessionsDir()
 val o = JSONObject()
 o.put("name", pn)
 val convoArr = JSONArray()
 convoSnap.forEach { (r, c) -> convoArr.put(JSONObject().put("r", r).put("c", c)) }
 o.put("convo", convoArr)
 val logArr = JSONArray()
 logSnap.forEach { e ->
 if (e.type in setOf("user", "answer", "narration", "note", "error", "action")) {
 logArr.put(JSONObject().put("t", e.type).put("x", e.text).put("p", e.path).put("r", e.result))
 }
 }
 o.put("log", logArr)
 o.put("sf", JSONArray(sfSnap))
 java.io.File(dir, pd.substringAfterLast('/') + ".json").writeText(o.toString())
 } catch (_: Exception) {}
 }
 }
 fun loadSession(dir: String) {
 scope.launch {
 // Opening/switching a session must not carry over a stale computer monitor.
 browserLiveUrl = null; browserBooting = false; browserPageUrl = ""
 browserElapsedSec = 0; browserStatusDetail = ""; browserStepLabel = ""
 val data = withContext(Dispatchers.IO) {
 // Warm Grok-style project memory (.ahamai/) alongside the transcript restore.
 runCatching { com.ahamai.app.data.ContextMemoryManager.get(dir) }
 try {
 val f = java.io.File(agentSessionsDir(), dir.substringAfterLast('/') + ".json")
 if (f.exists()) JSONObject(f.readText()) else null
 } catch (_: Exception) { null }
 }
 convo.clear()
 val restored = mutableListOf<AgentLogEntry>()
 if (data != null) {
 data.optJSONArray("convo")?.let { ca ->
 for (i in 0 until ca.length()) ca.optJSONObject(i)?.let { convo.add(it.optString("r") to it.optString("c")) }
 }
 data.optJSONArray("log")?.let { la ->
 for (i in 0 until la.length()) la.optJSONObject(i)?.let {
 restored.add(AgentLogEntry(it.optString("t"), it.optString("x"), path = it.optString("p"), result = it.optString("r")))
 }
 }
 data.optJSONArray("sf")?.let { sf ->
 sessionFiles = (0 until sf.length()).mapNotNull { sf.optString(it) }.toSet()
 }
 }
 agentLog = restored
 }
 }

 fun runAgent(task: String, attachments: List<String> = emptyList(), displayText: String? = null) {
 if (task.isBlank() || isRunning) return
 val shown = displayText?.takeIf { it.isNotBlank() } ?: task
 // Agent-mode ad #3: free user over their monthly token allowance → rewarded video on submit.
 com.ahamai.app.ui.components.AgentAds.onTaskSubmitOverLimit(context)
 // Fresh run → clear any stale "AhamAI's Computer" monitor from a previous run, so it only
 // shows when THIS run actually opens the browser (fixes the "always showing" issue).
 browserLiveUrl = null; browserBooting = false; browserPageUrl = ""
 browserElapsedSec = 0; browserStatusDetail = ""; browserStepLabel = ""
 // Avoid duplicate user bubble when submitMessage already seeded the holder optimistically.
 val last = agentLog.lastOrNull()
 val alreadySeeded = last?.type == "user" && (last.text == shown || last.text == task)
 if (!alreadySeeded) {
  agentLog = agentLog + AgentLogEntry("user", shown, attachments = attachments)
 }
 inputText = ""
 isRunning = true
 // Count this as one agent run (Usage screen activity). Mirrors how chat is counted in ChatScreen.
 com.ahamai.app.data.UsageTracker.recordCall(context, "agent")
 com.ahamai.app.AppScope.scope.launch { com.ahamai.app.data.UsageTracker.syncToFirestore(context) }
 val runId = projectDir.ifBlank { "workspace" }
 com.ahamai.app.service.RunningTasks.start(
 id = runId,
 type = com.ahamai.app.service.RunningTasks.Type.WORKSPACE,
 title = projectName.ifBlank { "Workspace" }
 )
 if (projectDir.isNotBlank()) {
  val pn = projectName.ifBlank { projectDir.substringAfterLast('/') }
  AgentTaskStore.markWorking(
   context,
   projectDir,
   title = "", // provisional humanized from prompt — not the folder name
   projectName = pn,
   prompt = task
  )
  showFinishedToast = false
  // AI short title for Mission Control (non-blocking)
  com.ahamai.app.AppScope.scope.launch {
   val ep = runCatching { com.ahamai.app.data.ApiConfig.chat(context) }.getOrNull()
   val bu = ep?.baseUrl?.takeIf { it.isNotBlank() } ?: baseUrl
   val key = ep?.apiKey?.takeIf { it.isNotBlank() } ?: apiKey
   val model = ep?.model?.takeIf { it.isNotBlank() } ?: agentModel
   AgentTaskStore.generateAndApplyTitle(context, projectDir, task, bu, key, model)
  }
 }

 // Run on the process-wide scope so the agent keeps working if the user navigates
 // away or minimizes the app (the keep-alive service holds the process open).
 rt.job = com.ahamai.app.AppScope.scope.launch {
 try {
 BigChangePolicy.beginSession(task)
 BigChangePolicy.enterScope()
 com.ahamai.app.data.FileSessionTracker.clear(projectDir)
 val bigNote = BigChangePolicy.noteForUi()
 if (bigNote.isNotBlank()) {
  agentLog = agentLog + AgentLogEntry("note", bigNote)
 }
 // Permission gate (modes in ToolPermission; prompt UI via AgentPermissionGate)
 suspend fun execAuthorized(step: CodeAgent.AgentStep): String {
  BigChangePolicy.enterScope()

  // SAVE_SKILL: surface skill id in the permission sheet, not the whole markdown body.
  val authPath = if (step.action == "saveskill") {
   step.path.ifBlank {
    Regex("""(?m)^name:\s*([a-z0-9-]+)""")
     .find(step.detail)?.groupValues?.getOrNull(1)
     .orEmpty()
   }.ifBlank { "custom-skill" }
  } else step.path
  val authDetail = if (step.action == "saveskill") {
   val replace = step.arg2.trim().lowercase() in setOf("true", "1", "yes", "replace", "overwrite")
   (if (replace) "Replace skill · " else "Create skill · ") +
    step.detail.lines().take(6).joinToString(" ").take(140)
  } else {
   listOf(step.detail, step.arg2).filter { it.isNotBlank() }.joinToString(" ").take(160)
  }
  val ok = AgentPermissionGate.authorize(
   permissionMode, step.action, authPath, authDetail, projectDir
  )
  if (!ok) return AgentPermissionGate.denyMessage(permissionMode, step.action, authPath)
  return CodeAgent.executeStep(context, projectDir, step)
 }
 // Grok-style: open a rewind checkpoint for this user turn (before any FS mutations).
 val promptIdx = if (projectDir.isNotBlank()) {
  withContext(Dispatchers.IO) { rt.beginPrompt(projectDir) }
 } else -1
 if (promptIdx >= 0) {
  agentLog = agentLog + AgentLogEntry(
   "note",
   "Checkpoint · turn #$promptIdx (Rewind restores all files this turn changes)"
  )
 }
 if (convo.isEmpty()) {
 // Z-AI LEVEL: Reset skills and structured tools for new session —
 // but keep any skills the user just pinned / slash-invoked this send.
 val keepSkillIds = SkillManager.getLoadedSkills().map { it.id }
 val keepSession = com.ahamai.app.data.SkillRuntime.currentSession()
 SkillManager.reset()
 keepSkillIds.forEach { SkillManager.loadSkill(it) }
 // Re-activate slash skill package so L2 body + grants survive session reset
 if (keepSession != null) {
  com.ahamai.app.data.SkillRuntime.activate(
   context, keepSession.skillId, keepSession.arguments, projectDir
  )
 }
 StructuredTools.resetLoadedCategories()
 com.ahamai.app.data.SkillRuntime.setProjectContext(projectDir)
 // Warm Grok-style context memory from disk (.ahamai/) for this project
 withContext(Dispatchers.IO) { com.ahamai.app.data.ContextMemoryManager.get(projectDir) }
 // Parallel project scan (tree + rules + build root) — was 3 serial disk walks.
 val (tree, rules, buildRoot) = coroutineScope {
  val t = async(Dispatchers.IO) { ProjectManager.buildTreeString(projectDir) }
  val r = async(Dispatchers.IO) { ProjectManager.readProjectRules(projectDir) }
  val b = async(Dispatchers.IO) { ProjectManager.detectBuildRoot(projectDir) }
  Triple(t.await(), r.await(), b.await())
 }
 if (rules != null) agentLog = agentLog + AgentLogEntry("note", "Loaded project rules (AGENTS.md)")
 if (buildRoot.isNotBlank()) agentLog = agentLog + AgentLogEntry("note", "Detected build root: $buildRoot/")
 val memStats = com.ahamai.app.data.ContextMemoryManager.statsLine(projectDir, emptyList())
 if (memStats.contains("facts") && !memStats.contains("facts 0")) {
 agentLog = agentLog + AgentLogEntry("note", "Context memory loaded · $memStats")
 }
 convo.add("system" to CodeAgent.systemPrompt())
 convo.add("user" to CodeAgent.buildInitialPrompt(tree, task, rules, attachments, buildRoot, projectDir) + BigChangePolicy.promptAddon())
 } else {
 val changed = if (sessionFiles.isEmpty()) "none yet" else sessionFiles.joinToString(", ")
 // Pin follow-up + inject "already fixed" ledger so model doesn't redo prior work
 val continuity = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.setGoal(projectDir, task)
 com.ahamai.app.data.ContextMemoryManager.buildSessionContinuityBlock(
 projectDir, sessionFiles, currentTask = task
 )
 }
 val memHint = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.buildMemoryBlock(projectDir, worklogExtra = false)
 }.let { if (it.isBlank()) "" else "\n\n$it\n" }
 convo.add(
 "user" to (
 CodeAgent.attachmentPrompt(attachments) +
 "FOLLOW-UP TASK: $task\n\n" +
 "You are continuing the SAME project session.\n" +
 "Files touched so far: $changed.\n\n" +
 continuity + memHint +
 com.ahamai.app.data.ToolTimeouts.agentPolicyBlock() + "\n\n" +
 com.ahamai.app.data.AgentDiscipline.efficiencyPolicyBlock() + "\n\n" +
 "Do ONLY what this follow-up still needs. Do NOT re-apply earlier fixes unless VERIFY shows they are still broken. " +
 "Do NOT re-search the whole project for files already listed in SESSION CONTINUITY. " +
 "Re-READ only if you lack file content or an edit failed. Finish with DONE when this request is done." +
 com.ahamai.app.data.AgentCompletionGuard.followUpStopRedoBlock(projectDir) +
 BigChangePolicy.promptAddon()
 )
 )
 }
 // Grok-style context memory: token-budget compaction (not crude message-count drop).
 // Keeps system + first task + recent turns; middle → episode digests; durable facts stay.
 val compactNote = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.compactIfNeeded(
 projectDir, convo, sessionFiles
 )
 }
 if (compactNote != null) {
 agentLog = agentLog + AgentLogEntry("note", compactNote)
 }
 val working = convo

 var turn = 0
 var done = false
 var completedCleanly = false
 var verified = false
 var planLogIndex = -1
 var planItems = listOf<PlanItem>()
 // Restore incomplete durable plan into UI + local checklist (across turns / restarts)
 val savedPlan = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.getActivePlan(projectDir)
 }
 if (savedPlan.isNotEmpty() && savedPlan.any { !it.done }) {
 planItems = savedPlan.map { PlanItem(it.text, it.done) }
 planLogIndex = agentLog.size
 agentLog = agentLog + AgentLogEntry("plan", "Plan (restored)", plan = planItems)
 val prog = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.planProgressLine(projectDir)
 }
 if (prog != null) agentLog = agentLog + AgentLogEntry("planstep", prog)
 }
 val recentActions = mutableListOf<String>()
 // Exact tool-key → last successful/any result. Used to short-circuit identical re-runs
 // (the classic "already has data, still re-reads / re-verifies" loop).
 val resultByActionKey = HashMap<String, String>()
 val autoFixedKeys = HashSet<String>()
 var consecutiveFailures = 0
 var malformedRetries = 0
 // Explore thrash: after work lands, model often burns 20–30 greps re-finding files.
 var exploreSinceWork = 0
 var hadWorkThisRun = false
 var postEditNudgeSent = false
 while (!done) {
 turn++
 // --- Stream the model response LIVE (tokens + narration appear in real time) ---
 var streamIdx = -1
 var lastShownLen = 0
 // Mid-stream tool execution: a read-only tool starts running the INSTANT its
 // block finishes streaming, overlapping its IO with the model still generating.
 val prefetch = HashMap<String, kotlinx.coroutines.Deferred<String>>()
 val launchedKeys = HashSet<String>()
 var closerCount = 0
 // Resolve the endpoint for the SELECTED agent model so it routes to ITS provider
 // (not the default), and rotate that provider's keys via getNextKey.
 val agentEp = com.ahamai.app.data.ApiConfig.resolveForModel(context, agentModel, agent = true)
 val reqBase = agentEp.baseUrl.takeIf { it.isNotBlank() } ?: baseUrl
 val liveKey = com.ahamai.app.data.ApiConfig.getNextKey(reqBase)
 .takeIf { !it.isNullOrBlank() } ?: agentEp.apiKey.takeIf { it.isNotBlank() } ?: apiKey
 // Native function calling — model returns structured tool_calls JSON.
 // Narration is synthesized from tool names/args by the UI (like Cursor/Kiro).
 val toolsArray = com.ahamai.app.data.StructuredTools.buildToolsArray()
 val response = ApiClient.streamAgentResponseWithTools(reqBase, liveKey, agentModel, working, toolsArray) { partial ->
 // Whenever a NEW tool block closes, fire off any read-only tools right away.
 val closers = Regex("</tool_call>|\\nEND_FILE", RegexOption.IGNORE_CASE).findAll(partial).count()
 if (closers > closerCount) {
 closerCount = closers
 for (s in CodeAgent.parseActions(partial)) {
 if (s.action !in READ_ONLY_ACTIONS) continue
 val sk = stepKey(s)
 if (!launchedKeys.add(sk)) continue
 prefetch[sk] = com.ahamai.app.AppScope.scope.async(Dispatchers.IO) { execAuthorized(s) }
 }
 }
 // Throttle recompositions: refresh ~every 16 new chars or on a line break.
 if (partial.length - lastShownLen < 16 && !partial.endsWith("\n")) return@streamAgentResponseWithTools
 lastShownLen = partial.length
 val preview = CodeAgent.livePreview(partial)
 if (preview.isBlank()) return@streamAgentResponseWithTools
 agentLog = if (streamIdx < 0) {
 streamIdx = agentLog.size
 agentLog + AgentLogEntry("narration", preview, live = true)
 } else if (streamIdx in agentLog.indices) {
 agentLog.toMutableList().also { it[streamIdx] = it[streamIdx].copy(text = preview, live = true) }
 } else agentLog
 }
 val raw = response.getOrNull()
 if (raw == null) {
 if (streamIdx in agentLog.indices) agentLog = agentLog.toMutableList().also { it.removeAt(streamIdx) }
 consecutiveFailures++
 // Hard cap: after 15 retries, stop and surface an error.
 if (consecutiveFailures >= 15) {
 agentLog = agentLog + AgentLogEntry("error", "Could not reach the model after ${consecutiveFailures} attempts. Check your connection and try again.")
 break
 }
 // Fast exponential backoff: 300ms, 600ms, 1200ms, 2000ms, 3000ms, 5000ms, 8000ms (capped)
 val backoff = longArrayOf(300, 600, 1200, 2000, 3000, 5000, 8000)[(consecutiveFailures - 1).coerceAtMost(6)]
 agentLog = agentLog + AgentLogEntry("note", "Connection/model hiccup, retrying (#${consecutiveFailures})…")
 kotlinx.coroutines.delay(backoff)
 continue
 }
 consecutiveFailures = 0
 working.add("assistant" to raw)
 // Token usage (billing): estimate from prompt + response characters this turn.
 com.ahamai.app.data.UsageTracker.recordChatTokens(
 context, working.sumOf { it.second.length }, raw.length
 )

 // Enforce the tool-call format ONLY when needed. A plain-text reply with no
 // tool call is USUALLY the model's final answer (Claude-Code style) — accept it
 // silently instead of nagging. Only nudge (invisibly) when the prose is clearly
 // an incomplete lead-in like "Let me check..." / "I'll now...".
 if (!CodeAgent.containsToolCall(raw)) {
 if (streamIdx in agentLog.indices) agentLog = agentLog.toMutableList().also { it.removeAt(streamIdx) }
 val prose = com.ahamai.app.data.ToolCallParser.stripAll(raw).trim()
 // "Incomplete lead-in" = short, or ends mid-thought ("...", ":", "let me", "I'll", "now").
 val looksIncomplete = prose.length < 40 ||
 Regex("(?i)(let me|i'?ll|i will|now i|let's|hold on|one moment|checking|first,? i|next,? i|going to)\\b[^.!?]{0,40}[.:…]?\\s*$").containsMatchIn(prose) ||
 prose.endsWith(":") || prose.endsWith("…") || prose.endsWith("...")
 if (looksIncomplete && malformedRetries < 2) {
 // Nudge silently — up to 2 retries before accepting as final.
 malformedRetries++
 working.add("user" to ("(Continue with a tool call. To act use e.g. " +
 "<tool_call>READ_FILE<arg_value>path</arg_value></tool_call>; to finish use " +
 "<tool_call>ANSWER<arg_value>your answer</arg_value></tool_call> then <tool_call>DONE</tool_call>.)"))
 continue
 }
 // Otherwise: this prose IS the answer. Show it and finish cleanly.
 agentLog = agentLog + AgentLogEntry("answer", prose.ifBlank { raw.trim() })
 break
 }
 malformedRetries = 0

 val steps = CodeAgent.parseActions(raw)

 // Finalize the live narration entry: keep clean prose, or drop it if the turn
 // was only tool calls / an answer (those get their own rows below).
 val finalNarr = CodeAgent.extractNarration(raw)
 if (streamIdx in agentLog.indices) {
 agentLog = if (finalNarr.length > 2)
 agentLog.toMutableList().also { it[streamIdx] = it[streamIdx].copy(text = finalNarr, live = false) }
 else
 agentLog.toMutableList().also { it.removeAt(streamIdx) }
 }

 if (steps.isEmpty()) {
 agentLog = agentLog + AgentLogEntry("answer", raw.trim())
 break
 }

 // --- Collect READ-ONLY tool results: ones prefetched mid-stream are already
 // running; run any leftover ones in parallel. All resolve concurrently. ---
 val readOnlySteps = steps.filter { it.action in READ_ONLY_ACTIONS }
 val preComputed = HashMap<String, String>()
 if (readOnlySteps.isNotEmpty() || prefetch.isNotEmpty()) {
 // Map each read-only tool key → its action so every await/execute gets a per-tool timeout.
 // Without this a single hung read-only tool (slow network fetch/search, a stuck prefetch)
 // blocked awaitAll() FOREVER and froze the whole agent — the user then had to Stop + resend.
 val actionByKey = readOnlySteps.associate { stepKey(it) to it.action }
 coroutineScope {
 val running = prefetch.entries.map { (k, d) ->
 async {
 val act = actionByKey[k] ?: "read"
 val toMs = toolTimeoutMs(act)
 val r = withTimeoutOrNull(toMs) { runCatching { d.await() }.getOrElse { "ERROR: ${it.message}" } }
 if (r == null) d.cancel() // stop the runaway coroutine so it can't leak/keep blocking
 k to (r ?: ("ERROR: " + com.ahamai.app.data.ToolTimeouts.timeoutMessageForModel(act, "", toMs).trim()))
 }
 }
 val leftover = readOnlySteps.filter { stepKey(it) !in prefetch }.map { s ->
 async(Dispatchers.IO) {
 val toMs = toolTimeoutMs(s.action)
 val r = withTimeoutOrNull(toMs) {
 runCatching { execAuthorized(s) }.getOrElse { "ERROR: ${it.message}" }
 }
 stepKey(s) to (r ?: ("ERROR: " + com.ahamai.app.data.ToolTimeouts.timeoutMessageForModel(
  s.action, s.path, toMs
 ).trim()))
 }
 }
 (running + leftover).awaitAll()
 }.forEach { (k, v) -> preComputed[k] = v }
 }

 // --- Parallel LOCAL mutations (different files at once — instant multi-file edit) ---
 // Snapshots old content per wave (correct undo), then runs each wave concurrently.
 val oldContentByKey = HashMap<String, String>()
 val localMutations = steps.filter {
  it.action in PARALLEL_LOCAL_ACTIONS || it.action in EXCLUSIVE_LOCAL_ACTIONS
 }
 if (localMutations.isNotEmpty()) {
  for (wave in buildLocalParallelWaves(localMutations)) {
   // Snapshot pre-wave file contents once per path (for undo + diff).
   // Grok rewind captures real disk bytes inside ProjectManager mutate hooks;
   // we also prime the tracker here so parallel waves still get a before-shot.
   val waveOldByPath = HashMap<String, String>()
   for (s in wave) {
    if (s.path.isBlank()) continue
    if (s.path !in waveOldByPath) {
     val body = withContext(Dispatchers.IO) {
      // Prefer raw disk text for undo/diff (not the model-facing READ_FILE wrapper).
      try {
       val f = ProjectManager.resolveFile(projectDir, s.path)
       if (f != null && f.isFile && f.length() <= 2_000_000L && ProjectManager.isTextFile(f.name))
        f.readText()
       else ""
      } catch (_: Exception) { "" }
     }
     waveOldByPath[s.path] = body
     withContext(Dispatchers.IO) {
      com.ahamai.app.data.FileStateRegistry.captureBeforeWithContentIfActive(
       projectDir, s.path, if (body.isEmpty()) null else body
      )
     }
    }
    oldContentByKey[stepKey(s)] = waveOldByPath[s.path] ?: ""
    if (s.action in listOf("move", "copy") && s.arg2.isNotBlank()) {
     withContext(Dispatchers.IO) {
      com.ahamai.app.data.FileStateRegistry.captureBeforeIfActive(projectDir, s.arg2)
     }
    }
   }
   if (wave.size == 1) {
    val s = wave[0]
    val sk = stepKey(s)
    if (sk !in preComputed) {
     val toMs = toolTimeoutMs(s.action)
     val r = withTimeoutOrNull(toMs) {
      runCatching { execAuthorized(s) }
       .getOrElse { "ERROR: ${it.message}" }
     } ?: ("ERROR: " + com.ahamai.app.data.ToolTimeouts.timeoutMessageForModel(
      s.action, s.path, toMs
     ).trim())
     preComputed[sk] = r
    }
   } else {
    coroutineScope {
     wave.map { s ->
      async(Dispatchers.IO) {
       val sk = stepKey(s)
       if (sk in preComputed) return@async sk to preComputed.getValue(sk)
       val toMs = toolTimeoutMs(s.action)
       val r = withTimeoutOrNull(toMs) {
        runCatching { execAuthorized(s) }
         .getOrElse { "ERROR: ${it.message}" }
       } ?: ("ERROR: " + com.ahamai.app.data.ToolTimeouts.timeoutMessageForModel(
        s.action, s.path, toMs
       ).trim())
       sk to r
      }
     }.awaitAll()
    }.forEach { (k, v) -> preComputed[k] = v }
   }
  }
 }

 val results = StringBuilder()
 /** Per-step tool outputs for ContextMemoryManager.observe (fix ledger + errors). */
 val stepResults = mutableListOf<String>()
 var structureChanged = false
 for (step in steps) {
 var actionIndex = -1
 when (step.action) {
 "answer" -> {
   agentLog = agentLog + AgentLogEntry("answer", step.detail)
   done = true
   completedCleanly = true
   // Don't leave sticky plan/open-loops that cause the next turn to "restart" finished work.
  withContext(Dispatchers.IO) {
   com.ahamai.app.data.ContextMemoryManager.clearPlan(projectDir)
   com.ahamai.app.data.AgentCompletionGuard.markRunComplete(
    projectDir = projectDir,
    summary = step.detail.take(400).ifBlank { task.take(200) },
    task = task,
    filesTouched = sessionFiles
   )
  }
  planItems = emptyList()
 }
 "plan" -> {
 planItems = step.detail.split("\n")
 .map { it.trim().removePrefix("-").removePrefix("*").trim() }
 .filter { it.isNotBlank() }
 .map { PlanItem(it.replace(Regex("^\\d+[.)]\\s*"), ""), false) }
 if (planItems.isNotEmpty()) {
 planLogIndex = agentLog.size
 agentLog = agentLog + AgentLogEntry("plan", "Plan", plan = planItems)
 withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.setPlan(
 projectDir, planItems.map { it.text }
 )
 }
 }
 results.append(
 "[PLAN]\nPlan recorded with ${planItems.size} steps (saved to project memory). " +
 "Execute them and COMPLETE_STEP <n> as you finish each (1-based index or step text).\n\n"
 )
 }
 "completestep" -> {
 val (ok, status) = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.completePlanStep(projectDir, step.detail)
 }
 // Sync UI checklist from durable plan when possible
 val durable = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.getActivePlan(projectDir)
 }
 if (durable.isNotEmpty()) {
 planItems = durable.map { PlanItem(it.text, it.done) }
 if (planLogIndex in agentLog.indices)
 agentLog = agentLog.toMutableList().also {
 it[planLogIndex] = it[planLogIndex].copy(plan = planItems)
 }
 }
 // Fallback: local index mark if durable empty
 if (durable.isEmpty()) {
 val idx = (step.detail.trim().toIntOrNull() ?: 0) - 1
 if (idx in planItems.indices) {
 planItems = planItems.mapIndexed { i, p -> if (i == idx) p.copy(done = true) else p }
 if (planLogIndex in agentLog.indices)
 agentLog = agentLog.toMutableList().also {
 it[planLogIndex] = it[planLogIndex].copy(plan = planItems)
 }
 }
 }
 val prog = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.planProgressLine(projectDir)
 } ?: run {
 val nextIdx = planItems.indexOfFirst { !it.done }
 if (nextIdx >= 0) "Step ${nextIdx + 1}/${planItems.size} · ${planItems[nextIdx].text}"
 else if (planItems.isNotEmpty()) "All ${planItems.size} steps done"
 else status
 }
 agentLog = agentLog + AgentLogEntry("planstep", prog)
 results.append("[COMPLETE_STEP]\n$status\n\n")
 if (!ok) results.append("Use COMPLETE_STEP with a valid 1-based step number or unique step text.\n\n")
 }
 "done" -> {
 // Completion guard: stop "finished then restart" loops (plan sticky / false deliverable / double verify).
 val userTaskFull = convo.firstOrNull { it.first == "user" }?.second.orEmpty()
 val hasAnswer = agentLog.any { it.type == "answer" }
 val didEdits = rt.undoStack.any {
  it.first in listOf("edit", "multiedit", "bulkedit", "applypatch", "write", "create", "insertlines")
 } || sessionFiles.isNotEmpty()
 val hasFileCard = agentLog.any { it.type == "filechip" }
 val guard = com.ahamai.app.data.AgentCompletionGuard.evaluateDone(
  projectDir = projectDir,
  alreadyNudged = verified,
  turn = turn,
  hasAnswer = hasAnswer,
  didEdits = didEdits,
  sessionFiles = sessionFiles,
  hasFileChip = hasFileCard,
  userTask = userTaskFull + "\n" + task
 )
 when (guard.decision) {
  com.ahamai.app.data.AgentCompletionGuard.Decision.NUDGE -> {
   verified = true
   results.append(guard.nudgeMessage)
  }
  com.ahamai.app.data.AgentCompletionGuard.Decision.FINISH -> {
   if (didEdits && !verified) {
    verified = true
    val staticReport = withContext(Dispatchers.IO) {
     val mode = if (sessionFiles.any { it.contains("AndroidManifest") || it.endsWith(".kt") })
      "android" else "quick"
     com.ahamai.app.data.StaticVerifier.verify(projectDir, pathScope = "", mode = mode).format(24)
    }
    if (staticReport.contains("FAILED")) {
     results.append("[STATIC VERIFY]\n$staticReport\n\n")
     // Targeted fix only — no whole-tree re-search phase after verify fails.
     results.append(com.ahamai.app.data.AgentDiscipline.staticVerifyFixNudge(staticReport))
    } else {
     done = true
    }
    } else {
     done = true
     completedCleanly = true
    }
    if (done) {
     if (step.detail.isNotBlank() && agentLog.lastOrNull()?.type != "answer") {
     agentLog = agentLog + AgentLogEntry("note", step.detail)
    }
    withContext(Dispatchers.IO) {
     if (guard.clearPlan) com.ahamai.app.data.ContextMemoryManager.clearPlan(projectDir)
     com.ahamai.app.data.AgentCompletionGuard.markRunComplete(
      projectDir = projectDir,
      summary = step.detail.ifBlank { task.take(200) },
      task = task,
      filesTouched = sessionFiles
     )
    }
    planItems = emptyList()
   }
  }
 }
 }
 // ===== Z-AI LEVEL: ASK_USER — pause loop, wait for user =====
 "askuser" -> {
 actionIndex = agentLog.size
 val questionsJson = step.detail
 // Recovery card in the log (tap Answer again if sheet was dismissed)
 agentLog = agentLog + AgentLogEntry(
 type = "recovery",
 text = "Agent needs your input",
 path = "Answer to continue",
 result = questionsJson,
 actionKind = "askuser",
 live = true
 )
 // Mission Control → Action Required while blocked on the user
 if (projectDir.isNotBlank()) {
  AgentTaskStore.markNeedsYou(context, projectDir, "Needs your answer")
 }
 try {
 askUserQuestions = questionsJson
 val deferred = CompletableDeferred<String>()
 askUserDeferred = deferred
 // SUSPEND the agent loop until the user answers
 val userAnswers = deferred.await()
 askUserQuestions = null
 askUserDeferred = null
 if (projectDir.isNotBlank()) {
  AgentTaskStore.upsert(context, projectDir, status = AgentTaskStore.Status.WORKING)
 }
 if (actionIndex in agentLog.indices) {
 agentLog = agentLog.toMutableList().also {
 it[actionIndex] = it[actionIndex].copy(
 live = false,
 text = "You answered · agent continuing",
 path = "Answered"
 )
 }
 }
 // Inject the user's answers into the conversation
 results.append("[USER ANSWERS]\n$userAnswers\n\nContinue with these requirements in mind. Do NOT ask again — proceed with the task using these answers.\n\n")
 working.add("user" to "[ASK_USER ANSWERS]\n$userAnswers")
 } catch (e: kotlinx.coroutines.CancellationException) {
 askUserQuestions = null
 askUserDeferred = null
 if (actionIndex in agentLog.indices) {
 agentLog = agentLog.toMutableList().also {
 it[actionIndex] = it[actionIndex].copy(live = false, path = "Cancelled")
 }
 }
 throw e
 }
 }
 // ===== Z-AI LEVEL: PARALLEL_TASKS — multiple sub-agents at once =====
 "paralleltasks" -> {
 actionIndex = agentLog.size
 agentLog = agentLog + AgentLogEntry("action", "Launching parallel agents", path = "${step.detail.take(80)}...")
 try {
 val tasksArr = JSONArray(step.detail)
 val worklog = SpecializedAgents.readWorklog(projectDir)
 val taskTriples = mutableListOf<Triple<String, String, String>>()
 for (i in 0 until tasksArr.length().coerceAtMost(4)) {
 val t = tasksArr.getJSONObject(i)
 val desc = t.optString("description", t.optString("prompt", t.optString("task", "")))
 val type = t.optString("agent_type", t.optString("agent", "general-purpose"))
 if (desc.isNotBlank()) taskTriples.add(Triple(desc, type, worklog))
 }
 if (taskTriples.isNotEmpty()) {
 val parallelResults = SpecializedAgents.runParallel(
 context, projectDir, baseUrl, liveKey, agentModel, taskTriples
 )
 val sb = StringBuilder("[PARALLEL RESULTS (${parallelResults.size} agents)]\n\n")
 parallelResults.forEachIndexed { i, r ->
 sb.append("--- Agent ${i + 1} ---\n$r\n\n")
 }
 results.append(sb.toString())
 working.add("user" to sb.toString())
 } else {
 results.append("ERROR: PARALLEL_TASKS needs at least one task.\n")
 }
 } catch (e: kotlinx.coroutines.CancellationException) {
 throw e
 } catch (e: Exception) {
 results.append("ERROR: Parallel tasks failed: ${e.message}\n")
 }
 }
 else -> {
 // First browser step kicks off the cloud-browser boot (~40s–1min);
 // Manus-style: show dock + open computer overlay; track status for the live card.
 if (step.action.startsWith("browser") || step.action.startsWith("computer")) {
 val freshSession = browserLiveUrl == null && !browserBooting
 if (browserLiveUrl == null) browserBooting = true
 if (freshSession) browserElapsedSec = 0
 browserFullScreenOpen = true
 val (lab, detail) = CodeAgent.actionLabel(step)
 browserStepLabel = lab
 browserStatusDetail = listOf(lab, detail).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { lab }
 }
 val (label, path) = CodeAgent.actionLabel(step)
 // Surface the model's one-line "why" (explanation) as the subtitle when given.
 // Strip any XML tags that may have leaked into the path/explanation.
 val rawSubtitle = if (step.explanation.isNotBlank()) step.explanation.take(80) else path
 val subtitle = rawSubtitle.replace(Regex("<[^>]*>"), "").trim()
 // ghbuildstatus gets its own live build-progress card instead of a plain row.
 if (step.action != "ghbuildstatus") {
 actionIndex = agentLog.size
 val budget = toolTimeoutMs(step.action)
 val budgetLabel = com.ahamai.app.data.ToolTimeouts.formatBudget(budget)
 val basePath = subtitle.ifBlank { "" }
 // Show timeout budget on every tool log from the start; live elapsed ticks in the row UI.
 val pathWithBudget = listOf(basePath, "timeout $budgetLabel")
  .filter { it.isNotBlank() }.joinToString(" · ")
 agentLog = agentLog + AgentLogEntry(
  type = "action",
  text = label,
  path = pathWithBudget,
  actionKind = step.action,
  live = true,
  timeoutBudgetMs = budget,
  startedAtMs = System.currentTimeMillis()
 )
 }
 }
 }
 if (step.action in listOf("done", "answer", "plan", "completestep")) {
 // Still track for memory.observe so DONE/plan land in the fix ledger / open loops
 stepResults.add(
 when (step.action) {
 "done" -> "DONE ${step.detail.take(200)}"
 "answer" -> "ANSWER ${step.detail.take(200)}"
 "plan" -> "PLAN ${step.detail.take(200)}"
 else -> "OK"
 }
 )
 continue
 }

 // --- Soft anti-loop (never hard-stop, never show recovery UI) ---
 // Sliding-window repeat counts were false-positive heavy: real agent work often
 // reuses tools (re-read after edits, re-run tests, cloud shell, browser, GH polls).
 // We only (1) reuse successful cached results for identical args, and (2) inject a
 // quiet system hint after a *consecutive* identical streak — the run always continues.
 val loopExempt = step.action in listOf(
  "ghbuild", "ghbuildstatus", "ghlogs", "ghpush", "ghpr", "ghissues", "ghcreateissue",
  "ghrepos", "ghbranches", "ghreadremote", "ghlistremote", "ghcreaterepo",
  "ghcreatebranch", "ghswitchbranch", "ghopenrepo", "ghstatus",
  "askuser", "paralleltasks", "loadskill", "listskillresources", "readskillresource",
  "runskillscript", "saveskill",
  "worklog", "remember", "forget", "memory",
  // Iterative / poll / sandbox work — repeating is normal progress, not stuck:
  "cloudshell", "runpython", "runjob", "jobstatus", "http", "fetch",
  "websearch", "imagesearch", "readurl", "screenshot", "previewweb",
  "browseropen", "browserclick", "browsertype", "browserscroll", "browsershot",
  "computeropen", "computerclick", "computertype", "completestep",
  "plan", "answer", "done", "task", "analyzeimage",
  "listartifacts", "listdownloads", "list", "glob", "repomap"
 )
 val key = "${step.action}|${step.path}|${step.arg2}|${step.detail.take(40)}"
 // Consecutive streak of the exact same key only (interleaved different tools reset).
 val consecutiveSame = recentActions.takeLastWhile { it == key }.size
 recentActions.add(key)

 // Reuse a prior *successful* result for the exact same call — saves time, no UI.
 // ERROR outcomes are NOT cached so transient failures can be retried.
 val cachedPrior = resultByActionKey[key]
 var forcedCachedResult: String? = null
 if (!loopExempt && cachedPrior != null) {
 forcedCachedResult = cachedPrior
 // Quiet hint only after the model hammers the same cached call repeatedly.
 if (consecutiveSame >= 2) {
 results.append(
 "[SYSTEM] Reusing prior result for ${step.action.uppercase()} ${step.path.ifBlank { "(same args)" }}. " +
 "Do not re-verify unless the file changed. Prefer NEW work, or ANSWER + DONE if finished.\n\n"
 )
 }
 } else if (!loopExempt && consecutiveSame >= 4) {
 // Soft model-only nudge — never stop the loop, never surface a recovery card.
 results.append(
 "[SYSTEM] Same tool+args ${consecutiveSame + 1}x in a row. " +
 "If results are unchanged, try a different approach or ANSWER + DONE. Continuing.\n\n"
 )
 }

 val needsDiff = step.action in listOf("edit", "write", "create", "delete", "move", "bulkedit", "multiedit", "applypatch", "insertlines")
 // Prefer pre-wave snapshot (taken before parallel mutate). Fallback: live read only if needed.
 val oldContent = if (!needsDiff) ""
 else oldContentByKey[stepKey(step)]
  ?: withContext(Dispatchers.IO) {
   ProjectManager.readFile(projectDir, step.path).let { if (it.startsWith("ERROR")) "" else it }
  }

 val toolStartMs = System.currentTimeMillis()
 // Human subtitle for this step (path / explanation) — kept stable while timer ticks.
 val stepSubtitle = run {
  val (_, p) = CodeAgent.actionLabel(step)
  val raw = if (step.explanation.isNotBlank()) step.explanation.take(80) else p
  raw.replace(Regex("<[^>]*>"), "").trim()
 }
 // 30s progress pings collected for the model + UI notes (every tool).
 val progressPings = mutableListOf<String>()
 // Heartbeat: 1s UI timer + every 30s a [PROGRESS PING] so agent knows work is alive.
 suspend fun runWithTimeoutAndHeartbeat(
  budgetMs: Long,
  block: suspend () -> String
 ): String {
  val idx = actionIndex
  val started = System.currentTimeMillis()
  return coroutineScope {
   val beat = launch {
    var nextPingAt = com.ahamai.app.data.ToolTimeouts.PING_EVERY_MS
    var pingN = 0
    while (true) {
     delay(1_000L)
     val el = System.currentTimeMillis() - started
     // Live elapsed/budget on the action row
     if (idx in agentLog.indices) {
      val e = agentLog[idx]
      if (e.type == "action" && e.live) {
       val progress = com.ahamai.app.data.ToolTimeouts.progressLabel(el, budgetMs, finished = false)
       val pathNext = listOf(stepSubtitle, progress).filter { it.isNotBlank() }.joinToString(" · ")
       agentLog = agentLog.toMutableList().also {
        it[idx] = e.copy(path = pathNext, timeoutBudgetMs = budgetMs, startedAtMs = started)
       }
      }
     }
     // Every 30s: ping for agent understanding + soft UI note
     if (el >= nextPingAt) {
      pingN++
      val line = com.ahamai.app.data.ToolTimeouts.progressPingLine(
       step.action, step.path, el, budgetMs, pingN
      )
      progressPings.add(line)
      nextPingAt += com.ahamai.app.data.ToolTimeouts.PING_EVERY_MS
     }
     // Stop beating after budget (timeout will win)
     if (el >= budgetMs + 2_000L) break
    }
   }
   try {
    val raw = withTimeoutOrNull(budgetMs) {
     runCatching { block() }.getOrElse { "ERROR: ${it.message?.take(200)}" }
    } ?: ("ERROR: " + com.ahamai.app.data.ToolTimeouts.timeoutMessageForModel(
     step.action, step.path, budgetMs
    ).trim())
    // Attach 30s pings so the model sees the tool was healthy / long-running.
    com.ahamai.app.data.ToolTimeouts.attachPingsToResult(raw, progressPings.toList())
   } finally {
    beat.cancel()
   }
  }
 }

 val toolTimeout = toolTimeoutMs(step.action)
 val result = if (forcedCachedResult != null) {
 forcedCachedResult
 } else if (step.action == "ghpush") {
 runWithTimeoutAndHeartbeat(toolTimeout) {
  pushChangedFiles(context, projectDir, sessionFiles.toList(), step.detail, step.arg2)
 }
 } else if (step.action == "ghbuild") {
 runWithTimeoutAndHeartbeat(toolTimeout) {
  buildApkFlow(context, projectDir, projectName, sessionFiles.toList())
 }
 } else if (step.action == "verifybuild") {
 runWithTimeoutAndHeartbeat(toolTimeout) {
  withContext(Dispatchers.IO) {
   com.ahamai.app.data.StaticVerifier.verify(
    projectDir,
    pathScope = "",
    mode = "android"
   ).format()
  }
 }
 } else if (step.action == "ghbuildstatus") {
 // Live build-progress card: step checklist + % + ETA + log tail + tips.
 val bpIdx = agentLog.size
 agentLog = agentLog + AgentLogEntry(
  "buildprogress", "queued", path = BUILD_TIPS[0],
  timeoutBudgetMs = toolTimeout, startedAtMs = System.currentTimeMillis(), live = true
 )
 var pollN = 0
 val out = runWithTimeoutAndHeartbeat(toolTimeout) {
  buildStatusFlow(context) { label, progress, steps, logTail ->
   val tip = BUILD_TIPS[(pollN++ / 2) % BUILD_TIPS.size]
   if (bpIdx in agentLog.indices)
    agentLog = agentLog.toMutableList().also {
     it[bpIdx] = it[bpIdx].copy(
      text = label, path = tip, buildSteps = steps,
      logTail = logTail, buildProgress = progress
     )
    }
  }
 }
 // Freeze the card in its final (done) state — stops the spinner, triggers confetti.
 if (bpIdx in agentLog.indices)
  agentLog = agentLog.toMutableList().also {
   it[bpIdx] = it[bpIdx].copy(result = if (out.startsWith("ERROR")) "failed" else "done", live = false)
  }
 out
 } else if (step.action == "task") {
 val agentType = step.path.ifBlank { "general-purpose" }
 val worklog = SpecializedAgents.readWorklog(projectDir)
 runWithTimeoutAndHeartbeat(toolTimeout) {
  SpecializedAgents.runSpecialized(
   context, projectDir, baseUrl, liveKey, agentModel,
   step.detail, agentType, worklog
  )
 }
 } else {
 // Reuse the result already computed (prefetched/parallel) for read-only tools.
 // Otherwise execute with timeout + auto-retry (prevents UI hang on stuck tools).
 val cached = preComputed[stepKey(step)]
 if (cached != null) cached
 else {
 var toolResult: String? = null
 var toolRetries = 0
 while (toolResult == null && toolRetries < 2) {
  val attempt = toolRetries + 1
  toolResult = runWithTimeoutAndHeartbeat(toolTimeout) {
   runCatching { execAuthorized(step) }
    .getOrElse { "ERROR: ${it.message?.take(200)}" }
  }
  // runWithTimeout returns ERROR string on timeout — detect & allow one retry for short tools only
  if (toolResult.startsWith("ERROR:") && toolResult.contains("TIMEOUT", ignoreCase = true)) {
   toolRetries++
   if (toolRetries < 2 && !com.ahamai.app.data.ToolTimeouts.isLongRunning(step.action)) {
    agentLog = agentLog + AgentLogEntry(
     "note",
     "Tool timeout (${com.ahamai.app.data.ToolTimeouts.formatBudget(toolTimeout)}), retrying once…"
    )
    toolResult = null // retry
   } else {
    // Keep the timeout ERROR (includes model guidance); stop retrying long tools.
    break
   }
  }
 }
 toolResult ?: ("ERROR: " + com.ahamai.app.data.ToolTimeouts.timeoutMessageForModel(
  step.action, step.path, toolTimeout, attempt = 2, maxAttempts = 2
 ).trim())
 }
 }
 // Rebind workspace when agent opens/switches a GitHub repo/branch (marker from GitHubClient).
 val openMarker = Regex("\\[\\[GH_OPEN_PROJECT\\]](.+?)\\[\\[/GH_OPEN_PROJECT\\]]")
 var resultForMemory = result
 openMarker.find(result)?.groupValues?.getOrNull(1)?.let { payload ->
  val parts = payload.split("|")
  val newDir = parts.getOrNull(0)?.trim().orEmpty()
  val newName = parts.getOrNull(1)?.trim().orEmpty()
  if (newDir.isNotBlank() && java.io.File(newDir).isDirectory) {
   projectDir = newDir
   projectName = newName.ifBlank { newDir.substringAfterLast('/') }
   fileCount = ProjectManager.countFiles(newDir)
   connectedRepo = prefs.getConnectedRepo()
   // Clear session file/diff tracking for the new workspace
   sessionFiles = emptySet()
   sessionAdded = 0
   sessionRemoved = 0
   rt.fileDiffs.value = emptyMap()
   agentLog = agentLog + AgentLogEntry(
    "note",
    "Workspace switched → ${projectName.ifBlank { newDir }} @ ${prefs.getConnectedBranch().ifBlank { "default" }}"
   )
   val tree = withContext(Dispatchers.IO) { ProjectManager.buildTreeString(newDir) }
   convo.add("user" to "WORKSPACE UPDATED — you are now editing a new local copy of the connected GitHub repo:\nRepo: ${prefs.getConnectedRepo()} @ ${prefs.getConnectedBranch()}\n\nFILE TREE:\n$tree\n\nContinue with LIST_FILES / READ_FILE on this tree. Do not edit the old project path.")
  }
  resultForMemory = result.replace(openMarker, "").trim()
 }
 // Cache successful outcomes only (so identical re-calls can short-circuit).
 // Never cache ERROR — retries after timeouts/flakes must still re-execute.
 if (forcedCachedResult == null && !result.startsWith("ERROR")) {
 resultByActionKey[key] = resultForMemory
 // After a successful mutation, drop STALE read/search caches for that path only
 // (content changed). Keep recentActions — consecutive streak still detects true loops.
 if (needsDiff && step.path.isNotBlank()) {
 val p = step.path
 resultByActionKey.keys.removeAll { k ->
 val act = k.substringBefore('|')
 val pathPart = k.substringAfter('|', "").substringBefore('|')
 act in setOf("read", "readlines", "grep", "search", "codebasesearch") && pathPart == p
 }
 }
 }
 // Compress huge tool dumps before they enter conversation memory (re-read files for detail).
 val compressed = com.ahamai.app.data.ContextMemoryManager.compressToolResult(
 step.action, step.path, resultForMemory
 )
 results.append("[${step.action.uppercase()} ${step.path}]\n$compressed\n\n")
 // Timeouts: soft amber note in the log (no blocking popup). Full guidance is already
 // in the tool result string so the model sees it once and adapts (split work / new approach).
 val isToolTimeout = result.startsWith("ERROR") &&
  (result.contains("[TIMEOUT]", ignoreCase = true) ||
   result.contains("timed out", ignoreCase = true))
 if (isToolTimeout) {
  val budget = toolTimeoutMs(step.action)
  val label = listOf(step.action.uppercase(), step.path).filter { it.isNotBlank() }.joinToString(" · ")
  agentLog = agentLog + AgentLogEntry(
   type = "note",
   text = "Timeout · $label · budget ${com.ahamai.app.data.ToolTimeouts.formatBudget(budget)} — agent will adapt"
  )
  // Ensure model gets recovery guidance even if a legacy timeout string lacked [TIMEOUT].
  if (!result.contains("[TIMEOUT]", ignoreCase = true)) {
   results.append(
    com.ahamai.app.data.ToolTimeouts.timeoutMessageForModel(
     step.action, step.path, budget
    )
   )
  }
 }
 // Error recovery: allow ONE silent retry nudge per exact key — not infinite AUTO-FIX loops.
 if (result.startsWith("ERROR") && !isToolTimeout &&
  step.action !in listOf("answer", "done", "plan", "completestep")
 ) {
 if (key in autoFixedKeys) {
 results.append(
 "[SYSTEM] Same tool failed again with the same args. Do NOT retry identically. " +
 "Use different args, a different tool, or ANSWER + DONE if blocked.\n\n"
 )
 } else {
 autoFixedKeys.add(key)
 results.append(
 "[AUTO-FIX] Tool error (one retry allowed with DIFFERENT args): " +
 "fix path/anchor and continue. Do not report this to the user.\n\n"
 )
 }
 }
 // Only flag a STRUCTURE change (which re-injects the whole file tree next turn) when the
 // tree genuinely changed. A WRITE/CREATE onto an EXISTING file is a content-only edit —
 // the tree is identical, so re-sending it every edit just bloats the prompt and slows
 // streaming on multi-edit tasks. Treat write/create as structural ONLY for a NEW file.
 if (!result.startsWith("ERROR") && step.action in STRUCTURE_ACTIONS) {
 val contentOnlyWrite = step.action in listOf("write", "create") && oldContent.isNotBlank()
 if (!contentOnlyWrite) structureChanged = true
 }
 // ── Undo stack: save old content before destructive state changes, so we can revert. ──
 if (!result.startsWith("ERROR") && step.action in listOf("edit", "write", "create", "delete", "move", "bulkedit", "multiedit", "applypatch", "insertlines")) {
 rt.undoStack.add(Triple(step.action, step.path, oldContent))
 }
 // ── Self-review: the edit already SUCCEEDED (anchor matched — a miss returns ERROR and is
 // handled above). Do NOT invite a routine READ_LINES here: nudging a re-read after every
 // single edit burned a turn per edit AND was a major cause of the "edit → re-read → edit
 // again" repeat loop the user saw. Only re-read if YOU have a concrete reason to doubt it. ──
 if (!result.startsWith("ERROR") && step.action in listOf("edit", "multiedit", "bulkedit", "applypatch", "insertlines")) {
 results.append(
 "[OK] Edit to ${step.path} applied. Do NOT re-read or re-verify this file. " +
 "Next: edit something else still needed, or ANSWER + DONE.\n\n"
 )
 }
  // WRITE full rewrite OK after READ this session; MULTI_EDIT for partial multi-hunk.
 if (step.action == "write" && result.startsWith("ERROR") && result.contains("requires READ_FILE")) {
 results.append(
  "[HINT] READ the file first this session, then WRITE full content — " +
  "or use MULTI_EDIT / EDIT_FILE for partial changes.\n\n"
 )
 }
 // ── Live preview tools emit a sentinel token; parse it and show an inline card.
 if (step.action == "previewweb" && result.contains("[[PREVIEW_WEB_APP]]")) {
 val m = Regex("\\[\\[PREVIEW_WEB_APP\\]\\](.+?)\\[\\[/PREVIEW_WEB_APP\\]\\]").find(result)
 if (m != null) {
 agentLog = agentLog + AgentLogEntry(
 type = "preview",
 text = "Live preview",
 previewKind = "webapp",
 previewPayload = m.groupValues[1].trim()
 )
 }
 } else if (step.action == "renderdiagram" && result.contains("[[DIAGRAM_IMAGE]]")) {
 // Diagram rendered — use the original mermaid/graphviz source from step.detail
 // and render it via PreviewCard (Mermaid.js WebView), NOT via downloaded PNG
 // (which often shows blank when Kroki/mermaid.ink fails or returns a bad file).
 val mermaidSource = step.detail.ifBlank { 
 // Fallback: try to extract from result (shouldn't normally happen)
 Regex("\\[\\[DIAGRAM_IMAGE\\]\\](.+?)\\[\\[/DIAGRAM_IMAGE\\]\\]").find(result)?.groupValues?.get(1)?.trim() ?: ""
 }
 if (mermaidSource.isNotBlank()) {
 agentLog = agentLog + AgentLogEntry(
 type = "preview",
 text = "Diagram",
 previewKind = "diagram",
 previewPayload = mermaidSource
 )
 }
 } else if (step.action == "renderchart" && result.contains("[[RENDER_CHART]]")) {
 // Emit an inline chart preview card (Chart.js in a WebView).
 val m = Regex("\\[\\[RENDER_CHART\\]\\]([\\s\\S]+?)\\[\\[/RENDER_CHART\\]\\]").find(result)
 if (m != null) {
 agentLog = agentLog + AgentLogEntry(
 type = "preview",
 text = "Chart",
 previewKind = "chart",
 previewPayload = m.groupValues[1].trim()
 )
 }
 }
 // ── Live agentic-browser: parse the stream URL + page URL for ANY browser step
 // (not just one action). This pins the live WebView above the log and clears the
 // "booting" loader. NOTE: this MUST run for browser* actions — keeping it gated on
 // renderchart was the bug that left browserLiveUrl null and looped "booting…".
 if (step.action.startsWith("browser") || step.action.startsWith("computer")) {
 // If the tool returned an ERROR (daemon setup failed, health check timed out, etc.),
 // clear the booting state and surface the monitor icon so the user can retry.
 if (result.startsWith("ERROR")) {
 browserBooting = false
 // Keep fullscreen open so the user can read the error / retry without hunting icons.
 browserFullScreenOpen = true
 } else {
 Regex("\\[\\[BROWSER_LIVE\\]\\](.+?)\\[\\[/BROWSER_LIVE\\]\\]").find(result)?.let {
 val u = it.groupValues[1].trim().trimEnd('/')
 if (u.isNotBlank()) {
 browserLiveUrl = u
 browserFullScreenOpen = true
 }
 }
 Regex("(?m)^URL:\\s*(.+)$").find(result)?.let {
 browserPageUrl = it.groupValues[1].trim()
 }
 browserBooting = false
 }
 }
 // ── Manus-style deliverable cards: when a tool produces a user-facing file
 // (PDF / Office / image / zip / md / …), emit a "filechip" so the chat shows
 // a colored icon pill the user can tap (preview + save). Folder-only tools
 // (makeshorts) and pure device-write tools without a project path are skipped.
 val filechipActions = setOf(
 "createpptx", "createxlsx", "createdocx", "createcsv",
 "pdfcreate", "generateimage", "screenshot", "apkrebuild",
 "pdfaddchart", "pdfcompress", "zip", "imageedit", "download",
 "pdfedit", "pdfaddpage", "pdfaddimage", "pdffillform",
 "pdfmerge", "pdfsplit", "pdfcollage",
 // Also surface WRITE/CREATE/CLOUD_PULL/EXPORT of deliverable files
 "write", "create", "cloudpull", "exporttodevice", "copy", "move"
 )
 if (!result.startsWith("ERROR") && step.action in filechipActions) {
 val parsedPath = when {
 result.contains("saved to ") ->
 result.substringAfter("saved to ").substringBefore("\n").trim()
 result.contains("→ project/") ->
 result.substringAfter("→ project/").substringBefore(" (").substringBefore("\n").trim()
 result.contains("bytes to ") ->
 result.substringAfter("bytes to ").substringBefore("\n").trim()
 result.contains(" -> ") ->
 result.substringAfter(" -> ").substringBefore(" (").substringBefore("\n").trim()
 result.contains(" at ") ->
 result.substringAfter(" at ").substringBefore(" (").substringBefore(" [").trim()
 result.contains("pulled to ") ->
 result.substringAfter("pulled to ").substringBefore("\n").trim()
 result.contains("exported ") ->
 result.substringAfter("exported ").substringBefore(" ").substringBefore("\n").trim()
 else -> null
 }
 val stepOutputPath = when (step.action) {
 "createpptx", "createxlsx", "createdocx", "createcsv", "pdfcreate",
 "generateimage", "screenshot", "download",
 "pdfedit", "pdfaddpage", "pdfaddimage", "pdffillform", "pdfaddchart",
 "write", "create", "exporttodevice" -> step.path
 "zip", "pdfcollage", "apkrebuild", "cloudpull", "copy", "move" ->
 step.arg2.ifBlank { step.path }
 "pdfcompress", "pdfmerge", "pdfsplit" -> step.detail.ifBlank { step.path }
 "imageedit" -> step.arg3.ifBlank { step.path }
 else -> step.path.ifBlank { null }
 }
 val chipPath = listOfNotNull(parsedPath, stepOutputPath)
 .map { it.trim().removePrefix("./").removePrefix("/") }
 .firstOrNull { cand ->
 cand.isNotBlank() && cand.length < 200 &&
 runCatching {
 val f = java.io.File(projectDir, cand)
 f.exists() && f.isFile &&
 // Prefer real deliverables; still show code files if explicitly written as the task output
 (com.ahamai.app.ui.components.FilePreviewKit.isDeliverable(cand) ||
 step.action in setOf(
 "createpptx", "createxlsx", "createdocx", "createcsv",
 "pdfcreate", "generateimage", "screenshot", "zip", "download",
 "imageedit", "apkrebuild"
 ))
 }.getOrDefault(false)
 }
 // Avoid flooding the log with duplicate cards for the same path in one turn
 val alreadyShown = agentLog.any { it.type == "filechip" && it.path == chipPath }
 if (chipPath != null && !alreadyShown) {
 agentLog = agentLog + AgentLogEntry(
 type = "filechip",
 text = chipPath.substringAfterLast('/'),
 path = chipPath
 )
 }
 }
 // Attach the tool output + switch the label to past tense ("Reading" -> "Read")
 // now that the action has finished. Include elapsed + budget so every log shows timeout.
 if (actionIndex >= 0 && actionIndex < agentLog.size) {
 val elapsedMs = System.currentTimeMillis() - toolStartMs
 val budgetMs = toolTimeoutMs(step.action)
 val timeLabel = com.ahamai.app.data.ToolTimeouts.progressLabel(
  elapsedMs, budgetMs, finished = true
 )
 val timedOut = result.startsWith("ERROR") &&
  (result.contains("[TIMEOUT]", ignoreCase = true) || result.contains("TIMEOUT", ignoreCase = true))
 val curEntry = agentLog[actionIndex]
 val baseSub = stepSubtitle.ifBlank {
  curEntry.path.split(" · ").firstOrNull {
   !it.startsWith("timeout") && !it.contains("working") && !it.contains(" / ")
  }.orEmpty()
 }
 val pathDone = listOf(
  baseSub,
  if (timedOut) "TIMEOUT · $timeLabel" else timeLabel
 ).filter { it.isNotBlank() }.joinToString(" · ")
 // Skills: hide full SKILL.md bodies for library skills; compact save/resource lines.
 val uiResult = when (step.action) {
  "loadskill" -> {
   val skillId = step.detail.trim()
   if (SkillManager.shouldHideSkillBodyInUi(skillId)) {
    val skill = SkillManager.findSkill(skillId)
    if (result.startsWith("ERROR")) result.take(400)
    else "Skill loaded${skill?.name?.let { " · $it" } ?: ""}" +
     if (result.contains("FORKED SUB-AGENT")) " · forked" else ""
   } else {
    result.take(6000)
   }
  }
  "saveskill" -> result.lines().firstOrNull()?.take(280) ?: result.take(280)
  "readskillresource" -> {
   if (result.startsWith("ERROR")) result.take(400)
   else "Resource · ${step.path.ifBlank { step.detail }.take(80)}"
  }
  "listskillresources" -> result.lines().take(12).joinToString("\n").take(800)
  "runskillscript" -> result.take(2000)
  else -> result.replace(
   Regex("\\[\\[BROWSER_LIVE\\]\\].*?\\[\\[/BROWSER_LIVE\\]\\]\\n?"), ""
  ).take(6000)
 }
 agentLog = agentLog.toMutableList().also { list ->
 list[actionIndex] = curEntry.copy(
 text = if (timedOut) "Timed out" else CodeAgent.actionLabelPast(step),
 path = pathDone,
 result = uiResult,
 live = false,
 timeoutBudgetMs = budgetMs
 )
 }
 }
 // Monthly feature-cap usage (billing).
 when (step.action) {
 "generateimage" -> com.ahamai.app.data.UsageTracker.recordFeature(context, "image")
 "browseropen" -> com.ahamai.app.data.UsageTracker.recordFeature(context, "browser")
 "ghbuild" -> com.ahamai.app.data.UsageTracker.recordFeature(context, "apk")
 "makeshorts" -> com.ahamai.app.data.UsageTracker.recordFeature(context, "video")
 "webscan", "recon", "vulnscan", "dirfuzz", "niktoscan",
 "sqlitest", "sslscan", "urlharvest", "scansecrets", "securityaudit" ->
 com.ahamai.app.data.UsageTracker.recordFeature(context, "scan")
 }

 // Intentionally NOT clearing recentActions after edits — that reset let
 // edit → re-read → re-edit loops run forever. Stale read caches for the path
 // are invalidated above when storing resultByActionKey.

 if (needsDiff && !result.startsWith("ERROR")) {
 val newContent = withContext(Dispatchers.IO) {
 ProjectManager.readFile(projectDir, step.path).let { if (it.startsWith("ERROR")) "" else it }
 }
 val diff = DiffUtil.compute(oldContent, newContent)
 if (diff.added > 0 || diff.removed > 0 ||
 step.action in listOf("write", "create", "applypatch", "delete")
 ) {
 sessionAdded += diff.added
 sessionRemoved += diff.removed
 if (step.path.isNotBlank()) sessionFiles = sessionFiles + step.path
 val currentDiffs = rt.fileDiffs.value.toMutableMap()
 if (step.path.isNotBlank()) currentDiffs[step.path] = diff
 rt.fileDiffs.value = currentDiffs
 if (actionIndex in agentLog.indices) {
 agentLog = agentLog.toMutableList().also { list ->
 list[actionIndex] = list[actionIndex].copy(diff = diff)
 }
 }
 // Fix ledger: "pehla me kya fix kiya" for later turns / follow-ups
 val summary = when {
 step.explanation.isNotBlank() -> step.explanation.trim()
 step.action == "create" || step.action == "write" ->
 "created/wrote ${step.path.substringAfterLast('/')}"
 step.action == "applypatch" -> "applied multi-hunk patch"
 step.action == "delete" -> "deleted"
 else -> {
 val hint = step.arg2.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(50)
 if (!hint.isNullOrBlank()) "edited near: $hint"
 else "edited ${step.path.substringAfterLast('/')}"
 }
 }
 withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.recordFix(
 projectDir = projectDir,
 path = step.path,
 action = step.action,
 summary = summary,
 detail = result.lineSequence().firstOrNull()?.take(100).orEmpty(),
 added = diff.added,
 removed = diff.removed
 )
 }
 }
 }
 // Track tool result for memory.observe (errors → open loops next turn)
 stepResults.add(result)
 }
 // Track explore-vs-work so we can stop post-success "find file again" thrash.
 val turnExplore = steps.count { com.ahamai.app.data.AgentDiscipline.isExplore(it.action) }
 val turnWork = steps.count { com.ahamai.app.data.AgentDiscipline.isWork(it.action) }
 if (turnWork > 0) {
  hadWorkThisRun = true
  exploreSinceWork = 0
  if (!postEditNudgeSent && sessionFiles.isNotEmpty() && !done) {
   postEditNudgeSent = true
   results.append(
    com.ahamai.app.data.AgentDiscipline.postEditStopExploreNudge(sessionFiles)
   )
  }
 } else if (turnExplore > 0 && hadWorkThisRun) {
  exploreSinceWork += turnExplore
 }
 // Hard thrash brake: many search/read tools after real work, no new work.
 if (!done && hadWorkThisRun && exploreSinceWork >= 6) {
  results.append(
   com.ahamai.app.data.AgentDiscipline.exploreThrashNudge(exploreSinceWork, sessionFiles)
  )
  exploreSinceWork = 0 // one strong nudge per burst
  agentLog = agentLog + AgentLogEntry(
   "note",
   "Stop re-searching — use known files or DONE"
  )
 }
 // Early explore budget: too many greps before any edit is also wasteful.
 if (!done && !hadWorkThisRun && turn >= 4) {
  val totalExplore = recentActions.count { key ->
   val a = key.substringBefore('|')
   com.ahamai.app.data.AgentDiscipline.isExplore(a)
  }
  if (totalExplore >= 10 && turnExplore >= 2) {
   results.append(
    "[SEARCH BUDGET] You already used many list/grep/search tools without finishing the work. " +
     "Pick the best path from the tree/map you have, EDIT it, then ANSWER + DONE. " +
     "No more whole-project greps.\n\n"
   )
  }
 }
 // Auto worklog mid-run when this turn actually mutated files / ran heavy tools
 // (model often never calls WORKLOG itself).
 if (!done && projectDir.isNotBlank()) {
  val mutActions = steps.map { it.action }.filter {
   it in setOf(
    "edit", "write", "create", "delete", "multiedit", "bulkedit", "applypatch",
    "insertlines", "cloudshell", "runpython", "ghpush", "ghbuild", "runapp",
    "pdfcreate", "createxlsx", "createpptx", "createdocx", "generateimage"
   )
  }
  if (mutActions.isNotEmpty()) {
   val filesThisTurn = steps.map { it.path }.filter { it.isNotBlank() }.distinct()
   withContext(Dispatchers.IO) {
    com.ahamai.app.data.Worklog.appendProgress(
     projectDir, turn, mutActions, filesThisTurn.ifEmpty { sessionFiles }
    )
   }
  }
 }
 fileCount = withContext(Dispatchers.IO) { ProjectManager.countFiles(projectDir) }
 // Fold in any messages the user sent WHILE we were working (live steering).
 val queued = if (pendingUserMessages.isNotEmpty()) {
 val m = pendingUserMessages.toList(); pendingUserMessages.clear(); m
 } else emptyList()
 if (done && queued.isEmpty()) break
 if (done && queued.isNotEmpty()) { done = false; verified = false }
 val queuedBlock = if (queued.isNotEmpty())
 "\n\nNEW MESSAGE(S) from the user (sent while you were working — address these now):\n" +
 queued.joinToString("\n") { "- $it" }
 else ""
 // Keep the model grounded in the REAL project after it changes files — prevents
 // "lost" created files and stops it assuming files from elsewhere exist.
 val treeBlock = if (structureChanged)
 "\n\nCURRENT PROJECT FILES (after your changes — these are the ONLY files that exist; rely on this):\n" +
 withContext(Dispatchers.IO) { ProjectManager.buildTreeString(projectDir) }
 else ""
 // Grok-style: observe this turn into durable/hot memory (fixes, files, DONE, errors).
 withContext(Dispatchers.IO) {
 // Pad stepResults if some steps continued early (plan/done) without execute
 while (stepResults.size < steps.size) stepResults.add(0, "")
 com.ahamai.app.data.ContextMemoryManager.observe(
 projectDir = projectDir,
 steps = steps,
 toolResultsByStep = stepResults,
 userTask = task
 )
 }
 val fixReminder = withContext(Dispatchers.IO) {
 val mem = com.ahamai.app.data.ContextMemoryManager.get(projectDir)
 if (mem.fixes.isEmpty()) ""
 else "\n\n[ALREADY FIXED THIS SESSION — do not redo unless still broken]\n" +
 mem.fixes.takeLast(8).joinToString("\n") { "- ${it.line()}" } + "\n"
 }
 working.add(
 "user" to (
 results.toString() + queuedBlock + treeBlock + fixReminder +
 "\nContinue ONLY with NEW unfinished work. Do NOT re-read/re-verify OK files. " +
 "Do NOT re-apply listed fixes. Do NOT restart a feature that already works. " +
 "If the user request is already satisfied → ANSWER + DONE immediately (no extra exploration)." +
 BigChangePolicy.promptAddon()
 )
 )
 // Mid-run compaction if context ballooned from tool dumps this turn.
 val midCompact = withContext(Dispatchers.IO) {
 com.ahamai.app.data.ContextMemoryManager.compactIfNeeded(
 projectDir, working, sessionFiles
 )
 }
 if (midCompact != null) {
 agentLog = agentLog + AgentLogEntry("note", midCompact)
 }
 }
 } catch (e: Exception) {
 // /stop cancels the coroutine → CancellationException; that's intentional, not an error.
 if (e is kotlinx.coroutines.CancellationException) throw e
 // API errors (rate limit, auth, network) — auto-retry silently instead of stopping
 val msg = e.message ?: "Unknown error"
 val isApiError = msg.contains("rate") || msg.contains("auth") || msg.contains("429") ||
 msg.contains("503") || msg.contains("502") || msg.contains("500") ||
 msg.contains("timeout", true) || msg.contains("connect", true) ||
 msg.contains("stream") || msg.contains("reset") || msg.contains("refused")
 if (isApiError) {
 // Recoverable — show a subtle note but DON'T mark as final error
 agentLog = agentLog + AgentLogEntry("note", "Connection issue detected, recovering silently…")
 } else {
 agentLog = agentLog + AgentLogEntry("error", "Error: ${msg.take(200)}")
 }
 }
 // Finalize any still-"live" narration so previewable blocks (mermaid/chart) auto-render in agent mode.
 if (agentLog.any { it.live }) agentLog = agentLog.map { if (it.live) it.copy(live = false) else it }
 // Grok-style: capture after-snapshots + persist rewind points for this turn.
 if (projectDir.isNotBlank()) {
  withContext(Dispatchers.IO) {
   try { rt.endPrompt(projectDir) } catch (_: Exception) {}
  }
 }
 BigChangePolicy.endSession()
 isRunning = false
 rt.job = null
 com.ahamai.app.service.RunningTasks.finish(runId)
 saveSession()
 sessionsRefresh++
 // Cloud-backup workspaces right after a run finishes — so the user's progress
 // is mirrored to Firestore/GitHub even if the app is force-killed before the
 // screen's onDispose fires. This is the fix for "workspace disappears after
 // app uninstall" — previously backup only ran on navigate-away.
  try { com.ahamai.app.data.AuthManager.backupWorkspaces(context) } catch (_: Exception) {}
  // 10-second delayed check: verify the agent actually completed or stopped mid-response.
  // Uses agentLog (always accessible) instead of local vars that go out of scope.
  com.ahamai.app.AppScope.scope.launch {
   delay(10_000L)
   if (!isRunning && rt.job == null) {
    val hasAnswer = agentLog.any { it.type == "answer" }
    val hasDoneMarker = agentLog.any { it.type == "done" || it.type == "complete" }
    if (!hasAnswer && !hasDoneMarker) {
     agentLog = agentLog + AgentLogEntry(
      type = "note",
      text = "⚠️ Agent stopped mid-response — the task may not be complete. You can send a follow-up message to continue."
     )
    }
   }
  }
  }
 }

 // Stop a running agent (triggered by typing /stop). Cancels the background job; progress is kept.
 fun stopAgent() {
 if (!isRunning && rt.job == null) return
 rt.job?.cancel()
 rt.job = null
 if (agentLog.any { it.live }) agentLog = agentLog.map { if (it.live) it.copy(live = false) else it }
 isRunning = false
 pendingUserMessages.clear()
 // Still close the rewind point so after-snapshots exist for what already ran.
 if (projectDir.isNotBlank()) {
  com.ahamai.app.AppScope.scope.launch(Dispatchers.IO) {
   try { rt.endPrompt(projectDir) } catch (_: Exception) {}
  }
 }
 BigChangePolicy.endSession()
 agentLog = agentLog + AgentLogEntry("note", "Stop — Your progress is kept. Send a message to continue. Use Rewind to undo this turn’s file edits.")
 com.ahamai.app.service.RunningTasks.finish(projectDir.ifBlank { "workspace" })
 saveSession()
 }

 /** Grok-style /btw — side Q&A that never cancels or steers the main agent job. */
 fun dismissBtw() {
  val wasLoading = btwLoading
  btwJob?.cancel()
  btwJob = null
  btwOpen = false
  btwLoading = false
  btwToolStatus = null
  // Drop unfinished answers if dismissed mid-stream (matches Grok panel behavior).
  if (wasLoading) {
   btwQuestion = ""
   btwAnswer = ""
  }
 }

 fun runBtwAside(question: String) {
  val q = question.trim()
  if (q.isBlank()) return
  btwJob?.cancel()
  btwOpen = true
  btwQuestion = q
  btwAnswer = ""
  btwLoading = true
  btwToolStatus = null
  inputText = ""
  btwJob = scope.launch {
   try {
    val resolved = runCatching { com.ahamai.app.data.ApiConfig.chat(context) }.getOrNull()
    val bu = resolved?.baseUrl?.takeIf { it.isNotBlank() } ?: baseUrl
    val key = resolved?.apiKey?.takeIf { it.isNotBlank() } ?: apiKey
    val model = resolved?.model?.takeIf { it.isNotBlank() } ?: agentModel
    if (bu.isBlank() || key.isBlank()) {
     btwAnswer = "_No API configured — set a chat provider first._"
     return@launch
    }

    val now = java.text.SimpleDateFormat(
     "EEEE, d MMMM yyyy, h:mm a",
     java.util.Locale.getDefault()
    ).format(java.util.Date())
    val tz = java.util.TimeZone.getDefault().id
    val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    val systemPrompt = buildString {
     append("Current date & time (user's device): $now ($tz). Current year: $year.\n")
     append(
      "IMPORTANT: You do NOT have a knowledge cutoff. You are connected to LIVE web search. " +
       "Never claim you lack recent data, never mention a training cutoff, and always use the " +
       "current date/time/year above for anything time-sensitive.\n\n"
     )
     append(
      "You are ahamai answering a brief side question (BTW) while a coding agent may be " +
       "working on a larger task. Answer concisely and helpfully in markdown. " +
       "Do not invent long plans — this is an aside only. " +
       "If useful, mention files or APIs by name without rewriting the main task.\n\n"
     )
     append(
      "WEB SEARCH: For news, prices, sports, weather, “who is…”, “what happened…”, live facts, " +
       "or anything that may be outdated — search first. Prefer SEARCH over guessing.\n" +
       "Emit exactly one of these forms (nothing else until you get TOOL RESULTS):\n" +
       "  SEARCH: <query>\n" +
       "  or <tool_call>SEARCH<arg_value><query></arg_value></tool_call>\n" +
       "Optional: READ: <url> to open a specific page after search.\n" +
       "After TOOL RESULTS, answer the user in markdown. Do NOT repeat tool-call syntax. " +
       "Do not invent search results."
     )
    }

    val working = mutableListOf(
     "system" to systemPrompt,
     "user" to q
    )
    val executed = mutableSetOf<Pair<String, String>>()
    var toolTurns = 0
    val maxToolTurns = 4

    while (true) {
     btwToolStatus = null
     val sb = StringBuilder()
     ApiClient.streamChat(bu, key, model, working).collect { delta ->
      val piece = delta.text
      if (!piece.isNullOrEmpty()) {
       sb.append(piece)
       // Hide raw tool syntax while streaming
       btwAnswer = btwStripToolSyntax(sb.toString())
      }
     }
     val raw = sb.toString()
     val calls = btwExtractSearchCalls(raw)
      .filterNot { it in executed }
      .filter { it.first == "SEARCH" || it.first == "READ" }
      .take(6)

     val hasXml = raw.contains("tool_call", ignoreCase = true)
     val visibleClean = btwStripToolSyntax(raw).trim()
     val shouldRun = calls.isNotEmpty() &&
      toolTurns < maxToolTurns &&
      (hasXml || visibleClean.length <= 200)

     if (shouldRun) {
      toolTurns++
      executed.addAll(calls)
      val first = calls.first()
      val statusType = if (calls.size > 1) "SEARCH" else first.first
      val statusQuery = if (calls.size > 1) {
       val sc = calls.count { it.first == "SEARCH" }
       val rc = calls.count { it.first == "READ" }
       buildString {
        if (sc > 0) append("$sc search${if (sc > 1) "es" else ""}")
        if (rc > 0) {
         if (isNotEmpty()) append(" + ")
         append("$rc page${if (rc > 1) "s" else ""}")
        }
        append(" in parallel")
       }
      } else first.second
      btwToolStatus = ToolStatus(type = statusType, query = statusQuery, domain = null)
      // Keep any short preamble; main UI is the chat-style search pill
      btwAnswer = visibleClean

      val results = withContext(Dispatchers.IO) {
       kotlinx.coroutines.coroutineScope {
        calls.map { (type, arg) ->
         async {
          val r = when (type) {
           "READ" -> com.ahamai.app.data.WebTools.read(arg)
           else -> com.ahamai.app.data.WebTools.search(arg)
          }
          "[$type] $arg\n$r\n\n"
         }
        }.awaitAll().joinToString("")
       }
      }

      btwToolStatus = null
      working.add("assistant" to raw)
      working.add(
       "user" to (
        "TOOL RESULTS:\n$results\n" +
         "Answer the side question using these results and the current date/time. " +
         "Be concise in markdown. Do NOT emit more tool-call syntax unless you still " +
         "need one more SEARCH or READ."
        )
      )
      continue
     }

     btwAnswer = visibleClean.ifBlank { "_No answer returned._" }
     break
    }
   } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
   } catch (e: Exception) {
    btwAnswer = "Couldn't answer: ${e.message ?: "unknown error"}"
   } finally {
    btwToolStatus = null
    btwLoading = false
   }
  }
 }

 // Send from the composer: if the agent is idle, start a run; if it's working, queue the
 // message so it's picked up on the agent's next turn (live steering, no interruption).
  fun submitMessage(text: String) {
  agentStickToBottom = true
 val typed = text.trim()
 if (typed.isBlank() && pendingAttachments.isEmpty()) return
 val display = typed.ifBlank { "Please inspect the attached file(s)." }
 var t = display
 // /stop — halt the running agent from the composer (no artificial limits; user is in control).
 if (typed.equals("/stop", ignoreCase = true)) { inputText = ""; stopAgent(); return }
 // /btw <question> — side aside; never interrupts main run or queues as steering.
 val btwMatch = Regex("^/btw(?:\\s+(.*))?$", RegexOption.IGNORE_CASE).matchEntire(typed)
 if (btwMatch != null) {
  val q = btwMatch.groupValues.getOrNull(1)?.trim().orEmpty()
  if (q.isBlank()) {
   inputText = "/btw "
   agentLog = agentLog + AgentLogEntry(
    "note",
    "BTW · type `/btw your question` for a side answer — main agent keeps working."
   )
   return
  }
  runBtwAside(q)
  return
 }
 // Claude/Codex: skill tool grants clear on each new user message
 com.ahamai.app.data.SkillRuntime.clearTurnGrant()
 SkillManager.init(context)
 com.ahamai.app.data.SkillRuntime.init(context)
 if (projectDir.isNotBlank()) {
  com.ahamai.app.data.SkillRuntime.setProjectContext(projectDir)
 }
 // Claude/Codex-style slash skill: /skill-name [args] — activate package + inject rendered body
 val slash = SkillManager.parseSlashInvoke(typed)
 if (slash != null) {
  val activated = com.ahamai.app.data.SkillRuntime.activate(
   context, slash.skillId, slash.arguments, projectDir
  )
  t = if (activated != null) {
   buildString {
    append("User invoked skill `/${slash.skillId}`")
    if (slash.arguments.isNotBlank()) append(" with arguments: ${slash.arguments}")
    append(".\n\n")
    append(com.ahamai.app.data.SkillRuntime.formatLoadResponse(activated, slash.arguments))
    append("\n\nExecute this skill now for the current project.")
   }
  } else slash.expandedTask
  agentLog = agentLog + AgentLogEntry(
   "note",
   "Skill · /${slash.skillId}" + if (slash.arguments.isNotBlank()) " · ${slash.arguments.take(80)}" else ""
  )
 } else {
  // Natural language "create a skill…" → load skill-creator + SAVE_SKILL guidance
  SkillManager.maybeExpandSkillCreatorIntent(typed)?.let { expanded ->
   com.ahamai.app.data.SkillRuntime.activate(context, "skill-creator", "", projectDir)
   t = expanded
   agentLog = agentLog + AgentLogEntry("note", "Skill creator loaded · AI can draft SKILL.md and SAVE_SKILL (asks you first)")
  }
 }
 // Snapshot staged attachments for this message, then clear the composer strip.
 val atts = pendingAttachments.toList()
 pendingAttachments.clear()
 // First message with no project yet → create an empty project with an AI-generated title.
 var justCreated = false
 if (projectDir.isBlank()) {
 val dir = ProjectManager.createEmptyProject(context, "Project")
 projectDir = dir
 projectName = "Project"
 fileCount = ProjectManager.countFiles(dir)
 justCreated = true
 }
 // If this is the very first user message for this session (no title file), generate an AI name.
 val hasTitle = projectDir.isNotBlank() && ProjectManager.getSessionTitle(projectDir) != null
 if (projectDir.isNotBlank() && !hasTitle) {
 val d = projectDir; val msg = display
 scope.launch(Dispatchers.IO) {
 try {
 val promptMsgs = listOf(
 "system" to "You are a helpful assistant. Based on the user's task, generate a short, descriptive session name (3-5 words max, no quotes, no punctuation except hyphen). Just output the plain name.",
 "user" to msg
 )
 val result = ApiClient.sendChatMessage(baseUrl, apiKey, agentModel, promptMsgs)
 val title = result.getOrNull()?.trim()?.take(40)?.ifBlank { null }
 if (title != null) {
 ProjectManager.setSessionTitle(d, title)
 if (d == projectDir) projectName = title
 }
 sessionsRefresh++
 } catch (_: Exception) {}
 }
 }
 if (isRunning) {
 agentLog = agentLog + AgentLogEntry("user", display, attachments = atts)
 agentLog = agentLog + AgentLogEntry("note", "Queued — I'll factor this in on the next step.")
 pendingUserMessages.add(CodeAgent.attachmentPrompt(atts) + t)
 inputText = ""
 } else if (justCreated) {
 // Project dir was created THIS frame → defer the run by one composition so runAgent
 // binds to holder(projectDir). Optimistically seed the NEW holder with the user
 // bubble NOW so the list never blanks while we rebind (was: messages vanish until
 // the response finished).
 inputText = ""
 pendingStartAtts = atts
 pendingStart = t
 pendingStartDisplay = display
 val newHolder = AgentRuntime.holder(projectDir)
 if (newHolder.agentLog.value.isEmpty()) {
  newHolder.agentLog.value = listOf(
   AgentLogEntry("user", display, attachments = atts)
  )
 }
} else {
  runAgent(t, atts, displayText = display)
  }
  // Immediately scroll to the bottom when user sends a message, so their message
  // appears at the bottom instead of at the top of the list.
  scope.launch {
   delay(32)
   runCatching { logState.animateScrollToItem(logState.layoutInfo.totalItemsCount - 1) }
  }
 }

 // Deferred run: when a brand-new project was just created, we set pendingStart and trigger the
 // run on the NEXT composition — by then `rt` has rebound to holder(projectDir), so the run's
 // live state lands in the correct holder and the session shows LIVE when reopened from History.
 LaunchedEffect(projectDir, pendingStart) {
 val p = pendingStart
 if (p != null && projectDir.isNotBlank() && !isRunning) {
 pendingStart = null
 inputText = ""
 val a = pendingStartAtts; pendingStartAtts = emptyList()
 val d = pendingStartDisplay; pendingStartDisplay = null
 runAgent(p, a, displayText = d)
 }
 }

 // Create a brand-new project (template or empty) right here, then optionally start the agent.
 fun startScratch(prompt: String?, useTemplate: Boolean, label: String) {
 val dir = if (useTemplate) ProjectManager.createFromTemplate(context, label)
 else ProjectManager.createEmptyProject(context, label)
 projectDir = dir; projectName = label
 fileCount = ProjectManager.countFiles(dir)
 // Generate AI session title from the prompt if provided
 if (!prompt.isNullOrBlank()) {
 scope.launch(Dispatchers.IO) {
 try {
 val promptMsgs = listOf(
 "system" to "You are a helpful assistant. Based on the user's task, generate a short, descriptive session name (3-5 words max, no quotes, no punctuation except hyphen). Just output the plain name.",
 "user" to prompt
 )
 val result = ApiClient.sendChatMessage(baseUrl, apiKey, agentModel, promptMsgs)
 val title = result.getOrNull()?.trim()?.take(40)?.ifBlank { null }
 if (title != null) {
 ProjectManager.setSessionTitle(dir, title)
 if (dir == projectDir) projectName = title
 }
 } catch (_: Exception) {}
 }
 pendingStart = prompt
 }
 }

 // Open an existing project (cloned repo / extracted zip) on this same screen. Does NOT
 // auto-run — the agent simply waits for the user's first instruction.
 fun openProjectDir(dir: String, name: String) {
 projectDir = dir
 projectName = ProjectManager.getSessionTitle(dir) ?: name
 fileCount = ProjectManager.countFiles(dir)
 }

 // --- Workspace sidebar actions ---
 fun switchToProject(dir: String, name: String) {
 if (dir == projectDir) { scope.launch { drawerState.close() }; return }
 saveSession()
 projectDir = dir
 projectName = ProjectManager.getSessionTitle(dir) ?: name
 fileCount = ProjectManager.countFiles(dir)
 agentLog = emptyList(); convo.clear(); pendingUserMessages.clear()
 sessionFiles = emptySet(); sessionAdded = 0; sessionRemoved = 0
 inputText = ""
 loadSession(dir)
 scope.launch { drawerState.close() }
 }
 fun newProjectAndSwitch() {
 val dir = ProjectManager.createEmptyProject(context, "Project")
 sessionsRefresh++
 switchToProject(dir, "Project")
 }
 fun exportFileToDevice(relPath: String) {
 scope.launch {
 val msg = withContext(Dispatchers.IO) {
 val f = java.io.File(projectDir, relPath)
 if (f.exists() && f.isFile) DeviceStorage.saveBytesToDownloads(context, f.readBytes(), f.name)
 else "ERROR: file not found"
 }
 agentLog = agentLog + AgentLogEntry("note", if (msg.startsWith("OK")) "Saved ${relPath.substringAfterLast('/')} to Downloads" else msg)
 }
 }
 fun deleteSession(info: ProjectManager.ProjectInfo) {
 scope.launch {
 withContext(Dispatchers.IO) { ProjectManager.deleteProject(info.path) }
 if (info.path == projectDir) { projectDir = ""; projectName = ""; agentLog = emptyList(); convo.clear() }
 sessionsRefresh++
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
 if (prefs.isGithubConnected()) { showRepoSheet = true; loadRepos() }
 else if (!GitHubClient.isLoginConfigured()) { showTokenDialog = true } // fallback when OAuth app not configured
 else { showGithubLogin = true }
 }

 fun loadBranches() = scope.launch {
 branchesLoading = true
 branches = withContext(Dispatchers.IO) { GitHubClient.listBranches(prefs.getGithubToken(), prefs.getConnectedRepo()) }
 branchesLoading = false
 }

 val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
 if (uri != null) scope.launch {
 busy = "Extracting project..."
 val dir = withContext(Dispatchers.IO) { ProjectManager.extractZip(context, uri) }
 busy = null
 if (dir != null) openProjectDir(dir, dir.substringAfterLast('/'))
 }
 }

 // Copy a picked/captured attachment into the project's attachments/ folder so the agent can
 // reference it by path. Creates an empty project first if none exists yet.
 fun importAttachment(uri: android.net.Uri, suggestedName: String?) {
 scope.launch {
 try {
 if (projectDir.isBlank()) {
 val dir = ProjectManager.createEmptyProject(context, "Project")
 projectDir = dir; projectName = "Project"
 fileCount = ProjectManager.countFiles(dir)
 }
 val name = (suggestedName ?: attachmentDisplayName(context, uri) ?: "file_${System.currentTimeMillis()}")
 .replace(Regex("[\\\\/]"), "_")
 .let { raw ->
 // Content URIs often have NO extension (e.g. "1000000123"); without one the
 // agent can't route the file to ANALYZE_IMAGE / PDF_READ and "can't see" it.
 // Infer the correct extension from the MIME type when it's missing.
 val hasExt = raw.contains('.') && raw.substringAfterLast('.').length in 1..5
 if (hasExt) raw else {
 val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
 val ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime ?: "")
 if (!ext.isNullOrBlank()) "$raw.$ext" else raw
 }
 }
 withContext(Dispatchers.IO) {
 val dest = java.io.File(projectDir, "attachments/$name")
 dest.parentFile?.mkdirs()
 context.contentResolver.openInputStream(uri)?.use { input ->
 dest.outputStream().use { out -> input.copyTo(out) }
 }
 }
 fileCount = withContext(Dispatchers.IO) { ProjectManager.countFiles(projectDir) }
 pendingAttachments.add("attachments/$name")
 } catch (e: Exception) {
 agentLog = agentLog + AgentLogEntry("error", "Attach failed: ${e.message}")
 }
 }
 }

val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
  uris.forEach { uri -> importAttachment(uri, null) }
  }
  val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
  uris.forEach { uri -> importAttachment(uri, null) }
  }
 val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
 val uri = pendingCameraUri
 if (success && uri != null) importAttachment(uri, pendingCameraName.ifBlank { "photo_${System.currentTimeMillis()}.jpg" })
 }

 fun launchCamera() {
 try {
 val name = "photo_${System.currentTimeMillis()}.jpg"
 val photoFile = java.io.File(context.cacheDir, name)
 val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", photoFile)
 pendingCameraUri = uri
 pendingCameraName = name
 cameraLauncher.launch(uri)
 } catch (e: Exception) {
 agentLog = agentLog + AgentLogEntry("error", "Camera failed: ${e.message}")
 }
 }

 val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
 if (granted) launchCamera()
 else agentLog = agentLog + AgentLogEntry("note", "Camera permission denied.")
 }

 // Saves the whole project as a .zip directly to the device Downloads folder.
 // Uses streaming copy (not readBytes()) to support 200MB+ zips without OOM.
 val exportProject: () -> Unit = {
 scope.launch {
 val msg = withContext(Dispatchers.IO) {
 val zip = ProjectManager.createZip(context, projectDir, projectName)
 if (zip != null && zip.exists()) {
 val outName = "${projectName.ifBlank { "workspace" }}.zip"
 DeviceStorage.saveFileToDownloads(context, zip, outName, "application/zip")
 } else "ERROR: Could not create project zip."
 }
 agentLog = agentLog + AgentLogEntry("note", if (msg.startsWith("ERROR")) msg else "Project saved to Downloads as ${projectName.ifBlank { "workspace" }}.zip")
 }
 Unit
 }

 LaunchedEffect(Unit) {
 if (!initialPrompt.isNullOrBlank()) {
 // If this is a fresh AI handoff with no project yet, create one so the agent
 // has somewhere to work.
 if (projectDir.isBlank()) {
 val dir = withContext(Dispatchers.IO) { ProjectManager.createEmptyProject(context, "Project") }
 projectDir = dir
 projectName = "Project"
 fileCount = withContext(Dispatchers.IO) { ProjectManager.countFiles(dir) }
 }
 // Visibly drop the task into the composer, then send it automatically (deferred one
 // composition so runAgent binds to holder(projectDir) → live on re-entry).
 inputText = initialPrompt
 delay(450)
 pendingStart = initialPrompt
 } else if (projectDir.isNotBlank()) {
 // Opened an existing workspace from History — restore its saved transcript
 // (conversation + agent log) so the chat history shows up, not just the files.
 // BUT if a run is already live in the holder (background run / returning mid-task),
 // keep the live state instead of overwriting it with the older saved snapshot.
 if (!isRunning && agentLog.isEmpty()) loadSession(projectDir)
 fileCount = withContext(Dispatchers.IO) { ProjectManager.countFiles(projectDir) }
 }
 }

 // ── Save session when the user navigates away (back button) ──
 // Without this, pressing back discards the current workspace session entirely.
 DisposableEffect(projectDir) {
 onDispose {
 if (projectDir.isNotBlank()) {
 val pd = projectDir
 val pn = projectName
 val convoSnap = convo.toList()
 val logSnap = agentLog
 val sfSnap = sessionFiles.toList()
 com.ahamai.app.AppScope.scope.launch(Dispatchers.IO) {
 try {
 val dir = agentSessionsDir()
 val o = JSONObject()
 o.put("name", pn)
 val convoArr = JSONArray()
 convoSnap.forEach { (r, c) -> convoArr.put(JSONObject().put("r", r).put("c", c)) }
 o.put("convo", convoArr)
 val logArr = JSONArray()
 logSnap.forEach { e ->
 if (e.type in setOf("user", "answer", "narration", "note", "error", "action")) {
 logArr.put(JSONObject().put("t", e.type).put("x", e.text).put("p", e.path).put("r", e.result))
 }
 }
 o.put("log", logArr)
 o.put("sf", JSONArray(sfSnap))
 val sessionFile = java.io.File(dir, pd.substringAfterLast('/') + ".json")
 sessionFile.writeText(o.toString())
 // Mirror this workspace (content + agent transcript) to the cloud so it survives an
 // uninstall/reinstall or a sign-in on another device — restored on sign-in by
 // AuthManager.restoreWorkspaces. This replaces the old metadata-only copy in
 // Downloads/AhamAI/workspaces, which cluttered the user's Downloads with a new file on
 // every navigate-away and could only ever restore an EMPTY placeholder.
 try { com.ahamai.app.data.AuthManager.backupWorkspaces(context) } catch (_: Exception) {}
 } catch (_: Exception) {}
 }
 }
 }
 }

 val sessions = remember(sessionsRefresh, projectDir) { ProjectManager.listProjects(context) }
 // Workspace projects now live on the History screen, so the in-agent sidebar is removed.
 ModalNavigationDrawer(
 drawerState = drawerState,
 gesturesEnabled = false,
 drawerContent = { }
 ) {
 Scaffold(
 topBar = {
 TopAppBar(
 windowInsets = WindowInsets.statusBars,
 navigationIcon = {
 // iOS circular chrome + chevron back
 com.ahamai.app.ui.components.IosChromeIconButton(
  isDark = isDark,
  onClick = onChatMode,
  contentDescription = "Back"
 ) {
  Icon(
   AdminIcons.ArrowBackIos,
   contentDescription = null,
   tint = if (isDark) Color(0xFFE5E5EA) else Color(0xFF3A3A3C),
   modifier = Modifier.size(16.dp)
  )
 }
 },
 title = {
 Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
 if (started && projectName.isNotBlank()) {
 Column(horizontalAlignment = Alignment.CenterHorizontally) {
 Text(
 projectName.take(28),
 fontSize = 15.sp,
 fontWeight = FontWeight.SemiBold,
 fontFamily = InterFamily,
 color = primaryText,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 letterSpacing = (-0.2).sp
 )

 }
 } else {
 ModeToggle(selected = "Agent", isDark = isDark, onChat = onChatMode, onAgent = {})
 }
 }
 },
 actions = {
 if (started) {
 com.ahamai.app.ui.components.IosChromeIconButton(
  isDark = isDark,
  onClick = { showFiles = true },
  contentDescription = "Workspace"
 ) {
  Icon(
   AdminIcons.FolderMultiple,
   contentDescription = null,
   tint = if (isDark) Color(0xFFE5E5EA) else Color(0xFF3A3A3C),
   modifier = Modifier.size(17.dp)
  )
 }
 Spacer(Modifier.width(4.dp))
 }
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
 .border(1.dp, if (isDark) cDarkBorder else cLightBorder, CircleShape)
 )
 } else {
 Icon(Icons.Outlined.AccountCircle, "Profile", tint = muted, modifier = Modifier.size(24.dp))
 }
 }
 },
 colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
 )
 },
 containerColor = if (isDark) cDarkBg else cLightBg
 ) { padding ->
 // Match status / nav bars to page bg (no mismatched strip).
 val pageBg = if (isDark) cDarkBg else cLightBg
 val pageView = LocalView.current
 DisposableEffect(isDark, pageBg) {
  val activity = pageView.context as? android.app.Activity
  val window = activity?.window
  if (window != null) {
   androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
   @Suppress("DEPRECATION")
   window.statusBarColor = android.graphics.Color.TRANSPARENT
   @Suppress("DEPRECATION")
   window.navigationBarColor = android.graphics.Color.TRANSPARENT
   androidx.core.view.WindowCompat.getInsetsController(window, pageView).apply {
    isAppearanceLightStatusBars = !isDark
    isAppearanceLightNavigationBars = !isDark
   }
  }
  onDispose { }
 }
 Column(
 modifier = Modifier
 .fillMaxSize()
 .background(pageBg)
 .padding(padding)
 .imePadding()
 .pointerInput(Unit) {
 var total = 0f
 detectHorizontalDragGestures(
 onDragStart = { total = 0f },
 onDragEnd = { if (total >= 140f) onChatMode() }
 ) { _, dragAmount -> total += dragAmount }
 }
 ) {
// Artifacts strip is now shown above the input area (near bottom of this Column).
 // Finished toast card (in-app Live Activity style)
 Box(Modifier.fillMaxWidth()) {
  FinishedToastCard(
   // Removed per design: no auto "Review" prompt after a task completes.
   visible = false,
   title = projectName.ifBlank { "Task complete" },
   added = sessionAdded,
   removed = sessionRemoved,
   fileCount = sessionFiles.size,
   elapsedLabel = AgentTaskStore.formatElapsed(
    lastRunElapsedSec.coerceAtLeast(
     AgentTaskStore.getByProject(context, projectDir)?.elapsedSec ?: 0
    )
   ),
   isDark = isDark,
   primaryText = primaryText,
   muted = muted,
   onReview = { showEditedFilesSheet = true; showFinishedToast = false },
   onDismiss = { showFinishedToast = false },
   modifier = Modifier
    .align(Alignment.TopCenter)
    .padding(horizontal = 12.dp, vertical = 4.dp)
    .zIndex(2f)
  )
 }
 if (!started && agentLog.isEmpty()) {
 // ---- HOME: greet user by name, compact iOS-style ----
 Spacer(Modifier.weight(1f))
 Column(
 modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
 horizontalAlignment = Alignment.CenterHorizontally
 ) {
 Text(
 text = "Hi${if (userName.isNotBlank()) ", ${userName.split(" ").firstOrNull() ?: userName}" else ""}",
 fontSize = 22.sp,
 color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
 fontFamily = InterFamily,
 fontWeight = FontWeight.Bold,
 textAlign = androidx.compose.ui.text.style.TextAlign.Center,
 lineHeight = 28.sp,
 letterSpacing = (-0.4).sp
 )
 Spacer(Modifier.height(8.dp))
 Text(
 "What are we making today?",
 fontSize = 15.sp,
 color = muted,
 fontFamily = InterFamily,
 lineHeight = 20.sp,
 textAlign = androidx.compose.ui.text.style.TextAlign.Center
 )
 Spacer(Modifier.height(4.dp))
 Text(
 "Plan, ask, build — apps, sites, scripts, GitHub.",
 fontSize = 13.sp, color = muted.copy(alpha = 0.9f), fontFamily = InterFamily, lineHeight = 18.sp,
 textAlign = androidx.compose.ui.text.style.TextAlign.Center
 )
 }
 Spacer(Modifier.height(16.dp))
 } else if (agentLog.isEmpty() && !isRunning) {
 Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
 Text(
 "Describe what you'd like done.",
 fontSize = 12.sp, color = muted, fontFamily = InterFamily,
 textAlign = androidx.compose.ui.text.style.TextAlign.Center,
 modifier = Modifier.padding(horizontal = 48.dp)
 )
 }
 } else {
 // Always keep the log LazyColumn mounted once a session started / is running
 // so messages never unmount mid-run (holder rebind used to flash empty state).
 LazyColumn(
 state = logState,
 modifier = Modifier.weight(1f).fillMaxWidth(),
 contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
 verticalArrangement = Arrangement.spacedBy(10.dp),
 userScrollEnabled = true
 ) {
 items(
 groupedLog,
 key = { item -> if (item is List<*>) (item.first() as AgentLogEntry).id else (item as AgentLogEntry).id },
 contentType = { item ->
  if (item is List<*>) "action_group" else (item as AgentLogEntry).type
 }
 ) { item ->
 // No heavy item placement animation while scrolling — keeps flings smooth with
 // WebView / images remeasuring mid-list.
 Box {
 if (item is List<*>) {
 @Suppress("UNCHECKED_CAST")
 ActionGroupRow(item as List<AgentLogEntry>, isDark, muted, primaryText)
 } else {
 val entry = item as AgentLogEntry
 AgentLogRow(
 entry, isDark, muted, primaryText,
 projectDir = projectDir,
 onOpenFile = { previewPath = it },
 onRecoveryAction = { action ->
 when (action) {
 "continue_timeout" -> {
 val msg = "Last tool timed out (${entry.path}). Continue the task with a different/faster approach — do not blindly re-run the same long tool."
 if (isRunning) pendingUserMessages.add(msg) else runAgent(msg)
 }
 "continue_loop" -> {
 val msg = "You got stuck repeating (${entry.path}). Continue with a genuinely different approach — do not re-run the same action."
 if (isRunning) pendingUserMessages.add(msg) else runAgent(msg)
 }
 "undo" -> {
 scope.launch {
 // Prefer Grok multi-file turn rewind; fall back to single-file LIFO.
 val tracker = rt.fileState ?: com.ahamai.app.data.FileStateRegistry.tracker(projectDir)
 val msg = if (tracker.hasRewindableHistory()) {
  val resp = rt.rewindLastTurn(projectDir)
  sessionFiles = rt.sessionFiles.value
  val remain = rt.fileDiffs.value
  sessionAdded = remain.values.sumOf { it.added }
  sessionRemoved = remain.values.sumOf { it.removed }
  agentLog = agentLog + AgentLogEntry(
   if (resp.success) "note" else "error",
   resp.summaryLine()
  )
  resp.summaryLine()
 } else {
  val m = rt.undoLast(projectDir)
  if (m.startsWith("Undone")) sessionFiles = rt.sessionFiles.value
  m
 }
 android.widget.Toast.makeText(context, msg.substringBefore(" —").take(80), android.widget.Toast.LENGTH_SHORT).show()
 }
 }
 "answer_again" -> {
 if (entry.result.isNotBlank()) {
 askUserQuestions = entry.result
 if (askUserDeferred == null || askUserDeferred?.isCompleted == true) {
 // Sheet only — if agent already moved on, answers go as a follow-up message
 askUserDeferred = CompletableDeferred()
 scope.launch {
 val ans = askUserDeferred?.await() ?: return@launch
 askUserQuestions = null
 askUserDeferred = null
 if (ans.isNotBlank() && !ans.startsWith("User skipped")) {
 if (isRunning) pendingUserMessages.add("[ASK_USER ANSWERS]\n$ans")
 else runAgent("User clarified:\n$ans\nContinue the task with these answers.")
 }
 }
 }
 }
 }
 "dismiss" -> { /* card stays; user can ignore */ }
 }
 }
 )
 }
 }
 }
 // Processing row — Grok-style "Responding · 6.9s" status with pixel indicator.
if (isRunning) {
   item(key = "thinking-footer") {
    val (mode, label) = deriveActivity(agentLog)
    // Uses hoisted actionElapsedSec — stable across scroll/nav recompositions.
   Column(
    modifier = Modifier
     .fillMaxWidth()
     .padding(top = 6.dp, bottom = 10.dp)
   ) {
    ThinkingIndicator(
     isDark = isDark,
     muted = muted,
     mode = mode,
     label = label,
     elapsedSec = actionElapsedSec
    )
   }
  }
 }
 }
 }


 // Status strip — scroll chevrons only (thinking lives in the list for a continuous loop feel).
 if (agentLog.isNotEmpty()) {
 Row(
 modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 14.dp, top = 2.dp, bottom = 2.dp),
 horizontalArrangement = Arrangement.End,
 verticalAlignment = Alignment.CenterVertically
 ) {
 Row(verticalAlignment = Alignment.CenterVertically) {
 Box(
 modifier = Modifier.size(28.dp).clip(CircleShape)
 .agentPressable(haptics = agentHaptics, pressedScale = 0.9f) {
  agentHaptics.tick()
  agentStickToBottom = false
  scope.launch { runCatching { logState.animateScrollToItem(0) } }
 },
 contentAlignment = Alignment.Center
 ) {
 Icon(
 imageVector = AdminIcons.BootstrapArrow90degUp,
 contentDescription = "Scroll to top",
 tint = muted,
 modifier = Modifier.size(16.dp)
 )
 }
 Spacer(Modifier.width(2.dp))
 Box(
 modifier = Modifier.size(28.dp).clip(CircleShape)
 .agentPressable(haptics = agentHaptics, pressedScale = 0.9f) {
  agentHaptics.tick()
  agentStickToBottom = true
  scope.launch {
   runCatching { logState.animateScrollToItem((groupedLog.size - 1).coerceAtLeast(0)) }
  }
 },
 contentAlignment = Alignment.Center
 ) {
 Icon(
 imageVector = AdminIcons.BootstrapArrow90degDown,
 contentDescription = "Scroll to bottom",
 tint = muted,
 modifier = Modifier.size(16.dp)
 )
 }
 }
 }
 }

// Input — shared iOS composer (same component for home + follow-up chatting)
  Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
  // Sticky plan progress — stays visible while a multi-step plan is active
  val stickyPlan = remember(agentLog) {
   val plan = agentLog.asReversed().firstOrNull { it.type == "plan" && !it.plan.isNullOrEmpty() }?.plan
   if (plan.isNullOrEmpty()) null
   else {
    val next = plan.indexOfFirst { !it.done }
    if (next >= 0) Triple(next + 1, plan.size, plan[next].text)
    else Triple(plan.size, plan.size, "All steps done")
   }
  }
  // Sticky while plan has open steps, or while agent is still running with a plan
  if (stickyPlan != null && (stickyPlan.third != "All steps done" || isRunning)) {
   val (cur, total, label) = stickyPlan
   val allDone = label == "All steps done"
Surface(
     shape = RoundedCornerShape(10.dp),
     color = if (isDark) Color(0xFF1A1A1E) else Color(0xFFF0FDF4),
     border = null,
     modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
    ) {
     Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
      verticalAlignment = Alignment.CenterVertically
     ) {
      Column(modifier = Modifier.weight(1f)) {
       Text(
        if (allDone) "✓ $total/$total done"
        else "Step $cur/$total",
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (allDone) Color(0xFF34C759) else Color(0xFF0A84FF),
        fontFamily = InterFamily
       )
       Text(
        label,
        fontSize = 11.sp,
        color = primaryText,
        fontFamily = InterFamily,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
       )
      }
      Spacer(Modifier.width(6.dp))
      Text(
       "$cur/$total",
       fontSize = 10.sp,
       fontWeight = FontWeight.Medium,
       color = if (allDone) Color(0xFF34C759) else Color(0xFF0A84FF),
       fontFamily = JetBrainsMonoFamily
      )
     }
    }
  }
  // Sticky banner while ASK_USER is waiting — flat iOS card, no status dots
  if (askUserQuestions != null) {
   Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
    border = BorderStroke(
     0.5.dp,
     if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    ),
    modifier = Modifier
     .fillMaxWidth()
     .padding(bottom = 8.dp)
     .clickable { /* sheet already open via askUserQuestions */ }
   ) {
    Row(
     modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
     verticalAlignment = Alignment.CenterVertically
    ) {
     Column(modifier = Modifier.weight(1f)) {
      Text(
       "Questions for you",
       fontSize = 14.sp,
       fontWeight = FontWeight.SemiBold,
       color = primaryText,
       fontFamily = InterFamily
      )
      Text(
       "Answer below to continue",
       fontSize = 12.sp,
       color = muted,
       fontFamily = InterFamily
      )
     }
     Text(
      "Review",
      fontSize = 15.sp,
      fontWeight = FontWeight.Medium,
      color = primaryText,
      fontFamily = InterFamily
     )
    }
   }
  }
  // Manus-style computer dock — sits above the composer while a browser session is active.
  if ((browserLiveUrl != null || browserBooting || browserBootTimeout.value) && !browserFullScreenOpen) {
  com.ahamai.app.ui.components.BrowserComputerDock(
  isDark = isDark,
  booting = browserBooting && browserLiveUrl == null,
  live = browserLiveUrl != null,
  pageUrl = browserPageUrl,
  title = if (browserBooting && browserLiveUrl == null) "Starting browser"
  else browserStepLabel.ifBlank { "AhamAI's computer" },
  detail = browserStatusDetail,
  elapsedSec = browserElapsedSec,
  onOpen = { browserFullScreenOpen = true },
  modifier = Modifier.padding(bottom = 8.dp)
  )
  }
  // Artifacts preview — screenshots only, shown in bottom edited-files area below
  var artifactTick by remember { mutableStateOf(0) }
  DisposableEffect(projectDir) {
   val l: () -> Unit = { artifactTick++ }
   com.ahamai.app.data.ArtifactStore.addListener(l)
   onDispose { com.ahamai.app.data.ArtifactStore.removeListener(l) }
  }
  val storeShots = remember(projectDir, sessionsRefresh, isRunning, sessionFiles, artifactTick) {
   com.ahamai.app.data.ArtifactStore.screenshots(projectDir).mapNotNull { a ->
    a.abs(projectDir)?.absolutePath
   } + listBrowserScreenshots(projectDir)
  }.distinct()
// /btw aside panel — sits above the composer, never blocks the agent run
AgentBtwAside(
 visible = btwOpen,
 question = btwQuestion,
 answer = btwAnswer,
 loading = btwLoading,
 isDark = isDark,
 toolStatus = btwToolStatus,
 onDismiss = { dismissBtw() }
)
AgentComposer(
  inputText = inputText,
  onInput = { inputText = it },
  isRunning = isRunning,
  isDark = isDark,
  muted = muted,
  primaryText = primaryText,
  large = !started && agentLog.isEmpty(),
  onSend = { agentHaptics.send(); submitMessage(inputText) },
  onStop = { agentHaptics.reject(); stopAgent() },
  modelName = agentModel,
  onModelClick = { agentHaptics.select(); showAgentModelSheet = true },
  onVoiceCall = { agentHaptics.tick(); showAgentVoiceCall = true },
  permissionMode = permissionMode,
  onPermissionModeClick = { showPermModeSheet = true },
 topContent = if (pendingAttachments.isNotEmpty()) {
 {
 PendingAttachmentStrip(
 projectDir = projectDir,
 attachments = pendingAttachments.toList(),
 isDark = isDark,
 muted = muted,
 primaryText = primaryText,
 onRemove = { rel ->
 pendingAttachments.remove(rel)
 scope.launch(Dispatchers.IO) { runCatching { java.io.File(projectDir, rel).delete() } }
 }
 )
 }
 } else null,
 bottomBar = if (!started && connectedRepo.isNotBlank()) {
 {
 TraeContextTray(
 connectedRepo = connectedRepo,
 branch = prefs.getConnectedBranch(),
 isDark = isDark, muted = muted, primaryText = primaryText,
 onRepo = { openRepoFlow() },
 onBranch = { showBranchSheet = true; loadBranches() }
 )
 }
 } else null
 ) {
 // Leading + attach — soft circular control with press + haptic
 Box(
 modifier = Modifier.size(32.dp).clip(CircleShape)
    .background(if (isDark) Color(0xFF2A2A30) else Color(0xFFF0F0F5))
    .agentPressable(haptics = agentHaptics, pressedScale = 0.9f) {
  agentHaptics.tick()
  scope.launch {
   recentPhotos = withContext(Dispatchers.IO) { agentQueryRecentImages(context) }
   showAttachSheet = true
  }
 },
 contentAlignment = Alignment.Center
 ) {
 androidx.compose.foundation.Image(
 painter = painterResource(R.drawable.trae_ic_input_bar_add),
 contentDescription = "Attach",
 modifier = Modifier.size(16.dp),
 colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(if (isDark) Color(0xFFD4D4D8) else Color(0xFF3F3F46))
 )
 }
 }
// Artifacts + edited files strip — screenshots, file diffs with dynamic label
  val haveArtifacts = storeShots.isNotEmpty()
  val haveFiles = sessionFiles.isNotEmpty()
  if (started && (haveArtifacts || haveFiles)) {
  val diffs = rt.fileDiffs.value
  Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, end = 4.dp)) {
  Row(
  modifier = Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp),
  verticalAlignment = Alignment.CenterVertically,
  horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
  val label = when {
   haveArtifacts && haveFiles -> "Screenshots · ${sessionFiles.size} file${if (sessionFiles.size == 1) "" else "s"}"
   haveArtifacts -> "Screenshots (${storeShots.size})"
   else -> "Edited ${sessionFiles.size} file${if (sessionFiles.size == 1) "" else "s"}"
  }
  Text(
  label,
  fontSize = 10.sp,
  color = muted,
  fontFamily = InterFamily,
  fontWeight = FontWeight.Medium
  )
  if (haveFiles) {
  Text("+$sessionAdded", fontSize = 10.sp, color = cDiffGreen, fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMonoFamily)
  Text("−$sessionRemoved", fontSize = 10.sp, color = cDiffRed, fontWeight = FontWeight.SemiBold, fontFamily = JetBrainsMonoFamily)
  }
  Spacer(Modifier.weight(1f))
  PermissionModeChip(permissionMode, muted) { showPermModeSheet = true }
  // Grok-style multi-file turn rewind (primary). Falls back to single-file LIFO undo.
  val canRewindTurn = !isRunning && projectDir.isNotBlank() && run {
   val t = rt.fileState ?: com.ahamai.app.data.FileStateRegistry.tracker(projectDir)
   t.hasRewindableHistory()
  }
  if (haveFiles && (canRewindTurn || rt.undoStack.isNotEmpty()) && !isRunning) {
  Row(
  verticalAlignment = Alignment.CenterVertically,
  horizontalArrangement = Arrangement.spacedBy(4.dp),
  modifier = Modifier
  .clip(RoundedCornerShape(7.dp))
  .clickable {
  val tracker = rt.fileState ?: com.ahamai.app.data.FileStateRegistry.tracker(projectDir)
if (tracker.hasRewindableHistory()) {
    showRewindConfirm = true
   } else {
   scope.launch {
    val revertedPath = rt.undoStack.lastOrNull()?.second
    val msg = rt.undoLast(projectDir)
    if (msg.startsWith("Undone")) {
     sessionFiles = rt.sessionFiles.value
     if (revertedPath != null) {
      val d = rt.fileDiffs.value[revertedPath]
      if (d != null) {
       sessionAdded = (sessionAdded - d.added).coerceAtLeast(0)
       sessionRemoved = (sessionRemoved - d.removed).coerceAtLeast(0)
       rt.fileDiffs.value = rt.fileDiffs.value.toMutableMap().apply { remove(revertedPath) }
      }
     }
    }
    android.widget.Toast.makeText(context, msg.take(80), android.widget.Toast.LENGTH_SHORT).show()
   }
  }
  }
  .padding(horizontal = 8.dp, vertical = 3.dp)
  ) {
  Icon(
  imageVector = com.ahamai.app.ui.icons.Lucide.RotateCcw,
  contentDescription = "Rewind last turn (all files)",
  tint = muted,
  modifier = Modifier.size(12.dp)
  )
  Text(
   if (canRewindTurn) "Rewind" else "Undo",
   fontSize = 10.sp,
   color = muted,
   fontWeight = FontWeight.Medium,
   fontFamily = JetBrainsMonoFamily
  )
  }
  }
  }
  // Screenshot thumbnails row (first 6)
  if (haveArtifacts) {
  Row(
  modifier = Modifier
  .fillMaxWidth()
  .horizontalScroll(rememberScrollState())
  .padding(end = 8.dp, top = 6.dp),
  horizontalArrangement = Arrangement.spacedBy(8.dp),
  verticalAlignment = Alignment.CenterVertically
  ) {
  storeShots.take(6).forEach { abs ->
  val rel = abs.removePrefix(projectDir).trimStart('/', '\\')
  Box(
  modifier = Modifier
  .size(width = 72.dp, height = 52.dp)
  .clip(RoundedCornerShape(10.dp))
  .clickable { previewPath = rel }
  .background(Color.Black.copy(alpha = 0.1f))
  ) {
  AsyncImage(
  model = java.io.File(abs),
  contentDescription = "Screenshot",
  modifier = Modifier.fillMaxSize(),
  contentScale = ContentScale.Crop
  )
  }
  }
  if (storeShots.size > 6) {
  Text(
  "+${storeShots.size - 6} more",
  fontSize = 9.sp, fontFamily = JetBrainsMonoFamily,
  color = muted.copy(alpha = 0.85f), fontWeight = FontWeight.Medium
  )
  }
  }
  }
  // File diff chips (if any)
  if (haveFiles && diffs.isNotEmpty()) {
  val maxChips = 4
  val shown = diffs.entries.take(maxChips)
  val extra = diffs.size - shown.size
  Row(
  modifier = Modifier
  .fillMaxWidth()
  .horizontalScroll(rememberScrollState())
  .padding(end = 8.dp, top = 6.dp),
  horizontalArrangement = Arrangement.spacedBy(6.dp),
  verticalAlignment = Alignment.CenterVertically
  ) {
  shown.forEach { (path, d) ->
  EditedFileChip(path, d, isDark, muted, primaryText) { previewPath = path }
  }
  if (extra > 0) {
 Surface(
  shape = RoundedCornerShape(7.dp),
  color = if (isDark) Color(0xFF1F1F1F) else cLightBg,
  modifier = Modifier.clip(RoundedCornerShape(7.dp))
  .clickable { showEditedFilesSheet = true }
  ) {
  Text(
  "+$extra more",
  modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
  fontSize = 9.sp, fontFamily = JetBrainsMonoFamily,
  color = muted.copy(alpha = 0.85f), fontWeight = FontWeight.Medium
 )
 }
 }
 }
 }
 }
 }
 }
 if (!started && agentLog.isEmpty()) Spacer(Modifier.weight(1f))
 }
 }
 }

 if (showFiles) {
 FilesSheet(projectDir, fileCount, isDark, muted, primaryText, onExport = exportProject) { showFiles = false }
 }

 if (showEditedFilesSheet) {
 EditedFilesSheet(
 fileDiffs = rt.fileDiffs.value,
 isDark = isDark, muted = muted, primaryText = primaryText,
 onOpen = { previewPath = it; showEditedFilesSheet = false },
 onDismiss = { showEditedFilesSheet = false }
 )
 }

 previewPath?.let { p ->
 FilePreviewSheet(projectDir, p, isDark, muted, primaryText) { previewPath = null }
 }

// (annotate/point & draw removed)

 if (showShipHint) {
  IosDialog(isDark = isDark, onDismissRequest = { showShipHint = false }) {
   Text("Ship from phone", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
   Spacer(Modifier.height(6.dp))
   Text(
    "A ship prompt is ready in the composer. Send it to open a PR, summarize diffs, and list deploy previews — then review & merge from the agent log.",
    fontSize = 11.sp, color = muted, fontFamily = InterFamily, lineHeight = 15.sp
   )
   Spacer(Modifier.height(12.dp))
   Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
    TextButton(onClick = { showShipHint = false }) {
     Text("Got it", fontSize = 12.sp, color = primaryText, fontWeight = FontWeight.SemiBold)
    }
   }
  }
 }

 // ── Safety: permission confirm + mode sheet + rewind picker (AgentSafetyUi) ──
 permDialogReq?.let { req ->
  PermissionConfirmDialog(
   req = req,
   isDark = isDark,
   onAllowOnce = {
    AgentPermissionGate.resolve(true, always = false, projectKey = projectDir)
    if (projectDir.isNotBlank() && isRunning) {
     AgentTaskStore.upsert(context, projectDir, status = AgentTaskStore.Status.WORKING)
    }
   },
   onAlwaysAllow = {
    AgentPermissionGate.resolve(true, always = true, projectKey = projectDir)
    if (projectDir.isNotBlank() && isRunning) {
     AgentTaskStore.upsert(context, projectDir, status = AgentTaskStore.Status.WORKING)
    }
   },
   onDeny = {
    AgentPermissionGate.resolve(false, projectKey = projectDir)
    if (projectDir.isNotBlank() && isRunning) {
     AgentTaskStore.upsert(context, projectDir, status = AgentTaskStore.Status.WORKING)
    }
   }
  )
 }
 if (showPermModeSheet) {
  PermissionModeSheet(
   current = permissionMode,
   isDark = isDark,
   onSelect = {
    permissionMode = it
    ToolPermission.saveMode(context, it)
    showPermModeSheet = false
    agentLog = agentLog + AgentLogEntry("note", "Permission mode · ${it.label}")
   },
   onDismiss = { showPermModeSheet = false }
  )
}
  // ── Rewind confirmation dialog (iOS style, matching other confirm dialogs) ──
  if (showRewindConfirm && projectDir.isNotBlank()) {
   val primaryMono = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
   val mutedMono = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B6B6B)
   IosDialog(isDark = isDark, onDismissRequest = { showRewindConfirm = false }) {
    Text(
     "Rewind everything?",
     fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
     fontFamily = com.ahamai.app.ui.theme.InterFamily, color = primaryMono
    )
    Spacer(Modifier.height(6.dp))
    Text(
     "This will undo all file changes from the last executed turn. You can still redo later via the picker.",
     fontSize = 13.sp, fontFamily = com.ahamai.app.ui.theme.InterFamily, color = mutedMono,
     lineHeight = 18.sp
    )
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
     TextButton(onClick = { showRewindConfirm = false }) {
      Text("Cancel", fontSize = 14.sp, fontFamily = com.ahamai.app.ui.theme.InterFamily, color = mutedMono)
     }
     Spacer(Modifier.width(8.dp))
     TextButton(onClick = {
      showRewindConfirm = false
      showRewindPicker = true
     }) {
      Text("Rewind", fontSize = 14.sp, fontFamily = com.ahamai.app.ui.theme.InterFamily, color = primaryMono, fontWeight = FontWeight.SemiBold)
     }
    }
   }
  }

  if (showRewindPicker && projectDir.isNotBlank()) {
  val metas = remember(showRewindPicker, projectDir, sessionFiles.size) {
   (rt.fileState ?: com.ahamai.app.data.FileStateRegistry.tracker(projectDir)).getMetas()
  }
  RewindPickerSheet(
   metas = metas,
   isDark = isDark,
   onPick = { idx ->
    showRewindPicker = false
    scope.launch {
     val resp = rt.rewindToTurn(projectDir, idx)
     sessionFiles = rt.sessionFiles.value
     val remain = rt.fileDiffs.value
     sessionAdded = remain.values.sumOf { it.added }
     sessionRemoved = remain.values.sumOf { it.removed }
     agentLog = agentLog + AgentLogEntry(
      if (resp.success) "note" else "error",
      resp.summaryLine()
     )
     android.widget.Toast.makeText(context, formatRewindToast(resp), android.widget.Toast.LENGTH_SHORT).show()
    }
   },
   onDismiss = { showRewindPicker = false }
  )
 }

 // ── ASK_USER clarification UI ──
 askUserQuestions?.let { json ->
 AskUserSheet(
 questionsJson = json,
 isDark = isDark,
 onSubmit = { answers ->
 val d = askUserDeferred
 if (d != null && !d.isCompleted) d.complete(answers)
 else {
 // Agent already moved on — inject as follow-up
 if (isRunning) pendingUserMessages.add("[ASK_USER ANSWERS]\n$answers")
 else runAgent("User clarified:\n$answers\nContinue with these answers.")
 }
 askUserQuestions = null
 },
 onDismiss = {
 // If dismissed without answering, skip so the agent never hangs forever
 val d = askUserDeferred
 if (d != null && !d.isCompleted) {
 d.complete("User skipped the questions. Use your best judgment and proceed.")
 }
 askUserQuestions = null
 // keep deferred reference until await() finishes; don't null if agent still waiting
 if (d == null || d.isCompleted) askUserDeferred = null
 }
 )
 }

 busy?.let { msg ->
 IosDialog(isDark = isDark, onDismissRequest = {}) {
 Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
 IOSSpinner(size = 20.dp, color = primaryText)
 Spacer(Modifier.width(16.dp))
 Text(msg, color = primaryText, fontSize = 14.sp, fontFamily = InterFamily)
 }
 }
 }

// ── Agent model selector sheet ────────────────────────────────────────────
  if (showAgentModelSheet) {
  IosBottomSheet(
  isDark = isDark,
  onDismissRequest = { showAgentModelSheet = false },
  sheetState = agentModelSheetState,
  noHandle = false
  ) {
  ModelPickerContent(
  models = agentModels,
  loading = agentModelsLoading && agentModels.isEmpty(),
  error = agentModelsError,
  current = agentModel,
  isDark = isDark,
  subtitle = "Choose a model for this agent",
  onSelect = { selected ->
  agentModel = selected
  prefs.setAgentModel(selected)
  scope.launch { agentModelSheetState.hide() }
  .invokeOnCompletion { showAgentModelSheet = false }
  }
  )
  }
  // Sheet open: paint cache/config first, then soft refresh (same as chat picker)
 LaunchedEffect(showAgentModelSheet) {
 if (!showAgentModelSheet) return@LaunchedEffect
 agentModelsError = null
 val instant = com.ahamai.app.data.ApiConfig.cachedAgentModels()
  .ifEmpty { com.ahamai.app.data.ApiConfig.listAgentModelsFromConfig(context) }
 if (instant.isNotEmpty()) agentModels = instant
 agentModelsLoading = agentModels.isEmpty()
 val res = withContext(Dispatchers.IO) {
  com.ahamai.app.data.ApiConfig.listAgentModels(context)
 }
 res.fold(
  onSuccess = { agentModels = it },
  onFailure = { if (agentModels.isEmpty()) agentModelsError = it.message }
 )
 agentModelsLoading = false
 }
 }

 // ── Manus-style "AhamAI's computer" full-screen overlay ──
 if (browserFullScreenOpen && (browserLiveUrl != null || browserBooting || browserBootTimeout.value)) {
 com.ahamai.app.ui.components.BrowserLiveView(
 url = browserLiveUrl,
 pageUrl = browserPageUrl,
 booting = browserBooting && browserLiveUrl == null,
 isDark = isDark,
 isLandscape = browserLandscape,
 statusTitle = "AhamAI is using Browser",
 statusDetail = browserStatusDetail,
 stepLabel = browserStepLabel.ifBlank {
 if (browserBooting && browserLiveUrl == null) "Setting up browser" else "Browser session"
 },
 onClose = { browserFullScreenOpen = false },
 onToggleOrientation = {
 browserLandscape = !browserLandscape
 if (browserLiveUrl != null) {
 scope.launch { CloudBrowser.rotate(context, projectDir) }
 }
 },
 modifier = Modifier.fillMaxSize()
 )
 }

 // ── Voice Call overlay ──
 if (showAgentVoiceCall) {
 com.ahamai.app.screens.VoiceCallScreen(
 onDismiss = { showAgentVoiceCall = false },
 llmBaseUrl = baseUrl,
 llmApiKey = apiKey,
 llmModel = agentModel
 )
 }

 if (showTokenDialog) { var token by remember { mutableStateOf("") }
 var err by remember { mutableStateOf<String?>(null) }
 var checking by remember { mutableStateOf(false) }
 IosDialog(isDark = isDark, onDismissRequest = { showTokenDialog = false }) {
 Text("Connect GitHub", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
 Spacer(Modifier.height(6.dp))
 Text(
  "Paste a PAT with repo + workflow + delete_repo scopes (delete_repo wipes temp APK build repos).",
  fontSize = 12.sp, color = muted, fontFamily = InterFamily
 )
 Spacer(Modifier.height(14.dp))
 Surface(shape = RoundedCornerShape(10.dp), color = if (isDark) Color(0xFF1F1F1F) else Color(0xFFF3F3F6)) {
 BasicTextField(
 value = token, onValueChange = { token = it; err = null },
 modifier = Modifier.fillMaxWidth().padding(12.dp),
 textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = primaryText, fontFamily = JetBrainsMonoFamily),
 cursorBrush = SolidColor(primaryText), singleLine = true,
 decorationBox = { inner -> Box { if (token.isEmpty()) Text("ghp_...", fontSize = 13.sp, color = muted, fontFamily = JetBrainsMonoFamily); inner() } }
 )
 }
 if (err != null) { Spacer(Modifier.height(8.dp)); Text(err!!, fontSize = 12.sp, color = Color(0xFFEF4444)) }
 Spacer(Modifier.height(16.dp))
 Row(
  modifier = Modifier.fillMaxWidth(),
  horizontalArrangement = Arrangement.End,
  verticalAlignment = Alignment.CenterVertically
 ) {
 Text(
  "Cancel", fontSize = 13.sp, color = muted, fontFamily = InterFamily,
  modifier = Modifier.clickable { showTokenDialog = false }.padding(horizontal = 12.dp, vertical = 8.dp)
 )
 Spacer(Modifier.width(8.dp))
 val connectEnabled = token.isNotBlank() && !checking
 // Compact monochrome pill + GitHub favicon (not giant blue)
 val connectBg = when {
  !connectEnabled -> if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
  isDark -> Color(0xFFECECEC)
  else -> Color(0xFF111111)
 }
 val connectFg = when {
  !connectEnabled -> if (isDark) Color(0xFF636366) else Color(0xFF8E8E93)
  isDark -> Color(0xFF111111)
  else -> Color.White
 }
 Row(
  verticalAlignment = Alignment.CenterVertically,
  modifier = Modifier
   .clip(RoundedCornerShape(18.dp))
   .background(connectBg)
   .then(if (connectEnabled) Modifier.clickable {
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
     } else err = "Invalid token. Need repo + workflow + delete_repo scopes."
    }
   } else Modifier)
   .padding(horizontal = 12.dp, vertical = 7.dp)
 ) {
  if (checking) {
   IOSSpinner(size = 12.dp, color = connectFg)
  } else {
   coil.compose.AsyncImage(
    model = "https://www.google.com/s2/favicons?domain=github.com&sz=64",
    contentDescription = "GitHub",
    modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
   )
   Spacer(Modifier.width(6.dp))
   Text("Connect", color = connectFg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, fontFamily = InterFamily)
  }
 }
 }
 }
 }

 if (showGithubLogin) {
 val clipboard = LocalClipboardManager.current
 var device by remember { mutableStateOf<GitHubClient.DeviceCode?>(null) }
 var status by remember { mutableStateOf("starting") } // starting, waiting, error, success
 var attempt by remember { mutableStateOf(0) } // bump to retry
 var confirmDismiss by remember { mutableStateOf(false) } // guard against accidental dismiss
 var pollingActive by remember { mutableStateOf(true) }

 // Kick off the device-flow request (and re-run on Retry).
 LaunchedEffect(attempt) {
 status = "starting"
 device = null
 try {
 val dc = GitHubClient.startDeviceLogin()
 if (dc == null) {
 // OAuth app not reachable/configured — fall back to the manual token dialog.
 showGithubLogin = false
 showTokenDialog = true
 return@LaunchedEffect
 }
 device = dc
 status = "waiting"
 var interval = dc.interval.coerceAtLeast(5)
 var consecutiveErrors = 0
 while (true) {
 delay(interval * 1000L)
 // Check if we should still be polling
 if (!showGithubLogin || status == "error" || pollingActive == false) break
 val result = kotlinx.coroutines.withTimeoutOrNull(15000L) {
 GitHubClient.pollDeviceToken(dc.deviceCode)
 }
 if (result == null) {
 consecutiveErrors++
 if (consecutiveErrors >= 3) { status = "error"; break }
 continue
 }
 val (token, st) = result
 consecutiveErrors = 0
 when (st) {
 "ok" -> {
 if (!token.isNullOrBlank()) {
 prefs.saveGithubToken(token)
 AuthManager.uid()?.let { prefs.saveGithubOwner(it) }
 AuthManager.backupGithubToken(context)
 status = "success"
 // Small delay so user sees the success state
 delay(300)
 showGithubLogin = false
 showRepoSheet = true
 loadRepos()
 } else {
 status = "error"
 }
 return@LaunchedEffect
 }
 "pending" -> { /* keep waiting */ }
 "slow_down" -> interval = (interval + 5).coerceAtMost(30)
 else -> {
 consecutiveErrors++
 if (consecutiveErrors >= 2) { status = "error"; break }
 }
 }
 }
 } catch (e: kotlinx.coroutines.CancellationException) {
 throw e
 } catch (e: Exception) {
 status = "error"
 }
 }

 IosBottomSheet(isDark = isDark, onDismissRequest = {
 // If waiting for auth, ask for confirmation so the user doesn't lose progress
 if (status == "waiting") confirmDismiss = true
 else showGithubLogin = false
 }) {
 // Drag indicator
 Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
 Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFD1D1D6)))
 }
 Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
 Text("Login with GitHub", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
 Spacer(Modifier.height(12.dp))
 val dc = device
 if (status == "starting" || dc == null) {
 Row(verticalAlignment = Alignment.CenterVertically) {
 IOSSpinner(size = 18.dp, color = primaryText)
 Spacer(Modifier.width(12.dp))
 Text("Preparing secure login…", fontSize = 13.sp, color = muted, fontFamily = InterFamily)
 }
 } else if (status == "error") {
 Text("Login didn't complete. The code may have expired or was declined.", fontSize = 13.sp, color = muted, fontFamily = InterFamily, lineHeight = 18.sp)
 Spacer(Modifier.height(8.dp))
 Text("You can also use a Personal Access Token instead.", fontSize = 12.sp, color = muted, fontFamily = InterFamily, lineHeight = 16.sp)
 Spacer(Modifier.height(16.dp))
 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
 Text("Cancel", fontSize = 13.sp, color = muted, fontFamily = InterFamily, modifier = Modifier.clickable { showGithubLogin = false }.padding(horizontal = 12.dp, vertical = 8.dp))
 Text("Use Token Instead", fontSize = 13.sp, color = iosBlue, fontWeight = FontWeight.Medium, fontFamily = InterFamily, modifier = Modifier.clickable { showGithubLogin = false; showTokenDialog = true }.padding(horizontal = 12.dp, vertical = 8.dp))
 Text("Retry", fontSize = 13.sp, color = iosBlue, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, modifier = Modifier.clickable { attempt++ }.padding(horizontal = 12.dp, vertical = 8.dp))
 }
 } else {
 Text("Enter this code at github.com/login/device", fontSize = 13.sp, color = muted, fontFamily = InterFamily)
 Spacer(Modifier.height(16.dp))
 Surface(
 modifier = Modifier.fillMaxWidth(),
 shape = RoundedCornerShape(12.dp),
 color = if (isDark) Color(0xFF1F1F1F) else Color(0xFFF3F3F6)
 ) {
 Text(
 dc.userCode,
 modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
 fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = JetBrainsMonoFamily,
 color = primaryText, letterSpacing = 6.sp,
 textAlign = androidx.compose.ui.text.style.TextAlign.Center
 )
 }
 Spacer(Modifier.height(16.dp))
 // Compact theme-based GitHub CTA (not a giant blue bar)
 val ghBtnBg = if (isDark) Color(0xFFECECEC) else Color(0xFF111111)
 val ghBtnFg = if (isDark) Color(0xFF111111) else Color.White
 Row(
  modifier = Modifier
   .fillMaxWidth()
   .clip(RoundedCornerShape(20.dp))
   .background(ghBtnBg)
   .clickable {
    clipboard.setText(AnnotatedString(dc.userCode))
    try {
     context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(dc.verificationUri)))
    } catch (_: Exception) {}
   }
   .padding(horizontal = 14.dp, vertical = 10.dp),
  verticalAlignment = Alignment.CenterVertically,
  horizontalArrangement = Arrangement.Center
 ) {
  coil.compose.AsyncImage(
   model = "https://www.google.com/s2/favicons?domain=github.com&sz=64",
   contentDescription = "GitHub",
   modifier = Modifier.size(16.dp).clip(RoundedCornerShape(3.dp))
  )
  Spacer(Modifier.width(8.dp))
  Text("Open GitHub", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ghBtnFg, fontFamily = InterFamily)
 }
 Spacer(Modifier.height(16.dp))
 Row(verticalAlignment = Alignment.CenterVertically) {
 IOSSpinner(size = 16.dp, color = muted)
 Spacer(Modifier.width(10.dp))
 Text("Waiting for authorization…", fontSize = 12.sp, color = muted, fontFamily = InterFamily, modifier = Modifier.weight(1f))
 Text("Cancel", fontSize = 13.sp, color = muted, fontFamily = InterFamily, modifier = Modifier.clickable { showGithubLogin = false }.padding(horizontal = 8.dp, vertical = 8.dp))
 }
 }
 Spacer(Modifier.height(8.dp))
 }
 }

 // Confirm dismiss dialog when GitHub login is in progress
 if (confirmDismiss) {
 IosDialog(isDark = isDark, onDismissRequest = { confirmDismiss = false }) {
 Text("Cancel login?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
 Spacer(Modifier.height(6.dp))
 Text("You're in the middle of logging in. Closing this will cancel the process.", fontSize = 13.sp, color = muted, fontFamily = InterFamily)
 Spacer(Modifier.height(16.dp))
 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
 Text("Keep waiting", fontSize = 13.sp, color = iosBlue, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, modifier = Modifier.clickable { confirmDismiss = false }.padding(horizontal = 12.dp, vertical = 8.dp))
 Text("Close", fontSize = 13.sp, color = muted, fontFamily = InterFamily, modifier = Modifier.clickable { confirmDismiss = false; showGithubLogin = false }.padding(horizontal = 12.dp, vertical = 8.dp))
 }
 }
 }
 }

 // ── Spring animation for the repo sheet entrance ──
 val repoSheetAnim = remember { Animatable(0f) }
 if (showRepoSheet) {
 LaunchedEffect(Unit) {
 repoSheetAnim.snapTo(0f)
 repoSheetAnim.animateTo(1f, animationSpec = spring(
 dampingRatio = Spring.DampingRatioMediumBouncy,
 stiffness = Spring.StiffnessMediumLow
 ))
 }
 IosBottomSheet(isDark = isDark, onDismissRequest = { showRepoSheet = false }) {
 var repoQuery by remember { mutableStateOf("") }
 val shownRepos = remember(repos, repoQuery) {
 if (repoQuery.isBlank()) repos else repos.filter { it.fullName.contains(repoQuery, true) }
 }
 Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
 // iOS sheet header
 Row(
 modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Column(Modifier.weight(1f)) {
 Text("Repositories", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText, fontFamily = InterFamily, letterSpacing = (-0.4).sp)
 if (connectedRepo.isNotBlank()) {
 Text(connectedRepo, fontSize = 13.sp, color = muted, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
 }
 }
 if (prefs.isGithubConnected()) {
 Text(
  "Disconnect",
  fontSize = 15.sp,
  color = Color(0xFFFF3B30),
  fontWeight = FontWeight.Medium,
  fontFamily = InterFamily,
  modifier = Modifier
   .clip(RoundedCornerShape(8.dp))
   .clickable {
    scope.launch { AuthManager.clearGithubBackup(context) }
    connectedRepo = ""; showRepoSheet = false
   }
   .padding(horizontal = 10.dp, vertical = 6.dp)
 )
 }
 }
 // iOS search field
 Box(
 modifier = Modifier
 .fillMaxWidth()
 .padding(horizontal = 16.dp, vertical = 4.dp)
 .clip(RoundedCornerShape(12.dp))
 .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA))
 .padding(horizontal = 12.dp, vertical = 10.dp)
 ) {
 Row(verticalAlignment = Alignment.CenterVertically) {
 Icon(Icons.Outlined.Search, null, tint = muted, modifier = Modifier.size(16.dp))
 Spacer(Modifier.width(8.dp))
 Box(Modifier.weight(1f)) {
 if (repoQuery.isEmpty()) Text("Search", fontSize = 16.sp, color = muted.copy(alpha = 0.55f), fontFamily = InterFamily)
 BasicTextField(
 value = repoQuery, onValueChange = { repoQuery = it }, singleLine = true,
 textStyle = LocalTextStyle.current.copy(fontSize = 16.sp, color = primaryText, fontFamily = InterFamily),
 cursorBrush = SolidColor(iosBlue), modifier = Modifier.fillMaxWidth()
 )
 }
 }
 }
 Spacer(Modifier.height(4.dp))
 when {
 reposLoading -> {
 // Smooth skeleton loading
 Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
 repeat(4) { i ->
 val shimmer = remember { Animatable(0f) }
 LaunchedEffect(Unit) {
 shimmer.animateTo(1f, animationSpec = infiniteRepeatable(
 animation = tween(1200, easing = LinearEasing),
 repeatMode = RepeatMode.Reverse
 ))
 }
 Row(
 modifier = Modifier
 .fillMaxWidth()
 .padding(vertical = 6.dp)
 .clip(RoundedCornerShape(8.dp))
 .background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFF4F4F4))
 .padding(10.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.15f + shimmer.value * 0.1f)))
 Spacer(Modifier.width(8.dp))
 Column(modifier = Modifier.weight(1f)) {
 Box(modifier = Modifier.height(10.dp).fillMaxWidth(0.5f + (i % 2) * 0.15f).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.12f + shimmer.value * 0.08f)))
 Spacer(Modifier.height(4.dp))
 Box(modifier = Modifier.height(8.dp).fillMaxWidth(0.3f).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.08f + shimmer.value * 0.06f)))
 }
 }
 }
 }
 }
 reposError != null -> Text(
 reposError!!, color = muted, fontSize = 11.sp, fontFamily = InterFamily,
 modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
 )
 else -> LazyColumn(
 modifier = Modifier.fillMaxWidth(),
 contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp, top = 8.dp)
 ) {
 items(shownRepos, key = { it.fullName }) { repo ->
 val isSel = repo.fullName == connectedRepo
 val rowBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
 Column(
  Modifier
   .fillMaxWidth()
   .padding(bottom = 8.dp)
   .clip(RoundedCornerShape(12.dp))
   .background(rowBg)
 ) {
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
      openProjectDir(dir, repo.fullName.substringAfterLast('/'))
     }
    }
   }
   .padding(horizontal = 14.dp, vertical = 12.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  Column(modifier = Modifier.weight(1f)) {
   Text(
    repo.fullName.substringAfterLast('/'),
    fontSize = 16.sp, fontWeight = FontWeight.Medium,
    color = primaryText, fontFamily = InterFamily,
    maxLines = 1, overflow = TextOverflow.Ellipsis
   )
   Spacer(Modifier.height(2.dp))
   Row(verticalAlignment = Alignment.CenterVertically) {
    if (repo.private) {
     Icon(
      imageVector = com.ahamai.app.ui.icons.AdminIcons.Lock,
      contentDescription = "private",
      tint = Color(0xFFFF9F0A),
      modifier = Modifier.size(11.dp)
     )
     Spacer(Modifier.width(4.dp))
    }
    Text(
     (repo.language ?: "Code") + " · " + repo.defaultBranch,
     fontSize = 13.sp, color = muted, fontFamily = InterFamily,
     maxLines = 1
    )
   }
  }
  if (isSel) {
   Icon(Icons.Filled.Check, "selected", tint = iosBlue, modifier = Modifier.size(18.dp))
  } else {
   Icon(Icons.Filled.ChevronRight, null, tint = muted.copy(alpha = 0.45f), modifier = Modifier.size(18.dp))
  }
 }
 } // Column item wrapper
 } // items
 } // LazyColumn
 } // when
 } // Column sheet
 } // IosBottomSheet
 } // if showRepoSheet

 val branchSheetAnim = remember { Animatable(0f) }
 if (showBranchSheet) {
 LaunchedEffect(Unit) {
 branchSheetAnim.snapTo(0f)
 branchSheetAnim.animateTo(1f, animationSpec = spring(
 dampingRatio = Spring.DampingRatioMediumBouncy,
 stiffness = Spring.StiffnessMediumLow
 ))
 }
 IosBottomSheet(isDark = isDark, onDismissRequest = { showBranchSheet = false }) {
 Column(modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp)) {
 Row(
 modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 8.dp, top = 4.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Column(Modifier.weight(1f)) {
 Text("Branches", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primaryText, fontFamily = InterFamily, letterSpacing = (-0.4).sp)
 Text(
 connectedRepo.substringAfterLast('/').ifBlank { "Select a branch" },
 fontSize = 13.sp, color = muted, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis
 )
 }
 }
 if (branchesLoading) {
 // Skeleton loading for branches
 Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
 repeat(3) { i ->
 val shimmer = remember { Animatable(0f) }
 LaunchedEffect(Unit) {
 shimmer.animateTo(1f, animationSpec = infiniteRepeatable(
 animation = tween(1200, easing = LinearEasing),
 repeatMode = RepeatMode.Reverse
 ))
 }
 Row(
 modifier = Modifier
 .fillMaxWidth()
 .padding(vertical = 5.dp)
.clip(RoundedCornerShape(8.dp))
  .background(if (isDark) Color(0xFF1F1F1F) else cLightBg)
  .padding(10.dp),
  verticalAlignment = Alignment.CenterVertically
  ) {
  Box(modifier = Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.15f + shimmer.value * 0.1f)))
  Spacer(Modifier.width(8.dp))
  Box(modifier = Modifier.height(9.dp).fillMaxWidth(0.35f + (i % 2) * 0.1f).clip(RoundedCornerShape(2.dp)).background(muted.copy(alpha = 0.1f + shimmer.value * 0.07f)))
 }
 }
 }
 } else if (branches.isEmpty()) {
 Text("No branches found.", color = muted, fontSize = 11.sp, fontFamily = InterFamily, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
 } else {
 LazyColumn(
 modifier = Modifier.fillMaxWidth(),
 contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp, top = 4.dp)
 ) {
 items(branches) { br ->
 val isCur = br == prefs.getConnectedBranch()
 val rowBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
 Spacer(Modifier.height(6.dp))
 Row(
  modifier = Modifier
   .fillMaxWidth()
   .clip(RoundedCornerShape(12.dp))
   .background(rowBg)
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
      openProjectDir(dir, connectedRepo.substringAfterLast('/'))
     }
    }
   }
   .padding(horizontal = 14.dp, vertical = 14.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  Icon(
   imageVector = com.ahamai.app.ui.icons.Lucide.GitBranch,
   contentDescription = null,
   tint = if (isCur) iosBlue else muted,
   modifier = Modifier.size(18.dp)
  )
  Spacer(Modifier.width(12.dp))
  Text(
   br,
   fontSize = 16.sp,
   fontWeight = if (isCur) FontWeight.SemiBold else FontWeight.Normal,
   fontFamily = InterFamily,
   color = if (isCur) iosBlue else primaryText,
   modifier = Modifier.weight(1f),
   maxLines = 1,
   overflow = TextOverflow.Ellipsis
  )
  if (isCur) {
   Icon(Icons.Filled.Check, "current", tint = iosBlue, modifier = Modifier.size(18.dp))
  }
 }
 }
 }
 }
 }
 }
 }

 // ── Skills sheet (Grok-style) ──
 if (showSkillsSheet) {
 SkillsSheet(
 isDark = isDark,
 onDismiss = { showSkillsSheet = false },
 onManageAll = {
 showSkillsSheet = false
 showSkillsOverlay = true
 },
 onSkillCreator = {
 showSkillsSheet = false
 SkillManager.loadSkill("skill-creator")
 inputText = "/skill-creator "
 },
 onAddSkill = {
 showSkillsSheet = false
 showAddSkillDialog = true
 }
 )
 }

 if (showSkillsOverlay) {
 androidx.compose.ui.window.Dialog(
 onDismissRequest = { showSkillsOverlay = false },
 properties = androidx.compose.ui.window.DialogProperties(
 usePlatformDefaultWidth = false,
 decorFitsSystemWindows = false
 )
 ) {
 Box(
 modifier = Modifier
 .fillMaxSize()
.background(if (isDark) cDarkBg else cLightBg)
  ) {
  SkillsScreen(
 onBack = { showSkillsOverlay = false },
 onSkillCreator = {
 showSkillsOverlay = false
 SkillManager.loadSkill("skill-creator")
 inputText = "/skill-creator "
 }
 )
 }
 }
 }

if (showAddSkillDialog) {
  var n by remember { mutableStateOf("") }
  var d by remember { mutableStateOf("") }
  var c by remember { mutableStateOf("") }
  var importMd by remember { mutableStateOf("") }
  var skillUrl by remember { mutableStateOf("") }
  var isFetching by remember { mutableStateOf(false) }
  var addMode by remember { mutableStateOf(0) } // 0 = form, 1 = Paste SKILL.md
  val dlgBg = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F7)
  val fld = if (isDark) Color(0xFF1F1F1F) else Color(0xFFECECEC)
  val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
  val secondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF6B6B6B)
  val borderCol = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE5E5EA)
  val canSave = if (addMode == 1) importMd.contains("---") else n.isNotBlank()
  AlertDialog(
  onDismissRequest = { showAddSkillDialog = false },
  containerColor = dlgBg,
  shape = RoundedCornerShape(20.dp),
  title = {
  Column {
  Text("Add Skill", fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = primary, fontSize = 17.sp)
  Text("Agent Skills / SKILL.md standard", fontSize = 11.sp, fontFamily = InterFamily, color = secondary)
  }
  },
  text = {
  Column(
  modifier = Modifier
  .fillMaxWidth()
  .heightIn(max = 420.dp)
  .verticalScroll(rememberScrollState())
  ) {
  Row(Modifier.fillMaxWidth()) {
  listOf("Form", "Paste SKILL.md").forEachIndexed { i, label ->
  val selected = addMode == i
  Text(
  text = label,
  modifier = Modifier
  .clip(RoundedCornerShape(8.dp))
  .clickable { addMode = i }
  .padding(horizontal = 10.dp, vertical = 6.dp),
  color = if (selected) primary else secondary,
  fontSize = 13.sp,
  fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
  fontFamily = InterFamily
  )
  }
  }
  Spacer(Modifier.height(10.dp))
  if (addMode == 1) {
  Text("SKILL.md URL (optional)", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily, color = secondary, modifier = Modifier.padding(bottom = 6.dp))
  Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
  OutlinedTextField(
  value = skillUrl, onValueChange = { skillUrl = it },
  placeholder = { Text("https://raw.githubusercontent.com/.../SKILL.md", fontSize = 11.sp) },
  modifier = Modifier.weight(1f),
  singleLine = true,
  colors = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = fld, unfocusedContainerColor = fld,
  focusedBorderColor = borderCol, unfocusedBorderColor = borderCol
  ),
  textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = InterFamily, color = primary)
  )
  Spacer(Modifier.width(8.dp))
  Text(
  "Fetch",
  modifier = Modifier
  .clip(RoundedCornerShape(8.dp))
  .background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFE5E5EA))
  .clickable(enabled = skillUrl.isNotBlank() && !isFetching) {
  val url = skillUrl.trim()
  kotlinx.coroutines.MainScope().launch {
  isFetching = true
  try {
  val text = withContext(Dispatchers.IO) { URL(url).readText() }
  if (text.contains("---")) importMd = text
  } catch (_: Exception) { }
  isFetching = false
  }
  }
  .padding(horizontal = 14.dp, vertical = 8.dp),
  fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily,
  color = if (skillUrl.isNotBlank() && !isFetching) primary else secondary
  )
  }
  Spacer(Modifier.height(8.dp))
  Text("SKILL.md", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily, color = secondary, modifier = Modifier.padding(bottom = 6.dp))
  OutlinedTextField(
  value = importMd, onValueChange = { importMd = it },
  placeholder = { Text("---\nname: my-skill\ndescription: >\n  What it does and when to use it.\n---\n\n# My Skill\n...", fontSize = 11.sp) },
  minLines = 6,
  modifier = Modifier.fillMaxWidth(),
  colors = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = fld,
  unfocusedContainerColor = fld,
  focusedBorderColor = borderCol,
  unfocusedBorderColor = borderCol
  ),
  textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = InterFamily, color = primary)
  )
  } else {
  Text("Name", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily, color = secondary, modifier = Modifier.padding(bottom = 6.dp))
  OutlinedTextField(
  value = n, onValueChange = { n = it },
  placeholder = { Text("e.g. Code Reviewer", fontSize = 12.sp) },
  singleLine = true,
  modifier = Modifier.fillMaxWidth(),
  colors = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = fld,
  unfocusedContainerColor = fld,
  focusedBorderColor = borderCol,
  unfocusedBorderColor = borderCol
  ),
  textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = InterFamily, color = primary)
  )
  Spacer(Modifier.height(12.dp))
  Text("Description (what + when)", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily, color = secondary, modifier = Modifier.padding(bottom = 6.dp))
  OutlinedTextField(
  value = d, onValueChange = { d = it },
  placeholder = { Text("What it does AND when the agent should use it", fontSize = 12.sp) },
  singleLine = false,
  minLines = 3,
  modifier = Modifier.fillMaxWidth(),
  colors = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = fld,
  unfocusedContainerColor = fld,
  focusedBorderColor = borderCol,
  unfocusedBorderColor = borderCol
  ),
  textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = InterFamily, color = primary)
  )
  Spacer(Modifier.height(12.dp))
  Text("Instructions (SKILL.md body)", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily, color = secondary, modifier = Modifier.padding(bottom = 6.dp))
  OutlinedTextField(
  value = c, onValueChange = { c = it },
  placeholder = { Text("Step-by-step rules the agent must follow…", fontSize = 12.sp) },
  minLines = 4,
  modifier = Modifier.fillMaxWidth(),
  colors = OutlinedTextFieldDefaults.colors(
  focusedContainerColor = fld,
  unfocusedContainerColor = fld,
  focusedBorderColor = borderCol,
  unfocusedBorderColor = borderCol
  ),
  textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = InterFamily, color = primary)
  )
  }
  }
  },
  confirmButton = {
  TextButton(
  enabled = canSave,
  onClick = {
  SkillManager.init(context)
  if (addMode == 1) {
  SkillManager.importSkillMd(importMd, enabled = true)
  } else {
  SkillManager.createCustomSkill(n.trim(), d.trim(), c.trim(), enabled = true)
  }
  showAddSkillDialog = false
  }
  ) { Text("Save", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, color = primary) }
  },
  dismissButton = {
  TextButton(onClick = { showAddSkillDialog = false }) {
  Text("Cancel", fontFamily = InterFamily, color = secondary)
  }
  }
  )
  }

 // ── Attachment sheet (iOS Settings style — same as chat mode) ──
 if (showAttachSheet) {
 AgentAttachmentSheet(
 isDark = isDark,
 recentPhotos = recentPhotos,
 onCamera = {
 showAttachSheet = false
 val granted = androidx.core.content.ContextCompat.checkSelfPermission(
 context, android.Manifest.permission.CAMERA
 ) == android.content.pm.PackageManager.PERMISSION_GRANTED
 if (granted) launchCamera() else cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
 },
 onPhotos = {
 showAttachSheet = false; imagePicker.launch("image/*")
 },
 onFiles = {
 showAttachSheet = false; filePicker.launch("*/*")
 },
 onPhotoPicked = { uriStr ->
  showAttachSheet = false
  runCatching {
   importAttachment(android.net.Uri.parse(uriStr), null)
  }
 },
 onConnectors = {
 showAttachSheet = false; showConnectorsSheet = true
 },
 onSkills = {
 // Open full SkillsScreen (same UI as profile) — not the old Grok sheet
 showAttachSheet = false; showSkillsOverlay = true
 },
 onGitHub = {
 showAttachSheet = false; openRepoFlow()
 },
 onDismiss = { showAttachSheet = false }
 )
 }

 // ── Connectors hero sheet (3 floating animated favicons + Try these + Add) ──
 if (showConnectorsSheet) {
 ConnectorsSheet(
 isDark = isDark,
 onManageAll = {
 showConnectorsSheet = false
 showConnectorsOverlay = true
 },
 onDismiss = { showConnectorsSheet = false }
 )
 }

 // ── Full Connectors browser (from "+ Add" on the hero sheet) ──
 if (showConnectorsOverlay) {
 androidx.compose.ui.window.Dialog(
 onDismissRequest = { showConnectorsOverlay = false },
 properties = androidx.compose.ui.window.DialogProperties(
 usePlatformDefaultWidth = false,
 decorFitsSystemWindows = false
 )
 ) {
 // Same surface as ConnectorsScreen so dialog window never flashes another colour.
 Box(
 modifier = Modifier
 .fillMaxSize()
.background(if (isDark) cDarkBg else cLightBg)
  ) {
  ConnectorsScreen(onBack = { showConnectorsOverlay = false })
 }
 }
 }

}
// end CodeAgentScreen

/** ONE shared composer pill used on the agent home AND for follow-up chatting. The [leading]
 * slot holds the attachment "+" button so the input stays identical in every state. */
@Composable
private fun AgentComposer(
 inputText: String,
 onInput: (String) -> Unit,
 isRunning: Boolean,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 onSend: () -> Unit,
 onStop: () -> Unit = {},
 large: Boolean = false,
 modelName: String = "",
 onModelClick: () -> Unit = {},
 onVoiceCall: () -> Unit = {},
 permissionMode: PermissionMode = PermissionMode.AUTO,
 onPermissionModeClick: () -> Unit = {},
 topContent: (@Composable () -> Unit)? = null,
 bottomBar: (@Composable () -> Unit)? = null,
 leading: @Composable () -> Unit
) {
 val haptics = rememberAgentHaptics()
 // iOS Messages–style pill (matches chat composer)
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
 Column(modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 8.dp)) {
 if (topContent != null) {
 topContent()
 Spacer(Modifier.height(6.dp))
 }
 // Compact multi-line text area with internal scroll.
 val inputScroll = rememberScrollState()
 LaunchedEffect(inputText) { inputScroll.scrollTo(inputScroll.maxValue) }
 BasicTextField(
 value = inputText,
 onValueChange = onInput,
 modifier = Modifier.fillMaxWidth()
 .heightIn(min = if (large) 52.dp else 36.dp, max = 120.dp)
 .verticalScroll(inputScroll),
 textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = primaryText, fontFamily = InterFamily, lineHeight = 21.sp),
 cursorBrush = SolidColor(primaryText),
 decorationBox = { inner ->
 Box {
 if (inputText.isEmpty()) {
 if (isRunning) Text(
 "Working…  ·  /btw side question",
 fontSize = 15.sp,
 color = muted.copy(alpha = 0.75f),
 fontFamily = InterFamily,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis
 )
 else Text(
 "Message…",
 fontSize = 15.sp,
 color = muted.copy(alpha = 0.72f),
 fontFamily = InterFamily,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis
 )
 }
 inner()
 }
 }
 )
 Spacer(Modifier.height(6.dp))
 // Bottom action row: "+" · model chip · voice · send
 Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
 leading()
 // Model selector chip — soft pill like Synara footer
 Surface(
 modifier = Modifier
 .padding(horizontal = 2.dp)
 .clip(RoundedCornerShape(50))
 .agentPressable(haptics = haptics, pressedScale = 0.96f) { haptics.select(); onModelClick() },
 shape = RoundedCornerShape(50),
 color = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0F0F5)
 ) {
 Row(
 modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.spacedBy(3.dp)
 ) {
Text(
  text = modelName.substringAfterLast('/').ifBlank { modelName },
  fontSize = 11.sp, color = muted, maxLines = 1,
  modifier = Modifier.widthIn(max = 130.dp),
  overflow = TextOverflow.Ellipsis,
  fontFamily = InterFamily,
  fontWeight = FontWeight.Medium
  )
 Icon(com.ahamai.app.ui.icons.Phosphor.CaretDown, null, tint = muted, modifier = Modifier.size(12.dp))
 }
 }
Spacer(Modifier.weight(1f))
  // Voice call button
  if (!isRunning && inputText.isBlank()) {
 Box(
 modifier = Modifier.size(34.dp).clip(CircleShape)
  .background(if (isDark) Color(0xFF2A2A30) else Color(0xFFF0F0F5))
  .agentPressable(haptics = haptics, pressedScale = 0.9f) { haptics.tick(); onVoiceCall() },
 contentAlignment = Alignment.Center
 ) {
 Icon(
 imageVector = com.ahamai.app.ui.icons.Phosphor.Phone,
 contentDescription = "Voice Call",
 tint = if (isDark) Color(0xFF9CA3AF) else primaryText,
 modifier = Modifier.size(15.dp)
 )
 }
 Spacer(Modifier.width(6.dp))
 }
 val canSend = inputText.isNotBlank()
 run {
 // Unified, smoothly-animated composer action button: background color and
 // icon crossfade between states instead of swapping composables outright,
 // plus a light press-scale — same "alive" feel as the button, animated.
 val sendState = when {
 isRunning && !canSend -> "stop"
 canSend -> "send"
 else -> "idle"
 }
 val sendBg by animateColorAsState(
 targetValue = when (sendState) {
 "stop" -> Color(0xFFEF4444)
 "send" -> if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
 else -> if (isDark) cDarkBorder else cLightBorder
 },
 animationSpec = tween(220), label = "agentSendBg"
 )
 val sendInteraction = remember { MutableInteractionSource() }
 val sendPressed by sendInteraction.collectIsPressedAsState()
 HapticOnPress(interactionSource = sendInteraction, haptics = haptics, enabled = sendState != "idle")
 val sendScale by animateFloatAsState(
 targetValue = if (sendPressed) 0.88f else 1f,
 animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
 label = "agentSendScale"
 )
 Box(
 modifier = Modifier
 .size(36.dp)
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
 (fadeIn(tween(160)) + scaleIn(initialScale = 0.55f, animationSpec = tween(160))) togetherWith
 (fadeOut(tween(90)) + scaleOut(targetScale = 0.55f, animationSpec = tween(90)))
 },
 label = "agentSendIcon"
 ) { state ->
 if (state == "stop") {
   Icon(
   imageVector = Lucide.Square,
    contentDescription = "Stop",
   tint = Color.White,
   modifier = Modifier.size(14.dp)
   )
   } else {
 Icon(
 // Up-arrow send (Material) — Phosphor.ArrowUp was drawn left-facing / “ulta”
 imageVector = Icons.Filled.ArrowUpward,
 contentDescription = "Send",
tint = when (state) {
    "send" -> if (isDark) Color.Black else Color.White
    else -> if (isDark) Color(0xFF9CA3AF) else Color.White
   },
 modifier = Modifier.size(20.dp)
 )
 }
 }
 }
 }
 }
 // Repo / branch tray — no harsh divider; soft gap only
 if (bottomBar != null) {
 Spacer(Modifier.height(8.dp))
 bottomBar()
 }
 }
 }
}

/**
 * Fancy STOP control shown while the agent is working: a continuously-spinning arc (the "loading"
 * feel) wrapped around a solid stop square, with a small "Stop" caption underneath. Loading and
 * stop are unified into one icon — tapping it halts the run.
 */
@Composable
private fun StopRingButton(isDark: Boolean, onStop: () -> Unit) {
 val iosBlue = if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF)
 val transition = rememberInfiniteTransition(label = "stopring")
 val angle by transition.animateFloat(
 initialValue = 0f, targetValue = 360f,
 animationSpec = infiniteRepeatable(animation = tween(900, easing = LinearEasing)),
 label = "rot"
 )
 Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
 Box(
 modifier = Modifier.size(38.dp).clip(CircleShape).clickable { onStop() },
 contentAlignment = Alignment.Center
 ) {
 Canvas(modifier = Modifier.size(38.dp)) {
 val stroke = 2.6.dp.toPx()
 val inset = stroke / 2f
 // Faint full track ring.
 drawCircle(
 color = iosBlue.copy(alpha = 0.16f),
 radius = (size.minDimension - stroke) / 2f,
 style = Stroke(width = stroke)
 )
 // Bright sweeping arc that rotates → the "loading" motion.
 rotate(angle) {
 drawArc(
 color = iosBlue,
 startAngle = 0f, sweepAngle = 100f, useCenter = false,
 topLeft = Offset(inset, inset),
 size = Size(size.width - stroke, size.height - stroke),
 style = Stroke(width = stroke, cap = StrokeCap.Round)
 )
 }
 }
 // Solid rounded stop square in the centre.
 Box(modifier = Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF)))
 }
 Text("Stop", fontSize = 8.5.sp, color = iosBlue.copy(alpha = 0.8f), fontFamily = InterFamily, fontWeight = FontWeight.Medium)
 }
}

/**
 * Cycling input placeholder: "Ask AhamAI to " + a capability that TYPES in, holds, then BURSTS
 * away (scale + fade) before the next one — same spirit as the banner. Monochrome.
 */
@Composable
private fun CyclingPlaceholder(muted: Color) {
 val items = remember {
 listOf(
 "build an Android app",
 "build a full-stack web app",
 "scan a site for vulnerabilities",
 "decompile & rebuild an APK",
 "create or edit a PDF",
 "read the text in an image",
 "run a command in the cloud",
 "push your project to GitHub",
 "build & ship an APK",
 "find secrets in code"
 )
 }
 var index by remember { mutableStateOf(0) }
 var shown by remember { mutableStateOf("") }
 var bursting by remember { mutableStateOf(false) }
 val burst = remember { Animatable(0f) }
 LaunchedEffect(Unit) {
 while (true) {
 val f = items[index]
 bursting = false; burst.snapTo(0f); shown = ""
 for (i in 1..f.length) { shown = f.substring(0, i); delay(58) }
 delay(2200)
 bursting = true; burst.animateTo(1f, tween(420)); bursting = false
 shown = ""; index = (index + 1) % items.size; delay(160)
 }
 }
 val f = items[index]
 val ph = muted.copy(alpha = 0.7f)
 Row(verticalAlignment = Alignment.CenterVertically) {
 Text("Ask to ", fontSize = 14.sp, color = ph, fontFamily = InterFamily, maxLines = 1)
 val scale = 1f + burst.value * 0.18f
 val a = if (bursting) (1f - burst.value).coerceIn(0f, 1f) else 1f
 Text(
 text = (if (bursting) f else shown) + (if (!bursting && shown.length < f.length) "\u258C" else ""),
 fontSize = 14.sp, color = ph, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis,
 modifier = Modifier.graphicsLayer {
 scaleX = scale; scaleY = scale; this.alpha = a
 transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
 }
 )
 }
}

/** Flat ChatGPT-style suggestion row: plain outline icon + label, NO card/background. */
/**
 * iOS-style "liquid glass" banner: translucent frosted panel (no gradient) that TYPES out one
 * capability after another, then makes it BURST away (text scales+fades while a ring of small
 * monochrome dots flies outward) before typing the next. All monochrome — no colours, no icons
 * with colour.
 */
@Composable
private fun FeatureBanner(isDark: Boolean, primaryText: Color, muted: Color) {
 val features = remember {
 listOf(
 "Generate images from text",
 "Decompile & rebuild APKs",
 "Security recon & vuln scans",
 "Clone any website's source",
 "Create & edit PDFs in place",
 "Run any command in the cloud",
 "Read text from images · OCR",
 "Build Android apps into an APK",
 "Install any tool on the fly",
 "Edit, crop & watermark images",
 "Scan code for hidden secrets",
 "Capture full-page screenshots"
 )
 }
 var index by remember { mutableStateOf(0) }
 var shown by remember { mutableStateOf("") }
 var bursting by remember { mutableStateOf(false) }
 val burst = remember { Animatable(0f) }

 LaunchedEffect(Unit) {
 while (true) {
 val full = features[index]
 bursting = false
 burst.snapTo(0f)
 shown = ""
 for (i in 1..full.length) { shown = full.substring(0, i); delay(34) }
 delay(1300)
 bursting = true
 burst.animateTo(1f, tween(480))
 shown = ""
 bursting = false
 index = (index + 1) % features.size
 delay(150)
 }
 }

 // Frosted "liquid glass" tinted with the app's accent (iOS blue) — flat translucent fill +
 // hairline accent border + soft shadow. No gradient.
 val glass = if (isDark) iosBlue.copy(alpha = 0.13f) else iosBlue.copy(alpha = 0.08f)
 val borderC = if (isDark) iosBlue.copy(alpha = 0.32f) else iosBlue.copy(alpha = 0.22f)
 val full = features[index]
 Box(
 modifier = Modifier
 .fillMaxWidth()
 .height(58.dp)
 .shadow(14.dp, RoundedCornerShape(22.dp), clip = false)
 .clip(RoundedCornerShape(22.dp))
 .background(glass)
 .border(1.dp, borderC, RoundedCornerShape(22.dp)),
 contentAlignment = Alignment.CenterStart
 ) {
 // Burst particles (accent colour) emanating from where the text sits.
 if (bursting) {
 Canvas(Modifier.matchParentSize()) {
 val p = burst.value
 val cx = 26.dp.toPx()
 val cy = size.height / 2f
 val n = 12
 for (k in 0 until n) {
 val ang = (2.0 * PI * k / n).toFloat()
 val r = 6.dp.toPx() + p * 48.dp.toPx()
 drawCircle(
 color = iosBlue.copy(alpha = (1f - p) * 0.55f),
 radius = ((1f - p) * 2.4f + 0.6f).dp.toPx(),
 center = Offset(cx + cos(ang) * r, cy + sin(ang) * r)
 )
 }
 }
 }
 Row(modifier = Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
 val scale = 1f + burst.value * 0.22f
 val a = if (bursting) (1f - burst.value).coerceIn(0f, 1f) else 1f
 Text(
 text = (if (bursting) full else shown) + (if (!bursting && shown.length < full.length) "\u258C" else ""),
 fontSize = 15.sp, fontWeight = FontWeight.Medium, color = primaryText, fontFamily = InterFamily,
 maxLines = 1, overflow = TextOverflow.Ellipsis,
 modifier = Modifier.graphicsLayer {
 scaleX = scale; scaleY = scale; this.alpha = a
 transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
 }
 )
 }
 }
}

/** Compact suggestion row: a soft looping Lottie "glow" behind a themed colour icon + label/sub. */
@Composable
private fun SuggestRow(icon: ImageVector, accent: Color, label: String, sub: String, primaryText: Color, muted: Color, onClick: () -> Unit) {
 val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.suggest_pulse))
 val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
 Row(
 modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
 .padding(horizontal = 16.dp, vertical = 9.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
 if (composition != null) {
 LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.matchParentSize())
 }
 Icon(icon, null, tint = accent, modifier = Modifier.size(19.dp))
 }
 Spacer(Modifier.width(13.dp))
 Column(modifier = Modifier.weight(1f)) {
 Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primaryText, fontFamily = InterFamily)
 if (sub.isNotBlank()) {
 Text(sub, fontSize = 11.sp, color = muted, fontFamily = InterFamily, maxLines = 1, overflow = TextOverflow.Ellipsis)
 }
 }
 }
}

/**
 * Agent attachment sheet — same iOS Settings style as chat [AttachmentSheet]:
 * large title, grouped list rows, recent photo grid, plus agent tools group.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentAttachmentSheet(
 isDark: Boolean,
 recentPhotos: List<String>,
 onCamera: () -> Unit,
 onPhotos: () -> Unit,
 onFiles: () -> Unit,
 onPhotoPicked: (String) -> Unit,
 onConnectors: () -> Unit,
 onSkills: () -> Unit,
 onGitHub: () -> Unit,
 onDismiss: () -> Unit
) {
 val textColor = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk
  else com.ahamai.app.ui.theme.ChatPalette.LightInk
 val subtextColor = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInkSecondary
  else com.ahamai.app.ui.theme.ChatPalette.LightInkSecondary
 val groupBg = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkSurface
  else com.ahamai.app.ui.theme.ChatPalette.LightSurfaceElevated
 val sep = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkBorder
  else com.ahamai.app.ui.theme.ChatPalette.LightBorder
 // Monochrome — no blue accents on agent attach sheet
 val iconTint = if (isDark) Color(0xFFAEAEB2) else Color(0xFF3A3A3C)
 val chevron = if (isDark) Color(0xFF5A5A5E) else Color(0xFFC7C7CC)

 IosBottomSheet(
  isDark = isDark,
  onDismissRequest = onDismiss,
  noHandle = false
 ) {
  Column(
   modifier = Modifier
    .fillMaxWidth()
    .navigationBarsPadding()
    .padding(bottom = 14.dp)
  ) {
   Text(
    text = "Attach",
    fontSize = 22.sp,
    fontWeight = FontWeight.Bold,
    color = textColor,
    fontFamily = InterFamily,
    letterSpacing = (-0.4f).sp,
    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
   )
   Text(
    text = "Photos, files & tools",
    fontSize = 13.sp,
    color = subtextColor,
    fontFamily = InterFamily,
    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
   )

   // Media group — compact monochrome tiles
   Row(
    modifier = Modifier
     .fillMaxWidth()
     .padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
   ) {
    AgentIosAttachTile(
     label = "Camera",
     icon = Lucide.Camera,
     textColor = textColor,
     iconTint = iconTint,
     onClick = onCamera,
     modifier = Modifier.weight(1f)
    )
    AgentIosAttachTile(
     label = "Photos",
     icon = Lucide.Image,
     textColor = textColor,
     iconTint = iconTint,
     onClick = onPhotos,
     modifier = Modifier.weight(1f)
    )
    AgentIosAttachTile(
     label = "Files",
     icon = Lucide.File,
     textColor = textColor,
     iconTint = iconTint,
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
     fontFamily = InterFamily,
     modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
    )
    androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
     columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
     modifier = Modifier
      .fillMaxWidth()
      .heightIn(max = 220.dp)
      .padding(horizontal = 16.dp),
     horizontalArrangement = Arrangement.spacedBy(6.dp),
     verticalArrangement = Arrangement.spacedBy(6.dp),
     userScrollEnabled = false
    ) {
     items(recentPhotos.take(8).size) { idx ->
      val uri = recentPhotos[idx]
      val interaction = remember { MutableInteractionSource() }
      val pressed by interaction.collectIsPressedAsState()
      val scale by animateFloatAsState(
       targetValue = if (pressed) 0.94f else 1f,
       animationSpec = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
       ),
       label = "photoScale"
      )
      AsyncImage(
       model = uri,
       contentDescription = "Photo",
       contentScale = ContentScale.Crop,
       modifier = Modifier
        .aspectRatio(1f)
        .scale(scale)
        .clip(RoundedCornerShape(10.dp))
        .clickable(
         interactionSource = interaction,
         indication = null
        ) { onPhotoPicked(uri) }
      )
     }
    }
   }

   // Agent tools group
   Spacer(Modifier.height(22.dp))
   Text(
    text = "AGENT",
    fontSize = 12.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.4.sp,
    color = subtextColor,
    fontFamily = InterFamily,
    modifier = Modifier.padding(start = 32.dp, bottom = 8.dp)
   )
   Column(
    modifier = Modifier
     .fillMaxWidth()
     .padding(horizontal = 16.dp)
     .clip(RoundedCornerShape(14.dp))
     .background(groupBg)
   ) {
    AgentIosAttachRow(
     label = "Connectors",
     icon = Lucide.Plug,
     textColor = textColor,
     iconTint = iconTint,
     chevron = chevron,
     onClick = onConnectors
    )
    HorizontalDivider(thickness = 0.5.dp, color = sep, modifier = Modifier.padding(start = 52.dp))
    AgentIosAttachRow(
     label = "Skills",
     icon = Lucide.SkillsNodes,
     textColor = textColor,
     iconTint = iconTint,
     chevron = chevron,
     onClick = onSkills
    )
    HorizontalDivider(thickness = 0.5.dp, color = sep, modifier = Modifier.padding(start = 52.dp))
    AgentIosAttachRow(
     label = "GitHub",
     icon = Lucide.Github,
     textColor = textColor,
     iconTint = iconTint,
     chevron = chevron,
     onClick = onGitHub
    )
   }
  }
 }
}

/** iOS Settings-style row — flat icon, label, chevron (matches chat attach rows). */
@Composable
private fun AgentIosAttachRow(
 label: String,
 icon: ImageVector,
 textColor: Color,
 iconTint: Color,
 chevron: Color,
 onClick: () -> Unit
) {
 val interaction = remember { MutableInteractionSource() }
 val pressed by interaction.collectIsPressedAsState()
 val scale by animateFloatAsState(
  targetValue = if (pressed) 0.985f else 1f,
  animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
  label = "attachRowScale"
 )
 Row(
  modifier = Modifier
   .fillMaxWidth()
   .scale(scale)
   .clickable(
    interactionSource = interaction,
    indication = null
   ) { onClick() }
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
   fontFamily = InterFamily,
   letterSpacing = (-0.2f).sp,
   modifier = Modifier.weight(1f)
  )
  Icon(
   imageVector = Lucide.ChevronRight,
   contentDescription = null,
   tint = chevron,
   modifier = Modifier.size(16.dp)
  )
 }
}
/**
 * Agent attachment tile — circular icon tile (same style as chat [IosAttachTile]).
 */
@Composable
private fun AgentIosAttachTile(
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
   .clickable(
    interactionSource = remember { MutableInteractionSource() },
    indication = null
   ) { onClick() }
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
   fontFamily = InterFamily,
   maxLines = 1
  )
 }
}

/** Recent device photos for the agent attachment sheet (MediaStore). */
private fun agentQueryRecentImages(context: android.content.Context): List<String> {
 val images = mutableListOf<String>()
 try {
  val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
   android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
  } else {
   android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
  }
  val projection = arrayOf(android.provider.MediaStore.Images.Media._ID)
  val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
  context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
   val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
   var count = 0
   while (cursor.moveToNext() && count < 10) {
    val id = cursor.getLong(idColumn)
    val uri = android.content.ContentUris.withAppendedId(collection, id)
    images.add(uri.toString())
    count++
   }
  }
 } catch (_: Exception) {}
 return images
}

 /** Classic iOS activity indicator: 12 rounded spokes radiating from the center, each fading in
 * opacity, ticking around in 12 discrete steps. A clean replacement for Material's spinner. */
@Composable
internal fun IOSSpinner(modifier: Modifier = Modifier, color: Color = Color(0xFF8E8E93), size: Dp = 20.dp) {
 val transition = rememberInfiniteTransition(label = "iosSpinner")
 val t by transition.animateFloat(
 initialValue = 0f,
 targetValue = 12f,
 animationSpec = infiniteRepeatable(
 animation = tween(durationMillis = 1000, easing = LinearEasing),
 repeatMode = RepeatMode.Restart
 ),
 label = "step"
 )
 Canvas(modifier = modifier.size(size)) {
 val n = 12
 val step = t.toInt() % n
 // Stepwise rotation (12 ticks) for the authentic iOS "jump" feel.
 rotate(degrees = step * (360f / n)) {
 val w = this.size.width
 val cx = w / 2f
 val cy = this.size.height / 2f
 val outer = w / 2f
 val inner = outer * 0.45f
 val stroke = (w * 0.085f).coerceAtLeast(1.2f)
 for (i in 0 until n) {
 val angle = (Math.PI * 2.0 * i / n) - Math.PI / 2.0
 val cos = kotlin.math.cos(angle).toFloat()
 val sin = kotlin.math.sin(angle).toFloat()
 val alpha = 0.15f + 0.85f * (i.toFloat() / (n - 1))
 drawLine(
 color = color.copy(alpha = alpha),
 start = Offset(cx + cos * inner, cy + sin * inner),
 end = Offset(cx + cos * outer, cy + sin * outer),
 strokeWidth = stroke,
 cap = androidx.compose.ui.graphics.StrokeCap.Round
 )
 }
 }
 }
}

/** Best-effort display name for a content Uri (falls back to the last path segment). */
private fun attachmentDisplayName(context: android.content.Context, uri: android.net.Uri): String? {
 return try {
 var name: String? = null
 if (uri.scheme == "content") {
 context.contentResolver.query(uri, null, null, null, null)?.use { c ->
 val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
 if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
 }
 }
 name ?: uri.lastPathSegment?.substringAfterLast('/')
 } catch (_: Exception) {
 uri.lastPathSegment?.substringAfterLast('/')
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
 Text(label, fontSize = 12.sp, color = primaryText, fontFamily = JetBrainsMonoFamily, maxLines = 1)
 }
}

/** Trae-style branch chip: the real Trae git-branch icon + label + down chevron. */
@Composable
private fun TraeBranchChip(label: String, muted: Color, primaryText: Color, onClick: () -> Unit) {
 Row(
 modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 androidx.compose.foundation.Image(
 painter = painterResource(R.drawable.trae_git_branches),
 contentDescription = null,
 modifier = Modifier.size(16.dp),
 colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(muted)
 )
 Spacer(Modifier.width(5.dp))
 Text(label, fontSize = 12.sp, color = primaryText, fontFamily = JetBrainsMonoFamily, maxLines = 1)
 Spacer(Modifier.width(2.dp))
 androidx.compose.foundation.Image(
 painter = painterResource(R.drawable.trae_ic_input_arrow_down),
 contentDescription = null,
 modifier = Modifier.size(14.dp),
 colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(muted)
 )
 }
}

/** Footer chip that shows the real Trae GitHub icon (used for the GitHub repository chip). */
@Composable
private fun FooterFaviconChip(domain: String, label: String, muted: Color, primaryText: Color, onClick: () -> Unit) {
 Row(
 modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 androidx.compose.foundation.Image(
 painter = painterResource(R.drawable.trae_ic_input_bar_github),
 contentDescription = null,
 modifier = Modifier.size(16.dp),
 colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(primaryText)
 )
 Spacer(Modifier.width(5.dp))
 Text(label, fontSize = 12.sp, color = primaryText, fontFamily = JetBrainsMonoFamily, maxLines = 1)
 Spacer(Modifier.width(2.dp))
 androidx.compose.foundation.Image(
 painter = painterResource(R.drawable.trae_ic_input_arrow_down),
 contentDescription = null,
 modifier = Modifier.size(14.dp),
 colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(muted)
 )
 }
}

/** Composer attachment strip: staged files as thumbnails, each with an X to remove before sending. */
@Composable
private fun PendingAttachmentStrip(
 projectDir: String,
 attachments: List<String>,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 onRemove: (String) -> Unit
) {
 Row(
 modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
 horizontalArrangement = Arrangement.spacedBy(8.dp)
 ) {
 attachments.forEach { rel ->
 val ext = rel.substringAfterLast('.', "").lowercase()
 val isImage = ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
 Box(modifier = Modifier.padding(top = 4.dp, end = 4.dp)) {
 if (isImage) {
 AsyncImage(
 model = java.io.File(projectDir, rel),
 contentDescription = null,
 contentScale = androidx.compose.ui.layout.ContentScale.Crop,
 modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))
 .background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFE9E9EC))
 )
 } else {
 // DIRECT preview in the composer (xlsx table, pdf page, doc text, csv, or clean card).
 Box(modifier = Modifier.width(240.dp)) {
 Box(modifier = Modifier.heightIn(max = 190.dp).clip(RoundedCornerShape(14.dp))) {
 com.ahamai.app.ui.components.FilePreviewCard(
 projectDir = projectDir,
 relPath = rel,
 isDark = isDark,
 compact = true,
 showActions = false   // composer attachment — no actions
 )
 }
 }
 }
 // X remove control
 Box(
 modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)
 .size(18.dp).clip(CircleShape).background(Color(0xCC000000))
 .clickable { onRemove(rel) },
 contentAlignment = Alignment.Center
 ) {
 Icon(Icons.Filled.Close, "Remove", tint = Color.White, modifier = Modifier.size(11.dp))
 }
 }
 }
 }
}

/**
 * iOS-style repo / branch chips under the agent composer.
 * No vertical divider — two soft capsules with spacing.
 */
@Composable
private fun TraeContextTray(
 connectedRepo: String,
 branch: String,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 onRepo: () -> Unit,
 onBranch: () -> Unit
) {
 val chipBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)
 val chipInk = if (connectedRepo.isNotBlank()) primaryText else muted
 Row(
  modifier = Modifier.fillMaxWidth(),
  verticalAlignment = Alignment.CenterVertically,
  horizontalArrangement = Arrangement.spacedBy(8.dp)
 ) {
  IosTrayChip(
   iconRes = R.drawable.trae_ic_input_bar_github,
   label = if (connectedRepo.isNotBlank()) connectedRepo.substringAfterLast('/').take(18) else "GitHub",
   labelColor = chipInk,
   muted = muted,
   chipBg = chipBg,
   modifier = Modifier.weight(1f),
   onClick = onRepo
  )
  IosTrayChip(
   iconRes = R.drawable.trae_git_branches,
   label = if (connectedRepo.isNotBlank()) branch.take(16).ifBlank { "main" } else "Branch",
   labelColor = if (connectedRepo.isNotBlank()) primaryText else muted.copy(alpha = 0.55f),
   muted = muted,
   chipBg = chipBg,
   modifier = Modifier.weight(1f),
   enabled = connectedRepo.isNotBlank(),
   onClick = onBranch
  )
 }
}

@Composable
private fun IosTrayChip(
 iconRes: Int,
 label: String,
 labelColor: Color,
 muted: Color,
 chipBg: Color,
 modifier: Modifier = Modifier,
 enabled: Boolean = true,
 onClick: () -> Unit = {}
) {
 Row(
  modifier = modifier
   .clip(RoundedCornerShape(12.dp))
   .background(chipBg)
   .clickable(enabled = enabled, onClick = onClick)
   .padding(horizontal = 10.dp, vertical = 8.dp),
  verticalAlignment = Alignment.CenterVertically
 ) {
  androidx.compose.foundation.Image(
   painter = painterResource(iconRes),
   contentDescription = null,
   modifier = Modifier.size(15.dp),
   colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(labelColor)
  )
  Spacer(Modifier.width(6.dp))
  Text(
   label,
   fontSize = 12.sp,
   fontFamily = InterFamily,
   fontWeight = FontWeight.Medium,
   color = labelColor,
   maxLines = 1,
   overflow = TextOverflow.Ellipsis,
   modifier = Modifier.weight(1f, fill = false)
  )
  Spacer(Modifier.width(2.dp))
  Icon(
   imageVector = Icons.Filled.KeyboardArrowDown,
   contentDescription = null,
   tint = muted,
   modifier = Modifier.size(16.dp)
  )
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceDrawer(
 context: android.content.Context,
 projectDir: String,
 projectName: String,
 fileCount: Int,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 sessions: List<ProjectManager.ProjectInfo>,
 onOpenFile: (String) -> Unit,
 onExportFile: (String) -> Unit,
 onExportZip: () -> Unit,
 onSwitchSession: (ProjectManager.ProjectInfo) -> Unit,
 onNewProject: () -> Unit,
 onDeleteSession: (ProjectManager.ProjectInfo) -> Unit,
 onClose: () -> Unit
) {
 // Clean palette — no blue. Single accent derived from neutral grays so the drawer
 // feels calm and minimal, matching the iOS settings list aesthetic.
 val bg = if (isDark) cDarkBg else cLightBg
 val accent = if (isDark) Color(0xFFE5E5EA) else Color(0xFF141414)
 val softPillBg = if (isDark) Color(0xFF141414) else Color.White
 val activePillBg = if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA)

 // Spring-based entrance animation — feels faster and more natural than tween.
 val drawerAnim = remember { Animatable(0f) }
 LaunchedEffect(Unit) {
 drawerAnim.animateTo(1f, animationSpec = spring(
 dampingRatio = Spring.DampingRatioMediumBouncy,
 stiffness = Spring.StiffnessMediumLow
 ))
 }
 // Per-row stagger animation — each file/session slides in with a tiny delay so the
 // drawer feels "alive" instead of popping in all at once.
 var query by remember { mutableStateOf("") }
 var filter by remember { mutableStateOf("All") }
 val collapsed = remember { mutableStateListOf<String>() }
 var files by remember { mutableStateOf<List<ProjectFile>>(emptyList()) }
 LaunchedEffect(projectDir, fileCount) {
 files = withContext(Dispatchers.IO) {
 if (projectDir.isBlank()) emptyList() else ProjectManager.listFiles(projectDir)
 }
 }

 fun ext(p: String) = p.substringAfterLast('.', "").lowercase()
 fun matchesType(p: String) = when (filter) {
 "Images" -> ext(p) in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg")
 "APKs" -> ext(p) in setOf("apk", "aab")
 "PDFs" -> ext(p) == "pdf"
 "Code" -> ext(p) in setOf("kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "c", "cpp", "h", "go", "rs", "rb", "php", "html", "css", "json", "xml", "yml", "yaml", "sh", "gradle", "md")
 else -> true
 }
 val flatMode = query.isNotBlank() || filter != "All"
 val visible = if (flatMode)
 files.filter { !it.isDirectory && (query.isBlank() || it.relativePath.contains(query, true)) && matchesType(it.relativePath) }
 else
 files.filter { f -> collapsed.none { cf -> f.relativePath != cf && f.relativePath.startsWith("$cf/") } }

 ModalDrawerSheet(
 drawerContainerColor = bg,
 // No shape, no border — the drawer is fully edge-to-edge for an ultra-clean feel.
 modifier = Modifier.fillMaxHeight().width(326.dp).graphicsLayer {
 alpha = drawerAnim.value
 translationX = (1f - drawerAnim.value) * -40f // slides in from the left
 }
 ) {
 Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 18.dp)) {
 // Header — no borders, no background pills. Just typography.
 Row(
 modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Column(modifier = Modifier.weight(1f)) {
 Text(
 "Workspace",
 fontSize = 24.sp,
 fontWeight = FontWeight.Bold,
 color = primaryText,
 fontFamily = InterFamily
 )
 AnimatedContent(
 targetState = projectName.ifBlank { "No project open" },
 transitionSpec = {
 (slideInVertically { it / 4 } + fadeIn()) togetherWith
 (slideOutVertically { -it / 4 } + fadeOut())
 },
 label = "projectNameSwap"
 ) { name ->
 Text(
 name,
 fontSize = 12.sp,
 color = muted,
 fontFamily = InterFamily,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis
 )
 }
 }
 Box(
 modifier = Modifier
 .size(36.dp)
 .clip(CircleShape)
 .background(softPillBg)
 .clickable { onNewProject() },
 contentAlignment = Alignment.Center
 ) {
 Icon(Icons.Outlined.Add, "New project", tint = primaryText, modifier = Modifier.size(20.dp))
 }
 }

 // Search — no border, just a soft fill (iOS-style inset grouped search).
 Box(
 modifier = Modifier
 .fillMaxWidth()
 .clip(RoundedCornerShape(12.dp))
 .background(softPillBg)
 .padding(horizontal = 12.dp, vertical = 11.dp)
 ) {
 Row(verticalAlignment = Alignment.CenterVertically) {
 Icon(Icons.Outlined.Search, null, tint = muted, modifier = Modifier.size(17.dp))
 Spacer(Modifier.width(8.dp))
 Box(Modifier.weight(1f)) {
 if (query.isEmpty()) {
 Text("Search files", fontSize = 14.sp, color = muted, fontFamily = InterFamily)
 }
 BasicTextField(
 value = query, onValueChange = { query = it },
 textStyle = LocalTextStyle.current.copy(
 fontSize = 14.sp,
 color = primaryText,
 fontFamily = InterFamily
 ),
 cursorBrush = SolidColor(iosBlue),
 singleLine = true,
 modifier = Modifier.fillMaxWidth()
 )
 }
 }
 }
 Spacer(Modifier.height(12.dp))

 // Filter chips — soft pills, no borders, animated selection.
 Row(
 modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
 horizontalArrangement = Arrangement.spacedBy(7.dp)
 ) {
 listOf("All", "Images", "APKs", "PDFs", "Code").forEach { t ->
 val sel = filter == t
 Box(
 modifier = Modifier
 .clip(RoundedCornerShape(20.dp))
 .background(if (sel) activePillBg else Color.Transparent)
 .clickable { filter = t }
 .padding(horizontal = 13.dp, vertical = 7.dp)
 ) {
 Text(
 t,
 fontSize = 12.sp,
 fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
 color = if (sel) primaryText else muted,
 fontFamily = InterFamily
 )
 }
 }
 }
 Spacer(Modifier.height(10.dp))

 LazyColumn(
 modifier = Modifier.weight(1f).fillMaxWidth(),
 contentPadding = PaddingValues(vertical = 6.dp)
 ) {
 item {
 Text(
 "FILES",
 fontSize = 10.5.sp,
 fontWeight = FontWeight.SemiBold,
 color = muted.copy(alpha = 0.7f),
 fontFamily = InterFamily,
 letterSpacing = 0.8.sp,
 modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
 )
 }
 if (visible.isEmpty()) {
 item {
 Text(
 if (projectDir.isBlank()) "No active project. Tap + to start one." else "No files yet.",
 fontSize = 13.sp,
 color = muted,
 fontFamily = InterFamily,
 modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
 )
 }
 }
 itemsIndexed(visible) { idx, f ->
 val isDir = f.isDirectory
 val isColl = f.relativePath in collapsed
 // Subtle row-press scale animation.
 val pressed = remember { Animatable(1f) }
 Row(
 modifier = Modifier
 .fillMaxWidth()
 .clip(RoundedCornerShape(10.dp))
 .clickable {
 if (isDir) { if (isColl) collapsed.remove(f.relativePath) else collapsed.add(f.relativePath) }
 else onOpenFile(f.relativePath)
 }
 .padding(start = (4 + (if (flatMode) 0 else f.depth * 13)).dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
 .graphicsLayer { scaleX = pressed.value; scaleY = pressed.value },
 verticalAlignment = Alignment.CenterVertically
 ) {
 if (isDir) {
 Icon(
 if (isColl) Icons.Filled.ChevronRight else Icons.Filled.KeyboardArrowDown,
 null, tint = muted, modifier = Modifier.size(16.dp)
 )
 Spacer(Modifier.width(3.dp))
 Icon(Icons.Outlined.FolderOpen, null, tint = muted, modifier = Modifier.size(16.dp))
 } else {
 Spacer(Modifier.width(19.dp))
 Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = muted, modifier = Modifier.size(15.dp))
 }
 Spacer(Modifier.width(10.dp))
 Text(
 if (flatMode) f.relativePath else f.relativePath.substringAfterLast('/'),
 fontSize = 13.5.sp,
 fontFamily = InterFamily,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 color = if (isDir) primaryText else (if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)),
 modifier = Modifier.weight(1f)
 )
 if (!isDir) {
 Box(
 modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onExportFile(f.relativePath) },
 contentAlignment = Alignment.Center
 ) {
 Icon(AdminIcons.BootstrapSave, "Save to device", tint = muted, modifier = Modifier.size(15.dp))
 }
 }
 }
 }
 item {
 Spacer(Modifier.height(18.dp))
 Text(
 "RECENT SESSIONS",
 fontSize = 10.5.sp,
 fontWeight = FontWeight.SemiBold,
 color = muted.copy(alpha = 0.7f),
 fontFamily = InterFamily,
 letterSpacing = 0.8.sp,
 modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
 )
 if (sessions.isEmpty()) {
 Text(
 "No saved sessions yet.",
 fontSize = 12.5.sp,
 color = muted,
 fontFamily = InterFamily,
 modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
 )
 }
 }
 items(sessions) { s ->
 val cur = s.path == projectDir
 Row(
 modifier = Modifier
 .fillMaxWidth()
 .padding(vertical = 2.dp)
 .clip(RoundedCornerShape(12.dp))
 .background(if (cur) activePillBg else Color.Transparent)
 .clickable { onSwitchSession(s) }
 .padding(vertical = 11.dp, horizontal = 12.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Icon(
 Icons.Outlined.FolderOpen, null,
 tint = if (cur) primaryText else muted,
 modifier = Modifier.size(17.dp)
 )
 Spacer(Modifier.width(11.dp))
 Column(Modifier.weight(1f)) {
 Text(
 s.name,
 fontSize = 14.sp,
 fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Medium,
 color = primaryText,
 fontFamily = InterFamily,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis
 )
 Text(
 "${s.fileCount} files",
 fontSize = 11.sp,
 color = muted,
 fontFamily = InterFamily
 )
 }
 Box(
 modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onDeleteSession(s) },
 contentAlignment = Alignment.Center
 ) {
 Icon(Icons.Outlined.Delete, "Delete", tint = muted, modifier = Modifier.size(14.dp))
 }
 }
 }
 }
 // Export button — soft pill, no border.
 Box(
 modifier = Modifier
 .fillMaxWidth()
 .padding(vertical = 14.dp)
 .clip(RoundedCornerShape(14.dp))
 .background(softPillBg)
 .clickable { onExportZip() }
 .padding(vertical = 13.dp),
 contentAlignment = Alignment.Center
 ) {
 Row(verticalAlignment = Alignment.CenterVertically) {
 Icon(AdminIcons.ZipIcon, null, tint = primaryText, modifier = Modifier.size(17.dp))
 Spacer(Modifier.width(8.dp))
 Text(
 "Export workspace (.zip)",
 fontSize = 13.5.sp,
 fontWeight = FontWeight.Medium,
 color = primaryText,
 fontFamily = InterFamily
 )
 }
 }
 }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilesSheet(
 projectDir: String, fileCount: Int, isDark: Boolean, muted: Color, primaryText: Color,
 onExport: () -> Unit, onDismiss: () -> Unit
) {
 val files = remember { ProjectManager.listFiles(projectDir) }
 var viewing by remember { mutableStateOf<String?>(null) }
 var content by remember { mutableStateOf("") }
 val border = if (isDark) cDarkBorder else cLightBorder

 ModalBottomSheet(
 onDismissRequest = onDismiss,
containerColor = if (isDark) cDarkBg else cLightBg,
  dragHandle = { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFD1D1D6))) } }
  ) {
  if (viewing != null) {
 Column(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
 Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
 IconButton(onClick = { viewing = null }, modifier = Modifier.size(30.dp)) {
 Icon(Icons.Filled.ArrowBack, "Back", tint = muted, modifier = Modifier.size(17.dp))
 }
 Spacer(Modifier.width(6.dp))
 Icon(Icons.Outlined.Description, null, tint = muted, modifier = Modifier.size(15.dp))
 Spacer(Modifier.width(8.dp))
 Text(viewing!!.substringAfterLast('/'), fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = JetBrainsMonoFamily, color = primaryText, maxLines = 1)
 }
 Spacer(Modifier.height(8.dp))
 Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 28.dp)) {
 MarkdownText(text = "```${viewing!!.substringAfterLast('.')}\n$content\n```", color = primaryText)
 }
 }
 } else {
 // Header: Workspace · file count · Export
 Row(
 modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 10.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Icon(AdminIcons.FolderMultiple, null, tint = primaryText, modifier = Modifier.size(19.dp))
 Spacer(Modifier.width(10.dp))
 Text("Workspace", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryText)
 Spacer(Modifier.width(8.dp))
 Text("$fileCount files", fontSize = 11.sp, color = muted, fontFamily = JetBrainsMonoFamily, modifier = Modifier.weight(1f))
 Row(
 modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onExport() }
 .padding(horizontal = 8.dp, vertical = 6.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Icon(AdminIcons.BootstrapSave, null, tint = muted, modifier = Modifier.size(16.dp))
 Spacer(Modifier.width(5.dp))
 Text("Save", fontSize = 12.sp, color = muted, fontFamily = InterFamily)
 }
 }
 Spacer(Modifier.height(8.dp))
 LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)) {
 items(files) { f ->
 Row(
 modifier = Modifier.fillMaxWidth()
 .clickable(enabled = !f.isDirectory) {
 if (!f.isDirectory && ProjectManager.isTextFile(f.relativePath)) {
 content = ProjectManager.readFile(projectDir, f.relativePath)
 viewing = f.relativePath
 }
 }
 .padding(start = (20 + f.depth * 12).dp, end = 16.dp, top = 7.dp, bottom = 7.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Icon(
 if (f.isDirectory) Icons.Outlined.FolderOpen else Icons.AutoMirrored.Outlined.InsertDriveFile,
 null, tint = if (f.isDirectory) iosBlue.copy(alpha = 0.8f) else muted, modifier = Modifier.size(15.dp)
 )
 Spacer(Modifier.width(10.dp))
 Text(f.relativePath.substringAfterLast('/'), fontSize = 13.sp, fontFamily = JetBrainsMonoFamily,
 color = if (f.isDirectory) primaryText else (if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)))
 }
 }
 }
 }
 }
}

/** Compact chip for a single edited file shown side-by-side below the composer: filename +
 * "+added −removed" stats. Tapping it opens the file preview. */
@Composable
private fun EditedFileChip(
 path: String,
 diff: DiffUtil.DiffResult,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 onClick: () -> Unit
) {
 // Synara light/dark file row: soft surface, mono path, green/red stats
Surface(
  shape = RoundedCornerShape(10.dp),
  color = if (isDark) cDarkRow else Color.White,
  modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onClick() }
  ) {
 Row(
 modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.spacedBy(6.dp)
 ) {
 Icon(
 Icons.AutoMirrored.Outlined.InsertDriveFile,
 null,
 tint = muted,
 modifier = Modifier.size(12.dp)
 )
 Text(
 path.take(36),
 fontSize = 10.sp,
 fontFamily = JetBrainsMonoFamily,
 color = primaryText.copy(alpha = 0.9f),
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 modifier = Modifier.widthIn(max = 160.dp)
 )
 if (diff.added > 0) {
 Text("+${diff.added}", fontSize = 10.sp, fontFamily = JetBrainsMonoFamily, color = cDiffGreen, fontWeight = FontWeight.SemiBold)
 }
 if (diff.removed > 0) {
 Text("−${diff.removed}", fontSize = 10.sp, fontFamily = JetBrainsMonoFamily, color = cDiffRed, fontWeight = FontWeight.SemiBold)
 }
 }
 }
}

/** Bottom sheet listing every file edited this session with its diff stats. Opened from the
 * "+N more" chip when the composer footer can't show them all side-by-side. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditedFilesSheet(
 fileDiffs: Map<String, DiffUtil.DiffResult>,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 onOpen: (String) -> Unit,
 onDismiss: () -> Unit
) {
 val totalAdded = fileDiffs.values.sumOf { it.added }
 val totalRemoved = fileDiffs.values.sumOf { it.removed }
ModalBottomSheet(
  onDismissRequest = onDismiss,
  containerColor = if (isDark) cDarkBg else cLightBg,
  dragHandle = { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFD1D1D6))) } }
  ) {
  Row(
  modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 10.dp),
  verticalAlignment = Alignment.CenterVertically
  ) {
  Icon(Icons.Outlined.Description, null, tint = primaryText, modifier = Modifier.size(19.dp))
  Spacer(Modifier.width(10.dp))
  Text("Edited files", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = primaryText)
 Spacer(Modifier.width(8.dp))
 Text("${fileDiffs.size}", fontSize = 11.sp, color = muted, fontFamily = JetBrainsMonoFamily, modifier = Modifier.weight(1f))
 Text("+$totalAdded", fontSize = 11.sp, color = Color(0xFF34C759), fontWeight = FontWeight.Medium, fontFamily = JetBrainsMonoFamily)
 Spacer(Modifier.width(8.dp))
 Text("\u2212$totalRemoved", fontSize = 11.sp, color = Color(0xFFFF3B30), fontWeight = FontWeight.Medium, fontFamily = JetBrainsMonoFamily)
 }
 LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp)) {
 items(fileDiffs.entries.toList()) { (path, d) ->
 Row(
 modifier = Modifier.fillMaxWidth()
 .clickable { onOpen(path) }
 .padding(start = 20.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = muted, modifier = Modifier.size(15.dp))
 Spacer(Modifier.width(10.dp))
 Column(modifier = Modifier.weight(1f)) {
 Text(
 path.substringAfterLast('/'),
 fontSize = 13.sp, fontFamily = JetBrainsMonoFamily,
 color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151),
 maxLines = 1, overflow = TextOverflow.Ellipsis
 )
 if (path.contains('/')) {
 Text(
 path.substringBeforeLast('/'),
 fontSize = 9.sp, fontFamily = JetBrainsMonoFamily,
 color = muted.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis
 )
 }
 }
 Spacer(Modifier.width(8.dp))
 Text("+${d.added}", fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFF34C759))
 Spacer(Modifier.width(6.dp))
 Text("\u2212${d.removed}", fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFFFF3B30))
 }
 }
 }
 }
}

/** Bottom sheet that previews/opens a project file: text (syntax block), image (inline), or a
 * share action for anything else. Used by attachment chips and agent-created file chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilePreviewSheet(
 projectDir: String, path: String, isDark: Boolean, muted: Color, primaryText: Color, onDismiss: () -> Unit
) {
 val context = LocalContext.current
val name = path.substringAfterLast('/')
  val ext = path.substringAfterLast('.', "").lowercase()
  val isImage = ext in setOf("png", "jpg", "jpeg", "webp", "gif")
  val isText = remember(path) { ProjectManager.isTextFile(path) }
  val content = remember(path) { if (isText && !isImage) ProjectManager.readFile(projectDir, path) else "" }

  fun shareFile() {
  try {
  val file = File(projectDir, path)
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  val intent = Intent(Intent.ACTION_SEND).apply {
  type = context.contentResolver.getType(uri) ?: "application/octet-stream"
  putExtra(Intent.EXTRA_STREAM, uri)
  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  context.startActivity(Intent.createChooser(intent, "Open / Share"))
  } catch (_: Exception) {}
  }

 ModalBottomSheet(
   onDismissRequest = onDismiss,
   containerColor = if (isDark) cDarkBg else cLightBg,
   dragHandle = { Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Box(modifier = Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (isDark) Color(0xFF2A2A2A) else Color(0xFFD1D1D6))) } }
   ) {
   Column(modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
  Row(
  modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
  verticalAlignment = Alignment.CenterVertically
  ) {
  // Clean filename — like a subtle watermark, no icon
  Text(name, fontSize = 13.sp, fontWeight = FontWeight.Normal, fontFamily = InterFamily, color = muted.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
   Spacer(Modifier.width(4.dp))
  IconButton(onClick = onDismiss, modifier = Modifier.size(34.dp)) {
  Icon(Icons.Filled.Close, "Close", tint = muted, modifier = Modifier.size(18.dp))
  }
  }
 when {
 isImage -> {
 Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
 AsyncImage(
 model = File(projectDir, path),
 contentDescription = name,
 modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(12.dp))
 )
 }
 Spacer(Modifier.height(20.dp))
 }
 isText -> {
 Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 8.dp)) {
 MarkdownText(text = "```$ext\n$content\n```", color = primaryText)
 Spacer(Modifier.height(12.dp))
 }
 }
 else -> {
 Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
 Text("This file can't be previewed here.", fontSize = 13.sp, color = muted, fontFamily = InterFamily)
 Spacer(Modifier.height(16.dp))
 Box(
 modifier = Modifier.clip(RoundedCornerShape(12.dp))
 .background(if (isDark) cDarkRow else cLightRow)
 .clickable { shareFile() }
 .padding(horizontal = 18.dp, vertical = 12.dp)
 ) {
 Text("Open / Share", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = primaryText, fontFamily = InterFamily)
 }
 Spacer(Modifier.height(20.dp))
 }
 }
 }
 }
 }
}

/** Reads the session's changed files and pushes them to the connected GitHub repo. */
private suspend fun pushChangedFiles(
 context: android.content.Context,
 projectDir: String,
 changedPaths: List<String>,
 message: String,
 branchArg: String
): String = withContext(Dispatchers.IO) {
 val prefs = com.ahamai.app.data.PreferencesManager(context)
 val token = prefs.getGithubToken()
 val repo = prefs.getConnectedRepo()
 if (token.isBlank() || repo.isBlank()) return@withContext "ERROR: No GitHub repo connected."
 val branch = branchArg.ifBlank { prefs.getConnectedBranch() }
 val files = LinkedHashMap<String, ByteArray>()
 for (p in changedPaths) {
 try {
 val f = java.io.File(projectDir, p)
 if (f.exists() && f.isFile) files[p] = f.readBytes()
 } catch (_: Exception) {}
 }
 // Fallback: if the session's changed-file list is empty/stale (e.g. after a branch/repo
 // switch, or a tracking gap), push the WHOLE project. commitFiles only uploads files that
 // actually differ from the repo, so this pushes the real diff and never falsely reports
 // "no changed files" when edits clearly exist. If truly nothing changed it returns
 // "already up to date" — not an error.
 if (files.isEmpty()) files.putAll(collectAllFiles(projectDir))
 if (files.isEmpty()) return@withContext "ERROR: Project is empty — nothing to push."
 com.ahamai.app.data.GitHubClient.commitFiles(
 token, repo, branch, files,
 message.ifBlank { "Update from AhamAI" }
 )
}

/**
 * Wipe a throwaway public APK-build repo and clear local "connected repo" if it pointed there.
 * Always used after success OR failure so project source leaves no public GitHub trace.
 */
private suspend fun wipeTempBuildRepo(
 context: android.content.Context,
 token: String,
 repo: String,
): String {
 val gh = com.ahamai.app.data.GitHubClient
 if (repo.isBlank()) return "skip: empty"
 val del = withContext(Dispatchers.IO) {
  when {
   gh.isTempBuildRepo(repo) -> gh.deleteTempBuildRepo(token, repo)
   // Extra safety for misnamed throwaways
   repo.substringAfter('/').contains("ahamai-build", ignoreCase = true) ->
    gh.deleteRepo(token, repo)
   else -> "skip: not a temp build repo ($repo)"
  }
 }
 val prefs = com.ahamai.app.data.PreferencesManager(context)
 if (prefs.getConnectedRepo() == repo) {
  prefs.saveConnectedRepo("", "")
 }
 // Keep token; update cloud backup so a deleted temp repo is not re-attached later.
 com.ahamai.app.data.AuthManager.backupGithubToken(context)
 return del
}

/** Builds the project's APK in the cloud via GitHub Actions. Creates a repo + pushes if needed. */
private suspend fun buildApkFlow(
 context: android.content.Context,
 projectDir: String,
 projectName: String,
 changedPaths: List<String>
): String = withContext(Dispatchers.IO) {
 // Agent-mode ad #1: rewarded video as the (long) cloud build kicks off, filling the wait.
 com.ahamai.app.ui.components.AgentAds.onBuildStart(context)
 val prefs = com.ahamai.app.data.PreferencesManager(context)
 val token = prefs.getGithubToken()
 if (token.isBlank())
 return@withContext "GITHUB NOT CONNECTED. To build an APK I need GitHub. Ask the user to tap the Repository button on the Agent home and paste a token (with 'repo' + 'workflow' + 'delete_repo' scopes), then try building again."
 val gh = com.ahamai.app.data.GitHubClient
 // Sweep leftover public temp build repos from prior crashes / failed cleanups — no public traces.
 runCatching { gh.cleanupOrphanTempBuildRepos(token) }
 // Always push the COMPLETE project so the repo is fully buildable (gradlew, settings.gradle,
 // build.gradle, app/, .github/ — everything). Pushing only changed files left repos missing
 // the gradle wrapper/config, which broke the build.
 val all = collectAllFiles(projectDir)
 if (all.isEmpty()) return@withContext "ERROR: project is empty, nothing to build."

 // INSTANT offline pre-flight (no SDK/Gradle): braces, package/path, wrapper, manifest, Compose…
 val preflight = com.ahamai.app.data.StaticVerifier.verify(projectDir, mode = "android")
 if (!preflight.ok) {
 return@withContext "BUILD NOT STARTED — instant static verify found issues that would fail the cloud build. " +
 "Fix them, then build again:\n\n${preflight.format()}"
 }
 // EVERY build gets a FRESH PUBLIC throwaway repo (unlimited free Actions minutes).
 // It is deleted automatically after success OR failure so source never stays on GitHub.
 val repo = gh.createTempBuildRepo(token, projectName.ifBlank { "ahamai-app" })
 ?: return@withContext "ERROR: couldn't create a GitHub repo for the build " +
 "(token may lack 'repo' scope, or it can't create repos). Fix the token, then build again."
 // auto_init created the account's default branch — push and build there.
 val branch = gh.getRepo(token, repo)?.defaultBranch ?: "main"
 java.io.File(projectDir, ".ahamai-repo").writeText("$repo\n$branch")
 java.io.File(projectDir, ".ahamai-build-repo").writeText(repo)
 // Point the connected repo at the build repo so GH_BUILD_STATUS / GH_BUILD_LOGS track it.
 prefs.saveConnectedRepo(repo, branch)
 com.ahamai.app.data.AuthManager.backupGithubToken(context)
 val push = gh.commitFiles(token, repo, branch, all, "Build from AhamAI")
 if (push.startsWith("ERROR")) {
  // Push failed → still wipe the empty/public repo so nothing is left online.
  val del = gh.deleteTempBuildRepo(token, repo)
  prefs.saveConnectedRepo("", "")
  runCatching { java.io.File(projectDir, ".ahamai-build-repo").delete() }
  return@withContext "Push failed: $push\nTemp build repo cleanup: $del"
 }
 val res = gh.startApkBuild(token, repo, branch)
 if (res.startsWith("ERROR")) {
  val del = gh.deleteTempBuildRepo(token, repo)
  prefs.saveConnectedRepo("", "")
  runCatching { java.io.File(projectDir, ".ahamai-build-repo").delete() }
  return@withContext "$res\nTemp build repo cleanup: $del"
 }
 // Repo URL intentionally NOT emphasized — it is temporary and will be deleted after the run.
 "$res\nTemp public build repo will be auto-deleted when the build finishes (success or fail)."
}

/** Polls the latest cloud build until it completes, reporting live progress (step checklist,
 * percent + ETA, and a log tail), instead of freezing the agent loop. Returns the final outcome. */
private suspend fun buildStatusFlow(
 context: android.content.Context,
 onProgress: suspend (text: String, progress: Float, steps: List<com.ahamai.app.data.GitHubClient.BuildStepInfo>, logTail: String) -> Unit
): String {
 val prefs = com.ahamai.app.data.PreferencesManager(context)
 val token = prefs.getGithubToken()
 val repo = prefs.getConnectedRepo()
 if (token.isBlank() || repo.isBlank()) return "ERROR: No GitHub repo connected."
 val gh = com.ahamai.app.data.GitHubClient
 val start = System.currentTimeMillis()
 val timeoutMs = 12 * 60 * 1000L
 fun fmt(s: Long): String = if (s >= 60) "${s / 60}m ${s % 60}s" else "${s}s"
 var pollN = 0
 var lastLog = ""
 while (true) {
 val st = withContext(Dispatchers.IO) { gh.latestBuildState(token, repo) }
 ?: return "No build runs found yet."
 val prog = if (st.runId > 0) withContext(Dispatchers.IO) { gh.fetchBuildProgress(token, repo, st.runId) }
 else com.ahamai.app.data.GitHubClient.BuildProgress(emptyList(), -1)
 val steps = prog.steps
 val elapsedSec = (System.currentTimeMillis() - start) / 1000
 val completed = steps.count { it.conclusion != null }
 val frac = if (steps.isNotEmpty()) completed.toFloat() / steps.size else -1f

if (st.status == "completed") {
  onProgress(fmt(elapsedSec), 1f, steps, lastLog)
  return if (st.conclusion == "success") {
  val saved = withContext(Dispatchers.IO) {
   runCatching { gh.downloadBuildArtifact(context, token, repo, st.runId) }
    .getOrElse { "APK download failed: ${it.message}" }
  }
  // SUCCESS → download APK first, then ALWAYS wipe (even if download failed).
  val del = wipeTempBuildRepo(context, token, repo)
  withContext(Dispatchers.IO) { runCatching { gh.cleanupOrphanTempBuildRepos(token) } }
  "Build SUCCESS. $saved\nTemp build repo removed ($del)."
  } else {
  // FAILURE / cancelled / timed_out on Actions → capture logs, THEN wipe repo.
  val logs = withContext(Dispatchers.IO) { runCatching { gh.buildLogs(token, repo) }.getOrDefault("(no logs)") }
  val del = wipeTempBuildRepo(context, token, repo)
  withContext(Dispatchers.IO) { runCatching { gh.cleanupOrphanTempBuildRepos(token) } }
  "Build ${st.conclusion}.\n--- build errors ---\n$logs\n---\nTemp build repo removed ($del). Fix locally, then build again."
  }
  }

 // Tail the log every other poll (best-effort) so the advanced view stays fresh.
 if (st.status == "in_progress" && prog.jobId > 0 && pollN % 2 == 0) {
 val tail = withContext(Dispatchers.IO) { gh.buildLogTail(token, repo, prog.jobId) }
 if (tail.isNotBlank()) lastLog = tail
 }
 // ETA: project from step-completion rate, else assume a ~210s typical build.
 val etaSec = when {
 frac in 0.05f..0.99f -> ((elapsedSec / frac) - elapsedSec).toLong().coerceIn(5, 600)
 else -> (210 - elapsedSec).coerceAtLeast(5)
 }
 val label = "${fmt(elapsedSec)} · ~${fmt(etaSec)} left"
 onProgress(label, frac, steps, lastLog)
 pollN++
 if (System.currentTimeMillis() - start > timeoutMs) {
  // Timed out waiting: still wipe the public temp repo so source is never left online.
  // User can rebuild; orphan sweep also runs on next build start / app open.
  val logs = withContext(Dispatchers.IO) {
   runCatching { gh.buildLogs(token, repo) }.getOrDefault("(no logs)")
  }
  val del = wipeTempBuildRepo(context, token, repo)
  withContext(Dispatchers.IO) { runCatching { gh.cleanupOrphanTempBuildRepos(token) } }
  return "Build still ${st.status} after ${fmt(elapsedSec)} — stopped waiting.\n" +
   "Temp build repo removed ($del) so the project source is not left on GitHub.\n" +
   "--- last logs ---\n$logs\n---\nCall GH_BUILD_APK again to rebuild."
 }
 kotlinx.coroutines.delay(10_000)
 }
}

/** Collects all non-binary-excluded project files as path -> bytes for an initial push. */
private fun collectAllFiles(projectDir: String): LinkedHashMap<String, ByteArray> {
 val map = LinkedHashMap<String, ByteArray>()
 try {
 for (pf in com.ahamai.app.data.ProjectManager.listFiles(projectDir)) {
 if (pf.isDirectory) continue
 // Skip internal AhamAI metadata files — not part of the project source
 if (pf.relativePath.startsWith(".ahamai")) continue
 try {
 val f = java.io.File(projectDir, pf.relativePath)
 if (f.exists() && f.isFile && f.length() < 5_000_000) map[pf.relativePath] = f.readBytes()
 } catch (_: Exception) {}
 }
 // listFiles skips dot-folders, but we MUST push .github (the build workflow lives there)
 val ghDir = java.io.File(projectDir, ".github")
 if (ghDir.exists()) {
 ghDir.walkTopDown().filter { it.isFile && it.length() < 5_000_000 }.forEach { f ->
 val rel = f.absolutePath.removePrefix(java.io.File(projectDir).absolutePath).removePrefix("/")
 try { map[rel] = f.readBytes() } catch (_: Exception) {}
 }
 }
 } catch (_: Exception) {}
 return map
}

private fun actionIcon(label: String): ImageVector = when {
 // Search/grep/find MUST come before the generic web-page branch below — this used to fall
 // through to Lucide.Globe (a web icon) for plain file search, which was confusing.
 label.startsWith("Search") || label.startsWith("Grep") || label.contains("discover", true) ||
 label.contains("Harvest", true) -> Lucide.Search
 label.contains("page", true) || label.startsWith("Opening") || label.startsWith("Clicking") ||
 label.startsWith("Typing") || label.startsWith("Scrolling") || label.startsWith("Fetch") ||
 label.startsWith("Test") || label.startsWith("Going back") -> Lucide.Globe
 label.contains("scan", true) || label.startsWith("Recon") || label.contains("Vuln") ||
 label.contains("SQLi") || label.contains("XSS") || label.contains("TLS") ||
 label.contains("Port") || label.contains("Nikto") || label.contains("audit", true) ||
 label.contains("secret", true) || label.contains("OSINT", true) -> Lucide.Eye
 label.contains("image", true) || label.contains("diagram", true) || label.contains("chart", true) ||
 label.contains("collage", true) || label.startsWith("Screenshot") -> Lucide.Image
 label.startsWith("Read") -> Lucide.Eye
 label.contains("PDF", true) || label.contains("Word", true) || label.contains("Excel", true) ||
 label.contains("PowerPoint", true) || label.contains("CSV", true) || label.contains("shorts", true) ||
 label.startsWith("Edit") || label.startsWith("Writ") || label.startsWith("Wrote") ||
 label.startsWith("Multi") -> Lucide.Edit
 label.startsWith("Creat") || label.startsWith("Add") -> Lucide.Plus
 label.startsWith("Delet") -> Lucide.X
 label.startsWith("Cloud shell") || label.startsWith("Running") || label.startsWith("Ran") ||
 label.contains("Installing", true) || label.startsWith("Check") -> Lucide.Terminal
 label.startsWith("Download") || label.startsWith("Saving") || label.startsWith("Import") ||
 label.startsWith("Pulling") || label.startsWith("Pushing to cloud") -> Lucide.Download
 label.contains("GitHub", true) || label.startsWith("Pushing") || label.contains("pull request", true) ||
 label.contains("Build", true) -> Lucide.Server
 label.startsWith("List") -> Lucide.Layers
 label.startsWith("Run") || label.startsWith("Started") -> Lucide.Play
 else -> Lucide.Terminal
}

/** Category accent for the tool-use timeline tile. */
private fun actionTint(label: String): Color = when {
 label.contains("scan", true) || label.startsWith("Recon") || label.contains("Vuln") ||
 label.contains("SQLi") || label.contains("XSS") || label.contains("audit", true) ||
 label.contains("secret", true) || label.startsWith("Delet") -> Color(0xFFEF4444) // red
 label.contains("image", true) || label.contains("diagram", true) || label.contains("chart", true) -> Color(0xFFEC4899) // pink
 label.contains("PDF", true) || label.contains("Word", true) || label.contains("Excel", true) ||
 label.contains("PowerPoint", true) || label.contains("CSV", true) || label.contains("shorts", true) -> Color(0xFFF97316) // orange
 label.startsWith("Search") || label.startsWith("Grep") || label.contains("OSINT", true) -> Color(0xFFEAB308) // amber
 label.contains("page", true) || label.startsWith("Opening") || label.startsWith("Clicking") ||
 label.startsWith("Typing") || label.startsWith("Fetch") || label.startsWith("Test") -> Color(0xFF3B82F6) // blue
 label.startsWith("Cloud shell") || label.startsWith("Running") || label.startsWith("Ran") ||
 label.contains("Installing", true) -> Color(0xFF8B5CF6) // violet
 label.contains("GitHub", true) || label.startsWith("Pushing") || label.contains("Build", true) -> Color(0xFF64748B) // slate
 label.startsWith("Creat") || label.startsWith("Writ") || label.startsWith("Edit") || label.startsWith("Read") -> Color(0xFF22C55E) // green
 else -> Color(0xFF6366F1) // indigo
}

/** Buckets a raw CodeAgent.AgentStep.action id into "edited" / "read" / "ran" for the grouped
 * summary chip label (e.g. "Ran 3 commands, edited 2 files, read 1 file"), reusing the same
 * READ_ONLY_ACTIONS / STRUCTURE_ACTIONS partitions the agent loop already relies on. */
private fun actionBucket(actionKind: String): String = when {
 actionKind in STRUCTURE_ACTIONS || actionKind in setOf("edit", "write", "create", "multiedit", "applypatch", "insertlines") -> "edited"
 actionKind in READ_ONLY_ACTIONS -> "read"
 else -> "ran"
}

/** Groups consecutive "action" entries (all the tool calls from a single agent turn) into one
 * batch — List<AgentLogEntry> — so they render as a single collapsible "Ran N commands, edited M
 * files, read K files" row instead of a separate pill per tool call. Everything else (user
 * messages, narration, answers, plans, errors…) passes through unchanged as a bare AgentLogEntry.
 * A run of exactly one action is left ungrouped so it keeps its familiar single-row look. */
private fun groupAgentLog(log: List<AgentLogEntry>): List<Any> {
 val out = ArrayList<Any>(log.size)
 var run = ArrayList<AgentLogEntry>()
 fun flush() {
 if (run.isEmpty()) return
 if (run.size == 1) out.add(run[0]) else out.add(run.toList())
 run = ArrayList()
 }
for (e in log) {
  if (e.type == "action") run.add(e) else if (e.type == "note" && (e.text.contains("Checkpoint") || e.text.startsWith("Big-change mode"))) { /* hide from UI */ } else { flush(); out.add(e) }
  }
 flush()
 return out
}

/** Extract a bare domain from a URL string, or null if not a URL. */
private fun domainOf(text: String): String? {
 val t = text.trim()
 if (!t.startsWith("http://") && !t.startsWith("https://")) return null
 return try {
 val host = java.net.URI(t).host ?: return null
 host.removePrefix("www.").ifBlank { null }
 } catch (_: Exception) {
 null
 }
}

@Composable
private fun AgentLogRow(
 entry: AgentLogEntry,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 projectDir: String = "",
 onOpenFile: (String) -> Unit = {},
 onRecoveryAction: (String) -> Unit = {}
) {
 when (entry.type) {
 "user" -> {
 val clipboardManager = LocalClipboardManager.current
 val context = LocalContext.current
 var copied by remember { mutableStateOf(false) }
 Column(
  modifier = Modifier
   .fillMaxWidth()
   .padding(vertical = 2.dp),
  horizontalAlignment = Alignment.End
 ) {
 // Attachment previews above the message bubble.
 if (entry.attachments.isNotEmpty() && projectDir.isNotBlank()) {
 val isSingleImage = entry.attachments.size == 1 &&
 entry.attachments.all { it.substringAfterLast('.').lowercase() in setOf("png","jpg","jpeg","webp","gif","bmp") }
 if (isSingleImage) {
 entry.attachments.take(1).forEach { rel ->
 AsyncImage(
 model = java.io.File(projectDir, rel),
 contentDescription = "attached image",
 contentScale = ContentScale.Crop,
 modifier = Modifier
 .size(168.dp)
 .clip(RoundedCornerShape(16.dp))
 .clickable { onOpenFile(rel) }
 .padding(bottom = 6.dp)
 )
 }
 } else {
 Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
 entry.attachments.take(3).forEach { rel ->
 val ext = rel.substringAfterLast('.').lowercase()
 if (ext in setOf("png","jpg","jpeg","webp","gif","bmp")) {
 AsyncImage(
 model = java.io.File(projectDir, rel),
 contentDescription = "attached image",
 contentScale = ContentScale.Crop,
 modifier = Modifier
 .size(84.dp)
 .clip(RoundedCornerShape(14.dp))
 .clickable { onOpenFile(rel) }
 )
} else {
  val isZip = ext in setOf("zip", "rar", "7z", "tar", "gz")
  if (isZip) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
  modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (isDark) Color(0xFF2C2C2E) else Color.White).padding(horizontal = 8.dp, vertical = 6.dp)) {
  Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFCA8A04).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
  Icon(com.ahamai.app.ui.icons.AdminIcons.ZipIcon, null, tint = Color(0xFFCA8A04), modifier = Modifier.size(18.dp))
  }
  Text(rel.substringAfterLast('/'), fontSize = 11.sp, color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151), maxLines = 1, overflow = TextOverflow.Ellipsis, fontFamily = JetBrainsMonoFamily)
  }
  } else {
  Box(modifier = Modifier.widthIn(max = 130.dp)) {
  com.ahamai.app.ui.components.FilePreviewCard(projectDir = projectDir, relPath = rel, isDark = isDark, compact = true, showActions = false)
  }
  }
  }
 }
 }
 }
 }
 // Clean iOS Messages–style user bubble (monochrome, no status dots)
 val hasAttachment = entry.attachments.isNotEmpty()
 val bubbleBg = when {
  copied -> if (isDark) Color(0xFF2C2C2E) else Color(0xFFE8E8ED)
  hasAttachment -> Color.Transparent
  isDark -> Color(0xFF2C2C2E)
  else -> Color(0xFFE9E9EB) // iOS gray bubble
 }
 Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
 Surface(
 shape = RoundedCornerShape(18.dp),
 color = bubbleBg,
 border = null,
 tonalElevation = 0.dp,
 shadowElevation = 0.dp,
 modifier = Modifier
  .widthIn(max = 300.dp)
  .pointerInput(entry.text) {
   detectTapGestures(
    onDoubleTap = {
     clipboardManager.setText(AnnotatedString(entry.text))
     copied = true
     android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
    }
   )
  }
 ) {
 Text(
  entry.text,
  modifier = Modifier.padding(
   horizontal = if (hasAttachment && !copied) 2.dp else 14.dp,
   vertical = if (hasAttachment && !copied) 2.dp else 10.dp
  ),
  fontSize = 16.sp,
  lineHeight = 21.sp,
  color = primaryText,
  fontFamily = InterFamily,
  fontWeight = FontWeight.Normal,
  letterSpacing = (-0.2).sp
 )
 }
 }
 }
 LaunchedEffect(copied) {
 if (copied) { kotlinx.coroutines.delay(1200); copied = false }
 }
 }
 // Only render actions that produced a RESULT (a collapsible tool output worth keeping in the
 // timeline). In-progress / side-effect actions are surfaced live by the pixel indicator at
 // the bottom, so we no longer duplicate them as rows here — this keeps the loop uncluttered.
"action" -> {
  // Clean tool row: minimal icon (no bg), verb inline, path hidden behind expand
  val (pathContent, time) = run {
  val p = entry.path.trim()
  if (p.contains(" · ")) {
  val parts = p.split(" · ", limit = 2)
  parts[0].trim() to (parts.getOrNull(1) ?: "").trim()
  } else if (p.matches(Regex(".*(ms|s|m)$")) || p.contains("timeout") || p.contains("/")) {
  "" to p
  } else {
  p.ifBlank { entry.text } to ""
  }
  }
  var expanded by remember(entry.id) { mutableStateOf(false) }
  val isTimeout = entry.text.contains("Timed out", true) ||
   time.contains("TIMEOUT", true) ||
   entry.result.contains("[TIMEOUT]", true)
  val isErr = entry.result.startsWith("ERROR") || isTimeout
  val isLiveTool = entry.live && entry.startedAtMs > 0L
  val timeColor = when {
   isTimeout -> Color(0xFFFF9F0A)
   isLiveTool -> Color(0xFF0A84FF)
   isErr -> cDiffRed
   else -> muted.copy(alpha = 0.85f)
  }
  val verb = entry.text.ifBlank { "Run" }.replaceFirstChar { it.uppercase() }
  val target = pathContent.ifBlank { "" }
  val canExpand = entry.result.isNotBlank() || entry.diff != null || target.isNotBlank()
  val toolIcon = actionIcon(verb)
  val iconTint = when {
   isErr -> cDiffRed
   isLiveTool -> Color(0xFF0A84FF)
   else -> if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)
  }

  Column(
  modifier = Modifier
  .fillMaxWidth()
  .padding(vertical = 1.dp)
  ) {
  Row(
  verticalAlignment = Alignment.CenterVertically,
  modifier = Modifier
  .fillMaxWidth()
  .clip(RoundedCornerShape(10.dp))
  .clickable(enabled = canExpand) { expanded = !expanded }
  .padding(horizontal = 2.dp, vertical = 6.dp)
  ) {
  // Minimal icon — no background
  Icon(toolIcon, null, tint = iconTint, modifier = Modifier.size(16.dp))
  Spacer(Modifier.width(8.dp))
  Text(
   verb,
   fontSize = 13.sp,
   fontFamily = InterFamily,
   fontWeight = FontWeight.Medium,
   color = if (isErr) cDiffRed else primaryText,
   maxLines = 1,
   letterSpacing = (-0.15).sp,
   modifier = Modifier.weight(1f)
  )
  entry.diff?.let { d ->
  if (d.added > 0) Text(
  "+${d.added}",
  fontSize = 11.sp,
  fontFamily = JetBrainsMonoFamily,
  color = cDiffGreen,
  fontWeight = FontWeight.SemiBold
  )
  if (d.removed > 0) {
  Spacer(Modifier.width(4.dp))
  Text(
  "−${d.removed}",
  fontSize = 11.sp,
  fontFamily = JetBrainsMonoFamily,
  color = cDiffRed,
  fontWeight = FontWeight.SemiBold
  )
  }
  }
  if (time.isNotBlank()) {
  Spacer(Modifier.width(6.dp))
  Text(
  time,
  fontSize = 11.sp,
  color = timeColor,
  fontFamily = InterFamily,
  maxLines = 1,
  overflow = TextOverflow.Ellipsis
  )
  }
  if (canExpand) {
  Spacer(Modifier.width(4.dp))
  Icon(
  if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronRight,
  null,
  tint = muted.copy(alpha = 0.4f),
  modifier = Modifier.size(14.dp)
  )
  }
  }
  if (expanded) {
  // File path shown only when expanded
  if (target.isNotBlank() && target != "…") {
   Spacer(Modifier.height(2.dp))
   Text(
    target,
    fontSize = 12.sp,
    fontFamily = JetBrainsMonoFamily,
    color = muted,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.padding(start = 26.dp, bottom = 4.dp)
   )
  }
 entry.diff?.let { d ->
 if (d.lines.isNotEmpty()) InlineCodeDiffBlock(d, isDark)
 }
 if (entry.result.isNotBlank() && (entry.diff == null || entry.diff!!.lines.isEmpty() || isErr)) {
 Spacer(Modifier.height(4.dp))
 Surface(
 shape = RoundedCornerShape(12.dp),
 color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF7F7F8),
 border = null,
 modifier = Modifier.fillMaxWidth()
 ) {
 androidx.compose.foundation.text.selection.SelectionContainer {
 Text(
 entry.result.take(2500),
 fontSize = 11.sp,
 fontFamily = JetBrainsMonoFamily,
 color = if (isErr) Color(0xFFFF6B6B) else muted,
 modifier = Modifier.padding(12.dp),
 lineHeight = 16.sp
 )
 }
 }
 }
 }
 }
 }
 "answer" -> {
 Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp)) {
 MarkdownText(
  text = entry.text,
  color = primaryText,
  projectDir = projectDir.ifBlank { null },
  onOpenFile = onOpenFile,
  fontSize = 15.sp,
  lineHeight = 23.sp
 )
 }
 }
"narration" -> {
  val thoughtInk = if (isDark) Color(0xFFAEAEB2) else Color(0xFF6C6C70)
  Column(
  modifier = Modifier
  .fillMaxWidth()
  .padding(vertical = 4.dp, horizontal = 2.dp)
  ) {
  Row(verticalAlignment = Alignment.CenterVertically) {
  Box(
   modifier = Modifier
    .size(5.dp)
    .clip(CircleShape)
    .background(if (entry.live) Color(0xFF0A84FF) else muted.copy(alpha = 0.35f))
  )
  Spacer(Modifier.width(6.dp))
  Text(
  if (entry.live) "Thinking…" else "Thought",
  fontSize = 11.sp,
  fontWeight = FontWeight.Medium,
  color = muted,
  fontFamily = InterFamily,
  letterSpacing = (-0.1).sp
  )
  }
  Spacer(Modifier.height(4.dp))
  MarkdownText(
   text = entry.text,
   color = thoughtInk,
   isStreaming = entry.live,
   projectDir = projectDir.ifBlank { null },
   onOpenFile = onOpenFile,
   fontSize = 13.sp,
   lineHeight = 20.sp,
   modifier = Modifier.padding(start = 11.dp)
  )
  }
  }
 "plan" -> {
 val items = entry.plan ?: emptyList()
 val doneCount = items.count { it.done }
 Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
 Text(
 "Plan ($doneCount/${items.size})",
 fontSize = 12.sp,
 fontWeight = FontWeight.SemiBold,
 color = muted,
 letterSpacing = (-0.1).sp
 )
 Spacer(Modifier.height(6.dp))
 items.forEach { item ->
 Row(
 verticalAlignment = Alignment.Top,
 modifier = Modifier.padding(vertical = 3.dp)
 ) {
 Icon(
 if (item.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
 null,
 tint = if (item.done) Color(0xFF34C759) else muted.copy(alpha = 0.6f),
 modifier = Modifier.size(16.dp).padding(top = 1.dp)
 )
 Spacer(Modifier.width(8.dp))
 Text(
 item.text,
 fontSize = 14.sp,
 color = if (item.done) muted else primaryText,
 fontFamily = InterFamily,
 lineHeight = 20.sp,
 textDecoration = if (item.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
 )
 }
 }
 }
 }
 "note" -> {
 val isTimeoutRetry = entry.text.startsWith("Tool timeout") ||
  entry.text.startsWith("Timeout ·") ||
  entry.text.contains("budget", ignoreCase = true) && entry.text.contains("adapt", ignoreCase = true)
 val isProgressPing = entry.text.startsWith("Ping ") ||
  entry.text.contains("still working", ignoreCase = true)
 val transient = entry.text.startsWith("Thinking") || entry.text.startsWith("Reviewing") ||
 entry.text.startsWith("Verifying") || entry.text.startsWith("Connection/model hiccup") ||
 entry.text.startsWith("Loaded project rules")
 if (isTimeoutRetry) {
 // Soft amber line — agent continues; no blocking recovery popup
 Text(
 entry.text,
 fontSize = 11.sp,
 color = Color(0xFFFF9F0A),
 fontFamily = InterFamily,
 lineHeight = 15.sp,
 modifier = Modifier.padding(vertical = 2.dp)
 )
 } else if (isProgressPing) {
 // Soft blue 30s heartbeat — long work is healthy, not stuck
 Text(
 entry.text,
 fontSize = 11.sp,
 color = Color(0xFF0A84FF).copy(alpha = 0.9f),
 fontFamily = InterFamily,
 lineHeight = 15.sp,
 modifier = Modifier.padding(vertical = 2.dp)
 )
 } else if (!transient) {
 Text(
 entry.text,
 fontSize = 11.sp,
 color = muted.copy(alpha = 0.75f),
 fontFamily = InterFamily,
 lineHeight = 15.sp
 )
 }
 }
 "preview" -> {
 // Live preview card — web app / diagram / chart rendered inline in the chat.
 if (entry.previewKind == "webapp" && projectDir.isNotBlank()) {
 com.ahamai.app.ui.components.PreviewCard(
 kind = "webapp",
 payload = entry.previewPayload,
 projectDir = projectDir
 )
 } else if (entry.previewKind == "diagram" && projectDir.isNotBlank()) {
 // Diagram rendered via PreviewCard with Mermaid.js WebView (reliable, renders locally).
 // This replaces the old approach that downloaded a PNG (which was often blank/slow).
 com.ahamai.app.ui.components.PreviewCard(
 kind = "diagram",
 payload = entry.previewPayload,
 projectDir = projectDir
 )
 } else if (entry.previewKind == "image" && projectDir.isNotBlank()) {
 // Rendered diagram / image — shown inline exactly like message content: just the
 // image, no card chrome or coloured background. A small save button lets the user
 // download it straight to their gallery/Downloads.
 val ctx0 = LocalContext.current
 val scope0 = rememberCoroutineScope()
 val imgFile = remember(entry.previewPayload) { java.io.File(projectDir, entry.previewPayload) }
 Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
 AsyncImage(
 model = coil.request.ImageRequest.Builder(ctx0)
 .data(imgFile)
 .memoryCacheKey("${entry.previewPayload}:${imgFile.lastModified()}")
 .diskCacheKey("${entry.previewPayload}:${imgFile.lastModified()}")
 .crossfade(true)
 .build(),
 contentDescription = "Diagram",
 contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
 modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
 )
 // Save-to-device button overlay (top-right).
 Box(
 modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
 .size(32.dp).clip(CircleShape)
 .background(Color(0x99000000))
 .clickable {
 scope0.launch {
 val r = withContext(Dispatchers.IO) {
 runCatching {
 com.ahamai.app.data.DeviceStorage.saveBytesToDownloads(
 ctx0, imgFile.readBytes(), imgFile.name, "image/png"
 )
 }.getOrElse { "ERROR: ${it.message}" }
 }
 android.widget.Toast.makeText(
 ctx0,
 if (r.startsWith("ERROR")) "Save failed" else "Saved to Downloads",
 android.widget.Toast.LENGTH_SHORT
 ).show()
 }
 },
 contentAlignment = Alignment.Center
 ) {
 Icon(AdminIcons.BootstrapSave, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(16.dp))
 }
 }
 } else if (entry.previewKind == "chart" && projectDir.isNotBlank()) {
 com.ahamai.app.ui.components.PreviewCard(
 kind = "chart",
 payload = entry.previewPayload,
 projectDir = projectDir
 )
 } else if (entry.previewKind == "browser" && projectDir.isNotBlank()) {
 // Live agentic-browser view: a minimal browser chrome bar (URL) + the screenshot.
 val ctx0 = LocalContext.current
 val shot = remember(entry.previewPayload) { java.io.File(projectDir, entry.previewPayload) }
          Surface(
              shape = RoundedCornerShape(14.dp),
              color = if (isDark) cDarkRow else cLightRow,
              border = null,
              modifier = Modifier.fillMaxWidth()
          ) {
 Column {
 // Chrome bar: real site favicon + the URL pill (no dots).
 Row(
 modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 val faviconDomain = remember(entry.text) { domainOf(entry.text) }
 if (faviconDomain != null) {
 AsyncImage(
 model = "https://www.google.com/s2/favicons?domain=$faviconDomain&sz=64",
 contentDescription = null,
 modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp))
 )
 } else {
 Icon(Icons.Outlined.Language, null, tint = muted, modifier = Modifier.size(13.dp))
 }
 Spacer(Modifier.width(8.dp))
 Box(
 modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
.background(if (isDark) cDarkBg else cLightBg)
  .padding(horizontal = 10.dp, vertical = 5.dp)
  ) {
  Text(
 entry.text.ifBlank { "browser" },
 fontSize = 11.sp, fontFamily = JetBrainsMonoFamily,
 color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis
 )
 }
 }
 AsyncImage(
 // Unique cache key per file + its mtime so a NEW screenshot never
 // visually replaces earlier preview cards (fixes coil cache collision).
 model = coil.request.ImageRequest.Builder(ctx0)
 .data(shot)
 .memoryCacheKey("${entry.previewPayload}:${shot.lastModified()}")
 .diskCacheKey("${entry.previewPayload}:${shot.lastModified()}")
 .crossfade(true)
 .build(),
 contentDescription = "Browser view",
 contentScale = androidx.compose.ui.layout.ContentScale.FillWidth,
 modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)
 .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
 )
 }
 }
 }
 }
 "filechip" -> {
 // Manus-style deliverable: visual preview (if any) + full-width colored file card.
 // Tap opens in-app viewer with Save to Downloads / Open externally.
 if (projectDir.isNotBlank() && entry.path.isNotBlank()) {
 Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
 com.ahamai.app.ui.components.FilePreviewCard(
 projectDir = projectDir,
 relPath = entry.path,
 isDark = isDark
 )
 }
 }
 }
 // Step-progress lines are surfaced live by the pixel indicator and tracked in the Plan card,
 // so we no longer repeat them as separate rows in the loop (declutter + no duplication).
 "planstep" -> {}
 "buildprogress" -> {
 val steps = entry.buildSteps ?: emptyList()
 val done = entry.result == "done"
 val failed = steps.any { it.conclusion == "failure" }
 // Greenish accent for building, red for failed, green for success — matches pixel animation colors
 val accent = when { failed -> Color(0xFFFF3B30); done -> Color(0xFF34C759); else -> Color(0xFF0A84FF) }
 val darkAccent = when { failed -> Color(0x99FF3B30); done -> Color(0x9934C759); else -> Color(0x660A84FF) }
 // Pixel-style build animation — a 5x5 grid inspired by the agent screen pixel animation
 Column(
 modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
 horizontalAlignment = Alignment.CenterHorizontally
 ) {
 Box(contentAlignment = Alignment.Center, modifier = Modifier.size(130.dp)) {
 if (done || failed) {
 // Static final state: checkmark or X made of pixels
 BuildPixelResult(failed = failed, accent = accent, darkAccent = darkAccent)
 } else {
 // Animated converging pixels (like the CONVERGE mode but larger and greenish)
 BuildPixelAnimation(progress = entry.buildProgress, accent = accent)
 }
 }
 Spacer(Modifier.height(16.dp))
 Text(
 when { done && failed -> "Build failed"; done -> "Build complete"; else -> "Building your APK" },
 fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
 color = if (failed) accent else primaryText, fontFamily = InterFamily
 )
 if (!done && entry.buildProgress in 0f..1f) {
 Spacer(Modifier.height(4.dp))
 Text("${(entry.buildProgress * 100).toInt()}%", fontSize = 12.sp, color = muted, fontFamily = InterFamily)
 }
 }
 }
 "error" -> {
 Surface(shape = RoundedCornerShape(10.dp), color = if (isDark) Color(0xFF2B1313) else Color(0xFFFEE2E2), modifier = Modifier.fillMaxWidth()) {
 Text(entry.text, modifier = Modifier.padding(11.dp), fontSize = 12.sp, color = Color(0xFFEF4444))
 }
 }
 "recovery" -> {
 RecoveryActionCard(
 entry = entry,
 isDark = isDark,
 muted = muted,
 primaryText = primaryText,
 onAction = onRecoveryAction
 )
 }
 "diff" -> {} // inline diff card removed — per-file changes now shown in the StatusBar
 "changesummary" -> {} // stats now in footer
 }
}

/**
 * Interactive recovery card for tool timeout / anti-repeat stop / ASK_USER.
 * CTAs: continue with a new approach, undo last edit, re-open questions.
 */
@Composable
private fun RecoveryActionCard(
 entry: AgentLogEntry,
 isDark: Boolean,
 muted: Color,
 primaryText: Color,
 onAction: (String) -> Unit
) {
 val kind = entry.actionKind
 val accent = when (kind) {
 "timeout" -> Color(0xFFFF9F0A)
 "loop_stop" -> Color(0xFFEF4444)
 "askuser" -> Color(0xFF007AFF)
 else -> Color(0xFFEF4444)
 }
 // askuser: flat system card (no tinted “alert” look). Others keep soft status surfaces.
 val bg = when (kind) {
 "timeout" -> if (isDark) Color(0xFF2A2110) else Color(0xFFFFF6E8)
 "loop_stop" -> if (isDark) Color(0xFF2B1313) else Color(0xFFFEE2E2)
 "askuser" -> if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
 else -> if (isDark) Color(0xFF2B1313) else Color(0xFFFEE2E2)
 }
 val borderColor = when (kind) {
 "askuser" -> if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.06f)
 else -> Color.Transparent
 }
 val title = when (kind) {
 "timeout" -> "Tool timed out"
 "loop_stop" -> "Agent stuck (repeated action)"
 "askuser" -> if (entry.live) "Questions for you" else entry.text.ifBlank { "Questions answered" }
 else -> entry.text
 }
 val subtitle = entry.path.ifBlank {
 when (kind) {
 "timeout" -> "The tool stopped responding"
 "loop_stop" -> "Same tool call too many times"
 "askuser" -> if (entry.live) "Tap Answer to continue" else "You can open answers again"
 else -> ""
 }
 }
 val detail = entry.result.take(280)

      Surface(
          shape = RoundedCornerShape(14.dp),
          color = bg,
          border = if (borderColor != Color.Transparent) BorderStroke(0.5.dp, borderColor) else null,
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
      ) {
 Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
 // No status dots — clean iOS title row
 Text(
 title,
 fontSize = 14.sp,
 fontWeight = FontWeight.SemiBold,
 color = primaryText,
 fontFamily = InterFamily
 )
 if (subtitle.isNotBlank()) {
 Spacer(Modifier.height(2.dp))
 Text(
 subtitle,
 fontSize = 12.sp,
 color = muted,
 fontFamily = InterFamily,
 maxLines = 2,
 overflow = TextOverflow.Ellipsis
 )
 }
 if (detail.isNotBlank() && kind != "askuser") {
 Spacer(Modifier.height(8.dp))
 Text(
 detail,
 fontSize = 11.sp,
 color = muted,
 fontFamily = InterFamily,
 lineHeight = 15.sp,
 maxLines = 4,
 overflow = TextOverflow.Ellipsis
 )
 }
 Spacer(Modifier.height(10.dp))
 Row(
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 modifier = Modifier.fillMaxWidth()
 ) {
 when (kind) {
 "timeout" -> {
 RecoveryChip("Continue differently", accent) { onAction("continue_timeout") }
 RecoveryChip("Rewind turn", primaryText, outlined = true, isDark = isDark) { onAction("undo") }
 }
 "loop_stop" -> {
 RecoveryChip("Continue differently", accent) { onAction("continue_loop") }
 RecoveryChip("Rewind turn", primaryText, outlined = true, isDark = isDark) { onAction("undo") }
 }
 "askuser" -> {
 if (entry.live || entry.result.isNotBlank()) {
 RecoveryChip(
 if (entry.live) "Answer" else "Review",
 accent
 ) { onAction("answer_again") }
 }
 }
 else -> {
 RecoveryChip("Continue", accent) { onAction("continue_loop") }
 }
 }
 }
 }
 }
}

@Composable
private fun RecoveryChip(
 label: String,
 accent: Color,
 outlined: Boolean = false,
 isDark: Boolean = true,
 onClick: () -> Unit
) {
 val bg = if (outlined) {
 if (isDark) Color(0xFF1C1C1F) else Color(0xFFF4F4F4)
 } else accent.copy(alpha = if (isDark) 0.18f else 0.12f)
 val border = if (outlined) {
 if (isDark) Color(0xFF2C2C2E) else Color(0xFFD1D1D6)
 } else accent.copy(alpha = 0.35f)
 Box(
 modifier = Modifier
 .clip(RoundedCornerShape(10.dp))
 .background(bg)
 .border(0.75.dp, border, RoundedCornerShape(10.dp))
 .clickable(onClick = onClick)
 .padding(horizontal = 12.dp, vertical = 8.dp)
 ) {
 Text(
 label,
 fontSize = 12.sp,
 fontWeight = FontWeight.SemiBold,
 color = accent,
 fontFamily = InterFamily
 )
 }
}

/** Collapsed summary — clean compact row with no icon background. */
@Composable
private fun ActionGroupRow(entries: List<AgentLogEntry>, isDark: Boolean, muted: Color, primaryText: Color) {
 var expanded by remember(entries.first().id) { mutableStateOf(false) }
 val ran = entries.count { actionBucket(it.actionKind) == "ran" }
 val edited = entries.count { actionBucket(it.actionKind) == "edited" }
 val read = entries.count { actionBucket(it.actionKind) == "read" }
 val totalAdded = entries.sumOf { it.diff?.added ?: 0 }
 val totalRemoved = entries.sumOf { it.diff?.removed ?: 0 }
 val hasError = entries.any { it.result.startsWith("ERROR") }
 val parts = buildList {
 if (ran > 0) add(if (ran == 1) "1 command" else "$ran commands")
 if (edited > 0) add(if (edited == 1) "1 edit" else "$edited edits")
 if (read > 0) add(if (read == 1) "1 read" else "$read reads")
 }
 val summary = parts.joinToString(" · ").ifBlank { "${entries.size} steps" }

 Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 modifier = Modifier
 .fillMaxWidth()
 .clickable { expanded = !expanded }
 .padding(vertical = 6.dp)
) {
  // Minimal icon — no background
  Icon(
   if (hasError) Lucide.AlertTriangle else Lucide.Layers,
   null,
   tint = if (hasError) cDiffRed else muted,
   modifier = Modifier.size(14.dp)
  )
  Spacer(Modifier.width(8.dp))
  Text(
  summary,
 fontSize = 13.sp,
 fontFamily = InterFamily,
 fontWeight = FontWeight.Medium,
 color = if (hasError) cDiffRed else muted,
 maxLines = 1,
 overflow = TextOverflow.Ellipsis,
 letterSpacing = (-0.1).sp,
 modifier = Modifier.weight(1f, fill = false)
 )
 if (totalAdded > 0 || totalRemoved > 0) {
 Spacer(Modifier.width(8.dp))
 if (totalAdded > 0) Text(
 "+$totalAdded",
 fontSize = 11.sp,
 fontFamily = JetBrainsMonoFamily,
 color = cDiffGreen,
 fontWeight = FontWeight.SemiBold
 )
 if (totalRemoved > 0) {
 Spacer(Modifier.width(5.dp))
 Text(
 "−$totalRemoved",
 fontSize = 11.sp,
 fontFamily = JetBrainsMonoFamily,
 color = cDiffRed,
 fontWeight = FontWeight.SemiBold
 )
 }
 }
 Spacer(Modifier.width(6.dp))
 Icon(
 if (expanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.ChevronRight,
 null,
 tint = muted.copy(alpha = 0.55f),
 modifier = Modifier.size(16.dp)
 )
 }
 if (expanded) {
 Spacer(Modifier.height(6.dp))
 Column(
 modifier = Modifier
 .fillMaxWidth()
 .clip(RoundedCornerShape(14.dp))
 .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFF7F7F8))
 .padding(vertical = 4.dp)
 ) {
 entries.forEach { e -> ActionGroupItemRow(e, isDark, muted, primaryText) }
 }
 }
 }
}

/**
 * Expanded step row — Grok TUI style:
 * ◆ Edit path.kt  +N −M
 * then green/red line diff when available (app palette status greens/reds).
 */
@Composable
private fun ActionGroupItemRow(entry: AgentLogEntry, isDark: Boolean, muted: Color, primaryText: Color) {
 var itemExpanded by remember(entry.id) { mutableStateOf(false) }
 val bucket = actionBucket(entry.actionKind)
 val verb = when (bucket) {
  "edited" -> if (entry.text.startsWith("Edit")) entry.text else "Editing"
  "read" -> if (entry.text.startsWith("Read")) entry.text else "Reading"
  else -> entry.text.ifBlank { "Run" }.replaceFirstChar { it.uppercase() }
 }
 val isErr = entry.result.startsWith("ERROR")
 val label = entry.path.substringBefore(" · ").ifBlank {
  if (bucket in setOf("read", "edited")) "" else entry.text
 }
 val toolIcon = actionIcon(verb)
 val iconTint = if (isErr) cDiffRed else if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)
Column(modifier = Modifier.fillMaxWidth()) {
  Row(
   verticalAlignment = Alignment.CenterVertically,
   modifier = Modifier
   .fillMaxWidth()
   .clickable(enabled = entry.result.isNotBlank() || entry.diff != null) {
   itemExpanded = !itemExpanded
   }
   .padding(horizontal = 12.dp, vertical = 9.dp)
  ) {
  // Clean icon — no background box
  Icon(toolIcon, null, tint = iconTint, modifier = Modifier.size(16.dp))
 Spacer(Modifier.width(10.dp))
 Column(modifier = Modifier.weight(1f)) {
 Text(
  verb,
  fontSize = 13.sp,
  fontFamily = InterFamily,
  fontWeight = FontWeight.SemiBold,
  color = if (isErr) Color(0xFFFF3B30) else primaryText,
  maxLines = 1,
  letterSpacing = (-0.15).sp
 )
 if (label.isNotBlank()) {
  Text(
   label,
   fontSize = 12.sp,
   fontFamily = JetBrainsMonoFamily,
   color = muted,
   maxLines = 1,
   overflow = TextOverflow.Ellipsis
  )
 }
 }
 entry.diff?.let { d ->
 if (d.added > 0) Text(
 "+${d.added}",
 fontSize = 11.sp,
 fontFamily = JetBrainsMonoFamily,
 color = Color(0xFF34C759),
 fontWeight = FontWeight.SemiBold
 )
 if (d.removed > 0) {
 Spacer(Modifier.width(4.dp))
 Text(
 "−${d.removed}",
 fontSize = 11.sp,
 fontFamily = JetBrainsMonoFamily,
 color = Color(0xFFFF3B30),
 fontWeight = FontWeight.SemiBold
 )
 }
 }
 }
 if (itemExpanded) {
 entry.diff?.let { d ->
 if (d.lines.isNotEmpty()) {
 InlineCodeDiffBlock(d, isDark)
 }
 }
 if (entry.result.isNotBlank() && (entry.diff == null || entry.diff!!.lines.isEmpty() || isErr)) {
 androidx.compose.foundation.text.selection.SelectionContainer {
 Text(
 entry.result.take(2500),
 fontSize = 11.sp,
 fontFamily = JetBrainsMonoFamily,
 color = if (isErr) Color(0xFFFF6B6B) else muted,
 modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 10.dp),
 lineHeight = 16.sp
 )
 }
 }
 }
 }
}

/** Green/red line preview — mirrors Grok TUI process view; uses status greens/reds on dark card. */
@Composable
private fun InlineCodeDiffBlock(diff: DiffUtil.DiffResult, isDark: Boolean) {
 val bg = if (isDark) Color(0xFF0A0A0C) else Color(0xFFFFFFFF)
 val addBg = if (isDark) Color(0xFF0F2A18) else Color(0xFFE8F8EE)
 val delBg = if (isDark) Color(0xFF2A1214) else Color(0xFFFCE8EA)
 val addFg = if (isDark) Color(0xFF86EFAC) else Color(0xFF166534)
 val delFg = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B)
 val ctxFg = if (isDark) Color(0xFF9CA3AF) else Color(0xFF52525B)
 Column(
 modifier = Modifier
 .fillMaxWidth()
 .padding(start = 14.dp, end = 10.dp, bottom = 8.dp)
 .clip(RoundedCornerShape(8.dp))
 .background(bg)
 .border(
 1.dp,
 if (isDark) Color(0xFF222226) else Color(0xFFE5E5EA),
 RoundedCornerShape(8.dp)
 )
 .padding(vertical = 4.dp)
 ) {
 diff.lines.take(40).forEach { line ->
 val (rowBg, rowFg, prefix) = when (line.type) {
 '+' -> Triple(addBg, addFg, "+")
 '-' -> Triple(delBg, delFg, "−")
 else -> Triple(Color.Transparent, ctxFg, " ")
 }
 Text(
 "$prefix ${line.text}".take(200),
 fontSize = 10.sp,
 fontFamily = JetBrainsMonoFamily,
 color = rowFg,
 lineHeight = 14.sp,
 modifier = Modifier
 .fillMaxWidth()
 .background(rowBg)
 .padding(horizontal = 8.dp, vertical = 1.dp)
 )
 }
 if (diff.lines.size > 40) {
 Text(
 "… ${diff.lines.size - 40} more lines",
 fontSize = 9.sp,
 color = ctxFg,
 fontFamily = InterFamily,
 modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
 )
 }
 }
}

/**
 * Pixel-style build animation — a 5×5 grid of dots converging toward the center like the
 * CONVERGE mode in the ThinkingIndicator, but larger and green-tinted for build progress.
 */
@Composable
private fun BuildPixelAnimation(progress: Float, accent: Color) {
 val transition = rememberInfiniteTransition(label = "buildPixels")
 val cursor by transition.animateFloat(
 initialValue = 0f, targetValue = 16f,
 animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
 label = "cursor"
 )
 val grid = 5
 val spacing = 6.dp
 val dotSize = 5.dp
 Column(verticalArrangement = Arrangement.spacedBy(spacing), horizontalAlignment = Alignment.CenterHorizontally) {
 for (r in 0 until grid) {
 Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
 for (c in 0 until grid) {
 // Distance from center (2,2)
 val dr = r - 2f; val dc = c - 2f
 val distFromCenter = kotlin.math.sqrt(dr * dr + dc * dc) / 2.828f // normalize 0..1
 // Pulsing brightness per dot
 val idx = (r * grid + c).toFloat()
 val raw = kotlin.math.abs((cursor - idx + 16f) % 16f)
 val brightness = (1f - raw / 8f).coerceIn(0.15f, 1f) * (1f - distFromCenter * 0.3f)
 Box(
 modifier = Modifier.size(dotSize)
 .clip(RoundedCornerShape(2.dp))
 .background(accent.copy(alpha = brightness.coerceIn(0.1f, 0.9f)))
 )
 }
 }
 }
 }
 // Progress indicator below the grid
 if (progress >= 0f) {
 Spacer(Modifier.height(10.dp))
 Box(modifier = Modifier.width(100.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.15f))) {
 Box(modifier = Modifier.fillMaxWidth(fraction = progress.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(accent.copy(alpha = 0.7f)))
 }
 }
}

/**
 * Static pixel result for build success (checkmark) or failure (X), shown after the build finishes.
 */
@Composable
private fun BuildPixelResult(failed: Boolean, accent: Color, darkAccent: Color) {
 val grid = 5
 val spacing = 5.dp
 val dotSize = 5.dp
 // Checkmark pattern for success, X pattern for failure
 val pattern = if (failed) {
 // X pattern on 5x5
 listOf(
 listOf(true, false, false, false, true),
 listOf(false, true, false, true, false),
 listOf(false, false, true, false, false),
 listOf(false, true, false, true, false),
 listOf(true, false, false, false, true)
 )
 } else {
 // Checkmark pattern on 5x5
 listOf(
 listOf(false, false, false, false, false),
 listOf(false, false, false, false, true),
 listOf(false, false, false, true, false),
 listOf(false, false, true, false, false),
 listOf(true, true, false, false, false)
 )
 }
 Column(verticalArrangement = Arrangement.spacedBy(spacing), horizontalAlignment = Alignment.CenterHorizontally) {
 for (r in 0 until grid) {
 Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
 for (c in 0 until grid) {
 val on = pattern[r][c]
 Box(
 modifier = Modifier.size(dotSize)
 .clip(RoundedCornerShape(2.dp))
 .background(if (on) accent else accent.copy(alpha = 0.08f))
 )
 }
 }
 }
 }
}

// ── App-themed "pixel" working indicator ─────────────────────────────────────
// A small SQUARE made of a 3×3 grid of pixels. The animation pattern AND the label both adapt
// to what the agent is actually doing right now — reading, writing, scanning, building,
// downloading, etc. — so the user always knows what's happening at a glance.

/**
 * Visual pattern the 3×3 pixel grid is currently drawing. Picked from the latest action verb
 * (see [deriveActivity]). Each mode is a different sweep / pulse / flow over the same 9 dots,
 * so the indicator stays compact but visibly changes behaviour as the agent moves through phases.
 */
private enum class PixelMode {
 WAVE, // diagonal band sweep — default "thinking" / composing
 SWEEP_H, // left-to-right column sweep — reading, listing, browsing, fetching
 FILL_V, // top-to-bottom row fill — writing, editing, creating, formatting
 PULSE, // centre-out radial pulse — running, executing, planning
 RADAR, // circular radar sweep around the centre — security scans, recon
 CONVERGE, // dots converge to centre then explode — building / compiling
 FLOW_DOWN, // downward vertical flow — downloading, pulling, unzipping
 FLOW_UP // upward vertical flow — pushing, uploading, zipping
}

/**
 * Map the latest agent-log entry to a (mode, label) pair shown beside the pixel square.
 *
 * - empty log → WAVE, "Thinking…"
 * - last entry is an "action" → mode derived from the action verb (Reading/Writing/…)
 * label = "<verb> <path>" (e.g. "Reading MainActivity.kt")
 * - "plan" → PULSE, "Planning approach…"
 * - "planstep" → PULSE, the step text (already formatted "Step X/Y · …")
 * - "buildprogress" → CONVERGE, "Building APK (cloud)…"
 * - "preview" → SWEEP_H, "Preparing preview…"
 * - "note" → WAVE, the note text (capped)
 * - "answer" → WAVE, "Composing response…"
 * - anything else → WAVE, "Working…"
 */
private fun deriveActivity(log: List<AgentLogEntry>): Pair<PixelMode, String> {
 if (log.isEmpty()) return PixelMode.WAVE to "Thinking…"
 // Live narration = model still streaming → "Responding…" (Grok TUI parity)
 val liveNarr = log.lastOrNull { it.type == "narration" && it.live }
 if (liveNarr != null) return PixelMode.WAVE to "Responding…"
 // Prefer latest substantive activity (not a trailing note)
 val last = log.lastOrNull {
 it.type == "action" || it.type == "plan" || it.type == "planstep" ||
 it.type == "buildprogress" || it.type == "preview" || it.type == "answer" ||
 it.type == "narration"
 } ?: log.last()
 return when (last.type) {
 "plan" -> PixelMode.PULSE to "Planning approach…"
 "planstep" -> PixelMode.PULSE to last.text.take(64)
 "buildprogress" -> PixelMode.CONVERGE to "Building APK…"
 "preview" -> PixelMode.SWEEP_H to "Preparing preview…"
 "answer" -> PixelMode.WAVE to "Composing response…"
 "narration" -> PixelMode.WAVE to "Thinking…"
 "note" -> {
 val t = last.text.removePrefix("Stop — ").removePrefix("⏹ ").trim().take(64)
 PixelMode.WAVE to (if (t.isBlank()) "Working…" else t)
 }
 "action" -> {
 val verb = last.text
 val mode = when {
 // Build / compile → converge
 verb.startsWith("Building") || verb.startsWith("Rebuilding") ||
 verb.startsWith("Adding Android template") -> PixelMode.CONVERGE

 // Security scans → radar
 verb.startsWith("Scanning") || verb.startsWith("Recon") ||
 verb.startsWith("Port scan") || verb.startsWith("Fast port scan") ||
 verb.startsWith("Vuln") || verb.startsWith("Fuzzing") ||
 verb.startsWith("Nikto") || verb.startsWith("SQLi") ||
 verb.startsWith("TLS") || verb.startsWith("XSS") ||
 verb.startsWith("Cmd") || verb.startsWith("CRLF") ||
 verb.startsWith("Param") || verb.startsWith("Content discovery") ||
 verb.startsWith("WordPress") || verb.startsWith("Static analysis") ||
 verb.startsWith("Deep secret") || verb.endsWith("OSINT") -> PixelMode.RADAR

 // Download / import → flow down
 verb.startsWith("Downloading") || verb.startsWith("Saving to Downloads") ||
 verb.startsWith("Pulling") || verb.startsWith("Unzipping") ||
 verb.startsWith("Importing") -> PixelMode.FLOW_DOWN

 // Upload / push / zip → flow up
 verb.startsWith("Pushing") || verb.startsWith("Zipping") ||
 verb.startsWith("Opening pull request") || verb.startsWith("Creating issue") -> PixelMode.FLOW_UP

 // Write / edit / create → vertical fill
 verb.startsWith("Writing") || verb.startsWith("Creating") ||
 verb.startsWith("Editing") || verb.startsWith("Multi-editing") ||
 verb.startsWith("Adding") || verb.startsWith("Renaming") ||
 verb.startsWith("Formatting") || verb.startsWith("Linting") ||
 verb.startsWith("Saving") || verb.startsWith("Loading") ||
 verb.startsWith("Bulk") || verb.startsWith("Generating") ||
 verb.startsWith("Decompiling") || verb.startsWith("Rebuilding") -> PixelMode.FILL_V

 // Run / execute / cloud → centre pulse
 verb.startsWith("Running") || verb.startsWith("Cloud") ||
 verb.startsWith("Testing") || verb.startsWith("Started background") ||
 verb.startsWith("Checking") || verb.startsWith("Delegating") ||
 verb.startsWith("Searched") || verb.startsWith("Read APK") -> PixelMode.PULSE

 // Read / search / browse / list / fetch / preview / render → horizontal sweep
 verb.startsWith("Reading") || verb.startsWith("Listing") ||
 verb.startsWith("Grep") || verb.startsWith("Searching") ||
 verb.startsWith("Fetching") || verb.startsWith("Codebase") ||
 verb.startsWith("Symbol") || verb.startsWith("Opening") ||
 verb.startsWith("Clicking") || verb.startsWith("Typing") ||
 verb.startsWith("Pressing") || verb.startsWith("Scrolling") ||
 verb.startsWith("Going back") || verb.startsWith("Looking") ||
 verb.startsWith("Viewing") || verb.startsWith("Previewing") ||
 verb.startsWith("Rendering") || verb.startsWith("Screenshot") ||
 verb.startsWith("Cloning") || verb.startsWith("Showing") ||
 verb.startsWith("Read ") -> PixelMode.SWEEP_H

 else -> PixelMode.WAVE
 }
 // Compose label: verb + path (truncated). Path often holds the file/target.
 val path = last.path.trim()
 val full = if (path.isNotBlank()) "$verb $path" else verb
 mode to full.take(72)
 }
 else -> PixelMode.WAVE to "Working…"
 }
}

@Composable
private fun ThinkingIndicator(
 isDark: Boolean,
 muted: Color,
 mode: PixelMode = PixelMode.WAVE,
 label: String = "",
 elapsedSec: Float = 0f
) {
 val transition = rememberInfiniteTransition(label = "pixelsquare")
 // Single shared cursor — each mode interprets it differently so we keep ONE animation
 // driver (saves a slot table and keeps the cadence consistent across mode switches).
 val cursor by transition.animateFloat(
 initialValue = 0f, targetValue = 9f,
 animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
 label = "cursor"
 )
 // Match the chat screen's monochrome pixel-thinking colour (neutral gray, no brand accent).
 val pixelColor = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
 val grid = 3

 // Per-cell alpha (0.18..1) for the current mode + cursor. Computed once per frame.
 fun cellAlpha(r: Int, c: Int): Float {
 val sqrt2 = kotlin.math.sqrt(2f)
 return when (mode) {
 PixelMode.WAVE -> {
 // Diagonal band sweep (0..4 bands) — the classic "thinking" shimmer.
 val band = (r + c).toFloat()
 val raw = kotlin.math.abs((cursor * 5f / 9f - band + 5f) % 5f)
 val dist = if (raw > 2.5f) 5f - raw else raw
 (1f - dist / 2.2f).coerceIn(0.18f, 1f)
 }
 PixelMode.SWEEP_H -> {
 // Column cursor moves left → right; neighbours fade smoothly.
 val colCursor = (cursor * 4f / 9f) % 4f // 0..4
 val raw = kotlin.math.abs(colCursor - c.toFloat())
 val dist = if (raw > 2f) 4f - raw else raw
 (1f - dist / 1.6f).coerceIn(0.18f, 1f)
 }
 PixelMode.FILL_V -> {
 // 9-dot index, top-to-bottom; dots already "written" stay bright, current fades in.
 val idx = (r * 3 + c).toFloat()
 when {
 idx < cursor -> 1f
 idx < cursor + 1f -> 0.45f + 0.55f * (cursor - idx)
 else -> 0.18f
 }
 }
 PixelMode.PULSE -> {
 // Centre (1,1) lights first, then cross (1,0)(0,1)(2,1)(1,2), then corners.
 val cheby = kotlin.math.max(kotlin.math.abs(r - 1), kotlin.math.abs(c - 1))
 val ringCursor = (cursor * 3f / 9f) % 3f // 0..3
 val raw = kotlin.math.abs(ringCursor - cheby.toFloat())
 val dist = if (raw > 1.5f) 3f - raw else raw
 (1f - dist / 1.3f).coerceIn(0.18f, 1f)
 }
 PixelMode.RADAR -> {
 // 8 perimeter dots sweep clockwise around the centre; centre stays steady.
 if (r == 1 && c == 1) return 0.55f
 val cyclePos = when {
 r == 0 && c == 0 -> 0
 r == 0 && c == 1 -> 1
 r == 0 && c == 2 -> 2
 r == 1 && c == 2 -> 3
 r == 2 && c == 2 -> 4
 r == 2 && c == 1 -> 5
 r == 2 && c == 0 -> 6
 r == 1 && c == 0 -> 7
 else -> 0
 }
 val radarCursor = (cursor * 8f / 9f) % 8f
 val raw = kotlin.math.abs(radarCursor - cyclePos.toFloat())
 val dist = if (raw > 4f) 8f - raw else raw
 (1f - dist / 2.2f).coerceIn(0.18f, 1f)
 }
 PixelMode.CONVERGE -> {
 // Bright ring shrinks to the centre, then explodes back out (0..2 → 0..2).
 val dotR = kotlin.math.sqrt((r - 1f) * (r - 1f) + (c - 1f) * (c - 1f)) / sqrt2 // 0..1
 val convCursor = (cursor * 2f / 9f) % 2f // 0..2 (V-shape)
 val targetR = if (convCursor <= 1f) 1f - convCursor else convCursor - 1f
 val diff = kotlin.math.abs(dotR - targetR)
 (1f - diff / 0.7f).coerceIn(0.18f, 1f)
 }
 PixelMode.FLOW_DOWN -> {
 // Bright row moves top → bottom, with a fading trail above it.
 val rowCursor = (cursor * 3f / 9f) % 3f // 0..3
 val raw = ((rowCursor - r.toFloat()) + 3f) % 3f
 (1f - raw / 1.8f).coerceIn(0.18f, 1f)
 }
 PixelMode.FLOW_UP -> {
 // Bright row moves bottom → top.
 val rowCursor = (cursor * 3f / 9f) % 3f
 val raw = ((rowCursor + r.toFloat()) + 3f) % 3f
 (1f - raw / 1.8f).coerceIn(0.18f, 1f)
 }
 }
 }

 Row(
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.Start,
 modifier = Modifier
 .fillMaxWidth()
 .padding(vertical = 2.dp)
 ) {
 // ── Pixel grid ──
 Box(
 modifier = Modifier.size(16.dp),
 contentAlignment = Alignment.Center
 ) {
 Column(verticalArrangement = Arrangement.spacedBy(1.6.dp)) {
 for (r in 0 until grid) {
 Row(horizontalArrangement = Arrangement.spacedBy(1.6.dp)) {
 for (c in 0 until grid) {
 val a = cellAlpha(r, c)
 Box(
 modifier = Modifier.size(3.8.dp)
 .clip(RoundedCornerShape(1.dp))
 .background(pixelColor.copy(alpha = a * 0.75f))
 )
 }
 }
 }
}
}
  }
 }

 // ── Claude Code / Codex CLI-style session status bar ─────────────────────────
@Composable
private fun StatusBar(isDark: Boolean, muted: Color, primaryText: Color,
 filesChanged: Int, linesAdded: Int, linesRemoved: Int,
 fileDiffs: Map<String, DiffUtil.DiffResult> = emptyMap()) {
 Surface(
 modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
 shape = RoundedCornerShape(8.dp),
 color = Color.Transparent
 ) {
 Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
 Row(
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.spacedBy(10.dp)
 ) {
 Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, null, tint = muted.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
 Text("$filesChanged files", fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily, color = muted.copy(alpha = 0.7f))
 Text("+$linesAdded", fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFF34C759).copy(alpha = 0.8f))
 Text("−$linesRemoved", fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFFFF3B30).copy(alpha = 0.8f))
 }
 // Per-file diff breakdown (Claude Code / Codex style)
 if (fileDiffs.isNotEmpty()) {
 Spacer(Modifier.height(4.dp))
 fileDiffs.entries.take(5).forEach { (path, diff) ->
 Row(
 verticalAlignment = Alignment.CenterVertically,
 horizontalArrangement = Arrangement.spacedBy(8.dp),
 modifier = Modifier.padding(start = 14.dp)
 ) {
 Text(
 text = path.substringAfterLast('/').take(18),
 fontSize = 9.sp, fontFamily = JetBrainsMonoFamily,
 color = muted.copy(alpha = 0.6f),
 maxLines = 1, overflow = TextOverflow.Ellipsis
 )
 Text("+${diff.added} −${diff.removed}",
 fontSize = 9.sp, fontFamily = JetBrainsMonoFamily,
 color = muted.copy(alpha = 0.6f)
 )
 }
 }
 if (fileDiffs.size > 5) {
 Text("… and ${fileDiffs.size - 5} more files",
 fontSize = 9.sp, fontFamily = JetBrainsMonoFamily,
 color = muted.copy(alpha = 0.5f),
 modifier = Modifier.padding(start = 14.dp)
 )
 }
 }
 }
 }
}


// ── ASK_USER — iOS Settings-style clarification sheet (no status dots / chip clutter) ──

/**
 * Parses ASK_USER JSON and shows a clean bottom sheet:
 * large title, inset grouped option lists (check), free-text fields, Continue / Skip.
 *
 * JSON: [{"header":"…","question":"…","options":[{"label":"…","description":"…"}],"type":"single|multi"},…]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AskUserSheet(
 questionsJson: String,
 isDark: Boolean,
 onSubmit: (String) -> Unit,
 onDismiss: () -> Unit
) {
 val ink = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000)
 val muted = Color(0xFF8E8E93)
 // Monochrome CTAs — no system blue on agent sheets
 val link = ink
 val groupBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
 val sheetBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)
 val sep = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f)
 val fieldBg = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

 val questions = remember(questionsJson) {
 fun parseObj(obj: org.json.JSONObject): AskQuestion {
 val options = obj.optJSONArray("options")?.let { oa ->
 (0 until oa.length()).map { j ->
 val o = oa.optJSONObject(j) ?: return@map ("" to "")
 o.optString("label", o.optString("text", "")) to
 o.optString("description", o.optString("desc", ""))
 }.filter { it.first.isNotBlank() }
 } ?: emptyList()
 return AskQuestion(
 header = obj.optString("header", obj.optString("title", "")),
 question = obj.optString("question", obj.optString("prompt", obj.optString("text", ""))),
 options = options,
 isMulti = obj.optString("type", "single").contains("multi", ignoreCase = true)
 )
 }
 try {
 val trimmed = questionsJson.trim()
 when {
 trimmed.startsWith("[") -> {
 val arr = JSONArray(trimmed)
 (0 until arr.length()).map { i -> parseObj(arr.getJSONObject(i)) }
 }
 trimmed.startsWith("{") -> {
 val root = JSONObject(trimmed)
 val arr = root.optJSONArray("questions") ?: root.optJSONArray("items")
 if (arr != null) (0 until arr.length()).map { i -> parseObj(arr.getJSONObject(i)) }
 else listOf(parseObj(root))
 }
 else -> listOf(AskQuestion("", questionsJson, emptyList(), false))
 }
 } catch (_: Exception) {
 listOf(AskQuestion("", questionsJson, emptyList(), false))
 }
 }

 var selectionsMap by remember { mutableStateOf(mapOf<Int, Set<String>>()) }
 val freeTexts = remember {
 mutableStateListOf<String>().apply { questions.forEach { add("") } }
 }
 val answeredCount = remember(selectionsMap, freeTexts.toList()) {
 questions.indices.count { qi ->
 val sel = selectionsMap[qi]
 (!sel.isNullOrEmpty()) || freeTexts.getOrElse(qi) { "" }.isNotBlank()
 }
 }
 val hasAnyAnswer = answeredCount > 0

 fun buildAnswers(allowEmpty: Boolean): String {
 val sb = StringBuilder()
 questions.forEachIndexed { qi, q ->
 val header = q.header.ifBlank { "Q${qi + 1}" }
 val sel = selectionsMap[qi]
 val free = freeTexts.getOrElse(qi) { "" }
 val answer = when {
 sel != null && sel.isNotEmpty() -> sel.joinToString(", ")
 free.isNotBlank() -> free
 allowEmpty -> "(skipped)"
 else -> "(no answer)"
 }
 sb.append("$header: $answer\n")
 }
 return sb.toString().trim()
 }

 ModalBottomSheet(
 onDismissRequest = onDismiss,
 containerColor = sheetBg,
 tonalElevation = 0.dp,
 shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
 dragHandle = {
 Box(
 Modifier
 .fillMaxWidth()
 .padding(top = 10.dp, bottom = 4.dp),
 contentAlignment = Alignment.Center
 ) {
 Box(
 Modifier
 .width(36.dp)
 .height(5.dp)
 .clip(RoundedCornerShape(2.5.dp))
 .background(muted.copy(alpha = 0.35f))
 )
 }
 }
 ) {
 Column(
 Modifier
 .fillMaxWidth()
 .navigationBarsPadding()
 .padding(bottom = 20.dp)
 .heightIn(max = 560.dp)
 .verticalScroll(rememberScrollState())
 ) {
 // Large title — no dots / badges
 Text(
 "Questions",
 fontSize = 28.sp,
 fontWeight = FontWeight.Bold,
 color = ink,
 fontFamily = InterFamily,
 letterSpacing = (-0.5).sp,
 modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
 )
 Text(
 if (questions.size <= 1) "Choose an option to continue"
 else "$answeredCount of ${questions.size} answered",
 fontSize = 13.sp,
 color = muted,
 fontFamily = InterFamily,
 modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)
 )

 questions.forEachIndexed { qi, q ->
 // Section label
 if (q.header.isNotBlank()) {
 Text(
 q.header.uppercase(),
 fontSize = 12.sp,
 fontWeight = FontWeight.SemiBold,
 color = muted,
 letterSpacing = 0.4.sp,
 fontFamily = InterFamily,
 modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
 )
 }
 Text(
 q.question.ifBlank { "Your preference?" },
 fontSize = 15.sp,
 fontWeight = FontWeight.SemiBold,
 color = ink,
 fontFamily = InterFamily,
 lineHeight = 20.sp,
 modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp)
 )
 if (q.isMulti && q.options.isNotEmpty()) {
 Text(
 "Select one or more",
 fontSize = 12.sp,
 color = muted,
 fontFamily = InterFamily,
 modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 6.dp)
 )
 }

 if (q.options.isNotEmpty()) {
 // iOS inset grouped list — no flow chips
 Column(
 Modifier
 .fillMaxWidth()
 .padding(horizontal = 16.dp)
 .clip(RoundedCornerShape(12.dp))
 .background(groupBg)
 ) {
 q.options.forEachIndexed { oi, (label, desc) ->
 val currentSel = selectionsMap[qi] ?: emptySet()
 val selected = label in currentSel
 Row(
 Modifier
 .fillMaxWidth()
 .clickable {
 val cur = selectionsMap[qi] ?: emptySet()
 val updated = if (q.isMulti) {
 if (label in cur) cur - label else cur + label
 } else setOf(label)
 selectionsMap = selectionsMap + (qi to updated)
 }
 .padding(horizontal = 16.dp, vertical = 13.dp),
 verticalAlignment = Alignment.CenterVertically
 ) {
 Column(Modifier.weight(1f)) {
 Text(
 label,
 fontSize = 16.sp,
 fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
 color = ink,
 fontFamily = InterFamily
 )
 if (desc.isNotBlank()) {
 Spacer(Modifier.height(2.dp))
 Text(
 desc,
 fontSize = 13.sp,
 color = muted,
 fontFamily = InterFamily,
 maxLines = 2,
 overflow = TextOverflow.Ellipsis
 )
 }
 }
 if (selected) {
 Text(
 "✓",
 color = link,
 fontSize = 17.sp,
 fontWeight = FontWeight.SemiBold
 )
 }
 }
 if (oi < q.options.lastIndex) {
 HorizontalDivider(
 thickness = 0.5.dp,
 color = sep,
 modifier = Modifier.padding(start = 16.dp)
 )
 }
 }
 }
 } else {
 // Free text — quiet field
 Box(
 Modifier
 .fillMaxWidth()
 .padding(horizontal = 16.dp)
 .clip(RoundedCornerShape(12.dp))
 .background(fieldBg)
 .padding(horizontal = 14.dp, vertical = 12.dp)
 ) {
 BasicTextField(
 value = freeTexts.getOrElse(qi) { "" },
 onValueChange = { if (qi < freeTexts.size) freeTexts[qi] = it },
 modifier = Modifier.fillMaxWidth(),
 textStyle = LocalTextStyle.current.copy(
 fontSize = 16.sp,
 color = ink,
 fontFamily = InterFamily,
 lineHeight = 22.sp
 ),
 cursorBrush = SolidColor(link),
 decorationBox = { inner ->
 Box {
 if (freeTexts.getOrElse(qi) { "" }.isEmpty()) {
 Text(
 "Type your answer…",
 fontSize = 16.sp,
 color = muted,
 fontFamily = InterFamily
 )
 }
 inner()
 }
 }
 )
 }
 }

 Spacer(Modifier.height(18.dp))
 }

 // Actions — monochrome CTAs (black/white, not blue)
 val ctaBg = if (isDark) Color(0xFFECECEC) else Color(0xFF1A1A1A)
 val ctaFg = if (isDark) Color(0xFF1A1A1A) else Color.White
 Column(Modifier.padding(horizontal = 16.dp)) {
 Box(
 Modifier
 .fillMaxWidth()
 .height(50.dp)
 .clip(RoundedCornerShape(12.dp))
 .background(if (hasAnyAnswer) ctaBg else ctaBg.copy(alpha = 0.35f))
 .clickable(enabled = hasAnyAnswer) {
 onSubmit(buildAnswers(allowEmpty = false))
 },
 contentAlignment = Alignment.Center
 ) {
 Text(
 "Continue",
 fontSize = 17.sp,
 fontWeight = FontWeight.SemiBold,
 color = ctaFg,
 fontFamily = InterFamily
 )
 }
 Spacer(Modifier.height(4.dp))
 Box(
 Modifier
 .fillMaxWidth()
 .height(44.dp)
 .clip(RoundedCornerShape(12.dp))
 .clickable {
 onSubmit("User skipped the questions. Use your best judgment and proceed.")
 },
 contentAlignment = Alignment.Center
 ) {
 Text(
 "Skip",
 fontSize = 16.sp,
 fontWeight = FontWeight.Medium,
 color = muted,
 fontFamily = InterFamily
 )
 }
 }
 }
 }
}

/** Data class for a single parsed ASK_USER question. */
private data class AskQuestion(
 val header: String,
 val question: String,
 val options: List<Pair<String, String>>, // label to description
 val isMulti: Boolean
)

/** Collect browser / computer-use screenshots under the project for Artifacts strip. */
private fun listBrowserScreenshots(projectDir: String): List<String> {
 if (projectDir.isBlank()) return emptyList()
 val roots = listOf(
  java.io.File(projectDir, "browser"),
  java.io.File(projectDir, "screenshots"),
  java.io.File(projectDir, ".ahamai/screenshots")
 )
 val exts = setOf("png", "jpg", "jpeg", "webp")
 return roots
  .filter { it.isDirectory }
  .flatMap { dir ->
   dir.walkTopDown().maxDepth(3)
    .filter { it.isFile && it.extension.lowercase() in exts }
    .toList()
  }
  .sortedByDescending { it.lastModified() }
  .take(12)
  .map { it.absolutePath }
}

// ── BTW web-search helpers (SEARCH / READ only — no sources UI) ──────────────

private val BTW_DIRECTIVE_REGEX =
 Regex("(?im)^[ \\t>*-]*(SEARCH|READ)\\s*:\\s*(.+)$")

/** Map model tool names → SEARCH or READ (BTW only supports these two). */
private fun btwToolType(name: String): String? {
 val n = name.lowercase().replace('-', '_').replace(' ', '_')
 return when {
  n.contains("image") || n.contains("img") -> null // no image search in BTW
  n.contains("read") || n.contains("fetch") || n.contains("browse") ||
   n.contains("visit") || n.contains("url") || n.contains("scrape") ||
   n == "open" -> "READ"
  n.contains("search") || n == "web_search" || n == "websearch" || n == "google" -> "SEARCH"
  else -> null
 }
}

/**
 * Extract SEARCH/READ tool calls for the BTW side panel.
 * Accepts SEARCH:/READ: lines, <tool_call> XML, and [ToolCallParser] variants.
 */
private fun btwExtractSearchCalls(text: String): List<Pair<String, String>> {
 val calls = mutableListOf<Pair<String, String>>()

 for (m in BTW_DIRECTIVE_REGEX.findAll(text)) {
  val type = m.groupValues[1].uppercase()
  val arg = m.groupValues[2].trim().trimEnd(']').trim().trim('"', '\'', '`', '<', '>')
  if (arg.isNotBlank() && !arg.equals("none", ignoreCase = true)) {
   calls.add(type to arg)
  }
 }

 if (calls.isEmpty()) {
  for (c in com.ahamai.app.data.ToolCallParser.extract(text)) {
   val type = btwToolType(c.name) ?: continue
   val arg = c.named["query"]
    ?: c.named["url"]
    ?: c.named["q"]
    ?: c.args.firstOrNull()?.trim().orEmpty()
   if (arg.isNotBlank()) calls.add(type to arg)
  }
 }

 return calls.distinct()
}

/** Strip tool-call syntax so the BTW panel never shows raw SEARCH:/XML. */
private fun btwStripToolSyntax(text: String): String {
 var t = text
 t = Regex("<tool[_ ]?call>[\\s\\S]*?</tool[_ ]?call>", RegexOption.IGNORE_CASE).replace(t, "")
 t = Regex("<tool[_ ]?call>[\\s\\S]*$", RegexOption.IGNORE_CASE).replace(t, "")
 t = BTW_DIRECTIVE_REGEX.replace(t, "")
 t = Regex(
  "</?(tool_call|toolcall|tool_response|arg_key|arg_value|function_call|parameter)[^>]*>",
  RegexOption.IGNORE_CASE
 ).replace(t, "")
 t = Regex("<[a-zA-Z_/]*$").replace(t, "")
 t = com.ahamai.app.data.ToolCallParser.stripAll(t)
 t = Regex("\\n{3,}").replace(t, "\n\n")
 return t.trim()
}
