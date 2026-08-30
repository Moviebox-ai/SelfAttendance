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

    // ── Fields that SetupProfileScreen / EditProfileScreen are actually
    //    allowed to write. `uniqueId` is DELIBERATELY excluded here — it is
    //    owned exclusively by UniqueIdGenerator/AuthRepository.ensureUniqueId().
    //
    //    UserProfile has `val uniqueId: String = ""`. The old code did
    //    `.set(profile, SetOptions.merge())` with the FULL POJO, and
    //    SetupProfileScreen/EditProfileScreen build `UserProfile(...)`
    //    without ever passing `uniqueId`, so it defaulted to "". Firestore's
    //    merge() writes *every* field present in the POJO — including that
    //    blank uniqueId — so every profile save/update silently overwrote
    //    the real 6-digit ID with "". That's why the ID "wasn't generating":
    //    it was generated fine by UniqueIdGenerator, then immediately erased
    //    the moment the user saved/edited their profile. Fix: build an
    //    explicit field map that never includes uniqueId, so profile saves
    //    can no longer touch it at all.
    private fun editableFieldsMap(uid: String, profile: UserProfile): Map<String, Any> = mapOf(
        "uid"           to uid,
        "name"          to profile.name,
        "monthlySalary" to profile.monthlySalary,
        "workingDays"   to profile.workingDays,
        "standardHours" to profile.standardHours,
        "overtimeRate"  to profile.overtimeRate
    )

    // ---------------- SAVE PROFILE ----------------

    suspend fun saveProfile(
        uid: String,
        profile: UserProfile
    ): Result<Unit> {

        return try {

            usersCollection
                .document(uid)
                .set(editableFieldsMap(uid, profile), SetOptions.merge())
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

            // lekin updateProfile mein nahi. Agar caller galat uid bheje to
            // Firestore document sahi (uid parameter) par likhta tha lekin
            // document ke andar "uid" field galat rehti thi. Ab dono consistent
            // hain (editableFieldsMap() dono jagah `uid` parameter use karta hai).
            usersCollection
                .document(uid)
                .set(editableFieldsMap(uid, profile), SetOptions.merge())
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

    // UniqueIdGenerator.generateAndReserve() merges a bare
    // { uid, uniqueId } map into users/{uid} for EVERY account (new or
    // existing) right after login/register, and AuthViewModel calls
    // ensureUniqueId() before profileExists(). That means the users/{uid}
    // document already exists by the time this check runs, even for a
    // brand-new account that has never filled in the Setup Profile form —
    // so doc.exists() was always true and the app navigated straight to
    // the dashboard instead of Setup Profile.
    //
    // A profile only "exists" once the user has actually filled the setup
    // form, and `name` is the one required field SetupProfileScreen always
    // writes. So check for a non-blank `name` instead of raw doc existence.
    suspend fun profileExists(uid: String): Boolean {

        return try {

            val doc = usersCollection
                .document(uid)
                .get()
                .await()

            doc.exists() && !doc.getString("name").isNullOrBlank()

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
