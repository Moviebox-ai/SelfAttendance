package com.aaryo.selfattendance.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * HourlyReminderWorker — fires every 1–2 hours.
 *
 * Logic:
 *  1. If attendance not marked today → show "Mark Attendance" notification
 *     that deep-links directly to the Dashboard (Home) screen so the user
 *     can mark attendance with one tap.
 *
 * WorkManager will call this every ~90 minutes (minimum 15 min on API 23+,
 * we use a 60-min interval so actual delivery is ~60–90 min due to doze).
 */
class HourlyReminderWorker(
    context: android.content.Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val WORK_NAME = "hourlyReminderWork"

        // Rotating "Mark Attendance" messages — deep-link → Dashboard (Home screen)
        private val MARK_ATTENDANCE_MESSAGES = listOf(
            Pair("🏠 Mark Today's Attendance", "Home screen kholen aur ek tap mein aaj ki attendance mark karein! 📋"),
            Pair("⏰ Attendance Abhi Tak Pending!", "Aaj mark nahi kiya? Home screen par jaayein aur abhi update karein."),
            Pair("✅ Quick Attendance Check", "Ek second mein attendance mark karein — Home screen tap karein! 🔥"),
            Pair("🗓️ Aaj Bhi Present Ho?", "Toh mark zaroor karein! Home screen par tap karein — salary sahi rahegi."),
            Pair("🔔 Don't Break Your Streak!", "Attendance mark karein aur apna streak banaye rakhein. Home kholen! 💪")
        )
    }

    override fun doWork(): Result {
        return try {
            // Check notification permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return Result.success()
                }
            }

            val prefs = PreferencesManager(applicationContext)

            // Respect user preference — skip all notifications if hourly reminders are disabled
            if (!prefs.isHourlyReminderEnabled) return Result.success()

            // BUG FIX: Locale.getDefault() returns non-ASCII digits on Hindi/Bengali/Persian
            // devices, causing date strings to never match prefs.lastMarkedDate (always Locale.US).
            // Result: reminders kept firing even after attendance was already marked.
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val attendanceMarked = prefs.lastMarkedDate == today

            // Pick rotating message index based on current hour
            val hourIndex = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            // Show "Mark Attendance" notification (→ Dashboard) if not marked today
            if (!attendanceMarked) {
                val msg = MARK_ATTENDANCE_MESSAGES[hourIndex % MARK_ATTENDANCE_MESSAGES.size]
                AppNotificationManager.showMarkAttendanceNotification(
                    context = applicationContext,
                    title   = msg.first,
                    body    = msg.second
                )
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("HourlyReminderWorker", "doWork failed: ${e.message}", e)
            Result.failure()
        }
    }
}
