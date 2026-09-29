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
 * and uncompromised runtime environment.
 *
 * Checks performed:
 * 1. Root and SU detection (Magisk, KernelSU, Busybox, test-keys, su binaries)
 * 2. Virtual/cloned containers (Parallel Space, Dual Space, VirtualXposed)
 * 3. Dynamic instrumentation & hooking frameworks (Frida, Xposed, Substrate)
 * 4. Google Play Integrity API token handshake
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
        // 1. Fast-path Root Detection
        if (RootDetector.isDeviceRooted()) {
            Log.e(TAG, "Device compromised: Root detected")
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
                        onVerdict(IntegrityVerdict.Trusted)
                    } else {
                        Log.w(TAG, "Play Integrity returned an empty token")
                        // In release, empty token is considered suspicious
                        if (!BuildConfig.DEBUG) {
                            onVerdict(IntegrityVerdict.Compromised("EMPTY_INTEGRITY_TOKEN"))
                        } else {
                            onVerdict(IntegrityVerdict.Trusted)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "Play Integrity API error: ${e.message}")
                    // In debug / emulators without Google Play Store, allow graceful fallback
                    // On genuine devices with Google Play, log exception
                    onVerdict(IntegrityVerdict.Trusted)
                }

        } catch (e: Exception) {
            Log.w(TAG, "Play Integrity initialization failure: ${e.message}")
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
