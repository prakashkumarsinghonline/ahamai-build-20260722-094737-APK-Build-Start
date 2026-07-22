package com.ahamai.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.VercelMCPManager
import com.ahamai.app.data.VercelMCPTools
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import com.ahamai.app.ui.components.IosDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MCPScreen(onBack: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Monochrome palette matching ProfileScreen
    val bg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val cardBg = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F7)
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
    val secondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF6B6B6B)
    val muted = if (isDark) Color(0xFF6B6B6B) else Color(0xFF9CA3AF)
    val fieldBg = if (isDark) Color(0xFF1F1F1F) else Color(0xFFF4F4F4)
    val sep = if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF3B30)
    val sectionShape = RoundedCornerShape(12.dp)

    val conn = remember { VercelMCPManager.current }
    var token by remember { mutableStateOf(conn.token) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    var showTools by remember { mutableStateOf(false) }

    @Composable fun sectionLabel(title: String) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = InterFamily,
            color = muted,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 12.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    com.ahamai.app.ui.icons.AdminIcons.ArrowBackIos,
                    contentDescription = "Back",
                    tint = secondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = "MCP Servers",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
        ) {
            Spacer(Modifier.height(4.dp))

            // ── SECTION: CONNECTION STATUS ──
            sectionLabel("CONNECTION")
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sectionShape)
                    .background(cardBg)
            ) {
                // Status row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0C0C0E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(com.ahamai.app.R.drawable.vercel_icon),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Vercel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = primary
                        )
                        Text(
                            text = when {
                                conn.verified && conn.enabled -> "Connected"
                                conn.token.isNotBlank() -> "Token saved"
                                else -> "Not connected"
                            },
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            color = if (conn.verified && conn.enabled) green else secondary
                        )
                    }
                    if (conn.verified && conn.enabled) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(green),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── SECTION: ACCESS TOKEN ──
            sectionLabel("ACCESS TOKEN")
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sectionShape)
                    .background(cardBg)
            ) {
                // Token input
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        "Paste your Vercel access token",
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        color = secondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(fieldBg)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        if (token.isEmpty()) {
                            Text(
                                "vcr_...",
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFamily,
                                color = secondary.copy(alpha = 0.5f)
                            )
                        }
                        BasicTextField(
                            value = token,
                            onValueChange = { token = it; testResult = null },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontSize = 11.sp,
                                fontFamily = JetBrainsMonoFamily,
                                color = primary
                            ),
                            cursorBrush = SolidColor(secondary),
                            singleLine = true
                        )
                    }

                    testResult?.let { result ->
                        val ok = result == "OK"
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (ok) "Verified" else result,
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            color = if (ok) green else red
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Action buttons row — Get Token / Test / Connect
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Get Token
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(fieldBg)
                                .clickable {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://vercel.com/account/tokens")
                                    )
                                    context.startActivity(intent)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Get Token",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = InterFamily,
                                color = secondary
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // Test button (only when new token typed)
                        if (token.isNotBlank() && token != conn.token) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(fieldBg)
                                    .clickable(enabled = !testing) {
                                        testing = true
                                        testResult = null
                                        scope.launch {
                                            val err = VercelMCPManager.verifyToken(token.trim())
                                            testResult = if (err == null) "OK" else err
                                            testing = false
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (testing) "..." else "Test",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = InterFamily,
                                    color = primary
                                )
                            }
                        }

                        // Connect / Update
                        if (token.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) Color(0xFFECECEC) else Color(0xFF141414))
                                    .clickable {
                                        VercelMCPManager.setToken(token.trim())
                                        scope.launch {
                                            val err = VercelMCPManager.verifyToken(token.trim())
                                            if (err == null) {
                                                VercelMCPManager.setVerified(true)
                                                VercelMCPManager.setEnabled(true)
                                                testResult = "OK"
                                            } else {
                                                testResult = err
                                            }
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (conn.token.isNotBlank() && token == conn.token) "Update" else "Connect",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFamily,
                                    color = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F7)
                                )
                            }
                        }
                    }

                    // Disconnect
                    if (conn.token.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Disconnect",
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            color = red,
                            modifier = Modifier
                                .clickable { showDisconnectConfirm = true }
                                .padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── SECTION: AVAILABLE TOOLS ──
            sectionLabel("AVAILABLE TOOLS")
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(sectionShape)
                    .background(cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTools = !showTools }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Tools (${VercelMCPTools.getToolsList().size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = primary
                        )
                        Icon(
                            imageVector = if (showTools) Lucide.ChevronUp else Lucide.ChevronDown,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    AnimatedVisibility(visible = showTools) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            val tools = VercelMCPTools.getToolsList()
                            tools.forEachIndexed { idx, (name, desc) ->
                                if (idx > 0) Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        name,
                                        fontSize = 12.sp,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = if (conn.verified) primary else secondary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        desc,
                                        fontSize = 10.sp,
                                        fontFamily = InterFamily,
                                        color = secondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Disconnect confirm ──
    if (showDisconnectConfirm) {
        IosDialog(isDark = isDark, onDismissRequest = { showDisconnectConfirm = false }) {
            Text(
                "Disconnect Vercel?",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "The agent will lose access to Vercel deployments and logs.",
                fontSize = 13.sp,
                fontFamily = InterFamily,
                color = secondary
            )
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancel", fontSize = 14.sp, fontFamily = InterFamily, color = secondary)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    VercelMCPManager.disconnect()
                    token = ""
                    testResult = null
                    showDisconnectConfirm = false
                }) {
                    Text("Disconnect", fontSize = 14.sp, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, color = red)
                }
            }
        }
    }
}