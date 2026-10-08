package com.aaryo.selfattendance.billing

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Manages the 7-day Free Trial for Business Mode (Employer / Staff Management).
 *
 * ANTI-TRIAL-RESET ENGINE:
 * Prevents trial renewal when the user uninstalls and reinstalls the app:
 * 1. Persistent Device Storage (MediaStore + Public Documents/Downloads):
 *    Survives uninstallation on the physical device even without internet.
 * 2. Firestore Cloud Multi-Anchor:
 *    Records trial start time in Firestore under:
 *      a) Device Hardware Fingerprint (`businessTrials/dev_{fingerprint}`)
 *      b) User Account ID (`businessTrials/{uid}`)
 *      c) Normalized Email (`businessTrials/email_{emailKey}`)
 * 3. Earliest Timestamp Rule:
 *    Always enforces the EARLIEST trial timestamp found across all local & cloud sources.
 *    If 7 days have elapsed from the original start date, the trial is permanently expired.
 */
class BusinessTrialManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // ---------------------------------------------------------------
    //  Synchronous reads — used by Compose screens for instant UI paint.
    // ---------------------------------------------------------------

    fun isTrialActive(): Boolean {
        if (prefs.getBoolean(KEY_FORCE_EXPIRED, false)) return false

        val serverExpiry = prefs.getLong(KEY_BUSINESS_TRIAL_EXPIRY, 0L)
        val currentTime = getEffectiveCurrentTime()
        if (serverExpiry > 0L) {
            val active = currentTime < serverExpiry
            if (!active) {
                prefs.edit().putBoolean(KEY_FORCE_EXPIRED, true).apply()
            }
            return active
        }

        val firstOpenTime = getOrSetFirstOpenTime()
        val diffMillis = currentTime - firstOpenTime
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diffMillis)
        val active = daysElapsed < TRIAL_DURATION_DAYS
        if (!active) {
            prefs.edit().putBoolean(KEY_FORCE_EXPIRED, true).apply()
        }
        return active
    }

    fun getRemainingDays(): Int {
        if (prefs.getBoolean(KEY_FORCE_EXPIRED, false)) return 0

        val serverExpiry = prefs.getLong(KEY_BUSINESS_TRIAL_EXPIRY, 0L)
        val currentTime = getEffectiveCurrentTime()
        if (serverExpiry > 0L) {
            val diffMillis = serverExpiry - currentTime
            if (diffMillis <= 0L) {
                prefs.edit().putBoolean(KEY_FORCE_EXPIRED, true).apply()
                return 0
            }
            val remaining = (diffMillis / TimeUnit.DAYS.toMillis(1)).toInt() + 1
            return if (remaining > TRIAL_DURATION_DAYS) TRIAL_DURATION_DAYS else remaining
        }

        val firstOpenTime = getOrSetFirstOpenTime()
        val diffMillis = currentTime - firstOpenTime
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
        val remaining = TRIAL_DURATION_DAYS - daysElapsed
        if (remaining <= 0) {
            prefs.edit().putBoolean(KEY_FORCE_EXPIRED, true).apply()
            return 0
        }
        return remaining
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
    //  Multi-Tier Cloud & Device Verification Engine
    // ---------------------------------------------------------------

    /**
     * Authoritatively verifies and syncs the user's trial start date.
     * Checks Device Hardware Fingerprint, UID, and Email in Firestore,
     * combined with on-device PersistentDeviceStorage.
     *
     * Guarantees that uninstalling and reinstalling the app NEVER resets or extends the trial.
     */
    suspend fun syncWithServer(): Boolean {
        val user = auth.currentUser
        val deviceFingerprint = getDeviceFingerprint(context)
        val candidateTimes = mutableListOf<Long>()

        // 1. Check on-device persistent storage (survives uninstallations)
        PersistentDeviceStorage.readAnchorTime(context)?.let {
            if (it > MIN_VALID_TIMESTAMP) candidateTimes.add(it)
        }

        // 2. Check local SharedPreferences
        val localStart = prefs.getLong(KEY_BUSINESS_TRIAL_START, 0L)
        if (localStart > MIN_VALID_TIMESTAMP) {
            candidateTimes.add(localStart)
        }

        // 3. Check Firestore Cloud anchors
        if (user != null) {
            try {
                // 3A. Check Device Fingerprint in Firestore (Prevents account-switching on same phone)
                try {
                    val devDocRef = firestore.collection(TRIAL_COLLECTION).document("dev_$deviceFingerprint")
                    val devSnap = devDocRef.get().await()
                    if (devSnap.exists()) {
                        val time = devSnap.getTimestamp("trialStartTime")?.toDate()?.time
                            ?: devSnap.getLong("trialStartTime")
                        if (time != null && time > MIN_VALID_TIMESTAMP) {
                            candidateTimes.add(time)
                            Log.d(TAG, "Found Firestore device trial anchor: $time")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "devDoc query skipped: ${e.message}")
                }

                // 3B. Check User UID in Firestore
                try {
                    val uidDocRef = firestore.collection(TRIAL_COLLECTION).document(user.uid)
                    val uidSnap = uidDocRef.get().await()
                    if (uidSnap.exists()) {
                        val time = uidSnap.getTimestamp("trialStartTime")?.toDate()?.time
                            ?: uidSnap.getLong("trialStartTime")
                        if (time != null && time > MIN_VALID_TIMESTAMP) {
                            candidateTimes.add(time)
                            Log.d(TAG, "Found Firestore UID trial anchor: $time")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "uidDoc query skipped: ${e.message}")
                }

                // 3C. Check User Profile in Firestore
                try {
                    val userProfileSnap = firestore.collection(USERS_COLLECTION).document(user.uid).get().await()
                    if (userProfileSnap.exists()) {
                        val profileTime = userProfileSnap.getTimestamp("businessTrialStartTime")?.toDate()?.time
                            ?: userProfileSnap.getLong("businessTrialStartTime")
                        if (profileTime != null && profileTime > MIN_VALID_TIMESTAMP) {
                            candidateTimes.add(profileTime)
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "userProfile query skipped: ${e.message}")
                }

                // 3D. Check Normalized Email in Firestore
                val emailDocKey = emailKeyFor(user.email)
                if (emailDocKey != null) {
                    try {
                        val emailSnap = firestore.collection(TRIAL_COLLECTION).document("email_$emailDocKey").get().await()
                        if (emailSnap.exists()) {
                            val emailTime = emailSnap.getTimestamp("trialStartTime")?.toDate()?.time
                                ?: emailSnap.getLong("trialStartTime")
                            if (emailTime != null && emailTime > MIN_VALID_TIMESTAMP) {
                                candidateTimes.add(emailTime)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "emailSnap query skipped: ${e.message}")
                    }
                }

            } catch (dbErr: Exception) {
                Log.w(TAG, "Firestore sync query error: ${dbErr.message}")
            }
        }

        // 4. Resolve the EARLIEST recorded trial start time
        val earliestTime = candidateTimes.filter { it > MIN_VALID_TIMESTAMP }.minOrNull()

        if (earliestTime != null) {
            // Existing trial found! Compute true elapsed time from the original start date
            val expiry = earliestTime + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())
            val diff = System.currentTimeMillis() - earliestTime
            val elapsed = TimeUnit.MILLISECONDS.toDays(diff).toInt()
            val remaining = Math.max(0, TRIAL_DURATION_DAYS - elapsed)
            val isActive = elapsed < TRIAL_DURATION_DAYS

            prefs.edit()
                .putLong(KEY_BUSINESS_TRIAL_START, earliestTime)
                .putLong(KEY_BUSINESS_TRIAL_EXPIRY, expiry)
                .putBoolean(KEY_BUSINESS_TRIAL_ACTIVE, isActive)
                .putInt(KEY_BUSINESS_TRIAL_REMAINING_DAYS, remaining)
                .putInt(KEY_BUSINESS_TRIAL_ELAPSED_DAYS, elapsed)
                .putBoolean(KEY_FORCE_EXPIRED, !isActive)
                .putBoolean(KEY_VERIFIED_BY_SERVER, true)
                .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
                .apply()

            // Ensure physical device storage also has this true anchor
            PersistentDeviceStorage.saveAnchorTime(context, earliestTime)

            // Propagate anchor to all cloud anchors if user is logged in
            if (user != null) {
                writeCloudAnchorsIfMissing(user, earliestTime, deviceFingerprint)
            }

            Log.d(TAG, "Enforced trial start=$earliestTime, elapsed=$elapsed days, remaining=$remaining days, active=$isActive")
            return true
        } else {
            // Brand new first-time trial initialization
            val newStartTime = System.currentTimeMillis()
            val newExpiry = newStartTime + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())

            prefs.edit()
                .putLong(KEY_BUSINESS_TRIAL_START, newStartTime)
                .putLong(KEY_BUSINESS_TRIAL_EXPIRY, newExpiry)
                .putBoolean(KEY_BUSINESS_TRIAL_ACTIVE, true)
                .putInt(KEY_BUSINESS_TRIAL_REMAINING_DAYS, TRIAL_DURATION_DAYS)
                .putInt(KEY_BUSINESS_TRIAL_ELAPSED_DAYS, 0)
                .putBoolean(KEY_FORCE_EXPIRED, false)
                .putBoolean(KEY_VERIFIED_BY_SERVER, true)
                .putLong(KEY_LAST_SERVER_SYNC, System.currentTimeMillis())
                .apply()

            PersistentDeviceStorage.saveAnchorTime(context, newStartTime)

            if (user != null) {
                writeCloudAnchorsIfMissing(user, newStartTime, deviceFingerprint)
            }

            Log.d(TAG, "Created fresh trial start: $newStartTime")
            return true
        }
    }

    /**
     * Persists trial start time to Firestore under both UID, Device Fingerprint, and Email.
     */
    private suspend fun writeCloudAnchorsIfMissing(
        user: com.google.firebase.auth.FirebaseUser,
        startTime: Long,
        deviceFingerprint: String
    ) {
        val baseData = hashMapOf(
            "uid" to user.uid,
            "email" to (user.email ?: ""),
            "trialStartTime" to com.google.firebase.Timestamp(Date(startTime)),
            "deviceFingerprint" to deviceFingerprint,
            "createdAt" to com.google.firebase.Timestamp.now()
        )

        // 1. UID document
        try {
            val uidDocRef = firestore.collection(TRIAL_COLLECTION).document(user.uid)
            val uidSnap = uidDocRef.get().await()
            if (!uidSnap.exists()) {
                uidDocRef.set(baseData).await()
                Log.d(TAG, "Saved trial to businessTrials/${user.uid}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Save to businessTrials/${user.uid} failed: ${e.message}")
        }

        // 2. Device Fingerprint document
        try {
            val devDocRef = firestore.collection(TRIAL_COLLECTION).document("dev_$deviceFingerprint")
            val devSnap = devDocRef.get().await()
            if (!devSnap.exists()) {
                devDocRef.set(baseData).await()
                Log.d(TAG, "Saved trial to businessTrials/dev_$deviceFingerprint")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Save to businessTrials/dev_$deviceFingerprint failed: ${e.message}")
        }

        // 3. Email document
        val emailDocKey = emailKeyFor(user.email)
        if (emailDocKey != null) {
            try {
                val emailDocRef = firestore.collection(TRIAL_COLLECTION).document("email_$emailDocKey")
                val emailSnap = emailDocRef.get().await()
                if (!emailSnap.exists()) {
                    emailDocRef.set(baseData).await()
                    Log.d(TAG, "Saved trial to businessTrials/email_$emailDocKey")
                }
            } catch (e: Exception) {
                Log.d(TAG, "Save to businessTrials/email_$emailDocKey failed: ${e.message}")
            }
        }
    }

    // ---------------------------------------------------------------
    //  Internal Helpers
    // ---------------------------------------------------------------

    /**
     * Retrieves or sets the initial trial open time.
     * ALWAYS consults PersistentDeviceStorage first to detect if this device
     * was previously used before an uninstallation.
     */
    private fun getOrSetFirstOpenTime(): Long {
        var time = prefs.getLong(KEY_BUSINESS_TRIAL_START, 0L)
        if (time <= MIN_VALID_TIMESTAMP) {
            // Check physical device anchor first!
            val deviceAnchor = PersistentDeviceStorage.readAnchorTime(context)
            if (deviceAnchor != null && deviceAnchor > MIN_VALID_TIMESTAMP) {
                time = deviceAnchor
                Log.d(TAG, "Restored existing trial start from PersistentDeviceStorage: $time")
            } else {
                time = System.currentTimeMillis()
                Log.d(TAG, "Initialized default trial start: $time")
            }

            val expiry = time + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())
            val isExpired = System.currentTimeMillis() >= expiry

            prefs.edit()
                .putLong(KEY_BUSINESS_TRIAL_START, time)
                .putLong(KEY_BUSINESS_TRIAL_EXPIRY, expiry)
                .putBoolean(KEY_FORCE_EXPIRED, isExpired)
                .apply()

            PersistentDeviceStorage.saveAnchorTime(context, time)
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
        private const val MIN_VALID_TIMESTAMP = 1577836800000L // 01-Jan-2020
    }
}
