package com.neurasamu.build.chat

import android.util.Base64
import com.neurasamu.build.data.AtomicStorage
import com.neurasamu.build.data.ChatAttachment
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

sealed class AnalyzedAttachment {
    data class ImagePart(val mimeType: String, val dataUri: String) : AnalyzedAttachment()
    data class AudioPart(val format: String, val base64Data: String) : AnalyzedAttachment()
    data class TextPart(val header: String, val content: String) : AnalyzedAttachment()
}

object FileAnalyzer {
    private const val MAX_EXTRACTED_CHARS = 60_000
    private const val MAX_AUDIO_BYTES = 20L * 1024L * 1024L

    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
    private val AUDIO_EXTS = setOf("wav", "mp3", "ogg", "flac", "m4a", "aac")
    private val VIDEO_EXTS = setOf("mp4", "mkv", "mov", "avi", "webm", "3gp")
    private val OFFICE_ZIP_EXTS = setOf("docx", "xlsx", "pptx", "odt", "odp", "ods", "epub")
    private val TEXT_EXTS = setOf(
        "txt", "md", "markdown", "json", "csv", "tsv", "yaml", "yml", "xml", "html", "htm",
        "css", "scss", "js", "jsx", "ts", "tsx", "kt", "kts", "java", "py", "c", "h", "cpp",
        "hpp", "cc", "cs", "go", "rs", "rb", "php", "swift", "sh", "bash", "zsh", "bat", "ps1",
        "sql", "log", "ini", "toml", "cfg", "conf", "gradle", "properties", "env", "dockerfile",
        "makefile", "cmake", "r", "m", "lua", "dart", "scala", "groovy", "tex", "svg"
    )

    fun isImageFile(name: String, mimeType: String): Boolean {
        if (mimeType.startsWith("image/", ignoreCase = true)) return true
        return name.substringAfterLast('.', "").lowercase() in IMAGE_EXTS
    }

    fun analyze(att: ChatAttachment): AnalyzedAttachment {
        val file = File(att.path)
        if (!file.exists() || !file.isFile) {
            return AnalyzedAttachment.TextPart(
                header = "[Attachment missing: ${att.name}]",
                content = "(File could not be found on device)"
            )
        }

        val ext = att.name.substringAfterLast('.', "").lowercase()
        val sizeStr = AtomicStorage.formatBytes(file.length())

        return when {
            isImageFile(att.name, att.mimeType) -> {
                val mime = when (ext) {
                    "png" -> "image/png"
                    "webp" -> "image/webp"
                    "gif" -> "image/gif"
                    "bmp" -> "image/bmp"
                    else -> "image/jpeg"
                }
                val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                AnalyzedAttachment.ImagePart(mime, "data:$mime;base64,$b64")
            }

            ext in AUDIO_EXTS || att.mimeType.startsWith("audio/", ignoreCase = true) -> {
                if (file.length() <= MAX_AUDIO_BYTES) {
                    val fmt = if (ext in AUDIO_EXTS) ext else "wav"
                    val b64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    AnalyzedAttachment.AudioPart(fmt, b64)
                } else {
                    AnalyzedAttachment.TextPart(
                        header = "[Audio file: ${att.name} ($sizeStr)]",
                        content = "Audio file exceeds 20 MB inline limit. File metadata only."
                    )
                }
            }

            ext == "pdf" -> extractPdf(file, att.name, sizeStr)

            ext in OFFICE_ZIP_EXTS -> extractOfficeZip(file, att.name, ext, sizeStr)

            ext in VIDEO_EXTS || att.mimeType.startsWith("video/", ignoreCase = true) -> {
                AnalyzedAttachment.TextPart(
                    header = "[Video file: ${att.name}]",
                    content = "Format: ${ext.uppercase()} · Size: $sizeStr (binary video metadata)"
                )
            }

            ext in TEXT_EXTS || att.mimeType.startsWith("text/", ignoreCase = true) -> {
                val text = readTextCapped(file)
                AnalyzedAttachment.TextPart(
                    header = "=== File: ${att.name} ($sizeStr) ===",
                    content = text
                )
            }

            else -> {
                // Binary-safe UTF-8 heuristic fallback
                if (looksLikeUtf8Text(file)) {
                    AnalyzedAttachment.TextPart(
                        header = "=== File: ${att.name} ($sizeStr) ===",
                        content = readTextCapped(file)
                    )
                } else {
                    AnalyzedAttachment.TextPart(
                        header = "[Binary file: ${att.name}]",
                        content = "Type: ${att.mimeType} · Size: $sizeStr (non-text binary attachment)"
                    )
                }
            }
        }
    }

    private fun extractPdf(file: File, name: String, sizeStr: String): AnalyzedAttachment.TextPart {
        return try {
            PDDocument.load(file).use { doc ->
                val pages = doc.numberOfPages
                val stripper = PDFTextStripper()
                val raw = stripper.getText(doc)?.trim().orEmpty()
                if (raw.isBlank()) {
                    AnalyzedAttachment.TextPart(
                        header = "=== PDF: $name ($pages pages · $sizeStr) ===",
                        content = "[Warning: No selectable text found in this PDF. It appears to be a scanned image PDF.]"
                    )
                } else {
                    val clipped = clipText(raw)
                    AnalyzedAttachment.TextPart(
                        header = "=== PDF: $name ($pages pages · $sizeStr) ===",
                        content = clipped
                    )
                }
            }
        } catch (e: Throwable) {
            AnalyzedAttachment.TextPart(
                header = "=== PDF: $name ($sizeStr) ===",
                content = "[Could not extract PDF text: ${e.message}]"
            )
        }
    }

    private fun extractOfficeZip(
        file: File,
        name: String,
        ext: String,
        sizeStr: String
    ): AnalyzedAttachment.TextPart {
        return try {
            val sb = StringBuilder()
            val xmlTagRegex = Regex("<[^>]+>")
            val multiSpaceRegex = Regex("[ \\t]+")

            ZipInputStream(FileInputStream(file)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val en = entry.name.lowercase()
                    val relevant = when (ext) {
                        "docx" -> en == "word/document.xml" || en.startsWith("word/header") || en.startsWith("word/footer")
                        "xlsx" -> en == "xl/sharedstrings.xml" || en.startsWith("xl/worksheets/sheet")
                        "pptx" -> en.startsWith("ppt/slides/slide") && en.endsWith(".xml")
                        "odt", "odp", "ods" -> en == "content.xml"
                        "epub" -> en.endsWith(".xhtml") || en.endsWith(".html") || en.endsWith(".htm")
                        else -> false
                    }
                    if (relevant && !entry.isDirectory && sb.length < MAX_EXTRACTED_CHARS) {
                        val rawBytes = zis.readBytes()
                        val xml = String(rawBytes, Charsets.UTF_8)
                            .replace("</w:p>", "\n")
                            .replace("</p>", "\n")
                            .replace("</tr>", "\n")
                            .replace("</row>", "\n")
                            .replace("<br/>", "\n")
                            .replace("<br>", "\n")
                        val stripped = xmlTagRegex.replace(xml, " ")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'")
                            .replace("&#39;", "'")
                        val cleaned = multiSpaceRegex.replace(stripped, " ").trim()
                        if (cleaned.isNotEmpty()) {
                            sb.append(cleaned).append("\n\n")
                        }
                    }
                    zis.closeEntry()
                }
            }
            val result = clipText(sb.toString().trim())
            AnalyzedAttachment.TextPart(
                header = "=== Document: $name (${ext.uppercase()} · $sizeStr) ===",
                content = result.ifBlank { "(No readable text extracted from $name)" }
            )
        } catch (e: Throwable) {
            AnalyzedAttachment.TextPart(
                header = "=== Document: $name ($sizeStr) ===",
                content = "[Failed to read document archive: ${e.message}]"
            )
        }
    }

    private fun looksLikeUtf8Text(file: File): Boolean {
        return try {
            val sampleSize = minOf(file.length(), 4096L).toInt()
            if (sampleSize <= 0) return true
            val buf = ByteArray(sampleSize)
            FileInputStream(file).use { it.read(buf) }
            var nonPrintable = 0
            for (b in buf) {
                val u = b.toInt() and 0xFF
                if (u == 0) return false
                if (u < 9 || (u in 14..31)) nonPrintable++
            }
            (nonPrintable.toDouble() / sampleSize.toDouble()) < 0.05
        } catch (_: Exception) {
            false
        }
    }

    private fun readTextCapped(file: File): String {
        return try {
            val maxBytes = MAX_EXTRACTED_CHARS * 2
            val len = minOf(file.length(), maxBytes.toLong()).toInt()
            val buf = ByteArray(len)
            FileInputStream(file).use { it.read(buf) }
            clipText(String(buf, Charsets.UTF_8))
        } catch (e: Exception) {
            "[Failed to read text: ${e.message}]"
        }
    }

    private fun clipText(s: String): String {
        return if (s.length > MAX_EXTRACTED_CHARS) {
            s.take(MAX_EXTRACTED_CHARS) + "\n\n...[truncated at $MAX_EXTRACTED_CHARS characters]"
        } else s
    }

    fun toOpenAiContentParts(
        userText: String,
        attachments: List<ChatAttachment>
    ): Any {
        if (attachments.isEmpty()) return userText
        val analyzed = attachments.map { analyze(it) }
        val hasMediaParts = analyzed.any { it is AnalyzedAttachment.ImagePart || it is AnalyzedAttachment.AudioPart }

        val textCombined = buildString {
            analyzed.filterIsInstance<AnalyzedAttachment.TextPart>().forEach { tp ->
                append(tp.header).append("\n").append(tp.content).append("\n\n")
            }
            if (userText.isNotBlank()) {
                append(userText)
            } else if (!hasMediaParts) {
                append("Please analyze the attached file(s).")
            }
        }.trim()

        if (!hasMediaParts) return textCombined

        val arr = org.json.JSONArray()
        if (textCombined.isNotBlank()) {
            arr.put(JSONObject().apply {
                put("type", "text")
                put("text", textCombined)
            })
        }
        analyzed.forEach { part ->
            when (part) {
                is AnalyzedAttachment.ImagePart -> {
                    arr.put(JSONObject().apply {
                        put("type", "image_url")
                        put("image_url", JSONObject().put("url", part.dataUri))
                    })
                }
                is AnalyzedAttachment.AudioPart -> {
                    arr.put(JSONObject().apply {
                        put("type", "input_audio")
                        put("input_audio", JSONObject().apply {
                            put("data", part.base64Data)
                            put("format", part.format)
                        })
                    })
                }
                is AnalyzedAttachment.TextPart -> {}
            }
        }
        return arr
    }
}
