package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class MultifyFamilyDecision(
    val family: String,
    val scores: Map<String, Double>,
    val pointers: Map<String, Double?>
)

object MultifyFamilyPolicy {
    const val FAMILY_MOMENTUM = "MOMENTUM_CONTINUATION"
    const val FAMILY_BREAKOUT = "BREAKOUT_EXPANSION"
    const val FAMILY_PULLBACK = "PULLBACK_RECLAIM"

    val families = listOf(FAMILY_MOMENTUM, FAMILY_BREAKOUT, FAMILY_PULLBACK)

    // Exactly 40 pre-call observable pointers. These are normalized/derived where possible so
    // they remain comparable across stocks with very different prices.
    val pointerNames = listOf(
        "ltp",
        "spread_bps",
        "order_book_imbalance",
        "day_change_pct",
        "market_cap",
        "range52_position",
        "vwap_distance_pct",
        "ema9_distance_pct",
        "ema20_distance_pct",
        "ema50_distance_pct",
        "ema9_slope_atr",
        "ema20_slope_atr",
        "vwap_slope_atr",
        "macd_histogram_slope",
        "rsi_slope",
        "volume_acceleration",
        "green_volume_share",
        "structure_score",
        "session_high_distance_pct",
        "session_low_distance_pct",
        "atr14",
        "atr_pct",
        "rsi14",
        "boll_width",
        "macd_histogram",
        "trend_slope_atr",
        "rvol",
        "donchian_high_distance_pct",
        "donchian_low_distance_pct",
        "orb5_high_distance_pct",
        "orb5_low_distance_pct",
        "orb15_high_distance_pct",
        "orb15_low_distance_pct",
        "last_body_pct",
        "last_upper_wick_pct",
        "last_lower_wick_pct",
        "previous_body_pct",
        "gap_vs_prev_close_pct",
        "last_close_vs_prev_high_pct",
        "last_close_vs_prev_low_pct"
    )

    init {
        require(pointerNames.size == 40)
        require(pointerNames.distinct().size == 40)
        require(families.size == 3)
    }

    fun classify(f: FeatureSnapshot, marketRegime: String): MultifyFamilyDecision {
        val p = featureVector(f)
        val momentum = momentumScore(f, p, marketRegime)
        val breakout = breakoutScore(f, p, marketRegime)
        val pullback = pullbackScore(f, p, marketRegime)
        val scores = linkedMapOf(
            FAMILY_MOMENTUM to momentum,
            FAMILY_BREAKOUT to breakout,
            FAMILY_PULLBACK to pullback
        )
        val family = scores.maxByOrNull { it.value }?.key ?: FAMILY_MOMENTUM
        return MultifyFamilyDecision(family, scores, p)
    }

    fun featureVector(f: FeatureSnapshot): Map<String, Double?> {
        fun dist(value: Double?): Double? = value?.takeIf { it > 0.0 && f.ltp > 0.0 }?.let { (f.ltp / it - 1.0) * 100.0 }
        fun body(open: Double?, close: Double?): Double? =
            if (open != null && close != null && open > 0.0) (close / open - 1.0) * 100.0 else null
        fun upper(open: Double?, high: Double?, close: Double?): Double? {
            if (open == null || high == null || close == null || open <= 0.0) return null
            return (high - max(open, close)) / open * 100.0
        }
        fun lower(open: Double?, low: Double?, close: Double?): Double? {
            if (open == null || low == null || close == null || open <= 0.0) return null
            return (min(open, close) - low) / open * 100.0
        }
        fun vs(base: Double?, value: Double?): Double? =
            if (base != null && value != null && base > 0.0) (value / base - 1.0) * 100.0 else null

        return linkedMapOf(
            "ltp" to f.ltp,
            "spread_bps" to f.spreadBps,
            "order_book_imbalance" to f.orderBookImbalance,
            "day_change_pct" to f.dayChangePct,
            "market_cap" to f.marketCap,
            "range52_position" to f.range52Position,
            "vwap_distance_pct" to dist(f.vwap),
            "ema9_distance_pct" to dist(f.ema9),
            "ema20_distance_pct" to dist(f.ema20),
            "ema50_distance_pct" to dist(f.ema50),
            "ema9_slope_atr" to f.ema9Slope,
            "ema20_slope_atr" to f.ema20Slope,
            "vwap_slope_atr" to f.vwapSlope,
            "macd_histogram_slope" to f.macdHistogramSlope,
            "rsi_slope" to f.rsiSlope,
            "volume_acceleration" to f.volumeAcceleration,
            "green_volume_share" to f.greenVolumeShare,
            "structure_score" to f.structureScore,
            "session_high_distance_pct" to f.sessionHighDistancePct,
            "session_low_distance_pct" to f.sessionLowDistancePct,
            "atr14" to f.atr14,
            "atr_pct" to f.atr14?.let { if (f.ltp > 0.0) it / f.ltp * 100.0 else null },
            "rsi14" to f.rsi14,
            "boll_width" to f.bollWidth,
            "macd_histogram" to f.macdHistogram,
            "trend_slope_atr" to f.trendSlopeAtr,
            "rvol" to f.rvol,
            "donchian_high_distance_pct" to dist(f.donchianHigh),
            "donchian_low_distance_pct" to dist(f.donchianLow),
            "orb5_high_distance_pct" to dist(f.orb5High),
            "orb5_low_distance_pct" to dist(f.orb5Low),
            "orb15_high_distance_pct" to dist(f.orb15High),
            "orb15_low_distance_pct" to dist(f.orb15Low),
            "last_body_pct" to body(f.lastOpen, f.lastClose),
            "last_upper_wick_pct" to upper(f.lastOpen, f.lastHigh, f.lastClose),
            "last_lower_wick_pct" to lower(f.lastOpen, f.lastLow, f.lastClose),
            "previous_body_pct" to body(f.prevOpen, f.prevClose),
            "gap_vs_prev_close_pct" to vs(f.prevClose, f.lastOpen),
            "last_close_vs_prev_high_pct" to vs(f.prevHigh, f.lastClose),
            "last_close_vs_prev_low_pct" to vs(f.prevLow, f.lastClose)
        )
    }

    private fun momentumScore(f: FeatureSnapshot, p: Map<String, Double?>, marketRegime: String): Double {
        var score = 0.0
        score += positive(p["vwap_distance_pct"], 0.0, 0.8) * 0.12
        score += positive(p["ema9_distance_pct"], 0.0, 0.8) * 0.08
        score += positive(p["ema20_distance_pct"], 0.0, 1.5) * 0.08
        score += positive(f.ema9Slope, 0.0, 0.25) * 0.09
        score += positive(f.ema20Slope, -0.02, 0.18) * 0.07
        score += positive(f.vwapSlope, -0.02, 0.20) * 0.06
        score += positive(f.trendSlopeAtr, 0.0, 0.30) * 0.10
        score += positive(f.rvol, 0.8, 1.8) * 0.08
        score += positive(f.greenVolumeShare, 0.50, 0.75) * 0.07
        score += positive(f.structureScore, -0.10, 0.80) * 0.08
        score += positive(f.dayChangePct, -0.20, 2.0) * 0.07
        score += centered(f.rsi14, 62.0, 18.0) * 0.05
        score += if (marketRegime == "BREADTH_BULL") 0.05 else 0.0
        return score.coerceIn(0.0, 1.0)
    }

    private fun breakoutScore(f: FeatureSnapshot, p: Map<String, Double?>, marketRegime: String): Double {
        var score = 0.0
        score += nearOrAbove(p["session_high_distance_pct"], 0.35) * 0.12
        score += nearOrAbove(p["donchian_high_distance_pct"], 0.30) * 0.12
        score += nearOrAbove(p["orb5_high_distance_pct"], 0.30) * 0.10
        score += nearOrAbove(p["orb15_high_distance_pct"], 0.35) * 0.08
        score += positive(f.rvol, 1.0, 2.2) * 0.12
        score += positive(f.volumeAcceleration, 0.0, 1.0) * 0.10
        score += positive(p["atr_pct"], 0.25, 1.5) * 0.08
        score += positive(f.bollWidth, 0.008, 0.05) * 0.06
        score += positive(f.orderBookImbalance, -0.05, 0.45) * 0.07
        score += positive(f.dayChangePct, 0.0, 2.5) * 0.07
        score += positive(f.range52Position, 0.45, 0.95) * 0.04
        score += if (marketRegime == "BREADTH_BULL") 0.04 else 0.0
        return score.coerceIn(0.0, 1.0)
    }

    private fun pullbackScore(f: FeatureSnapshot, p: Map<String, Double?>, marketRegime: String): Double {
        var score = 0.0
        score += centered(p["vwap_distance_pct"], 0.20, 0.60) * 0.12
        score += centered(p["ema9_distance_pct"], 0.15, 0.55) * 0.10
        score += positive(p["ema20_distance_pct"], -0.20, 1.0) * 0.09
        score += positive(p["ema50_distance_pct"], -0.20, 2.0) * 0.07
        score += positive(f.ema9Slope, -0.03, 0.20) * 0.08
        score += positive(f.ema20Slope, -0.03, 0.16) * 0.07
        score += centered(f.rsi14, 55.0, 14.0) * 0.08
        score += positive(f.structureScore, -0.20, 0.70) * 0.09
        score += positive(p["last_lower_wick_pct"], 0.0, 0.60) * 0.07
        score += positive(p["last_close_vs_prev_low_pct"], 0.0, 1.2) * 0.05
        score += positive(f.greenVolumeShare, 0.45, 0.70) * 0.06
        score += positive(f.rvol, 0.65, 1.5) * 0.06
        score += if (marketRegime == "BREADTH_NEUTRAL") 0.04 else 0.02
        return score.coerceIn(0.0, 1.0)
    }

    private fun positive(v: Double?, low: Double, high: Double): Double {
        if (v == null || !v.isFinite() || high <= low) return 0.0
        return ((v - low) / (high - low)).coerceIn(0.0, 1.0)
    }

    private fun centered(v: Double?, center: Double, halfWidth: Double): Double {
        if (v == null || !v.isFinite() || halfWidth <= 0.0) return 0.0
        return (1.0 - abs(v - center) / halfWidth).coerceIn(0.0, 1.0)
    }

    private fun nearOrAbove(distancePct: Double?, tolerance: Double): Double {
        if (distancePct == null || !distancePct.isFinite()) return 0.0
        // Derived distance is positive above the reference and negative below it.
        return when {
            distancePct >= 0.0 -> 1.0
            distancePct >= -tolerance -> 1.0 - abs(distancePct) / tolerance
            else -> 0.0
        }
    }
}
