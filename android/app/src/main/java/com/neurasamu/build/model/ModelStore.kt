package com.neurasamu.build.model

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ModelStore {

    private val ACCEPTED_EXT = listOf(".gguf", ".litertlm", ".task", ".bin")
    private const val META_SUFFIX = ".meta.json"

    fun modelsDir(ctx: Context): File {
        val d = File(ctx.filesDir, "models")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun list(ctx: Context): List<SamuModel> {
        val d = modelsDir(ctx)
        return d.listFiles { f ->
            f.isFile
                && ACCEPTED_EXT.any { f.name.endsWith(it, ignoreCase = true) }
                && !f.name.startsWith("mmproj", ignoreCase = true)
                && !f.name.endsWith(META_SUFFIX)
        }?.map { f -> buildModel(f) }?.sortedBy { it.displayName } ?: emptyList()
    }

    fun listByCategory(ctx: Context, category: ModelCategory): List<SamuModel> =
        list(ctx).filter { it.category == category }

    fun listByFamily(ctx: Context): Map<ModelFamily, List<SamuModel>> =
        list(ctx).groupBy { it.family }

    suspend fun import(
        ctx: Context,
        uri: Uri,
        category: ModelCategory,
        mmprojUri: Uri? = null
    ): SamuModel = withContext(Dispatchers.IO) {
        val name = queryName(ctx, uri) ?: "model-${UUID.randomUUID().toString().take(6)}"
        val safeName = ensureExt(name)
        val dest = File(modelsDir(ctx), safeName)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
        } ?: error("Cannot open input stream")

        // Copy mmproj if provided
        var mmprojPath: String? = null
        if (mmprojUri != null) {
            val mName = queryName(ctx, mmprojUri) ?: "mmproj-${dest.name}"
            val mSafe = if (mName.endsWith(".gguf", true)) mName else "$mName.gguf"
            val mDest = File(modelsDir(ctx), mSafe)
            ctx.contentResolver.openInputStream(mmprojUri)?.use { input ->
                mDest.outputStream().use { output -> input.copyTo(output, 1 shl 20) }
            }
            mmprojPath = mDest.absolutePath
        }

        val model = buildModel(dest, category, mmprojPath)
        saveMeta(dest, category, mmprojPath)
        model
    }

    fun updateMeta(ctx: Context, model: SamuModel, category: ModelCategory, mmprojPath: String?) {
        saveMeta(model.file, category, mmprojPath)
    }

    fun delete(ctx: Context, model: SamuModel) {
        if (model.file.exists()) model.file.delete()
        val meta = metaFile(model.file)
        if (meta.exists()) meta.delete()
        // Also delete associated mmproj if it was inside models dir
        model.mmprojPath?.let { p ->
            val f = File(p)
            if (f.exists() && f.parentFile == modelsDir(ctx)) f.delete()
        }
    }

    private fun buildModel(
        file: File,
        categoryOverride: ModelCategory? = null,
        mmprojOverride: String? = null
    ): SamuModel {
        val meta = loadMeta(file)
        val category = categoryOverride ?: meta?.first ?: ModelCategory.TEXT
        val mmproj = mmprojOverride ?: meta?.second
            ?: findSiblingMmproj(file)
        return SamuModel(
            id = file.nameWithoutExtension,
            displayName = file.nameWithoutExtension,
            file = file,
            sizeBytes = file.length(),
            category = category,
            mmprojPath = mmproj
        )
    }

    private fun findSiblingMmproj(modelFile: File): String? {
        val parent = modelFile.parentFile ?: return null
        return parent.listFiles { f ->
            f.isFile && f.name.startsWith("mmproj", ignoreCase = true)
                && f.name.endsWith(".gguf", ignoreCase = true)
        }?.firstOrNull()?.absolutePath
    }

    private fun metaFile(modelFile: File): File =
        File(modelFile.parentFile, modelFile.name + META_SUFFIX)

    private fun saveMeta(modelFile: File, category: ModelCategory, mmprojPath: String?) {
        try {
            val obj = JSONObject().apply {
                put("category", category.name)
                if (mmprojPath != null) put("mmproj", mmprojPath)
            }
            metaFile(modelFile).writeText(obj.toString())
        } catch (_: Exception) {}
    }

    private fun loadMeta(modelFile: File): Pair<ModelCategory, String?>? {
        return try {
            val f = metaFile(modelFile)
            if (!f.exists()) return null
            val obj = JSONObject(f.readText())
            val cat = ModelCategory.fromName(obj.optString("category", "")) ?: ModelCategory.TEXT
            val mmproj = obj.optString("mmproj", "").takeIf { it.isNotBlank() }
            cat to mmproj
        } catch (_: Exception) { null }
    }

    private fun ensureExt(name: String): String {
        val lc = name.lowercase()
        if (ACCEPTED_EXT.any { lc.endsWith(it) }) return name
        return "$name.gguf"
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
