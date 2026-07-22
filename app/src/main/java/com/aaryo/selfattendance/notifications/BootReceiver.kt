package com.aaryo.selfattendance.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aaryo.selfattendance.data.local.PreferencesManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = PreferencesManager(context)

        // Reschedule the user-configured daily reminder
        if (prefs.isReminderEnabled) {
            ReminderScheduler.schedule(context, prefs.reminderHour, prefs.reminderMinute)
        }

        // Reschedule hourly reminders + offers
        if (prefs.isHourlyReminderEnabled) {
            ReminderScheduler.scheduleHourlyReminders(context)
        }

        // Reschedule weekly summary
        if (prefs.isWeeklySummaryEnabled) {
            ReminderScheduler.scheduleWeeklySummary(context)
        }

        // Reschedule backup reminder
        if (prefs.isBackupReminderEnabled) {
            ReminderScheduler.scheduleBackupReminder(context)
        }
    }
}
