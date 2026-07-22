package com.aaryo.selfattendance.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.*
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * BackupReminderWorker — fires every Sunday evening at 8 PM.
 * Reminds user to backup their attendance data.
 */
class BackupReminderWorker(
    context: android.content.Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val WORK_NAME = "backupReminderWork"

        fun schedule(context: android.content.Context) {
            val delay = calculateDelayToNextSunday()
            val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(WORK_NAME)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: android.content.Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        private fun calculateDelayToNextSunday(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                if (before(now)) add(Calendar.WEEK_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }

    override fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    applicationContext, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return Result.success()
        }

        val prefs = PreferencesManager(applicationContext)
        if (!prefs.isBackupReminderEnabled) return Result.success()

        AppNotificationManager.showOfferNotification(
            context = applicationContext,
            title   = "💾 Weekly Backup Reminder",
            body    = "Aapka attendance data safe rakhein! Settings > Backup mein jaake abhi backup karein. Data loss se bachaiye."
        )

        return Result.success()
    }
}
