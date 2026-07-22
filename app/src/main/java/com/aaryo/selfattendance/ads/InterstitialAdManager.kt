package com.aaryo.selfattendance.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * InterstitialAdManager — dedicated class for interstitial lifecycle.
 *
 * AD AVAILABILITY FIX:
 * onAdFailedToLoad now schedules an exponential-backoff retry via Handler
 * so the interstitial slot is never permanently empty after a transient
 * network error or AdMob rate-limit response.
 * A periodic health-check fires every HEALTH_CHECK_MS to guarantee the
 * ad is always pre-loaded and ready.
 */
class InterstitialAdManager(private val context: Context) {

    companion object {
        private const val TAG               = "InterstitialAdManager"
        private const val FREQUENCY         = 3           // show every N saves (AdMob policy: must not show on every action)
        private const val SHOW_DELAY_MS     = 400L        // ms after save success

        // ── Retry / health-check timings ──────────────────────────────────
        private const val RETRY_BASE_MS     = 30_000L            // 30 s base
        private const val RETRY_MAX_MS      = 30 * 60 * 1_000L   // 30 min cap
        private const val HEALTH_CHECK_MS   = 5 * 60 * 1_000L    // 5 min
    }

    private val adUnitId: String
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/1033173712"
        else
            "ca-app-pub-5703232582358249/4828770637"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading      = false
    // FIX: Use Long to avoid Int overflow after ~2 billion saves.
    private var saveEventCount = 0L
    private var retryCount     = 0

    private val handler = Handler(Looper.getMainLooper())

    // ── Periodic health-check ─────────────────────────────────────────────────

    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            if (AdsController.isAdsEnabled && AdsController.mobileAdsReady &&
                interstitialAd == null && !isLoading) {
                Log.d(TAG, "Health-check: interstitial slot empty — triggering preload")
                loadAd()
            }
            handler.postDelayed(this, HEALTH_CHECK_MS)
        }
    }

    init {
        handler.postDelayed(healthCheckRunnable, HEALTH_CHECK_MS)
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    fun preload() {
        if (!AdsController.isAdsEnabled) return
        if (!AdsController.mobileAdsReady) return   // must not call AdMob before MobileAds.initialize()
        if (isLoading || interstitialAd != null) return
        loadAd()
    }

    private fun loadAd() {
        if (isLoading) return
        if (!AdsController.mobileAdsReady) return   // guard against init-order crash
        isLoading = true
        Log.d(TAG, "Loading interstitial (attempt ${retryCount + 1})…")

        try {
            InterstitialAd.load(
                context,
                adUnitId,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoading      = false
                        retryCount     = 0
                        Log.d(TAG, "Interstitial preloaded ✓")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isLoading      = false
                        Log.e(TAG, "Interstitial load failed (code ${error.code}): ${error.message}")
                        scheduleRetry()
                    }
                }
            )
        } catch (e: Exception) {
            isLoading = false
            Log.e(TAG, "InterstitialAd.load() threw: ${e.message}")
        }
    }

    private fun scheduleRetry() {
        val delayMs = minOf(RETRY_BASE_MS * (1L shl retryCount), RETRY_MAX_MS)
        retryCount++
        Log.d(TAG, "Interstitial retry #$retryCount in ${delayMs / 1000}s")
        handler.postDelayed({
            if (interstitialAd == null && !isLoading &&
                AdsController.isAdsEnabled && AdsController.mobileAdsReady) {
                loadAd()
            }
        }, delayMs)
    }

    // ── Show after save ───────────────────────────────────────────────────────

    /**
     * Call this AFTER a successful overtime save.
     * The save result is already committed — this never blocks it.
     */
    fun showAfterOvertimeSave(
        activity: Activity,
        scope: CoroutineScope
    ) {
        saveEventCount++
        Log.d(TAG, "Save event #$saveEventCount")

        if (saveEventCount % FREQUENCY.toLong() != 0L) {
            Log.d(TAG, "Interstitial — frequency not met, skipping")
            preload()
            return
        }

        if (!AdsController.canShowAd()) {
            Log.d(TAG, "Interstitial — global cooldown active, skipping")
            preload()
            return
        }

        val ad = interstitialAd ?: run {
            Log.d(TAG, "Interstitial — not ready, skipping gracefully")
            preload()
            return
        }

        scope.launch(Dispatchers.Main) {
            delay(SHOW_DELAY_MS)

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    AdsController.recordAdShown()
                    interstitialAd = null
                    retryCount     = 0
                    Log.d(TAG, "Interstitial shown ✓")
                }
                override fun onAdDismissedFullScreenContent() {
                    retryCount = 0
                    preload()
                    Log.d(TAG, "Interstitial dismissed — preloading next")
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    Log.e(TAG, "Interstitial show failed: ${error.message}")
                    scheduleRetry()
                }
            }

            ad.show(activity)
        }
    }

    /** Call when the manager is no longer needed to stop background retries. */
    fun destroy() {
        handler.removeCallbacksAndMessages(null)
    }
}
