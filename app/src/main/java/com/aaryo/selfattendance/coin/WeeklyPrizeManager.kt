package com.aaryo.selfattendance.coin

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * WeeklyPrizeManager — handles the weekly Big Prize leaderboard & reward tiers.
 *
 * How it works:
 *  • Every Sunday 00:00 UTC the weekly cycle resets.
 *  • Users earn "weekly coins" by collecting regular coins during the week.
 *  • At week-end the top earner wins the Grand Prize (special badge + bonus coins).
 *  • Prize tiers: 🥇 Grand (800+ coins), 🥈 Elite (500+), 🥉 Champion (300+),
 *    ⭐ Achiever (100+), 🎯 Participant (<100).
 *  • All data stored in Firestore collection "weeklyPrize/{weekId}/entries/{uid}".
 */
class WeeklyPrizeManager(
    private val coinRepository: CoinRepository? = null
) {
    companion object {
        private const val TAG = "WeeklyPrizeManager"
        private const val COLLECTION = "weeklyPrize"

        // Prize tiers (min weekly coins needed)
        val PRIZE_TIERS = listOf(
            PrizeTier("🏆 Grand Champion", 800, 500, "Grand Prize Winner! Ultimate collector!"),
            PrizeTier("💎 Elite Earner",   500, 300, "Elite tier! You're a top performer!"),
            PrizeTier("🥉 Champion",        300, 150, "Champion! Great dedication this week!"),
            PrizeTier("⭐ Achiever",        100,  50, "Achiever! Keep collecting!"),
            PrizeTier("🎯 Participant",       0,   0, "Participate to climb the ranks!")
        )
    }

    private val db   = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ── Week ID ───────────────────────────────────────────────────────────────

    /** Returns "2025-W23" style key for the current week. */
    fun currentWeekId(): String {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    /** Returns the next Sunday reset time as a Timestamp. */
    fun nextResetTime(): Long {
        val cal = Calendar.getInstance()
        val daysUntilSunday = (Calendar.SUNDAY + 7 - cal.get(Calendar.DAY_OF_WEEK)) % 7
        val adjustedDays = if (daysUntilSunday == 0) 7 else daysUntilSunday
        cal.add(Calendar.DAY_OF_YEAR, adjustedDays)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Days remaining in the current week cycle. */
    fun daysRemainingInWeek(): Int {
        val cal = Calendar.getInstance()
        val daysUntilSunday = (Calendar.SUNDAY + 7 - cal.get(Calendar.DAY_OF_WEEK)) % 7
        return if (daysUntilSunday == 0) 7 else daysUntilSunday
    }

    // ── Leaderboard ──────────────────────────────────────────────────────────

    /** Fetch top N entries for the current week. */
    suspend fun getLeaderboard(limit: Int = 10): List<LeaderboardEntry> {
        return try {
            val weekId = currentWeekId()
            val snap = db.collection(COLLECTION)
                .document(weekId)
                .collection("entries")
                .orderBy("weeklyCoins", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()

            snap.documents.mapIndexedNotNull { index, doc ->
                val coins    = doc.getLong("weeklyCoins")?.toInt() ?: 0
                val name     = doc.getString("displayName") ?: "Anonymous"
                val uid      = doc.id
                LeaderboardEntry(
                    rank         = index + 1,
                    uid          = uid,
                    displayName  = name,
                    weeklyCoins  = coins,
                    tier         = tierFor(coins),
                    isCurrentUser = uid == auth.currentUser?.uid
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Leaderboard fetch failed", e)
            emptyList()
        }
    }

    /** Get current user's weekly entry. */
    suspend fun getCurrentUserEntry(): LeaderboardEntry? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val weekId = currentWeekId()
            val doc = db.collection(COLLECTION)
                .document(weekId)
                .collection("entries")
                .document(uid)
                .get()
                .await()

            if (!doc.exists()) return LeaderboardEntry(
                rank = 0, uid = uid,
                displayName = "You",
                weeklyCoins = 0,
                tier = tierFor(0),
                isCurrentUser = true
            )

            val coins = doc.getLong("weeklyCoins")?.toInt() ?: 0
            LeaderboardEntry(
                rank          = 0, // rank requires full leaderboard query
                uid           = uid,
                displayName   = doc.getString("displayName") ?: "You",
                weeklyCoins   = coins,
                tier          = tierFor(coins),
                isCurrentUser = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "User entry fetch failed", e)
            null
        }
    }

    /** Update current user's weekly coin count. Call after every coin earn.
     *
     * BUG FIX: Parameter renamed from totalNewCoins to earnedCoins to clarify
     * this is an INCREMENT, not the new total. The old naming implied callers
     * might pass total weekly coins, causing confusion.
     *
     * The function reads the existing weekly total and adds only the incremental
     * amount earned in this transaction. This prevents double-counting when the
     * same session calls this multiple times.
     */
    suspend fun syncWeeklyCoins(earnedCoins: Int) {
        val uid = auth.currentUser?.uid ?: return
        val displayName = auth.currentUser?.displayName
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "User"
        try {
            val weekId = currentWeekId()
            val ref = db.collection(COLLECTION)
                .document(weekId)
                .collection("entries")
                .document(uid)

            val doc = ref.get().await()
            val existing = doc.getLong("weeklyCoins")?.toInt() ?: 0

            ref.set(
                mapOf(
                    "weeklyCoins" to existing + earnedCoins,
                    "displayName" to displayName,
                    "lastUpdated" to Timestamp.now(),
                    "uid"         to uid
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.e(TAG, "Weekly sync failed", e)
        }
    }

    // ── Tier helpers ─────────────────────────────────────────────────────────

    fun tierFor(weeklyCoins: Int): PrizeTier =
        PRIZE_TIERS.first { weeklyCoins >= it.minCoins }

    fun coinsToNextTier(weeklyCoins: Int): Int {
        val nextTier = PRIZE_TIERS.lastOrNull { it.minCoins > weeklyCoins }
        return if (nextTier == null) 0 else nextTier.minCoins - weeklyCoins
    }
}

// ─── Data Classes ─────────────────────────────────────────────────────────────

data class PrizeTier(
    val label       : String,
    val minCoins    : Int,
    val bonusCoins  : Int,
    val description : String
)

data class LeaderboardEntry(
    val rank         : Int,
    val uid          : String,
    val displayName  : String,
    val weeklyCoins  : Int,
    val tier         : PrizeTier,
    val isCurrentUser: Boolean = false
)
