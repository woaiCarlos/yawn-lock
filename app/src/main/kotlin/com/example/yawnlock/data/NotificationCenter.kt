package com.example.yawnlock.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.yawnlock.MainActivity
import com.example.yawnlock.R
import com.example.yawnlock.domain.DurationFormatter
import com.example.yawnlock.service.StopReceiver

object NotificationCenter {
    const val CHANNEL_ID = "yawn_lock_countdown"
    const val NOTIF_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = context.getString(R.string.notification_channel_desc) }
                mgr.createNotificationChannel(channel)
            }
        }
    }

    fun build(context: Context, remainingMs: Long, isPaused: Boolean): Notification {
        val openAppIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, StopReceiver::class.java).setAction(StopReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (isPaused) "已暂停" else "剩余 ${DurationFormatter.toMmSs(remainingMs)}"
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(title)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_stop, context.getString(R.string.notification_stop), stopIntent)
            .build()
    }

    fun update(context: Context, remainingMs: Long, isPaused: Boolean) {
        val notif = build(context, remainingMs, isPaused)
        (context.getSystemService(NotificationManager::class.java))
            .notify(NOTIF_ID, notif)
    }

    fun cancel(context: Context) {
        (context.getSystemService(NotificationManager::class.java))
            .cancel(NOTIF_ID)
    }
}
