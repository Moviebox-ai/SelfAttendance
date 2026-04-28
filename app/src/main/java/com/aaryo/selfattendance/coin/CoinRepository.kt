package com.aaryo.selfattendance.coin

import android.util.Log
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * Handles all coin read/write operations against Firestore.
 *
 * Security model (defence-in-depth — three independent layers):
 *  1. Client local cache (PreferencesManager) — fast, no network
 *  2. Client Firestore check (canEarnCoins) — authoritative, network
 *  3. Server Firestore Security Rules — last line of defence
 *
 * No direct coin manipulation allowed from client; only addTransaction() is exposed.
 */
class CoinRepository(
    private val preferencesManager: PreferencesManager? = null,
    initialDailyLimit: Int = 50
) {

    companion object {
        private const val TAG = "CoinRepository"
        const val MAX_AD_COINS_PER_DAY    = 50
        const val AD_REWARD_AMOUNT        = 10
        const val DAILY_LOGIN_AMOUNT      = 5
        private const val USERS_COLLECTION        = "users"
        private const val TRANSACTIONS_COLLECTION = "coinTransactions"
        private const val WALLET_DOCUMENT         = "wallet"
    }

    // ── Admin-controlled live settings ──────────────────────────────
    @Volatile var dailyLimit       : Int     = initialDailyLimit ; private set
    @Volatile var adRewardAmount   : Int     = AD_REWARD_AMOUNT  ; private set
    @Volatile var dailyLoginBonus  : Int     = DAILY_LOGIN_AMOUNT; private set
    @Volatile var isWalletEnabled  : Boolean = true              ; private set
    @Volatile var isAdRewardEnabled: Boolean = true              ; private set

    /** Fetches global wallet config from Firestore. Call once at app start. */
    suspend fun fetchAdminSettings() {
        try {
            val doc = db.collection("adminSettings").document("walletConfig").get().await()
            if (doc.exists()) {
                doc.getLong("dailyCoinLimit")?.let       { dailyLimit        = it.toInt() }
                doc.getLong("adRewardAmount")?.let       { adRewardAmount    = it.toInt() }
                doc.getLong("dailyLoginBonus")?.let      { dailyLoginBonus   = it.toInt() }
                doc.getBoolean("isWalletEnabled")?.let   { isWalletEnabled   = it }
                doc.getBoolean("isAdRewardEnabled")?.let { isAdRewardEnabled = it }

                @Suppress("UNCHECKED_CAST")
                val prices = doc.get("featurePrices") as? Map<String, Long> ?: emptyMap()
                PremiumFeature.values().forEach { feature ->
                    prices[feature.firestoreKey]?.let { feature.coinCost = it.toInt() }
                }

                @Suppress("UNCHECKED_CAST")
                val locked = doc.get("lockedFeatures") as? Map<String, Boolean> ?: emptyMap()
                _adminLockedFeatures = locked

                Log.d(TAG, "AdminSettings: limit=$dailyLimit reward=$adRewardAmount wallet=$isWalletEnabled prices=$prices locked=$locked")
            }
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "fetchAdminSettings [${e.code}] — using defaults")
        } catch (e: Exception) {
            Log.e(TAG, "fetchAdminSettings failed", e)
        }
    }

    @Volatile private var _adminLockedFeatures: Map<String, Boolean> = emptyMap()

    fun isAdminLocked(feature: PremiumFeature): Boolean =
        _adminLockedFeatures[feature.firestoreKey] == true

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val uid: String?
        get() = auth.currentUser?.uid

    // ── Public API ──────────────────────────────────────────────────

    /** Returns current coin balance. Returns 0 if not signed in or on error. */
    suspend fun getBalance(): Int {
        val userId = uid ?: return 0
        return try {
            val doc = walletRef(userId).get().await()
            doc.getLong("balance")?.toInt() ?: 0
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "getBalance denied [${e.code}] — returning 0")
            0
        } catch (e: Exception) {
            Log.e(TAG, "getBalance failed", e)
            0
        }
    }

    /**
     * Returns how many ad-reward coins were earned today from Firestore
     * (source of truth). Also syncs the local SharedPrefs cache.
     */
    suspend fun getAdCoinsEarnedToday(): Int {
        val userId = uid ?: return dailyLimit
        return try {
            val startOfDay = startOfDayTimestamp()
            val snap = transactionsRef(userId)
                .whereEqualTo("type", TransactionType.AD_REWARD.name)
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .get()
                .await()
            val total = snap.documents.sumOf { it.getLong("amount")?.toInt() ?: 0 }
            // Keep local cache in sync with Firestore truth
            preferencesManager?.setLocalDailyCoins(total)
            total
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "getAdCoinsEarnedToday [${e.code}] — falling back to local cache")
            preferencesManager?.getLocalDailyCoinsEarned() ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "getAdCoinsEarnedToday failed", e)
            preferencesManager?.getLocalDailyCoinsEarned() ?: 0
        }
    }

    /**
     * REPOSITORY-LEVEL GATE — authoritative check (requires network).
     *
     * Returns true only when ALL of the following hold:
     *  1. User is authenticated
     *  2. Wallet is enabled by admin
     *  3. Ad rewards are enabled by admin
     *  4. Ad cooldown has elapsed
     *  5. Coins earned today + next reward <= daily limit
     */
    suspend fun canEarnCoins(): Boolean {
        if (uid == null) return false
        if (!isWalletEnabled) return false
        if (!isAdRewardEnabled) return false
        if (preferencesManager?.canWatchAd() == false) return false
        val earnedToday = getAdCoinsEarnedToday()
        return earnedToday + adRewardAmount <= dailyLimit
    }

    /**
     * Fast synchronous check using only local cache — safe to call on UI thread.
     * Use to drive button enabled/disabled state without a Firestore round-trip.
     */
    fun canEarnCoinsLocal(): Boolean {
        if (uid == null) return false
        if (!isWalletEnabled) return false
        if (!isAdRewardEnabled) return false
        if (preferencesManager?.canWatchAd() == false) return false
        val localEarned = preferencesManager?.getLocalDailyCoinsEarned() ?: 0
        return localEarned + adRewardAmount <= dailyLimit
    }

    /** Seconds until next ad is allowed (0 = ready now). */
    fun adCooldownRemainingSeconds(): Long =
        preferencesManager?.adCooldownRemainingSeconds() ?: 0L

    /**
     * Adds an ad reward transaction.
     *
     * Validates via TWO client-side layers before writing:
     *  Layer 1 — local cache check (fast, avoids Firestore if obviously blocked)
     *  Layer 2 — authoritative Firestore check
     *
     * Records cooldown + updates local cache on confirmed success.
     */
    suspend fun addAdReward(): Result<Int> {
        val userId = uid ?: return Result.failure(Exception("User not signed in"))

        // ── Layer 1: fast local check ─────────────────────────────────
        if (preferencesManager?.canWatchAd() == false) {
            val remaining = preferencesManager.adCooldownRemainingSeconds()
            return Result.failure(Exception("Please wait ${remaining}s before watching another ad."))
        }
        val localEarned = preferencesManager?.getLocalDailyCoinsEarned() ?: 0
        if (localEarned + adRewardAmount > dailyLimit) {
            return Result.failure(Exception("Daily limit reached. Come back tomorrow!"))
        }

        // ── Layer 2: authoritative Firestore check ────────────────────
        val todayEarned = getAdCoinsEarnedToday()
        if (todayEarned + adRewardAmount > dailyLimit) {
            return Result.failure(Exception("Daily limit reached. Come back tomorrow!"))
        }

        // ── Execute ───────────────────────────────────────────────────
        val result = addTransaction(
            userId      = userId,
            type        = TransactionType.AD_REWARD,
            amount      = adRewardAmount,
            description = "Watched rewarded ad"
        )

        if (result.isSuccess) {
            // Record cooldown + update local cache only on confirmed success
            preferencesManager?.recordAdWatched()
            preferencesManager?.addLocalDailyCoins(adRewardAmount)
        }

        return result
    }

    /**
     * Adds a daily login bonus. Checks if already claimed today.
     * Returns null if already claimed, Result<Int> otherwise.
     */
    suspend fun claimDailyLogin(): Result<Int>? {
        val userId = uid ?: return Result.failure(Exception("User not signed in"))
        if (hasDailyLoginToday(userId)) return null
        return addTransaction(
            userId      = userId,
            type        = TransactionType.DAILY_LOGIN,
            amount      = dailyLoginBonus,
            description = "Daily login bonus"
        )
    }

    /** Adds a streak bonus transaction. */
    suspend fun addStreakBonus(amount: Int, streakDays: Int): Result<Int> {
        val userId = uid ?: return Result.failure(Exception("User not signed in"))
        return addTransaction(
            userId      = userId,
            type        = TransactionType.STREAK_BONUS,
            amount      = amount,
            description = "🔥 ${streakDays}-day streak bonus!"
        )
    }

    /** Spend coins to unlock a premium feature. */
    suspend fun spendCoinsForFeature(feature: PremiumFeature): Result<Int> {
        val userId  = uid ?: return Result.failure(Exception("User not signed in"))
        val balance = getBalance()
        if (balance < feature.coinCost) {
            return Result.failure(
                Exception("Not enough coins. Need ${feature.coinCost}, have $balance.")
            )
        }
        try {
            walletRef(userId).update("unlockedFeatures.${feature.firestoreKey}", true).await()
        } catch (e: Exception) {
            Log.e(TAG, "Feature unlock update failed", e)
        }
        return addTransaction(
            userId      = userId,
            type        = TransactionType.FEATURE_UNLOCK,
            amount      = -feature.coinCost,
            description = "Unlocked: ${feature.displayName}"
        )
    }

    /** Returns true if a specific premium feature is unlocked. */
    suspend fun isFeatureUnlocked(feature: PremiumFeature): Boolean {
        val userId = uid ?: return false
        return try {
            val doc = walletRef(userId).get().await()
            @Suppress("UNCHECKED_CAST")
            val features = doc.get("unlockedFeatures") as? Map<String, Boolean> ?: emptyMap()
            features[feature.firestoreKey] == true
        } catch (e: Exception) {
            Log.e(TAG, "isFeatureUnlocked failed", e)
            false
        }
    }

    /** Returns map of all unlocked features. */
    suspend fun getUnlockedFeatures(): Map<String, Boolean> {
        val userId = uid ?: return emptyMap()
        return try {
            val doc = walletRef(userId).get().await()
            @Suppress("UNCHECKED_CAST")
            doc.get("unlockedFeatures") as? Map<String, Boolean> ?: emptyMap()
        } catch (e: Exception) { emptyMap() }
    }

    /** Returns last N coin transactions (for history display). */
    suspend fun getRecentTransactions(limit: Long = 20): List<CoinTransaction> {
        val userId = uid ?: return emptyList()
        return try {
            val snap = transactionsRef(userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get()
                .await()
            snap.documents.mapNotNull { doc ->
                try { doc.toObject(CoinTransaction::class.java)?.copy(id = doc.id) }
                catch (e: Exception) { null }
            }
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "getRecentTransactions [${e.code}] — returning empty")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getRecentTransactions failed", e)
            emptyList()
        }
    }

    /** Returns (currentStreak, longestStreak) from wallet doc. */
    suspend fun getStreakInfo(): Pair<Int, Int> {
        val userId = uid ?: return Pair(0, 0)
        return try {
            val doc = walletRef(userId).get().await()
            val current = doc.getLong("currentStreak")?.toInt() ?: 0
            val longest = doc.getLong("longestStreak")?.toInt() ?: 0
            Pair(current, longest)
        } catch (e: Exception) { Pair(0, 0) }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private suspend fun addTransaction(
        userId     : String,
        type       : TransactionType,
        amount     : Int,
        description: String
    ): Result<Int> {
        return try {
            val txData = hashMapOf(
                "type"        to type.name,
                "amount"      to amount,
                "timestamp"   to Timestamp.now(),
                "description" to description
            )

            val newBalance = db.runTransaction { tx ->
                val walletDoc = walletRef(userId)
                val snap      = tx.get(walletDoc)
                val current   = snap.getLong("balance")?.toInt() ?: 0
                val newBal    = maxOf(0, current + amount)

                // BUG FIX: Previously used set() with manually copied fields.
                // If "unlockedFeatures" didn't exist in the doc (new wallet), it
                // was written as emptyMap() which silently wiped any existing
                // features if the doc was partially written before.
                // Now using update() for existing docs and set() only for new ones,
                // with merge() to ensure no fields are ever deleted.
                val updateData = mapOf(
                    "balance"       to newBal,
                    "lastUpdated"   to FieldValue.serverTimestamp(),
                    "currentStreak" to (snap.getLong("currentStreak") ?: 0L),
                    "longestStreak" to (snap.getLong("longestStreak") ?: 0L)
                )
                tx.set(
                    walletDoc,
                    updateData,
                    com.google.firebase.firestore.SetOptions.merge()
                )
                newBal
            }.await()

            transactionsRef(userId).add(txData).await()
            Log.d(TAG, "Transaction added: $type $amount → balance=$newBalance")
            Result.success(newBalance)

        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "addTransaction Firestore error [${e.code}]", e)
            val msg = when (e.code) {
                Code.PERMISSION_DENIED -> "Transaction blocked by server. Check Firestore Security Rules."
                Code.UNAUTHENTICATED   -> "Please sign in to earn coins."
                Code.UNAVAILABLE       -> "Server unavailable. Please try again."
                else                   -> "Something went wrong (${e.code})."
            }
            Result.failure(Exception(msg))
        } catch (e: Exception) {
            Log.e(TAG, "addTransaction failed", e)
            Result.failure(e)
        }
    }

    private suspend fun hasDailyLoginToday(userId: String): Boolean {
        return try {
            val snap = transactionsRef(userId)
                .whereEqualTo("type", TransactionType.DAILY_LOGIN.name)
                .whereGreaterThanOrEqualTo("timestamp", startOfDayTimestamp())
                .limit(1)
                .get()
                .await()
            snap.isEmpty.not()
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "hasDailyLoginToday [${e.code}] — assuming not claimed")
            false
        } catch (e: Exception) { false }
    }

    private fun startOfDayTimestamp(): Timestamp {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return Timestamp(cal.time)
    }

    private fun walletRef(userId: String) =
        db.collection(USERS_COLLECTION).document(userId)
            .collection("wallet").document(WALLET_DOCUMENT)

    private fun transactionsRef(userId: String) =
        db.collection(USERS_COLLECTION).document(userId)
            .collection(TRANSACTIONS_COLLECTION)
}
