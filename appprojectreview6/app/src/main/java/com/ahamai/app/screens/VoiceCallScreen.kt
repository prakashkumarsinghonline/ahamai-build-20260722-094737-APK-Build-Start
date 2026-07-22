package com.ahamai.app.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import com.ahamai.app.ui.icons.Lucide
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ahamai.app.data.AssemblyAIVoiceAgent
import com.ahamai.app.ui.theme.InterFamily
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun VoiceCallScreen(
    onDismiss: () -> Unit,
    llmBaseUrl: String = "",
    llmApiKey: String = "",
    llmModel: String = "gpt-4o-mini"
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // ── App-standard neutral palette (chrome/text) ──
    val bg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
    val secondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF8E8E93)
    val muted = if (isDark) Color(0xFF6E6E73) else Color(0xFFC7C7CC)
    val iosRed = Color(0xFFFF3B30)

    // ── Orb palette: soft pastels that gently cycle through each other ──
    // Light, airy tones (no heavy saturation) — the orb morphs across these over time.
    val orbPalette = remember {
        listOf(
            Color(0xFFBFE3FF), // soft sky blue
            Color(0xFFD6C6FF), // lavender
            Color(0xFFFFC9E9), // soft pink
            Color(0xFFFFD9B8), // peach
            Color(0xFFC7F5DD), // mint
            Color(0xFFCFE0FF)  // periwinkle
        )
    }
    // Monochrome accent for the on-screen labels ("AI is speaking", "AI", icons) — no blue.
    val orbAccent = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)

    // ── State ──
    val agentState by AssemblyAIVoiceAgent.state.collectAsState()
    val userTranscript by AssemblyAIVoiceAgent.userTranscript.collectAsState()
    val agentText by AssemblyAIVoiceAgent.agentText.collectAsState()
    val audioLevel by AssemblyAIVoiceAgent.audioLevel.collectAsState()
    val error by AssemblyAIVoiceAgent.error.collectAsState()

    var isMuted by remember { mutableStateOf(false) }
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var callDuration by remember { mutableStateOf(0) }
    val scrollState = rememberScrollState()
    var selectedVoice by remember { mutableStateOf("ivy") }
    var showVoicePicker by remember { mutableStateOf(false) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) AssemblyAIVoiceAgent.start(voiceModel = selectedVoice)
    }

    LaunchedEffect(Unit) {
        if (hasPermission) AssemblyAIVoiceAgent.start(voiceModel = selectedVoice)
        else permLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Timer — only counts when actually connected
    LaunchedEffect(agentState) {
        if (agentState == AssemblyAIVoiceAgent.State.LISTENING || agentState == AssemblyAIVoiceAgent.State.AGENT_SPEAKING) {
            while (true) { delay(1000); callDuration++ }
        }
    }

    // Auto-scroll transcript
    LaunchedEffect(agentText, userTranscript) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    DisposableEffect(Unit) { onDispose { AssemblyAIVoiceAgent.stop() } }

    // ── Animations ──
    val inf = rememberInfiniteTransition(label = "voiceAnim")
    // Gentle idle breathing (subtle — the orb should feel calm, not pulse hard)
    val breathe by inf.animateFloat(
        0.97f, 1.03f,
        infiniteRepeatable(tween(3200, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "breathe"
    )
    // Slow rotation of the soft sheen
    val spin by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin"
    )
    // Colour-cycle phase (0..palette.size) — the orb morphs through the pastels
    val colorPhase by inf.animateFloat(
        0f, orbPalette.size.toFloat(),
        infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "colorPhase"
    )
    // Expanding ripple rings (very subtle, only while active/speaking)
    val ripple by inf.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple"
    )
    // Soft wobble so the orb never feels frozen
    val wobble by inf.animateFloat(
        0f, (2f * PI).toFloat(),
        infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "wobble"
    )

    // Current morphed colours (interpolated across the pastel palette).
    fun paletteAt(offset: Int): Color {
        val n = orbPalette.size
        val idx = (colorPhase.toInt() + offset) % n
        val nxt = (idx + 1) % n
        val f = colorPhase - colorPhase.toInt()
        return lerp(orbPalette[idx], orbPalette[nxt], f)
    }
    val colorA = paletteAt(0)
    val colorB = paletteAt(2)

    val isActive = agentState == AssemblyAIVoiceAgent.State.LISTENING || agentState == AssemblyAIVoiceAgent.State.AGENT_SPEAKING
    val isSpeaking = agentState == AssemblyAIVoiceAgent.State.AGENT_SPEAKING

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Top bar ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                if (callDuration > 0) {
                    Text(
                        "%d:%02d".format(callDuration / 60, callDuration % 60),
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Medium,
                        color = muted
                    )
                } else if (agentState == AssemblyAIVoiceAgent.State.CONNECTING) {
                    Text("Connecting…", fontSize = 12.sp, fontFamily = InterFamily, color = muted)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .offset(y = (-14).dp)
                        .clip(CircleShape)
                        .clickable { showVoicePicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Lucide.Sliders, null,
                        tint = secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.weight(0.4f))

            // ── Center: soft pastel orb that gently morphs colour ──
            val level = audioLevel.coerceIn(0f, 1f)
            // Sound reaction is deliberately SMALL so the orb stays calm, not jumpy.
            val react = when {
                isSpeaking -> level * 0.10f
                isActive -> level * 0.06f
                else -> 0f
            }
            // Idle-vs-active brightness of the surrounding glow.
            val glowStrength = when {
                isSpeaking -> 0.9f
                isActive -> 0.7f
                agentState == AssemblyAIVoiceAgent.State.CONNECTING -> 0.5f
                else -> 0.5f
            }

            Box(
                modifier = Modifier.size(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val center = Offset(cx, cy)
                    val minR = size.minDimension / 2f

                    // Subtle organic wobble so it "lives" (tiny).
                    val wob = 1f + 0.01f * sin(wobble.toDouble()).toFloat()
                    val orbR = minR * 0.40f * breathe * wob * (1f + react)

                    // 1) Outer soft glow halo in the current pastel colour ───────────
                    val haloR = (orbR * 1.55f).coerceAtMost(minR)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to colorA.copy(alpha = 0.28f * glowStrength),
                                0.55f to colorB.copy(alpha = 0.12f * glowStrength),
                                1.0f to Color.Transparent
                            ),
                            center = center,
                            radius = haloR
                        ),
                        radius = haloR,
                        center = center
                    )

                    // 2) Very subtle ripple rings while active/speaking ──────────────
                    if (isActive) {
                        for (k in 0 until 2) {
                            val t = ((ripple + k.toFloat() / 2f) % 1f)
                            val rR = orbR * (1f + t * 0.5f)
                            if (rR > minR) continue
                            val a = (1f - t) * 0.14f * glowStrength * (0.5f + level * 0.5f)
                            drawCircle(
                                color = colorA.copy(alpha = a.coerceIn(0f, 0.22f)),
                                radius = rR,
                                center = center,
                                style = Stroke(width = (1.6f + level * 1.6f))
                            )
                        }
                    }

                    // 3) Orb body — soft, light, two pastel tones (minimal gradient) ─
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.0f to Color.White.copy(alpha = 0.92f),
                                0.45f to lerp(Color.White, colorA, 0.7f),
                                1.0f to colorB
                            ),
                            center = Offset(cx - orbR * 0.16f, cy - orbR * 0.2f),
                            radius = orbR * 1.15f
                        ),
                        radius = orbR,
                        center = center
                    )
                    // Slow rotating sheen — gentle motion, low alpha (not "liquid busy").
                    rotate(degrees = spin, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colorB.copy(alpha = 0.28f),
                                    Color.Transparent,
                                    colorA.copy(alpha = 0.22f),
                                    Color.Transparent
                                ),
                                center = center
                            ),
                            radius = orbR,
                            center = center,
                            alpha = 0.55f
                        )
                    }

                    // 4) Glossy top highlight (soft 3D sheen) ───────────────────────
                    val hlCenter = Offset(cx - orbR * 0.3f, cy - orbR * 0.36f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.7f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = hlCenter,
                            radius = orbR * 0.85f
                        ),
                        radius = orbR,
                        center = center
                    )
                    // 5) Gentle inner glow that softly breathes with the voice ───────
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f + level * 0.28f * glowStrength),
                                Color.Transparent
                            ),
                            center = center,
                            radius = orbR * (0.55f + level * 0.2f)
                        ),
                        radius = orbR * 0.85f,
                        center = center
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Status label ──
            Text(
                when {
                    isSpeaking -> "AI is speaking…"
                    isActive && !isMuted -> "Listening…"
                    isMuted -> "Muted"
                    agentState == AssemblyAIVoiceAgent.State.CONNECTING -> "Connecting…"
                    agentState == AssemblyAIVoiceAgent.State.ERROR -> error ?: "Error"
                    else -> "Ready"
                },
                fontSize = 13.sp,
                fontFamily = InterFamily,
                fontWeight = FontWeight.Medium,
                color = when {
                    isSpeaking -> orbAccent
                    agentState == AssemblyAIVoiceAgent.State.ERROR -> iosRed
                    else -> secondary
                }
            )

            Spacer(Modifier.height(16.dp))

            // ── Live transcription ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 28.dp)
            ) {
                if (agentText.isNotBlank()) {
                    Text(
                        "AI",
                        fontSize = 10.sp,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        color = orbAccent,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        agentText,
                        fontSize = 15.sp,
                        fontFamily = InterFamily,
                        color = primary,
                        lineHeight = 22.sp
                    )
                    Spacer(Modifier.height(16.dp))
                }

                if (userTranscript.isNotBlank()) {
                    Text(
                        "YOU",
                        fontSize = 10.sp,
                        fontFamily = InterFamily,
                        fontWeight = FontWeight.Bold,
                        color = muted,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        userTranscript,
                        fontSize = 14.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        lineHeight = 20.sp
                    )
                }

                if (agentState == AssemblyAIVoiceAgent.State.ERROR) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        error ?: "Connection error",
                        fontSize = 11.sp,
                        fontFamily = InterFamily,
                        color = iosRed
                    )
                }
            }

            // ── Bottom controls ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 40.dp, top = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) primary.copy(alpha = 0.10f) else Color.Transparent)
                            .clickable { isMuted = !isMuted; AssemblyAIVoiceAgent.setMuted(isMuted) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                            null,
                            tint = if (isMuted) orbAccent else primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isMuted) "Unmute" else "Mute",
                        fontSize = 10.sp,
                        color = muted,
                        fontFamily = InterFamily
                    )
                }

                Spacer(Modifier.width(48.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(iosRed.copy(alpha = 0.12f))
                            .clickable { AssemblyAIVoiceAgent.stop(); onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CallEnd, null,
                            tint = iosRed,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "End",
                        fontSize = 10.sp,
                        color = muted,
                        fontFamily = InterFamily
                    )
                }
            }
        }

        // ── Voice picker overlay ──
        if (showVoicePicker) {
            val pickerScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { showVoicePicker = false }
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(if (isDark) Color(0xFF141414) else Color(0xFFF5F5F7))
                        .padding(top = 16.dp, bottom = 32.dp)
                ) {
                    Text(
                        "Voice",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = primary,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .verticalScroll(pickerScroll)
                    ) {
                        AssemblyAIVoiceAgent.VOICES.forEach { (id, label) ->
                            val isSel = id == selectedVoice
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedVoice = id
                                        showVoicePicker = false
                                        AssemblyAIVoiceAgent.stop()
                                        AssemblyAIVoiceAgent.start(voiceModel = id)
                                    }
                                    .padding(horizontal = 20.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontFamily = InterFamily,
                                    fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal,
                                    color = primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSel) {
                                    Icon(
                                        Icons.Filled.Check, null,
                                        tint = orbAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
