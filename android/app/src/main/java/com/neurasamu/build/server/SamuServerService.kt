package com.neurasamu.build.server

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neurasamu.build.MainActivity
import com.neurasamu.build.R
import com.neurasamu.build.SamuApp

class SamuServerService : Service() {

    companion object {
        const val ACTION_STOP = "com.neurasamu.build.STOP_SERVER"
        const val EXTRA_PORT = "port"
        const val EXTRA_MODEL = "model"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            SamuRuntime.stopAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val port = intent?.getIntExtra(EXTRA_PORT, 8080) ?: 8080
        val model = intent?.getStringExtra(EXTRA_MODEL) ?: "no-model"
        startForeground(1, buildNotification(port, model))
        return START_STICKY
    }

    private fun buildNotification(port: Int, model: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SamuServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, SamuApp.CHANNEL_SERVER)
            .setContentTitle("SaMu Lab • running")
            .setContentText("Port $port • $model")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
    }
}
