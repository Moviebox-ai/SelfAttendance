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
 *  • REMINDER_CHANNEL  — daily attendance reminders (deep links → Calendar).
 *  • REWARD_CHANNEL    — reward earned after rewarded ad (deep links → Wallet).
 *
 * Android 13+ POST_NOTIFICATIONS permission is checked before every notify()
 * call so we never crash on a missing permission.
 *
 * Deep-link strategy: MainActivity reads the "open_screen" extra and routes
 * the NavGraph to the correct screen on launch.
 */
object AppNotificationManager {

    // ── Channel IDs ───────────────────────────────────────────────────────
    private const val REMINDER_CHANNEL_ID   = "attendance_reminder_v2"
    private const val REWARD_CHANNEL_ID     = "reward_earned_v2"

    // ── Notification IDs ──────────────────────────────────────────────────
    private const val NOTIF_ID_REMINDER     = 1001
    private const val NOTIF_ID_REWARD       = 2001

    // ── Deep-link extras ──────────────────────────────────────────────────
    private const val EXTRA_OPEN_SCREEN     = "open_screen"
    private const val SCREEN_CALENDAR       = "calendar"
    private const val SCREEN_WALLET         = "wallet"

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
            description = "Daily nudges to mark your attendance"
            enableVibration(true)
            setShowBadge(true)
        }

        val rewardChannel = NotificationChannel(
            REWARD_CHANNEL_ID,
            "Rewards",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Coin rewards from ads and streaks"
            enableVibration(true)
            setShowBadge(true)
        }

        manager.createNotificationChannel(reminderChannel)
        manager.createNotificationChannel(rewardChannel)
    }

    // ── Reminder Notification ─────────────────────────────────────────────

    /**
     * Show a high-priority reminder notification that deep-links to the
     * Calendar screen.
     */
    fun showReminderNotification(context: Context) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = buildPendingIntent(
            context  = context,
            screen   = SCREEN_CALENDAR,
            requestCode = 101
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("📅 Mark Today's Attendance")
            .setContentText("Stay on track — mark your attendance now!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Don't break your streak! 🔥 Mark today's attendance to keep your " +
                        "records accurate and salary calculations up to date."
                    )
                    .setBigContentTitle("📅 Attendance Reminder")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_REMINDER, notification)
    }

    // ── Reward Notification ───────────────────────────────────────────────

    /**
     * Show a reward notification after a rewarded ad completes.
     * Deep-links to the Wallet screen so the user can see their updated balance.
     *
     * @param coins Number of coins earned.
     */
    fun showRewardNotification(context: Context, coins: Int) {
        if (!hasNotificationPermission(context)) return

        val pendingIntent = buildPendingIntent(
            context     = context,
            screen      = SCREEN_WALLET,
            requestCode = 102
        )

        val notification = NotificationCompat.Builder(context, REWARD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_coin)
            .setContentTitle("🎉 You Earned $coins Coins!")
            .setContentText("Your reward has been added to your wallet 💰")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "Awesome! 🎁 You just earned $coins coins by watching an ad. " +
                        "Check your wallet to see your updated balance and unlock " +
                        "premium features!"
                    )
                    .setBigContentTitle("🎉 Reward Earned!")
            )
            .addAction(
                R.drawable.ic_coin,
                "Open Wallet",
                pendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_REWARD, notification)
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
