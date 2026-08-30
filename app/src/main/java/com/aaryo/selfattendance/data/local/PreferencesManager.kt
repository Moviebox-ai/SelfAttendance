package com.aaryo.selfattendance.data.local

import android.content.Context
import android.content.SharedPreferences

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
        const val KEY_APP_MODE           = "app_mode"
        const val KEY_BUSINESS_NAME      = "business_name"
        const val KEY_BUSINESS_OWNER     = "business_owner"
        const val KEY_BUSINESS_PHONE     = "business_phone"
        const val KEY_BUSINESS_EMAIL     = "business_email"
        const val KEY_BUSINESS_ADDRESS   = "business_address"
        const val KEY_BUSINESS_GSTIN     = "business_gstin"
        const val KEY_BUSINESS_CURRENCY  = "business_currency"

        const val MODE_SELF              = "SELF"
        const val MODE_EMPLOYER          = "EMPLOYER"
    }

    // ── App Mode (Self Attendance vs Employer Staff Manager) ─────────

    var appMode: String
        get() = prefs.getString(KEY_APP_MODE, MODE_SELF) ?: MODE_SELF
        set(value) = prefs.edit().putString(KEY_APP_MODE, value).apply()

    var businessName: String
        get() = prefs.getString(KEY_BUSINESS_NAME, "My Business") ?: "My Business"
        set(value) = prefs.edit().putString(KEY_BUSINESS_NAME, value).apply()

    var businessOwnerName: String
        get() = prefs.getString(KEY_BUSINESS_OWNER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BUSINESS_OWNER, value).apply()

    var businessPhone: String
        get() = prefs.getString(KEY_BUSINESS_PHONE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BUSINESS_PHONE, value).apply()

    var businessEmail: String
        get() = prefs.getString(KEY_BUSINESS_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BUSINESS_EMAIL, value).apply()

    var businessAddress: String
        get() = prefs.getString(KEY_BUSINESS_ADDRESS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BUSINESS_ADDRESS, value).apply()

    var businessGstin: String
        get() = prefs.getString(KEY_BUSINESS_GSTIN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BUSINESS_GSTIN, value).apply()

    var businessCurrency: String
        get() = prefs.getString(KEY_BUSINESS_CURRENCY, "₹") ?: "₹"
        set(value) = prefs.edit().putString(KEY_BUSINESS_CURRENCY, value).apply()

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
        get() = prefs.getInt(KEY_REMINDER_HOUR, 21)
        set(value) = prefs.edit().putInt(KEY_REMINDER_HOUR, value).apply()

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_REMINDER_MINUTE, value).apply()

    // ── Clear All ────────────────────────────────────────────────────

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Clear ONLY user-specific data (coins, spin, streak, premium unlocks,
     * referral, coin-gate dates). Device-level settings (dark mode, theme,
     * language, reminders) are intentionally kept.
     *
     * Call this BEFORE syncFromFirebase() on every login/account-switch so
     * a new account always starts with local=0 — ensuring the
     * `if (remoteBal >= prefs.coinBalance)` condition in syncFromFirebase
     * evaluates to TRUE and correctly loads the new account's Firebase data.
     *
     * Without this, User B logging in on a device that previously had User A
     * would inherit User A's coins from SharedPreferences.
     */
    fun clearUserData() {
        prefs.edit().apply {
            // Attendance
            remove(KEY_LAST_MARKED_DATE)
            // Streak
            remove("last_streak_gate_date")
            remove("my_streak_for_leaderboard")
            // Coins
            remove("coin_balance")
            remove("total_coins_earned")
            remove("coin_security_sig")
            remove("sec_daily_earn_date")
            remove("sec_daily_earned_coins")
            remove("sec_last_reward_elapsed")
            // Daily spin
            remove("last_spin_date")
            remove("daily_spins_used")
            remove("last_spin_timestamp_ms")
            remove("last_spin_index")
            remove("winning_spin_indices")
            // Coin gate dates
            remove("last_daily_login_date")
            remove("last_attendance_coin_date")
            // Premium (boolean flags)
            remove("prem_pdf_export")
            remove("prem_csv_export")
            remove("prem_ad_free_until_ms")
            remove("prem_gold_badge")
            remove("prem_salary_insights")
            // Premium (time-locked)
            remove("prem_theme_until_ms")
            remove("prem_restore_until_ms")
            remove("prem_reset_until_ms")
            remove("prem_pdf_export_until_ms")
            remove("prem_salary_slip_until_ms")
            // Referral
            remove("referred_by_uid")
            remove("has_entered_referral_code")
            remove("last_referral_visit_date")
        }.apply()
    }

    // ── Theme ────────────────────────────────────────────────────────

    var selectedTheme: String
        get() = prefs.getString("selected_theme", "deep_violet") ?: "deep_violet"
        set(value) = prefs.edit().putString("selected_theme", value).apply()

    // ── Language ─────────────────────────────────────────────────────

    var selectedLanguage: String
        get() = prefs.getString("selected_language", "en") ?: "en"
        set(value) = prefs.edit().putString("selected_language", value).apply()

    /** True after the first-run country/language picker has been shown once. */
    var languagePickerShown: Boolean
        get() = prefs.getBoolean("language_picker_shown", false)
        set(value) = prefs.edit().putBoolean("language_picker_shown", value).apply()

    /** ISO 3166-1 alpha-2 country code picked on first run, e.g. "IN", "US". */
    var selectedCountry: String
        get() = prefs.getString("selected_country", "") ?: ""
        set(value) = prefs.edit().putString("selected_country", value).apply()

    // ── Currency ──────────────────────────────────────────────────────────────
    /** ISO 4217 currency code, e.g. "INR", "USD", "EUR". Defaults to INR. */
    var selectedCurrency: String
        get() = prefs.getString("selected_currency", "INR") ?: "INR"
        set(value) = prefs.edit().putString("selected_currency", value).apply()

    // ── Hourly Reminder ──────────────────────────────────────────────

    var isHourlyReminderEnabled: Boolean
        get() = prefs.getBoolean("hourly_reminder_enabled", true)
        set(value) = prefs.edit().putBoolean("hourly_reminder_enabled", value).apply()

    // ── Backup Reminder ───────────────────────────────────────────────────

    var isBackupReminderEnabled: Boolean
        get() = prefs.getBoolean("backup_reminder_enabled", true)
        set(value) = prefs.edit().putBoolean("backup_reminder_enabled", value).apply()

    // ── Auto Backup ───────────────────────────────────────────────────────

    /** Whether automatic weekly backup is enabled (default: true). */
    var isAutoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_enabled", true)
        set(value) = prefs.edit().putBoolean("auto_backup_enabled", value).apply()

    /**
     * Epoch milliseconds of the last successful automatic backup.
     * 0L means no backup has been performed yet.
     */
    var lastAutoBackupTime: Long
        get() = prefs.getLong("last_auto_backup_time", 0L)
        set(value) = prefs.edit().putLong("last_auto_backup_time", value).apply()

    /** Human-readable "Last backed up" string, e.g. "15 Jul 2025, 2:30 AM". */
    val lastAutoBackupFormatted: String
        get() {
            val ms = lastAutoBackupTime
            if (ms == 0L) return "Kabhi nahi"
            val sdf = java.text.SimpleDateFormat("d MMM yyyy, h:mm a", java.util.Locale.getDefault())
            return sdf.format(java.util.Date(ms))
        }

    // ── Weekly Summary Notification ───────────────────────────────────────

    var isWeeklySummaryEnabled: Boolean
        get() = prefs.getBoolean("weekly_summary_enabled", true)
        set(value) = prefs.edit().putBoolean("weekly_summary_enabled", value).apply()

    // ── Streak Gate ────────────────────────────────────────────────────────

    var lastStreakGateDate: String
        get() = prefs.getString("last_streak_gate_date", "") ?: ""
        set(value) = prefs.edit().putString("last_streak_gate_date", value).apply()

    // ── Daily Spin ────────────────────────────────────────────────────────

    var lastSpinDate: String
        get() = prefs.getString("last_spin_date", "") ?: ""
        set(value) = prefs.edit().putString("last_spin_date", value).apply()

    var lastSpinIndex: Int
        get() = prefs.getInt("last_spin_index", 0)
        set(value) = prefs.edit().putInt("last_spin_index", value).apply()

    /** How many spins the user has used today (resets when date changes). */
    var dailySpinsUsed: Int
        get() = prefs.getInt("daily_spins_used", 0)
        set(value) = prefs.edit().putInt("daily_spins_used", value.coerceAtLeast(0)).apply()

    /** Epoch millis of the last spin — used for 1-minute cooldown between spins. */
    var lastSpinTimestampMs: Long
        get() = prefs.getLong("last_spin_timestamp_ms", 0L)
        set(value) = prefs.edit().putLong("last_spin_timestamp_ms", value).apply()

    /** Comma-separated 0-based spin indices that reward coins today (e.g. "2,5,8"). */
    var winningSpinIndices: String
        get() = prefs.getString("winning_spin_indices", "") ?: ""
        set(value) = prefs.edit().putString("winning_spin_indices", value).apply()

    // ── Leaderboard ───────────────────────────────────────────────────────

    var myStreakForLeaderboard: Int
        get() = prefs.getInt("my_streak_for_leaderboard", 0)
        set(value) = prefs.edit().putInt("my_streak_for_leaderboard", value).apply()

    // ── Coins Balance ─────────────────────────────────────────────────────

    /** Total coins the user currently holds. */
    var coinBalance: Int
        get() = prefs.getInt("coin_balance", 0)
        set(value) = prefs.edit().putInt("coin_balance", value.coerceAtLeast(0)).apply()

    /** Cumulative coins ever earned (for stats). */
    var totalCoinsEarned: Int
        get() = prefs.getInt("total_coins_earned", 0)
        set(value) = prefs.edit().putInt("total_coins_earned", value).apply()

    /** Cryptographic HMAC-SHA256 signature for coin balance integrity. */
    var coinSecuritySignature: String
        get() = prefs.getString("coin_security_sig", "") ?: ""
        set(value) = prefs.edit().putString("coin_security_sig", value).apply()

    var securityDailyEarnDate: String
        get() = prefs.getString("sec_daily_earn_date", "") ?: ""
        set(value) = prefs.edit().putString("sec_daily_earn_date", value).apply()

    var securityDailyEarnedCoins: Int
        get() = prefs.getInt("sec_daily_earned_coins", 0)
        set(value) = prefs.edit().putInt("sec_daily_earned_coins", value).apply()

    var securityLastRewardElapsedRealtime: Long
        get() = prefs.getLong("sec_last_reward_elapsed", 0L)
        set(value) = prefs.edit().putLong("sec_last_reward_elapsed", value).apply()

    // ── Premium Features Unlocked ─────────────────────────────────────────

    /** PDF Export unlocked via coins. */
    var isPdfExportUnlocked: Boolean
        get() = prefs.getBoolean("prem_pdf_export", false)
        set(value) = prefs.edit().putBoolean("prem_pdf_export", value).apply()

    /** CSV Export unlocked via coins. */
    var isCsvExportUnlocked: Boolean
        get() = prefs.getBoolean("prem_csv_export", false)
        set(value) = prefs.edit().putBoolean("prem_csv_export", value).apply()

    /** Epoch millis until which ad-free (coin-purchased) is active. */
    var adFreeUntilCoinMs: Long
        get() = prefs.getLong("prem_ad_free_until_ms", 0L)
        set(value) = prefs.edit().putLong("prem_ad_free_until_ms", value).apply()

    /** Gold Badge unlocked via coins. */
    var isGoldBadgeUnlocked: Boolean
        get() = prefs.getBoolean("prem_gold_badge", false)
        set(value) = prefs.edit().putBoolean("prem_gold_badge", value).apply()

    /** Salary Insights unlocked via coins. */
    var isSalaryInsightsUnlocked: Boolean
        get() = prefs.getBoolean("prem_salary_insights", false)
        set(value) = prefs.edit().putBoolean("prem_salary_insights", value).apply()

    // ── Time-Locked Premium Features (epoch ms; 0 = locked) ──────────────

    /** Legacy single "App Theme" unlock — kept only to migrate old unlocks
     *  into the new per-theme scheme below. Do not use for new logic. */
    var premThemeUnlockUntilMs: Long
        get() = prefs.getLong("prem_theme_until_ms", Long.MAX_VALUE)
        set(value) = prefs.edit().putLong("prem_theme_until_ms", value).apply()

    /** Each theme is its own premium unlock now (epoch ms; 0 = locked) —
     *  unlocking one theme does NOT unlock the others. */
    fun themeUnlockUntilMs(themeKey: String): Long =
        prefs.getLong("prem_theme_${themeKey}_until_ms", Long.MAX_VALUE)

    fun setThemeUnlockUntilMs(themeKey: String, value: Long) {
        prefs.edit().putLong("prem_theme_${themeKey}_until_ms", value).apply()
    }

    /** One-time migration: users who had already unlocked the old shared
     *  "theme" feature keep access to their currently-selected theme;
     *  every other theme is locked and must be purchased individually. */
    fun migrateLegacyThemeUnlockIfNeeded(currentThemeKey: String) {
        if (prefs.getBoolean("prem_theme_migrated", false)) return
        val legacyUntil = premThemeUnlockUntilMs
        if (legacyUntil > 0L && themeUnlockUntilMs(currentThemeKey) == 0L) {
            setThemeUnlockUntilMs(currentThemeKey, legacyUntil)
        }
        prefs.edit().putBoolean("prem_theme_migrated", true).apply()
    }

    /** Restore Data — unlocked via coins for a random 1–5 day duration. */
    var premRestoreUnlockUntilMs: Long
        get() = prefs.getLong("prem_restore_until_ms", Long.MAX_VALUE)
        set(value) = prefs.edit().putLong("prem_restore_until_ms", value).apply()

    /** Reset Attendance Data — unlocked via coins for a random 1–5 day duration. */
    var premResetUnlockUntilMs: Long
        get() = prefs.getLong("prem_reset_until_ms", Long.MAX_VALUE)
        set(value) = prefs.edit().putLong("prem_reset_until_ms", value).apply()

    /** Export PDF Report — unlocked via coins for a random 1–5 day duration. */
    var premPdfExportUnlockUntilMs: Long
        get() = prefs.getLong("prem_pdf_export_until_ms", Long.MAX_VALUE)
        set(value) = prefs.edit().putLong("prem_pdf_export_until_ms", value).apply()

    /** Generate Salary Slip — unlocked via coins for a random 1–5 day duration. */
    var premSalarySlipUnlockUntilMs: Long
        get() = prefs.getLong("prem_salary_slip_until_ms", Long.MAX_VALUE)
        set(value) = prefs.edit().putLong("prem_salary_slip_until_ms", value).apply()

    // ── Daily Login Coins ─────────────────────────────────────────────────

    /** Last date (YYYY-MM-DD) when daily login bonus coins were awarded. */
    var lastDailyLoginDate: String
        get() = prefs.getString("last_daily_login_date", "") ?: ""
        set(value) = prefs.edit().putString("last_daily_login_date", value).apply()

    // ── Attendance Present Coins ──────────────────────────────────────────

    /** Last date (YYYY-MM-DD) when attendance PRESENT bonus coins were awarded. */
    var lastAttendanceCoinDate: String
        get() = prefs.getString("last_attendance_coin_date", "") ?: ""
        set(value) = prefs.edit().putString("last_attendance_coin_date", value).apply()

    // ── Referral ──────────────────────────────────────────────────────────

    /** UID of the user who referred this user. Empty = not referred. */
    var referredByUid: String
        get() = prefs.getString("referred_by_uid", "") ?: ""
        set(value) = prefs.edit().putString("referred_by_uid", value).apply()

    /** Whether this user has already entered a referral code. */
    var hasEnteredReferralCode: Boolean
        get() = prefs.getBoolean("has_entered_referral_code", false)
        set(value) = prefs.edit().putBoolean("has_entered_referral_code", value).apply()

    /** Last date (YYYY-MM-DD) when referral visit was recorded in Firebase. */
    var lastReferralVisitDate: String
        get() = prefs.getString("last_referral_visit_date", "") ?: ""
        set(value) = prefs.edit().putString("last_referral_visit_date", value).apply()
}
