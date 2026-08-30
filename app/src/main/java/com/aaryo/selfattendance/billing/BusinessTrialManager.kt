package com.aaryo.selfattendance.billing

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Manages the 7-day Free Trial for Business Mode (Employer / Staff Management).
 *
 * IMPORTANT (anti-abuse fix):
 * The trial start time used to live ONLY in local SharedPreferences
 * (KEY_BUSINESS_TRIAL_START). Clearing the app's storage (Settings > Apps >
 * Clear Data) wiped that pref, so on next launch getOrSetFirstOpenTime()
 * stamped a brand-new "now" and the same Google account got a fresh 7-day
 * trial indefinitely — even though it signs back in with the SAME Firebase
 * uid (Google sign-in is deterministic per Google account).
 *
 * Fix: the authoritative trial start time is now a Firestore document keyed
 * by a normalized hash of the user's email, under /businessTrials/{emailKey}.
 * - The very first time a given account starts the trial, this device
 *   creates that document (server rules only allow CREATE, never UPDATE by
 *   the user — see firestore.rules).
 * - Every subsequent check (same device after data-clear, a reinstall, or
 *   even a fresh account created with the same Gmail after deleting the old
 *   one) reads the EXISTING document instead of creating a new one, so the
 *   original start time — and therefore the expiry date — never moves.
 * - Local SharedPreferences are kept purely as an offline cache so the UI
 *   can render instantly; they're overwritten with the server value on every
 *   successful sync, so local tampering (or a stale value left over from
 *   clearing data) can't extend the trial once the device is back online.
 */
class BusinessTrialManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    // ---------------------------------------------------------------
    //  Synchronous reads — used by Compose screens for instant paint.
    //  Backed by the local cache, which syncWithServer() keeps honest.
    // ---------------------------------------------------------------

    fun isTrialActive(): Boolean {
        val firstOpenTime = getOrSetFirstOpenTime()
        val currentTime = System.currentTimeMillis()
        val diffMillis = currentTime - firstOpenTime
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diffMillis)
        return daysElapsed < TRIAL_DURATION_DAYS
    }

    fun getRemainingDays(): Int {
        val firstOpenTime = getOrSetFirstOpenTime()
        val currentTime = System.currentTimeMillis()
        val diffMillis = currentTime - firstOpenTime
        val daysElapsed = TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
        val remaining = TRIAL_DURATION_DAYS - daysElapsed
        return if (remaining > 0) remaining else 0
    }

    fun getElapsedDays(): Int {
        val firstOpenTime = getOrSetFirstOpenTime()
        val currentTime = System.currentTimeMillis()
        val diffMillis = currentTime - firstOpenTime
        return TimeUnit.MILLISECONDS.toDays(diffMillis).toInt()
    }

    fun hasSeenWelcome(): Boolean {
        return prefs.getBoolean(KEY_WELCOME_SEEN, false)
    }

    fun markWelcomeSeen() {
        prefs.edit().putBoolean(KEY_WELCOME_SEEN, true).apply()
    }

    fun getTrialStartTime(): Long {
        return getOrSetFirstOpenTime()
    }

    fun getTrialExpiryTime(): Long {
        val startTime = getOrSetFirstOpenTime()
        return startTime + TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS.toLong())
    }

    fun getFormattedExpiryDate(): String {
        val expiryMs = getTrialExpiryTime()
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(expiryMs))
    }

    fun getFormattedStartDate(): String {
        val startMs = getTrialStartTime()
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(startMs))
    }

    // ---------------------------------------------------------------
    //  Server sync — call once per screen entry (LaunchedEffect(Unit))
    //  before reading the getters above, so the UI reflects the real,
    //  server-anchored trial window instead of a locally-reset one.
    // ---------------------------------------------------------------

    /**
     * Reconciles the local trial-start cache with the server record for the
     * signed-in account. Safe to call every time the trial screens are
     * opened — it only ever WRITES on the very first call for a given
     * account (creating the record), and only READS after that.
     *
     * Returns true if the local cache was successfully reconciled with the
     * server (online path). Returns false if there's no signed-in user or
     * the device is offline, in which case the existing local cache (or a
     * fresh "now" if this is the very first launch ever) keeps being used
     * until the next successful sync.
     */
    suspend fun syncWithServer(): Boolean {
        val user = auth.currentUser ?: return false
        val emailKey = emailKeyFor(user.email) ?: return false

        return try {
            val docRef = firestore.collection(TRIAL_COLLECTION).document(emailKey)

            val resolvedStartMillis = firestore.runTransaction { txn ->
                val snapshot = txn.get(docRef)
                if (snapshot.exists()) {
                    // Existing account (data-clear, reinstall, or even a
                    // recreated account with the same Gmail) — do NOT touch
                    // it. Just read the original start time.
                    snapshot.getTimestamp("trialStartTime")?.toDate()?.time
                        ?: snapshot.getLong("trialStartTime")
                        ?: System.currentTimeMillis()
                } else {
                    // Genuinely first time this account has ever requested
                    // Business trial access. Anchor it to server "now" —
                    // never trust whatever the local cache currently says.
                    val now = System.currentTimeMillis()
                    val data = hashMapOf(
                        "uid" to user.uid,
                        "email" to emailKey,
                        "trialStartTime" to com.google.firebase.Timestamp(now / 1000, 0),
                        "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    txn.set(docRef, data)
                    now
                }
            }.await()

            prefs.edit().putLong(KEY_BUSINESS_TRIAL_START, resolvedStartMillis).apply()
            true
        } catch (e: Exception) {
            Log.e("BusinessTrialManager", "Trial sync failed, using local cache", e)
            false
        }
    }

    // ---------------------------------------------------------------
    //  Internals
    // ---------------------------------------------------------------

    private fun getOrSetFirstOpenTime(): Long {
        var time = prefs.getLong(KEY_BUSINESS_TRIAL_START, 0L)
        if (time == 0L) {
            // Only hit on the very first read before any sync has ever
            // completed (e.g. no network yet). syncWithServer() will
            // correct this to the real server value as soon as it can.
            time = System.currentTimeMillis()
            prefs.edit().putLong(KEY_BUSINESS_TRIAL_START, time).apply()
        }
        return time
    }

    /**
     * Normalizes an email into a stable Firestore document key so the same
     * Google/Gmail account always maps to the same trial record, even
     * across sign-outs, data clears, or a deleted-and-recreated account.
     *
     * For gmail.com / googlemail.com addresses this also strips dots and
     * any "+tag" suffix from the local part, since Gmail treats
     * "john.doe@gmail.com", "johndoe@gmail.com" and
     * "johndoe+trial@gmail.com" as the same inbox — without this, someone
     * could mint "new" trial identities from one real Gmail account.
     */
    private fun emailKeyFor(rawEmail: String?): String? {
        val email = rawEmail?.trim()?.lowercase(java.util.Locale.ROOT)
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
        private const val PREFS_NAME = "business_trial_prefs"
        private const val KEY_BUSINESS_TRIAL_START = "business_trial_start_time"
        private const val KEY_WELCOME_SEEN = "business_trial_welcome_seen"
        private const val TRIAL_COLLECTION = "businessTrials"
        const val TRIAL_DURATION_DAYS = 7
    }
}
