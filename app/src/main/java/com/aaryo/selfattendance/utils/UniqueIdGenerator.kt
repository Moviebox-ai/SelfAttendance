package com.aaryo.selfattendance.utils

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

/**
 * Generates a unique, human-shareable ID in "AX-XXXXXXX" format (e.g. "AX-4567789")
 * for each user account.
 *
 * The ID exists so support/admins can look up a user's data on the server
 * (Firestore) without needing their long Firebase UID. It is reserved
 * atomically in a dedicated `userIds` collection (documentId = the AX-code)
 * so two accounts can never collide, then copied onto the user's
 * `users/{uid}` document as the `uniqueId` field.
 */
object UniqueIdGenerator {

    private const val ID_COLLECTION = "userIds"
    private const val MAX_ATTEMPTS = 15
    private val AX_ID_REGEX = Regex("^AX-[0-9]{7}$")

    /** Generates a random AX ID string, e.g. "AX-4567789". */
    private fun randomAxCode(): String {
        val digits = Random.nextInt(0, 10000000).toString().padStart(7, '0')
        return "AX-$digits"
    }

    /** Returns true if the ID matches the valid "AX-XXXXXXX" format (AX- followed by 7 digits). */
    fun isValidAxId(id: String?): Boolean {
        return !id.isNullOrBlank() && AX_ID_REGEX.matches(id)
    }

    /**
     * Returns the existing AX-format ID already reserved for [uid], if any.
     * Returns null if missing or in old format (which triggers migration to AX-format).
     */
    suspend fun existingIdFor(firestore: FirebaseFirestore, uid: String): String? {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            val id = doc.getString("uniqueId")?.trim()
            if (isValidAxId(id)) id else null
        } catch (e: Exception) {
            Log.e("UniqueIdGenerator", "existingIdFor failed: ${e.message}")
            null
        }
    }

    /**
     * Ensures [uid] has a valid "AX-XXXXXXX" unique ID, generating and atomically
     * reserving a fresh one if it doesn't have one yet or if it has an old format ID.
     * Safe to call repeatedly (e.g. on every login) — it is a no-op once a valid AX ID exists.
     */
    suspend fun generateAndReserve(
        firestore: FirebaseFirestore,
        uid: String
    ): String {

        // Already has a valid AX-format ID? Reuse it instead of minting a duplicate.
        existingIdFor(firestore, uid)?.let { return it }

        val usersRef = firestore.collection("users").document(uid)
        var lastError: Exception? = null

        repeat(MAX_ATTEMPTS) {
            val code = randomAxCode()
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

                Log.i("UniqueIdGenerator", "Reserved unique AX ID $code for uid: $uid")
                return code

            } catch (e: UniqueIdCollisionException) {
                // Code already taken by another user -- retry with a new random code.
            } catch (e: Exception) {
                lastError = e
                Log.e("UniqueIdGenerator", "Reserve attempt failed: ${e.message}")
            }
        }

        val reason = lastError?.message
        throw IllegalStateException(
            "Could not generate a unique AX ID after $MAX_ATTEMPTS attempts" +
                (reason?.let { ": $it" } ?: "")
        )
    }

    private class UniqueIdCollisionException : Exception("Unique ID already taken")
}
