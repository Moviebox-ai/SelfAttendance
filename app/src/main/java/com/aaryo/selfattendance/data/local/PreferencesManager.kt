package com.aaryo.selfattendance.data.local

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "app_settings"

        // ── General keys ─────────────────────────────────────────────
        const val KEY_LAST_MARKED_DATE   = "last_marked_date"
        const val KEY_DARK_MODE          = "dark_mode"
        const val KEY_BIOMETRIC          = "biometric"
        const val KEY_REMINDER_ENABLED   = "reminder"
        const val KEY_REMINDER_HOUR      = "reminder_hour"
        const val KEY_REMINDER_MINUTE    = "reminder_minute"

        // ── Wallet / Coin keys ───────────────────────────────────────
        /** Epoch ms of the last successfully-completed rewarded ad */
        const val KEY_LAST_AD_WATCHED_TS  = "last_ad_watched_timestamp"
        /** "yyyy-MM-dd" stamp for the locally-cached daily total */
        const val KEY_DAILY_COINS_DATE    = "daily_coins_date"
        /** Coins earned from ads today (local cache, resets at midnight) */
        const val KEY_DAILY_COINS_EARNED  = "daily_coins_earned_local"

        /** Cooldown between rewarded ads: 60 seconds */
        const val AD_COOLDOWN_SECONDS     = 60L
    }

    // ── Last Marked Date ─────────────────────────────────────────────

    var lastMarkedDate: String
        get() = prefs.getString(KEY_LAST_MARKED_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_MARKED_DATE, value).apply()

    // ── Dark Mode ────────────────────────────────────────────────────

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    // ── Biometric ────────────────────────────────────────────────────

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    // ── Reminder ─────────────────────────────────────────────────────

    var isReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()

    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, 9)
        set(value) = prefs.edit().putInt(KEY_REMINDER_HOUR, value).apply()

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_REMINDER_MINUTE, value).apply()

    // ── Clear All ────────────────────────────────────────────────────

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // ── Theme ────────────────────────────────────────────────────────

    var selectedTheme: String
        get() = prefs.getString("selected_theme", "deep_violet") ?: "deep_violet"
        set(value) = prefs.edit().putString("selected_theme", value).apply()

    // ── Language ─────────────────────────────────────────────────────

    var selectedLanguage: String
        get() = prefs.getString("selected_language", "en") ?: "en"
        set(value) = prefs.edit().putString("selected_language", value).apply()

    // ── Daily Coin Limit (user-configurable) ─────────────────────────

    var dailyCoinLimit: Int
        get() = prefs.getInt("daily_coin_limit", 50)
        set(value) = prefs.edit().putInt("daily_coin_limit", value).apply()

    // ── Ad Cooldown System ───────────────────────────────────────────
    // Stores epoch-ms of the last successful rewarded-ad completion so
    // the UI can enforce a cooldown without a Firestore round-trip.

    /**
     * Epoch ms of the last rewarded-ad completion. Returns 0L if never watched.
     */
    var lastAdWatchedTimestamp: Long
        get() = prefs.getLong(KEY_LAST_AD_WATCHED_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_AD_WATCHED_TS, value).apply()

    /**
     * Stamps the current time as the last ad-watch moment.
     * Call immediately after a confirmed successful reward.
     */
    fun recordAdWatched() {
        lastAdWatchedTimestamp = System.currentTimeMillis()
    }

    /**
     * Seconds remaining in the cooldown period, or 0 if the user may
     * watch another ad right now.
     */
    fun adCooldownRemainingSeconds(cooldownSeconds: Long = AD_COOLDOWN_SECONDS): Long {
        val elapsed = (System.currentTimeMillis() - lastAdWatchedTimestamp) / 1_000
        return maxOf(0L, cooldownSeconds - elapsed)
    }

    /**
     * Returns true when the cooldown has elapsed and the user is
     * allowed to watch another rewarded ad.
     */
    fun canWatchAd(cooldownSeconds: Long = AD_COOLDOWN_SECONDS): Boolean =
        adCooldownRemainingSeconds(cooldownSeconds) == 0L

    // ── Local Daily Coin Cache ───────────────────────────────────────
    // Mirrors today's ad-earned coins in SharedPreferences so the UI
    // renders the progress bar instantly without a Firestore read.
    // Resets automatically when the calendar day changes.

    private fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    /**
     * Coins earned from ads today according to the local cache.
     * Auto-resets to 0 when the date changes.
     */
    fun getLocalDailyCoinsEarned(): Int {
        val storedDate = prefs.getString(KEY_DAILY_COINS_DATE, "") ?: ""
        if (storedDate != todayString()) {
            prefs.edit()
                .putString(KEY_DAILY_COINS_DATE, todayString())
                .putInt(KEY_DAILY_COINS_EARNED, 0)
                .apply()
            return 0
        }
        return prefs.getInt(KEY_DAILY_COINS_EARNED, 0)
    }

    /**
     * Adds [coins] to the local daily cache and stamps today's date.
     * Call after a confirmed [CoinRepository.addAdReward] success.
     */
    fun addLocalDailyCoins(coins: Int) {
        val today   = todayString()
        val current = if (prefs.getString(KEY_DAILY_COINS_DATE, "") == today)
                          prefs.getInt(KEY_DAILY_COINS_EARNED, 0)
                      else 0
        prefs.edit()
            .putString(KEY_DAILY_COINS_DATE, today)
            .putInt(KEY_DAILY_COINS_EARNED, current + coins)
            .apply()
    }

    /**
     * Overwrites the local daily cache with the authoritative Firestore value.
     * Call after [CoinRepository.getAdCoinsEarnedToday] returns.
     */
    fun setLocalDailyCoins(coins: Int) {
        prefs.edit()
            .putString(KEY_DAILY_COINS_DATE, todayString())
            .putInt(KEY_DAILY_COINS_EARNED, coins)
            .apply()
    }
}
