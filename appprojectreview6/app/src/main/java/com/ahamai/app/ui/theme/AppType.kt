package com.ahamai.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ahamai.app.R

// ── Brand / logo faces (KEEP for AhamAI wordmark & splash — do not use for body) ──

/** Poppins — geometric sans used for the AhamAI brand logo. */
val PoppinsRegular = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
)

/** Audiowide — futuristic display font used for the AhamAI brand logo. */
val AudiowideFamily = FontFamily(
    Font(R.font.audiowide_regular, FontWeight.Normal),
)

/** Outfit — modern geometric sans for the AhamAI logo (Light 300). */
val OutfitFamily = FontFamily(
    Font(R.font.outfit_light, FontWeight.Light),
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold),
)

/** Unica One — condensed unicase sans used for the AhamAI brand logo / splash. */
val UnicaOneRegular = FontFamily(
    Font(R.font.unicaone_regular, FontWeight.Normal),
)

// ── Product UI (ChatGPT-aligned: Söhne → Inter as free screen-native stand-in) ──

/**
 * Inter — product UI sans (ChatGPT uses Klim Söhne; Inter is the closest free screen font).
 * All chat, agent, settings, and chrome text should use this.
 */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** JetBrains Mono — code blocks (ChatGPT uses Söhne Mono; JB Mono is the app mono). */
val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
)

// Legacy aliases — map old decorative UI faces to Inter so call sites keep compiling
// without pulling Raleway/Montserrat/OpenSans into body copy (ChatGPT-like unity).
val MontserratFamily = InterFamily
val OpenSansFamily = InterFamily
val RalewayFamily = InterFamily
val OswaldFamily = InterFamily

/**
 * ChatGPT-inspired type scale (web chat.openai.com–style hierarchy).
 * Body ≈ 16px / ~1.75 lh; chrome 12–14px; empty-state hero 28–32px.
 */
object ChatType {
    // Message body — readable on phone with side margins (not edge-to-edge)
    val body = 16.5.sp
    val bodyLine = 26.sp

    // Secondary UI (sidebar titles, list rows, buttons)
    val sm = 13.5.sp
    val smLine = 19.sp

    // Meta / captions / timestamps / helper
    val xs = 12.sp
    val xsLine = 16.sp

    // Micro labels (chips, badges)
    val xxs = 11.sp

    // Code blocks
    val code = 13.5.sp
    val codeLine = 20.sp

    // Markdown headings
    val h1 = 22.sp
    val h2 = 19.sp
    val h3 = 17.sp

    // Empty-state / marketing hero (not logo)
    val hero = 28.sp
    val heroSub = 15.sp

    // Composer input
    val input = 16.sp
    val inputLine = 24.sp
}

/**
 * Material3 typography — Inter throughout (ChatGPT product stack).
 * Logo screens still pass [UnicaOneRegular] / [OutfitFamily] explicitly.
 */
val AhamTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    titleSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = ChatType.body,
        lineHeight = ChatType.bodyLine,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = ChatType.sm,
        lineHeight = ChatType.smLine,
        letterSpacing = 0.15.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = ChatType.xs,
        lineHeight = ChatType.xsLine,
        letterSpacing = 0.2.sp
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = ChatType.sm,
        lineHeight = ChatType.smLine,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = ChatType.xs,
        lineHeight = ChatType.xsLine,
        letterSpacing = 0.3.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = ChatType.xxs,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    ),
)
