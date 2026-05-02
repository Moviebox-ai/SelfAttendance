package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.aaryo.selfattendance.data.model.UserProfile
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // ---------------- EMAIL LOGIN ----------------

    suspend fun login(
        email: String,
        password: String
    ): Result<Unit> {

        return try {

            auth.signInWithEmailAndPassword(email, password).await()

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e("AuthRepository", "Email login failed", e)

            Result.failure(
                Exception(e.message ?: "Login failed")
            )
        }
    }

    // ---------------- REGISTER ----------------

    suspend fun register(
        email: String,
        password: String
    ): Result<Unit> {

        return try {

            auth.createUserWithEmailAndPassword(
                email,
                password
            ).await()

            // Send email verification after registration
            auth.currentUser?.sendEmailVerification()?.await()

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e("AuthRepository", "Register failed", e)

            Result.failure(
                Exception(e.message ?: "Registration failed")
            )
        }
    }

    // ---------------- FORGOT PASSWORD ----------------

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Password reset failed", e)
            Result.failure(Exception(e.message ?: "Password reset failed"))
        }
    }

    // ---------------- GOOGLE SIGN IN ----------------

    suspend fun firebaseAuthWithGoogle(
        idToken: String
    ): Result<FirebaseUser?> {

        return try {

            val credential = GoogleAuthProvider.getCredential(idToken, null)

            val result = auth.signInWithCredential(credential).await()

            Result.success(result.user)

        } catch (e: Exception) {

            Log.e("AuthRepository", "Google sign-in failed", e)

            Result.failure(
                Exception(e.message ?: "Google sign-in failed")
            )
        }
    }

    // ---------------- SAVE PROFILE ----------------

    suspend fun saveUserProfile(
        profile: UserProfile
    ): Result<Unit> {

        return try {

            val uid = auth.currentUser?.uid
                ?: return Result.failure(
                    Exception("User not logged in")
                )

            firestore.collection("users")
                .document(uid)
                .set(profile, SetOptions.merge())
                .await()

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e("AuthRepository", "Save profile failed", e)

            Result.failure(
                Exception(e.message ?: "Profile save failed")
            )
        }
    }

    // ---------------- PROFILE EXISTS ----------------

    suspend fun isUserProfileExists(): Boolean {

        return try {

            val uid = auth.currentUser?.uid ?: return false

            val doc = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            doc.exists()

        } catch (e: Exception) {

            Log.e("AuthRepository", "Profile check failed", e)

            false
        }
    }

    // ---------------- GET PROFILE ----------------

    suspend fun getUserProfile(): UserProfile? {

        return try {

            val uid = auth.currentUser?.uid ?: return null

            val doc = firestore.collection("users")
                .document(uid)
                .get()
                .await()

            doc.toObject(UserProfile::class.java)

        } catch (e: Exception) {

            Log.e("AuthRepository", "Get profile failed", e)

            null
        }
    }

    // ---------------- CURRENT USER ----------------

    fun currentUser(): FirebaseUser? {
        return auth.currentUser
    }

    // ---------------- LOGOUT ----------------

    fun logout() {
        auth.signOut()
    }

    // ────────────────────────────────────────────────────────────────────
    // DELETE ACCOUNT — PLAY STORE MANDATORY REQUIREMENT
    //
    // Google Play Policy: Apps with account creation must provide
    // in-app account deletion. (Policy effective May 2023)
    //
    // This function:
    //  1. Deletes all user data from Firestore (users + coinTransactions + wallet)
    //  2. Deletes the Firebase Auth account
    //
    // NOTE: For Google Sign-In users, re-authentication is required before
    // deletion. Pass the idToken from GoogleSignIn to reAuthenticateGoogle().
    // ────────────────────────────────────────────────────────────────────

    suspend fun deleteAccount(): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))

            val uid = user.uid

            // Step 1: Delete Firestore user data
            deleteUserFirestoreData(uid)

            // Step 2: Delete Firebase Auth account
            user.delete().await()

            Log.i("AuthRepository", "Account deleted successfully: $uid")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e("AuthRepository", "Account deletion failed", e)
            // If "requires-recent-login" error, caller should trigger re-auth
            Result.failure(Exception(e.message ?: "Account deletion failed"))
        }
    }

    /**
     * Re-authenticate with email/password before sensitive operations (account delete).
     * Required by Firebase when the session is older.
     */
    suspend fun reAuthenticateEmail(email: String, password: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))
            val credential = EmailAuthProvider.getCredential(email, password)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Re-authentication failed", e)
            Result.failure(Exception(e.message ?: "Re-authentication failed"))
        }
    }

    /**
     * Re-authenticate with Google before sensitive operations.
     */
    suspend fun reAuthenticateGoogle(idToken: String): Result<Unit> {
        return try {
            val user = auth.currentUser
                ?: return Result.failure(Exception("No user logged in"))
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            user.reauthenticate(credential).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google re-authentication failed", e)
            Result.failure(Exception(e.message ?: "Google re-authentication failed"))
        }
    }

    // ── Private helper: delete all Firestore data for a user ─────────────

    private suspend fun deleteUserFirestoreData(uid: String) {
        try {
            // Delete coin transactions sub-collection documents
            val transactions = firestore
                .collection("users")
                .document(uid)
                .collection("coinTransactions")
                .get()
                .await()
            for (doc in transactions.documents) {
                doc.reference.delete().await()
            }

            // Delete wallet document
            firestore
                .collection("users")
                .document(uid)
                .collection("wallet")
                .document("wallet")
                .delete()
                .await()

            // Delete main user document
            firestore
                .collection("users")
                .document(uid)
                .delete()
                .await()

            Log.i("AuthRepository", "Firestore data deleted for uid: $uid")

        } catch (e: Exception) {
            // Log but don't block — Auth deletion still proceeds
            Log.e("AuthRepository", "Firestore data deletion partial failure: ${e.message}")
        }
    }
}
