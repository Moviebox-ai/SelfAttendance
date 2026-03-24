package com.aaryo.selfattendance

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.aaryo.selfattendance.ads.AdManager
import com.aaryo.selfattendance.ads.ConsentManager
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import com.aaryo.selfattendance.notifications.AttendanceReminderWorker
import com.aaryo.selfattendance.security.RootDetector
import com.aaryo.selfattendance.security.IntegrityCheck
import com.aaryo.selfattendance.security.BiometricGate
import com.aaryo.selfattendance.ui.navigation.AppNavGraph
import com.aaryo.selfattendance.ui.theme.SelfAttendanceTheme
import com.aaryo.selfattendance.update.InAppUpdateManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : FragmentActivity() {

    private val remoteConfig = RemoteConfigManager.getInstance()

    // ✅ In-App Update
    private lateinit var updateManager: InAppUpdateManager
    private val snackbarHostState = SnackbarHostState()

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // ---------------- IN-APP UPDATE ----------------
        updateManager = InAppUpdateManager(this)

        // forceUpdate = false → Flexible (background download, soft snackbar)
        // forceUpdate = true  → Immediate (full-screen block, user update kiye bina app use nahi kar sakta)
        updateManager.checkForUpdate(
            forceUpdate = false,
            onDownloaded = {
                lifecycleScope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "Naya update ready hai!",
                        actionLabel = "Restart"
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        updateManager.completeUpdate()
                    }
                }
            }
        )

        // ---------------- REMOTE CONFIG ----------------
        lifecycleScope.launch {
            try {
                remoteConfig.fetch()
            } catch (_: Exception) {}
        }

        // ---------------- SCREENSHOT CONTROL ----------------
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

        // ---------------- SECURITY ----------------
        handleSecurityChecks()

        // ---------------- PLAY INTEGRITY ----------------
        try {
            IntegrityCheck(this).check()
        } catch (_: Exception) {}

        // ---------------- CONSENT → ADS ----------------
        ConsentManager.requestConsent(this) {
            initAds()
        }

        // ---------------- REMINDER ----------------
        scheduleAttendanceReminder()

        // ---------------- UI ----------------
        setContent {

            val prefManager = remember { PreferencesManager(this@MainActivity) }

            var darkMode by remember { mutableStateOf(prefManager.isDarkMode) }
            var biometricPassed by remember { mutableStateOf(false) }

            SelfAttendanceTheme(darkTheme = darkMode) {

                if (biometricRequired() && !biometricPassed) {

                    BiometricGate(
                        onSuccess = { biometricPassed = true }
                    )

                } else {
                    AppNavGraph()
                }

                // ✅ Snackbar — "Naya update ready hai! → Restart"
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }

    // ✅ onResume — agar immediate update beech mein ruk gayi thi toh resume karo
    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) {
            updateManager.resumeImmediateUpdateIfNeeded()
        }
    }

    // ✅ onDestroy — listener unregister karo memory leak rokne ke liye
    override fun onDestroy() {
        super.onDestroy()
        if (::updateManager.isInitialized) {
            updateManager.unregister()
        }
    }

    // ---------------- SECURITY FUNCTION ----------------
    private fun handleSecurityChecks() {

        if (BuildConfig.DEBUG) return

        try {

            if (RootDetector.isDeviceRooted()) {
                showSecurityWarningDialog(
                    title = "Security Warning",
                    message = "This device appears to be rooted. Some features may not work correctly. Proceed with caution."
                )
                return
            }

        } catch (_: Exception) {}
    }

    private fun showSecurityWarningDialog(title: String, message: String) {
        try {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Continue") { dialog, _ -> dialog.dismiss() }
                .setCancelable(true)
                .show()
        } catch (_: Exception) {}
    }

    // ---------------- NOTIFICATION ----------------
    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            val permission = Manifest.permission.POST_NOTIFICATIONS

            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    // ---------------- BIOMETRIC ----------------
    private fun biometricRequired(): Boolean {
        return PreferencesManager(this).isBiometricEnabled
    }

    // ---------------- ADS ----------------
    private fun initAds() {
        try {
            AdManager.loadAd(this)
        } catch (_: Exception) {}
    }

    // ---------------- REMINDER ----------------
    private fun scheduleAttendanceReminder() {

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val reminderWork =
            PeriodicWorkRequestBuilder<AttendanceReminderWorker>(
                24,
                TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(applicationContext)
            .enqueueUniquePeriodicWork(
                "attendanceReminder",
                ExistingPeriodicWorkPolicy.KEEP,
                reminderWork
            )
    }
}