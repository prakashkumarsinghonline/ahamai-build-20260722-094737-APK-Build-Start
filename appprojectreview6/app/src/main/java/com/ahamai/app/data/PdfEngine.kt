package com.ahamai.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import com.itextpdf.kernel.pdf.*
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.canvas.parser.PdfCanvasProcessor
import com.itextpdf.kernel.pdf.canvas.parser.EventType
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import com.itextpdf.kernel.pdf.canvas.parser.data.IEventData
import com.itextpdf.kernel.pdf.canvas.parser.data.TextRenderInfo
import com.itextpdf.kernel.pdf.canvas.parser.listener.IEventListener
import com.itextpdf.kernel.pdf.canvas.parser.listener.LocationTextExtractionStrategy
import com.itextpdf.kernel.pdf.xobject.PdfImageXObject
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.font.PdfFont
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.utils.PdfMerger
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.borders.Border
import com.itextpdf.layout.borders.SolidBorder
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.io.image.ImageDataFactory
import com.itextpdf.forms.PdfAcroForm

/**
 * PDF Engine - handles creation, REAL in-place text editing, merging, splitting,
 * compression (quality up/down) and image collages of PDFs using iText7.
 *
 * Paths from the agent are resolved fuzzily (via ProjectManager) so a slightly-off
 * path still finds the right file instead of failing.
 */
object PdfEngine {

    // ---------- Public API (called from CodeAgent.executeStep) ----------

    fun createPdf(projectDir: String, outputPath: String, content: String, style: String = "", watermark: String = ""): String {
        return try {
            // Sanitize output path: ensure it's a valid filename ending in .pdf
            var path = outputPath.trim().removePrefix("/").removePrefix("./")
            if (path.isBlank() || path == "." || path == "pdf") {
                path = "output.pdf"
            }
            if (!path.lowercase().endsWith(".pdf")) {
                path = "$path.pdf"
            }
            // If the path is a directory that exists, append a filename
            val candidate = File(projectDir, path)
            val outFile = if (candidate.isDirectory) {
                File(candidate, "document.pdf")
            } else {
                candidate
            }
            outFile.parentFile?.mkdirs()
            if (content.isBlank()) return "ERROR: No content provided for the PDF. Pass the markdown content as the second argument."
            createStyledPdf(projectDir, outFile, content, style, watermark)
            // Self-verify: reopen the file and count pages so the agent never has to do a
            // separate PDF_READ just to confirm the PDF is valid.
            val (pages, ok) = verifyPdf(outFile)
            if (!ok) return "ERROR: PDF was written but failed verification (couldn't re-open). The content may be malformed."
            val relPath = outFile.relativeTo(File(projectDir)).path
            buildString {
                append("OK: Created PDF at $relPath ")
                append("[style=${resolveTheme(style).name}, $pages page(s), ${fmtSize(outFile.length())}")
                if (watermark.isNotBlank()) append(", watermark=\"${watermark.trim()}\"")
                append("]. Verified readable — do NOT recreate it; it renders inline for the user.")
            }
        } catch (e: Exception) {
            "ERROR: PDF creation failed: ${e.message}"
        }
    }

    /** Reopen a freshly-written PDF and report (pageCount, isValid). */
    private fun verifyPdf(file: File): Pair<Int, Boolean> = try {
        if (!file.exists() || file.length() < 100L) 0 to false
        else {
            val doc = PdfDocument(PdfReader(file.absolutePath))
            val n = doc.numberOfPages
            doc.close()
            n to (n > 0)
        }
    } catch (e: Exception) { 0 to false }

    /**
     * REAL in-place text edit: finds [oldText] on each page and overlays [newText]
     * using the SAME font, size, colour and exact position as the original — the rest
     * of the PDF (layout, images, other text) is left completely untouched.
     */
    fun editText(projectDir: String, pdfPath: String, oldText: String, newText: String): String {
        return try {
            val file = resolve(projectDir, pdfPath) ?: return "ERROR: File not found: $pdfPath"
            if (oldText.isBlank()) return "ERROR: nothing to find (old text is empty)."
            val count = replaceTextInPdf(file, oldText, newText)
            if (count > 0)
                "OK: Replaced $count occurrence(s) of \"$oldText\" with \"$newText\" in ${file.name}. Original layout & fonts preserved."
            else
                "ERROR: \"$oldText\" was not found as selectable text in ${file.name}. It may be part of a scanned image, or split across lines/styles. Run PDF_READ first to see the exact extractable text, then match it precisely."
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun addPage(projectDir: String, pdfPath: String, content: String): String {
        return try {
            val file = resolve(projectDir, pdfPath) ?: return "ERROR: File not found: $pdfPath"
            appendPageToPdf(file, content)
            "OK: Added new page to ${file.name}"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun addImage(projectDir: String, pdfPath: String, imagePath: String, page: Int): String {
        return try {
            val file = resolve(projectDir, pdfPath) ?: return "ERROR: File not found: $pdfPath"
            val imgFile = resolve(projectDir, imagePath) ?: return "ERROR: Image not found: $imagePath"
            insertImageIntoPdf(file, imgFile, page)
            "OK: Added image to page $page of ${file.name}"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun addChart(projectDir: String, outputPath: String, chartType: String, dataJson: String): String {
        return try {
            val outFile = File(projectDir, outputPath.trim().removePrefix("/"))
            outFile.parentFile?.mkdirs()
            renderChartToPdf(outFile, chartType, dataJson)
            "OK: Created chart PDF at ${outputPath.trim()}"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Merge PDFs. Accepts EITHER (file1, file2, output) OR a comma-separated list of
     * files in [arg1] with the output in [arg2].
     */
    fun mergePdfs(projectDir: String, arg1: String, arg2: String, arg3: String): String {
        return try {
            val files = ArrayList<File>()
            val output: String
            if (arg1.contains(",")) {
                for (p in arg1.split(",").map { it.trim() }.filter { it.isNotBlank() }) {
                    val f = resolve(projectDir, p) ?: return "ERROR: File not found: $p"
                    files.add(f)
                }
                output = arg2.trim().ifBlank { "merged.pdf" }
            } else {
                val f1 = resolve(projectDir, arg1) ?: return "ERROR: File not found: $arg1"
                val f2 = resolve(projectDir, arg2) ?: return "ERROR: File not found: $arg2"
                files.add(f1); files.add(f2)
                output = arg3.trim().ifBlank { "merged.pdf" }
            }
            if (files.size < 2) return "ERROR: need at least 2 PDFs to merge."
            val outFile = File(projectDir, output.removePrefix("/"))
            outFile.parentFile?.mkdirs()
            val pages = mergePdfFiles(files, outFile)
            "OK: Merged ${files.size} PDFs (${pages} pages total) into $output"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun splitPdf(projectDir: String, inputPath: String, pages: String, output: String): String {
        return try {
            val inFile = resolve(projectDir, inputPath) ?: return "ERROR: File not found: $inputPath"
            val outName = output.trim().removePrefix("/").ifBlank { inFile.nameWithoutExtension + "_pages.pdf" }
            val outFile = File(projectDir, outName)
            outFile.parentFile?.mkdirs()
            val n = splitPdfPages(inFile, pages, outFile)
            "OK: Extracted $n page(s) [$pages] to $outName"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun readPdf(projectDir: String, pdfPath: String): String {
        return try {
            val file = resolve(projectDir, pdfPath) ?: return "ERROR: File not found: $pdfPath"
            extractTextFromPdf(file)
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    fun fillForm(projectDir: String, pdfPath: String, fieldsJson: String): String {
        return try {
            val file = resolve(projectDir, pdfPath) ?: return "ERROR: File not found: $pdfPath"
            fillPdfForm(file, fieldsJson)
            "OK: Filled form fields in ${file.name}"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Change PDF quality/size. [quality] = "low" | "medium" | "high".
     * low/medium downscale + re-compress JPEG images (big wins for scans/photos);
     * all levels rewrite the file with maximum stream compression.
     */
    fun compress(projectDir: String, inputPath: String, output: String, quality: String): String {
        return try {
            val inFile = resolve(projectDir, inputPath) ?: return "ERROR: File not found: $inputPath"
            val q = quality.trim().lowercase()
            val (scale, jpegQ, downscale) = when {
                q.startsWith("low") || q == "small" || q == "compress" -> Triple(0.5f, 40, true)
                q.startsWith("med") -> Triple(0.75f, 60, true)
                q.startsWith("high") || q == "best" -> Triple(1.0f, 85, false)
                else -> Triple(0.7f, 60, true)
            }
            val outName = output.trim().removePrefix("/").ifBlank { inFile.nameWithoutExtension + "_compressed.pdf" }
            val outFile = File(projectDir, outName)
            outFile.parentFile?.mkdirs()
            val before = inFile.length()
            val touched = compressPdf(inFile, outFile, scale, jpegQ, downscale)
            val after = outFile.length()
            "OK: ${if (q.startsWith("high")) "Optimized" else "Compressed"} ${inFile.name} (${fmtSize(before)} -> ${fmtSize(after)}, $touched image(s) re-encoded) saved to $outName"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /**
     * Build an image collage PDF. [imagesCsv] = comma/newline-separated image paths,
     * [output] = output pdf, [columnsArg] = number of columns (blank = auto grid).
     */
    fun collage(projectDir: String, imagesCsv: String, output: String, columnsArg: String): String {
        return try {
            val paths = imagesCsv.split(Regex("[,\\n]")).map { it.trim() }.filter { it.isNotBlank() }
            if (paths.isEmpty()) return "ERROR: no images provided for the collage."
            val files = ArrayList<File>()
            for (p in paths) {
                val f = resolve(projectDir, p) ?: return "ERROR: image not found: $p"
                files.add(f)
            }
            val cols = columnsArg.trim().toIntOrNull()
                ?: Math.ceil(Math.sqrt(files.size.toDouble())).toInt().coerceAtLeast(1)
            val outName = output.trim().removePrefix("/").ifBlank { "collage.pdf" }
            val outFile = File(projectDir, outName)
            outFile.parentFile?.mkdirs()
            buildCollage(outFile, files, cols.coerceIn(1, 6))
            "OK: Created collage PDF ($outName) with ${files.size} image(s) in ${cols.coerceIn(1, 6)} columns."
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    // ---------- Path resolution ----------

    private fun resolve(projectDir: String, relPath: String): File? {
        ProjectManager.resolveFile(projectDir, relPath)?.let { return it }
        val direct = File(projectDir, relPath.trim().trim('"', '`', '\'').removePrefix("/").removePrefix("./"))
        return if (direct.exists()) direct else null
    }

    private fun fmtSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    // ---------- Markdown -> PDF ----------

    // ---------- Styled Markdown -> PDF (themes, watermark, inline images, page numbers) ----------

    /** A visual theme controlling fonts and colours for a generated PDF. */
    private data class Theme(
        val name: String,
        val bodyFont: String, val boldFont: String, val italicFont: String, val headFont: String,
        val heading: DeviceRgb, val body: DeviceRgb, val accent: DeviceRgb,
        val pageBg: DeviceRgb?, val codeBg: DeviceRgb, val codeText: DeviceRgb,
        val rule: DeviceRgb, val tableHeaderBg: DeviceRgb, val tableHeaderText: DeviceRgb
    )

    /** Resolve a style name to a Theme. Unknown / blank -> "modern". */
    private fun resolveTheme(style: String): Theme {
        val s = style.trim()
        val lower = s.lowercase()
        // 1. Pick a base preset (first known name found in the string), else "modern".
        val baseName = listOf("dark", "night", "classic", "elegant", "serif", "corporate",
            "business", "minimal", "mono", "modern").firstOrNull { lower.contains(it) } ?: "modern"
        var t = basePreset(baseName)
        // 2. Apply ANY custom overrides the caller passed, so themes are NOT limited to the
        //    presets: accent/heading/body/rule=#hex, bg=#hex|dark|white, font=serif|sans|mono.
        var customized = false
        val tok = Regex("([a-zA-Z]+)\\s*[=:]\\s*(#?[0-9A-Fa-f]{3,6}|serif|sans|mono|dark|white|light)")
        for (m in tok.findAll(s)) {
            val k = m.groupValues[1].lowercase(); val v = m.groupValues[2]
            val col = hexColor(v)
            val nt = when (k) {
                "accent" -> col?.let { t.copy(accent = it, tableHeaderBg = it) }
                "heading", "head", "title" -> col?.let { t.copy(heading = it) }
                "body", "text" -> col?.let { t.copy(body = it) }
                "rule", "border", "line" -> col?.let { t.copy(rule = it) }
                "code" -> col?.let { t.copy(codeBg = it) }
                "bg", "background", "page" -> when {
                    v.equals("white", true) || v.equals("light", true) -> t.copy(pageBg = null)
                    v.equals("dark", true) -> t.copy(pageBg = DeviceRgb(22, 24, 33), body = DeviceRgb(223, 226, 231))
                    else -> col?.let { t.copy(pageBg = it) }
                }
                "font" -> when (v.lowercase()) {
                    "serif" -> t.copy(bodyFont = StandardFonts.TIMES_ROMAN, boldFont = StandardFonts.TIMES_BOLD, italicFont = StandardFonts.TIMES_ITALIC, headFont = StandardFonts.TIMES_BOLD)
                    "mono" -> t.copy(bodyFont = StandardFonts.COURIER, boldFont = StandardFonts.COURIER_BOLD, italicFont = StandardFonts.COURIER_OBLIQUE, headFont = StandardFonts.COURIER_BOLD)
                    else -> t.copy(bodyFont = StandardFonts.HELVETICA, boldFont = StandardFonts.HELVETICA_BOLD, italicFont = StandardFonts.HELVETICA_OBLIQUE, headFont = StandardFonts.HELVETICA_BOLD)
                }
                else -> null
            }
            if (nt != null) { t = nt; customized = true }
        }
        return if (customized) t.copy(name = "custom") else t
    }

    /** Parse "#RRGGBB" / "RRGGBB" / "#RGB" into a DeviceRgb, or null if not a hex colour. */
    private fun hexColor(v: String): DeviceRgb? {
        val h = v.removePrefix("#")
        val hh = when (h.length) {
            3 -> h.map { "$it$it" }.joinToString("")
            6 -> h
            else -> return null
        }
        return try {
            DeviceRgb(hh.substring(0, 2).toInt(16), hh.substring(2, 4).toInt(16), hh.substring(4, 6).toInt(16))
        } catch (e: Exception) { null }
    }

    /** The built-in starting points. Custom overrides are layered on top of these. */
    private fun basePreset(name: String): Theme {
        val H = StandardFonts.HELVETICA; val HB = StandardFonts.HELVETICA_BOLD; val HO = StandardFonts.HELVETICA_OBLIQUE
        val T = StandardFonts.TIMES_ROMAN; val TB = StandardFonts.TIMES_BOLD; val TI = StandardFonts.TIMES_ITALIC
        return when (name) {
            "dark", "night" -> Theme("dark", H, HB, HO, HB,
                DeviceRgb(147, 197, 253), DeviceRgb(223, 226, 231), DeviceRgb(96, 165, 250),
                DeviceRgb(22, 24, 33), DeviceRgb(40, 42, 56), DeviceRgb(224, 226, 232),
                DeviceRgb(70, 74, 92), DeviceRgb(33, 36, 50), DeviceRgb(191, 219, 254))
            "classic", "elegant", "serif" -> Theme("classic", T, TB, TI, TB,
                DeviceRgb(80, 20, 35), DeviceRgb(33, 33, 33), DeviceRgb(122, 30, 50),
                null, DeviceRgb(245, 240, 236), DeviceRgb(40, 30, 30),
                DeviceRgb(210, 200, 195), DeviceRgb(122, 30, 50), DeviceRgb(255, 255, 255))
            "corporate", "business" -> Theme("corporate", H, HB, HO, HB,
                DeviceRgb(15, 42, 76), DeviceRgb(30, 35, 42), DeviceRgb(15, 42, 76),
                null, DeviceRgb(238, 242, 248), DeviceRgb(30, 35, 42),
                DeviceRgb(200, 210, 225), DeviceRgb(15, 42, 76), DeviceRgb(255, 255, 255))
            "minimal", "mono" -> Theme("minimal", H, HB, HO, HB,
                DeviceRgb(17, 17, 17), DeviceRgb(40, 40, 40), DeviceRgb(0, 0, 0),
                null, DeviceRgb(245, 245, 245), DeviceRgb(30, 30, 30),
                DeviceRgb(225, 225, 225), DeviceRgb(30, 30, 30), DeviceRgb(255, 255, 255))
            else -> Theme("modern", H, HB, HO, HB,
                DeviceRgb(17, 24, 39), DeviceRgb(33, 37, 41), DeviceRgb(30, 35, 45),
                null, DeviceRgb(241, 245, 249), DeviceRgb(30, 35, 45),
                DeviceRgb(220, 224, 230), DeviceRgb(30, 35, 45), DeviceRgb(255, 255, 255))
        }
    }

    /** Per-page decoration: page background (dark themes), faint diagonal watermark, page numbers. */
    private class DecorHandler(
        val theme: Theme, val watermark: String?, val wmFont: PdfFont, val footFont: PdfFont
    ) : com.itextpdf.kernel.events.IEventHandler {
        override fun handleEvent(event: com.itextpdf.kernel.events.Event) {
            val ev = event as com.itextpdf.kernel.events.PdfDocumentEvent
            val pdf = ev.document
            val page = ev.page
            val size = page.pageSize
            val pageNum = pdf.getPageNumber(page)
            // Drawn BEFORE page content (so it sits behind text): background + watermark.
            val under = PdfCanvas(page.newContentStreamBefore(), page.resources, pdf)
            theme.pageBg?.let {
                under.saveState(); under.setFillColor(it)
                under.rectangle(0.0, 0.0, size.width.toDouble(), size.height.toDouble()); under.fill(); under.restoreState()
            }
            if (!watermark.isNullOrBlank()) {
                under.saveState()
                under.setExtGState(com.itextpdf.kernel.pdf.extgstate.PdfExtGState().setFillOpacity(0.10f))
                val lc = com.itextpdf.layout.Canvas(under, size)
                val p = Paragraph(watermark).setFont(wmFont).setFontSize(60f).setFontColor(theme.accent)
                lc.showTextAligned(
                    p, size.width / 2f, size.height / 2f, pageNum,
                    TextAlignment.CENTER, com.itextpdf.layout.properties.VerticalAlignment.MIDDLE,
                    (Math.PI / 4.0).toFloat()
                )
                lc.close()
                under.restoreState()
            }
            // Drawn AFTER content: footer page number.
            val over = PdfCanvas(page.newContentStreamAfter(), page.resources, pdf)
            val lc = com.itextpdf.layout.Canvas(over, size)
            lc.showTextAligned(
                Paragraph(pageNum.toString()).setFont(footFont).setFontSize(9f).setFontColor(theme.rule),
                size.width / 2f, 22f, pageNum,
                TextAlignment.CENTER, com.itextpdf.layout.properties.VerticalAlignment.BOTTOM, 0f
            )
            lc.close()
        }
    }

    /**
     * Pre-processes markdown content to ensure block-level markers are on their own lines.
     * Models often send content like "...text.## Heading" or "...end.**1. Item" without
     * line breaks, which causes the PDF parser to miss headings, bullets, and separators.
     */
    private fun preprocessMarkdownContent(raw: String): String {
        var text = raw
        // Insert newline before heading markers stuck to preceding text
        // e.g. "...sentence.## Heading" → "...sentence.\n## Heading"
        // [^\n#] ensures we don't split "##" into "#\n#"
        text = Regex("([^\\n#])(#{1,3} )").replace(text) { "${it.groupValues[1]}\n${it.groupValues[2]}" }

        // Insert newline before bullet points stuck to preceding text
        text = Regex("([^\\n])([-*] [A-Z])").replace(text) { "${it.groupValues[1]}\n${it.groupValues[2]}" }

        // Insert newline before numbered lists stuck to preceding text
        text = Regex("([^\\n])(\\*{0,2}\\d+\\. )").replace(text) { "${it.groupValues[1]}\n${it.groupValues[2]}" }

        // Insert newline before directives stuck to text
        text = Regex("([^\\n])(\\[(?:NEWPAGE|STEPS|FLOW|PAGECOLOR))").replace(text) { "${it.groupValues[1]}\n${it.groupValues[2]}" }

        // Split overly long paragraphs at sentence boundaries (>300 chars without a newline)
        val lines = text.split("\n")
        val processed = StringBuilder()
        for (line in lines) {
            if (line.length > 300 && !line.trimStart().startsWith("|") && !line.trimStart().startsWith("```")) {
                val split = line.replace(Regex("\\. ([A-Z])")) { ".\n${it.groupValues[1]}" }
                processed.append(split)
            } else {
                processed.append(line)
            }
            processed.append("\n")
        }

        return processed.toString().trimEnd()
    }

    private fun createStyledPdf(projectDir: String, outFile: File, content: String, style: String, watermark: String) {
        val theme = resolveTheme(style)
        val writer = PdfWriter(FileOutputStream(outFile))
        val pdfDoc = PdfDocument(writer)
        val font = PdfFontFactory.createFont(theme.bodyFont)
        val boldFont = PdfFontFactory.createFont(theme.boldFont)
        val italicFont = PdfFontFactory.createFont(theme.italicFont)
        val headFont = PdfFontFactory.createFont(theme.headFont)
        val monoFont = PdfFontFactory.createFont(StandardFonts.COURIER)

        pdfDoc.addEventHandler(
            com.itextpdf.kernel.events.PdfDocumentEvent.END_PAGE,
            DecorHandler(theme, watermark.trim().ifBlank { null }, boldFont, font)
        )

        val document = Document(pdfDoc, PageSize.A4)
        document.setMargins(54f, 48f, 60f, 48f)

        // Pre-process: models often send markdown without proper line breaks before headings,
        // bullets, and other block-level markers. Insert newlines so the parser picks them up.
        val preprocessed = preprocessMarkdownContent(content)
        val lines = preprocessed.split("\n")
        var i = 0
        var inCodeBlock = false
        val codeBlockLines = mutableListOf<String>()
        var codeBlockLang = ""
        val imgRegex = Regex("^!\\[[^\\]]*]\\(\\s*<?([^)\\s>]+)[^)]*\\)\\s*$")

        while (i < lines.size) {
            val line = lines[i]

            if (line.trim().equals("[NEWPAGE]", ignoreCase = true) && !inCodeBlock) {
                document.add(AreaBreak()); i++; continue
            }
            // Page background color directive: [PAGECOLOR #hex]
            val pageColorMatch = Regex("^\\s*\\[PAGECOLOR\\s+(#[0-9a-fA-F]{6})\\s*]", RegexOption.IGNORE_CASE).find(line.trim())
            if (pageColorMatch != null && !inCodeBlock) {
                val hex = pageColorMatch.groupValues[1]
                val r = Integer.parseInt(hex.substring(1, 3), 16)
                val g = Integer.parseInt(hex.substring(3, 5), 16)
                val b = Integer.parseInt(hex.substring(5, 7), 16)
                val pageNum = pdfDoc.numberOfPages
                if (pageNum > 0) {
                    val page = pdfDoc.getPage(pageNum)
                    val canvas = PdfCanvas(page.newContentStreamBefore(), page.resources, pdfDoc)
                    canvas.saveState()
                        .setFillColor(DeviceRgb(r, g, b))
                        .rectangle(0.0, 0.0, page.pageSize.width.toDouble(), page.pageSize.height.toDouble())
                        .fill()
                        .restoreState()
                }
                i++; continue
            }
            if (line.trim().startsWith("```")) {
                if (inCodeBlock) {
                    // Check if this was a mermaid/chart block — render as styled box
                    val blockLang = codeBlockLang.lowercase()
                    if (blockLang == "mermaid" || blockLang == "chart") {
                        // Render as a styled diagram placeholder with the source
                        val diagramLabel = if (blockLang == "mermaid") "📊 Diagram" else "📈 Chart"
                        document.add(
                            Paragraph(diagramLabel).setFont(boldFont).setFontSize(10f)
                                .setFontColor(theme.accent).setMarginTop(8f).setMarginBottom(2f)
                        )
                        document.add(
                            Paragraph(codeBlockLines.joinToString("\n"))
                                .setFont(monoFont).setFontSize(8f)
                                .setFontColor(theme.codeText).setBackgroundColor(theme.codeBg)
                                .setPadding(8f).setMarginBottom(6f)
                        )
                    } else {
                        document.add(
                            Paragraph(codeBlockLines.joinToString("\n"))
                                .setFont(monoFont).setFontSize(9f)
                                .setFontColor(theme.codeText).setBackgroundColor(theme.codeBg)
                                .setPadding(10f).setMarginTop(4f).setMarginBottom(6f)
                        )
                    }
                    codeBlockLines.clear(); inCodeBlock = false; codeBlockLang = ""
                } else {
                    inCodeBlock = true
                    codeBlockLang = line.trim().removePrefix("```").trim()
                }
                i++; continue
            }
            if (inCodeBlock) { codeBlockLines.add(line); i++; continue }

            // Process diagram: "[STEPS] one | two | three" (or [FLOW]) -> numbered colourful steps.
            val diagUp = line.trim().uppercase()
            if (diagUp.startsWith("[STEPS]") || diagUp.startsWith("[FLOW]")) {
                val tl = line.trim()
                val payload = tl.substring(tl.indexOf(']') + 1).trim()
                val items = payload.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                if (items.isNotEmpty())
                    addStepsDiagram(document, theme, font, boldFont, italicFont, monoFont, items)
                i++; continue
            }

            // Inline image: ![alt](path-or-url) on its own line
            val imgM = imgRegex.find(line.trim())
            if (imgM != null) {
                val src = imgM.groupValues[1].trim()
                try {
                    val data = if (src.startsWith("http"))
                        ImageDataFactory.create(java.net.URL(src))
                    else resolve(projectDir, src)?.let { ImageDataFactory.create(it.absolutePath) }
                    if (data != null) {
                        // Image sizing: fit within page width (margins: 48 left + 48 right = 96pt from A4 595pt)
                        val maxW = PageSize.A4.width - 96f
                        document.add(
                            Image(data)
                                .setMaxWidth(maxW)
                                .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                                .setMarginTop(8f).setMarginBottom(8f)
                        )
                    } else {
                        document.add(Paragraph("[image not found: $src]").setFont(italicFont).setFontSize(9f).setFontColor(theme.body))
                    }
                } catch (_: Exception) {
                    document.add(Paragraph("[couldn't load image: $src]").setFont(italicFont).setFontSize(9f).setFontColor(theme.body))
                }
                i++; continue
            }

            // Display LaTeX: $$...$$ or \[...\] (single- or multi-line) -> rendered as an image.
            val ltTrim = line.trim()
            if (ltTrim.startsWith("$$") || ltTrim.startsWith("\\[")) {
                val (formula, next) = collectLatex(lines, i)
                if (formula != null) {
                    val img = latexImage(formula, theme)
                    if (img != null) {
                        document.add(img.setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER)
                            .setMarginTop(6f).setMarginBottom(6f))
                    } else {
                        document.add(Paragraph(formula).setFont(monoFont).setFontSize(10f)
                            .setFontColor(theme.body).setMarginTop(4f).setMarginBottom(4f))
                    }
                    i = next; continue
                }
            }

            if (line.startsWith("### ")) {
                document.add(Paragraph(line.removePrefix("### ")).setFont(headFont).setFontSize(14f)
                    .setFontColor(theme.heading).setMarginTop(8f).setMarginBottom(4f)); i++; continue
            }
            if (line.startsWith("## ")) {
                document.add(Paragraph(line.removePrefix("## ")).setFont(headFont).setFontSize(17f)
                    .setFontColor(theme.heading).setMarginTop(12f).setMarginBottom(4f)
                    .setBorderBottom(SolidBorder(theme.rule, 0.75f)).setPaddingBottom(3f)); i++; continue
            }
            if (line.startsWith("# ")) {
                document.add(Paragraph(line.removePrefix("# ")).setFont(headFont).setFontSize(24f)
                    .setFontColor(theme.accent).setMarginTop(10f).setMarginBottom(8f)); i++; continue
            }
            if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
                val bulletText = line.trimStart().removePrefix("- ").removePrefix("* ")
                val para = Paragraph().setFont(font).setFontSize(11f).setFontColor(theme.body).setMarginLeft(20f)
                para.add(Text("\u2022  ").setFontColor(theme.accent))
                addStyledText(para, bulletText, font, boldFont, italicFont, monoFont, theme)
                document.add(para); i++; continue
            }
            if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
                val tableLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && lines[j].trim().startsWith("|") && lines[j].trim().endsWith("|")) {
                    tableLines.add(lines[j]); j++
                }
                renderStyledTable(document, tableLines, font, boldFont, italicFont, monoFont, theme); i = j; continue
            }
            if (line.trim().matches(Regex("^[-*_]{3,}$"))) {
                document.add(Paragraph(" ").setBorderBottom(SolidBorder(theme.rule, 0.5f)).setMarginTop(8f).setMarginBottom(8f))
                i++; continue
            }
            if (line.isNotBlank()) {
                val para = Paragraph().setFont(font).setFontSize(11f).setFontColor(theme.body).setMarginBottom(4f)
                addStyledText(para, line, font, boldFont, italicFont, monoFont, theme)
                document.add(para)
            } else {
                document.add(Paragraph("\n").setFontSize(6f))
            }
            i++
        }
        document.close()
    }

    /**
     * Render a LaTeX formula to a PNG and wrap it as an iText Image, so math shows up in PDFs
     * instead of raw \[ ... \] / $$ ... $$ text. Returns null if it can't be rendered.
     */
    private fun latexImage(formula: String, theme: Theme): Image? {
        return try {
            val cv = theme.body.colorValue  // iText returns RGB components in 0..1
            val r = (cv[0] * 255).toInt().coerceIn(0, 255)
            val g = (cv[1] * 255).toInt().coerceIn(0, 255)
            val b = (cv[2] * 255).toInt().coerceIn(0, 255)
            val argb = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            val drawable = ru.noties.jlatexmath.JLatexMathDrawable.builder(formula)
                .textSize(40f)
                .padding(6)
                .background(0x00000000)
                .color(argb)
                .align(ru.noties.jlatexmath.JLatexMathDrawable.ALIGN_LEFT)
                .build()
            val w = drawable.intrinsicWidth.coerceIn(1, 4000)
            val h = drawable.intrinsicHeight.coerceIn(1, 4000)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
            val img = Image(ImageDataFactory.create(bos.toByteArray()))
            // Scale down: JLatexMath renders at 40px; ~0.5pt/px keeps math at a natural size.
            img.scaleToFit((w * 0.5f).coerceAtMost(PageSize.A4.width - 96f), (h * 0.5f))
            img
        } catch (e: Throwable) { null }
    }

    /**
     * Collect a display-LaTeX block starting at [start]. Handles single-line ($$ x $$ / \[ x \])
     * and multi-line blocks. Returns the inner formula (or null) and the index AFTER the block.
     */
    private fun collectLatex(lines: List<String>, start: Int): Pair<String?, Int> {
        val first = lines[start].trim()
        val open: String; val close: String
        when {
            first.startsWith("$$")  -> { open = "$$"; close = "$$" }
            first.startsWith("\\[") -> { open = "\\["; close = "\\]" }
            else -> return null to (start + 1)
        }
        val afterOpen = first.substring(open.length)
        // Single-line: closing delimiter on the same line.
        val closeIdx = afterOpen.indexOf(close)
        if (closeIdx >= 0) return afterOpen.substring(0, closeIdx).trim().ifBlank { null } to (start + 1)
        // Multi-line: accumulate until a line containing the closing delimiter.
        val sb = StringBuilder(afterOpen).append('\n')
        var j = start + 1
        while (j < lines.size) {
            val l = lines[j]
            val ci = l.indexOf(close)
            if (ci >= 0) { sb.append(l.substring(0, ci)); return sb.toString().trim().ifBlank { null } to (j + 1) }
            sb.append(l).append('\n'); j++
        }
        return sb.toString().trim().ifBlank { null } to j
    }

    /** Render a vertical numbered process diagram: number badge + bordered step card, with down arrows.
     *  Badges/arrows use the document's theme accent (no hardcoded blue) so the PDF stays monochrome
     *  and consistent with whatever theme was chosen. */
    private fun addStepsDiagram(
        document: Document, theme: Theme, font: PdfFont, boldFont: PdfFont, italicFont: PdfFont, monoFont: PdfFont, items: List<String>
    ) {
        val white = DeviceRgb(255, 255, 255)
        items.forEachIndexed { idx, raw ->
            val color = theme.accent
            val table = Table(floatArrayOf(1f, 9f)).useAllAvailableWidth().setMarginTop(2f).setMarginBottom(0f)
            val badge = Cell()
                .add(Paragraph((idx + 1).toString()).setFont(boldFont).setFontSize(14f).setFontColor(white))
                .setBackgroundColor(color).setBorder(Border.NO_BORDER)
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setPaddingTop(8f).setPaddingBottom(8f)
            val bodyPara = Paragraph().setFont(font).setFontSize(11.5f).setFontColor(theme.body)
            addStyledText(bodyPara, raw, font, boldFont, italicFont, monoFont, theme)
            val body = Cell()
                .add(bodyPara)
                .setBorder(SolidBorder(theme.rule, 0.75f))
                .setVerticalAlignment(com.itextpdf.layout.properties.VerticalAlignment.MIDDLE)
                .setPaddingLeft(10f).setPaddingRight(10f).setPaddingTop(8f).setPaddingBottom(8f)
            table.addCell(badge); table.addCell(body)
            document.add(table)
            if (idx < items.size - 1) {
                document.add(
                    Paragraph("\u2193").setFont(boldFont).setFontSize(13f).setFontColor(color)
                        .setTextAlignment(TextAlignment.LEFT).setMarginLeft(14f).setMarginTop(1f).setMarginBottom(1f)
                )
            }
        }
        document.add(Paragraph("\n").setFontSize(4f))
    }

    private fun addStyledText(
        para: Paragraph, text: String, font: PdfFont, boldFont: PdfFont, italicFont: PdfFont, monoFont: PdfFont, theme: Theme,
        textColor: DeviceRgb? = null
    ) {
        val baseColor = textColor ?: theme.body
        val parts = Regex("(\\*\\*(.+?)\\*\\*|__(.+?)__|\\*(.+?)\\*|_(.+?)_|`(.+?)`|\\[(.+?)]\\((.+?)\\)|[^*_`\\[]+)").findAll(text)
        for (part in parts) {
            val full = part.groupValues[0]
            when {
                full.startsWith("**") || full.startsWith("__") ->
                    para.add(Text(full.removePrefix("**").removeSuffix("**").removePrefix("__").removeSuffix("__")).setFont(boldFont).setFontColor(baseColor))
                full.startsWith("`") ->
                    para.add(Text(full.removePrefix("`").removeSuffix("`")).setFont(monoFont).setFontColor(theme.codeText).setBackgroundColor(theme.codeBg))
                full.startsWith("[") && part.groupValues[7].isNotEmpty() ->
                    para.add(Text(part.groupValues[7]).setFont(font).setFontColor(theme.accent)
                        .setUnderline().setAction(com.itextpdf.kernel.pdf.action.PdfAction.createURI(part.groupValues[8])))
                (full.startsWith("*") || full.startsWith("_")) && full.length > 1 ->
                    para.add(Text(full.removePrefix("*").removeSuffix("*").removePrefix("_").removeSuffix("_")).setFont(italicFont).setFontColor(baseColor))
                else -> para.add(Text(full).setFont(font).setFontColor(baseColor))
            }
        }
    }

    private fun renderStyledTable(document: Document, tableLines: List<String>, font: PdfFont, boldFont: PdfFont, italicFont: PdfFont, monoFont: PdfFont, theme: Theme) {
        val dataLines = tableLines.filter { !it.trim().matches(Regex("^\\|[-:|\\s]+\\|$")) }
        if (dataLines.isEmpty()) return
        val firstRow = parsePipeRow(dataLines[0])
        val numCols = firstRow.size
        if (numCols == 0) return
        val table = Table(UnitValue.createPercentArray(numCols)).useAllAvailableWidth()
        table.setMarginTop(6f).setMarginBottom(8f)
        for (cell in firstRow) {
            // Header cells: bold base font, and still parse inline markdown so **x**/`x` render.
            val hp = Paragraph().setFontSize(10f)
            addStyledText(hp, cell.trim(), boldFont, boldFont, italicFont, monoFont, theme, textColor = theme.tableHeaderText)
            table.addHeaderCell(
                Cell().add(hp)
                    .setBackgroundColor(theme.tableHeaderBg).setPadding(6f).setBorder(SolidBorder(theme.rule, 0.5f))
            )
        }
        for (idx in 1 until dataLines.size) {
            val cells = parsePipeRow(dataLines[idx])
            for (j in 0 until numCols) {
                // Data cells: parse inline markdown (**bold**, *italic*, `code`, links) so they
                // render properly instead of showing literal ** / ` characters.
                val cp = Paragraph().setFontSize(10f)
                addStyledText(cp, cells.getOrElse(j) { "" }.trim(), font, boldFont, italicFont, monoFont, theme)
                table.addCell(
                    Cell().add(cp)
                        .setPadding(5f).setBorder(SolidBorder(theme.rule, 0.5f))
                )
            }
        }
        document.add(table)
    }

    private fun parsePipeRow(line: String): List<String> {
        val trimmed = line.trim().removePrefix("|").removeSuffix("|")
        return trimmed.split("|").map { it.trim() }
    }

    // ---------- REAL in-place text replacement ----------

    private class CharBox(
        val ch: String,
        val left: Float, val right: Float, val bottom: Float, val top: Float,
        val startX: Float, val baselineY: Float,
        val font: PdfFont, val size: Float,
        val color: com.itextpdf.kernel.colors.Color?
    )

    private class CharCollector : IEventListener {
        val chars = ArrayList<CharBox>()
        override fun eventOccurred(data: IEventData, type: EventType) {
            if (type != EventType.RENDER_TEXT) return
            val tri = data as? TextRenderInfo ?: return
            for (ci in tri.characterRenderInfos) {
                val t = ci.text
                if (t.isEmpty()) continue
                val asc = ci.ascentLine
                val desc = ci.descentLine
                val base = ci.baseline
                val xs = listOf(
                    desc.startPoint.get(0), desc.endPoint.get(0),
                    asc.startPoint.get(0), asc.endPoint.get(0)
                )
                val ys = listOf(
                    desc.startPoint.get(1), desc.endPoint.get(1),
                    asc.startPoint.get(1), asc.endPoint.get(1)
                )
                chars.add(
                    CharBox(
                        t,
                        xs.minOrNull() ?: 0f, xs.maxOrNull() ?: 0f,
                        ys.minOrNull() ?: 0f, ys.maxOrNull() ?: 0f,
                        base.startPoint.get(0), base.startPoint.get(1),
                        ci.font, ci.fontSize,
                        runCatching { ci.fillColor }.getOrNull()
                    )
                )
            }
        }
        override fun getSupportedEvents(): MutableSet<EventType> = mutableSetOf(EventType.RENDER_TEXT)
    }

    /**
     * Locate [oldText] in a page's character stream, returning [start, endExclusive) index ranges
     * into [chars]. Tries progressively more forgiving matching so a phrase still matches even when
     * the PDF encodes spacing differently from the logical text (the usual reason an "exact" edit
     * silently fails):
     *   1. exact substring
     *   2. whitespace-collapsed (runs of spaces/newlines treated as one space, edges trimmed)
     *   3. whitespace-ignored (all spacing removed on both sides)
     * Matching is also case-insensitive as a final fallback.
     */
    private fun findCharRanges(chars: List<CharBox>, oldText: String): List<Pair<Int, Int>> {
        // 1. exact
        rangesFrom(chars, oldText, removeAll = false, exact = true, ignoreCase = false)
            .let { if (it.isNotEmpty()) return it }
        // 2. whitespace-collapsed
        rangesFrom(chars, oldText, removeAll = false, exact = false, ignoreCase = false)
            .let { if (it.isNotEmpty()) return it }
        // 3. whitespace-ignored
        rangesFrom(chars, oldText, removeAll = true, exact = false, ignoreCase = false)
            .let { if (it.isNotEmpty()) return it }
        // 4. + case-insensitive
        rangesFrom(chars, oldText, removeAll = false, exact = false, ignoreCase = true)
            .let { if (it.isNotEmpty()) return it }
        return rangesFrom(chars, oldText, removeAll = true, exact = false, ignoreCase = true)
    }

    private fun rangesFrom(
        chars: List<CharBox>, oldText: String, removeAll: Boolean, exact: Boolean, ignoreCase: Boolean
    ): List<Pair<Int, Int>> {
        // Build a normalized string parallel to [chars] with per-position original index bounds.
        val sb = StringBuilder()
        val startIdx = ArrayList<Int>()
        val endIdx = ArrayList<Int>()
        var i = 0
        while (i < chars.size) {
            val c = chars[i].ch
            val isWs = c.isEmpty() || c.all { it.isWhitespace() }
            if (isWs && !exact) {
                if (removeAll) { i++; continue }
                val runStart = i
                while (i < chars.size && (chars[i].ch.isEmpty() || chars[i].ch.all { it.isWhitespace() })) i++
                sb.append(' '); startIdx.add(runStart); endIdx.add(i)
            } else {
                sb.append(c); startIdx.add(i); endIdx.add(i + 1); i++
            }
        }
        val needle = when {
            exact -> oldText
            removeAll -> oldText.filterNot { it.isWhitespace() }
            else -> oldText.trim().replace(Regex("\\s+"), " ")
        }
        if (needle.isEmpty()) return emptyList()
        val hay = if (ignoreCase) sb.toString().lowercase() else sb.toString()
        val find = if (ignoreCase) needle.lowercase() else needle
        val ranges = ArrayList<Pair<Int, Int>>()
        var from = 0
        while (true) {
            val ni = hay.indexOf(find, from)
            if (ni < 0) break
            val ne = ni + find.length
            if (ne - 1 < endIdx.size && ni < startIdx.size) ranges.add(startIdx[ni] to endIdx[ne - 1])
            from = ne
        }
        return ranges
    }

    private fun replaceTextInPdf(file: File, oldText: String, newText: String): Int {
        val tempFile = File(file.parent, "_tmp_edit_${file.name}")
        file.copyTo(tempFile, overwrite = true)
        var replacements = 0
        val reader = PdfReader(tempFile.absolutePath)
        val writer = PdfWriter(FileOutputStream(file))
        val pdfDoc = PdfDocument(reader, writer)
        try {
            val helvetica = PdfFontFactory.createFont(StandardFonts.HELVETICA)
            for (pageNum in 1..pdfDoc.numberOfPages) {
                val page = pdfDoc.getPage(pageNum)
                val collector = CharCollector()
                PdfCanvasProcessor(collector).processPageContent(page)
                val chars = collector.chars
                if (chars.isEmpty()) continue

                val ranges = findCharRanges(chars, oldText)
                if (ranges.isEmpty()) continue

                val canvas = PdfCanvas(page)
                for ((rStart, rEnd) in ranges) {
                    val matched = chars.subList(rStart, rEnd)
                    if (matched.isEmpty()) continue
                    val left = matched.minOf { it.left }
                    val right = matched.maxOf { it.right }
                    val bottom = matched.minOf { it.bottom }
                    val top = matched.maxOf { it.top }
                    val first = matched.first()

                    // Cover the old glyphs with a white box matching their bounds.
                    canvas.saveState()
                    canvas.setFillColor(ColorConstants.WHITE)
                    canvas.rectangle(
                        (left - 0.5).toDouble(),
                        (bottom - 1.0).toDouble(),
                        (right - left + 1.0).toDouble(),
                        (top - bottom + 1.5).toDouble()
                    )
                    canvas.fill()
                    canvas.restoreState()

                    // Redraw the new text in the SAME font/size/colour at the same baseline.
                    canvas.saveState()
                    canvas.beginText()
                    var usedFont = first.font
                    try {
                        canvas.setFontAndSize(first.font, first.size.coerceAtLeast(1f))
                    } catch (_: Exception) {
                        usedFont = helvetica
                        canvas.setFontAndSize(helvetica, first.size.coerceAtLeast(1f))
                    }
                    first.color?.let { runCatching { canvas.setFillColor(it) } }
                    canvas.moveText(first.startX.toDouble(), first.baselineY.toDouble())
                    try {
                        canvas.showText(newText)
                    } catch (_: Exception) {
                        // Embedded subset font may lack some glyphs — fall back to Helvetica.
                        if (usedFont != helvetica) canvas.setFontAndSize(helvetica, first.size.coerceAtLeast(1f))
                        runCatching { canvas.showText(newText) }
                    }
                    canvas.endText()
                    canvas.restoreState()
                    replacements++
                }
            }
        } finally {
            pdfDoc.close()
            tempFile.delete()
        }
        return replacements
    }

    // ---------- Add page / image ----------

    private fun appendPageToPdf(file: File, content: String) {
        val tempFile = File(file.parent, "_tmp_addpage_${file.name}")
        file.copyTo(tempFile, overwrite = true)
        val reader = PdfReader(tempFile.absolutePath)
        val writer = PdfWriter(FileOutputStream(file))
        val pdfDoc = PdfDocument(reader, writer)
        val document = Document(pdfDoc)
        try {
            document.add(AreaBreak())
            val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
            for (line in content.split("\n")) {
                document.add(Paragraph(line).setFont(font).setFontSize(11f))
            }
        } finally {
            document.close()
            tempFile.delete()
        }
    }

    private fun insertImageIntoPdf(file: File, imgFile: File, pageNum: Int) {
        val tempFile = File(file.parent, "_tmp_img_${file.name}")
        file.copyTo(tempFile, overwrite = true)
        val reader = PdfReader(tempFile.absolutePath)
        val writer = PdfWriter(FileOutputStream(file))
        val pdfDoc = PdfDocument(reader, writer)
        try {
            val targetPage = if (pageNum in 1..pdfDoc.numberOfPages) pageNum else pdfDoc.numberOfPages
            val page = pdfDoc.getPage(targetPage)
            val imgData = ImageDataFactory.create(imgFile.absolutePath)
            val canvas = PdfCanvas(page)
            val pageSize = page.pageSize
            val imgWidth = minOf(imgData.width, pageSize.width - 100f)
            val imgHeight = imgData.height * (imgWidth / imgData.width)
            val x = (pageSize.width - imgWidth) / 2
            val y = pageSize.height / 2 - imgHeight / 2
            canvas.addImageFittedIntoRectangle(
                imgData,
                com.itextpdf.kernel.geom.Rectangle(x, y, imgWidth, imgHeight),
                false
            )
        } finally {
            pdfDoc.close()
            tempFile.delete()
        }
    }

    // ---------- Charts ----------

    private fun renderChartToPdf(outFile: File, chartType: String, dataJson: String) {
        val writer = PdfWriter(FileOutputStream(outFile))
        val pdfDoc = PdfDocument(writer)
        val document = Document(pdfDoc, PageSize.A4)
        document.setMargins(50f, 50f, 50f, 50f)
        val font = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        val boldFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)

        val labels = mutableListOf<String>()
        val values = mutableListOf<Float>()
        var title = "Chart"
        try {
            val labelsMatch = Regex("\"labels\"\\s*:\\s*\\[([^]]+)]").find(dataJson)
            val valuesMatch = Regex("\"values\"\\s*:\\s*\\[([^]]+)]").find(dataJson)
            val titleMatch = Regex("\"title\"\\s*:\\s*\"([^\"]+)\"").find(dataJson)
            labelsMatch?.groupValues?.get(1)?.split(",")?.forEach { labels.add(it.trim().trim('"')) }
            valuesMatch?.groupValues?.get(1)?.split(",")?.forEach { values.add(it.trim().toFloatOrNull() ?: 0f) }
            titleMatch?.groupValues?.get(1)?.let { title = it }
        } catch (_: Exception) {}

        if (labels.isEmpty() || values.isEmpty()) {
            document.add(Paragraph("Error: Could not parse chart data").setFont(font))
            document.close()
            return
        }

        document.add(Paragraph(title).setFont(boldFont).setFontSize(16f)
            .setTextAlignment(TextAlignment.CENTER).setMarginBottom(20f))

        val page = pdfDoc.getPage(1)
        val canvas = PdfCanvas(page)
        val pageSize = page.pageSize
        val maxVal = values.maxOrNull() ?: 1f
        val colors = listOf(
            DeviceRgb(55, 60, 75), DeviceRgb(200, 70, 60),
            DeviceRgb(210, 160, 40), DeviceRgb(60, 150, 90),
            DeviceRgb(130, 90, 200), DeviceRgb(200, 130, 50)
        )

        when (chartType.lowercase()) {
            "pie" -> {
                val centerX = pageSize.width / 2
                val centerY = 400f
                val radius = 120f
                val total = values.sum()
                var startAngle = 0.0
                for (idx in values.indices) {
                    val sweepAngle = (values[idx] / total) * 360.0
                    val color = colors[idx % colors.size]
                    canvas.saveState()
                    canvas.setFillColor(color)
                    canvas.moveTo(centerX.toDouble(), centerY.toDouble())
                    val steps = 40
                    for (s in 0..steps) {
                        val angle = Math.toRadians(startAngle + sweepAngle * s / steps)
                        val px = centerX + radius * Math.cos(angle).toFloat()
                        val py = centerY + radius * Math.sin(angle).toFloat()
                        canvas.lineTo(px.toDouble(), py.toDouble())
                    }
                    canvas.lineTo(centerX.toDouble(), centerY.toDouble())
                    canvas.fill()
                    canvas.restoreState()

                    val legendY = 220f - idx * 18f
                    canvas.saveState(); canvas.setFillColor(color)
                    canvas.rectangle(80.0, legendY.toDouble(), 12.0, 12.0); canvas.fill(); canvas.restoreState()
                    canvas.beginText(); canvas.setFontAndSize(font, 10f)
                    canvas.moveText(96.0, legendY.toDouble())
                    canvas.showText("${labels.getOrElse(idx) { "" }}: ${values[idx].toInt()}")
                    canvas.endText()
                    startAngle += sweepAngle
                }
            }
            "line" -> {
                val chartLeft = 80f; val chartBottom = 200f
                val chartWidth = pageSize.width - 160f; val chartHeight = 300f
                canvas.setStrokeColor(ColorConstants.BLACK); canvas.setLineWidth(1f)
                canvas.moveTo(chartLeft.toDouble(), chartBottom.toDouble())
                canvas.lineTo(chartLeft.toDouble(), (chartBottom + chartHeight).toDouble())
                canvas.moveTo(chartLeft.toDouble(), chartBottom.toDouble())
                canvas.lineTo((chartLeft + chartWidth).toDouble(), chartBottom.toDouble())
                canvas.stroke()
                canvas.saveState(); canvas.setStrokeColor(DeviceRgb(55, 60, 75)); canvas.setLineWidth(2f)
                val stepX = chartWidth / (values.size - 1).coerceAtLeast(1)
                for (idx in values.indices) {
                    val x = chartLeft + idx * stepX
                    val y = chartBottom + (values[idx] / maxVal) * chartHeight * 0.9f
                    if (idx == 0) canvas.moveTo(x.toDouble(), y.toDouble()) else canvas.lineTo(x.toDouble(), y.toDouble())
                }
                canvas.stroke(); canvas.restoreState()
                for (idx in values.indices) {
                    val x = chartLeft + idx * stepX
                    val y = chartBottom + (values[idx] / maxVal) * chartHeight * 0.9f
                    canvas.saveState(); canvas.setFillColor(DeviceRgb(55, 60, 75))
                    canvas.circle(x.toDouble(), y.toDouble(), 3.0); canvas.fill(); canvas.restoreState()
                    canvas.beginText(); canvas.setFontAndSize(font, 8f)
                    canvas.moveText((x - 5).toDouble(), (chartBottom - 15).toDouble())
                    canvas.showText(labels.getOrElse(idx) { "" }); canvas.endText()
                }
            }
            else -> { // bar
                val chartLeft = 80f; val chartBottom = 200f
                val chartWidth = pageSize.width - 160f; val chartHeight = 300f
                val barWidth = chartWidth / (labels.size * 2f)
                canvas.setStrokeColor(ColorConstants.BLACK); canvas.setLineWidth(1f)
                canvas.moveTo(chartLeft.toDouble(), chartBottom.toDouble())
                canvas.lineTo(chartLeft.toDouble(), (chartBottom + chartHeight).toDouble())
                canvas.moveTo(chartLeft.toDouble(), chartBottom.toDouble())
                canvas.lineTo((chartLeft + chartWidth).toDouble(), chartBottom.toDouble())
                canvas.stroke()
                for (idx in values.indices) {
                    val barHeight = (values[idx] / maxVal) * chartHeight * 0.9f
                    val x = chartLeft + (idx * 2 + 1) * barWidth
                    val color = colors[idx % colors.size]
                    canvas.saveState(); canvas.setFillColor(color)
                    canvas.rectangle(x.toDouble(), chartBottom.toDouble(), barWidth.toDouble(), barHeight.toDouble())
                    canvas.fill(); canvas.restoreState()
                    canvas.beginText(); canvas.setFontAndSize(font, 9f)
                    canvas.moveText((x + barWidth / 4).toDouble(), (chartBottom - 15).toDouble())
                    canvas.showText(labels.getOrElse(idx) { "" }); canvas.endText()
                    canvas.beginText(); canvas.setFontAndSize(font, 9f)
                    canvas.moveText((x + barWidth / 4).toDouble(), (chartBottom + barHeight + 5).toDouble())
                    canvas.showText(values[idx].toInt().toString()); canvas.endText()
                }
            }
        }
        document.close()
    }

    // ---------- Merge / split / read / form ----------

    private fun mergePdfFiles(files: List<File>, output: File): Int {
        val writer = PdfWriter(FileOutputStream(output))
        val pdfDoc = PdfDocument(writer)
        val merger = PdfMerger(pdfDoc)
        var totalPages = 0
        try {
            for (file in files) {
                val srcDoc = PdfDocument(PdfReader(file.absolutePath))
                merger.merge(srcDoc, 1, srcDoc.numberOfPages)
                totalPages += srcDoc.numberOfPages
                srcDoc.close()
            }
        } finally {
            pdfDoc.close()
        }
        return totalPages
    }

    private fun splitPdfPages(inFile: File, pages: String, outFile: File): Int {
        val pageNumbers = parsePageRanges(pages)
        val srcDoc = PdfDocument(PdfReader(inFile.absolutePath))
        val destDoc = PdfDocument(PdfWriter(FileOutputStream(outFile)))
        var n = 0
        try {
            for (pageNum in pageNumbers) {
                if (pageNum in 1..srcDoc.numberOfPages) {
                    srcDoc.copyPagesTo(pageNum, pageNum, destDoc)
                    n++
                }
            }
        } finally {
            destDoc.close()
            srcDoc.close()
        }
        return n
    }

    private fun parsePageRanges(pages: String): List<Int> {
        val result = mutableListOf<Int>()
        for (part in pages.split(",")) {
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val bounds = trimmed.split("-")
                val start = bounds[0].trim().toIntOrNull() ?: continue
                val end = bounds[1].trim().toIntOrNull() ?: continue
                result.addAll(start..end)
            } else {
                trimmed.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result.distinct().sorted()
    }

    private fun extractTextFromPdf(file: File): String {
        val pdfDoc = PdfDocument(PdfReader(file.absolutePath))
        val sb = StringBuilder()
        try {
            for (pageNum in 1..pdfDoc.numberOfPages) {
                sb.append("--- Page $pageNum ---\n")
                val text = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(pageNum), LocationTextExtractionStrategy())
                sb.append(text).append("\n\n")
            }
        } finally {
            pdfDoc.close()
        }
        return sb.toString().take(8000)
    }

    private fun fillPdfForm(file: File, fieldsJson: String) {
        val tempFile = File(file.parent, "_tmp_form_${file.name}")
        file.copyTo(tempFile, overwrite = true)
        val pdfDoc = PdfDocument(PdfReader(tempFile.absolutePath), PdfWriter(FileOutputStream(file)))
        try {
            val form = PdfAcroForm.getAcroForm(pdfDoc, false)
            if (form != null) {
                val fieldRegex = Regex("\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\"")
                for (match in fieldRegex.findAll(fieldsJson)) {
                    form.getField(match.groupValues[1])?.setValue(match.groupValues[2])
                }
                form.flattenFields()
            }
        } finally {
            pdfDoc.close()
            tempFile.delete()
        }
    }

    // ---------- Compression (quality up/down) ----------

    private fun compressPdf(inFile: File, outFile: File, scale: Float, jpegQ: Int, downscale: Boolean): Int {
        val reader = PdfReader(inFile.absolutePath)
        val writer = PdfWriter(FileOutputStream(outFile),
            WriterProperties().setFullCompressionMode(true).setCompressionLevel(9))
        val doc = PdfDocument(reader, writer)
        var touched = 0
        try {
            if (downscale) {
                for (p in 1..doc.numberOfPages) {
                    val resources = doc.getPage(p).pdfObject.getAsDictionary(PdfName.Resources) ?: continue
                    val xobjects = resources.getAsDictionary(PdfName.XObject) ?: continue
                    for (name in xobjects.keySet().toList()) {
                        val stream = xobjects.getAsStream(name) ?: continue
                        if (PdfName.Image != stream.getAsName(PdfName.Subtype)) continue
                        if (stream.get(PdfName.SMask) != null || stream.get(PdfName.Mask) != null) continue
                        // Only safely handle JPEG (DCTDecode) images on Android.
                        val filter = stream.get(PdfName.Filter)
                        val isJpeg = filter == PdfName.DCTDecode ||
                            (filter is PdfArray && filter.contains(PdfName.DCTDecode))
                        if (!isJpeg) continue
                        try {
                            val xobj = PdfImageXObject(stream)
                            if (xobj.width <= 64f || xobj.height <= 64f) continue
                            val imgBytes = xobj.imageBytes
                            val bmp = BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size) ?: continue
                            val nw = (bmp.width * scale).toInt().coerceAtLeast(1)
                            val nh = (bmp.height * scale).toInt().coerceAtLeast(1)
                            val scaled = if (scale < 1f) Bitmap.createScaledBitmap(bmp, nw, nh, true) else bmp
                            val baos = ByteArrayOutputStream()
                            scaled.compress(Bitmap.CompressFormat.JPEG, jpegQ, baos)
                            val jpg = baos.toByteArray()
                            if (jpg.isEmpty()) continue
                            stream.clear()
                            stream.setData(jpg)
                            stream.put(PdfName.Type, PdfName.XObject)
                            stream.put(PdfName.Subtype, PdfName.Image)
                            stream.put(PdfName.Width, PdfNumber(scaled.width))
                            stream.put(PdfName.Height, PdfNumber(scaled.height))
                            stream.put(PdfName.BitsPerComponent, PdfNumber(8))
                            stream.put(PdfName.ColorSpace, PdfName.DeviceRGB)
                            stream.put(PdfName.Filter, PdfName.DCTDecode)
                            touched++
                        } catch (_: Exception) { /* leave this image untouched */ }
                    }
                }
            }
        } finally {
            doc.close()
        }
        return touched
    }

    // ---------- Collage ----------

    private fun buildCollage(outFile: File, files: List<File>, cols: Int) {
        val pdfDoc = PdfDocument(PdfWriter(FileOutputStream(outFile)))
        val document = Document(pdfDoc, PageSize.A4)
        document.setMargins(24f, 24f, 24f, 24f)
        try {
            val table = Table(UnitValue.createPercentArray(cols)).useAllAvailableWidth()
            table.setBorder(Border.NO_BORDER)
            for (f in files) {
                try {
                    val data = ImageDataFactory.create(f.absolutePath)
                    val img = Image(data).setAutoScale(true)
                    table.addCell(Cell().add(img).setPadding(4f).setBorder(Border.NO_BORDER))
                } catch (_: Exception) {
                    table.addCell(Cell().add(Paragraph("(couldn't load ${f.name})").setFontSize(9f)).setBorder(Border.NO_BORDER))
                }
            }
            val rem = (cols - (files.size % cols)) % cols
            repeat(rem) { table.addCell(Cell().setBorder(Border.NO_BORDER)) }
            document.add(table)
        } finally {
            document.close()
        }
    }
}
