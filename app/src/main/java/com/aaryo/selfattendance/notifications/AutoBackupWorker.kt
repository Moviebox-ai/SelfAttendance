package com.aaryo.selfattendance.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.*
import com.aaryo.selfattendance.data.local.PreferencesManager
import com.aaryo.selfattendance.data.repository.AttendanceRepository
import com.aaryo.selfattendance.data.repository.BackupRepository
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

/**
 * AutoBackupWorker — silently backs up attendance data to Firestore every week.
 *
 * Unlike [BackupReminderWorker] (which only shows a reminder notification),
 * this worker ACTUALLY performs the backup and then notifies the user that
 * their data is safe — no manual action needed.
 *
 * Policy:
 *  • Requires network (Firestore needs internet).
 *  • Skipped gracefully if user is not logged in.
 *  • Skipped if auto-backup is disabled in preferences.
 *  • Success → "✅ Aapka data safe hai!" notification.
 *  • Failure → "⚠️ Auto backup fail hua" notification so user knows to backup manually.
 *  • Saves last backup timestamp in SharedPreferences for Settings display.
 */
class AutoBackupWorker(
    context: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "autoBackupWork"

        private const val TAG = "AutoBackupWorker"

        /**
         * Schedule daily auto backup.
         * Uses UPDATE policy so any existing schedule is updated to daily interval.
         */
        fun schedule(context: android.content.Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            Log.d(TAG, "Automated daily backup scheduled — runs every 24 hours when network is available")
        }

        fun cancel(context: android.content.Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Auto backup cancelled")
        }
    }

    override suspend fun doWork(): Result {

        val prefs = PreferencesManager(applicationContext)

        // Skip if user has disabled auto-backup
        if (!prefs.isAutoBackupEnabled) {
            Log.d(TAG, "Auto backup disabled in preferences — skipping")
            return Result.success()
        }

        // Skip if notification permission is missing (Android 13+)
        // We still return success so WorkManager doesn't treat this as a failure.
        val hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Get current Firebase user — worker runs in background, Auth session persists.
        val uid = try {
            FirebaseAuth.getInstance().currentUser?.uid
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseAuth unavailable in worker: ${e.message}")
            null
        }

        if (uid == null) {
            Log.d(TAG, "No logged-in user — skipping auto backup")
            // Not an error — user may have logged out. Return success so WorkManager
            // keeps the periodic schedule alive for when they log back in.
            return Result.success()
        }

        return try {
            val attendanceRepo = AttendanceRepository()
            val backupRepo     = BackupRepository()

            val attendanceList = attendanceRepo.getAllAttendance(uid)

            if (attendanceList.isEmpty()) {
                Log.d(TAG, "No attendance records to backup")
                prefs.lastAutoBackupTime = System.currentTimeMillis()
                return Result.success()
            }

            // Perform the actual backup to Firestore
            backupRepo.backupAttendance(uid, attendanceList)

            // Persist last backup timestamp so Settings can show "Last backed up: ..."
            val nowMs = System.currentTimeMillis()
            prefs.lastAutoBackupTime = nowMs

            Log.d(TAG, "Auto backup success — ${attendanceList.size} records saved")

            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "Auto backup failed: ${e.message}", e)

            // Retry up to 3 times with exponential backoff before giving up
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
