package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Historical Fixed RESEARCH evidence only, never broker state or a live Fixed recommendation.
 *
 * Imports the fixed_recommendations.json section from a prior diagnostic ZIP without
 * recreating outstanding orders, holdings, execution eligibility or trade alerts.
 * Historical TARGET_HIT is a sampled price observation, NOT realized P&L.
 */
internal data class FixedHistoricalEvidenceRow(
    val date: String,
    val symbol: String,
    val publishedAtMs: Long,
    val entryPrice: Double,
    val targetPrice: Double,
    val targetPct: Double,
    val latestObservedPrice: Double,
    val lastObservedAtMs: Long,
    val maximumFavourablePct: Double,
    val maximumAdversePct: Double,
    val observedTargetHit: Boolean,
    val snapshotOutcome: String,
    val observedAfterPublication: Boolean,
    val provenance: String = "FIXED_ARCHIVED_RECOMMENDATION_NOT_TRADE",
    val isRealizedTrade: Boolean = false
)

internal data class FixedHistoricalEvidenceSummary(
    val rows: Int, val days: Int, val targetObserved: Int, val unresolved: Int
)

/** No broker data is trusted as a real trade without explicit executed-order evidence. */
internal object FixedHistoricalEvidenceValidationPolicy {
    fun valid(r: FixedHistoricalEvidenceRow): Boolean =
        r.date.length == 10 && runCatching { LocalDate.parse(r.date) }.isSuccess &&
            r.symbol.matches(Regex("[A-Z0-9&._-]{1,32}")) &&
            r.publishedAtMs > 0L && r.entryPrice.isFinite() && r.entryPrice > 0.0 &&
            r.targetPrice.isFinite() && r.targetPrice > r.entryPrice &&
            r.targetPct.isFinite() && r.targetPct > 0.0 && r.targetPct < 25.0 &&
            r.latestObservedPrice.isFinite() && r.latestObservedPrice > 0.0 &&
            r.lastObservedAtMs >= r.publishedAtMs &&
            r.maximumFavourablePct.isFinite() && r.maximumFavourablePct in 0.0..200.0 &&
            r.maximumAdversePct.isFinite() && r.maximumAdversePct in 0.0..200.0 &&
            !r.isRealizedTrade
}

@Singleton
class FixedHistoricalEvidenceArchive @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("fixed_historical_research_v803", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val lock = Any()

    private fun valid(r: FixedHistoricalEvidenceRow): Boolean =
        FixedHistoricalEvidenceValidationPolicy.valid(r)

    private fun key(r: FixedHistoricalEvidenceRow): String = "${r.date}|${r.symbol}"

    private fun list(): List<FixedHistoricalEvidenceRow> =
        runCatching {
            val raw = prefs.getString("observations", null) ?: return@runCatching emptyList()
            gson.fromJson(raw, Array<FixedHistoricalEvidenceRow>::class.java)?.toList().orEmpty()
                .filter(::valid)
        }.getOrDefault(emptyList())

    private fun commit(rows: Collection<FixedHistoricalEvidenceRow>) {
        val recent = rows.map { it.date }.distinct().sortedDescending().take(30).toSet()
        val retained = rows.filter { it.date in recent }
            .sortedWith(compareByDescending<FixedHistoricalEvidenceRow> { it.date }
                .thenByDescending { it.publishedAtMs })
            .take(2500)
        prefs.edit().putString("observations", gson.toJson(retained)).commit()
    }

    private fun fromRecommendation(r: FixedRecommendationRecord): FixedHistoricalEvidenceRow =
        FixedHistoricalEvidenceRow(
            date = r.recommendationDate,
            symbol = r.symbol.uppercase(Locale.US),
            publishedAtMs = r.generatedAtMs,
            entryPrice = r.entryPrice,
            targetPrice = r.targetPrice,
            targetPct = r.targetPct,
            latestObservedPrice = r.lastPrice,
            lastObservedAtMs = r.lastObservedAtMs,
            maximumFavourablePct = r.maxFavourablePct,
            maximumAdversePct = r.maxAdversePct,
            observedTargetHit = r.status == "TARGET_HIT",
            snapshotOutcome = when (r.status) {
                "TARGET_HIT" -> "TARGET_PRICE_OBSERVED_NOT_REALIZED"
                "SESSION_ENDED", "MISSED" -> "SESSION_ENDED_OR_MISSED_RESEARCH"
                else -> "UNRESOLVED_AT_SNAPSHOT"
            },
            observedAfterPublication = r.lastObservedAtMs > r.generatedAtMs
        )

    /** Snapshot current Fixed-only recommendation research without changing any trading state. */
    internal fun capture(rows: List<FixedRecommendationRecord>): Int = synchronized(lock) {
        if (rows.isEmpty()) return@synchronized 0
        val all = list().associateBy(::key).toMutableMap()
        var changed = 0
        for (record in rows) {
            val new = runCatching { fromRecommendation(record) }.getOrNull() ?: continue
            if (!valid(new)) continue
            val old = all[key(new)]
            if (old == null || new.lastObservedAtMs > old.lastObservedAtMs ||
                (new.observedTargetHit && !old.observedTargetHit)) {
                all[key(new)] = new
                changed++
            }
        }
        if (changed > 0) commit(all.values)
        changed
    }

    /** Accepts the OLD v8.0.x fixed_recommendations.json format; never invokes upsert(). */
    fun restorePriorRecommendations(json: String): Int = synchronized(lock) {
        if (json.length > 6_000_000) return@synchronized 0
        val rows = runCatching { gson.fromJson(json, Array<FixedRecommendationRecord>::class.java)?.toList() }
            .getOrNull().orEmpty().take(2500)
        capture(rows)
    }

    fun restoreHistoricalJson(json: String): Int = synchronized(lock) {
        if (json.length > 6_000_000) return@synchronized 0
        val candidates = runCatching {
            gson.fromJson(json, Array<FixedHistoricalEvidenceRow>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList()).take(2500)
        val all = list().associateBy(::key).toMutableMap()
        var changed = 0
        for (row in candidates.filter(::valid)) {
            val old = all[key(row)]
            if (old == null || row.lastObservedAtMs > old.lastObservedAtMs ||
                (row.observedTargetHit && !old.observedTargetHit)) {
                all[key(row)] = row
                changed++
            }
        }
        if (changed > 0) commit(all.values)
        changed
    }

    internal fun summary(): FixedHistoricalEvidenceSummary = synchronized(lock) {
        val all = list()
        FixedHistoricalEvidenceSummary(
            all.size, all.map { it.date }.distinct().size,
            all.count { it.observedTargetHit },
            all.count { it.snapshotOutcome == "UNRESOLVED_AT_SNAPSHOT" }
        )
    }
    fun archive(): String = synchronized(lock) { gson.toJson(list()) }
}
