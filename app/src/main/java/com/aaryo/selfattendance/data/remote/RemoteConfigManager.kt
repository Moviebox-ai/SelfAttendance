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
            "show_rewarded_ad"        to true,
            "app_enabled"             to true,
            "allow_screenshot"        to true,
            "coin_ad_reward_amount"   to 10L,
            "coin_daily_limit"        to 50L,
            "coin_daily_login_bonus"  to 5L,
            "reward_system_enabled"   to true,

            // ── Alternative (Direct) Update System ──────────────────────────
            // Set these in Firebase Console to push updates outside Play Store
            "alt_update_enabled"      to false,      // master switch
            "alt_update_version_code" to 0L,         // minimum required versionCode
            "alt_update_version_name" to "",         // display version e.g. "2.1.0"
            "alt_update_apk_url"      to "",         // direct APK download URL (Firebase Storage / CDN)
            "alt_update_changelog"    to "",         // what's new text shown to user
            "alt_update_force"        to false,      // true = user cannot skip
            "alt_update_title"        to "Naya Update Available!"  // dialog title
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
    fun showRewardedAd()     = isAdsEnabled() && remoteConfig.getBoolean("show_rewarded_ad")

    fun isAppEnabled()    = remoteConfig.getBoolean("app_enabled")
    fun allowScreenshot() = remoteConfig.getBoolean("allow_screenshot")

    fun coinAdRewardAmount()    = remoteConfig.getLong("coin_ad_reward_amount").toInt()
    fun coinDailyLimit()        = remoteConfig.getLong("coin_daily_limit").toInt()
    fun coinDailyBonus()        = remoteConfig.getLong("coin_daily_login_bonus").toInt()
    fun isRewardSystemEnabled() = remoteConfig.getBoolean("reward_system_enabled")

    // ── Alternative Update System getters ────────────────────────────────────
    fun isAltUpdateEnabled()    = remoteConfig.getBoolean("alt_update_enabled")
    fun altUpdateVersionCode()  = remoteConfig.getLong("alt_update_version_code").toInt()
    fun altUpdateVersionName()  = remoteConfig.getString("alt_update_version_name")
    fun altUpdateApkUrl()       = remoteConfig.getString("alt_update_apk_url")
    fun altUpdateChangelog()    = remoteConfig.getString("alt_update_changelog")
    fun isAltUpdateForced()     = remoteConfig.getBoolean("alt_update_force")
    fun altUpdateTitle()        = remoteConfig.getString("alt_update_title")

    fun getString(key: String): String = remoteConfig.getString(key)
}
