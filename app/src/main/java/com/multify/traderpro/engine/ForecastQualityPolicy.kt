package com.multify.traderpro.engine

import kotlin.math.max

data class ForecastQualityDecision(
    val eligible: Boolean,
    val volatilityCapacityPct: Double,
    val reason: String
)

object ForecastQualityPolicy {
    const val MIN_TARGET_PCT = 1.0
    const val MIN_CONFIDENCE = 0.86
    const val MIN_DIRECTIONAL_SCORE = 0.84
    const val MAX_SPREAD_BPS = 35.0
    const val MIN_RVOL = 1.05

    fun adaptiveTargetPct(multifyMfeAveragePct: Double, priorForecastMfePct: List<Double>): Double {
        val prior = priorForecastMfePct.filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val robustForecastMfe = if (prior.isEmpty()) 0.0 else max(
            AdaptiveLearningMath.median(prior, 0.0),
            AdaptiveLearningMath.trimmedMean(prior, 0.0)
        )
        val multify = multifyMfeAveragePct.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        return max(MIN_TARGET_PCT, max(multify, robustForecastMfe))
    }

    fun evaluate(evaluation: StrategyEvaluation, targetPct: Double): ForecastQualityDecision {
        val f = evaluation.features
        val atrPct = if (f.ltp > 0.0 && f.atr14 != null && f.atr14 > 0.0) f.atr14 / f.ltp * 100.0 else 0.0
        val capacityPct = atrPct * 3.5

        fun reject(reason: String) = ForecastQualityDecision(false, capacityPct, reason)

        if (evaluation.regime != "TREND") return reject("Only empirically strong TREND setups are admitted")
        if (evaluation.confidence < MIN_CONFIDENCE) return reject("Confidence below quality floor")
        if (evaluation.directionalScore < MIN_DIRECTIONAL_SCORE) return reject("Directional score below quality floor")
        if ((f.spreadBps ?: 0.0) > MAX_SPREAD_BPS) return reject("Spread too wide")
        if ((f.rvol ?: 0.0) < MIN_RVOL) return reject("Relative volume too weak")
        if (f.vwap == null || f.ltp <= f.vwap) return reject("Price is not above VWAP")
        if (f.ema9 == null || f.ema20 == null || f.ema9 <= f.ema20) return reject("EMA9 is not above EMA20")
        if ((f.ema9Slope ?: 0.0) <= 0.0) return reject("EMA9 slope is not positive")
        if ((f.trendSlopeAtr ?: 0.0) <= 0.0) return reject("Price structure slope is not positive")
        if ((f.structureScore ?: 0.0) < -0.15) return reject("Recent market structure is deteriorating")
        if ((f.dayChangePct ?: 0.0) < -0.25) return reject("Day momentum is negative")
        if (capacityPct + 1e-9 < targetPct) return reject("Observed volatility cannot support the target")

        return ForecastQualityDecision(true, capacityPct, "High-quality TREND continuation with enough volatility capacity")
    }

    fun rankingBonus(evaluation: StrategyEvaluation, capacityPct: Double, targetPct: Double): Double {
        val f = evaluation.features
        val capacityBonus = ((capacityPct - targetPct).coerceAtLeast(0.0) / 10.0).coerceAtMost(0.08)
        val rvolBonus = (((f.rvol ?: 1.0) - 1.0) * 0.04).coerceIn(0.0, 0.06)
        val structureBonus = (((f.structureScore ?: 0.0) + 0.2) * 0.04).coerceIn(0.0, 0.05)
        return capacityBonus + rvolBonus + structureBonus
    }
}
