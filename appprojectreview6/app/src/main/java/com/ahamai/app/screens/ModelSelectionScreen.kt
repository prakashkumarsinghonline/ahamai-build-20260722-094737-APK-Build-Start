package com.ahamai.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.ApiClient
import com.ahamai.app.ui.theme.InterFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun getModelIconDomain(modelId: String, providerDomain: String?): String {
    val lower = modelId.lowercase()
    return when {
        lower.contains("gpt") || lower.contains("openai") || lower.startsWith("o1") || lower.startsWith("o3") || lower.startsWith("o4") -> "openai.com"
        lower.contains("claude") || lower.contains("anthropic") -> "anthropic.com"
        lower.contains("gemini") || lower.contains("google") -> "google.com"
        lower.contains("llama") || lower.contains("meta") -> "meta.ai"
        lower.contains("deepseek") -> "deepseek.com"
        lower.contains("mistral") || lower.contains("mixtral") -> "mistral.ai"
        lower.contains("qwen") -> "qwenlm.ai"
        lower.contains("groq") -> "groq.com"
        lower.contains("cohere") || lower.contains("command") -> "cohere.com"
        lower.contains("phi") || lower.contains("microsoft") -> "microsoft.com"
        lower.contains("perplexity") -> "perplexity.ai"
        lower.contains("nous") -> "nousresearch.com"
        lower.contains("codestral") -> "mistral.ai"
        else -> providerDomain ?: "openai.com"
    }
}

@Composable
fun ModelSelectionScreen(
    baseUrl: String,
    apiKey: String,
    providerDomain: String?,
    onBack: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    fun fetchModels() {
        isLoading = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                ApiClient.fetchModels(baseUrl, apiKey)
            }
            result.onSuccess {
                models = it
                isLoading = false
            }.onFailure {
                error = it.message ?: "Unknown error"
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { fetchModels() }

    Column(modifier = Modifier.fillMaxSize().padding(top = 48.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = if (isDark) Color.White else Color(0xFF0D0D0D)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Select a Model",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                fontFamily = InterFamily,
                color = if (isDark) Color.White else Color(0xFF0D0D0D)
            )
        }

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching models...",
                            fontSize = 13.sp,
                            fontFamily = InterFamily,
                            color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                        )
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Failed to fetch models",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            fontFamily = InterFamily,
                            color = if (isDark) Color.White else Color(0xFF0D0D0D)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error ?: "",
                            fontSize = 12.sp,
                            fontFamily = InterFamily,
                            color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF))
                                .clickable { fetchModels() }
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Retry", fontFamily = InterFamily, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                }
            }
            else -> {
                // Live search box
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF141414) else Color(0xFFF4F4F4)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text("Search models…", fontSize = 14.sp, fontFamily = InterFamily, color = if (isDark) Color(0xFF8E8E93) else Color(0xFF9CA3AF))
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, fontFamily = InterFamily, color = if (isDark) Color.White else Color(0xFF0D0D0D)),
                                cursorBrush = SolidColor(if (isDark) Color.White else Color(0xFF0D0D0D)),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (query.isNotEmpty()) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = Color(0xFF8E8E93), modifier = Modifier.size(18.dp).clickable { query = "" })
                        }
                    }
                }

                val filteredModels = remember(models, query) {
                    val q = query.trim()
                    if (q.isEmpty()) models else models.filter { it.contains(q, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(filteredModels) { model ->
                        val isSelected = model == selectedModel
                        val bgColor = if (isSelected) {
                            if (isDark) Color(0xFF141414) else Color(0xFFF3F4F6)
                        } else {
                            Color.Transparent
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedModel = model },
                            shape = RoundedCornerShape(10.dp),
                            color = bgColor
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model,
                                    fontSize = 14.sp,
                                    fontFamily = InterFamily,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (isDark) Color(0xFFE5E5E5) else Color(0xFF0D0D0D),
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF22C55E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val continueBg = if (selectedModel != null)
                        (if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF))
                    else (if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA))
                    val continueFg = if (selectedModel != null) Color.White
                        else (if (isDark) Color(0xFF636366) else Color(0xFFB0B0B0))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(continueBg)
                            .then(if (selectedModel != null) Modifier.clickable { selectedModel?.let { onModelSelected(it) } } else Modifier)
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Continue",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFamily,
                            color = continueFg
                        )
                    }
                }
            }
        }
    }
}
