package com.neurasamu.build.server

import android.util.Log

object SamuEngine {

    private const val TAG = "SamuEngine"

    init {
        System.loadLibrary("samu_jni")
    }

    external fun nativeDetectGpu(outStats: LongArray): String

    external fun nativeLoadModel(
        path: String,
        nThreads: Long,
        nThreadsBatch: Long,
        ctxSize: Long,
        nGpuLayers: Long,
        progressCallback: ((Any) -> Any)?
    )

    external fun nativeGenerate(
        prompt: String,
        maxTokens: Long,
        temperature: Double,
        topP: Double,
        topK: Long,
        minP: Double,
        typicalP: Double,
        repeatPenalty: Double,
        frequencyPenalty: Double,
        presencePenalty: Double,
        repeatLastN: Long,
        mirostat: Long,
        mirostatTau: Double,
        mirostatEta: Double,
        seed: Long,
        penalizeNewline: Boolean,
        tokenCallback: ((Any) -> Any)?
    )

    external fun nativeStop()
    external fun nativeFreeModel()
    external fun nativeGetTokensUsed(): Int
    external fun nativeGetContextSize(): Int
    external fun nativeClearContext()
    external fun nativeSetSystemPromptLength(length: Int)

    @Volatile var isLoaded: Boolean = false
        private set

    fun loadModel(
        path: String,
        threads: Int,
        threadsBatch: Int,
        ctx: Int,
        gpuLayers: Int = 0,
        onProgress: ((Double) -> Unit)? = null
    ) {
        val cb: ((Any) -> Any)? = onProgress?.let { user ->
            { arg ->
                val d = (arg as? Double) ?: 0.0
                user(d)
                Unit
            }
        }
        nativeLoadModel(
            path,
            threads.toLong(),
            threadsBatch.toLong(),
            ctx.toLong(),
            gpuLayers.toLong(),
            cb
        )
        isLoaded = true
    }

    fun unload() {
        try { nativeFreeModel() } finally { isLoaded = false }
    }

    fun stop() = nativeStop()
    fun clearContext() = nativeClearContext()
    fun tokensUsed(): Int = nativeGetTokensUsed()
    fun contextSize(): Int = nativeGetContextSize()

    fun generate(
        prompt: String,
        maxTokens: Int = 512,
        temperature: Double = 0.7,
        topP: Double = 0.9,
        topK: Long = 40,
        minP: Double = 0.05,
        typicalP: Double = 1.0,
        repeatPenalty: Double = 1.1,
        frequencyPenalty: Double = 0.0,
        presencePenalty: Double = 0.0,
        repeatLastN: Long = 64,
        seed: Long = -1,
        onToken: (String) -> Unit
    ) {
        if (!isLoaded) throw IllegalStateException("Model not loaded")
        val cb: (Any) -> Any = { arg ->
            val s = arg as? String ?: ""
            onToken(s)
            Unit
        }
        nativeGenerate(
            prompt, maxTokens.toLong(),
            temperature, topP, topK, minP, typicalP,
            repeatPenalty, frequencyPenalty, presencePenalty, repeatLastN,
            0L, 5.0, 0.1,
            seed, true,
            cb
        )
    }

    fun detectGpu(): String {
        val stats = LongArray(8)
        return try { nativeDetectGpu(stats) } catch (e: Throwable) {
            Log.w(TAG, "GPU detect failed: ${e.message}"); "cpu-only"
        }
    }
}
