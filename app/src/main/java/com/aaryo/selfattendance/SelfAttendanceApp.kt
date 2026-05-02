package com.aaryo.selfattendance

import android.app.Application
import android.util.Log
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.ads.AppOpenAdManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*

class SelfAttendanceApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Manages App Open Ads triggered by ProcessLifecycleOwner on each
     * foreground event. Exposed so MainActivity can call loadAd() after
     * consent is obtained.
     */
    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun onCreate() {
        super.onCreate()
        initFirebase()
        initCrashlytics()
        initAdsSystem()
        initRemoteConfig()
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
        } catch (e: Exception) {
            Log.e("AppInit", "Firebase init failed", e)
        }
    }

    private fun initCrashlytics() {
        try {
            FirebaseCrashlytics.getInstance()
                .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
        } catch (e: Exception) {
            Log.e("AppInit", "Crashlytics init failed", e)
        }
    }

    private fun initAdsSystem() {
        try {
            // Only sets isAdsEnabled flag.
            // MobileAds.initialize() is deferred to MainActivity after user consent.
            AdsController.init(this)

            // Block app-open ads during onboarding — will be cleared by MainActivity
            // once the user is past splash/auth.
            AdsController.isOnboardingActive = true

            appOpenAdManager = AppOpenAdManager(this)
            appOpenAdManager.register()
        } catch (e: Exception) {
            Log.e("AppInit", "AdsController init failed", e)
        }
    }

    private fun initRemoteConfig() {
        applicationScope.launch {
            try {
                val remote = RemoteConfigManager.getInstance()
                remote.fetch()
                val adsEnabled = remote.isAdsEnabled()
                withContext(Dispatchers.Main) {
                    AdsController.isAdsEnabled = adsEnabled
                }
            } catch (e: Exception) {
                Log.e("RemoteConfig", "Fetch failed", e)
            }
        }
    }
}
