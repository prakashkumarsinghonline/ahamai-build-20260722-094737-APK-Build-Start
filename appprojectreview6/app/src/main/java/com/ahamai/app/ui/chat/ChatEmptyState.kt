package com.ahamai.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.ui.icons.AdminIcons
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.ChatPalette
import com.ahamai.app.ui.theme.InterFamily
import kotlinx.coroutines.delay

/**
 * iOS Messages / Notes–style empty chat.
 *
 * [resetKey] must change on every new chat so suggestions reshuffle and entrance
 * animations re-run (pass chatKey / session id from the host).
 */
@Composable
fun ChatHomeWelcome(
    isDark: Boolean,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    resetKey: Any = Unit
) {
    key(resetKey) {
        ChatHomeWelcomeBody(isDark = isDark, onSuggestion = onSuggestion, modifier = modifier)
    }
}

@Composable
private fun ChatHomeWelcomeBody(
    isDark: Boolean,
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ink = if (isDark) ChatPalette.DarkInk else ChatPalette.LightInk
    val muted = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkSecondary
    val card = if (isDark) ChatPalette.DarkSurface else ChatPalette.LightSurfaceElevated
    val chevron = if (isDark) ChatPalette.DarkInkTertiary else ChatPalette.LightInkTertiary

    data class Suggest(val icon: ImageVector, val title: String, val prompt: String)

    val pool = remember {
        listOf(
            Suggest(Lucide.MessageSquare, "What can you help with?", "What can you help me with today? Give a short overview."),
            Suggest(Lucide.Edit, "Write something creative", "Write a short creative piece — surprise me."),
            Suggest(Lucide.Sparkles, "Brainstorm ideas", "Help me brainstorm 5 ideas for a side project."),
            Suggest(Lucide.BookOpen, "Explain a concept", "Explain how neural networks work in simple terms."),
            Suggest(Lucide.Code, "Help with code", "Help me write a clean Kotlin data class example."),
            Suggest(Lucide.Search, "Research a topic", "Summarize the pros and cons of remote work."),
            Suggest(Lucide.FileText, "Make a plan", "Make a simple weekly plan to learn Android development."),
            Suggest(Lucide.MessageSquare, "Just chat", "Let's have a friendly conversation. Start with a fun question.")
        )
    }
    // Fresh shuffle every new chat (keyed composition)
    val suggestions = remember { pool.shuffled().take(4) }

    var headerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(40)
        headerVisible = true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = headerVisible,
            enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 12 }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
// Chat welcome icon — clean robot face, no background
                Icon(
                    imageVector = AdminIcons.WelcomeChatIcon,
                    contentDescription = null,
                    tint = muted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "How can I help?",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    letterSpacing = (-0.5f).sp,
                    textAlign = TextAlign.Center,
                    fontFamily = InterFamily
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Pick a suggestion or type below",
                    fontSize = 15.sp,
                    color = muted,
                    textAlign = TextAlign.Center,
                    fontFamily = InterFamily
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(card)
        ) {
            suggestions.forEachIndexed { index, s ->
                val alpha = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    delay(80L + index * 45L)
                    alpha.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { this.alpha = alpha.value }
                        .chatPressable(pressedScale = 0.98f) { onSuggestion(s.prompt) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(card),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = s.icon,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = s.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink,
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
                if (index < suggestions.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 58.dp)
                            .height(0.5.dp)
                            .background(
                                if (isDark) ChatPalette.DarkBorder.copy(alpha = 0.55f)
                                else ChatPalette.LightBorder.copy(alpha = 0.9f)
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ChatRelatedQueries(
    queries: List<String>,
    isDark: Boolean,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queries.isEmpty()) return
    val textColor = if (isDark) ChatPalette.DarkInk else ChatPalette.LightInk
    val muted = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkSecondary
    val inter = InterFamily

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp)
    ) {
        Text(
            text = "Related",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = muted,
            fontFamily = inter,
            letterSpacing = 0.15.sp,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            queries.forEachIndexed { index, q ->
                var visible by remember(q) { mutableStateOf(false) }
                LaunchedEffect(q) {
                    delay(index * 22L)
                    visible = true
                }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(200, delayMillis = index * 24)) +
                        slideInVertically(tween(220)) { it / 8 }
                ) {
                    Text(
                        text = q,
                        fontSize = 14.sp,
                        color = textColor,
                        lineHeight = 19.sp,
                        fontFamily = inter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .chatPressable(pressedScale = 0.98f) { onClick(q) }
                            .padding(horizontal = 2.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}
