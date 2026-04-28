package com.aaryo.selfattendance

import android.content.Context
import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.aaryo.selfattendance.ads.AdsController
import com.aaryo.selfattendance.ads.ConsentManager
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.notifications.AttendanceReminderWorker
import com.aaryo.selfattendance.notifications.AppNotificationManager
import com.aaryo.selfattendance.security.BiometricGate
import com.aaryo.selfattendance.security.IntegrityCheck
import com.aaryo.selfattendance.security.RootDetector
import com.aaryo.selfattendance.ui.navigation.AppNavGraph
import com.aaryo.selfattendance.ui.navigation.Routes
import com.aaryo.selfattendance.ui.theme.AppTheme
import com.aaryo.selfattendance.ui.theme.SelfAttendanceTheme
import com.aaryo.selfattendance.update.AltUpdateDialog
import com.aaryo.selfattendance.update.AlternativeUpdateManager
import com.aaryo.selfattendance.update.InAppUpdateManager
import com.aaryo.selfattendance.utils.LocaleManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = PreferencesManager(newBase).selectedLanguage
        super.attachBaseContext(LocaleManager.applyLocale(newBase, lang))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val lang = PreferencesManager(this).selectedLanguage
        LocaleManager.applyLocale(this, lang)
    }

    private val remoteConfig     = RemoteConfigManager.getInstance()
    private lateinit var updateManager   : InAppUpdateManager
    private lateinit var altUpdateManager: AlternativeUpdateManager
    private val snackbarHostState = SnackbarHostState()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            // Permission result handled silently — we never force-show rationale
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        updateManager    = InAppUpdateManager(this)
        altUpdateManager = AlternativeUpdateManager(this)

        // Setup notification channels early
        AppNotificationManager.setupChannels(this)

        // ── In-App Update ─────────────────────────────────────────────────
        updateManager.checkForUpdate(
            forceUpdate = false,
            onDownloaded = {
                lifecycleScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message     = "Update ready!",
                        actionLabel = "Restart"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        updateManager.completeUpdate()
                    }
                }
            }
        )

        lifecycleScope.launch {
            try { remoteConfig.fetch() } catch (_: Exception) {}
            altUpdateManager.checkAltUpdate()
        }

        try {
            if (!remoteConfig.allowScreenshot()) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        } catch (_: Exception) {}

        requestNotificationPermission()
        handleSecurityChecks()

        try { IntegrityCheck(this).check() } catch (_: Exception) {}

        // ── Consent → Ads init ────────────────────────────────────────────
        // onboarding flag was set to true in SelfAttendanceApp.
        // It is cleared inside initAds() after consent is obtained and ads
        // are ready, ensuring no App Open ad fires during splash/auth.
        ConsentManager.requestConsent(this) { initAds() }

        scheduleAttendanceReminder()

        setContent {
            val prefManager = remember { PreferencesManager(this@MainActivity) }
            var darkMode  by remember { mutableStateOf(prefManager.isDarkMode) }
            var appTheme  by remember { mutableStateOf(AppTheme.fromKey(prefManager.selectedTheme)) }
            var biometricPassed by remember { mutableStateOf(false) }

            val altUpdateState by altUpdateManager.state.collectAsStateWithLifecycle()
            var altUpdateDismissed by remember { mutableStateOf(false) }

            SelfAttendanceTheme(appTheme = appTheme, darkTheme = darkMode) {

                if (biometricRequired() && !biometricPassed) {
                    BiometricGate { biometricPassed = true }
                } else {

                    // ── Deep-link handling (notification tap) ──────────────
                    val openScreen = intent?.getStringExtra("open_screen")
                    val startScreen = when (openScreen) {
                        "calendar" -> Routes.CALENDAR
                        "wallet"   -> Routes.WALLET
                        else       -> null
                    }

                    AppNavGraph(notificationStartScreen = startScreen)
                }

                SnackbarHost(hostState = snackbarHostState)

                if (!altUpdateDismissed &&
                    altUpdateState !is AlternativeUpdateManager.UpdateState.None
                ) {
                    AltUpdateDialog(
                        state   = altUpdateState,
                        manager = altUpdateManager,
                        onDismiss = { altUpdateDismissed = true }
                    )
                }
            }
        }
    }

    // ── Ads initialisation ────────────────────────────────────────────────

    private fun initAds() {
        try {
            if (!ConsentManager.canShowAds(this)) return

            // Preload all ad formats
            AdsController.preload(this)

            // Preload App Open ad (first show will be on next foreground event
            // once the onboarding flag is cleared)
            (application as? SelfAttendanceApp)?.appOpenAdManager?.loadAd()

            // ✅ Clear onboarding gate — App Open ads can now fire on next
            //    foreground. We delay clearing it by one frame so the current
            //    activity resume cycle (which triggered initAds) is not
            //    immediately interrupted by an ad.
            window.decorView.post {
                AdsController.isOnboardingActive = false
            }
        } catch (_: Exception) {}
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) {
            updateManager.resumeImmediateUpdateIfNeeded()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::updateManager.isInitialized) {
            updateManager.unregister()
        }
    }

    // ── Security ──────────────────────────────────────────────────────────

    private fun handleSecurityChecks() {
        if (BuildConfig.DEBUG) return
        try {
            if (RootDetector.isDeviceRooted()) {
                showSecurityWarningDialog(
                    "Security Warning",
                    "Device rooted. Some features may not work."
                )
            }
        } catch (_: Exception) {}
    }

    private fun showSecurityWarningDialog(title: String, message: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Continue") { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) {}
    }

    // ── Notifications ─────────────────────────────────────────────────────

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(perm)
            }
        }
    }

    // ── Biometric ─────────────────────────────────────────────────────────

    private fun biometricRequired() = PreferencesManager(this).isBiometricEnabled

    // ── Work ─────────────────────────────────────────────────────────────

    private fun scheduleAttendanceReminder() {
        val work = PeriodicWorkRequestBuilder<AttendanceReminderWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(
                "attendanceReminder",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
    }
}
