package com.aaryo.selfattendance.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.util.Locale

// ═══════════════════════════════════════════════════════════════
//  LocaleManager  — v4 (Full fix)
//
//  Root causes fixed:
//   1. context as? Activity fails in Compose — LocalContext.current
//      returns a ContextThemeWrapper, not the Activity directly.
//      Fix: use findActivity() which traverses the ContextWrapper chain.
//   2. onConfigurationChanged() discarded the returned context (no-op).
//      Fix: call recreate() inside onConfigurationChanged when locale
//      does not match saved preference.
//   3. API 33+ applicationLocales triggers onConfigurationChanged
//      instead of recreate — handled by the recreate() call below.
//   4. Application class did not override attachBaseContext, so
//      applicationContext used system locale. Fix: override in
//      SelfAttendanceApp.
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
        "es" to "Español (Spanish)",
        "fr" to "Français (French)",
        "ar" to "العربية (Arabic)",
        "pt" to "Português (Portuguese)",
        "de" to "Deutsch (German)",
        "id" to "Bahasa Indonesia",
        "tr" to "Türkçe (Turkish)",
        "ko" to "한국어 (Korean)",
        "ja" to "日本語 (Japanese)",
        "zh" to "中文简体 (Chinese)"
    )

    // ── Traverse ContextWrapper chain to find the underlying Activity ──────
    // Compose's LocalContext.current is a ContextThemeWrapper, not the
    // Activity itself — so `context as? Activity` silently returns null.
    // This helper traverses the chain and always finds the real Activity.
    fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    // ── Parses a stored language code into a Locale. ────────────────────────
    // Plain Locale(String) cannot parse BCP-47 tags with a script subtag
    // (e.g. "hi-Latn" for Hinglish — Hindi meaning, written in Latin script).
    // Locale.forLanguageTag() handles both simple codes ("en", "hi") and
    // script-qualified ones ("hi-Latn") correctly.
    private fun parseLocale(languageCode: String): Locale =
        Locale.forLanguageTag(normalizeLanguageTag(languageCode))

    fun normalizeLanguageTag(languageCode: String): String =
        Locale.forLanguageTag(languageCode.ifBlank { "en" }).toLanguageTag()

    // ── Called from attachBaseContext() ────────────────────────────────────
    // Wraps the base Context so ALL resource lookups use [languageCode].
    fun applyLocale(base: Context, languageCode: String): Context {
        val normalizedCode = normalizeLanguageTag(languageCode)
        val locale = parseLocale(normalizedCode)
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

    // ── Called when user picks a language ──────────────────────────────────
    // Persists the choice, rebuilds Resources, then restarts the Activity.
    fun setLocaleAndRestart(activity: Activity, languageCode: String) {
        val normalizedCode = normalizeLanguageTag(languageCode)

        // 1. Persist
        PreferencesManager(activity).selectedLanguage = normalizedCode

        val locale = parseLocale(normalizedCode)
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
        //    NOTE: on API 33+, setting applicationLocales triggers
        //    onConfigurationChanged (not recreate) because manifest has
        //    configChanges="locale". The explicit recreate() below handles it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val sysLocMgr =
                    activity.getSystemService(android.app.LocaleManager::class.java)
                sysLocMgr?.applicationLocales =
                    android.os.LocaleList.forLanguageTags(normalizedCode)
            } catch (_: Exception) { /* attachBaseContext still covers it */ }
        }

        // 4. Explicit recreate — attachBaseContext() re-wraps with correct locale.
        //    configChanges="locale" only prevents SYSTEM-initiated recreation;
        //    an explicit recreate() always goes through the full lifecycle.
        activity.recreate()
    }

    fun syncSystemAppLocaleIfNeeded(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val savedLanguage = normalizeLanguageTag(PreferencesManager(activity).selectedLanguage)
        try {
            val sysLocMgr = activity.getSystemService(android.app.LocaleManager::class.java)
            val activeLanguage = sysLocMgr?.applicationLocales?.toLanguageTags().orEmpty()
            if (activeLanguage != savedLanguage) {
                sysLocMgr?.applicationLocales = android.os.LocaleList.forLanguageTags(savedLanguage)
            }
        } catch (_: Exception) {
            // attachBaseContext() still applies the saved app locale for all resources.
        }
    }

    fun getDisplayName(code: String): String = SUPPORTED_LANGUAGES[code] ?: "English"
}
