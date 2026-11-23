package com.neurasamu.build.server

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.neurasamu.build.MainActivity
import com.neurasamu.build.R
import com.neurasamu.build.SamuApp
import com.neurasamu.build.data.ServerRunState

class SaMuServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                onExternalStopRequested?.invoke()
                stopForeground(STOP_FOREGROUND_REMOVE)
                releaseWakeLock()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val port = intent.getIntExtra(EXTRA_PORT, 8080)
                val status = intent.getStringExtra(EXTRA_STATUS) ?: "Online"
                val detail = intent.getStringExtra(EXTRA_DETAIL) ?: "Port $port"
                val nm = getSystemService(NotificationManager::class.java)
                nm?.notify(NOTIF_ID, buildNotification(port, status, detail))
                return START_STICKY
            }
            else -> {
                val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
                val status = intent?.getStringExtra(EXTRA_STATUS) ?: "Running"
                val detail = intent?.getStringExtra(EXTRA_DETAIL) ?: "Port $port"
                startForeground(NOTIF_ID, buildNotification(port, status, detail))
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SaMuLab::ServerWakeLock")
                wakeLock?.setReferenceCounted(false)
            }
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(12 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun buildNotification(port: Int, status: String, detail: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, SaMuServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SamuApp.CHANNEL_SERVER)
            .setContentTitle("SaMu Lab · $status")
            .setContentText("Port $port · $detail")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_START = "com.neurasamu.build.ACTION_START_SERVER"
        const val ACTION_UPDATE = "com.neurasamu.build.ACTION_UPDATE_SERVER"
        const val ACTION_STOP = "com.neurasamu.build.ACTION_STOP_SERVER"
        const val EXTRA_PORT = "extra_port"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_DETAIL = "extra_detail"

        @Volatile
        var onExternalStopRequested: (() -> Unit)? = null

        fun startOrUpdate(ctx: Context, port: Int, state: ServerRunState, modelName: String) {
            val statusLabel = when (state) {
                ServerRunState.STARTING -> "Starting..."
                ServerRunState.RUNNING -> "Online"
                ServerRunState.PROCESSING -> "Busy"
                ServerRunState.STOPPED -> "Stopped"
            }
            val i = Intent(ctx, SaMuServerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_STATUS, statusLabel)
                putExtra(EXTRA_DETAIL, modelName.ifBlank { "Ready" })
            }
            try {
                ContextCompat.startForegroundService(ctx, i)
            } catch (_: Exception) {}
        }

        fun stop(ctx: Context) {
            try {
                ctx.stopService(Intent(ctx, SaMuServerService::class.java))
            } catch (_: Exception) {}
        }
    }
}
