package com.ahamai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * ChatGPT-inspired product palette.
 *
 * Web references (chat.openai.com / community CSS dumps):
 *  - Light: near-white page, soft gray chrome (#F4F4F4), near-black ink (#0D0D0D)
 *  - Dark (current product): very black main surface; elevated chrome ~#212121 / #141414
 *  - Accent: iOS system blue #0A84FF (was OpenAI green)
 *
 * Dark mode here is **AMOLED pure black** (#000) for page bg — OLED battery + true black —
 * with slightly elevated surfaces for cards/sheets so hierarchy still reads.
 */
object ChatPalette {
    // ── Light (ChatGPT light) ───────────────────────────────────────────────
    val LightBg = Color(0xFFF5F5F7)
    val LightSurface = Color(0xFFF4F4F4)
    val LightSurfaceElevated = Color(0xFFFFFFFF)
    val LightSurfaceAlt = Color(0xFFECECEC)
    val LightBorder = Color(0xFFE5E5E5)
    val LightBorderStrong = Color(0xFFD1D1D1)
    val LightInk = Color(0xFF0D0D0D)
    val LightInkSecondary = Color(0xFF6B6B6B)
    val LightInkTertiary = Color(0xFF8E8E8E)
    // Match agent-mode user bubble (white card on light grey page)
    val LightUserBubble = Color(0xFFFFFFFF)
    val LightCodeBg = Color(0xFFF6F8FA)
    val LightCodeHeader = Color(0xFFEDEFF2)

    // ── Dark (greyish, NOT AMOLED pure-black) ───────────────────────────────
    // Page bg is a comfortable neutral dark grey; surfaces step progressively lighter so
    // cards/sheets keep their hierarchy against the grey page.
    val DarkBg = Color(0xFF0C0C0E)
    val DarkSurface = Color(0xFF202024)
    val DarkSurfaceElevated = Color(0xFF27272B)
    val DarkSurfaceAlt = Color(0xFF303036)
    val DarkBorder = Color(0xFF3A3A40)
    val DarkBorderStrong = Color(0xFF4C4C54)
    val DarkInk = Color(0xFFECECEC)
    val DarkInkSecondary = Color(0xFF9A9AA2)
    val DarkInkTertiary = Color(0xFF6B6B72)
    // Match agent-mode elevated surface (#1C1C1F)
    val DarkUserBubble = Color(0xFF1C1C1F)
    val DarkCodeBg = Color(0xFF202024)
    val DarkCodeHeader = Color(0xFF2A2A30)

    // ── Shared ──────────────────────────────────────────────────────────────
    /** iOS system blue (dark-mode systemBlue); primary brand accent. */
    val Accent = Color(0xFF0A84FF)
    /** Slightly deeper blue for pressed / soft fills (iOS light-mode systemBlue). */
    val AccentSoft = Color(0xFF007AFF)
    val Danger = Color(0xFFEF4444)
    val LinkDark = Color(0xFF6AB7FF)
    val LinkLight = Color(0xFF007AFF)
}

@Immutable
data class ChatColorScheme(
    val bg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceAlt: Color,
    val border: Color,
    val borderStrong: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val userBubble: Color,
    val codeBg: Color,
    val codeHeader: Color,
    val accent: Color,
    val danger: Color,
    val link: Color,
    val isDark: Boolean
)

fun chatColors(isDark: Boolean): ChatColorScheme =
    if (isDark) ChatColorScheme(
        bg = ChatPalette.DarkBg,
        surface = ChatPalette.DarkSurface,
        surfaceElevated = ChatPalette.DarkSurfaceElevated,
        surfaceAlt = ChatPalette.DarkSurfaceAlt,
        border = ChatPalette.DarkBorder,
        borderStrong = ChatPalette.DarkBorderStrong,
        ink = ChatPalette.DarkInk,
        inkSecondary = ChatPalette.DarkInkSecondary,
        inkTertiary = ChatPalette.DarkInkTertiary,
        userBubble = ChatPalette.DarkUserBubble,
        codeBg = ChatPalette.DarkCodeBg,
        codeHeader = ChatPalette.DarkCodeHeader,
        accent = ChatPalette.Accent,
        danger = ChatPalette.Danger,
        link = ChatPalette.LinkDark,
        isDark = true
    ) else ChatColorScheme(
        bg = ChatPalette.LightBg,
        surface = ChatPalette.LightSurface,
        surfaceElevated = ChatPalette.LightSurfaceElevated,
        surfaceAlt = ChatPalette.LightSurfaceAlt,
        border = ChatPalette.LightBorder,
        borderStrong = ChatPalette.LightBorderStrong,
        ink = ChatPalette.LightInk,
        inkSecondary = ChatPalette.LightInkSecondary,
        inkTertiary = ChatPalette.LightInkTertiary,
        userBubble = ChatPalette.LightUserBubble,
        codeBg = ChatPalette.LightCodeBg,
        codeHeader = ChatPalette.LightCodeHeader,
        accent = ChatPalette.Accent,
        danger = ChatPalette.Danger,
        link = ChatPalette.LinkLight,
        isDark = false
    )

@Composable
@ReadOnlyComposable
fun chatColors(): ChatColorScheme = chatColors(isSystemInDarkTheme())
