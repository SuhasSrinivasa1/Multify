package com.multify.traderpro.engine

import kotlin.math.max

data class ForecastQualityDecision(
    val tier: String,
    val publishEligible: Boolean,
    val executionEligible: Boolean,
    val volatilityCapacityPct: Double,
    val reason: String,
    val failedRules: List<String> = emptyList()
) {
    val eligible: Boolean get() = publishEligible
}

object ForecastQualityPolicy {
    const val MIN_TARGET_PCT = 1.0

    const val PRIMARY_MIN_CONFIDENCE = 0.63
    const val PRIMARY_MIN_DIRECTIONAL_SCORE = 0.28
    const val PRIMARY_MAX_SPREAD_BPS = 40.0
    const val PRIMARY_MIN_RVOL = 0.85

    const val SECONDARY_MIN_CONFIDENCE = 0.58
    const val SECONDARY_MIN_DIRECTIONAL_SCORE = 0.15
    const val SECONDARY_MAX_SPREAD_BPS = 45.0
    const val SECONDARY_MIN_RVOL = 0.70

    fun adaptiveTargetPct(multifyMfeAveragePct: Double, priorForecastMfePct: List<Double>): Double {
        val prior = priorForecastMfePct.filter { it.isFinite() && it >= 0.0 && it < 25.0 }
        val robustForecastMfe = if (prior.isEmpty()) 0.0 else max(
            AdaptiveLearningMath.median(prior, 0.0),
            AdaptiveLearningMath.trimmedMean(prior, 0.0)
        )
        val multify = multifyMfeAveragePct.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
        return max(MIN_TARGET_PCT, max(multify, robustForecastMfe))
    }

    fun evaluate(
        evaluation: StrategyEvaluation,
        targetPct: Double,
        marketRegime: String = "BREADTH_NEUTRAL"
    ): ForecastQualityDecision {
        val f = evaluation.features
        val atrPct = if (f.ltp > 0.0 && f.atr14 != null && f.atr14 > 0.0) f.atr14 / f.ltp * 100.0 else 0.0
        val capacityPct = atrPct * 4.0
        val aboveVwap = f.vwap?.let { f.ltp > it } == true
        val emaAligned = f.ema9 != null && f.ema20 != null && f.ema9 > f.ema20
        val emaSlopePositive = (f.ema9Slope ?: 0.0) > -0.02
        val trendSlopePositive = (f.trendSlopeAtr ?: 0.0) > 0.0
        val structureHealthy = (f.structureScore ?: 0.0) >= -0.20
        val dayHealthy = (f.dayChangePct ?: 0.0) >= -0.50
        val momentumVotes = listOf(aboveVwap, emaAligned, emaSlopePositive, trendSlopePositive, structureHealthy).count { it }

        val primaryFails = mutableListOf<String>()
        if (marketRegime == "BREADTH_WEAK") primaryFails += "weak market breadth"
        if (evaluation.regime != "TREND") primaryFails += "not TREND"
        if (evaluation.confidence < PRIMARY_MIN_CONFIDENCE) primaryFails += "confidence below PRIMARY floor"
        if (evaluation.directionalScore < PRIMARY_MIN_DIRECTIONAL_SCORE) primaryFails += "directional score below PRIMARY floor"
        if ((f.spreadBps ?: 0.0) > PRIMARY_MAX_SPREAD_BPS) primaryFails += "spread too wide for PRIMARY"
        if ((f.rvol ?: 0.0) < PRIMARY_MIN_RVOL) primaryFails += "RVOL below PRIMARY floor"
        if (!aboveVwap) primaryFails += "below VWAP"
        if (!emaAligned) primaryFails += "EMA9 <= EMA20"
        if (!emaSlopePositive) primaryFails += "EMA9 slope negative"
        if (!trendSlopePositive) primaryFails += "trend slope non-positive"
        if (!structureHealthy) primaryFails += "structure weak"
        if (!dayHealthy) primaryFails += "day momentum weak"
        if (capacityPct + 1e-9 < targetPct * 0.75) primaryFails += "volatility capacity below 75% of target"

        if (primaryFails.isEmpty()) {
            return ForecastQualityDecision(
                tier = "PRIMARY",
                publishEligible = true,
                executionEligible = true,
                volatilityCapacityPct = capacityPct,
                reason = "Production TREND setup: momentum alignment, liquidity and volatility capacity confirmed"
            )
        }

        val secondaryFails = mutableListOf<String>()
        if (evaluation.regime !in setOf("TREND", "MIXED")) secondaryFails += "regime not TREND/MIXED"
        if (evaluation.confidence < SECONDARY_MIN_CONFIDENCE) secondaryFails += "confidence below SECONDARY floor"
        if (evaluation.directionalScore < SECONDARY_MIN_DIRECTIONAL_SCORE) secondaryFails += "directional score below SECONDARY floor"
        if ((f.spreadBps ?: 0.0) > SECONDARY_MAX_SPREAD_BPS) secondaryFails += "spread too wide for SECONDARY"
        if ((f.rvol ?: 0.0) < SECONDARY_MIN_RVOL) secondaryFails += "RVOL below SECONDARY floor"
        if (momentumVotes < 2) secondaryFails += "fewer than 2/5 momentum confirmations"
        if (!dayHealthy) secondaryFails += "day momentum weak"
        if (capacityPct + 1e-9 < targetPct * 0.55) secondaryFails += "volatility capacity below 55% of target"

        if (secondaryFails.isEmpty()) {
            return ForecastQualityDecision(
                tier = "SECONDARY",
                publishEligible = true,
                executionEligible = false,
                volatilityCapacityPct = capacityPct,
                reason = "Analyst watchlist: positive structure but not strong enough for production forecast execution",
                failedRules = primaryFails
            )
        }

        return ForecastQualityDecision(
            tier = "RESEARCH",
            publishEligible = false,
            executionEligible = false,
            volatilityCapacityPct = capacityPct,
            reason = secondaryFails.joinToString("; "),
            failedRules = primaryFails + secondaryFails
        )
    }

    fun rankingBonus(
        evaluation: StrategyEvaluation,
        capacityPct: Double,
        targetPct: Double,
        tier: String = "RESEARCH"
    ): Double {
        val f = evaluation.features
        val capacityBonus = ((capacityPct - targetPct).coerceAtLeast(0.0) / 8.0).coerceAtMost(0.10)
        val rvolBonus = (((f.rvol ?: 1.0) - 1.0) * 0.05).coerceIn(0.0, 0.07)
        val structureBonus = (((f.structureScore ?: 0.0) + 0.2) * 0.05).coerceIn(0.0, 0.06)
        val tierBonus = when (tier) {
            "PRIMARY" -> 0.16
            "SECONDARY" -> 0.07
            else -> 0.0
        }
        return capacityBonus + rvolBonus + structureBonus + tierBonus
    }
}
