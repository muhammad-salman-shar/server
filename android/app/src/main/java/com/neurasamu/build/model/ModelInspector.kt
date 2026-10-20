package com.neurasamu.build.model

import java.io.File

enum class SamuModelType(val label: String, val emoji: String) {
    CHAT("Chat", "💬"),
    VISION("Vision", "👁️"),
    IMAGE_GEN("Image Gen", "🎨"),
    VIDEO_GEN("Video Gen", "🎬"),
    AUDIO_IN("Audio In", "🎤"),
    AUDIO_GEN("Audio Gen", "🔊"),
    EMBEDDING("Embedding", "🧩"),
    UNKNOWN("Unknown", "❓")
}

object ModelInspector {
    fun detect(file: File): SamuModelType {
        val name = file.name.lowercase()
        
        if (name.endsWith(".litertlm") || name.endsWith(".task")) return SamuModelType.CHAT
        
        if (name.endsWith(".gguf")) {
            // 1. Filename heuristics (fast)
            if (name.contains("mmproj") || name.contains("vision") || name.contains("llava") || name.contains("vl")) return SamuModelType.VISION
            if (name.contains("sd1") || name.contains("sdxl") || name.contains("flux") || name.contains("lumina")) return SamuModelType.IMAGE_GEN
            if (name.contains("wan") || name.contains("ltx") || name.contains("svd") || name.contains("video")) return SamuModelType.VIDEO_GEN
            if (name.contains("whisper")) return SamuModelType.AUDIO_IN
            if (name.contains("bark") || name.contains("tts") || name.contains("vits")) return SamuModelType.AUDIO_GEN
            if (name.contains("embed")) return SamuModelType.EMBEDDING

            // 2. Binary header heuristics (deep scan)
            try {
                val bytes = ByteArray(1024 * 512) // Read first 512KB
                file.inputStream().use { it.read(bytes) }
                val header = String(bytes, Charsets.ISO_8859_1).lowercase()
                
                if (header.contains("clip.vision") || header.contains("mmproj")) return SamuModelType.VISION
                if (header.contains("sd1.") || header.contains("sdxl") || header.contains("flux")) return SamuModelType.IMAGE_GEN
                if (header.contains("wan2") || header.contains("ltxv")) return SamuModelType.VIDEO_GEN
                if (header.contains("whisper")) return SamuModelType.AUDIO_IN
                if (header.contains("embeddinggemma")) return SamuModelType.EMBEDDING
                
                // LLM Text Architectures
                if (header.contains("llama") || header.contains("qwen") || header.contains("gemma") || 
                    header.contains("phi") || header.contains("deepseek") || header.contains("mistral") ||
                    header.contains("smollm")) {
                    return SamuModelType.CHAT
                }
            } catch (_: Exception) {}
        }
        return SamuModelType.UNKNOWN
    }
}
