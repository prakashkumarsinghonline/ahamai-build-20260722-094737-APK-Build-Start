package com.ahamai.app.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.screens.ToolStatus
import com.ahamai.app.ui.chat.ChatToolStatusIndicator
import com.ahamai.app.ui.components.MarkdownText
import com.ahamai.app.ui.theme.InterFamily

/**
 * Clean iOS-style BTW aside — flat card, no nested chrome.
 * Side answer only; does not interrupt the main agent run.
 *
 * Web search shows the same chat-mode [ChatToolStatusIndicator] processing pill
 * (magnifying glass + "Searching"). No sources bar, favicons, or source sheets.
 */
@Composable
fun AgentBtwAside(
    visible: Boolean,
    question: String,
    answer: String,
    loading: Boolean,
    isDark: Boolean,
    onDismiss: () -> Unit,
    toolStatus: ToolStatus? = null,
    modifier: Modifier = Modifier
) {
    // iOS system materials (same family as IosDialog / sheets)
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val ink = if (isDark) Color(0xFFF2F2F7) else Color(0xFF000000)
    val secondary = Color(0xFF8E8E93)
    val link = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val hairline = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(240)) { it / 6 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(180)) { it / 8 },
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .border(0.5.dp, hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Nav row — plain labels only (no chips, circles, icons)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BTW",
                    color = secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    letterSpacing = 0.3.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "Done",
                    color = link,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = InterFamily,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                )
            }

            Spacer(Modifier.height(10.dp))
            // Hairline — iOS list separator style
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(hairline)
            )
            Spacer(Modifier.height(10.dp))

            // Question — primary label
            Text(
                text = question,
                color = ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Answer — flat on same surface, no nested box
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Chat-mode search processing animation only (no favicons / sources strip)
                if (toolStatus != null) {
                    ChatToolStatusIndicator(status = toolStatus, isDark = isDark)
                }
                when {
                    toolStatus != null && answer.isBlank() -> {
                        // Status pill is the loading UI while searching
                    }
                    loading && answer.isBlank() -> {
                        Text(
                            text = "…",
                            color = secondary,
                            fontSize = 15.sp,
                            fontFamily = InterFamily,
                            lineHeight = 21.sp
                        )
                    }
                    answer.isNotBlank() -> {
                        MarkdownText(
                            text = answer,
                            color = ink,
                            isStreaming = loading && toolStatus == null,
                            fontSize = 15.sp,
                            lineHeight = 21.sp
                        )
                    }
                    else -> {
                        Text(
                            text = "…",
                            color = secondary,
                            fontSize = 15.sp,
                            fontFamily = InterFamily
                        )
                    }
                }
            }
        }
    }
}
