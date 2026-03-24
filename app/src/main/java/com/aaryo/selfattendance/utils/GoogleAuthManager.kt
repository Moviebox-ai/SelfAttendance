package com.aaryo.selfattendance.utils

import android.app.Activity
import android.content.Intent
import com.google.android.gms.auth.api.signin.*
import com.google.firebase.auth.*
import kotlinx.coroutines.tasks.await

class GoogleAuthManager(activity: Activity) {

    private val auth = FirebaseAuth.getInstance()

    private val googleSignInClient: GoogleSignInClient

    init {

        val options = GoogleSignInOptions.Builder(
            GoogleSignInOptions.DEFAULT_SIGN_IN
        )
            .requestIdToken(
                activity.getString(com.aaryo.selfattendance.R.string.default_web_client_id)
            )
            .requestEmail()
            .build()

        googleSignInClient =
            GoogleSignIn.getClient(activity, options)
    }

    fun getSignInIntent(): Intent {
        // Sign out first so account picker always appears
        googleSignInClient.signOut()
        return googleSignInClient.signInIntent
    }

    suspend fun firebaseAuthWithGoogle(
        idToken: String
    ): Result<FirebaseUser?> {

        return try {

            val credential = GoogleAuthProvider.getCredential(idToken, null)

            val result = auth.signInWithCredential(credential).await()

            Result.success(result.user)

        } catch (e: Exception) {

            Result.failure(e)
        }
    }
}
