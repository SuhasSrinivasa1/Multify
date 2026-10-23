package com.multify.traderpro.data.logging

import android.content.Context
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuditLogger @Inject constructor(
    @ApplicationContext context: Context
) {
    private val directory = File(context.filesDir, "audit").apply { mkdirs() }
    private val file = File(directory, "system_audit.jsonl")
    private val lock = Any()
    private val asyncDropped = AtomicLong(0L)
    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(ASYNC_QUEUE_CAPACITY),
        { runnable ->
            Thread(runnable, "multify-audit-writer").apply {
                isDaemon = true
                priority = Thread.MIN_PRIORITY
            }
        },
        { _, _ -> asyncDropped.incrementAndGet() }
    )

    fun log(category: String, event: String, details: Map<String, Any?> = emptyMap()) {
        write(category, event, details)
    }

    /**
     * Non-blocking market-hours logging. The caller only enqueues a tiny object; JSON serialization
     * and disk I/O run on a dedicated low-priority thread and never sit in the trading critical path.
     */
    fun logAsync(category: String, event: String, details: Map<String, Any?> = emptyMap()) {
        val capturedAtMs = System.currentTimeMillis()
        val capturedUtc = Instant.now().toString()
        executor.execute {
            runCatching {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                write(category, event, details, capturedAtMs, capturedUtc)
            }
        }
    }

    fun asyncDroppedCount(): Long = asyncDropped.get()

    fun readAll(): String {
        // Export is user-initiated / non-critical. Drain queued telemetry first so the ZIP contains
        // the most recent 429 and latency records.
        runCatching {
            executor.submit {}.get(EXPORT_FLUSH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }
        return synchronized(lock) {
            buildString {
                val old = File(directory, "system_audit.1.jsonl")
                if (old.exists()) append(old.readText())
                if (file.exists()) append(file.readText())
            }
        }
    }

    private fun write(
        category: String,
        event: String,
        details: Map<String, Any?>,
        timestampMs: Long = System.currentTimeMillis(),
        timestampUtc: String = Instant.now().toString()
    ) {
        runCatching {
            synchronized(lock) {
                rotateIfNeeded()
                val obj = JSONObject()
                    .put("timestamp_ms", timestampMs)
                    .put("timestamp_utc", timestampUtc)
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

    companion object {
        private const val MAX_BYTES = 12L * 1024L * 1024L
        private const val ASYNC_QUEUE_CAPACITY = 4096
        private const val EXPORT_FLUSH_TIMEOUT_MS = 3_000L
    }
}
