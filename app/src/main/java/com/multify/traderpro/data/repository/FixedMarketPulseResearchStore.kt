package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.network.FixedPulseResearchDto
import com.multify.traderpro.engine.FixedMarketPulsePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Research-only early pulse from already-collected Fixed LTP snapshots. No trading effect. */
internal data class FixedPulseEvidence(
    val date: String, val symbol: String, val atMs: Long,
    val priceBefore: Double, val firstPulsePrice: Double, val pulsePct: Double,
    val latestAtMs: Long, val latestPrice: Double,
    val publishedAtMs: Long? = null, val officialEntryPrice: Double? = null,
    val source: String = "FIRST_APP_PULSE_NOT_VERIFIED_NSE_OPEN"
) {
    fun dto() = FixedPulseResearchDto(symbol, atMs, firstPulsePrice, pulsePct,
        latestPrice, officialEntryPrice, publishedAtMs)
}

@Singleton
class FixedMarketPulseResearchStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fixed_market_pulse_shadow_v803", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().create()

    @Synchronized internal fun capture(date: String, snapshot: FixedPersistedMarketDiscoveryState?,
        recommendations: List<FixedRecommendationRecord>): Int {
        if (snapshot == null || snapshot.date != date || snapshot.updatedAtMs <= 0L) return 0
        val oldAt = if (prefs.getString("price_date", "") == date)
            prefs.getLong("price_snapshot_at", 0L) else 0L
        val old = if (oldAt > 0L) FixedMarketDiscoveryStateCodec.decode(
            prefs.getString("price_snapshot_values", "").orEmpty()) else emptyMap()
        val pulses = FixedMarketPulsePolicy.rank(old, snapshot.previousLtps, oldAt, snapshot.updatedAtMs)
        val indexed = load().associateBy { "${it.date}|${it.symbol}" }.toMutableMap()
        var created = 0
        for (p in pulses) {
            val key = "$date|${p.symbol}"
            val prior = indexed[key]
            if (prior == null) {
                indexed[key] = FixedPulseEvidence(date, p.symbol, snapshot.updatedAtMs,
                    p.firstPrice, p.latestPrice, p.increasePct, snapshot.updatedAtMs, p.latestPrice)
                created++
            } else if (snapshot.updatedAtMs > prior.latestAtMs)
                indexed[key] = prior.copy(latestAtMs = snapshot.updatedAtMs, latestPrice = p.latestPrice)
        }
        val official = recommendations.associateBy { it.symbol.uppercase() }
        val reconciled = indexed.values.map { prior ->
            val match = if (prior.date == date) official[prior.symbol] else null
            if (match != null && prior.publishedAtMs == null && match.generatedAtMs > prior.atMs)
                prior.copy(publishedAtMs = match.generatedAtMs, officialEntryPrice = match.entryPrice)
            else prior
        }
        if (created > 0 || reconciled.any { indexed["${it.date}|${it.symbol}"] != it }) save(reconciled)
        prefs.edit().putString("price_date", date).putLong("price_snapshot_at", snapshot.updatedAtMs)
            .putString("price_snapshot_values", FixedMarketDiscoveryStateCodec.encode(snapshot.previousLtps))
            .commit()
        return created
    }

    @Synchronized fun recent(date: String, limit: Int = 12): List<FixedPulseResearchDto> =
        load().filter { it.date == date }.sortedByDescending { it.atMs }.take(limit).map { it.dto() }

    @Synchronized fun archive(): String = gson.toJson(load())

    @Synchronized fun restoreArchived(json: String): Int {
        if (json.length > 2_000_000) return 0
        val input = runCatching {
            gson.fromJson(json, Array<FixedPulseEvidence>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList()).take(1000)
        val known = load().associateBy { "${it.date}|${it.symbol}" }.toMutableMap()
        var added = 0
        for (r in input) {
            if (!r.symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) ||
                runCatching { LocalDate.parse(r.date) }.isFailure ||
                r.atMs <= 0L || r.latestAtMs < r.atMs ||
                !r.firstPulsePrice.isFinite() || r.firstPulsePrice <= 0.0 ||
                !r.latestPrice.isFinite() || r.latestPrice <= 0.0 ||
                !r.pulsePct.isFinite() || r.pulsePct !in 0.0..20.0) continue
            val key = "${r.date}|${r.symbol}"
            if (key !in known) { known[key] = r; added++ }
        }
        if (added > 0) save(known.values)
        return added
    }

    private fun load(): List<FixedPulseEvidence> = runCatching {
        gson.fromJson(prefs.getString("records", "[]"), Array<FixedPulseEvidence>::class.java)?.toList().orEmpty()
    }.getOrDefault(emptyList())

    private fun save(rows: Collection<FixedPulseEvidence>) {
        val dates = rows.map { it.date }.distinct().sortedDescending().take(10).toSet()
        prefs.edit().putString("records", gson.toJson(rows.filter { it.date in dates }
            .sortedWith(compareByDescending<FixedPulseEvidence> { it.date }.thenByDescending { it.atMs })
            .take(1000))).commit()
    }
}
