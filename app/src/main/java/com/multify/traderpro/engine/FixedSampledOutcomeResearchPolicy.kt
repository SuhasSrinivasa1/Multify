package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Research-only: quotes are periodic last-traded-price samples, not candle highs,
 * executable fills, or proof of realised net gain. No inputs to production.
 */
internal data class FixedSampledRecommendation(
    val id: String,
    val symbol: String,
    val generatedAtMs: Long,
    val entryPrice: Double,
    val fullTargetPct: Double
)

internal data class FixedSampledQuote(val atMs: Long, val price: Double)

internal object FixedSampledOutcomeResearchPolicy {
    const val CONSERVATIVE_POST_SCAN_DELAY_MS = 90_000L
    private val horizons = listOf(15, 30, 60)
    private val india = ZoneId.of("Asia/Kolkata")

    fun report(
        recommendations: List<FixedSampledRecommendation>,
        pricesByEvent: Map<String, List<FixedSampledQuote>>,
        lastCompleteSweepAtMs: Long
    ): Map<String, Any?> {
        val windows = horizons.map { minutes ->
            val horizonMs = minutes * 60_000L
            val matured = recommendations.filter {
                lastCompleteSweepAtMs >= it.generatedAtMs + horizonMs
            }
            val observed = matured.mapNotNull { row ->
                if (row.entryPrice <= 0.0 || !row.entryPrice.isFinite()) return@mapNotNull null
                val samples = pricesByEvent[row.id].orEmpty().filter {
                    it.atMs >= row.generatedAtMs + CONSERVATIVE_POST_SCAN_DELAY_MS &&
                        it.atMs <= row.generatedAtMs + horizonMs &&
                        it.price.isFinite() && it.price > 0.0
                }
                if (samples.isEmpty()) null else {
                    val maxGain = samples.maxOf { (it.price / row.entryPrice - 1.0) * 100.0 }
                    val time = Instant.ofEpochMilli(row.generatedAtMs).atZone(india).toLocalTime()
                    val group = when {
                        time >= LocalTime.of(9,15) && time < LocalTime.of(9,30) -> "OPENING_0915_0930"
                        time < LocalTime.of(11,0) -> "MORNING_AFTER_0930"
                        time < LocalTime.of(13,0) -> "MID_SESSION"
                        else -> "AFTER_1300"
                    }
                    Triple(row, maxGain, group)
                }
            }
            fun breakdown(rows: List<Triple<FixedSampledRecommendation, Double, String>>): Map<String, Any?> {
                val n = rows.size
                fun count(threshold: (Triple<FixedSampledRecommendation, Double, String>) -> Boolean): Int =
                    rows.count(threshold)
                val half = count { it.second + 1e-9 >= 0.5 }
                val arm = count { it.second + 1e-9 >= 0.55 }
                val one = count { it.second + 1e-9 >= 1.0 }
                val full = count { it.second + 1e-9 >= it.first.fullTargetPct }
                fun pct(hits: Int): Double? = if (n == 0) null else hits * 100.0 / n
                return mapOf(
                    "sampled_events" to n,
                    "at_least_0_50_pct" to half,
                    "at_least_0_55_pct" to arm,
                    "at_least_1_00_pct" to one,
                    "full_target_hits" to full,
                    "at_least_0_50_rate_pct" to pct(half),
                    "at_least_0_55_rate_pct" to pct(arm),
                    "at_least_1_00_rate_pct" to pct(one),
                    "full_target_rate_pct" to pct(full)
                )
            }
            mapOf(
                "horizon_minutes" to minutes,
                "matured_events" to matured.size,
                "matured_with_no_valid_sample" to (matured.size - observed.size),
                "immature_events_excluded" to (recommendations.size - matured.size),
                "all" to breakdown(observed),
                "time_of_day" to observed.groupBy { it.third }
                    .mapValues { (_, rows) -> breakdown(rows) }
            )
        }
        return mapOf(
            "schema" to "fixed_sampled_maturity_v1",
            "source" to "COMPLETED_FULL_MARKET_LTP_SWEEPS_ONLY",
            "realized_trade_profit" to false,
            "intrabar_highs_available" to false,
            "entry_timestamp_is_scan_start_not_broker_fill" to true,
            "conservative_first_postscan_sample_delay_ms" to CONSERVATIVE_POST_SCAN_DELAY_MS,
            "clock_ist" to "Asia/Kolkata",
            "last_complete_sweep_at_ms" to lastCompleteSweepAtMs,
            "candidate_event_count" to recommendations.size,
            "horizons" to windows
        )
    }
}
