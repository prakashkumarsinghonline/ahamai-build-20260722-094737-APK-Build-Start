package com.ahamai.app.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.AgentTaskStore
import com.ahamai.app.data.ChatHistoryManager
import com.ahamai.app.data.ProjectManager
import com.ahamai.app.ui.components.IosDialog
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.InterFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Full-page Data controls (not a bottom sheet) — chat wipe, workspace import/export,
 * delete all. Matches Manus / profile elevated-card style.
 */
@Composable
fun DataControlsScreen(
    onBack: () -> Unit,
    onClearChats: () -> Unit = {}
) {
    BackHandler { onBack() }
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val cell = if (isDark) Color(0xFF202024) else Color(0xFFFFFFFF)
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val secondary = if (isDark) Color(0xFF9A9AA2) else Color(0xFF6B6B6B)
    val sep = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0F0F2)
    val red = Color(0xFFFF3B30)

    var workspaceInfos by remember { mutableStateOf<List<ProjectManager.ProjectInfo>>(emptyList()) }
    var chatCount by remember { mutableStateOf(0) }
    var refresh by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showClearChats by remember { mutableStateOf(false) }
    var showClearAll by remember { mutableStateOf(false) }
    var showWorkspaces by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        workspaceInfos = withContext(Dispatchers.IO) { ProjectManager.listProjects(context) }
        chatCount = withContext(Dispatchers.IO) { ChatHistoryManager.loadSessions(context).size }
    }

    val totalFiles = remember(workspaceInfos) { workspaceInfos.sumOf { it.fileCount } }

    val importWs = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = "Importing…"
            val dir = withContext(Dispatchers.IO) { ProjectManager.importWorkspaceHistory(context, uri) }
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
            refresh++
            busy = null
            val n = ProjectManager.lastImportCount
            toast = when {
                dir == null -> "Import failed"
                n > 1 -> "Imported $n workspaces"
                else -> "Imported · offline"
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bg)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(48.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(cell)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Lucide.ArrowLeft, "Back", tint = primary, modifier = Modifier.size(18.dp))
            }
            Text(
                "Data controls",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                "Offline on this device · export before uninstall",
                fontSize = 13.sp,
                fontFamily = InterFamily,
                color = secondary,
                modifier = Modifier.padding(bottom = 14.dp, start = 4.dp)
            )

            // Main actions card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(cell)
            ) {
                DataRow(
                    icon = { Icon(Lucide.Layers, null, tint = primary, modifier = Modifier.size(20.dp)) },
                    title = "Chat History",
                    trailing = if (chatCount > 0) "$chatCount chats" else "Empty",
                    primary = primary,
                    secondary = secondary,
                    sep = sep,
                    showDivider = true,
                    onClick = { showClearChats = true }
                )
                DataRow(
                    icon = { Icon(Lucide.Folder, null, tint = primary, modifier = Modifier.size(20.dp)) },
                    title = "Workspaces",
                    trailing = if (workspaceInfos.isEmpty()) "None"
                    else "${workspaceInfos.size} · $totalFiles files",
                    primary = primary,
                    secondary = secondary,
                    sep = sep,
                    showDivider = true,
                    onClick = { showWorkspaces = true }
                )
                DataRow(
                    icon = { Icon(Lucide.Trash2, null, tint = red, modifier = Modifier.size(20.dp)) },
                    title = "Delete All Data",
                    trailing = null,
                    primary = red,
                    secondary = secondary,
                    sep = sep,
                    showDivider = false,
                    showChevron = false,
                    onClick = { showClearAll = true }
                )
            }

            Spacer(Modifier.height(14.dp))

            // Import / Export all
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CuteAction(
                    label = "Import",
                    icon = Lucide.Download,
                    cell = cell,
                    primary = primary,
                    modifier = Modifier.weight(1f)
                ) { importWs.launch("application/zip") }
                CuteAction(
                    label = "Export all",
                    icon = Lucide.Layers,
                    cell = cell,
                    primary = if (workspaceInfos.isEmpty()) secondary else primary,
                    enabled = workspaceInfos.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch {
                        busy = "Exporting all…"
                        val result = withContext(Dispatchers.IO) {
                            ProjectManager.exportAllWorkspaceHistory(context)
                        }
                        busy = null
                        toast = if (result.startsWith("OK")) "Saved to Downloads/AhamAI"
                        else result
                    }
                }
            }

            busy?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, fontSize = 12.sp, fontFamily = InterFamily, color = secondary)
            }
            toast?.let { msg ->
                LaunchedEffect(msg) {
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                    toast = null
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Workspace list overlay ──
    if (showWorkspaces) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .height(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(cell)
                        .clickable { showWorkspaces = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Lucide.ArrowLeft, "Back", tint = primary, modifier = Modifier.size(18.dp))
                }
                Text(
                    "Workspaces",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = primary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (workspaceInfos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No workspaces yet", fontSize = 14.sp, fontFamily = InterFamily, color = secondary)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(workspaceInfos, key = { it.path }) { info ->
                        var exporting by remember { mutableStateOf(false) }
                        var deleting by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(cell)
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
                                Icon(Lucide.Folder, null, tint = primary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    info.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFamily,
                                    color = primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${info.fileCount} files · ${ProjectManager.formatWorkspaceAge(info.lastModified)}",
                                    fontSize = 11.sp,
                                    fontFamily = InterFamily,
                                    color = secondary
                                )
                            }
                            if (exporting || deleting) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = secondary)
                            } else {
                                TextButton(onClick = {
                                    scope.launch {
                                        exporting = true
                                        val r = withContext(Dispatchers.IO) {
                                            ProjectManager.exportWorkspaceHistory(context, info.path)
                                        }
                                        exporting = false
                                        toast = if (r.startsWith("OK")) "Exported" else r
                                    }
                                }) {
                                    Text("Export", fontSize = 12.sp, fontFamily = InterFamily, color = primary)
                                }
                                TextButton(onClick = {
                                    scope.launch {
                                        deleting = true
                                        withContext(Dispatchers.IO) {
                                            ProjectManager.deleteProject(info.path)
                                            AgentTaskStore.remove(context, info.path)
                                        }
                                        refresh++
                                        deleting = false
                                    }
                                }) {
                                    Text("Delete", fontSize = 12.sp, fontFamily = InterFamily, color = red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    } // Box

    if (showClearChats) {
        IosDialog(isDark = isDark, onDismissRequest = { showClearChats = false }) {
            Text("Delete Chat History", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Permanently delete all $chatCount chat sessions?",
                fontSize = 13.sp, fontFamily = InterFamily, color = secondary
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showClearChats = false }) {
                    Text("Cancel", fontFamily = InterFamily, color = secondary)
                }
                TextButton(onClick = {
                    onClearChats()
                    ChatHistoryManager.clearAll(context)
                    chatCount = 0
                    showClearChats = false
                    refresh++
                }) {
                    Text("Delete", fontFamily = InterFamily, color = red, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showClearAll) {
        IosDialog(isDark = isDark, onDismissRequest = { showClearAll = false }) {
            Text("Delete All Data", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = primary)
            Spacer(Modifier.height(8.dp))
            Text(
                "This deletes all chats, workspaces, and agent sessions on this device. Export first if you need a backup.",
                fontSize = 13.sp, fontFamily = InterFamily, color = secondary, lineHeight = 18.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showClearAll = false }) {
                    Text("Cancel", fontFamily = InterFamily, color = secondary)
                }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            ChatHistoryManager.clearAll(context)
                            ProjectManager.listProjects(context).forEach {
                                ProjectManager.deleteProject(it.path)
                                AgentTaskStore.remove(context, it.path)
                            }
                            val uid = com.ahamai.app.data.AuthManager.uid()?.takeIf { it.isNotBlank() } ?: "guest"
                            File(context.filesDir, "agent_sessions_$uid").takeIf { it.exists() }?.deleteRecursively()
                        }
                        showClearAll = false
                        refresh++
                        toast = "All data deleted"
                    }
                }) {
                    Text("Delete All", fontFamily = InterFamily, color = red, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DataRow(
    icon: @Composable () -> Unit,
    title: String,
    trailing: String?,
    primary: Color,
    secondary: Color,
    sep: Color,
    showDivider: Boolean,
    showChevron: Boolean = true,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            Spacer(Modifier.width(14.dp))
            Text(title, fontSize = 16.sp, fontFamily = InterFamily, color = primary, modifier = Modifier.weight(1f))
            if (!trailing.isNullOrBlank()) {
                Text(trailing, fontSize = 13.sp, fontFamily = InterFamily, color = secondary)
                Spacer(Modifier.width(4.dp))
            }
            if (showChevron) {
                Icon(Lucide.ChevronRight, null, tint = secondary.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
        if (showDivider) {
            Box(Modifier.fillMaxWidth().padding(start = 50.dp).height(0.5.dp).background(sep))
        }
    }
}

@Composable
private fun CuteAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    cell: Color,
    primary: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cell)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = primary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily, color = primary)
    }
}
