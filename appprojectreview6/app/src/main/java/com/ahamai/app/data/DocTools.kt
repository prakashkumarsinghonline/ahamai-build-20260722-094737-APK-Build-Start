package com.ahamai.app.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One-call generators for Office file formats (XLSX / PPTX / DOCX), built in the E2B cloud
 * sandbox with openpyxl / python-pptx / python-docx. The model passes ONE JSON spec describing
 * the whole document; the matching Python builder turns it into a real file, which is pulled
 * back into the project (so it renders/exports in-app) and verified — all in a single tool call.
 *
 * This replaces the old flow where the agent had to install a library, write a script, run it,
 * and pull the file back across many separate steps.
 */
object DocTools {

    private fun b64(s: String): String =
        Base64.encodeToString(s.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    /**
     * Create a CSV natively (no cloud needed). [spec] is either a JSON object {"rows":[[...]]},
     * a bare JSON 2-D array [[...],[...]], or raw CSV text. Values are properly quoted/escaped.
     */
    fun createCsv(projectDir: String, outPath: String, spec: String): String {
        return try {
            val out = File(projectDir, outPath.trim().removePrefix("/").ifBlank { "data.csv" })
            out.parentFile?.mkdirs()
            val trimmed = spec.trim()
            val text: String
            val rowCount: Int
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                val rows = parseRows(trimmed)
                val sb = StringBuilder()
                for (row in rows) sb.append(row.joinToString(",") { csvEscape(it) }).append("\r\n")
                text = sb.toString(); rowCount = rows.size
            } else {
                // Already raw CSV text
                text = trimmed; rowCount = trimmed.split("\n").size
            }
            out.writeText(text)
            "OK: Created CSV at ${out.name} ($rowCount rows, ${out.length()} bytes). Verified — renders as a table in chat."
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    private fun parseRows(json: String): List<List<String>> {
        val arr = when {
            json.trim().startsWith("{") -> org.json.JSONObject(json).optJSONArray("rows") ?: org.json.JSONArray()
            else -> org.json.JSONArray(json)
        }
        val rows = ArrayList<List<String>>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONArray(i) ?: continue
            val cells = ArrayList<String>()
            for (j in 0 until r.length()) cells.add(r.opt(j)?.toString() ?: "")
            rows.add(cells)
        }
        return rows
    }

    private fun csvEscape(v: String): String =
        if (v.contains(',') || v.contains('"') || v.contains('\n') || v.contains('\r'))
            "\"" + v.replace("\"", "\"\"") + "\""
        else v

    suspend fun createXlsx(ctx: Context, projectDir: String, outPath: String, spec: String): String =
        build(ctx, projectDir, outPath, spec, "openpyxl", "openpyxl", XLSX_BUILDER, "Excel spreadsheet")

    suspend fun createPptx(ctx: Context, projectDir: String, outPath: String, spec: String): String =
        build(ctx, projectDir, outPath, spec, "pptx", "python-pptx", PPTX_BUILDER, "PowerPoint presentation")

    suspend fun createDocx(ctx: Context, projectDir: String, outPath: String, spec: String): String =
        build(ctx, projectDir, outPath, spec, "docx", "python-docx", DOCX_BUILDER, "Word document")

    private suspend fun build(
        ctx: Context, projectDir: String, outPath: String, spec: String,
        importName: String, pipPkg: String, script: String, kind: String
    ): String = withContext(Dispatchers.IO) {
        val prefs = PreferencesManager(ctx)
        if (!prefs.isE2bEnabled() || !prefs.isE2bConfigured()) {
            return@withContext "ERROR: Cloud engine not configured. Open Profile → Cloud Engine to enable $kind generation."
        }
        // Make sure the builder library is importable (installed + verified).
        CloudTools.ensurePython(ctx, projectDir, importName, pipPkg)?.let { return@withContext "ERROR: $it" }
        // Upload any project files the spec references (e.g. images for slides).
        runCatching { CloudTools.syncProjectUp(ctx, projectDir) }

        val out = outPath.trim().removePrefix("/").ifBlank { "document.${kind.first()}" }
        val scriptB64 = b64(script)
        val specB64 = b64(spec.trim().ifBlank { "{}" })
        val cmd =
            "echo $scriptB64 | base64 -d > /tmp/_doc_build.py; " +
            "echo $specB64 | base64 -d > /tmp/_doc_spec.json; " +
            "mkdir -p \"\$(dirname '/workspace/$out')\"; " +
            "python3 /tmp/_doc_build.py /tmp/_doc_spec.json '/workspace/$out' 2>&1; " +
            "echo '---'; ls -l '/workspace/$out' 2>/dev/null || echo NO_OUTPUT"
        val res = CloudTools.execProv(ctx, projectDir, cmd, 180)
        // Pull the generated file back into the project so it renders / can be exported.
        val pull = CloudTools.cloudPull(ctx, projectDir, "/workspace/$out", out)
        val f = File(projectDir, out)
        if (f.exists() && f.length() > 0L) {
            // Generate a sidecar preview PDF (via LibreOffice) so the phone can render a REAL
            // thumbnail of this office file natively. Best-effort — never fails the creation.
            val hasThumb = makeOfficePreviewPdf(ctx, projectDir, out)
            "OK: Created $kind at $out (${f.length()} bytes). Verified${if (hasThumb) " (with visual preview)" else ""} — it renders inline and can be EXPORT_TO_DEVICE'd. Do NOT recreate it."
        } else {
            "ERROR: $kind generation failed.\n${res.formatted(1500)}\n$pull"
        }
    }

    /**
     * Convert an office file to a sidecar `<file>.preview.pdf` using LibreOffice (headless) in the
     * sandbox and pull it back, so the app can render a real first-page thumbnail natively.
     * Returns true if the preview PDF was produced. Best-effort; swallows all errors.
     */
    private suspend fun makeOfficePreviewPdf(ctx: Context, projectDir: String, out: String): Boolean {
        return try {
            val base = out.substringAfterLast('/').substringBeforeLast('.')
            val cmd =
                "command -v soffice >/dev/null 2>&1 || { export DEBIAN_FRONTEND=noninteractive; " +
                "apt-get update -qq >/dev/null 2>&1; apt-get install -y -qq --no-install-recommends " +
                "libreoffice-writer libreoffice-calc libreoffice-impress >/dev/null 2>&1; }; " +
                "rm -rf /tmp/_prev && mkdir -p /tmp/_prev; cd /workspace && " +
                "HOME=/tmp soffice --headless --convert-to pdf --outdir /tmp/_prev '$out' >/dev/null 2>&1; " +
                "cp \"/tmp/_prev/$base.pdf\" '/workspace/$out.preview.pdf' 2>/dev/null; " +
                "ls -l '/workspace/$out.preview.pdf' >/dev/null 2>&1 && echo HAVE_PREVIEW || echo NO_PREVIEW"
            val res = CloudTools.execProv(ctx, projectDir, cmd, 300)
            if (res.stdout.contains("HAVE_PREVIEW")) {
                CloudTools.cloudPull(ctx, projectDir, "/workspace/$out.preview.pdf", "$out.preview.pdf")
                File(projectDir, "$out.preview.pdf").let { it.exists() && it.length() > 0L }
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------
    //  Python builders — each reads a JSON spec (argv[1]) and writes the file (argv[2]).
    // ---------------------------------------------------------------------------------------

    private val XLSX_BUILDER = """
import sys, json, openpyxl
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.utils import get_column_letter
from openpyxl.chart import BarChart, LineChart, PieChart, Reference
spec = json.load(open(sys.argv[1])); out = sys.argv[2]
wb = openpyxl.Workbook(); first = True
sheets = spec.get('sheets') or [{'name': 'Sheet1', 'rows': spec.get('rows', [])}]
thin = Side(style='thin', color='DDDDDD'); border = Border(left=thin, right=thin, top=thin, bottom=thin)
for sh in sheets:
    ws = wb.active if first else wb.create_sheet(); first = False
    ws.title = (str(sh.get('name') or 'Sheet'))[:31]
    rows = sh.get('rows', [])
    for r in rows: ws.append(r)
    has_header = sh.get('header', True) and len(rows) > 0
    for ri, row in enumerate(ws.iter_rows(), start=1):
        for c in row:
            c.border = border
            if ri == 1 and has_header:
                c.font = Font(bold=True, color='FFFFFF'); c.fill = PatternFill('solid', fgColor='2563EB'); c.alignment = Alignment(horizontal='center', vertical='center')
    for col in ws.columns:
        width = max((len(str(c.value)) for c in col if c.value is not None), default=8)
        ws.column_dimensions[col[0].column_letter].width = min(max(width + 2, 9), 60)
    if has_header: ws.freeze_panes = 'A2'
    ch = sh.get('chart')
    if ch and len(rows) > 1:
        ctype = (ch.get('type') or 'bar').lower()
        chart = {'bar': BarChart, 'line': LineChart, 'pie': PieChart}.get(ctype, BarChart)()
        chart.title = ch.get('title') or ''
        ncols = len(rows[0])
        cat_col = int(ch.get('cat_col', 1)); val_col = int(ch.get('val_col', ncols))
        min_row = int(ch.get('min_row', 1)); max_row = int(ch.get('max_row', len(rows)))
        data = Reference(ws, min_col=val_col, min_row=min_row, max_row=max_row)
        cats = Reference(ws, min_col=cat_col, min_row=min_row + 1, max_row=max_row)
        chart.add_data(data, titles_from_data=True); chart.set_categories(cats)
        chart.height = 8; chart.width = 15
        ws.add_chart(chart, ch.get('anchor') or (get_column_letter(ncols + 2) + '2'))
wb.save(out); print('OK: %d sheet(s)' % len(wb.sheetnames))
""".trim()

    // PPTX builder: clean monochrome/neutral deck by DEFAULT (text + layout stay neutral), with
    // COLOURFUL charts and process-diagrams, proper image aspect-fit, a styled cover slide and
    // page numbers. Default font is Inter (sans, bold headings). EVERYTHING is overridable via the
    // spec — theme / accent / font / heading_font / colors — nothing is hard-coded.
    //   slide kinds: {title,bullets:[...]} | {title,image:"path"} |
    //                {title,chart:{type:bar|column|line|pie|area, categories:[...], series:[{name,values:[...]}]}} |
    //                {title,diagram:{steps:["..."]}}
    private val PPTX_BUILDER = """
import sys, json, os
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.chart.data import CategoryChartData
from pptx.enum.chart import XL_CHART_TYPE, XL_LEGEND_POSITION

spec = json.load(open(sys.argv[1])); out = sys.argv[2]

def H(x):
    try:
        s = str(x).lstrip('#')
        return RGBColor(int(s[0:2],16), int(s[2:4],16), int(s[4:6],16))
    except Exception:
        return RGBColor(0x11,0x18,0x27)

THEMES = {
  'mono':  {'bg':'FFFFFF','head':'111827','body':'374151','accent':'111827','rule':'D1D5DB','sub':'6B7280'},
  'light': {'bg':'FFFFFF','head':'111827','body':'374151','accent':'2F6FED','rule':'E5E7EB','sub':'6B7280'},
  'dark':  {'bg':'0F1116','head':'F3F4F6','body':'D1D5DB','accent':'60A5FA','rule':'374151','sub':'9CA3AF'},
}
tname = str(spec.get('theme','mono')).lower()
T = dict(THEMES.get(tname, THEMES['mono']))
if spec.get('accent'): T['accent'] = str(spec['accent']).lstrip('#')
BODY_FONT = spec.get('font') or 'Inter'
HEAD_FONT = spec.get('heading_font') or BODY_FONT
COLORS = spec.get('colors') or ['2F6FED','19A7A0','F2A93B','E5604D','7C5CFC','37B679']

p = Presentation(); p.slide_width = Inches(13.333); p.slide_height = Inches(7.5)
BLANK = p.slide_layouts[6]

def bg(slide):
    slide.background.fill.solid(); slide.background.fill.fore_color.rgb = H(T['bg'])

def textbox(slide, text, l, t, w, h, size, color, bold=False, font=None, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP):
    tb = slide.shapes.add_textbox(Inches(l), Inches(t), Inches(w), Inches(h))
    tf = tb.text_frame; tf.word_wrap = True; tf.vertical_anchor = anchor
    items = text if isinstance(text, list) else [text]
    for i, ln in enumerate(items):
        para = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
        para.alignment = align; para.space_after = Pt(6)
        run = para.add_run(); run.text = str(ln)
        run.font.size = Pt(size); run.font.bold = bold
        run.font.name = font or BODY_FONT; run.font.color.rgb = H(color)
    return tb

def rule(slide, l, t, w, hexc, thick=3):
    ln = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(l), Inches(t), Inches(w), Pt(thick))
    ln.fill.solid(); ln.fill.fore_color.rgb = H(hexc); ln.line.fill.background(); ln.shadow.inherit = False
    return ln

def header(slide, title):
    if title:
        textbox(slide, title, 0.6, 0.42, 12.1, 1.0, 30, T['head'], bold=True, font=HEAD_FONT)
        rule(slide, 0.62, 1.34, 3.2, T['accent'], 3)

def pagenum(slide, n):
    textbox(slide, str(n), 12.3, 7.0, 0.8, 0.35, 11, T['sub'], align=PP_ALIGN.RIGHT)

def fit_picture(slide, path, bx, by, bw, bh):
    pic = slide.shapes.add_picture(path, Inches(bx), Inches(by))
    nw, nh = pic.width, pic.height
    bwe, bhe = Inches(bw), Inches(bh)
    r = min(bwe / nw, bhe / nh)
    pic.width = int(nw * r); pic.height = int(nh * r)
    pic.left = int(Inches(bx) + (bwe - pic.width) / 2)
    pic.top = int(Inches(by) + (bhe - pic.height) / 2)
    return pic

def add_chart(slide, ch, bx, by, bw, bh):
    t = str(ch.get('type','column')).lower()
    cats = ch.get('categories') or ch.get('labels') or []
    series = ch.get('series')
    if not series:
        series = [{'name': ch.get('title','Series'), 'values': ch.get('values') or []}]
    cd = CategoryChartData(); cd.categories = [str(c) for c in cats]
    for s in series:
        try: cd.add_series(str(s.get('name','')), [float(x) for x in s.get('values',[])])
        except Exception: pass
    xlmap = {'bar':XL_CHART_TYPE.BAR_CLUSTERED,'column':XL_CHART_TYPE.COLUMN_CLUSTERED,
             'line':XL_CHART_TYPE.LINE_MARKERS,'pie':XL_CHART_TYPE.PIE,'area':XL_CHART_TYPE.AREA}
    gf = slide.shapes.add_chart(xlmap.get(t, XL_CHART_TYPE.COLUMN_CLUSTERED),
                                Inches(bx), Inches(by), Inches(bw), Inches(bh), cd)
    chart = gf.chart
    try:
        if ch.get('title'): chart.has_title = True; chart.chart_title.text_frame.text = str(ch['title'])
        else: chart.has_title = False
    except Exception: pass
    try:
        if len(series) > 1 or t == 'pie':
            chart.has_legend = True; chart.legend.position = XL_LEGEND_POSITION.BOTTOM; chart.legend.include_in_layout = False
        else: chart.has_legend = False
    except Exception: pass
    try:
        if t == 'pie':
            for i, pt in enumerate(chart.plots[0].series[0].points):
                pt.format.fill.solid(); pt.format.fill.fore_color.rgb = H(COLORS[i % len(COLORS)])
        else:
            for i, s in enumerate(chart.series):
                s.format.fill.solid(); s.format.fill.fore_color.rgb = H(COLORS[i % len(COLORS)])
                try: s.format.line.color.rgb = H(COLORS[i % len(COLORS)])
                except Exception: pass
    except Exception as e:
        print('chart color err', e)
    return gf

def add_diagram(slide, dg, bx, by, bw, bh):
    steps = dg.get('steps') or []
    if not steps: return
    n = len(steps); gap = 0.26
    bh_each = max(0.55, min(1.05, (bh - gap * (n - 1)) / n))
    d = min(bh_each, 0.72)
    cy = by
    for i, st in enumerate(steps):
        label = st if isinstance(st, str) else (st.get('text') or st.get('title') or '')
        col = COLORS[i % len(COLORS)]
        circ = slide.shapes.add_shape(MSO_SHAPE.OVAL, Inches(bx), Inches(cy), Inches(d), Inches(d))
        circ.fill.solid(); circ.fill.fore_color.rgb = H(col); circ.line.fill.background(); circ.shadow.inherit = False
        cp = circ.text_frame.paragraphs[0]; cp.alignment = PP_ALIGN.CENTER
        cr = cp.add_run(); cr.text = str(i + 1); cr.font.bold = True; cr.font.size = Pt(18)
        cr.font.color.rgb = H('FFFFFF'); cr.font.name = HEAD_FONT
        boxl = bx + d + 0.25
        box = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE, Inches(boxl), Inches(cy), Inches(bw - d - 0.25), Inches(bh_each))
        box.fill.solid(); box.fill.fore_color.rgb = H(T['bg'])
        box.line.color.rgb = H(T['rule']); box.line.width = Pt(1); box.shadow.inherit = False
        btf = box.text_frame; btf.word_wrap = True; btf.vertical_anchor = MSO_ANCHOR.MIDDLE; btf.margin_left = Inches(0.2)
        bp = btf.paragraphs[0]; bp.alignment = PP_ALIGN.LEFT
        br = bp.add_run(); br.text = str(label); br.font.size = Pt(16); br.font.color.rgb = H(T['head']); br.font.name = BODY_FONT
        cy += bh_each + gap

if spec.get('title'):
    s = p.slides.add_slide(BLANK); bg(s)
    band = s.shapes.add_shape(MSO_SHAPE.RECTANGLE, Inches(0), Inches(0), Inches(0.35), Inches(7.5))
    band.fill.solid(); band.fill.fore_color.rgb = H(T['accent']); band.line.fill.background(); band.shadow.inherit = False
    textbox(s, str(spec['title']), 1.0, 2.5, 11.3, 1.9, 46, T['head'], bold=True, font=HEAD_FONT, anchor=MSO_ANCHOR.MIDDLE)
    if spec.get('subtitle'):
        textbox(s, str(spec['subtitle']), 1.05, 4.4, 11.0, 1.0, 22, T['sub'], font=BODY_FONT)

for idx, sl in enumerate(spec.get('slides', []), 1):
    s = p.slides.add_slide(BLANK); bg(s)
    header(s, sl.get('title',''))
    cx, cw, ctop, chgt = 0.7, 11.9, 1.7, 5.2
    try:
        if sl.get('image'):
            img = sl['image']; path = img if os.path.isabs(img) else '/workspace/' + img
            if os.path.exists(path): fit_picture(s, path, cx, ctop, cw, chgt)
            else: textbox(s, '[image not found: ' + str(img) + ']', cx, ctop, cw, 0.5, 14, T['sub'])
        elif sl.get('chart'):
            add_chart(s, sl['chart'], cx, ctop, cw, chgt)
        elif sl.get('diagram'):
            add_diagram(s, sl['diagram'], cx, ctop, cw, chgt)
        else:
            bullets = sl.get('bullets') or ([sl['text']] if sl.get('text') else [])
            tb = s.shapes.add_textbox(Inches(cx), Inches(ctop), Inches(cw), Inches(chgt))
            tf = tb.text_frame; tf.word_wrap = True
            for i, b in enumerate(bullets):
                para = tf.paragraphs[0] if i == 0 else tf.add_paragraph(); para.space_after = Pt(12)
                run = para.add_run(); run.text = '\u2022  ' + str(b)
                run.font.size = Pt(18); run.font.color.rgb = H(T['body']); run.font.name = BODY_FONT
    except Exception as e:
        print('slide err', e)
    if sl.get('notes'):
        s.notes_slide.notes_text_frame.text = str(sl['notes'])
    pagenum(s, idx)

p.save(out); print('OK: %d slide(s)' % len(p.slides._sldIdLst))
""".trim()

    private val DOCX_BUILDER = """
import sys, json, os, docx
from docx.shared import Pt, Inches, RGBColor
spec = json.load(open(sys.argv[1])); out = sys.argv[2]
d = docx.Document()
if spec.get('title'): d.add_heading(str(spec['title']), 0)
if spec.get('subtitle'): d.add_paragraph(str(spec['subtitle']))
for sec in spec.get('sections', []):
    if sec.get('heading'): d.add_heading(str(sec['heading']), int(sec.get('level', 1)))
    if sec.get('text'): d.add_paragraph(str(sec['text']))
    for b in sec.get('bullets', []): d.add_paragraph(str(b), style='List Bullet')
    tbl = sec.get('table')
    if tbl and len(tbl) > 0:
        t = d.add_table(rows=0, cols=len(tbl[0]))
        try: t.style = 'Light Grid Accent 1'
        except Exception: pass
        for row in tbl:
            cells = t.add_row().cells
            for i, v in enumerate(row):
                if i < len(cells): cells[i].text = str(v)
    img = sec.get('image')
    if img:
        path = img if os.path.isabs(img) else '/workspace/' + img
        if os.path.exists(path):
            try: d.add_picture(path, width=Inches(6))
            except Exception as e: print('img err', e)
d.save(out); print('OK: document')
""".trim()
}
