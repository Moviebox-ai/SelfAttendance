package com.aaryo.selfattendance.data.repository

import android.content.Context
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.security.CoinSecurityEngine
import java.time.LocalDate

class CoinRepository(private val context: Context) {

    private val prefs = PreferencesManager(context)

    fun isIntegrityValid(): Boolean {
        return CoinSecurityEngine.verifyIntegrity(prefs)
    }

    fun getLocalDailyCoinsEarned(): Int {
        val today = LocalDate.now().toString()
        return if (prefs.lastSpinDate == today) {
            prefs.dailySpinsUsed
        } else {
            0
        }
    }

    fun setLocalDailyCoins(coins: Int) {
        val today = LocalDate.now().toString()
        prefs.lastSpinDate = today
        prefs.dailySpinsUsed = coins
    }

    /**
     * Safely increments coins while enforcing Anti-Cheat integrity and rate-limits.
     */
    fun addLocalDailyCoins(coins: Int): Boolean {
        val today = LocalDate.now().toString()
        if (prefs.lastSpinDate != today) {
            prefs.lastSpinDate = today
            prefs.dailySpinsUsed = 0
        }

        // Validate award against anti-cheat rules
        val validation = CoinSecurityEngine.validateCoinAward(
            prefs = prefs,
            amount = coins,
            lastElapsedRealtimeMs = prefs.securityLastRewardElapsedRealtime
        )

        if (validation !is CoinSecurityEngine.ValidationResult.Allowed) {
            return false
        }

        val success = CoinSecurityEngine.secureCreditCoins(
            prefs = prefs,
            amount = coins,
            source = "ad_reward_or_spin"
        )

        if (success) {
            prefs.dailySpinsUsed = prefs.dailySpinsUsed + coins
        }
        return success
    }

    fun canWatchAd(): Boolean {
        val now = System.currentTimeMillis()
        val lastTimestamp = prefs.lastSpinTimestampMs
        // Cooldown of 60 seconds (60,000 ms)
        return (now - lastTimestamp) >= 60_000L
    }

    fun adCooldownRemainingSeconds(): Long {
        val now = System.currentTimeMillis()
        val lastTimestamp = prefs.lastSpinTimestampMs
        val elapsed = now - lastTimestamp
        val totalCooldown = 60_000L
        return if (elapsed < totalCooldown) {
            (totalCooldown - elapsed) / 1000L
        } else {
            0L
        }
    }

    fun recordAdWatched() {
        prefs.lastSpinTimestampMs = System.currentTimeMillis()
    }
}
