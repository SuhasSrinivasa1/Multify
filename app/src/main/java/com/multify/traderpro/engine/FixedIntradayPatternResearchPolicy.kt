package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Descriptive research flags only: never referenced by production Fixed quality,
 * market candidate ranking, publication, auto BUY, SELL, GTT or loss management.
 * Every threshold is a hypothesis from the 2026-11-11/12 historical audit,
 * NOT an established predictive rule or trade instruction.
 */
internal object FixedIntradayPatternResearchPolicy {
    private val india = ZoneId.of("Asia/Kolkata")

    fun analyse(e: FixedStrategyEvaluation, model: FixedMultifyFactorModel,
                capturedAtMs: Long): Map<String, Any?> {
        val f = e.features
        val atr = f.atr14?.takeIf { it.isFinite() && it > 0.0 }
        val vwapAtr = if (atr != null && f.vwap != null)
            (f.ltp - f.vwap) / atr else null
        val rsi = f.rsi14?.takeIf { it.isFinite() }
        val rsiSlope = f.rsiSlope?.takeIf { it.isFinite() }
        val green = f.greenVolumeShare?.takeIf { it.isFinite() }
        val volume = f.rvol?.takeIf { it.isFinite() }
        val imbalance = f.orderBookImbalance?.takeIf { it.isFinite() }
        val spread = f.spreadBps?.takeIf { it.isFinite() }
        val nearHigh = f.sessionHighDistancePct?.takeIf { it.isFinite() }
        val local = Instant.ofEpochMilli(capturedAtMs).atZone(india).toLocalTime()
        val timeGroup = when {
            local >= LocalTime.of(9, 15) && local < LocalTime.of(9, 30) -> "OPENING_0915_0930"
            local < LocalTime.of(11, 0) -> "MORNING_AFTER_0930"
            local < LocalTime.of(13, 0) -> "MID_SESSION"
            local < LocalTime.of(14, 30) -> "EARLY_AFTERNOON"
            local < LocalTime.of(15, 0) -> "LATE_AFTERNOON"
            else -> "LAST_30_MINUTES_OR_OUTSIDE"
        }
        val highRvol = volume != null && volume >= 3.0
        val strongGreen = green != null && green >= 0.80
        val positiveBook = imbalance != null && imbalance >= 0.0 &&
            spread != null && spread in 0.0..40.0
        val extended = vwapAtr != null && vwapAtr >= 3.0
        val exhaustion = rsi != null && rsi >= 75.0 &&
            rsiSlope != null && rsiSlope <= 0.0
        val reversalContext = extended && exhaustion &&
            nearHigh != null && nearHigh <= 0.10
        val scoreByFamily = model.families.associate { it.name to it.score }
        return mapOf(
            "schema" to "fixed_pattern_hypotheses_v8322",
            "research_only" to true,
            "data_source" to "SAME_FROZEN_15_MIN_EVALUATION_NO_NEW_QUOTES",
            "missing_values_are_not_false_market_signals" to true,
            "exchange_quote_age_verified" to false,
            "time_of_day_ist" to timeGroup,
            "opening_completed_15m_current_session_bar" to
                (f.sessionCandleCount > 0),
            "green_volume_share" to green,
            "green_share_ge_80pct_hypothesis" to (green?.let { strongGreen }),
            "relative_volume" to volume,
            "rvol_ge_3_hypothesis" to (volume?.let { highRvol }),
            "order_book_imbalance" to imbalance,
            "spread_bps" to spread,
            "positive_book_with_spread_le_40bps_hypothesis" to
                (if (imbalance != null && spread != null) positiveBook else null),
            "green_rvol_depth_trio_hypothesis" to
                (if (green != null && volume != null && imbalance != null &&
                    spread != null) strongGreen && highRvol && positiveBook else null),
            "vwap_distance_atr" to vwapAtr,
            "under_three_atr_from_vwap_hypothesis" to (vwapAtr?.let { it < 3.0 }),
            "rsi14" to rsi,
            "rsi_slope" to rsiSlope,
            "rsi_exhaustion_hypothesis" to
                (if (rsi != null && rsiSlope != null) exhaustion else null),
            "session_high_distance_pct_completed_bars" to nearHigh,
            "peak_extension_and_deceleration_hypothesis" to
                (if (vwapAtr != null && rsi != null && rsiSlope != null &&
                    nearHigh != null) reversalContext else null),
            "factor_family_reference_scores_not_live_breadth" to scoreByFamily,
            "factor_availability_by_family" to model.families.associate {
                it.name to mapOf("available" to it.availableFactors, "total" to it.totalFactors)
            },
            "observed_raw_factor_average_reference" to
                model.factors.map { it.score }.average(),
            "positive_factor_count" to model.factors.count { it.score > 0.05 },
            "negative_factor_count" to model.factors.count { it.score < -0.05 },
            "original_strategy_confidence" to e.confidence,
            "original_strategy_directional_score" to e.directionalScore,
            "cannot_infer_90_percent_accuracy_from_this_row" to true,
            "trading_decisions_changed" to false
        )
    }
}
