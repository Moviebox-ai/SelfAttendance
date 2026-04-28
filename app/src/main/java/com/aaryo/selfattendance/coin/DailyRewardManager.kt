package com.aaryo.selfattendance.coin

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Manages daily login bonus and streak system.
 *
 * Streak rules:
 *  • Login on consecutive days → streak increases.
 *  • Miss a day → streak resets to 1.
 *  • Streak milestones grant bonus coins.
 */
class DailyRewardManager(
    private val coinRepository: CoinRepository
) {

    companion object {
        private const val TAG = "DailyRewardManager"
        private const val USERS_COLLECTION = "users"
        private const val WALLET_DOCUMENT = "wallet"

        // Streak milestone → bonus coins
        val STREAK_MILESTONES = mapOf(
            3 to 15,
            7 to 30,
            14 to 50,
            30 to 100
        )
    }

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    data class DailyRewardResult(
        val coinsEarned: Int,
        val newBalance: Int,
        val currentStreak: Int,
        val isStreakBonus: Boolean = false,
        val streakBonusCoins: Int = 0,
        // NOTE: processDailyLogin() now returns null when already claimed.
        // This field is kept for internal use only (always false when non-null result).
        val alreadyClaimed: Boolean = false
    )

    /**
     * Call this on every app launch / wallet screen open.
     * Returns DailyRewardResult if a reward was granted, or null if already claimed today.
     *
     * BUG FIX: Return type changed to nullable (DailyRewardResult?) so callers
     * can distinguish "already claimed" (null) from "reward granted" (non-null).
     * Previously returned alreadyClaimed=true inside the object, but WalletViewModel
     * used result?.let{} which expected null for the "no reward" case.
     */
    suspend fun processDailyLogin(): DailyRewardResult? {
        val userId = auth.currentUser?.uid ?: return null

        val streakData = getAndUpdateStreak(userId)

        // Try to claim daily login bonus
        val loginResult = coinRepository.claimDailyLogin()
        if (loginResult == null) {
            // Already claimed today — return null so callers skip dialog
            return null
        }

        // Use admin-controlled bonus amount, not hardcoded constant
        var totalCoins = coinRepository.dailyLoginBonus
        var newBalance = loginResult.getOrDefault(0)
        var streakBonusCoins = 0
        var isStreakBonus = false

        // Check streak milestone bonus
        val streakBonusAmount = STREAK_MILESTONES[streakData.newStreak]
        if (streakBonusAmount != null) {
            val bonusResult = coinRepository.addStreakBonus(streakBonusAmount, streakData.newStreak)
            bonusResult.onSuccess { bal ->
                newBalance = bal
                totalCoins += streakBonusAmount
                streakBonusCoins = streakBonusAmount
                isStreakBonus = true
            }
        }

        return DailyRewardResult(
            coinsEarned = totalCoins,
            newBalance = newBalance,
            currentStreak = streakData.newStreak,
            isStreakBonus = isStreakBonus,
            streakBonusCoins = streakBonusCoins,
            alreadyClaimed = false
        )
    }

    // -------- Streak calculation --------

    private data class StreakUpdate(val newStreak: Int, val longestStreak: Int)

    private suspend fun getAndUpdateStreak(userId: String): StreakUpdate {
        val walletRef = db.collection(USERS_COLLECTION)
            .document(userId)
            .collection("wallet")
            .document(WALLET_DOCUMENT)

        return try {
            val newStreakData = db.runTransaction { tx ->
                val snap = tx.get(walletRef)
                val lastLoginTs = snap.getTimestamp("lastLoginDate")
                val currentStreak = snap.getLong("currentStreak")?.toInt() ?: 0
                val longestStreak = snap.getLong("longestStreak")?.toInt() ?: 0

                val now = Calendar.getInstance()
                val newStreak: Int

                if (lastLoginTs == null) {
                    // First ever login
                    newStreak = 1
                } else {
                    val lastLoginCal = Calendar.getInstance().apply { time = lastLoginTs.toDate() }
                    val daysBetween = daysBetween(lastLoginCal, now)

                    newStreak = when {
                        daysBetween == 0 -> currentStreak   // Same day, no change
                        daysBetween == 1 -> currentStreak + 1  // Consecutive day
                        else -> 1  // Missed days — reset
                    }
                }

                val newLongest = maxOf(longestStreak, newStreak)

                tx.set(walletRef, mapOf(
                    "lastLoginDate" to Timestamp.now(),
                    "currentStreak" to newStreak,
                    "longestStreak" to newLongest
                ), SetOptions.merge())

                Pair(newStreak, newLongest)
            }.await()

            StreakUpdate(newStreakData.first, newStreakData.second)
        } catch (e: Exception) {
            Log.e(TAG, "Streak update failed", e)
            StreakUpdate(1, 1)
        }
    }

    private fun daysBetween(from: Calendar, to: Calendar): Int {
        val fromDay = from.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val toDay = to.apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return TimeUnit.MILLISECONDS.toDays(toDay - fromDay).toInt()
    }

    /**
     * Returns the next streak milestone coins (for progress display in UI).
     */
    fun getNextMilestone(currentStreak: Int): Pair<Int, Int>? { // Pair<targetDays, bonusCoins>
        return STREAK_MILESTONES.entries
            .filter { it.key > currentStreak }
            .minByOrNull { it.key }
            ?.let { Pair(it.key, it.value) }
    }
}
