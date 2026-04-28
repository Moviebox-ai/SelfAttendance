package com.aaryo.selfattendance.notifications

import android.content.Context
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    private const val WORK_NAME = "attendanceReminder"

    /**
     * Schedule daily reminder at fixed time (default: 9:00 PM)
     */
    fun schedule(context: Context, hour: Int = 21, minute: Int = 0) {

        val delay = calculateInitialDelay(hour, minute)

        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request =
            PeriodicWorkRequestBuilder<AttendanceReminderWorker>(
                1, TimeUnit.DAYS
            )
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE, // ✅ safe replace
            request
        )
    }

    /**
     * Cancel reminder (use in settings toggle)
     */
    fun cancel(context: Context) {
        WorkManager.getInstance(context)
            .cancelUniqueWork(WORK_NAME)
    }

    /**
     * Calculate delay so notification fires at exact time daily
     */
    private fun calculateInitialDelay(hour: Int, minute: Int): Long {

        val now = Calendar.getInstance()

        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)

            // If time already passed → next day
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        return target.timeInMillis - now.timeInMillis
    }
}
