package com.aaryo.selfattendance.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 *
 * COLD START FIX:
 *  • showOnColdStart() is called from MainActivity.initAds() after onboarding ends.
 *  • If the ad is already loaded → shown immediately.
 *  • If ad is still loading → showPendingOnLoad flag is set; ad shows as soon as
 *    onAdLoaded() fires (with a 300 ms settle delay for the UI).
 *  • This means the App Open Ad now shows on EVERY app open — both cold start
 *    and background-to-foreground.
 *
 * AUTO-RELOAD FIX:
 *  • When an ad loads successfully, a Handler is scheduled to fire
 *    RELOAD_AHEAD_MS (30 min) before the 4-hour expiry window closes.
 *  • This preloads the next ad while the current one is still valid, so
 *    there is never a gap where the ad expired but no replacement is ready.
 *  • The scheduled reload is cancelled if the ad is shown/dismissed early
 *    (loadAd() is called immediately after dismiss anyway).
 */
class AppOpenAdManager(private val application: Application) :
    Application.ActivityLifecycleCallbacks,
    DefaultLifecycleObserver {

    companion object {
        private const val TAG = "AppOpenAdManager"
        private const val AD_EXPIRY_MS    = 4 * 60 * 60 * 1_000L  // 4 hours (AdMob limit)
        /** Start loading the next ad this many ms before expiry (30 minutes). */
        private const val RELOAD_AHEAD_MS = 30 * 60 * 1_000L       // 30 minutes
        /** Delay after which we schedule a proactive reload: 3.5 hours after load. */
        private const val RELOAD_DELAY_MS = AD_EXPIRY_MS - RELOAD_AHEAD_MS
        /** Min time app must have been in background before showing on bg→fg resume. */
        private const val BG_MIN_PAUSE_MS = 30_000L                // 30 seconds
        /** Small settle delay before showing ad on cold start (UI renders first). */
        private const val COLD_START_SHOW_DELAY_MS = 300L
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

    /**
     * FIX: Set to true by showOnColdStart() when the ad isn't loaded yet.
     * Cleared as soon as the ad is shown (or if show fails).
     * This lets onAdLoaded() auto-show the ad once it arrives.
     */
    private var showPendingOnLoad = false
    /** Guard against double-show when showOnColdStart is called more than once. */
    private var coldStartShowScheduled = false

    private var currentActivity: Activity? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // ── Proactive reload scheduler ─────────────────────────────────────────
    private val reloadHandler = Handler(Looper.getMainLooper())
    private val reloadRunnable = Runnable {
        Log.d(TAG, "Proactive reload triggered (ad nearing expiry)")
        appOpenAd = null
        loadAd()
    }

    private fun scheduleProactiveReload() {
        reloadHandler.removeCallbacks(reloadRunnable)
        reloadHandler.postDelayed(reloadRunnable, RELOAD_DELAY_MS)
        Log.d(TAG, "Proactive reload scheduled in ${RELOAD_DELAY_MS / 60_000} min")
    }

    private fun cancelProactiveReload() {
        reloadHandler.removeCallbacks(reloadRunnable)
    }

    // ── Registration ──────────────────────────────────────────────────────

    fun register() {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    // ── ProcessLifecycleObserver (background → foreground) ────────────────

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (!AdsController.isAdsEnabled) return
        if (AdsController.isOnboardingActive) {
            // Cold start: onboarding is still active here — showOnColdStart()
            // will handle the show once initAds() sets isOnboardingActive=false.
            Log.d(TAG, "onStart: onboarding active — cold start show will be handled by showOnColdStart()")
            return
        }

        // Background → foreground: only show if backgrounded long enough
        val pauseDuration = System.currentTimeMillis() - backgroundedAt
        if (backgroundedAt > 0 && pauseDuration < BG_MIN_PAUSE_MS) {
            Log.d(TAG, "Skipping App Open — too short background pause (${pauseDuration}ms)")
            return
        }

        if (!AdsController.canShowAd()) return

        showAdIfAvailable()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        backgroundedAt = System.currentTimeMillis()
    }

    // ── Cold Start Show ───────────────────────────────────────────────────

    /**
     * Call this from MainActivity.initAds() AFTER setting isOnboardingActive = false.
     *
     * Har fresh app open (cold start) pe App Open Ad dikhane ke liye.
     *
     * - Agar ad already loaded hai → 300ms settle delay ke baad show hoga.
     * - Agar ad abhi load ho raha hai → showPendingOnLoad = true set hoga;
     *   jab onAdLoaded() fire karega toh automatically show ho jaayega.
     */
    fun showOnColdStart() {
        Log.d(TAG, "showOnColdStart() called")

        if (!AdsController.isAdsEnabled) {
            Log.d(TAG, "showOnColdStart: ads disabled — skipping")
            return
        }

        // FIX: Guard against double invocation (e.g. rotation + init race).
        if (coldStartShowScheduled) {
            Log.d(TAG, "showOnColdStart: already scheduled — ignoring duplicate call")
            return
        }

        if (isAdAvailable()) {
            // Ad ready hai — thoda UI settle hone de phir show karo
            Log.d(TAG, "showOnColdStart: ad available — showing after ${COLD_START_SHOW_DELAY_MS}ms")
            coldStartShowScheduled = true
            mainHandler.postDelayed({
                coldStartShowScheduled = false
                if (!AdsController.isOnboardingActive && AdsController.isAdsEnabled) {
                    showAdIfAvailable()
                }
            }, COLD_START_SHOW_DELAY_MS)
        } else {
            // Ad abhi load nahi hua — flag set karo, onAdLoaded() mein show hoga
            Log.d(TAG, "showOnColdStart: ad not ready — pending show on load")
            coldStartShowScheduled = true
            showPendingOnLoad = true
            loadAd()
        }
    }

    // ── Load ──────────────────────────────────────────────────────────────

    fun loadAd() {
        if (!AdsController.isAdsEnabled) return
        if (!AdsController.mobileAdsReady) {
            Log.d(TAG, "MobileAds not ready yet — deferring App Open Ad load")
            return
        }
        if (isLoadingAd) return
        if (isAdAvailable()) return

        isLoadingAd = true
        Log.d(TAG, "Loading App Open Ad…")

        try {
            AppOpenAd.load(
                application,
                adUnitId,
                AdRequest.Builder().build(),
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        appOpenAd   = ad
                        loadTime    = Date().time
                        isLoadingAd = false
                        Log.d(TAG, "App Open Ad loaded ✓")
                        scheduleProactiveReload()

                        // FIX: Agar cold start ke liye pending show tha toh ab show karo
                        if (showPendingOnLoad && !AdsController.isOnboardingActive) {
                            showPendingOnLoad = false
                            Log.d(TAG, "Pending cold start show — showing now")
                            mainHandler.postDelayed({
                                coldStartShowScheduled = false
                                if (!AdsController.isOnboardingActive && AdsController.isAdsEnabled) {
                                    showAdIfAvailable()
                                }
                            }, COLD_START_SHOW_DELAY_MS)
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingAd           = false
                        showPendingOnLoad     = false   // fail pe flag clear karo
                        coldStartShowScheduled = false // FIX: reset guard on failure
                        Log.e(TAG, "App Open Ad load failed: ${error.message}")
                        reloadHandler.postDelayed({ loadAd() }, 5 * 60 * 1_000L)
                    }
                }
            )
        } catch (e: Exception) {
            isLoadingAd       = false
            showPendingOnLoad = false
            Log.e(TAG, "AppOpenAd.load() threw — MobileAds may not be initialized: ${e.message}")
        }
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
                AdsController.recordAdShown()
                Log.d(TAG, "App Open Ad shown ✓")
            }
            override fun onAdDismissedFullScreenContent() {
                isShowingAd       = false
                appOpenAd         = null
                showPendingOnLoad = false
                cancelProactiveReload()
                loadAd()
                Log.d(TAG, "App Open Ad dismissed — loading next")
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isShowingAd       = false
                appOpenAd         = null
                showPendingOnLoad = false
                cancelProactiveReload()
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
            Log.d(TAG, "App Open Ad expired — discarding (caller will reload)")
            appOpenAd = null
            cancelProactiveReload()
            // Do NOT call loadAd() here — showAdIfAvailable() already calls
            // loadAd() when isAdAvailable() returns false, avoiding a double-load.
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
