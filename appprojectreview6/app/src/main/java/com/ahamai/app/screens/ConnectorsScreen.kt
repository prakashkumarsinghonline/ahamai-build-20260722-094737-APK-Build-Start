package com.ahamai.app.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.ahamai.app.data.ConnectorCatalog
import com.ahamai.app.data.ConnectorsManager
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * iOS Settings-style Connectors browser.
 * Search: local + MCP Registry + Smithery + curated directory.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorsScreen(onBack: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    LaunchedEffect(Unit) { ConnectorsManager.init(context) }

    // iOS system greys
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val group = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val primary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
    val secondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val accent = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val sep = if (isDark) Color(0xFF38383A) else Color(0xFFC6C6C8)
    val searchBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val green = Color(0xFF34C759)
    val red = Color(0xFFFF3B30)
    val fieldBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFFFFFFF)

    val view = LocalView.current
    val bgArgb = if (isDark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    DisposableEffect(isDark, bgArgb) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        val activityWindow = (view.context as? android.app.Activity)?.window
        val windows = listOfNotNull(dialogWindow, activityWindow).distinct()
        windows.forEach { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = bgArgb
            window.navigationBarColor = bgArgb
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
        onDispose {
            windows.forEach { window ->
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }

    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var activeConnector by remember { mutableStateOf<String?>(null) }
    var showAddConnector by remember { mutableStateOf(false) }
    var refreshTick by remember { mutableIntStateOf(0) }
    fun refresh() { refreshTick++ }

    var catalogHits by remember { mutableStateOf<List<ConnectorCatalog.CatalogHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var dirHealth by remember { mutableStateOf<Map<String, Boolean>?>(null) }

    val connectedIds = remember(refreshTick) {
        ConnectorsManager.connected().map { it.id }.toSet()
    }
    val connectedList = remember(refreshTick) { ConnectorsManager.connected() }
    val customList = remember(refreshTick) { ConnectorsManager.ALL.filter { it.isCustom } }

    // Debounced universal search
    LaunchedEffect(search, refreshTick) {
        val q = search.trim()
        if (q.isNotEmpty()) delay(320)
        searching = true
        searchError = null
        try {
            catalogHits = ConnectorCatalog.search(q)
        } catch (e: Exception) {
            searchError = e.message?.take(100)
            catalogHits = runCatching { ConnectorCatalog.search(q) }.getOrDefault(emptyList())
        }
        searching = false
    }

    LaunchedEffect(Unit) {
        dirHealth = runCatching { ConnectorCatalog.directoryHealth() }.getOrNull()
    }

    val localHits = remember(catalogHits) {
        catalogHits.filter { it.source == ConnectorCatalog.Source.LOCAL }
    }
    val webHits = remember(catalogHits) {
        catalogHits.filter { it.source != ConnectorCatalog.Source.LOCAL }
    }
    val builtinHits = remember(localHits, search, refreshTick) {
        if (search.isBlank()) {
            ConnectorsManager.BUILTIN.map { def ->
                ConnectorCatalog.CatalogHit(
                    id = "local:${def.id}",
                    name = def.name,
                    description = def.description,
                    serverUrl = def.authUrl,
                    source = ConnectorCatalog.Source.LOCAL,
                    category = "Built-in",
                    faviconUrl = def.faviconUrl,
                    alreadyInstalled = true,
                    localConnectorId = def.id
                )
            }
        } else {
            localHits.filter { it.category == "Built-in" }
        }
    }

    val filterChips = listOf("All", "Connected", "Built-in", "Custom", "Discover")
    var chip by remember { mutableStateOf("All") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Nav
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("Close", color = accent, fontSize = 17.sp, fontFamily = InterFamily)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showAddConnector = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add", tint = accent)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item {
                Text(
                    "Connectors",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFamily,
                    color = primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Text(
                    "Connect services for the agent · MCP directories included",
                    fontSize = 13.sp,
                    fontFamily = InterFamily,
                    color = secondary,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
                )
            }

            // Search
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(searchBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = secondary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = search,
                        onValueChange = { search = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = primary, fontFamily = InterFamily),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (search.isEmpty()) {
                                Text("Search connectors, MCP…", fontSize = 16.sp, color = secondary, fontFamily = InterFamily)
                            }
                            inner()
                        }
                    )
                    if (search.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, "Clear",
                            tint = secondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { search = "" }
                        )
                    } else if (searching) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = secondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterChips) { label ->
                        val sel = chip == label
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = InterFamily,
                            color = if (sel) Color.White else primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (sel) accent else group)
                                .clickable { chip = label }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            // Directory health
            dirHealth?.let { health ->
                if (search.isBlank() && (chip == "All" || chip == "Discover")) {
                    item {
                        val mcpOk = health["mcp_registry"] == true
                        val smithOk = health["smithery"] == true
                        Text(
                            buildString {
                                append("Directories · ")
                                append(if (mcpOk) "MCP ✓" else "MCP ✗")
                                append(" · ")
                                append(if (smithOk) "Smithery ✓" else "Smithery ✗")
                            },
                            fontSize = 12.sp,
                            fontFamily = InterFamily,
                            color = secondary,
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 10.dp)
                        )
                    }
                }
            }

            // Connected
            if ((chip == "All" || chip == "Connected") && search.isBlank()) {
                if (connectedList.isNotEmpty() || chip == "Connected") {
                    item { IosSecLabel("CONNECTED", secondary) }
                    item {
                        IosGroup(group) {
                            if (connectedList.isEmpty()) {
                                Text(
                                    "No services connected yet. Open Built-in or Discover to add one.",
                                    fontSize = 14.sp,
                                    fontFamily = InterFamily,
                                    color = secondary,
                                    modifier = Modifier.padding(20.dp),
                                    lineHeight = 18.sp
                                )
                            } else {
                                connectedList.forEachIndexed { i, def ->
                                    IosConnRow(
                                        title = def.name,
                                        subtitle = def.description,
                                        favicon = def.faviconUrl,
                                        primary = primary,
                                        secondary = secondary,
                                        sep = sep,
                                        showDivider = i < connectedList.lastIndex,
                                        trailing = {
                                            Text("Connected", fontSize = 13.sp, color = green, fontFamily = InterFamily)
                                        },
                                        onClick = { activeConnector = def.id }
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(18.dp)) }
                }
            }

            // Custom / Add
            if (chip == "All" || chip == "Custom") {
                item { IosSecLabel("MY CONNECTORS", secondary) }
                item {
                    IosGroup(group) {
                        IosConnRow(
                            title = "Add custom connector",
                            subtitle = "Paste API or MCP server URL",
                            favicon = "",
                            primary = primary,
                            secondary = secondary,
                            sep = sep,
                            showDivider = customList.isNotEmpty(),
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(accent.copy(0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Add, null, tint = accent, modifier = Modifier.size(20.dp))
                                }
                            },
                            trailing = {
                                Icon(Icons.Filled.ChevronRight, null, tint = secondary.copy(0.5f), modifier = Modifier.size(18.dp))
                            },
                            onClick = { showAddConnector = true }
                        )
                        val shownCustoms = if (search.isBlank()) customList else customList.filter {
                            it.name.contains(search, true) || it.description.contains(search, true) ||
                                it.serverUrl.contains(search, true)
                        }
                        shownCustoms.forEachIndexed { i, def ->
                            IosConnRow(
                                title = def.name,
                                subtitle = def.serverUrl.ifBlank { def.description },
                                favicon = def.faviconUrl,
                                primary = primary,
                                secondary = secondary,
                                sep = sep,
                                showDivider = i < shownCustoms.lastIndex,
                                trailing = {
                                    val on = def.id in connectedIds
                                    Text(
                                        if (on) "Connected" else "Connect",
                                        fontSize = 13.sp,
                                        color = if (on) green else accent,
                                        fontFamily = InterFamily
                                    )
                                },
                                onClick = { activeConnector = def.id }
                            )
                        }
                    }
                }
                item {
                    Text(
                        "Custom connectors use custom_*_request tools once connected.",
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 18.dp)
                    )
                }
            }

            // Built-in
            if (chip == "All" || chip == "Built-in") {
                item { IosSecLabel(if (search.isBlank()) "BUILT-IN" else "IN APP", secondary) }
                item {
                    IosGroup(group) {
                        if (builtinHits.isEmpty()) {
                            Text(
                                "No built-in matches",
                                fontSize = 14.sp,
                                color = secondary,
                                fontFamily = InterFamily,
                                modifier = Modifier.padding(20.dp)
                            )
                        } else {
                            builtinHits.forEachIndexed { i, hit ->
                                val id = hit.localConnectorId.orEmpty()
                                val on = id in connectedIds
                                IosConnRow(
                                    title = hit.name,
                                    subtitle = hit.description,
                                    favicon = hit.faviconUrl,
                                    primary = primary,
                                    secondary = secondary,
                                    sep = sep,
                                    showDivider = i < builtinHits.lastIndex,
                                    trailing = {
                                        Text(
                                            if (on) "Connected" else "Connect",
                                            fontSize = 13.sp,
                                            color = if (on) green else accent,
                                            fontFamily = InterFamily
                                        )
                                    },
                                    onClick = { if (id.isNotBlank()) activeConnector = id }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(18.dp)) }
            }

            // Discover (web / curated / MCP / Smithery)
            if (chip == "All" || chip == "Discover") {
                item {
                    IosSecLabel(
                        if (search.isBlank()) "DISCOVER" else "FROM THE INTERNET",
                        secondary
                    )
                }
                item {
                    IosGroup(group) {
                        when {
                            searching && webHits.isEmpty() -> {
                                Row(
                                    Modifier.padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = secondary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Searching MCP Registry & Smithery…",
                                        fontSize = 14.sp,
                                        fontFamily = InterFamily,
                                        color = secondary
                                    )
                                }
                            }
                            webHits.isEmpty() -> {
                                Text(
                                    if (search.isBlank())
                                        "Featured connectors load here. Type Notion, Slack, Stripe…"
                                    else
                                        "No directory results for \"$search\". Try another name or add custom.",
                                    fontSize = 14.sp,
                                    fontFamily = InterFamily,
                                    color = secondary,
                                    modifier = Modifier.padding(20.dp),
                                    lineHeight = 18.sp
                                )
                            }
                            else -> {
                                webHits.forEachIndexed { i, hit ->
                                    val on = hit.localConnectorId != null && hit.localConnectorId in connectedIds
                                    val label = when {
                                        on -> "Connected"
                                        hit.alreadyInstalled || hit.localConnectorId != null -> "Connect"
                                        else -> "Add"
                                    }
                                    IosConnRow(
                                        title = hit.name,
                                        subtitle = hit.description,
                                        favicon = hit.faviconUrl,
                                        primary = primary,
                                        secondary = secondary,
                                        sep = sep,
                                        showDivider = i < webHits.lastIndex,
                                        badge = when (hit.source) {
                                            ConnectorCatalog.Source.MCP_REGISTRY -> "MCP"
                                            ConnectorCatalog.Source.SMITHERY -> "Smithery"
                                            ConnectorCatalog.Source.CURATED -> hit.category.take(12)
                                            else -> null
                                        },
                                        trailing = {
                                            Text(
                                                label,
                                                fontSize = 13.sp,
                                                color = if (on) green else accent,
                                                fontFamily = InterFamily
                                            )
                                        },
                                        onClick = {
                                            scope.launch {
                                                val id = ConnectorCatalog.installFromCatalog(hit)
                                                refresh()
                                                activeConnector = id
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                searchError?.let { err ->
                    item {
                        Text(
                            "Some directories unavailable: $err",
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            color = secondary,
                            modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp)
                        )
                    }
                }
                item {
                    Text(
                        "Search merges built-in, your customs, official MCP Registry, and Smithery. Add installs a custom connector, then Connect verifies the token.",
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }

    if (showAddConnector) {
        AddCustomConnectorDialog(
            isDark = isDark,
            primary = primary,
            secondary = secondary,
            muted = secondary,
            fieldBg = fieldBg,
            onDismiss = { showAddConnector = false },
            onCreated = { id ->
                showAddConnector = false
                refresh()
                activeConnector = id
            }
        )
    }

    activeConnector?.let { id ->
        val def = ConnectorsManager.findById(id)
        if (def != null) {
            ConnectorConnectSheet(
                def = def,
                isDark = isDark,
                primary = primary,
                secondary = secondary,
                muted = secondary,
                fieldBg = fieldBg,
                green = green,
                red = red,
                onDismiss = { activeConnector = null; refresh() }
            )
        } else {
            LaunchedEffect(id) { activeConnector = null }
        }
    }
}

@Composable
private fun IosSecLabel(text: String, color: Color) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = InterFamily,
        color = color,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 6.dp, top = 2.dp)
    )
}

@Composable
private fun IosGroup(bg: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        content = content
    )
}

@Composable
private fun IosConnRow(
    title: String,
    subtitle: String,
    favicon: String,
    primary: Color,
    secondary: Color,
    sep: Color,
    showDivider: Boolean,
    badge: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val chip = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            } else if (favicon.isNotBlank()) {
                AsyncImage(
                    model = favicon,
                    contentDescription = title,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(chip)
                )
            } else {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(chip),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title.take(1).uppercase(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = primary,
                        fontFamily = InterFamily
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontFamily = InterFamily,
                        color = primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!badge.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            badge,
                            fontSize = 10.sp,
                            fontFamily = InterFamily,
                            color = secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(chip)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 62.dp)
                    .height(0.5.dp)
                    .background(sep.copy(alpha = 0.55f))
            )
        }
    }
}

@Composable
private fun AddCustomConnectorDialog(
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    muted: Color,
    fieldBg: Color,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf("bearer") }
    val bg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val border = if (isDark) Color(0xFF38383A) else Color(0xFFC6C6C8)
    val canSave = name.isNotBlank() &&
        (serverUrl.startsWith("http://") || serverUrl.startsWith("https://"))

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg,
        title = {
            Column {
                Text("Add connector", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = primary)
                Text(
                    "Remote API or MCP URL · like iOS / ChatGPT custom connectors",
                    fontSize = 12.sp, fontFamily = InterFamily, color = secondary
                )
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                FieldLabel("Name", secondary)
                Field(name, { name = it }, "e.g. My API", primary, fieldBg, border, muted)
                Spacer(Modifier.height(10.dp))
                FieldLabel("Description", secondary)
                Field(description, { description = it }, "What this is for", primary, fieldBg, border, muted)
                Spacer(Modifier.height(10.dp))
                FieldLabel("Server URL", secondary)
                Field(serverUrl, { serverUrl = it }, "https://api.example.com/mcp", primary, fieldBg, border, muted)
                Spacer(Modifier.height(12.dp))
                FieldLabel("Auth", secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("bearer" to "Bearer", "api_key" to "API key", "none" to "None").forEach { (id, label) ->
                        val sel = authType == id
                        Text(
                            label,
                            fontSize = 12.sp,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                            fontFamily = InterFamily,
                            color = if (sel) primary else secondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) fieldBg else Color.Transparent)
                                .border(0.5.dp, border, RoundedCornerShape(10.dp))
                                .clickable { authType = id }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "After Connect, the agent can call this API with custom_*_request tools.",
                    fontSize = 11.sp, lineHeight = 15.sp, fontFamily = InterFamily, color = muted
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    val c = ConnectorsManager.createCustomConnector(
                        name = name.trim(),
                        description = description.trim(),
                        serverUrl = serverUrl.trim(),
                        authType = authType
                    )
                    onCreated(c.id)
                }
            ) {
                Text("Add", fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, color = primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = InterFamily, color = secondary)
            }
        }
    )
}

@Composable
private fun FieldLabel(text: String, color: Color) {
    Text(text, Modifier.padding(bottom = 6.dp), color, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = InterFamily)
}

@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    primary: Color,
    fieldBg: Color,
    border: Color,
    muted: Color
) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(fieldBg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, fontSize = 13.sp, fontFamily = InterFamily, color = muted.copy(0.7f))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(fontSize = 13.sp, fontFamily = InterFamily, color = primary),
            cursorBrush = SolidColor(primary),
            singleLine = true
        )
    }
}

/**
 * Three real favicons that float independently (different orbits / phases),
 * matching Grok's connectors hero animation.
 */
@Composable
fun FloatingConnectorFavicons(
    faviconUrls: List<String>,
    modifier: Modifier = Modifier
) {
    val urls = remember(faviconUrls) {
        when {
            faviconUrls.isEmpty() -> emptyList()
            faviconUrls.size >= 3 -> faviconUrls.take(3)
            else -> (faviconUrls + faviconUrls + faviconUrls).take(3)
        }
    }
    if (urls.isEmpty()) return

    // Rotate which favicons appear every few seconds (like Grok cycling logos)
    val pool = remember(faviconUrls) {
        if (faviconUrls.size <= 3) faviconUrls
        else faviconUrls
    }
    var offset by remember { mutableIntStateOf(0) }
    LaunchedEffect(pool) {
        if (pool.size <= 3) return@LaunchedEffect
        while (true) {
            delay(2800)
            offset = (offset + 1) % pool.size
        }
    }
    val shown = remember(pool, offset) {
        if (pool.size <= 3) urls
        else listOf(
            pool[offset % pool.size],
            pool[(offset + 1) % pool.size],
            pool[(offset + 2) % pool.size]
        )
    }

    val infinite = rememberInfiniteTransition(label = "floating_favicons")

    val leftY by infinite.animateFloat(
        initialValue = -10f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ly"
    )
    val leftX by infinite.animateFloat(
        initialValue = -8f, targetValue = 10f,
        animationSpec = infiniteRepeatable(tween(3100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lx"
    )
    val leftRot by infinite.animateFloat(
        initialValue = -8f, targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse),
        label = "lr"
    )

    val centerY by infinite.animateFloat(
        initialValue = 8f, targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cy"
    )
    val centerX by infinite.animateFloat(
        initialValue = 6f, targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cx"
    )
    val centerRot by infinite.animateFloat(
        initialValue = 5f, targetValue = -5f,
        animationSpec = infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse),
        label = "cr"
    )
    val centerScale by infinite.animateFloat(
        initialValue = 0.96f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "cs"
    )

    val rightY by infinite.animateFloat(
        initialValue = -6f, targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ry"
    )
    val rightX by infinite.animateFloat(
        initialValue = 10f, targetValue = -8f,
        animationSpec = infiniteRepeatable(tween(2900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "rx"
    )
    val rightRot by infinite.animateFloat(
        initialValue = 10f, targetValue = -10f,
        animationSpec = infiniteRepeatable(tween(3800, easing = LinearEasing), RepeatMode.Reverse),
        label = "rr"
    )

    // Slow orbital phase so icons drift on small circles (extra independent motion)
    val orbit by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "orbit"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        // Left — smaller, softer
        FloatingFavicon(
            url = shown[0],
            size = 44.dp,
            alpha = 0.72f,
            offsetX = leftX + 8f * cos(Math.toRadians((orbit + 40).toDouble())).toFloat() - 88f,
            offsetY = leftY + 6f * sin(Math.toRadians((orbit + 40).toDouble())).toFloat(),
            rotation = leftRot,
            scale = 1f
        )
        // Right — smaller, softer
        FloatingFavicon(
            url = shown[2],
            size = 44.dp,
            alpha = 0.72f,
            offsetX = rightX + 8f * cos(Math.toRadians((orbit + 200).toDouble())).toFloat() + 88f,
            offsetY = rightY + 6f * sin(Math.toRadians((orbit + 200).toDouble())).toFloat(),
            rotation = rightRot,
            scale = 1f
        )
        // Center — larger, on top
        FloatingFavicon(
            url = shown[1],
            size = 56.dp,
            alpha = 1f,
            offsetX = centerX + 4f * cos(Math.toRadians((orbit + 120).toDouble())).toFloat(),
            offsetY = centerY + 5f * sin(Math.toRadians((orbit + 120).toDouble())).toFloat(),
            rotation = centerRot,
            scale = centerScale
        )
    }
}

@Composable
private fun BoxScope.FloatingFavicon(
    url: String,
    size: Dp,
    alpha: Float,
    offsetX: Float,
    offsetY: Float,
    rotation: Float,
    scale: Float
) {
    val isDark = isSystemInDarkTheme()
    val tileBg = if (isDark) Color(0xFF1F1F1F) else Color(0xFFF5F5F7)
    val tileBorder = if (isDark) Color(0x22FFFFFF) else Color(0x14000000)
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
                rotationZ = rotation
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .size(size + 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tileBg)
            .border(0.5.dp, tileBorder, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = Modifier
                .size(size * 0.72f)
                .clip(RoundedCornerShape(8.dp))
        )
    }
}

/**
 * Connect flow — soft gradient header, large favicon, clean white curvy
 * token card, stylish solid Connect pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectorConnectSheet(
    def: ConnectorsManager.ConnectorDef,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    muted: Color,
    fieldBg: Color,
    green: Color,
    red: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Match connectors page surface (light + dark)
    val sheetBg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF4F4F4)

    val state = remember(def.id) { ConnectorsManager.getState(def.id) }
    var token by remember(def.id) { mutableStateOf(state.token) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var showDisconnectConfirm by remember { mutableStateOf(false) }
    val isAlreadyConnected = ConnectorsManager.isConnected(def.id)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val brand = Color(def.brandColor)
    val gradientTop = brand.copy(alpha = if (isDark) 0.32f else 0.16f)

    // Curvy token card — white in light, elevated dark surface in dark
    val tokenCardBg = if (isDark) Color(0xFF141414) else Color(0xFFF5F5F7)
    val tokenInnerBg = if (isDark) Color(0xFF2A2A2C) else Color(0xFFECECEC)
    val tokenBorder = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE5E5EA)
    val tokenHintColor = if (isDark) Color(0xFF8E8E93) else muted
    val tokenPlaceholderColor = if (isDark) Color(0xFF636366) else muted.copy(alpha = 0.55f)
    val linkBlue = if (isDark) Color(0xFF64D2FF) else Color(0xFF0A66C2)

    // Custom "none" auth can connect without a token
    val needsToken = !(def.isCustom && def.authType == "none")
    val canConnect = (!needsToken || token.isNotBlank()) && !testing
    // Solid high-contrast Connect CTA in both themes
    val connectBg = when {
        !canConnect && isDark -> Color(0xFF1F1F1F)
        !canConnect -> Color(0xFFE5E5EA)
        isDark -> Color(0xFFEBEBF0) // soft white pill on dark
        else -> Color(0xFF111111)
    }
    val connectFg = when {
        !canConnect && isDark -> Color(0xFF636366)
        !canConnect -> Color(0xFF8E8E93)
        isDark -> Color(0xFF111111)
        else -> Color.White
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Soft brand gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(gradientTop, sheetBg)
                        )
                    )
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primary
                    )
                }
                Text(
                    def.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 52.dp, top = 18.dp)
                )

                // Favicon on soft white rounded tile
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp)
                        .size(80.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isDark) Color(0xFF1F1F1F) else Color(0xFFF5F5F7))
                        .border(
                            0.5.dp,
                            if (isDark) Color(0x22FFFFFF) else Color(0x14000000),
                            RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = def.faviconUrl,
                        contentDescription = def.name,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    def.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFamily,
                    color = primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    def.tagline,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = InterFamily,
                    color = secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(22.dp))

                if (def.isCustom) {
                    Text(
                        "Server: ${def.serverUrl}",
                        fontSize = 12.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // ── Clean white curvy token card ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(tokenCardBg)
                        .border(0.5.dp, tokenBorder, RoundedCornerShape(22.dp))
                        .padding(horizontal = 18.dp, vertical = 18.dp)
                ) {
                    Text(
                        if (def.isCustom && def.authType == "none") "Authentication"
                        else if (def.isCustom && def.authType == "api_key") "API key"
                        else "Access token",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFamily,
                        color = primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        def.tokenHint,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontFamily = InterFamily,
                        color = tokenHintColor
                    )

                    Spacer(Modifier.height(14.dp))

                    if (!(def.isCustom && def.authType == "none")) {
                        // Inner curvy input
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(tokenInnerBg)
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF48484A) else tokenBorder,
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (token.isEmpty()) {
                                Text(
                                    def.tokenPlaceholder,
                                    fontSize = 14.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = tokenPlaceholderColor
                                )
                            }
                            BasicTextField(
                                value = token,
                                onValueChange = { token = it; testResult = null },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(
                                    fontSize = 14.sp,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (isDark) Color(0xFFECECEC) else primary,
                                    lineHeight = 20.sp
                                ),
                                cursorBrush = SolidColor(if (isDark) Color(0xFFECECEC) else primary),
                                singleLine = true
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                    } else {
                        Text(
                            "This custom connector has no auth. Tap Connect to verify the server URL.",
                            fontSize = 13.sp,
                            fontFamily = InterFamily,
                            color = tokenHintColor
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // Get token / open server link row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(def.authUrl)))
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (def.isCustom) "Open server URL" else "Get token from ${def.name}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = linkBlue,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "↗",
                            fontSize = 14.sp,
                            color = linkBlue
                        )
                    }

                    testResult?.let { r ->
                        Spacer(Modifier.height(10.dp))
                        val ok = r == "OK"
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (ok) green.copy(alpha = if (isDark) 0.18f else 0.12f)
                                    else red.copy(alpha = if (isDark) 0.18f else 0.12f)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = if (ok) "Token verified successfully" else r,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = InterFamily,
                                color = if (ok) {
                                    if (isDark) Color(0xFF30D158) else green
                                } else {
                                    if (isDark) Color(0xFFFF453A) else red
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Compact theme-based Connect pill (not a giant blue bar)
                val compactBg = when {
                    !canConnect -> if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
                    isDark -> Color(0xFFECECEC)
                    else -> Color(0xFF111111)
                }
                val compactFg = when {
                    !canConnect -> if (isDark) Color(0xFF636366) else Color(0xFF8E8E93)
                    isDark -> Color(0xFF111111)
                    else -> Color.White
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(compactBg)
                        .clickable(enabled = canConnect) {
                            testing = true
                            testResult = null
                            scope.launch {
                                val tok = if (needsToken) token.trim() else ""
                                val err = ConnectorsManager.verify(def.id, tok)
                                testResult = err ?: "OK"
                                testing = false
                                if (err == null) {
                                    ConnectorsManager.saveState(
                                        def.id,
                                        ConnectorsManager.ConnectorState(
                                            enabled = true,
                                            token = tok,
                                            verified = true
                                        )
                                    )
                                    // GitHub connector ↔ agent token must stay in sync
                                    if (def.id == ConnectorsManager.GITHUB && tok.isNotBlank()) {
                                        val p = com.ahamai.app.data.PreferencesManager(context)
                                        p.saveGithubToken(tok)
                                        com.ahamai.app.data.AuthManager.uid()?.let { p.saveGithubOwner(it) }
                                        com.ahamai.app.data.AuthManager.backupGithubToken(context)
                                    }
                                    onDismiss()
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (testing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = compactFg
                        )
                    } else {
                        if (def.id == ConnectorsManager.GITHUB) {
                            coil.compose.AsyncImage(
                                model = "https://www.google.com/s2/favicons?domain=github.com&sz=64",
                                contentDescription = "GitHub",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isAlreadyConnected) "Update" else "Connect",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFamily,
                            color = compactFg
                        )
                    }
                }

                if (isAlreadyConnected) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Disconnect",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = InterFamily,
                        color = red,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { showDisconnectConfirm = true }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                if (def.isCustom) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Delete custom connector",
                        fontSize = 13.sp,
                        fontFamily = InterFamily,
                        color = muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                ConnectorsManager.deleteCustomConnector(def.id)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showDisconnectConfirm) {
        AlertDialog(
            onDismissRequest = { showDisconnectConfirm = false },
            containerColor = tokenCardBg,
            title = {
                Text(
                    "Disconnect ${def.name}?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = primary
                )
            },
            text = {
                Text(
                    "The agent will lose access to your ${def.name} account.",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = secondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ConnectorsManager.disconnect(def.id)
                    if (def.id == ConnectorsManager.GITHUB) {
                        // Full agent GitHub disconnect (token + cloud backup)
                        scope.launch {
                            com.ahamai.app.data.AuthManager.clearGithubBackup(context)
                        }
                    }
                    token = ""
                    testResult = null
                    showDisconnectConfirm = false
                    onDismiss()
                }) {
                    Text("Disconnect", fontSize = 13.sp, fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, color = red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectConfirm = false }) {
                    Text("Cancel", fontSize = 13.sp, fontFamily = InterFamily, color = secondary)
                }
            }
        )
    }
}
