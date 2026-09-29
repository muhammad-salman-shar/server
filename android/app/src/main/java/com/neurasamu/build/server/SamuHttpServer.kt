package com.neurasamu.build.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class SamuHttpServer(
    private val ctx: Context,
    private val engine: LlamaEngine,
    port: Int = 8080
) : NanoHTTPD("0.0.0.0", port) {

    @Volatile var apiKey: String? = null
    @Volatile var displayNameOverride: String? = null

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(15, TimeUnit.MINUTES)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/health" && session.method == Method.GET -> health()
                session.uri == "/v1/models" && session.method == Method.GET -> listModels()
                session.uri == "/v1/chat/completions" -> chat(session)
                session.uri == "/v1/completions" -> chat(session)
                session.method == Method.OPTIONS -> corsOk()
                else -> err(Response.Status.NOT_FOUND, "not found")
            }
        } catch (e: Exception) {
            err(Response.Status.INTERNAL_ERROR, e.message ?: "server error")
        }
    }

    private fun health(): Response = json(Response.Status.OK, """{"status":"ok","model":${quote(engine.activeModel?.apiName ?: "")}}""")

    private fun listModels(): Response {
        val m = engine.activeModel
        val name = displayNameOverride?.takeIf { it.isNotBlank() } ?: m?.apiName ?: "no-model"
        val body = """{"object":"list","data":[{"id":${quote(name)},"object":"model","owned_by":"samu-lab"}]}"""
        return json(Response.Status.OK, body)
    }

    private fun chat(session: IHTTPSession): Response {
        val m = engine.activeModel ?: return err(Response.Status.SERVICE_UNAVAILABLE, "no model loaded")
        if (!checkAuth(session)) return err(Response.Status.UNAUTHORIZED, "unauthorized")

        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: "{}"
        val node = JSONObject(raw)
        val override = displayNameOverride?.takeIf { it.isNotBlank() }
        if (override != null) node.put("model", override)

        val engineUrl = "http://127.0.0.1:${engine.enginePort}${session.uri}"
        val req = Request.Builder()
            .url(engineUrl)
            .post(node.toString().toRequestBody(jsonType))
            .build()

        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            val status = Response.Status.values().firstOrNull { it.requestStatus == resp.code }
                ?: Response.Status.OK
            return json(status, body)
        }
    }

    private fun checkAuth(session: IHTTPSession): Boolean {
        val key = apiKey ?: return true
        if (key.isBlank()) return true
        val h = session.headers["authorization"] ?: return false
        return h.removePrefix("Bearer ").trim() == key
    }

    private fun quote(s: String) = JSONObject.quote(s)

    private fun json(status: Response.Status, body: String): Response {
        val r = newFixedLengthResponse(status, "application/json; charset=utf-8", body)
        addCors(r)
        return r
    }

    private fun err(status: Response.Status, msg: String): Response {
        val b = """{"error":${JSONObject.quote(msg)}}"""
        return json(status, b)
    }

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
        try { start(SOCKET_READ_TIMEOUT, false) } catch (e: IOException) {
            throw RuntimeException("Failed to start server: ${e.message}", e)
        }
    }
}
