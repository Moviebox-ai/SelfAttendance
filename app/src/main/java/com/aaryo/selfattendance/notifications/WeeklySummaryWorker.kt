package com.aaryo.selfattendance.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.*
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.AuthRepository
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.runBlocking
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WeeklySummaryWorker — fires every Monday morning at 9 AM.
 * Shows last week's attendance summary as a notification.
 */
class WeeklySummaryWorker(
    context: android.content.Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val WORK_NAME = "weeklySummaryWork"

        fun schedule(context: android.content.Context) {
            val delay = calculateDelayToNextMonday()
            val request = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
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

        private fun calculateDelayToNextMonday(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 9)
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

        try {
            val uid = runBlocking { AuthRepository().currentUser()?.uid } ?: return Result.success()
            val allRecords = runBlocking { AttendanceRepository().getAllAttendance(uid) }

            // Get last week's records
            val cal = Calendar.getInstance()
            cal.add(Calendar.WEEK_OF_YEAR, -1)
            val weekYear = cal.get(Calendar.YEAR)
            val weekNum  = cal.get(Calendar.WEEK_OF_YEAR)

            val lastWeekRecords = allRecords.filter { record ->
                try {
                    val d = java.time.LocalDate.parse(record.date)
                    val rc = Calendar.getInstance().apply {
                        set(d.year, d.monthValue - 1, d.dayOfMonth)
                    }
                    rc.get(Calendar.YEAR) == weekYear &&
                    rc.get(Calendar.WEEK_OF_YEAR) == weekNum
                } catch (_: Exception) { false }
            }

            if (lastWeekRecords.isEmpty()) return Result.success()

            val present = lastWeekRecords.count { it.status == "PRESENT" }
            val half    = lastWeekRecords.count { it.status == "HALF" || it.status == "HALF_DAY" }
            val absent  = lastWeekRecords.count { it.status == "ABSENT" }
            val total   = lastWeekRecords.size

            val emoji = when {
                present == total -> "🏆"
                absent == 0      -> "✅"
                absent <= 1      -> "👍"
                else             -> "📊"
            }

            val title = "$emoji Last Week's Attendance Summary"
            val body  = "Present: $present | Half Day: $half | Absent: $absent\n" +
                        "Attendance Rate: ${((present + half * 0.5) / total * 100).toInt()}%"

            AppNotificationManager.showAttendanceReminderWithMessage(
                applicationContext, title, body
            )
        } catch (e: Exception) {
            FirebaseCrashlytics.getInstance().recordException(e)
        }

        return Result.success()
    }
}
