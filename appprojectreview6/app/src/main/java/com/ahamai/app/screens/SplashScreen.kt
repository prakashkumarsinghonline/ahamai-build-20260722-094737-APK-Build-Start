package com.ahamai.app.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.R
import com.ahamai.app.ui.theme.InterFamily
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Your branded splash: AhamAI icon + “Crafted with ❤️ in Bihar, India” watermark.
 *
 * Logo is fully visible on first frame (matches system splash) so there is no plain
 * solid screen after the OS splash dismisses. Only a soft scale settle + watermark fade.
 */
@Composable
fun SplashScreen() {
    val isDark = isSystemInDarkTheme()
    // Must match values/colors.xml splash_bg + MaterialTheme background
    val bg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val watermarkColor = if (isDark) Color(0xFF8E8E8E) else Color(0xFF6B6B6B)

    // Start fully visible — continuous with system splash logo, no blank flash
    val logoScale = remember { Animatable(0.96f) }
    val logoY = remember { Animatable(6f) }
    val footerAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                logoScale.animateTo(
                    1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                logoY.animateTo(
                    0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
            launch {
                // Watermark: icon first, then footer
                footerAlpha.animateTo(
                    1f,
                    animationSpec = tween(280, delayMillis = 80, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Image(
                painter = painterResource(R.drawable.ic_ahamai_logo),
                contentDescription = "AhamAI",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        translationY = logoY.value
                        clip = false
                    }
                    .clip(RoundedCornerShape(28.dp))
            )

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(bottom = 28.dp)
                    .graphicsLayer { alpha = footerAlpha.value }
            ) {
                Text(
                    "Crafted with ❤️ in Bihar, India",
                    fontSize = 11.sp,
                    fontFamily = InterFamily,
                    color = watermarkColor
                )
                Spacer(Modifier.width(5.dp))
                Text("🇮🇳", fontSize = 12.sp)
            }
        }
    }
}
