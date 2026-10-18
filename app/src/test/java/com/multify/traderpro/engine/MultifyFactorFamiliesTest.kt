package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultifyFactorFamiliesTest {
    private fun evaluation(): StrategyEvaluation {
        val f = FeatureSnapshot(
            ltp = 101.0, bid = 100.95, ask = 101.05, spreadBps = 9.9, orderBookImbalance = 0.20,
            dayChangePct = 1.2, marketCap = 50_000_000_000.0, range52Position = 0.80, vwap = 100.0,
            ema9 = 100.8, ema20 = 100.2, ema50 = 99.4, ema9Slope = 0.12, ema20Slope = 0.08,
            vwapSlope = 0.06, macdHistogramSlope = 0.05, rsiSlope = 3.0, volumeAcceleration = 0.30,
            greenVolumeShare = 0.68, structureScore = 0.50, sessionHighDistancePct = 0.10,
            sessionLowDistancePct = 2.0, atr14 = 0.50, rsi14 = 63.0, bollWidth = 0.02,
            macdHistogram = 0.08, trendSlopeAtr = 0.18, rvol = 1.45, donchianHigh = 100.5,
            donchianLow = 97.0, orb5High = 100.4, orb5Low = 98.5, orb15High = 100.3, orb15Low = 98.0,
            lastOpen = 100.0, lastHigh = 101.2, lastLow = 99.9, lastClose = 101.0,
            prevOpen = 99.5, prevHigh = 100.4, prevLow = 99.2, prevClose = 100.0, candles = emptyList()
        )
        return StrategyEvaluation(
            regime = "TREND", directionalScore = 0.50, confidence = 0.71,
            strategy = "VWAP trend continuation", reason = "test",
            votes = listOf(
                StrategyVote("VWAP trend continuation", 0.5, "test"),
                StrategyVote("EMA 9/20 trend", 0.4, "test"),
                StrategyVote("Order-book imbalance", 0.2, "test")
            ),
            features = f
        )
    }

    @Test fun modelAlwaysContainsExactlyFortyFactorsAndThreeFamilies() {
        val model = MultifyFactorFamilies.evaluate(evaluation(), "BREADTH_BULL")
        assertEquals(40, model.factors.size)
        assertEquals(3, model.families.size)
    }

    @Test fun strongTrendProducesPositiveFamilyConsensus() {
        val model = MultifyFactorFamilies.evaluate(evaluation(), "BREADTH_BULL")
        assertTrue(model.consensusScore > 0.0)
        assertTrue(model.positiveFamilies >= 2)
        assertTrue(MultifyFactorFamilies.rankingBonus(model) > 0.0)
    }
}
