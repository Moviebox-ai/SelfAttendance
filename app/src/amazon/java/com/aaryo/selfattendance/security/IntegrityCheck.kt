package com.aaryo.selfattendance.security

import android.content.Context
import android.util.Log

/**
 * Amazon flavor stub — Google Play Integrity API is not available on Amazon
 * Fire devices. Integrity validation is skipped entirely; the check always
 * passes so the app continues normally.
 *
 * Future: replace with a server-side signature check or Amazon Device
 * Messaging token verification if deeper trust is needed.
 */
class IntegrityCheck(private val context: Context) {

    fun check(onResult: (passed: Boolean) -> Unit = {}) {
        Log.d("IntegrityCheck", "Play Integrity skipped on Amazon build — passing automatically")
        onResult(true)
    }
}
