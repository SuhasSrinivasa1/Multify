package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.network.EarlyOpportunityDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Research-only durable timeline. This never restores/replays trading state and
 * is not used by any Fixed/Forecast strategy or broker execution method.
 *
 * FixedRecommendationRecord.generatedAtMs is scan START, not notification time.
 * It is explicitly recorded as a proxy, never reported as actual publication.
 */
internal data class EarlyIdeaEvidence(
    val date: String,
    val symbol: String,
    val observedAtMs: Long,
    val observedPrice: Double,
    val qualityScore: Int,
    val signalType: String,
    val lastObservedAtMs: Long,
    val latestObservedPrice: Double,
    val officialScanStartedAtMs: Long? = null,
    val officialEntryPrice: Double? = null,
    val source: String = "ANDROID_RECEIPT_NOT_EXCHANGE_TICK",
    val tradeEffect: String = "NONE"
)

@Singleton
class EarlyOpportunityJournal @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("early_opportunity_ledger_v810", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().create()

    @Synchronized internal fun observe(
        date: String,
        ideas: List<EarlyOpportunityDto>,
        official: List<FixedRecommendationRecord>
    ): Int {
        val stored = load().associateBy { "${it.date}|${it.symbol}|${it.observedAtMs}" }.toMutableMap()
        var newCount = 0
        for (idea in ideas.take(3)) {
            if (idea.tradeEligible || idea.status !in setOf("WATCH", "EARLY_BUY_IDEA") ||
                !idea.symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) ||
                !idea.observedPrice.isFinite() || idea.observedPrice <= 0.0 ||
                idea.firstObservedAtMs <= 0L) continue
            val key = "$date|${idea.symbol}|${idea.firstObservedAtMs}"
            val old = stored[key]
            if (old == null) {
                stored[key] = EarlyIdeaEvidence(
                    date, idea.symbol, idea.firstObservedAtMs, idea.observedPrice,
                    idea.qualityScore, idea.status, idea.lastObservedAtMs,
                    idea.observedPrice
                )
                newCount++
            } else if (idea.lastObservedAtMs > old.lastObservedAtMs) {
                stored[key] = old.copy(
                    lastObservedAtMs = idea.lastObservedAtMs,
                    latestObservedPrice = idea.observedPrice
                )
            }
        }
        val bySymbol = official.groupBy { it.symbol.uppercase() }
        val resolved = stored.values.map { old ->
            if (old.date != date || old.officialScanStartedAtMs != null) old
            else {
                val match = bySymbol[old.symbol]?.filter {
                    it.generatedAtMs > old.observedAtMs
                }?.minByOrNull { it.generatedAtMs }
                if (match == null) old else old.copy(
                    officialScanStartedAtMs = match.generatedAtMs,
                    officialEntryPrice = match.entryPrice
                )
            }
        }
        val changed = newCount > 0 || resolved.any {
            val key = "${it.date}|${it.symbol}|${it.observedAtMs}"
            it != stored[key]
        }
        if (changed) save(resolved)
        return newCount
    }

    @Synchronized fun archive(): String = gson.toJson(load())

    /** Research-only archive import; never restores positions, BUY orders or GTTs. */
    @Synchronized fun restoreArchived(json: String): Int {
        if (json.length > 2_000_000) return 0
        val incoming = runCatching {
            gson.fromJson(json, Array<EarlyIdeaEvidence>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList()).take(500)
        val indexed = load().associateBy {
            "${it.date}|${it.symbol}|${it.observedAtMs}"
        }.toMutableMap()
        var added = 0
        for (row in incoming) {
            if (runCatching { LocalDate.parse(row.date) }.isFailure ||
                !row.symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) ||
                row.tradeEffect != "NONE" || row.observedAtMs <= 0L ||
                !row.observedPrice.isFinite() || row.observedPrice <= 0.0 ||
                row.qualityScore !in 0..100) continue
            val key = "${row.date}|${row.symbol}|${row.observedAtMs}"
            if (key !in indexed) { indexed[key] = row; added++ }
        }
        if (added > 0) save(indexed.values)
        return added
    }

    private fun load(): List<EarlyIdeaEvidence> = runCatching {
        gson.fromJson(prefs.getString("rows", "[]"),
            Array<EarlyIdeaEvidence>::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())

    private fun save(rows: Collection<EarlyIdeaEvidence>) {
        val dates = rows.map { it.date }.distinct().sortedDescending().take(30).toSet()
        prefs.edit().putString("rows", gson.toJson(rows.filter { it.date in dates }
            .sortedWith(compareByDescending<EarlyIdeaEvidence> { it.date }
                .thenByDescending { it.observedAtMs }).take(500))).commit()
    }
}
