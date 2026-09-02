package com.aaryo.selfattendance.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * InAppUpdateManager — Automated & Enforced Play Store App Update System.
 *
 * Ensures users are instantly prompted or transitioned to the latest app version
 * as soon as an internet connection or update notification is available.
 */
class InAppUpdateManager(private val activity: Activity) {

    private val appUpdateManager: AppUpdateManager by lazy {
        AppUpdateManagerFactory.create(activity)
    }

    private val _isForceUpdateBlocked = MutableStateFlow(false)
    val isForceUpdateBlocked: StateFlow<Boolean> = _isForceUpdateBlocked.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private var onUpdateDownloaded: (() -> Unit)? = null

    private val installStateListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADING -> {
                _isDownloading.value = true
                Log.d(TAG, "Update downloading in background: ${state.bytesDownloaded()}/${state.totalBytesToDownload()}")
            }
            InstallStatus.DOWNLOADED -> {
                _isDownloading.value = false
                Log.d(TAG, "Update downloaded — ready to install")
                onUpdateDownloaded?.invoke() ?: completeUpdate()
            }
            InstallStatus.FAILED, InstallStatus.CANCELED -> {
                _isDownloading.value = false
            }
            else -> {}
        }
    }

    /**
     * Checks for available updates and starts immediate or flexible update flows.
     */
    fun checkForUpdate(
        forceUpdate: Boolean = false,
        autoInstallOnDownload: Boolean = true,
        onDownloaded: (() -> Unit)? = null
    ) {
        if (BuildConfig.IS_AMAZON) {
            Log.d(TAG, "Amazon build — skipping Play in-app update check")
            return
        }

        // 1. Check if Firebase Remote Config mandates a force update
        val remoteConfig = RemoteConfigManager.getInstance()
        val minVersion = remoteConfig.getMinRequiredVersion()
        val currentVersion = BuildConfig.VERSION_CODE.toLong()
        val isForcedByConfig = remoteConfig.isForceUpdateRequired() || (minVersion > 0 && currentVersion < minVersion)

        val mustForce = forceUpdate || isForcedByConfig
        _isForceUpdateBlocked.value = mustForce

        onUpdateDownloaded = onDownloaded

        appUpdateManager.unregisterListener(installStateListener)
        appUpdateManager.registerListener(installStateListener)

        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                handleUpdateInfo(info, mustForce)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Play Store Update check failed: ${e.message}")
                if (mustForce) {
                    _isForceUpdateBlocked.value = true
                }
            }
    }

    private fun handleUpdateInfo(info: AppUpdateInfo, forceUpdate: Boolean) {
        val isAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        val isDeveloperTriggered = info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS

        if (!isAvailable && !isDeveloperTriggered) {
            Log.d(TAG, "No Google Play update available at this time")
            // If remote config still mandates force update, keep blocking
            val remoteConfig = RemoteConfigManager.getInstance()
            val minVersion = remoteConfig.getMinRequiredVersion()
            if (minVersion > 0 && BuildConfig.VERSION_CODE < minVersion) {
                _isForceUpdateBlocked.value = true
            } else if (!remoteConfig.isForceUpdateRequired()) {
                _isForceUpdateBlocked.value = false
            }
            return
        }

        if (forceUpdate && info.isImmediateUpdateAllowed) {
            Log.d(TAG, "Starting IMMEDIATE Play Store update")
            _isForceUpdateBlocked.value = true
            appUpdateManager.startUpdateFlowForResult(
                info, activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                UPDATE_REQUEST_CODE
            )
        } else if (forceUpdate) {
            // Immediate in-app update not allowed directly by Play Core, but update is mandated:
            // Keep blocking UI so user is forced to tap "Update Now" and go to Play Store
            Log.d(TAG, "Force update mandated: keeping UI blocked for Play Store update")
            _isForceUpdateBlocked.value = true
        } else if (info.isImmediateUpdateAllowed && !BuildConfig.DEBUG) {
            // Prioritize immediate update so user's app updates without delay
            Log.d(TAG, "Starting IMMEDIATE update flow for fast synchronization")
            appUpdateManager.startUpdateFlowForResult(
                info, activity,
                AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                UPDATE_REQUEST_CODE
            )
        } else if (info.isFlexibleUpdateAllowed) {
            Log.d(TAG, "Starting FLEXIBLE background update download")
            appUpdateManager.startUpdateFlowForResult(
                info, activity,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                UPDATE_REQUEST_CODE
            )
        }
    }

    fun completeUpdate() {
        if (BuildConfig.IS_AMAZON) return
        try {
            appUpdateManager.completeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete update", e)
        }
    }

    fun resumeImmediateUpdateIfNeeded() {
        if (BuildConfig.IS_AMAZON) return
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    Log.d(TAG, "Resuming interrupted immediate update")
                    _isForceUpdateBlocked.value = true
                    appUpdateManager.startUpdateFlowForResult(
                        info, activity,
                        AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                        UPDATE_REQUEST_CODE
                    )
                } else if (info.installStatus() == InstallStatus.DOWNLOADED) {
                    Log.d(TAG, "Update already downloaded — completing installation now")
                    completeUpdate()
                }
            }
    }

    fun unregister() {
        if (BuildConfig.IS_AMAZON) return
        try {
            appUpdateManager.unregisterListener(installStateListener)
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 500

        /**
         * Opens Google Play Store directly to this app's page.
         */
        fun openPlayStore(context: Context) {
            val packageName = context.packageName
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                }
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        }
    }
}
