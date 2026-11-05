package com.multify.traderpro.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NseEquitySessionCalendarTest {
    private val india = ZoneId.of("Asia/Kolkata")
    private fun ist(date: String, time: String) =
        LocalDate.parse(date).atTime(LocalTime.parse(time)).atZone(india)

    @Test fun startClockRunsNineAmOnWeekdaysButNotOtherSundays() {
        val friday = ist("2026-11-06", "08:59")
        assertTrue(NseEquitySessionCalendar.nextStartupAfter(friday) ==
            ist("2026-11-06", "09:00"))
        assertTrue(NseEquitySessionCalendar.nextStartupAfter(ist("2026-11-06", "09:00")) ==
            ist("2026-11-08", "14:00"))
        assertTrue(NseEquitySessionCalendar.nextStartupAfter(ist("2026-11-08", "14:00")) ==
            ist("2026-11-09", "09:00"))
        assertTrue(NseEquitySessionCalendar.startupAt(LocalDate.parse("2026-11-15")) == null)
        assertTrue(NseEquitySessionCalendar.startupAt(LocalDate.parse("2026-11-07")) == null)
        assertTrue(NseEquitySessionCalendar.startupAt(LocalDate.parse("2026-11-08")) ==
            ist("2026-11-08", "14:00"))
    }

    @Test fun preopenIsObservationOnlyAndStartsAtCorrectExchangeTime() {
        assertTrue(NseEquitySessionCalendar.isWarmupWindow(ist("2026-11-08", "14:00")))
        assertFalse(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-08", "14:00")))
        assertFalse(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-08", "14:04")))
        assertTrue(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-08", "14:05")))
        assertTrue(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-08", "14:19")))
        assertFalse(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-08", "14:20")))
        assertTrue(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-09", "09:00")))
        assertTrue(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-09", "09:14")))
        assertFalse(NseEquitySessionCalendar.isPreOpenAuctionWindow(ist("2026-11-09", "09:15")))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-09", "09:00")))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-08", "14:05")))
    }

    @Test fun muhuratSundayWarmsUpBeforeContinuousTrading() {
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-08", "14:19")))
        assertTrue(NseEquitySessionCalendar.isWarmupWindow(ist("2026-11-08", "14:05")))
        assertTrue(NseEquitySessionCalendar.isWarmupWindow(ist("2026-11-08", "14:19")))
        assertFalse(NseEquitySessionCalendar.isWarmupWindow(ist("2026-11-08", "14:20")))
        assertTrue(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-08", "14:20")))
        assertTrue(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-08", "15:14")))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-08", "15:15")))
    }

    @Test fun muhuratEntryCutoffProtectsCasSecurities() {
        assertTrue(NseEquitySessionCalendar.isFixedNewEntryWindow(ist("2026-11-08", "14:59")))
        assertFalse(NseEquitySessionCalendar.isFixedNewEntryWindow(ist("2026-11-08", "15:00")))
        assertTrue(NseEquitySessionCalendar.isOpen(ist("2026-11-08", "15:29")))
        assertFalse(NseEquitySessionCalendar.isOpen(ist("2026-11-08", "15:30")))
        assertTrue(NseEquitySessionCalendar.mayPrepareNextFixedSweep(ist("2026-11-08", "14:40")))
        assertFalse(NseEquitySessionCalendar.mayPrepareNextFixedSweep(ist("2026-11-08", "15:00")))
        assertTrue(FixedBuyAvailabilityPolicy.nseRegularSession(ist("2026-11-08", "14:20").toInstant().toEpochMilli()))
        assertFalse(FixedBuyAvailabilityPolicy.fixedNewEntryWindow(ist("2026-11-08", "15:00").toInstant().toEpochMilli()))
    }

    @Test fun ordinarySundayAndSaturdayRemainClosed() {
        assertFalse(NseEquitySessionCalendar.isOpen(ist("2026-11-15", "14:20")))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-15", "14:20")))
        assertFalse(NseEquitySessionCalendar.isOpen(ist("2026-11-07", "09:15")))
    }

    @Test fun originalWeekdayWindowIsPreserved() {
        assertTrue(NseEquitySessionCalendar.isWarmupWindow(ist("2026-11-09", "09:00")))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-09", "09:14")))
        assertTrue(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-09", "09:15")))
        assertTrue(NseEquitySessionCalendar.isFixedNewEntryWindow(ist("2026-11-09", "15:14")))
        assertFalse(NseEquitySessionCalendar.isFixedNewEntryWindow(ist("2026-11-09", "15:15")))
        assertFalse(NseEquitySessionCalendar.isFixedScanWindow(ist("2026-11-09", "15:30")))
    }
    @Test fun fixedActionabilityDoesNotMistakeSpecialSundayForClosedWeekend() {
        val duringSession = FixedEntryActionabilityPolicy.evaluate(
            ist("2026-11-08", "14:30").toInstant().toEpochMilli(),
            quote = null, referencePrice = null, fixedTargetPct = 1.1161043
        )
        assertFalse(duringSession.reasons.contains("OUTSIDE_NSE_CASH_SESSION"))
        val afterEntryCutoff = FixedEntryActionabilityPolicy.evaluate(
            ist("2026-11-08", "15:00").toInstant().toEpochMilli(),
            quote = null, referencePrice = null, fixedTargetPct = 1.1161043
        )
        assertTrue(afterEntryCutoff.reasons.contains("LESS_THAN_15_MINUTES_TO_CLOSE"))
    }


}
