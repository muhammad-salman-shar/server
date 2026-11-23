package com.neurasamu.build

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.File

class SamuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            PDFBoxResourceLoader.init(applicationContext)
        } catch (_: Throwable) {}

        ensureAppDirs()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_SERVER,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = getString(R.string.notif_channel_desc)
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(ch)
        }
    }

    private fun ensureAppDirs() {
        listOf(
            File(filesDir, "models"),
            File(filesDir, "media/images"),
            File(filesDir, "media/audio"),
            File(filesDir, "media/videos"),
            File(filesDir, "attachments")
        ).forEach { dir ->
            if (!dir.exists()) dir.mkdirs()
        }
    }

    companion object {
        lateinit var instance: SamuApp
            private set
        const val CHANNEL_SERVER = "samu_server"
    }
}
