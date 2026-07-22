package com.ahamai.app.screens

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.PreferencesManager
import com.ahamai.app.ui.chat.rememberChatHaptics
import com.ahamai.app.ui.flow.FlowEnter
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Swipeable onboarding — a faithful adaptation of Trae's 3-page onboarding
 * (com.bytedance.trae.home.onboarding.OnboardingFragment), extended to 4 pages
 * for AhamAI's four headline capabilities.
 *
 * Trae reference (decompiled):
 *  - ViewPager with N pages: each page = a hero illustration + a two-part title
 *    (normal line + an italic serif line) + a button.
 *  - Thin "underline pill" indicators: active 32x2dp, inactive 8x2dp.
 *  - Button text: "Continue" on every page, "Let's Go" on the last.
 *  - Warm off-white background (#F7F6F5) / near-black dark (#171717).
 *
 * The hero art is built natively in Compose (Trae's own PNGs carry TRAE branding,
 * so they can't be reused) — same visual language: dotted gradient sky, a device/
 * card mockup, a floating accent badge, a status toast, and a voice-waveform pill.
 */

private data class OnbPage(
    val titleNormal: String,
    val titleItalic: String,
    val subtitle: String,
    val accent: Color,
    val kind: Int,            // which hero illustration to draw
    val buttonText: String,
    val isLast: Boolean
)

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferencesManager(context) }

    // ── Palette — same light/dark page bg as chat history / agent ──
    val bg            = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val titleColor    = if (isDark) Color(0xFFFAFAFA) else Color(0xFF0B0B0A)
    val subtitleColor = if (isDark) Color(0xFF8A8988) else Color(0xFF6B6B6A)
    // Brand orange (same as app icon) for Continue / Let's Go
    val btnBg         = Color(0xFFFC7F02)
    val btnText       = Color(0xFFFFFFFF)
    val dotActive     = if (isDark) Color(0xFFE5E5E5) else Color(0xFF1B1B1A)
    val dotInactive   = if (isDark) Color(0x4DE5E5E5) else Color(0x4D8A8988)

    val pages = remember {
        listOf(
            OnbPage(
                titleNormal = "Build Real",
                titleItalic = "Android Apps",
                subtitle = "Describe an app — AI writes it and compiles a real APK for you.",
                accent = Color(0xFF4B3FE3),
                kind = 0,
                buttonText = "Continue",
                isLast = false
            ),
            OnbPage(
                titleNormal = "Browse & Act on",
                titleItalic = "Any Website",
                subtitle = "A real cloud browser the AI drives — it logs in, fills forms & scrapes sites.",
                accent = Color(0xFF0EA5E9),
                kind = 5,
                buttonText = "Continue",
                isLast = false
            ),
            OnbPage(
                titleNormal = "Scan & Secure",
                titleItalic = "Sites & Apps",
                subtitle = "Recon, CVE & port scans, SQLi/XSS, plus APK & mobile-app audits.",
                accent = Color(0xFF8B5CF6),
                kind = 1,
                buttonText = "Continue",
                isLast = false
            ),
            OnbPage(
                titleNormal = "Create & Read",
                titleItalic = "Any Document",
                subtitle = "PDF, Word, Excel, PowerPoint & CSV — generated in one tap.",
                accent = Color(0xFFEA580C),
                kind = 2,
                buttonText = "Continue",
                isLast = false
            ),
            OnbPage(
                titleNormal = "Run Code in a",
                titleItalic = "Cloud Sandbox",
                subtitle = "A full Linux machine — run commands and install any tool instantly.",
                accent = Color(0xFF10B981),
                kind = 3,
                buttonText = "Continue",
                isLast = false
            ),
            OnbPage(
                titleNormal = "Chat Free,",
                titleItalic = "Forever",
                subtitle = "Free AI chat with live web search, charts & diagrams built in.",
                accent = Color(0xFF3F85FF),
                kind = 4,
                buttonText = "Let's Go",
                isLast = true
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val current = pagerState.currentPage
    val haptics = rememberChatHaptics()

    fun finish() {
        haptics.success()
        prefs.setOnboardingCompleted()
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        FlowEnter(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Skip (top-right) — hidden on the last page ──
                Box(modifier = Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.TopEnd) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !pages[current].isLast,
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(160))
                    ) {
                        Text(
                            "Skip",
                            fontSize = 15.sp,
                            fontFamily = InterFamily,
                            color = subtitleColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable {
                                    haptics.tick()
                                    finish()
                                }
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                // ── Swipeable pages — parallax + fade at page edges ──
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) { page ->
                    val p = pages[page]
                    val pageOffset = (
                        (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        ).absoluteValue.coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .graphicsLayer {
                                // Soft depth while swiping between pages
                                val t = 1f - pageOffset * 0.12f
                                alpha = 1f - pageOffset * 0.35f
                                scaleX = t
                                scaleY = t
                                translationY = pageOffset * 18f
                            },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            OnboardingHero(kind = p.kind, accent = p.accent, isDark = isDark)
                        }
                        Spacer(Modifier.height(24.dp))
                        Text(
                            text = p.titleNormal,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFamily,
                            color = titleColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 36.sp
                        )
                        Text(
                            text = p.titleItalic,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            fontStyle = FontStyle.Italic,
                            color = p.accent,
                            textAlign = TextAlign.Center,
                            lineHeight = 40.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = p.subtitle,
                            fontSize = 14.sp,
                            fontFamily = InterFamily,
                            color = subtitleColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Thin pill indicators (springy Trae-style) ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { i ->
                        val active = i == current
                        val width by animateDpAsState(
                            if (active) 32.dp else 8.dp,
                            spring(dampingRatio = 0.82f, stiffness = 380f),
                            label = "dotW"
                        )
                        val alpha by animateFloatAsState(
                            if (active) 1f else 0.55f,
                            tween(220),
                            label = "dotA"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .width(width)
                                .height(3.dp)
                                .graphicsLayer { this.alpha = alpha }
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) dotActive else dotInactive)
                        )
                    }
                }

                Spacer(Modifier.height(28.dp))

                // ── Continue / Let's Go — press scale + haptic + label crossfade ──
                val btnInteraction = remember { MutableInteractionSource() }
                val btnPressed by btnInteraction.collectIsPressedAsState()
                val btnScale by animateFloatAsState(
                    targetValue = if (btnPressed) 0.94f else 1f,
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 480f),
                    label = "onbBtn"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.56f)
                        .height(48.dp)
                        .scale(btnScale)
                        .clip(RoundedCornerShape(24.dp))
                        .background(btnBg)
                        .clickable(
                            interactionSource = btnInteraction,
                            indication = null
                        ) {
                            if (pages[current].isLast) {
                                finish()
                            } else {
                                haptics.tick()
                                scope.launch {
                                    pagerState.animateScrollToPage(
                                        current + 1,
                                        animationSpec = spring(
                                            dampingRatio = 0.9f,
                                            stiffness = 280f
                                        )
                                    )
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = pages[current].buttonText,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)))
                                .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.92f, animationSpec = tween(120)))
                        },
                        label = "onbBtnText"
                    ) { label ->
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFamily,
                            color = btnText
                        )
                    }
                }
            }
        }
    }
}

/* ─────────────────────────── Hero illustrations ─────────────────────────── */

@Composable
private fun OnboardingHero(kind: Int, accent: Color, isDark: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 360.dp),
        contentAlignment = Alignment.Center
    ) {
        // Dotted gradient "sky" behind every page.
        DottedSky(accent = accent, isDark = isDark)
        when (kind) {
            0 -> HeroBuild(accent, isDark)
            1 -> HeroSecurity(accent, isDark)
            2 -> HeroDocs(accent, isDark)
            3 -> HeroCloud(accent, isDark)
            5 -> HeroBrowser(accent, isDark)
            else -> HeroChat(accent, isDark)
        }
    }
}

/** A soft dotted background that fades from the accent tint at the top to nothing. */
@Composable
private fun DottedSky(accent: Color, isDark: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val gap = 22f
        val r = 2.2f
        val cols = (size.width / gap).toInt() + 1
        val rows = (size.height / gap).toInt() + 1
        for (yi in 0..rows) {
            val y = yi * gap
            val fade = (1f - (y / size.height)).coerceIn(0f, 1f)
            val a = 0.30f * fade
            if (a <= 0.01f) continue
            for (xi in 0..cols) {
                drawCircle(
                    color = accent.copy(alpha = a),
                    radius = r,
                    center = androidx.compose.ui.geometry.Offset(xi * gap, y)
                )
            }
        }
    }
}

/** A rounded "card/device" surface used inside the hero illustrations. */
@Composable
private fun MockCard(
    modifier: Modifier = Modifier,
    isDark: Boolean,
    cornerRadius: Int = 22,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(if (isDark) Color(0xFF141414) else Color(0xFFF4F4F6))
            .border(1.dp, if (isDark) Color(0x14FFFFFF) else Color(0x0F000000), RoundedCornerShape(cornerRadius.dp)),
        content = content
    )
}

/** A small floating circular badge with an icon (the accent action-circle). */
@Composable
private fun FloatingBadge(icon: ImageVector, accent: Color, modifier: Modifier = Modifier, size: Int = 46) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(accent),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size((size * 0.5).dp))
    }
}

/** A small status "toast" chip (e.g., "APK ready", "Task completed"). */
@Composable
private fun StatusToast(text: String, accent: Color, isDark: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isDark) Color(0xFF26262A) else Color(0xFFF4F4F6))
            .border(1.dp, if (isDark) Color(0x14FFFFFF) else Color(0x0F000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.CheckCircle, null, tint = accent, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.sp, fontFamily = InterFamily, color = if (isDark) Color(0xFFE5E5E5) else Color(0xFF141414))
    }
}

/** Voice-waveform pill with an indigo mic button on the end (Trae's signature). */
@Composable
private fun WaveformPill(accent: Color, isDark: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (isDark) Color(0xFF26262A) else Color(0xFFF4F4F6))
            .border(1.dp, if (isDark) Color(0x14FFFFFF) else Color(0x0F000000), RoundedCornerShape(24.dp))
            .padding(start = 16.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.width(70.dp).height(22.dp)) {
            val bars = 11
            val step = size.width / bars
            val heights = listOf(0.3f, 0.6f, 0.9f, 0.5f, 1f, 0.7f, 0.4f, 0.85f, 0.55f, 0.3f, 0.6f)
            for (i in 0 until bars) {
                val h = size.height * heights[i % heights.size]
                val x = i * step + step / 2
                drawLine(
                    color = accent.copy(alpha = 0.85f),
                    start = androidx.compose.ui.geometry.Offset(x, (size.height - h) / 2),
                    end = androidx.compose.ui.geometry.Offset(x, (size.height + h) / 2),
                    strokeWidth = 3f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        FloatingBadge(Icons.Outlined.Code, accent, size = 34)
    }
}

/* ---- Page 1: Build apps → APK ---- */
@Composable
private fun HeroBuild(accent: Color, isDark: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // Phone mockup with code lines
        MockCard(
            modifier = Modifier.width(150.dp).height(280.dp),
            isDark = isDark
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(5.dp))
                    Box(Modifier.width(46.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent.copy(alpha = 0.25f)))
                }
                Spacer(Modifier.height(16.dp))
                val widths = listOf(0.9f, 0.6f, 0.75f, 0.5f, 0.85f, 0.4f, 0.7f)
                widths.forEach { w ->
                    Box(Modifier.fillMaxWidth(w).height(7.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (isDark) Color(0xFF3A3A3E) else Color(0xFFE8E8EC)))
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        // Floating Android badge
        FloatingBadge(Icons.Outlined.Android, accent, modifier = Modifier.align(Alignment.TopEnd).padding(end = 36.dp, top = 30.dp))
        // APK ready toast
        StatusToast("APK ready", accent, isDark, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
    }
}

/* ---- Page 2: Security tools ---- */
@Composable
private fun HeroSecurity(accent: Color, isDark: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // Dark terminal card with scan output
        MockCard(modifier = Modifier.width(260.dp).height(180.dp), isDark = true, cornerRadius = 18) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Terminal, null, tint = accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("nuclei -scan", fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFF9CA3AF))
                }
                Spacer(Modifier.height(12.dp))
                listOf(0.85f, 0.6f, 0.95f, 0.7f).forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(">", fontSize = 10.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFF22C55E))
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.fillMaxWidth(w).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF22C55E).copy(alpha = 0.35f)))
                    }
                    Spacer(Modifier.height(9.dp))
                }
            }
        }
        FloatingBadge(Icons.Outlined.Lock, accent, modifier = Modifier.align(Alignment.TopStart).padding(start = 28.dp, top = 18.dp))
        StatusToast("Vulnerability found", accent, isDark, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 20.dp))
        FloatingBadge(Icons.Outlined.BugReport, accent, modifier = Modifier.align(Alignment.BottomStart).padding(start = 24.dp, bottom = 30.dp), size = 38)
    }
}

/* ---- Page 3: Documents (create & read PDF/Word/Excel/PPT/CSV) ---- */
@Composable
private fun HeroDocs(accent: Color, isDark: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // A clean fanned stack of three document cards.
        DocCard(Icons.Outlined.GridOn, "XLSX", Color(0xFF22A06B), isDark,
            Modifier.rotate(-12f).offset(x = (-70).dp, y = 12.dp))
        DocCard(Icons.Outlined.Slideshow, "PPTX", Color(0xFFE8590C), isDark,
            Modifier.rotate(11f).offset(x = 70.dp, y = 12.dp))
        DocCard(Icons.Outlined.PictureAsPdf, "PDF", Color(0xFFE03131), isDark,
            Modifier.offset(y = (-10).dp))
        StatusToast("Document ready", accent, isDark, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
    }
}

/** A single document card (icon + file-type label + a couple of text lines). */
@Composable
private fun DocCard(icon: ImageVector, label: String, tint: Color, isDark: Boolean, modifier: Modifier = Modifier) {
    MockCard(modifier = modifier.width(116.dp).height(150.dp), isDark = isDark, cornerRadius = 16) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(10.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = InterFamily, color = tint)
            Spacer(Modifier.height(10.dp))
            listOf(0.95f, 0.7f, 0.85f, 0.5f).forEach { w ->
                Box(Modifier.fillMaxWidth(w).height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(if (isDark) Color(0xFF3A3A3E) else Color(0xFFE8E8EC)))
                Spacer(Modifier.height(7.dp))
            }
        }
    }
}

/* ---- Page 4: Cloud / run anywhere ---- */
@Composable
private fun HeroCloud(accent: Color, isDark: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // Central cloud node
        Box(
            modifier = Modifier.size(110.dp).clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            FloatingBadge(Icons.Outlined.Cloud, accent, size = 64)
        }
        // Connected device chips around the cloud
        FloatingBadge(Icons.Outlined.Terminal, accent, modifier = Modifier.align(Alignment.TopStart).padding(start = 30.dp, top = 24.dp), size = 40)
        FloatingBadge(Icons.Outlined.Language, accent, modifier = Modifier.align(Alignment.TopEnd).padding(end = 30.dp, top = 30.dp), size = 40)
        FloatingBadge(Icons.Outlined.Android, accent, modifier = Modifier.align(Alignment.BottomStart).padding(start = 36.dp, bottom = 40.dp), size = 40)
        // Waveform pill bottom
        WaveformPill(accent, isDark, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
        StatusToast("Task completed", accent, isDark, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 70.dp))
    }
}

/* ---- Page 2: Agentic browser ---- */
@Composable
private fun HeroBrowser(accent: Color, isDark: Boolean) {
    val line = if (isDark) Color(0xFF3A3A3E) else Color(0xFFD9D9DE)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        MockCard(modifier = Modifier.width(252.dp).height(208.dp), isDark = isDark, cornerRadius = 18) {
            Column(modifier = Modifier.fillMaxSize()) {
                // URL bar — globe + address pill (no dots)
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Language, null, tint = accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.weight(1f).height(20.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF1A1A1C) else Color(0xFFF0F0F2)),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(Modifier.padding(start = 10.dp).fillMaxWidth(0.5f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(line))
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(line))
                // Page content + a highlighted numbered element (the "act by index" idea) + a cursor
                Box(Modifier.fillMaxSize().padding(14.dp)) {
                    Column {
                        Box(Modifier.fillMaxWidth(0.65f).height(8.dp).clip(RoundedCornerShape(3.dp)).background(line))
                        Spacer(Modifier.height(11.dp))
                        Box(Modifier.fillMaxWidth(0.95f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(line))
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(line))
                        Spacer(Modifier.height(18.dp))
                        Box(contentAlignment = Alignment.CenterStart) {
                            Box(
                                Modifier.width(116.dp).height(34.dp).clip(RoundedCornerShape(9.dp))
                                    .background(accent.copy(alpha = 0.14f))
                                    .border(1.5.dp, accent, RoundedCornerShape(9.dp))
                            )
                            Row(Modifier.padding(start = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(accent), contentAlignment = Alignment.Center) {
                                    Text("2", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = InterFamily)
                                }
                                Spacer(Modifier.width(7.dp))
                                Text("Sign in", fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily,
                                    color = if (isDark) Color(0xFFE5E5E5) else Color(0xFF141414))
                            }
                        }
                    }
                    Icon(
                        Icons.Outlined.TouchApp, null,
                        tint = if (isDark) Color.White else Color(0xFF141414),
                        modifier = Modifier.align(Alignment.BottomStart).offset(x = 96.dp, y = (-4).dp).size(22.dp)
                    )
                }
            }
        }
        StatusToast("Logged in", accent, isDark, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
    }
}

/* ---- Page 6: Always-free chat — clean chat bubbles with a real comment ---- */
@Composable
private fun HeroChat(accent: Color, isDark: Boolean) {
    val aiBubble = if (isDark) Color(0xFF26262A) else Color(0xFFEFEFF1)
    val aiText = if (isDark) Color(0xFFE5E5E5) else Color(0xFF141414)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.width(256.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // User question bubble (right, accent)
            Box(
                modifier = Modifier.align(Alignment.End).widthIn(max = 190.dp)
                    .clip(RoundedCornerShape(18.dp)).background(accent).padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text("What can AhamAI do for me?", fontSize = 13.sp, fontFamily = InterFamily, color = Color.White, lineHeight = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            // AI reply bubble (left, grey) with a capability chip + a real comment
            Box(
                modifier = Modifier.align(Alignment.Start).widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(18.dp)).background(aiBubble).padding(14.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, null, tint = accent, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(5.dp))
                        com.ahamai.app.ui.components.AhamaiLogo(
                            color = accent,
                            fontSize = 10.sp
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "I build apps, scan sites, drive a real browser & chat free — all in one place ✨",
                        fontSize = 13.sp, fontFamily = InterFamily, color = aiText, lineHeight = 19.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            StatusToast("Always free", accent, isDark)
        }
    }
}