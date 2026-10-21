package com.doublesail.scheduleboard.startup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.doublesail.scheduleboard.MainActivity
import com.doublesail.scheduleboard.R

object StartupNotifications {
    private const val Channel = "board_startup"
    private const val NotificationId = 1001
    const val ExtraBootLaunch = "launched_at_boot"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    Channel,
                    context.getString(R.string.channel_startup),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun launchIntent(context: Context) = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        .putExtra(ExtraBootLaunch, true)

    fun show(context: Context) {
        if (!canNotify(context)) return
        createChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, Channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.startup_notification_title))
            .setContentText(context.getString(R.string.startup_notification_body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        // A permission can be revoked between the check and notify().
        try {
            NotificationManagerCompat.from(context).notify(NotificationId, notification)
        } catch (_: SecurityException) {
            // The launcher / Magisk startup routes remain available.
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NotificationId)
    }
}
