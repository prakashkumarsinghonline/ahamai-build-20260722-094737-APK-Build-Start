package com.ahamai.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahamai.app.ui.icons.SkillIconGlyph
import com.ahamai.app.ui.theme.InterFamily

/**
 * Compact list row with icon, title, description, and optional toggle switch.
 */
@Composable
fun SkillManageRow(
    skillId: String,
    storedIcon: String,
    title: String,
    description: String,
    primary: Color,
    secondary: Color,
    showToggle: Boolean = false,
    checked: Boolean = false,
    toggleEnabled: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = false,
    sep: Color = Color.Transparent,
    rowBg: Color? = null
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (rowBg != null) Modifier.background(rowBg) else Modifier)
                .clickable(enabled = onClick != null) { onClick?.invoke() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon chip
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSystemInDarkTheme()) Color(0xFF2A2A30) else Color(0xFFF0F0F5)
                    ),
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

            // Title + desc
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
                if (description.isNotBlank()) {
                    Text(
                        description,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontFamily = InterFamily,
                        color = secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Toggle
            if (showToggle) {
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = checked,
                    onCheckedChange = {
                        if (toggleEnabled) onToggle?.invoke(it)
                    },
                    enabled = toggleEnabled,
                    modifier = Modifier.scale(0.72f),
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = if (isSystemInDarkTheme()) Color(0xFF0A84FF) else Color(0xFF0A84FF),
                        checkedThumbColor = Color.White,
                        uncheckedTrackColor = if (isSystemInDarkTheme()) Color(0xFF3A3A3C) else Color(0xFFD1D1D1),
                        uncheckedThumbColor = if (isSystemInDarkTheme()) Color(0xFF8E8E93) else Color.White,
                        disabledCheckedTrackColor = Color(0xFF3A3A3C).copy(alpha = 0.5f),
                        disabledUncheckedTrackColor = Color(0xFF3A3A3C).copy(alpha = 0.3f)
                    )
                )
            }
        }

        // Divider
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 62.dp)
                    .height(0.5.dp)
                    .background(sep)
            )
        }
    }
}