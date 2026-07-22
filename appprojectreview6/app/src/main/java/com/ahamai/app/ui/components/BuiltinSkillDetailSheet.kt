package com.ahamai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.data.SkillManager
import com.ahamai.app.ui.icons.SkillIconGlyph
import com.ahamai.app.ui.theme.InterFamily

/**
 * Bottom sheet detail view for a built-in skill — shows icon, name, description,
 * pin/unpin action, and a prominent Pin button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltinSkillDetailSheet(
    skill: SkillManager.Skill,
    isDark: Boolean,
    primary: Color,
    secondary: Color,
    muted: Color,
    cell: Color,
    onDismiss: () -> Unit,
    onPin: () -> Unit,
    isPinned: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = cell,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            SkillIconGlyph(
                skillId = skill.id,
                storedIcon = SkillManager.getIconSvg(skill.id).ifBlank { skill.iconSvg },
                tint = primary,
                size = 22.dp
            )
            Spacer(Modifier.height(10.dp))
            Text(skill.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = primary)
            Spacer(Modifier.height(4.dp))
            Text(skill.description, fontSize = 13.sp, lineHeight = 18.sp, fontFamily = InterFamily, color = secondary)
            Spacer(Modifier.height(16.dp))
            Text(
                "Pin this skill so the agent always applies its expertise in this session.",
                fontSize = 12.sp,
                fontFamily = InterFamily,
                color = muted
            )
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(if (isDark) Color(0xFFEBEBF0) else Color(0xFF111111))
                    .clickable(onClick = onPin),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isPinned) "Unpin from session" else "Pin for this session",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = if (isDark) Color(0xFF111111) else Color(0xFFF5F5F7)
                )
            }
        }
    }
}