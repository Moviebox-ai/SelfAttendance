package com.aaryo.selfattendance.notifications

import android.content.Context
import androidx.work.*
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * StreakAtRiskWorker — one-shot worker that fires at 8 PM if attendance is not yet marked.
 *
 * Scheduled fresh every day from DashboardScreen when:
 *  - The user opens the app and has NOT marked attendance today.
 * Cancelled automatically if:
 *  - The user marks attendance (PreferencesManager.lastMarkedDate == today).
 *
 * On fire, it checks lastMarkedDate one more time — if marked since scheduling,
 * the notification is silently skipped.
 */
class StreakAtRiskWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {

        private const val WORK_NAME = "streakAtRiskReminder"

        /**
         * Schedule a one-shot 8 PM "Streak at Risk" reminder for today.
         * Replaces any previously scheduled instance so it is always idempotent.
         */
        fun scheduleForToday(context: Context) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20)   // 8 PM
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If 8 PM already passed today, don't reschedule for tomorrow
            if (target.before(now)) return

            val delayMs = target.timeInMillis - now.timeInMillis

            val request = OneTimeWorkRequestBuilder<StreakAtRiskWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        /** Cancel if user marks attendance before 8 PM. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override fun doWork(): Result {
        return try {
            val prefs = PreferencesManager(applicationContext)
            // BUG FIX: Locale.getDefault() returns non-ASCII digits on Hindi/Bengali/Persian
            // devices — date string never matches prefs.lastMarkedDate, so the "Streak at Risk"
            // notification fires at 8 PM even when attendance was already marked.
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            // Skip if attendance already marked
            if (prefs.lastMarkedDate == today) return Result.success()

            AppNotificationManager.showAttendanceReminderWithMessage(
                context = applicationContext,
                title   = "🔥 Streak at Risk!",
                body    = "Aaj attendance mark nahi ki — streak toot jayegi! Abhi mark karein."
            )

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("StreakAtRiskWorker", "doWork failed: ${e.message}", e)
            Result.failure()
        }
    }
}
