package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedEntrySafetyOverlayTest {
    private fun features(lastClose: Double = 102.10, rsi: Double = 78.0) = FixedFeatureSnapshot(
        ltp = 102.60, bid = 102.59, ask = 102.61, spreadBps = 2.0,
        orderBookImbalance = 0.2, dayChangePct = 1.5, marketCap = null, range52Position = 0.7,
        vwap = 100.0, ema9 = 102.0, ema20 = 101.0, ema9Slope = 0.2, vwapSlope = 0.1,
        structureScore = 0.5, sessionHighDistancePct = 0.05, atr14 = 1.0, rsi14 = rsi,
        bollWidth = 0.02, macdHistogram = 0.1, trendSlopeAtr = 0.2, rvol = 1.5,
        donchianHigh = 103.0, donchianLow = 99.0, orb5High = 102.0, orb5Low = 100.0,
        orb15High = 102.0, orb15Low = 99.0, lastOpen = 101.8, lastHigh = 103.0,
        lastLow = 101.50, lastClose = lastClose, prevOpen = 101.4, prevHigh = 102.0,
        prevLow = 101.4, prevClose = 101.5, candles = emptyList(), sessionCandleCount = 12
    )
    @Test fun confirmedHigherLowRebreakoutMayPassModestExtension() {
        val result = FixedEntrySafetyOverlay.assess(features())
        assertTrue(result.confirmedMultiPeakRebreakout)
        assertTrue(result.eligible)
    }
    @Test fun failedBreakoutRemainsRejected() {
        val result = FixedEntrySafetyOverlay.assess(features(lastClose = 101.8))
        assertFalse(result.eligible)
        assertTrue(result.reasons.any { it.contains("failed breakout") })
    }
    @Test fun extremeRsiStillRejectsEvenARebreakout() {
        val result = FixedEntrySafetyOverlay.assess(features(rsi = 82.0))
        assertFalse(result.eligible)
    }
}
