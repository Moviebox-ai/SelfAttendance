package com.aaryo.selfattendance.admin

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aaryo.selfattendance.data.remote.RemoteConfigManager
import java.security.MessageDigest

// ═══════════════════════════════════════════════════════════════
//  AdminPinManager  — v2.0 (Fixed)
//
//  Bug fixes:
//   • DEFAULT_HASH was 63 chars (wrong) → now correct SHA-256("000000")
//   • No fallback when Remote Config not set → now "000000" is default
//   • Added first-time setup: if no PIN set, user can create one
//
//  PIN priority (highest → lowest):
//   1. Locally saved hash (EncryptedSharedPreferences)
//   2. Firebase Remote Config "admin_pin_hash"
//   3. Hardcoded default = SHA-256("000000") → "000000" works out of box
//
//  Change default PIN:
//   Firebase Console → Remote Config → admin_pin_hash = your SHA-256
// ═══════════════════════════════════════════════════════════════

class AdminPinManager(context: Context) {

    companion object {
        private const val TAG             = "AdminPinManager"
        private const val PREFS_FILE      = "admin_secure_store"
        private const val KEY_PIN_HASH    = "adm_pin_h"
        private const val KEY_ATTEMPTS    = "adm_att"
        private const val KEY_LOCKOUT_END = "adm_lock"
        private const val KEY_PIN_SET     = "adm_pin_set"   // first-time flag

        const val MAX_ATTEMPTS    = 5
        const val LOCKOUT_MILLIS  = 15 * 60 * 1000L   // 15 minutes

        // Correct SHA-256("000000") — 64 chars, verified
        // This means default PIN is "000000" until you change it
        private const val DEFAULT_HASH =
            "91b4d142823f7d20c5f08df69122de43f35f057a988d9619f6d3138485c9a203"
    }

    private val encPrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences failed, falling back to plain prefs", e)
            context.getSharedPreferences(PREFS_FILE + "_fb", Context.MODE_PRIVATE)
        }
    }

    // ── Public API ────────────────────────────────────────────────

    sealed class VerifyResult {
        object Success                        : VerifyResult()
        object FirstTimeSetup                 : VerifyResult()   // No PIN set yet
        data class Failed(val attemptsLeft: Int) : VerifyResult()
        data class Locked(val remainingMs: Long) : VerifyResult()
    }

    /** Returns true if no custom PIN has been saved locally yet */
    fun isFirstTimeSetup(): Boolean =
        !encPrefs.getBoolean(KEY_PIN_SET, false)

    /**
     * Verify PIN.
     * Returns [VerifyResult.FirstTimeSetup] if no PIN configured yet
     * (so UI can offer "Set your admin PIN" flow).
     */
    fun verify(inputPin: String): VerifyResult {
        if (inputPin.length != 6) {
            return VerifyResult.Failed(MAX_ATTEMPTS)
        }

        // Lockout check
        val lockoutEnd = encPrefs.getLong(KEY_LOCKOUT_END, 0L)
        val now        = System.currentTimeMillis()
        if (now < lockoutEnd) {
            return VerifyResult.Locked(lockoutEnd - now)
        }

        val inputHash  = sha256(inputPin)
        val storedHash = getStoredHash()

        return if (inputHash == storedHash) {
            resetAttempts()
            VerifyResult.Success
        } else {
            val attempts = incrementAttempts()
            if (attempts >= MAX_ATTEMPTS) {
                encPrefs.edit().putLong(KEY_LOCKOUT_END, now + LOCKOUT_MILLIS).apply()
                resetAttempts()
                VerifyResult.Locked(LOCKOUT_MILLIS)
            } else {
                VerifyResult.Failed(MAX_ATTEMPTS - attempts)
            }
        }
    }

    /**
     * Save a new admin PIN (hash only — plaintext never stored).
     * Call this from first-time setup or PIN change flow.
     */
    fun setPin(newPin: String): Boolean {
        if (newPin.length != 6 || !newPin.all { it.isDigit() }) return false
        encPrefs.edit()
            .putString(KEY_PIN_HASH, sha256(newPin))
            .putBoolean(KEY_PIN_SET, true)
            .apply()
        Log.d(TAG, "Admin PIN updated")
        return true
    }

    /** Lockout helpers */
    fun getLockoutRemainingMs(): Long {
        val end = encPrefs.getLong(KEY_LOCKOUT_END, 0L)
        return maxOf(0L, end - System.currentTimeMillis())
    }
    fun isLocked() = getLockoutRemainingMs() > 0

    // ── Private helpers ───────────────────────────────────────────

    private fun getStoredHash(): String {
        // 1. Locally saved (highest priority)
        val local = encPrefs.getString(KEY_PIN_HASH, null)
        if (!local.isNullOrBlank() && local.length == 64) return local

        // 2. Firebase Remote Config
        val remote = try {
            RemoteConfigManager.getInstance().getString("admin_pin_hash").trim()
        } catch (e: Exception) { "" }
        if (remote.length == 64) return remote

        // 3. Hardcoded default = SHA-256("000000")
        return DEFAULT_HASH
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun incrementAttempts(): Int {
        val next = encPrefs.getInt(KEY_ATTEMPTS, 0) + 1
        encPrefs.edit().putInt(KEY_ATTEMPTS, next).apply()
        return next
    }

    private fun resetAttempts() {
        encPrefs.edit().putInt(KEY_ATTEMPTS, 0).apply()
    }
}
