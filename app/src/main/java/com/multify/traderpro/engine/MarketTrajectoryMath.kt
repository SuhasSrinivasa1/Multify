package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.sign

data class TrajectoryDecision(
    val regime: String,
    val composite: Double,
    val waveiness: Double,
    val preferredSide: String,
    val confidence: Double,
    val reason: String
)

/**
 * Event-neutral trajectory classifier used from wave 2 onward.
 * Missing market/sector inputs are neutral rather than guessed.
 * The live engine may later feed verified NIFTY/sector breadth into marketBias/sectorBias.
 */
object MarketTrajectoryMath {
    fun classify(
        features: FeatureSnapshot,
        longScore: Double,
        shortScore: Double,
        marketBias: Double? = null,
        sectorBias: Double? = null,
        symbolHistoricalBias: Double? = null
    ): TrajectoryDecision {
        val trend = (features.trendSlopeAtr ?: 0.0).coerceIn(-1.5, 1.5) / 1.5
        val vwap = features.vwap?.takeIf { it > 0.0 }?.let { ((features.ltp / it) - 1.0).coerceIn(-0.02, 0.02) / 0.02 } ?: 0.0
        val ema = when {
            features.ema9 != null && features.ema20 != null && features.ema20 > 0.0 -> ((features.ema9 / features.ema20) - 1.0).coerceIn(-0.015, 0.015) / 0.015
            else -> 0.0
        }
        val micro = (features.orderBookImbalance ?: 0.0).coerceIn(-1.0, 1.0)
        val day = ((features.dayChangePct ?: 0.0) / 4.0).coerceIn(-1.0, 1.0)
        val scoreGap = (longScore - shortScore).coerceIn(-1.0, 1.0)
        val context = (((marketBias ?: 0.0) + (sectorBias ?: 0.0)) / 2.0).coerceIn(-1.0, 1.0)
        val symbol = (symbolHistoricalBias ?: 0.0).coerceIn(-1.0, 1.0)
        val direction = if (trend == 0.0) scoreGap.sign else trend.sign
        val volume = (((features.rvol ?: 1.0) - 1.0) / 2.0).coerceIn(-0.5, 1.0) * direction

        // Price/trajectory dominates; fundamentals/history are context, not intraday timing triggers.
        val composite = (
            0.24 * trend +
            0.14 * vwap +
            0.10 * ema +
            0.13 * volume +
            0.11 * micro +
            0.10 * day +
            0.08 * scoreGap +
            0.05 * context +
            0.03 * symbol +
            0.03 * (features.vwapSlope ?: 0.0).coerceIn(-1.0, 1.0) +
            0.03 * (features.structureScore ?: 0.0).coerceIn(-1.0, 1.0)
        ).coerceIn(-1.0, 1.0)

        // High disagreement between short-term trend, VWAP, EMA and ensemble implies an oscillating/wave regime.
        val meanAbs = (abs(trend) + abs(vwap) + abs(ema) + abs(scoreGap)) / 4.0
        val agreement = abs(trend + vwap + ema + scoreGap) / 4.0
        val waveiness = (meanAbs - agreement + (if ((features.rsi14 ?: 50.0) in 42.0..58.0) 0.15 else 0.0)).coerceIn(0.0, 1.0)

        val atrPct = (features.atr14 ?: 0.0) / features.ltp.coerceAtLeast(1e-9)
        val breakout = features.donchianHigh?.let { features.ltp > it } == true && (features.rvol ?: 1.0) > 1.15
        val breakdown = features.donchianLow?.let { features.ltp < it } == true && (features.rvol ?: 1.0) > 1.15
        val reversal = (features.structureScore ?: 0.0) * composite < -0.08 && abs(features.structureScore ?: 0.0) > .45
        val regime = when {
            breakout -> "BREAKOUT"
            breakdown -> "BREAKDOWN"
            reversal -> "REVERSAL"
            atrPct > .025 -> "HIGH_VOLATILITY"
            atrPct < .004 && (features.rvol ?: 1.0) < .90 -> "LOW_VOLATILITY"
            waveiness >= 0.40 && abs(composite) < 0.42 -> "OSCILLATING"
            composite >= 0.24 -> "UP_TREND"
            composite <= -0.24 -> "DOWN_TREND"
            else -> "NEUTRAL"
        }
        val preferred = when {
            composite > 0.08 -> "LONG"
            composite < -0.08 -> "SHORT"
            else -> if (longScore >= shortScore) "LONG" else "SHORT"
        }
        val confidence = (0.50 + abs(composite) * 0.38 + waveiness * 0.08).coerceIn(0.50, 0.92)
        return TrajectoryDecision(
            regime = regime,
            composite = composite,
            waveiness = waveiness,
            preferredSide = preferred,
            confidence = confidence,
            reason = "trajectory=$regime composite=${fmt(composite)} waveiness=${fmt(waveiness)} trend=${fmt(trend)} vwap=${fmt(vwap)} ema=${fmt(ema)} volume=${fmt(volume)} micro=${fmt(micro)} day=${fmt(day)}"
        )
    }

    private fun fmt(x: Double) = "%.3f".format(java.util.Locale.US, x)
}
