package com.aaryo.selfattendance

import android.app.Application
import android.util.Log
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.ads.AppOpenAdManager
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.notifications.AppNotificationManager
import com.aaryo.selfattendance.utils.LocaleManager
import com.aaryo.selfattendance.notifications.ReminderScheduler
import com.aaryo.selfattendance.utils.AppValidator
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.*

class SelfAttendanceApp : Application() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("AppScope", "Unhandled coroutine exception", throwable)
        try {
            FirebaseCrashlytics.getInstance().recordException(throwable)
        } catch (e: Exception) {
            // Crashlytics itself failed to record — nothing further to report to,
            // fall back to logcat only so we don't lose the failure silently.
            Log.e("AppScope", "Crashlytics recordException failed", e)
        }
    }

    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + coroutineExceptionHandler
    )

    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun attachBaseContext(base: android.content.Context) {
        // Apply saved locale to the Application context so any component
        // that uses applicationContext also gets the correct locale.
        val lang = com.aaryo.selfattendance.data.local.PreferencesManager(base).selectedLanguage
        super.attachBaseContext(LocaleManager.applyLocale(base, lang))
    }

    override fun onCreate() {
        super.onCreate()
        installGlobalCrashHandler()
        initFirebase()
        initCrashlytics()
        initAdsSystem()
        initRemoteConfig()
        initNotifications()
        // Log environment diagnostics (debug builds + Amazon release builds for triage)
        AppValidator.logDiagnostics(this)
    }

    /**
     * Installs a global uncaught exception handler that logs to Crashlytics
     * before delegating to the default handler. This ensures crashes on
     * background threads and Amazon-specific components are always captured.
     */
    private fun installGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("GlobalCrash", "Uncaught exception on thread ${thread.name}", throwable)
                // Only record to Crashlytics if Firebase is initialized
                if (FirebaseApp.getApps(this).isNotEmpty()) {
                    FirebaseCrashlytics.getInstance().recordException(throwable)
                }
            } catch (_: Exception) {
                // Never let the crash handler itself crash
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    private fun initFirebase() {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
                Log.d("AppInit", "Firebase initialized")
            }
        } catch (e: Exception) {
            // Firebase can fail on Amazon devices if google-services.json is
            // missing required fields, but the app must not crash — Room DB,
            // local attendance data, and offline features all work without it.
            Log.e("AppInit", "Firebase init failed — app will run in offline mode", e)
        }
    }

    private fun initCrashlytics() {
        try {
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(!BuildConfig.DEBUG)
                // Tag all reports with the store flavor for triage
                setCustomKey("store", BuildConfig.STORE_NAME)
                setCustomKey("is_amazon", BuildConfig.IS_AMAZON)
            }
        } catch (e: Exception) {
            Log.e("AppInit", "Crashlytics init failed", e)
        }
    }

    private fun initAdsSystem() {
        try {
            AdsController.init(this)
            AdsController.isOnboardingActive = true

            appOpenAdManager = AppOpenAdManager(this)
            appOpenAdManager.register()
        } catch (e: Exception) {
            Log.e("AppInit", "AdsController init failed", e)
            // Provide a safe no-op AppOpenAdManager so lateinit is satisfied
            if (!::appOpenAdManager.isInitialized) {
                appOpenAdManager = AppOpenAdManager(this).also { it.register() } // FIX: register for lifecycle callbacks
            }
        }
    }

    private fun initRemoteConfig() {
        applicationScope.launch {
            try {
                val remote = RemoteConfigManager.getInstance()
                remote.fetch()
                withContext(Dispatchers.Main) {
                    // Only disable ads if Remote Config says so globally.
                    // Never force-enable here — premium REMOVE_ADS feature must take priority.
                    if (!remote.isAdsEnabled()) {
                        AdsController.isAdsEnabled = false
                    }
                }
            } catch (e: Exception) {
                Log.e("RemoteConfig", "Fetch failed — using cached defaults", e)
                // Do not override isAdsEnabled on failure; leave it as initialized.
            }
        }
    }

    /**
     * Setup notification channels and schedule periodic reminders.
     *
     * Two schedulers run independently:
     *  1. Daily reminder — fires once at the user's chosen time (default 9 PM).
     *  2. Hourly reminders — fires every ~60 min with attendance nudge + rotating offers.
     */
    private fun initNotifications() {
        try {
            // Create all notification channels (attendance, offers)
            AppNotificationManager.setupChannels(this)

            val prefs = PreferencesManager(this)

            // BUG FIX #7: Respect user's reminder toggle — only schedule if enabled.
            // Previously this was always scheduled ignoring the user's preference.
            if (prefs.isReminderEnabled) {
                ReminderScheduler.schedule(this, prefs.reminderHour, prefs.reminderMinute)
            }

            // BUG FIX #1 (companion): Only schedule hourly reminders if enabled.
            // HourlyReminderWorker also guards per-execution, but skipping
            // scheduling entirely saves WorkManager overhead when disabled.
            if (prefs.isHourlyReminderEnabled) {
                ReminderScheduler.scheduleHourlyReminders(this)
            }

            // Always schedule weekly attendance summary (every Monday 9 AM)
            ReminderScheduler.scheduleWeeklySummary(this)

            // Always schedule weekly backup reminder (every Sunday 8 PM)
            ReminderScheduler.scheduleBackupReminder(this)

            Log.d("AppInit", "Notifications initialized")
        } catch (e: Exception) {
            Log.e("AppInit", "Notification init failed", e)
        }
    }
}
