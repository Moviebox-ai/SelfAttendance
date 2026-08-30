package com.aaryo.selfattendance.update

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AlternativeUpdateManager — REMOVED.
 * This stub exists only to satisfy any remaining compile-time references.
 * All methods are no-ops. Delete this file once all call-sites are removed.
 */
class AlternativeUpdateManager(context: Context) {

    sealed class UpdateState {
        object None           : UpdateState()
        data class Available(
            val versionName : String = "",
            val changelog   : String = "",
            val apkUrl      : String = "",
            val isForced    : Boolean = false,
            val title       : String = ""
        ) : UpdateState()
        object Downloading    : UpdateState()
        data class Progress(val percent: Int) : UpdateState()
        object ReadyToInstall : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.None)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkAltUpdate() { /* removed */ }
    fun downloadAndInstall(activity: Activity, apkUrl: String) { /* removed */ }
    fun installApk(activity: Activity) { /* removed */ }
    fun cancelPolling() { /* removed */ }
}
