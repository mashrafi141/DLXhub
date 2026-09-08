package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class DownloadForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "dlxhub_download_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.dlxhub.START"
        const val ACTION_UPDATE = "com.example.dlxhub.UPDATE"
        const val ACTION_STOP = "com.example.dlxhub.STOP"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_PROGRESS = "extra_progress"
        const val EXTRA_SPEED = "extra_speed"
        const val EXTRA_ETA = "extra_eta"

        fun start(context: Context, title: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent)
            } else context.startService(intent)
        }

        fun updateProgress(context: Context, title: String, progress: Int, speedText: String, etaText: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_PROGRESS, progress.coerceIn(0, 100))
                putExtra(EXTRA_SPEED, speedText)
                putExtra(EXTRA_ETA, etaText)
            }
            // The service is already foreground while this is called. This
            // avoids creating a second service instance or notification ID.
            context.startService(intent)
        }

        fun stop(context: Context) {
            // stopService guarantees onDestroy -> stopForeground(REMOVE), and
            // cancel() removes the same notification ID defensively.
            context.stopService(Intent(context, DownloadForegroundService::class.java))
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.cancel(NOTIFICATION_ID)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DLXhub:DownloadWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (wakeLock?.isHeld != true) wakeLock?.acquire(2 * 60 * 60 * 1000L)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading media"
                startForeground(NOTIFICATION_ID, buildNotification(title, 0, "", ""))
            }
            ACTION_UPDATE -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading media"
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0).coerceIn(0, 100)
                val speed = intent.getStringExtra(EXTRA_SPEED).orEmpty()
                val eta = intent.getStringExtra(EXTRA_ETA).orEmpty()
                notificationManager.notify(NOTIFICATION_ID, buildNotification(title, progress, speed, eta))
            }
            ACTION_STOP -> stopCleanly()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCleanly(restart = false)
        super.onDestroy()
    }

    private fun stopCleanly(restart: Boolean = false) {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        notificationManager.cancel(NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (restart) stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "DLXhub Media Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows live download progress, speed, and status"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(title: String, progress: Int, speed: String, eta: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subText = buildString {
            if (speed.isNotBlank()) append(speed)
            if (eta.isNotBlank()) { if (isNotEmpty()) append(" · "); append(eta) }
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DLXhub: $title")
            .setContentText(if (subText.isNotBlank()) subText else "$progress% completed")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress <= 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .build()
    }
}
