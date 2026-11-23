package com.neurasamu.build.engine

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.neurasamu.build.server.SamuEngine
import java.io.File

data class HardwareProfile(
    val cpuCores: Int,
    val bigCores: Int,
    val totalRamGb: Double,
    val availRamGb: Double,
    val hasVulkan: Boolean,
    val hasOpenCl: Boolean,
    val nativeGpuLabel: String,
    val tierLabel: String,
    val recommendedThreads: Int,
    val recommendedBatchThreads: Int,
    val recommendedGpuLayers: Int,
    val recommendedContext: Int
)

object HardwareDetector {
    @Volatile
    private var cached: HardwareProfile? = null

    fun detect(ctx: Context, forceRefresh: Boolean = false): HardwareProfile {
        if (!forceRefresh) cached?.let { return it }

        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val bigCores = detectBigCores(cores)

        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mi)
        val totalRamGb = if (mi.totalMem > 0) mi.totalMem / (1024.0 * 1024.0 * 1024.0) else 4.0
        val availRamGb = if (mi.availMem > 0) mi.availMem / (1024.0 * 1024.0 * 1024.0) else 2.0

        val pm = ctx.packageManager
        val hasVulkan = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            pm.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL)
        val hasOpenCl = listOf(
            "/system/vendor/lib64/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so"
        ).any { File(it).exists() }

        val nativeGpu = try {
            SamuEngine.detectGpu()
        } catch (_: Throwable) {
            "cpu-only"
        }

        val gpuActive = nativeGpu.isNotBlank() &&
            !nativeGpu.equals("cpu-only", ignoreCase = true) &&
            !nativeGpu.startsWith("err", ignoreCase = true)

        val recThreads = bigCores.coerceIn(2, 6).coerceAtMost(cores)
        val recBatch = cores.coerceIn(recThreads, 8)
        val recGpuLayers = if (gpuActive) 99 else 0
        val recCtx = when {
            totalRamGb >= 11.0 -> 16384
            totalRamGb >= 7.0 -> 8192
            totalRamGb >= 5.0 -> 4096
            else -> 2048
        }

        val ramInt = kotlin.math.ceil(totalRamGb).toInt().coerceAtLeast(1)
        val tierLabel = when {
            gpuActive -> "Vulkan GPU (${cores}T / ${ramInt}GB RAM)"
            totalRamGb >= 10.0 && cores >= 8 -> "High-End (${cores}T / ${ramInt}GB RAM)"
            totalRamGb >= 6.0 -> "Standard (${cores}T / ${ramInt}GB RAM)"
            else -> "Compact (${cores}T / ${ramInt}GB RAM)"
        }

        val profile = HardwareProfile(
            cpuCores = cores,
            bigCores = bigCores,
            totalRamGb = totalRamGb,
            availRamGb = availRamGb,
            hasVulkan = hasVulkan,
            hasOpenCl = hasOpenCl,
            nativeGpuLabel = nativeGpu,
            tierLabel = tierLabel,
            recommendedThreads = recThreads,
            recommendedBatchThreads = recBatch,
            recommendedGpuLayers = recGpuLayers,
            recommendedContext = recCtx
        )
        cached = profile
        return profile
    }

    private fun detectBigCores(total: Int): Int {
        val freqs = mutableListOf<Long>()
        for (i in 0 until total) {
            val f = File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq")
            if (f.exists()) {
                val khz = try { f.readText().trim().toLong() } catch (_: Exception) { 0L }
                if (khz > 0L) freqs.add(khz)
            }
        }
        if (freqs.isEmpty()) return (total / 2).coerceIn(2, 4)
        val highest = freqs.max()
        return freqs.count { it >= (highest * 0.90).toLong() }.coerceIn(1, total)
    }
}
