package com.aaryo.selfattendance.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * RewardRepository — Firebase Firestore sync for ALL reward / coin / premium data.
 *
 * Everything is stored as nested maps inside the existing user document:
 *   users/{uid} → {
 *       rewards: { coinBalance, totalCoinsEarned, spin metadata… },
 *       premiumUnlocks: { theme, restore, reset, pdf, salary, coinBalance }
 *   }
 *
 * SetOptions.merge() ensures profile fields (name, salary, etc.) are never
 * overwritten.
 *
 * All methods are null-safe when the user is not logged in.
 */
object RewardRepository {

    private const val TAG = "RewardRepository"

    private val db  get() = FirebaseFirestore.getInstance()
    private val uid get() = FirebaseAuth.getInstance().currentUser?.uid

    // ── Save spin / coin data (called from RewardsScreen after each spin) ────
    //
    // BUG FIX (race condition): coinBalance/totalCoinsEarned used to be written
    // as absolute values read from local prefs. If the same account was open on
    // two devices and both earned coins around the same time, the second
    // Firestore write would overwrite the first — silently losing coins.
    // FieldValue.increment() is atomic server-side, so concurrent writes from
    // multiple devices now always sum correctly instead of racing.
    suspend fun saveRewards(
        coinsDelta             : Int,
        lastSpinDate           : String,
        dailySpinsUsed         : Int,
        lastSpinTimestampMs    : Long,
        lastSpinIndex          : Int,
        lastDailyLoginDate     : String = "",
        lastAttendanceCoinDate : String = ""
    ) {
        val currentUid = uid ?: run {
            Log.d(TAG, "saveRewards: user not logged in — skipping Firebase sync")
            return
        }
        try {
            val payload = mapOf(
                "rewards" to mapOf(
                    "coinBalance"            to FieldValue.increment(coinsDelta.toLong()),
                    "totalCoinsEarned"       to FieldValue.increment(coinsDelta.toLong()),
                    "lastSpinDate"           to lastSpinDate,
                    "dailySpinsUsed"         to dailySpinsUsed,
                    "lastSpinTimestampMs"    to lastSpinTimestampMs,
                    "lastSpinIndex"          to lastSpinIndex,
                    "lastDailyLoginDate"     to lastDailyLoginDate,
                    "lastAttendanceCoinDate" to lastAttendanceCoinDate,
                    "updatedAt"              to System.currentTimeMillis()
                )
            )
            db.collection("users").document(currentUid)
                .set(payload, SetOptions.merge()).await()
            Log.d(TAG, "Rewards saved: coinsDelta=$coinsDelta")
        } catch (e: Exception) {
            Log.e(TAG, "saveRewards failed: ${e.message}")
        }
    }

    // ── Save daily-spin-dialog metadata only (DailySpinScreen — no coins) ────

    suspend fun saveSpinMetadata(
        lastSpinDate        : String,
        dailySpinsUsed      : Int,
        lastSpinTimestampMs : Long,
        lastSpinIndex       : Int
    ) {
        val currentUid = uid ?: return
        try {
            val payload = mapOf(
                "rewards" to mapOf(
                    "lastSpinDate"          to lastSpinDate,
                    "dailySpinsUsed"        to dailySpinsUsed,
                    "lastSpinTimestampMs"   to lastSpinTimestampMs,
                    "lastSpinIndex"         to lastSpinIndex,
                    "updatedAt"             to System.currentTimeMillis()
                )
            )
            db.collection("users").document(currentUid)
                .set(payload, SetOptions.merge()).await()
            Log.d(TAG, "SpinMetadata saved: date=$lastSpinDate used=$dailySpinsUsed")
        } catch (e: Exception) {
            Log.e(TAG, "saveSpinMetadata failed: ${e.message}")
        }
    }

    // ── Save premium feature unlocks + coin spend (SettingsScreen) ───────────
    //
    // BUG FIX (race condition): same reasoning as saveRewards — coinBalance is
    // now applied as an atomic delta (negative, since this is a spend) instead
    // of an absolute overwrite, so it can't clobber a concurrent write from
    // another device/session.
    suspend fun savePremiumUnlocks(
        coinsDelta             : Int,
        restoreUntilMs         : Long,
        resetUntilMs           : Long,
        pdfUntilMs             : Long,
        salaryUntilMs          : Long
    ) {
        val currentUid = uid ?: run {
            Log.d(TAG, "savePremiumUnlocks: user not logged in — skipping")
            return
        }
        try {
            val payload = mapOf(
                "rewards" to mapOf(
                    "coinBalance"               to FieldValue.increment(coinsDelta.toLong()),
                    "updatedAt"                 to System.currentTimeMillis()
                ),
                "premiumUnlocks" to mapOf(
                    "restoreUntilMs"            to restoreUntilMs,
                    "resetUntilMs"              to resetUntilMs,
                    "pdfUntilMs"                to pdfUntilMs,
                    "salaryUntilMs"             to salaryUntilMs,
                    "updatedAt"                 to System.currentTimeMillis()
                )
            )
            db.collection("users").document(currentUid)
                .set(payload, SetOptions.merge()).await()
            Log.d(TAG, "PremiumUnlocks saved: coinsDelta=$coinsDelta")
        } catch (e: Exception) {
            Log.e(TAG, "savePremiumUnlocks failed: ${e.message}")
        }
    }

    // ── Save a single premium theme unlock + coin spend (ThemePickerDialog) ──
    // Each theme unlocks independently — stored under premiumUnlocks.themeUnlocks.<key>
    // so unlocking one theme never grants access to the others.
    suspend fun saveThemeUnlock(coinsDelta: Int, themeKey: String, untilMs: Long) {
        val currentUid = uid ?: run {
            Log.d(TAG, "saveThemeUnlock: user not logged in — skipping")
            return
        }
        try {
            val payload = mapOf(
                "rewards" to mapOf(
                    "coinBalance" to FieldValue.increment(coinsDelta.toLong()),
                    "updatedAt"   to System.currentTimeMillis()
                ),
                "premiumUnlocks" to mapOf(
                    "themeUnlocks" to mapOf(themeKey to untilMs),
                    "updatedAt"    to System.currentTimeMillis()
                )
            )
            db.collection("users").document(currentUid)
                .set(payload, SetOptions.merge()).await()
            Log.d(TAG, "ThemeUnlock saved: theme=$themeKey coinsDelta=$coinsDelta")
        } catch (e: Exception) {
            Log.e(TAG, "saveThemeUnlock failed: ${e.message}")
        }
    }

    // ── Save leaderboard streak ───────────────────────────────────────────────

    suspend fun saveStreak(currentStreak: Int, bestStreak: Int) {
        val currentUid = uid ?: return
        try {
            val payload = mapOf(
                "leaderboard" to mapOf(
                    "currentStreak" to currentStreak,
                    "bestStreak"    to bestStreak,
                    "updatedAt"     to System.currentTimeMillis()
                )
            )
            db.collection("users").document(currentUid)
                .set(payload, SetOptions.merge()).await()
            Log.d(TAG, "Streak saved: current=$currentStreak best=$bestStreak")
        } catch (e: Exception) {
            Log.e(TAG, "saveStreak failed: ${e.message}")
        }
    }


    // ── One-shot restore: Firebase → local prefs (call at app startup) ───────
    // Loads coins + premium unlocks BEFORE any screen is displayed.
    // Prevents DashboardScreen / CalendarScreen from reading prefs=0 after a
    // data-clear and writing 0+new_coins back to Firebase (which destroys the
    // saved balance).

    suspend fun syncFromFirebase(prefs: com.aaryo.selfattendance.data.local.PreferencesManager) {
        val currentUid = uid ?: run {
            Log.d(TAG, "syncFromFirebase: not logged in — skip")
            return
        }
        try {
            val doc = db.collection("users").document(currentUid).get().await()

            // ── Coin / spin data ────────────────────────────────────────────
            @Suppress("UNCHECKED_CAST")
            val rewards = doc.get("rewards") as? Map<String, Any>
            if (rewards != null) {
                val remoteBal = (rewards["coinBalance"] as? Long)?.toInt() ?: 0
                if (remoteBal >= prefs.coinBalance) {
                    prefs.coinBalance         = remoteBal
                    prefs.totalCoinsEarned    = (rewards["totalCoinsEarned"]    as? Long)?.toInt() ?: prefs.totalCoinsEarned
                    prefs.lastSpinDate        = (rewards["lastSpinDate"]        as? String)         ?: prefs.lastSpinDate
                    prefs.dailySpinsUsed      = (rewards["dailySpinsUsed"]      as? Long)?.toInt() ?: prefs.dailySpinsUsed
                    prefs.lastSpinTimestampMs = (rewards["lastSpinTimestampMs"] as? Long)           ?: prefs.lastSpinTimestampMs
                    prefs.lastSpinIndex       = (rewards["lastSpinIndex"]       as? Long)?.toInt() ?: prefs.lastSpinIndex
                    Log.d(TAG, "syncFromFirebase: balance restored=$remoteBal")
                }
                // Always restore daily-gate dates from Firebase (even if coin balance unchanged).
                // This prevents re-awarding daily login & attendance coins after data-clear.
                val remoteDailyLogin = (rewards["lastDailyLoginDate"]     as? String) ?: ""
                val remoteAttCoin    = (rewards["lastAttendanceCoinDate"] as? String) ?: ""
                if (remoteDailyLogin > prefs.lastDailyLoginDate) {
                    prefs.lastDailyLoginDate = remoteDailyLogin
                    Log.d(TAG, "syncFromFirebase: lastDailyLoginDate restored=$remoteDailyLogin")
                }
                if (remoteAttCoin > prefs.lastAttendanceCoinDate) {
                    prefs.lastAttendanceCoinDate = remoteAttCoin
                    Log.d(TAG, "syncFromFirebase: lastAttendanceCoinDate restored=$remoteAttCoin")
                }
            }

            // ── Premium unlocks ─────────────────────────────────────────────
            @Suppress("UNCHECKED_CAST")
            val premium = doc.get("premiumUnlocks") as? Map<String, Any>
            if (premium != null) {
                val rRestore = (premium["restoreUntilMs"] as? Long) ?: 0L
                val rReset   = (premium["resetUntilMs"]   as? Long) ?: 0L
                val rPdf     = (premium["pdfUntilMs"]     as? Long) ?: 0L
                val rSalary  = (premium["salaryUntilMs"]  as? Long) ?: 0L
                if (rRestore > prefs.premRestoreUnlockUntilMs)     prefs.premRestoreUnlockUntilMs     = rRestore
                if (rReset   > prefs.premResetUnlockUntilMs)       prefs.premResetUnlockUntilMs       = rReset
                if (rPdf     > prefs.premPdfExportUnlockUntilMs)   prefs.premPdfExportUnlockUntilMs   = rPdf
                if (rSalary  > prefs.premSalarySlipUnlockUntilMs)  prefs.premSalarySlipUnlockUntilMs  = rSalary

                // Each theme unlocks independently — merge in every per-theme
                // timestamp the cloud has, never overwrite a longer local one.
                @Suppress("UNCHECKED_CAST")
                val rThemeUnlocks = premium["themeUnlocks"] as? Map<String, Any>
                rThemeUnlocks?.forEach { (themeKey, value) ->
                    val rUntil = (value as? Long) ?: 0L
                    if (rUntil > prefs.themeUnlockUntilMs(themeKey)) {
                        prefs.setThemeUnlockUntilMs(themeKey, rUntil)
                    }
                }
                Log.d(TAG, "syncFromFirebase: premium unlocks restored")
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncFromFirebase failed: ${e.message}")
        }
    }

    // ── Load all reward data (RewardsScreen on entry) ─────────────────────────

    suspend fun loadRewards(): RewardData? {
        val currentUid = uid ?: return null
        return try {
            val doc = db.collection("users").document(currentUid).get().await()
            @Suppress("UNCHECKED_CAST")
            val map = doc.get("rewards") as? Map<String, Any> ?: return null
            RewardData(
                coinBalance         = (map["coinBalance"]         as? Long)?.toInt() ?: 0,
                totalCoinsEarned    = (map["totalCoinsEarned"]    as? Long)?.toInt() ?: 0,
                lastSpinDate        = (map["lastSpinDate"]        as? String) ?: "",
                dailySpinsUsed      = (map["dailySpinsUsed"]      as? Long)?.toInt() ?: 0,
                lastSpinTimestampMs = (map["lastSpinTimestampMs"] as? Long) ?: 0L,
                lastSpinIndex       = (map["lastSpinIndex"]       as? Long)?.toInt() ?: 0
            )
        } catch (e: Exception) {
            Log.e(TAG, "loadRewards failed: ${e.message}")
            null
        }
    }

    // ── Load premium unlocks (SettingsScreen on entry) ────────────────────────

    suspend fun loadPremiumUnlocks(): PremiumUnlockData? {
        val currentUid = uid ?: return null
        return try {
            val doc = db.collection("users").document(currentUid).get().await()
            @Suppress("UNCHECKED_CAST")
            val map = doc.get("premiumUnlocks") as? Map<String, Any> ?: return null
            @Suppress("UNCHECKED_CAST")
            val rewards = doc.get("rewards") as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val themeUnlocks = (map["themeUnlocks"] as? Map<String, Any>)
                ?.mapValues { (_, v) -> (v as? Long) ?: 0L } ?: emptyMap()
            PremiumUnlockData(
                coinBalance     = (rewards?.get("coinBalance") as? Long)?.toInt() ?: 0,
                themeUnlocks    = themeUnlocks,
                restoreUntilMs  = (map["restoreUntilMs"] as? Long) ?: 0L,
                resetUntilMs    = (map["resetUntilMs"]   as? Long) ?: 0L,
                pdfUntilMs      = (map["pdfUntilMs"]     as? Long) ?: 0L,
                salaryUntilMs   = (map["salaryUntilMs"]  as? Long) ?: 0L
            )
        } catch (e: Exception) {
            Log.e(TAG, "loadPremiumUnlocks failed: ${e.message}")
            null
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Data classes
// ─────────────────────────────────────────────────────────────────────────────

data class RewardData(
    val coinBalance         : Int,
    val totalCoinsEarned    : Int,
    val lastSpinDate        : String,
    val dailySpinsUsed      : Int,
    val lastSpinTimestampMs : Long,
    val lastSpinIndex       : Int
)

data class PremiumUnlockData(
    val coinBalance     : Int,
    // themeKey -> unlockedUntilMs; each of the 6 themes unlocks independently.
    val themeUnlocks    : Map<String, Long>,
    val restoreUntilMs  : Long,
    val resetUntilMs    : Long,
    val pdfUntilMs      : Long,
    val salaryUntilMs   : Long
)
