package com.ahamai.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahamai.app.ProviderItem
import com.ahamai.app.ui.theme.InterFamily

@Composable
fun ProviderSelectionScreen(onProviderSelected: (ProviderItem) -> Unit) {
    val providers = listOf(
        // ─── Free, no-setup option first so it's the obvious default. ───
        

        ProviderItem(
            name = "Custom provider",
            description = "Use your own OpenAI-compatible endpoint",
            domain = null,
            defaultBaseUrl = "",
            isCustom = true
        ),
        ProviderItem(
            name = "OpenRouter",
            description = "Access many hosted models through one OpenAI-compatible API",
            domain = "openrouter.ai",
            defaultBaseUrl = "https://openrouter.ai/api/v1"
        ),
        ProviderItem(
            name = "Sarvam AI",
            description = "Indian LLMs with strong multilingual & Indic language support.",
            domain = "sarvam.ai",
            defaultBaseUrl = "https://api.sarvam.ai/v1"
        ),
        ProviderItem(
            name = "OpenCode Zen",
            description = "Free access to many top models (GPT, Claude, Gemini, DeepSeek).",
            domain = "opencode.ai",
            defaultBaseUrl = "https://opencode.ai/zen/v1"
        ),
        ProviderItem(
            name = "Ollama",
            description = "Hosted Ollama web API.",
            domain = "ollama.ai",
            defaultBaseUrl = "http://localhost:11434/v1"
        ),
        ProviderItem(
            name = "OpenAI",
            description = "Creator of GPT models, industry-leading AI.",
            domain = "openai.com",
            defaultBaseUrl = "https://api.openai.com/v1"
        ),
        ProviderItem(
            name = "Google AI Studio",
            description = "Multimodal AI with text, image, audio, and video.",
            domain = "aistudio.google.com",
            defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta/openai"
        ),
        ProviderItem(
            name = "Anthropic Claude",
            description = "Safety-focused with strong reasoning.",
            domain = "anthropic.com",
            defaultBaseUrl = "https://api.anthropic.com/v1"
        ),
        ProviderItem(
            name = "Groq",
            description = "Ultra-fast inference for open models.",
            domain = "groq.com",
            defaultBaseUrl = "https://api.groq.com/openai/v1"
        ),
        ProviderItem(
            name = "DeepSeek",
            description = "Strong reasoning and coding AI.",
            domain = "deepseek.com",
            defaultBaseUrl = "https://api.deepseek.com/v1"
        ),
        ProviderItem(
            name = "Mistral AI",
            description = "European open-weight models with strong multilingual support.",
            domain = "mistral.ai",
            defaultBaseUrl = "https://api.mistral.ai/v1"
        ),
        ProviderItem(
            name = "Cohere",
            description = "Enterprise-grade NLP and embeddings.",
            domain = "cohere.com",
            defaultBaseUrl = "https://api.cohere.ai/v1"
        ),
        ProviderItem(
            name = "Together AI",
            description = "Run open-source models with fast inference.",
            domain = "together.ai",
            defaultBaseUrl = "https://api.together.xyz/v1"
        ),
        ProviderItem(
            name = "Fireworks AI",
            description = "Blazing fast inference for open models.",
            domain = "fireworks.ai",
            defaultBaseUrl = "https://api.fireworks.ai/inference/v1"
        ),
        ProviderItem(
            name = "Perplexity",
            description = "AI-powered search and conversational answers.",
            domain = "perplexity.ai",
            defaultBaseUrl = "https://api.perplexity.ai"
        )
    )

    Column(modifier = Modifier.fillMaxSize()) {
        // Top app bar with AhamAI branding
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            com.ahamai.app.ui.components.AhamaiLogo(
                color = MaterialTheme.colorScheme.primary,
                fontSize = 22.sp
            )
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)) {
                            append("Here are some of\nthe providers you can\naccess through ")
                        }
                        withStyle(
                            style = com.ahamai.app.ui.components.ahamaiLogoSpanStyle(
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            append("ahamai")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp)) {
                            append(":")
                        }
                    },
                    modifier = Modifier.padding(bottom = 20.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(providers) { provider ->
                ProviderCard(
                    provider = provider,
                    onClick = { onProviderSelected(provider) }
                )
            }
        }

        BottomBar()
    }
}

@Composable
private fun ProviderCard(provider: ProviderItem, onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()

    if (provider.isCustom) {
        CustomProviderCard(isDark = isDark, onClick = onClick)
    } else {
        RegularProviderCard(provider = provider, isDark = isDark, onClick = onClick)
    }
}

@Composable
private fun CustomProviderCard(isDark: Boolean, onClick: () -> Unit) {
    val cardBackground = if (isDark) Color(0xFF1A1A2A) else Color(0xFFF0F0F5)
    val iconTint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = cardBackground
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Custom provider",
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Custom provider",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (isDark) Color.White else Color(0xFF0D0D0D)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Use your own OpenAI-compatible endpoint",
                    fontSize = 12.sp,
                    color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun RegularProviderCard(provider: ProviderItem, isDark: Boolean, onClick: () -> Unit) {
    val cardBackground = if (isDark) Color(0xFF1A1A2A) else Color(0xFFF0F0F5)
    val textColor = if (isDark) Color.White else Color(0xFF0D0D0D)
    val descriptionColor = if (isDark) Color(0xFFB0B0B0) else Color(0xFF6B7280)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = cardBackground
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val faviconUrl = "https://www.google.com/s2/favicons?domain=${provider.domain}&sz=64"

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = provider.name,
                modifier = Modifier.size(24.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = provider.description,
                    fontSize = 12.sp,
                    color = descriptionColor,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun BottomBar() {
    val mutedColor = if (isSystemInDarkTheme()) Color(0xFF9CA3AF) else Color(0xFF6B7280)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Keep as minimal text, but use Inter font
        Text(text = "Skip", fontSize = 14.sp, fontFamily = InterFamily, color = mutedColor, modifier = Modifier.clickable { })
        Text(text = "Need help choosing?", fontSize = 14.sp, fontFamily = InterFamily, color = mutedColor, modifier = Modifier.clickable { })
    }
}
