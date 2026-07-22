package com.aaryo.selfattendance.update

  import android.app.Activity
  import android.util.Log
  import com.aaryo.selfattendance.BuildConfig
  import com.google.android.play.core.appupdate.AppUpdateInfo
  import com.google.android.play.core.appupdate.AppUpdateManager
  import com.google.android.play.core.appupdate.AppUpdateManagerFactory
  import com.google.android.play.core.appupdate.AppUpdateOptions
  import com.google.android.play.core.install.InstallStateUpdatedListener
  import com.google.android.play.core.install.model.AppUpdateType
  import com.google.android.play.core.install.model.InstallStatus
  import com.google.android.play.core.install.model.UpdateAvailability
  import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
  import com.google.android.play.core.ktx.isImmediateUpdateAllowed

  /**
   * In-app update manager using Google Play Core.
   *
   * On Google Play builds (IS_AMAZON == false) — uses Play Core to check for
   * and deliver in-app updates (flexible + immediate).
   *
   * On Amazon builds (IS_AMAZON == true) — all methods are no-ops.
   * Updates on Amazon are handled by the Amazon Appstore.
   */
  class InAppUpdateManager(private val activity: Activity) {

      private val appUpdateManager: AppUpdateManager by lazy {
          AppUpdateManagerFactory.create(activity)
      }

      private var onUpdateDownloaded: (() -> Unit)? = null

      private val installStateListener = InstallStateUpdatedListener { state ->
          if (state.installStatus() == InstallStatus.DOWNLOADED) {
              Log.d(TAG, "Update downloaded — ready to install")
              onUpdateDownloaded?.invoke()
          }
      }

      fun checkForUpdate(
          forceUpdate: Boolean = false,
          onDownloaded: (() -> Unit)? = null
      ) {
          if (BuildConfig.IS_AMAZON) {
              Log.d(TAG, "Amazon build — skipping Play in-app update check")
              return
          }
          onUpdateDownloaded = onDownloaded

          appUpdateManager.unregisterListener(installStateListener)
          appUpdateManager.registerListener(installStateListener)

          appUpdateManager.appUpdateInfo
              .addOnSuccessListener { info -> handleUpdateInfo(info, forceUpdate) }
              .addOnFailureListener { e -> Log.e(TAG, "Update check failed: ${e.message}") }
      }

      private fun handleUpdateInfo(info: AppUpdateInfo, forceUpdate: Boolean) {
          val isAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE

          if (!isAvailable) {
              Log.d(TAG, "No update available")
              return
          }

          if (forceUpdate && info.isImmediateUpdateAllowed) {
              Log.d(TAG, "Starting IMMEDIATE update")
              appUpdateManager.startUpdateFlowForResult(
                  info, activity,
                  AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                  UPDATE_REQUEST_CODE
              )
          } else if (!forceUpdate && info.isFlexibleUpdateAllowed) {
              Log.d(TAG, "Starting FLEXIBLE update")
              appUpdateManager.startUpdateFlowForResult(
                  info, activity,
                  AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                  UPDATE_REQUEST_CODE
              )
          }
      }

      fun completeUpdate() {
          if (BuildConfig.IS_AMAZON) return
          appUpdateManager.completeUpdate()
      }

      fun resumeImmediateUpdateIfNeeded() {
          if (BuildConfig.IS_AMAZON) return
          appUpdateManager.appUpdateInfo
              .addOnSuccessListener { info ->
                  if (info.updateAvailability() ==
                      UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                  ) {
                      Log.d(TAG, "Resuming interrupted immediate update")
                      appUpdateManager.startUpdateFlowForResult(
                          info, activity,
                          AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                          UPDATE_REQUEST_CODE
                      )
                  }
              }
      }

      fun unregister() {
          if (BuildConfig.IS_AMAZON) return
          appUpdateManager.unregisterListener(installStateListener)
      }

      companion object {
          private const val TAG = "InAppUpdateManager"
          const val UPDATE_REQUEST_CODE = 500
      }
  }
  