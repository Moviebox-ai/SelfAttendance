package com.aaryo.selfattendance.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.security.SecureRandom

/**
 * Verifies device and app integrity via the Google Play Integrity API.
 *
 * On Google Play builds (IS_AMAZON == false) — requests a Play Integrity token
 * and reports the result. Failure always passes (never blocks the user).
 *
 * On Amazon builds (IS_AMAZON == true) — immediately passes without calling
 * any Play APIs (Play Integrity is unavailable on Fire devices).
 */
class IntegrityCheck(private val context: Context) {

    fun check(onResult: (passed: Boolean) -> Unit = {}) {
        if (BuildConfig.IS_AMAZON) {
            Log.d(TAG, "Amazon build — skipping Play Integrity check")
            onResult(true)
            return
        }

        try {
            val integrityManager = IntegrityManagerFactory.create(context)

            val nonceBytes = ByteArray(24)
            SecureRandom().nextBytes(nonceBytes)
            val nonce = Base64.encodeToString(
                nonceBytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val request = IntegrityTokenRequest.builder()
                .setNonce(nonce)
                .build()

            integrityManager.requestIntegrityToken(request)
                .addOnSuccessListener { response ->
                    val token = response.token()
                    if (token.isNotEmpty()) {
                        Log.d(TAG, "Integrity token received")
                        onResult(true)
                    } else {
                        Log.w(TAG, "Empty integrity token")
                        onResult(false)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Integrity check failed: ${e.message}")
                    onResult(true) // Graceful fallback — never block the user
                }

        } catch (e: Exception) {
            Log.e(TAG, "Integrity check crash: ${e.message}")
            onResult(true) // Graceful fallback
        }
    }

    companion object {
        private const val TAG = "IntegrityCheck"
    }
}
