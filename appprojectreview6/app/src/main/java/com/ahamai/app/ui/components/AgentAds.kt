package com.ahamai.app.ui.components

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ahamai.app.data.RemoteConfigManager
import com.ahamai.app.data.UsageTracker
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Full-screen ads for AGENT mode (chat uses [ChatAdCard] instead).
 *
 * Fail-soft: load/show errors never block the agent. Preloads after bootstrap when possible.
 */
object AgentAds {
    private const val TAG = "AgentAds"

    private val rewardedUnit: String
        get() = AdMobBootstrap.resolveUnit(
            RemoteConfigManager.agentRewardedAdUnit,
            AdMobBootstrap.REWARDED_TEST_UNIT
        )
    private val interstitialUnit: String
        get() = AdMobBootstrap.resolveUnit(
            RemoteConfigManager.agentInterstitialAdUnit,
            AdMobBootstrap.INTERSTITIAL_TEST_UNIT
        )

    private val minGapMs: Long
        get() = if (AdMobBootstrap.isTestMode()) 0L
        else RemoteConfigManager.adMinGapSeconds.coerceAtLeast(0) * 1000L

    private val completionInterval: Int
        get() = if (AdMobBootstrap.isTestMode()) 1
        else RemoteConfigManager.agentCompletionInterval.coerceAtLeast(1)

    @Volatile private var completions = 0
    @Volatile private var lastFullscreenAt = 0L
    private val main = Handler(Looper.getMainLooper())

    private fun agentEligible(context: Context): Boolean {
        if (!AdMobAds.shouldShowAds(context)) {
            Log.d(TAG, "not eligible: shouldShowAds=false")
            return false
        }
        if (!RemoteConfigManager.agentAdsEnabled) {
            Log.d(TAG, "not eligible: agentAdsEnabled=false")
            return false
        }
        val gapOk = System.currentTimeMillis() - lastFullscreenAt >= minGapMs
        if (!gapOk) Log.d(TAG, "not eligible: min-gap")
        return gapOk
    }

    private fun activityOf(context: Context): Activity? {
        val act = AdMobBootstrap.findActivity(context)
        if (act == null) Log.w(TAG, "No Activity — cannot show full-screen ad")
        return act
    }

    /** Cloud APK build started → rewarded. */
    fun onBuildStart(context: Context) {
        val act = activityOf(context) ?: return
        if (!RemoteConfigManager.agentBuildAdEnabled || !agentEligible(context)) {
            Log.d(TAG, "onBuildStart skipped")
            return
        }
        showRewarded(act)
    }

    /** Free user over token allowance submits a task → rewarded. */
    fun onTaskSubmitOverLimit(context: Context) {
        val act = activityOf(context) ?: return
        if (!RemoteConfigManager.agentOverLimitAdEnabled || !agentEligible(context)) return
        if (UsageTracker.tokenFraction(context) < 1f) return
        showRewarded(act)
    }

    /** Agent task finished → throttled interstitial. */
    fun onTaskComplete(context: Context) {
        val act = activityOf(context) ?: return
        if (!RemoteConfigManager.agentCompletionAdEnabled) {
            Log.d(TAG, "onTaskComplete: completion ad disabled")
            return
        }
        if (!AdMobAds.shouldShowAds(context) || !RemoteConfigManager.agentAdsEnabled) {
            Log.d(TAG, "onTaskComplete: gate closed")
            return
        }
        completions++
        val every = completionInterval
        if (completions % every != 0) {
            Log.d(TAG, "onTaskComplete skip completions=$completions every=$every")
            return
        }
        if (System.currentTimeMillis() - lastFullscreenAt < minGapMs) {
            Log.d(TAG, "onTaskComplete min-gap")
            return
        }
        // Brief delay so UI settles after run end (show right after finish feels more reliable)
        main.postDelayed({
            if (!act.isFinishing && !act.isDestroyed) showInterstitial(act)
        }, 400L)
    }

    private fun showRewarded(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        fun doLoad(unit: String, allowFallback: Boolean) {
            if (activity.isFinishing || activity.isDestroyed) return
            Log.i(TAG, "Loading rewarded unit=$unit")
            RewardedAd.load(
                activity, unit, AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Rewarded fail code=${error.code} msg=${error.message} unit=$unit")
                        if (allowFallback && unit != AdMobBootstrap.REWARDED_TEST_UNIT) {
                            doLoad(AdMobBootstrap.REWARDED_TEST_UNIT, allowFallback = false)
                        }
                    }
                    override fun onAdLoaded(ad: RewardedAd) {
                        presentRewarded(activity, ad)
                    }
                }
            )
        }
        waitReadyThen {
            doLoad(rewardedUnit, allowFallback = rewardedUnit != AdMobBootstrap.REWARDED_TEST_UNIT)
        }
    }

    private fun presentRewarded(activity: Activity, ad: RewardedAd) {
        if (activity.isFinishing || activity.isDestroyed) return
        lastFullscreenAt = System.currentTimeMillis()
        main.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            runCatching {
                ad.show(activity, OnUserEarnedRewardListener { })
                Log.i(TAG, "Rewarded shown")
            }.onFailure { Log.e(TAG, "Rewarded show failed: ${it.message}") }
        }
    }

    private fun showInterstitial(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        fun doLoad(unit: String, allowFallback: Boolean) {
            if (activity.isFinishing || activity.isDestroyed) return
            Log.i(TAG, "Loading interstitial unit=$unit")
            InterstitialAd.load(
                activity, unit, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Interstitial fail code=${error.code} msg=${error.message} unit=$unit")
                        if (allowFallback && unit != AdMobBootstrap.INTERSTITIAL_TEST_UNIT) {
                            doLoad(AdMobBootstrap.INTERSTITIAL_TEST_UNIT, allowFallback = false)
                        }
                    }
                    override fun onAdLoaded(ad: InterstitialAd) {
                        presentInterstitial(activity, ad)
                    }
                }
            )
        }
        waitReadyThen {
            doLoad(interstitialUnit, allowFallback = interstitialUnit != AdMobBootstrap.INTERSTITIAL_TEST_UNIT)
        }
    }

    private fun presentInterstitial(activity: Activity, ad: InterstitialAd) {
        if (activity.isFinishing || activity.isDestroyed) return
        lastFullscreenAt = System.currentTimeMillis()
        main.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            runCatching {
                ad.show(activity)
                Log.i(TAG, "Interstitial shown")
            }.onFailure { Log.e(TAG, "Interstitial show failed: ${it.message}") }
        }
    }

    /** Wait up to ~6s for MobileAds init, then run [block] on main. */
    private fun waitReadyThen(block: () -> Unit) {
        main.post {
            if (AdMobBootstrap.isReady) {
                block()
                return@post
            }
            Log.d(TAG, "Waiting for MobileAds init…")
            var tries = 0
            fun tick() {
                if (AdMobBootstrap.isReady || tries >= 24) {
                    // Force ready optimistically so we still attempt load (SDK often works)
                    if (!AdMobBootstrap.isReady) {
                        Log.w(TAG, "Proceeding with ad load without ready signal")
                    }
                    block()
                } else {
                    tries++
                    main.postDelayed({ tick() }, 250L)
                }
            }
            tick()
        }
    }
}
