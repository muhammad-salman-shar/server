package com.neurasamu.build.model

import java.io.File

enum class ModelFormat(val label: String, val extensions: List<String>) {
    GGUF("GGUF (llama.cpp)", listOf(".gguf")),
    LITERT("LiteRT / MediaPipe", listOf(".litertlm", ".task", ".bin")),
    CUSTOM("Custom / other", emptyList());

    companion object {
        fun fromFileName(name: String): ModelFormat {
            val lc = name.lowercase()
            return values().firstOrNull { f -> f.extensions.any { lc.endsWith(it) } } ?: CUSTOM
        }
    }
}

enum class ModelFamily(val label: String, val keywords: List<String>) {
    LLAMA("Llama", listOf("llama", "meta-llama")),
    GEMMA("Gemma", listOf("gemma")),
    QWEN("Qwen", listOf("qwen")),
    DEEPSEEK("DeepSeek", listOf("deepseek")),
    PHI("Phi", listOf("phi-", "phi_")),
    MISTRAL("Mistral", listOf("mistral", "mixtral")),
    ZAI("ZAI", listOf("zai", "glm")),
    OTHER("Other", emptyList());

    companion object {
        fun fromName(name: String): ModelFamily {
            val lc = name.lowercase()
            return values().firstOrNull { f -> f.keywords.any { lc.contains(it) } } ?: OTHER
        }
    }
}

data class SamuModel(
    val id: String,
    val displayName: String,
    val file: File,
    val sizeBytes: Long,
    val format: ModelFormat = ModelFormat.fromFileName(file.name),
    val family: ModelFamily = ModelFamily.fromName(file.nameWithoutExtension),
    val ctx: Int = 2048,
    val threads: Int = 4
) {
    val apiName: String get() = id
}
