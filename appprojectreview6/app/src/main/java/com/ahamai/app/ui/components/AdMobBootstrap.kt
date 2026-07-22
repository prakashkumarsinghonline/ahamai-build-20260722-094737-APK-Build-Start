package com.ahamai.app.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single place that boots Google Mobile Ads correctly.
 *
 * Rules:
 *  - [start] on main thread early in Activity.onCreate
 *  - Sample App ID in manifest → always use Google test unit IDs (live units never fill)
 *  - [ready] must be true before any loadAd
 *  - [findActivity] walks ContextWrapper (Compose LocalContext-safe)
 */
object AdMobBootstrap {
    private const val TAG = "AdMobBootstrap"

    /** Google's official SAMPLE AdMob App ID (must pair with sample unit ids). */
    const val SAMPLE_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    const val NATIVE_TEST_UNIT = "ca-app-pub-3940256099942544/2247696110"
    const val REWARDED_TEST_UNIT = "ca-app-pub-3940256099942544/5224354917"
    const val INTERSTITIAL_TEST_UNIT = "ca-app-pub-3940256099942544/1033173712"

    private val started = AtomicBoolean(false)
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    @Volatile
    private var usingSampleAppId: Boolean = true

    @Volatile
    private var appContext: Context? = null

    /** True once [MobileAds.initialize] has completed (or timed out optimistically). */
    val isReady: Boolean get() = _ready.value

    /**
     * True when the manifest still ships Google's sample App ID, OR all admin unit ids
     * are blank (dev / not configured → test creatives).
     */
    fun isTestMode(context: Context? = null): Boolean {
        if (usingSampleAppId) return true
        val rc = com.ahamai.app.data.RemoteConfigManager
        return rc.chatNativeAdUnit.isBlank() &&
            rc.agentRewardedAdUnit.isBlank() &&
            rc.agentInterstitialAdUnit.isBlank()
    }

    /**
     * Call from [Activity.onCreate] on the **main thread**, as early as possible.
     * Safe to call multiple times.
     */
    fun start(context: Context) {
        appContext = context.applicationContext
        if (!started.compareAndSet(false, true)) {
            // Already started — if ready was never set, kick safety again
            if (!_ready.value) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!_ready.value) {
                        _ready.value = true
                        Log.i(TAG, "Mobile Ads ready (late safety)")
                    }
                }, 1_500L)
            }
            return
        }
        val app = context.applicationContext
        usingSampleAppId = readIsSampleAppId(app)
        Log.i(TAG, "Starting Mobile Ads (sampleAppId=$usingSampleAppId)")

        val main = Handler(Looper.getMainLooper())
        fun applyConfigAndInit() {
            runCatching {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(
                            listOf(
                                AdRequest.DEVICE_ID_EMULATOR,
                                // Common debug builds — GMA logs the real hash in logcat if needed
                            )
                        )
                        .build()
                )
            }.onFailure { Log.w(TAG, "setRequestConfiguration: ${it.message}") }

            val markReady = Runnable {
                if (!_ready.value) {
                    _ready.value = true
                    Log.i(TAG, "Mobile Ads ready (sampleAppId=$usingSampleAppId)")
                }
            }
            runCatching {
                MobileAds.initialize(app) {
                    main.post(markReady)
                }
            }.onFailure {
                Log.e(TAG, "MobileAds.initialize threw: ${it.message}")
                main.post(markReady)
            }
            // Never block forever if the callback is dropped
            main.postDelayed(markReady, 3_000L)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyConfigAndInit()
        } else {
            main.post { applyConfigAndInit() }
        }
    }

    private fun readIsSampleAppId(context: Context): Boolean {
        return runCatching {
            val ai = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            val id = ai.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID").orEmpty()
            Log.i(TAG, "Manifest APPLICATION_ID=${id.take(28)}…")
            id.isBlank() || id == SAMPLE_APP_ID
        }.getOrDefault(true)
    }

    /**
     * Resolve unit id: sample App ID → always test units.
     * Real App ID + blank/invalid admin unit → test unit fallback.
     */
    fun resolveUnit(configured: String?, testUnit: String): String {
        if (usingSampleAppId) return testUnit
        val clean = com.ahamai.app.data.RemoteConfigManager.sanitizeAdUnit(configured)
        return clean.ifBlank { testUnit }
    }

    /** Walk ContextWrapper chain to find the hosting Activity (Compose LocalContext-safe). */
    fun findActivity(context: Context?): Activity? {
        var ctx = context
        var depth = 0
        while (ctx != null && depth < 16) {
            when (ctx) {
                is Activity -> return if (ctx.isFinishing || ctx.isDestroyed) null else ctx
                is ContextWrapper -> ctx = ctx.baseContext
                else -> return null
            }
            depth++
        }
        return null
    }

    fun applicationContext(): Context? = appContext
}
