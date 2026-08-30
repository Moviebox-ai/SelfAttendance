package com.aaryo.selfattendance.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

enum class RewardedAdResult {
    EARNED,
    DISMISSED,
    FAILED,
    NOT_READY;

    val isEarned: Boolean get() = this == EARNED
}

// ═══════════════════════════════════════════════════════════════
//  RewardedAdManager
//
//  Loads and shows a Rewarded Video Ad.
//  Reward: user earns "ad-free" mode for AD_FREE_DURATION_MS.
//
//  Policy:
//   • Ad is preloaded on init so it is ready instantly.
//   • showAd() is a no-op when ad isn't loaded or not ready.
//   • onRewarded callback fires only when user completes the ad.
//   • Exponential backoff retry on load failure (capped at 30 min).
// ═══════════════════════════════════════════════════════════════

class RewardedAdManager(private val appContext: Context) {

    companion object {
        private const val TAG = "RewardedAdManager"

        /** Reward duration — user gets ad-free for 30 minutes. */
        const val AD_FREE_DURATION_MS = 30 * 60 * 1_000L

        private const val RETRY_BASE_MS = 30_000L
        private const val RETRY_MAX_MS  = 30 * 60 * 1_000L

        private val rewardedAdUnitId: String
            get() = if (BuildConfig.DEBUG)
                "ca-app-pub-3940256099942544/5224354917"   // Test ID
            else
                "ca-app-pub-5703232582358249/7486230006"   // Rewarded Ad Unit
    }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())

    // ── Load ──────────────────────────────────────────────────────────────

    fun preload() {
        if (!AdsController.isAdsEnabled || !AdsController.mobileAdsReady) return
        load()
    }

    private fun load() {
        // preload() checks isAdsEnabled, but onAdDismissedFullScreenContent and
        // onAdFailedToShowFullScreenContent both call load() directly — bypassing
        // the isAdsEnabled check in preload(). If ads are disabled mid-session
        // (e.g. Remote Config update), rewarded ad keeps reloading silently.
        if (!AdsController.isAdsEnabled || !AdsController.mobileAdsReady) return
        if (isLoading || rewardedAd != null) return
        isLoading = true
        Log.d(TAG, "Loading rewarded ad (attempt ${retryCount + 1})…")

        RewardedAd.load(
            appContext,
            rewardedAdUnitId,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd  = ad
                    isLoading   = false
                    retryCount  = 0
                    Log.d(TAG, "Rewarded ad loaded ✓")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(TAG, "Rewarded ad load failed (${error.code}): ${error.message}")
                    rewardedAd = null
                    isLoading  = false
                    val delay = minOf(RETRY_BASE_MS * (1L shl retryCount), RETRY_MAX_MS)
                    retryCount++
                    handler.postDelayed({
                        if (rewardedAd == null && !isLoading && AdsController.isAdsEnabled) load()
                    }, delay)
                }
            }
        )
    }

    // ── Show ──────────────────────────────────────────────────────────────

    /**
     * Show the rewarded ad.
     *
     * @param activity    Current foreground Activity.
     * @param onRewarded  Called when user earns the reward (ad fully watched).
     * @param onNotReady  Called when no ad is loaded yet (show a "try again" message).
     */
    fun showAd(
        activity   : Activity,
        onRewarded : () -> Unit,
        onNotReady : () -> Unit = {}
    ) {
        showAd(activity) { result ->
            if (result == RewardedAdResult.EARNED) {
                onRewarded()
            } else if (result == RewardedAdResult.NOT_READY) {
                onNotReady()
            }
        }
    }

    fun showAd(
        activity : Activity,
        onResult : (RewardedAdResult) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.d(TAG, "Rewarded ad not ready — triggering preload")
            load()
            onResult(RewardedAdResult.NOT_READY)
            return
        }

        var rewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                AdsController.recordAdShown()
                rewardedAd = null
                Log.d(TAG, "Rewarded ad showing ✓")
            }
            override fun onAdDismissedFullScreenContent() {
                retryCount = 0
                load()
                Log.d(TAG, "Rewarded ad dismissed — reloading")
                AdsController.onFullScreenAdDismissed()
                if (!rewardEarned) {
                    onResult(RewardedAdResult.DISMISSED)
                }
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                Log.e(TAG, "Rewarded ad show failed: ${error.message}")
                val delay = minOf(RETRY_BASE_MS * (1L shl retryCount), RETRY_MAX_MS)
                retryCount++
                handler.postDelayed({ load() }, delay)
                onResult(RewardedAdResult.FAILED)
            }
        }

        ad.show(activity) { _ ->
            Log.d(TAG, "User earned reward ✓")
            rewardEarned = true
            onResult(RewardedAdResult.EARNED)
        }
    }

    fun showRewardedAd(
        activity : Activity,
        onResult : (RewardedAdResult) -> Unit
    ) {
        showAd(activity, onResult)
    }

    fun showRewardedAd(
        activity   : Activity,
        onRewarded : () -> Unit,
        onNotReady : () -> Unit = {}
    ) {
        showAd(activity, onRewarded, onNotReady)
    }

    /** True when a rewarded ad is loaded and ready to show. */
    fun isReady(): Boolean = rewardedAd != null

    val isAdReady: Boolean
        get() = isReady()

    @JvmName("isAdReadyFunc")
    fun isAdReady(): Boolean = isReady()

    fun destroy() {
        handler.removeCallbacksAndMessages(null)
        rewardedAd = null
    }
}
