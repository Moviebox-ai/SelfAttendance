package com.aaryo.selfattendance.data.local

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "app_settings"

        // Keys
        const val KEY_LAST_MARKED_DATE = "last_marked_date"
        const val KEY_DARK_MODE        = "dark_mode"
        const val KEY_BIOMETRIC        = "biometric"
        const val KEY_REMINDER_ENABLED = "reminder"
        const val KEY_REMINDER_HOUR    = "reminder_hour"
        const val KEY_REMINDER_MINUTE  = "reminder_minute"
    }

    // ----------------------------------------------------------------
    // LAST MARKED DATE
    // ----------------------------------------------------------------

    var lastMarkedDate: String
        get() = prefs.getString(KEY_LAST_MARKED_DATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_MARKED_DATE, value).apply()

    // ----------------------------------------------------------------
    // DARK MODE
    // ----------------------------------------------------------------

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    // ----------------------------------------------------------------
    // BIOMETRIC
    // ----------------------------------------------------------------

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply()

    // ----------------------------------------------------------------
    // REMINDER
    // ----------------------------------------------------------------

    var isReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_REMINDER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_REMINDER_ENABLED, value).apply()

    var reminderHour: Int
        get() = prefs.getInt(KEY_REMINDER_HOUR, 9)
        set(value) = prefs.edit().putInt(KEY_REMINDER_HOUR, value).apply()

    var reminderMinute: Int
        get() = prefs.getInt(KEY_REMINDER_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_REMINDER_MINUTE, value).apply()

    // ----------------------------------------------------------------
    // CLEAR ALL
    // ----------------------------------------------------------------

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
