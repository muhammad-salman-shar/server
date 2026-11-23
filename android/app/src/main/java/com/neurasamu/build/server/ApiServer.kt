package com.neurasamu.build.server

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.ModelEntry
import com.neurasamu.build.data.ModelKind
import com.neurasamu.build.data.ServerSettings
import com.neurasamu.build.data.ThinkingOverride
import com.neurasamu.build.engine.HardwareDetector
import com.neurasamu.build.engine.MediaEngines
import com.neurasamu.build.engine.SamuEngineRouter
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ApiServer(
    private val ctx: Context,
    port: Int,
    hostname: String,
    private val settingsProvider: () -> ServerSettings,
    private val modelsProvider: () -> List<ModelEntry>,
    private val activeModelProvider: () -> ModelEntry?
) : NanoHTTPD(hostname, port) {

    @Volatile
    var isStarted: Boolean = false
        private set

    fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
        isStarted = true
    }

    fun stopServer() {
        isStarted = false
        try {
            stop()
        } catch (_: Exception) {}
    }

    // CRITICAL STREAMING FIX: Never GZIP SSE streams or media binaries
    override fun useGzipWhenAccepted(r: Response): Boolean {
        val mime = r.mimeType?.lowercase() ?: ""
        if (mime.contains("text/event-stream") ||
            mime.startsWith("image/") ||
            mime.startsWith("audio/") ||
            mime.startsWith("video/")
        ) {
            return false
        }
        return super.useGzipWhenAccepted(r)
    }

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return corsPreflight()
        }

        val path = session.uri ?: "/"
        return try {
            when {
                path == "/health" && session.method == Method.GET -> handleHealth()
                path == "/v1/models" && session.method == Method.GET -> handleModels()
                path == "/v1/chat/completions" && session.method == Method.POST -> handleChatCompletions(session)
                path == "/v1/completions" && session.method == Method.POST -> handleCompletions(session)
                path == "/v1/images/generations" && session.method == Method.POST -> handleImageGen(session)
                path == "/v1/audio/speech" && session.method == Method.POST -> handleAudioSpeech(session)
                path == "/v1/videos/generations" && session.method == Method.POST -> handleVideoGen(session)
                path.startsWith("/media/") && session.method == Method.GET -> handleMediaFile(path)
                else -> jsonError(Response.Status.NOT_FOUND, "Endpoint not found: $path")
            }
        } catch (e: Exception) {
            ServerMonitor.log(LogLevel.ERR, "HTTP ${session.method} $path failed: ${e.message}")
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Internal server error")
        }
    }

    private fun checkAuth(session: IHTTPSession): Boolean {
        val s = settingsProvider()
        if (!s.requireAuth) return true
        val expected = s.apiKey.trim()
        if (expected.isEmpty()) return false
        val auth = session.headers["authorization"] ?: session.headers["Authorization"] ?: return false
        val presented = auth.removePrefix("Bearer ").removePrefix("bearer ").trim()
        return presented == expected
    }

    private fun activeModelName(): String {
        val s = settingsProvider()
        if (s.modelApiName.isNotBlank()) return s.modelApiName.trim()
        return activeModelProvider()?.displayName ?: "samu-local"
    }

    private fun supportsThinking(): Boolean {
        val active = activeModelProvider() ?: return false
        return when (active.profile.thinkingMode) {
            ThinkingOverride.ENABLED -> true
            ThinkingOverride.DISABLED -> false
            ThinkingOverride.AUTO -> active.ggufMeta?.supportsThinking == true
        }
    }

    // ==================== GET /health ====================

    private fun handleHealth(): Response {
        val active = activeModelProvider()
        val hw = HardwareDetector.detect(ctx)
        val obj = JSONObject().apply {
            put("status", if (SamuEngineRouter.isLoaded()) "ok" else "idle")
            put("engine_running", SamuEngineRouter.isLoaded())
            put("model", activeModelName())
            put("supports_thinking", supportsThinking())
            put("supports_vision", SamuEngineRouter.isMmprojLoaded())
            put("native_context", active?.nativeContext ?: 0)
            put("context_size", SamuEngineRouter.contextSize())
            put("hardware", JSONObject().apply {
                put("tier", hw.tierLabel)
                put("cores", hw.cpuCores)
                put("ram_gb", String.format("%.1f", hw.totalRamGb))
                put("vulkan", hw.hasVulkan)
                put("gpu", hw.nativeGpuLabel)
            })
        }
        return jsonResponse(Response.Status.OK, obj.toString())
    }

    // ==================== GET /v1/models ====================

    private fun handleModels(): Response {
        val data = JSONArray()
        val now = System.currentTimeMillis() / 1000
        val active = activeModelProvider()
        if (active != null) {
            data.put(JSONObject().apply {
                put("id", activeModelName())
                put("object", "model")
                put("created", now)
                put("owned_by", "samu-lab")
                put("kind", active.profile.kind.badge)
            })
        }
        modelsProvider().forEach { entry ->
            if (entry.path != active?.path) {
                data.put(JSONObject().apply {
                    put("id", entry.displayName)
                    put("object", "model")
                    put("created", now)
                    put("owned_by", "samu-lab")
                    put("kind", entry.profile.kind.badge)
                })
            }
        }
        if (data.length() == 0) {
            data.put(JSONObject().apply {
                put("id", activeModelName())
                put("object", "model")
                put("created", now)
                put("owned_by", "samu-lab")
            })
        }
        val out = JSONObject().apply {
            put("object", "list")
            put("data", data)
        }
        return jsonResponse(Response.Status.OK, out.toString())
    }

    // ==================== POST /v1/chat/completions ====================

    private fun handleChatCompletions(session: IHTTPSession): Response {
        if (!checkAuth(session)) return jsonError(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        if (!SamuEngineRouter.isLoaded()) {
            return jsonError(Response.Status.SERVICE_UNAVAILABLE, "No LLM model loaded. Load a model in Server tab.")
        }

        val rawBody = readPostBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "Empty request body")
        val node = JSONObject(rawBody)
        val messages = node.optJSONArray("messages")
            ?: return jsonError(Response.Status.BAD_REQUEST, "messages[] is required")

        val stream = node.optBoolean("stream", false)
        val temperature = node.optDouble("temperature", 0.7).coerceIn(0.0, 2.0)
        val topP = node.optDouble("top_p", 0.9).coerceIn(0.05, 1.0)
        val reasoningBudget = node.optInt("reasoning_budget", -1)
        val reasoningEffort = node.optString("reasoning_effort", "").lowercase()
        val templateKwargs = node.optJSONObject("chat_template_kwargs")
        val explicitDisableThinking =
            (templateKwargs != null && templateKwargs.has("enable_thinking") && !templateKwargs.optBoolean("enable_thinking", true)) ||
                reasoningEffort == "none" ||
                reasoningBudget == 0

        val requestedMaxTokens = node.optInt("max_tokens", 2048)
        val effectiveMaxTokens = if (reasoningBudget > 0) {
            maxOf(requestedMaxTokens, reasoningBudget + 2048).coerceIn(64, 16384)
        } else {
            requestedMaxTokens.coerceIn(64, 16384)
        }

        val parsed = extractMultimodalPrompt(messages, skipThinking = explicitDisableThinking)

        if (parsed.visionRgb != null && !SamuEngineRouter.isMmprojLoaded()) {
            return jsonError(
                Response.Status.BAD_REQUEST,
                "Image attached, but no mmproj projector is loaded. Attach mmproj-*.gguf in Model Settings and reload."
            )
        }
        if (parsed.audioSamples != null && !SamuEngineRouter.isMmprojLoaded()) {
            return jsonError(
                Response.Status.BAD_REQUEST,
                "Audio attached, but no mmproj projector is loaded. Attach mmproj-*.gguf in Model Settings and reload."
            )
        }

        return if (stream) {
            streamLlmResponse(parsed, effectiveMaxTokens, temperature, topP, reasoningBudget)
        } else {
            fullLlmResponse(parsed, effectiveMaxTokens, temperature, topP, reasoningBudget)
        }
    }

    // ==================== POST /v1/completions ====================

    private fun handleCompletions(session: IHTTPSession): Response {
        if (!checkAuth(session)) return jsonError(Response.Status.UNAUTHORIZED, "Invalid or missing API key")
        if (!SamuEngineRouter.isLoaded()) {
            return jsonError(Response.Status.SERVICE_UNAVAILABLE, "No LLM model loaded")
        }

        val rawBody = readPostBody(session)
            ?: return jsonError(Response.Status.BAD_REQUEST, "Empty request body")
        val node = JSONObject(rawBody)
        val prompt = node.optString("prompt", "")
        if (prompt.isBlank()) return jsonError(Response.Status.BAD_REQUEST, "prompt is required")

        val stream = node.optBoolean("stream", false)
        val maxTokens = node.optInt("max_tokens", 1024).coerceIn(1, 16384)
        val temperature = node.optDouble("temperature", 0.7).coerceIn(0.0, 2.0)
        val topP = node.optDouble("top_p", 0.9).coerceIn(0.05, 1.0)
        val parsed = ParsedPrompt(prompt = prompt)

        return if (stream) {
            streamLlmResponse(parsed, maxTokens, temperature, topP, -1)
        } else {
            fullLlmResponse(parsed, maxTokens, temperature, topP, -1)
        }
    }

    // ==================== LLM EXECUTION (STREAM + FULL) ====================

    private fun executeGeneration(
        parsed: ParsedPrompt,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        onToken: (String) -> Unit
    ) {
        SamuEngineRouter.inferenceLock.lock()
        try {
            SamuEngineRouter.clearContext()
            when {
                parsed.visionRgb != null -> {
                    SamuEngineRouter.generateWithImage(
                        prompt = parsed.prompt,
                        rgbData = parsed.visionRgb,
                        width = parsed.visionWidth,
                        height = parsed.visionHeight,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topP = topP,
                        onToken = onToken
                    )
                }
                parsed.audioSamples != null -> {
                    SamuEngineRouter.generateWithAudio(
                        prompt = parsed.prompt,
                        audioSamples = parsed.audioSamples,
                        sampleRate = parsed.audioSampleRate,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topP = topP,
                        onToken = onToken
                    )
                }
                else -> {
                    SamuEngineRouter.generate(
                        prompt = parsed.prompt,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topP = topP,
                        onToken = onToken
                    )
                }
            }
        } finally {
            SamuEngineRouter.inferenceLock.unlock()
        }
    }

    private fun fullLlmResponse(
        parsed: ParsedPrompt,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        reasoningBudget: Int
    ): Response {
        val model = activeModelName()
        ServerMonitor.onRequestStart("POST /v1/chat/completions (non-stream · $model)")
        val sb = StringBuilder()
        var toks = 0
        return try {
            executeGeneration(parsed, maxTokens, temperature, topP) { tok ->
                sb.append(tok)
                toks++
                ServerMonitor.onTokenGenerated(1)
            }
            ServerMonitor.onRequestEnd(true, "Completed $toks tokens ($model)")
            val body = JSONObject().apply {
                put("id", "chatcmpl-samu-${System.currentTimeMillis()}")
                put("object", "chat.completion")
                put("created", System.currentTimeMillis() / 1000)
                put("model", model)
                put("choices", JSONArray().put(JSONObject().apply {
                    put("index", 0)
                    put("message", JSONObject().apply {
                        put("role", "assistant")
                        put("content", sb.toString())
                    })
                    put("finish_reason", "stop")
                }))
                put("usage", JSONObject().apply {
                    put("prompt_tokens", 0)
                    put("completion_tokens", toks)
                    put("total_tokens", SamuEngineRouter.tokensUsed().coerceAtLeast(toks))
                })
            }.toString()
            jsonResponse(Response.Status.OK, body)
        } catch (e: Exception) {
            ServerMonitor.onRequestEnd(false, "Generation failed: ${e.message}")
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Generation failed")
        }
    }

    private fun streamLlmResponse(
        parsed: ParsedPrompt,
        maxTokens: Int,
        temperature: Double,
        topP: Double,
        reasoningBudget: Int
    ): Response {
        val id = "chatcmpl-samu-${System.currentTimeMillis()}"
        val created = System.currentTimeMillis() / 1000
        val model = activeModelName()

        val resp = object : Response(Response.Status.OK, "text/event-stream; charset=utf-8", null, -1) {
            override fun send(outputStream: OutputStream) {
                ServerMonitor.onRequestStart("POST /v1/chat/completions (SSE · $model)")
                var toks = 0
                var clientAborted = false
                try {
                    val headerText = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/event-stream; charset=utf-8\r\n")
                        append("Cache-Control: no-cache, no-transform\r\n")
                        append("X-Accel-Buffering: no\r\n")
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
                        append("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
                        append("Connection: close\r\n")
                        append("Transfer-Encoding: chunked\r\n")
                        append("\r\n")
                    }
                    outputStream.write(headerText.toByteArray(Charsets.UTF_8))
                    outputStream.flush()

                    fun writeChunk(payload: String) {
                        val bytes = payload.toByteArray(Charsets.UTF_8)
                        outputStream.write(Integer.toHexString(bytes.size).toByteArray(Charsets.US_ASCII))
                        outputStream.write("\r\n".toByteArray(Charsets.US_ASCII))
                        if (bytes.isNotEmpty()) outputStream.write(bytes)
                        outputStream.write("\r\n".toByteArray(Charsets.US_ASCII))
                        outputStream.flush()
                    }

                    fun emitSse(obj: JSONObject) = writeChunk("data: $obj\n\n")

                    val initial = JSONObject().apply {
                        put("id", id)
                        put("object", "chat.completion.chunk")
                        put("created", created)
                        put("model", model)
                        put("choices", JSONArray().put(JSONObject().apply {
                            put("index", 0)
                            put("delta", JSONObject().put("role", "assistant"))
                            put("finish_reason", JSONObject.NULL)
                        }))
                    }
                    emitSse(initial)

                    executeGeneration(parsed, maxTokens, temperature, topP) { tok ->
                        if (clientAborted) return@executeGeneration
                        toks++
                        ServerMonitor.onTokenGenerated(1)
                        val chunk = JSONObject().apply {
                            put("id", id)
                            put("object", "chat.completion.chunk")
                            put("created", created)
                            put("model", model)
                            put("choices", JSONArray().put(JSONObject().apply {
                                put("index", 0)
                                put("delta", JSONObject().put("content", tok))
                                put("finish_reason", JSONObject.NULL)
                            }))
                        }
                        try {
                            emitSse(chunk)
                        } catch (_: Exception) {
                            clientAborted = true
                            SamuEngineRouter.stop()
                        }
                    }

                    if (!clientAborted) {
                        val doneChunk = JSONObject().apply {
                            put("id", id)
                            put("object", "chat.completion.chunk")
                            put("created", created)
                            put("model", model)
                            put("choices", JSONArray().put(JSONObject().apply {
                                put("index", 0)
                                put("delta", JSONObject())
                                put("finish_reason", "stop")
                            }))
                        }
                        emitSse(doneChunk)
                        writeChunk("data: [DONE]\n\n")
                        outputStream.write("0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        outputStream.flush()
                        ServerMonitor.onRequestEnd(true, "Streamed $toks tokens ($model)")
                    } else {
                        ServerMonitor.onRequestEnd(true, "Stream stopped by client after $toks tokens")
                    }
                } catch (e: Exception) {
                    ServerMonitor.onRequestEnd(false, "Stream error: ${e.message}")
                    try {
                        val errSse = "data: {\"error\":${JSONObject.quote(e.message ?: "stream error")}}\n\n"
                        val b = errSse.toByteArray(Charsets.UTF_8)
                        outputStream.write(Integer.toHexString(b.size).toByteArray(Charsets.US_ASCII))
                        outputStream.write("\r\n".toByteArray(Charsets.US_ASCII))
                        outputStream.write(b)
                        outputStream.write("\r\n0\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        outputStream.flush()
                    } catch (_: Exception) {}
                }
            }
        }
        resp.setGzipEncoding(false)
        return resp
    }

    // ==================== MULTIMODAL ENDPOINTS ====================

    private fun handleImageGen(session: IHTTPSession): Response {
        if (!checkAuth(session)) return jsonError(Response.Status.UNAUTHORIZED, "Unauthorized")
        val rawBody = readPostBody(session) ?: return jsonError(Response.Status.BAD_REQUEST, "Empty body")
        val node = JSONObject(rawBody)
        val prompt = node.optString("prompt", "").trim()
        if (prompt.isEmpty()) return jsonError(Response.Status.BAD_REQUEST, "prompt is required")

        val size = node.optString("size", "512x512")
        val steps = node.optInt("steps", 20)
        val initImg = node.optString("init_image_b64", "").takeIf { it.isNotBlank() }

        val imageModel = modelsProvider().firstOrNull { it.profile.kind == ModelKind.IMAGE_GEN }
        ServerMonitor.onRequestStart("POST /v1/images/generations")
        return try {
            val res = MediaEngines.generateImage(ctx, prompt, imageModel, size, steps, initImg)
            val b64 = Base64.encodeToString(res.file.readBytes(), Base64.NO_WRAP)
            val baseUrl = buildRequestBaseUrl(session)
            val fullUrl = "$baseUrl${res.relativeUrlPath}"
            ServerMonitor.onRequestEnd(true, "Image created: ${res.file.name}")
            val body = JSONObject().apply {
                put("created", System.currentTimeMillis() / 1000)
                put("url", fullUrl)
                put("local_path", res.file.absolutePath)
                put("b64_json", b64)
                put("data", JSONArray().put(JSONObject().apply {
                    put("url", fullUrl)
                    put("local_path", res.file.absolutePath)
                    put("b64_json", b64)
                }))
            }.toString()
            jsonResponse(Response.Status.OK, body)
        } catch (e: Exception) {
            ServerMonitor.onRequestEnd(false, "Image gen error: ${e.message}")
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Image generation failed")
        }
    }

    private fun handleAudioSpeech(session: IHTTPSession): Response {
        if (!checkAuth(session)) return jsonError(Response.Status.UNAUTHORIZED, "Unauthorized")
        val rawBody = readPostBody(session) ?: return jsonError(Response.Status.BAD_REQUEST, "Empty body")
        val node = JSONObject(rawBody)
        val text = node.optString("input", "").ifBlank { node.optString("prompt", "") }.trim()
        if (text.isEmpty()) return jsonError(Response.Status.BAD_REQUEST, "input or prompt is required")

        val ttsModel = modelsProvider().firstOrNull { it.profile.kind == ModelKind.AUDIO_TTS }
        ServerMonitor.onRequestStart("POST /v1/audio/speech")
        return try {
            val res = MediaEngines.generateAudio(ctx, text, ttsModel)
            val baseUrl = buildRequestBaseUrl(session)
            val fullUrl = "$baseUrl${res.relativeUrlPath}"
            ServerMonitor.onRequestEnd(true, "Audio created: ${res.file.name}")
            val r = newFixedLengthResponse(
                Response.Status.OK,
                res.mimeType,
                FileInputStream(res.file),
                res.file.length()
            )
            r.setGzipEncoding(false)
            addCors(r)
            r.addHeader("X-SaMu-Media-Url", fullUrl)
            r.addHeader("X-SaMu-Local-Path", res.file.absolutePath)
            r.addHeader("Content-Disposition", "inline; filename=\"${res.file.name}\"")
            r
        } catch (e: Exception) {
            ServerMonitor.onRequestEnd(false, "Audio TTS error: ${e.message}")
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Audio synthesis failed")
        }
    }

    private fun handleVideoGen(session: IHTTPSession): Response {
        if (!checkAuth(session)) return jsonError(Response.Status.UNAUTHORIZED, "Unauthorized")
        val rawBody = readPostBody(session) ?: return jsonError(Response.Status.BAD_REQUEST, "Empty body")
        val node = JSONObject(rawBody)
        val prompt = node.optString("prompt", "").trim()
        if (prompt.isEmpty()) return jsonError(Response.Status.BAD_REQUEST, "prompt is required")

        val frames = node.optInt("frames", 16)
        val initImg = node.optString("init_image_b64", "").takeIf { it.isNotBlank() }
        val videoModel = modelsProvider().firstOrNull { it.profile.kind == ModelKind.VIDEO_GEN }

        ServerMonitor.onRequestStart("POST /v1/videos/generations")
        return try {
            val res = MediaEngines.generateVideo(ctx, prompt, videoModel, frames, initImg)
            val baseUrl = buildRequestBaseUrl(session)
            val fullUrl = "$baseUrl${res.relativeUrlPath}"
            ServerMonitor.onRequestEnd(true, "Video created: ${res.file.name}")
            val body = JSONObject().apply {
                put("created", System.currentTimeMillis() / 1000)
                put("url", fullUrl)
                put("local_path", res.file.absolutePath)
                put("mime", res.mimeType)
            }.toString()
            jsonResponse(Response.Status.OK, body)
        } catch (e: Exception) {
            ServerMonitor.onRequestEnd(false, "Video gen error: ${e.message}")
            jsonError(Response.Status.INTERNAL_ERROR, e.message ?: "Video generation failed")
        }
    }

    private fun handleMediaFile(uriPath: String): Response {
        val parts = uriPath.removePrefix("/media/").split("/")
        if (parts.size != 2) return jsonError(Response.Status.BAD_REQUEST, "Invalid media path")
        val category = parts[0]
        val filename = parts[1]
        if (filename.isBlank() || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            return jsonError(Response.Status.FORBIDDEN, "Invalid filename")
        }
        val baseDir = when (category) {
            "images" -> AtomicStorage.imagesDir(ctx)
            "audio" -> AtomicStorage.audioDir(ctx)
            "videos" -> AtomicStorage.videosDir(ctx)
            else -> return jsonError(Response.Status.NOT_FOUND, "Unknown media category")
        }
        val target = File(baseDir, filename)
        if (!target.exists() || !target.isFile || !target.canonicalPath.startsWith(baseDir.canonicalPath)) {
            return jsonError(Response.Status.NOT_FOUND, "Media file not found")
        }
        val mime = when (target.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
        val r = newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(target), target.length())
        r.setGzipEncoding(false)
        addCors(r)
        return r
    }

    // ==================== PROMPT & MULTIMODAL PARSING ====================

    private data class ParsedPrompt(
        val prompt: String,
        val visionRgb: ByteArray? = null,
        val visionWidth: Int = 0,
        val visionHeight: Int = 0,
        val audioSamples: FloatArray? = null,
        val audioSampleRate: Int = 16000
    )

    private fun extractMultimodalPrompt(messages: JSONArray, skipThinking: Boolean): ParsedPrompt {
        val pairs = mutableListOf<Pair<String, String>>()
        var rgb: ByteArray? = null
        var imgW = 0
        var imgH = 0
        var audioSamples: FloatArray? = null
        var audioRate = 16000
        val marker = "<__media__>"

        for (i in 0 until messages.length()) {
            val m = messages.optJSONObject(i) ?: continue
            val role = m.optString("role", "user")
            val contentNode = m.opt("content")
            val textBuf = StringBuilder()

            when (contentNode) {
                is String -> textBuf.append(contentNode)
                is JSONArray -> {
                    for (j in 0 until contentNode.length()) {
                        val part = contentNode.optJSONObject(j) ?: continue
                        when (part.optString("type", "")) {
                            "text" -> {
                                if (textBuf.isNotEmpty()) textBuf.append("\n")
                                textBuf.append(part.optString("text", ""))
                            }
                            "image_url" -> {
                                val url = part.optJSONObject("image_url")?.optString("url", "")
                                    ?: part.optString("image_url", "")
                                val decoded = decodeImageRgb(url)
                                if (decoded != null) {
                                    rgb = decoded.first
                                    imgW = decoded.second
                                    imgH = decoded.third
                                    if (!textBuf.contains(marker)) {
                                        textBuf.insert(0, "$marker\n")
                                    }
                                }
                            }
                            "input_audio" -> {
                                val b64 = part.optJSONObject("input_audio")?.optString("data", "") ?: ""
                                val decoded = decodeAudioWav(b64)
                                if (decoded != null) {
                                    audioSamples = decoded.first
                                    audioRate = decoded.second
                                    if (!textBuf.contains(marker)) {
                                        textBuf.insert(0, "$marker\n")
                                    }
                                }
                            }
                            "audio_url" -> {
                                val url = part.optJSONObject("audio_url")?.optString("url", "") ?: ""
                                val b64 = if (url.startsWith("data:")) url.substringAfter(",") else url
                                val decoded = decodeAudioWav(b64)
                                if (decoded != null) {
                                    audioSamples = decoded.first
                                    audioRate = decoded.second
                                    if (!textBuf.contains(marker)) {
                                        textBuf.insert(0, "$marker\n")
                                    }
                                }
                            }
                        }
                    }
                }
            }
            pairs.add(role to textBuf.toString())
        }

        var prompt = applyChatTemplate(pairs)
        if (prompt.isBlank()) {
            val sb = StringBuilder()
            for ((role, content) in pairs) {
                sb.append("<|im_start|>").append(role).append("\n")
                sb.append(content).append("<|im_end|>\n")
            }
            sb.append("<|im_start|>assistant\n")
            prompt = sb.toString()
        }

        if (skipThinking && supportsThinking()) {
            val trimmed = prompt.trimEnd()
            prompt = when {
                trimmed.endsWith("<think>") -> "$prompt\n</think>\n\n"
                trimmed.endsWith("</think>") -> prompt
                else -> "$prompt<think>\n</think>\n\n"
            }
        }

        return ParsedPrompt(
            prompt = prompt,
            visionRgb = rgb,
            visionWidth = imgW,
            visionHeight = imgH,
            audioSamples = audioSamples,
            audioSampleRate = audioRate
        )
    }

    private fun decodeImageRgb(urlOrB64: String): Triple<ByteArray, Int, Int>? {
        val b64 = if (urlOrB64.startsWith("data:")) urlOrB64.substringAfter(",", "") else urlOrB64
        if (b64.isBlank()) return null
        return try {
            val bytes = Base64.decode(b64.trim(), Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val w = bmp.width
            val h = bmp.height
            val px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            val out = ByteArray(w * h * 3)
            var k = 0
            for (p in px) {
                out[k++] = ((p shr 16) and 0xFF).toByte()
                out[k++] = ((p shr 8) and 0xFF).toByte()
                out[k++] = (p and 0xFF).toByte()
            }
            bmp.recycle()
            Triple(out, w, h)
        } catch (_: Exception) {
            null
        }
    }

    private fun decodeAudioWav(b64: String): Pair<FloatArray, Int>? {
        if (b64.isBlank()) return null
        return try {
            val bytes = Base64.decode(b64.trim(), Base64.DEFAULT)
            if (bytes.size < 44) return null
            val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return null
            var pos = 12
            var sampleRate = 16000
            var channels = 1
            var bitsPerSample = 16
            var dataOffset = -1
            var dataSize = 0
            while (pos + 8 <= bytes.size) {
                val chunkId = String(bytes, pos, 4)
                val chunkSize = bb.getInt(pos + 4)
                if (chunkId == "fmt ") {
                    channels = bb.getShort(pos + 10).toInt().coerceAtLeast(1)
                    sampleRate = bb.getInt(pos + 12).coerceAtLeast(8000)
                    bitsPerSample = bb.getShort(pos + 22).toInt().coerceAtLeast(8)
                } else if (chunkId == "data") {
                    dataOffset = pos + 8
                    dataSize = chunkSize
                    break
                }
                pos += 8 + chunkSize + (chunkSize and 1)
            }
            if (dataOffset < 0 || dataSize <= 0) return null
            val bytesPerSample = (bitsPerSample / 8).coerceAtLeast(1)
            val end = (dataOffset + dataSize).coerceAtMost(bytes.size)
            val totalSamples = (end - dataOffset) / bytesPerSample
            val samples = FloatArray(totalSamples)
            for (k in 0 until totalSamples) {
                val off = dataOffset + k * bytesPerSample
                samples[k] = when (bitsPerSample) {
                    16 -> bb.getShort(off) / 32768f
                    8 -> ((bytes[off].toInt() and 0xFF) - 128) / 128f
                    32 -> bb.getFloat(off)
                    else -> 0f
                }
            }
            if (channels > 1) {
                val mono = FloatArray(samples.size / channels)
                for (k in mono.indices) {
                    var sum = 0f
                    for (c in 0 until channels) sum += samples[k * channels + c]
                    mono[k] = sum / channels
                }
                mono to sampleRate
            } else {
                samples to sampleRate
            }
        } catch (_: Exception) {
            null
        }
    }

    // ==================== HELPERS ====================

    private fun readPostBody(session: IHTTPSession): String? {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"]?.takeIf { it.isNotBlank() }
    }

    private fun buildRequestBaseUrl(session: IHTTPSession): String {
        val host = session.headers["host"] ?: "127.0.0.1:${settingsProvider().port}"
        return "http://$host"
    }

    private fun jsonResponse(status: Response.Status, body: String): Response {
        val r = newFixedLengthResponse(status, "application/json; charset=utf-8", body)
        addCors(r)
        return r
    }

    private fun jsonError(status: Response.Status, msg: String): Response =
        jsonResponse(status, JSONObject().put("error", JSONObject().put("message", msg)).toString())

    private fun corsPreflight(): Response {
        val r = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
        addCors(r)
        return r
    }

    private fun addCors(r: Response) {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        r.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        r.addHeader("Access-Control-Expose-Headers", "X-SaMu-Media-Url, X-SaMu-Local-Path")
    }
}
