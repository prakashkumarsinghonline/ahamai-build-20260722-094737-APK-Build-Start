package com.ahamai.app.ui.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.screens.ToolStatus
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.ChatPalette
import com.ahamai.app.ui.theme.InterFamily

/**
 * iOS Messages-style typing dots — no bubbles, no chrome.
 */
@Composable
fun ChatTypingIndicator(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val dotColor = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkTertiary
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "phase"
    )
    Row(
        modifier = modifier.padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val a = (1f - kotlin.math.abs(phase - i) / 1.55f).coerceIn(0.25f, 1f)
            val y = (kotlin.math.sin((phase - i) * 1.15f) * 2.4f).coerceIn(-2.8f, 2.8f)
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        translationY = y
                        alpha = a
                    }
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}

/**
 * Tool-calling status — iOS clean search pill (not a heavy “tool call” card).
 * Web search uses a soft circular magnifying-glass, not a raw globe/tool dump.
 */
@Composable
fun ChatToolStatusIndicator(
    status: ToolStatus,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val label = when (status.type) {
        "READ" -> "Reading"
        "IMAGE_SEARCH" -> "Searching images"
        else -> "Searching"
    }
    val subtitle = when (status.type) {
        "READ" -> status.domain ?: status.query.take(48)
        else -> status.query.take(56)
    }
    val textPrimary = if (isDark) ChatPalette.DarkInk else ChatPalette.LightInk
    val textMuted = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkSecondary
    val skel = if (isDark) Color(0xFF2C2C30) else Color(0xFFE5E5EA)
    val inter = InterFamily

    // iOS SF-style: soft circle + Lucide Search (clean magnifying glass)
    val isWeb = status.type != "READ" && status.type != "IMAGE_SEARCH"
    val icon: ImageVector = when (status.type) {
        "READ" -> Lucide.File
        "IMAGE_SEARCH" -> Lucide.Image
        else -> Lucide.Search
    }
    val iconCircle = if (isDark) Color(0xFF2A2A2E) else Color(0xFFF2F2F7)
    val iconTint = if (isDark) Color(0xFF8E8E93) else Color(0xFF636366)
    val pillBorder = if (isDark) Color(0x22FFFFFF) else Color(0x14000000)

    val transition = rememberInfiniteTransition(label = "toolSkeleton")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        // Compact iOS activity row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .then(
                    if (isWeb) Modifier
                        .border(0.5.dp, pillBorder, RoundedCornerShape(22.dp))
                        .background(if (isDark) Color(0xFF161618) else Color(0xFFF9F9FB))
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                    else Modifier.padding(vertical = 2.dp)
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .clip(CircleShape)
                    .background(iconCircle)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    fontFamily = inter,
                    letterSpacing = (-0.2f).sp
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 12.sp,
                        color = textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = inter,
                        letterSpacing = (-0.1f).sp
                    )
                }
            }
        }

        // Light skeleton only for non-web (keep web ultra-minimal iOS)
        if (!isWeb) {
            Spacer(modifier.height(10.dp))
            when (status.type) {
                "IMAGE_SEARCH" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(4) { i ->
                        ChatShimmerBar(
                            Modifier.size(56.dp),
                            skel,
                            sweep,
                            i,
                            RoundedCornerShape(12.dp)
                        )
                    }
                }
                "READ" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ChatShimmerBar(Modifier.fillMaxWidth(0.9f).height(8.dp), skel, sweep, 0)
                    ChatShimmerBar(Modifier.fillMaxWidth(0.75f).height(8.dp), skel, sweep, 1)
                }
            }
        }
    }
}

@Composable
fun ChatShimmerBar(
    modifier: Modifier,
    color: Color,
    sweep: Float,
    index: Int = 0,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val phase = ((sweep + index * 0.14f) % 1f)
    val crest = 1f - kotlin.math.abs(phase - 0.5f) * 2f
    val alpha = 0.38f + 0.52f * crest
    Box(modifier.clip(shape).background(color.copy(alpha = alpha)))
}
