package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class ForecastHistoricalOutcome(
    val date: String,
    val symbol: String,
    val strategy: String,
    val regime: String,
    val marketRegime: String,
    val maxFavourablePct: Double,
    val maxAdversePct: Double,
    val closePct: Double,
    val targetHit: Boolean
)

data class ForecastEvidenceDiagnostics(
    val sampleCount: Int = 0,
    val distinctDays: Int = 0,
    val distinctSymbols: Int = 0,
    val positiveCount: Int = 0,
    val flatCount: Int = 0,
    val adverseCount: Int = 0,
    val targetHitRatePct: Double = 0.0,
    val positiveRatePct: Double = 0.0,
    val medianMfePct: Double = 0.0,
    val medianMaePct: Double = 0.0,
    val medianClosePct: Double = 0.0,
    val consistency: Double = 0.0,
    val confidence: Double = 0.0,
    val validationSamples: Int = 0,
    val validationTargetHitRatePct: Double = 0.0,
    val shadowPenalty: Double = 0.0
)

data class ForecastEconomicsDecision(
    val quantity: Int,
    val assumedEntryPrice: Double,
    val assumedExitPrice: Double,
    val estimatedChargesRupees: Double,
    val estimatedSlippageRupees: Double,
    val grossProfitRupees: Double,
    val netProfitRupees: Double,
    val netReturnPct: Double,
    val downsideProxyPct: Double,
    val riskAdjustedScore: Double,
    val profitableAfterCosts: Boolean
)

data class ForecastFamilyOutcome(
    val date: String,
    val symbol: String,
    val marketRegime: String,
    val momentumScore: Double,
    val participationScore: Double,
    val structureScore: Double,
    val positive: Boolean
)

data class ForecastShadowFilterReview(
    val passes: Boolean,
    val failedRules: List<String>
)

/**
 * Adaptive-only Forecast policy. Fixed does not call this class.
 *
 * Live publication may use current-snapshot economics as a contemporaneous profitability gate.
 * Historical confidence bonuses, negative/flat penalties, stricter filters, contextual targets and
 * factor-family reweighting remain shadow-only until explicitly promoted after out-of-sample validation.
 */
object ForecastAdaptiveSelectionPolicy {
    const val MIN_NET_RETURN_PCT = 0.50
    const val ASSUMED_BASE_SLIPPAGE_BPS = 2.0
    const val MAX_ASSUMED_HALF_SPREAD_BPS = 20.0
    const val MIN_DOWNSIDE_PROXY_PCT = 0.25
    const val MIN_MATURE_SAMPLES = 5
    const val MIN_MATURE_DAYS = 3
    const val MIN_MATURE_SYMBOLS = 3
    const val MAX_POSITIVE_CONFIDENCE_BONUS = 0.04
    const val MAX_ECONOMICS_BONUS = 0.04
    const val MAX_RISK_BONUS = 0.04

    /** Stricter candidate filter experiment. This is telemetry/research only. */
    fun shadowFilterReview(evaluation: StrategyEvaluation, targetPct: Double): ForecastShadowFilterReview {
        val f = evaluation.features
        val fails = mutableListOf<String>()
        val atrPct = f.atr14?.takeIf { f.ltp > 0.0 && it > 0.0 }?.let { it / f.ltp * 100.0 }
        if (f.spreadBps == null) fails += "missing spread"
        else if (f.spreadBps > 35.0) fails += "spread >35bps"
        if (f.rvol == null) fails += "missing RVOL"
        else if (f.rvol < 1.0) fails += "RVOL <1.0"
        if (f.atr14 == null || atrPct == null) fails += "missing ATR"
        else if (atrPct * 4.0 + 1e-9 < targetPct) fails += "ATR capacity < target"
        if (f.vwap == null) fails += "missing VWAP"
        if (f.ema9 == null || f.ema20 == null) fails += "missing EMA9/20"
        if (f.candles.size < 20) fails += "insufficient candles"
        return ForecastShadowFilterReview(fails.isEmpty(), fails)
    }

    fun economics(
        entryPrice: Double,
        targetPct: Double,
        budgetRupees: Long,
        spreadBps: Double?,
        atrPct: Double?
    ): ForecastEconomicsDecision {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 || targetPct <= 0.0 || budgetRupees <= 0L) {
            return ForecastEconomicsDecision(0, entryPrice, entryPrice, 0.0, 0.0, 0.0, 0.0, 0.0,
                MIN_DOWNSIDE_PROXY_PCT, 0.0, false)
        }
        val halfSpreadBps = ((spreadBps ?: 0.0).coerceAtLeast(0.0) / 2.0)
            .coerceAtMost(MAX_ASSUMED_HALF_SPREAD_BPS)
        val slippageBpsPerLeg = ASSUMED_BASE_SLIPPAGE_BPS + halfSpreadBps
        val entryFill = entryPrice * (1.0 + slippageBpsPerLeg / 10_000.0)
        val targetReference = entryPrice * (1.0 + targetPct / 100.0)
        val exitFill = targetReference * (1.0 - slippageBpsPerLeg / 10_000.0)
        val qty = floor(budgetRupees.toDouble() / entryFill).toInt().coerceAtLeast(0)
        if (qty <= 0 || exitFill <= 0.0) {
            return ForecastEconomicsDecision(0, entryFill, exitFill, 0.0, 0.0, 0.0, 0.0, 0.0,
                MIN_DOWNSIDE_PROXY_PCT, 0.0, false)
        }
        val gross = (exitFill - entryFill) * qty
        val charges = GrowwDeliveryCostPolicy.estimateCharges(entryFill, exitFill, qty).total
        val noSlipGross = (targetReference - entryPrice) * qty
        val slippageCost = (noSlipGross - gross).coerceAtLeast(0.0)
        val net = gross - charges
        val capital = entryFill * qty
        val netReturn = if (capital > 0.0) net / capital * 100.0 else 0.0
        val roundTripSlippagePct = slippageBpsPerLeg * 2.0 / 100.0
        val downside = max(
            MIN_DOWNSIDE_PROXY_PCT,
            max(atrPct?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0, roundTripSlippagePct)
        )
        val riskAdjusted = netReturn / downside
        return ForecastEconomicsDecision(
            quantity = qty,
            assumedEntryPrice = entryFill,
            assumedExitPrice = exitFill,
            estimatedChargesRupees = charges,
            estimatedSlippageRupees = slippageCost,
            grossProfitRupees = gross,
            netProfitRupees = net,
            netReturnPct = netReturn,
            downsideProxyPct = downside,
            riskAdjustedScore = riskAdjusted,
            profitableAfterCosts = netReturn + 1e-9 >= MIN_NET_RETURN_PCT
        )
    }

    /** Minimum gross target needed for the configured budget to retain at least 0.50% net. */
    fun minimumTargetPctForNetReturn(
        entryPrice: Double,
        budgetRupees: Long,
        spreadBps: Double?,
        targetNetReturnPct: Double = MIN_NET_RETURN_PCT
    ): Double {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 || budgetRupees <= 0L ||
            !targetNetReturnPct.isFinite() || targetNetReturnPct <= 0.0) return 0.0
        val conservativeSpread = spreadBps ?: (MAX_ASSUMED_HALF_SPREAD_BPS * 2.0)
        var low = 0.0
        var high = 20.0
        if (economics(entryPrice, high, budgetRupees, conservativeSpread, null).netReturnPct + 1e-9 < targetNetReturnPct) {
            return high
        }
        repeat(48) {
            val mid = (low + high) / 2.0
            val net = economics(entryPrice, mid, budgetRupees, conservativeSpread, null).netReturnPct
            if (net + 1e-9 >= targetNetReturnPct) high = mid else low = mid
        }
        return high
    }

    fun diagnostics(
        strategy: String,
        regime: String,
        marketRegime: String,
        outcomes: List<ForecastHistoricalOutcome>
    ): ForecastEvidenceDiagnostics {
        val context = outcomes.filter {
            it.strategy == strategy && it.regime == regime && it.marketRegime == marketRegime
        }.ifEmpty {
            outcomes.filter { it.regime == regime && it.marketRegime == marketRegime }
        }
        if (context.isEmpty()) return ForecastEvidenceDiagnostics()

        val clean = context.filter {
            it.maxFavourablePct.isFinite() && it.maxAdversePct.isFinite() && it.closePct.isFinite()
        }
        if (clean.isEmpty()) return ForecastEvidenceDiagnostics()

        val positive = clean.count { it.closePct > 0.05 || it.targetHit }
        val adverse = clean.count { !(it.closePct > 0.05 || it.targetHit) && it.closePct < -0.05 }
        val flat = (clean.size - positive - adverse).coerceAtLeast(0)
        val hitRate = clean.count { it.targetHit }.toDouble() / clean.size
        val positiveRate = positive.toDouble() / clean.size
        val medianMfe = AdaptiveLearningMath.median(clean.map { it.maxFavourablePct }, 0.0)
        val medianMae = AdaptiveLearningMath.median(clean.map { it.maxAdversePct }, 0.0)
        val medianClose = AdaptiveLearningMath.median(clean.map { it.closePct }, 0.0)
        val dayCount = clean.map { it.date }.distinct().size
        val symbolCount = clean.map { it.symbol.uppercase() }.distinct().size

        // Agreement between target-hit and positive-close evidence; 1.0 = very consistent.
        val dispersion = clean.map { if (it.targetHit || it.closePct > 0.05) 1.0 else 0.0 }
        val mean = dispersion.average()
        val variance = dispersion.map { (it - mean) * (it - mean) }.average()
        val consistency = (1.0 - sqrt(variance).coerceIn(0.0, 0.5) * 2.0).coerceIn(0.0, 1.0)
        val breadthConfidence = min(1.0, clean.size / 12.0) *
            min(1.0, dayCount / 5.0) *
            min(1.0, symbolCount / 5.0)
        val confidence = (breadthConfidence * (0.55 + 0.45 * consistency)).coerceIn(0.0, 1.0)

        // Chronological holdout: the newest 25% of distinct dates are validation only.
        val dates = clean.map { it.date }.distinct().sorted()
        val validationDateCount = if (dates.size >= 4) max(1, dates.size / 4) else 0
        val validationDates = if (validationDateCount > 0) dates.takeLast(validationDateCount).toSet() else emptySet()
        val validation = clean.filter { it.date in validationDates }
        val validationHitRate = if (validation.isEmpty()) 0.0
            else validation.count { it.targetHit }.toDouble() / validation.size * 100.0

        // Shadow-only negative learning proposal. Never add this value to live ranking.
        val adversePressure = (adverse.toDouble() / clean.size * 0.06) +
            ((medianMae - medianMfe).coerceAtLeast(0.0) * 0.015) +
            ((-medianClose).coerceAtLeast(0.0) * 0.01)
        val shadowPenalty = (-adversePressure).coerceIn(-0.10, 0.0)

        return ForecastEvidenceDiagnostics(
            sampleCount = clean.size,
            distinctDays = dayCount,
            distinctSymbols = symbolCount,
            positiveCount = positive,
            flatCount = flat,
            adverseCount = adverse,
            targetHitRatePct = hitRate * 100.0,
            positiveRatePct = positiveRate * 100.0,
            medianMfePct = medianMfe,
            medianMaePct = medianMae,
            medianClosePct = medianClose,
            consistency = consistency,
            confidence = confidence,
            validationSamples = validation.size,
            validationTargetHitRatePct = validationHitRate,
            shadowPenalty = shadowPenalty
        )
    }

    fun positiveConfidenceBonus(diagnostics: ForecastEvidenceDiagnostics): Double {
        val mature = diagnostics.sampleCount >= MIN_MATURE_SAMPLES &&
            diagnostics.distinctDays >= MIN_MATURE_DAYS &&
            diagnostics.distinctSymbols >= MIN_MATURE_SYMBOLS
        if (!mature || diagnostics.positiveRatePct <= 50.0) return 0.0
        val edge = ((diagnostics.positiveRatePct - 50.0) / 50.0).coerceIn(0.0, 1.0)
        return (edge * diagnostics.confidence * MAX_POSITIVE_CONFIDENCE_BONUS)
            .coerceIn(0.0, MAX_POSITIVE_CONFIDENCE_BONUS)
    }

    fun economicsBonus(economics: ForecastEconomicsDecision): Double {
        if (!economics.profitableAfterCosts) return 0.0
        return (economics.netReturnPct / 2.0).coerceIn(0.0, MAX_ECONOMICS_BONUS)
    }

    fun riskBonus(economics: ForecastEconomicsDecision): Double {
        if (!economics.profitableAfterCosts) return 0.0
        return (economics.riskAdjustedScore / 20.0).coerceIn(0.0, MAX_RISK_BONUS)
    }

    /** Diagnostic-only target suggestion; current live expansion safeguards remain authoritative. */
    fun shadowTargetPct(currentTargetPct: Double, diagnostics: ForecastEvidenceDiagnostics): Double {
        if (diagnostics.sampleCount < MIN_MATURE_SAMPLES || diagnostics.medianMfePct <= 0.0) return currentTargetPct
        val reliability = diagnostics.confidence.coerceIn(0.0, 1.0)
        val adverseHaircut = (diagnostics.medianMaePct * 0.20).coerceAtMost(0.40)
        val evidenceTarget = (diagnostics.medianMfePct * (0.65 + 0.15 * reliability) - adverseHaircut)
            .coerceAtLeast(ForecastQualityPolicy.MIN_TARGET_PCT)
        return evidenceTarget.coerceIn(
            ForecastQualityPolicy.MIN_TARGET_PCT,
            currentTargetPct + ForecastTargetExpansionPolicy.MAX_UPLIFT_PCT_POINTS
        )
    }

    /**
     * Diagnostic-only family reweighting proposal. Uses winners and non-winners together and is
     * intentionally not wired into MultifyFactorFamilies live weights.
     */
    fun shadowFamilyWeights(
        marketRegime: String,
        outcomes: List<ForecastFamilyOutcome>
    ): Map<String, Double> {
        val rows = outcomes.filter { it.marketRegime == marketRegime }
        val distinctDays = rows.map { it.date }.distinct().size
        val distinctSymbols = rows.map { it.symbol.uppercase() }.distinct().size
        if (rows.size < 8 || distinctDays < MIN_MATURE_DAYS || distinctSymbols < MIN_MATURE_SYMBOLS) {
            return mapOf(
                MultifyFactorFamilies.MOMENTUM_TREND to 1.0,
                MultifyFactorFamilies.PARTICIPATION_LIQUIDITY to 1.0,
                MultifyFactorFamilies.STRUCTURE_CONTEXT to 1.0
            )
        }
        fun suggested(selector: (ForecastFamilyOutcome) -> Double): Double {
            val winners = rows.filter { it.positive }.map(selector)
            val others = rows.filterNot { it.positive }.map(selector)
            if (winners.isEmpty() || others.isEmpty()) return 1.0
            val separation = winners.average() - others.average()
            return (1.0 + separation * 0.25).coerceIn(0.80, 1.20)
        }
        return mapOf(
            MultifyFactorFamilies.MOMENTUM_TREND to suggested { it.momentumScore },
            MultifyFactorFamilies.PARTICIPATION_LIQUIDITY to suggested { it.participationScore },
            MultifyFactorFamilies.STRUCTURE_CONTEXT to suggested { it.structureScore }
        )
    }
}
