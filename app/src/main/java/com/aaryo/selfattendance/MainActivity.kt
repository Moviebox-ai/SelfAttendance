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
  import androidx.activity.enableEdgeToEdge
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
  import com.aaryo.selfattendance.security.AntiDecompileGuard
import com.aaryo.selfattendance.security.BiometricGate
  import com.aaryo.selfattendance.security.IntegrityCheck
  import com.aaryo.selfattendance.security.RootDetector
  import com.aaryo.selfattendance.ui.navigation.AppNavGraph
  import com.aaryo.selfattendance.ui.navigation.Routes
  import com.aaryo.selfattendance.ui.theme.AppTheme
  import com.aaryo.selfattendance.ui.theme.SelfAttendanceTheme
  import com.aaryo.selfattendance.ui.update.ForceUpdateScreen
  import com.aaryo.selfattendance.update.InAppUpdateManager
  import com.aaryo.selfattendance.notifications.ReminderScheduler
  import com.aaryo.selfattendance.utils.LocaleManager
  import com.aaryo.selfattendance.utils.NetworkMonitor
  import com.google.firebase.crashlytics.FirebaseCrashlytics
  import kotlinx.coroutines.launch
  import androidx.lifecycle.compose.collectAsStateWithLifecycle
  import com.aaryo.selfattendance.billing.BillingManager
  import com.aaryo.selfattendance.ui.components.SubscriptionCelebrationDialog

  class MainActivity : FragmentActivity() {

      override fun attachBaseContext(newBase: Context) {
          val lang = PreferencesManager(newBase).selectedLanguage
          super.attachBaseContext(LocaleManager.applyLocale(newBase, lang))
      }

      override fun onConfigurationChanged(newConfig: Configuration) {
          super.onConfigurationChanged(newConfig)
          val savedLang = PreferencesManager(this).selectedLanguage
          val activeLang = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
              newConfig.locales.get(0)?.toLanguageTag() ?: "en"
          } else {
              @Suppress("DEPRECATION") newConfig.locale?.toLanguageTag() ?: "en"
          }
          if (activeLang != LocaleManager.normalizeLanguageTag(savedLang)) {
              recreate()
          }
      }

      private val remoteConfig      = RemoteConfigManager.getInstance()
      private lateinit var updateManager: InAppUpdateManager
      private lateinit var networkMonitor: NetworkMonitor
      private val snackbarHostState = SnackbarHostState()

      private val _notificationScreen = mutableStateOf<String?>(null)

      private val notificationPermissionLauncher =
          registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

      override fun onCreate(savedInstanceState: Bundle?) {
          installSplashScreen()
          super.onCreate(savedInstanceState)

          enableEdgeToEdge()

          LocaleManager.syncSystemAppLocaleIfNeeded(this)

          updateManager = InAppUpdateManager(this)
          networkMonitor = NetworkMonitor(this)

          // Whenever internet becomes available, immediately check & trigger auto-update
          networkMonitor.setOnNetworkAvailableListener {
              runOnUiThread {
                  checkAndTriggerAutoUpdate()
              }
          }

          AppNotificationManager.setupChannels(this)

          _notificationScreen.value = resolveOpenScreen(intent)

          checkAndTriggerAutoUpdate()

          lifecycleScope.launch {
              try {
                  remoteConfig.fetch()
                  checkAndTriggerAutoUpdate()
              } catch (e: kotlinx.coroutines.CancellationException) {
                  throw e
              } catch (e: Exception) {
                  FirebaseCrashlytics.getInstance().recordException(e)
              }
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
              val billingManager = remember { BillingManager.getInstance(this@MainActivity) }
              var darkMode  by remember { mutableStateOf(prefManager.isDarkMode) }
              var appTheme  by remember { mutableStateOf(AppTheme.fromKey(prefManager.selectedTheme)) }
              var biometricPassed by remember { mutableStateOf(false) }
              var celebrationSku by remember { mutableStateOf<String?>(null) }

              LaunchedEffect(billingManager) {
                  billingManager.purchaseCelebrationEvents.collect { sku ->
                      celebrationSku = sku
                  }
              }

              val notifScreen by _notificationScreen
              val isForceBlocked by updateManager.isForceUpdateBlocked.collectAsStateWithLifecycle()

              SelfAttendanceTheme(appTheme = appTheme, darkTheme = darkMode) {

                  if (isForceBlocked) {
                      ForceUpdateScreen(
                          onRetryCheck = { checkAndTriggerAutoUpdate() }
                      )
                  } else if (biometricRequired() && !biometricPassed) {
                      BiometricGate { biometricPassed = true }
                  } else {
                      AppNavGraph(notificationStartScreen = notifScreen)
                  }

                  celebrationSku?.let { sku ->
                      SubscriptionCelebrationDialog(
                          sku = sku,
                          onDismiss = { celebrationSku = null }
                      )
                  }

                  SnackbarHost(hostState = snackbarHostState)
              }
          }
      }

      private fun checkAndTriggerAutoUpdate() {
          if (isFinishing || isDestroyed) return
          lifecycleScope.launch {
              try {
                  val minVersion = remoteConfig.getMinRequiredVersion()
                  val forceRequired = remoteConfig.isForceUpdateRequired()
                  val currentVersion = BuildConfig.VERSION_CODE.toLong()
                  val mustForce = forceRequired || (minVersion > 0 && currentVersion < minVersion)

                  updateManager.checkForUpdate(
                      forceUpdate = mustForce,
                      autoInstallOnDownload = true,
                      onDownloaded = {
                          lifecycleScope.launch {
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
              } catch (e: Exception) {
                  FirebaseCrashlytics.getInstance().recordException(e)
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
              "settings"  -> Routes.SETTINGS   // Auto backup notification deep-links here
              else        -> null
          }
      }

      // ── Ads initialisation ────────────────────────────────────────────────

      private fun initAds() {
          try {
              // The UMP consent form was already shown by requestConsentAndInitAds().
              // If the user declined or consent is REQUIRED, AdMob internally serves
              // non-personalized ads — we must never block the entire ad pipeline.
              // canShowAds() returning false used to leave isOnboardingActive=true
              // permanently, silently blocking ALL full-screen ads for the entire session.
              lifecycleScope.launch {
                  // BUG FIX: Previously this only set isAdsEnabled = true when Remote Config
                  // said enabled, but never explicitly set it to false when disabled.
                  // If SelfAttendanceApp.initRemoteConfig() hadn't finished yet at this point,
                  // isAdsEnabled stayed true (the Application-level default) even when the
                  // server intended to disable ads. Now we read and apply the Remote Config
                  // value in both directions so the decision is always consistent.
                  val remote = RemoteConfigManager.getInstance()
                  val adsEnabled = remote.isAdsEnabled()
                  AdsController.isAdsEnabled = adsEnabled
                  if (!adsEnabled) {
                      android.util.Log.w("MainActivity", "initAds: ads disabled by Remote Config")
                  }
                  AdsController.preload(this@MainActivity)

                  window.decorView.post {
                      AdsController.isOnboardingActive = false

                      val app = application as? SelfAttendanceApp
                      app?.appOpenAdManager?.let { manager ->
                          manager.loadAd()
                          if (AdsController.isAdsEnabled && !biometricRequired()) manager.showOnColdStart()
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
              checkAndTriggerAutoUpdate()
          }
      }

      override fun onDestroy() {
          super.onDestroy()
          if (::updateManager.isInitialized) {
              updateManager.unregister()
          }
          if (::networkMonitor.isInitialized) {
              networkMonitor.unregister()
          }
      }

      // ── Security ──────────────────────────────────────────────────────────

      private fun handleSecurityChecks() {
          if (BuildConfig.DEBUG) return
          try {
              if (!AntiDecompileGuard.isDeviceAndAppSecure(this)) {
                  FirebaseCrashlytics.getInstance().log("Security check: Debugger or hooking tool active")
              }
              if (RootDetector.isDeviceRooted()) {
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

      // centralised in SelfAttendanceApp.initNotifications() which already
      // respects prefs.isReminderEnabled. Calling it here too caused every
      // app launch to schedule the WorkManager task twice unnecessarily.
  }
  