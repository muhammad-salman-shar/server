package com.neurasamu.build.engine

import com.neurasamu.build.data.GgufMetadata
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

object GgufInspector {
    private data class CacheKey(val path: String, val size: Long, val mtime: Long)
    private val cache = ConcurrentHashMap<CacheKey, GgufMetadata>()

    private const val GGUF_TYPE_UINT8 = 0
    private const val GGUF_TYPE_INT8 = 1
    private const val GGUF_TYPE_UINT16 = 2
    private const val GGUF_TYPE_INT16 = 3
    private const val GGUF_TYPE_UINT32 = 4
    private const val GGUF_TYPE_INT32 = 5
    private const val GGUF_TYPE_FLOAT32 = 6
    private const val GGUF_TYPE_BOOL = 7
    private const val GGUF_TYPE_STRING = 8
    private const val GGUF_TYPE_ARRAY = 9
    private const val GGUF_TYPE_UINT64 = 10
    private const val GGUF_TYPE_INT64 = 11
    private const val GGUF_TYPE_FLOAT64 = 12

    fun inspect(file: File): GgufMetadata {
        if (!file.exists() || !file.isFile || !file.name.endsWith(".gguf", ignoreCase = true)) {
            return GgufMetadata(valid = false)
        }
        val key = CacheKey(file.absolutePath, file.length(), file.lastModified())
        cache[key]?.let { return it }

        val parsed = try {
            parseBinaryHeader(file)
        } catch (_: Throwable) {
            GgufMetadata(valid = false)
        }
        cache[key] = parsed
        return parsed
    }

    fun clearCache() = cache.clear()

    private fun parseBinaryHeader(file: File): GgufMetadata {
        BufferedInputStream(FileInputStream(file), 64 * 1024).use { ins ->
            val magic = readExact(ins, 4)
            if (magic[0] != 'G'.code.toByte() ||
                magic[1] != 'G'.code.toByte() ||
                magic[2] != 'U'.code.toByte() ||
                magic[3] != 'F'.code.toByte()
            ) {
                return GgufMetadata(valid = false)
            }

            val version = readUInt32LE(ins).toInt()
            if (version !in 1..5) {
                return GgufMetadata(valid = false, version = version)
            }

            // Skip tensor count (uint64)
            readInt64LE(ins)
            val kvCount = readInt64LE(ins).coerceIn(0L, 100_000L)

            var architecture = ""
            var modelName = ""
            var nativeContext = 0
            var chatTemplate = ""

            for (i in 0L until kvCount) {
                val key = readGgufString(ins, maxCapture = 512)
                val valueType = readUInt32LE(ins).toInt()

                when {
                    key == "general.architecture" && valueType == GGUF_TYPE_STRING -> {
                        architecture = readGgufString(ins, maxCapture = 256)
                    }
                    key == "general.name" && valueType == GGUF_TYPE_STRING -> {
                        modelName = readGgufString(ins, maxCapture = 512)
                    }
                    key == "tokenizer.chat_template" && valueType == GGUF_TYPE_STRING -> {
                        chatTemplate = readGgufString(ins, maxCapture = 131_072)
                    }
                    key.endsWith(".context_length") && isIntegerType(valueType) -> {
                        val v = readIntegerValue(ins, valueType)
                        if (v > 0L && v <= Int.MAX_VALUE) {
                            nativeContext = v.toInt()
                        }
                    }
                    else -> {
                        skipGgufValue(ins, valueType)
                    }
                }
            }

            val tplLower = chatTemplate.lowercase()
            val nameLower = (modelName + " " + file.name).lowercase()
            val supportsThinking =
                tplLower.contains("<think>") ||
                    tplLower.contains("enable_thinking") ||
                    tplLower.contains("reasoning") ||
                    tplLower.contains("thought") ||
                    nameLower.contains("r1") ||
                    nameLower.contains("qwq") ||
                    nameLower.contains("thinking") ||
                    nameLower.contains("reasoner")

            return GgufMetadata(
                valid = true,
                version = version,
                architecture = architecture,
                modelName = modelName.ifBlank { file.nameWithoutExtension },
                nativeContext = nativeContext,
                supportsThinking = supportsThinking,
                chatTemplate = chatTemplate
            )
        }
    }

    private fun isIntegerType(t: Int): Boolean =
        t == GGUF_TYPE_UINT8 || t == GGUF_TYPE_INT8 ||
            t == GGUF_TYPE_UINT16 || t == GGUF_TYPE_INT16 ||
            t == GGUF_TYPE_UINT32 || t == GGUF_TYPE_INT32 ||
            t == GGUF_TYPE_UINT64 || t == GGUF_TYPE_INT64

    private fun readIntegerValue(ins: InputStream, type: Int): Long = when (type) {
        GGUF_TYPE_UINT8 -> (readByte(ins).toInt() and 0xFF).toLong()
        GGUF_TYPE_INT8 -> readByte(ins).toLong()
        GGUF_TYPE_UINT16 -> (readUInt16LE(ins) and 0xFFFF).toLong()
        GGUF_TYPE_INT16 -> readUInt16LE(ins).toShort().toLong()
        GGUF_TYPE_UINT32 -> readUInt32LE(ins)
        GGUF_TYPE_INT32 -> readUInt32LE(ins).toInt().toLong()
        GGUF_TYPE_UINT64, GGUF_TYPE_INT64 -> readInt64LE(ins)
        else -> 0L
    }

    private fun skipGgufValue(ins: InputStream, type: Int) {
        when (type) {
            GGUF_TYPE_UINT8, GGUF_TYPE_INT8, GGUF_TYPE_BOOL -> skipFully(ins, 1)
            GGUF_TYPE_UINT16, GGUF_TYPE_INT16 -> skipFully(ins, 2)
            GGUF_TYPE_UINT32, GGUF_TYPE_INT32, GGUF_TYPE_FLOAT32 -> skipFully(ins, 4)
            GGUF_TYPE_UINT64, GGUF_TYPE_INT64, GGUF_TYPE_FLOAT64 -> skipFully(ins, 8)
            GGUF_TYPE_STRING -> {
                val len = readInt64LE(ins)
                if (len < 0) throw EOFException("Invalid GGUF string len")
                skipFully(ins, len)
            }
            GGUF_TYPE_ARRAY -> {
                val elemType = readUInt32LE(ins).toInt()
                val count = readInt64LE(ins)
                if (count < 0) throw EOFException("Invalid GGUF array count")
                val fixedSize = fixedTypeSize(elemType)
                if (fixedSize > 0) {
                    skipFully(ins, count * fixedSize)
                } else {
                    for (i in 0L until count) {
                        skipGgufValue(ins, elemType)
                    }
                }
            }
            else -> throw IllegalStateException("Unknown GGUF value type: $type")
        }
    }

    private fun fixedTypeSize(type: Int): Long = when (type) {
        GGUF_TYPE_UINT8, GGUF_TYPE_INT8, GGUF_TYPE_BOOL -> 1L
        GGUF_TYPE_UINT16, GGUF_TYPE_INT16 -> 2L
        GGUF_TYPE_UINT32, GGUF_TYPE_INT32, GGUF_TYPE_FLOAT32 -> 4L
        GGUF_TYPE_UINT64, GGUF_TYPE_INT64, GGUF_TYPE_FLOAT64 -> 8L
        else -> -1L
    }

    private fun readGgufString(ins: InputStream, maxCapture: Int): String {
        val len = readInt64LE(ins)
        if (len < 0) throw EOFException("Negative GGUF string length")
        if (len == 0L) return ""
        val toRead = len.coerceAtMost(maxCapture.toLong()).toInt()
        val buf = readExact(ins, toRead)
        val remaining = len - toRead
        if (remaining > 0L) skipFully(ins, remaining)
        return String(buf, Charsets.UTF_8)
    }

    private fun readByte(ins: InputStream): Byte {
        val b = ins.read()
        if (b < 0) throw EOFException()
        return b.toByte()
    }

    private fun readExact(ins: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = ins.read(buf, off, n - off)
            if (r < 0) throw EOFException()
            off += r
        }
        return buf
    }

    private fun skipFully(ins: InputStream, n: Long) {
        var rem = n
        val scratch = ByteArray(8192)
        while (rem > 0L) {
            val skipped = ins.skip(rem)
            if (skipped > 0L) {
                rem -= skipped
            } else {
                val r = ins.read(scratch, 0, minOf(rem, scratch.size.toLong()).toInt())
                if (r < 0) throw EOFException()
                rem -= r
            }
        }
    }

    private fun readUInt16LE(ins: InputStream): Int {
        val b = readExact(ins, 2)
        return (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8)
    }

    private fun readUInt32LE(ins: InputStream): Long {
        val b = readExact(ins, 4)
        return ((b[0].toLong() and 0xFF)) or
            ((b[1].toLong() and 0xFF) shl 8) or
            ((b[2].toLong() and 0xFF) shl 16) or
            ((b[3].toLong() and 0xFF) shl 24)
    }

    private fun readInt64LE(ins: InputStream): Long {
        val b = readExact(ins, 8)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).long
    }
}
