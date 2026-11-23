package com.neurasamu.build.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object AtomicStorage {
    private val lock = Any()

    val SUPPORTED_MODEL_EXTENSIONS = listOf(
        ".gguf", ".safetensors", ".ckpt", ".onnx", ".litertlm", ".task", ".bin"
    )

    fun modelsDir(ctx: Context): File = File(ctx.filesDir, "models").apply { mkdirs() }
    fun extraFilesDir(ctx: Context): File = File(ctx.filesDir, "models_extra").apply { mkdirs() }
    fun attachmentsDir(ctx: Context): File = File(ctx.filesDir, "attachments").apply { mkdirs() }
    fun imagesDir(ctx: Context): File = File(ctx.filesDir, "media/images").apply { mkdirs() }
    fun audioDir(ctx: Context): File = File(ctx.filesDir, "media/audio").apply { mkdirs() }
    fun videosDir(ctx: Context): File = File(ctx.filesDir, "media/videos").apply { mkdirs() }

    private fun writeAtomic(target: File, content: String) {
        synchronized(lock) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(content, Charsets.UTF_8)
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
        }
    }

    private fun readTextSafe(target: File): String? {
        synchronized(lock) {
            if (!target.exists()) return null
            return try {
                target.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                null
            }
        }
    }

    // ==================== settings.json ====================

    fun loadSettings(ctx: Context): ServerSettings {
        val raw = readTextSafe(File(ctx.filesDir, "settings.json")) ?: return ServerSettings()
        return try {
            val o = JSONObject(raw)
            val profilesMap = mutableMapOf<String, ModelProfile>()
            val pObj = o.optJSONObject("modelProfiles")
            if (pObj != null) {
                val keys = pObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val po = pObj.optJSONObject(key) ?: continue
                    val extras = mutableListOf<ExtraModelFile>()
                    val eArr = po.optJSONArray("extraFiles")
                    if (eArr != null) {
                        for (i in 0 until eArr.length()) {
                            val eo = eArr.optJSONObject(i) ?: continue
                            val ep = eo.optString("path", "")
                            if (ep.isNotBlank()) {
                                extras.add(
                                    ExtraModelFile(
                                        id = eo.optString("id", UUID.randomUUID().toString()),
                                        role = ExtraFileRole.fromKey(eo.optString("role", "other")),
                                        path = ep,
                                        displayName = eo.optString("displayName", File(ep).name)
                                    )
                                )
                            }
                        }
                    }
                    profilesMap[key] = ModelProfile(
                        modelPath = po.optString("modelPath", key),
                        kind = ModelKind.fromName(po.optString("kind", "")),
                        mmprojPath = po.optString("mmprojPath", "").takeIf { it.isNotBlank() },
                        extraFiles = extras,
                        gpuLayers = po.optInt("gpuLayers", -1),
                        thinkingMode = ThinkingOverride.fromName(po.optString("thinkingMode", "AUTO")),
                        customArgs = po.optString("customArgs", "")
                    )
                }
            }
            ServerSettings(
                port = o.optInt("port", 8080).coerceIn(1024, 65535),
                lanAccess = o.optBoolean("lanAccess", true),
                cpuThreads = o.optInt("cpuThreads", 0).coerceAtLeast(0),
                contextSize = o.optInt("contextSize", 8000).coerceAtLeast(0),
                requireAuth = o.optBoolean("requireAuth", false),
                apiKey = o.optString("apiKey", ""),
                modelApiName = o.optString("modelApiName", ""),
                activeModelPath = o.optString("activeModelPath", "").takeIf { it.isNotBlank() },
                useOtherDeviceUrl = o.optBoolean("useOtherDeviceUrl", false),
                modelProfiles = profilesMap
            )
        } catch (_: Exception) {
            ServerSettings()
        }
    }

    fun saveSettings(ctx: Context, s: ServerSettings) {
        val o = JSONObject().apply {
            put("port", s.port)
            put("lanAccess", s.lanAccess)
            put("cpuThreads", s.cpuThreads)
            put("contextSize", s.contextSize)
            put("requireAuth", s.requireAuth)
            put("apiKey", s.apiKey)
            put("modelApiName", s.modelApiName)
            put("activeModelPath", s.activeModelPath ?: "")
            put("useOtherDeviceUrl", s.useOtherDeviceUrl)
            val pObj = JSONObject()
            s.modelProfiles.forEach { (k, p) ->
                pObj.put(k, JSONObject().apply {
                    put("modelPath", p.modelPath)
                    put("kind", p.kind.name)
                    put("mmprojPath", p.mmprojPath ?: "")
                    put("gpuLayers", p.gpuLayers)
                    put("thinkingMode", p.thinkingMode.name)
                    put("customArgs", p.customArgs)
                    val eArr = JSONArray()
                    p.extraFiles.forEach { ef ->
                        eArr.put(JSONObject().apply {
                            put("id", ef.id)
                            put("role", ef.role.key)
                            put("path", ef.path)
                            put("displayName", ef.displayName)
                        })
                    }
                    put("extraFiles", eArr)
                })
            }
            put("modelProfiles", pObj)
        }
        writeAtomic(File(ctx.filesDir, "settings.json"), o.toString(2))
    }

    // ==================== apis.json ====================

    fun loadApis(ctx: Context): List<ApiConfig> {
        val raw = readTextSafe(File(ctx.filesDir, "apis.json")) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<ApiConfig>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id", UUID.randomUUID().toString())
                if (id == BUILTIN_LOCAL_API_ID) continue
                out.add(
                    ApiConfig(
                        id = id,
                        label = o.optString("label", "Custom API"),
                        baseUrl = UrlSanitizer.sanitize(o.optString("baseUrl", "")),
                        modelName = o.optString("modelName", ""),
                        apiKey = o.optString("apiKey", ""),
                        systemPrompt = o.optString("systemPrompt", ""),
                        isBuiltIn = false
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveApis(ctx: Context, apis: List<ApiConfig>) {
        val arr = JSONArray()
        apis.filter { !it.isBuiltIn && it.id != BUILTIN_LOCAL_API_ID }.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("label", a.label)
                put("baseUrl", UrlSanitizer.sanitize(a.baseUrl))
                put("modelName", a.modelName)
                put("apiKey", a.apiKey)
                put("systemPrompt", a.systemPrompt)
            })
        }
        writeAtomic(File(ctx.filesDir, "apis.json"), arr.toString(2))
    }

    // ==================== chats.json ====================

    fun loadChats(ctx: Context): List<ChatSession> {
        val raw = readTextSafe(File(ctx.filesDir, "chats.json")) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<ChatSession>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val msgs = mutableListOf<ChatMessage>()
                val mArr = o.optJSONArray("messages")
                if (mArr != null) {
                    for (j in 0 until mArr.length()) {
                        val mo = mArr.optJSONObject(j) ?: continue
                        val atts = mutableListOf<ChatAttachment>()
                        val aArr = mo.optJSONArray("attachments")
                        if (aArr != null) {
                            for (k in 0 until aArr.length()) {
                                val ao = aArr.optJSONObject(k) ?: continue
                                atts.add(
                                    ChatAttachment(
                                        id = ao.optString("id", UUID.randomUUID().toString()),
                                        name = ao.optString("name", "file"),
                                        path = ao.optString("path", ""),
                                        mimeType = ao.optString("mimeType", "application/octet-stream"),
                                        sizeBytes = ao.optLong("sizeBytes", 0L),
                                        isImage = ao.optBoolean("isImage", false)
                                    )
                                )
                            }
                        }
                        val medObj = mo.optJSONObject("media")
                        val media = if (medObj != null && medObj.optString("localPath", "").isNotBlank()) {
                            GeneratedMedia(
                                kind = MediaKind.fromName(medObj.optString("kind", "IMAGE")),
                                localPath = medObj.optString("localPath", ""),
                                remoteUrl = medObj.optString("remoteUrl", ""),
                                fileName = medObj.optString("fileName", ""),
                                mimeType = medObj.optString("mimeType", ""),
                                prompt = medObj.optString("prompt", ""),
                                sizeBytes = medObj.optLong("sizeBytes", 0L),
                                refImagePath = medObj.optString("refImagePath", "").takeIf { it.isNotBlank() }
                            )
                        } else null
                        msgs.add(
                            ChatMessage(
                                id = mo.optString("id", UUID.randomUUID().toString()),
                                role = mo.optString("role", "user"),
                                content = mo.optString("content", ""),
                                attachments = atts,
                                media = media,
                                isError = mo.optBoolean("isError", false),
                                errorText = mo.optString("errorText", "").takeIf { it.isNotBlank() },
                                timestamp = mo.optLong("timestamp", System.currentTimeMillis()),
                                mode = ChatMode.fromName(mo.optString("mode", "CHAT"))
                            )
                        )
                    }
                }
                out.add(
                    ChatSession(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        title = o.optString("title", "New Chat"),
                        messages = msgs,
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
            out
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveChats(ctx: Context, chats: List<ChatSession>) {
        val arr = JSONArray()
        chats.forEach { c ->
            arr.put(JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("createdAt", c.createdAt)
                put("updatedAt", c.updatedAt)
                val mArr = JSONArray()
                c.messages.forEach { m ->
                    mArr.put(JSONObject().apply {
                        put("id", m.id)
                        put("role", m.role)
                        put("content", m.content)
                        put("isError", m.isError)
                        put("errorText", m.errorText ?: "")
                        put("timestamp", m.timestamp)
                        put("mode", m.mode.name)
                        if (m.attachments.isNotEmpty()) {
                            val aArr = JSONArray()
                            m.attachments.forEach { a ->
                                aArr.put(JSONObject().apply {
                                    put("id", a.id)
                                    put("name", a.name)
                                    put("path", a.path)
                                    put("mimeType", a.mimeType)
                                    put("sizeBytes", a.sizeBytes)
                                    put("isImage", a.isImage)
                                })
                            }
                            put("attachments", aArr)
                        }
                        m.media?.let { med ->
                            put("media", JSONObject().apply {
                                put("kind", med.kind.name)
                                put("localPath", med.localPath)
                                put("remoteUrl", med.remoteUrl)
                                put("fileName", med.fileName)
                                put("mimeType", med.mimeType)
                                put("prompt", med.prompt)
                                put("sizeBytes", med.sizeBytes)
                                put("refImagePath", med.refImagePath ?: "")
                            })
                        }
                    })
                }
                put("messages", mArr)
            })
        }
        writeAtomic(File(ctx.filesDir, "chats.json"), arr.toString(2))
    }

    // ==================== chat_prefs.json ====================

    fun loadChatPrefs(ctx: Context): ChatPrefs {
        val raw = readTextSafe(File(ctx.filesDir, "chat_prefs.json")) ?: return ChatPrefs()
        return try {
            val o = JSONObject(raw)
            ChatPrefs(
                themeMode = ThemeMode.fromName(o.optString("themeMode", "DARK")),
                fontScale = o.optDouble("fontScale", 1.0).toFloat().coerceIn(0.8f, 1.4f),
                customInstructionsEnabled = o.optBoolean("customInstructionsEnabled", false),
                customInstructions = o.optString("customInstructions", ""),
                thinkingEffort = ThinkingEffort.fromName(o.optString("thinkingEffort", "AUTO")),
                chatMode = ChatMode.fromName(o.optString("chatMode", "CHAT")),
                activeApiId = o.optString("activeApiId", BUILTIN_LOCAL_API_ID),
                activeChatId = o.optString("activeChatId", "").takeIf { it.isNotBlank() }
            )
        } catch (_: Exception) {
            ChatPrefs()
        }
    }

    fun saveChatPrefs(ctx: Context, p: ChatPrefs) {
        val o = JSONObject().apply {
            put("themeMode", p.themeMode.name)
            put("fontScale", p.fontScale.toDouble())
            put("customInstructionsEnabled", p.customInstructionsEnabled)
            put("customInstructions", p.customInstructions)
            put("thinkingEffort", p.thinkingEffort.name)
            put("chatMode", p.chatMode.name)
            put("activeApiId", p.activeApiId)
            put("activeChatId", p.activeChatId ?: "")
        }
        writeAtomic(File(ctx.filesDir, "chat_prefs.json"), o.toString(2))
    }

    // ==================== SAF File Import Helpers ====================

    fun queryDisplayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = c.getString(idx)
                        if (!name.isNullOrBlank()) return sanitizeFileName(name)
                    }
                }
            }
        } catch (_: Exception) {}
        return sanitizeFileName(uri.lastPathSegment?.substringAfterLast('/') ?: "file_${UUID.randomUUID().toString().take(8)}")
    }

    fun queryMimeType(ctx: Context, uri: Uri, fileName: String): String {
        val fromResolver = try { ctx.contentResolver.getType(uri) } catch (_: Exception) { null }
        if (!fromResolver.isNullOrBlank() && fromResolver != "application/octet-stream") return fromResolver
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "pdf" -> "application/pdf"
            "wav" -> "audio/wav"
            "mp3" -> "audio/mpeg"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "m4a", "aac" -> "audio/mp4"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    fun copyUriToDir(ctx: Context, uri: Uri, targetDir: File, overrideName: String? = null): File {
        targetDir.mkdirs()
        val rawName = overrideName ?: queryDisplayName(ctx, uri)
        val safeName = sanitizeFileName(rawName)
        var dest = File(targetDir, safeName)
        if (dest.exists() && targetDir == attachmentsDir(ctx)) {
            val base = safeName.substringBeforeLast('.', safeName)
            val ext = safeName.substringAfterLast('.', "")
            val suffix = UUID.randomUUID().toString().take(6)
            dest = File(targetDir, if (ext.isNotEmpty()) "${base}_$suffix.$ext" else "${base}_$suffix")
        }
        val tmp = File(targetDir, "${dest.name}.part")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            tmp.outputStream().use { output ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                }
            }
        } ?: error("Unable to open selected file stream")
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
        return dest
    }

    fun sanitizeFileName(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_").trim()
        return cleaned.ifBlank { "file_${UUID.randomUUID().toString().take(6)}" }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        return String.format("%.2f GB", mb / 1024.0)
    }
}
