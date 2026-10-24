package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastAdaptiveSelectionPolicyTest {

    @Test
    fun economicsIncludesChargesAndSlippage() {
        val x = ForecastAdaptiveSelectionPolicy.economics(
            entryPrice = 500.0,
            targetPct = 1.0,
            budgetRupees = 200_000L,
            spreadBps = 10.0,
            atrPct = 0.8
        )
        assertTrue(x.quantity > 0)
        assertTrue(x.estimatedChargesRupees > 0.0)
        assertTrue(x.estimatedSlippageRupees > 0.0)
        assertTrue(x.grossProfitRupees > x.netProfitRupees)
        assertTrue(x.netProfitRupees > 0.0)
        assertTrue(x.profitableAfterCosts)
    }

    @Test
    fun diagnosticsUsesAllOutcomesAndChronologicalHoldout() {
        val rows = (1..12).map { i ->
            ForecastHistoricalOutcome(
                date = "2026-10-${i.toString().padStart(2, '0')}",
                symbol = "S${i % 4}",
                strategy = "Trend",
                regime = "TREND",
                marketRegime = "BREADTH_NEUTRAL",
                maxFavourablePct = if (i <= 8) 1.4 else 0.4,
                maxAdversePct = if (i <= 8) 0.3 else 1.1,
                closePct = if (i <= 8) 0.7 else -0.5,
                targetHit = i <= 8
            )
        }
        val d = ForecastAdaptiveSelectionPolicy.diagnostics("Trend", "TREND", "BREADTH_NEUTRAL", rows)
        assertEquals(12, d.sampleCount)
        assertEquals(8, d.positiveCount)
        assertEquals(4, d.adverseCount)
        assertTrue(d.validationSamples > 0)
        assertTrue(d.shadowPenalty < 0.0)
        assertTrue(d.confidence > 0.0)
    }

    @Test
    fun negativePenaltyAndEvidenceBonusAreSeparate() {
        val weak = ForecastEvidenceDiagnostics(
            sampleCount = 10,
            distinctDays = 4,
            distinctSymbols = 4,
            positiveRatePct = 40.0,
            confidence = 0.9,
            shadowPenalty = -0.08
        )
        assertEquals(0.0, ForecastAdaptiveSelectionPolicy.positiveConfidenceBonus(weak), 1e-9)
        assertTrue(weak.shadowPenalty < 0.0)
    }

    @Test
    fun shadowTargetRespectsExistingExpansionSafeguard() {
        val d = ForecastEvidenceDiagnostics(
            sampleCount = 10,
            distinctDays = 4,
            distinctSymbols = 4,
            medianMfePct = 3.0,
            medianMaePct = 0.4,
            confidence = 0.9
        )
        val target = ForecastAdaptiveSelectionPolicy.shadowTargetPct(1.2, d)
        assertTrue(target >= ForecastQualityPolicy.MIN_TARGET_PCT)
        assertTrue(target <= 1.2 + ForecastTargetExpansionPolicy.MAX_UPLIFT_PCT_POINTS + 1e-9)
    }

    @Test
    fun shadowFamilyWeightsRemainNeutralUntilMature() {
        val rows = listOf(
            ForecastFamilyOutcome("2026-10-01", "A", "BREADTH_BULL", 0.8, 0.2, 0.1, true),
            ForecastFamilyOutcome("2026-10-02", "B", "BREADTH_BULL", 0.7, 0.2, 0.2, false)
        )
        val weights = ForecastAdaptiveSelectionPolicy.shadowFamilyWeights("BREADTH_BULL", rows)
        assertEquals(1.0, weights.getValue(MultifyFactorFamilies.MOMENTUM_TREND), 1e-9)
    }

    @Test
    fun stricterFilterIsShadowOnlyPolicyDecision() {
        val f = FeatureSnapshot(
            ltp = 500.0, bid = 499.9, ask = 500.1, spreadBps = 4.0,
            orderBookImbalance = 0.1, dayChangePct = 1.0, marketCap = 1e10, range52Position = 0.7,
            vwap = 498.0, ema9 = 499.0, ema20 = 497.0, ema50 = 490.0,
            ema9Slope = 0.1, ema20Slope = 0.08, vwapSlope = 0.05, macdHistogramSlope = 0.05,
            rsiSlope = 2.0, volumeAcceleration = 0.3, greenVolumeShare = 0.65, structureScore = 0.4,
            sessionHighDistancePct = 0.2, sessionLowDistancePct = 1.5, atr14 = 5.0, rsi14 = 62.0,
            bollWidth = 0.02, macdHistogram = 1.0, trendSlopeAtr = 0.2, rvol = 1.2,
            donchianHigh = 499.0, donchianLow = 485.0, orb5High = 498.0, orb5Low = 490.0,
            orb15High = 497.0, orb15Low = 489.0, lastOpen = 496.0, lastHigh = 501.0,
            lastLow = 495.0, lastClose = 500.0, prevOpen = 494.0, prevHigh = 498.0,
            prevLow = 493.0, prevClose = 496.0,
            candles = (1..20).map { Candle(it.toLong(), 495.0, 501.0, 494.0, 500.0, 1000.0) }
        )
        val e = StrategyEvaluation("TREND", 0.5, 0.8, "Trend", "ok", emptyList(), f)
        val review = ForecastAdaptiveSelectionPolicy.shadowFilterReview(e, 1.0)
        assertTrue(review.passes)
        assertFalse(review.failedRules.isNotEmpty())
    }
}

