package com.ahamai.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ahamai.app.data.*
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.components.IosBottomSheet
import com.ahamai.app.ui.theme.InterFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsSheet(
    isDark: Boolean,
    onToolSelected: (Tool, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTool by remember { mutableStateOf<Tool?>(null) }
    var inputText by remember { mutableStateOf("") }

    IosBottomSheet(isDark = isDark, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (selectedTool == null) {
                // Tool selection
                Text(
                    text = "Tools",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color.White else Color(0xFF0D0D0D),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ToolsManager.CATEGORIES.forEach { category ->
                    val tools = ToolsManager.getToolsByCategory(category)
                    if (tools.isNotEmpty()) {
                        Text(
                            text = category,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF),
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp)
                        )
                        tools.forEach { tool ->
                            ToolRow(tool = tool, isDark = isDark, onClick = { selectedTool = tool })
                        }
                    }
                }
            } else {
                // Tool input
                val tool = selectedTool!!
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    IconButton(onClick = { selectedTool = null }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, "Back", tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280), modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(tool.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0D0D0D))
                }
                Text(tool.description, fontSize = 13.sp, color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280), modifier = Modifier.padding(bottom = 12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F8)
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = if (isDark) Color.White else Color(0xFF0D0D0D)),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (inputText.isEmpty()) Text(getPlaceholder(tool.id), fontSize = 14.sp, color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF))
                                inner()
                            }
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                val runEnabled = inputText.isNotBlank()
                val runBg = if (runEnabled) (if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF))
                    else (if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA))
                val runFg = if (runEnabled) Color.White
                    else (if (isDark) Color(0xFF636366) else Color(0xFFB0B0B0))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(runBg)
                        .then(if (runEnabled) Modifier.clickable { onToolSelected(tool, inputText); onDismiss() } else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Run Tool", fontWeight = FontWeight.SemiBold, color = runFg, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: Tool, isDark: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(34.dp),
            shape = RoundedCornerShape(8.dp),
            color = if (isDark) Color(0xFF1C1C1C) else Color(0xFFF0F0F5)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(getToolEmoji(tool.id), fontSize = 16.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isDark) Color.White else Color(0xFF0D0D0D))
            Text(tool.description, fontSize = 11.sp, color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF))
        }
    }
}

private fun getToolEmoji(id: String): String = when(id) {
    "web_scraper" -> "\uD83C\uDF10"
    "news_scraper" -> "\uD83D\uDCF0"
    "weather" -> "\u26C5"
    "crypto_price" -> "\uD83D\uDCB0"
    "stock_price" -> "\uD83D\uDCC8"
    "image_search" -> "\uD83D\uDDBC"
    "wikipedia" -> "\uD83D\uDCDA"
    "translator" -> "\uD83C\uDF0D"
    "dictionary" -> "\uD83D\uDCD6"
    "ip_geo" -> "\uD83D\uDCCD"
    "qr_code" -> "\uD83D\uDD33"
    "math_solver" -> "\uD83E\uDDEE"
    "color_palette" -> "\uD83C\uDFA8"
    "github_trending" -> "\uD83D\uDC19"
    "url_expander" -> "\uD83D\uDD17"
    else -> "\uD83D\uDD27"
}

private fun getPlaceholder(id: String): String = when(id) {
    "web_scraper" -> "Enter URL to scrape..."
    "news_scraper" -> "Search topic or 'latest'..."
    "weather" -> "Enter city name..."
    "crypto_price" -> "Coin name (btc, eth, sol)..."
    "stock_price" -> "Stock symbol (AAPL, TSLA)..."
    "image_search" -> "What images to find..."
    "wikipedia" -> "Search Wikipedia..."
    "translator" -> "Text to hindi (or 'text to french')..."
    "dictionary" -> "Enter a word..."
    "ip_geo" -> "IP address or 'my ip'..."
    "qr_code" -> "Text or URL for QR..."
    "math_solver" -> "Math expression or equation..."
    "color_palette" -> "Theme (ocean, sunset, forest)..."
    "github_trending" -> "Search repos or 'trending'..."
    "url_expander" -> "Enter URL to inspect..."
    else -> "Enter input..."
}

// ===== SKILLS SHEET (Grok-style) =====

/**
 * Bottom sheet from agent + menu / chat:
 * Manage Skills · Add Skill · My Skills (toggles) · Built-in catalog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsSheet(
    isDark: Boolean,
    onDismiss: () -> Unit,
    onManageAll: () -> Unit = {},
    onSkillCreator: () -> Unit = {},
    onAddSkill: () -> Unit = {}
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { SkillManager.init(context) }

    val interFamily = InterFamily
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val secondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF6B6B6B)
    val muted = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)

    var tick by remember { mutableIntStateOf(0) }
    val custom = remember(tick) { SkillManager.customSkills() }
    val featuredBuiltin = remember {
        val order = listOf("docx", "pdf", "pptx", "skill-creator", "xlsx")
        val all = SkillManager.ALL_SKILLS
        order.mapNotNull { id -> all.find { it.id == id } } +
            all.filter { it.id !in order }
    }

    IosBottomSheet(isDark = isDark, onDismissRequest = onDismiss, noHandle = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Skills",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = interFamily,
                color = primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
                textAlign = TextAlign.Center
            )

            SkillActionRow(
                icon = Lucide.Sliders,
                title = "Manage Skills",
                primary = primary,
                onClick = { onDismiss(); onManageAll() }
            )
            SkillActionRow(
                icon = Icons.Filled.Add,
                title = "Add Skill",
                primary = primary,
                onClick = { onDismiss(); onAddSkill() }
            )

            Spacer(Modifier.height(18.dp))

            Text(
                "My Skills",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = interFamily,
                color = muted,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            if (custom.isEmpty()) {
                Text(
                    "No custom skills yet",
                    fontSize = 13.sp,
                    fontFamily = interFamily,
                    color = secondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                custom.forEach { s ->
                    SkillToggleRow(
                        skillId = s.id,
                        storedIcon = s.iconSvg,
                        title = s.name,
                        description = s.description,
                        primary = primary,
                        secondary = secondary,
                        checked = s.enabled,
                        onCheckedChange = {
                            SkillManager.setCustomEnabled(s.id, it)
                            tick++
                        }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Text(
                "Built-in",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = interFamily,
                color = muted,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(featuredBuiltin, key = { it.id }) { skill ->
                    SkillToggleRow(
                        skillId = skill.id,
                        storedIcon = com.ahamai.app.data.SkillManager.getIconSvg(skill.id)
                            .ifBlank { skill.iconSvg },
                        title = skill.name,
                        description = skill.description,
                        primary = primary,
                        secondary = secondary,
                        checked = null,
                        onCheckedChange = {},
                        onClick = {
                            if (skill.id == "skill-creator") {
                                onDismiss()
                                onSkillCreator()
                            } else {
                                if (SkillManager.isLoaded(skill.id)) SkillManager.unloadSkill(skill.id)
                                else SkillManager.loadSkill(skill.id)
                                tick++
                            }
                        },
                        trailing = if (skill.id != "skill-creator" && SkillManager.isLoaded(skill.id)) "On" else null
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    primary: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = InterFamily,
            color = primary
        )
    }
}

@Composable
private fun SkillToggleRow(
    skillId: String,
    storedIcon: String? = null,
    title: String,
    description: String,
    primary: Color,
    secondary: Color,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null,
    trailing: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        com.ahamai.app.ui.icons.SkillIconGlyph(
            skillId = skillId,
            storedIcon = storedIcon,
            tint = primary,
            size = 22.dp
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                color = primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = InterFamily,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            checked != null -> {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF34C759),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = if (isSystemInDarkTheme()) Color(0xFF2A2A2A) else Color(0xFFE5E5EA),
                        uncheckedBorderColor = Color.Transparent
                    )
                )
            }
            trailing != null -> {
                Text(
                    trailing,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFamily,
                    color = Color(0xFF34C759)
                )
            }
        }
    }
}

// ===== CONNECTORS SHEET (Grok-style hero from agent + menu) =====

/**
 * Bottom sheet from the agent's "+" menu — Grok-style hero:
 * floating real favicons (independent motion), "Bring your tools…",
 * "Try these" quick rows, solid "+ Add" CTA → full ConnectorsScreen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsSheet(
    isDark: Boolean,
    onManageAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val secondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF6B6B6B)
    val muted = if (isDark) Color(0xFF6B6B6B) else Color(0xFF9CA3AF)
    val fieldBg = if (isDark) Color(0xFF1F1F1F) else Color(0xFFE9E9EE)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF3B30)

    var refreshTick by remember { mutableIntStateOf(0) }
    val refresh = { refreshTick++ }

    val allConnectors = remember(refreshTick) { ConnectorsManager.ALL }
    val connectedIds by remember(refreshTick) {
        derivedStateOf { ConnectorsManager.connected().map { it.id }.toSet() }
    }
    val tryThese = remember(allConnectors) { allConnectors.take(3) }
    val faviconUrls = remember(allConnectors) { allConnectors.map { it.faviconUrl } }

    var activeConnector by remember { mutableStateOf<String?>(null) }

    IosBottomSheet(isDark = isDark, onDismissRequest = onDismiss, noHandle = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Connectors",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )

            FloatingConnectorFavicons(
                faviconUrls = faviconUrls,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                "Bring your tools to AhamAI",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFamily,
                color = primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "AhamAI can search, draft, and pull context from your apps without leaving chat.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontFamily = InterFamily,
                color = secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(28.dp))

            Text(
                "Try these",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = InterFamily,
                color = muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            )

            tryThese.forEach { conn ->
                val isConnected = conn.id in connectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (isConnected) {
                                ConnectorsManager.setEnabled(
                                    conn.id,
                                    !ConnectorsManager.getState(conn.id).enabled
                                )
                                refresh()
                            } else {
                                activeConnector = conn.id
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = conn.faviconUrl,
                        contentDescription = conn.name,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        conn.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = InterFamily,
                        color = primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (isConnected) {
                        Text(
                            "Connected",
                            fontSize = 14.sp,
                            fontFamily = InterFamily,
                            color = green
                        )
                    } else {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Connect",
                            tint = muted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (isDark) Color(0xFFECECEC) else Color(0xFF141414))
                    .clickable {
                        onDismiss()
                        onManageAll()
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF141414) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Add",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = if (isDark) Color(0xFF141414) else Color.White
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    activeConnector?.let { id ->
        ConnectorConnectSheet(
            def = ConnectorsManager.byId(id),
            isDark = isDark,
            primary = primary,
            secondary = secondary,
            muted = muted,
            fieldBg = fieldBg,
            green = green,
            red = red,
            onDismiss = { activeConnector = null; refresh() }
        )
    }
}

// ===== AGENTS SHEET =====


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsSheet(
    isDark: Boolean,
    activeAgent: Agent?,
    onAgentSelected: (Agent?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    val allAgents = remember { AgentsManager.getAllAgents(context) }

    IosBottomSheet(isDark = isDark, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            if (!showCreate) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Agents", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (isDark) Color.White else Color(0xFF0D0D0D))
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showCreate = true },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isDark) Color(0xFF1C1C1C) else Color(0xFFF0F0F5)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, "Create", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Create", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Deactivate button
                if (activeAgent != null) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onAgentSelected(null); onDismiss() },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF1A1020) else Color(0xFFFDF2F8)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("\u274C", fontSize = 16.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("Deactivate Agent", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color(0xFFEF4444))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                allAgents.forEach { agent ->
                    val isActive = activeAgent?.id == agent.id
                    AgentRow(agent = agent, isActive = isActive, isDark = isDark, onClick = {
                        onAgentSelected(agent)
                        onDismiss()
                    })
                }
            } else {
                CreateAgentView(isDark = isDark, onBack = { showCreate = false }, onCreated = { agent ->
                    AgentsManager.saveAgent(context, agent)
                    showCreate = false
                    onAgentSelected(agent)
                    onDismiss()
                })
            }
        }
    }
}

@Composable
private fun AgentRow(agent: Agent, isActive: Boolean, isDark: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Rounded square avatar like reference
        Surface(
            modifier = Modifier.size(42.dp),
            shape = RoundedCornerShape(10.dp),
            color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    else if (isDark) Color(0xFF1C1C1C) else Color(0xFFF0F0F5)
        ) {
            Box(contentAlignment = Alignment.Center) { Text(agent.emoji, fontSize = 20.sp) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(agent.name, fontSize = 15.sp, fontWeight = FontWeight.Normal, color = if (isDark) Color.White else Color(0xFF0D0D0D))
        }
        if (isActive) {
            Icon(Icons.Filled.Check, "Active", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CreateAgentView(isDark: Boolean, onBack: () -> Unit, onCreated: (Agent) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf(PIXEL_EMOJIS[0]) }
    var selectedTools by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showEmojiPicker by remember { mutableStateOf(false) }

    // Auto-select tools when description changes
    LaunchedEffect(description) {
        if (description.length > 10) {
            selectedTools = AgentsManager.autoSelectTools(description).toSet()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Close, "Back", tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("Create Agent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color.White else Color(0xFF0D0D0D))
        }

        // Emoji picker
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Surface(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable { showEmojiPicker = !showEmojiPicker },
                shape = CircleShape,
                color = if (isDark) Color(0xFF1C1C1C) else Color(0xFFF0F0F5)
            ) {
                Box(contentAlignment = Alignment.Center) { Text(selectedEmoji, fontSize = 24.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Text("Tap to pick avatar", fontSize = 12.sp, color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF))
        }

        if (showEmojiPicker) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.height(100.dp).padding(bottom = 10.dp)
            ) {
                items(PIXEL_EMOJIS) { emoji ->
                    Box(
                        modifier = Modifier.size(40.dp).clip(CircleShape).clickable { selectedEmoji = emoji; showEmojiPicker = false },
                        contentAlignment = Alignment.Center
                    ) { Text(emoji, fontSize = 20.sp) }
                }
            }
        }

        // Name
        Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(10.dp), color = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F8)) {
            BasicTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth().padding(12.dp), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = if (isDark) Color.White else Color(0xFF0D0D0D)), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { inner -> Box { if (name.isEmpty()) Text("Agent name", fontSize = 14.sp, color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)); inner() } })
        }

        // Description (AI uses this to auto-select tools)
        Surface(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(10.dp), color = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F8)) {
            BasicTextField(value = description, onValueChange = { description = it }, modifier = Modifier.fillMaxWidth().padding(12.dp).heightIn(min = 50.dp), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = if (isDark) Color.White else Color(0xFF0D0D0D)), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { inner -> Box { if (description.isEmpty()) Text("Describe what this agent does (AI will auto-select tools)", fontSize = 14.sp, color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)); inner() } })
        }

        // Tools selection
        Text("Tools (auto-selected by AI)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF), modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.heightIn(max = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ToolsManager.ALL_TOOLS) { tool ->
                val isSelected = tool.id in selectedTools
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        selectedTools = if (isSelected) selectedTools - tool.id else selectedTools + tool.id
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else if (isDark) Color(0xFF141414) else Color(0xFFF5F5F8)
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(getToolEmoji(tool.id), fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(tool.name, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal, color = if (isSelected) MaterialTheme.colorScheme.primary else if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        val createEnabled = name.isNotBlank()
        val createBg = if (createEnabled) (if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF))
            else (if (isDark) Color(0xFF1F1F1F) else Color(0xFFE5E5EA))
        val createFg = if (createEnabled) Color.White
            else (if (isDark) Color(0xFF636366) else Color(0xFFB0B0B0))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(createBg)
                .then(if (createEnabled) Modifier.clickable {
                    val agent = Agent(
                        id = "custom_${System.currentTimeMillis()}",
                        name = name,
                        emoji = selectedEmoji,
                        description = description.ifBlank { name },
                        systemPrompt = "You are ${name}. ${description}. Use your available tools when needed to provide accurate and helpful responses.",
                        toolIds = selectedTools.toList(),
                        isBuiltIn = false
                    )
                    onCreated(agent)
                } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text("Create Agent", fontWeight = FontWeight.SemiBold, color = createFg, fontSize = 15.sp)
        }
    }
}
