package com.aaryo.selfattendance.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.security.SecureRandom

class IntegrityCheck(private val context: Context) {

    fun check(onResult: (passed: Boolean) -> Unit = {}) {
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
                        Log.d("IntegrityCheck", "Integrity token received")
                        onResult(true)
                    } else {
                        Log.w("IntegrityCheck", "Empty integrity token")
                        onResult(false)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("IntegrityCheck", "Integrity check failed: ${e.message}")
                    onResult(true) // Graceful fallback
                }

        } catch (e: Exception) {
            Log.e("IntegrityCheck", "Integrity check crash: ${e.message}")
            onResult(true) // Graceful fallback
        }
    }
}
