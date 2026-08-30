package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.aaryo.selfattendance.data.model.UserProfile
import com.aaryo.selfattendance.utils.UniqueIdGenerator
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

            // Generate the unique AX ID as soon as the account exists,
            // so support/admins can find this user's data on the server.
            auth.currentUser?.uid?.let { uid ->
                runCatching { UniqueIdGenerator.generateAndReserve(firestore, uid) }
                    .onFailure { Log.e("AuthRepository", "Unique ID generation failed: ${it.message}") }
            }

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

            // Brand-new Google account -> mint its unique AX ID right away.
            val isNewUser = result.additionalUserInfo?.isNewUser == true
            if (isNewUser) {
                result.user?.uid?.let { uid ->
                    runCatching { UniqueIdGenerator.generateAndReserve(firestore, uid) }
                        .onFailure { Log.e("AuthRepository", "Unique ID generation failed: ${it.message}") }
                }
            }

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

            // its default `uniqueId = ""`, wiping out whatever ID
            // UniqueIdGenerator already reserved. uniqueId is owned only by
            // UniqueIdGenerator, so it's excluded here.
            firestore.collection("users")
                .document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "name" to profile.name,
                        "monthlySalary" to profile.monthlySalary,
                        "workingDays" to profile.workingDays,
                        "standardHours" to profile.standardHours,
                        "overtimeRate" to profile.overtimeRate
                    ),
                    SetOptions.merge()
                )
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

    // ---------------- UNIQUE ID (server lookup) ----------------

    /**
     * Same as [ensureUniqueId] but propagates the real failure (permission
     * denied, network, etc.) instead of swallowing it to null.
     *
     * ID…" placeholder with no way to know WHY it never resolved and no way
     * to retry, because ensureUniqueId() caught every exception internally
     * and returned null — indistinguishable from "still loading". Use this
     * version wherever the UI needs to show an error + Retry button.
     */
    suspend fun ensureUniqueIdOrThrow(uid: String): String {
        return UniqueIdGenerator.generateAndReserve(firestore, uid)
    }

    /**
     * Ensures the given user has a unique AX ID (AX-XXXXXXX), generating one if
     * missing or old format. Called after every successful login/registration so both
     * brand-new and pre-existing accounts end up with an ID. Never throws.
     */
    suspend fun ensureUniqueId(uid: String): String? {
        return try {
            UniqueIdGenerator.generateAndReserve(firestore, uid)
        } catch (e: Exception) {
            Log.e("AuthRepository", "ensureUniqueId failed: ${e.message}")
            null
        }
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
    //  1. Deletes all user data from Firestore (users collection)
    //  2. Deletes the Firebase Auth account
    //
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

    // (chunked at 400 per batch — safely under the 500-op limit).
    // Previously, documents were deleted one-by-one with individual .await()
    // calls, which was O(n) in round-trips, very slow for users with many
    // records, and left partial data if the network failed mid-loop.
    private suspend fun batchDeleteCollection(docs: com.google.firebase.firestore.QuerySnapshot) {
        docs.documents.chunked(400).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { doc -> batch.delete(doc.reference) }
            batch.commit().await()
        }
    }

    private suspend fun deleteUserFirestoreData(uid: String) {
        try {
            // Delete main user document
            firestore
                .collection("users")
                .document(uid)
                .delete()
                .await()

            // Also delete attendance and backup collections (batched).
            // Previously these were left as orphaned data in Firestore after
            // account deletion — a data-privacy violation (Play Store policy
            // requires all user data to be erased on account deletion).
            val attendanceDays = firestore
                .collection("attendance")
                .document(uid)
                .collection("days")
                .get()
                .await()
            batchDeleteCollection(attendanceDays)
            firestore.collection("attendance").document(uid).delete().await()

            val backupDays = firestore
                .collection("backup")
                .document(uid)
                .collection("days")
                .get()
                .await()
            batchDeleteCollection(backupDays)
            firestore.collection("backup").document(uid).delete().await()

            // Previously referrals/{uid} and referralCodes/{code} were left as
            // orphaned data after account deletion — a Play Store data privacy
            // policy violation. All user data must be erased on account deletion.
            try {
                firestore.collection("referrals").document(uid).delete().await()
                Log.d("AuthRepository", "Referral record deleted for uid: $uid")
            } catch (e: Exception) {
                Log.e("AuthRepository", "Referral record deletion failed: ${e.message}")
            }
            try {
                val shortCode = uid.take(8).uppercase()
                firestore.collection("referralCodes").document(shortCode).delete().await()
                Log.d("AuthRepository", "Referral code deleted: $shortCode")
            } catch (e: Exception) {
                Log.e("AuthRepository", "Referral code deletion failed: ${e.message}")
            }

            Log.i("AuthRepository", "Firestore data deleted for uid: $uid")

        } catch (e: Exception) {
            // Log but don't block — Auth deletion still proceeds
            Log.e("AuthRepository", "Firestore data deletion partial failure: ${e.message}")
        }
    }
}
