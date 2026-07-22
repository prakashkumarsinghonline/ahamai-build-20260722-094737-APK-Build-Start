package com.ahamai.app.ui.flow

import androidx.compose.animation.ContentTransform
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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Shared motion language for first-run flow:
 * Splash → Onboarding → Auth (login/signup) → Home.
 *
 * Soft springs, no harsh snaps — feels like iOS / Google first-open.
 */
object FlowMotion {

    private val ease = FastOutSlowInEasing

    /** Splash dissolving into the next screen. */
    fun splashExit(): ContentTransform {
        val enter = fadeIn(tween(320, easing = ease)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(340, easing = ease))
        val exit = fadeOut(tween(260, easing = ease)) +
            scaleOut(targetScale = 1.02f, animationSpec = tween(260, easing = ease))
        return enter.togetherWith(exit)
    }

    /**
     * Forward step in the funnel (onboarding → auth → home).
     * Soft rise + fade in; previous fades and eases slightly up.
     */
    fun forward(): ContentTransform {
        val enter = fadeIn(tween(340, easing = ease)) +
            slideInVertically(
                animationSpec = tween(380, easing = ease),
                initialOffsetY = { it / 18 }
            ) +
            scaleIn(initialScale = 0.97f, animationSpec = tween(380, easing = ease))
        val exit = fadeOut(tween(240, easing = ease)) +
            scaleOut(targetScale = 0.985f, animationSpec = tween(240, easing = ease))
        return enter.togetherWith(exit)
    }

    /** Auth login ⇄ signup switch. */
    fun authModeSwitch(toSignup: Boolean): ContentTransform {
        val dir = if (toSignup) 1 else -1
        val enter = fadeIn(tween(280, easing = ease)) +
            slideInHorizontally(
                animationSpec = tween(320, easing = ease),
                initialOffsetX = { full -> dir * full / 12 }
            ) +
            scaleIn(initialScale = 0.98f, animationSpec = tween(300, easing = ease))
        val exit = fadeOut(tween(200, easing = ease)) +
            slideOutHorizontally(
                animationSpec = tween(240, easing = ease),
                targetOffsetX = { full -> -dir * full / 14 }
            )
        return enter.togetherWith(exit)
    }

    /** Default in-app screen change (snappy but soft). */
    fun softCrossfade(): ContentTransform {
        val enter = fadeIn(tween(220, easing = ease)) +
            scaleIn(initialScale = 0.995f, animationSpec = tween(220, easing = ease))
        val exit = fadeOut(tween(160, easing = ease))
        return enter.togetherWith(exit)
    }

    /**
     * Chat ⇄ Agent mode switch — iOS-style, almost no travel.
     * Pure short crossfade + micro scale (no vertical slide, no big jump).
     */
    fun modeSwitch(): ContentTransform {
        // ~220ms dissolve; scale barely moves so ModeToggle feels continuous
        val enter = fadeIn(animationSpec = tween(200, easing = ease)) +
            scaleIn(
                initialScale = 0.994f,
                animationSpec = tween(220, easing = ease)
            )
        val exit = fadeOut(animationSpec = tween(150, easing = ease))
        return enter togetherWith exit
    }

    /**
     * AI-triggered handoff into agent — still soft, not a full page swipe.
     * Slight horizontal drift only (1/28 width), short fade.
     */
    fun agentHandoff(): ContentTransform {
        val enter = fadeIn(tween(240, easing = ease)) +
            slideInHorizontally(animationSpec = tween(260, easing = ease)) { full -> full / 28 } +
            scaleIn(initialScale = 0.99f, animationSpec = tween(260, easing = ease))
        val exit = fadeOut(tween(180, easing = ease)) +
            slideOutHorizontally(animationSpec = tween(200, easing = ease)) { full -> -full / 40 }
        return enter.togetherWith(exit)
    }

    fun pageEnter(): EnterTransition =
        fadeIn(tween(280, easing = ease)) +
            slideInVertically(tween(320, easing = ease)) { it / 20 }

    fun pageExit(): ExitTransition =
        fadeOut(tween(180, easing = ease))
}

/**
 * One-shot entrance when a flow screen first appears:
 * fade + slight rise + scale — runs once per composition key.
 */
@Composable
fun FlowEnter(
    modifier: Modifier = Modifier,
    delayMs: Long = 0L,
    content: @Composable () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs)
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "flowAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.97f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "flowScale"
    )
    val ty by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "flowY"
    )
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            scaleX = scale
            scaleY = scale
            translationY = ty
        }
    ) {
        content()
    }
}
