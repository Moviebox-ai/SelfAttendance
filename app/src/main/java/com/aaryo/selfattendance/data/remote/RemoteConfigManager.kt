package com.aaryo.selfattendance.data.remote

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

class RemoteConfigManager {

    private val remoteConfig: FirebaseRemoteConfig =
        FirebaseRemoteConfig.getInstance()

    init {

        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()

        remoteConfig.setConfigSettingsAsync(configSettings)

        val defaults = mapOf(

            // ---------------- APP CONTROL ----------------
            "app_enabled" to true,

            // ---------------- ADS CONTROL ----------------
            "ads_enabled" to true,
            "show_banner" to true,
            "show_native" to true,
            "show_interstitial" to true,

            // ---------------- SECURITY ----------------
            "allow_screenshot" to true
        )

        remoteConfig.setDefaultsAsync(defaults)
    }

    // ---------------- FETCH ----------------

    suspend fun fetch() {

        try {

            remoteConfig.fetchAndActivate().await()

        } catch (e: Exception) {

            Log.e(
                "RemoteConfig",
                "Fetch failed",
                e
            )
        }
    }

    // ---------------- APP STATUS ----------------

    fun isAppEnabled(): Boolean {

        return try {
            remoteConfig.getBoolean("app_enabled")
        } catch (e: Exception) {
            true
        }
    }

    // ---------------- ADS ----------------

    fun adsEnabled(): Boolean {

        return try {
            remoteConfig.getBoolean("ads_enabled")
        } catch (e: Exception) {
            true
        }
    }

    fun showBannerAd(): Boolean {

        // ✅ FIX: try-catch add kiya — exception aane par default true return hoga
        return try {
            adsEnabled() && remoteConfig.getBoolean("show_banner")
        } catch (e: Exception) {
            true
        }
    }

    fun showNativeAd(): Boolean {

        return try {
            adsEnabled() && remoteConfig.getBoolean("show_native")
        } catch (e: Exception) {
            true
        }
    }

    fun showInterstitialAd(): Boolean {

        return try {
            adsEnabled() && remoteConfig.getBoolean("show_interstitial")
        } catch (e: Exception) {
            true
        }
    }

    // ---------------- SCREENSHOT CONTROL ----------------

    fun allowScreenshot(): Boolean {

        return try {
            remoteConfig.getBoolean("allow_screenshot")
        } catch (e: Exception) {
            true
        }
    }

    // ✅ FIX: Singleton — SelfAttendanceApp aur DashboardScreen
    // dono ek hi instance use karenge
    companion object {
        @Volatile
        private var INSTANCE: RemoteConfigManager? = null

        fun getInstance(): RemoteConfigManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RemoteConfigManager().also { INSTANCE = it }
            }
        }
    }
}
