package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * ReferralRepository — Firestore-backed referral tracking.
 *
 * Structure:
 *   referrals/{referredUserUid} = {
 *     referrerUid       : String,   — who referred them
 *     startDate         : String,   — YYYY-MM-DD when referral was linked
 *     lastVisitDate     : String,   — YYYY-MM-DD of last tracked daily visit
 *     consecutiveDays   : Int,      — current streak of consecutive days
 *     rewardPaid        : Boolean   — whether 450 coins were paid to referrer
 *   }
 *
 * My referral code = my Firebase UID (first 8 chars uppercase shown in UI)
 */
object ReferralRepository {

    private const val TAG = "ReferralRepository"
    private const val COLLECTION = "referrals"
    private const val REFERRAL_REWARD_COINS = 450
    private const val REQUIRED_CONSECUTIVE_DAYS = 5

    private val db  get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    // ── Get this user's referral code (8-char uppercase prefix of UID) ──────

    fun getMyReferralCode(): String? = uid

    fun getMyReferralCodeShort(): String? = uid?.take(8)?.uppercase()

    // ── Register this user's referral code in Firestore (call on first login) ─
    // Stores referralCodes/{8-char-code} → {uid} so others can look up the full UID.

    suspend fun registerReferralCode() {
        val myUid = uid ?: return
        val code = myUid.take(8).uppercase()
        try {
            db.collection("referralCodes").document(code)
                .set(mapOf("uid" to myUid), SetOptions.merge()).await()
            Log.d(TAG, "Referral code registered: $code → $myUid")
        } catch (e: Exception) {
            Log.e(TAG, "registerReferralCode failed: ${e.message}")
        }
    }

    // ── Resolve a short code to a full UID via Firestore lookup ──────────────

    suspend fun resolveCode(shortCode: String): String? {
        if (shortCode.length != 8) return null
        return try {
            val doc = db.collection("referralCodes").document(shortCode.uppercase()).get().await()
            doc.getString("uid")
        } catch (e: Exception) {
            Log.e(TAG, "resolveCode failed: ${e.message}")
            null
        }
    }

    // ── Link a referral: called when user enters a referral code ─────────────

    suspend fun linkReferral(referrerUid: String): Boolean {
        val myUid = uid ?: return false
        if (referrerUid.isBlank() || referrerUid == myUid) return false

        return try {
            val today = LocalDate.now().toString()
            val data = mapOf(
                "referrerId"        to referrerUid,
                "createdAt"         to Timestamp.now(),
                "startDate"         to today,
                "lastVisitDate"     to today,
                "consecutiveDays"   to 1,
                "rewardPaid"        to false
            )
            db.collection(COLLECTION).document(myUid)
                .set(data, SetOptions.merge()).await()
            Log.d(TAG, "Referral linked: referrer=$referrerUid, referred=$myUid")
            true
        } catch (e: Exception) {
            Log.e(TAG, "linkReferral failed: ${e.message}")
            false
        }
    }

    // ── Check if referral already linked for this user ────────────────────────

    suspend fun isAlreadyReferred(): Boolean {
        val myUid = uid ?: return false
        return try {
            val doc = db.collection(COLLECTION).document(myUid).get().await()
            doc.exists() && (doc.getString("referrerId")?.isNotBlank() == true)
        } catch (e: Exception) {
            Log.e(TAG, "isAlreadyReferred check failed: ${e.message}")
            false
        }
    }

    // ── Record today's visit for referred user ────────────────────────────────
    // Call once per day when app is opened. Returns true if updated.

    suspend fun recordDailyVisit(): Boolean {
        val myUid = uid ?: return false
        val today = LocalDate.now().toString()

        return try {
            val docRef = db.collection(COLLECTION).document(myUid)
            val doc = docRef.get().await()
            if (!doc.exists()) return false

            val referrerUid = doc.getString("referrerId") ?: return false
            if (referrerUid.isBlank()) return false

            val lastVisit = doc.getString("lastVisitDate") ?: ""
            val rewardPaid = doc.getBoolean("rewardPaid") ?: false

            if (lastVisit == today) return false // already recorded today

            val yesterday = LocalDate.now().minusDays(1).toString()
            val currentStreak = (doc.getLong("consecutiveDays") ?: 0L).toInt()
            val newStreak = if (lastVisit == yesterday) currentStreak + 1 else 1

            docRef.update(mapOf(
                "lastVisitDate"   to today,
                "consecutiveDays" to newStreak
            )).await()

            Log.d(TAG, "Daily visit recorded: streak=$newStreak")

            // Check if referrer should be rewarded
            if (newStreak >= REQUIRED_CONSECUTIVE_DAYS && !rewardPaid) {
                awardReferralCoinsToReferrer(referrerUid, myUid)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "recordDailyVisit failed: ${e.message}")
            false
        }
    }

    // ── Award 450 coins to the referrer ──────────────────────────────────────

    private suspend fun awardReferralCoinsToReferrer(referrerUid: String, referredUid: String) {
        try {
            val userDocRef    = db.collection("users").document(referrerUid)
            val referralDocRef = db.collection(COLLECTION).document(referredUid)

            // Use a Firestore transaction to prevent race conditions when
            // multiple devices could simultaneously trigger the coin award.
            db.runTransaction { transaction ->
                val userSnap    = transaction.get(userDocRef)
                val referralSnap = transaction.get(referralDocRef)

                // Guard: skip if reward was already paid inside the transaction
                if (referralSnap.getBoolean("rewardPaid") == true) return@runTransaction

                @Suppress("UNCHECKED_CAST")
                val rewards      = userSnap.get("rewards") as? Map<String, Any>
                val currentBal   = (rewards?.get("coinBalance")      as? Long)?.toInt() ?: 0
                val currentTotal = (rewards?.get("totalCoinsEarned") as? Long)?.toInt() ?: 0

                transaction.update(
                    userDocRef,
                    mapOf(
                        "rewards.coinBalance"      to currentBal + REFERRAL_REWARD_COINS,
                        "rewards.totalCoinsEarned" to currentTotal + REFERRAL_REWARD_COINS,
                        "rewards.updatedAt"        to System.currentTimeMillis()
                    )
                )
                transaction.update(referralDocRef, "rewardPaid", true)
            }.await()

            Log.d(TAG, "Referral reward paid (transaction): $REFERRAL_REWARD_COINS coins to $referrerUid")
        } catch (e: Exception) {
            Log.e(TAG, "awardReferralCoins failed: ${e.message}")
        }
    }

    // ── Load all referred users for the current user (referrer view) ──────────

    suspend fun loadMyReferrals(): List<ReferralEntry> {
        val myUid = uid ?: return emptyList()
        return try {
            val snapshot = db.collection(COLLECTION)
                .whereEqualTo("referrerId", myUid)
                .get().await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    ReferralEntry(
                        referredUid     = doc.id,
                        startDate       = doc.getString("startDate") ?: "",
                        lastVisitDate   = doc.getString("lastVisitDate") ?: "",
                        consecutiveDays = (doc.getLong("consecutiveDays") ?: 0L).toInt(),
                        rewardPaid      = doc.getBoolean("rewardPaid") ?: false
                    )
                } catch (_: Exception) { null }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadMyReferrals failed: ${e.message}")
            emptyList()
        }
    }

    // ── Check and collect any pending referral rewards for current user ────────
    // double coin award when called simultaneously from two devices. Previously
    // coins were read and written outside a transaction — a race condition that
    // could grant the same reward twice.
    // Returns coins collected (0 if none).

    suspend fun collectPendingReferralRewards(): Int {
        val myUid = uid ?: return 0
        var totalCollected = 0

        try {
            val snapshot = db.collection(COLLECTION)
                .whereEqualTo("referrerId", myUid)
                .whereEqualTo("rewardPaid", false)
                .get().await()

            val eligibleDocs = snapshot.documents.filter { doc ->
                val streak = (doc.getLong("consecutiveDays") ?: 0L).toInt()
                streak >= REQUIRED_CONSECUTIVE_DAYS
            }

            if (eligibleDocs.isEmpty()) return 0

            val userDocRef = db.collection("users").document(myUid)

            // transaction so the rewardPaid guard is checked atomically with the
            // coin update — no double payment possible even with concurrent calls.
            for (doc in eligibleDocs) {
                val referralDocRef = db.collection(COLLECTION).document(doc.id)
                var coinsAwarded = 0

                try {
                    db.runTransaction { transaction ->
                        val referralSnap = transaction.get(referralDocRef)
                        // Guard: skip if already paid inside this transaction
                        if (referralSnap.getBoolean("rewardPaid") == true) return@runTransaction

                        val userSnap = transaction.get(userDocRef)
                        @Suppress("UNCHECKED_CAST")
                        val rewards = userSnap.get("rewards") as? Map<String, Any>
                        val currentBalance = (rewards?.get("coinBalance") as? Long)?.toInt() ?: 0
                        val currentTotal   = (rewards?.get("totalCoinsEarned") as? Long)?.toInt() ?: 0

                        transaction.update(
                            userDocRef,
                            mapOf(
                                "axCoins"                  to FieldValue.increment(REFERRAL_REWARD_COINS.toLong()),
                                "rewards.coinBalance"      to currentBalance + REFERRAL_REWARD_COINS,
                                "rewards.totalCoinsEarned" to currentTotal   + REFERRAL_REWARD_COINS,
                                "rewards.updatedAt"        to System.currentTimeMillis()
                            )
                        )
                        transaction.update(referralDocRef, "rewardPaid", true)
                        coinsAwarded = REFERRAL_REWARD_COINS
                    }.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Transaction failed for referral ${doc.id}: ${e.message}")
                }

                totalCollected += coinsAwarded
            }

            if (totalCollected > 0) {
                Log.d(TAG, "Collected pending referral rewards: $totalCollected coins")
            }
        } catch (e: Exception) {
            Log.e(TAG, "collectPendingReferralRewards failed: ${e.message}")
        }

        return totalCollected
    }
}

data class ReferralEntry(
    val referredUid     : String,
    val startDate       : String,
    val lastVisitDate   : String,
    val consecutiveDays : Int,
    val rewardPaid      : Boolean
)
