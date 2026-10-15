package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastQualityPolicyTest {
    private fun features(
        regimeFriendly: Boolean = true,
        atr: Double = 0.40,
        ltp: Double = 100.0,
        rvol: Double = 1.40,
        spread: Double = 10.0
    ) = FeatureSnapshot(
        ltp = ltp,
        bid = 99.95,
        ask = 100.05,
        spreadBps = spread,
        orderBookImbalance = 0.20,
        dayChangePct = 1.20,
        marketCap = null,
        range52Position = 0.75,
        vwap = if (regimeFriendly) 99.0 else 101.0,
        ema9 = if (regimeFriendly) 100.2 else 99.8,
        ema20 = 99.5,
        ema50 = 98.0,
        ema9Slope = if (regimeFriendly) 0.10 else -0.10,
        ema20Slope = 0.06,
        vwapSlope = 0.05,
        macdHistogramSlope = 0.05,
        rsiSlope = 2.0,
        volumeAcceleration = 0.30,
        greenVolumeShare = 0.65,
        structureScore = 0.50,
        sessionHighDistancePct = 0.10,
        sessionLowDistancePct = 2.0,
        atr14 = atr,
        rsi14 = 62.0,
        bollWidth = 0.02,
        macdHistogram = 0.10,
        trendSlopeAtr = if (regimeFriendly) 0.20 else -0.10,
        rvol = rvol,
        donchianHigh = 99.0,
        donchianLow = 96.0,
        orb5High = 99.0,
        orb5Low = 97.0,
        orb15High = 99.0,
        orb15Low = 96.5,
        lastOpen = 99.4,
        lastHigh = 100.2,
        lastLow = 99.2,
        lastClose = 100.0,
        prevOpen = 99.0,
        prevHigh = 99.6,
        prevLow = 98.8,
        prevClose = 99.4,
        candles = emptyList()
    )

    @Test fun targetNeverFallsBelowOnePercent() {
        assertEquals(1.0, ForecastQualityPolicy.adaptiveTargetPct(0.45, listOf(0.3, 0.6, 0.8)), 0.0001)
    }

    @Test fun targetRisesWithMultifyMfeAverage() {
        assertEquals(1.35, ForecastQualityPolicy.adaptiveTargetPct(1.35, listOf(0.8, 1.0, 1.1)), 0.0001)
    }

    @Test fun strongTrendCandidatePassesOnePercentGate() {
        val e = StrategyEvaluation(
            regime = "TREND",
            directionalScore = 0.92,
            confidence = 0.92,
            strategy = "VWAP trend continuation",
            reason = "test",
            votes = emptyList(),
            features = features(atr = 0.40)
        )
        assertTrue(ForecastQualityPolicy.evaluate(e, 1.0).eligible)
    }

    @Test fun mixedRegimeIsRejectedEvenWithHighConfidence() {
        val e = StrategyEvaluation(
            regime = "MIXED",
            directionalScore = 0.95,
            confidence = 0.95,
            strategy = "VWAP trend continuation",
            reason = "test",
            votes = emptyList(),
            features = features()
        )
        assertFalse(ForecastQualityPolicy.evaluate(e, 1.0).eligible)
    }

    @Test fun lowVolatilityCannotPretendToSupportOnePercentTarget() {
        val e = StrategyEvaluation(
            regime = "TREND",
            directionalScore = 0.92,
            confidence = 0.92,
            strategy = "VWAP trend continuation",
            reason = "test",
            votes = emptyList(),
            features = features(atr = 0.10)
        )
        assertFalse(ForecastQualityPolicy.evaluate(e, 1.0).eligible)
    }
}
