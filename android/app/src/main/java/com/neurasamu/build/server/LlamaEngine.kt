package com.neurasamu.build.server

import android.content.Context
import com.neurasamu.build.model.SamuModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Launches the bundled llama-server binary (shipped as libllama-server.so
 * in jniLibs so Android allows exec from nativeLibraryDir).
 */
class LlamaEngine(private val ctx: Context) {

    @Volatile private var process: Process? = null
    @Volatile private var currentModel: SamuModel? = null
    @Volatile var enginePort: Int = 8091
        private set

    val activeModel: SamuModel? get() = currentModel

    private fun binaryFile(): File {
        val fromLib = File(ctx.applicationInfo.nativeLibraryDir, "libllama-server.so")
        if (fromLib.exists()) return fromLib
        // fallback for local dev (non-packaged)
        return File(ctx.filesDir, "llama-server")
    }

    suspend fun ensureLoaded(model: SamuModel): Int = withContext(Dispatchers.IO) {
        if (currentModel?.id == model.id && process?.isAlive == true) {
            return@withContext enginePort
        }
        stopInternal()

        val bin = binaryFile()
        if (!bin.exists()) throw IOException("llama-server binary missing at ${bin.absolutePath}")
        if (!bin.canExecute()) bin.setExecutable(true)

        val cmd = mutableListOf(
            bin.absolutePath,
            "-m", model.file.absolutePath,
            "--host", "127.0.0.1",
            "--port", enginePort.toString(),
            "-c", model.ctx.toString(),
            "-t", model.threads.toString()
        )
        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        pb.environment()["LD_LIBRARY_PATH"] = ctx.applicationInfo.nativeLibraryDir
        val proc = pb.start()
        process = proc
        currentModel = model

        // drain stdout to avoid pipe buffer filling
        Thread {
            try {
                proc.inputStream.bufferedReader().forEachLine { /* log if needed */ }
            } catch (_: Exception) {}
        }.apply { isDaemon = true }.start()

        val ok = waitReady(60_000)
        if (!ok) {
            stopInternal()
            throw IOException("llama-server not ready in 60s")
        }
        enginePort
    }

    private fun waitReady(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val p = process ?: return false
            if (!p.isAlive) return false
            try {
                val c = (URL("http://127.0.0.1:$enginePort/health").openConnection() as HttpURLConnection)
                c.connectTimeout = 500
                c.readTimeout = 500
                if (c.responseCode == 200) {
                    c.disconnect()
                    return true
                }
                c.disconnect()
            } catch (_: Exception) {}
            Thread.sleep(300)
        }
        return false
    }

    fun stop() { stopInternal() }

    private fun stopInternal() {
        val p = process ?: return
        try {
            p.destroy()
            if (!p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly()
        } catch (_: Exception) {
        } finally {
            process = null
            currentModel = null
        }
    }
}
