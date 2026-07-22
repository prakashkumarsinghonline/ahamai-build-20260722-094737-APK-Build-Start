package com.ahamai.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ahamai.app.ui.theme.UnicaOneRegular

/**
 * Canonical AhamAI wordmark — same style as Profile top bar:
 * lowercase "ahamai" · Unica One · Normal weight · 0.5sp tracking.
 */
@Composable
fun AhamaiLogo(
    color: Color,
    fontSize: TextUnit = 22.sp,
    letterSpacing: TextUnit = 0.5.sp,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    Text(
        text = "ahamai",
        fontSize = fontSize,
        fontWeight = FontWeight.Normal,
        fontFamily = UnicaOneRegular,
        color = color,
        letterSpacing = letterSpacing,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** Inline SpanStyle for placeholders like "Chat with ahamai…" */
fun ahamaiLogoSpanStyle(
    fontSize: TextUnit = 16.sp,
    color: Color = Color.Unspecified
): SpanStyle = SpanStyle(
    fontFamily = UnicaOneRegular,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.5.sp,
    fontSize = fontSize,
    color = color
)
