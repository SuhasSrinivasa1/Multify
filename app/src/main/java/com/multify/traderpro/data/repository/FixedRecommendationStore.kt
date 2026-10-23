package com.multify.traderpro.data.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.multify.traderpro.data.local.LongForecastEntity
import com.multify.traderpro.data.network.ForecastDto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

internal data class FixedRecommendationRecord(
    val id: Long,
    val recommendationDate: String,
    val rank: Int,
    val symbol: String,
    val entryPrice: Double,
    val targetPct: Double,
    val targetPrice: Double,
    val confidence: Double,
    val score: Double,
    val strategy: String,
    val regime: String,
    val marketRegime: String,
    val championKey: String,
    val reason: String,
    val generatedAtMs: Long,
    val status: String = "ACTIVE",
    val targetHitAtMs: Long? = null,
    val lastPrice: Double,
    val maxFavourablePct: Double = 0.0,
    val maxAdversePct: Double = 0.0,
    val lastObservedAtMs: Long,
    val qualityTier: String = "PRIMARY",
    val executionEligible: Boolean = true
) {
    fun toDto(): ForecastDto = ForecastDto(
        rank = rank,
        symbol = symbol,
        confidence = confidence,
        score = score,
        reason = reason,
        entryPrice = entryPrice,
        targetPrice = targetPrice,
        targetPct = targetPct,
        status = status,
        strategy = strategy,
        regime = regime,
        marketRegime = marketRegime,
        championTag = championKey,
        maxFavourablePct = maxFavourablePct,
        maxAdversePct = maxAdversePct,
        generatedAtMs = generatedAtMs,
        qualityTier = qualityTier,
        executionEligible = executionEligible
    )
}

internal data class FixedScanObservation(
    val recommendationDate: String,
    val scanAtMs: Long,
    val symbol: String,
    val qualityTier: String,
    val publishEligible: Boolean,
    val executionEligible: Boolean
)

private data class FixedRecommendationState(
    val recommendations: List<FixedRecommendationRecord> = emptyList(),
    val observations: List<FixedScanObservation> = emptyList()
)

@Singleton
class FixedRecommendationStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fixed_recommendations_v75", Context.MODE_PRIVATE)
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    @Synchronized
    internal fun recommendations(date: String): List<FixedRecommendationRecord> =
        load().recommendations.filter { it.recommendationDate == date }.sortedBy { it.rank }

    @Synchronized
    internal fun observations(date: String): List<FixedScanObservation> =
        load().observations.filter { it.recommendationDate == date }.sortedBy { it.scanAtMs }

    @Synchronized
    internal fun seedFromAdaptiveIfEmpty(date: String, rows: List<LongForecastEntity>) {
        if (recommendations(date).isNotEmpty() || rows.isEmpty()) return
        val state = load()
        val seeded = rows.map { row ->
            FixedRecommendationRecord(
                id = fixedId(date, row.symbol),
                recommendationDate = date,
                rank = row.rank,
                symbol = row.symbol,
                entryPrice = row.entryPrice,
                targetPct = row.targetPct,
                targetPrice = row.targetPrice,
                confidence = row.confidence,
                score = row.score,
                strategy = row.strategy,
                regime = row.regime,
                marketRegime = row.marketRegime,
                championKey = row.championKey,
                reason = "Frozen v7.4.1 seed · ${row.reason}",
                generatedAtMs = row.generatedAtMs,
                status = row.status,
                targetHitAtMs = row.targetHitAtMs,
                lastPrice = row.lastPrice,
                maxFavourablePct = row.maxFavourablePct,
                maxAdversePct = row.maxAdversePct,
                lastObservedAtMs = row.lastObservedAtMs,
                qualityTier = row.qualityTier,
                executionEligible = row.executionEligible
            )
        }
        save(state.copy(recommendations = retainDays(state.recommendations + seeded)))
    }

    @Synchronized
    internal fun upsert(record: FixedRecommendationRecord) {
        val state = load()
        val rows = state.recommendations
            .filterNot { it.recommendationDate == record.recommendationDate && it.symbol.equals(record.symbol, true) }
            .toMutableList()
        rows += record
        save(state.copy(recommendations = retainDays(rows)))
    }

    @Synchronized
    internal fun appendObservations(rows: List<FixedScanObservation>) {
        if (rows.isEmpty()) return
        val state = load()
        val merged = (state.observations + rows)
            .distinctBy { "${it.recommendationDate}|${it.scanAtMs}|${it.symbol.uppercase(Locale.US)}" }
        save(state.copy(observations = retainObservationDays(merged)))
    }

    @Synchronized
    internal fun update(record: FixedRecommendationRecord) = upsert(record)

    private fun load(): FixedRecommendationState {
        val raw = prefs.getString(KEY_STATE, null) ?: return FixedRecommendationState()
        return runCatching { gson.fromJson(raw, FixedRecommendationState::class.java) }
            .getOrDefault(FixedRecommendationState())
    }

    private fun save(state: FixedRecommendationState) {
        prefs.edit().putString(KEY_STATE, gson.toJson(state)).commit()
    }

    private fun retainDays(rows: List<FixedRecommendationRecord>): List<FixedRecommendationRecord> {
        val dates = rows.map { it.recommendationDate }.distinct().sortedDescending().take(30).toSet()
        return rows.filter { it.recommendationDate in dates }
    }

    private fun retainObservationDays(rows: List<FixedScanObservation>): List<FixedScanObservation> {
        val dates = rows.map { it.recommendationDate }.distinct().sortedDescending().take(5).toSet()
        return rows.filter { it.recommendationDate in dates }
    }

    companion object {
        private const val KEY_STATE = "state"
        internal fun fixedId(date: String, symbol: String): Long {
            val x = "$date|${symbol.uppercase(Locale.US)}|FIXED-V741".hashCode().toLong()
            return if (x == Long.MIN_VALUE) 1L else kotlin.math.abs(x).coerceAtLeast(1L)
        }
    }
}
