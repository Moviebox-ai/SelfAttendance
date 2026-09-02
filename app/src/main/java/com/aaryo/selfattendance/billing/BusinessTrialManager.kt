package com.aaryo.selfattendance.billing

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages the 7-day Free Trial for Business Mode (Employer / Staff Management).
 *
 * SERVER-AUTHORITATIVE CLOUD FUNCTION ARCHITECTURE:
 * To prevent trial reset when a user uninstalls and reinstalls the app:
 * 1. The Firebase Cloud Function `verifyUserTrial` verifies the user's trial start date
 *    strictly based on their unique User ID (UID) stored in Firestore.
 * 2. The Cloud Function executes on Google Cloud / Firebase server with the authoritative
 *    server timestamp, eliminating client-side clock tampering and uninstallation resets.
 * 3. Even after a complete uninstallation and local data wipe, as soon as the user logs in
 *    or opens the app, the Cloud Function fetches their permanent Firestore trial anchor.
 * 4. Resilient multi-tier fallback: If the network is temporarily unreachable, local cache
 *    and direct Firestore queries maintain seamless offline user experience.
 */
class BusinessTrialManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val functions by lazy { FirebaseFunctions.getInstance() }

    // ---------------------------------------------------------------
    //  Synchronous reads — used by Compose screens for instant UI paint.
    // ---------------------------------------------------------------

    fun isTrialActive(): Boolean {
        if (prefs.getBoolean(KEY_FORCE_EXPIRED, false)) return false

        val serverExpiry = prefs.getLong(KEY_BUSINESS_TRIAL_EXPIRY, 0L)
        val currentTime = getEffectiveCurrentTime()
        if (serverExpiry > 0L) {
            return currentTime < serverExpiry
        }

        val firstOpenTime = getOrSetFirstOpenTime()
        val diffMillis = currentTime - firstOpenTime
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diffMillis)
        return daysElapsed < TRIAL_DURATION_DAYS
    }

    fun getRemainingDays(): Int {
        if (prefs.getBoolean(KEY_FORCE_EXPIRED, false)) return 0

        val serverExpiry = prefs.getLong(KEY_BUSINESS_TRIAL_EXPIRY, 0L)
        val currentTime = getEffectiveCurrentTime()
        if (serverExpiry > 0L) {
            val diffMillis = serverExpiry - currentTime
            if (diffMillis <= 0L) return 0
            // Round up so day 1 shows 7 days remaining, etc.
            val remaining = (diffMillis / TimeUnit.DAYS.toMillis(1)).toInt() + 1
            return if (remaining > TRIAL_DURATION_DAYS) TRIAL_DURATION_DAYS else remaining
        }

        val firstOpenTime = getOrSetFirstOpenTime()
        val diffMillis = currentTime - firstOpenTime
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
        val remaining = TRIAL_DURATION_DAYS - daysElapsed
        return if (remaining > 0) remaining else 0
    }

    fun getElapsedDays(): Int {
        val serverStartTime = prefs.getLong(KEY_BUSINESS_TRIAL_START, 0L)
        val effectiveStart = if (serverStartTime > 0L) serverStartTime else getOrSetFirstOpenTime()
        val currentTime = getEffectiveCurrentTime()
        val diffMillis = Math.max(0L, currentTime - effectiveStart)
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }

    fun hasSeenWelcome(): Boolean {
        return prefs.getBoolean(KEY_WELCOME_SEEN, false)
    }

    fun markWelcomeSeen() {
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    fun getTrialStartTime(): Long {
        val serverStart = prefs.getLong(KEY_BUSINESS_TRIAL_START, 0L)
        return if (serverStart > 0L) serverStart else getOrSetFirstOpenTime()
    }

    fun getTrialExpiryTime(): Long {
        val serverExpiry = prefs.getLong(KEY_BUSINESS_TRIAL_EXPIRY, 0L)
        if (serverExpiry > 0L) return serverExpiry

        val startTime = getTrialStartTime()
        return startTime + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())
    }

    fun getFormattedExpiryDate(): String {
        val expiryMs = getTrialExpiryTime()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(expiryMs))
    }

    fun getFormattedStartDate(): String {
        val startMs = getTrialStartTime()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        return sdf.format(Date(startMs))
    }

    fun isVerifiedByServer(): Boolean {
        return prefs.getBoolean(KEY_VERIFIED_BY_SERVER, false)
    }

    // ---------------------------------------------------------------
    //  Firebase Cloud Function Verification Engine
    // ---------------------------------------------------------------

    /**
     * Authoritatively verifies the user's trial start date using the Firebase Cloud Function
     * `verifyUserTrial` based on their unique User ID (UID) stored in Firestore.
     *
     * If the user uninstalled and re-installed the app, this call immediately restores the
     * true trial start date and remaining days from Firestore, preventing any trial reset.
     */
    suspend fun syncWithServer(): Boolean {
        val user = auth.currentUser
        val deviceFingerprint = getDeviceFingerprint(context)

        // Tier 1: Call Firebase Cloud Function (Server-Authoritative Source of Truth)
        if (user != null) {
            try {
                Log.d(TAG, "Calling Firebase Cloud Function 'verifyUserTrial' for UID: ${user.uid}")
                val httpsCallable = functions.getHttpsCallable("verifyUserTrial")
                val requestPayload = hashMapOf<String, Any>(
                    "deviceFingerprint" to deviceFingerprint,
                    "platform" to "android"
                )

                val result = httpsCallable.call(requestPayload).await()
                val responseData = result.data as? Map<*, *>

                if (responseData != null && responseData["success"] == true) {
                    val serverStartTime = (responseData["trialStartTime"] as? Number)?.toLong() ?: 0L
                    val serverExpiryTime = (responseData["trialExpiryTime"] as? Number)?.toLong() ?: 0L
                    val isTrialActive = responseData["isTrialActive"] as? Boolean ?: true
                    val remainingDays = (responseData["remainingDays"] as? Number)?.toInt() ?: 0
                    val elapsedDays = (responseData["elapsedDays"] as? Number)?.toInt() ?: 0

                    if (serverStartTime > 0L) {
                        prefs.edit()
                            .putLong(KEY_BUSINESS_TRIAL_START, serverStartTime)
                            .putLong(KEY_BUSINESS_TRIAL_EXPIRY, serverExpiryTime)
                            .putBoolean(KEY_BUSINESS_TRIAL_ACTIVE, isTrialActive)
                            .putInt(KEY_BUSINESS_TRIAL_REMAINING_DAYS, remainingDays)
                            .putInt(KEY_BUSINESS_TRIAL_ELAPSED_DAYS, elapsedDays)
                            .putBoolean(KEY_VERIFIED_BY_SERVER, true)
                            .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
                            .apply()

                        Log.d(TAG, "Verified trial via Cloud Function: start=$serverStartTime, remaining=$remainingDays, active=$isTrialActive")
                        return true
                    }
                }
            } catch (cfException: Exception) {
                Log.w(TAG, "Cloud function verifyUserTrial failed: ${cfException.message}. Proceeding to Firestore fallback.")
            }
        }

        // Tier 2: Direct Firestore verification by unique User ID (UID)
        if (user != null) {
            try {
                val candidateTimes = mutableListOf<Long>()

                // 2A. Check Firestore by UID document
                val uidDocRef = firestore.collection(TRIAL_COLLECTION).document(user.uid)
                val uidSnap = uidDocRef.get().await()
                if (uidSnap.exists()) {
                    val time = uidSnap.getTimestamp("trialStartTime")?.toDate()?.time
                        ?: uidSnap.getLong("trialStartTime")
                    if (time != null && time > 0L) {
                        candidateTimes.add(time)
                        Log.d(TAG, "Found trial in businessTrials/${user.uid}: $time")
                    }
                }

                // 2B. Check Firestore user profile document
                val userProfileSnap = firestore.collection(USERS_COLLECTION).document(user.uid).get().await()
                if (userProfileSnap.exists()) {
                    val profileTime = userProfileSnap.getTimestamp("businessTrialStartTime")?.toDate()?.time
                        ?: userProfileSnap.getLong("businessTrialStartTime")
                    if (profileTime != null && profileTime > 0L) {
                        candidateTimes.add(profileTime)
                        Log.d(TAG, "Found trial in users/${user.uid}: $profileTime")
                    }
                }

                // 2C. Check email anchor if available
                val emailDocKey = emailKeyFor(user.email)
                if (emailDocKey != null) {
                    val emailSnap = firestore.collection(TRIAL_COLLECTION).document("email_$emailDocKey").get().await()
                    if (emailSnap.exists()) {
                        val emailTime = emailSnap.getTimestamp("trialStartTime")?.toDate()?.time
                            ?: emailSnap.getLong("trialStartTime")
                        if (emailTime != null && emailTime > 0L) {
                            candidateTimes.add(emailTime)
                        }
                    }
                }

                val earliestServerTime = candidateTimes.filter { it > 1577836800000L }.minOrNull()

                if (earliestServerTime != null && earliestServerTime > 0L) {
                    val expiry = earliestServerTime + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())
                    val diff = System.currentTimeMillis() - earliestServerTime
                    val elapsed = TimeUnit.MILLISECONDS.toDays(diff).toInt()
                    val remaining = Math.max(0, TRIAL_DURATION_DAYS - elapsed)
                    val isActive = elapsed < TRIAL_DURATION_DAYS

                    prefs.edit()
                        .putLong(KEY_BUSINESS_TRIAL_START, earliestServerTime)
                        .putLong(KEY_BUSINESS_TRIAL_EXPIRY, expiry)
                        .putBoolean(KEY_BUSINESS_TRIAL_ACTIVE, isActive)
                        .putInt(KEY_BUSINESS_TRIAL_REMAINING_DAYS, remaining)
                        .putInt(KEY_BUSINESS_TRIAL_ELAPSED_DAYS, elapsed)
                        .putBoolean(KEY_VERIFIED_BY_SERVER, true)
                        .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
                        .apply()

                    Log.d(TAG, "Direct Firestore trial verified for UID ${user.uid}: start=$earliestServerTime, remaining=$remaining")
                    return true
                } else {
                    // Initialize trial in Firestore for this unique UID
                    val newStartTime = System.currentTimeMillis()
                    val newExpiry = newStartTime + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())

                    val data = hashMapOf(
                        "uid" to user.uid,
                        "email" to (user.email ?: ""),
                        "trialStartTime" to com.google.firebase.Timestamp(Date(newStartTime)),
                        "createdAt" to com.google.firebase.Timestamp.now(),
                        "verifiedBy" to "direct_firestore_init"
                    )
                    uidDocRef.set(data).await()

                    prefs.edit()
                        .putLong(KEY_BUSINESS_TRIAL_START, newStartTime)
                        .putLong(KEY_BUSINESS_TRIAL_EXPIRY, newExpiry)
                        .putBoolean(KEY_BUSINESS_TRIAL_ACTIVE, true)
                        .putInt(KEY_BUSINESS_TRIAL_REMAINING_DAYS, TRIAL_DURATION_DAYS)
                        .putInt(KEY_BUSINESS_TRIAL_ELAPSED_DAYS, 0)
                        .putBoolean(KEY_VERIFIED_BY_SERVER, true)
                        .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
                        .apply()

                    Log.d(TAG, "Created new trial in Firestore for UID ${user.uid}: start=$newStartTime")
                    return true
                }
            } catch (dbErr: Exception) {
                Log.w(TAG, "Direct Firestore verification failed (offline): ${dbErr.message}")
            }
        }

        // Tier 3: Local cache fallback (when completely offline)
        val localTime = prefs.getLong(KEY_BUSINESS_TRIAL_START, 0L)
        if (localTime <= 0L) {
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_BUSINESS_TRIAL_START, now).apply()
        }

        return false
    }

    // ---------------------------------------------------------------
    //  Internal Helpers
    // ---------------------------------------------------------------

    private fun getOrSetFirstOpenTime(): Long {
        var time = prefs.getLong(KEY_BUSINESS_TRIAL_START, 0L)
        if (time <= 0L) {
            time = System.currentTimeMillis()
            prefs.edit().putLong(KEY_BUSINESS_TRIAL_START, time).apply()
            Log.d(TAG, "Initialized default trial start: $time")
        }
        return time
    }

    /**
     * Prevents clock tampering where user rolls back the device clock.
     */
    private fun getEffectiveCurrentTime(): Long {
        val now = System.currentTimeMillis()
        val lastKnown = prefs.getLong(KEY_LAST_KNOWN_TIME, 0L)
        if (now < lastKnown) {
            // Clock was set backwards! Use last known timestamp to preserve true elapsed progression.
            return lastKnown
        }
        prefs.edit().putLong(KEY_LAST_KNOWN_TIME, now).apply()
        return now
    }

    /**
     * Deterministic device hardware fingerprint.
     */
    private fun getDeviceFingerprint(context: Context): String {
        val androidId = try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (_: Exception) {
            ""
        }
        val raw = if (androidId.isNotBlank() && androidId != "9774d56d682e549c") {
            "dev_$androidId"
        } else {
            "dev_${android.os.Build.MANUFACTURER}_${android.os.Build.MODEL}_${android.os.Build.DEVICE}"
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Normalizes an email to prevent Gmail dot or plus-tag aliasing.
     */
    private fun emailKeyFor(rawEmail: String?): String? {
        val email = rawEmail?.trim()?.lowercase(Locale.ROOT)
        if (email.isNullOrBlank() || !email.contains("@")) return null

        val (localPart, domain) = email.split("@", limit = 2).let { it[0] to it[1] }
        val normalized = if (domain == "gmail.com" || domain == "googlemail.com") {
            val withoutTag = localPart.substringBefore("+")
            "${withoutTag.replace(".", "")}@gmail.com"
        } else {
            "$localPart@$domain"
        }

        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "BusinessTrialManager"
        private const val PREFS_NAME = "business_trial_prefs"
        private const val KEY_BUSINESS_TRIAL_START = "business_trial_start_time"
        private const val KEY_BUSINESS_TRIAL_EXPIRY = "business_trial_expiry_time"
        private const val KEY_BUSINESS_TRIAL_ACTIVE = "business_trial_active"
        private const val KEY_BUSINESS_TRIAL_REMAINING_DAYS = "business_trial_remaining_days"
        private const val KEY_BUSINESS_TRIAL_ELAPSED_DAYS = "business_trial_elapsed_days"
        private const val KEY_VERIFIED_BY_SERVER = "business_trial_verified_by_server"
        private const val KEY_LAST_SERVER_SYNC = "business_trial_last_server_sync"
        private const val KEY_LAST_KNOWN_TIME = "business_trial_last_known_time"
        private const val KEY_FORCE_EXPIRED = "business_trial_force_expired"
        private const val KEY_WELCOME_SEEN = "business_trial_welcome_seen"

        private const val TRIAL_COLLECTION = "businessTrials"
        private const val USERS_COLLECTION = "users"
        const val TRIAL_DURATION_DAYS = 7
    }
}
