package com.ahamai.app.ui.agent

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import com.ahamai.app.ui.chat.ChatHaptics
import com.ahamai.app.ui.chat.HapticOnPress
import com.ahamai.app.ui.chat.rememberChatHaptics
import com.ahamai.app.ui.flow.FlowEnter

/**
 * Agent-mode motion language — iOS-butter springs (high response, soft settle).
 * Keeps CodeAgentScreen / AgentHomeScreen free of duplicated animation boilerplate.
 */
object AgentFeel {
    /** Press feedback — snappy like iOS UIButton */
    val pressSpring = spring<Float>(
        dampingRatio = 0.68f,
        stiffness = 680f
    )
    /** Soft UI settles (sheets, chips) */
    val softSpring = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 280f
    )
    val snappySpring = spring<Float>(
        dampingRatio = 0.74f,
        stiffness = 520f
    )
    /** FAB / floating chrome */
    val fabSpring = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 520f
    )

    fun logItemEnter(): EnterTransition =
        fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(260, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 18 }
            ) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(240, easing = FastOutSlowInEasing))

    fun logItemExit(): ExitTransition = fadeOut(tween(120))

    fun homeEnter(): EnterTransition =
        fadeIn(tween(300, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(340, easing = FastOutSlowInEasing)) { it / 24 } +
            scaleIn(initialScale = 0.972f, animationSpec = tween(340, easing = FastOutSlowInEasing))

    fun listRowEnter(index: Int = 0): EnterTransition {
        val delay = (index * 28).coerceAtMost(180)
        return fadeIn(tween(220, delayMillis = delay, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(260, delayMillis = delay, easing = FastOutSlowInEasing)) { it / 14 } +
            scaleIn(initialScale = 0.98f, animationSpec = tween(240, delayMillis = delay, easing = FastOutSlowInEasing))
    }
}

/** Reuse chat haptics API under agent naming for call-site clarity. */
@Composable
fun rememberAgentHaptics(): ChatHaptics = rememberChatHaptics()

/**
 * Press-scale + optional press haptic — same as chatPressable, agent-branded.
 */
fun Modifier.agentPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    haptics: ChatHaptics? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = AgentFeel.pressSpring,
        label = "agentPress"
    )
    if (haptics != null) {
        HapticOnPress(interactionSource = interaction, haptics = haptics, enabled = enabled)
    }
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/** Soft one-shot appear for agent home / empty states. */
@Composable
fun AgentEnter(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FlowEnter(modifier = modifier, content = content)
}

/**
 * One-shot fade/rise for log rows keyed by id (does not re-animate on stream updates
 * if [key] stays stable).
 */
@Composable
fun rememberAgentAppear(key: Any): Boolean {
    var appeared by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { appeared = true }
    return appeared
}
