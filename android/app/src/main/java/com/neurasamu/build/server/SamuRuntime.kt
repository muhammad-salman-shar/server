package com.neurasamu.build.server

import android.content.Context
import com.neurasamu.build.model.SamuModel

object SamuRuntime {
    @Volatile private var engine: LlamaEngine? = null
    @Volatile private var http: SamuHttpServer? = null
    @Volatile var port: Int = 8080
        private set

    fun engine(ctx: Context): LlamaEngine {
        engine?.let { return it }
        synchronized(this) {
            engine?.let { return it }
            val e = LlamaEngine(ctx.applicationContext)
            engine = e
            return e
        }
    }

    fun httpOrNull(): SamuHttpServer? = http

    fun http(ctx: Context): SamuHttpServer {
        http?.let { return it }
        synchronized(this) {
            http?.let { return it }
            val h = SamuHttpServer(ctx.applicationContext, engine(ctx), port)
            http = h
            return h
        }
    }

    fun startServer(ctx: Context, model: SamuModel): String {
        val h = http(ctx)
        try { h.startSafe() } catch (_: Exception) {}
        h.displayNameOverride = model.displayName
        return "http://0.0.0.0:$port"
    }

    fun stopAll() {
        try { http?.stop() } catch (_: Exception) {}
        try { engine?.stop() } catch (_: Exception) {}
        http = null
        engine = null
    }
}
