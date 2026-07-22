package com.ahamai.app.ui.components

import com.ahamai.app.ui.agent.rememberAgentHaptics
import com.ahamai.app.ui.agent.agentPressable
import com.ahamai.app.ui.chat.HapticOnPress

import com.ahamai.app.ui.theme.UnicaOneRegular
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import com.ahamai.app.data.AgentTaskStore
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import java.io.File

// App palette (matches ChatPalette / agent chrome)
private val Accent = Color(0xFF0A84FF)
private val GreenOk = Color(0xFF34C759)
private val RedDiff = Color(0xFFFF3B30)
private val AmberNeed = Color(0xFFFF9F0A)
private val BlueWork = Color(0xFF0A84FF)

fun AgentTaskStore.Status.dotColor(): Color = when (this) {
    AgentTaskStore.Status.WORKING -> BlueWork
    AgentTaskStore.Status.NEEDS_YOU -> AmberNeed
    AgentTaskStore.Status.FINISHED -> GreenOk
    AgentTaskStore.Status.READY_MERGE -> Accent
    AgentTaskStore.Status.MERGED -> Color(0xFF8E8E93)
    AgentTaskStore.Status.IDLE -> Color(0xFF8E8E93)
    AgentTaskStore.Status.FAILED -> RedDiff
}

@Composable
fun MissionSectionLabel(text: String, muted: Color, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = InterFamily,
        color = muted.copy(alpha = 0.75f),
        letterSpacing = 0.6.sp
    )
}

/**
 * Cursor-style workspace / task row — compact, fluid press scale.
 */
@Composable
fun MissionTaskRow(
    task: AgentTaskStore.Task,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onExport: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    ageLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAgentHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    HapticOnPress(interaction, haptics)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 480f, dampingRatio = 0.62f),
        label = "taskRowScale"
    )
    val cardBg = if (isDark) Color(0xFF141414).copy(alpha = 0.72f) else Color.White.copy(alpha = 0.82f)
    val borderC = if (isDark) Color(0xFF1F1F1F).copy(alpha = 0.7f) else Color(0xFFE5E5EA).copy(alpha = 0.85f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = { haptics.tick(); onClick() }),
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = BorderStroke(0.5.dp, borderC),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Soft folder chip
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isDark) Color(0xFF1C1C20) else Color(0xFFF0F0F5)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(task.status.dotColor())
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp)
                )
                Icon(
                    Icons.Outlined.AccountTree,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val repo = task.repoLabel.ifBlank { task.projectName }.take(18)
                    Text(
                        repo,
                        fontSize = 11.sp,
                        fontFamily = JetBrainsMonoFamily,
                        color = muted,
                        maxLines = 1
                    )
                    if (task.fileCount > 0 || task.added > 0 || task.removed > 0) {
                        Text("  ·  ", fontSize = 10.sp, color = muted.copy(alpha = 0.5f))
                        if (task.added > 0) {
                            Text("+${task.added}", fontSize = 10.sp, color = GreenOk, fontFamily = JetBrainsMonoFamily)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (task.removed > 0) {
                            Text("−${task.removed}", fontSize = 10.sp, color = RedDiff, fontFamily = JetBrainsMonoFamily)
                            Spacer(Modifier.width(4.dp))
                        }
                        if (task.fileCount > 0) {
                            Text(
                                "${task.fileCount} file${if (task.fileCount == 1) "" else "s"}",
                                fontSize = 10.sp,
                                color = muted,
                                fontFamily = JetBrainsMonoFamily
                            )
                        }
                    } else {
                        Text("  ·  ${task.status.label}", fontSize = 10.sp, color = muted, fontFamily = InterFamily)
                    }
                    if (!ageLabel.isNullOrBlank()) {
                        Text("  ·  $ageLabel", fontSize = 10.sp, color = muted.copy(alpha = 0.75f), fontFamily = InterFamily)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            if (onExport != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onExport),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.FileDownload,
                        contentDescription = "Export",
                        tint = muted.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            if (onDelete != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "Delete",
                        tint = muted.copy(alpha = 0.55f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePin),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.PushPin,
                    contentDescription = if (task.pinned) "Unpin" else "Pin",
                    tint = if (task.pinned) Accent else muted.copy(alpha = 0.45f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Top status banner on agent run — Working / Finished / Ready to merge.
 */
@Composable
fun AgentStatusBanner(
    isRunning: Boolean,
    needsYou: Boolean,
    title: String,
    added: Int,
    removed: Int,
    fileCount: Int,
    elapsedSec: Int,
    readyToMerge: Boolean,
    checksPassed: Boolean,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    onReview: () -> Unit,
    onShip: () -> Unit,
    onPreview: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val status = when {
        isRunning -> AgentTaskStore.Status.WORKING
        needsYou -> AgentTaskStore.Status.NEEDS_YOU
        readyToMerge -> AgentTaskStore.Status.READY_MERGE
        added > 0 || removed > 0 || fileCount > 0 -> AgentTaskStore.Status.FINISHED
        else -> AgentTaskStore.Status.IDLE
    }
    val bg = if (isDark) Color(0xFF141414) else Color(0xFFF4F4F4)
    val border = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE5E5E5)

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(tween(220)) + expandVertically(spring(stiffness = Spring.StiffnessMediumLow)),
        exit = fadeOut(tween(160)) + shrinkVertically()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            color = bg,
            border = BorderStroke(0.5.dp, border)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(status.dotColor())
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        status.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = status.dotColor()
                    )
                    if (elapsedSec > 0) {
                        Text(
                            "  ·  ${AgentTaskStore.formatElapsed(elapsedSec)}",
                            fontSize = 10.sp,
                            color = muted,
                            fontFamily = JetBrainsMonoFamily
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (readyToMerge || checksPassed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                null,
                                tint = GreenOk,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (checksPassed) "Checks passed" else "Ready",
                                fontSize = 10.sp,
                                color = GreenOk,
                                fontFamily = InterFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                if (title.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = InterFamily,
                        color = primaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (fileCount > 0 || added > 0 || removed > 0) {
                        Text(
                            buildString {
                                if (fileCount > 0) append("$fileCount file${if (fileCount == 1) "" else "s"}")
                                if (added > 0) {
                                    if (isNotEmpty()) append("  ·  ")
                                    append("+$added")
                                }
                                if (removed > 0) {
                                    if (isNotEmpty()) append("  ")
                                    append("−$removed")
                                }
                            },
                            fontSize = 10.sp,
                            fontFamily = JetBrainsMonoFamily,
                            color = muted
                        )
                    } else {
                        Text(
                            if (isRunning) "Agent is working…" else "Plan, ask, or ship from here",
                            fontSize = 10.sp,
                            color = muted,
                            fontFamily = InterFamily
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    MissionCtaChip(
                        icon = Icons.Outlined.RateReview,
                        label = "Review",
                        isDark = isDark,
                        primaryText = primaryText,
                        muted = muted,
                        filled = false,
                        onClick = onReview
                    )
                    if (onPreview != null) {
                        MissionCtaChip(
                            icon = Icons.Outlined.Image,
                            label = "Preview",
                            isDark = isDark,
                            primaryText = primaryText,
                            muted = muted,
                            filled = false,
                            onClick = onPreview
                        )
                    }
                    MissionCtaChip(
                        icon = if (readyToMerge) Icons.Outlined.CallMerge else Icons.Outlined.RocketLaunch,
                        label = if (readyToMerge) "Merge" else "Ship",
                        isDark = isDark,
                        primaryText = primaryText,
                        muted = muted,
                        filled = true,
                        onClick = onShip
                    )
                }
            }
        }
    }
}

@Composable
fun MissionCtaChip(
    icon: ImageVector,
    label: String,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    filled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        filled -> Accent
        isDark -> Color(0xFF1F1F1F)
        else -> Color.White
    }
    val fg = if (filled) Color.White else primaryText
    val border = if (filled) Color.Transparent else if (isDark) Color(0xFF2A2A2A) else Color(0xFFE5E5EA)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(0.5.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (filled) Color.White else muted, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = fg, fontFamily = InterFamily)
    }
}

/**
 * Horizontal artifacts strip: diffs · screenshots · walkthrough.
 */
@Composable
fun ArtifactsStrip(
    screenshotPaths: List<String>,
    diffCount: Int,
    hasWalkthrough: Boolean,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    onOpenDiffs: () -> Unit,
    onOpenScreenshot: (String) -> Unit,
    onAnnotate: (String) -> Unit,
    onWalkthrough: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (screenshotPaths.isEmpty() && diffCount == 0 && !hasWalkthrough) return

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        // "Artifacts" label — UnicaOneRegular + accent (matching AhamAI appbar logo style)
        Text(
            text = "ARTIFACTS",
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = UnicaOneRegular,
            color = Accent,
            letterSpacing = 0.6.sp
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (diffCount > 0) {
                ArtifactChip(
                    label = "Diffs · $diffCount",
                    isDark = isDark,
                    primaryText = primaryText,
                    muted = muted,
                    onClick = onOpenDiffs
                )
            }
            if (hasWalkthrough) {
                ArtifactChip(
                    label = "Walkthrough",
                    isDark = isDark,
                    primaryText = primaryText,
                    muted = muted,
                    accent = true,
                    onClick = onWalkthrough
                )
            }
            screenshotPaths.take(6).forEach { path ->
                ArtifactThumb(
                    path = path,
                    isDark = isDark,
                    muted = muted,
                    onOpen = { onOpenScreenshot(path) },
                    onAnnotate = { onAnnotate(path) }
                )
            }
        }
    }
}

@Composable
private fun ArtifactChip(
    label: String,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val bg = when {
        accent -> Accent.copy(alpha = if (isDark) 0.18f else 0.12f)
        isDark -> Color(0xFF1A1A1A)
        else -> Color(0xFFF0F0F0)
    }
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = InterFamily,
        color = if (accent) Accent else primaryText
    )
}

@Composable
private fun ArtifactThumb(
    path: String,
    isDark: Boolean,
    muted: Color,
    onOpen: () -> Unit,
    onAnnotate: () -> Unit
) {
    val bmp = remember(path) {
        runCatching {
            val f = File(path)
            if (f.exists()) BitmapFactory.decodeFile(path) else null
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .size(width = 72.dp, height = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFEEEFF2))
            .clickable(onClick = onOpen)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Outlined.Image,
                null,
                tint = muted,
                modifier = Modifier.align(Alignment.Center).size(16.dp)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onAnnotate),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Outlined.Edit, "Annotate", tint = Color.White, modifier = Modifier.size(10.dp))
        }
    }
}

/**
 * Finished toast card (in-app), Cursor "Stay in the loop" style.
 */
@Composable
fun FinishedToastCard(
    visible: Boolean,
    title: String,
    added: Int,
    removed: Int,
    fileCount: Int,
    elapsedLabel: String,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(240)) + slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        ),
        exit = fadeOut(tween(180)),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isDark) Color(0xF21C1C1E) else Color(0xF7FFFFFF),
            border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)),
            shadowElevation = 8.dp
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Finished · $elapsedLabel",
                        fontSize = 10.sp,
                        color = GreenOk,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Close,
                        "Dismiss",
                        tint = muted,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onDismiss)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = primaryText,
                    fontFamily = InterFamily,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (added > 0) Text("+$added", fontSize = 10.sp, color = GreenOk, fontFamily = JetBrainsMonoFamily)
                    if (removed > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("−$removed", fontSize = 10.sp, color = RedDiff, fontFamily = JetBrainsMonoFamily)
                    }
                    if (fileCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("· $fileCount files", fontSize = 10.sp, color = muted, fontFamily = JetBrainsMonoFamily)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Review",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Accent)
                            .clickable(onClick = onReview)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontFamily = InterFamily
                    )
                }
            }
        }
    }
}

/**
 * Point-and-draw annotation overlay on a screenshot.
 */
@Composable
fun AnnotateImageDialog(
    imagePath: String,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    onDone: (note: String, strokeCount: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val bmp = remember(imagePath) {
        runCatching { BitmapFactory.decodeFile(imagePath) }.getOrNull()
    }
    var note by remember { mutableStateOf("") }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(18.dp),
            color = if (isDark) Color(0xFF0A0A0A) else Color.White
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Point & draw",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        fontFamily = InterFamily
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Clear",
                        fontSize = 11.sp,
                        color = muted,
                        modifier = Modifier
                            .clickable {
                                strokes.clear()
                                current = emptyList()
                            }
                            .padding(8.dp)
                    )
                    Text(
                        "Done",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Accent,
                        modifier = Modifier
                            .clickable {
                                onDone(note.trim(), strokes.size + if (current.isNotEmpty()) 1 else 0)
                            }
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF141414) else Color(0xFFF4F4F4))
                ) {
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset -> current = listOf(offset) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        current = current + change.position
                                    },
                                    onDragEnd = {
                                        if (current.size > 1) strokes.add(current)
                                        current = emptyList()
                                    },
                                    onDragCancel = { current = emptyList() }
                                )
                            }
                    ) {
                        val all = strokes + listOfNotNull(current.takeIf { it.isNotEmpty() })
                        all.forEach { pts ->
                            if (pts.size < 2) return@forEach
                            val path = Path().apply {
                                moveTo(pts.first().x, pts.first().y)
                                for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                            }
                            drawPath(
                                path,
                                color = Color(0xFFFF3B30),
                                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = note,
                    onValueChange = { note = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        color = primaryText,
                        fontFamily = InterFamily
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF141414) else Color(0xFFF4F4F4))
                        .padding(10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (note.isEmpty()) {
                                Text(
                                    "Describe the change (e.g. make icon larger)…",
                                    fontSize = 12.sp,
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
    }
}

/**
 * Compact quick-action pill for Agent home.
 */
@Composable
fun MissionQuickPill(
    label: String,
    isDark: Boolean,
    primaryText: Color,
    muted: Color,
    onClick: () -> Unit
) {
    val haptics = rememberAgentHaptics()
    Text(
        label,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF141414) else Color.White.copy(alpha = 0.75f))
            .border(
                0.5.dp,
                if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA),
                RoundedCornerShape(20.dp)
            )
            .agentPressable(pressedScale = 0.95f, haptics = haptics) {
                haptics.tick()
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = primaryText,
        fontFamily = InterFamily
    )
}

/**
 * Active agents stack chip (multi-run indicator).
 */
@Composable
fun ActiveAgentsChip(
    count: Int,
    isDark: Boolean,
    muted: Color,
    primaryText: Color,
    onClick: () -> Unit
) {
    if (count <= 0) return
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF1A1A1A) else Color(0xFFF0F0F0))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(BlueWork))
        Spacer(Modifier.width(6.dp))
        Text(
            "$count Active",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = primaryText,
            fontFamily = InterFamily
        )
    }
}

// ── Screenshot-style task list (All tasks · Action Required / In Progress / Idle) ──

/**
 * High-level buckets for Mission Control list.
 * Completed runs stay **Completed** (not mixed with empty Idle workspaces).
 */
enum class AgentTaskBucket { ACTION_REQUIRED, IN_PROGRESS, COMPLETED, IDLE }

fun AgentTaskStore.Status.toBucket(): AgentTaskBucket = when (this) {
    AgentTaskStore.Status.NEEDS_YOU, AgentTaskStore.Status.FAILED -> AgentTaskBucket.ACTION_REQUIRED
    AgentTaskStore.Status.WORKING -> AgentTaskBucket.IN_PROGRESS
    AgentTaskStore.Status.FINISHED, AgentTaskStore.Status.READY_MERGE, AgentTaskStore.Status.MERGED ->
        AgentTaskBucket.COMPLETED
    AgentTaskStore.Status.IDLE -> AgentTaskBucket.IDLE
}

/** Status colors — soft iOS system palette. */
/** Action Required: iOS system orange/yellow (attention). */
private val ActionAmber = Color(0xFFFF9F0A)
private val ProgressInk = Color(0xFF8E8E93)
/** Idle: soft iOS system blue — calm “ready / empty”. */
private val IdleInk = Color(0xFF5AC8FA)
private val IdleRing = Color(0xFF5AC8FA)
/** Completed / success: iOS system green. */
private val SuccessGreen = Color(0xFF34C759)

@Composable
fun AgentTasksSectionHeader(
    title: String,
    bucket: AgentTaskBucket,
    modifier: Modifier = Modifier
) {
    val color = when (bucket) {
        AgentTaskBucket.ACTION_REQUIRED -> ActionAmber
        AgentTaskBucket.IN_PROGRESS -> ProgressInk
        AgentTaskBucket.COMPLETED -> SuccessGreen
        AgentTaskBucket.IDLE -> IdleInk
    }
    Text(
        text = title,
        modifier = modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = InterFamily,
        color = color,
        letterSpacing = 0.2.sp
    )
}

/**
 * Clean iOS-style status glyphs (SF Symbol–adjacent):
 * - Action Required: amber filled circle + white “!” (failed → red)
 * - In Progress: soft rotating arc
 * - Completed: green circle + white check
 * - Idle: soft hollow blue ring
 */
@Composable
fun AgentTaskStatusGlyph(
    status: AgentTaskStore.Status,
    modifier: Modifier = Modifier
) {
    val bucket = status.toBucket()
    Box(modifier.size(22.dp), contentAlignment = Alignment.Center) {
        when (bucket) {
            AgentTaskBucket.ACTION_REQUIRED -> {
                // iOS exclamationmark.circle.fill — yellow/orange attention
                val fill = if (status == AgentTaskStore.Status.FAILED) RedDiff else ActionAmber
                Canvas(Modifier.size(17.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(color = fill, radius = r)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = r * 0.92f,
                        center = Offset(size.width * 0.42f, size.height * 0.36f)
                    )
                    val cx = size.width * 0.5f
                    val barTop = size.height * 0.22f
                    val barBottom = size.height * 0.58f
                    drawLine(
                        color = Color.White,
                        start = Offset(cx, barTop),
                        end = Offset(cx, barBottom),
                        strokeWidth = 1.85f,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 1.35f,
                        center = Offset(cx, size.height * 0.74f)
                    )
                }
            }
            AgentTaskBucket.IN_PROGRESS -> {
                val transition = rememberInfiniteTransition(label = "progressArc")
                val rot by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(900, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rot"
                )
                Canvas(Modifier.size(16.dp).graphicsLayer { rotationZ = rot }) {
                    val stroke = Stroke(width = 1.8f, cap = StrokeCap.Round)
                    val inset = 1.6f
                    drawArc(
                        color = ProgressInk,
                        startAngle = -90f,
                        sweepAngle = 270f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(
                            size.width - inset * 2,
                            size.height - inset * 2
                        ),
                        style = stroke
                    )
                }
            }
            AgentTaskBucket.COMPLETED -> {
                // Green success disc + crisp white check
                Canvas(Modifier.size(17.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(color = SuccessGreen, radius = r)
                    val check = Path().apply {
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.52f
                        moveTo(cx - r * 0.38f, cy - r * 0.02f)
                        lineTo(cx - r * 0.10f, cy + r * 0.28f)
                        lineTo(cx + r * 0.40f, cy - r * 0.32f)
                    }
                    drawPath(
                        path = check,
                        color = Color.White,
                        style = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }
            AgentTaskBucket.IDLE -> {
                // Hollow blue ring — calm “ready / empty workspace”
                Canvas(Modifier.size(14.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(
                        color = IdleRing,
                        radius = r - 1.2f,
                        style = Stroke(width = 1.6f, cap = StrokeCap.Round)
                    )
                }
            }
        }
    }
}

/**
 * Minimal task row — status glyph + title only (reference list style).
 * No favicons, no heavy cards, no export chips on the row.
 * Long-press opens the floating context menu (caller handles [onLongPress]).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AgentTaskListRow(
    title: String,
    status: AgentTaskStore.Status,
    primaryText: Color,
    muted: Color,
    dimmed: Boolean = false,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAgentHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    HapticOnPress(interaction, haptics)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = com.ahamai.app.ui.agent.AgentFeel.pressSpring,
        label = "taskListRow"
    )
    val ink = when {
        dimmed -> muted.copy(alpha = 0.55f)
        status == AgentTaskStore.Status.MERGED -> muted.copy(alpha = 0.65f)
        else -> primaryText
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = {
                    haptics.tick()
                    onClick()
                },
                onLongClick = {
                    if (onLongPress != null) {
                        haptics.select()
                        onLongPress()
                    }
                }
            )
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AgentTaskStatusGlyph(status = status)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title.ifBlank { "Untitled task" },
            fontSize = 16.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = InterFamily,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = (-0.2f).sp,
            modifier = Modifier.weight(1f)
        )
    }
}
