package com.aaryo.selfattendance.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.*
import com.aaryo.selfattendance.data.local.PreferencesManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    const val TAG = "ReminderScheduler"

    // Alarm Request Codes & Slot IDs
    const val REQ_8AM = 1001
    const val REQ_1330PM = 1002
    const val REQ_18PM = 1003
    const val REQ_CUSTOM = 1004

    const val SLOT_8AM = "8AM"
    const val SLOT_1330PM = "1330PM"
    const val SLOT_18PM = "18PM"
    const val SLOT_CUSTOM = "CUSTOM"

    // WorkManager Names (as secondary fallback)
    private const val WORK_8AM = "attendanceReminder_8AM"
    private const val WORK_1330PM = "attendanceReminder_1330PM"
    private const val WORK_18PM = "attendanceReminder_18PM"

    private const val OLD_WORK_NAME = "attendanceReminder"
    private const val HOURLY_WORK_NAME = "hourlyAttendanceOfferReminder"

    /**
     * Schedule all daily reminders (8:00 AM, 1:30 PM, 6:00 PM, plus custom time if set).
     * Uses AlarmManager exact alarms to fire at exact specified times even when app is closed.
     */
    fun schedule(context: Context) {
        val prefs = PreferencesManager(context)
        if (!prefs.isReminderEnabled) {
            cancel(context)
            return
        }

        // 1. Schedule exact alarms with AlarmManager
        scheduleExactAlarm(context, SLOT_8AM, REQ_8AM, 8, 0)
        scheduleExactAlarm(context, SLOT_1330PM, REQ_1330PM, 13, 30)
        scheduleExactAlarm(context, SLOT_18PM, REQ_18PM, 18, 0)

        // Custom time set in settings (if hour/minute configured)
        val customHour = prefs.reminderHour
        val customMin = prefs.reminderMinute
        if (customHour in 0..23 && customMin in 0..59) {
            if (!isStandardSlot(customHour, customMin)) {
                scheduleExactAlarm(context, SLOT_CUSTOM, REQ_CUSTOM, customHour, customMin)
            }
        }

        // 2. Schedule WorkManager as secondary fallback
        scheduleWorkManagerFallback(context)
    }

    /**
     * Overload for backwards compatibility.
     */
    fun schedule(context: Context, hour: Int, minute: Int) {
        schedule(context)
    }

    private fun isStandardSlot(hour: Int, min: Int): Boolean {
        return (hour == 8 && min == 0) || (hour == 13 && min == 30) || (hour == 18 && min == 0)
    }

    fun scheduleExactAlarm(context: Context, slot: String, requestCode: Int, hour: Int, minute: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_SLOT_ID, slot)
                putExtra(AlarmReceiver.EXTRA_HOUR, hour)
                putExtra(AlarmReceiver.EXTRA_MINUTE, minute)
                putExtra(AlarmReceiver.EXTRA_REQ_CODE, requestCode)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val triggerMillis = calculateNextTriggerMillis(hour, minute)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    // Fallback if exact alarm permission is not granted on Android 12+
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                } else {
                    try {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    } catch (se: SecurityException) {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                }
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }

            Log.d(TAG, "Exact alarm scheduled for $slot ($hour:$minute) in ${(triggerMillis - System.currentTimeMillis()) / 1000}s")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm for $slot: ${e.message}", e)
        }
    }

    private fun calculateNextTriggerMillis(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now) || timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return target.timeInMillis
    }

    fun cancel(context: Context) {
        cancelExactAlarms(context)
        cancelWorkManager(context)
    }

    private fun cancelExactAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val reqCodes = listOf(REQ_8AM, REQ_1330PM, REQ_18PM, REQ_CUSTOM)
        for (code in reqCodes) {
            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                code,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun scheduleWorkManagerFallback(context: Context) {
        cancelAllLegacyWork(context)
        enqueueDailyWorkSlot(context, WORK_8AM, 8, 0)
        enqueueDailyWorkSlot(context, WORK_1330PM, 13, 30)
        enqueueDailyWorkSlot(context, WORK_18PM, 18, 0)
    }

    private fun enqueueDailyWorkSlot(context: Context, workName: String, hour: Int, minute: Int) {
        val delayMs = calculateInitialDelay(hour, minute)
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<AttendanceReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .addTag(workName)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun cancelWorkManager(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(WORK_8AM)
        wm.cancelUniqueWork(WORK_1330PM)
        wm.cancelUniqueWork(WORK_18PM)
        cancelAllLegacyWork(context)
    }

    fun scheduleHourlyReminders(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HOURLY_WORK_NAME)
    }

    fun cancelHourlyReminders(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(HOURLY_WORK_NAME)
    }

    private fun cancelAllLegacyWork(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(OLD_WORK_NAME)
        wm.cancelUniqueWork(HOURLY_WORK_NAME)
        wm.cancelUniqueWork(HourlyReminderWorker.WORK_NAME)
        wm.cancelUniqueWork("streakAtRiskReminder")
        wm.cancelUniqueWork("weeklySummaryWork")
        wm.cancelUniqueWork("backupReminderWork")
    }

    private fun calculateInitialDelay(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return target.timeInMillis - now.timeInMillis
    }

    fun scheduleWeeklySummary(context: Context) = WeeklySummaryWorker.cancel(context)
    fun cancelWeeklySummary(context: Context) = WeeklySummaryWorker.cancel(context)

    fun scheduleBackupReminder(context: Context) = BackupReminderWorker.cancel(context)
    fun cancelBackupReminder(context: Context) = BackupReminderWorker.cancel(context)

    fun scheduleAutoBackup(context: Context) = AutoBackupWorker.schedule(context)
    fun cancelAutoBackup(context: Context) = AutoBackupWorker.cancel(context)

    fun scheduleStreakAtRisk(context: Context) = StreakAtRiskWorker.cancel(context)
    fun cancelStreakAtRisk(context: Context) = StreakAtRiskWorker.cancel(context)
}
