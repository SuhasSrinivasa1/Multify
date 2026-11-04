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

    @Test fun missingQuoteNeverFabricatesPeakInformation() {
        val v=p.evaluate(timestamp(11,30),null,null,1.1161043)
        assertTrue(v.actionable)
        assertNull(v.distanceFromQuoteHighPct)
        assertNull(v.firstPrimaryRunupPct)
        assertNull(v.vwapAtrExtension)
    }
}
