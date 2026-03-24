package com.aaryo.selfattendance.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {

            val reminderWork =
                PeriodicWorkRequestBuilder<AttendanceReminderWorker>(
                    24,
                    TimeUnit.HOURS
                ).build()

            WorkManager
                .getInstance(context)
                .enqueueUniquePeriodicWork(
                    "attendanceReminder",
                    ExistingPeriodicWorkPolicy.KEEP,
                    reminderWork
                )
        }
    }
}