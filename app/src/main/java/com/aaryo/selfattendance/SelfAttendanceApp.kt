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

            // Configure Firestore offline persistence with persistent cache
            try {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
                            .setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
                db.firestoreSettings = settings
                Log.d("AppInit", "Firestore persistent cache configured")
            } catch (e: Exception) {
                Log.w("AppInit", "Firestore settings already applied or failed: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("AppInit", "Firebase init failed — app will run in offline mode", e)
        }
    }

    private fun initCrashlytics() {
        try {
            val isLive = RemoteConfigManager.isLiveConfig
            FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(isLive && !BuildConfig.DEBUG)
                if (isLive) {
                    // Tag all reports with the store flavor for triage
                    setCustomKey("store", BuildConfig.STORE_NAME)
                    setCustomKey("is_amazon", BuildConfig.IS_AMAZON)
                }
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
                appOpenAdManager = AppOpenAdManager(this).also { it.register() }
            }
        }
    }

    private fun initRemoteConfig() {
        applicationScope.launch {
            try {
                val remote = RemoteConfigManager.getInstance()
                remote.fetch()
                withContext(Dispatchers.Main) {
                    // BUG FIX: Previously this could disable ads AFTER MainActivity.initAds()
                    // had already enabled them and started preloading (race condition).
                    // If initAds() ran first (consent took < fetch time), it set isAdsEnabled=true
                    // and kicked off preloads. Then this block ran and set isAdsEnabled=false,
                    // instantly killing every active ad slot via Compose state recomposition.
                    //
                    // Now we always apply the remote value in both directions so the final state
                    // is always consistent with what the server says — whether this runs before
                    // or after initAds(). MainActivity.initAds() does the same bidirectional
                    // assignment so whichever runs last wins with the correct server value.
                    val adsEnabled = remote.isAdsEnabled()
                    AdsController.isAdsEnabled = adsEnabled
                    if (!adsEnabled) {
                        Log.w("RemoteConfig", "Ads globally disabled by Remote Config")
                    } else {
                        Log.d("RemoteConfig", "Remote Config: ads enabled")
                    }
                }
            } catch (e: CancellationException) {
                throw e
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
            // Create all notification channels
            AppNotificationManager.setupChannels(this)

            val prefs = PreferencesManager(this)

            if (prefs.isReminderEnabled) {
                // Schedule fixed 8:00 AM, 1:30 PM, and 6:00 PM daily reminders
                ReminderScheduler.schedule(this)
            } else {
                ReminderScheduler.cancel(this)
            }

            // Cancel any hourly, weekly summary, or extra notification workers
            ReminderScheduler.cancelHourlyReminders(this)
            ReminderScheduler.cancelWeeklySummary(this)
            ReminderScheduler.cancelBackupReminder(this)

            // Silent automatic backup in background
            ReminderScheduler.scheduleAutoBackup(this)

            Log.d("AppInit", "Notifications initialized with 8 AM, 1:30 PM, and 6 PM schedule")
        } catch (e: Exception) {
            Log.e("AppInit", "Notification init failed", e)
        }
    }
}
