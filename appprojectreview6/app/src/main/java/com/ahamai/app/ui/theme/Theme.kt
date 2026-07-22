package com.ahamai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Material3 schemes mapped to [ChatPalette]:
 *  - Light ≈ ChatGPT light (white page, soft gray surfaces)
 *  - Dark  ≈ AMOLED pure black + elevated charcoal chrome
 */
private val AmoledDarkColorScheme = darkColorScheme(
    primary = ChatPalette.Accent,
    onPrimary = Color.White,
    secondary = ChatPalette.DarkInkSecondary,
    onSecondary = ChatPalette.DarkInk,
    tertiary = ChatPalette.DarkInkTertiary,
    background = ChatPalette.DarkBg,
    onBackground = ChatPalette.DarkInk,
    surface = ChatPalette.DarkBg,
    onSurface = ChatPalette.DarkInk,
    surfaceVariant = ChatPalette.DarkSurfaceElevated,
    onSurfaceVariant = ChatPalette.DarkInkSecondary,
    outline = ChatPalette.DarkBorder,
    outlineVariant = ChatPalette.DarkBorderStrong,
    inverseSurface = ChatPalette.LightSurface,
    inverseOnSurface = ChatPalette.LightInk,
    error = ChatPalette.Danger,
    onError = Color.White
)

private val ChatGptLightColorScheme = lightColorScheme(
    primary = ChatPalette.Accent,
    onPrimary = Color.White,
    secondary = ChatPalette.LightInkSecondary,
    onSecondary = ChatPalette.LightInk,
    tertiary = ChatPalette.LightInkTertiary,
    background = ChatPalette.LightBg,
    onBackground = ChatPalette.LightInk,
    surface = ChatPalette.LightBg,
    onSurface = ChatPalette.LightInk,
    surfaceVariant = ChatPalette.LightSurface,
    onSurfaceVariant = ChatPalette.LightInkSecondary,
    outline = ChatPalette.LightBorder,
    outlineVariant = ChatPalette.LightBorderStrong,
    inverseSurface = ChatPalette.DarkSurfaceElevated,
    inverseOnSurface = ChatPalette.DarkInk,
    error = ChatPalette.Danger,
    onError = Color.White
)

/**
 * [forceDarkMode]: null = follow system, true = force dark, false = force light.
 * Used by the Appearance picker in ProfileScreen.
 */
@Composable
fun HelloKotlinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    forceDarkMode: Boolean? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val effectiveDark = forceDarkMode ?: darkTheme
    val colorScheme = if (effectiveDark) AmoledDarkColorScheme else ChatGptLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AhamTypography
    ) {
        // Universal default font: any Text without an explicit fontFamily inherits Inter,
        // so the whole app reads as one typeface. Explicit families (JetBrains Mono for code,
        // logo fonts) still override this locally.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = InterFamily)
        ) {
            content()
        }
    }
}
