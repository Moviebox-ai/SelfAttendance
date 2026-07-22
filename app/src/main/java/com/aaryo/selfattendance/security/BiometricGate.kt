package com.aaryo.selfattendance.security

import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity

@Composable
fun BiometricGate(
    onSuccess: () -> Unit
) {
    val context = LocalContext.current

    // BUG FIX: `context as? FragmentActivity` fails when LocaleManager wraps the
    // context in a ContextWrapper (which it always does for locale switching).
    // Walk the ContextWrapper chain to find the real FragmentActivity.
    // If still not found, fall through to onSuccess() — same safe fallback as onError.
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<FragmentActivity>()
        .firstOrNull()

    LaunchedEffect(Unit) {
        if (activity == null) {
            // No FragmentActivity found — cannot authenticate. Let user through
            // rather than leaving them permanently locked on this screen.
            onSuccess()
            return@LaunchedEffect
        }
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
