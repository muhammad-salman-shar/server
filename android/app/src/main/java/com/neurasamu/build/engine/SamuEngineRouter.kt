package com.neurasamu.build.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.locks.ReentrantLock

/**
 * Routes inference calls cleanly to llama.cpp (GGUF) or MediaPipe LiteRT,
 * guarded with a ReentrantLock so simultaneous local Chat + LAN requests never crash JNI.
 */
object SamuEngineRouter {
    private const val TAG = "EngineRouter"
    val inferenceLock = ReentrantLock(true)

    enum class Backend { GGUF, LITERT, UNSUPPORTED }

    @Volatile
    var active: Backend? = null
        private set

    fun detect(modelFile: File): Backend {
        val name = modelFile.name.lowercase()
        return when {
            name.endsWith(".gguf") -> Backend.GGUF
            name.endsWith(".litertlm") || name.endsWith(".task") || name.endsWith(".bin") -> Backend.LITERT
            else -> Backend.UNSUPPORTED
        }
    }

    fun backendLabel(b: Backend): String = when (b) {
        Backend.GGUF -> "llama.cpp (GGUF)"
        Backend.LITERT -> "LiteRT (Google)"
        Backend.UNSUPPORTED -> "unsupported"
    }

    fun load(
        ctx: Context,
        modelFile: File,
        threads: Int,
        threadsBatch: Int,
        ctxSize: Int,
        gpuLayers: Int = 0,
        onProgress: ((Double) -> Unit)? = null
    ) {
        inferenceLock.lock()
        try {
            unloadUnlocked()
            val b = detect(modelFile)
            Log.i(TAG, "Loading ${modelFile.name} via $b (ctx=$ctxSize, t=$threads/$threadsBatch, ngl=$gpuLayers)")
            when (b) {
                Backend.GGUF -> {
                    GgufBridge.load(modelFile, threads, threadsBatch, ctxSize, gpuLayers, onProgress)
                    active = Backend.GGUF
                }
                Backend.LITERT -> {
                    LiteRtBridge.load(ctx, modelFile, threads, ctxSize)
                    active = Backend.LITERT
                }
                Backend.UNSUPPORTED -> throw IllegalArgumentException(
                    "Unsupported LLM format: ${modelFile.name}"
                )
            }
        } finally {
            inferenceLock.unlock()
        }
    }

    fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        onToken: (String) -> Unit
    ) {
        when (active) {
            Backend.GGUF -> GgufBridge.generate(prompt, maxTokens, temperature, topP, onToken)
            Backend.LITERT -> LiteRtBridge.generate(prompt, maxTokens, temperature, topP, onToken)
            else -> throw IllegalStateException("No model loaded")
        }
    }

    fun generateWithImage(
        prompt: String,
        rgbData: ByteArray,
        width: Int,
        height: Int,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        onToken: (String) -> Unit
    ) {
        when (active) {
            Backend.GGUF -> GgufBridge.generateWithImage(
                prompt, rgbData, width, height, maxTokens, temperature, topP, onToken
            )
            else -> throw IllegalStateException("Vision requires a GGUF model with mmproj attached")
        }
    }

    fun generateWithAudio(
        prompt: String,
        audioSamples: FloatArray,
        sampleRate: Int,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        onToken: (String) -> Unit
    ) {
        when (active) {
            Backend.GGUF -> GgufBridge.generateWithAudio(
                prompt, audioSamples, sampleRate, maxTokens, temperature, topP, onToken
            )
            else -> throw IllegalStateException("Audio input requires a GGUF model with mmproj attached")
        }
    }

    fun loadMmproj(path: String): Boolean =
        if (active == Backend.GGUF) GgufBridge.loadMmproj(path) else false

    fun isMmprojLoaded(): Boolean =
        if (active == Backend.GGUF) GgufBridge.isMmprojLoaded() else false

    fun freeMmproj() {
        if (active == Backend.GGUF) GgufBridge.freeMmproj()
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
        stop()
        inferenceLock.lock()
        try {
            unloadUnlocked()
        } finally {
            inferenceLock.unlock()
        }
    }

    private fun unloadUnlocked() {
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
