package com.aaryo.selfattendance.update

import android.app.Activity
import android.util.Log
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
 * Play Store flavor — full Google Play Core in-app update implementation.
 * This class is compiled ONLY for the "play" product flavor.
 * For the "amazon" flavor, see src/amazon/.../update/InAppUpdateManager.kt
 */
class InAppUpdateManager(private val activity: Activity) {

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity)

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
        onUpdateDownloaded = onDownloaded

        appUpdateManager.unregisterListener(installStateListener)
        appUpdateManager.registerListener(installStateListener)

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                handleUpdateInfo(info, forceUpdate)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Update check failed: ${e.message}")
            }
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
                info,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                UPDATE_REQUEST_CODE
            )
        } else if (!forceUpdate && info.isFlexibleUpdateAllowed) {
            Log.d(TAG, "Starting FLEXIBLE update")
            appUpdateManager.startUpdateFlowForResult(
                info,
                activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                UPDATE_REQUEST_CODE
            )
        }
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun resumeImmediateUpdateIfNeeded() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
                ) {
                    Log.d(TAG, "Resuming interrupted immediate update")
                    appUpdateManager.startUpdateFlowForResult(
                        info,
                        activity,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                        UPDATE_REQUEST_CODE
                    )
                }
            }
    }

    fun unregister() {
        appUpdateManager.unregisterListener(installStateListener)
    }

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 500
    }
}
