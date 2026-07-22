package com.ahamai.app.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import com.ahamai.app.ui.icons.AdminIcons
import com.ahamai.app.ui.icons.AdminIcons.BootstrapFileCode
import com.ahamai.app.ui.icons.AdminIcons.FluentDocument
import com.ahamai.app.ui.icons.AdminIcons.PdfIcon
import com.ahamai.app.ui.icons.AdminIcons.ZipIcon
import com.ahamai.app.ui.icons.AdminIcons.ExcelIcon
import com.ahamai.app.ui.icons.AdminIcons.DocIcon
import com.ahamai.app.ui.icons.AdminIcons.CodeFileIcon
import com.ahamai.app.ui.icons.AdminIcons.TextFileIcon
import com.ahamai.app.ui.icons.AdminIcons.MdIcon
import com.ahamai.app.ui.icons.AdminIcons.SaveIcon
import com.ahamai.app.ui.icons.AdminIcons.CancelSquareIcon
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import com.ahamai.app.ui.icons.Lucide
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Rich, in-message previews for files an agent creates/mentions: real PDF page thumbnails
 * (Android [PdfRenderer]), CSV tables, image previews, and typed cards for Office files —
 * each tappable to open a full in-app viewer dialog (with an "open externally" fallback).
 */
object FilePreviewKit {

    val VIDEO_EXTS = setOf("mp4", "mov", "webm", "mkv", "m4v", "3gp", "avi")

    val PREVIEWABLE = setOf(
        "pdf", "png", "jpg", "jpeg", "webp", "gif", "bmp",
        "csv", "tsv", "xlsx", "xls", "pptx", "ppt", "docx", "doc"
    ) + VIDEO_EXTS

    fun isImage(ext: String) = ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun isVideo(ext: String) = ext in VIDEO_EXTS

    fun typeLabel(ext: String): String = when (ext) {
        "pdf" -> "PDF"
        "csv", "tsv" -> "CSV"
        "xlsx", "xls" -> "Excel"
        "pptx", "ppt" -> "PowerPoint"
        "docx", "doc" -> "Word"
        "md", "markdown" -> "Markdown"
        "txt" -> "Text"
        "html", "htm" -> "HTML"
        "apk" -> "Android app"
        "zip" -> "ZIP archive"
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> "Image"
        in VIDEO_EXTS -> "Video"
        "json" -> "JSON"
        else -> ext.uppercase().ifBlank { "File" }
    }

    /** Broad category shown in the clean card subtitle: "Code · HTML · 3.14 kB". */
    fun categoryLabel(ext: String): String = when {
        ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg") -> "Image"
        ext in VIDEO_EXTS -> "Video"
        ext == "pdf" -> "Document"
        ext in setOf("docx", "doc", "xlsx", "xls", "pptx", "ppt", "csv", "tsv") -> "Document"
        ext in setOf("md", "markdown", "txt") -> "Text"
        ext in setOf("zip", "rar", "7z", "tar", "gz") -> "Archive"
        ext == "apk" -> "App"
        ext == "json" -> "Data"
        ext.isBlank() -> "File"
        else -> "Code"
    }

    fun iconFor(ext: String): Pair<ImageVector, Color> = when (ext) {
        "pdf" -> PdfIcon to Color(0xFFE53935)
        "csv", "tsv", "xlsx", "xls" -> ExcelIcon to Color(0xFF2E7D32)
        "pptx", "ppt" -> Icons.Filled.Slideshow to Color(0xFFD84315)
        "docx", "doc" -> DocIcon to Color(0xFF1565C0)
        "md", "markdown" -> MdIcon to Color(0xFF2563EB)
        "apk" -> Icons.Filled.Android to Color(0xFF3DDC84)
        "zip", "rar", "7z", "tar", "gz" -> ZipIcon to Color(0xFFCA8A04)
        in VIDEO_EXTS -> Icons.Filled.Movie to Color(0xFF8E24AA)
        "kt", "kts", "java", "py", "js", "ts", "html", "css", "xml", "json", "yml", "yaml", "sh", "gradle", "sql", "go", "rs", "c", "cpp", "h", "swift", "php", "rb" -> CodeFileIcon to Color(0xFF6B7280)
        "txt" -> TextFileIcon to Color(0xFF6B7280)
        else -> Icons.Filled.InsertDriveFile to Color(0xFF607D8B)
    }

    /** Brand color for the Manus-style rounded icon tile behind the glyph. */
    fun brandColor(ext: String): Color = when (ext.lowercase()) {
        "pdf" -> Color(0xFFE53935)
        "csv", "tsv" -> Color(0xFF16A34A)
        "xlsx", "xls" -> Color(0xFF217346)
        "pptx", "ppt" -> Color(0xFFC43E1C)
        "docx", "doc" -> Color(0xFF2B579A)
        "md", "markdown", "txt" -> Color(0xFF2563EB)
        "apk" -> Color(0xFF3DDC84)
        "zip", "rar", "7z", "tar", "gz" -> Color(0xFFCA8A04)
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> Color(0xFF7C3AED)
        in VIDEO_EXTS -> Color(0xFF8E24AA)
        "html", "htm" -> Color(0xFFE34F26)
        "json", "xml", "yml", "yaml" -> Color(0xFF64748B)
        else -> Color(0xFF64748B)
    }

    /** Prefer a short human title for markdown (first # heading), else null. */
    fun displayTitle(file: File, ext: String): String? {
        if (ext !in setOf("md", "markdown", "txt")) return null
        return try {
            file.bufferedReader().use { br ->
                repeat(40) {
                    val line = br.readLine() ?: return null
                    val t = line.trim()
                    if (t.startsWith("#")) {
                        val h = t.trimStart('#').trim()
                        if (h.isNotBlank()) return h.take(80)
                    }
                }
            }
            null
        } catch (_: Exception) { null }
    }

    /** Extensions that count as user-facing "deliverables" (show Manus file card). */
    val DELIVERABLE_EXTS = setOf(
        "pdf", "docx", "doc", "xlsx", "xls", "pptx", "ppt", "csv", "tsv",
        "md", "markdown", "txt", "html", "htm", "apk", "zip", "png", "jpg", "jpeg",
        "webp", "gif", "mp4", "mov", "webm", "json"
    )

    fun isDeliverable(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in DELIVERABLE_EXTS
    }

    /** Grab a representative frame from a video for an inline thumbnail. Returns null on failure. */
    fun videoThumbnail(file: File): Bitmap? {
        val mmr = android.media.MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            // Prefer a frame ~1s in (skips black intro frames); fall back to the first frame.
            mmr.getFrameAtTime(1_000_000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.getFrameAtTime(0)
                ?: mmr.frameAtTime
        } catch (e: Exception) {
            null
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    fun fmtSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    /** Render one PDF page to a bitmap at [targetWidthPx]. Returns null on failure. */
    fun renderPdfPage(file: File, index: Int, targetWidthPx: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (index < 0 || index >= renderer.pageCount) return null
            val page = renderer.openPage(index)
            val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
            val w = targetWidthPx.coerceIn(1, 2000)
            val h = (w * ratio).toInt().coerceIn(1, 4000)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bmp
        } catch (e: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    fun pdfPageCount(file: File): Int {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            renderer.pageCount
        } catch (e: Exception) { 0 } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    /** Minimal CSV/TSV parser handling quoted fields. */
    fun parseDelimited(text: String, delim: Char, maxRows: Int): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var field = StringBuilder()
        var row = ArrayList<String>()
        var inQuotes = false
        var i = 0
        while (i < text.length && rows.size < maxRows) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == delim -> { row.add(field.toString()); field = StringBuilder() }
                c == '\n' -> { row.add(field.toString()); rows.add(row); row = ArrayList(); field = StringBuilder() }
                c == '\r' -> { /* skip */ }
                else -> field.append(c)
            }
            i++
        }
        if ((field.isNotEmpty() || row.isNotEmpty()) && rows.size < maxRows) {
            row.add(field.toString()); rows.add(row)
        }
        return rows
    }

    private fun unescapeXml(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'")

    // Namespace-tolerant (matches <c>, <x:c>, …) OOXML tag regexes.
    private val DOT = RegexOption.DOT_MATCHES_ALL
    private val T_RE = Regex("<(?:\\w+:)?t\\b[^>]*>(.*?)</(?:\\w+:)?t>", DOT)

    /** Best-effort on-device XLSX reader → a 2D grid (first worksheet), for a direct preview. */
    fun readXlsxGrid(file: File, maxRows: Int = 60): List<List<String>> = try {
        val zip = java.util.zip.ZipFile(file)
        val shared = ArrayList<String>()
        zip.getEntry("xl/sharedStrings.xml")?.let { e ->
            val xml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            Regex("<(?:\\w+:)?si\\b[^>]*>(.*?)</(?:\\w+:)?si>", DOT).findAll(xml).forEach { m ->
                shared.add(T_RE.findAll(m.groupValues[1]).joinToString("") { unescapeXml(it.groupValues[1]) })
            }
        }
        val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml")
            ?: zip.entries().asSequence().firstOrNull { it.name.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            ?: zip.entries().asSequence().firstOrNull { it.name.startsWith("xl/worksheets/sheet") }
        val grid = ArrayList<List<String>>()
        sheetEntry?.let { e ->
            val xml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            val cellRe = Regex("<(?:\\w+:)?c\\b([^>]*)(?:/>|>(.*?)</(?:\\w+:)?c>)", DOT)
            val vRe = Regex("<(?:\\w+:)?v\\b[^>]*>(.*?)</(?:\\w+:)?v>", DOT)
            Regex("<(?:\\w+:)?row\\b[^>]*>(.*?)</(?:\\w+:)?row>", DOT).findAll(xml).take(maxRows).forEach { rowM ->
                val cells = ArrayList<String>()
                cellRe.findAll(rowM.groupValues[1]).forEach { cM ->
                    val attrs = cM.groupValues[1]
                    val inner = cM.groupValues[2]
                    val raw = vRe.find(inner)?.groupValues?.get(1) ?: ""
                    cells.add(
                        when {
                            attrs.contains("t=\"s\"") -> shared.getOrNull(raw.trim().toIntOrNull() ?: -1) ?: ""
                            attrs.contains("t=\"inlineStr\"") || attrs.contains("t=\"str\"") ->
                                T_RE.find(inner)?.groupValues?.get(1)?.let { unescapeXml(it) } ?: unescapeXml(raw)
                            else -> unescapeXml(raw)
                        }
                    )
                }
                grid.add(cells)
            }
        }
        zip.close()
        grid.filter { row -> row.any { it.isNotBlank() } }   // drop fully-empty rows
    } catch (_: Exception) { emptyList() }

    /** Best-effort on-device DOCX reader → plain text (paragraphs). */
    fun readDocxText(file: File, maxChars: Int = 12000): String = try {
        val zip = java.util.zip.ZipFile(file)
        val xml = zip.getEntry("word/document.xml")?.let { zip.getInputStream(it).bufferedReader().use { r -> r.readText() } } ?: ""
        zip.close()
        val sb = StringBuilder()
        Regex("<(?:\\w+:)?p\\b[^>]*>(.*?)</(?:\\w+:)?p>", DOT).findAll(xml).forEach { p ->
            val line = T_RE.findAll(p.groupValues[1]).joinToString("") { unescapeXml(it.groupValues[1]) }
            sb.append(line).append('\n')
        }
        val out = sb.toString().trim()
        // Fallback: if paragraph parsing yielded nothing, grab every text run.
        (if (out.isBlank()) T_RE.findAll(xml).joinToString(" ") { unescapeXml(it.groupValues[1]) }.trim() else out).take(maxChars)
    } catch (_: Exception) { "" }

    /** Best-effort on-device PPTX reader → slide text. */
    fun readPptxText(file: File, maxChars: Int = 12000): String = try {
        val zip = java.util.zip.ZipFile(file)
        val slides = zip.entries().asSequence()
            .filter { it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
            .sortedBy { it.name.replace(Regex("\\D"), "").toIntOrNull() ?: 0 }.toList()
        val sb = StringBuilder()
        slides.forEachIndexed { i, e ->
            val xml = zip.getInputStream(e).bufferedReader().use { it.readText() }
            val text = T_RE.findAll(xml).joinToString("\n") { unescapeXml(it.groupValues[1]) }.trim()
            if (text.isNotBlank()) sb.append("Slide ${i + 1}\n").append(text).append("\n\n")
        }
        zip.close()
        sb.toString().trim().take(maxChars)
    } catch (_: Exception) { "" }

    fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "pdf" -> "application/pdf"
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"; "bmp" -> "image/bmp"
        "csv" -> "text/csv"; "tsv" -> "text/tab-separated-values"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "xls" -> "application/vnd.ms-excel"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "ppt" -> "application/vnd.ms-powerpoint"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "doc" -> "application/msword"
        "apk" -> "application/vnd.android.package-archive"
        "mp4", "m4v" -> "video/mp4"; "mov" -> "video/quicktime"; "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"; "3gp" -> "video/3gpp"; "avi" -> "video/x-msvideo"
        "txt", "md", "log" -> "text/plain"; "json" -> "application/json"; "html", "htm" -> "text/html"
        else -> "*/*"
    }

    /** Save the ORIGINAL file (original name + extension) into Downloads/AhamAI. */
    fun saveToDownloads(ctx: android.content.Context, file: File): String = try {
        com.ahamai.app.data.DeviceStorage.saveToAhamAIFolder(ctx, file.readBytes(), file.name, mimeFor(file.extension))
    } catch (e: Exception) { "ERROR: ${e.message}" }

    fun openExternally(ctx: android.content.Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeFor(file.extension))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(Intent.createChooser(intent, "Open ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }
}

/**
 * Inline preview for a project file — Manus-style:
 *  1) Optional visual preview (image / PDF page / CSV table / video poster)
 *  2) Full-width rounded **deliverable card** with colored type icon + name + size
 * Tap opens the full in-app viewer (save / open externally).
 */
@Composable
fun FilePreviewCard(projectDir: String, relPath: String, isDark: Boolean, compact: Boolean = false, showActions: Boolean = true) {
    val file = remember(projectDir, relPath) { File(projectDir, relPath.trim().removePrefix("./").removePrefix("/")) }
    if (!file.exists() || !file.isFile) return
    val ext = remember(relPath) { relPath.substringAfterLast('.', "").lowercase() }
    val officePreviewPdf = remember(file.path) {
        if (ext in setOf("xlsx", "xls", "pptx", "ppt", "docx", "doc"))
            File("${file.path}.preview.pdf").takeIf { it.exists() && it.length() > 0 }
        else null
    }
    var showViewer by remember { mutableStateOf(false) }
    val imgMaxH = if (compact) 200.dp else 260.dp
    val corner = if (compact) 14.dp else 16.dp

    val isImg = FilePreviewKit.isImage(ext)
    val isVid = FilePreviewKit.isVideo(ext)
    val isPdf = ext == "pdf"
    val isCsv = ext == "csv" || ext == "tsv"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (compact) 4.dp else 8.dp)
    ) {
        when {
            // ── DIRECT preview, NO label: image / video / pdf ──
            isImg -> AsyncImage(
                model = file,
                contentDescription = relPath,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = imgMaxH)
                    .clip(RoundedCornerShape(corner))
                    .background(if (isDark) Color(0xFF141414) else Color(0xFFF4F4F4))
                    .clickable { showViewer = true }
            )
            isVid -> {
                val thumb by produceState<Bitmap?>(initialValue = null, file.path) {
                    value = withContext(Dispatchers.IO) { FilePreviewKit.videoThumbnail(file) }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = imgMaxH)
                        .clip(RoundedCornerShape(corner))
                        .background(if (isDark) Color(0xFF141414) else Color(0xFFF4F4F4))
                        .clickable { showViewer = true },
                    contentAlignment = Alignment.Center
                ) {
                    thumb?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = relPath,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = imgMaxH)
                        )
                    } ?: Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(Color(0x66000000)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Play", modifier = Modifier.size(30.dp), tint = Color.White)
                    }
                }
            }
            isPdf -> {
                val thumb by produceState<Bitmap?>(initialValue = null, file.path) {
                    value = withContext(Dispatchers.IO) { FilePreviewKit.renderPdfPage(file, 0, 720) }
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(corner)).clickable { showViewer = true }) {
                    thumb?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = relPath,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = imgMaxH + 40.dp).background(Color.White)
                        )
                    } ?: Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }

            // ── DIRECT preview (like PDF, no card): office with a rendered page ──
            officePreviewPdf != null -> {
                val thumb by produceState<Bitmap?>(initialValue = null, officePreviewPdf.path) {
                    value = withContext(Dispatchers.IO) { FilePreviewKit.renderPdfPage(officePreviewPdf, 0, 720) }
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(corner)).clickable { showViewer = true }) {
                    thumb?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = relPath,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().heightIn(max = imgMaxH + 40.dp).background(Color.White)
                        )
                    } ?: Box(Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            }
            // CSV / TSV → table rendered directly
            isCsv -> {
                val rows = remember(file.path) {
                    runCatching { FilePreviewKit.parseDelimited(file.readText(), if (ext == "tsv") '\t' else ',', 40) }.getOrDefault(emptyList())
                }
                if (rows.isEmpty()) CleanFileCard(file, ext, isDark, onClick = { showViewer = true }, showActions = showActions)
                else DirectPreviewBox(corner, isDark, onClick = { showViewer = true }) {
                    Box(Modifier.heightIn(max = imgMaxH)) { MiniTable(rows, isDark) }
                }
            }
            // XLSX / XLS → parsed sheet as a table (on-device, no cloud needed)
            ext == "xlsx" || ext == "xls" -> {
                val rows by produceState(initialValue = emptyList<List<String>>(), file.path) {
                    value = withContext(Dispatchers.IO) { FilePreviewKit.readXlsxGrid(file) }
                }
                if (rows.isEmpty()) {
                    // Fallback: try extracting text like a document
                    val text by produceState(initialValue = "", file.path) {
                        value = withContext(Dispatchers.IO) { FilePreviewKit.readDocxText(file) }
                    }
                    if (text.isBlank()) CleanFileCard(file, ext, isDark, onClick = { showViewer = true }, showActions = showActions)
                    else DirectPreviewBox(corner, isDark, onClick = { showViewer = true }) {
                        Text(
                            text = text,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF1F2937),
                            modifier = Modifier.fillMaxWidth().heightIn(max = imgMaxH).verticalScroll(rememberScrollState()).padding(16.dp)
                        )
                    }
                }
                else DirectPreviewBox(corner, isDark, onClick = { showViewer = true }) {
                    Box(Modifier.heightIn(max = imgMaxH)) { MiniTable(rows, isDark) }
                }
            }
            // DOCX / DOC / PPTX / PPT → extracted text rendered like a document page
            ext == "docx" || ext == "doc" || ext == "pptx" || ext == "ppt" -> {
                val text by produceState(initialValue = "", file.path) {
                    value = withContext(Dispatchers.IO) {
                        when (ext) {
                            "docx", "doc" -> FilePreviewKit.readDocxText(file)
                            else -> FilePreviewKit.readPptxText(file)
                        }
                    }
                }
                if (text.isBlank()) CleanFileCard(file, ext, isDark, onClick = { showViewer = true }, showActions = showActions)
                else DirectPreviewBox(corner, isDark, onClick = { showViewer = true }) {
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = if (isDark) Color(0xFFD1D5DB) else Color(0xFF1F2937),
                        modifier = Modifier.fillMaxWidth().heightIn(max = imgMaxH).verticalScroll(rememberScrollState()).padding(16.dp)
                    )
                }
            }

            // ── Everything else: clean monochrome file card (Claude-style) ──
            else -> CleanFileCard(file = file, ext = ext, isDark = isDark, onClick = { showViewer = true }, showActions = showActions)
        }
    }

    if (showViewer) {
        if (officePreviewPdf != null) FileViewerDialog(file, officePreviewPdf, "pdf", isDark, showActions) { showViewer = false }
        else FileViewerDialog(file, file, ext, isDark, showActions) { showViewer = false }
    }
}

/** A clean rounded container that renders file content directly (like the PDF preview), no label. */
@Composable
private fun DirectPreviewBox(
    corner: androidx.compose.ui.unit.Dp,
    isDark: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(if (isDark) Color(0xFF141414) else Color(0xFFFFFFFF))
            .clickable(onClick = onClick)
    ) { content() }
}

/**
 * Clean, monochrome file card (Claude-style): a folded-corner document thumbnail with a
 * type glyph, the filename, a "Category · EXT · size" line, and a download action.
 * Used for non-previewable files (code, svg, docx-without-preview, zip, apk, json…).
 */
@Composable
fun CleanFileCard(
    file: File,
    ext: String,
    isDark: Boolean,
    titleOverride: String? = null,
    showActions: Boolean = true,
    onClick: () -> Unit = {}
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = if (isDark) Color(0xFFECECEC) else Color(0xFF141414)
    val secondary = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    // iOS-style white rounded card with dark-mode support
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val (glyph, _) = FilePreviewKit.iconFor(ext)
    val isZip = ext in setOf("zip", "rar", "7z", "tar", "gz")
    val title = titleOverride ?: FilePreviewKit.displayTitle(file, ext)
    val name = file.name
    val subtitle = buildString {
        append(FilePreviewKit.categoryLabel(ext))
        append(" · ")
        append(ext.uppercase().ifBlank { "FILE" })
        if (file.exists()) { append(" · "); append(FilePreviewKit.fmtSize(file.length())) }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(if (isZip) 10.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (isZip) 8.dp else 12.dp)
        ) {
            // Cute small type icon for zip; no icon for others (clean watermark style)
            if (isZip) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFCA8A04).copy(alpha = if (isDark) 0.2f else 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = glyph,
                        contentDescription = null,
                        tint = Color(0xFFCA8A04),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (title != null && title.isNotBlank() && !title.equals(name, true)) title else name,
                    fontSize = 13.sp, fontWeight = FontWeight.Normal, color = primary.copy(alpha = 0.8f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle, fontSize = 11.sp, color = secondary.copy(alpha = 0.7f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            // Download action — hidden for user-attached files (only AI-generated files show it).
            if (showActions) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            scope.launch {
                                val msg = withContext(Dispatchers.IO) {
                                    runCatching { FilePreviewKit.saveToDownloads(ctx, file) }.getOrElse { "ERROR" }
                                }
                                android.widget.Toast.makeText(
                                    ctx,
                                    if (msg.startsWith("OK")) "Saved to Downloads/AhamAI" else "Couldn't save file",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = SaveIcon,
                        contentDescription = "Download",
                        tint = secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** Folded-corner document thumbnail with a centred monochrome type glyph. */
@Composable
private fun DocThumb(glyph: ImageVector, isDark: Boolean) {
    val docBg = if (isDark) Color(0xFF2A2A2E) else Color(0xFFFFFFFF)
    val border = if (isDark) Color(0xFFFFFFFF) else Color(0xFF999999)
    val ink = if (isDark) Color(0xFFB8B8C0) else Color(0xFF8A8A92)
    Box(modifier = Modifier.size(width = 42.dp, height = 50.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val fold = w * 0.34f
            val r = 4.dp.toPx()
            val body = Path().apply {
                moveTo(r, 0f)
                lineTo(w - fold, 0f)
                lineTo(w, fold)
                lineTo(w, h - r)
                quadraticBezierTo(w, h, w - r, h)
                lineTo(r, h)
                quadraticBezierTo(0f, h, 0f, h - r)
                lineTo(0f, r)
                quadraticBezierTo(0f, 0f, r, 0f)
                close()
            }
            drawPath(body, color = docBg)
            drawPath(body, color = border, style = Stroke(width = 1.5.dp.toPx()))
            // Folded corner triangle
            val foldPath = Path().apply {
                moveTo(w - fold, 0f)
                lineTo(w - fold, fold)
                lineTo(w, fold)
                close()
            }
            drawPath(foldPath, color = if (isDark) Color(0xFF1C1C1E) else Color(0xFFEDEDF0))
            drawPath(foldPath, color = border, style = Stroke(width = 1.2.dp.toPx()))
        }
        Icon(imageVector = glyph, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp).offset(y = 4.dp))
    }
}

/**
 * Back-compat: the old colored "Manus" deliverable pill now renders as the clean
 * monochrome card, so every existing caller gets the new look.
 */
@Composable
fun ManusDeliverableCard(
    file: File,
    ext: String,
    isDark: Boolean,
    titleOverride: String? = null,
    onClick: () -> Unit = {}
) = CleanFileCard(file = file, ext = ext, isDark = isDark, titleOverride = titleOverride, onClick = onClick)

@Composable
private fun MiniTable(rows: List<List<String>>, isDark: Boolean) {
    if (rows.isEmpty()) return
    val headerBg = if (isDark) Color(0xFF1E2030) else Color(0xFFE8EBF2)
    val lineCol = if (isDark) Color(0xFF2A2C36) else Color(0xFFE3E5EA)
    val txt = if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)
    val cols = (rows.maxOfOrNull { it.size } ?: 1).coerceAtMost(6)
    Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp)) {
        rows.forEachIndexed { ri, row ->
            Row {
                for (c in 0 until cols) {
                    Text(
                        text = row.getOrElse(c) { "" }.take(24),
                        fontSize = 11.sp,
                        fontWeight = if (ri == 0) FontWeight.Bold else FontWeight.Normal,
                        color = txt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(96.dp)
                            .background(if (ri == 0) headerBg else Color.Transparent)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            HorizontalDivider(color = lineCol, thickness = 0.5.dp)
        }
    }
}

/**
 * Back-compat alias — same Manus deliverable card used everywhere.
 */
@Composable
fun IosFileResultCard(
    file: File,
    ext: String,
    isDark: Boolean,
    onClick: () -> Unit = {}
) {
    ManusDeliverableCard(file = file, ext = ext, isDark = isDark, onClick = onClick)
}

/** Full in-app viewer. [displayFile] drives the title/icon/save/open (original extension);
 *  [renderFile] + [renderExt] drive what's actually rendered (e.g. an office file's preview PDF). */
@Composable
fun FileViewerDialog(displayFile: File, renderFile: File, renderExt: String, isDark: Boolean, showSave: Boolean = true, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ext = renderExt
    val displayExt = remember(displayFile.path) { displayFile.extension.lowercase() }
    val bg = if (isDark) Color(0xFF0C0C0E) else Color.White
    val primary = if (isDark) Color(0xFFE5E7EB) else Color(0xFF0D0D0D)
    val muted = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize().padding(10.dp), shape = RoundedCornerShape(16.dp), color = bg) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clean filename — no file icon, like a subtle watermark
                    Text(displayFile.name, fontSize = 13.sp, fontWeight = FontWeight.Normal, color = muted.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    // Single clean save icon — hidden for user-attached files.
                    if (showSave) {
                        IconButton(onClick = {
                            scope.launch {
                                val msg = withContext(Dispatchers.IO) { FilePreviewKit.saveToDownloads(ctx, displayFile) }
                                android.widget.Toast.makeText(ctx, if (msg.startsWith("OK")) "Saved to Downloads/AhamAI" else "Couldn't save", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }) {
                            Icon(SaveIcon, "Save to Downloads", tint = muted, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(CancelSquareIcon, "Close", tint = muted, modifier = Modifier.size(22.dp))
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val file = renderFile
                    when {
                        FilePreviewKit.isImage(ext) -> ZoomableImage(file)
                        FilePreviewKit.isVideo(ext) -> InlineVideoPlayer(file)
                        ext == "pdf" -> PdfPagesView(file, isDark)
                        // HTML → Preview (rendered, zoomable WebView) ⇄ Code toggle
                        ext == "html" || ext == "htm" -> HtmlViewer(file, isDark, primary, muted)
                        ext == "csv" || ext == "tsv" -> {
                            val rows = remember(file.path) {
                                runCatching { FilePreviewKit.parseDelimited(file.readText(), if (ext == "tsv") '\t' else ',', 400) }.getOrDefault(emptyList())
                            }
                            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { MiniTable(rows, isDark) }
                        }
                        ext in setOf("docx", "doc", "pptx", "ppt") -> {
                            val text = remember(file.path) {
                                runCatching {
                                    when (ext) {
                                        "docx", "doc" -> FilePreviewKit.readDocxText(file, 50000)
                                        else -> FilePreviewKit.readPptxText(file, 50000)
                                    }
                                }.getOrDefault("")
                            }
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                if (text.isNotBlank()) {
                                    MarkdownText(text = text, color = primary)
                                } else {
                                    Text("No text content could be extracted from this ${FilePreviewKit.typeLabel(ext)} file.", color = muted)
                                }
                            }
                        }
                        ext in setOf("xlsx", "xls") -> {
                            val rows = remember(file.path) {
                                runCatching { FilePreviewKit.readXlsxGrid(file, 200) }.getOrDefault(emptyList())
                            }
                            if (rows.isNotEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { MiniTable(rows, isDark) }
                            } else {
                                // Try doc text extraction as fallback
                                val text = remember(file.path) {
                                    runCatching { FilePreviewKit.readDocxText(file, 30000) }.getOrDefault("")
                                }
                                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                    if (text.isNotBlank()) {
                                        Text(text, color = primary)
                                    } else {
                                        Text("This Excel file contains no extractable text preview.", color = muted)
                                    }
                                }
                            }
                        }
                        ext in setOf("txt", "md", "json", "log", "xml", "yml", "yaml", "kt", "java", "py", "js", "ts", "css") -> {
                            val content = remember(file.path) { runCatching { file.readText().take(20000) }.getOrDefault("") }
                            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                                MarkdownText(text = "```$ext\n$content\n```", color = primary)
                            }
                        }
                        else -> Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            val (icon, tint) = FilePreviewKit.iconFor(ext)
                            Icon(icon, null, tint = tint, modifier = Modifier.size(64.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("${FilePreviewKit.typeLabel(ext)} can't be rendered inline.", color = muted, fontSize = 13.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { FilePreviewKit.openExternally(ctx, file) }) {
                                Icon(Icons.Filled.OpenInNew, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Open in another app")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen image with pinch-to-zoom + pan (double-tap to reset). */
@Composable
private fun ZoomableImage(file: File) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = file,
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                }
        )
    }
}

/** HTML viewer with a Preview (rendered, zoomable WebView) ⇄ Code toggle. */
@Composable
private fun HtmlViewer(file: File, isDark: Boolean, primary: Color, muted: Color) {
    var showCode by remember { mutableStateOf(false) }
    val html = remember(file.path) { runCatching { file.readText() }.getOrDefault("") }
    Column(Modifier.fillMaxSize()) {
        // Segmented toggle: Preview | Code
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            val selBg = if (isDark) Color(0xFF2A2A2E) else Color(0xFFECECEF)
            Row(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (isDark) Color(0xFF161616) else Color(0xFFF4F4F4)).padding(3.dp)
            ) {
                listOf("Preview" to false, "Code" to true).forEach { (label, code) ->
                    val active = showCode == code
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (active) primary else muted,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (active) selBg else Color.Transparent)
                            .clickable { showCode = code }
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = if (isDark) Color(0xFF22242C) else Color(0xFFECEDEF))
        if (showCode) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                MarkdownText(text = "```html\n${html.take(40000)}\n```", color = primary)
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.builtInZoomControls = true   // pinch zoom
                        settings.displayZoomControls = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        setBackgroundColor(android.graphics.Color.WHITE)
                    }
                },
                update = { it.loadDataWithBaseURL(null, html, "text/html", "utf-8", null) }
            )
        }
    }
}

/** Renders up to a cap of PDF pages as bitmaps in a vertical scroll. */
@Composable
private fun PdfPagesView(file: File, isDark: Boolean) {
    val pageCount = remember(file.path) { FilePreviewKit.pdfPageCount(file) }
    val cap = pageCount.coerceAtMost(20)
    val pages by produceState<List<Bitmap>>(initialValue = emptyList(), file.path) {
        value = withContext(Dispatchers.IO) {
            (0 until cap).mapNotNull { FilePreviewKit.renderPdfPage(file, it, 1000) }
        }
    }
    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            pages.forEach { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                )
            }
            if (pageCount > cap) {
                Text("… showing first $cap of $pageCount pages. Use \u2197 to open the full file.",
                    fontSize = 11.sp, color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                    modifier = Modifier.padding(8.dp))
            }
        }
    }
}


/**
 * Full-screen-ish inline video player used inside [FileViewerDialog]. Uses the platform
 * [android.widget.VideoView] + [android.widget.MediaController] (no extra deps), centered with
 * no opaque card background so it sits cleanly on the dialog surface.
 */
@Composable
private fun InlineVideoPlayer(file: File) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                val container = android.widget.FrameLayout(ctx)
                val videoView = android.widget.VideoView(ctx).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    ).apply { gravity = android.view.Gravity.CENTER }
                    val controller = android.widget.MediaController(ctx)
                    controller.setAnchorView(this)
                    setMediaController(controller)
                    setVideoURI(android.net.Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        controller.show(0)
                        start()
                    }
                }
                container.addView(videoView)
                container
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
