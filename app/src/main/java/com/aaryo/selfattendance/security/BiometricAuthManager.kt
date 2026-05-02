package com.aaryo.selfattendance.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthManager(
    private val activity: FragmentActivity
) {

    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {

        val biometricManager = BiometricManager.from(activity)

        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        when (biometricManager.canAuthenticate(authenticators)) {

            BiometricManager.BIOMETRIC_SUCCESS -> {

                val executor = ContextCompat.getMainExecutor(activity)

                val biometricPrompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {

                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            onSuccess()
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {

                            if (errorCode ==
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                return
                            }

                            onError(errString.toString())
                        }

                        override fun onAuthenticationFailed() {
                            onError("Authentication failed")
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Self Attendance Pro")
                    .setSubtitle("Authenticate to continue")
                    .setAllowedAuthenticators(authenticators)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                onError("No biometric hardware found")

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                onError("Biometric hardware unavailable")

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                onError("No biometric credentials enrolled")

            else ->
                onError("Biometric authentication unavailable")
        }
    }
}