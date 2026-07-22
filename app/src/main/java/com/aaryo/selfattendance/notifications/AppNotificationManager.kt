package com.aaryo.selfattendance.notifications

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aaryo.selfattendance.MainActivity
import com.aaryo.selfattendance.R

/**
 * AppNotificationManager — centralised notification dispatch.
 *
 * Channels:
 *  • REMINDER_CHANNEL       — daily/hourly attendance reminders (deep links → Calendar).
 *  • MARK_ATTENDANCE_CHANNEL— "mark attendance" nudges (deep links → Dashboard/Home).
 *
 * Android 13+ POST_NOTIFICATIONS permission is checked before every notify()
 * call so we never crash on a missing permission.
 */
object AppNotificationManager {

    // ── Channel IDs ───────────────────────────────────────────────────────
    private const val REMINDER_CHANNEL_ID        = "attendance_reminder_v2"
    private const val MARK_ATTENDANCE_CHANNEL_ID = "mark_attendance_v1"

    // ── Notification IDs ──────────────────────────────────────────────────
    private const val NOTIF_ID_REMINDER          = 1001
    private const val NOTIF_ID_MARK_ATTENDANCE   = 4001

    // ── Deep-link extras ──────────────────────────────────────────────────
    private const val EXTRA_OPEN_SCREEN          = "open_screen"
    private const val SCREEN_CALENDAR            = "calendar"
    private const val SCREEN_DASHBOARD           = "dashboard"

    // ── Channel setup ─────────────────────────────────────────────────────

    /**
     * Create notification channels. Safe to call multiple times.
     * Must be called early in Application or Activity.onCreate().
     */
    fun setupChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Attendance Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Hourly nudges to mark your attendance"
            enableVibration(true)
            setShowBadge(true)
        }

        val markAttendanceChannel = NotificationChannel(
            MARK_ATTENDANCE_CHANNEL_ID,
            "Mark Attendance",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Quick tap to mark today's attendance from home screen"
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(markAttendanceChannel)
    }

    // ── Mark Attendance Notification (→ Dashboard) ────────────────────────

    /**
     * Show a high-priority "Mark Attendance" notification that deep-links
     * directly to the Dashboard (Home) screen.
     */
    fun showMarkAttendanceNotification(
        context: Context,
        title: String = "🏠 Mark Today's Attendance",
        body:  String = "Tap to open the home screen and mark your attendance in one tap!"
    ) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = buildPendingIntent(context, SCREEN_DASHBOARD, 104)

        val notification = NotificationCompat.Builder(context, MARK_ATTENDANCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_MARK_ATTENDANCE, notification)
    }

    // ── Reminder Notification (→ Calendar) ───────────────────────────────

    /**
     * Show a high-priority reminder notification that deep-links to Calendar.
     */
    fun showReminderNotification(context: Context) {
        showAttendanceReminderWithMessage(
            context = context,
            title   = "📅 Mark Today's Attendance",
            body    = "Stay on track — mark your attendance now!"
        )
    }

    /**
     * Show attendance reminder with custom title + body (used by HourlyReminderWorker).
     * Deep-links to the Calendar screen for detailed attendance entry.
     */
    fun showAttendanceReminderWithMessage(context: Context, title: String, body: String) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = buildPendingIntent(context, SCREEN_CALENDAR, 101)

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(body)
                    .setBigContentTitle(title)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, notification)
    }

    // ── Generic Offer / Promotional Notification ──────────────────────────

    /**
     * Show a promotional or offer notification with a custom title and body.
     * Used by BackupReminderWorker and similar workers.
     */
    fun showOfferNotification(context: Context, title: String, body: String) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = buildPendingIntent(context, SCREEN_DASHBOARD, 301)

        val notification = NotificationCompat.Builder(context, MARK_ATTENDANCE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body).setBigContentTitle(title))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_MARK_ATTENDANCE + 200, notification)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun buildPendingIntent(
        context:     Context,
        screen:      String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_SCREEN, screen)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
