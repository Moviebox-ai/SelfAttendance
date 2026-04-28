package com.aaryo.selfattendance.admin

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════
//  AdminRepository
//
//  All Firestore admin operations.
//  Mirrors the same path structure as CoinRepository:
//    users/{uid}/wallet/wallet
//    users/{uid}/coinTransactions/{txId}
// ═══════════════════════════════════════════════════════════════

data class AdminUserSummary(
    val uid        : String = "",
    val email      : String = "",
    val name       : String = "",
    val balance    : Int    = 0,
    val totalEarned: Int    = 0
)

data class AdminTransaction(
    val id         : String    = "",
    val type       : String    = "",
    val amount     : Int       = 0,
    val description: String    = "",
    val timestamp  : Timestamp = Timestamp.now()
)

data class WalletSettings(
    val dailyCoinLimit    : Int                     = 50,
    val adRewardAmount    : Int                     = 10,
    val dailyLoginBonus   : Int                     = 5,
    val isWalletEnabled   : Boolean                 = true,
    val isAdRewardEnabled : Boolean                 = true,
    val maxBalance        : Int                     = 1_000_000,
    // Per-feature coin prices (admin can change what each feature costs)
    val featurePrices     : Map<String, Int>        = emptyMap(),
    // Features admin has force-locked (even if user paid, they can't use it)
    val lockedFeatures    : Map<String, Boolean>    = emptyMap()
)

class AdminRepository {

    companion object {
        private const val TAG        = "AdminRepository"
        private const val USERS      = "users"
        private const val WALLET_COL = "wallet"
        private const val WALLET_DOC = "wallet"
        private const val TX_COL     = "coinTransactions"
    }

    private val db = FirebaseFirestore.getInstance()

    // ── Get all users (reads /users collection) ───────────────────

    suspend fun getAllUsers(): Result<List<AdminUserSummary>> = runCatching {
        val snap = db.collection(USERS).get().await()

        // BUG FIX: Previously fetched wallet for each user SEQUENTIALLY inside
        // mapNotNull{} with .await() — this is an N+1 Firestore problem.
        // With 100 users that's 101 sequential network calls.
        // Fixed: use coroutineScope + async/awaitAll to run all wallet fetches
        // in parallel, reducing total time from O(N) to O(1) network round-trips.
        coroutineScope {
            snap.documents.map { doc ->
                async {
                    try {
                        val walletSnap = db.collection(USERS).document(doc.id)
                            .collection(WALLET_COL).document(WALLET_DOC).get().await()
                        AdminUserSummary(
                            uid     = doc.id,
                            email   = doc.getString("email") ?: "",
                            name    = doc.getString("name")  ?: doc.id.take(8),
                            balance = walletSnap.getLong("balance")?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        // Return user without balance if wallet fetch fails
                        AdminUserSummary(
                            uid   = doc.id,
                            email = doc.getString("email") ?: "",
                            name  = doc.getString("name")  ?: doc.id.take(8)
                        )
                    }
                }
            }.awaitAll()
        }
    }

    // ── Get single user wallet ────────────────────────────────────

    suspend fun getWallet(userId: String): Result<AdminUserSummary> = runCatching {
        val walletDoc = db.collection(USERS).document(userId)
            .collection(WALLET_COL).document(WALLET_DOC).get().await()
        val profileDoc = db.collection(USERS).document(userId).get().await()

        AdminUserSummary(
            uid     = userId,
            email   = profileDoc.getString("email") ?: "",
            name    = profileDoc.getString("name")  ?: userId.take(8),
            balance = walletDoc.getLong("balance")?.toInt() ?: 0
        )
    }

    // ── Add coins ─────────────────────────────────────────────────

    suspend fun addCoins(
        userId : String,
        amount : Int,
        reason : String
    ): Result<Int> {
        if (amount <= 0) return Result.failure(Exception("Amount must be positive"))
        return modifyBalance(
            userId      = userId,
            delta       = amount,
            type        = "ADMIN_ADD",
            description = "Admin added $amount coins — $reason"
        ).also {
            if (it.isSuccess) {
                AdminAuditLogger.log(AdminAction.ADD_COINS, userId, "+$amount — $reason")
            }
        }
    }

    // ── Deduct coins ──────────────────────────────────────────────

    suspend fun deductCoins(
        userId : String,
        amount : Int,
        reason : String
    ): Result<Int> {
        if (amount <= 0) return Result.failure(Exception("Amount must be positive"))
        return modifyBalance(
            userId      = userId,
            delta       = -amount,
            type        = "ADMIN_DEDUCT",
            description = "Admin deducted $amount coins — $reason"
        ).also {
            if (it.isSuccess) {
                AdminAuditLogger.log(AdminAction.DEDUCT_COINS, userId, "-$amount — $reason")
            }
        }
    }

    // ── Reset wallet ──────────────────────────────────────────────

    suspend fun resetWallet(userId: String, reason: String): Result<Unit> = runCatching {
        val walletRef = db.collection(USERS).document(userId)
            .collection(WALLET_COL).document(WALLET_DOC)

        // BUG FIX: Previously set() with merge() included "unlockedFeatures: emptyMap()"
        // which wipes all features the user has purchased with their coins.
        // Admin "reset wallet" should only reset the balance and streaks,
        // NOT erase purchased feature unlocks (which represent real user value).
        // Use merge() without overwriting unlockedFeatures.
        db.runTransaction { tx ->
            tx.set(walletRef, mapOf(
                "balance"       to 0,
                "currentStreak" to 0,
                "longestStreak" to 0,
                "lastUpdated"   to FieldValue.serverTimestamp()
            ), com.google.firebase.firestore.SetOptions.merge())
        }.await()

        // Log reset transaction
        val txData = hashMapOf(
            "type"        to "ADMIN_RESET",
            "amount"      to 0,
            "timestamp"   to Timestamp.now(),
            "description" to "Admin reset wallet — $reason"
        )
        db.collection(USERS).document(userId).collection(TX_COL).add(txData).await()
        AdminAuditLogger.log(AdminAction.RESET_WALLET, userId, reason)
    }

    // ── Get transaction history for a user ────────────────────────

    suspend fun getTransactions(
        userId: String,
        limit : Long = 30
    ): Result<List<AdminTransaction>> = runCatching {
        val snap = db.collection(USERS).document(userId)
            .collection(TX_COL)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .get().await()

        snap.documents.mapNotNull { doc ->
            try {
                AdminTransaction(
                    id          = doc.id,
                    type        = doc.getString("type")        ?: "",
                    amount      = doc.getLong("amount")?.toInt() ?: 0,
                    description = doc.getString("description") ?: "",
                    timestamp   = doc.getTimestamp("timestamp") ?: Timestamp.now()
                )
            } catch (e: Exception) { null }
        }.also {
            AdminAuditLogger.log(AdminAction.VIEW_TRANSACTIONS, userId)
        }
    }

    // ── Global stats ──────────────────────────────────────────────

    suspend fun getGlobalStats(): Result<Pair<Int, Int>> = runCatching {
        // Returns (totalUsers, totalCoinsDistributed)
        // FIX: No longer does N+1 reads — reads users collection count only.
        // Total coins is now maintained via a denormalized counter in
        // adminStats/global (updated by Cloud Functions on wallet writes).
        // Fallback: if adminStats doc doesn't exist, show user count + 0 coins.
        val users = db.collection(USERS).get().await()
        val totalUsers = users.size()

        val statsDoc = try {
            db.collection("adminStats").document("global").get().await()
        } catch (_: Exception) { null }

        val totalCoins = statsDoc?.getLong("totalCoinsDistributed")?.toInt() ?: 0
        Pair(totalUsers, totalCoins)
    }

    // ── Private helpers ───────────────────────────────────────────

    private suspend fun modifyBalance(
        userId     : String,
        delta      : Int,
        type       : String,
        description: String
    ): Result<Int> = runCatching {
        val walletRef = db.collection(USERS).document(userId)
            .collection(WALLET_COL).document(WALLET_DOC)

        val newBalance = db.runTransaction { tx ->
            val snap       = tx.get(walletRef)
            val current    = snap.getLong("balance")?.toInt() ?: 0
            val newBal     = maxOf(0, current + delta)
            tx.set(walletRef, mapOf(
                "balance"     to newBal,
                "lastUpdated" to FieldValue.serverTimestamp()
            ), com.google.firebase.firestore.SetOptions.merge())
            newBal
        }.await()

        // Write audit transaction record
        val txData = hashMapOf(
            "type"        to type,
            "amount"      to delta,
            "timestamp"   to Timestamp.now(),
            "description" to description
        )
        db.collection(USERS).document(userId).collection(TX_COL).add(txData).await()

        newBalance
    }

suspend fun getWalletSettings(): Result<WalletSettings> = runCatching {
        val doc = db.collection("adminSettings").document("walletConfig").get().await()
        if (!doc.exists()) return@runCatching WalletSettings()

        @Suppress("UNCHECKED_CAST")
        val prices  = doc.get("featurePrices")  as? Map<String, Long>    ?: emptyMap()
        @Suppress("UNCHECKED_CAST")
        val locked  = doc.get("lockedFeatures") as? Map<String, Boolean> ?: emptyMap()

        WalletSettings(
            dailyCoinLimit    = doc.getLong("dailyCoinLimit")?.toInt()    ?: 50,
            adRewardAmount    = doc.getLong("adRewardAmount")?.toInt()    ?: 10,
            dailyLoginBonus   = doc.getLong("dailyLoginBonus")?.toInt()   ?: 5,
            isWalletEnabled   = doc.getBoolean("isWalletEnabled")         ?: true,
            isAdRewardEnabled = doc.getBoolean("isAdRewardEnabled")       ?: true,
            maxBalance        = doc.getLong("maxBalance")?.toInt()        ?: 1_000_000,
            featurePrices     = prices.mapValues { it.value.toInt() },
            lockedFeatures    = locked
        )
    }

    suspend fun saveWalletSettings(
        settings: WalletSettings,
        adminUid: String
    ): Result<Unit> = runCatching {
        val data = mapOf(
            "dailyCoinLimit"    to settings.dailyCoinLimit,
            "adRewardAmount"    to settings.adRewardAmount,
            "dailyLoginBonus"   to settings.dailyLoginBonus,
            "isWalletEnabled"   to settings.isWalletEnabled,
            "isAdRewardEnabled" to settings.isAdRewardEnabled,
            "maxBalance"        to settings.maxBalance,
            "featurePrices"     to settings.featurePrices,
            "lockedFeatures"    to settings.lockedFeatures,
            "updatedAt"         to com.google.firebase.Timestamp.now(),
            "updatedBy"         to adminUid
        )
        db.collection("adminSettings").document("walletConfig")
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()

        AdminAuditLogger.log(
            AdminAction.RESET_WALLET,
            details = "Wallet settings updated: limit=${settings.dailyCoinLimit}, " +
                      "reward=${settings.adRewardAmount}, enabled=${settings.isWalletEnabled}, " +
                      "lockedFeatures=${settings.lockedFeatures}"
        )
    }

}