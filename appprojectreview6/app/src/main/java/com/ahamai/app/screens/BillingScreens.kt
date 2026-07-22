package com.ahamai.app.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.Plans
import com.ahamai.app.data.PreferencesManager
import com.ahamai.app.ui.components.AhamaiLogo
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import com.ahamai.app.ui.theme.UnicaOneRegular
import com.ahamai.app.ui.theme.chatColors

// ══════════════════════════════════════════════════════════════════════════════
// PRICING — clean creative cards, soft tinted headers, no icons
// Unica One · Inter · JetBrains Mono
// ══════════════════════════════════════════════════════════════════════════════

@Composable
fun PricingScreen(onBack: () -> Unit) {
    val c = chatColors()
    val context = LocalContext.current
    val prefs = remember { PreferencesManager(context) }

    var inr by remember { mutableStateOf(prefs.isCurrencyInr()) }
    var currentPlan by remember { mutableStateOf(prefs.getPlanId()) }
    val scroll = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize().background(c.bg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top bar ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "←",
                    fontSize = 20.sp,
                    fontFamily = InterFamily,
                    color = c.ink,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(CircleShape)
                        .background(c.surfaceAlt)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )

                AhamaiLogo(color = c.ink, fontSize = 18.sp)

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    CurrencyToggle(inr, c) {
                        inr = it
                        prefs.setCurrencyInr(it)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(6.dp))

                Text(
                    "Plans",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = UnicaOneRegular,
                    color = c.ink,
                    letterSpacing = 1.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Chat unlimited on every plan. Agent work uses tokens & APK builds.",
                    fontSize = 13.sp,
                    fontFamily = InterFamily,
                    color = c.inkSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "When capacity is high, paid users get queue priority.",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = c.inkTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(Modifier.height(20.dp))

                Plans.ALL.forEach { plan ->
                    val isCurrent = Plans.byId(currentPlan).id == plan.id
                    PlanCard(
                        plan = plan,
                        inr = inr,
                        isCurrent = isCurrent,
                        isDark = c.isDark,
                        appColors = c,
                        onChoose = {
                            prefs.setPlanId(plan.id)
                            currentPlan = plan.id
                        }
                    )
                    Spacer(Modifier.height(14.dp))
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Secure payments · Cancel anytime",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = c.inkTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 28.dp)
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PLAN CARD — soft header strip + stats + features + cute small CTA
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlanCard(
    plan: Plans.Plan,
    inr: Boolean,
    isCurrent: Boolean,
    isDark: Boolean,
    appColors: com.ahamai.app.ui.theme.ChatColorScheme,
    onChoose: () -> Unit
) {
    val palette = planHeaderPalette(plan.id, isDark)
    val cardShape = RoundedCornerShape(18.dp)
    val borderColor = if (plan.highlight) {
        palette.accent.copy(alpha = 0.45f)
    } else {
        appColors.border
    }
    val borderWidth = if (plan.highlight) 1.5.dp else 1.dp

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(cardShape)
            .border(borderWidth, borderColor, cardShape)
            .background(appColors.surfaceElevated)
    ) {
        // ── Soft tinted header ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(palette.headerTop, palette.headerBottom)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        plan.name,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = UnicaOneRegular,
                        color = palette.title,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.weight(1f))
                    if (plan.highlight) {
                        Text(
                            "popular",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFamily,
                            color = palette.accent,
                            letterSpacing = 0.4.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(palette.accent.copy(alpha = if (isDark) 0.22f else 0.14f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    plan.tagline,
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = palette.subtitle
                )

                Spacer(Modifier.height(12.dp))

                // Price
                Row(verticalAlignment = Alignment.Bottom) {
                    if (plan.isFree) {
                        Text(
                            "Free",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFamily,
                            color = palette.title
                        )
                    } else {
                        Text(
                            if (inr) "₹${plan.inr}" else "$${plan.usd}",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = JetBrainsMonoFamily,
                            color = palette.title
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "/mo",
                            fontSize = 13.sp,
                            fontFamily = InterFamily,
                            color = palette.subtitle,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }

        // ── Body ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Stat chips — mono numbers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    label = "tokens",
                    value = Plans.tokenLabel(plan.tokens),
                    accent = palette.accent,
                    surface = appColors.surfaceAlt,
                    ink = appColors.ink,
                    inkSec = appColors.inkSecondary,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "apk builds",
                    value = plan.apkBuilds.toString(),
                    accent = palette.accent,
                    surface = appColors.surfaceAlt,
                    ink = appColors.ink,
                    inkSec = appColors.inkSecondary,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            // Secondary stat line
            val queueLine = if (plan.priority) {
                "Priority queue when busy"
            } else {
                "Standard queue · paid first when busy"
            }
            Text(
                "Chat unlimited  ·  $queueLine",
                fontSize = 11.sp,
                fontFamily = InterFamily,
                color = appColors.inkTertiary,
                lineHeight = 15.sp
            )

            if (plan.security) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Security testing for web & APK included",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFamily,
                    color = palette.accent,
                    lineHeight = 15.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(appColors.border.copy(alpha = 0.7f))
            )
            Spacer(Modifier.height(10.dp))

            val visible = if (expanded) plan.features else plan.features.take(4)
            visible.forEach { feature ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        "·",
                        fontSize = 14.sp,
                        fontFamily = InterFamily,
                        color = palette.accent,
                        modifier = Modifier.padding(end = 8.dp, top = 0.dp)
                    )
                    Text(
                        feature,
                        fontSize = 13.sp,
                        fontFamily = InterFamily,
                        color = appColors.ink,
                        lineHeight = 18.sp
                    )
                }
            }

            if (plan.features.size > 4) {
                Spacer(Modifier.height(6.dp))
                Text(
                    if (expanded) "show less" else "show all ${plan.features.size}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFamily,
                    color = palette.accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
                )
            }

            Spacer(Modifier.height(14.dp))

            // Cute small CTA — not full-width giant
            val ctaLabel = when {
                isCurrent -> "Current"
                plan.isFree -> "Start free"
                else -> "Get ${plan.name}"
            }
            val ctaBg = when {
                isCurrent -> appColors.surfaceAlt
                plan.highlight -> palette.accent
                else -> appColors.ink
            }
            val ctaFg = when {
                isCurrent -> appColors.inkSecondary
                plan.highlight -> Color.White
                else -> if (isDark) Color(0xFF0C0C0E) else Color.White
            }

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(ctaBg)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onChoose
                        )
                        .padding(horizontal = 22.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        ctaLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = ctaFg
                    )
                }
            }

            if (isCurrent) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "your active plan",
                    fontSize = 11.sp,
                    fontFamily = InterFamily,
                    color = appColors.inkTertiary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    accent: Color,
    surface: Color,
    ink: Color,
    inkSec: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = JetBrainsMonoFamily,
            color = accent
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            fontSize = 11.sp,
            fontFamily = InterFamily,
            color = inkSec
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// CURRENCY TOGGLE — compact pill
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun CurrencyToggle(
    inr: Boolean,
    appColors: com.ahamai.app.ui.theme.ChatColorScheme,
    onToggle: (Boolean) -> Unit
) {
    val bg = appColors.surfaceAlt
    val activeBg = appColors.surfaceElevated
    val activeText = appColors.ink
    val inactiveText = appColors.inkTertiary

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onToggle(!inr) }
            )
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(false to "USD", true to "INR").forEach { (isInr, label) ->
            val active = inr == isInr
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) activeBg else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    fontFamily = JetBrainsMonoFamily,
                    color = if (active) activeText else inactiveText
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// PER-PLAN SOFT HEADER PALETTES
// ══════════════════════════════════════════════════════════════════════════════

private data class PlanHeaderPalette(
    val headerTop: Color,
    val headerBottom: Color,
    val title: Color,
    val subtitle: Color,
    val accent: Color
)

private fun planHeaderPalette(id: String, isDark: Boolean): PlanHeaderPalette = when (id) {
    "free" -> if (isDark) PlanHeaderPalette(
        headerTop = Color(0xFF1A2430),
        headerBottom = Color(0xFF141A22),
        title = Color(0xFFE8F0F8),
        subtitle = Color(0xFF9AADC0),
        accent = Color(0xFF7EB6E8)
    ) else PlanHeaderPalette(
        headerTop = Color(0xFFE8F2FC),
        headerBottom = Color(0xFFF4F8FD),
        title = Color(0xFF1A2B3C),
        subtitle = Color(0xFF5A7088),
        accent = Color(0xFF4A90C8)
    )
    "pro" -> if (isDark) PlanHeaderPalette(
        headerTop = Color(0xFF0F2438),
        headerBottom = Color(0xFF121C28),
        title = Color(0xFFE6F2FF),
        subtitle = Color(0xFF8FB4D8),
        accent = Color(0xFF5AA9FF)
    ) else PlanHeaderPalette(
        headerTop = Color(0xFFDCEEFF),
        headerBottom = Color(0xFFEEF6FF),
        title = Color(0xFF0A2A4A),
        subtitle = Color(0xFF4A7090),
        accent = Color(0xFF0A84FF)
    )
    "plus" -> if (isDark) PlanHeaderPalette(
        headerTop = Color(0xFF1E1A32),
        headerBottom = Color(0xFF16141F),
        title = Color(0xFFF0ECFF),
        subtitle = Color(0xFFA89BC8),
        accent = Color(0xFFA78BFA)
    ) else PlanHeaderPalette(
        headerTop = Color(0xFFEDE8FF),
        headerBottom = Color(0xFFF6F3FF),
        title = Color(0xFF2A1F4A),
        subtitle = Color(0xFF6B5F8A),
        accent = Color(0xFF6D5AE6)
    )
    "enterprise" -> if (isDark) PlanHeaderPalette(
        headerTop = Color(0xFF241828),
        headerBottom = Color(0xFF18141C),
        title = Color(0xFFF8ECF4),
        subtitle = Color(0xFFC0A0B8),
        accent = Color(0xFFE8A0C8)
    ) else PlanHeaderPalette(
        headerTop = Color(0xFFF8E8F0),
        headerBottom = Color(0xFFFDF4F8),
        title = Color(0xFF3A1A2C),
        subtitle = Color(0xFF8A5A72),
        accent = Color(0xFFC45A90)
    )
    else -> if (isDark) PlanHeaderPalette(
        headerTop = Color(0xFF1C1C20),
        headerBottom = Color(0xFF141416),
        title = Color(0xFFECECEC),
        subtitle = Color(0xFF9A9AA2),
        accent = Color(0xFF0A84FF)
    ) else PlanHeaderPalette(
        headerTop = Color(0xFFF0F0F2),
        headerBottom = Color(0xFFFAFAFA),
        title = Color(0xFF141414),
        subtitle = Color(0xFF6B6B6B),
        accent = Color(0xFF0A84FF)
    )
}
