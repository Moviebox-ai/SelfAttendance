package com.aaryo.selfattendance.update

  import android.app.Activity
  import android.util.Log

  /**
   * Amazon flavor stub — Google Play Core in-app update is NOT available on
   * Amazon Fire devices. Updates are delivered via the Amazon Appstore.
   *
   * All methods are no-ops so MainActivity can call them safely without
   * any IS_AMAZON branching.
   */
  class InAppUpdateManager(private val activity: Activity) {

      fun checkForUpdate(
          forceUpdate: Boolean = false,
          onDownloaded: (() -> Unit)? = null
      ) {
          Log.d(TAG, "Play Core updates disabled on Amazon build — updates via Amazon Appstore")
      }

      fun completeUpdate() {
          // no-op on Amazon
      }

      fun resumeImmediateUpdateIfNeeded() {
          // no-op on Amazon
      }

      fun unregister() {
          // no-op on Amazon
      }

      companion object {
          private const val TAG = "InAppUpdateManager"
          const val UPDATE_REQUEST_CODE = 500
      }
  }
  