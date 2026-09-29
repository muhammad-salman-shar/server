package com.neurasamu.build.server

import android.content.Context
import com.neurasamu.build.model.SamuModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LlamaEngine(private val ctx: Context) {

    @Volatile private var process: Process? = null
    @Volatile private var currentModel: SamuModel? = null
    @Volatile var enginePort: Int = 8091
        private set
    @Volatile var lastError: String? = null
        private set

    val activeModel: SamuModel? get() = currentModel

    fun logFile(): File = File(ctx.filesDir, "engine.log")

    private fun binaryFile(): File {
        val fromLib = File(ctx.applicationInfo.nativeLibraryDir, "libllama-server.so")
        if (fromLib.exists()) return fromLib
        return File(ctx.filesDir, "llama-server")
    }

    /** Run llama-server --version to verify binary actually executes on this device. */
    suspend fun selfTest(): String = withContext(Dispatchers.IO) {
        val bin = binaryFile()
        if (!bin.exists()) return@withContext "MISSING: ${bin.absolutePath}"
        if (!bin.canExecute()) bin.setExecutable(true)
        try {
            val p = ProcessBuilder(bin.absolutePath, "--version")
                .redirectErrorStream(true)
                .also { it.environment()["LD_LIBRARY_PATH"] = ctx.applicationInfo.nativeLibraryDir }
                .start()
            val out = p.inputStream.bufferedReader().readText()
            val ok = p.waitFor(15, TimeUnit.SECONDS)
            val code = if (ok) p.exitValue() else { p.destroyForcibly(); -1 }
            "exit=$code\n$out"
        } catch (e: Exception) {
            "EXEC_FAILED: ${e.javaClass.simpleName}: ${e.message}"
        }
    }

    suspend fun ensureLoaded(model: SamuModel): Int = withContext(Dispatchers.IO) {
        if (currentModel?.id == model.id && process?.isAlive == true) {
            return@withContext enginePort
        }
        stopInternal()

        val bin = binaryFile()
        if (!bin.exists()) throw IOException("llama-server binary missing: ${bin.absolutePath}")
        if (!bin.canExecute()) bin.setExecutable(true)

        val cmd = listOf(
            bin.absolutePath,
            "-m", model.file.absolutePath,
            "--host", "127.0.0.1",
            "--port", enginePort.toString(),
            "-c", model.ctx.toString(),
            "-t", model.threads.toString()
        )

        appendLog("\n=== starting ${model.displayName} @ ${System.currentTimeMillis()} ===")
        appendLog("CMD: ${cmd.joinToString(" ")}")

        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = ctx.applicationInfo.nativeLibraryDir
        val proc = try { pb.start() } catch (e: Exception) {
            lastError = "spawn failed: ${e.javaClass.simpleName}: ${e.message}"
            appendLog(lastError!!)
            throw IOException(lastError)
        }
        process = proc
        currentModel = model

        val died = AtomicBoolean(false)
        val drain = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                reader.forEachLine { line ->
                    appendLog(line)
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; name = "samu-drain" }
        drain.start()

        val ready = waitReady(proc, 300_000L, died)
        if (!ready) {
            val code = try { proc.exitValue() } catch (_: Exception) { null }
            val tail = readLogTail(40)
            stopInternal()
            val msg = if (died.get()) {
                "llama-server exited during startup (exit=${code ?: "?"})\n--- last log ---\n$tail"
            } else {
                "llama-server not ready in 300s (still running, exit=$code)\n--- last log ---\n$tail"
            }
            lastError = msg
            throw IOException(msg)
        }
        lastError = null
        enginePort
    }

    private fun waitReady(proc: Process, timeoutMs: Long, died: AtomicBoolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastErr: String? = null
        while (System.currentTimeMillis() < deadline) {
            if (!proc.isAlive) { died.set(true); return false }

            // 1. /health
            if (httpOk("/health")) return true
            // 2. /props (naya endpoint)
            if (httpOk("/props")) return true
            // 3. TCP connect fallback
            if (tcpOk()) return true

            Thread.sleep(300)
        }
        return false
    }

    private fun httpOk(path: String): Boolean {
        return try {
            val c = URL("http://127.0.0.1:$enginePort$path").openConnection() as HttpURLConnection
            c.connectTimeout = 400
            c.readTimeout = 400
            val ok = c.responseCode in 200..299
            c.disconnect()
            ok
        } catch (_: Exception) { false }
    }

    private fun tcpOk(): Boolean {
        return try {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", enginePort), 300) }
            true
        } catch (_: Exception) { false }
    }

    fun stop() { stopInternal() }

    private fun stopInternal() {
        val p = process ?: return
        try {
            p.destroy()
            if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: Exception) {} finally {
            process = null
            currentModel = null
        }
    }

    fun appendLog(line: String) {
        try { logFile().appendText(line + "\n") } catch (_: Exception) {}
    }

    fun readLogTail(lines: Int): String {
        return try {
            val f = logFile()
            if (!f.exists()) return "(no log)"
            f.readLines().takeLast(lines).joinToString("\n")
        } catch (e: Exception) { "(log read failed: ${e.message})" }
    }

    fun clearLog() { try { logFile().writeText("") } catch (_: Exception) {} }
}
