package com.ahamai.app.ui.components

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ahamai.app.R
import com.ahamai.app.data.RemoteConfigManager
import com.ahamai.app.data.UsageTracker
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * AdMob configuration + gating for the chat-mode "Sponsored" native ad card.
 */
object AdMobAds {
    private const val TAG = "AdMobAds"
    const val NATIVE_TEST_UNIT = AdMobBootstrap.NATIVE_TEST_UNIT

    val nativeUnit: String
        get() = AdMobBootstrap.resolveUnit(
            RemoteConfigManager.chatNativeAdUnit,
            NATIVE_TEST_UNIT
        )

    /** Every Nth completed assistant message. Test mode → always 1. */
    val chatInterval: Int
        get() {
            val configured = RemoteConfigManager.chatAdInterval.coerceAtLeast(1)
            return if (AdMobBootstrap.isTestMode()) 1 else configured
        }

    /**
     * Base gate for chat + agent ads.
     * - Master switch off → no ads
     * - freeOnly + paid plan → no ads (unless sample/test App ID for local debug)
     */
    fun shouldShowAds(context: Context): Boolean {
        // Sample App ID: always allow so local debug never looks "broken" when admin
        // free-only / plan edge cases fire. Production real App ID still respects gates.
        if (AdMobBootstrap.isTestMode()) {
            if (!RemoteConfigManager.adsEnabled) {
                Log.d(TAG, "gate: master off (even in test mode)")
                return false
            }
            return true
        }
        if (!RemoteConfigManager.adsEnabled) {
            Log.d(TAG, "gate: master ads_enabled=false")
            return false
        }
        if (RemoteConfigManager.adsFreeOnly) {
            val free = runCatching { UsageTracker.currentPlan(context).isFree }.getOrDefault(true)
            if (!free) {
                Log.d(TAG, "gate: free-only and plan is paid")
                return false
            }
        }
        return true
    }

    fun chatAdsOn(context: Context): Boolean {
        val on = shouldShowAds(context) && RemoteConfigManager.chatAdsEnabled
        if (!on) {
            Log.d(
                TAG,
                "chatAdsOff master=${RemoteConfigManager.adsEnabled} " +
                    "chat=${RemoteConfigManager.chatAdsEnabled} freeOnly=${RemoteConfigManager.adsFreeOnly} " +
                    "test=${AdMobBootstrap.isTestMode()}"
            )
        }
        return on
    }
}

/**
 * ChatGPT-style sponsored native card. Renders nothing until an ad loads.
 * Loads only after [AdMobBootstrap] is ready; falls back to Google test unit; soft-retries.
 */
@Composable
fun ChatAdCard(isDark: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val appCtx = remember(context) { context.applicationContext }
    val unit = AdMobAds.nativeUnit
    val adsReady by AdMobBootstrap.ready.collectAsState()
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val main = remember { Handler(Looper.getMainLooper()) }

    // Key on unit + ready so we reload when admin switches units or SDK becomes ready
    DisposableEffect(unit, adsReady) {
        if (!adsReady) {
            Log.d("AdMobAds", "ChatAdCard waiting for MobileAds init")
            return@DisposableEffect onDispose { }
        }

        var destroyed = false
        var loaded: NativeAd? = null
        var retryRunnable: Runnable? = null

        lateinit var sched: (String, Boolean, Int, Long) -> Unit

        fun loadAd(unitId: String, allowTestFallback: Boolean, attempt: Int) {
            if (destroyed) return
            Log.i(
                "AdMobAds",
                "Loading native unit=$unitId attempt=$attempt ready=${AdMobBootstrap.isReady} " +
                    "testMode=${AdMobBootstrap.isTestMode()}"
            )
            // Application context is fine for load; avoids themed-wrapper issues.
            val loadCtx = appCtx
            val loader = AdLoader.Builder(loadCtx, unitId)
                .forNativeAd { ad ->
                    main.post {
                        if (destroyed) {
                            ad.destroy()
                            return@post
                        }
                        loaded?.destroy()
                        loaded = ad
                        nativeAd = ad
                        Log.i("AdMobAds", "Native loaded headline=${ad.headline}")
                    }
                }
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setRequestMultipleImages(false)
                        .setReturnUrlsForImageAssets(false)
                        .build()
                )
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(
                            "AdMobAds",
                            "Native fail code=${error.code} domain=${error.domain} " +
                                "msg=${error.message} unit=$unitId attempt=$attempt"
                        )
                        if (destroyed) return
                        // Custom unit failed → Google test unit
                        if (allowTestFallback && unitId != AdMobAds.NATIVE_TEST_UNIT) {
                            loadAd(AdMobAds.NATIVE_TEST_UNIT, false, 0)
                            return
                        }
                        // Soft retry with backoff (no-fill / network)
                        val delay = (2_000L * (attempt + 1)).coerceAtMost(15_000L)
                        sched(unitId, false, attempt + 1, delay)
                    }

                    override fun onAdLoaded() {
                        Log.d("AdMobAds", "onAdLoaded unit=$unitId")
                    }
                })
                .build()
            runCatching { loader.loadAd(AdRequest.Builder().build()) }
                .onFailure {
                    Log.e("AdMobAds", "loadAd threw: ${it.message}")
                    sched(unitId, allowTestFallback, attempt + 1, 3_000L)
                }
        }

        sched = { unitId, allowTestFallback, attempt, delayMs ->
            if (!destroyed && attempt <= 6) {
                val r = Runnable {
                    if (!destroyed) loadAd(unitId, allowTestFallback, attempt)
                }
                retryRunnable = r
                main.postDelayed(r, delayMs)
            }
        }

        // Always prefer configured unit first; resolveUnit already forced test when sample App ID
        loadAd(unit, unit != AdMobAds.NATIVE_TEST_UNIT, 0)

        onDispose {
            destroyed = true
            retryRunnable?.let { main.removeCallbacks(it) }
            loaded?.destroy()
            nativeAd = null
        }
    }

    val ad = nativeAd ?: return

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(vertical = 6.dp),
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.native_ad_card, null) as NativeAdView
        },
        update = { adView ->
            // Ensure parent can measure wrap content
            adView.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            bindNativeAd(ad, adView, isDark)
        }
    )
}

private fun px(context: Context, dp: Float): Float = dp * context.resources.displayMetrics.density

private fun bindNativeAd(ad: NativeAd, adView: NativeAdView, isDark: Boolean) {
    runCatching {
        val card = adView.findViewById<LinearLayout>(R.id.ad_card) ?: return
        val sponsored = adView.findViewById<TextView>(R.id.ad_sponsored)
        val icon = adView.findViewById<ImageView>(R.id.ad_icon)
        val headline = adView.findViewById<TextView>(R.id.ad_headline)
        val body = adView.findViewById<TextView>(R.id.ad_body)
        val cta = adView.findViewById<TextView>(R.id.ad_cta)

        val ink = if (isDark) AndroidColor.parseColor("#ECECEC") else AndroidColor.parseColor("#141414")
        val muted = if (isDark) AndroidColor.parseColor("#8E8E93") else AndroidColor.parseColor("#6B7280")
        val cardColor = if (isDark) AndroidColor.parseColor("#161616") else AndroidColor.parseColor("#F4F4F4")
        val ctaBg = if (isDark) AndroidColor.parseColor("#ECECEC") else AndroidColor.parseColor("#141414")
        val ctaFg = if (isDark) AndroidColor.parseColor("#141414") else AndroidColor.parseColor("#FFFFFF")

        card.background = GradientDrawable().apply {
            cornerRadius = px(card.context, 16f)
            setColor(cardColor)
        }
        sponsored?.setTextColor(muted)
        headline?.setTextColor(ink)
        body?.setTextColor(muted)

        headline?.text = ad.headline?.takeIf { it.isNotBlank() } ?: "Sponsored"
        val bodyText = ad.body
        if (bodyText.isNullOrBlank()) body?.visibility = View.GONE
        else {
            body?.text = bodyText
            body?.visibility = View.VISIBLE
        }

        val iconAsset = ad.icon
        if (icon != null) {
            if (iconAsset?.drawable != null) {
                icon.setImageDrawable(iconAsset.drawable)
                icon.visibility = View.VISIBLE
                adView.iconView = icon
            } else {
                icon.visibility = View.GONE
                adView.iconView = null
            }
        }

        if (cta != null) {
            val ctaText = ad.callToAction
            if (ctaText.isNullOrBlank()) {
                cta.visibility = View.GONE
                adView.callToActionView = null
            } else {
                cta.text = ctaText
                cta.setTextColor(ctaFg)
                cta.background = GradientDrawable().apply {
                    cornerRadius = px(cta.context, 20f)
                    setColor(ctaBg)
                }
                cta.visibility = View.VISIBLE
                adView.callToActionView = cta
            }
        }

        adView.headlineView = headline
        adView.bodyView = body
        adView.setNativeAd(ad)
    }.onFailure {
        Log.e("AdMobAds", "bindNativeAd failed: ${it.message}", it)
    }
}
