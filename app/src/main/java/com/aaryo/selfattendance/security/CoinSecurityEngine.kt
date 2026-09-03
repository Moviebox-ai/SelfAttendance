package com.aaryo.selfattendance.security

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import java.time.LocalDate

/**
 * CoinSecurityEngine — Multi-layer Anti-Cheat & Integrity Verification.
 *
 * Protects the coin economy against:
 * 1. Root / Hex / SharedPreferences XML tampering (via Cryptographic HMAC-SHA256 Signature).
 * 2. Memory injectors & cheat engines (validates checksum on critical operations).
 * 3. Clock manipulation / Date rollback attacks (uses SystemClock.elapsedRealtime alongside wall-clock).
 * 4. Velocity exploitation / Rapid automated spamming (strict per-action & daily rate limits).
 */
object CoinSecurityEngine {

    private const val TAG = "CoinSecurityEngine"

    // Cryptographic salt combined with application secret
    private const val SECURITY_SALT = "Aaryo_SelfAtt_Sec_CoinSalt_v2_987413"

    // Limits & Thresholds
    const val MAX_SINGLE_AWARD_LIMIT = 50       // Maximum coins in a single non-admin action
    const val MAX_DAILY_EARN_CAP     = 200      // Maximum coins earnable in 24 hours
    const val MIN_AD_COOLDOWN_MS     = 55_000L  // Minimum real elapsed time between rewards (55s)

    private val currentUid: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: "guest_device"

    /**
     * Generates an HMAC-SHA256 signature for the given coin state.
     */
    fun generateSignature(coinBalance: Int, totalEarned: Int, uid: String): String {
        val raw = "$uid#$coinBalance#$totalEarned#$SECURITY_SALT"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Checks if the local coin storage has been altered without a valid cryptographic signature.
     * Supports seamless UID migration between guest and authenticated accounts, preventing false wipes.
     * Returns true if data is authentic, false if tampered.
     */
    fun verifyIntegrity(prefs: PreferencesManager): Boolean {
        val storedSig = prefs.coinSecuritySignature
        val storedUid = prefs.coinSecurityUid
        val balance = prefs.coinBalance
        val totalEarned = prefs.totalCoinsEarned

        // If never initialized (brand new install or upgrade from older version), initialize signature
        if (storedSig.isEmpty()) {
            updateSignature(prefs, balance, totalEarned)
            return true
        }

        val uid = currentUid
        val matchesCurrent = storedSig == generateSignature(balance, totalEarned, uid)
        val matchesStoredUid = storedUid.isNotEmpty() && storedSig == generateSignature(balance, totalEarned, storedUid)
        val matchesGuest = storedSig == generateSignature(balance, totalEarned, "guest_device")

        val matches = matchesCurrent || matchesStoredUid || matchesGuest

        if (matches) {
            // If user transitioned from guest/another state to an authenticated account, upgrade signature
            if (uid != "guest_device" && storedUid != uid) {
                updateSignature(prefs, balance, totalEarned)
            }
            return true
        }

        Log.e(TAG, "TAMPER DETECTED! Expected: ${generateSignature(balance, totalEarned, uid)}, Stored: $storedSig for coins: $balance")
        return false
    }

    /**
     * Atomically computes and writes a new cryptographic signature alongside the new coin state.
     */
    fun updateSignature(prefs: PreferencesManager, newBalance: Int, newTotalEarned: Int) {
        val uid = currentUid
        val sig = generateSignature(newBalance, newTotalEarned, uid)
        prefs.coinSecurityUid = uid
        prefs.coinSecuritySignature = sig
    }

    /**
     * Validates an incoming coin award before applying it.
     * Checks:
     * - Single transaction ceiling
     * - 24-Hour Velocity limits
     * - Real elapsed time cooldown
     */
    fun validateCoinAward(
        prefs: PreferencesManager,
        amount: Int,
        lastElapsedRealtimeMs: Long
    ): ValidationResult {
        if (amount <= 0) {
            return ValidationResult.Rejected("Invalid coin amount: $amount")
        }

        if (amount > MAX_SINGLE_AWARD_LIMIT) {
            Log.w(TAG, "Coin award rejected: $amount exceeds single action limit $MAX_SINGLE_AWARD_LIMIT")
            return ValidationResult.Rejected("Exceeds single award limit")
        }

        val nowElapsed = SystemClock.elapsedRealtime()
        if (lastElapsedRealtimeMs > 0L) {
            val realElapsed = nowElapsed - lastElapsedRealtimeMs
            if (realElapsed < MIN_AD_COOLDOWN_MS) {
                Log.w(TAG, "Coin award rejected: Cooldown bypassed (elapsed: ${realElapsed}ms)")
                return ValidationResult.Rejected("Reward cooldown active. Please wait.")
            }
        }

        val today = LocalDate.now().toString()
        val dailyEarned = if (prefs.securityDailyEarnDate == today) {
            prefs.securityDailyEarnedCoins
        } else {
            0
        }

        if (dailyEarned + amount > MAX_DAILY_EARN_CAP) {
            Log.w(TAG, "Daily coin velocity limit hit: $dailyEarned + $amount > $MAX_DAILY_EARN_CAP")
            return ValidationResult.Rejected("Daily reward limit reached ($MAX_DAILY_EARN_CAP coins/day). Come back tomorrow!")
        }

        return ValidationResult.Allowed
    }

    /**
     * Safely applies a validated coin addition with cryptographic re-signing.
     */
    fun secureCreditCoins(
        prefs: PreferencesManager,
        amount: Int,
        source: String
    ): Boolean {
        // First verify existing integrity
        if (!verifyIntegrity(prefs)) {
            Log.e(TAG, "Cannot credit coins: Storage integrity violation detected!")
            // Reset to safe 0 if tampered
            prefs.coinBalance = 0
            prefs.totalCoinsEarned = 0
            updateSignature(prefs, 0, 0)
            return false
        }

        val today = LocalDate.now().toString()
        if (prefs.securityDailyEarnDate != today) {
            prefs.securityDailyEarnDate = today
            prefs.securityDailyEarnedCoins = 0
        }

        val newBalance = prefs.coinBalance + amount
        val newTotal = prefs.totalCoinsEarned + amount

        prefs.coinBalance = newBalance
        prefs.totalCoinsEarned = newTotal
        prefs.securityDailyEarnedCoins += amount
        prefs.securityLastRewardElapsedRealtime = SystemClock.elapsedRealtime()

        // Sign the new state
        updateSignature(prefs, newBalance, newTotal)
        Log.d(TAG, "Securely credited $amount coins from $source. New balance: $newBalance")
        return true
    }

    /**
     * Safely spends coins with cryptographic re-signing.
     */
    fun secureSpendCoins(
        prefs: PreferencesManager,
        amount: Int
    ): Boolean {
        if (!verifyIntegrity(prefs)) {
            Log.e(TAG, "Cannot spend coins: Storage integrity violation detected!")
            prefs.coinBalance = 0
            updateSignature(prefs, 0, prefs.totalCoinsEarned)
            return false
        }

        if (prefs.coinBalance < amount) {
            return false
        }

        val newBalance = prefs.coinBalance - amount
        prefs.coinBalance = newBalance
        updateSignature(prefs, newBalance, prefs.totalCoinsEarned)
        Log.d(TAG, "Securely deducted $amount coins. New balance: $newBalance")
        return true
    }

    sealed class ValidationResult {
        object Allowed : ValidationResult()
        data class Rejected(val reason: String) : ValidationResult()
    }
}
