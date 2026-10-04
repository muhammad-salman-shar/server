package com.neurasamu.build.server

import android.content.Context
import com.neurasamu.build.data.ApiKeyStore
import com.neurasamu.build.data.AppSettings
import com.neurasamu.build.engine.SamuEngineRouter
import com.neurasamu.build.model.SamuModel
import java.io.File

object SamuRuntime {
    @Volatile private var http: SamuHttpServer? = null
    @Volatile var port: Int = 8080
        private set
    @Volatile var hostname: String = "0.0.0.0"
        private set

    @Volatile var activeModel: SamuModel? = null
        private set

    @Volatile var settings: AppSettings = AppSettings()

    fun httpOrNull(): SamuHttpServer? = http

    fun refreshFromSettings(ctx: Context) {
        settings = AppSettings.load(ctx)
        port = settings.port
        hostname = if (settings.lanEnabled) "0.0.0.0" else "127.0.0.1"
        val keys = ApiKeyStore.load(ctx)
            .filter { it.enabled && it.key.isNotBlank() }
            .map { it.key }.toSet()
        http?.validKeys = keys
    }

    fun http(ctx: Context): SamuHttpServer {
        http?.let {
            val keys = ApiKeyStore.load(ctx)
                .filter { k -> k.enabled && k.key.isNotBlank() }
                .map { k -> k.key }.toSet()
            it.validKeys = keys
            return it
        }
        synchronized(this) {
            http?.let { return it }
            settings = AppSettings.load(ctx)
            port = settings.port
            hostname = if (settings.lanEnabled) "0.0.0.0" else "127.0.0.1"
            val keys = ApiKeyStore.load(ctx)
                .filter { it.enabled && it.key.isNotBlank() }
                .map { it.key }.toSet()
            val h = SamuHttpServer(ctx.applicationContext, port, hostname)
            h.validKeys = keys
            http = h
            return h
        }
    }

    fun loadModel(ctx: Context, model: SamuModel) {
        val topology = CpuTopology.detect()
        android.util.Log.i(
            "SamuRuntime",
            "Loading ${model.displayName} format=${model.format} family=${model.family} " +
                "decode=${topology.decodeThreads} prefill=${topology.prefillThreads}"
        )
        SamuEngineRouter.load(
            ctx = ctx,
            modelFile = model.file,
            threads = topology.decodeThreads,
            threadsBatch = topology.prefillThreads,
            ctxSize = model.ctx,
            onToken = {}
        )
        activeModel = model
    }

    fun unloadModel() {
        try { SamuEngineRouter.unload() } catch (_: Exception) {}
        activeModel = null
    }

    fun startServer(ctx: Context, model: SamuModel): String {
        val h = http(ctx)
        try { h.startSafe() } catch (_: Exception) {}
        h.displayNameOverride = model.displayName
        val displayHost = if (hostname == "0.0.0.0") "0.0.0.0" else "127.0.0.1"
        return "http://$displayHost:$port"
    }

    fun stopAll() {
        try { http?.stop() } catch (_: Exception) {}
        try { SamuEngineRouter.unload() } catch (_: Exception) {}
        http = null
        activeModel = null
    }
}

data class CpuTopology(
    val total: Int,
    val bigCores: Int,
    val bigCoreIds: List<Int>,
    val decodeThreads: Int,
    val prefillThreads: Int
) {
    companion object {
        fun detect(): CpuTopology {
            val total = Runtime.getRuntime().availableProcessors()
            val maxFreqs = mutableListOf<Pair<Int, Long>>()
            for (i in 0 until total) {
                val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
                if (f.exists()) {
                    val khz = try { f.readText().trim().toLong() } catch (_: Exception) { 0L }
                    maxFreqs.add(i to khz)
                }
            }
            if (maxFreqs.isEmpty()) {
                val t = (total / 2).coerceIn(2, 4)
                return CpuTopology(total, t, (0 until t).toList(), t, total)
            }
            val highest = maxFreqs.maxOf { it.second }
            val big = maxFreqs.filter { it.second >= (highest * 0.95).toLong() }
            val bigIds = big.map { it.first }.sorted()
            val bigCount = bigIds.size.coerceAtLeast(1)
            val decode = bigCount.coerceIn(1, 4)
            val prefill = total.coerceIn(decode, 8)
            return CpuTopology(total, bigCount, bigIds, decode, prefill)
        }
    }
}
