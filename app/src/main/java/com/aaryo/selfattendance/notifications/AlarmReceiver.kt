package com.aaryo.selfattendance.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * AlarmReceiver — handles exact time notification triggers from AlarmManager.
 * Fires notifications AT THE EXACT SPECIFIED TIME even if the app is killed or
 * the device is in Doze mode.
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "AlarmReceiver"
        const val EXTRA_SLOT_ID = "extra_slot_id"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        const val EXTRA_REQ_CODE = "extra_req_code"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)

        // 1. Check if reminders are enabled
        if (!prefs.isReminderEnabled) {
            Log.d(TAG, "Reminders disabled in preferences — skipping alarm")
            return
        }

        val slot = intent.getStringExtra(EXTRA_SLOT_ID) ?: ReminderScheduler.SLOT_8AM
        val hour = intent.getIntExtra(EXTRA_HOUR, 8)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val reqCode = intent.getIntExtra(EXTRA_REQ_CODE, ReminderScheduler.REQ_8AM)

        Log.d(TAG, "Alarm triggered for slot: $slot ($hour:$minute)")

        // 2. Check if attendance already marked today
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val attendanceMarked = prefs.lastMarkedDate == today

        if (!attendanceMarked) {
            AppNotificationManager.setupChannels(context)

            val (title, body) = when (slot) {
                ReminderScheduler.SLOT_8AM -> {
                    Pair(
                        "🌅 Morning Attendance Check",
                        "Mark your morning attendance to keep your daily streak and salary records up to date. 📋"
                    )
                }
                ReminderScheduler.SLOT_1330PM -> {
                    Pair(
                        "☀️ Afternoon Reminder",
                        "Have you marked today's attendance? Please check and update your record now. ⏱️"
                    )
                }
                ReminderScheduler.SLOT_18PM -> {
                    Pair(
                        "🌆 End of Day Reminder",
                        "Don't forget to mark your attendance before the day ends! 🔥"
                    )
                }
                else -> {
                    Pair(
                        "⏰ Attendance Reminder",
                        "Don't forget to mark today's attendance! Tap to update now. 📋"
                    )
                }
            }

            AppNotificationManager.showMarkAttendanceNotification(
                context = context,
                title = title,
                body = body
            )
        } else {
            Log.d(TAG, "Attendance already marked today ($today) — skipping notification")
        }

        // 3. Immediately reschedule the exact alarm for tomorrow at the same time
        ReminderScheduler.scheduleExactAlarm(context, slot, reqCode, hour, minute)
    }
}
