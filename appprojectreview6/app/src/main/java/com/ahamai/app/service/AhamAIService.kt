package com.ahamai.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ahamai.app.MainActivity

/**
 * Lightweight foreground service used purely to keep the app process alive while
 * a chat is streaming or an agent run is in progress and the user has minimized
 * the app.
 *
 * The actual work (SSE streaming, the agent tool loop) continues to run on the
 * coroutines that started it inside the app process — a foreground service just
 * prevents Android from killing that process, so the response is no longer cut
 * off when the app goes to the background. When the user returns, the existing
 * UI state picks up exactly where it left off.
 */
class AhamAIService : Service() {

    companion object {
        const val CHANNEL_ID = "ahamai_background"
        const val CHANNEL_NAME = "AhamAI Background Tasks"
        const val NOTIFICATION_ID = 1001

        const val ACTION_KEEP_ALIVE = "KEEP_ALIVE"
        const val ACTION_STOP = "STOP"

        /** Start (or refresh) the keep-alive foreground service. */
        fun startKeepAlive(context: Context) {
            val intent = Intent(context, AhamAIService::class.java).apply {
                action = ACTION_KEEP_ALIVE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
                // startForegroundService can throw if we're already in a restricted
                // state; the in-process coroutine keeps running regardless.
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, AhamAIService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
            }
            else -> {
                // ACTION_KEEP_ALIVE (default): just hold a foreground notification.
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        // If the OS kills us anyway, try to restart so the process keeps living
        // while work is pending.
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val count = RunningTasks.tasks.size
        val text = when {
            count <= 0 -> "Working in the background\u2026"
            count == 1 -> RunningTasks.tasks.first().title.ifBlank { "Working in the background\u2026" }
            else -> "$count tasks running in the background"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AhamAI")
.setSubText(null)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when AhamAI is processing in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        stopForegroundCompat()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The user swiped the app away. If work is still running in-process, keep the
        // foreground service alive so the OS doesn't kill the process mid-response.
        if (RunningTasks.hasAny) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification())
            } catch (_: Exception) {}
        } else {
            stopForegroundCompat()
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }
}
