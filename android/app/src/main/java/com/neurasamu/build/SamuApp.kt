package com.neurasamu.build

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SamuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_SERVER,
                getString(R.string.notif_channel_name),
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            ch.description = getString(R.string.notif_channel_desc)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        lateinit var instance: SamuApp
            private set
        const val CHANNEL_SERVER = "samu_server"
    }
}
