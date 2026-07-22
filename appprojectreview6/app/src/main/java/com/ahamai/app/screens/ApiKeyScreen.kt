package com.ahamai.app.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahamai.app.ProviderItem
@Composable
fun ApiKeyScreen(
    provider: ProviderItem,
    onBack: () -> Unit,
    onConnect: (baseUrl: String, apiKey: String) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    var apiKey by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf(provider.defaultBaseUrl) }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (provider.domain != null) {
            val faviconUrl = "https://www.google.com/s2/favicons?domain=${provider.domain}&sz=64"
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(faviconUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = provider.name,
                modifier = Modifier.size(56.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Custom provider",
                tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = provider.name,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = if (isDark) Color.White else Color(0xFF0D0D0D)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Enter your ${provider.name} API key",
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            color = if (isDark) Color.White else Color(0xFF0D0D0D)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Clean input fields - no borders, just subtle background
        val fieldBg = if (isDark) Color(0xFF1A1A2A) else Color(0xFFF0F0F5)

        val fieldLabelColor = if (isDark) Color(0xFFA0A0A0) else Color(0xFF6B6B6B)
        val fieldPlaceholderColor = if (isDark) Color(0xFF6E6E73) else Color(0xFF9A9A9A)

        // API Key — static label above the box (login/signup style, no floating label)
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "API Key",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = fieldLabelColor,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text("sk-…", color = fieldPlaceholderColor) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide API key" else "Show API key"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedContainerColor = fieldBg,
                    focusedContainerColor = fieldBg
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Base URL — static label above the box
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Base URL",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = fieldLabelColor,
                modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                placeholder = { Text("https://api.example.com/v1", color = fieldPlaceholderColor) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedContainerColor = fieldBg,
                    focusedContainerColor = fieldBg
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { onConnect(baseUrl, apiKey) },
            modifier = Modifier.wrapContentWidth().height(44.dp),
            shape = RoundedCornerShape(24.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 0.dp)
        ) {
            Text(text = "Connect", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onBack) {
            Text(
                text = "Back",
                color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                fontSize = 14.sp
            )
        }
    }
}
