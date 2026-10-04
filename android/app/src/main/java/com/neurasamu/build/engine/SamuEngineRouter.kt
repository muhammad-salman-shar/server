package com.neurasamu.build.engine

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Picks the right inference backend based on file extension.
 * Currently: .gguf → llama.cpp (JNI), .litertlm/.task/.bin → LiteRT via MediaPipe.
 */
object SamuEngineRouter {

    private const val TAG = "EngineRouter"

    enum class Backend { GGUF, LITERT, UNSUPPORTED }

    fun detect(modelFile: File): Backend {
        val name = modelFile.name.lowercase()
        return when {
            name.endsWith(".gguf") -> Backend.GGUF
            name.endsWith(".litertlm") || name.endsWith(".task") ||
                name.endsWith(".bin") -> Backend.LITERT
            else -> Backend.UNSUPPORTED
        }
    }

    fun backendLabel(b: Backend): String = when (b) {
        Backend.GGUF -> "llama.cpp (GGUF)"
        Backend.LITERT -> "LiteRT (Google)"
        Backend.UNSUPPORTED -> "unsupported"
    }

    @Volatile var active: Backend? = null
        private set

    fun load(ctx: Context, modelFile: File, threads: Int, threadsBatch: Int,
             ctxSize: Int, onToken: (String) -> Unit) {
        val b = detect(modelFile)
        Log.i(TAG, "Loading ${modelFile.name} via $b")
        when (b) {
            Backend.GGUF -> {
                GgufBridge.load(modelFile, threads, threadsBatch, ctxSize)
                active = Backend.GGUF
            }
            Backend.LITERT -> {
                LiteRtBridge.load(ctx, modelFile, threads, ctxSize)
                active = Backend.LITERT
            }
            Backend.UNSUPPORTED -> throw IllegalArgumentException(
                "Unsupported model format: ${modelFile.name}"
            )
        }
    }

    fun generate(prompt: String, maxTokens: Int, temperature: Double, topP: Double,
                 onToken: (String) -> Unit) {
        when (active) {
            Backend.GGUF -> GgufBridge.generate(prompt, maxTokens, temperature, topP, onToken)
            Backend.LITERT -> LiteRtBridge.generate(prompt, maxTokens, temperature, topP, onToken)
            else -> throw IllegalStateException("No model loaded")
        }
    }

    fun stop() {
        when (active) {
            Backend.GGUF -> GgufBridge.stop()
            Backend.LITERT -> LiteRtBridge.stop()
            else -> {}
        }
    }
    fun clearContext() {
        when (active) {
            Backend.GGUF -> GgufBridge.clearContext()
            Backend.LITERT -> LiteRtBridge.clearContext()
            else -> {}
        }
    }


    fun unload() {
        when (active) {
            Backend.GGUF -> GgufBridge.unload()
            Backend.LITERT -> LiteRtBridge.unload()
            else -> {}
        }
        active = null
    }

    fun isLoaded(): Boolean = when (active) {
        Backend.GGUF -> GgufBridge.isLoaded
        Backend.LITERT -> LiteRtBridge.isLoaded
        else -> false
    }

    fun contextSize(): Int = when (active) {
        Backend.GGUF -> GgufBridge.contextSize()
        Backend.LITERT -> LiteRtBridge.contextSize()
        else -> 0
    }

    fun tokensUsed(): Int = when (active) {
        Backend.GGUF -> GgufBridge.tokensUsed()
        Backend.LITERT -> 0
        else -> 0
    }
}
