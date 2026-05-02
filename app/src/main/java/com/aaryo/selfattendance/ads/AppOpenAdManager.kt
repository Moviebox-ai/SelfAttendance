package com.aaryo.selfattendance.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aaryo.selfattendance.BuildConfig
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import java.util.Date

/**
 * AppOpenAdManager — handles App Open Ad lifecycle.
 *
 * RULES (Google AdMob policy):
 *  • Never show during onboarding / login (AdsController.isOnboardingActive guard).
 *  • Never show immediately after another full-screen ad (global cooldown via AdsController).
 *  • Background-to-foreground cooldown: 30 seconds minimum before showing.
 *  • Ad expires after 4 hours and is discarded.
 *  • Show is gated by AdsController.canShowAd() — single source of truth.
 */
class AppOpenAdManager(private val application: Application) :
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_EXPIRY_MS     = 4 * 60 * 60 * 1_000L  // 4 hours
        /** Min time app must have been in background before showing on resume. */
        private const val BG_MIN_PAUSE_MS  = 30_000L                // 30 seconds
    }

    private val adUnitId: String
        get() = if (BuildConfig.DEBUG)
            "ca-app-pub-3940256099942544/9257395921"
        else
            "ca-app-pub-5703232582358249/9541869822"

    private var appOpenAd: AppOpenAd? = null
    private var isLoadingAd  = false
    private var loadTime: Long = 0L
    private var isShowingAd  = false

    /** Timestamp when app last went to background. */
    private var backgroundedAt: Long = 0L

    private var currentActivity: Activity? = null

    // ── Registration ──────────────────────────────────────────────────────

    fun register() {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ── ProcessLifecycleObserver (app foreground / background) ────────────

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (!AdsController.isAdsEnabled) return
        if (AdsController.isOnboardingActive) {
            Log.d(TAG, "Skipping App Open — onboarding active")
            return
        }

        // Only show if app was backgrounded long enough (not just a quick switch)
        val pauseDuration = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt > 0 && pauseDuration < BG_MIN_PAUSE_MS) {
            Log.d(TAG, "Skipping App Open — too short background pause (${pauseDuration}ms)")
            return
        }

        // Delegate to global cooldown guard
        if (!AdsController.canShowAd()) return

        showAdIfAvailable()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        backgroundedAt = System.currentTimeMillis()
    }

    // ── Load ──────────────────────────────────────────────────────────────

    fun loadAd() {
        if (!AdsController.isAdsEnabled) return
        if (isLoadingAd) return
        if (isAdAvailable()) return   // still have a fresh, valid ad

        isLoadingAd = true
        Log.d(TAG, "Loading App Open Ad…")

        AppOpenAd.load(
            application,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTime  = Date().time
                    isLoadingAd = false
                    Log.d(TAG, "App Open Ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoadingAd = false
                    Log.e(TAG, "App Open Ad load failed: ${error.message}")
                }
            }
        )
    }

    // ── Show ──────────────────────────────────────────────────────────────

    fun showAdIfAvailable() {
        if (isShowingAd) {
            Log.d(TAG, "Ad already showing — skipping")
            return
        }
        if (!isAdAvailable()) {
            Log.d(TAG, "Ad not ready — triggering load")
            loadAd()
            return
        }

        val activity = currentActivity ?: run {
            Log.w(TAG, "No foreground activity — skipping")
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                isShowingAd = true
                AdsController.recordAdShown()    // update global cooldown
                Log.d(TAG, "App Open Ad shown")
            }
            override fun onAdDismissedFullScreenContent() {
                isShowingAd  = false
                appOpenAd    = null
                loadAd()                         // preload the next one
                Log.d(TAG, "App Open Ad dismissed — loading next")
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isShowingAd = false
                appOpenAd   = null
                loadAd()
                Log.e(TAG, "App Open Ad show failed: ${error.message}")
            }
        }

        appOpenAd?.show(activity)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun isAdAvailable(): Boolean {
        val ad = appOpenAd ?: return false
        val isExpired = Date().time - loadTime > AD_EXPIRY_MS
        if (isExpired) {
            Log.d(TAG, "App Open Ad expired — discarding")
            appOpenAd = null
            return false
        }
        return true
    }

    // ── Activity lifecycle ────────────────────────────────────────────────

    override fun onActivityCreated(activity: Activity, bundle: Bundle?) {}

    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isShowingAd) currentActivity = activity
    }

    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, bundle: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) currentActivity = null
    }
}
