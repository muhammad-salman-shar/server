package com.neurasamu.build.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ApiKey(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val key: String,
    val createdAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true
)

object ApiKeyStore {
    private const val PREFS = "samu_apikeys"
    private const val K_LIST = "list"

    fun load(ctx: Context): MutableList<ApiKey> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = p.getString(K_LIST, null) ?: return mutableListOf()
        return try {
            val arr = JSONArray(raw)
            val out = mutableListOf<ApiKey>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    ApiKey(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        label = o.optString("label", "Key"),
                        key = o.optString("key", ""),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        enabled = o.optBoolean("enabled", true)
                    )
                )
            }
            out
        } catch (_: Exception) { mutableListOf() }
    }

    fun save(ctx: Context, list: List<ApiKey>) {
        val arr = JSONArray()
        list.forEach { k ->
            arr.put(JSONObject().apply {
                put("id", k.id)
                put("label", k.label)
                put("key", k.key)
                put("createdAt", k.createdAt)
                put("enabled", k.enabled)
            })
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(K_LIST, arr.toString()) }
    }

    fun generate(label: String): ApiKey {
        val random = UUID.randomUUID().toString().replace("-", "").take(24)
        return ApiKey(label = label, key = "samu-$random")
    }
}
