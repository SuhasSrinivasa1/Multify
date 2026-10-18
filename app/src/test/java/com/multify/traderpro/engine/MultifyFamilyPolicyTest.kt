package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultifyFamilyPolicyTest {
    private fun features() = FeatureSnapshot(
        ltp = 100.0,
        bid = 99.95,
        ask = 100.05,
        spreadBps = 10.0,
        orderBookImbalance = 0.25,
        dayChangePct = 1.1,
        marketCap = 25000.0,
        range52Position = 0.72,
        vwap = 99.3,
        ema9 = 99.8,
        ema20 = 99.2,
        ema50 = 98.0,
        ema9Slope = 0.12,
        ema20Slope = 0.07,
        vwapSlope = 0.05,
        macdHistogramSlope = 0.04,
        rsiSlope = 1.5,
        volumeAcceleration = 0.30,
        greenVolumeShare = 0.64,
        structureScore = 0.50,
        sessionHighDistancePct = 0.10,
        sessionLowDistancePct = 2.1,
        atr14 = 0.45,
        rsi14 = 62.0,
        bollWidth = 0.02,
        macdHistogram = 0.10,
        trendSlopeAtr = 0.22,
        rvol = 1.35,
        donchianHigh = 99.7,
        donchianLow = 96.0,
        orb5High = 99.5,
        orb5Low = 97.0,
        orb15High = 99.4,
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

    @Test fun exactlyFortyPointersAreFixed() {
        assertEquals(40, MultifyFamilyPolicy.pointerNames.size)
        assertEquals(40, MultifyFamilyPolicy.pointerNames.distinct().size)
        assertEquals(40, MultifyFamilyPolicy.featureVector(features()).size)
    }

    @Test fun exactlyThreeFamiliesAreFixed() {
        assertEquals(3, MultifyFamilyPolicy.families.size)
        assertEquals(3, MultifyFamilyPolicy.families.distinct().size)
    }

    @Test fun classifierAlwaysChoosesOneOfThreeFamilies() {
        val decision = MultifyFamilyPolicy.classify(features(), "BREADTH_BULL")
        assertTrue(decision.family in MultifyFamilyPolicy.families)
        assertEquals(3, decision.scores.size)
        assertEquals(40, decision.pointers.size)
    }
}
