package com.neurasamu.build.engine

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.ExtraFileRole
import com.neurasamu.build.data.ModelEntry
import com.neurasamu.build.data.ModelKind
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class MediaGenerationResult(
    val file: File,
    val mimeType: String,
    val relativeUrlPath: String
)

object MediaEngines {

    private fun findBinary(ctx: Context, names: List<String>): File? {
        val nativeDir = File(ctx.applicationInfo.nativeLibraryDir)
        val binDir = File(ctx.filesDir, "bin")
        val candidates = mutableListOf<File>()
        names.forEach { n ->
            candidates.add(File(nativeDir, "lib$n.so"))
            candidates.add(File(nativeDir, n))
            candidates.add(File(binDir, n))
        }
        return candidates.firstOrNull { it.exists() && it.canExecute() }
    }

    // ==================== IMAGE GENERATION ====================

    fun generateImage(
        ctx: Context,
        prompt: String,
        model: ModelEntry?,
        size: String = "512x512",
        steps: Int = 20,
        initImageB64: String? = null
    ): MediaGenerationResult {
        if (model == null) {
            throw IllegalStateException(
                "No Image Gen model configured. Add a .safetensors / .ckpt / .gguf diffusion model and set its type to Image Gen in Server -> Model Settings."
            )
        }
        val sdBin = findBinary(ctx, listOf("sd", "sd-cli", "stable-diffusion"))
            ?: throw IllegalStateException(
                "Local stable-diffusion.cpp binary not bundled on this Android build. Configure a remote Image API in Chat -> + API or place arm64 'sd' binary in app files/bin."
            )

        val parts = size.lowercase().split("x")
        val width = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(128, 2048) ?: 512
        val height = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(128, 2048) ?: 512

        val outFile = File(AtomicStorage.imagesDir(ctx), "img_${System.currentTimeMillis()}.png")
        val cmd = mutableListOf(sdBin.absolutePath)

        val nameLc = model.file.name.lowercase()
        if (nameLc.contains("flux") || nameLc.contains("sd3")) {
            cmd.addAll(listOf("--diffusion-model", model.file.absolutePath))
        } else {
            cmd.addAll(listOf("-m", model.file.absolutePath))
        }

        attachDiffusionExtras(cmd, model)

        var tempRef: File? = null
        if (!initImageB64.isNullOrBlank()) {
            tempRef = writeTempBase64Image(ctx, initImageB64)
            cmd.addAll(listOf("--init-img", tempRef.absolutePath, "--strength", "0.75"))
        }

        cmd.addAll(
            listOf(
                "-p", prompt,
                "-W", width.toString(),
                "-H", height.toString(),
                "--steps", steps.coerceIn(1, 100).toString(),
                "-o", outFile.absolutePath
            )
        )
        appendCustomArgs(cmd, model.profile.customArgs)

        try {
            runEngineProcess(cmd, timeoutMinutes = 15)
        } finally {
            tempRef?.delete()
        }

        if (!outFile.exists() || outFile.length() == 0L) {
            throw IllegalStateException("Image engine finished without producing an output PNG.")
        }
        return MediaGenerationResult(
            file = outFile,
            mimeType = "image/png",
            relativeUrlPath = "/media/images/${outFile.name}"
        )
    }

    // ==================== AUDIO / TTS GENERATION ====================

    fun generateAudio(
        ctx: Context,
        text: String,
        model: ModelEntry?
    ): MediaGenerationResult {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) throw IllegalArgumentException("Text prompt is required for Audio TTS")

        val outFile = File(AtomicStorage.audioDir(ctx), "tts_${System.currentTimeMillis()}.wav")

        // 1. Try Piper (.onnx) if configured and binary available
        if (model != null && model.profile.kind == ModelKind.AUDIO_TTS) {
            if (model.file.name.endsWith(".onnx", ignoreCase = true)) {
                val piperBin = findBinary(ctx, listOf("piper"))
                if (piperBin != null) {
                    val cfg = model.profile.extraFiles.firstOrNull {
                        it.role == ExtraFileRole.VOICE_CONFIG && File(it.path).exists()
                    }?.path
                    val cmd = mutableListOf(
                        piperBin.absolutePath,
                        "--model", model.file.absolutePath,
                        "--output_file", outFile.absolutePath
                    )
                    if (cfg != null) cmd.addAll(listOf("--config", cfg))
                    appendCustomArgs(cmd, model.profile.customArgs)
                    runEngineProcess(cmd, stdinText = cleanText, timeoutMinutes = 5)
                    if (outFile.exists() && outFile.length() > 44L) {
                        return MediaGenerationResult(outFile, "audio/wav", "/media/audio/${outFile.name}")
                    }
                }
            } else if (model.file.name.endsWith(".gguf", ignoreCase = true)) {
                val ttsBin = findBinary(ctx, listOf("llama-tts"))
                if (ttsBin != null) {
                    val vocoder = model.profile.extraFiles.firstOrNull {
                        it.role == ExtraFileRole.VOCODER && File(it.path).exists()
                    }?.path
                    val cmd = mutableListOf(
                        ttsBin.absolutePath,
                        "-m", model.file.absolutePath,
                        "-p", cleanText,
                        "-o", outFile.absolutePath
                    )
                    if (vocoder != null) cmd.addAll(listOf("-mv", vocoder))
                    appendCustomArgs(cmd, model.profile.customArgs)
                    runEngineProcess(cmd, timeoutMinutes = 5)
                    if (outFile.exists() && outFile.length() > 44L) {
                        return MediaGenerationResult(outFile, "audio/wav", "/media/audio/${outFile.name}")
                    }
                }
            }
        }

        // 2. Built-in Android TextToSpeech.synthesizeToFile WAV fallback (always works out of the box!)
        synthesizeAndroidTtsWav(ctx, cleanText, outFile)
        if (!outFile.exists() || outFile.length() == 0L) {
            throw IllegalStateException("Audio TTS synthesis failed to create WAV file")
        }
        return MediaGenerationResult(
            file = outFile,
            mimeType = "audio/wav",
            relativeUrlPath = "/media/audio/${outFile.name}"
        )
    }

    private fun synthesizeAndroidTtsWav(ctx: Context, text: String, outFile: File) {
        val initLatch = CountDownLatch(1)
        val initStatus = AtomicReference(TextToSpeech.ERROR)
        var ttsRef: TextToSpeech? = null

        ttsRef = TextToSpeech(ctx.applicationContext) { status ->
            initStatus.set(status)
            initLatch.countDown()
        }

        if (!initLatch.await(10, TimeUnit.SECONDS) || initStatus.get() != TextToSpeech.SUCCESS) {
            try { ttsRef.shutdown() } catch (_: Exception) {}
            throw IllegalStateException("Android TextToSpeech engine unavailable on this device")
        }

        val synthLatch = CountDownLatch(1)
        val synthErr = AtomicReference<String?>(null)
        val utteranceId = "samu_tts_${UUID.randomUUID()}"

        try {
            ttsRef.language = Locale.getDefault()
            ttsRef.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(uid: String?) {}
                override fun onDone(uid: String?) {
                    if (uid == utteranceId) synthLatch.countDown()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(uid: String?) {
                    if (uid == utteranceId) {
                        synthErr.set("TTS synthesis error")
                        synthLatch.countDown()
                    }
                }
                override fun onError(uid: String?, errorCode: Int) {
                    if (uid == utteranceId) {
                        synthErr.set("TTS synthesis error (code $errorCode)")
                        synthLatch.countDown()
                    }
                }
            })

            val params = Bundle()
            val clipped = if (text.length > 3800) text.take(3800) else text
            val res = ttsRef.synthesizeToFile(clipped, params, outFile, utteranceId)
            if (res != TextToSpeech.SUCCESS) {
                throw IllegalStateException("TTS synthesizeToFile returned error ($res)")
            }
            if (!synthLatch.await(90, TimeUnit.SECONDS)) {
                throw IllegalStateException("TTS synthesis timed out")
            }
            synthErr.get()?.let { throw IllegalStateException(it) }
        } finally {
            try { ttsRef.stop() } catch (_: Exception) {}
            try { ttsRef.shutdown() } catch (_: Exception) {}
        }
    }

    // ==================== VIDEO GENERATION ====================

    fun generateVideo(
        ctx: Context,
        prompt: String,
        model: ModelEntry?,
        frames: Int = 16,
        initImageB64: String? = null
    ): MediaGenerationResult {
        if (model == null) {
            throw IllegalStateException(
                "No Video Gen model configured. Add a diffusion video model and set its type to Video Gen in Server -> Model Settings."
            )
        }
        val sdBin = findBinary(ctx, listOf("sd", "sd-cli", "stable-diffusion"))
            ?: throw IllegalStateException(
                "Local diffusion video binary not bundled on this Android build. Connect a remote Video API in Chat -> + API or place arm64 'sd' binary in app files/bin."
            )

        val outFile = File(AtomicStorage.videosDir(ctx), "vid_${System.currentTimeMillis()}.mp4")
        val cmd = mutableListOf(
            sdBin.absolutePath,
            "-M", "vid_gen",
            "--diffusion-model", model.file.absolutePath,
            "--video-frames", frames.coerceIn(4, 128).toString(),
            "-p", prompt,
            "-o", outFile.absolutePath
        )
        attachDiffusionExtras(cmd, model)

        var tempRef: File? = null
        if (!initImageB64.isNullOrBlank()) {
            tempRef = writeTempBase64Image(ctx, initImageB64)
            cmd.addAll(listOf("--init-img", tempRef.absolutePath))
        }
        appendCustomArgs(cmd, model.profile.customArgs)

        try {
            runEngineProcess(cmd, timeoutMinutes = 25)
        } finally {
            tempRef?.delete()
        }

        if (!outFile.exists() || outFile.length() == 0L) {
            throw IllegalStateException("Video engine finished without producing an output video.")
        }
        return MediaGenerationResult(
            file = outFile,
            mimeType = "video/mp4",
            relativeUrlPath = "/media/videos/${outFile.name}"
        )
    }

    private fun attachDiffusionExtras(cmd: MutableList<String>, model: ModelEntry) {
        model.profile.extraFiles.filter { File(it.path).exists() }.forEach { ef ->
            when (ef.role) {
                ExtraFileRole.VAE -> cmd.addAll(listOf("--vae", ef.path))
                ExtraFileRole.CLIP -> cmd.addAll(listOf("--clip_l", ef.path))
                ExtraFileRole.T5 -> cmd.addAll(listOf("--t5xxl", ef.path))
                ExtraFileRole.LORA -> {
                    val dir = File(ef.path).parentFile?.absolutePath
                    if (dir != null) cmd.addAll(listOf("--lora-model-dir", dir))
                }
                else -> {}
            }
        }
    }

    private fun appendCustomArgs(cmd: MutableList<String>, raw: String) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return
        trimmed.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { cmd.add(it) }
    }

    private fun writeTempBase64Image(ctx: Context, rawB64: String): File {
        val pure = if (rawB64.startsWith("data:")) rawB64.substringAfter(",") else rawB64
        val bytes = Base64.decode(pure.trim(), Base64.DEFAULT)
        val tmp = File(ctx.cacheDir, "ref_${UUID.randomUUID()}.png")
        tmp.writeBytes(bytes)
        return tmp
    }

    private fun runEngineProcess(
        cmd: List<String>,
        stdinText: String? = null,
        timeoutMinutes: Long = 10
    ) {
        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        val proc = pb.start()
        if (stdinText != null) {
            proc.outputStream.bufferedWriter().use { w ->
                w.write(stdinText)
                w.newLine()
                w.flush()
            }
        }
        val logLines = mutableListOf<String>()
        val readerThread = Thread {
            try {
                proc.inputStream.bufferedReader().forEachLine { line ->
                    if (line.isNotBlank()) {
                        logLines.add(line)
                        if (logLines.size > 40) logLines.removeAt(0)
                    }
                }
            } catch (_: Exception) {}
        }
        readerThread.start()
        val finished = proc.waitFor(timeoutMinutes, TimeUnit.MINUTES)
        if (!finished) {
            proc.destroyForcibly()
            throw IllegalStateException("Engine timed out after $timeoutMinutes minutes")
        }
        readerThread.join(1000)
        val code = proc.exitValue()
        if (code != 0) {
            val lastErr = logLines.lastOrNull { it.isNotBlank() } ?: "exit code $code"
            throw IllegalStateException("Engine crashed ($code): $lastErr")
        }
    }
}
