package com.neurasamu.build.engine

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object LiteRtBridge {

    private const val TAG = "LiteRtBridge"

    @Volatile private var inference: LlmInference? = null
    @Volatile private var loadedContextSize: Int = 0

    @Volatile var isLoaded: Boolean = false
        private set

    @Synchronized
    fun load(ctx: Context, file: File, threads: Int, ctxSize: Int) {
        unload()
        if (!file.exists()) throw IllegalArgumentException("Model not found: ${file.absolutePath}")
        Log.i(TAG, "Loading LiteRT model: ${file.name}, ctx=$ctxSize, threads=$threads")

        val options = LlmInferenceOptions.builder()
            .setModelPath(file.absolutePath)
            .setMaxTokens(ctxSize.coerceIn(512, 8192))
            .setMaxTopK(64)
            .build()

        inference = LlmInference.createFromOptions(ctx.applicationContext, options)
        loadedContextSize = ctxSize
        isLoaded = true
        Log.i(TAG, "LiteRT model ready")
    }

    fun generate(prompt: String, maxTokens: Int, temperature: Double,
                 topP: Double, onToken: (String) -> Unit) {
        val inf = inference ?: throw IllegalStateException("LiteRT model not loaded")

        val latch = CountDownLatch(1)
        val err = AtomicReference<Throwable?>(null)

        val listener = ProgressListener<String> { partialResult, done ->
            if (partialResult.isNotEmpty()) onToken(partialResult)
            if (done) latch.countDown()
        }

        try {
            inf.generateResponseAsync(prompt, listener)
        } catch (e: Exception) {
            err.set(e)
            latch.countDown()
        }

        if (!latch.await(10, TimeUnit.MINUTES)) {
            throw RuntimeException("LiteRT generation timed out")
        }
        err.get()?.let { throw it }
    }

    fun stop() {
        Log.i(TAG, "stop() called (no cancel API in 0.10.24)")
    }

    fun clearContext() { /* MediaPipe is stateless per call — no-op */ }

    fun unload() {
        try {
            inference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close failed: ${e.message}")
        } finally {
            inference = null
            isLoaded = false
        }
    }

    fun contextSize(): Int = loadedContextSize
}
