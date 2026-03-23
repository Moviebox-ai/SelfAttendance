package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.aaryo.selfattendance.data.model.UserProfile
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

            Result.success(Unit)

        } catch (e: Exception) {

            Log.e("AuthRepository", "Register failed", e)

            Result.failure(
                Exception(e.message ?: "Registration failed")
            )
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
}
