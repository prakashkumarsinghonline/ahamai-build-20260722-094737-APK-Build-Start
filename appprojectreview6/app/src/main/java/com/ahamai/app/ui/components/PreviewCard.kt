package com.ahamai.app.ui.components

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.drawToBitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import android.widget.Toast
import coil.compose.AsyncImage
import com.ahamai.app.ui.icons.AdminIcons
import com.ahamai.app.ui.icons.Lucide
import com.ahamai.app.ui.theme.InterFamily
import com.ahamai.app.ui.theme.JetBrainsMonoFamily
import com.ahamai.app.ui.theme.UnicaOneRegular
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Configure an inline preview WebView so the parent LazyColumn can fling past it.
 * Without this, WebView steals vertical gestures and chat/agent scroll feels stuck.
 */
@SuppressLint("ClickableViewAccessibility")
private fun WebView.preferParentScroll() {
    isNestedScrollingEnabled = false
    overScrollMode = android.view.View.OVER_SCROLL_NEVER
    isVerticalScrollBarEnabled = false
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            // Let the Compose list own the gesture unless the user is clearly
            // interacting with the page (tap / short drag stays on WebView).
            MotionEvent.ACTION_DOWN -> {
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_MOVE -> {
                // Prefer parent list scroll for vertical drags.
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        false // still deliver events to WebView for clicks / JS
    }
}

/**
 * Live preview card shown inline in the agent log when a tool emits a preview marker.
 * Three flavours: web app (WebView of a project file), diagram (Mermaid), chart (Chart.js).
 *
 * For diagrams and charts: renders INLINE (no separate card background/border/title) like
 * regular markdown content, with a save button overlay.
 */
@Composable
fun PreviewCard(
    kind: String,
    payload: String,
    projectDir: String,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF6F8FA)
    val border = if (isDark) Color(0xFFFFFFFF) else Color(0xFF999999)

    // Diagram / chart: flexible-height WebView + PNG-only save (no HTML fallback).
    if (kind == "diagram" || kind == "chart") {
        Box(modifier = modifier.fillMaxWidth()) {
            when (kind) {
                "diagram" -> FlexibleMermaidPreview(payload, isDark)
                "chart" -> FlexibleChartPreview(payload, isDark)
            }
            DiagramChartSaveButton(
                kind = kind,
                payload = payload,
                isDark = isDark,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
        return
    }

    // Web app preview keeps the separate card style.
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Live Web Preview",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFamily,
                    color = if (isDark) Color.White else Color(0xFF111418)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    payload,
                    fontSize = 11.sp,
                    fontFamily = JetBrainsMonoFamily,
                    color = if (isDark) Color(0xFF8B95A1) else Color(0xFF6B7280),
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(8.dp))
            WebAppPreview(projectDir, payload, isDark)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebAppPreview(projectDir: String, relPath: String, isDark: Boolean) {
    val target = File(projectDir, relPath)
    var webView by remember { mutableStateOf<WebView?>(null) }
    val projectRoot = remember(target.absolutePath) { target.parentFile ?: File(projectDir) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF1A1D23) else Color.White))
    {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.allowFileAccess = true
                    settings.allowContentAccess = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    preferParentScroll()
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            // Keep navigation inside the preview (no external browser launches).
                            view.loadUrl(request.url.toString())
                            return true
                        }
                    }
                    webChromeClient = WebChromeClient()
                    // Use file:// URL so relative paths in HTML resolve against the project root.
                    loadUrl("file://${target.absolutePath}")
                    webView = this
                }
            },
            update = { wv ->
                // Reload only if the path actually changed.
                if (wv.url != "file://${target.absolutePath}") {
                    wv.loadUrl("file://${target.absolutePath}")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Refresh button overlay (bottom-right) for convenience.
        Row(
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            SmallFloatingActionButton(
                onClick = { webView?.reload() },
                containerColor = if (isDark) Color(0xFF2A2F36) else Color.White,
                contentColor = if (isDark) Color.White else Color(0xFF111418)
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Reload preview", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun MermaidPreview(mermaidSource: String, isDark: Boolean) {
    val html = buildMermaidHtml(mermaidSource, isDark)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 500.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF1A1D23) else Color.White)
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
                    preferParentScroll()
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadDataWithBaseURL("https://mermaid/", html, "text/html", "utf-8", null)
                }
            },
            update = { wv ->
                wv.loadDataWithBaseURL("https://mermaid/", html, "text/html", "utf-8", null)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChartPreview(payload: String, isDark: Boolean) {
    // payload format: "<chartType>|<title>\n<jsonData>" OR just raw JSON (from chat ```chart blocks)
    val firstNewline = payload.indexOf('\n')
    val header: String
    val data: String
    // If the payload starts with [ or { it's raw JSON (no header line).
    val trimmed = payload.trimStart()
    if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
        header = "bar|"
        data = payload
    } else {
        header = if (firstNewline >= 0) payload.substring(0, firstNewline) else payload
        data = if (firstNewline >= 0) payload.substring(firstNewline + 1) else "[]"
    }
    val parts = header.split("|", limit = 2)
    val chartType = parts.getOrNull(0)?.ifBlank { "bar" } ?: "bar"
    val title = parts.getOrNull(1) ?: ""

    val html = buildChartHtml(chartType, title, data, isDark)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF1A1D23) else Color.White)
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
                    preferParentScroll()
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                    loadDataWithBaseURL("https://charts/", html, "text/html", "utf-8", null)
                }
            },
            update = { wv ->
                wv.loadDataWithBaseURL("https://charts/", html, "text/html", "utf-8", null)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun buildMermaidHtml(source: String, isDark: Boolean): String {
    val bg = if (isDark) "#1A1D23" else "#FFFFFF"
    val fg = if (isDark) "#E8EAED" else "#111418"
    val escaped = source
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("\$", "\\\$")
    return """
<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
<style>
  body { margin:0; padding:16px; background:$bg; color:$fg; font-family: -apple-system, system-ui, sans-serif; }
  #d { background:transparent; }
  .mermaid { font-size:14px; }
</style>
</head>
<body>
<div class="mermaid" id="d">$escaped</div>
<script>
  try {
    mermaid.initialize({ startOnLoad: true, theme: '${if (isDark) "dark" else "default"}', securityLevel: 'loose' });
  } catch (e) { document.body.innerText = 'Diagram error: ' + e.message; }
</script>
</body></html>
    """.trimIndent()
}

private fun buildChartHtml(type: String, title: String, jsonData: String, isDark: Boolean): String {
    val bg = if (isDark) "#1A1D23" else "#FFFFFF"
    val fg = if (isDark) "#E8EAED" else "#111418"
    val grid = if (isDark) "rgba(255,255,255,0.06)" else "rgba(0,0,0,0.06)"
    val muted = if (isDark) "#9AA0A6" else "#6B7280"
    val accent = "#6366F1"
    val accentLight = if (isDark) "rgba(99,102,241,0.2)" else "rgba(99,102,241,0.12)"
    val palette = "['#6366F1','#22C55E','#F59E0B','#EF4444','#A855F7','#06B6D4','#EC4899','#14B8A6']"
    val escaped = jsonData
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("\$", "\\\$")
    val titleHtml = if (title.isBlank()) "" else """<div style="text-align:center;padding:0 0 12px 0"><span style="font-size:15px;font-weight:600;color:$fg">$title</span></div>"""
    return """
<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<script src="https://cdn.jsdelivr.net/npm/chart.js@4/dist/chart.umd.min.js"></script>
<style>
  * { margin:0; padding:0; box-sizing:border-box; }
  body { 
    background:$bg; color:$fg; 
    font-family: 'Inter', -apple-system, system-ui, -apple-system, sans-serif;
    display:flex; align-items:center; justify-content:center;
    min-height:100vh; padding:16px;
  }
  .chart-wrap {
    width:100%; max-width:600px;
    background:$bg;
    border-radius:12px;
    padding:20px 16px 12px 16px;
  }
  canvas { width:100% !important; height:auto !important; }
</style>
</head>
<body>
<div class="chart-wrap">
$titleHtml
<canvas id="c"></canvas>
</div>
<script>
  try {
    const raw = `$escaped`;
    let parsed = JSON.parse(raw);
    let labels, datasets;
    if (Array.isArray(parsed)) {
      labels = parsed.map(x => x.label || x.x || '');
      datasets = [{ label: (parsed[0] && parsed[0].label) || 'Value', data: parsed.map(x => x.value || x.y || 0) }];
    } else if (parsed.labels && parsed.datasets) {
      labels = parsed.labels;
      datasets = parsed.datasets;
    } else {
      labels = Object.keys(parsed);
      datasets = [{ label:'Value', data: Object.values(parsed) }];
    }
    const t = ${"\""}$type${"\""}.toLowerCase();
    const chartType = (t === 'doughnut') ? 'doughnut' : (t === 'area' ? 'line' : (t === 'radar' ? 'radar' : t));
    const isArea = (t === 'area');
    const pal = $palette;
    const isPolar = t==='doughnut'||t==='pie'||t==='radar';
    new Chart(document.getElementById('c'), {
      type: chartType,
      data: { 
        labels: labels, 
        datasets: datasets.map((d,i) => ({
          ...d,
          fill: isArea || isPolar,
          backgroundColor: isPolar
            ? labels.map((_,j) => pal[j % pal.length])
            : (isArea ? '$accentLight' : pal[0] + 'CC'),
          borderColor: isPolar ? '#ffffff30' : pal[0],
          borderWidth: isPolar ? 2 : 2,
          tension: 0.3,
          pointBackgroundColor: pal[0],
          pointBorderColor: '$bg',
          pointRadius: 4,
          pointHoverRadius: 6,
        })) 
      },
      options: {
        responsive: true,
        maintainAspectRatio: true,
        aspectRatio: 1.6,
        interaction: { intersect: false, mode: 'index' },
        plugins: { 
          legend: { 
            display: isPolar || datasets.length > 1,
            position: 'bottom',
            labels: { color: '$fg', padding: 12, font: { size: 11, family: 'Inter, system-ui' }, usePointStyle: true, pointStyle: 'circle' } 
          },
          tooltip: {
            backgroundColor: '${if (isDark) "#2A2D35" else "#1C1E26"}',
            titleColor: '#fff',
            bodyColor: '#cbd5e1',
            cornerRadius: 8,
            padding: 10,
            boxPadding: 6,
            usePointStyle: true,
            callbacks: {
              label: function(ctx) {
                let v = ctx.parsed.y ?? ctx.parsed.r ?? ctx.parsed;
                if (typeof v === 'number') { 
                  if (Number.isInteger(v)) return ctx.dataset.label + ': ' + v;
                  return ctx.dataset.label + ': ' + v.toFixed(1);
                }
                return ctx.dataset.label + ': ' + v;
              }
            }
          }
        },
        scales: isPolar ? {} : {
          x: { 
            ticks: { color: '$muted', font: { size: 10, family: 'Inter, system-ui' }, maxRotation: 45 },
            grid: { color: '$grid', drawBorder: false },
            border: { display: false }
          },
          y: { 
            beginAtZero: true,
            ticks: { color: '$muted', font: { size: 10, family: 'Inter, system-ui' }, maxTicksLimit: 6 },
            grid: { color: '$grid', drawBorder: false },
            border: { display: false }
          }
        }
      }
    });
  } catch (e) { 
    document.body.innerHTML = '<div style="padding:20px;text-align:center;color:$muted">⚠ Chart error: ' + e.message + '</div>'; 
  }
</script>
</body></html>
    """.trimIndent()
}


/**
 * Manus-inspired full-screen computer view.
 * Title uses the same Unica One "Aham"+"AI" logo treatment as the chat TopAppBar.
 * Desktop preview expands edge-to-edge (no letterbox); noVNC chrome is stripped via JS.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserLiveView(
    url: String?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    pageUrl: String = "",
    booting: Boolean = false,
    isLandscape: Boolean = true,
    statusTitle: String = "AhamAI is using Browser",
    statusDetail: String = "",
    stepLabel: String = "",
    onClose: () -> Unit = {},
    onToggleOrientation: () -> Unit = {}
) {
    val pageBg = if (isDark) Color(0xFF0C0C0E) else Color(0xFFF4F4F5)
    val ink = if (isDark) Color(0xFFECECEC) else Color(0xFF111113)
    val muted = if (isDark) Color(0xFF8E8E96) else Color(0xFF6B7280)
    val card = if (isDark) Color(0xFF141416) else Color(0xFFFFFFFF)
    val chip = if (isDark) Color(0xFF1E1E22) else Color(0xFFFFFFFF)
    val cream = if (isDark) Color(0xFFEFDCC0) else Color(0xFF7A5F45)
    val liveGreen = Color(0xFF22C55E)
    val track = if (isDark) Color(0xFF2C2C30) else Color(0xFFE4E4E7)

    val isDesktop = remember(url) {
        url?.contains("vnc.html", ignoreCase = true) == true ||
            url?.contains(":6080") == true ||
            url?.contains("-6080-") == true
    }
    val domain = remember(pageUrl, isDesktop, booting) {
        when {
            isDesktop && pageUrl.isNotBlank() ->
                pageUrl.substringAfter("://", pageUrl).substringBefore('/').removePrefix("www.").ifBlank { "desktop" }
            isDesktop -> "desktop"
            pageUrl.isBlank() -> if (booting) "starting…" else "browser"
            else -> {
                val host = pageUrl.substringAfter("://", pageUrl).substringBefore('/').trim()
                host.removePrefix("www.").ifBlank { "browser" }
            }
        }
    }
    val usingLabel = if (isDesktop) "AhamAI is using Desktop" else statusTitle
    var takeControl by remember { mutableStateOf(isDesktop) } // desktop needs touches for noVNC

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(pageBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Top bar: same AhamAI logo style as chat TopAppBar ───────────────
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Lucide.X, "Close", tint = muted, modifier = Modifier.size(22.dp))
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Profile-style brand mark
                AhamaiLogo(
                    color = ink,
                    fontSize = 22.sp
                )
                Text(
                    "computer",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    // Match the minimized dock's "computer" label (Unica One, not Inter).
                    fontFamily = UnicaOneRegular,
                    color = muted,
                    modifier = Modifier.offset(y = (-2).dp),
                    letterSpacing = 0.2.sp
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = chip,
                shadowElevation = if (isDark) 0.dp else 1.dp,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable(onClick = onToggleOrientation)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isLandscape) Lucide.Monitor else Lucide.Smartphone,
                        "Orientation", tint = ink, modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Lucide.ChevronDown, null, tint = muted, modifier = Modifier.size(14.dp))
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // ── Preview: match 1024×768 desktop aspect (4:3) so no black letterbox bar ──
        // Filling a tall phone box with a landscape framebuffer always left a huge black gap;
        // sizing the card to the real aspect ratio keeps the stream edge-to-edge and large.
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = card,
            shadowElevation = if (isDark) 0.dp else 10.dp,
            tonalElevation = if (isDark) 1.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                if (!isDesktop) {
                    Text(
                        domain,
                        Modifier.align(Alignment.CenterHorizontally).padding(bottom = 4.dp, top = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = InterFamily,
                        color = muted,
                        style = androidx.compose.ui.text.TextStyle(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                        )
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        // Desktop 1024×768 → 4:3. Playwright live stream is 1280×820 ≈ 16:10.
                        .aspectRatio(if (isDesktop) (1024f / 768f) else (1280f / 820f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF0A0A0C)),
                    contentAlignment = Alignment.Center
                ) {
                    if (url != null) {
                        val webHolder = remember(url) { mutableStateOf<WebView?>(null) }
                        // noVNC-only fit inject (Playwright live page is plain HTML /shot — do not inject)
                        if (isDesktop) {
                            LaunchedEffect(url, statusDetail) {
                                delay(350)
                                webHolder.value?.evaluateJavascript(NOVNC_FULLSCREEN_JS, null)
                                delay(1200)
                                webHolder.value?.evaluateJavascript(NOVNC_FULLSCREEN_JS, null)
                            }
                            LaunchedEffect(url) {
                                while (true) {
                                    delay(3000)
                                    webHolder.value?.evaluateJavascript(NOVNC_FULLSCREEN_JS, null)
                                }
                            }
                        }
                        key(url) {
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
                                        settings.builtInZoomControls = false
                                        settings.displayZoomControls = false
                                        settings.setSupportZoom(false)
                                        settings.mediaPlaybackRequiresUserGesture = false
                                        settings.mixedContentMode =
                                            android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                        setBackgroundColor(android.graphics.Color.parseColor("#0A0A0C"))
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, u: String?) {
                                                super.onPageFinished(view, u)
                                                // Only inject noVNC helpers for desktop URLs
                                                if (u?.contains("vnc", ignoreCase = true) == true ||
                                                    url.contains("vnc.html", ignoreCase = true)
                                                ) {
                                                    view?.evaluateJavascript(NOVNC_FULLSCREEN_JS, null)
                                                }
                                            }
                                            // The live-view host (sandbox port 3000) can be briefly
                                            // unreachable while the daemon warms or the edge routes.
                                            // Auto-retry the main frame so the screen recovers instead
                                            // of staying frozen while the agent keeps working.
                                            override fun onReceivedError(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                                error: android.webkit.WebResourceError?
                                            ) {
                                                super.onReceivedError(view, request, error)
                                                if (request?.isForMainFrame == true && view != null) {
                                                    view.postDelayed(
                                                        { runCatching { view.loadUrl(url) } },
                                                        1500
                                                    )
                                                }
                                            }
                                        }
                                        webChromeClient = WebChromeClient()
                                        webHolder.value = this
                                        loadUrl(url)
                                    }
                                },
                                update = { wv ->
                                    webHolder.value = wv
                                    if (wv.tag != url) {
                                        wv.tag = url
                                        wv.loadUrl(url)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (!takeControl && !isDesktop) {
                            Box(
                                Modifier.fillMaxSize().clickable(
                                    interactionSource = remember {
                                        androidx.compose.foundation.interaction.MutableInteractionSource()
                                    },
                                    indication = null,
                                    onClick = {}
                                )
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 28.dp)
                        ) {
                            BootSpinner(color = cream)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (booting) "Starting the cloud session…" else "No live stream yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = InterFamily,
                                color = ink
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                if (booting)
                                    "Desktop boots in about 30–60s the first time."
                                else "The agent will open a live view when ready.",
                                fontSize = 12.sp,
                                fontFamily = InterFamily,
                                color = muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    // "Take control" button removed — the live view is agent-driven (view-only).
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Clean live-action line (no card, no "using Browser" label, no Live badge) ──
        val actionLine = when {
            statusDetail.isNotBlank() -> statusDetail
            stepLabel.isNotBlank() -> stepLabel
            booting && url == null -> "Starting…"
            isDesktop -> "Desktop session"
            else -> "Browser session"
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                actionLine,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFamily,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * noVNC inject: hide chrome, auto-connect, scale framebuffer to FILL the WebView (cover).
 * Parent Compose box is already 4:3 (same as 1024×768), so cover ≈ exact fit with no black bar.
 */
private const val NOVNC_FULLSCREEN_JS = """
(function(){
  try {
    var css = document.getElementById('__aham_vnc_css');
    if (!css) {
      css = document.createElement('style');
      css.id = '__aham_vnc_css';
      css.textContent = [
        'html,body{margin:0!important;padding:0!important;width:100%!important;height:100%!important;overflow:hidden!important;background:#0a0a0c!important;}',
        '#noVNC_control_bar_anchor,#noVNC_control_bar,#noVNC_status_bar,#noVNC_control_bar_handle,',
        '#noVNC_transition,#noVNC_connect_dlg,.noVNC_panel,.noVNC_vcenter,#noVNC_hint_anchor{display:none!important;visibility:hidden!important;pointer-events:none!important;}',
        '#noVNC_container,#noVNC_screen{position:fixed!important;inset:0!important;width:100%!important;height:100%!important;',
        'margin:0!important;padding:0!important;overflow:hidden!important;background:#0a0a0c!important;}'
      ].join('');
      (document.head||document.documentElement).appendChild(css);
    }
    function clickConnect(){
      var b = document.getElementById('noVNC_connect_button');
      if (b) { b.click(); return true; }
      var btns = document.querySelectorAll('button, input[type=button], .noVNC_button');
      for (var i=0;i<btns.length;i++){
        var t=(btns[i].textContent||btns[i].value||'').toLowerCase();
        if (t.indexOf('connect')>=0){ btns[i].click(); return true; }
      }
      return false;
    }
    clickConnect();
    [300,900,2000].forEach(function(ms){ setTimeout(clickConnect, ms); });
    function fit(){
      try {
        if (window.UI && UI.rfb) {
          UI.rfb.scaleViewport = true;
          UI.rfb.clipViewport = false;
          UI.rfb.resizeSession = false;
          try { UI.rfb.showDotCursor = false; } catch(e){}
          try { if (typeof UI.applyResizeMode === 'function') UI.applyResizeMode(); } catch(e){}
          try { if (typeof UI.updateViewClip === 'function') UI.updateViewClip(); } catch(e){}
        }
        // Cover-scale canvas into container (fills 4:3 box edge-to-edge)
        var cont = document.getElementById('noVNC_container') || document.getElementById('noVNC_screen') || document.body;
        var canvas = cont ? cont.querySelector('canvas') : null;
        if (canvas && cont) {
          var cw = cont.clientWidth || window.innerWidth;
          var ch = cont.clientHeight || window.innerHeight;
          var bw = canvas.width || 1024;
          var bh = canvas.height || 768;
          if (cw > 0 && ch > 0 && bw > 0 && bh > 0) {
            var s = Math.min(cw / bw, ch / bh); // contain inside 4:3 parent = fill, no letterbox
            var tw = Math.round(bw * s), th = Math.round(bh * s);
            canvas.style.position = 'absolute';
            canvas.style.width = tw + 'px';
            canvas.style.height = th + 'px';
            canvas.style.left = Math.round((cw - tw) / 2) + 'px';
            canvas.style.top = Math.round((ch - th) / 2) + 'px';
            canvas.style.margin = '0';
            canvas.style.maxWidth = 'none';
            canvas.style.maxHeight = 'none';
          }
        }
        window.dispatchEvent(new Event('resize'));
      } catch(e){}
    }
    fit();
    [200,600,1200,2500,5000].forEach(function(ms){ setTimeout(fit, ms); });
    if (!window.__aham_fit_iv) window.__aham_fit_iv = setInterval(fit, 2000);
  } catch(e){}
})();
"""

/**
 * Manus-style dock above the composer — soft card, thumb, title, timer, chevron.
 */
@Composable
fun BrowserComputerDock(
    isDark: Boolean,
    booting: Boolean,
    live: Boolean,
    pageUrl: String = "",
    title: String = "AhamAI's computer",
    detail: String = "",
    elapsedSec: Int = 0,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val card = if (isDark) Color(0xFF161618) else Color(0xFFFFFFFF)
    val ink = if (isDark) Color(0xFFECECEC) else Color(0xFF111113)
    val muted = if (isDark) Color(0xFF8E8E96) else Color(0xFF6B7280)
    val cream = if (isDark) Color(0xFFEFDCC0) else Color(0xFF7A5F45)
    val thumbBg = if (isDark) Color(0xFF0E0E10) else Color(0xFFF3F3F5)
    val border = if (isDark) Color(0xFFFFFFFF) else Color(0xFF999999)
    val domain = remember(pageUrl) {
        pageUrl.substringAfter("://", pageUrl).substringBefore('/').removePrefix("www.").ifBlank { "browser" }
    }
    val subtitle = detail.ifBlank {
        when {
            booting -> "Starting cloud session…"
            live && pageUrl.isNotBlank() -> domain
            live -> "Live · tap to expand"
            else -> "Tap to open"
        }
    }
    val time = remember(elapsedSec) {
        "%02d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = card,
        shadowElevation = if (isDark) 0.dp else 6.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, border),
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(thumbBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Lucide.Monitor, null,
                    tint = when {
                        live -> cream
                        booting -> muted
                        else -> muted
                    },
                    modifier = Modifier.size(20.dp)
                )
                if (live) {
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp)
                            .size(7.dp).clip(CircleShape).background(Color(0xFF22C55E))
                    )
                } else if (booting) {
                    BootSpinner(color = cream, modifier = Modifier.align(Alignment.BottomEnd).padding(5.dp).size(12.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // Profile-style brand mark + "computer" label
                Text(
                    text = buildAnnotatedString {
                        withStyle(ahamaiLogoSpanStyle(fontSize = 15.sp, color = ink)) {
                            append("ahamai")
                        }
                        withStyle(
                            SpanStyle(
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp,
                                letterSpacing = 0.2.sp,
                                color = muted,
                                fontFamily = UnicaOneRegular
                            )
                        ) {
                            append("  computer")
                        }
                    },
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(time, fontSize = 11.sp, fontFamily = InterFamily, color = muted)
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.ChevronDown, null, tint = muted, modifier = Modifier.size(18.dp))
        }
    }
}

/** A clean, branded boot loader — smooth rotating arc used while Chromium starts. */
@Composable
private fun BootSpinner(color: Color, modifier: Modifier = Modifier.size(34.dp)) {
    val t = rememberInfiniteTransition(label = "boot")
    val angle by t.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(820, easing = androidx.compose.animation.core.LinearEasing)),
        label = "rot"
    )
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val strokeW = (size.minDimension * 0.09f).coerceIn(1.5f, 4f)
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = strokeW,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawArc(color = color.copy(alpha = 0.18f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = stroke)
        drawArc(color = color, startAngle = angle, sweepAngle = 90f, useCenter = false, style = stroke)
    }
}


/**
 * AhamAI's Computer — a unified live cloud-workspace panel (better than Manus's "Computer"):
 * one window with Browser / Terminal / Files tabs and a live status light. The agent's real
 * Chromium, shell output and project files in a single framed surface.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AhamAIComputer(
    liveUrl: String?,
    pageUrl: String,
    booting: Boolean,
    running: Boolean,
    terminalText: String,
    fileCount: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    isLandscape: Boolean = true,
    onToggleOrientation: () -> Unit = {},
    onOpenFiles: () -> Unit = {}
) {
    val hasBrowser = liveUrl != null || booting
    var tab by remember { mutableStateOf(if (hasBrowser) 0 else 1) }
    var minimized by remember { mutableStateOf(false) }
    LaunchedEffect(hasBrowser) { if (hasBrowser) tab = 0 }

    val frame = if (isDark) Color(0xFF161618) else Color.White
    val bar = if (isDark) Color(0xFF1F1F22) else Color(0xFFF2F2F2)
    val txt = if (isDark) Color(0xFFEDEDED) else Color(0xFF111114)
    val muted = if (isDark) Color(0xFF9A9AA2) else Color(0xFF6B7280)
    val online = running || hasBrowser

    Surface(shape = RoundedCornerShape(18.dp), color = frame, shadowElevation = 16.dp, modifier = modifier.fillMaxWidth()) {
        Column {
            // Window title bar: live status + name + tabs
            Row(
                modifier = Modifier.fillMaxWidth().background(bar).padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dot = if (online) Color(0xFF22C55E) else Color(0xFF9AA0AA)
                if (online) {
                    val tr = rememberInfiniteTransition(label = "live")
                    val a by tr.animateFloat(0.4f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a")
                    Box(Modifier.size(8.dp).clip(CircleShape).background(dot.copy(alpha = a)))
                } else Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.width(8.dp))
                Text("AhamAI’s Computer", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = txt, fontFamily = InterFamily)
                Spacer(Modifier.weight(1f))
                listOf("Browser", "Terminal", "Files").forEachIndexed { i, t ->
                    val sel = tab == i
                    Box(
                        modifier = Modifier.padding(start = 4.dp).clip(RoundedCornerShape(50))
                            .background(if (sel) (if (isDark) Color(0xFF333338) else Color.White) else Color.Transparent)
                            .clickable { minimized = false; tab = i }.padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(t, fontSize = 11.sp, color = if (sel) txt else muted, fontFamily = InterFamily,
                            fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
                // Minimize / restore control — collapse the computer to just this title bar.
                Box(
                    modifier = Modifier.padding(start = 8.dp).size(26.dp).clip(RoundedCornerShape(7.dp))
                        .background(if (isDark) Color(0xFF2A2A2E) else Color(0xFFE7E7EA))
                        .clickable { minimized = !minimized },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (minimized) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                        contentDescription = if (minimized) "Restore" else "Minimize",
                        tint = txt, modifier = Modifier.size(17.dp)
                    )
                }
            }
            if (!minimized) Box(modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp, max = 420.dp).background(if (tab == 0) Color(0xFF0D0D0D) else frame)) {
                when (tab) {
                    0 -> ComputerBrowser(liveUrl, pageUrl, booting, isDark, isLandscape, onToggleOrientation)
                    1 -> ComputerTerminal(terminalText, isDark)
                    else -> ComputerFiles(fileCount, isDark, txt, muted, onOpenFiles)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ComputerBrowser(
    url: String?,
    pageUrl: String,
    booting: Boolean,
    isDark: Boolean,
    isLandscape: Boolean = true,
    onToggleOrientation: () -> Unit = {}
) {
    val accent = if (isDark) Color(0xFFECECEC) else Color(0xFFEDEDED)
    val domain = remember(pageUrl) { pageUrl.substringAfter("://", pageUrl).substringBefore('/').removePrefix("www.").trim() }
    val addr = remember(pageUrl, booting) {
        if (pageUrl.isBlank()) (if (booting) "booting…" else "live browser")
        else pageUrl.removePrefix("https://").removePrefix("http://").trimEnd('/')
    }
    Column(Modifier.fillMaxSize()) {
        // chrome bar
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xFF1E1F24)).padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (domain.isNotBlank()) AsyncImage("https://www.google.com/s2/favicons?domain=$domain&sz=64", null, modifier = Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)))
            else Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF55585F)))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).background(Color(0xFF14151A)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                Text(addr, fontSize = 10.5.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFFAEB4BF), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.fillMaxSize().background(Color(0xFF0D0D0D)), contentAlignment = Alignment.Center) {
            if (url != null) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
                            settings.loadWithOverviewMode = true; settings.useWideViewPort = true
                            setBackgroundColor(android.graphics.Color.parseColor("#0D0D0D"))
                            webViewClient = WebViewClient(); webChromeClient = WebChromeClient(); loadUrl(url)
                        }
                    },
                    update = { wv -> if (wv.tag != url) { wv.tag = url; wv.loadUrl(url) } },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
                    BootSpinner(color = accent)
                    Spacer(Modifier.height(16.dp))
                    Text("Booting the cloud browser…", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily, color = Color(0xFFF1F1F1))
                    Spacer(Modifier.height(5.dp))
                    Text("Spinning up a real Chromium — about 40 sec to 1 min the first time.", fontSize = 11.sp, fontFamily = InterFamily, color = Color(0xFF9AA0AA), textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 15.sp)
                }
            }
            // Rotation toggle button (bottom-right corner of screen)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { onToggleOrientation() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLandscape) Lucide.Smartphone else Lucide.Monitor,
                    contentDescription = if (isLandscape) "Switch to portrait" else "Switch to landscape",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
private fun ComputerTerminal(text: String, isDark: Boolean) {
    val scroll = rememberScrollState()
    LaunchedEffect(text) { scroll.scrollTo(scroll.maxValue) }
    Box(Modifier.fillMaxSize().background(if (isDark) Color(0xFF0C0C0E) else Color(0xFF0D0D0D)).verticalScroll(scroll).padding(14.dp)) {
        Text(
            text.ifBlank { "$ waiting for commands…" },
            fontSize = 11.sp, fontFamily = JetBrainsMonoFamily, color = Color(0xFF8CE0A0), lineHeight = 16.sp
        )
    }
}

@Composable
private fun ComputerFiles(fileCount: Int, isDark: Boolean, txt: Color, muted: Color, onOpenFiles: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(if (isDark) Color(0xFF222226) else Color(0xFFF0F0F2)), contentAlignment = Alignment.Center) {
            Icon(Lucide.Layers, null, tint = Color(0xFF6366F1), modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("$fileCount file${if (fileCount == 1) "" else "s"} in workspace", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = txt, fontFamily = InterFamily)
        Spacer(Modifier.height(4.dp))
        Text("Everything the agent created lives here.", fontSize = 11.sp, color = muted, fontFamily = InterFamily)
        Spacer(Modifier.height(16.dp))
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF6366F1)).clickable { onOpenFiles() }.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text("Open files", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFamily)
        }
    }
}

/**
 * Captures the diagram/chart WebView as a PNG bitmap and saves it to the Downloads folder.
 * Uses a properly sized off-screen WebView with correct measure/layout so the diagram
 * actually renders before capture (fix: plain "diagram preview" fallback text).
 * If rendering fails, saves the HTML source file instead.
 */
private suspend fun savePreviewAsImage(
    context: android.content.Context,
    kind: String,
    payload: String,
    isDark: Boolean
) {
    withContext(Dispatchers.Main) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) {
            Toast.makeText(context, "Saving requires Android 8+", Toast.LENGTH_SHORT).show()
            return@withContext
        }

        val html = when (kind) {
            "diagram" -> buildMermaidHtml(payload, isDark)
            "chart" -> {
                val firstNewline = payload.indexOf('\n')
                val trimmed = payload.trimStart()
                val header: String
                val data: String
                if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
                    header = "bar|"
                    data = payload
                } else {
                    header = if (firstNewline >= 0) payload.substring(0, firstNewline) else payload
                    data = if (firstNewline >= 0) payload.substring(firstNewline + 1) else "[]"
                }
                val parts = header.split("|", limit = 2)
                val chartType = parts.getOrNull(0)?.ifBlank { "bar" } ?: "bar"
                val title = parts.getOrNull(1) ?: ""
                buildChartHtml(chartType, title, data, isDark)
            }
            else -> return@withContext
        }

        try {
            // Use a large, properly laid-out WebView so drawToBitmap captures full resolution.
            val targetW = 1400
            val targetH = 1000
            val wv = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setInitialScale(100)
                layoutParams = ViewGroup.LayoutParams(targetW, targetH)
                measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(targetW, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(targetH, android.view.View.MeasureSpec.EXACTLY)
                )
                layout(0, 0, targetW, targetH)
            }

            val captureDone = kotlinx.coroutines.CompletableDeferred<Boolean>()

            wv.webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    view?.apply {
                        measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(targetW, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(targetH, android.view.View.MeasureSpec.EXACTLY)
                        )
                        layout(0, 0, targetW, targetH)
                    }
                    // Wait longer for Mermaid/Chart.js to fully render after page load
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        // Re-layout again after JS render
                        view?.apply {
                            measure(
                                android.view.View.MeasureSpec.makeMeasureSpec(targetW, android.view.View.MeasureSpec.EXACTLY),
                                android.view.View.MeasureSpec.makeMeasureSpec(targetH, android.view.View.MeasureSpec.EXACTLY)
                            )
                            layout(0, 0, targetW, targetH)
                        }
                        captureDone.complete(true)
                    }, 5000)
                }
            }

            val baseUrl = if (kind == "diagram") "https://mermaid/" else "https://charts/"
            wv.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)

            // Wait up to 20 seconds for JS rendering + capture ready
            val rendered = kotlinx.coroutines.withTimeoutOrNull(20_000) { captureDone.await() } ?: false

            if (rendered) {
                // Final re-layout before capture
                wv.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(targetW, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(targetH, android.view.View.MeasureSpec.EXACTLY)
                )
                wv.layout(0, 0, targetW, targetH)

                val bitmap = wv.drawToBitmap(android.graphics.Bitmap.Config.ARGB_8888)
                // Verify the bitmap isn't blank (all same color = rendering failed)
                val firstPixel = if (bitmap.width > 0 && bitmap.height > 0) bitmap.getPixel(0, 0) else 0
                val blankThreshold = 5
                var blankCount = 0
                val step = maxOf(1, bitmap.width / 10)
                for (x in 0 until bitmap.width step step) {
                    for (y in 0 until bitmap.height step step) {
                        if (Math.abs(bitmap.getPixel(x, y) - firstPixel) < 0x010101) blankCount++
                    }
                }
                val total = ((bitmap.width / step) * (bitmap.height / step))
                if (blankCount > total * 0.85) {
                    // Mostly blank — fall through to save HTML
                    throw java.lang.Exception("Rendered image appears blank (${blankCount}/$total pixels uniform)")
                }

                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                val bytes = stream.toByteArray()
                val name = "AhamAI_${kind}_${System.currentTimeMillis()}.png"
                val result = com.ahamai.app.data.DeviceStorage.saveBytesToDownloads(context, bytes, name, "image/png")
                Toast.makeText(context, if (result.startsWith("OK")) "Saved to Downloads" else result, Toast.LENGTH_SHORT).show()
            } else {
                // Timed out rendering — save HTML file as fallback
                throw java.lang.Exception("render timed out")
            }
        } catch (e: Exception) {
            // Fallback: save the HTML source as a file so the user can open it in a browser
            // for a proper render, instead of the broken "diagram preview" text approach.
            try {
                val name = "AhamAI_${kind}_${System.currentTimeMillis()}.html"
                val result = com.ahamai.app.data.DeviceStorage.saveBytesToDownloads(context, html.toByteArray(Charsets.UTF_8), name, "text/html")
                Toast.makeText(context,
                    if (result.startsWith("OK")) "Saved as HTML (open in browser for full render)" else result,
                    Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(context, "Save failed: ${e.message?.take(40)}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
