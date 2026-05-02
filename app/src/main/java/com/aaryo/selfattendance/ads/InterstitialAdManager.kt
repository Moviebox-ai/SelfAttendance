package com.aaryo.selfattendance.ads

import android.app.Activity
import android.content.Context
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
 * Designed for use in ViewModel / feature scope where a caller wants a
 * self-contained manager (e.g., CalendarViewModel for overtime saves).
 *
 * AdsController.showInterstitialAfterSave() can be used as an alternative
 * when you want the global singleton approach. This class exists for clean
 * architecture separation as required by the spec.
 *
 * POLICY:
 *  • Ad shown only after [SHOW_DELAY_MS] to let the save confirmation settle.
 *  • Frequency control: every [FREQUENCY] qualifying events.
 *  • Graceful fallback: if ad not loaded, skip (never block user).
 *  • AdsController.canShowAd() is always consulted first.
 */
class InterstitialAdManager(private val context: Context) {

    companion object {
        private const val TAG           = "InterstitialAdManager"
        private const val FREQUENCY     = 3          // show every N saves
        private const val SHOW_DELAY_MS = 400L       // ms after save success
    }

    private val adUnitId: String
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/1033173712"
        else
            "ca-app-pub-5703232582358249/4828770637"

    private var interstitialAd: InterstitialAd? = null
    private var isLoading      = false
    private var saveEventCount = 0

    // ── Load ──────────────────────────────────────────────────────────────

    fun preload() {
        if (!AdsController.isAdsEnabled) return
        if (isLoading || interstitialAd != null) return
        isLoading = true

        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading      = false
                    Log.d(TAG, "Interstitial preloaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading      = false
                    Log.e(TAG, "Interstitial load failed: ${error.message}")
                }
            }
        )
    }

    // ── Show after save ───────────────────────────────────────────────────

    /**
     * Call this AFTER a successful overtime save.
     * The save result is already committed — this never blocks it.
     *
     * @param activity Foreground activity to show the ad in.
     * @param scope    CoroutineScope for the delay (use viewModelScope).
     */
    fun showAfterOvertimeSave(
        activity: Activity,
        scope: CoroutineScope
    ) {
        saveEventCount++
        Log.d(TAG, "Save event #$saveEventCount")

        if (saveEventCount % FREQUENCY != 0) {
            Log.d(TAG, "Interstitial — frequency not met, skipping")
            preload()    // keep the pipeline warm
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
            delay(SHOW_DELAY_MS)  // let save confirmation UI settle

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    AdsController.recordAdShown()
                    interstitialAd = null
                    Log.d(TAG, "Interstitial shown")
                }
                override fun onAdDismissedFullScreenContent() {
                    preload()
                    Log.d(TAG, "Interstitial dismissed — preloading next")
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    interstitialAd = null
                    preload()
                    Log.e(TAG, "Interstitial show failed: ${error.message}")
                }
            }

            ad.show(activity)
        }
    }
}
