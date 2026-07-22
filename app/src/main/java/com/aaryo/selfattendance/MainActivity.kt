package com.aaryo.selfattendance

  import android.content.Context
  import android.Manifest
  import android.app.AlertDialog
  import android.content.Intent
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
  import androidx.core.content.ContextCompat
  import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
  import androidx.fragment.app.FragmentActivity
  import androidx.lifecycle.lifecycleScope
  import com.aaryo.selfattendance.ads.AdsController
  import com.aaryo.selfattendance.ads.ConsentManager
  import com.aaryo.selfattendance.data.local.PreferencesManager
  import com.aaryo.selfattendance.data.remote.RemoteConfigManager
  import com.aaryo.selfattendance.notifications.AppNotificationManager
  import com.aaryo.selfattendance.security.BiometricGate
  import com.aaryo.selfattendance.security.IntegrityCheck
  import com.aaryo.selfattendance.security.RootDetector
  import com.aaryo.selfattendance.ui.navigation.AppNavGraph
  import com.aaryo.selfattendance.ui.navigation.Routes
  import com.aaryo.selfattendance.ui.theme.AppTheme
  import com.aaryo.selfattendance.ui.theme.SelfAttendanceTheme
  import com.aaryo.selfattendance.update.InAppUpdateManager
  import com.aaryo.selfattendance.notifications.ReminderScheduler
  import com.aaryo.selfattendance.utils.LocaleManager
  import com.google.firebase.crashlytics.FirebaseCrashlytics
  import kotlinx.coroutines.launch

  class MainActivity : FragmentActivity() {

      override fun attachBaseContext(newBase: Context) {
          val lang = PreferencesManager(newBase).selectedLanguage
          super.attachBaseContext(LocaleManager.applyLocale(newBase, lang))
      }

      override fun onConfigurationChanged(newConfig: Configuration) {
          super.onConfigurationChanged(newConfig)
          val savedLang = PreferencesManager(this).selectedLanguage
          val activeLang = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
              newConfig.locales.get(0)?.language ?: "en"
          } else {
              @Suppress("DEPRECATION") newConfig.locale?.language ?: "en"
          }
          if (activeLang != savedLang) {
              recreate()
          }
      }

      private val remoteConfig      = RemoteConfigManager.getInstance()
      private lateinit var updateManager: InAppUpdateManager
      private val snackbarHostState = SnackbarHostState()

      private val _notificationScreen = mutableStateOf<String?>(null)

      private val notificationPermissionLauncher =
          registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

      override fun onCreate(savedInstanceState: Bundle?) {
          installSplashScreen()
          super.onCreate(savedInstanceState)

          updateManager = InAppUpdateManager(this)

          AppNotificationManager.setupChannels(this)

          _notificationScreen.value = resolveOpenScreen(intent)

          updateManager.checkForUpdate(
              forceUpdate = false,
              onDownloaded = {
                  lifecycleScope.launch {
                      // BUG FIX: was using hardcoded "Update ready!" / "Restart" strings.
                      // Use the existing string resources so all locales display correctly.
                      val result = snackbarHostState.showSnackbar(
                          message     = getString(R.string.update_ready_message),
                          actionLabel = getString(R.string.update_restart_action)
                      )
                      if (result == SnackbarResult.ActionPerformed) {
                          updateManager.completeUpdate()
                      }
                  }
              }
          )

          lifecycleScope.launch {
              try { remoteConfig.fetch() } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
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
          } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }

          requestNotificationPermission()
          handleSecurityChecks()

          try { IntegrityCheck(this).check() } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }

          ConsentManager.requestConsent(this) { initAds() }

          setContent {
              val prefManager = remember { PreferencesManager(this@MainActivity) }
              var darkMode  by remember { mutableStateOf(prefManager.isDarkMode) }
              var appTheme  by remember { mutableStateOf(AppTheme.fromKey(prefManager.selectedTheme)) }
              var biometricPassed by remember { mutableStateOf(false) }

              val notifScreen by _notificationScreen

              SelfAttendanceTheme(appTheme = appTheme, darkTheme = darkMode) {

                  if (biometricRequired() && !biometricPassed) {
                      BiometricGate { biometricPassed = true }
                  } else {
                      AppNavGraph(notificationStartScreen = notifScreen)
                  }

                  SnackbarHost(hostState = snackbarHostState)
              }
          }
      }

      override fun onNewIntent(intent: Intent) {
          super.onNewIntent(intent)
          setIntent(intent)
          _notificationScreen.value = resolveOpenScreen(intent)
      }

      private fun resolveOpenScreen(intent: Intent?): String? {
          return when (intent?.getStringExtra("open_screen")) {
              "calendar"  -> Routes.CALENDAR
              "dashboard" -> Routes.DASHBOARD
              else        -> null
          }
      }

      // ── Ads initialisation ────────────────────────────────────────────────

      private fun initAds() {
          try {
              // BUG FIX: Do NOT early-return based on canShowAds() here.
              // The UMP consent form was already shown by requestConsentAndInitAds().
              // If the user declined or consent is REQUIRED, AdMob internally serves
              // non-personalized ads — we must never block the entire ad pipeline.
              // canShowAds() returning false used to leave isOnboardingActive=true
              // permanently, silently blocking ALL full-screen ads for the entire session.
              lifecycleScope.launch {
                  AdsController.isAdsEnabled = true

                  if (AdsController.isAdsEnabled) {
                      AdsController.preload(this@MainActivity)
                  }

                  window.decorView.post {
                      AdsController.isOnboardingActive = false

                      val app = application as? SelfAttendanceApp
                      app?.appOpenAdManager?.let { manager ->
                          manager.loadAd()
                          if (AdsController.isAdsEnabled) manager.showOnColdStart()
                      }
                  }
              }
          } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
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
                  // BUG FIX: was passing hardcoded English strings — use string resources
                  // so the dialog is translated correctly for all supported locales.
                  showSecurityWarningDialog(
                      getString(R.string.security_warning_title),
                      getString(R.string.security_rooted_device_msg)
                  )
              }
          } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
      }

      private fun showSecurityWarningDialog(title: String, message: String) {
          try {
              AlertDialog.Builder(this)
                  .setTitle(title)
                  .setMessage(message)
                  // BUG FIX: "Continue" was hardcoded — use the existing security_continue_btn resource.
                  .setPositiveButton(R.string.security_continue_btn) { d, _ -> d.dismiss() }
                  .show()
          } catch (e: Exception) { FirebaseCrashlytics.getInstance().recordException(e) }
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

      // BUG FIX #7: Removed scheduleAttendanceReminder() — scheduling is now
      // centralised in SelfAttendanceApp.initNotifications() which already
      // respects prefs.isReminderEnabled. Calling it here too caused every
      // app launch to schedule the WorkManager task twice unnecessarily.
  }
  