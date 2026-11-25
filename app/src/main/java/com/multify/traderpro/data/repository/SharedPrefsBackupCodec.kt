package com.multify.traderpro.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Typed SharedPreferences codec for portable research state only. */
internal object SharedPrefsBackupCodec {
    private val gson = Gson()

    fun encode(prefs: SharedPreferences): String {
        val root = JsonObject()
        prefs.all.forEach { (key, value) ->
            val item = JsonObject()
            when (value) {
                is String -> { item.addProperty("type", "string"); item.addProperty("value", value) }
                is Int -> { item.addProperty("type", "int"); item.addProperty("value", value) }
                is Long -> { item.addProperty("type", "long"); item.addProperty("value", value) }
                is Float -> { item.addProperty("type", "float"); item.addProperty("value", value) }
                is Boolean -> { item.addProperty("type", "boolean"); item.addProperty("value", value) }
                is Set<*> -> {
                    item.addProperty("type", "string_set")
                    item.add("value", gson.toJsonTree(value.filterIsInstance<String>()))
                }
            }
            if (item.has("type")) root.add(key, item)
        }
        return gson.toJson(root)
    }

    /** Import absent research preferences; never overwrite current device settings or lower cooldown deadlines. */
    fun restoreMerge(prefs: SharedPreferences, json: String): Int {
        val root = JsonParser.parseString(json).asJsonObject
        val current = prefs.all
        val editor = prefs.edit()
        var restored = 0
        for ((key, element) in root.entrySet()) {
            if (!element.isJsonObject) continue
            val item = element.asJsonObject
            val type = item.get("type")?.asString ?: continue
            val value = item.get("value") ?: continue
            val existing = current[key]
            if (existing == null) {
                put(editor, key, type, value)
                restored++
                continue
            }
            val isDeadline = key.contains("cooldown_until") || key.contains("_retry_") ||
                key.endsWith("_retry_at_ms") || key.endsWith("_until_ms") ||
                key.endsWith("_success_at_ms")
            val isFailureCounter = key.contains("nse_403_failures")
            if (isDeadline && existing is Long && type == "long" && value.asLong > existing) {
                editor.putLong(key, value.asLong)
                restored++
            } else if (isFailureCounter && existing is Int && type == "int" && value.asInt > existing) {
                editor.putInt(key, value.asInt)
                restored++
            } else if (existing is String && existing.isBlank() && type == "string" && value.asString.isNotBlank()) {
                editor.putString(key, value.asString)
                restored++
            } else if (existing is Set<*> && type == "string_set") {
                val merged = existing.filterIsInstance<String>().toMutableSet()
                value.asJsonArray.forEach { merged += it.asString }
                if (merged.size != existing.size) {
                    editor.putStringSet(key, merged)
                    restored++
                }
            }
        }
        check(editor.commit()) { "Unable to persist restored research preferences" }
        return restored
    }

    private fun put(
        editor: SharedPreferences.Editor,
        key: String,
        type: String,
        value: com.google.gson.JsonElement
    ) {
        when (type) {
            "string" -> editor.putString(key, value.asString)
            "int" -> editor.putInt(key, value.asInt)
            "long" -> editor.putLong(key, value.asLong)
            "float" -> editor.putFloat(key, value.asFloat)
            "boolean" -> editor.putBoolean(key, value.asBoolean)
            "string_set" -> editor.putStringSet(key, value.asJsonArray.map { it.asString }.toSet())
        }
    }
}
