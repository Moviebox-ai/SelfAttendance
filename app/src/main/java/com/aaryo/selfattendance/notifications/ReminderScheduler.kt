package com.aaryo.selfattendance.notifications

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun schedule(context: Context) {

        val constraints =
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

        val request =
            PeriodicWorkRequestBuilder<AttendanceReminderWorker>(
                1,
                TimeUnit.DAYS
            )
                .setConstraints(constraints)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "attendanceReminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }
}