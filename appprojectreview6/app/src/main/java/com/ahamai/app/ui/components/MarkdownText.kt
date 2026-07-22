package com.ahamai.app.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import com.ahamai.app.ui.icons.AdminIcons
import androidx.compose.material.icons.outlined.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.ChatType
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser

/**
 * A citation reference for inline [n] markers rendered inside answer text (Perplexity-style).
 * index = the number shown, domain = for the favicon, url = opened on tap.
 */
data class MarkdownCitation(val index: Int, val domain: String, val url: String)

/** Citations available to inline [n] markers in the current MarkdownText subtree (empty by default). */
internal val LocalMarkdownCitations = compositionLocalOf { emptyList<MarkdownCitation>() }
internal val LocalOnCitationClick = compositionLocalOf<(String) -> Unit> { {} }

/**
 * Clean Markdown renderer for chat — uses JetBrains markdown parser for AST
 * but renders with pure Compose. No external renderer dependencies.
 *
 * Supports: headings, bold, italic, strikethrough, inline code, code blocks,
 * links, lists (ordered/unordered), blockquotes, tables, images, LaTeX blocks,
 * horizontal rules, and inline [n] citations (when a citations list is supplied).
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color = Color.Unspecified,
    isStreaming: Boolean = false,
    modifier: Modifier = Modifier,
    projectDir: String? = null,
    onOpenFile: ((String) -> Unit)? = null,
    citations: List<MarkdownCitation> = emptyList(),
    onCitationClick: (String) -> Unit = {},
    // Optional plain-paragraph size override (defaults → ChatGPT scale: 16sp / 28sp).
    // Lets a caller like narration render slightly smaller/tighter without affecting every
    // other MarkdownText call site (chat answers, etc.) which never pass these.
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val isDark = isSystemInDarkTheme()
    val effectiveColor = if (color == Color.Unspecified) {
        if (isDark) Color(0xFFECECEC) else Color(0xFF0D0D0D)
    } else color

    // Extract LaTeX blocks before parsing — only match $$...$$ blocks,
    // NOT \[...\] which is too common in code/docs and causes content loss.
    val (cleanText, latexBlocks) = remember(text) { extractLatexBlocksSafe(text) }

    CompositionLocalProvider(
        LocalMarkdownCitations provides citations,
        LocalOnCitationClick provides onCitationClick
    ) {
        // Always use the full AST renderer — even during streaming. The lightweight
        // streaming renderer used previously did not handle images, tables, blockquotes
        // or nested formatting correctly, so the user saw raw `**`, `![alt](url)` etc.
        // The JetBrains markdown parser is fast enough to re-parse on every token update
        // for typical streaming rates, and the result is that ALL markdown types render
        // live as tokens arrive.
        RenderedMarkdown(
            text = cleanText,
            color = effectiveColor,
            isDark = isDark,
            latexBlocks = latexBlocks,
            modifier = modifier,
            onOpenFile = onOpenFile,
            baseFontSize = fontSize,
            baseLineHeight = lineHeight
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Streaming Renderer (lightweight, no AST parsing)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StreamingMarkdown(text: String, color: Color, isDark: Boolean, modifier: Modifier) {
    val shown = remember { mutableStateOf("") }
    val annotated = remember { mutableStateOf(AnnotatedString("")) }

    if (text != shown.value || annotated.value.text.isEmpty()) {
        shown.value = text
        annotated.value = buildStreamingAnnotated(text, color, isDark)
    }

    if (annotated.value.text.isEmpty()) return

    Text(
        text = annotated.value,
        fontFamily = InterFamily,
        fontSize = ChatType.body,
        lineHeight = ChatType.bodyLine,
        modifier = modifier.fillMaxWidth()
    )
}

private fun buildStreamingAnnotated(text: String, baseColor: Color, isDark: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = text.split("\n")
    val codeColor = if (isDark) Color(0xFFB0B0B0) else Color(0xFF555555)
    val linkColor = if (isDark) Color(0xFF6AB7FF) else Color(0xFF2563EB)
    var inCodeFence = false

    for ((lineIdx, rawLine) in lines.withIndex()) {
        val line = rawLine.trimEnd()

        // Detect code fence start/end
        if (line.trimStart().matches(Regex("^`{3,}(?:\\w*)$")) || line.trimStart().matches(Regex("^~{3,}$"))) {
            inCodeFence = !inCodeFence
            builder.pushStyle(SpanStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = ChatType.code,
                color = codeColor,
                background = if (isDark) Color(0xFF1F1F1F) else Color(0xFFEAECEF)
            ))
            builder.append(line)
            builder.pop()
            if (lineIdx < lines.size - 1) builder.append("\n")
            continue
        }

        if (inCodeFence) {
            // Inside a code fence — render as monospace
            builder.pushStyle(SpanStyle(
                fontFamily = JetBrainsMonoFamily,
                fontSize = ChatType.code,
                color = codeColor,
                background = if (isDark) Color(0xFF1F1F1F) else Color(0xFFEAECEF)
            ))
            builder.append(line)
            builder.pop()
            if (lineIdx < lines.size - 1) builder.append("\n")
            continue
        }

        // Horizontal rule
        if (line.trimStart().matches(Regex("^-{3,}$")) ||
            line.trimStart().matches(Regex("^\\*{3,}$")) ||
            line.trimStart().matches(Regex("^_{3,}$"))) {
            builder.append("\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015\u2015")
            if (lineIdx < lines.size - 1) builder.append("\n")
            continue
        }

        when {
            line.startsWith("### ") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = ChatType.body, color = baseColor))
                appendInline(builder, line.removePrefix("### "), baseColor, codeColor, linkColor, isDark)
                builder.pop()
            }
            line.startsWith("## ") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = ChatType.h2, color = baseColor))
                appendInline(builder, line.removePrefix("## "), baseColor, codeColor, linkColor, isDark)
                builder.pop()
            }
            line.startsWith("# ") -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = ChatType.h1, color = baseColor))
                appendInline(builder, line.removePrefix("# "), baseColor, codeColor, linkColor, isDark)
                builder.pop()
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                val bullet = line.trimStart().removePrefix("- ").removePrefix("* ")
                builder.append("  \u2022  ")
                appendInline(builder, bullet, baseColor, codeColor, linkColor, isDark)
            }
            line.trimStart().matches(Regex("\\d+\\.\\s+.*")) -> {
                val num = line.trimStart().substringBefore(".")
                val content = line.trimStart().substringAfter(". ")
                builder.append("  $num.  ")
                appendInline(builder, content, baseColor, codeColor, linkColor, isDark)
            }
            line.startsWith("> ") -> {
                builder.pushStyle(SpanStyle(color = baseColor.copy(alpha = 0.6f), fontStyle = FontStyle.Italic))
                builder.append("  ${line.removePrefix("> ")}")
                builder.pop()
            }
            line.startsWith("|") && line.endsWith("|") -> {
                // Simple table row in streaming — show as-is but highlight pipes
                appendInline(builder, line, baseColor, codeColor, linkColor, isDark)
            }
            else -> {
                appendInline(builder, line, baseColor, codeColor, linkColor, isDark)
            }
        }
        if (lineIdx < lines.size - 1) builder.append("\n")
    }
    return builder.toAnnotatedString()
}

private fun appendInline(
    builder: AnnotatedString.Builder,
    text: String,
    baseColor: Color,
    codeColor: Color,
    linkColor: Color,
    isDark: Boolean
) {
    // Match: **bold**, __bold__, ~~strike~~, `code`, [text](url), *italic*, _italic_
    val regex = Regex(
        "(\\*\\*(.+?)\\*\\*)" +          // **bold**
        "|__(.+?)__" +                    // __bold__
        "|~~(.+?)~~" +                    // ~~strikethrough~~
        "|`([^`]+?)`" +                   // `inline code` — non-backtick inside
        "|``(.+?)``" +                    // ``inline code`` (double backtick)
        "|\\[([^\\[\\]]+?)\\]\\(([^()]*(?:\\([^()]*\\)[^()]*)*)\\)" + // [text](url) — handles nested parens in URL
        "|\\*([^*\\s][^*]*[^*\\s]?)\\*" + // *italic* — requires at least one non-* char around
        "|(?<![\\w])_([^_\\s][^_]*[^_\\s]?)_(?![\\w])" // _italic_ — word boundary aware
    )

    var lastEnd = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastEnd) {
            builder.append(text.substring(lastEnd, match.range.first))
        }
        lastEnd = match.range.last + 1

        val g = match.groupValues
        when {
            g[1].isNotBlank() -> { // **bold**
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                builder.append(g[2])
                builder.pop()
            }
            g[3].isNotBlank() -> { // __bold__
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                builder.append(g[3])
                builder.pop()
            }
            g[4].isNotBlank() -> { // ~~strikethrough~~
                builder.pushStyle(SpanStyle(
                    textDecoration = TextDecoration.LineThrough,
                    color = baseColor
                ))
                builder.append(g[4])
                builder.pop()
            }
            g[5].isNotBlank() -> { // `code`
                builder.pushStyle(SpanStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = ChatType.code,
                    color = codeColor,
                    background = if (isDark) Color(0xFF1F1F1F) else Color(0xFFEAECEF)
                ))
                builder.append(g[5])
                builder.pop()
            }
            g[6].isNotBlank() -> { // ``code`` (double backtick)
                builder.pushStyle(SpanStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = ChatType.code,
                    color = codeColor,
                    background = if (isDark) Color(0xFF1F1F1F) else Color(0xFFEAECEF)
                ))
                builder.append(g[6])
                builder.pop()
            }
            g[7].isNotBlank() -> { // [text](url)
                builder.pushStyle(SpanStyle(
                    color = linkColor,
                    textDecoration = TextDecoration.Underline
                ))
                builder.append(g[7])
                builder.pop()
            }
            g[8].isNotBlank() -> { // *italic*
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                builder.append(g[8])
                builder.pop()
            }
            g[9].isNotBlank() -> { // _italic_ — word boundary aware
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                builder.append(g[9])
                builder.pop()
            }
        }
    }
    // Append remaining text
    if (lastEnd < text.length) {
        builder.append(text.substring(lastEnd))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Full AST Renderer (used when message is complete)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RenderedMarkdown(
    text: String,
    color: Color,
    isDark: Boolean,
    latexBlocks: List<String>,
    modifier: Modifier,
    onOpenFile: ((String) -> Unit)?,
    baseFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    baseLineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    if (text.isBlank() && latexBlocks.isEmpty()) return

    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier) {
        if (text.isNotBlank()) {
            ASTRenderer(text, color, isDark, uriHandler, onOpenFile, baseFontSize, baseLineHeight)
        }
        latexBlocks.forEachIndexed { idx, formula ->
            key(idx) {
                Spacer(Modifier.padding(top = 4.dp))
                LatexBlockView(formula, color)
            }
        }
    }
}

/**
 * Walks the JetBrains markdown AST and emits Compose composables.
 */
@Composable
private fun ASTRenderer(
    src: String,
    color: Color,
    isDark: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onOpenFile: ((String) -> Unit)?,
    baseFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    baseLineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val ast = remember(src) {
        val flavour = GFMFlavourDescriptor()
        MarkdownParser(flavour).parse(MarkdownElementTypes.MARKDOWN_FILE, src)
    }
    SelectionContainer {
        Column(modifier = Modifier.fillMaxWidth()) {
            for (child in ast.children) {
                RenderNode(src, child, color, isDark, uriHandler, onOpenFile, baseFontSize, baseLineHeight)
            }
        }
    }
}

@Composable
private fun RenderNode(
    src: String,
    node: ASTNode,
    color: Color,
    isDark: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onOpenFile: ((String) -> Unit)?,
    baseFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    baseLineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    when (node.type) {
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6 -> {
            // Headings — render with inline formatting support
            val level = when (node.type) {
                MarkdownElementTypes.ATX_1 -> 1
                MarkdownElementTypes.ATX_2 -> 2
                MarkdownElementTypes.ATX_3 -> 3
                MarkdownElementTypes.ATX_4 -> 4
                MarkdownElementTypes.ATX_5 -> 5
                else -> 6
            }
            val rawText = node.getTextInNode(src).toString().trimStart('#').trim()
            val fontSize = when (level) {
                1 -> ChatType.h1
                2 -> ChatType.h2
                3 -> ChatType.h3
                else -> ChatType.body
            }
            val weight = if (level <= 2) FontWeight.Bold else FontWeight.SemiBold
            // Use RichText so inline formatting (**bold**, `code`, links) renders inside headings
            RichText(
                text = rawText,
                color = color,
                isDark = isDark,
                uriHandler = uriHandler,
                onOpenFile = onOpenFile,
                modifier = Modifier.padding(vertical = 4.dp),
                overrideFontSize = fontSize,
                overrideFontWeight = weight
            )
        }
        MarkdownElementTypes.PARAGRAPH -> {
            val text = node.getTextInNode(src).toString().trim()
            if (text.isNotEmpty()) {
                if (IMAGE_MD_REGEX.containsMatchIn(text)) {
                    RenderParagraphWithImages(text, color, isDark, uriHandler, onOpenFile)
                } else {
                    RichText(
                        text = text, color = color, isDark = isDark, uriHandler = uriHandler, onOpenFile = onOpenFile,
                        baseFontSize = baseFontSize, baseLineHeight = baseLineHeight
                    )
                }
            }
        }
        MarkdownElementTypes.CODE_BLOCK -> {
            val code = node.getTextInNode(src).toString().trim()
            CodeBlock(code = code, isDark = isDark)
        }
        MarkdownElementTypes.CODE_FENCE -> {
            val raw = node.getTextInNode(src).toString().trim()
            val parsed = parseFence(raw)
            val lang = parsed.first?.lowercase()?.trim()
            val code = parsed.second
            when (lang) {
                "mermaid" -> PreviewCard(kind = "diagram", payload = code, projectDir = "")
                "chart" -> PreviewCard(kind = "chart", payload = code, projectDir = "")
                else -> CodeBlock(code = code, language = lang, isDark = isDark)
            }
        }
        MarkdownElementTypes.BLOCK_QUOTE -> {
            val text = node.getTextInNode(src).toString().trim().removePrefix(">").trim()
            Surface(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                shape = RoundedCornerShape(0.dp),
                color = Color.Transparent
            ) {
                Row(modifier = Modifier.padding(start = 12.dp)) {
                    Surface(
                        modifier = Modifier.width(3.dp),
                        shape = RoundedCornerShape(2.dp),
                        color = if (isDark) Color(0xFF4B3FE3).copy(alpha = 0.5f) else Color(0xFF6B7280).copy(alpha = 0.4f)
                    ) {}
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = text,
                        fontSize = 14.sp,
                        color = color.copy(alpha = 0.7f),
                        fontStyle = FontStyle.Italic,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        MarkdownElementTypes.UNORDERED_LIST -> {
            RenderList(src, node, ordered = false, color = color, isDark = isDark, uriHandler = uriHandler, onOpenFile = onOpenFile, depth = 0)
        }
        MarkdownElementTypes.ORDERED_LIST -> {
            RenderList(src, node, ordered = true, color = color, isDark = isDark, uriHandler = uriHandler, onOpenFile = onOpenFile, depth = 0)
        }
        GFMElementTypes.TABLE -> {
            TableBlock(node = node, src = src, color = color, isDark = isDark)
        }
        // Horizontal rules / thematic breaks — handled in text pre-processing
        else -> {
            // Render children for document-level / unknown nodes.
            // Also handle GFMElementTypes that may not be explicitly listed
            // but whose children should still render.
            if (node.children.isNotEmpty()) {
                for (child in node.children) {
                    RenderNode(src, child, color, isDark, uriHandler, onOpenFile, baseFontSize, baseLineHeight)
                }
            }
        }
    }
}

/** Renders a horizontal rule (---, ***, ___). */
@Composable
private fun HorizontalRule(isDark: Boolean) {
    val ruleColor = if (isDark) Color(0xFF333333) else Color(0xFFD1D5DB)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(1.dp)
            .background(ruleColor)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Rich Text (inline formatting: bold, italic, code, links)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Recursively renders ordered/unordered lists with proper nesting & indentation. Each item's
 * own inline text is rendered with full markdown; nested sub-lists recurse with more indent.
 */
@Composable
private fun RenderList(
    src: String,
    node: ASTNode,
    ordered: Boolean,
    color: Color,
    isDark: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onOpenFile: ((String) -> Unit)?,
    depth: Int
) {
    Column(modifier = Modifier.padding(start = if (depth == 0) 2.dp else 16.dp, top = 2.dp, bottom = 2.dp)) {
        var num = 1
        node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }.forEach { item ->
            val subLists = item.children.filter {
                it.type == MarkdownElementTypes.UNORDERED_LIST || it.type == MarkdownElementTypes.ORDERED_LIST
            }
            val fullText = item.getTextInNode(src).toString()
            val ownRaw = if (subLists.isNotEmpty()) {
                val cut = (subLists.first().startOffset - item.startOffset).coerceIn(0, fullText.length)
                fullText.substring(0, cut)
            } else fullText
            val cleaned = ownRaw.trim()
                .removePrefix("- ").removePrefix("* ").removePrefix("+ ")
                .let { if (ordered) it.replaceFirst(Regex("^\\d+[.)]\\s*"), "") else it }
                .trim()
            if (cleaned.isNotEmpty()) {
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text(
                        text = if (ordered) "$num. " else "\u2022  ",
                        fontSize = ChatType.body,
                        color = color,
                        fontWeight = if (ordered) FontWeight.SemiBold else FontWeight.Normal
                    )
                    RichText(text = cleaned, color = color, isDark = isDark, uriHandler = uriHandler, onOpenFile = onOpenFile, modifier = Modifier.weight(1f))
                }
            }
            subLists.forEach { sub ->
                RenderList(
                    src, sub,
                    ordered = sub.type == MarkdownElementTypes.ORDERED_LIST,
                    color = color, isDark = isDark, uriHandler = uriHandler, onOpenFile = onOpenFile,
                    depth = depth + 1
                )
            }
            num++
        }
    }
}

/**
 * Extends RichText to support override font size and weight (for headings).
 * Falls back to normal RichText if no overrides.
 */
@Composable
private fun RichText(
    text: String,
    color: Color,
    isDark: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onOpenFile: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    overrideFontSize: androidx.compose.ui.unit.TextUnit? = null,
    overrideFontWeight: FontWeight? = null,
    // Base plain-paragraph size (narration uses this to render smaller/tighter than the
    // 16sp/26sp default; unrelated to overrideFontSize/Weight above, which are for headings).
    baseFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    baseLineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val citations = LocalMarkdownCitations.current
    val onCiteClick = LocalOnCitationClick.current

    val hasOverrides = overrideFontSize != null || overrideFontWeight != null
    val resolvedFontSize = if (baseFontSize != androidx.compose.ui.unit.TextUnit.Unspecified) baseFontSize else ChatType.body
    val resolvedLineHeight = if (baseLineHeight != androidx.compose.ui.unit.TextUnit.Unspecified) baseLineHeight else ChatType.bodyLine

    if (citations.isEmpty() || !CITATION_REGEX.containsMatchIn(text)) {
        val annotated = remember(text, isDark, color) {
            buildRichAnnotated(text, color, isDark)
        }
        // Apply overrides if needed, otherwise normal rendering
        if (hasOverrides) {
            val styled = remember(annotated, overrideFontSize, overrideFontWeight, color, isDark) {
                val builder = AnnotatedString.Builder()
                builder.pushStyle(SpanStyle(
                    fontSize = overrideFontSize ?: resolvedFontSize,
                    fontWeight = overrideFontWeight
                ))
                builder.append(annotated)
                builder.pop()
                builder.toAnnotatedString()
            }
            Text(
                text = styled,
                fontFamily = InterFamily,
                fontSize = resolvedFontSize,
                lineHeight = resolvedLineHeight,
                modifier = modifier
            )
        } else {
            Text(
                text = annotated,
                fontFamily = InterFamily,
                fontSize = resolvedFontSize,
                lineHeight = resolvedLineHeight,
                modifier = modifier
            )
        }
        return
    }

    val citeMap = remember(citations) { citations.associateBy { it.index } }
    val annotated = remember(text, isDark, citations, color) {
        buildRichAnnotatedWithCites(text, color, isDark, citeMap.keys)
    }
    val inlineContent = remember(citations, isDark) {
        citeMap.values.associate { c ->
            "cite_${c.index}" to InlineTextContent(
                Placeholder(width = 1.2.em, height = 1.35.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                CitationChip(index = c.index, domain = c.domain, isDark = isDark) { onCiteClick(c.url) }
            }
        }
    }
    if (hasOverrides) {
        val styled = remember(annotated, overrideFontSize, overrideFontWeight) {
            val builder = AnnotatedString.Builder()
            builder.pushStyle(SpanStyle(
                fontSize = overrideFontSize ?: resolvedFontSize,
                fontWeight = overrideFontWeight
            ))
            builder.append(annotated)
            builder.pop()
            builder.toAnnotatedString()
        }
        Text(
            text = styled,
            fontFamily = InterFamily,
            inlineContent = inlineContent,
            fontSize = resolvedFontSize,
            lineHeight = resolvedLineHeight,
            modifier = modifier
        )
    } else {
        Text(
            text = annotated,
            fontFamily = InterFamily,
            inlineContent = inlineContent,
            fontSize = resolvedFontSize,
            lineHeight = resolvedLineHeight,
            modifier = modifier
        )
    }
}

/**
 * Inline citation chip: favicon only (no number). Borderless, backgroundless —
 * just the favicon so it reads as a clean reference marker without a number
 * cluttering the inline text. Monochrome by design.
 */
@Composable
private fun CitationChip(index: Int, domain: String, isDark: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
    ) {
        AsyncImage(
            model = "https://www.google.com/s2/favicons?domain=$domain&sz=64",
            contentDescription = domain,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(12.dp).clip(RoundedCornerShape(2.dp))
        )
    }
}

/** Matches an inline citation marker like [1] or [12] (a bare number in brackets). */
private val CITATION_REGEX = Regex("\\[(\\d{1,3})]")

/**
 * Like [buildRichAnnotated] but replaces [n] markers (whose n is a known citation) with an
 * inline-content placeholder id "cite_n". Segments between markers keep full inline formatting.
 * CRITICAL: skipped citation markers (not in validIndices) are still included as plain text
 * so content is never lost.
 */
private fun buildRichAnnotatedWithCites(
    text: String,
    baseColor: Color,
    isDark: Boolean,
    validIndices: Set<Int>
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var last = 0
    for (m in CITATION_REGEX.findAll(text)) {
        val n = m.groupValues[1].toIntOrNull()
        // Append text BEFORE this match (regardless of whether it's a valid citation)
        if (m.range.first > last) {
            appendRichInline(builder, text.substring(last, m.range.first), baseColor, isDark)
        }
        if (n != null && n in validIndices) {
            builder.appendInlineContent("cite_$n", "[$n]")
        } else {
            // NOT a valid citation — append the raw text so it's NOT lost
            builder.append(m.value)
        }
        last = m.range.last + 1
    }
    if (last < text.length) {
        appendRichInline(builder, text.substring(last), baseColor, isDark)
    }
    return builder.toAnnotatedString()
}

private fun buildRichAnnotated(text: String, baseColor: Color, isDark: Boolean): AnnotatedString {
    val builder = AnnotatedString.Builder()
    appendRichInline(builder, text, baseColor, isDark)
    return builder.toAnnotatedString()
}

/** Appends a text segment with inline markdown formatting (bold/italic/code/strike/link). */
private fun appendRichInline(
    builder: AnnotatedString.Builder,
    text: String,
    baseColor: Color,
    isDark: Boolean
) {
    val linkColor = if (isDark) Color(0xFF6AB7FF) else Color(0xFF2563EB)
    val codeColor = if (isDark) Color(0xFFB0B0B0) else Color(0xFF555555)

    // Groups: 1/2 **bold**, 3 __bold__, 4 ~~strike~~, 5 `code`, 6 ``code``,
    //         7 link-text + 8 link-url, 9 *italic*, 10 _italic_
    val regex = Regex(
        "(\\*\\*(.+?)\\*\\*)" +
        "|__(.+?)__" +
        "|~~(.+?)~~" +
        "|`([^`]+?)`" +
        "|``(.+?)``" +
        "|\\[([^\\[\\]]+?)\\]\\(([^()]*(?:\\([^()]*\\)[^()]*)*)\\)" +
        "|\\*([^*\\s][^*]*[^*\\s]?)\\*" +
        "|(?<![\\w])_([^_\\s][^_]*[^_\\s]?)_(?![\\w])"
    )

    var lastEnd = 0
    for (match in regex.findAll(text)) {
        if (match.range.first > lastEnd) {
            builder.append(text.substring(lastEnd, match.range.first))
        }
        lastEnd = match.range.last + 1

        val g = match.groupValues
        when {
            g[1].isNotBlank() -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                builder.append(g[2]); builder.pop()
            }
            g[3].isNotBlank() -> {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor))
                builder.append(g[3]); builder.pop()
            }
            g[4].isNotBlank() -> {
                builder.pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = baseColor))
                builder.append(g[4]); builder.pop()
            }
            g[5].isNotBlank() -> {
                builder.pushStyle(SpanStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = ChatType.code,
                    color = codeColor,
                    background = if (isDark) Color(0xFF1F1F1F) else Color(0xFFEAECEF)
                ))
                builder.append(g[5]); builder.pop()
            }
            g[6].isNotBlank() -> {
                builder.pushStyle(SpanStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = ChatType.code,
                    color = codeColor,
                    background = if (isDark) Color(0xFF1F1F1F) else Color(0xFFEAECEF)
                ))
                builder.append(g[6]); builder.pop()
            }
            // Real clickable link: [text](url)
            g[7].isNotBlank() && g[8].isNotBlank() -> {
                val url = g[8].trim()
                builder.withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                        )
                    )
                ) {
                    append(g[7])
                }
            }
            g[9].isNotBlank() -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                builder.append(g[9]); builder.pop()
            }
            g[10].isNotBlank() -> {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = baseColor))
                builder.append(g[10]); builder.pop()
            }
        }
    }
    if (lastEnd < text.length) {
        builder.append(text.substring(lastEnd))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline Markdown Images (![alt](url)) — image search results, screenshots, etc.
// ─────────────────────────────────────────────────────────────────────────────

/** Matches a Markdown image: ![alt](url "optional title") */
private val IMAGE_MD_REGEX = Regex("!\\[([^\\]]*)]\\(\\s*(\\S+?)(?:\\s+\"[^\"]*\")?\\s*\\)")

/**
 * Renders a paragraph that contains one or more Markdown images. Consecutive images are
 * laid out in a responsive 2-column grid (Perplexity-style image results); interleaved
 * prose is rendered as normal rich text. Non-image text between images is preserved in order.
 */
@Composable
private fun RenderParagraphWithImages(
    text: String,
    color: Color,
    isDark: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onOpenFile: ((String) -> Unit)?
) {
    val segments = remember(text) { splitImageSegments(text) }

    Column(modifier = Modifier.fillMaxWidth()) {
        var i = 0
        while (i < segments.size) {
            val seg = segments[i]
            when (seg) {
                is MdSegment.TextSeg -> {
                    val t = seg.text.trim()
                    if (t.isNotEmpty()) {
                        RichText(text = t, color = color, isDark = isDark, uriHandler = uriHandler, onOpenFile = onOpenFile)
                    }
                    i++
                }
                is MdSegment.ImageSeg -> {
                    val run = mutableListOf<MdSegment.ImageSeg>()
                    while (i < segments.size && segments[i] is MdSegment.ImageSeg) {
                        run.add(segments[i] as MdSegment.ImageSeg)
                        i++
                    }
                    ImageGrid(images = run, isDark = isDark, uriHandler = uriHandler)
                }
            }
        }
    }
}

private sealed class MdSegment {
    data class TextSeg(val text: String) : MdSegment()
    data class ImageSeg(val alt: String, val url: String) : MdSegment()
}

private fun splitImageSegments(text: String): List<MdSegment> {
    val out = mutableListOf<MdSegment>()
    var last = 0
    for (m in IMAGE_MD_REGEX.findAll(text)) {
        if (m.range.first > last) {
            out.add(MdSegment.TextSeg(text.substring(last, m.range.first)))
        }
        val alt = m.groupValues[1].trim()
        val url = m.groupValues[2].trim()
        if (url.startsWith("http")) out.add(MdSegment.ImageSeg(alt, url))
        last = m.range.last + 1
    }
    if (last < text.length) out.add(MdSegment.TextSeg(text.substring(last)))
    return out
}

@Composable
private fun ImageGrid(
    images: List<MdSegment.ImageSeg>,
    isDark: Boolean,
    uriHandler: androidx.compose.ui.platform.UriHandler
) {
    val placeholder = if (isDark) Color(0xFF141414) else Color(0xFFF0F0F3)
    if (images.size == 1) {
        val img = images.first()
        AsyncImage(
            model = img.url,
            contentDescription = img.alt.ifBlank { "image" },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(placeholder)
                .clickable { runCatching { uriHandler.openUri(img.url) } }
        )
    } else {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            images.chunked(2).forEach { rowImgs ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    rowImgs.forEach { img ->
                        AsyncImage(
                            model = img.url,
                            contentDescription = img.alt.ifBlank { "image" },
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(placeholder)
                                .clickable { runCatching { uriHandler.openUri(img.url) } }
                        )
                    }
                    if (rowImgs.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Code Block — monospace body with a lightweight, dependency-free regex syntax highlighter
// (keywords / strings / comments / numbers) plus a language header + copy button.
// ─────────────────────────────────────────────────────────────────────────────

private val CODE_KEYWORDS = setOf(
    "if", "else", "for", "foreach", "while", "do", "switch", "case", "default", "break", "continue",
    "return", "function", "fun", "def", "lambda", "class", "struct", "interface", "trait", "enum",
    "import", "from", "export", "package", "namespace", "using", "include", "require",
    "const", "let", "var", "val", "public", "private", "protected", "internal", "static", "final",
    "abstract", "override", "virtual", "readonly", "void", "null", "nil", "None", "undefined",
    "true", "false", "new", "this", "self", "super", "try", "catch", "except", "finally", "throw",
    "throws", "raise", "async", "await", "yield", "in", "is", "as", "typeof", "instanceof",
    "extends", "implements", "constructor", "init", "print", "println", "console"
)

private val CODE_TOKEN_REGEX = Regex(
    "(//[^\\n]*|#[^\\n]*)" +                                   // 1: line comment
    "|(/\\*[\\s\\S]*?\\*/)" +                                  // 2: block comment
    "|(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')" +      // 3: string literal
    "|\\b(\\d+\\.?\\d*)\\b" +                                  // 4: number
    "|\\b(" + CODE_KEYWORDS.joinToString("|") + ")\\b"         // 5: keyword
)

private fun highlightCode(code: String, isDark: Boolean, baseColor: Color): AnnotatedString {
    val kwColor = if (isDark) Color(0xFFC792EA) else Color(0xFF8250DF)
    val strColor = if (isDark) Color(0xFFC3E88D) else Color(0xFF116329)
    val commentColor = if (isDark) Color(0xFF7F848E) else Color(0xFF6E7781)
    val numColor = if (isDark) Color(0xFFF78C6C) else Color(0xFFB35900)

    return buildAnnotatedString {
        var last = 0
        for (m in CODE_TOKEN_REGEX.findAll(code)) {
            if (m.range.first > last) append(code.substring(last, m.range.first))
            val g = m.groupValues
            when {
                g[1].isNotEmpty() || g[2].isNotEmpty() ->
                    withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) { append(m.value) }
                g[3].isNotEmpty() -> withStyle(SpanStyle(color = strColor)) { append(m.value) }
                g[4].isNotEmpty() -> withStyle(SpanStyle(color = numColor)) { append(m.value) }
                g[5].isNotEmpty() -> withStyle(SpanStyle(color = kwColor, fontWeight = FontWeight.Medium)) { append(m.value) }
                else -> withStyle(SpanStyle(color = baseColor)) { append(m.value) }
            }
            last = m.range.last + 1
        }
        if (last < code.length) withStyle(SpanStyle(color = baseColor)) { append(code.substring(last)) }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun CodeBlock(code: String, language: String? = null, isDark: Boolean) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    val headerBg = if (isDark) Color(0xFF1C1C1F) else Color(0xFFEDEFF2)
    val codeBg = if (isDark) Color(0xFF141416) else Color(0xFFF6F8FA)
    val muted = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    val baseCodeColor = if (isDark) Color(0xFFE5E5E5) else Color(0xFF0D0D0D)
    // Cap very long dumps so the bubble doesn't look half-cut / messy
    val displayCode = remember(code) {
        if (code.length > 24_000) code.take(24_000) + "\n\n// … truncated for display …" else code
    }
    val highlighted = remember(displayCode, isDark, baseCodeColor) {
        highlightCode(displayCode, isDark, baseCodeColor)
    }

    // HTML/SVG preview toggle — also detect unlabeled fences that start with <svg / <html / <!DOCTYPE
    val lang = (language ?: "").lowercase().trim()
    val looksSvg = lang == "svg" || displayCode.trimStart().startsWith("<svg", ignoreCase = true)
    val looksHtml = lang == "html" || lang == "htm" ||
        displayCode.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
        displayCode.trimStart().startsWith("<html", ignoreCase = true) ||
        (displayCode.contains("<body", ignoreCase = true) && displayCode.contains("</", ignoreCase = true))
    val canPreview = looksSvg || looksHtml
    var showPreview by remember(code) { mutableStateOf(canPreview) }
    LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(1400)
            copied = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = codeBg
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Header row: language · preview-toggle · copy ──
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(headerBg)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        looksSvg -> "svg"
                        looksHtml -> "html"
                        lang.isNotEmpty() -> lang
                        else -> "code"
                    },
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFamily,
                    color = muted
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (canPreview) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showPreview = !showPreview }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (showPreview) Lucide.Code else Lucide.Eye,
                                contentDescription = if (showPreview) "Show code" else "Preview",
                                tint = if (showPreview) muted else Color(0xFF0A84FF),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = if (showPreview) "Code" else "Preview",
                                fontSize = 11.sp,
                                fontFamily = InterFamily,
                                color = if (showPreview) muted else Color(0xFF0A84FF)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            clipboard.setText(AnnotatedString(code))
                            copied = true
                        }.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Outlined.Done else AdminIcons.BootstrapCopy,
                            contentDescription = "Copy code",
                            tint = if (copied) Color(0xFF22C55E) else muted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            fontSize = 11.sp,
                            fontFamily = InterFamily,
                            color = if (copied) Color(0xFF22C55E) else muted
                        )
                    }
                }
            }
            // ── Body: code text or HTML/SVG live preview ──
            if (canPreview && showPreview) {
                val bgHex = if (isDark) "#0C0C0E" else "#FFFFFF"
                val webViewBg = if (isDark) android.graphics.Color.parseColor("#0C0C0E")
                    else android.graphics.Color.parseColor("#FFFFFF")
                val previewHtml = remember(displayCode, looksSvg, isDark) {
                    buildHtmlPreviewDocument(displayCode, looksSvg, bgHex)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                        .background(if (isDark) Color(0xFF0C0C0E) else Color.White)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.setSupportZoom(true)
                                settings.allowFileAccess = false
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                setBackgroundColor(webViewBg)
                                isVerticalScrollBarEnabled = true
                                isHorizontalScrollBarEnabled = true
                                setOnTouchListener { v, event ->
                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN ->
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                            v.parent?.requestDisallowInterceptTouchEvent(false)
                                    }
                                    false
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = WebViewClient()
                                // Non-null base URL is required for many pages / relative assets to render.
                                loadDataWithBaseURL(
                                    "https://appassets.androidplatform.net/",
                                    previewHtml,
                                    "text/html",
                                    "UTF-8",
                                    null
                                )
                            }
                        },
                        update = { web ->
                            // Reload only when content changes
                            web.loadDataWithBaseURL(
                                "https://appassets.androidplatform.net/",
                                previewHtml,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // Clean code: fixed max height + dual-axis scroll so long lines don't look cut off
                val hScroll = rememberScrollState()
                val vScroll = rememberScrollState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(vScroll)
                        .horizontalScroll(hScroll)
                ) {
                    SelectionContainer {
                        Text(
                            text = highlighted,
                            fontSize = ChatType.code,
                            fontFamily = JetBrainsMonoFamily,
                            lineHeight = ChatType.codeLine,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Full HTML document wrapper so partial HTML/SVG snippets always paint in WebView. */
private fun buildHtmlPreviewDocument(code: String, isSvg: Boolean, bgHex: String): String {
    val body = code.trim()
    if (isSvg || body.startsWith("<svg", ignoreCase = true)) {
        val svg = if (body.contains("<svg", ignoreCase = true)) {
            // Ensure SVG scales inside the viewport without zero size
            body.replace(Regex("<svg(\\s|>)", RegexOption.IGNORE_CASE)) { m ->
                if (body.contains("width=", ignoreCase = true) || body.contains("viewBox", ignoreCase = true)) {
                    m.value
                } else {
                    "<svg width=\"100%\" height=\"100%\" preserveAspectRatio=\"xMidYMid meet\"${m.groupValues[1]}"
                }
            }
        } else body
        return """
            <!DOCTYPE html><html><head>
            <meta charset="utf-8"/>
            <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=3"/>
            <style>
              html,body{margin:0;padding:0;width:100%;height:100%;background:$bgHex;}
              body{display:flex;align-items:center;justify-content:center;box-sizing:border-box;padding:12px;}
              svg{max-width:100%;max-height:100%;height:auto;}
            </style></head><body>$svg</body></html>
        """.trimIndent()
    }
    // If already a full document, inject viewport + base styles when missing
    if (body.contains("<html", ignoreCase = true) || body.contains("<!DOCTYPE", ignoreCase = true)) {
        val withMeta = if (body.contains("viewport", ignoreCase = true)) body
        else body.replace(
            Regex("<head([^>]*)>", RegexOption.IGNORE_CASE),
            "<head$1><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>"
        )
        return withMeta
    }
    // Snippet / fragment → wrap
    return """
        <!DOCTYPE html><html><head>
        <meta charset="utf-8"/>
        <meta name="viewport" content="width=device-width,initial-scale=1"/>
        <style>
          html,body{margin:0;padding:12px;background:$bgHex;color:${if (bgHex == "#0C0C0E") "#ECECEC" else "#0D0D0D"};
            font-family:-apple-system,system-ui,sans-serif;font-size:15px;line-height:1.45;}
          img,video,canvas,iframe{max-width:100%;height:auto;}
        </style></head><body>$body</body></html>
    """.trimIndent()
}

// ─────────────────────────────────────────────────────────────────────────────
// Table Renderer — column-consistent, alignment-aware, inline-formatted cells.
// ─────────────────────────────────────────────────────────────────────────────

private data class GfmTable(val rows: List<List<String>>, val alignments: List<TextAlign>)

/**
 * Splits one table row's raw text on unescaped, non-code-span '|' delimiters.
 * Leading/trailing bounding pipes ("| A | B |") produce empty first/last cells that are trimmed.
 * Genuinely empty INTERIOR cells ("| A | | C |") are kept so columns don't drift.
 */
private fun splitTableRow(raw: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var inCode = false
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        when {
            c == '\\' && i + 1 < raw.length && raw[i + 1] == '|' -> { current.append('|'); i += 2 }
            c == '`' -> { inCode = !inCode; current.append(c); i++ }
            c == '|' && !inCode -> { cells.add(current.toString().trim()); current.clear(); i++ }
            else -> { current.append(c); i++ }
        }
    }
    cells.add(current.toString().trim())

    // Remove leading empty cell from bounding pipe
    if (cells.size > 1 && cells.first().isEmpty()) cells.removeAt(0)
    // Remove trailing empty cell from bounding pipe — BUT only if there's
    // ALSO a leading pipe (both bounding). Otherwise it's a genuine trailing
    // pipe with no content after.
    if (cells.size > 1 && cells.last().isEmpty()) {
        // Only remove trailing empty if the RAW line started with |
        if (raw.trimStart().startsWith("|")) {
            cells.removeAt(cells.size - 1)
        }
    }
    return cells
}

private val TABLE_SEPARATOR_CELL = Regex("^:?-+:?$")

private fun parseGfmTable(node: ASTNode, src: String): GfmTable {
    val rawRows = mutableListOf<List<String>>()
    for (child in node.children) {
        if (child.type == GFMElementTypes.ROW || child.type == GFMElementTypes.HEADER) {
            val rowText = child.getTextInNode(src).toString().trim()
            if (rowText.isNotEmpty()) rawRows.add(splitTableRow(rowText))
        }
    }
    if (rawRows.isEmpty()) return GfmTable(emptyList(), emptyList())

    // Find separator row index
    val sepIdx = rawRows.indexOfFirst { row ->
        row.isNotEmpty() && row.all { TABLE_SEPARATOR_CELL.matches(it) }
    }

    // Determine max column count across ALL rows (including separator)
    val maxColCount = rawRows.maxOfOrNull { it.size } ?: 0
    if (maxColCount == 0) return GfmTable(emptyList(), emptyList())

    // Pad all rows to maxColCount
    fun padded(row: List<String>): List<String> = when {
        row.size == maxColCount -> row
        row.size > maxColCount -> row.take(maxColCount)
        else -> row + List(maxColCount - row.size) { "" }
    }

    val alignments = if (sepIdx >= 0) {
        padded(rawRows[sepIdx]).map { spec ->
            when {
                spec.startsWith(":") && spec.endsWith(":") -> TextAlign.Center
                spec.endsWith(":") -> TextAlign.End
                else -> TextAlign.Start
            }
        }
    } else List(maxColCount) { TextAlign.Start }

    val dataRows = rawRows.filterIndexed { i, _ -> i != sepIdx }.map(::padded)
    return GfmTable(dataRows, alignments)
}

@Composable
private fun TableBlock(node: ASTNode, src: String, color: Color, isDark: Boolean) {
    val table = remember(node, src, isDark) { parseGfmTable(node, src) }
    if (table.rows.isEmpty()) return

    // Premium monochrome table: no outer border, no vertical dividers, only a subtle
    // hairline between rows (very low alpha so it never reads as a hard line). Header
    // row gets a soft tint background so it reads as a header without any divider lines.
    val headerBg = if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.04f) else Color(0xFF000000).copy(alpha = 0.035f)
    val cellBg = Color.Transparent
    val rowSeparator = if (isDark) Color(0xFFFFFFFF).copy(alpha = 0.06f) else Color(0xFF000000).copy(alpha = 0.06f)
    val headerColor = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        table.rows.forEachIndexed { rowIdx, row ->
            val isHeader = rowIdx == 0
            Row(modifier = Modifier.fillMaxWidth().background(if (isHeader) headerBg else cellBg)) {
                row.forEachIndexed { colIdx, cell ->
                    val align = table.alignments.getOrElse(colIdx) { TextAlign.Start }
                    val cellAnnotated = remember(cell, isDark, color) {
                        buildRichAnnotated(cell, color, isDark)
                    }
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.Transparent
                    ) {
                        Text(
                            text = cellAnnotated,
                            fontSize = ChatType.code,
                            fontWeight = if (isHeader) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isHeader) headerColor else Color.Unspecified,
                            textAlign = align,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            // Subtle row separator (no divider line on the very last row, no vertical lines).
            if (rowIdx < table.rows.size - 1) {
                Box(
                    Modifier.fillMaxWidth().height(1.dp).background(rowSeparator)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LaTeX Block
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LatexBlockView(formula: String, color: Color) {
    val density = LocalDensity.current
    val textSizePx = with(density) { 17.sp.toPx() }
    val rendered = remember(formula, color) {
        runCatching { renderLatexToBitmap(formula, textSizePx, color) }.getOrNull()
    }
    if (rendered != null) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                bitmap = rendered.bitmap,
                contentDescription = formula,
                modifier = Modifier.width(with(density) { rendered.widthPx.toDp() })
            )
        }
    } else {
        Text(
            text = formula,
            fontFamily = JetBrainsMonoFamily,
            fontSize = ChatType.code,
            color = color,
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

// LaTeX delimiters models actually emit:
//   $$...$$  \[...\]  — display math
//   \(...\)  $...$    — inline math
// Single-dollar is only kept when the body looks like real math (so $5 / $1,000 stay money).
// Code is masked first so delimiters inside code samples are never extracted.
private val LATEX_DISPLAY_PATTERNS = listOf(
    Regex("\\$\\$([\\s\\S]+?)\\$\\$"),        // $$ ... $$
    Regex("\\\\\\[([\\s\\S]+?)\\\\\\]")        // \[ ... \]
)
private val LATEX_INLINE_PAREN = Regex("\\\\\\(([\\s\\S]+?)\\\\\\)") // \( ... \)
// Single $...$ — non-greedy, same line, not part of $$ (Kotlin: \$ = literal dollar)
private val LATEX_INLINE_DOLLAR = Regex("(?<!\\\\)\\$(?!\\$)([^\\$\\n]+?)(?<!\\\\)\\$(?!\\$)")
private val CODE_FENCE_MASK = Regex("```[\\s\\S]*?```")
private val INLINE_CODE_MASK = Regex("`[^`\\n]+`")

/** True if $...$ body is almost certainly math, not currency/plain text. */
private fun looksLikeLatexBody(body: String): Boolean {
    val s = body.trim()
    if (s.isEmpty() || s.length > 400) return false
    // Currency / plain numbers: $5, $12.99, $1,000
    if (s.matches(Regex("""^[\d,.\s]+${'$'}"""))) return false
    // Pure words without math operators
    if (s.matches(Regex("""^[A-Za-z][A-Za-z\s]{0,24}${'$'}"""))) return false
    // Strong LaTeX / math signals
    if (s.contains('\\')) return true
    if (s.any { it in "^_{}" }) return true
    if (Regex("""[=<>≤≥±∞∑∫√∂∇πθαβγΔλμσω]|\\times|\\cdot|\\frac""").containsMatchIn(s)) return true
    if (Regex("""[A-Za-z]\s*[=+\-*/]\s*[A-Za-z0-9]""").containsMatchIn(s)) return true
    if (Regex("""\d+\s*/\s*\d+""").containsMatchIn(s)) return true
    return false
}

/**
 * Extracts LaTeX blocks from text, supporting $$...$$, \[...\], \(...\), and safe $...$.
 * Fenced and inline code are masked out first so LaTeX-looking delimiters inside
 * code (regex, arrays, shell) are preserved verbatim instead of being swallowed.
 */
private fun extractLatexBlocksSafe(text: String): Pair<String, List<String>> {
    val stash = mutableListOf<String>()
    fun mask(s: String, re: Regex): String = re.replace(s) { m ->
        stash.add(m.value); "@@CODE${stash.size - 1}@@"
    }
    var work = mask(text, CODE_FENCE_MASK)
    work = mask(work, INLINE_CODE_MASK)

    val blocks = mutableListOf<String>()
    fun pull(re: Regex, requireMathLook: Boolean = false) {
        work = re.replace(work) { m ->
            val formula = m.groupValues[1].trim()
            if (formula.isEmpty()) return@replace m.value
            if (requireMathLook && !looksLikeLatexBody(formula)) return@replace m.value
            blocks.add(formula)
            "\n"
        }
    }
    // Display first (so $$ isn't eaten by single-$)
    for (re in LATEX_DISPLAY_PATTERNS) pull(re)
    pull(LATEX_INLINE_PAREN)
    pull(LATEX_INLINE_DOLLAR, requireMathLook = true)

    // Restore the masked code segments.
    val restored = Regex("@@CODE(\\d+)@@").replace(work) { m ->
        stash.getOrNull(m.groupValues[1].toInt()) ?: ""
    }
    return restored.trim() to blocks
}

private fun parseFence(raw0: String): Pair<String?, String> {
    val raw = raw0.replace("\r\n", "\n").trim('\n')
    if (raw.isEmpty()) return null to ""
    val lines = raw.split("\n")
    val openFence = Regex("^\\s*(`{3,}|~{3,})")
    val closeFence = Regex("^\\s*(`{3,}|~{3,})\\s*$")
    if (lines.isNotEmpty() && openFence.containsMatchIn(lines.first())) {
        val lang = lines.first().trim().trimStart('`', '~').trim().substringBefore(' ').ifBlank { null }
        val endIdx = if (lines.size > 1 && closeFence.matches(lines.last())) lines.size - 1 else lines.size
        val bodyLines = if (lines.size > 1) lines.subList(1, endIdx) else emptyList()
        return lang to bodyLines.joinToString("\n")
    }
    val body = lines.joinToString("\n") { it.removePrefix("    ").removePrefix("\t") }
    return null to body
}