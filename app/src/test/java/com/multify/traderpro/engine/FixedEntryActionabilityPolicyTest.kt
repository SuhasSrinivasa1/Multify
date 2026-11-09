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

    @Test fun holdsVeryExtendedNearHighFirstPrimaryMove() {
        // Example of the observed risk structure, not a reconstructed execution.
        val quote = QuotePayload(lastPrice=472.60, high=473.50, dayChangePct=8.0,
            bidPrice=472.2, offerPrice=472.5, volume=28_000L)
        val result = p.evaluate(timestamp(9, 22),quote,461.75,1.1161043)
        assertFalse(result.actionable)
        assertEquals("RESEARCH_ONLY",result.status)
        assertTrue(result.reasons.contains("EXTENDED_SINCE_FIRST_PRIMARY_AND_NEAR_DAY_HIGH"))
        assertTrue(result.firstPrimaryRunupPct!! > 2.0)
    }

    @Test fun winnersAreNotDisqualifiedSolelyForDayRiseOrNewHigh() {
        val quote = QuotePayload(lastPrice=500.0, high=500.3,
            dayChangePct=10.0, bidPrice=499.9, offerPrice=500.0, volume=100_000L)
        val v=p.evaluate(timestamp(11, 15),quote,498.0,1.1161043)
        assertTrue(v.actionable)
        assertEquals("CAUTION",v.status)
    }

    @Test fun anExtremeRiseNearHighWithThinVolumeIsHeld() {
        val quote=QuotePayload(lastPrice=300.0, high=300.20, dayChangePct=14.5,
            bidPrice=299.8, offerPrice=300.0, volume=850L)
        val v=p.evaluate(timestamp(13, 5),quote,null,1.1161043)
        assertFalse(v.actionable)
        assertTrue(v.reasons.contains("EXTREME_DAY_RISE_NEAR_HIGH_WITH_LIQUIDITY_WARNING"))
    }

    @Test fun lateCallIsResearchOnlyAndStillRecorded() {
        val quote=QuotePayload(lastPrice=400.0, high=420.0, dayChangePct=1.0)
        assertTrue(p.evaluate(timestamp(15,14),quote,null,1.1161043).actionable)
        assertFalse(p.evaluate(timestamp(15,15),quote,null,1.1161043).actionable)
        assertFalse(p.evaluate(timestamp(15,30),quote,null,1.1161043).actionable)
        assertFalse(p.evaluate(timestamp(9,0),quote,null,1.1161043).actionable)
    }

    @Test fun importedSameDayCallCannotBeTradedEvenOnFreshQuote() {
        val v=p.evaluate(timestamp(11,30),QuotePayload(lastPrice=250.0),
            null,1.1161043,priorSessionDuplicate=true)
        assertFalse(v.actionable)
        assertTrue(v.reasons.contains("PREVIOUSLY_RECOMMENDED_THIS_SESSION"))
    }

    @Test fun timestampVerifiedCurrentDayHighCanReplaceUnavailableBrokerHigh() {
        val quote = QuotePayload(lastPrice=472.60, high=null,
            dayChangePct=8.0, bidPrice=472.2, offerPrice=472.5, volume=28_000L)
        val v=p.evaluate(timestamp(9,40), quote, 461.75,1.1161043,
            verifiedSameDayCandleHigh=473.50)
        assertFalse(v.actionable)
        assertEquals("TIMESTAMP_VERIFIED_TODAY_15M_CANDLES",v.highEvidenceSource)
    }

    @Test fun multiDayHighIsNotAcceptedWithoutProofOfToday() {
        val quote=QuotePayload(lastPrice=472.60,high=null,dayChangePct=8.0,
            bidPrice=472.2,offerPrice=472.5,volume=28_000L)
        val v=p.evaluate(timestamp(9,40),quote,461.75,1.1161043)
        assertTrue(v.actionable)
        assertEquals("UNAVAILABLE",v.highEvidenceSource)
    }

    @Test fun multiDayLateBreakoutConfluenceBlocksNotificationAndAutoBuyActionability() {
        val snapshot = FixedFeatureSnapshot(
            ltp=955.0, bid=954.8, ask=955.2, spreadBps=4.2,
            orderBookImbalance=0.26, dayChangePct=4.755,
            marketCap=null, range52Position=null,
            vwap=891.005, ema9=928.667, ema20=917.899, atr14=7.307,
            rsi14=74.777, bollWidth=0.0538, macdHistogram=-0.1648,
            trendSlopeAtr=0.908, rvol=6.05,
            donchianHigh=947.35, donchianLow=892.0,
            orb5High=840.35, orb5Low=819.8,
            orb15High=840.35, orb15Low=819.8,
            lastOpen=950.0, lastHigh=955.0, lastLow=950.0, lastClose=955.0,
            prevOpen=924.6, prevHigh=925.0, prevLow=924.6, prevClose=924.6,
            candles=emptyList(), sessionCandleCount=6, sessionHighDistancePct=0.0
        )
        val quote = QuotePayload(lastPrice=955.0, high=null, dayChangePct=4.755,
            bidPrice=954.8, offerPrice=955.2, volume=854L)
        val v = p.evaluate(timestamp(14,58), quote, 955.0, 1.1161043, snapshot=snapshot)
        assertFalse(v.actionable)
        assertEquals("RESEARCH_ONLY", v.status)
        assertTrue(v.reasons.contains("LATE_BREAKOUT_NEAR_MULTI_DAY_15M_HIGH"))
        assertEquals("UNAVAILABLE", v.highEvidenceSource) // no fabricated NSE day high
        assertTrue(v.ema20AtrExtension!! >= 2.0)
        assertTrue(v.recentImpulseAtr!! >= 1.2)
    }

    @Test fun strongContinuationAwayFromMultiDayHighIsNotBlockedByNewGuard() {
        val snapshot = FixedFeatureSnapshot(
            ltp=1201.0, bid=1200.8, ask=1201.2, spreadBps=3.3,
            orderBookImbalance=0.1, dayChangePct=2.36,
            marketCap=null, range52Position=null,
            vwap=1176.5, ema9=1188.0, ema20=1147.6, atr14=8.8,
            rsi14=89.9, bollWidth=0.05, macdHistogram=0.2,
            trendSlopeAtr=0.9, rvol=2.0,
            donchianHigh=1220.0, donchianLow=1150.0,
            orb5High=1180.0, orb5Low=1160.0,
            orb15High=1190.0, orb15Low=1160.0,
            lastOpen=1190.0, lastHigh=1201.0, lastLow=1189.0, lastClose=1201.0,
            prevOpen=1147.0, prevHigh=1148.0, prevLow=1146.0, prevClose=1147.8,
            candles=emptyList(), sessionCandleCount=6, sessionHighDistancePct=3.61
        )
        val quote = QuotePayload(lastPrice=1201.0, high=null, dayChangePct=2.36,
            bidPrice=1200.8, offerPrice=1201.2, volume=100_000L)
        val v = p.evaluate(timestamp(14,58), quote, 1201.0, 1.1161043, snapshot=snapshot)
        assertFalse(v.reasons.contains("LATE_BREAKOUT_NEAR_MULTI_DAY_15M_HIGH"))
    }

    @Test fun missingQuoteNeverFabricatesPeakInformation() {
        val v=p.evaluate(timestamp(11,30),null,null,1.1161043)
        assertTrue(v.actionable)
        assertNull(v.distanceFromQuoteHighPct)
        assertNull(v.firstPrimaryRunupPct)
        assertNull(v.vwapAtrExtension)
    }
}
