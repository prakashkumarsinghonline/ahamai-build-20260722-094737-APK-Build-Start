package com.ahamai.app.ui.components

import com.ahamai.app.ui.theme.ChatPalette
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.FileRewindResponse
import com.ahamai.app.data.PermissionMode
import com.ahamai.app.data.PermissionRequest
import com.ahamai.app.data.RewindPointMeta
import com.ahamai.app.ui.icons.AdminIcons
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact safety UI (IosDialog / sheet) — kept out of CodeAgentScreen.
 * - Permission confirm for risky tools
 * - Permission mode picker
 * - Rewind turn picker
 */

@Composable
fun PermissionConfirmDialog(
    req: PermissionRequest,
    isDark: Boolean = isSystemInDarkTheme(),
    onAllowOnce: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit
) {
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
    val muted = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B6B6B)
    val bgAccent = if (isDark) Color(0xFF2A2A30) else Color(0xFFE8E8EB)

    IosDialog(isDark = isDark, onDismissRequest = onDeny) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.ShieldCheck, null, tint = muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(req.title(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily, color = primary)
        }
        Spacer(Modifier.height(10.dp))
        Text(req.body(), fontSize = 13.sp, fontFamily = InterFamily, color = muted, lineHeight = 18.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("Deny", fontSize = 14.sp, color = muted, fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onDeny).padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text("Always", fontSize = 14.sp, color = muted, fontFamily = InterFamily,
                modifier = Modifier.clickable(onClick = onAlwaysAllow).padding(8.dp))
            Spacer(Modifier.width(8.dp))
            Text("Allow", fontSize = 14.sp, color = primary, fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily, modifier = Modifier.clickable(onClick = onAllowOnce).padding(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionModeSheet(
    current: PermissionMode,
    isDark: Boolean = isSystemInDarkTheme(),
    onSelect: (PermissionMode) -> Unit,
    onDismiss: () -> Unit
) {
    // Match Model picker (ModelPickerContent): iOS inset group, no per-row cards
    val primary = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInk
        else com.ahamai.app.ui.theme.ChatPalette.LightInk
    val muted = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkInkSecondary
        else com.ahamai.app.ui.theme.ChatPalette.LightInkSecondary
    val groupBg = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkSurface
        else com.ahamai.app.ui.theme.ChatPalette.LightSurfaceElevated
    val sep = if (isDark) com.ahamai.app.ui.theme.ChatPalette.DarkBorder
        else com.ahamai.app.ui.theme.ChatPalette.LightBorder
    val checkColor = com.ahamai.app.ui.theme.ChatPalette.Accent
    val modes = PermissionMode.entries

    IosBottomSheet(isDark = isDark, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 540.dp)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
        ) {
            Text(
                "Permission Mode",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFamily,
                color = primary,
                letterSpacing = (-0.5f).sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                "How the agent handles tool usage",
                fontSize = 13.sp,
                fontFamily = InterFamily,
                color = muted,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )

            // Single grouped list — rows are transparent; only the group has a surface
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(groupBg)
                    .verticalScroll(rememberScrollState())
            ) {
                modes.forEachIndexed { index, mode ->
                    val selected = mode == current
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                mode.label,
                                fontSize = 16.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontFamily = InterFamily,
                                color = primary,
                                letterSpacing = (-0.2f).sp
                            )
                            if (mode.blurb.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    mode.blurb,
                                    fontSize = 13.sp,
                                    fontFamily = InterFamily,
                                    color = muted,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                        if (selected) {
                            Icon(
                                Lucide.Check,
                                contentDescription = "Selected",
                                tint = checkColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (index < modes.lastIndex) {
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = sep,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewindPickerSheet(
    metas: List<RewindPointMeta>,
    isDark: Boolean = isSystemInDarkTheme(),
    onPick: (promptIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
    val muted = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B6B6B)
    val row = if (isDark) Color(0xFF2A2A30) else Color(0xFFF0F0F2)
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    IosBottomSheet(isDark = isDark, onDismissRequest = onDismiss) {
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Lucide.RotateCcw, null, tint = muted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Rewind to turn", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily, color = primary)
        }
        Text(
            "Restores all files changed from that turn onward",
            fontSize = 12.sp, fontFamily = InterFamily, color = muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Spacer(Modifier.height(4.dp))
        if (metas.isEmpty()) {
            Text("No checkpoints yet", fontSize = 13.sp, color = muted, fontFamily = InterFamily,
                modifier = Modifier.padding(20.dp))
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                metas.asReversed().forEach { m ->
                    val time = if (m.createdAt > 0) fmt.format(Date(m.createdAt)) else "—"
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(row.copy(alpha = 0.5f))
                            .clickable { onPick(m.promptIndex) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Turn #${m.promptIndex}",
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                fontFamily = InterFamily, color = primary
                            )
                            Text(
                                "$time · ${m.numFileSnapshots} file${if (m.numFileSnapshots == 1) "" else "s"}",
                                fontSize = 12.sp, fontFamily = JetBrainsMonoFamily, color = muted
                            )
                        }
                        Icon(Lucide.RotateCcw, null, tint = muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Small mode chip for agent chrome (opens mode sheet). */
@Composable
/**
 * Maps each [PermissionMode] to a distinct Lucide icon for visual recognition.
 * Clean, expressive strokes that match each mode's semantics.
 */
fun modeIcon(mode: PermissionMode): ImageVector = when (mode) {
    PermissionMode.READONLY   -> Lucide.Eye        // watch-only
    PermissionMode.ASK        -> Lucide.MessageSquare // confirm before
    PermissionMode.ACCEPT_EDITS -> Lucide.Edit      // file edits only
    PermissionMode.AUTO       -> Lucide.Zap         // fast/automatic
    PermissionMode.FULL       -> AdminIcons.FullPermissionIcon       // full access — shield with keyhole
}

@Composable
fun PermissionModeChip(
    mode: PermissionMode,
    muted: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Icon(modeIcon(mode), null, tint = muted, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(mode.label, fontSize = 10.sp, color = muted, fontWeight = FontWeight.Medium,
            fontFamily = InterFamily)
    }
}

fun formatRewindToast(resp: FileRewindResponse): String = resp.summaryLine().take(90)
