package com.ahamai.app.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahamai.app.data.DeviceStorage
import com.ahamai.app.ui.icons.AdminIcons
import com.ahamai.app.ui.theme.InterFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Flexible Mermaid diagram + Chart.js previews with reliable PNG export.
 * Height adapts via JS → native bridge after render.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun FlexibleMermaidPreview(source: String, isDark: Boolean, modifier: Modifier = Modifier) {
    val html = remember(source, isDark) { buildMermaidHtmlV2(source, isDark) }
    FlexibleWebPreview(
        html = html,
        baseUrl = "https://cdn.jsdelivr.net/",
        isDark = isDark,
        defaultHeightDp = 240,
        minHeightDp = 160,
        maxHeightDp = 720,
        modifier = modifier
    )
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun FlexibleChartPreview(payload: String, isDark: Boolean, modifier: Modifier = Modifier) {
    // Prefer in-app Chart.js (multi-CDN). If WebView/CDN fails, fall back to QuickChart image.
    val html = remember(payload, isDark) { buildChartHtmlV2(payload, isDark) }
    val quickUrl = remember(payload, isDark) { buildQuickChartUrl(payload, isDark) }
    var useQuick by remember(payload) { mutableStateOf(false) }

    if (useQuick && quickUrl != null) {
        val bg = if (isDark) Color(0xFF1A1D23) else Color.White
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bg),
            contentAlignment = Alignment.Center
        ) {
            coil.compose.AsyncImage(
                model = quickUrl,
                contentDescription = "Chart",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    } else {
        FlexibleWebPreview(
            html = html,
            baseUrl = "https://cdn.jsdelivr.net/",
            isDark = isDark,
            defaultHeightDp = 280,
            minHeightDp = 200,
            maxHeightDp = 640,
            modifier = modifier,
            onHardError = {
                if (quickUrl != null) useQuick = true
            }
        )
    }
}

/**
 * QuickChart.io server-rendered chart — works when local Chart.js CDN is blocked.
 * Config is URL-encoded Chart.js JSON.
 */
internal fun buildQuickChartUrl(payload: String, isDark: Boolean): String? {
    return try {
        val (type, title, dataJson) = normalizeChartPayload(payload)
        val labels: org.json.JSONArray
        val values: org.json.JSONArray
        val parsed = try {
            if (dataJson.trimStart().startsWith("[")) org.json.JSONArray(dataJson)
            else org.json.JSONObject(dataJson)
        } catch (_: Exception) {
            // Strip trailing commas then retry
            val cleaned = dataJson.replace(Regex(",\\s*([}\\]])"), "$1")
            if (cleaned.trimStart().startsWith("[")) org.json.JSONArray(cleaned)
            else org.json.JSONObject(cleaned)
        }
        when (parsed) {
            is org.json.JSONArray -> {
                if (parsed.length() > 0 && parsed.opt(0) is Number) {
                    labels = org.json.JSONArray((0 until parsed.length()).map { "${it + 1}" })
                    values = parsed
                } else {
                    labels = org.json.JSONArray()
                    values = org.json.JSONArray()
                    for (i in 0 until parsed.length()) {
                        val o = parsed.optJSONObject(i) ?: continue
                        labels.put(o.optString("label", o.optString("name", o.optString("x", "${i + 1}"))))
                        values.put(o.optDouble("value", o.optDouble("y", o.optDouble("v", 0.0))))
                    }
                }
            }
            is org.json.JSONObject -> {
                if (parsed.has("labels") && parsed.has("datasets")) {
                    // Already Chart.js shape — pass through
                    val cfg = org.json.JSONObject().apply {
                        put("type", type.ifBlank { "bar" })
                        put("data", parsed)
                        put("options", org.json.JSONObject().apply {
                            put("plugins", org.json.JSONObject().apply {
                                put("legend", org.json.JSONObject().put("display", true))
                                if (title.isNotBlank()) put("title", org.json.JSONObject().put("display", true).put("text", title))
                            })
                        })
                    }
                    val enc = java.net.URLEncoder.encode(cfg.toString(), "UTF-8")
                    return "https://quickchart.io/chart?c=$enc&backgroundColor=${if (isDark) "1A1D23" else "FFFFFF"}&width=600&height=320&devicePixelRatio=2"
                }
                labels = org.json.JSONArray()
                values = org.json.JSONArray()
                val keys = parsed.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in setOf("type", "title", "chartType", "data", "datasets", "labels")) continue
                    labels.put(k)
                    values.put(parsed.optDouble(k, 0.0))
                }
            }
            else -> return null
        }
        if (values.length() == 0) return null
        val chartType = when (type.lowercase()) {
            "line", "area" -> "line"
            "pie" -> "pie"
            "doughnut", "donut" -> "doughnut"
            "radar" -> "radar"
            else -> "bar"
        }
        val cfg = org.json.JSONObject().apply {
            put("type", chartType)
            put("data", org.json.JSONObject().apply {
                put("labels", labels)
                put("datasets", org.json.JSONArray().put(org.json.JSONObject().apply {
                    put("label", title.ifBlank { "Value" })
                    put("data", values)
                    put("backgroundColor", org.json.JSONArray(listOf(
                        "#6366F1", "#22C55E", "#F59E0B", "#EF4444", "#A855F7", "#06B6D4"
                    )))
                }))
            })
            put("options", org.json.JSONObject().apply {
                put("plugins", org.json.JSONObject().apply {
                    put("legend", org.json.JSONObject().put("display", chartType == "pie" || chartType == "doughnut"))
                    if (title.isNotBlank()) {
                        put("title", org.json.JSONObject().put("display", true).put("text", title))
                    }
                })
            })
        }
        val enc = java.net.URLEncoder.encode(cfg.toString(), "UTF-8")
        "https://quickchart.io/chart?c=$enc&backgroundColor=${if (isDark) "1A1D23" else "FFFFFF"}&width=600&height=320&devicePixelRatio=2"
    } catch (_: Exception) {
        null
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
private fun FlexibleWebPreview(
    html: String,
    baseUrl: String,
    isDark: Boolean,
    defaultHeightDp: Int,
    minHeightDp: Int,
    maxHeightDp: Int,
    modifier: Modifier = Modifier,
    onHardError: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    var heightDp by remember(html) { mutableIntStateOf(defaultHeightDp) }
    var ready by remember(html) { mutableStateOf(false) }
    var error by remember(html) { mutableStateOf<String?>(null) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var hardErrorFired by remember(html) { mutableStateOf(false) }

    val bg = if (isDark) Color(0xFF1A1D23) else Color.White
    val muted = if (isDark) Color(0xFF9AA0A6) else Color(0xFF6B7280)

    fun reportHardError(msg: String) {
        error = msg.take(120)
        ready = true
        if (!hardErrorFired) {
            hardErrorFired = true
            onHardError?.invoke()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
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
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    isVerticalScrollBarEnabled = false
                    isNestedScrollingEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onReady(heightPx: Int) {
                            Handler(Looper.getMainLooper()).post {
                                val h = with(density) {
                                    (heightPx / density.density).roundToInt()
                                        .coerceIn(minHeightDp, maxHeightDp)
                                }
                                heightDp = h
                                ready = true
                                error = null
                            }
                        }

                        @JavascriptInterface
                        fun onError(msg: String) {
                            Handler(Looper.getMainLooper()).post {
                                reportHardError(msg)
                            }
                        }
                    }, "AhamAI")
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (!ready) {
                                    view?.evaluateJavascript(
                                        "(function(){try{var h=document.documentElement.scrollHeight||document.body.scrollHeight||400;AhamAI.onReady(h);}catch(e){AhamAI.onError(String(e));}})();",
                                        null
                                    )
                                }
                            }, 3200)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            errorCode: Int,
                            description: String?,
                            failingUrl: String?
                        ) {
                            Handler(Looper.getMainLooper()).post {
                                reportHardError(description ?: "WebView error $errorCode")
                            }
                        }
                    }
                    loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    webRef = this
                }
            },
            update = { },
            modifier = Modifier.fillMaxSize()
        )

        if (!ready && error == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(22.dp)
                    .align(Alignment.Center),
                strokeWidth = 2.dp,
                color = muted
            )
        }
        // Soft error only if no QuickChart fallback will take over
        if (onHardError == null) {
            error?.let { msg ->
                Text(
                    text = msg,
                    color = muted,
                    fontSize = 12.sp,
                    fontFamily = InterFamily,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }
    }

    DisposableEffect(html) {
        onDispose {
            webRef?.apply {
                stopLoading()
                destroy()
            }
            webRef = null
        }
    }
}

@Composable
fun DiagramChartSaveButton(
    kind: String,
    payload: String,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(enabled = !saving) {
                if (saving) return@clickable
                saving = true
                scope.launch {
                    val ok = saveDiagramOrChartPng(context, kind, payload, isDark)
                    saving = false
                    Toast.makeText(
                        context,
                        if (ok) "Saved PNG to Downloads/AhamAI"
                        else "Could not capture PNG — check network for Chart.js/Mermaid CDN",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (saving) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = Color.White
            )
        } else {
            Icon(
                imageVector = AdminIcons.BootstrapSave,
                contentDescription = "Save PNG",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Capture diagram/chart as PNG only (no HTML fallback).
 * Renders off-screen WebView, waits for AhamAI.onReady, then draws bitmap.
 */
suspend fun saveDiagramOrChartPng(
    context: android.content.Context,
    kind: String,
    payload: String,
    isDark: Boolean
): Boolean = withContext(Dispatchers.Main) {
    val html = when (kind) {
        "diagram" -> buildMermaidHtmlV2(payload, isDark)
        "chart" -> buildChartHtmlV2(payload, isDark)
        else -> return@withContext false
    }
    val targetW = 2000
    val targetH = 4000

    try {
        val wv = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            layoutParams = ViewGroup.LayoutParams(targetW, targetH)
            measure(
                View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(targetH, View.MeasureSpec.EXACTLY)
            )
            layout(0, 0, targetW, targetH)
        }

        val ready = kotlinx.coroutines.CompletableDeferred<Int>()
        wv.addJavascriptInterface(object {
            @JavascriptInterface
            fun onReady(heightPx: Int) {
                if (!ready.isCompleted) ready.complete(heightPx.coerceIn(200, 2000))
            }

            @JavascriptInterface
            fun onError(msg: String) {
                if (!ready.isCompleted) ready.complete(-1)
            }
        }, "AhamAI")

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Handler(Looper.getMainLooper()).postDelayed({
                    view?.evaluateJavascript(
                        "(function(){try{var h=document.documentElement.scrollHeight||480;if(window.__ahamaiReady){AhamAI.onReady(h);}else{setTimeout(function(){AhamAI.onReady(document.documentElement.scrollHeight||480);},1200);}}catch(e){AhamAI.onError(String(e));}})();",
                        null
                    )
                }, 600)
            }
        }

        wv.loadDataWithBaseURL("https://cdn.jsdelivr.net/", html, "text/html", "utf-8", null)

        val h = withTimeoutOrNull(20_000L) { ready.await() } ?: 0
        if (h <= 0) {
            wv.destroy()
            return@withContext false
        }

        // Extra settle for fonts/SVG
        suspendCancellableCoroutine { cont ->
            Handler(Looper.getMainLooper()).postDelayed({
                if (cont.isActive) cont.resume(Unit)
            }, 700)
        }

        val captureH = (h + 80).coerceIn(400, 4000)
        wv.measure(
            View.MeasureSpec.makeMeasureSpec(targetW, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(captureH, View.MeasureSpec.EXACTLY)
        )
        wv.layout(0, 0, targetW, captureH)

        val bmp = Bitmap.createBitmap(targetW, captureH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        wv.draw(canvas)

        // Blank check
        if (isMostlyBlank(bmp)) {
            wv.destroy()
            return@withContext false
        }

        val stream = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val bytes = stream.toByteArray()
        wv.destroy()

        val name = "AhamAI_${kind}_${System.currentTimeMillis()}.png"
        val result = DeviceStorage.saveToAhamAIFolder(context, bytes, name, "image/png")
        result.startsWith("OK")
    } catch (_: Exception) {
        false
    }
}

private fun isMostlyBlank(bmp: Bitmap): Boolean {
    if (bmp.width < 8 || bmp.height < 8) return true
    val stepX = max(1, bmp.width / 12)
    val stepY = max(1, bmp.height / 12)
    val first = bmp.getPixel(stepX, stepY)
    var same = 0
    var total = 0
    var x = stepX
    while (x < bmp.width) {
        var y = stepY
        while (y < bmp.height) {
            total++
            if (bmp.getPixel(x, y) == first) same++
            y += stepY
        }
        x += stepX
    }
    return total > 0 && same.toFloat() / total > 0.92f
}

// ── HTML builders (v2) ─────────────────────────────────────────────────────

internal fun buildMermaidHtmlV2(source: String, isDark: Boolean): String {
    val bg = if (isDark) "#1A1D23" else "#FFFFFF"
    val fg = if (isDark) "#E8EAED" else "#111418"
    val cleaned = source
        .trim()
        .removePrefix("```mermaid")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()
    val escaped = cleaned
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("\$", "\\\$")
        .replace("</", "<\\/")
    val theme = if (isDark) "dark" else "default"
    return """
<!DOCTYPE html>
<html><head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
<script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
<style>
html,body{margin:0;padding:0;background:$bg;color:$fg;}
#wrap{padding:12px 10px;display:flex;justify-content:center;align-items:flex-start;min-height:120px;}
.mermaid{width:100%;}
.mermaid svg{max-width:100%!important;height:auto!important;}
.err{padding:16px;font:13px system-ui;color:#ef4444;text-align:center;}
</style>
</head>
<body>
<div id="wrap"><pre class="mermaid" id="d">$escaped</pre></div>
<script>
(function(){
  function report(){
    try{
      var el=document.getElementById('wrap');
      var h=Math.max(el.scrollHeight, el.offsetHeight, document.documentElement.scrollHeight||0);
      window.__ahamaiReady=true;
      if(window.AhamAI&&AhamAI.onReady) AhamAI.onReady(Math.ceil(h+8));
    }catch(e){ if(window.AhamAI&&AhamAI.onError) AhamAI.onError(String(e)); }
  }
  try{
    mermaid.initialize({startOnLoad:false, theme:'$theme', securityLevel:'loose',
      flowchart:{useMaxWidth:true, htmlLabels:true},
      sequence:{useMaxWidth:true}
    });
    mermaid.run({nodes:[document.getElementById('d')]}).then(function(){
      setTimeout(report, 120);
      setTimeout(report, 500);
    }).catch(function(e){
      document.getElementById('wrap').innerHTML='<div class="err">Diagram error: '+(e&&e.message?e.message:e)+'</div>';
      if(window.AhamAI&&AhamAI.onError) AhamAI.onError(String(e&&e.message?e.message:e));
    });
  }catch(e){
    document.getElementById('wrap').innerHTML='<div class="err">Diagram failed to load (check network)</div>';
    if(window.AhamAI&&AhamAI.onError) AhamAI.onError(String(e));
  }
})();
</script>
</body></html>
""".trimIndent()
}

/**
 * Accepts many chart payload shapes and always produces Chart.js config.
 */
internal fun buildChartHtmlV2(payload: String, isDark: Boolean): String {
    val bg = if (isDark) "#1A1D23" else "#FFFFFF"
    val fg = if (isDark) "#E8EAED" else "#111418"
    val grid = if (isDark) "rgba(255,255,255,0.08)" else "rgba(0,0,0,0.06)"
    val muted = if (isDark) "#9AA0A6" else "#6B7280"
    val (type, title, dataJson) = normalizeChartPayload(payload)
    val safeData = dataJson
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("\$", "\\\$")
        .replace("</", "<\\/")
    val safeTitle = title.replace("<", "&lt;").replace(">", "&gt;")
    val titleHtml = if (safeTitle.isBlank()) "" else
        """<div style="text-align:center;font:600 14px system-ui;color:$fg;padding:4px 0 10px">$safeTitle</div>"""

    return """
<!DOCTYPE html>
<html><head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1"/>
<style>
html,body{margin:0;padding:0;background:$bg;color:$fg;font-family:system-ui,-apple-system,sans-serif;}
#box{padding:12px 10px 8px;width:100%;box-sizing:border-box;}
#holder{position:relative;width:100%;height:260px;}
.err{padding:20px;text-align:center;color:$muted;font-size:13px;}
</style>
</head>
<body>
<div id="box">
$titleHtml
<div id="holder"><canvas id="c"></canvas></div>
</div>
<script>
(function(){
  function done(h){
    window.__ahamaiReady=true;
    try{ if(window.AhamAI&&AhamAI.onReady) AhamAI.onReady(h||320); }catch(e){}
  }
  function fail(m){
    document.getElementById('box').innerHTML='<div class="err">'+m+'</div>';
    try{ if(window.AhamAI&&AhamAI.onError) AhamAI.onError(m); }catch(e){}
  }
  // Multi-CDN load for Chart.js (jsDelivr → unpkg → cdnjs)
  function loadScript(src){
    return new Promise(function(resolve,reject){
      var s=document.createElement('script');
      s.src=src; s.async=true;
      s.onload=function(){ resolve(); };
      s.onerror=function(){ reject(new Error('load fail '+src)); };
      document.head.appendChild(s);
    });
  }
  var cdns=[
    'https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js',
    'https://unpkg.com/chart.js@4.4.1/dist/chart.umd.min.js',
    'https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js'
  ];
  function boot(){
  try{
    if(typeof Chart==='undefined'){ fail('Chart.js failed to load (network)'); return; }
    var raw = `$safeData`;
    var parsed;
    try { parsed = JSON.parse(raw); } catch(e1) {
      try { parsed = JSON.parse(raw.replace(/,\s*([}\]])/g, '$$1')); }
      catch(e2){ fail('Invalid chart JSON'); return; }
    }
    var labels=[], datasets=[];
    if(Array.isArray(parsed)){
      if(parsed.length && typeof parsed[0]==='number'){
        labels = parsed.map(function(_,i){return String(i+1);});
        datasets=[{label:'Value', data:parsed}];
      } else {
        labels = parsed.map(function(x){ return (x&& (x.label||x.x||x.name||x.key)) || ''; });
        datasets=[{label:'Value', data:parsed.map(function(x){
          if(typeof x==='number') return x;
          return Number((x&& (x.value!=null?x.value:x.y!=null?x.y:x.v)) || 0);
        })}];
      }
    } else if(parsed && parsed.labels && parsed.datasets){
      labels = parsed.labels;
      datasets = parsed.datasets;
    } else if(parsed && typeof parsed==='object'){
      labels = Object.keys(parsed);
      datasets=[{label:'Value', data: Object.keys(parsed).map(function(k){return Number(parsed[k])||0;})}];
    } else {
      fail('Unsupported chart data'); return;
    }
    var t = ${JSONObject.quote(type)}.toLowerCase();
    var chartType = (t==='doughnut'||t==='pie'||t==='radar'||t==='line'||t==='bar'||t==='polarArea') ? (t==='area'?'line':t) : 'bar';
    if(t==='area') chartType='line';
    if(t==='polar'||t==='polararea') chartType='polarArea';
    var pal=['#6366F1','#22C55E','#F59E0B','#EF4444','#A855F7','#06B6D4','#EC4899','#14B8A6'];
    var isPolar = chartType==='doughnut'||chartType==='pie'||chartType==='polarArea'||chartType==='radar';
    var isArea = t==='area'||t==='line';
    var ctx=document.getElementById('c');
    new Chart(ctx,{
      type: chartType,
      data:{
        labels: labels,
        datasets: datasets.map(function(d,i){
          return Object.assign({}, d, {
            backgroundColor: isPolar ? labels.map(function(_,j){return pal[j%pal.length];}) : (isArea ? pal[i%pal.length]+'33' : pal[i%pal.length]+'CC'),
            borderColor: isPolar ? '#ffffff40' : pal[i%pal.length],
            borderWidth: 2,
            fill: isArea || isPolar,
            tension: 0.35,
            pointRadius: 3
          });
        })
      },
      options:{
        responsive:true,
        maintainAspectRatio:false,
        plugins:{
          legend:{display:isPolar||datasets.length>1, position:'bottom', labels:{color:'$fg', boxWidth:10, font:{size:11}}},
          title:{display:false}
        },
        scales: isPolar ? {} : {
          x:{ticks:{color:'$muted', maxRotation:40, font:{size:10}}, grid:{color:'$grid'}, border:{display:false}},
          y:{beginAtZero:true, ticks:{color:'$muted', font:{size:10}}, grid:{color:'$grid'}, border:{display:false}}
        }
      }
    });
    setTimeout(function(){
      var h = document.getElementById('box').scrollHeight || 300;
      done(Math.ceil(h+12));
    }, 180);
  }catch(e){ fail('Chart error: '+(e&&e.message?e.message:e)); }
  }
  (async function(){
    for(var i=0;i<cdns.length;i++){
      try{ await loadScript(cdns[i]); if(typeof Chart!=='undefined'){ boot(); return; } }catch(e){}
    }
    fail('Chart.js failed to load (network)');
  })();
})();
</script>
</body></html>
""".trimIndent()
}

/** Returns Triple(type, title, dataJsonString) */
private fun normalizeChartPayload(payload: String): Triple<String, String, String> {
    val raw = payload.trim()
        .removePrefix("```chart")
        .removePrefix("```json")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    // Header form: type|title\n{json}
    val nl = raw.indexOf('\n')
    if (nl > 0 && !raw.startsWith("{") && !raw.startsWith("[")) {
        val header = raw.substring(0, nl).trim()
        val body = raw.substring(nl + 1).trim()
        val parts = header.split("|", limit = 2)
        val type = parts.getOrNull(0)?.ifBlank { "bar" } ?: "bar"
        val title = parts.getOrNull(1)?.trim().orEmpty()
        if (body.startsWith("{") || body.startsWith("[")) {
            return Triple(type, title, body)
        }
    }

    // Pure JSON
    if (raw.startsWith("{") || raw.startsWith("[")) {
        // Try pull type/title from object
        try {
            if (raw.startsWith("{")) {
                val o = JSONObject(raw)
                val type = o.optString("type", o.optString("chartType", "bar")).ifBlank { "bar" }
                val title = o.optString("title", "")
                val data = when {
                    o.has("data") -> o.get("data").toString()
                    o.has("labels") && o.has("datasets") -> o.toString()
                    else -> o.toString()
                }
                return Triple(type, title, data)
            }
        } catch (_: Exception) { /* fall through */ }
        return Triple("bar", "", raw)
    }

    // CSV-ish: lines label,value
    val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.size >= 2 && lines.any { it.contains(",") || it.contains("\t") || it.contains("|") }) {
        val arr = JSONArray()
        for (line in lines) {
            val parts = line.split(",", "\t", "|").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 2) {
                val label = parts[0]
                val value = parts[1].toDoubleOrNull() ?: continue
                arr.put(JSONObject().put("label", label).put("value", value))
            }
        }
        if (arr.length() > 0) return Triple("bar", "", arr.toString())
    }

    // Single numbers
    return Triple("bar", "", """[{"label":"A","value":1},{"label":"B","value":2}]""")
}
