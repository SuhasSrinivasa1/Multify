package com.multify.traderpro.data.logging

import android.content.Context
import com.multify.traderpro.BuildConfig
import com.multify.traderpro.engine.DecisionEvidencePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Curated local decision/outcome corpus. Stored inside this app's private files directory:
 * signed APK assets cannot be edited or appended to at runtime.
 *
 * Never changes trading rules and never submits any request. Backups are user-exported;
 * uninstalling or changing signing certificates does not automatically migrate it.
 */
@Singleton
class DecisionEvidenceJournal @Inject constructor(@ApplicationContext context: Context) {
    private val directory = File(context.filesDir, "decision_evidence").apply { mkdirs() }
    private val activeFile = File(directory, "decision_evidence.jsonl")
    private val lock = Any()
    private val failedWrites = AtomicLong(0L)

    fun capture(category: String, event: String, details: JSONObject, timestampMs: Long) {
        if (!DecisionEvidencePolicy.shouldCapture(category, event)) return
        runCatching {
            val clean = JSONObject()
            details.keys().forEach { key ->
                if (!DecisionEvidencePolicy.allowedDetailKey(key)) return@forEach
                val value = details.opt(key)
                if (value != null && value != JSONObject.NULL) {
                    when (value) {
                        is JSONObject -> if (value.toString().length <= 16_000)
                            clean.put(key, value)
                        else clean.put(key, "OVERSIZED_NESTED_CONTEXT_IN_MAIN_AUDIT")
                        is org.json.JSONArray -> if (value.toString().length <= 16_000)
                            clean.put(key, value)
                        else clean.put(key, "OVERSIZED_VOTES_IN_MAIN_AUDIT")
                        is Number, is Boolean -> clean.put(key, value)
                        else -> clean.put(key, value.toString().take(500))
                    }
                }
            }
            val row = JSONObject()
                .put("schema", 1)
                .put("at_ms", timestampMs)
                .put("at_utc", Instant.ofEpochMilli(timestampMs).toString())
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("category", category)
                .put("event", event)
                .put("classification", DecisionEvidencePolicy.classification(category, event))
                .put("details", clean)
            val serialized = row.toString()
            if (serialized.length > MAX_LINE_CHARS) return
            synchronized(lock) {
                rotateIfNeeded()
                activeFile.appendText(serialized + "\n")
            }
        }.onFailure { failedWrites.incrementAndGet() }
        // Evidence storage is strictly advisory. A disk failure must not block a BUY,
        // SELL, broker reconciliation, or creation of a protective GTT.
    }

    fun archive(): String = synchronized(lock) {
        files().joinToString("") { it.readText() }
    }

    fun restoreArchived(input: String): Int = synchronized(lock) {
        // Restored evidence is never treated as a live signal or as an executed trade.
        if (input.length > MAX_IMPORT_CHARS) return@synchronized 0
        val seen = files().flatMap { it.readLines() }
            .mapNotNull(::recordId).toHashSet()
        var added = 0
        for (line in input.lineSequence()) {
            if (line.length !in 20..MAX_LINE_CHARS || added >= MAX_IMPORT_ROWS) continue
            val id = recordId(line) ?: continue
            if (!seen.add(id)) continue
            val row = runCatching { JSONObject(line) }.getOrNull() ?: continue
            if (!DecisionEvidencePolicy.shouldCapture(
                    row.optString("category"), row.optString("event"))) continue
            rotateIfNeeded()
            activeFile.appendText(line + "\n")
            added++
        }
        added
    }

    fun failedWriteCount(): Long = failedWrites.get()

    fun lineCount(): Int = synchronized(lock) {
        files().sumOf { file -> file.useLines { it.count() } }
    }

    private fun recordId(line: String): String? {
        val json = runCatching { JSONObject(line) }.getOrNull() ?: return null
        if (json.optInt("schema") != 1 || !json.has("at_ms") ||
            !json.has("category") || !json.has("event")) return null
        return MessageDigest.getInstance("SHA-256").digest(line.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private fun files(): List<File> = directory.listFiles()
        ?.filter { it.isFile && it.name.startsWith("decision_evidence") && it.name.endsWith(".jsonl") }
        ?.sortedWith(compareBy<File> { if (it.name == "decision_evidence.jsonl") 1 else 0 }
            .thenBy { it.name })
        .orEmpty()

    private fun rotateIfNeeded() {
        if (!activeFile.exists() || activeFile.length() < SEGMENT_BYTES) return
        val filename = "decision_evidence.${System.currentTimeMillis()}.jsonl"
        check(activeFile.renameTo(File(directory, filename))) {
            "Unable to rotate decision evidence journal"
        }
        // Never silently delete decision evidence. The user can export an archive
        // and explicitly clear app data if storage management is needed.
    }

    companion object {
        private const val SEGMENT_BYTES = 8L * 1024L * 1024L
        private const val MAX_LINE_CHARS = 48_000
        private const val MAX_IMPORT_CHARS = 128 * 1024 * 1024
        private const val MAX_IMPORT_ROWS = 50_000
    }
}
