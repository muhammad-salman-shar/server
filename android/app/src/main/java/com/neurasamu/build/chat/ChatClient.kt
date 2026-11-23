package com.neurasamu.build.chat

import android.content.Context
import android.util.Base64
import com.neurasamu.build.data.ApiConfig
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.ChatMessage
import com.neurasamu.build.data.GeneratedMedia
import com.neurasamu.build.data.MediaKind
import com.neurasamu.build.data.ThinkingEffort
import com.neurasamu.build.data.UrlSanitizer
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class StreamAbortHandle {
    private val aborted = AtomicBoolean(false)
    private val skipRequested = AtomicBoolean(false)
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    fun attach(conn: HttpURLConnection) {
        activeConnection.set(conn)
        if (aborted.get()) {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    fun abort() {
        aborted.set(true)
        try { activeConnection.get()?.disconnect() } catch (_: Exception) {}
    }

    fun abortForSkip() {
        skipRequested.set(true)
        aborted.set(true)
        try { activeConnection.get()?.disconnect() } catch (_: Exception) {}
    }

    fun isAborted(): Boolean = aborted.get()
    fun isSkipRequested(): Boolean = skipRequested.get()
}

object ChatClient {

    fun streamChatCompletion(
        api: ApiConfig,
        history: List<ChatMessage>,
        globalInstructions: String?,
        thinkingEffort: ThinkingEffort,
        forceSkipThinking: Boolean,
        abortHandle: StreamAbortHandle,
        onUpdate: (fullText: String) -> Unit
    ): String {
        val urlStr = UrlSanitizer.resolveEndpoint(api.baseUrl, "/v1/chat/completions")
        if (urlStr.isBlank()) throw IllegalArgumentException("API Base URL is empty")

        val messagesArr = JSONArray()

        val combinedSystem = listOfNotNull(
            globalInstructions?.trim()?.takeIf { it.isNotEmpty() },
            api.systemPrompt.trim().takeIf { it.isNotEmpty() }
        ).joinToString("\n\n")

        if (combinedSystem.isNotEmpty()) {
            messagesArr.put(JSONObject().apply {
                put("role", "system")
                put("content", combinedSystem)
            })
        }

        history.filter { !it.isError }.forEach { msg ->
            if (msg.role == "user") {
                val content = FileAnalyzer.toOpenAiContentParts(msg.content, msg.attachments)
                messagesArr.put(JSONObject().apply {
                    put("role", "user")
                    put("content", content)
                })
            } else if (msg.role == "assistant") {
                val cleanText = stripThinkTagsForHistory(msg.content)
                if (cleanText.isNotBlank()) {
                    messagesArr.put(JSONObject().apply {
                        put("role", "assistant")
                        put("content", cleanText)
                    })
                }
            }
        }

        val effectiveEffort = if (forceSkipThinking) ThinkingEffort.OFF else thinkingEffort
        val payload = JSONObject().apply {
            put("model", api.modelName.ifBlank { "samu-local" })
            put("messages", messagesArr)
            put("stream", true)
            when (effectiveEffort) {
                ThinkingEffort.OFF -> {
                    put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
                    put("reasoning_effort", "none")
                    put("reasoning_budget", 0)
                }
                ThinkingEffort.AUTO -> {
                    put("chat_template_kwargs", JSONObject().put("enable_thinking", true))
                }
                ThinkingEffort.LOW, ThinkingEffort.HIGH, ThinkingEffort.MAX, ThinkingEffort.ULTRA -> {
                    put("chat_template_kwargs", JSONObject().put("enable_thinking", true))
                    effectiveEffort.reasoningEffort?.let { put("reasoning_effort", it) }
                    put("reasoning_budget", effectiveEffort.budget)
                }
            }
        }

        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            useCaches = false
            connectTimeout = 20_000
            readTimeout = 0
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Cache-Control", "no-cache")
            if (api.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${api.apiKey.trim()}")
            }
        }
        abortHandle.attach(conn)

        val accumulated = StringBuilder()
        var inReasoningBlock = false
        var lastEmitMs = 0L

        fun emitNow(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (force || now - lastEmitMs >= 30L) {
                lastEmitMs = now
                onUpdate(accumulated.toString())
            }
        }

        try {
            conn.outputStream.use { os ->
                os.write(payload.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = try {
                    conn.errorStream?.bufferedReader()?.readText()
                } catch (_: Exception) {
                    null
                }
                val parsedMsg = parseErrorMessage(errBody) ?: "HTTP $code ${conn.responseMessage}"
                throw IllegalStateException(parsedMsg)
            }

            val contentType = conn.contentType?.lowercase() ?: ""
            if (contentType.contains("application/json") && !contentType.contains("event-stream")) {
                val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                val text = parseNonStreamingJson(raw)
                accumulated.append(text)
                emitNow(force = true)
                return accumulated.toString()
            }

            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8), 256).use { reader ->
                while (true) {
                    if (abortHandle.isAborted()) break
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isEmpty() || trimmed.startsWith(":")) continue

                    if (trimmed.startsWith("data:")) {
                        val data = trimmed.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        if (data.isEmpty()) continue

                        try {
                            val obj = JSONObject(data)
                            if (obj.has("error")) {
                                val errObj = obj.opt("error")
                                val msg = when (errObj) {
                                    is JSONObject -> errObj.optString("message", errObj.toString())
                                    else -> errObj?.toString() ?: "Stream error"
                                }
                                throw IllegalStateException(msg)
                            }
                            val choices = obj.optJSONArray("choices")
                            val first = choices?.optJSONObject(0)
                            val delta = first?.optJSONObject("delta")
                            if (delta != null) {
                                val reasoning = delta.optString("reasoning_content", "")
                                    .ifEmpty { delta.optString("reasoning", "") }
                                val content = delta.optString("content", "")

                                if (reasoning.isNotEmpty()) {
                                    if (!inReasoningBlock) {
                                        accumulated.append("<think>\n")
                                        inReasoningBlock = true
                                    }
                                    accumulated.append(reasoning)
                                    emitNow()
                                }
                                if (content.isNotEmpty()) {
                                    if (inReasoningBlock) {
                                        accumulated.append("\n</think>\n\n")
                                        inReasoningBlock = false
                                    }
                                    accumulated.append(content)
                                    emitNow()
                                }
                            } else {
                                // Non-streaming fallback wrapped in data:
                                val msgContent = first?.optJSONObject("message")?.optString("content", "")
                                if (!msgContent.isNullOrEmpty()) {
                                    accumulated.append(msgContent)
                                    emitNow(force = true)
                                }
                            }
                        } catch (e: IllegalStateException) {
                            throw e
                        } catch (_: Exception) {
                            // Ignore malformed keepalive chunks
                        }
                    } else if (trimmed.startsWith("{")) {
                        // Raw JSON fallback
                        val text = parseNonStreamingJson(trimmed)
                        if (text.isNotEmpty()) {
                            accumulated.append(text)
                            emitNow(force = true)
                        }
                    }
                }
            }

            if (inReasoningBlock) {
                accumulated.append("\n</think>\n\n")
            }
            if (abortHandle.isAborted() && !abortHandle.isSkipRequested()) {
                if (accumulated.isNotEmpty()) {
                    accumulated.append("\n\n[stopped]")
                }
            }
            emitNow(force = true)
            return accumulated.toString()
        } catch (e: Exception) {
            if (abortHandle.isSkipRequested()) {
                return accumulated.toString()
            }
            if (abortHandle.isAborted()) {
                if (accumulated.isNotEmpty()) {
                    accumulated.append("\n\n[stopped]")
                    emitNow(force = true)
                }
                return accumulated.toString()
            }
            if (accumulated.isNotEmpty()) {
                accumulated.append("\n\n[connection lost]")
                emitNow(force = true)
                return accumulated.toString()
            }
            throw e
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    // ==================== MULTIMODAL CLIENT CALLS ====================

    fun generateImage(
        ctx: Context,
        api: ApiConfig,
        prompt: String,
        refImagePath: String?,
        abortHandle: StreamAbortHandle
    ): GeneratedMedia {
        val urlStr = UrlSanitizer.resolveEndpoint(api.baseUrl, "/v1/images/generations")
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("size", "512x512")
            put("steps", 20)
            put("response_format", "b64_json")
            if (!refImagePath.isNullOrBlank()) {
                val f = File(refImagePath)
                if (f.exists()) {
                    put("init_image_b64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
            }
        }
        val conn = openJsonPost(urlStr, api.apiKey, payload, abortHandle)
        try {
            val raw = readResponseOrThrow(conn)
            val obj = JSONObject(raw)
            val dataItem = obj.optJSONArray("data")?.optJSONObject(0)
            val localPathFromServer = obj.optString("local_path", "")
                .ifBlank { dataItem?.optString("local_path", "") ?: "" }
            val remoteUrl = obj.optString("url", "")
                .ifBlank { dataItem?.optString("url", "") ?: "" }
            val b64 = obj.optString("b64_json", "")
                .ifBlank { dataItem?.optString("b64_json", "") ?: "" }

            val savedFile = when {
                localPathFromServer.isNotBlank() && File(localPathFromServer).exists() -> File(localPathFromServer)
                b64.isNotBlank() -> {
                    val out = File(AtomicStorage.imagesDir(ctx), "img_${System.currentTimeMillis()}.png")
                    out.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                    out
                }
                remoteUrl.isNotBlank() -> downloadToFile(remoteUrl, File(AtomicStorage.imagesDir(ctx), "img_${System.currentTimeMillis()}.png"), api.apiKey)
                else -> throw IllegalStateException("Server returned no image data")
            }

            return GeneratedMedia(
                kind = MediaKind.IMAGE,
                localPath = savedFile.absolutePath,
                remoteUrl = remoteUrl,
                fileName = savedFile.name,
                mimeType = "image/png",
                prompt = prompt,
                sizeBytes = savedFile.length(),
                refImagePath = refImagePath
            )
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    fun generateAudio(
        ctx: Context,
        api: ApiConfig,
        text: String,
        abortHandle: StreamAbortHandle
    ): GeneratedMedia {
        val urlStr = UrlSanitizer.resolveEndpoint(api.baseUrl, "/v1/audio/speech")
        val payload = JSONObject().apply {
            put("model", api.modelName.ifBlank { "tts-1" })
            put("input", text)
            put("prompt", text)
            put("response_format", "wav")
        }
        val conn = openJsonPost(urlStr, api.apiKey, payload, abortHandle)
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
                throw IllegalStateException(parseErrorMessage(errBody) ?: "HTTP $code")
            }
            val serverLocal = conn.getHeaderField("X-SaMu-Local-Path")
            val serverUrl = conn.getHeaderField("X-SaMu-Media-Url") ?: ""
            val bytes = conn.inputStream.readBytes()

            val savedFile = if (!serverLocal.isNullOrBlank() && File(serverLocal).exists()) {
                File(serverLocal)
            } else {
                val out = File(AtomicStorage.audioDir(ctx), "tts_${System.currentTimeMillis()}.wav")
                out.writeBytes(bytes)
                out
            }

            return GeneratedMedia(
                kind = MediaKind.AUDIO,
                localPath = savedFile.absolutePath,
                remoteUrl = serverUrl,
                fileName = savedFile.name,
                mimeType = "audio/wav",
                prompt = text,
                sizeBytes = savedFile.length()
            )
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    fun generateVideo(
        ctx: Context,
        api: ApiConfig,
        prompt: String,
        refImagePath: String?,
        abortHandle: StreamAbortHandle
    ): GeneratedMedia {
        val urlStr = UrlSanitizer.resolveEndpoint(api.baseUrl, "/v1/videos/generations")
        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("frames", 16)
            if (!refImagePath.isNullOrBlank()) {
                val f = File(refImagePath)
                if (f.exists()) {
                    put("init_image_b64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                }
            }
        }
        val conn = openJsonPost(urlStr, api.apiKey, payload, abortHandle)
        try {
            val raw = readResponseOrThrow(conn)
            val obj = JSONObject(raw)
            val localPath = obj.optString("local_path", "")
            val remoteUrl = obj.optString("url", "")
            val mime = obj.optString("mime", "video/mp4")

            val savedFile = when {
                localPath.isNotBlank() && File(localPath).exists() -> File(localPath)
                remoteUrl.isNotBlank() -> downloadToFile(remoteUrl, File(AtomicStorage.videosDir(ctx), "vid_${System.currentTimeMillis()}.mp4"), api.apiKey)
                else -> throw IllegalStateException("Server returned no video file")
            }

            return GeneratedMedia(
                kind = MediaKind.VIDEO,
                localPath = savedFile.absolutePath,
                remoteUrl = remoteUrl,
                fileName = savedFile.name,
                mimeType = mime,
                prompt = prompt,
                sizeBytes = savedFile.length(),
                refImagePath = refImagePath
            )
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }

    // ==================== HELPERS ====================

    private fun openJsonPost(
        urlStr: String,
        apiKey: String,
        payload: JSONObject,
        abortHandle: StreamAbortHandle
    ): HttpURLConnection {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            connectTimeout = 20_000
            readTimeout = 0
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept-Encoding", "identity")
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            }
        }
        abortHandle.attach(conn)
        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
            os.flush()
        }
        return conn
    }

    private fun readResponseOrThrow(conn: HttpURLConnection): String {
        val code = conn.responseCode
        if (code !in 200..299) {
            val errBody = try { conn.errorStream?.bufferedReader()?.readText() } catch (_: Exception) { null }
            throw IllegalStateException(parseErrorMessage(errBody) ?: "HTTP $code ${conn.responseMessage}")
        }
        return conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    }

    private fun downloadToFile(urlStr: String, target: File, apiKey: String): File {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
        }
        try {
            target.outputStream().use { out -> conn.inputStream.copyTo(out) }
            return target
        } finally {
            conn.disconnect()
        }
    }

    private fun parseNonStreamingJson(raw: String): String {
        return try {
            val obj = JSONObject(raw)
            val msg = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
            val reasoning = msg?.optString("reasoning_content", "").orEmpty()
            val content = msg?.optString("content", "").orEmpty()
            if (reasoning.isNotBlank()) "<think>\n$reasoning\n</think>\n\n$content" else content
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseErrorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(raw)
            val err = obj.opt("error")
            when (err) {
                is JSONObject -> err.optString("message", err.toString())
                is String -> err
                else -> obj.optString("message", raw.take(240))
            }
        } catch (_: Exception) {
            raw.take(240)
        }
    }

    private fun stripThinkTagsForHistory(text: String): String {
        return text.replace(Regex("<think>[\\s\\S]*?</think>"), "").trim()
    }
}
