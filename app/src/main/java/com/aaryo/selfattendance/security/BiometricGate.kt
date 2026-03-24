package com.aaryo.selfattendance.security

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@Composable
fun BiometricGate(
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity ?: return

    LaunchedEffect(Unit) {
        val biometricAuth = BiometricAuthManager(activity)
        biometricAuth.authenticate(
            onSuccess = { onSuccess() },
            onError = {
                // Fallback — allow app to continue
                onSuccess()
            }
        )
    }
}
