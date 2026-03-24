package com.aaryo.selfattendance

import android.app.Application
import android.util.Log
import com.aaryo.selfattendance.ads.AppOpenAdManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*

class SelfAttendanceApp : Application() {

    lateinit var appOpenAdManager: AppOpenAdManager

    private val applicationScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {

        super.onCreate()

        initFirebase()

        initCrashlytics()

        // ✅ FIX: MobileAds.initialize() consent ke BAAD hota hai.
        // MainActivity mein ConsentManager.requestConsent() → MobileAds.initialize()
        // Yahan se initAds() call hata diya.

        initAppOpenAds()

        initRemoteConfig()
    }

    // ---------------- FIREBASE ----------------

    private fun initFirebase() {

        try {

            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }

        } catch (e: Exception) {

            Log.e("AppInit", "Firebase init failed", e)
        }
    }

    // ---------------- CRASHLYTICS ----------------

    private fun initCrashlytics() {

        try {

            FirebaseCrashlytics
                .getInstance()
                .setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)

        } catch (e: Exception) {

            Log.e("AppInit", "Crashlytics init failed", e)
        }
    }

    // ---------------- ADMOB ----------------
    // ✅ FIX: MobileAds.initialize() ab ConsentManager ke andar hota hai
    // consent ke BAAD — MainActivity → ConsentManager → MobileAds.initialize()
    // initAds() yahan se REMOVE kar diya gaya hai

    // ---------------- APP OPEN ADS ----------------

    private fun initAppOpenAds() {

        try {

            appOpenAdManager = AppOpenAdManager(this)

        } catch (e: Exception) {

            Log.e("AppInit", "AppOpenAdManager init failed", e)
        }
    }

    // ---------------- REMOTE CONFIG ----------------

    private fun initRemoteConfig() {

        applicationScope.launch {

            try {

                // ✅ FIX: RemoteConfigManager() → RemoteConfigManager.getInstance()
                // Singleton use karo taaki DashboardScreen ka instance aur yeh
                // dono ek hi Firebase config share karein
                RemoteConfigManager.getInstance().fetch()

            } catch (e: Exception) {

                Log.e("RemoteConfig", "Fetch failed", e)
            }
        }
    }
}
