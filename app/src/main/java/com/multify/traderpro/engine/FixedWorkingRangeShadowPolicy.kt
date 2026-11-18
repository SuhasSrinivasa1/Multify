package com.multify.traderpro.engine

/**
 * Read-only, low-priority evidence derived from an ALREADY-COMPLETED 15-minute
 * Fixed evaluation. This object never participates in candidate ranking,
 * publication, order placement, stop placement, or trailing decisions.
 *
 * The labels below are HYPOTHESES, not validated trading signals. In particular,
 * RSI/VWAP extension can occur on winners: do not turn any label into a veto.
 */
internal object FixedWorkingRangeShadowPolicy {
    internal data class Inputs(
        val price: Double,
        val atr: Double? = null,
        val vwap: Double? = null,
        val rsi: Double? = null,
        val rsiSlope: Double? = null,
        val rvol: Double? = null,
        val volumeAcceleration: Double? = null,
        val spreadBps: Double? = null,
        val orderBookImbalance: Double? = null,
        val ema9: Double? = null,
        val ema20: Double? = null,
        val ema50: Double? = null,
        val macdHistogramSlope: Double? = null,
        val lastHigh: Double? = null,
        val lastLow: Double? = null,
        val lastClose: Double? = null,
        val prevHigh: Double? = null,
        val prevLow: Double? = null,
        val prevClose: Double? = null,
        val distanceBelowDayHighPct: Double? = null,
        val targetPct: Double = FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT
    )

    fun observe(e: FixedStrategyEvaluation, evaluationAtMs: Long): Map<String, Any?> {
        val f = e.features
        return evaluate(
            Inputs(
                price = f.ltp, atr = f.atr14, vwap = f.vwap, rsi = f.rsi14,
                rsiSlope = f.rsiSlope, rvol = f.rvol,
                volumeAcceleration = f.volumeAcceleration, spreadBps = f.spreadBps,
                orderBookImbalance = f.orderBookImbalance, ema9 = f.ema9,
                ema20 = f.ema20, ema50 = f.ema50,
                macdHistogramSlope = f.macdHistogramSlope,
                lastHigh = f.lastHigh, lastLow = f.lastLow, lastClose = f.lastClose,
                prevHigh = f.prevHigh, prevLow = f.prevLow, prevClose = f.prevClose,
                distanceBelowDayHighPct = f.sessionHighDistancePct
            ), evaluationAtMs
        )
    }

    internal fun evaluate(input: Inputs, evaluationAtMs: Long): Map<String, Any?> {
        fun valid(v: Double?): Double? = v?.takeIf { it.isFinite() }
        val price = valid(input.price)?.takeIf { it > 0.0 }
        val atr = valid(input.atr)?.takeIf { it > 0.0 }
        val rsi = valid(input.rsi)
        val rsiSlope = valid(input.rsiSlope)
        val rvol = valid(input.rvol)
        val accel = valid(input.volumeAcceleration)
        val spread = valid(input.spreadBps)
        val book = valid(input.orderBookImbalance)
        val macdSlope = valid(input.macdHistogramSlope)
        val extension = if (price != null && atr != null && valid(input.vwap) != null)
            (price - input.vwap!!) / atr else null
        val range = if (atr != null && valid(input.lastHigh) != null &&
            valid(input.lastLow) != null)
            ((input.lastHigh!! - input.lastLow!!) / atr).takeIf { it >= 0.0 } else null
        val previousRange = if (atr != null && valid(input.prevHigh) != null &&
            valid(input.prevLow) != null)
            ((input.prevHigh!! - input.prevLow!!) / atr).takeIf { it >= 0.0 } else null
        val impulse = if (atr != null && valid(input.lastClose) != null &&
            valid(input.prevClose) != null)
            (input.lastClose!! - input.prevClose!!) / atr else null
        val rangeRatio = if (range != null && previousRange != null &&
            previousRange > 0.0) range / previousRange else null
        val targetAtr = if (price != null && atr != null && input.targetPct.isFinite()
            && input.targetPct > 0.0) price * input.targetPct / (100.0 * atr) else null
        val ema9 = valid(input.ema9)
        val ema20 = valid(input.ema20)
        val ema50 = valid(input.ema50)
        val aligned = if (ema9 != null && ema20 != null && ema50 != null)
            ema9 > ema20 && ema20 > ema50 else null

        // Null means "not observed" rather than a negative classification.
        val explosive = if (rsi != null && range != null)
            rsi >= 83.0 && range >= 5.0 else null
        val extended = extension?.let { it > 4.5 }
        val slowing = if (rsiSlope != null && accel != null && macdSlope != null)
            rsiSlope <= 0.0 && accel <= 0.0 && macdSlope <= 0.0 else null
        val stabilized = if (rsi != null && range != null && rvol != null &&
            rsiSlope != null && impulse != null)
            rsi in 55.0..75.0 && range <= 2.0 &&
                rvol in 1.0..5.0 && rsiSlope >= 0.0 && impulse >= 0.0 else null
        val liquid = if (spread != null && book != null)
            spread >= 0.0 && spread <= 40.0 && book >= -1.0 else null
        val state = when {
            explosive == true && slowing == true -> "SPIKE_AND_DECELERATION_HYPOTHESIS"
            stabilized == true -> "POST_SPIKE_STABILIZATION_CANDIDATE"
            extended == true && aligned == true -> "EXTENDED_TREND_CAN_STILL_WIN"
            rsi == null || range == null || price == null -> "INSUFFICIENT_DATA"
            else -> "OTHER_OR_MIXED"
        }
        val missing = linkedMapOf(
            "atr" to atr, "vwap_extension_atr" to extension,
            "rsi" to rsi, "rsi_slope" to rsiSlope, "rvol" to rvol,
            "volume_acceleration" to accel, "macd_histogram_slope" to macdSlope,
            "spread_bps" to spread, "book_imbalance" to book,
            "range_15m_atr" to range, "prior_range_15m_atr" to previousRange,
            "impulse_15m_atr" to impulse, "target_atr_units" to targetAtr
        ).filterValues { it == null }.keys.toList()
        return mapOf(
            "schema" to "fixed_working_range_shadow_v9017",
            "research_only" to true,
            "does_not_approve_or_reject_trades" to true,
            "working_range_qualified" to false,
            "no_accuracy_claim" to true,
            "no_extra_broker_calls" to true,
            "features_from_existing_completed_15m_evaluation" to true,
            "actual_five_minute_candles_available" to false,
            "evaluation_client_at_ms" to evaluationAtMs,
            "exchange_tick_timestamp_verified" to false,
            "label_hypothesis_not_a_trade_signal" to state,
            "candidate_range_parameters" to mapOf(
                "rsi" to rsi, "rsi_slope" to rsiSlope, "rvol" to rvol,
                "volume_acceleration" to accel, "spread_bps" to spread,
                "order_book_imbalance" to book,
                "vwap_extension_atr" to extension,
                "last_completed_15m_range_atr" to range,
                "previous_completed_15m_range_atr" to previousRange,
                "range_ratio" to rangeRatio, "last_15m_close_impulse_atr" to impulse,
                "macd_histogram_slope" to macdSlope,
                "ema9_gt_ema20_gt_ema50" to aligned,
                "day_high_distance_pct" to valid(input.distanceBelowDayHighPct),
                "original_target_requirement_atr" to targetAtr
            ),
            "shadow_hypotheses" to mapOf(
                "rsi_ge_83_and_completed_bar_range_ge_5_atr" to explosive,
                "vwap_extension_gt_4_5_atr" to extended,
                "momentum_and_volume_joint_deceleration" to slowing,
                "post_spike_stabilization" to stabilized,
                "observed_liquidity_book" to liquid
            ),
            "missing_inputs" to missing,
            "label_outcome_pending_equal_horizon" to true
        )
    }
}
