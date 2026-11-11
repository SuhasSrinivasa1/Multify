package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class FixedEntryActionabilityPolicyTest {
    private val p = FixedEntryActionabilityPolicy
    private val ist = ZoneId.of("Asia/Kolkata")
    private fun timestamp(hour: Int, minute: Int): Long =
        LocalDate.of(2026, 11, 4).atTime(LocalTime.of(hour, minute))
            .atZone(ist).toInstant().toEpochMilli()

    private fun safeSnapshot(atMs: Long, price: Double, sessionHighDistancePct: Double? = null) =
        FixedFeatureSnapshot(
            ltp = price, bid = price - .1, ask = price + .1, spreadBps = 20.0,
            orderBookImbalance = .1, dayChangePct = 1.0, marketCap = null,
            range52Position = null, vwap = price - .5, ema9 = price - .2,
            ema20 = price - 1.0, ema9Slope = .1, ema20Slope = .05,
            vwapSlope = .1, macdHistogramSlope = .2, rsiSlope = 1.0,
            volumeAcceleration = .1, greenVolumeShare = .6, structureScore = .5,
            sessionHighDistancePct = sessionHighDistancePct, sessionLowDistancePct = 1.0,
            atr14 = 1.0, rsi14 = 60.0, bollWidth = .02, macdHistogram = .2,
            trendSlopeAtr = .1, rvol = 1.2, donchianHigh = price + 1.0,
            donchianLow = price - 2.0, orb5High = null, orb5Low = null,
            orb15High = price + .2, orb15Low = price - 1.0,
            lastOpen = price - .2, lastHigh = price + .2, lastLow = price - .5,
            lastClose = price, prevOpen = price - .3, prevHigh = price,
            prevLow = price - .8, prevClose = price - .2, candles = emptyList(),
            sessionCandleCount = 4, latestCompletedCandleAtMs = atMs - 60_000L
        )

    @Test fun holdsVeryExtendedNearHighFirstPrimaryMove() {
        // Example of the observed risk structure, not a reconstructed execution.
        val quote = QuotePayload(lastPrice=472.60, high=473.50, dayChangePct=8.0,
            bidPrice=472.2, offerPrice=472.5, volume=28_000L)
        val at = timestamp(9, 22)
        val result = p.evaluate(at,quote,461.75,1.1161043,
            snapshot = safeSnapshot(at, 472.60))
        assertFalse(result.actionable)
        assertEquals("RESEARCH_ONLY",result.status)
        assertTrue(result.reasons.contains("EXTENDED_SINCE_FIRST_PRIMARY_AND_NEAR_DAY_HIGH"))
        assertTrue(result.firstPrimaryRunupPct!! > 2.0)
    }

    @Test fun winnersAreNotDisqualifiedSolelyForDayRiseOrNewHigh() {
        val quote = QuotePayload(lastPrice=500.0, high=500.3,
            dayChangePct=10.0, bidPrice=499.9, offerPrice=500.0, volume=100_000L)
        val at = timestamp(11, 15)
        val v=p.evaluate(at,quote,498.0,1.1161043,
            snapshot = safeSnapshot(at, 500.0))
        assertTrue(v.actionable)
        assertEquals("CAUTION",v.status)
    }

    @Test fun anExtremeRiseNearHighWithThinVolumeIsHeld() {
        val quote=QuotePayload(lastPrice=300.0, high=300.20, dayChangePct=14.5,
            bidPrice=299.8, offerPrice=300.0, volume=850L)
        val at = timestamp(13, 5)
        val v=p.evaluate(at,quote,null,1.1161043,
            snapshot = safeSnapshot(at, 300.0))
        assertFalse(v.actionable)
        assertTrue(v.reasons.contains("EXTREME_DAY_RISE_NEAR_HIGH_WITH_LIQUIDITY_WARNING"))
    }

    @Test fun lateCallIsResearchOnlyAndStillRecorded() {
        val quote=QuotePayload(lastPrice=400.0, high=420.0, dayChangePct=1.0)
        val at = timestamp(15,14)
        assertTrue(p.evaluate(at,quote,null,1.1161043,
            snapshot = safeSnapshot(at, 400.0)).actionable)
        assertFalse(p.evaluate(timestamp(15,15),quote,null,1.1161043,
            snapshot = safeSnapshot(timestamp(15,15), 400.0)).actionable)
        assertFalse(p.evaluate(timestamp(15,30),quote,null,1.1161043,
            snapshot = safeSnapshot(timestamp(15,30), 400.0)).actionable)
        assertFalse(p.evaluate(timestamp(9,0),quote,null,1.1161043,
            snapshot = safeSnapshot(timestamp(9,0), 400.0)).actionable)
    }

    @Test fun importedSameDayCallCannotBeTradedEvenOnFreshQuote() {
        val at = timestamp(11,30)
        val v=p.evaluate(at,QuotePayload(lastPrice=250.0),
            null,1.1161043,priorSessionDuplicate=true, snapshot = safeSnapshot(at, 250.0))
        assertFalse(v.actionable)
        assertTrue(v.reasons.contains("PREVIOUSLY_RECOMMENDED_THIS_SESSION"))
    }

    @Test fun timestampVerifiedCurrentDayHighCanReplaceUnavailableBrokerHigh() {
        val quote = QuotePayload(lastPrice=472.60, high=null,
            dayChangePct=8.0, bidPrice=472.2, offerPrice=472.5, volume=28_000L)
        val at = timestamp(9,40)
        val v=p.evaluate(at, quote, 461.75,1.1161043,
            snapshot = safeSnapshot(at, 472.60),
            verifiedSameDayCandleHigh=473.50)
        assertFalse(v.actionable)
        assertEquals("TIMESTAMP_VERIFIED_TODAY_15M_CANDLES",v.highEvidenceSource)
    }

    @Test fun multiDayHighIsNotAcceptedWithoutProofOfToday() {
        val quote=QuotePayload(lastPrice=472.60,high=null,dayChangePct=8.0,
            bidPrice=472.2,offerPrice=472.5,volume=28_000L)
        val at = timestamp(9,40)
        val v=p.evaluate(at,quote,461.75,1.1161043,
            snapshot = safeSnapshot(at, 472.60))
        assertTrue(v.actionable)
        assertEquals("UNAVAILABLE",v.highEvidenceSource)
    }

    @Test fun missingQuoteNeverFabricatesPeakInformation() {
        val v=p.evaluate(timestamp(11,30),null,null,1.1161043)
        assertFalse(v.actionable)
        assertTrue(v.reasons.contains("VERIFIED_CURRENT_SESSION_FEATURES_UNAVAILABLE"))
        assertNull(v.distanceFromQuoteHighPct)
        assertNull(v.firstPrimaryRunupPct)
        assertNull(v.vwapAtrExtension)
    }
}
