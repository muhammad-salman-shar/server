package com.neurasamu.build.server

import android.content.Context
import com.neurasamu.build.engine.SamuEngineRouter
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

class SamuHttpServer(
    private val ctx: Context,
    port: Int = 8080,
    hostname: String = "0.0.0.0"
) : NanoHTTPD(hostname, port) {

    @Volatile var validKeys: Set<String> = emptySet()
    @Volatile var requireAuth: Boolean = false
    @Volatile var onRequest: ((String?) -> Unit)? = null
    @Volatile var displayNameOverride: String? = null

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/health" && session.method == Method.GET -> health()
                session.uri == "/v1/models" && session.method == Method.GET -> listModels()
                session.uri == "/v1/chat/completions" && session.method == Method.POST -> chat(session)
                session.uri == "/v1/completions" && session.method == Method.POST -> completions(session)
                session.method == Method.OPTIONS -> corsOk()
                else -> err(Response.Status.NOT_FOUND, "not found")
            }
        } catch (e: Exception) {
            err(Response.Status.INTERNAL_ERROR, e.message ?: "server error")
        }
    }

    private fun activeName(): String =
        displayNameOverride?.takeIf { it.isNotBlank() }
            ?: SamuRuntime.activeModel?.apiName
            ?: "no-model"

    private fun health(): Response =
        json(Response.Status.OK, """{"status":"ok","model":${JSONObject.quote(activeName())}}""")

    private fun listModels(): Response {
        val name = activeName()
        val body = """{"object":"list","data":[{"id":${JSONObject.quote(name)},"object":"model","owned_by":"samu-lab"}]}"""
        return json(Response.Status.OK, body)
    }

    private fun checkAuth(session: IHTTPSession): Boolean {
        if (!requireAuth) return true  // auth explicitly disabled
        if (validKeys.isEmpty()) return false  // auth ON but no keys — reject all
        val h = session.headers["authorization"] ?: return false
        val presented = h.removePrefix("Bearer ").trim()
        val ok = validKeys.contains(presented)
        if (ok) onRequest?.invoke(presented)
        return ok
    }


    private data class AudioInput(
        val promptWithMarker: String,
        val samples: FloatArray,
        val sampleRate: Int
    )

    private fun extractAudioInput(messages: JSONArray): AudioInput? {
        val sb = StringBuilder()
        var samples: FloatArray? = null
        var rate = 16000
        val marker = "<__media__>"

        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role", "user")
            val contentNode = m.opt("content")
            val textBuf = StringBuilder()

            if (contentNode is String) {
                textBuf.append(contentNode)
            } else if (contentNode is JSONArray) {
                for (j in 0 until contentNode.length()) {
                    val part = contentNode.getJSONObject(j)
                    when (part.optString("type", "")) {
                        "text" -> textBuf.append(part.optString("text", ""))
                        "input_audio" -> {
                            val audioObj = try { part.getJSONObject("input_audio") } catch (_: Exception) { null }
                            val b64 = audioObj?.optString("data", "") ?: ""
                            if (b64.isNotEmpty()) {
                                try {
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    val dec = decodeWav(bytes)
                                    if (dec != null) {
                                        samples = dec.first
                                        rate = dec.second
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        "audio_url" -> {
                            val urlObj = try { part.getJSONObject("audio_url") } catch (_: Exception) { null }
                            val url = urlObj?.optString("url", "") ?: ""
                            val b64 = if (url.startsWith("data:")) url.substringAfter(",", "") else url
                            if (b64.isNotEmpty()) {
                                try {
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    val dec = decodeWav(bytes)
                                    if (dec != null) {
                                        samples = dec.first
                                        rate = dec.second
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            sb.append("<start_of_turn>").append(role).append("\n")
            sb.append(textBuf).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")

        return if (samples != null) AudioInput("$marker\n$sb", samples!!, rate) else null
    }

    private fun decodeWav(bytes: ByteArray): Pair<FloatArray, Int>? {
        if (bytes.size < 44) return null
        val bb = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
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
                channels = bb.getShort(pos + 10).toInt()
                sampleRate = bb.getInt(pos + 12)
                bitsPerSample = bb.getShort(pos + 22).toInt()
            } else if (chunkId == "data") {
                dataOffset = pos + 8
                dataSize = chunkSize
                break
            }
            pos += 8 + chunkSize + (chunkSize and 1)
        }

        if (dataOffset < 0 || dataSize <= 0) return null
        val end = (dataOffset + dataSize).coerceAtMost(bytes.size)
        val totalSamples = (end - dataOffset) / (bitsPerSample / 8)
        val floatSamples = FloatArray(totalSamples)

        for (k in 0 until totalSamples) {
            val off = dataOffset + k * (bitsPerSample / 8)
            floatSamples[k] = when (bitsPerSample) {
                16 -> bb.getShort(off) / 32768f
                8 -> ((bytes[off].toInt() and 0xFF) - 128) / 128f
                32 -> bb.getFloat(off)
                else -> 0f
            }
        }

        if (channels > 1) {
            val mono = FloatArray(floatSamples.size / channels)
            for (k in mono.indices) {
                var sum = 0f
                for (c in 0 until channels) sum += floatSamples[k * channels + c]
                mono[k] = sum / channels
            }
            return mono to sampleRate
        }
        return floatSamples to sampleRate
    }

    private fun fullChatAudio(a: AudioInput, maxTokens: Int, temperature: Double, topP: Double): Response {
        try { SamuEngineRouter.clearContext() } catch (_: Exception) {}
        val sb = StringBuilder()
        SamuEngineRouter.generateWithAudio(
            prompt = a.promptWithMarker,
            audioSamples = a.samples,
            sampleRate = a.sampleRate,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            onToken = { sb.append(it) }
        )
        val text = sb.toString()
        val body = JSONObject().apply {
            put("id", "chatcmpl-samu-${System.currentTimeMillis()}")
            put("object", "chat.completion")
            put("created", System.currentTimeMillis() / 1000)
            put("model", activeName())
            put("choices", JSONArray().put(JSONObject().apply {
                put("index", 0)
                put("message", JSONObject().apply {
                    put("role", "assistant")
                    put("content", text)
                })
                put("finish_reason", "stop")
            }))
            put("usage", JSONObject().apply {
                put("prompt_tokens", 0)
                put("completion_tokens", 0)
                put("total_tokens", SamuEngineRouter.tokensUsed())
            })
        }.toString()
        return json(Response.Status.OK, body)
    }

    private data class VisionInput(
        val promptWithMarker: String,
        val rgb: ByteArray,
        val width: Int,
        val height: Int
    )

    private fun extractVisionInput(messages: JSONArray): VisionInput? {
        val sb = StringBuilder()
        var rgb: ByteArray? = null
        var w = 0
        var h = 0
        val marker = "<__media__>"

        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role", "user")
            val contentNode = m.opt("content")
            val textBuf = StringBuilder()

            if (contentNode is String) {
                textBuf.append(contentNode)
            } else if (contentNode is JSONArray) {
                for (j in 0 until contentNode.length()) {
                    val part = contentNode.getJSONObject(j)
                    when (part.optString("type", "")) {
                        "text" -> textBuf.append(part.optString("text", ""))
                        "image_url" -> {
                            val url = try {
                                part.getJSONObject("image_url").optString("url", "")
                            } catch (_: Exception) { "" }
                            val b64 = if (url.startsWith("data:")) url.substringAfter(",", "") else url
                            if (b64.isNotEmpty()) {
                                try {
                                    val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                    if (bmp != null) {
                                        w = bmp.width
                                        h = bmp.height
                                        val px = IntArray(w * h)
                                        bmp.getPixels(px, 0, w, 0, 0, w, h)
                                        val out = ByteArray(w * h * 3)
                                        var k = 0
                                        for (p in px) {
                                            out[k++] = ((p shr 16) and 0xFF).toByte()
                                            out[k++] = ((p shr 8) and 0xFF).toByte()
                                            out[k++] = (p and 0xFF).toByte()
                                        }
                                        rgb = out
                                        bmp.recycle()
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            sb.append("<start_of_turn>").append(role).append("\n")
            sb.append(textBuf).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")

        return if (rgb != null) {
            VisionInput("$marker\n$sb", rgb, w, h)
        } else null
    }

    private fun fullChatVision(v: VisionInput, maxTokens: Int, temperature: Double, topP: Double): Response {
        try { SamuEngineRouter.clearContext() } catch (_: Exception) {}
        val sb = StringBuilder()
        SamuEngineRouter.generateWithImage(
            prompt = v.promptWithMarker,
            rgbData = v.rgb,
            width = v.width,
            height = v.height,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            onToken = { sb.append(it) }
        )
        val text = sb.toString()
        val body = JSONObject().apply {
            put("id", "chatcmpl-samu-${System.currentTimeMillis()}")
            put("object", "chat.completion")
            put("created", System.currentTimeMillis() / 1000)
            put("model", activeName())
            put("choices", JSONArray().put(JSONObject().apply {
                put("index", 0)
                put("message", JSONObject().apply {
                    put("role", "assistant")
                    put("content", text)
                })
                put("finish_reason", "stop")
            }))
            put("usage", JSONObject().apply {
                put("prompt_tokens", 0)
                put("completion_tokens", 0)
                put("total_tokens", SamuEngineRouter.tokensUsed())
            })
        }.toString()
        return json(Response.Status.OK, body)
    }
    private fun chat(session: IHTTPSession): Response {
        if (!SamuEngineRouter.isLoaded()) return err(Response.Status.SERVICE_UNAVAILABLE, "no model loaded")
        if (!checkAuth(session)) return err(Response.Status.UNAUTHORIZED, "unauthorized")

        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: return err(Response.Status.BAD_REQUEST, "empty body")
        val node = JSONObject(raw)
        val messages = node.optJSONArray("messages")
            ?: return err(Response.Status.BAD_REQUEST, "messages[] required")

        // Vision path — if message has image_url, use multimodal
        val visionInput = extractVisionInput(messages)
        if (visionInput != null) {
            if (!SamuEngine.isMmprojLoaded()) {
                return err(Response.Status.BAD_REQUEST, "vision model but no mmproj loaded — add mmproj-*.gguf to model folder")
            }
            return fullChatVision(visionInput, node.optInt("max_tokens", 512).coerceIn(1, 4096),
                node.optDouble("temperature", 0.7), node.optDouble("top_p", 0.9))
        }
        val audioInput = extractAudioInput(messages)
        if (audioInput != null) {
            if (!SamuEngine.isMmprojLoaded()) {
                return err(Response.Status.BAD_REQUEST, "audio model but no mmproj loaded — add mmproj-*.gguf to model folder")
            }
            return fullChatAudio(audioInput, node.optInt("max_tokens", 512).coerceIn(1, 4096),
                node.optDouble("temperature", 0.7), node.optDouble("top_p", 0.9))
        }
        val prompt = buildChatPrompt(messages)
        val temperature = node.optDouble("temperature", 0.7)
        val topP = node.optDouble("top_p", 0.9)
        val stream = node.optBoolean("stream", false)

        val maxTokens = node.optInt("max_tokens", 512).coerceIn(1, 4096)
        return if (stream) streamChat(prompt, maxTokens, temperature, topP)
        else fullChat(prompt, maxTokens, temperature, topP)
    }

    private fun completions(session: IHTTPSession): Response {
        if (!SamuEngineRouter.isLoaded()) return err(Response.Status.SERVICE_UNAVAILABLE, "no model loaded")
        if (!checkAuth(session)) return err(Response.Status.UNAUTHORIZED, "unauthorized")

        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: return err(Response.Status.BAD_REQUEST, "empty body")
        val node = JSONObject(raw)
        val messages = node.optJSONArray("messages")
            ?: return err(Response.Status.BAD_REQUEST, "messages[] required")
        val prompt = node.optString("prompt", "")
        if (prompt.isBlank()) return err(Response.Status.BAD_REQUEST, "prompt required")
        val maxTokens = node.optInt("max_tokens", 512).coerceIn(1, 4096)
        val temperature = node.optDouble("temperature", 0.7)
        val topP = node.optDouble("top_p", 0.9)
        val stream = node.optBoolean("stream", false)

        return if (stream) streamChat(prompt, maxTokens, temperature, topP)
        else fullChat(prompt, maxTokens, temperature, topP)
    }

    private fun fullChat(prompt: String, maxTokens: Int, temperature: Double, topP: Double): Response {
        // Stateless per request — clear KV cache so this call is independent
        try { SamuEngineRouter.clearContext() } catch (_: Exception) {}
        val sb = StringBuilder()
        SamuEngineRouter.generate(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            onToken = { sb.append(it) }
        )
        val text = sb.toString()
        val body = JSONObject().apply {
            put("id", "chatcmpl-samu-${System.currentTimeMillis()}")
            put("object", "chat.completion")
            put("created", System.currentTimeMillis() / 1000)
            put("model", activeName())
            put("choices", JSONArray().put(JSONObject().apply {
                put("index", 0)
                put("message", JSONObject().apply {
                    put("role", "assistant")
                    put("content", text)
                })
                put("finish_reason", "stop")
            }))
            put("usage", JSONObject().apply {
                put("prompt_tokens", 0)
                put("completion_tokens", 0)
                put("total_tokens", SamuEngineRouter.tokensUsed())
            })
        }.toString()
        return json(Response.Status.OK, body)
    }

    /**
     * Streaming via direct socket write. Bypasses NanoHTTPD's pipe-based
     * chunked response, which deadlocks with cross-thread producers.
     */
    private fun streamChat(prompt: String, maxTokens: Int,
                           temperature: Double, topP: Double): Response {
        val id = "chatcmpl-samu-${System.currentTimeMillis()}"
        val created = System.currentTimeMillis() / 1000
        val model = activeName()

        return object : Response(Response.Status.OK, "text/event-stream", null, -1) {
            override fun send(outputStream: OutputStream) {
                try {
                    // status line + headers
                    val headers = buildString {
                        append("HTTP/1.1 200 OK\r\n")
                        append("Content-Type: text/event-stream; charset=utf-8\r\n")
                        append("Cache-Control: no-cache\r\n")
                        append("Access-Control-Allow-Origin: *\r\n")
                        append("Access-Control-Allow-Headers: Content-Type, Authorization\r\n")
                        append("Connection: close\r\n")
                        append("Transfer-Encoding: chunked\r\n")
                        append("\r\n")
                    }
                    outputStream.write(headers.toByteArray())
                    outputStream.flush()

                    fun writeChunk(s: String) {
                        val bytes = s.toByteArray(Charsets.UTF_8)
                        outputStream.write(Integer.toHexString(bytes.size).toByteArray())
                        outputStream.write("\r\n".toByteArray())
                        outputStream.write(bytes)
                        outputStream.write("\r\n".toByteArray())
                        outputStream.flush()
                    }

                    fun sse(obj: JSONObject) = writeChunk("data: $obj\n\n")

                    val first = JSONObject().apply {
                        put("id", id); put("object", "chat.completion.chunk")
                        put("created", created); put("model", model)
                        put("choices", JSONArray().put(JSONObject().apply {
                            put("index", 0)
                            put("delta", JSONObject().put("role", "assistant"))
                            put("finish_reason", JSONObject.NULL)
                        }))
                    }
                    sse(first)
                    // Stateless per request — clear KV cache before this generation
                    try { SamuEngineRouter.clearContext() } catch (_: Exception) {}

                    SamuEngineRouter.generate(
                        prompt = prompt,
                        maxTokens = maxTokens,
                        temperature = temperature,
                        topP = topP,
                        onToken = { tok ->
                            val chunk = JSONObject().apply {
                                put("id", id); put("object", "chat.completion.chunk")
                                put("created", created); put("model", model)
                                put("choices", JSONArray().put(JSONObject().apply {
                                    put("index", 0)
                                    put("delta", JSONObject().put("content", tok))
                                    put("finish_reason", JSONObject.NULL)
                                }))
                            }
                            sse(chunk)
                        }
                    )

                    val end = JSONObject().apply {
                        put("id", id); put("object", "chat.completion.chunk")
                        put("created", created); put("model", model)
                        put("choices", JSONArray().put(JSONObject().apply {
                            put("index", 0)
                            put("delta", JSONObject())
                            put("finish_reason", "stop")
                        }))
                    }
                    sse(end)
                    writeChunk("data: [DONE]\n\n")
                    writeChunk("")   // terminates chunked stream
                } catch (e: Exception) {
                    try {
                        val err = "data: {\"error\":${JSONObject.quote(e.message ?: "err")}}\n\n"
                        val b = err.toByteArray()
                        outputStream.write(Integer.toHexString(b.size).toByteArray())
                        outputStream.write("\r\n".toByteArray())
                        outputStream.write(b)
                        outputStream.write("\r\n".toByteArray())
                        outputStream.write("0\r\n\r\n".toByteArray())
                        outputStream.flush()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    private fun buildChatPrompt(messages: JSONArray): String {
        val pairs = mutableListOf<Pair<String, String>>()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role", "user")
            val content = m.optString("content", "")
            pairs.add(role to content)
        }
        val applied = applyChatTemplate(pairs)
        if (applied.isNotBlank()) return applied
        // Fallback: generic ChatML if model has no template
        val sb = StringBuilder()
        for ((role, content) in pairs) {
            sb.append("<|im_start|>").append(role).append("\n")
            sb.append(content).append("<|im_end|>\n")
        }
        sb.append("<|im_start|>assistant\n")
        return sb.toString()
    }

    private fun json(status: Response.Status, body: String): Response {
        val r = newFixedLengthResponse(status, "application/json; charset=utf-8", body)
        addCors(r)
        return r
    }

    private fun err(status: Response.Status, msg: String): Response =
        json(status, """{"error":${JSONObject.quote(msg)}}""")

    private fun corsOk(): Response {
        val r = newFixedLengthResponse(Response.Status.OK, "text/plain", "")
        addCors(r)
        return r
    }

    private fun addCors(r: Response) {
        r.addHeader("Access-Control-Allow-Origin", "*")
        r.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        r.addHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
    }

    fun startSafe() {
        try { start(SOCKET_READ_TIMEOUT, false) } catch (_: Exception) {}
    }
}
