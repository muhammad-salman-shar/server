package com.neurasamu.build.data

import android.content.Context
import androidx.core.content.edit

data class AppSettings(
    val port: Int = 8080,
    val lanEnabled: Boolean = true,
    val autoStart: Boolean = false,
    val parallelSlots: Int = 1,
    val defaultTemperature: Float = 0.7f,
    val defaultTopP: Float = 0.9f,
    val defaultMaxTokens: Int = 512,
    val chatTemplate: String = "auto"
) {
    companion object {
        private const val PREFS = "samu_settings"
        private const val K_PORT = "port"
        private const val K_LAN = "lan"
        private const val K_AUTOSTART = "auto_start"
        private const val K_PARALLEL = "parallel"
        private const val K_TEMP = "temp"
        private const val K_TOPP = "top_p"
        private const val K_MAXTOK = "max_tokens"
        private const val K_TEMPLATE = "template"

        fun load(ctx: Context): AppSettings {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return AppSettings(
                port = p.getInt(K_PORT, 8080),
                lanEnabled = p.getBoolean(K_LAN, true),
                autoStart = p.getBoolean(K_AUTOSTART, false),
                parallelSlots = p.getInt(K_PARALLEL, 1),
                defaultTemperature = p.getFloat(K_TEMP, 0.7f),
                defaultTopP = p.getFloat(K_TOPP, 0.9f),
                defaultMaxTokens = p.getInt(K_MAXTOK, 512),
                chatTemplate = p.getString(K_TEMPLATE, "auto") ?: "auto"
            )
        }

        fun save(ctx: Context, s: AppSettings) {
            val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.edit {
                putInt(K_PORT, s.port)
                putBoolean(K_LAN, s.lanEnabled)
                putBoolean(K_AUTOSTART, s.autoStart)
                putInt(K_PARALLEL, s.parallelSlots)
                putFloat(K_TEMP, s.defaultTemperature)
                putFloat(K_TOPP, s.defaultTopP)
                putInt(K_MAXTOK, s.defaultMaxTokens)
                putString(K_TEMPLATE, s.chatTemplate)
            }
        }
    }
}
