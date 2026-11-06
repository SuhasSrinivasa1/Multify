package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.Gson
import com.multify.traderpro.engine.HardStopLossPolicy
import com.multify.traderpro.engine.ResearchOutcomeChronology
import com.multify.traderpro.engine.ResearchOutcomeChronologyPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Research-only append journal, distinct from broker fills and the legacy Fixed status labels. */
@Singleton
class ResearchOutcomeChronologyJournal @Inject constructor(@ApplicationContext context: Context) {
    private val file = File(context.filesDir, "research_outcome_chronology.jsonl")
    private val gson = Gson()
    private val lock = Any()
    private val latest = linkedMapOf<String, ResearchOutcomeChronology>()

    init {
        if (file.isFile) file.forEachLine { line ->
            runCatching { gson.fromJson(line, ResearchOutcomeChronology::class.java) }
                .getOrNull()?.takeIf { it.key.isNotBlank() }?.let { latest[it.key] = it }
        }
    }

    fun observe(
        source: String, symbol: String, entryAtMs: Long, entryPrice: Double,
        targetPrice: Double, price: Double, observedAtMs: Long, quoteSource: String
    ): ResearchOutcomeChronology? = synchronized(lock) {
        if (source !in setOf("FIXED","FORECAST") || !symbol.matches(Regex("[A-Z0-9&.\\-]{1,30}")) ||
            !entryPrice.isFinite() || entryPrice <= 0.0 ||
            !targetPrice.isFinite() || targetPrice <= entryPrice || entryAtMs <= 0L) return@synchronized null
        // Version the key so historic -2% observations remain intact while new
        // research follows the live -5% hard-stop policy.
        val key = "$source|$entryAtMs|$symbol|HARD_STOP_${HardStopLossPolicy.MAX_LOSS_PCT.toInt()}PCT"
        val old = latest[key] ?: ResearchOutcomeChronology(
            key, source, symbol, entryAtMs, entryPrice, targetPrice,
            HardStopLossPolicy.stopPrice(entryPrice)
        )
        val next = ResearchOutcomeChronologyPolicy.observe(old, price, observedAtMs, quoteSource)
        if (next == old) return@synchronized null
        latest[key] = next
        file.appendText(gson.toJson(next) + "\n")
        next
    }

    fun recent(limit: Int = 500): List<ResearchOutcomeChronology> = synchronized(lock) {
        latest.values.sortedByDescending { it.entryAtMs }.take(limit)
    }

    fun archive(): String = synchronized(lock) { if (file.isFile) file.readText() else "" }

    fun restore(jsonl: String): Int = synchronized(lock) {
        var count = 0
        jsonl.lineSequence().forEach { line ->
            if (line.isBlank() || line.length > 10_000 || count >= 10_000) return@forEach
            val row = runCatching { gson.fromJson(line, ResearchOutcomeChronology::class.java) }
                .getOrNull() ?: return@forEach
            val legacyKey = "${row.source}|${row.entryAtMs}|${row.symbol}"
            val currentKey = "$legacyKey|HARD_STOP_${HardStopLossPolicy.MAX_LOSS_PCT.toInt()}PCT"
            if (row.source !in setOf("FIXED","FORECAST") || row.entryAtMs <= 0L ||
                row.symbol.isBlank() || row.key !in setOf(legacyKey, currentKey) ||
                row.sampleCount < 0 || row.lastObservedAtMs < row.entryAtMs) return@forEach
            if (row.lastObservedAtMs > (latest[row.key]?.lastObservedAtMs ?: 0L)) {
                file.appendText(gson.toJson(row) + "\n")
                latest[row.key] = row
                count++
            }
        }
        count
    }
}
