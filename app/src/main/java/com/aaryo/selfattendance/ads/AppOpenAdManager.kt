package com.aaryo.selfattendance.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.R
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd

// Google's official test App Open Ad ID
private const val TEST_APP_OPEN_ID = "ca-app-pub-3940256099942544/9257395921"

class AppOpenAdManager(private val app: Application) :
    Application.ActivityLifecycleCallbacks {

    private var appOpenAd: AppOpenAd? = null
    private var currentActivity: Activity? = null
    private var isLoading = false
    private var isShowingAd = false

    // AdMob policy: minimum 4 hours between app open ads
    private var lastShown = 0L
    private val COOLDOWN_MS = 4 * 60 * 60 * 1000L // 4 hours

    // Splash/Auth composables MainActivity ke andar hain — alag Activity class nahi hain.
    // Consent check hi primary gate hai — bina consent ke ad kabhi nahi dikhega.
    // Isliye activity name based exclusion ki zaroorat nahi.

    init {
        app.registerActivityLifecycleCallbacks(this)
        loadAd()
    }

    // ----------------------------------------------------------------
    // LOAD AD
    // ----------------------------------------------------------------

    private fun loadAd() {

        if (isLoading || appOpenAd != null) return

        val adUnit = if (BuildConfig.DEBUG) {
            TEST_APP_OPEN_ID
        } else {
            app.getString(R.string.app_open_ad_unit_id)
        }

        isLoading = true

        val request = AdRequest.Builder().build()

        AppOpenAd.load(
            app,
            adUnit,
            request,
            object : AppOpenAd.AppOpenAdLoadCallback() {

                override fun onAdLoaded(ad: AppOpenAd) {
                    Log.d("AppOpenAd", "Ad Loaded")
                    appOpenAd = ad
                    isLoading = false
                    setAdCallbacks()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e("AppOpenAd", "Load failed: ${error.message}")
                    appOpenAd = null
                    isLoading = false
                }
            }
        )
    }

    // ----------------------------------------------------------------
    // SET CALLBACKS
    // ----------------------------------------------------------------

    private fun setAdCallbacks() {

        appOpenAd?.fullScreenContentCallback =
            object : FullScreenContentCallback() {

                override fun onAdDismissedFullScreenContent() {
                    isShowingAd = false
                    lastShown = System.currentTimeMillis()
                    appOpenAd = null
                    loadAd()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.e("AppOpenAd", "Show failed: ${error.message}")
                    isShowingAd = false
                    appOpenAd = null
                    loadAd()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d("AppOpenAd", "Ad shown")
                    isShowingAd = true
                }
            }
    }

    // ----------------------------------------------------------------
    // SHOW AD
    // ----------------------------------------------------------------

    fun showAdIfAvailable() {

        if (isShowingAd) return

        val activity = currentActivity ?: return

        // Activity validity check
        if (activity.isFinishing || activity.isDestroyed) return

        // ✅ CONSENT CHECK — bina consent ke ad kabhi nahi dikhega
        // AdMob policy: user ne consent diya ho ya region mein required na ho
        if (!ConsentManager.canShowAds(activity)) {
            Log.d("AppOpenAd", "Consent not obtained — skipping ad")
            return
        }

        // Cooldown check
        val now = System.currentTimeMillis()
        if (now - lastShown < COOLDOWN_MS) return

        if (appOpenAd != null) {
            appOpenAd?.show(activity)
        } else {
            loadAd()
        }
    }

    // ----------------------------------------------------------------
    // LIFECYCLE CALLBACKS
    // ----------------------------------------------------------------

    override fun onActivityStarted(activity: Activity) {
        if (!isShowingAd) currentActivity = activity
    }

    override fun onActivityResumed(activity: Activity) {
        if (!isShowingAd) {
            currentActivity = activity
            showAdIfAvailable()
        }
    }

    override fun onActivityPaused(activity: Activity) {}

    override fun onActivityStopped(activity: Activity) {}

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
