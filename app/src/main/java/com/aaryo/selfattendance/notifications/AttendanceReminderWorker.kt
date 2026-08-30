package com.aaryo.selfattendance.notifications

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * AttendanceReminderWorker — fires daily attendance reminder notifications
 * strictly at 8:00 AM, 1:30 PM, and 6:00 PM.
 */
class AttendanceReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val NOTIFICATION_ID = 1001
    }

    override fun doWork(): Result {
        return try {
            AppNotificationManager.setupChannels(applicationContext)

            val prefs = PreferencesManager(applicationContext)

            // Respect user settings
            if (!prefs.isReminderEnabled) return Result.success()

            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val attendanceMarked = prefs.lastMarkedDate == today

            // If attendance is already marked today, skip notification
            if (attendanceMarked) return Result.success()

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)

            val (title, body) = when {
                currentHour in 6..11 -> {
                    Pair(
                        "🌅 Morning Attendance Check",
                        "Mark your morning attendance to keep your daily streak and salary records up to date. 📋"
                    )
                }
                currentHour in 12..16 -> {
                    Pair(
                        "☀️ Afternoon Reminder",
                        "Have you marked today's attendance? Please check and update your record now. ⏱️"
                    )
                }
                else -> {
                    Pair(
                        "🌆 End of Day Reminder",
                        "Don't forget to mark your attendance before the day ends! 🔥"
                    )
                }
            }

            AppNotificationManager.showMarkAttendanceNotification(
                context = applicationContext,
                title   = title,
                body    = body
            )

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AttendanceReminderWorker", "doWork failed: ${e.message}", e)
            Result.failure()
        }
    }
}

