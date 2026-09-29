package com.neurasamu.build.server

import android.content.Context
import com.neurasamu.build.model.SamuModel

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

    fun loadModel(ctx: Context, model: SamuModel, threads: Int = 4) {
        SamuEngine.loadModel(
            path = model.file.absolutePath,
            threads = threads,
            ctx = model.ctx,
            gpuLayers = 0
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
