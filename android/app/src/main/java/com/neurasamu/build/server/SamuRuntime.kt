package com.neurasamu.build.server

import android.content.Context
import com.neurasamu.build.model.SamuModel
import java.io.File

object SamuRuntime {
    @Volatile private var http: SamuHttpServer? = null
    @Volatile var port: Int = 8080
        private set

    @Volatile var activeModel: SamuModel? = null
        private set

    fun httpOrNull(): SamuHttpServer? = http

    fun http(ctx: Context): SamuHttpServer {
        http?.let { return it }
        synchronized(this) {
            http?.let { return it }
            val h = SamuHttpServer(ctx.applicationContext, port)
            http = h
            return h
        }
    }

    fun loadModel(ctx: Context, model: SamuModel) {
        val topology = CpuTopology.detect()
        android.util.Log.i(
            "SamuRuntime",
            "CPU topology: total=${topology.total}, big=${topology.bigCores}, " +
                "bigIds=${topology.bigCoreIds}, decodeThreads=${topology.decodeThreads}, " +
                "prefillThreads=${topology.prefillThreads}"
        )

        SamuEngine.loadModel(
            path = model.file.absolutePath,
            threads = topology.decodeThreads,       // decode: big cores only
            threadsBatch = topology.prefillThreads, // prefill: all cores
            ctx = model.ctx,
            gpuLayers = 0                            // Mali GPU — CPU only
        )
        activeModel = model
    }

    fun unloadModel() {
        try { SamuEngine.unload() } catch (_: Exception) {}
        activeModel = null
    }

    fun startServer(ctx: Context, model: SamuModel): String {
        val h = http(ctx)
        try { h.startSafe() } catch (_: Exception) {}
        h.displayNameOverride = model.displayName
        return "http://0.0.0.0:$port"
    }

    fun stopAll() {
        try { http?.stop() } catch (_: Exception) {}
        try { SamuEngine.unload() } catch (_: Exception) {}
        http = null
        activeModel = null
    }
}

/**
 * Reads big.LITTLE topology at runtime. Big cores only for decode (memory-bound),
 * all cores for prefill (compute-bound). Critical for MediaTek/Exynos where
 * using little cores in decode causes ~100x slowdown (barrier spin).
 */
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
                // fallback: use half the cores, min 2, max 4
                val t = (total / 2).coerceIn(2, 4)
                return CpuTopology(total, t, (0 until t).toList(), t, total)
            }

            val highest = maxFreqs.maxOf { it.second }
            // big cores = within 5% of highest freq
            val big = maxFreqs.filter { it.second >= (highest * 0.95).toLong() }
            val bigIds = big.map { it.first }.sorted()
            val bigCount = bigIds.size.coerceAtLeast(1)

            // decode: use exactly big-core count (memory-bandwidth bound)
            val decode = bigCount.coerceIn(1, 4)
            // prefill: use all cores (compute-bound)
            val prefill = total.coerceIn(decode, 8)

            return CpuTopology(total, bigCount, bigIds, decode, prefill)
        }
    }
}
