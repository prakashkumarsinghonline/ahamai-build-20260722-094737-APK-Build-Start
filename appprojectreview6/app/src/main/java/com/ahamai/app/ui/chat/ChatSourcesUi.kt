package com.ahamai.app.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ahamai.app.screens.SourceInfo
import com.ahamai.app.ui.components.IosBottomSheet
import com.ahamai.app.ui.icons.Phosphor
import com.ahamai.app.ui.theme.ChatPalette
import com.ahamai.app.ui.theme.InterFamily

/**
 * iOS Sources strip — stacked favicons (no grey tiles) + "N sources".
 * Opens iOS sheet with Settings-style list rows (hairline dividers, no card fills).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSourcesBar(
    sources: List<SourceInfo>,
    isDark: Boolean,
    onSourceClick: (String) -> Unit,
    onOpenSheet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (sources.isEmpty()) return

    var showSheet by remember { mutableStateOf(false) }
    val textPrimary = if (isDark) ChatPalette.DarkInk else ChatPalette.LightInk
    val textMuted = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkSecondary
    val ringColor = if (isDark) ChatPalette.DarkBg else ChatPalette.LightBg
    val visible = sources.take(4)
    val overflow = (sources.size - visible.size).coerceAtLeast(0)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = ChatMotion.butterSpring,
        label = "sourcesChipScale"
    )

    Row(
        modifier = modifier
            .padding(bottom = 10.dp, top = 2.dp)
            .scale(scale)
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                showSheet = true
                onOpenSheet?.invoke()
            }
            .padding(vertical = 4.dp).padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Stacked favicons — transparent, no fill behind
        Box(
            modifier = Modifier
                .height(20.dp)
                .width((16 + (visible.size - 1).coerceAtLeast(0) * 11 + if (overflow > 0) 11 else 0).dp)
        ) {
            visible.forEachIndexed { i, src ->
                AsyncImage(
                    model = src.favicon
                        ?: "https://www.google.com/s2/favicons?domain=${src.domain}&sz=64",
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .offset(x = (i * 11).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(1.25.dp, ringColor, CircleShape)
                )
            }
            if (overflow > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = (visible.size * 11).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .border(1.25.dp, ringColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+$overflow",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textMuted,
                        fontFamily = InterFamily
                    )
                }
            }
        }
        Text(
            text = if (sources.size == 1) "1 source" else "${sources.size} sources",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            fontFamily = InterFamily,
            letterSpacing = (-0.1f).sp
        )
        Icon(
            imageVector = Phosphor.CaretDown,
            contentDescription = null,
            tint = textMuted,
            modifier = Modifier
                .size(12.dp)
                .rotate(-90f)
        )
    }

    if (showSheet) {
        IosBottomSheet(
            isDark = isDark,
            onDismissRequest = { showSheet = false },
            noHandle = false
        ) {
            Text(
                text = "Sources",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                fontFamily = InterFamily,
                letterSpacing = (-0.2f).sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )
            Text(
                text = "From the web · used for this answer",
                fontSize = 13.sp,
                color = textMuted,
                fontFamily = InterFamily,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(sources.size) { index ->
                    ChatSourceListRow(
                        index = index + 1,
                        source = sources[index],
                        isDark = isDark,
                        showDivider = index < sources.lastIndex,
                        onClick = {
                            showSheet = false
                            onSourceClick(sources[index].url)
                        }
                    )
                }
            }
        }
    }
}

/**
 * iOS Settings-style source row — no card background; hairline divider only.
 */
@Composable
fun ChatSourceListRow(
    index: Int,
    source: SourceInfo,
    isDark: Boolean,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    val textPrimary = if (isDark) ChatPalette.DarkInk else ChatPalette.LightInk
    val textMuted = if (isDark) ChatPalette.DarkInkSecondary else ChatPalette.LightInkSecondary
    val sep = if (isDark) ChatPalette.DarkBorder else ChatPalette.LightBorder
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = ChatMotion.butterSpring,
        label = "sourceRowScale"
    )
    val authority = ChatSourceAuthority.tagFor(source.domain)

    Column(modifier = Modifier.fillMaxWidth().scale(scale)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = source.favicon
                    ?: "https://www.google.com/s2/favicons?domain=${source.domain}&sz=64",
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(5.dp))
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.title.ifBlank { source.domain }.take(90),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = InterFamily,
                    lineHeight = 19.sp,
                    letterSpacing = (-0.15f).sp
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = source.domain,
                        fontSize = 12.sp,
                        color = textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = InterFamily,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (authority != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = authority,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted,
                            fontFamily = InterFamily
                        )
                    }
                }
                if (!source.snippet.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = source.snippet.take(110),
                        fontSize = 12.sp,
                        color = textMuted.copy(alpha = 0.88f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp,
                        fontFamily = InterFamily
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "$index",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = textMuted,
                fontFamily = InterFamily
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = 0.5.dp,
                color = sep,
                modifier = Modifier.padding(start = 56.dp)
            )
        }
    }
}

object ChatSourceAuthority {
    private val govTlds = setOf(
        "gov", "mil", "gov.uk", "gc.ca", "go.jp", "gouv.fr", "gov.au", "bund.de",
        "gov.in", "go.kr", "gov.br", "gov.za", "gov.ng", "admin.ch", "gov.sg"
    )
    private val govHosts = setOf(
        "nasa.gov", "who.int", "europa.eu", "un.org", "fda.gov", "cdc.gov", "whitehouse.gov"
    )
    private val academicTlds = setOf("edu", "ac.uk", "edu.au", "ac.jp", "ac.nz", "edu.cn", "ac.in")
    private val academicHosts = setOf(
        "arxiv.org", "scholar.google.com", "researchgate.net", "jstor.org", "springer.com",
        "wikipedia.org", "britannica.com"
    )
    private val forumHosts = setOf(
        "reddit.com", "quora.com", "stackexchange.com", "stackoverflow.com",
        "news.ycombinator.com", "medium.com"
    )
    private val newsHosts = setOf(
        "bbc.com", "bbc.co.uk", "cnn.com", "nytimes.com", "reuters.com", "theguardian.com",
        "washingtonpost.com", "wsj.com", "bloomberg.com", "aljazeera.com", "ndtv.com",
        "timesofindia.indiatimes.com", "thehindu.com", "apnews.com", "ft.com"
    )
    private val blogHosts = setOf(
        "medium.com", "substack.com", "wordpress.com", "blogspot.com", "tumblr.com"
    )
    private val officialBrands = mapOf(
        "google" to "google.com", "apple" to "apple.com", "microsoft" to "microsoft.com",
        "openai" to "openai.com", "anthropic" to "anthropic.com", "meta" to "meta.com",
        "amazon" to "amazon.com", "netflix" to "netflix.com", "github" to "github.com",
        "x" to "x.com", "twitter" to "x.com", "ahamai" to "ahamai.app"
    )

    fun tagFor(rawDomain: String): String? {
        val d = rawDomain.removePrefix("www.").lowercase()
        val tld = d.substringAfterLast('.', "")
        val labels = d.split('.').filter { it.isNotEmpty() }
        if ("gov" in labels || "mil" in labels || tld in govTlds || d in govHosts) return "Government"
        if ("edu" in labels || "ac" in labels || tld in academicTlds || d in academicHosts) return "Academic"
        if (d in forumHosts || d.endsWith(".reddit.com") || d.endsWith(".stackexchange.com")) return "Forum"
        if (d in newsHosts) return "News"
        if (d in blogHosts || d.endsWith(".blogspot.com") || d.endsWith(".wordpress.com") ||
            d.endsWith(".substack.com")
        ) return "Blog"
        val root = rootDomain(d)
        for ((_, brandRoot) in officialBrands) {
            if (root == brandRoot) return "Official"
        }
        return null
    }

    private fun rootDomain(d: String): String {
        val parts = d.split('.')
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else d
    }
}
