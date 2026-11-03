package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Read-only, bounded audit of official Univest alerts, including ignored formats.
 * This is not a trading signal and never produces orders. Source content is only
 * exported when the user explicitly exports the diagnostic ZIP.
 */
@Singleton
class ForecastSourceNotificationJournal @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "forecast_univest_notification_capture.jsonl")
    private val gson = GsonBuilder().serializeNulls().create()
    private val lock = Any()
    data class Record(
        val fingerprint: String,
        val postedAtMs: Long,
        val receivedAtMs: Long,
        val classification: String,
        val symbol: String?,
        val outcome: String,
        val title: String,
        val text: String
    )
    fun append(at: Long, received: Long, classification: String, symbol: String?, outcome: String,
               title: String, body: String) = synchronized(lock) {
        val safeTitle = title.take(300)
        val safeBody = body.take(1800)
        val content = "$at|$safeTitle|$safeBody"
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray()).joinToString("") { "%02x".format(it) }
        val rows = read().toMutableList()
        if (rows.none { it.fingerprint == fingerprint }) {
            rows.add(Record(fingerprint, at, received, classification, symbol, outcome, safeTitle, safeBody))
            file.writeText(rows.takeLast(500).joinToString("\n") { gson.toJson(it) } + "\n")
        }
    }
    fun archive(): String = synchronized(lock) { if (file.isFile) file.readText() else "" }
    fun count(): Int = synchronized(lock) { read().size }
    private fun read(): List<Record> =
        if (!file.isFile) emptyList() else file.useLines { lines ->
            lines.mapNotNull { runCatching { gson.fromJson(it, Record::class.java) }.getOrNull() }.toList()
        }
}
