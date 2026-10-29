package com.multify.traderpro.engine

/** Adaptive Forecast only. 5-minute bars with 15/30-minute rolling context.
 * Fixed selection / publication is completely independent of this class.
 */
data class EarlyEntryAssessment(
    val qualifies: Boolean,
    val watch: Boolean,
    val score: Double,
    val reason: String,
    val breakoutDistancePct: Double,
    val fiveMinuteChangePct: Double
)

object ForecastEarlyEntryPolicy {
    fun assess(evaluation: StrategyEvaluation): EarlyEntryAssessment {
        val f = evaluation.features
        val bars = f.candles
        if (bars.size < 10 || f.ltp <= 0.0 || !f.ltp.isFinite())
            return EarlyEntryAssessment(false, false, 0.0, "Insufficient 5-minute candle history", 0.0, 0.0)
        val previous = bars.dropLast(1)
        val referenceHigh = previous.takeLast(6).maxOf { it.high }
        val priorFive = previous.takeLast(3)
        val previousFiveClose = bars.dropLast(1).lastOrNull()?.close ?: return EarlyEntryAssessment(false, false, 0.0, "No preceding 5-minute baseline", 0.0, 0.0)
        if (referenceHigh <= 0.0 || previousFiveClose <= 0.0)
            return EarlyEntryAssessment(false, false, 0.0, "Invalid reference price", 0.0, 0.0)
        val breakoutDistancePct = (f.ltp / referenceHigh - 1.0) * 100.0
        val fiveChangePct = (f.ltp / previousFiveClose - 1.0) * 100.0
        val recentBarChangePct = (f.ltp / (previous.lastOrNull()?.close ?: f.ltp) - 1.0) * 100.0
        val lastFiveLow = priorFive.minOf { it.low }
        val fiveLowRisePct = if (lastFiveLow > 0.0) (f.ltp / lastFiveLow - 1.0) * 100.0 else 100.0
        val nearBreakout = breakoutDistancePct in -0.55..0.20
        val notChasing = fiveChangePct in -0.55..0.90 && recentBarChangePct <= 0.65 && fiveLowRisePct <= 1.25
        val spreadSafe = f.spreadBps?.let { it <= 40.0 } ?: false
        val participation = (f.rvol ?: 0.0) >= 0.75 && (f.greenVolumeShare ?: 0.0) >= 0.43
        val direction = ((f.ema9Slope ?: 0.0) > -0.08 || (f.rsiSlope ?: 0.0) > 0.0) &&
            evaluation.directionalScore >= 0.08 && evaluation.confidence >= 0.53
        val structure = (f.structureScore ?: 0.0) > -0.33 &&
            (f.vwap?.let { f.ltp >= it * 0.996 } ?: false)
        val capacity = f.atr14?.let { it > 0.0 && it / f.ltp * 100.0 >= 0.12 } ?: false
        val votes = listOf(spreadSafe, participation, direction, structure, capacity).count { it }
        val qualifies = nearBreakout && notChasing && votes >= 4 && spreadSafe && direction && participation
        val watch = nearBreakout && notChasing && votes >= 3
        val score = (votes / 5.0 * 0.75 + (if (nearBreakout) 0.13 else 0.0) + (if (notChasing) 0.12 else 0.0))
            .coerceIn(0.0, 1.0)
        val reasons = listOf(
            if (nearBreakout) "near prior 30m resistance" else "not near prior 30m resistance",
            if (notChasing) "pre-spike price extension controlled" else "recent spike overextended",
            if (spreadSafe) "spread acceptable" else "spread missing/too wide",
            if (participation) "early volume participation" else "participation weak",
            if (direction) "momentum starting" else "momentum not ready",
            if (structure) "structure building" else "structure weak"
        )
        return EarlyEntryAssessment(qualifies, watch, score,
            reasons.joinToString(" · ") + " · 5m ${"%.2f".format(java.util.Locale.US, fiveChangePct)}%" +
                " · 30m resistance distance ${"%.2f".format(java.util.Locale.US, breakoutDistancePct)}%",
            breakoutDistancePct, fiveChangePct)
    }

    fun quality(evaluation: StrategyEvaluation, targetPct: Double): ForecastQualityDecision {
        val decision = assess(evaluation)
        val f = evaluation.features
        val capacityPct = f.atr14?.takeIf { it > 0.0 && f.ltp > 0.0 }
            ?.let { it / f.ltp * 100.0 * 4.0 } ?: 0.0
        val viable = capacityPct + 1e-9 >= targetPct * 0.55
        val primary = decision.qualifies && viable
        return ForecastQualityDecision(
            tier = if (primary) "PRIMARY" else if (decision.watch) "SECONDARY" else "RESEARCH",
            publishEligible = primary,
            // Early Forecast is initially signal-only until risk/latency is measured.
            // Manual BUY eligibility remains separate and requires explicit user action.
            executionEligible = primary,
            volatilityCapacityPct = capacityPct,
            reason = if (primary) "EARLY ENTRY · " + decision.reason
                else if (!viable) "EARLY WATCH · insufficient volatility capacity · " + decision.reason
                else "EARLY WATCH · " + decision.reason
        )
    }
}
