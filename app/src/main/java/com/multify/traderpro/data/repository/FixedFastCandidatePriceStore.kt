package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.network.FixedFastCandidateDto
import com.multify.traderpro.engine.FixedFastCandidateSelectionPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** REST client receipt timestamps are NOT verified exchange feed timestamps.
 * This information NEVER reaches Fixed ranking, confirmation, orders or SELL/GTT.
 */
internal data class FixedFastCandidateEvidence(
    val date: String, val symbol: String, val origin: String,
    val initialAtMs: Long, val baselinePrice: Double,
    val lastObservedAtMs: Long, val latestPrice: Double,
    val sampleCount: Int, val tradingEffect: String = "NONE"
) {
    fun dto() = FixedFastCandidateDto(
        symbol, origin, initialAtMs, baselinePrice, lastObservedAtMs,
        latestPrice, sampleCount, (latestPrice / baselinePrice - 1.0) * 100.0
    )
}

@Singleton
class FixedFastCandidatePriceStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fixed_fast_candidates_v804", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    @Synchronized fun capture(date: String,
        candidates: List<FixedFastCandidateSelectionPolicy.Candidate>,
        prices: Map<String, Double>, observedAtMs: Long
    ): Int {
        val byKey = load().associateBy { "${it.date}|${it.symbol}" }.toMutableMap()
        var count = 0
        for (c in candidates) {
            val price = prices[c.symbol] ?: prices["NSE_${c.symbol}"] ?: continue
            if (!price.isFinite() || price <= 0.0 || observedAtMs <= c.observedAtMs) continue
            val key = "$date|${c.symbol}"
            val prior = byKey[key]
            if (prior != null && observedAtMs <= prior.lastObservedAtMs) continue
            byKey[key] = FixedFastCandidateEvidence(
                date, c.symbol, prior?.origin ?: c.origin,
                prior?.initialAtMs ?: c.observedAtMs,
                prior?.baselinePrice ?: c.baselinePrice,
                observedAtMs, price, (prior?.sampleCount ?: 0) + 1
            )
            count++
        }
        if (count > 0) save(byKey.values)
        return count
    }

    @Synchronized fun recent(date: String, limit: Int = 12): List<FixedFastCandidateDto> =
        load().filter { it.date == date }.sortedByDescending { it.lastObservedAtMs }
            .take(limit).map { it.dto() }

    @Synchronized fun archive(): String = gson.toJson(load())

    @Synchronized fun restoreArchived(json: String): Int {
        if (json.length > 1_500_000) return 0
        val incoming = runCatching {
            gson.fromJson(json, Array<FixedFastCandidateEvidence>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList()).take(500)
        val all = load().associateBy { "${it.date}|${it.symbol}" }.toMutableMap()
        var imported = 0
        for (r in incoming) {
            if (!r.symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) ||
                runCatching { LocalDate.parse(r.date) }.isFailure ||
                !r.baselinePrice.isFinite() || r.baselinePrice <= 0.0 ||
                !r.latestPrice.isFinite() || r.latestPrice <= 0.0 ||
                r.lastObservedAtMs <= r.initialAtMs ||
                r.initialAtMs <= 0L || r.sampleCount < 1 || r.tradingEffect != "NONE") continue
            val key = "${r.date}|${r.symbol}"
            if ((all[key]?.lastObservedAtMs ?: 0L) < r.lastObservedAtMs) {
                all[key] = r
                imported++
            }
        }
        if (imported > 0) save(all.values)
        return imported
    }

    private fun load(): List<FixedFastCandidateEvidence> = runCatching {
        gson.fromJson(prefs.getString("rows", "[]"), Array<FixedFastCandidateEvidence>::class.java)
            ?.toList().orEmpty()
    }.getOrDefault(emptyList())

    private fun save(rows: Collection<FixedFastCandidateEvidence>) {
        val dates = rows.map { it.date }.distinct().sortedDescending().take(5).toSet()
        prefs.edit().putString("rows", gson.toJson(rows.filter { it.date in dates }
            .sortedByDescending { it.lastObservedAtMs }.take(500))).commit()
    }
}
