package com.aaryo.selfattendance.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.aaryo.selfattendance.data.local.PreferencesManager

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received action: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = PreferencesManager(context)
            if (prefs.isReminderEnabled) {
                ReminderScheduler.schedule(context)
            } else {
                ReminderScheduler.cancel(context)
            }
            ReminderScheduler.cancelHourlyReminders(context)
        }
    }
}
