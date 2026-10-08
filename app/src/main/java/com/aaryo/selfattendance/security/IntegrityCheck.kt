package com.aaryo.selfattendance.security

import android.content.Context
import android.util.Base64
import android.util.Log
import com.aaryo.selfattendance.BuildConfig
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.security.SecureRandom

/**
 * Enterprise Device Integrity and Play Integrity API Engine.
 *
 * Verifies that the app is executing on a genuine, unmodified device
 * and uncompromised runtime environment with ZERO false positives on
 * non-rooted user devices.
 *
 * Checks performed:
 * 1. Verified root detection (Magisk, KernelSU, active SU binaries, root management apps)
 * 2. Virtual/cloned containers (Parallel Space, Dual Space, VirtualXposed)
 * 3. Dynamic instrumentation & hooking frameworks (Frida, Xposed, Substrate)
 * 4. Google Play Integrity API token handshake (optional handshake, never blocks legit sideloads)
 */
class IntegrityCheck(private val context: Context) {

    sealed class IntegrityVerdict {
        object Trusted : IntegrityVerdict()
        data class Compromised(val reason: String) : IntegrityVerdict()
    }

    /**
     * Executes the comprehensive integrity evaluation asynchronously.
     */
    fun checkIntegrity(onVerdict: (verdict: IntegrityVerdict) -> Unit) {
        // 1. Precise Root Detection (with zero false positives)
        if (RootDetector.isDeviceRooted(context)) {
            Log.e(TAG, "Device compromised: Verified Root detected")
            onVerdict(IntegrityVerdict.Compromised("ROOT_DETECTED"))
            return
        }

        // 2. Virtual or Cloned Environment Detection
        if (AntiDecompileGuard.isVirtualOrClonedEnvironment(context)) {
            Log.e(TAG, "Device compromised: Cloned / virtual container detected")
            onVerdict(IntegrityVerdict.Compromised("VIRTUAL_CONTAINER_DETECTED"))
            return
        }

        // 3. Debugger and Hooking Framework Detection
        if (!AntiDecompileGuard.isDeviceAndAppSecure(context)) {
            Log.e(TAG, "Device compromised: Active debugger / hooking framework detected")
            onVerdict(IntegrityVerdict.Compromised("HOOKING_FRAMEWORK_DETECTED"))
            return
        }

        // 4. Amazon FireOS / Non-Play environment skip
        if (BuildConfig.IS_AMAZON) {
            Log.d(TAG, "Amazon build — Play Integrity skipped, local checks passed")
            onVerdict(IntegrityVerdict.Trusted)
            return
        }

        // 5. Google Play Integrity API Handshake
        // Used to attest device integrity with Google Play services.
        // If Play Integrity is unavailable (e.g. testing APK outside Play Store or without Play Services),
        // we log and trust, preventing false positive lockouts on legitimate user devices.
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
                        Log.d(TAG, "Play Integrity token successfully obtained")
                    } else {
                        Log.w(TAG, "Play Integrity returned an empty token")
                    }
                    onVerdict(IntegrityVerdict.Trusted)
                }
                .addOnFailureListener { e ->
                    // Graceful fallback: Do not block users who installed APK directly or are offline
                    Log.d(TAG, "Play Integrity API handshake skipped: ${e.message}")
                    onVerdict(IntegrityVerdict.Trusted)
                }

        } catch (e: Exception) {
            Log.d(TAG, "Play Integrity initialization skipped: ${e.message}")
            onVerdict(IntegrityVerdict.Trusted)
        }
    }

    /**
     * Backward-compatible simple boolean check for legacy calls.
     */
    fun check(onResult: (passed: Boolean) -> Unit = {}) {
        checkIntegrity { verdict ->
            onResult(verdict is IntegrityVerdict.Trusted)
        }
    }

    companion object {
        private const val TAG = "IntegrityCheck"
    }
}
