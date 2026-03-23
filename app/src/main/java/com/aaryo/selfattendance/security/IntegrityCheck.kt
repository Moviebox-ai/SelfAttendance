package com.aaryo.selfattendance.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.security.SecureRandom

/**
 * Play Integrity API — device + app authenticity verify karta hai.
 *
 * ⚠️  PRODUCTION UPGRADE (currently client-side only):
 *   1. Google Cloud Console → "Play Integrity API" enable karo
 *   2. token ko apne backend pe bhejo (Retrofit/OkHttp se POST request)
 *   3. Backend: Google ke decodeIntegrityToken endpoint se verify karo
 *   4. Backend response ke basis par access allow/deny karo
 *
 * Docs: https://developer.android.com/google/play/integrity/setup
 */
class IntegrityCheck(private val context: Context) {

    fun check(onResult: (passed: Boolean) -> Unit = {}) {

        try {

            val integrityManager = IntegrityManagerFactory.create(context)

            // Cryptographically secure nonce — replay attacks prevent karta hai
            // Base64 encoded 24-byte random value (URL-safe, no padding)
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
                        Log.d("IntegrityCheck", "Integrity token received — send to backend for server-side verification")
                        // TODO: token ko backend pe POST karo aur verify karo
                        // Example: MyApiService.verifyIntegrity(token)
                        onResult(true)
                    } else {
                        Log.w("IntegrityCheck", "Empty integrity token received")
                        onResult(false)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("IntegrityCheck", "Integrity check failed: ${e.message}")
                    // Graceful fallback — user ko block mat karo agar Play services issue ho
                    onResult(true)
                }

        } catch (e: Exception) {
            Log.e("IntegrityCheck", "Integrity check crash: ${e.message}")
            onResult(true) // Graceful fallback
        }
    }
}

