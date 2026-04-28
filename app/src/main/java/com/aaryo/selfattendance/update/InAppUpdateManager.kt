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

class InAppUpdateManager(private val activity: Activity) {

    private val appUpdateManager: AppUpdateManager =
        AppUpdateManagerFactory.create(activity)

    private var onUpdateDownloaded: (() -> Unit)? = null

    // ── Flexible update listener ──────────────────────────────────────────────
    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Log.d(TAG, "Update downloaded — ready to install")
            onUpdateDownloaded?.invoke()
        }
    }

    // ── Check for update ──────────────────────────────────────────────────────
    fun checkForUpdate(
        // true  = Immediate (force update — user cannot skip)
        // false = Flexible  (background download, soft prompt)
        forceUpdate: Boolean = false,
        onDownloaded: (() -> Unit)? = null
    ) {
        onUpdateDownloaded = onDownloaded

        appUpdateManager.registerListener(installStateListener)

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                handleUpdateInfo(info, forceUpdate)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Update check failed: ${e.message}")
            }
    }

    // ── Handle update info ────────────────────────────────────────────────────
    private fun handleUpdateInfo(info: AppUpdateInfo, forceUpdate: Boolean) {

        val isAvailable = info.updateAvailability() ==
                UpdateAvailability.UPDATE_AVAILABLE

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

    // ── Complete flexible update (call this when user taps "Restart") ─────────
    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    // ── Resume interrupted immediate update ───────────────────────────────────
    // MainActivity ke onResume mein call karo
    fun resumeImmediateUpdateIfNeeded() {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() ==
                    UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {

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

    // ── Cleanup ───────────────────────────────────────────────────────────────
    fun unregister() {
        appUpdateManager.unregisterListener(installStateListener)
    }

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 500
    }
}