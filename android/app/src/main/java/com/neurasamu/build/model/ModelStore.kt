package com.neurasamu.build.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ModelStore {
    fun modelsDir(ctx: Context): File {
        val d = File(ctx.filesDir, "models")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun list(ctx: Context): List<SamuModel> {
        val d = modelsDir(ctx)
        return d.listFiles { f -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?.map { f ->
                SamuModel(
                    id = f.nameWithoutExtension,
                    displayName = f.nameWithoutExtension,
                    file = f,
                    sizeBytes = f.length()
                )
            }?.sortedBy { it.displayName } ?: emptyList()
    }

    suspend fun import(ctx: Context, uri: Uri): SamuModel = withContext(Dispatchers.IO) {
        val name = queryName(ctx, uri) ?: "model-${UUID.randomUUID().toString().take(6)}.gguf"
        val safeName = if (name.endsWith(".gguf", true)) name else "$name.gguf"
        val dest = File(modelsDir(ctx), safeName)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
        } ?: error("Cannot open input stream")
        SamuModel(
            id = dest.nameWithoutExtension,
            displayName = dest.nameWithoutExtension,
            file = dest,
            sizeBytes = dest.length()
        )
    }

    fun delete(ctx: Context, model: SamuModel) {
        if (model.file.exists()) model.file.delete()
    }

    private fun queryName(ctx: Context, uri: Uri): String? {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) return c.getString(idx)
            }
        }
        return uri.lastPathSegment
    }
}
