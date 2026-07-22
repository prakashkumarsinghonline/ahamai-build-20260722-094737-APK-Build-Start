package com.ahamai.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.ui.icons.Phosphor
import com.ahamai.app.ui.theme.ChatPalette
import com.ahamai.app.ui.theme.InterFamily
import kotlinx.coroutines.delay

/**
 * iOS-style reasoning disclosure — closed by default.
 * No icon backgrounds, no status dots — plain SF-like label + chevron.
 */
@Composable
fun ChatThinkingPanel(
    reasoning: String,
    isStreaming: Boolean,
    isDark: Boolean,
    reasoningStartTime: Long? = null,
    reasoningDurationMs: Long? = null,
    onToggle: ((expanded: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isStreaming, reasoningStartTime) {
        if (isStreaming && reasoningStartTime != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - reasoningStartTime) / 1000f
                delay(120)
            }
        }
    }

    val cleanReasoning = reasoning
        .let { if (it.startsWith("null")) it.removePrefix("null") else it }
        .trim()

    val durationText = when {
        isStreaming && reasoningStartTime != null ->
            String.format("Thinking · %.1fs", elapsedSeconds)
        reasoningDurationMs != null ->
            String.format("Thought for %.1fs", reasoningDurationMs / 1000f)
        else -> if (isStreaming) "Thinking" else "Thought"
    }

    // App palette secondary ink (no extra chrome colors)
    val muted = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkSecondary
    val ink = if (isDark) ChatPalette.DarkInkTertiary else Color(0xFF636366)

    val chevronRot by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = ChatMotion.butterSpring,
        label = "chevron"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .chatPressable(pressedScale = 0.98f) {
                    isExpanded = !isExpanded
                    onToggle?.invoke(isExpanded)
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = durationText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                color = muted,
                letterSpacing = (-0.08f).sp
            )
            if (cleanReasoning.isNotBlank()) {
                Icon(
                    imageVector = Phosphor.CaretDown,
                    contentDescription = if (isExpanded) "Hide thinking" else "Show thinking",
                    tint = muted.copy(alpha = 0.8f),
                    modifier = Modifier
                        .size(14.dp)
                        .rotate(chevronRot)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded && cleanReasoning.isNotBlank(),
            enter = ChatMotion.panelExpand(),
            exit = ChatMotion.panelCollapse()
        ) {
            ChatThinkingContent(
                text = cleanReasoning,
                ink = ink,
                maxHeight = if (isStreaming) 160.dp else 220.dp,
                autoScroll = isStreaming && isExpanded
            )
        }
    }
}

@Composable
private fun ChatThinkingContent(
    text: String,
    ink: Color,
    maxHeight: Dp?,
    autoScroll: Boolean
) {
    val scrollState = rememberScrollState()
    if (autoScroll) {
        LaunchedEffect(text.length / 80, scrollState.maxValue) {
            runCatching { scrollState.animateScrollTo(scrollState.maxValue) }
        }
    }
    // iOS notes-style: indented body, no left rail bar, no card fill
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 2.dp, end = 4.dp)
            .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontFamily = InterFamily,
            fontWeight = FontWeight.Normal,
            color = ink,
            letterSpacing = (-0.05f).sp
        )
    }
}
