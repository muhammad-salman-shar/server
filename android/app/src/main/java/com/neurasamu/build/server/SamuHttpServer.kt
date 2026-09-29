package com.neurasamu.build.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SamuHttpServer(
    private val ctx: Context,
    port: Int = 8080
) : NanoHTTPD("0.0.0.0", port) {

    @Volatile var apiKey: String? = null
    @Volatile var displayNameOverride: String? = null

    private val pool = Executors.newCachedThreadPool()

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
        val key = apiKey?.takeIf { it.isNotBlank() } ?: return true
        val h = session.headers["authorization"] ?: return false
        return h.removePrefix("Bearer ").trim() == key
    }

    private fun chat(session: IHTTPSession): Response {
        if (!SamuEngine.isLoaded) return err(Response.Status.SERVICE_UNAVAILABLE, "no model loaded")
        if (!checkAuth(session)) return err(Response.Status.UNAUTHORIZED, "unauthorized")

        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: return err(Response.Status.BAD_REQUEST, "empty body")
        val node = JSONObject(raw)

        val messages = node.optJSONArray("messages")
            ?: return err(Response.Status.BAD_REQUEST, "messages[] required")
        val modelIn = node.optString("model", "")
        if (modelIn.isNotBlank() && modelIn != activeName() && modelIn != "any") {
            // Accept any model name for compatibility, but log mismatch
        }

        val prompt = buildChatPrompt(messages)
        val maxTokens = node.optInt("max_tokens", 512).coerceIn(1, 4096)
        val temperature = node.optDouble("temperature", 0.7)
        val topP = node.optDouble("top_p", 0.9)
        val stream = node.optBoolean("stream", false)

        return if (stream) streamChat(session, prompt, maxTokens, temperature, topP)
        else fullChat(prompt, maxTokens, temperature, topP)
    }

    private fun completions(session: IHTTPSession): Response {
        if (!SamuEngine.isLoaded) return err(Response.Status.SERVICE_UNAVAILABLE, "no model loaded")
        if (!checkAuth(session)) return err(Response.Status.UNAUTHORIZED, "unauthorized")

        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: return err(Response.Status.BAD_REQUEST, "empty body")
        val node = JSONObject(raw)
        val prompt = node.optString("prompt", "")
        if (prompt.isBlank()) return err(Response.Status.BAD_REQUEST, "prompt required")
        val maxTokens = node.optInt("max_tokens", 512).coerceIn(1, 4096)
        val temperature = node.optDouble("temperature", 0.7)
        val topP = node.optDouble("top_p", 0.9)
        val stream = node.optBoolean("stream", false)

        return if (stream) streamChat(session, prompt, maxTokens, temperature, topP)
        else fullChat(prompt, maxTokens, temperature, topP)
    }

    private fun fullChat(prompt: String, maxTokens: Int, temperature: Double, topP: Double): Response {
        val sb = StringBuilder()
        SamuEngine.generate(
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
                put("total_tokens", SamuEngine.tokensUsed())
            })
        }.toString()
        return json(Response.Status.OK, body)
    }

    private fun streamChat(session: IHTTPSession, prompt: String, maxTokens: Int,
                           temperature: Double, topP: Double): Response {
        val pipeIn = PipedInputStream(64 * 1024)
        val pipeOut = PipedOutputStream(pipeIn)
        val stopped = AtomicBoolean(false)

        pool.submit {
            try {
                val id = "chatcmpl-samu-${System.currentTimeMillis()}"
                val created = System.currentTimeMillis() / 1000
                val model = activeName()

                val first = JSONObject().apply {
                    put("id", id); put("object", "chat.completion.chunk")
                    put("created", created); put("model", model)
                    put("choices", JSONArray().put(JSONObject().apply {
                        put("index", 0)
                        put("delta", JSONObject().put("role", "assistant"))
                        put("finish_reason", JSONObject.NULL)
                    }))
                }
                pipeOut.write("data: $first\n\n".toByteArray())

                SamuEngine.generate(
                    prompt = prompt,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    topP = topP,
                    onToken = { tok ->
                        if (!stopped.get()) {
                            val chunk = JSONObject().apply {
                                put("id", id); put("object", "chat.completion.chunk")
                                put("created", created); put("model", model)
                                put("choices", JSONArray().put(JSONObject().apply {
                                    put("index", 0)
                                    put("delta", JSONObject().put("content", tok))
                                    put("finish_reason", JSONObject.NULL)
                                }))
                            }
                            try { pipeOut.write("data: $chunk\n\n".toByteArray()) } catch (_: Exception) {}
                        }
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
                pipeOut.write("data: $end\n\n".toByteArray())
                pipeOut.write("data: [DONE]\n\n".toByteArray())
                pipeOut.flush()
            } catch (e: Exception) {
                try { pipeOut.write("data: {\"error\":${JSONObject.quote(e.message ?: "err")}}\n\n".toByteArray()) } catch (_: Exception) {}
            } finally {
                try { pipeOut.close() } catch (_: Exception) {}
            }
        }

        val resp = newChunkedResponse(Response.Status.OK, "text/event-stream", pipeIn)
        resp.addHeader("Cache-Control", "no-cache")
        resp.addHeader("X-Accel-Buffering", "no")
        addCors(resp)
        return resp
    }

    private fun buildChatPrompt(messages: JSONArray): String {
        val sb = StringBuilder()
        for (i in 0 until messages.length()) {
            val m = messages.getJSONObject(i)
            val role = m.optString("role", "user")
            val content = m.optString("content", "")
            sb.append("<start_of_turn>").append(role).append("\n")
            sb.append(content).append("<end_of_turn>\n")
        }
        sb.append("<start_of_turn>model\n")
        return sb.toString()
    }

    private fun quote(s: String) = JSONObject.quote(s)

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
