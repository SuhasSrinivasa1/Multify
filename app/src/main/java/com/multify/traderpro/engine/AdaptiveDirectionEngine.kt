package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class AdaptiveDirectionInput(
    val currentSide: String,
    val waveCapitalRupees: Double,
    val price: Double,
    val atr: Double,
    val spreadBps: Double?,
    val longDirectionalScore: Double,
    val longConfidence: Double,
    val shortDirectionalScore: Double,
    val shortConfidence: Double,
    val trajectoryComposite: Double,
    val marketBias: Double = 0.0,
    val sectorBias: Double = 0.0,
    val dataFresh: Boolean = true,
    val decisionComplete: Boolean = true,
    val priorDecision: String = "HOLD"
)

data class AdaptiveDirectionDecision(
    val action: String,
    val longProbability: Double,
    val shortProbability: Double,
    val longExpectedValueRupees: Double,
    val shortExpectedValueRupees: Double,
    val longExpectedValuePct: Double,
    val shortExpectedValuePct: Double,
    val confidence: Double,
    val expectedValueGapRupees: Double,
    val expectedLongGainPct: Double,
    val expectedLongLossPct: Double,
    val expectedShortGainPct: Double,
    val expectedShortLossPct: Double,
    val estimatedCostsRupees: Double,
    val reason: String
)

object AdaptiveDirectionEngine {
    const val MODEL_VERSION = "ADE-1.0"

    fun decide(input: AdaptiveDirectionInput): AdaptiveDirectionDecision {
        if (!input.dataFresh) return hold("Market snapshot is stale")
        if (!input.decisionComplete) return hold("Decision snapshot is incomplete")
        if (input.price <= 0.0 || input.waveCapitalRupees <= 0.0) return hold("Invalid price/wave capital")
        val qty = floor(input.waveCapitalRupees / input.price).toInt()
        if (qty <= 0) return hold("Wave capital cannot fund one share")
        val notional = qty * input.price
        val spread = (input.spreadBps ?: 8.0).coerceAtLeast(0.0)
        if (spread > 45.0) return hold("Spread exceeds liquidity safety gate")
        val safeAtr = max(input.atr, max(input.price * 0.0035, 0.05))
        val atrPct = safeAtr / input.price
        val longRaw = 0.58 * input.longDirectionalScore + 0.22 * input.trajectoryComposite + 0.10 * input.marketBias + 0.10 * input.sectorBias
        val shortRaw = 0.58 * input.shortDirectionalScore - 0.22 * input.trajectoryComposite - 0.10 * input.marketBias - 0.10 * input.sectorBias
        val longP0 = probability(longRaw, input.longConfidence)
        val shortP0 = probability(shortRaw, input.shortConfidence)
        val norm = max(1e-9, longP0 + shortP0)
        val longP = (longP0 / norm).coerceIn(0.08, 0.92)
        val shortP = (shortP0 / norm).coerceIn(0.08, 0.92)
        val longStrength = abs(longRaw).coerceIn(0.0, 1.0)
        val shortStrength = abs(shortRaw).coerceIn(0.0, 1.0)
        val longGainPct = (atrPct * (1.05 + 1.10 * longStrength)).coerceIn(0.0025, 0.035)
        val longLossPct = (atrPct * (0.80 + 0.50 * (1.0 - longStrength))).coerceIn(0.0020, 0.025)
        val shortGainPct = (atrPct * (1.05 + 1.10 * shortStrength)).coerceIn(0.0025, 0.035)
        val shortLossPct = (atrPct * (0.80 + 0.50 * (1.0 - shortStrength))).coerceIn(0.0020, 0.025)
        val costs = estimatedCosts(notional, spread)
        val longEv = notional * (longP * longGainPct - shortP * longLossPct) - costs
        val shortEv = notional * (shortP * shortGainPct - longP * shortLossPct) - costs
        val gap = abs(longEv - shortEv)
        val winner = if (longEv >= shortEv) "LONG" else "SHORT"
        val winnerEv = max(longEv, shortEv)
        val loserEv = min(longEv, shortEv)
        val winnerP = if (winner == "LONG") longP else shortP
        val switching = input.currentSide in setOf("LONG","SHORT") && input.currentSide != winner
        val flipFlop = input.priorDecision in setOf("LONG","SHORT") && input.priorDecision != winner
        val minPositiveEv = max(costs * 0.35, notional * 0.00035)
        val baseGap = max(costs * 0.55, notional * 0.00055)
        val hysteresisGap = baseGap * when {
            switching && flipFlop -> 2.6
            switching -> 1.8
            flipFlop -> 1.5
            else -> 1.0
        }
        if (winnerEv <= minPositiveEv || gap < hysteresisGap) {
            return AdaptiveDirectionDecision(
                action = "HOLD", longProbability = longP, shortProbability = shortP,
                longExpectedValueRupees = longEv, shortExpectedValueRupees = shortEv,
                longExpectedValuePct = longEv / notional * 100.0, shortExpectedValuePct = shortEv / notional * 100.0,
                confidence = max(longP, shortP), expectedValueGapRupees = gap,
                expectedLongGainPct = longGainPct * 100.0, expectedLongLossPct = longLossPct * 100.0,
                expectedShortGainPct = shortGainPct * 100.0, expectedShortLossPct = shortLossPct * 100.0,
                estimatedCostsRupees = costs,
                reason = "NO TRADE: after-cost winner EV/gap is insufficient; winner=$winner EV=${money(winnerEv)} loser=${money(loserEv)} gap=${money(gap)}"
            )
        }
        return AdaptiveDirectionDecision(
            action = winner, longProbability = longP, shortProbability = shortP,
            longExpectedValueRupees = longEv, shortExpectedValueRupees = shortEv,
            longExpectedValuePct = longEv / notional * 100.0, shortExpectedValuePct = shortEv / notional * 100.0,
            confidence = winnerP, expectedValueGapRupees = gap,
            expectedLongGainPct = longGainPct * 100.0, expectedLongLossPct = longLossPct * 100.0,
            expectedShortGainPct = shortGainPct * 100.0, expectedShortLossPct = shortLossPct * 100.0,
            estimatedCostsRupees = costs,
            reason = "$winner selected by positive after-cost EV: LONG=${money(longEv)} SHORT=${money(shortEv)} costs=${money(costs)}"
        )
    }

    private fun probability(score: Double, confidence: Double): Double {
        val z = score.coerceIn(-1.5, 1.5) * 2.25 + (confidence.coerceIn(0.05, 0.95) - 0.5) * 1.35
        return (1.0 / (1.0 + exp(-z))).coerceIn(0.08, 0.92)
    }

    private fun estimatedCosts(notional: Double, spreadBps: Double): Double {
        val brokerage = min(40.0, max(2.0, notional * 0.002))
        val statutory = notional * 0.00045
        val halfSpread = notional * (spreadBps / 2.0) / 10_000.0
        val slippage = notional * (0.00035 + min(0.00045, spreadBps / 10_000.0 * 0.25))
        val liquidityReserve = notional * 0.00020
        return brokerage + statutory + halfSpread + slippage + liquidityReserve
    }

    private fun hold(reason: String) = AdaptiveDirectionDecision(
        action = "HOLD", longProbability = .5, shortProbability = .5,
        longExpectedValueRupees = 0.0, shortExpectedValueRupees = 0.0,
        longExpectedValuePct = 0.0, shortExpectedValuePct = 0.0,
        confidence = .5, expectedValueGapRupees = 0.0,
        expectedLongGainPct = 0.0, expectedLongLossPct = 0.0,
        expectedShortGainPct = 0.0, expectedShortLossPct = 0.0,
        estimatedCostsRupees = 0.0, reason = reason
    )

    private fun money(v: Double) = "₹" + "%.1f".format(java.util.Locale.US, v)
}

data class WaveCapitalEligibility(val eligible: Boolean, val waveNumber: Int, val availableCapitalRupees: Double, val reason: String)

object WaveCapitalPolicy {
    fun triggeredWave(anchorPrice: Double, currentPrice: Double, spacingPercent: Double, maximumWaves: Int): Int {
        if (anchorPrice <= 0.0 || currentPrice <= 0.0 || spacingPercent <= 0.0) return 1
        val displacementPct = abs(currentPrice / anchorPrice - 1.0) * 100.0
        return (floor(displacementPct / spacingPercent + 1e-9).toInt() + 1).coerceIn(1, maximumWaves.coerceIn(1, 20))
    }

    fun eligibility(
        requestedWave: Int, lastProcessedWave: Int, waveCapitalRupees: Double,
        campaignCapitalUsed: Double, maxCampaignCapital: Double,
        dailyLossRupees: Double, maxDailyLossRupees: Double,
        singleStockLossRupees: Double, maxSingleStockLossRupees: Double
    ): WaveCapitalEligibility {
        if (requestedWave <= 1 || requestedWave <= lastProcessedWave) return WaveCapitalEligibility(false, requestedWave, 0.0, "No new wave checkpoint")
        if (dailyLossRupees >= maxDailyLossRupees) return WaveCapitalEligibility(false, requestedWave, 0.0, "Daily loss limit reached")
        if (singleStockLossRupees >= maxSingleStockLossRupees) return WaveCapitalEligibility(false, requestedWave, 0.0, "Single-stock loss limit reached")
        val remaining = max(0.0, maxCampaignCapital - campaignCapitalUsed)
        val available = min(waveCapitalRupees, remaining)
        if (available < 1.0) return WaveCapitalEligibility(false, requestedWave, 0.0, "Campaign capital limit reached")
        return WaveCapitalEligibility(true, requestedWave, available, "Wave is eligible; direction engine must still approve LONG/SHORT/HOLD")
    }
}
