package com.multify.traderpro.data.logging

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    @ApplicationContext context: Context
) {
    private val directory = File(context.filesDir, "audit").apply { mkdirs() }
    private val file = File(directory, "system_audit.jsonl")
    private val lock = Any()

    fun log(category: String, event: String, details: Map<String, Any?> = emptyMap()) {
        runCatching {
            synchronized(lock) {
                rotateIfNeeded()
                val obj = JSONObject()
                    .put("timestamp_ms", System.currentTimeMillis())
                    .put("timestamp_utc", Instant.now().toString())
                    .put("category", category)
                    .put("event", event)
                val safe = JSONObject()
                details.forEach { (key, value) ->
                    if (!isSensitiveKey(key)) safe.put(key, value ?: JSONObject.NULL)
                }
                obj.put("details", safe)
                file.appendText(obj.toString() + "\n")
            }
        }
    }

    fun readAll(): String = synchronized(lock) {
        buildString {
            val old = File(directory, "system_audit.1.jsonl")
            if (old.exists()) append(old.readText())
            if (file.exists()) append(file.readText())
        }
    }

    private fun rotateIfNeeded() {
        if (!file.exists() || file.length() < MAX_BYTES) return
        val old = File(directory, "system_audit.1.jsonl")
        if (old.exists()) old.delete()
        file.renameTo(old)
    }

    private fun isSensitiveKey(key: String): Boolean {
        val k = key.lowercase()
        return k.contains("secret") || k.contains("token") || k.contains("authorization") ||
            k.contains("password") || k.contains("checksum") || k == "totp"
    }

    companion object { private const val MAX_BYTES = 5L * 1024L * 1024L }
}
