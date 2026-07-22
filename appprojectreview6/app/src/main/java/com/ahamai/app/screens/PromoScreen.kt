package com.ahamai.app.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.PoppinsRegular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Promotional screen shown after signup/login with iOS-like spring animations.
 * Displays credit top-up offers with bonus incentives.
 */
@Composable
fun PromoScreen(onDismiss: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    val bgColor = if (isDark) Color(0xFF0A0A0B) else Color(0xFFF5F3FF)
    val primary = if (isDark) Color.White else Color(0xFF111827)
    val secondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val muted = if (isDark) Color(0xFF4B5563) else Color(0xFF9CA3AF)
    val cardBg = if (isDark) Color(0xFF141418) else Color(0xFFF5F5F7)
    val cardBorder = if (isDark) Color(0xFF2A2A2E) else Color(0xFFE8E5F0)
    val accentPurple = Color(0xFF7C3AED)
    val accentPurpleLight = Color(0xFFA78BFA)
    val tagBg = if (isDark) Color(0xFF1E1B2E) else Color(0xFFF3EEFF)
    val tagText = Color(0xFF7C3AED)
    val bonusColor = Color(0xFF10B981)
    val gradientBg = if (isDark)
        Brush.verticalGradient(listOf(Color(0xFF0A0A0B), Color(0xFF0F0B1A), Color(0xFF0A0A0B)))
    else
        Brush.verticalGradient(listOf(Color(0xFFF5F3FF), Color(0xFFEDE9FE), Color(0xFFF5F3FF)))

    // ── iOS-like entrance animations ──
    val headerAlpha = remember { Animatable(0f) }
    val headerOffset = remember { Animatable(30f) }
    val giftScale = remember { Animatable(0f) }
    val card1Alpha = remember { Animatable(0f) }
    val card1Offset = remember { Animatable(60f) }
    val card2Alpha = remember { Animatable(0f) }
    val card2Offset = remember { Animatable(60f) }
    val card3Alpha = remember { Animatable(0f) }
    val card3Offset = remember { Animatable(60f) }
    val footerAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Staggered entrance like iOS
        launch { headerAlpha.animateTo(1f, tween(400, easing = EaseOutCubic)) }
        launch { headerOffset.animateTo(0f, tween(500, easing = EaseOutCubic)) }
        delay(150)
        launch { giftScale.animateTo(1f, tween(600, easing = EaseOutBack)) }
        delay(200)
        launch { card1Alpha.animateTo(1f, tween(350, easing = EaseOutCubic)) }
        launch { card1Offset.animateTo(0f, tween(450, easing = EaseOutCubic)) }
        delay(100)
        launch { card2Alpha.animateTo(1f, tween(350, easing = EaseOutCubic)) }
        launch { card2Offset.animateTo(0f, tween(450, easing = EaseOutCubic)) }
        delay(100)
        launch { card3Alpha.animateTo(1f, tween(350, easing = EaseOutCubic)) }
        launch { card3Offset.animateTo(0f, tween(450, easing = EaseOutCubic)) }
        delay(150)
        launch { footerAlpha.animateTo(1f, tween(300, easing = EaseOutCubic)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBg)
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))

            // ── Close button (top right) ──
            Box(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF1F1F23) else Color(0xFFEDE9FE))
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, null, tint = secondary, modifier = Modifier.size(15.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Header (animated) ──
            Column(
                modifier = Modifier
                    .alpha(headerAlpha.value)
                    .offset(y = headerOffset.value.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "One Time Offer",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFamily,
                    color = primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "You will never see this offer again",
                    fontSize = 13.sp,
                    fontFamily = InterFamily,
                    color = secondary
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Gift icon with bounce animation ──
            Box(
                modifier = Modifier
                    .scale(giftScale.value)
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFFA78BFA)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.CardGiftcard, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }

            Spacer(Modifier.height(8.dp))

            // New user bonus tag
            Surface(
                modifier = Modifier.scale(giftScale.value),
                shape = RoundedCornerShape(16.dp),
                color = tagBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Verified, null, tint = tagText, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "New User Bonus Included",
                        fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily, color = tagText
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Tier 1 (animated) ──
            Box(
                modifier = Modifier
                    .alpha(card1Alpha.value)
                    .offset(y = card1Offset.value.dp)
            ) {
                PromoOfferTier(
                    payAmount = "$2",
                    payAmountInr = "\u20B9200",
                    creditAmount = "$4",
                    creditAmountInr = "\u20B9400",
                    bonusAmount = "+$1",
                    bonusAmountInr = "+\u20B9100",
                    bonusLabel = "New User Bonus \uD83C\uDF89",
                    highlight = false,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primary = primary,
                    secondary = secondary,
                    bonusColor = bonusColor,
                    accentPurple = accentPurple
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Tier 2 (popular, animated) ──
            Box(
                modifier = Modifier
                    .alpha(card2Alpha.value)
                    .offset(y = card2Offset.value.dp)
            ) {
                PromoOfferTier(
                    payAmount = "$5",
                    payAmountInr = "\u20B9500",
                    creditAmount = "$10",
                    creditAmountInr = "\u20B91000",
                    bonusAmount = "+$1",
                    bonusAmountInr = "+\u20B9100",
                    bonusLabel = "Extra Bonus",
                    highlight = true,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primary = primary,
                    secondary = secondary,
                    bonusColor = bonusColor,
                    accentPurple = accentPurple
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Tier 3 (animated) ──
            Box(
                modifier = Modifier
                    .alpha(card3Alpha.value)
                    .offset(y = card3Offset.value.dp)
            ) {
                PromoOfferTier(
                    payAmount = "$10",
                    payAmountInr = "\u20B91000",
                    creditAmount = "$20",
                    creditAmountInr = "\u20B92000",
                    bonusAmount = "+$2",
                    bonusAmountInr = "+\u20B9200",
                    bonusLabel = "Extra Bonus",
                    highlight = false,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    primary = primary,
                    secondary = secondary,
                    bonusColor = bonusColor,
                    accentPurple = accentPurple
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Footer (animated) ──
            Column(
                modifier = Modifier.alpha(footerAlpha.value),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tags
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    PromoChip("No Hidden Charges", cardBg, cardBorder, secondary)
                    Spacer(Modifier.width(8.dp))
                    PromoChip("Cancel Anytime", cardBg, cardBorder, secondary)
                }

                Spacer(Modifier.height(20.dp))

                // CTA button
                val ctaInteraction = remember { MutableInteractionSource() }
                val ctaPressed by ctaInteraction.collectIsPressedAsState()
                val ctaScale by animateFloatAsState(
                    targetValue = if (ctaPressed) 0.95f else 1f,
                    animationSpec = tween(100),
                    label = "ctaScale"
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(ctaScale)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)))
                        )
                        .clickable(
                            interactionSource = ctaInteraction,
                            indication = null
                        ) { /* Demo - do nothing */ }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Claim Offer Now",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Skip
                Text(
                    text = "Maybe later",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    fontWeight = FontWeight.Medium,
                    color = muted,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Offer tier card ────────────────────────────────────────────────

@Composable
private fun PromoOfferTier(
    payAmount: String,
    payAmountInr: String,
    creditAmount: String,
    creditAmountInr: String,
    bonusAmount: String,
    bonusAmountInr: String,
    bonusLabel: String,
    highlight: Boolean,
    cardBg: Color,
    cardBorder: Color,
    primary: Color,
    secondary: Color,
    bonusColor: Color,
    accentPurple: Color
) {
    val borderColor = if (highlight) accentPurple else cardBorder
    val borderWidth = if (highlight) 1.5.dp else 0.8.dp

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        shadowElevation = if (highlight) 2.dp else 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: amount
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "Pay $payAmount",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFamily,
                            color = primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "/ $payAmountInr",
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            color = secondary
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Get $creditAmount ($creditAmountInr) credit worth",
                        fontSize = 11.sp,
                        fontFamily = InterFamily,
                        color = secondary
                    )
                }

                // Right: popular badge or arrow
                if (highlight) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accentPurple
                    ) {
                        Text(
                            "BEST",
                            fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            fontFamily = InterFamily, color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Bonus pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bonusColor.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.LocalOffer, null, tint = bonusColor, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "$bonusAmount ($bonusAmountInr) $bonusLabel",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = bonusColor
                    )
                }
            }
        }
    }
}

// ─── Small chip ─────────────────────────────────────────────────────

@Composable
private fun PromoChip(text: String, cardBg: Color, cardBorder: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, cardBorder)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontFamily = InterFamily,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}
