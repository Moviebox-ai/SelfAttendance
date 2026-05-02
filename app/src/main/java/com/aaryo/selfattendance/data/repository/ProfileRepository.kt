package com.aaryo.selfattendance.data.repository

import com.aaryo.selfattendance.data.model.UserProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class ProfileRepository {

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    private val usersCollection by lazy {
        db.collection("users")
    }

    // ---------------- SAVE PROFILE ----------------

    suspend fun saveProfile(
        uid: String,
        profile: UserProfile
    ): Result<Unit> {

        return try {

            val updatedProfile = profile.copy(uid = uid)

            usersCollection
                .document(uid)
                .set(updatedProfile, SetOptions.merge())
                .await()

            Result.success(Unit)

        } catch (e: Exception) {

            Result.failure(
                Exception(e.message ?: "Failed to save profile")
            )
        }
    }

    // ---------------- UPDATE PROFILE ----------------

    suspend fun updateProfile(
        uid: String,
        profile: UserProfile
    ): Result<Unit> {

        return try {

            usersCollection
                .document(uid)
                .set(profile, SetOptions.merge())
                .await()

            Result.success(Unit)

        } catch (e: Exception) {

            Result.failure(
                Exception(e.message ?: "Failed to update profile")
            )
        }
    }

    // ---------------- UPDATE SINGLE FIELD ----------------

    suspend fun updateField(
        uid: String,
        field: String,
        value: Any
    ): Result<Unit> {

        return try {

            usersCollection
                .document(uid)
                .update(field, value)
                .await()

            Result.success(Unit)

        } catch (e: Exception) {

            Result.failure(
                Exception(e.message ?: "Failed to update field")
            )
        }
    }

    // ---------------- GET PROFILE ----------------

    suspend fun getProfile(
        uid: String
    ): Result<UserProfile?> {

        return try {

            val doc = usersCollection
                .document(uid)
                .get()
                .await()

            val profile = doc.toObject(UserProfile::class.java)

            Result.success(profile)

        } catch (e: Exception) {

            Result.failure(
                Exception(e.message ?: "Failed to load profile")
            )
        }
    }

    // ---------------- CHECK PROFILE EXISTS ----------------

    suspend fun profileExists(uid: String): Boolean {

        return try {

            val doc = usersCollection
                .document(uid)
                .get()
                .await()

            doc.exists()

        } catch (e: Exception) {

            false
        }
    }

    // ---------------- DELETE PROFILE ----------------

    suspend fun deleteProfile(
        uid: String
    ): Result<Unit> {

        return try {

            usersCollection
                .document(uid)
                .delete()
                .await()

            Result.success(Unit)

        } catch (e: Exception) {

            Result.failure(
                Exception(e.message ?: "Failed to delete profile")
            )
        }
    }
}