package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Chooses exactly one AUTO direction decision at each 2% checkpoint after the initial Multify entry.
 * LONG/SHORT inputs are event-neutral strategy evaluations from the same market snapshot.
 * HOLD is deliberate: forcing a side when both expected values are weak creates churn.
 */
data class WaveDirectionDecision(
    val action: String,
    val longProbability: Double,
    val shortProbability: Double,
    val longExpectedValueRupees: Double,
    val shortExpectedValueRupees: Double,
    val winnerExpectedValueRupees: Double,
    val expectedValueGapRupees: Double,
    val confidence: Double,
    val reason: String
)

object WaveDirectionMath {
    const val WAVE_FRACTION = 0.02
    const val MAX_WAVES = 10

    fun waveIndex(anchorPrice: Double, currentPrice: Double): Int {
        if (anchorPrice <= 0.0 || currentPrice <= 0.0) return 0
        return floor(abs(currentPrice / anchorPrice - 1.0) / WAVE_FRACTION + 1e-9).toInt().coerceAtMost(MAX_WAVES - 1)
    }

    fun decide(
        currentSide: String,
        exposureRupees: Double,
        price: Double,
        atr: Double,
        spreadBps: Double?,
        longDirectionalScore: Double,
        longConfidence: Double,
        shortDirectionalScore: Double,
        shortConfidence: Double
    ): WaveDirectionDecision {
        if (price <= 0.0 || exposureRupees <= 0.0) return hold("Invalid checkpoint price/exposure")
        if ((spreadBps ?: 0.0) > 35.0) return hold("Spread is too wide for a direction change")

        val qty = floor(exposureRupees / price).toInt()
        if (qty <= 0) return hold("₹${exposureRupees.toInt()} fixed exposure cannot buy/short one share at the current price")

        val safeAtr = max(atr, max(price * 0.0035, 0.05))
        val longP = calibratedProbability(longDirectionalScore, longConfidence)
        val shortP = calibratedProbability(shortDirectionalScore, shortConfidence)
        val longEv = expectedValue(qty, price, safeAtr, longP, spreadBps)
        val shortEv = expectedValue(qty, price, safeAtr, shortP, spreadBps)

        val winner = if (longEv >= shortEv) "LONG" else "SHORT"
        val winnerP = if (winner == "LONG") longP else shortP
        val winnerScore = if (winner == "LONG") longDirectionalScore else shortDirectionalScore
        val winnerEv = max(longEv, shortEv)
        val gap = abs(longEv - shortEv)
        val flip = currentSide.equals("LONG", true) && winner == "SHORT" || currentSide.equals("SHORT", true) && winner == "LONG"

        val minProbability = if (flip) 0.64 else 0.58
        val minScore = if (flip) 0.16 else 0.08
        val minEv = if (flip) max(20.0, exposureRupees * 0.004) else max(10.0, exposureRupees * 0.002)
        val minGap = if (flip) max(15.0, exposureRupees * 0.003) else max(7.5, exposureRupees * 0.0015)

        if (winnerP < minProbability || winnerScore < minScore || winnerEv < minEv || gap < minGap) {
            return WaveDirectionDecision(
                action = "HOLD", longProbability = longP, shortProbability = shortP,
                longExpectedValueRupees = longEv, shortExpectedValueRupees = shortEv,
                winnerExpectedValueRupees = winnerEv, expectedValueGapRupees = gap,
                confidence = winnerP,
                reason = "No clean one-sided edge: winner=$winner p=${fmt(winnerP)} score=${fmt(winnerScore)} EV=₹${money(winnerEv)} gap=₹${money(gap)}"
            )
        }

        return WaveDirectionDecision(
            action = winner, longProbability = longP, shortProbability = shortP,
            longExpectedValueRupees = longEv, shortExpectedValueRupees = shortEv,
            winnerExpectedValueRupees = winnerEv, expectedValueGapRupees = gap,
            confidence = winnerP,
            reason = "One-sided checkpoint edge: $winner p=${fmt(winnerP)} EV=₹${money(winnerEv)} vs ₹${money(if (winner == "LONG") shortEv else longEv)}"
        )
    }

    private fun calibratedProbability(score: Double, confidence: Double): Double {
        // Blend the ensemble's normalized score with its own confidence. This is deliberately
        // conservative until the rolling live dataset is large enough for empirical calibration.
        val scoreProbability = 0.50 + score.coerceIn(-1.0, 1.0) * 0.38
        return (scoreProbability * 0.58 + confidence.coerceIn(0.05, 0.95) * 0.42).coerceIn(0.08, 0.92)
    }

    private fun expectedValue(qty: Int, price: Double, atr: Double, pWin: Double, spreadBps: Double?): Double {
        val strength = abs(pWin - 0.5) * 2.0
        val targetDistance = atr * (1.15 + 0.55 * strength)
        val stopDistance = atr * (0.90 + 0.10 * (1.0 - strength))
        val notional = qty * price
        // Conservative all-in friction proxy: statutory/brokerage + half-spread + slippage reserve.
        val frictionBps = 14.0 + (spreadBps ?: 8.0).coerceAtLeast(0.0) * 0.55
        val costs = notional * frictionBps / 10_000.0
        return qty * (pWin * targetDistance - (1.0 - pWin) * stopDistance) - costs
    }

    private fun hold(reason: String) = WaveDirectionDecision(
        action = "HOLD", longProbability = .5, shortProbability = .5,
        longExpectedValueRupees = 0.0, shortExpectedValueRupees = 0.0,
        winnerExpectedValueRupees = 0.0, expectedValueGapRupees = 0.0,
        confidence = .5, reason = reason
    )

    private fun fmt(x: Double) = "%.3f".format(java.util.Locale.US, x)
    private fun money(x: Double) = "%.1f".format(java.util.Locale.US, x)
}
