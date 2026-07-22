package com.aaryo.selfattendance.notifications

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val WORK_NAME        = "attendanceReminder"
    private const val HOURLY_WORK_NAME = "hourlyAttendanceOfferReminder"

    // ── Daily Reminder at Fixed Time ──────────────────────────────────────

    /**
     * Schedule daily reminder at fixed time (default: 9:00 PM).
     * This is the user-configured single daily reminder.
     */
    fun schedule(context: Context, hour: Int = 21, minute: Int = 0) {
        val delay = calculateInitialDelay(hour, minute)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<AttendanceReminderWorker>(
            1, TimeUnit.DAYS
        )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Cancel daily reminder (use in settings toggle).
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    // ── Hourly Reminder + Offers (every 60 minutes) ───────────────────────

    /**
     * Schedule periodic reminders every 60 minutes for:
     *  1. Attendance nudge (if not marked today)
     *  2. Rotating offer notifications
     *
     * WorkManager enforces a 15-minute minimum. On modern Android with Doze,
     * actual delivery is ~60–90 minutes — perfect for the 1–2 hour target.
     */
    fun scheduleHourlyReminders(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(false)
            .build()

        // Start first delivery 15 minutes after scheduling to avoid
        // spamming notifications immediately on install / reboot.
        val request = PeriodicWorkRequestBuilder<HourlyReminderWorker>(
            60, TimeUnit.MINUTES
        )
            .setInitialDelay(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(HOURLY_WORK_NAME)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            HOURLY_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Cancel hourly reminders (call when user disables in settings).
     */
    fun cancelHourlyReminders(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HOURLY_WORK_NAME)
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    /**
     * Calculate delay so notification fires at exact time daily.
     */
    private fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }

    // ── Weekly Summary (every Monday 9 AM) ───────────────────────────────

    fun scheduleWeeklySummary(context: android.content.Context) =
        WeeklySummaryWorker.schedule(context)

    fun cancelWeeklySummary(context: android.content.Context) =
        WeeklySummaryWorker.cancel(context)

    // ── Backup Reminder (every Sunday 8 PM) ──────────────────────────────

    fun scheduleBackupReminder(context: android.content.Context) =
        BackupReminderWorker.schedule(context)

    fun cancelBackupReminder(context: android.content.Context) =
        BackupReminderWorker.cancel(context)

    // ── Streak At Risk — one-shot 8 PM reminder if attendance not marked ──

    /**
     * Schedule a one-shot "Streak at Risk" notification for 8 PM today.
     * Call when user opens Dashboard and has NOT marked attendance yet.
     * Safe to call multiple times — uses REPLACE policy so it's idempotent.
     */
    fun scheduleStreakAtRisk(context: Context) =
        StreakAtRiskWorker.scheduleForToday(context)

    /**
     * Cancel the streak-at-risk reminder — call after user marks attendance.
     */
    fun cancelStreakAtRisk(context: Context) =
        StreakAtRiskWorker.cancel(context)

}