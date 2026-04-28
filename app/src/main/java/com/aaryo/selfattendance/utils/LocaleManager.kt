package com.aaryo.selfattendance.utils

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
//  LocaleManager  — v3 (Full fix)
//
//  Root causes fixed:
//   1. applyLocale() was missing LocaleList.setDefault() on API 24+,
//      so Compose kept resolving strings from the old locale cache.
//   2. setLocaleAndRestart() used deprecated updateConfiguration() only,
//      which is a no-op on modern APIs when called AFTER attachBaseContext.
//      Fix: call applyLocale() on the live Activity Resources THEN recreate().
//   3. Android 13+ (API 33) has a per-app system LocaleManager that
//      overrides our attachBaseContext pref on cold launch.
//      Fix: also set android.app.LocaleManager.applicationLocales.
//
//  Correct flow:
//   attachBaseContext()  → applyLocale(newBase, savedLang)
//   Language selected    → setLocaleAndRestart(activity, newLang)
//                          → saves pref
//                          → updates live Resources
//                          → updates system LocaleManager (API 33+)
//                          → activity.recreate()
// ═══════════════════════════════════════════════════════════════

object LocaleManager {

    val SUPPORTED_LANGUAGES = linkedMapOf(
        "en" to "English",
        "hi" to "हिंदी (Hindi)",
        "gu" to "ગુજરાતી (Gujarati)",
        "mr" to "मराठी (Marathi)",
        "bn" to "বাংলা (Bengali)",
        "ta" to "தமிழ் (Tamil)",
        "te" to "తెలుగు (Telugu)",
        "kn" to "ಕನ್ನಡ (Kannada)",
        "ml" to "മലയാളം (Malayalam)",
        "pa" to "ਪੰਜਾਬੀ (Punjabi)",
        "es" to "Español (Spanish)"
    )

    // ── Called from attachBaseContext() ───────────────────────────
    // Wraps the base Context so ALL resource lookups use [languageCode].
    fun applyLocale(base: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        return base.createConfigurationContext(config)
    }

    // ── Called when user picks a language ─────────────────────────
    // Persists the choice, rebuilds Resources, then restarts the Activity.
    fun setLocaleAndRestart(activity: Activity, languageCode: String) {
        // 1. Persist
        PreferencesManager(activity).selectedLanguage = languageCode

        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration(activity.resources.configuration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val localeList = LocaleList(locale)
            LocaleList.setDefault(localeList)
            config.setLocales(localeList)
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }

        // 2. Force-update the live Activity Resources so the recreated
        //    instance inherits the correct config immediately.
        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)

        // 3. Android 13+ system per-app locale — must be set or the
        //    system resets locale to device default on next cold launch.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val sysLocMgr =
                    activity.getSystemService(android.app.LocaleManager::class.java)
                sysLocMgr?.applicationLocales =
                    android.os.LocaleList.forLanguageTags(languageCode)
            } catch (_: Exception) { /* attachBaseContext still covers it */ }
        }

        // 4. Recreate — attachBaseContext() will re-wrap with correct locale
        activity.recreate()
    }

    fun getDisplayName(code: String): String = SUPPORTED_LANGUAGES[code] ?: "English"
}
