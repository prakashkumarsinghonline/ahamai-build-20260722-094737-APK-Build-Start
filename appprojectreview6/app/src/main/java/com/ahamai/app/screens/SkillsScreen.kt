package com.ahamai.app.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ahamai.app.data.SkillManager
import com.ahamai.app.ui.components.AddEditSkillDialog
import com.ahamai.app.ui.components.BuiltinSkillDetailSheet
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.icons.SkillIconGlyph
import com.ahamai.app.ui.theme.InterFamily

/**
 * iOS Settings-style Skills manager.
 * Grouped inset lists, large title, clean monochrome chrome.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    onBack: () -> Unit,
    onSkillCreator: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    LaunchedEffect(Unit) { SkillManager.init(context) }

    // iOS system greys
    val bg = if (isDark) Color(0xFF000000) else Color(0xFFF2F2F7)
    val group = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val primary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
    val secondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val accent = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val sep = if (isDark) Color(0xFF38383A) else Color(0xFFC6C6C8)
    val searchBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFE5E5EA)
    val iconChip = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)

    val view = LocalView.current
    val bgArgb = if (isDark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    DisposableEffect(isDark, bgArgb) {
        val activityWindow = (view.context as? android.app.Activity)?.window
        if (activityWindow != null) {
            WindowCompat.setDecorFitsSystemWindows(activityWindow, false)
            activityWindow.statusBarColor = bgArgb
            activityWindow.navigationBarColor = bgArgb
            WindowCompat.getInsetsController(activityWindow, activityWindow.decorView).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }
        onDispose {
            activityWindow?.statusBarColor = android.graphics.Color.TRANSPARENT
            activityWindow?.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    var tick by remember { mutableIntStateOf(0) }
    val adminVer = SkillManager.adminConfigVersion
    val custom = remember(tick) { SkillManager.customSkills() }
    val library = remember(adminVer, tick) {
        listOf(SkillManager.BUNDLED_SKILL_CREATOR) + SkillManager.ALL_SKILLS
    }

    var showAdd by remember { mutableStateOf(false) }
    var editSkill by remember { mutableStateOf<SkillManager.CustomSkill?>(null) }
    var detailSkill by remember { mutableStateOf<SkillManager.Skill?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    var pinnedIds by remember { mutableStateOf(SkillManager.getLoadedSkills().map { it.id }.toSet()) }
    fun refreshPinned() { pinnedIds = SkillManager.getLoadedSkills().map { it.id }.toSet() }

    val q = searchQuery.trim()
    val filteredCustom = remember(custom, q) {
        if (q.isBlank()) custom
        else custom.filter {
            it.name.contains(q, true) || it.description.contains(q, true) || it.id.contains(q, true)
        }
    }
    val filteredLibrary = remember(library, q) {
        if (q.isBlank()) library
        else library.filter {
            it.name.contains(q, true) || it.description.contains(q, true) || it.id.contains(q, true)
        }
    }
    val pinnedSkills = remember(pinnedIds, library, custom, tick) {
        pinnedIds.mapNotNull { id ->
            SkillManager.findSkill(id)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Nav bar ──────────────────────────────────────────────────────────
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
            IconButton(onClick = { showAdd = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add", tint = accent)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Large title
            item {
                Text(
                    "Skills",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFamily,
                    color = primary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Text(
                    "Packages · scripts · /slash · progressive load",
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
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 16.sp, color = primary, fontFamily = InterFamily),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search", fontSize = 16.sp, color = secondary, fontFamily = InterFamily)
                            }
                            inner()
                        }
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            // Skill creator promo card
            item {
                IosGroup(group) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSkillCreator?.invoke()
                                    ?: run {
                                        SkillManager.loadSkill("skill-creator")
                                        refreshPinned()
                                        tick++
                                    }
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accent.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Lucide.Skills, null, tint = accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Create a skill",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFamily,
                                color = primary
                            )
                            Text(
                                "/skill-creator · SAVE_SKILL (asks you) · disk packages + scripts",
                                fontSize = 13.sp,
                                fontFamily = InterFamily,
                                color = secondary,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, tint = secondary.copy(0.6f), modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Agent Skills use progressive disclosure: names first, full body on load, resources on demand.",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = secondary,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 18.dp),
                    lineHeight = 16.sp
                )
            }

            // Pinned (session-loaded)
            if (pinnedSkills.isNotEmpty() && q.isBlank()) {
                item { IosSectionLabel("SESSION", secondary) }
                item {
                    IosGroup(group) {
                        pinnedSkills.forEachIndexed { index, skill ->
                            IosSkillRow(
                                skillId = skill.id,
                                title = skill.name,
                                subtitle = "/${skill.id}",
                                storedIcon = SkillManager.getIconSvg(skill.id).ifBlank { skill.iconSvg },
                                primary = primary,
                                secondary = secondary,
                                iconChip = iconChip,
                                showDivider = index < pinnedSkills.lastIndex,
                                sep = sep,
                                trailing = {
                                    Text(
                                        "Loaded",
                                        fontSize = 13.sp,
                                        color = accent,
                                        fontFamily = InterFamily
                                    )
                                },
                                onClick = {
                                    if (skill.id == "skill-creator") onSkillCreator?.invoke()
                                    else detailSkill = skill
                                }
                            )
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(18.dp)) }
            }

            // My Skills
            item { IosSectionLabel("MY SKILLS", secondary) }
            if (filteredCustom.isEmpty()) {
                item {
                    IosGroup(group) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp, horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (q.isNotBlank()) "No matches" else "No custom skills",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = primary,
                                fontFamily = InterFamily
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (q.isBlank()) "Tap + or use /skill-creator in Agent"
                                else "Try another search",
                                fontSize = 13.sp,
                                color = secondary,
                                fontFamily = InterFamily
                            )
                        }
                    }
                }
            } else {
                item {
                    IosGroup(group) {
                        filteredCustom.forEachIndexed { index, s ->
                            val skill = s.toSkill()
                            IosSkillRow(
                                skillId = s.id,
                                title = s.name,
                                subtitle = s.description,
                                storedIcon = SkillManager.getIconSvg(s.id),
                                primary = primary,
                                secondary = secondary,
                                iconChip = iconChip,
                                showDivider = index < filteredCustom.lastIndex,
                                sep = sep,
                                trailing = {
                                    IosSwitch(
                                        checked = s.enabled,
                                        accent = accent,
                                        onCheckedChange = {
                                            SkillManager.setCustomEnabled(s.id, it)
                                            tick++
                                        }
                                    )
                                },
                                onClick = { editSkill = s }
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Custom skills sync to your account. Agent can SAVE_SKILL after you Allow.",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = secondary,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 18.dp),
                    lineHeight = 16.sp
                )
            }

            // Library
            item { IosSectionLabel("LIBRARY", secondary) }
            if (filteredLibrary.isEmpty()) {
                item {
                    IosGroup(group) {
                        Text(
                            if (q.isNotBlank()) "No library skills match"
                            else "Library empty — admin can publish skills remotely",
                            fontSize = 14.sp,
                            color = secondary,
                            fontFamily = InterFamily,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                item {
                    IosGroup(group) {
                        filteredLibrary.forEachIndexed { index, skill ->
                            val adminAllowed = SkillManager.isAdminAllowed(skill.id)
                            val userOn = skill.id !in SkillManager.disabledBuiltins()
                            val checked = adminAllowed && userOn
                            IosSkillRow(
                                skillId = skill.id,
                                title = skill.name,
                                subtitle = skill.description,
                                storedIcon = SkillManager.getIconSvg(skill.id).ifBlank { skill.iconSvg },
                                primary = if (adminAllowed) primary else secondary,
                                secondary = secondary,
                                iconChip = iconChip,
                                showDivider = index < filteredLibrary.lastIndex,
                                sep = sep,
                                trailing = {
                                    IosSwitch(
                                        checked = checked,
                                        enabled = adminAllowed,
                                        accent = accent,
                                        onCheckedChange = { want ->
                                            if (adminAllowed) {
                                                SkillManager.setBuiltinEnabled(skill.id, want)
                                                tick++
                                            }
                                        }
                                    )
                                },
                                onClick = {
                                    if (skill.id == "skill-creator") {
                                        onSkillCreator?.invoke()
                                    } else {
                                        detailSkill = skill
                                    }
                                }
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Toggle off to hide from the agent catalog. Tap a row for details or pin to this session.",
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = secondary,
                    modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 8.dp, bottom = 8.dp),
                    lineHeight = 16.sp
                )
            }
        }
    }

    if (showAdd) {
        AddEditSkillDialog(
            isDark = isDark,
            initial = null,
            onDismiss = { showAdd = false; tick++ },
            onSave = { name, desc, content ->
                val raw = content.trim()
                if (raw.startsWith("---")) {
                    SkillManager.importSkillMd(raw, enabled = true)
                        ?: SkillManager.createCustomSkill(name, desc, content, enabled = true)
                } else {
                    SkillManager.createCustomSkill(name, desc, content, enabled = true)
                }
                showAdd = false
                tick++
            }
        )
    }

    editSkill?.let { s ->
        AddEditSkillDialog(
            isDark = isDark,
            initial = s,
            onDismiss = { editSkill = null },
            onSave = { name, desc, content ->
                if (s.id.isNotBlank()) {
                    val raw = content.trim()
                    if (raw.startsWith("---")) {
                        val parsed = SkillManager.parseSkillMd(raw)
                        if (parsed != null) {
                            SkillManager.saveCustomSkill(
                                s.copy(
                                    id = s.id,
                                    name = name.ifBlank { parsed.name },
                                    description = parsed.description.ifBlank { desc },
                                    content = parsed.content,
                                    resources = parsed.resources.ifEmpty { s.resources },
                                    whenToUse = parsed.whenToUse,
                                    disableModelInvocation = parsed.disableModelInvocation,
                                    userInvocable = parsed.userInvocable,
                                    allowedTools = parsed.allowedTools,
                                    argumentHint = parsed.argumentHint
                                )
                            )
                        } else {
                            SkillManager.saveCustomSkill(
                                s.copy(name = name, description = desc, content = content)
                            )
                        }
                    } else {
                        SkillManager.saveCustomSkill(
                            s.copy(name = name, description = desc, content = content)
                        )
                    }
                }
                editSkill = null
                tick++
            },
            onDelete = {
                SkillManager.deleteCustomSkill(s.id)
                editSkill = null
                tick++
            }
        )
    }

    detailSkill?.let { skill ->
        BuiltinSkillDetailSheet(
            skill = skill,
            isDark = isDark,
            primary = primary,
            secondary = secondary,
            muted = secondary,
            cell = group,
            onDismiss = { detailSkill = null },
            onPin = {
                if (skill.id in pinnedIds) SkillManager.unloadSkill(skill.id)
                else SkillManager.loadSkill(skill.id)
                refreshPinned()
                tick++
            },
            isPinned = skill.id in pinnedIds
        )
    }
}

@Composable
private fun IosSectionLabel(text: String, color: Color) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = InterFamily,
        color = color,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(start = 32.dp, end = 16.dp, bottom = 6.dp, top = 4.dp)
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
private fun IosSkillRow(
    skillId: String,
    title: String,
    subtitle: String,
    storedIcon: String,
    primary: Color,
    secondary: Color,
    iconChip: Color,
    showDivider: Boolean,
    sep: Color,
    trailing: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(iconChip),
                contentAlignment = Alignment.Center
            ) {
                SkillIconGlyph(
                    skillId = skillId,
                    storedIcon = storedIcon,
                    tint = primary,
                    size = 18.dp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = InterFamily,
                    color = primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        maxLines = 1,
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 62.dp)
                    .height(0.5.dp)
                    .background(sep.copy(alpha = 0.55f))
            )
        }
    }
}

/** Compact iOS-like switch (blue when on). */
@Composable
private fun IosSwitch(
    checked: Boolean,
    accent: Color,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    val track by animateColorAsState(
        targetValue = when {
            !enabled -> Color(0xFF3A3A3C).copy(alpha = 0.4f)
            checked -> accent
            else -> Color(0xFF39393D)
        },
        label = "sw"
    )
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.scale(0.82f),
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = track,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = track,
            disabledCheckedTrackColor = track,
            disabledUncheckedTrackColor = track
        )
    )
}
