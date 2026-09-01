package com.aaryo.selfattendance.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManager
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Milestones representing positive user engagement moments suitable for requesting a review.
 */
enum class ReviewMilestone {
    ATTENDANCE_MARKED,
    SALARY_SLIP_GENERATED,
    STREAK_ACHIEVED,
    MANUAL_SETTINGS
}

/**
 * InAppReviewManager — Google Play In-App Review API Integration.
 *
 * Implements Google Play's In-App Review API adhering to official UX guidelines:
 * 1. Prompts at natural high-satisfaction milestones (attendance streak, salary slip creation).
 * 2. Strict frequency capping to prevent prompting too often (minimum 14-day interval).
 * 3. Graceful fallback to browser/Play Store listing when launched manually from Settings.
 * 4. Safe non-blocking execution with no impact on core attendance workflow.
 */
class InAppReviewManager(private val context: Context) {

    private val reviewManager: ReviewManager by lazy {
        ReviewManagerFactory.create(context.applicationContext)
    }

    private val prefs: PreferencesManager by lazy {
        PreferencesManager(context.applicationContext)
    }

    companion object {
        private const val TAG = "InAppReviewManager"

        /** Minimum attendance saves before the first automatic review prompt is triggered. */
        private const val MIN_ATTENDANCE_MARKS_FOR_REVIEW = 5

        /** Minimum cooldown between automated in-app review prompts (14 days in milliseconds). */
        private const val REVIEW_COOLDOWN_MS = 14L * 24 * 60 * 60 * 1000

        @Volatile
        private var instance: InAppReviewManager? = null

        fun getInstance(context: Context): InAppReviewManager {
            return instance ?: synchronized(this) {
                instance ?: InAppReviewManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Evaluates whether the current moment qualifies for an automated in-app review prompt.
     * If conditions are met, requests and launches the Google Play review dialog flow.
     */
    fun triggerSmartReviewIfAppropriate(
        activity: Activity,
        milestone: ReviewMilestone,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val now = System.currentTimeMillis()
        val lastPrompt = prefs.lastReviewPromptTimeMs
        val isCooldownActive = (now - lastPrompt) < REVIEW_COOLDOWN_MS

        val shouldPrompt = when (milestone) {
            ReviewMilestone.ATTENDANCE_MARKED -> {
                val count = prefs.incrementAttendanceMarksCount()
                // Prompt on 5th, 15th, 30th, 60th save if cooldown has elapsed
                !isCooldownActive && (count == MIN_ATTENDANCE_MARKS_FOR_REVIEW || count == 15 || count % 25 == 0)
            }
            ReviewMilestone.SALARY_SLIP_GENERATED -> {
                val count = prefs.incrementSalarySlipGeneratedCount()
                // Prompt on 1st or subsequent generated salary slips if cooldown has elapsed
                !isCooldownActive && (count == 1 || count % 5 == 0)
            }
            ReviewMilestone.STREAK_ACHIEVED -> {
                !isCooldownActive
            }
            ReviewMilestone.MANUAL_SETTINGS -> {
                true // Explicit user intent from settings -> always proceed
            }
        }

        if (!shouldPrompt) {
            onComplete?.invoke(false)
            return
        }

        Log.d(TAG, "Triggering in-app review for milestone: $milestone")
        requestReviewFlow(activity) { success ->
            if (success) {
                prefs.lastReviewPromptTimeMs = System.currentTimeMillis()
                prefs.reviewPromptCount = prefs.reviewPromptCount + 1
            }
            onComplete?.invoke(success)
        }
    }

    /**
     * Direct launch of the Google Play In-App Review Flow.
     * If [fallbackToPlayStoreIfUnavailable] is true (useful for "Rate Us" button in Settings),
     * opens the Play Store app listing directly if the review dialog cannot be displayed.
     */
    fun requestReviewFlow(
        activity: Activity,
        fallbackToPlayStoreIfUnavailable: Boolean = false,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        try {
            val request = reviewManager.requestReviewFlow()
            request.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val reviewInfo: ReviewInfo = task.result
                    val flow = reviewManager.launchReviewFlow(activity, reviewInfo)
                    flow.addOnCompleteListener { flowTask ->
                        // The flow has finished. Note: Google API doesn't indicate whether
                        // the user gave a review or dismissed it, to protect user privacy.
                        Log.d(TAG, "In-app review flow completed successfully.")
                        prefs.hasUserReviewedApp = true
                        onComplete?.invoke(true)
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "launchReviewFlow failed", e)
                        if (fallbackToPlayStoreIfUnavailable) {
                            openPlayStoreListing(activity)
                        }
                        onComplete?.invoke(false)
                    }
                } else {
                    Log.w(TAG, "requestReviewFlow unsuccessful: ${task.exception?.message}")
                    if (fallbackToPlayStoreIfUnavailable) {
                        openPlayStoreListing(activity)
                    }
                    onComplete?.invoke(false)
                }
            }.addOnFailureListener { e ->
                Log.e(TAG, "requestReviewFlow listener failure", e)
                if (fallbackToPlayStoreIfUnavailable) {
                    openPlayStoreListing(activity)
                }
                onComplete?.invoke(false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception initiating in-app review", e)
            if (fallbackToPlayStoreIfUnavailable) {
                openPlayStoreListing(activity)
            }
            onComplete?.invoke(false)
        }
    }

    /**
     * Opens the Play Store app listing page directly.
     * Uses the market:// scheme first with a fallback to the web URL.
     */
    fun openPlayStoreListing(context: Context) {
        val packageName = context.packageName
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
            }
            context.startActivity(marketIntent)
        } catch (e: ActivityNotFoundException) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }
}
