package com.neurasamu.build.engine

import com.neurasamu.build.server.SamuEngine
import java.io.File

object GgufBridge {

    @Volatile var isLoaded: Boolean = false
        private set

    fun load(file: File, threads: Int, threadsBatch: Int, ctxSize: Int) {
        SamuEngine.loadModel(
            path = file.absolutePath,
            threads = threads,
            threadsBatch = threadsBatch,
            ctx = ctxSize,
            gpuLayers = 0
        )
        isLoaded = true
    }

    fun generate(prompt: String, maxTokens: Int, temperature: Double,
                 topP: Double, onToken: (String) -> Unit) {
        SamuEngine.generate(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            onToken = onToken
        )

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


    fun stop() = SamuEngine.stop()
    fun clearContext() = SamuEngine.clearContext()


    fun unload() {
        try { SamuEngine.unload() } finally { isLoaded = false }
    }

    fun contextSize(): Int = try { SamuEngine.contextSize() } catch (_: Exception) { 0 }
    fun tokensUsed(): Int = try { SamuEngine.tokensUsed() } catch (_: Exception) { 0 }
}
