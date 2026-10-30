package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.max

data class EarlyEntryAssessment(
    val qualifies: Boolean,
    val watch: Boolean,
    val score: Double,
    val reason: String,
    val breakoutDistancePct: Double,
    val fiveMinuteChangePct: Double,
    val volatilityCapacityPct: Double = 0.0,
    val completedFiveMinuteBars: Int = 0,
    val multiPeakRetest: Boolean = false,
    val failedRules: List<String> = emptyList()
)

/** Forecast only. Uses completed 5m bars aggregated from timestamped 1m candles. */
object ForecastEarlyEntryPolicy {
    private const val MIN_COMPLETE_5M_BARS = 6
    private const val MIN_CAPACITY_PCT = 0.48
    private const val MULTI_PEAK_TOLERANCE_PCT = 0.45

    fun assess(evaluation: StrategyEvaluation, asOfMs: Long = System.currentTimeMillis()): EarlyEntryAssessment {
        val f = evaluation.features
        val bars = FiveMinuteCandleAggregator.aggregate(
            f.candles.map { MarketCandleBar(it.ts * 1000L, it.open, it.high, it.low, it.close, it.volume) },
            asOfMs
        )
        if (bars.size < MIN_COMPLETE_5M_BARS || f.ltp <= 0.0 || !f.ltp.isFinite()) {
            return EarlyEntryAssessment(
                false, false, 0.0, "Insufficient complete 5-minute bars for 30-minute context",
                0.0, 0.0, 0.0, bars.size, false, listOf("insufficient completed 5-minute context")
            )
        }

        // Keep the original 30-minute resistance reference (six completed 5m bars).
        // The wider eight-bar window is used only to recognise a controlled multi-peak retest.
        val contextBars = bars.takeLast(6)
        val window = bars.takeLast(8)
        val referenceHigh = contextBars.maxOf { it.high }
        val lastThree = bars.takeLast(3)
        val previousFiveClose = bars[bars.lastIndex - 1].close
        val latestFiveClose = bars.last().close
        if (referenceHigh <= 0.0 || previousFiveClose <= 0.0 || latestFiveClose <= 0.0) {
            return EarlyEntryAssessment(
                false, false, 0.0, "Invalid reference price", 0.0, 0.0,
                0.0, bars.size, false, listOf("invalid completed-candle reference")
            )
        }

        val breakoutDistancePct = (f.ltp / referenceHigh - 1.0) * 100.0
        val fiveChangePct = (latestFiveClose / previousFiveClose - 1.0) * 100.0
        val currentExtensionFromFiveClosePct = (f.ltp / latestFiveClose - 1.0) * 100.0
        val completeOneMinute = f.candles
            .filter { it.ts > 0L && it.ts * 1000L + 60_000L <= asOfMs }
            .sortedBy { it.ts }
        val lastOneMinute = completeOneMinute.lastOrNull()
        val previousOneMinute = completeOneMinute.getOrNull(completeOneMinute.lastIndex - 1)
        val recentBarChangePct = if (lastOneMinute != null && previousOneMinute != null && previousOneMinute.close > 0.0)
            (lastOneMinute.close / previousOneMinute.close - 1.0) * 100.0 else 100.0
        val currentExtensionFromOneMinuteClosePct = if (lastOneMinute != null && lastOneMinute.close > 0.0)
            (f.ltp / lastOneMinute.close - 1.0) * 100.0 else 100.0
        val low = lastThree.minOf { it.low }
        val fiveLowRisePct = if (low > 0.0) (f.ltp / low - 1.0) * 100.0 else 100.0

        val localPeaks = (1 until window.lastIndex).count { index ->
            val bar = window[index]
            bar.high >= window[index - 1].high &&
                bar.high >= window[index + 1].high &&
                bar.high >= referenceHigh * (1.0 - MULTI_PEAK_TOLERANCE_PCT / 100.0)
        }
        val recentLows = window.takeLast(3).map { it.low }
        val risingRetestFloor = recentLows.size == 3 &&
            recentLows[1] >= recentLows[0] * 0.998 &&
            recentLows[2] >= recentLows[1] * 0.998
        val multiPeakRetest = localPeaks >= 2 && risingRetestFloor &&
            breakoutDistancePct in -0.70..0.45
        val nearBreakout = breakoutDistancePct in -0.55..0.20 || multiPeakRetest

        val notChasing = fiveChangePct in -0.55..0.90 &&
            currentExtensionFromFiveClosePct in -0.55..0.65 &&
            recentBarChangePct in -0.45..0.65 &&
            currentExtensionFromOneMinuteClosePct in -0.55..0.65 &&
            fiveLowRisePct <= 1.25

        val trueRanges = bars.mapIndexedNotNull { index, bar ->
            val previous = bars.getOrNull(index - 1) ?: return@mapIndexedNotNull null
            max(bar.high - bar.low, max(abs(bar.high - previous.close), abs(bar.low - previous.close)))
        }
        val atr5 = trueRanges.takeLast(14).takeIf { it.isNotEmpty() }?.average()
        val atr5Pct = atr5?.takeIf { it.isFinite() && it > 0.0 }?.div(f.ltp)?.times(100.0) ?: 0.0
        // Four completed 5m ranges, not four 1m ATRs, are used for intraday capacity.
        val capacityPct = atr5Pct * 4.0
        val capacityVote = capacityPct >= MIN_CAPACITY_PCT

        val spreadSafe = f.spreadBps?.let { it.isFinite() && it in 0.0..40.0 } ?: false
        val participation = (f.rvol ?: 0.0) >= 0.75 && (f.greenVolumeShare ?: 0.0) >= 0.43
        val direction = ((f.ema9Slope ?: 0.0) > -0.08 || (f.rsiSlope ?: 0.0) > 0.0) &&
            evaluation.directionalScore >= 0.08 && evaluation.confidence >= 0.53
        val structure = (f.structureScore ?: 0.0) > -0.33 && (f.vwap?.let { f.ltp >= it * 0.996 } ?: false)
        val votes = listOf(spreadSafe, participation, direction, structure, capacityVote).count { it }
        val qualifies = nearBreakout && notChasing && votes >= 4 && spreadSafe && direction && participation
        val watch = nearBreakout && notChasing && votes >= 3

        val failures = mutableListOf<String>()
        if (!nearBreakout) failures += "not near prior 30m resistance"
        if (!notChasing) failures += "recent spike overextended"
        if (!spreadSafe) failures += "spread missing/too wide"
        if (!participation) failures += "participation weak"
        if (!direction) failures += "momentum not ready"
        if (!structure) failures += "structure weak"
        if (!capacityVote) failures += "five-minute ATR capacity below minimum floor"

        val score = (votes / 5.0 * 0.75 + (if (nearBreakout) 0.13 else 0.0) +
            (if (notChasing) 0.12 else 0.0)).coerceIn(0.0, 1.0)
        val reasons = listOf(
            when {
                multiPeakRetest -> "multi-peak retest/re-breakout structure confirmed"
                nearBreakout -> "near prior 30m resistance"
                else -> "not near prior 30m resistance"
            },
            if (notChasing) "pre-spike price extension controlled" else "recent spike overextended",
            if (spreadSafe) "spread acceptable" else "spread missing/too wide",
            if (participation) "early volume participation" else "participation weak",
            if (direction) "momentum starting" else "momentum not ready",
            if (structure) "structure building" else "structure weak"
        )
        val reason = reasons.joinToString(" · ") +
            " · 5m " + String.format(java.util.Locale.US, "%.2f", fiveChangePct) +
            "% · resistance distance " + String.format(java.util.Locale.US, "%.2f", breakoutDistancePct) +
            "% · 5m ATR capacity " + String.format(java.util.Locale.US, "%.2f", capacityPct) + "%"
        return EarlyEntryAssessment(
            qualifies, watch, score, reason, breakoutDistancePct, fiveChangePct,
            capacityPct, bars.size, multiPeakRetest, failures
        )
    }

    fun quality(
        evaluation: StrategyEvaluation,
        targetPct: Double,
        asOfMs: Long = System.currentTimeMillis()
    ): ForecastQualityDecision {
        val assessment = assess(evaluation, asOfMs)
        val viable = assessment.volatilityCapacityPct + 1e-9 >= targetPct * 0.55
        val primary = assessment.qualifies && viable
        val failed = assessment.failedRules.toMutableList()
        if (!viable) failed += "volatility capacity below 55% of calibrated target"
        return ForecastQualityDecision(
            tier = if (primary) "PRIMARY" else if (assessment.watch) "SECONDARY" else "RESEARCH",
            publishEligible = primary,
            executionEligible = primary,
            volatilityCapacityPct = assessment.volatilityCapacityPct,
            reason = if (primary) "EARLY ENTRY · " + assessment.reason
                else if (!viable) "EARLY WATCH · insufficient 5-minute volatility capacity · " + assessment.reason
                else "EARLY WATCH · " + assessment.reason,
            failedRules = failed.distinct()
        )
    }
}
