package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedMomentumConfirmationPolicyTest {
    private fun features(
        macd: Double? = 0.20,
        book: Double? = 0.10,
        rsiSlope: Double? = 1.0,
        rvol: Double? = 1.2,
        vwap: Double? = 99.0,
        lastOpen: Double? = 100.0,
        lastClose: Double? = 101.0,
        prevOpen: Double? = 101.0,
        prevClose: Double? = 100.0,
        prevHigh: Double? = 101.5,
        sessionCount: Int = 4
    ) = FixedFeatureSnapshot(
        ltp = 101.0, bid = 100.9, ask = 101.1, spreadBps = 19.8,
        orderBookImbalance = book, dayChangePct = 1.0, marketCap = null,
        range52Position = null, vwap = vwap, ema9 = 100.5, ema20 = 100.0,
        ema9Slope = .1, ema20Slope = .05, vwapSlope = .1,
        macdHistogramSlope = macd, rsiSlope = rsiSlope, volumeAcceleration = .1,
        greenVolumeShare = .6, structureScore = .5, sessionHighDistancePct = .5,
        sessionLowDistancePct = 2.0, atr14 = 1.0, rsi14 = 65.0, bollWidth = .02,
        macdHistogram = .2, trendSlopeAtr = .1, rvol = rvol, donchianHigh = 101.0,
        donchianLow = 98.0, orb5High = 100.5, orb5Low = 99.0,
        orb15High = 101.0, orb15Low = 98.0, lastOpen = lastOpen,
        lastHigh = 102.0, lastLow = 99.5, lastClose = lastClose,
        prevOpen = prevOpen, prevHigh = prevHigh, prevLow = 99.0,
        prevClose = prevClose, candles = emptyList(), sessionCandleCount = sessionCount
    )

    @Test fun threeFactorChallengerRequiresAllConfirmations() {
        assertTrue(FixedMomentumConfirmationPolicy.evaluate(features()).confirmed)
        assertFalse(FixedMomentumConfirmationPolicy.evaluate(features(macd = .149)).confirmed)
        assertFalse(FixedMomentumConfirmationPolicy.evaluate(features(book = 0.0)).confirmed)
        assertFalse(FixedMomentumConfirmationPolicy.evaluate(features(rsiSlope = 0.0)).confirmed)
    }

    @Test fun fiveMinuteEntryNeeds15mTrendPullbackReclaimVwapVolumeAndMomentum() {
        assertTrue(FixedFiveMinuteEntryPolicy.evaluate(features(), fifteenMinutePrimary = true).confirmed)
        assertFalse(FixedFiveMinuteEntryPolicy.evaluate(features(), fifteenMinutePrimary = false).confirmed)
        assertFalse(FixedFiveMinuteEntryPolicy.evaluate(features(lastClose = 100.5), true).confirmed)
        assertFalse(FixedFiveMinuteEntryPolicy.evaluate(features(vwap = null), true).confirmed)
        assertFalse(FixedFiveMinuteEntryPolicy.evaluate(features(rvol = .8), true).confirmed)
    }
}
