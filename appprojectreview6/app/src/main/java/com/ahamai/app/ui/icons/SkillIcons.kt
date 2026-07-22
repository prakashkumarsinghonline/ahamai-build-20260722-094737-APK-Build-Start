package com.ahamai.app.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared skill icon system.
 *
 * All skills (built-in, default, admin/new creation) render the same unified
 * Skills mark ([Lucide.Skills]). Per-skill SVG storage is ignored for display.
 */
object SkillIcons {

    data class Option(
        val key: String,
        val label: String,
        val icon: ImageVector
    )

    /** Single option — same glyph for every skill. */
    val catalog: List<Option> = listOf(
        Option("skills", "Skill", Lucide.Skills),
    )

    private val byKey: Map<String, ImageVector> =
        catalog.associate { it.key to it.icon }

    fun isRawSvg(stored: String?): Boolean {
        if (stored.isNullOrBlank()) return false
        val t = stored.trim()
        return t.contains("<svg", ignoreCase = true) ||
            (t.startsWith("<") && t.contains("path", ignoreCase = true))
    }

    fun encodeKey(key: String): String {
        val k = normalizeKey(key)
        return if (k.isBlank()) "" else "lucide:$k"
    }

    fun decodeKey(stored: String?): String {
        if (stored.isNullOrBlank() || isRawSvg(stored)) return ""
        val t = stored.trim()
        return normalizeKey(
            when {
                t.startsWith("lucide:", ignoreCase = true) -> t.substringAfter(':')
                t.startsWith("icon:", ignoreCase = true) -> t.substringAfter(':')
                else -> t
            }
        )
    }

    fun iconByKey(key: String): ImageVector? {
        val k = normalizeKey(key)
        if (k.isBlank()) return null
        return byKey[k] ?: Lucide.Skills
    }

    /** Always the unified skill glyph. */
    fun resolve(skillId: String, storedIcon: String? = null): ImageVector = Lucide.Skills

    fun iconForSkillId(id: String): ImageVector = Lucide.Skills

    fun defaultKeyForSkillId(id: String): String = "skills"

    /** Canonical SVG (same paths as [Lucide.Skills]). */
    const val UNIFIED_SKILL_SVG: String =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M17.5055 2.01874C12.8289 2.83455 12 7.5 12 7.5V22C12 22 12.8867 17.1272 18.0004 16.5588C18.5493 16.4978 19 16.0576 19 15.5058V3.39309C19 2.5654 18.3216 1.87638 17.5055 2.01874Z"></path><path d="M5.33333 5.00001C7.79379 4.99657 10.1685 5.88709 12 7.5V22C10.1685 20.3871 7.79379 19.4966 5.33333 19.5C3.77132 19.5 2.99032 19.5 2.64526 19.2792C2.4381 19.1466 2.35346 19.0619 2.22086 18.8547C2 18.5097 2 17.8941 2 16.6629V8.40322C2 6.97543 2 6.26154 2.54874 5.68286C3.09748 5.10418 3.65923 5.07432 4.78272 5.0146C4.965 5.00491 5.14858 5.00001 5.33333 5.00001Z"></path><path d="M12 22.001C13.8315 20.3881 16.2062 19.4976 18.6667 19.501C20.2287 19.501 21.0097 19.501 21.3547 19.2802C21.5619 19.1476 21.6465 19.0629 21.7791 18.8558C22 18.5107 22 17.8951 22 16.6639V8.40424C22 6.97645 22 6.26256 21.4513 5.68388C20.9025 5.1052 20.1235 5.05972 19 5"></path></svg>"""

    private fun normalizeKey(raw: String): String =
        raw.trim().lowercase()
            .replace('_', '-')
            .replace(' ', '-')
            .replace(Regex("-+"), "-")
            .trim('-')
}

/** Every skill row uses the same unified mark. */
@Composable
fun SkillIconGlyph(
    skillId: String,
    storedIcon: String? = null,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp
) {
    Icon(
        imageVector = Lucide.Skills,
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size)
    )
}
