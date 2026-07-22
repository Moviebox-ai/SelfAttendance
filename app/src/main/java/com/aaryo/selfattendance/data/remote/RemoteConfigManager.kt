package com.aaryo.selfattendance.data.remote

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

object RemoteConfigManager {

    fun getInstance(): RemoteConfigManager = this

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val settings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600)
            .build()
        remoteConfig.setConfigSettingsAsync(settings)

        val defaults = mapOf(
            "ads_enabled"             to true,
            "show_banner_ad"          to true,
            "show_native_ad"          to true,
            "show_interstitial_ad"    to true,
            "app_enabled"             to true,
            "allow_screenshot"        to true
        )
        remoteConfig.setDefaultsAsync(defaults)
    }

    suspend fun fetch() {
        try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            Log.e("RemoteConfig", "Fetch failed", e)
        }
    }

    fun isAdsEnabled()       = remoteConfig.getBoolean("ads_enabled")
    fun showBannerAd()       = isAdsEnabled() && remoteConfig.getBoolean("show_banner_ad")
    fun showNativeAd()       = isAdsEnabled() && remoteConfig.getBoolean("show_native_ad")
    fun showInterstitialAd() = isAdsEnabled() && remoteConfig.getBoolean("show_interstitial_ad")

    fun isAppEnabled()    = remoteConfig.getBoolean("app_enabled")
    fun allowScreenshot() = remoteConfig.getBoolean("allow_screenshot")

    fun getString(key: String): String = remoteConfig.getString(key)
}
