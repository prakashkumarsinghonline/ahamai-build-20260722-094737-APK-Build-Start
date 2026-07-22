package com.ahamai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.SkillManager
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Dialog for creating or editing a custom skill.
 * Supports form mode, paste SKILL.md mode, and URL fetch.
 */
@Composable
fun AddEditSkillDialog(
    isDark: Boolean,
    initial: SkillManager.CustomSkill?,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, content: String) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var desc by remember(initial) { mutableStateOf(initial?.description ?: "") }
    var content by remember(initial) { mutableStateOf(initial?.content ?: "") }
    var importMd by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(0) } // 0 = form, 1 = paste SKILL.md
    var nameError by remember { mutableStateOf<String?>(null) }
    var skillUrl by remember { mutableStateOf("") }
    var isFetching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val bg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF5F5F7)
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val secondary = if (isDark) Color(0xFFA0A0A0) else Color(0xFF6B6B6B)
    val muted = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
    val inputBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val border = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5E5)

    fun validateAndSave() {
        val n = name.trim()
        if (n.isBlank()) {
            nameError = "Name is required"
            return
        }
        val finalContent = if (mode == 1 && importMd.isNotBlank()) importMd.trim() else content.trim()
        if (finalContent.isBlank()) {
            nameError = "SKILL.md content is required"
            return
        }
        onSave(n, desc.trim(), finalContent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = bg,
        titleContentColor = primary,
        textContentColor = secondary,
        shape = RoundedCornerShape(16.dp),
        title = {
            Text(
                if (initial != null) "Edit Skill" else "Add Skill",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ── URL fetch ───────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    BasicTextField(
                        value = skillUrl,
                        onValueChange = { skillUrl = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .border(0.5.dp, border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        textStyle = TextStyle(
                            fontSize = 12.sp,
                            fontFamily = InterFamily,
                            color = primary
                        ),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (skillUrl.isEmpty()) {
                                Text(
                                    "Raw SKILL.md URL...",
                                    fontSize = 12.sp,
                                    fontFamily = InterFamily,
                                    color = muted
                                )
                            }
                            inner()
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isFetching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = primary
                        )
                    } else {
                        Text(
                            "Fetch",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            color = if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    val url = skillUrl.trim()
                                    if (url.isNotBlank()) {
                                        scope.launch {
                                            isFetching = true
                                            try {
                                                val fetched = withContext(Dispatchers.IO) {
                                                    URL(url).readText()
                                                }
                                                importMd = fetched
                                                mode = 1
                                                val fn = url.substringAfterLast('/').substringBeforeLast('.')
                                                if (name.isBlank() && fn.isNotBlank() && fn != "SKILL") {
                                                    name = fn.replace('-', ' ').replace('_', ' ')
                                                }
                                            } catch (_: Exception) {
                                                nameError = "Failed to fetch URL"
                                            }
                                            isFetching = false
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                // ── Mode tabs ───────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Color(0xFF1C1C1E) else Color(0xFFECECEC))
                        .padding(2.dp)
                ) {
                    listOf("Form", "Paste SKILL.md").forEachIndexed { idx, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (mode == idx) if (isDark) Color(0xFF2C2C2E) else Color.White else Color.Transparent)
                                .clickable { mode = idx }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (mode == idx) FontWeight.Medium else FontWeight.Normal,
                                fontFamily = InterFamily,
                                color = if (mode == idx) primary else secondary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (mode == 0) {
                    // ── Form mode ───────────────────────────────────────────
                    // Name
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .border(0.5.dp, border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = name,
                            onValueChange = { name = it; nameError = null },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 14.sp, fontFamily = InterFamily, color = primary),
                            singleLine = true,
                            cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
                            decorationBox = { inner ->
                                if (name.isEmpty()) {
                                    Text("Skill name", fontSize = 14.sp, fontFamily = InterFamily, color = muted)
                                }
                                inner()
                            }
                        )
                    }
                    if (nameError != null) {
                        Text(
                            nameError ?: "",
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            color = Color(0xFFEF4444),
                            modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // Description
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .border(0.5.dp, border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        BasicTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 14.sp, fontFamily = InterFamily, color = primary),
                            singleLine = true,
                            cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
                            decorationBox = { inner ->
                                if (desc.isEmpty()) {
                                    Text("Short description (optional)", fontSize = 14.sp, fontFamily = InterFamily, color = muted)
                                }
                                inner()
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // Content (scrollable textarea)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .border(0.5.dp, border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = content,
                            onValueChange = { content = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontFamily = JetBrainsMonoFamily,
                                color = primary
                            ),
                            cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
                            decorationBox = { inner ->
                                if (content.isEmpty()) {
                                    Text(
                                        "# Markdown / SKILL.md content",
                                        fontSize = 12.sp,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = muted
                                    )
                                }
                                inner()
                            }
                        )
                    }
                } else {
                    // ── Paste SKILL.md mode ─────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .border(0.5.dp, border, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = importMd,
                            onValueChange = { importMd = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            textStyle = TextStyle(
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                                fontFamily = JetBrainsMonoFamily,
                                color = primary
                            ),
                            cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
                            decorationBox = { inner ->
                                if (importMd.isEmpty()) {
                                    Text(
                                        "Paste your SKILL.md content here...",
                                        fontSize = 12.sp,
                                        fontFamily = JetBrainsMonoFamily,
                                        color = muted
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { validateAndSave() },
                colors = ButtonDefaults.textButtonColors(contentColor = if (isDark) Color(0xFF0A84FF) else Color(0xFF0A84FF))
            ) {
                Text(
                    if (initial != null) "Update" else "Create",
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                    ) {
                        Text(
                            "Delete",
                            fontWeight = FontWeight.Medium,
                            fontFamily = InterFamily,
                            fontSize = 14.sp
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Medium,
                        fontFamily = InterFamily,
                        fontSize = 14.sp
                    )
                }
            }
        }
    )
}