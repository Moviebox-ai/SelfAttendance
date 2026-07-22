package com.aaryo.selfattendance.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Generates a unique, human-shareable 6-digit numeric ID for each user account.
 *
 * The ID exists so support/admins can look up a user's data on the server
 * (Firestore) without needing their long Firebase UID. It is reserved
 * atomically in a dedicated `userIds` collection (documentId = the 6-digit
 * code) so two accounts can never collide, then copied onto the user's
 * `users/{uid}` document as the `uniqueId` field.
 */
object UniqueIdGenerator {

    private const val ID_COLLECTION = "userIds"
    private const val MIN_ID = 100000
    private const val MAX_ID = 999999
    private const val MAX_ATTEMPTS = 15

    /** Generates a random 6-digit numeric string, e.g. "042917". */
    private fun randomSixDigitCode(): String =
        Random.nextInt(MIN_ID, MAX_ID + 1).toString()

    /**
     * Returns the existing 6-digit ID already reserved for [uid], if any.
     */
    suspend fun existingIdFor(firestore: FirebaseFirestore, uid: String): String? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.getString("uniqueId")?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            Log.e("UniqueIdGenerator", "existingIdFor failed: ${e.message}")
            null
        }
    }

    /**
     * Ensures [uid] has a 6-digit unique ID, generating and atomically
     * reserving a fresh one if it doesn't have one yet. Safe to call
     * repeatedly (e.g. on every login) — it is a no-op once an ID exists.
     */
    suspend fun generateAndReserve(
        firestore: FirebaseFirestore,
        uid: String
    ): String {

        // Already has one? Reuse it instead of minting a duplicate.
        existingIdFor(firestore, uid)?.let { return it }

        val usersRef = firestore.collection("users").document(uid)
        var lastError: Exception? = null

        repeat(MAX_ATTEMPTS) {
            val code = randomSixDigitCode()
            val idRef = firestore.collection(ID_COLLECTION).document(code)

            try {
                firestore.runTransaction { txn ->
                    val existing = txn.get(idRef)
                    if (existing.exists()) {
                        throw UniqueIdCollisionException()
                    }
                    txn.set(idRef, mapOf("uid" to uid))
                    txn.set(usersRef, mapOf("uid" to uid, "uniqueId" to code), SetOptions.merge())
                }.await()

                Log.i("UniqueIdGenerator", "Reserved unique ID $code for uid: $uid")
                return code

            } catch (e: UniqueIdCollisionException) {
                // Code already taken by another user -- retry with a new random code.
            } catch (e: Exception) {
                lastError = e
                Log.e("UniqueIdGenerator", "Reserve attempt failed: ${e.message}")
            }
        }

        // BUG FIX: previously threw a generic "after N attempts" message with
        // no clue why (e.g. PERMISSION_DENIED from Firestore rules was
        // logged but never surfaced). Include the last real failure reason
        // so it's visible wherever this exception's message ends up (e.g. an
        // error banner in the UI), instead of only in Logcat.
        val reason = lastError?.message
        throw IllegalStateException(
            "Could not generate a unique ID after $MAX_ATTEMPTS attempts" +
                (reason?.let { ": $it" } ?: "")
        )
    }

    private class UniqueIdCollisionException : Exception("Unique ID already taken")
}
