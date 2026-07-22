package com.ahamai.app.ui.chat

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * Smooth chat haptics — send / stop / chips / presses.
 *
 * Strategy (most → least preferred):
 *  1. OEM predefined effects (CLICK / TICK / HEAVY_CLICK) — best on modern phones
 *  2. View performHapticFeedback with FLAG_IGNORE_GLOBAL_SETTING when needed
 *  3. Compose [HapticFeedback]
 *  4. Short soft one-shot via [Vibrator]
 *
 * Debounced so double-wired call sites (e.g. input bar + parent onSend) don't buzz twice.
 */
class ChatHaptics(
    private val view: View,
    private val context: Context,
    private val composeHaptics: HapticFeedback
) {
    private val vibrator: Vibrator? by lazy { resolveVibrator(context) }

    @Volatile private var lastAt = 0L
    @Volatile private var lastKind = -1

    // ── Public API ──────────────────────────────────────────────────────────

    /** Softest — chips, related, attach, voice. */
    fun tick() = fire(Kind.TICK)

    /** Light press-in while finger is down (send / stop / chips). */
    fun press() = fire(Kind.PRESS)

    /** Primary action — send message. */
    fun send() = fire(Kind.SEND)

    /** Confirm — copy, expand, generic confirm. */
    fun confirm() = fire(Kind.CONFIRM)

    /** Selection change — model pick, sheet open. */
    fun select() = fire(Kind.SELECT)

    /** Stop generation / destructive. */
    fun reject() = fire(Kind.REJECT)

    /** Answer finished streaming. */
    fun success() = fire(Kind.SUCCESS)

    // ── Core ────────────────────────────────────────────────────────────────

    private enum class Kind { TICK, PRESS, SEND, CONFIRM, SELECT, REJECT, SUCCESS }

    private fun fire(kind: Kind) {
        val now = SystemClock.uptimeMillis()
        // Debounce identical kinds (double send wiring) and very rapid presses.
        val minGap = when (kind) {
            Kind.PRESS -> 28L
            Kind.TICK -> 40L
            else -> 70L
        }
        if (kind.ordinal == lastKind && now - lastAt < minGap) return
        // Different kinds still coalesce if under 20ms (same gesture stack).
        if (now - lastAt < 20L) return
        lastAt = now
        lastKind = kind.ordinal

        // 1) Predefined vibrator effects — smoothest on Pixel / Samsung / OnePlus etc.
        if (tryPredefined(kind)) return

        // 2) View haptics (respects most system settings).
        if (tryViewHaptic(kind)) return

        // 3) Compose local haptics.
        if (tryComposeHaptic(kind)) return

        // 4) Soft one-shot fallback.
        tryOneShot(kind)
    }

    private fun tryPredefined(kind: Kind): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val v = vibrator ?: return false
        if (!v.hasVibrator()) return false
        return try {
            val effectId = when (kind) {
                Kind.TICK, Kind.PRESS -> VibrationEffect.EFFECT_TICK
                Kind.SEND, Kind.CONFIRM, Kind.SUCCESS -> VibrationEffect.EFFECT_CLICK
                Kind.SELECT -> VibrationEffect.EFFECT_TICK
                Kind.REJECT -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
            v.vibrate(VibrationEffect.createPredefined(effectId))
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryViewHaptic(kind: Kind): Boolean {
        return try {
            val constant = when {
                kind == Kind.TICK || kind == Kind.PRESS || kind == Kind.SELECT ->
                    HapticFeedbackConstants.CLOCK_TICK
                kind == Kind.REJECT ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        HapticFeedbackConstants.REJECT
                    else HapticFeedbackConstants.LONG_PRESS
                kind == Kind.SUCCESS || kind == Kind.CONFIRM || kind == Kind.SEND ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        HapticFeedbackConstants.CONFIRM
                    else HapticFeedbackConstants.KEYBOARD_TAP
                else -> HapticFeedbackConstants.VIRTUAL_KEY
            }
            // IGNORE_VIEW_SETTING: still fire even if the specific view has haptics off.
            // Do NOT ignore global setting — users who disabled haptics stay silent.
            val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
            view.performHapticFeedback(constant, flags)
        } catch (_: Exception) {
            false
        }
    }

    private fun tryComposeHaptic(kind: Kind): Boolean {
        return try {
            val type = when (kind) {
                Kind.TICK, Kind.PRESS, Kind.SELECT -> HapticFeedbackType.TextHandleMove
                Kind.REJECT -> HapticFeedbackType.LongPress
                else -> HapticFeedbackType.LongPress
            }
            composeHaptics.performHapticFeedback(type)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryOneShot(kind: Kind) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            val (ms, amp) = when (kind) {
                Kind.TICK, Kind.PRESS -> 8L to 36
                Kind.SELECT -> 10L to 42
                Kind.SEND, Kind.CONFIRM, Kind.SUCCESS -> 14L to 70
                Kind.REJECT -> 22L to 110
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val a = if (v.hasAmplitudeControl()) amp.coerceIn(1, 255)
                else VibrationEffect.DEFAULT_AMPLITUDE
                v.vibrate(VibrationEffect.createOneShot(ms, a))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (_: Exception) {
            // non-critical
        }
    }

    companion object {
        private fun resolveVibrator(context: Context): Vibrator? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun rememberChatHaptics(): ChatHaptics {
    val view = LocalView.current
    val context = LocalContext.current
    val compose = LocalHapticFeedback.current
    return remember(view, context, compose) { ChatHaptics(view, context, compose) }
}

/**
 * Fires a light [ChatHaptics.press] when the interaction source is pressed.
 * Use on the send button so feedback starts on finger-down, not only on click.
 */
@Composable
fun HapticOnPress(
    interactionSource: MutableInteractionSource,
    haptics: ChatHaptics,
    enabled: Boolean = true
) {
    LaunchedEffect(interactionSource, enabled) {
        if (!enabled) return@LaunchedEffect
        interactionSource.interactions.collect { interaction: Interaction ->
            if (interaction is PressInteraction.Press) {
                haptics.press()
            }
        }
    }
}
