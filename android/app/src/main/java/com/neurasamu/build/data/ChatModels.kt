package com.neurasamu.build.data

import java.io.File
import java.security.SecureRandom
import java.util.UUID

const val BUILTIN_LOCAL_API_ID = "__samu_local_api__"

enum class AppTab(val label: String) {
    CHAT("Chat"),
    SERVER("Server")
}

enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: DARK
    }
}

enum class ChatMode(val label: String, val subtitle: String, val placeholder: String) {
    CHAT("Chat", "Text, Vision & Files", "Message SaMu Lab..."),
    IMAGE("Image", "Generate Image (+ Ref)", "Describe the image to generate..."),
    AUDIO("Audio", "Text to Speech Audio", "Enter text to convert to speech..."),
    VIDEO("Video", "Generate Video (+ Ref)", "Describe the video to generate...");

    companion object {
        fun fromName(name: String?): ChatMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: CHAT
    }
}

enum class ThinkingEffort(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val budget: Int,
    val reasoningEffort: String?
) {
    AUTO("Auto", "Auto", "Model decides thinking depth", -1, null),
    OFF("Off (Skip)", "Off", "Skip thinking · instant answer", 0, "none"),
    LOW("Low", "Low", "Fast thinking (~1000 tok)", 1000, "low"),
    HIGH("High", "High", "+25% deeper than Low (~1250 tok)", 1250, "medium"),
    MAX("Max", "Max", "+50% deeper than Low (~1500 tok)", 1500, "high"),
    ULTRA("Ultra", "Ultra", "+80% deepest thinking (~1800 tok)", 1800, "high");

    companion object {
        fun fromName(name: String?): ThinkingEffort =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
    }
}

enum class ModelKind(val label: String, val badge: String) {
    TEXT_VISION("Text/Vision", "LLM"),
    IMAGE_GEN("Image Gen", "IMAGE"),
    AUDIO_TTS("Audio/TTS", "AUDIO"),
    VIDEO_GEN("Video Gen", "VIDEO");

    companion object {
        fun fromName(name: String?): ModelKind =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TEXT_VISION

        fun guessFromFileName(name: String): ModelKind {
            val lc = name.lowercase()
            return when {
                lc.endsWith(".onnx") || lc.contains("piper") || lc.contains("tts") ||
                    lc.contains("bark") || lc.contains("vits") || lc.contains("kokoro") -> AUDIO_TTS
                lc.contains("wan") || lc.contains("ltx") || lc.contains("svd") ||
                    lc.contains("mochi") || lc.contains("cogvideo") || lc.contains("video") -> VIDEO_GEN
                lc.endsWith(".safetensors") || lc.endsWith(".ckpt") ||
                    lc.contains("sdxl") || lc.contains("sd1") || lc.contains("sd3") ||
                    lc.contains("flux") || lc.contains("lumina") || lc.contains("diffusion") -> IMAGE_GEN
                else -> TEXT_VISION
            }
        }
    }
}

enum class ThinkingOverride(val label: String) {
    AUTO("Auto"),
    ENABLED("Force Supported"),
    DISABLED("No Thinking");

    companion object {
        fun fromName(name: String?): ThinkingOverride =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
    }
}

enum class ExtraFileRole(val key: String, val label: String) {
    VAE("vae", "VAE"),
    CLIP("clip", "CLIP"),
    T5("t5", "T5"),
    LORA("lora", "LORA"),
    VOICE_CONFIG("voice_config", "VOICE_CONFIG"),
    VOCODER("vocoder", "VOCODER"),
    OTHER("other", "OTHER");

    companion object {
        fun fromKey(key: String?): ExtraFileRole =
            entries.firstOrNull {
                it.key.equals(key, ignoreCase = true) || it.name.equals(key, ignoreCase = true)
            } ?: OTHER
    }
}

data class ExtraModelFile(
    val id: String = UUID.randomUUID().toString(),
    val role: ExtraFileRole = ExtraFileRole.OTHER,
    val path: String,
    val displayName: String = File(path).name
)

data class ModelProfile(
    val modelPath: String,
    val kind: ModelKind = ModelKind.guessFromFileName(File(modelPath).name),
    val mmprojPath: String? = null,
    val extraFiles: List<ExtraModelFile> = emptyList(),
    val gpuLayers: Int = -1, // -1 = Auto, 0 = CPU, >0 = custom
    val thinkingMode: ThinkingOverride = ThinkingOverride.AUTO,
    val customArgs: String = ""
)

data class ServerSettings(
    val port: Int = 8080,
    val lanAccess: Boolean = true,
    val cpuThreads: Int = 0, // 0 = Auto
    val contextSize: Int = 8000, // 0 = model max, no artificial cap
    val requireAuth: Boolean = false,
    val apiKey: String = "",
    val modelApiName: String = "",
    val activeModelPath: String? = null,
    val useOtherDeviceUrl: Boolean = false,
    val modelProfiles: Map<String, ModelProfile> = emptyMap()
) {
    fun profileFor(path: String): ModelProfile =
        modelProfiles[path] ?: ModelProfile(modelPath = path)

    companion object {
        fun generateApiKey(): String {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
            val rng = SecureRandom()
            val sb = StringBuilder("sk-samu-")
            repeat(32) { sb.append(chars[rng.nextInt(chars.length)]) }
            return sb.toString()
        }
    }
}

data class ApiConfig(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val baseUrl: String,
    val modelName: String,
    val apiKey: String = "",
    val systemPrompt: String = "",
    val isBuiltIn: Boolean = false
)

data class ChatAttachment(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val path: String,
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val isImage: Boolean = false
)

enum class MediaKind {
    IMAGE, AUDIO, VIDEO;

    companion object {
        fun fromName(name: String?): MediaKind =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: IMAGE
    }
}

data class GeneratedMedia(
    val kind: MediaKind,
    val localPath: String,
    val remoteUrl: String = "",
    val fileName: String = File(localPath).name,
    val mimeType: String = "",
    val prompt: String = "",
    val sizeBytes: Long = File(localPath).takeIf { it.exists() }?.length() ?: 0L,
    val refImagePath: String? = null
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String, // "user" or "assistant"
    val content: String = "",
    val attachments: List<ChatAttachment> = emptyList(),
    val media: GeneratedMedia? = null,
    val isError: Boolean = false,
    val errorText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val mode: ChatMode = ChatMode.CHAT
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatPrefs(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val fontScale: Float = 1.0f,
    val customInstructionsEnabled: Boolean = false,
    val customInstructions: String = "",
    val thinkingEffort: ThinkingEffort = ThinkingEffort.AUTO,
    val chatMode: ChatMode = ChatMode.CHAT,
    val activeApiId: String = BUILTIN_LOCAL_API_ID,
    val activeChatId: String? = null
)

data class GgufMetadata(
    val valid: Boolean = false,
    val version: Int = 0,
    val architecture: String = "",
    val modelName: String = "",
    val nativeContext: Int = 0,
    val supportsThinking: Boolean = false,
    val chatTemplate: String = ""
)

data class ModelEntry(
    val file: File,
    val displayName: String,
    val sizeBytes: Long,
    val profile: ModelProfile,
    val ggufMeta: GgufMetadata? = null
) {
    val path: String get() = file.absolutePath
    val nativeContext: Int get() = ggufMeta?.nativeContext ?: 0
    val hasMmproj: Boolean get() = !profile.mmprojPath.isNullOrBlank() && File(profile.mmprojPath).exists()
    val extraFilesCount: Int get() = profile.extraFiles.count { File(it.path).exists() }
}

enum class ServerRunState {
    STOPPED,
    STARTING,
    RUNNING,
    PROCESSING
}

object UrlSanitizer {
    private val markdownLinkRegex = Regex("""\[[^\]]*]\(([^)]+)\)""")

    fun sanitize(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        val mdMatch = markdownLinkRegex.find(s)
        if (mdMatch != null) {
            s = mdMatch.groupValues[1].trim()
        }
        s = s.removePrefix("<").removeSuffix(">").trim()
        if (s.isEmpty()) return ""
        if (!s.startsWith("http://", ignoreCase = true) &&
            !s.startsWith("https://", ignoreCase = true)
        ) {
            s = "http://$s"
        }
        return s.trimEnd('/')
    }

    fun resolveEndpoint(baseUrl: String, endpoint: String): String {
        val cleanBase = sanitize(baseUrl).trimEnd('/')
        val cleanEp = if (endpoint.startsWith("/")) endpoint else "/$endpoint"
        return when {
            cleanBase.endsWith("/v1") && cleanEp.startsWith("/v1/") ->
                cleanBase + cleanEp.removePrefix("/v1")
            cleanBase.endsWith(cleanEp) -> cleanBase
            else -> cleanBase + cleanEp
        }
    }
}
