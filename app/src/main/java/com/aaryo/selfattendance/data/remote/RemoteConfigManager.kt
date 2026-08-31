package com.aaryo.selfattendance.data.remote

import android.util.Log
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

object RemoteConfigManager {

    fun getInstance(): RemoteConfigManager = this

    val isLiveConfig: Boolean by lazy {
        try {
            val app = com.google.firebase.FirebaseApp.getInstance()
            val options = app.options
            val projectId = options.projectId
            val apiKey = options.apiKey
            !projectId.isNullOrBlank() && 
                projectId != "self-attendance-pro" && 
                !apiKey.isNullOrBlank() && 
                apiKey != "AIzaSyB8K1xT4vN9qZ2wL5mP7rJ0eA3cD6fH8yI"
        } catch (t: Throwable) {
            false
        }
    }

    private val remoteConfig: FirebaseRemoteConfig? by lazy {
        if (!isLiveConfig) {
            null
        } else {
            try {
                FirebaseRemoteConfig.getInstance().apply {
                    val settings = FirebaseRemoteConfigSettings.Builder()
                        .setMinimumFetchIntervalInSeconds(3600)
                        .build()
                    setConfigSettingsAsync(settings)
                    setDefaultsAsync(localDefaults)
                }
            } catch (e: Throwable) {
                Log.w("RemoteConfig", "FirebaseRemoteConfig initialization skipped/fallback: ${e.message}")
                null
            }
        }
    }

    // ── In-memory safe defaults ────────────────────────────────────────────
    // if getBoolean() is called before the task completes, unset keys return
    // false (Firebase SDK default for Boolean). This caused ads to be
    // permanently disabled on first launch / after a fresh install because
    // initRemoteConfig() read isAdsEnabled() before defaults were applied.
    //
    // getBooleanSafe() call falls back to our defaults instantly, regardless
    // of whether the Firebase task has finished or whether the key exists on
    // the server. setDefaultsAsync is still called so the SDK's cached values
    // match; the local map just guards the race window.
    private val localDefaults = mapOf(
        "ads_enabled"          to true,
        "show_banner_ad"       to true,
        "show_native_ad"       to true,
        "show_interstitial_ad" to true,
        "app_enabled"          to true,
        "allow_screenshot"     to true,
        "force_update_required" to false,
        "min_required_version" to 0L,
        "latest_version_code"  to 0L,
        "update_message"       to ""
    )

    suspend fun fetch() {
        try {
            remoteConfig?.fetchAndActivate()?.await()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Log.w("RemoteConfig", "Remote config fetch skipped or offline, using local defaults: ${e.message}")
        }
    }

    /**
     * fetched value AND whose default hasn't been committed to disk yet
     * (race with setDefaultsAsync). We resolve by checking the raw string
     * value: an empty string means "key not found / not yet applied" →
     * fall back to [localDefaults]. Only treat false as intentional when
     * the server explicitly sent "false".
     */
    private fun getBooleanSafe(key: String): Boolean {
        val rc = remoteConfig ?: return localDefaults[key] as? Boolean ?: true
        val rawStr = try { rc.getString(key) } catch (t: Throwable) { "" }
        if (rawStr.isEmpty()) {
            // Key not fetched / defaults not applied yet — use our local default
            return localDefaults[key] as? Boolean ?: true
        }
        return try { rc.getBoolean(key) } catch (t: Throwable) { localDefaults[key] as? Boolean ?: true }
    }

    private fun getLongSafe(key: String, fallback: Long = 0L): Long {
        val rc = remoteConfig ?: return (localDefaults[key] as? Long) ?: fallback
        val rawStr = try { rc.getString(key) } catch (t: Throwable) { "" }
        if (rawStr.isEmpty()) {
            return (localDefaults[key] as? Long) ?: fallback
        }
        return try { rc.getLong(key) } catch (t: Throwable) { (localDefaults[key] as? Long) ?: fallback }
    }

    fun isAdsEnabled()       = getBooleanSafe("ads_enabled")
    fun showBannerAd()       = isAdsEnabled() && getBooleanSafe("show_banner_ad")
    fun showNativeAd()       = isAdsEnabled() && getBooleanSafe("show_native_ad")
    fun showInterstitialAd() = isAdsEnabled() && getBooleanSafe("show_interstitial_ad")

    fun isAppEnabled()    = getBooleanSafe("app_enabled")
    fun allowScreenshot() = getBooleanSafe("allow_screenshot")

    fun isForceUpdateRequired(): Boolean = getBooleanSafe("force_update_required")
    fun getMinRequiredVersion(): Long = getLongSafe("min_required_version", 0L)
    fun getLatestVersionCode(): Long = getLongSafe("latest_version_code", 0L)
    fun getUpdateMessage(): String = remoteConfig?.let { try { it.getString("update_message") } catch (t: Throwable) { "" } } ?: ""

    fun getString(key: String): String = remoteConfig?.let { try { it.getString(key) } catch (t: Throwable) { "" } } ?: ""
}
