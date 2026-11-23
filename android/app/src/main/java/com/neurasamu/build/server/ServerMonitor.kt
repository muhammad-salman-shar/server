package com.neurasamu.build.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class LogLevel(val tag: String) {
    INFO("INFO"),
    ACTIVE("ACTIVE"),
    OK("OK"),
    ERR("ERR")
}

data class MonitorLogEntry(
    val id: Long,
    val timestamp: String,
    val level: LogLevel,
    val message: String
)

data class MonitorSnapshot(
    val activeRequests: Int = 0,
    val totalRequests: Long = 0L,
    val liveTokensPerSec: Double = 0.0,
    val totalTokens: Long = 0L,
    val logs: List<MonitorLogEntry> = emptyList()
)

object ServerMonitor {
    private const val MAX_LOGS = 60

    private val _snapshot = MutableStateFlow(MonitorSnapshot())
    val snapshot: StateFlow<MonitorSnapshot> = _snapshot.asStateFlow()

    private val activeCounter = AtomicInteger(0)
    private val requestCounter = AtomicLong(0L)
    private val tokenCounter = AtomicLong(0L)
    private val logIdGen = AtomicLong(0L)

    @Volatile
    private var currentStreamStartMs: Long = 0L
    private val currentStreamTokens = AtomicLong(0L)
    @Volatile
    private var lastSpeedUpdateMs: Long = 0L

    private fun nowTime(): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())

    fun log(level: LogLevel, message: String) {
        val entry = MonitorLogEntry(
            id = logIdGen.incrementAndGet(),
            timestamp = nowTime(),
            level = level,
            message = message
        )
        _snapshot.update { cur ->
            val updated = (cur.logs + entry).takeLast(MAX_LOGS)
            cur.copy(logs = updated)
        }
    }

    fun onRequestStart(label: String) {
        val active = activeCounter.incrementAndGet()
        val total = requestCounter.incrementAndGet()
        currentStreamStartMs = System.currentTimeMillis()
        currentStreamTokens.set(0L)
        lastSpeedUpdateMs = 0L
        _snapshot.update { cur ->
            cur.copy(activeRequests = active, totalRequests = total)
        }
        log(LogLevel.ACTIVE, label)
    }

    fun onTokenGenerated(count: Int = 1) {
        val total = tokenCounter.addAndGet(count.toLong())
        val streamToks = currentStreamTokens.addAndGet(count.toLong())
        val now = System.currentTimeMillis()
        if (now - lastSpeedUpdateMs >= 150L || streamToks <= 2L) {
            lastSpeedUpdateMs = now
            val elapsedSec = ((now - currentStreamStartMs).coerceAtLeast(1L)) / 1000.0
            val speed = if (elapsedSec > 0.05) streamToks / elapsedSec else 0.0
            _snapshot.update { cur ->
                cur.copy(
                    totalTokens = total,
                    liveTokensPerSec = speed
                )
            }
        }
    }

    fun onRequestEnd(ok: Boolean, detail: String) {
        val active = activeCounter.decrementAndGet().coerceAtLeast(0)
        val elapsedSec = ((System.currentTimeMillis() - currentStreamStartMs).coerceAtLeast(1L)) / 1000.0
        val streamToks = currentStreamTokens.get()
        val finalSpeed = if (streamToks > 0 && elapsedSec > 0.05) streamToks / elapsedSec else 0.0
        _snapshot.update { cur ->
            cur.copy(
                activeRequests = active,
                totalTokens = tokenCounter.get(),
                liveTokensPerSec = if (active == 0) 0.0 else finalSpeed
            )
        }
        log(if (ok) LogLevel.OK else LogLevel.ERR, detail)
    }

    fun resetCounters() {
        activeCounter.set(0)
        _snapshot.update { cur ->
            cur.copy(activeRequests = 0, liveTokensPerSec = 0.0)
        }
    }
}
