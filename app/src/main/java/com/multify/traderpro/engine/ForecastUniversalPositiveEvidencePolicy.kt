package com.multify.traderpro.engine

import java.time.Instant
import java.time.ZoneId
import kotlin.math.ln
import kotlin.math.min

data class ForecastPositiveEvidenceCase(
    val source: String,
    val evidenceDate: String,
    val symbol: String,
    val strategy: String,
    val regime: String,
    val marketRegime: String,
    val sectorRegime: String,
    val capBucket: String,
    val timeBucket: String,
    val dominantFamily: String,
    val realizedPct: Double?,
    val closePct: Double?,
    val maxFavourablePct: Double,
    val protectedTrailPct: Double?
)

/**
 * Positive-only adaptive evidence shared by Forecast learning.
 *
 * Fixed never calls this policy. A target miss can still be positive evidence when the common
 * stepped trailing architecture would have armed/protected a profit, or an actual trade closed
 * positive. Negative/flat outcomes remain diagnostic and never create a negative ranking weight.
 */
object ForecastUniversalPositiveEvidencePolicy {
    private val INDIA = ZoneId.of("Asia/Kolkata")
    const val MAX_RANKING_BONUS = 0.10
    const val MIN_EXACT_CONTEXT_SAMPLES = 5
    const val MIN_BROAD_CONTEXT_SAMPLES = 8
    const val MIN_DISTINCT_DAYS = 3
    const val MIN_DISTINCT_SYMBOLS = 3

    fun protectedTrailPct(maxFavourablePct: Double): Double? {
        if (!maxFavourablePct.isFinite() || maxFavourablePct < 0.0) return null
        val entry = 100.0
        val high = entry * (1.0 + maxFavourablePct / 100.0)
        return SteppedTrailingPolicy.stopGainPctForHighWater(entry, high)
    }

    fun qualifies(
        realizedPct: Double?,
        closePct: Double?,
        maxFavourablePct: Double,
        targetHit: Boolean = false
    ): Boolean {
        if (realizedPct?.let { it.isFinite() && it > 0.0 } == true) return true
        if (closePct?.let { it.isFinite() && it > 0.0 } == true) return true
        if (targetHit) return true
        return protectedTrailPct(maxFavourablePct)?.let { it > 0.0 } == true
    }

    fun positiveReason(
        realizedPct: Double?,
        closePct: Double?,
        maxFavourablePct: Double,
        targetHit: Boolean = false
    ): String = when {
        realizedPct?.let { it.isFinite() && it > 0.0 } == true -> "REALIZED_POSITIVE"
        targetHit -> "TARGET_HIT"
        protectedTrailPct(maxFavourablePct)?.let { it > 0.0 } == true -> "TRAIL_PROTECTED_POSITIVE"
        closePct?.let { it.isFinite() && it > 0.0 } == true -> "CLOSE_POSITIVE"
        else -> "DIAGNOSTIC_ONLY"
    }

    fun timeBucket(atMs: Long): String {
        val t = Instant.ofEpochMilli(atMs).atZone(INDIA).toLocalTime()
        return when {
            t.hour < 10 || (t.hour == 10 && t.minute < 15) -> "OPENING"
            t.hour < 12 -> "MORNING"
            t.hour < 14 -> "MIDDAY"
            else -> "CLOSING"
        }
    }

    /**
     * This is deliberately a proxy cohort, not an exchange/legal market-cap classification.
     * It uses the same log-scale market-cap signal already present in the 40-factor model.
     */
    fun capBucket(marketCap: Double?): String {
        val cap = marketCap?.takeIf { it.isFinite() && it > 0.0 } ?: return "CAP_UNKNOWN"
        val normalized = ((ln(cap) - 15.0) / 6.0).coerceIn(-1.0, 1.0)
        return when {
            normalized >= 0.35 -> "LARGE_PROXY"
            normalized >= -0.25 -> "MID_PROXY"
            else -> "SMALL_PROXY"
        }
    }

    fun rankingBonus(
        symbol: String,
        strategy: String,
        regime: String,
        marketRegime: String,
        sectorRegime: String,
        capBucket: String,
        timeBucket: String,
        dominantFamily: String,
        evidence: List<ForecastPositiveEvidenceCase>
    ): Double {
        if (evidence.isEmpty()) return 0.0

        fun mature(rows: List<ForecastPositiveEvidenceCase>, minSamples: Int): Boolean =
            rows.size >= minSamples &&
                rows.map { it.evidenceDate }.distinct().size >= MIN_DISTINCT_DAYS &&
                rows.map { it.symbol }.distinct().size >= MIN_DISTINCT_SYMBOLS

        val exact = evidence.filter {
            it.strategy == strategy && it.regime == regime && it.marketRegime == marketRegime &&
                it.capBucket == capBucket && it.timeBucket == timeBucket &&
                it.dominantFamily == dominantFamily &&
                (sectorRegime == "UNAVAILABLE" || it.sectorRegime == sectorRegime)
        }
        val contextual = evidence.filter {
            it.strategy == strategy && it.regime == regime && it.marketRegime == marketRegime &&
                it.dominantFamily == dominantFamily
        }
        val broad = evidence.filter {
            it.regime == regime && it.marketRegime == marketRegime
        }

        val rows = when {
            mature(exact, MIN_EXACT_CONTEXT_SAMPLES) -> exact
            mature(contextual, MIN_BROAD_CONTEXT_SAMPLES) -> contextual
            mature(broad, MIN_BROAD_CONTEXT_SAMPLES) -> broad
            else -> return 0.0
        }

        fun sourceWeight(source: String): Double = when (source.uppercase()) {
            "FIXED" -> 1.0
            "MULTIFY" -> 0.78
            "FORECAST" -> 0.58
            else -> 0.50
        }

        val weightedStrength = rows.map { row ->
            val protected = row.protectedTrailPct ?: 0.0
            val realized = row.realizedPct?.coerceAtLeast(0.0) ?: 0.0
            val close = row.closePct?.coerceAtLeast(0.0) ?: 0.0
            sourceWeight(row.source) * maxOf(protected, realized, close, row.maxFavourablePct * 0.35)
        }.average()

        var bonus = min(0.045, rows.size * 0.0025)
        bonus += min(0.035, weightedStrength * 0.012)
        if (rows.any { it.symbol.equals(symbol, true) }) bonus += 0.01
        return bonus.coerceIn(0.0, MAX_RANKING_BONUS)
    }
}
