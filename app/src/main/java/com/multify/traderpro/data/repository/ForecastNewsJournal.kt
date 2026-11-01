package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Append-only, portable forecast-only event journal. No API keys, holdings or order state. */
data class ForecastNewsEvent(
    val id: String,
    val source: String,
    val symbol: String,
    val headline: String,
    val summary: String,
    val url: String,
    val category: String,
    val direction: String,
    val publishedAtMs: Long,
    val firstSeenAtMs: Long,
    val featureJson: String,
    val classificationConfidence: Double,
    val status: String = "NEW",
    val rationale: String = "",
    val baselinePrice: Double? = null,
    val baselineAtMs: Long? = null,
    val maxFavourablePct: Double = 0.0,
    val maxAdversePct: Double = 0.0,
    val lastObservedPrice: Double? = null,
    val lastObservedAtMs: Long? = null,
    val firstOnePctAtMs: Long? = null,
    val completedAtMs: Long? = null
)

@Singleton
class ForecastNewsJournal @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "forecast_news_events.jsonl")
    private val gson = GsonBuilder().serializeNulls().create()
    private val lock = Any()
    private val latest = LinkedHashMap<String, ForecastNewsEvent>()

    init {
        if (file.isFile) file.forEachLine { line ->
            runCatching { gson.fromJson(line, ForecastNewsEvent::class.java) }
                .getOrNull()?.let { if (it.id.isNotBlank()) latest[it.id] = it }
        }
    }

    /** A write is durable before this method reports a new event. */
    fun addIfAbsent(row: ForecastNewsEvent): Boolean = synchronized(lock) {
        if (row.id in latest) return@synchronized false
        append(row)
        latest[row.id] = row
        true
    }

    fun update(row: ForecastNewsEvent) = synchronized(lock) {
        require(latest.containsKey(row.id)) { "Unknown news event" }
        append(row)
        latest[row.id] = row
    }

    fun recent(limit: Int = 500): List<ForecastNewsEvent> = synchronized(lock) {
        latest.values.toList().takeLast(limit)
    }

    fun dueForEvaluation(nowMs: Long, limit: Int = 3): List<ForecastNewsEvent> = synchronized(lock) {
        latest.values.asSequence().filter { it.status == "NEW" && it.symbol.isNotBlank() &&
            it.publishedAtMs in (nowMs - 20L * 60_000L)..nowMs
        }.take(limit).toList()
    }

    fun dueForOutcome(nowMs: Long, limit: Int = 4): List<ForecastNewsEvent> = synchronized(lock) {
        latest.values.asSequence().filter { it.baselinePrice != null && it.completedAtMs == null &&
            (it.lastObservedAtMs == null || nowMs - it.lastObservedAtMs >= 60_000L)
        }.take(limit).toList()
    }

    /** Purely prior completed outcomes: no future or same-day labels leak into prediction. */
    data class CategoryStats(val category: String, val observations: Int, val hitsOnePct: Int,
        val distinctDays: Int, val hitRate: Double, val averageMfe: Double)

    fun completedCategoryStats(beforeMs: Long): List<CategoryStats> = synchronized(lock) {
        latest.values.filter { row -> row.completedAtMs != null &&
            row.completedAtMs < beforeMs && row.baselinePrice != null &&
            row.category != "OTHER" && row.lastObservedAtMs != null
        }.groupBy { it.category }.map { (category, rows) ->
            val hits = rows.count { it.firstOnePctAtMs != null }
            CategoryStats(category, rows.size, hits,
                rows.map { it.publishedAtMs / (24 * 60 * 60_000L) }.distinct().size,
                hits.toDouble() / rows.size, rows.map { it.maxFavourablePct }.average())
        }.sortedByDescending { it.observations }
    }

    /** Exports the full immutable event/update stream, not just newest rows. */
    fun archive(): String = synchronized(lock) { if (file.exists()) file.readText() else "" }

    /** Idempotent merge, keeping locally newer observations; never enables a trading state. */
    fun restore(jsonl: String): Int = synchronized(lock) {
        var restored = 0
        jsonl.lineSequence().forEach { line ->
            if (line.isBlank()) return@forEach
            val row = runCatching { gson.fromJson(line, ForecastNewsEvent::class.java) }.getOrNull()
                ?: return@forEach
            if (row.id.isBlank() || row.id.length > 128 || row.symbol.length > 32) return@forEach
            val local = latest[row.id]
            if (local == null || (row.lastObservedAtMs ?: row.firstSeenAtMs) >
                (local.lastObservedAtMs ?: local.firstSeenAtMs)) {
                append(row)
                latest[row.id] = row
                restored++
            }
        }
        restored
    }

    private fun append(row: ForecastNewsEvent) {
        file.appendText(gson.toJson(row) + "\n")
    }
}
