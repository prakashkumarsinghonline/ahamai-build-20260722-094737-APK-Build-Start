package com.ahamai.app.ui.chat

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * iOS-inspired motion language for Chat mode.
 * Butter-smooth springs (high damping, no bounce), soft fades — UIKit-like feel.
 * App colors stay elsewhere; this is only timing / feel.
 */
object ChatMotion {
    /** iOS standard curve ≈ UIViewAnimationCurve.easeInOut */
    val iosEase = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
    /** Slightly snappier press */
    val iosEaseOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /** Butter spring — settles with almost no overshoot (iOS interactive). */
    val butterSpring = spring<Float>(
        dampingRatio = 0.92f,
        stiffness = 280f
    )
    val snappySpring = spring<Float>(
        dampingRatio = 0.88f,
        stiffness = 380f
    )
    val softSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val pressSpring = spring<Float>(
        dampingRatio = 0.86f,
        stiffness = 500f
    )

    val quickFade = tween<Float>(durationMillis = 200, easing = iosEase)
    val mediumFade = tween<Float>(durationMillis = 280, easing = iosEase)
    val expandMs = 320
    val collapseMs = 220

    /** Message appear — soft rise + fade (iMessage / Messages). */
    fun messageEnter(): EnterTransition =
        fadeIn(animationSpec = tween(280, easing = iosEase)) +
            slideInVertically(
                animationSpec = tween(340, easing = iosEaseOut),
                initialOffsetY = { (it * 0.06f).toInt().coerceAtLeast(8) }
            ) +
            scaleIn(
                initialScale = 0.988f,
                animationSpec = tween(300, easing = iosEaseOut)
            )

    fun messageExit(): ExitTransition =
        fadeOut(tween(160, easing = iosEase)) +
            scaleOut(targetScale = 0.99f, animationSpec = tween(160, easing = iosEase))

    /** Expandable panels (thinking, sources). */
    fun panelExpand(): EnterTransition =
        fadeIn(tween(240, easing = iosEase)) +
            expandVertically(
                animationSpec = tween(expandMs, easing = iosEaseOut),
                expandFrom = androidx.compose.ui.Alignment.Top
            )

    fun panelCollapse(): ExitTransition =
        fadeOut(tween(160, easing = iosEase)) +
            shrinkVertically(
                animationSpec = tween(collapseMs, easing = iosEase),
                shrinkTowards = androidx.compose.ui.Alignment.Top
            )

    /** Related / follow-up chips — soft stagger. */
    fun chipEnter(index: Int = 0): EnterTransition =
        fadeIn(tween(240, delayMillis = index * 32, easing = iosEase)) +
            scaleIn(
                initialScale = 0.94f,
                animationSpec = tween(280, delayMillis = index * 32, easing = iosEaseOut)
            )

    /** Streaming answer body — gentle fade when content first lands. */
    fun answerEnter(): EnterTransition =
        fadeIn(animationSpec = tween(320, easing = iosEaseOut)) +
            slideInVertically(
                animationSpec = tween(360, easing = iosEaseOut),
                initialOffsetY = { 10 }
            )
}

/**
 * Press-scale feedback without ripple (iOS no-ink style).
 */
fun Modifier.chatPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    haptics: ChatHaptics? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = ChatMotion.pressSpring,
        label = "chatPress"
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

@Composable
fun rememberMessageAppear(key: Any): Boolean {
    val appeared = remember(key) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(key) { appeared.value = true }
    return appeared.value
}
