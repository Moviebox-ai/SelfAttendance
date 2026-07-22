package com.aaryo.selfattendance.notifications

import androidx.work.Worker
import androidx.work.WorkerParameters
import android.content.Context
import com.aaryo.selfattendance.R
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * AttendanceReminderWorker — fires the daily attendance reminder notification.
 *
 * BUG FIX: Previously created its own "attendance_channel" notification channel
 * in parallel with AppNotificationManager's "attendance_reminder_v2" channel.
 * This caused two similar-named channels to appear in System Settings, so users
 * disabling one still received notifications from the other.
 *
 * Now delegates entirely to AppNotificationManager, which owns all channel
 * creation and notification dispatch. The duplicate channel and the hand-rolled
 * showNotification() / createNotificationChannel() methods have been removed.
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
            // Ensure the centralized channels exist (safe to call multiple times)
            AppNotificationManager.setupChannels(applicationContext)

            val prefs = PreferencesManager(applicationContext)

            // BUG FIX: Locale.getDefault() use karne se kuch locales (Hindi, Persian, etc.)
            // mein non-Arabic numerals return hote hain, jisse "yyyy-MM-dd" format mein
            // alag characters aate hain aur prefs.lastMarkedDate se match nahi hoti.
            // DB keys ke liye hamesha Locale.US use karo taaki format consistent rahe.
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date())

            val attendanceMarked = prefs.lastMarkedDate == today

            if (!attendanceMarked) {
                val title = applicationContext.getString(R.string.notification_reminder_title)
                // BUG FIX: The appended call-to-action was hardcoded English — not translatable.
                // Now uses the dedicated string resource so every locale sees the correct text.
                val body  = applicationContext.getString(R.string.notification_reminder_body) +
                        "\n\n" + applicationContext.getString(R.string.notification_reminder_streak_cta)

                AppNotificationManager.showAttendanceReminderWithMessage(
                    context = applicationContext,
                    title   = title,
                    body    = body
                )
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AttendanceReminderWorker", "doWork failed: ${e.message}", e)
            Result.failure()
        }
    }
}
