package com.neurasamu.build.engine

import com.neurasamu.build.server.SamuEngine
import java.io.File

object GgufBridge {

    @Volatile
    var isLoaded: Boolean = false
        private set

    fun load(
        file: File,
        threads: Int,
        threadsBatch: Int,
        ctxSize: Int,
        gpuLayers: Int = 0,
        onProgress: ((Double) -> Unit)? = null
    ) {
        SamuEngine.loadModel(
            path = file.absolutePath,
            threads = threads.coerceAtLeast(1),
            threadsBatch = threadsBatch.coerceAtLeast(1),
            ctx = ctxSize.coerceAtLeast(512),
            gpuLayers = gpuLayers.coerceAtLeast(0),
            onProgress = onProgress
        )
        isLoaded = true
    }

    fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        onToken: (String) -> Unit
    ) {
        SamuEngine.generate(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            onToken = onToken
        )
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
        SamuEngine.generateWithImage(
            prompt = prompt,
            rgbData = rgbData,
            width = width,
            height = height,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            onToken = onToken
        )
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
        SamuEngine.generateWithAudio(
            prompt = prompt,
            audioSamples = audioSamples,
            sampleRate = sampleRate,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            onToken = onToken
        )
    }

    fun loadMmproj(path: String): Boolean = try {
        SamuEngine.loadMmproj(path)
    } catch (_: Throwable) {
        false
    }

    fun isMmprojLoaded(): Boolean = try {
        SamuEngine.isMmprojLoaded()
    } catch (_: Throwable) {
        false
    }

    fun freeMmproj() {
        try {
            SamuEngine.freeMmproj()
        } catch (_: Throwable) {}
    }

    fun stop() {
        try {
            SamuEngine.stop()
        } catch (_: Throwable) {}
    }

    fun clearContext() {
        try {
            SamuEngine.clearContext()
        } catch (_: Throwable) {}
    }

    fun unload() {
        try {
            SamuEngine.freeMmproj()
        } catch (_: Throwable) {}
        try {
            SamuEngine.unload()
        } finally {
            isLoaded = false
        }
    }

    fun contextSize(): Int = try { SamuEngine.contextSize() } catch (_: Throwable) { 0 }
    fun tokensUsed(): Int = try { SamuEngine.tokensUsed() } catch (_: Throwable) { 0 }
}
