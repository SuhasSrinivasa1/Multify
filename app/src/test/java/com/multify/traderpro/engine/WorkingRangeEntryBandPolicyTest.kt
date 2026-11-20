package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class WorkingRangeEntryBandPolicyTest {
    private fun assess(
        drift: Double = 0.20, firstAge: Long = 45_000L, confirmAge: Long = 10_000L,
        vwap: Double? = 3.0, spread: Double? = 20.0, fresh: Double = 100.0
    ) = WorkingRangeEntryBandPolicy.assess(
        firstQualifiedAtMs = 1_000_000L,
        confirmedAtMs = 1_000_000L + firstAge - confirmAge,
        nowMs = 1_000_000L + firstAge,
        driftAtConfirmationPct = drift,
        confirmationPrice = 100.0,
        freshPrice = fresh,
        vwapExtensionAtConfirmationAtr = vwap,
        targetRequirementAtr = 1.0,
        targetPct = 1.0,
        freshSpreadBps = spread
    )

    @Test fun asianHotnrPeakChaseIsRejectedByMultipleIndependentGuards() {
        val r = WorkingRangeEntryBandPolicy.assess(
            firstQualifiedAtMs = 1_795_161_627_261L,
            confirmedAtMs = 1_795_161_826_730L,
            nowMs = 1_795_161_864_280L,
            driftAtConfirmationPct = 1.5552099533,
            confirmationPrice = 326.50,
            freshPrice = 326.50,
            vwapExtensionAtConfirmationAtr = 4.6011652,
            targetRequirementAtr = 1.4330654,
            targetPct = 1.1161043,
            freshSpreadBps = 38.2848
        )
        assertFalse(r.eligible)
        assertEquals("WORKING_RANGE_FIRST_QUALIFIED_SIGNAL_STALE", r.reason)
    }

    @Test fun upwardChaseOutsideHalfPercentBandIsRejected() {
        val r = assess(drift = 0.60)
        assertEquals("WORKING_RANGE_PRICE_ABOVE_ENTRY_BAND", r.reason)
    }

    @Test fun downwardRetracementOutsideRangeIsRejected() {
        val r = assess(drift = -0.80)
        assertEquals("WORKING_RANGE_MOMENTUM_LOST_BELOW_ENTRY_BAND", r.reason)
    }

    @Test fun confirmationOlderThanFortyFiveSecondsIsRejected() {
        val r = assess(firstAge = 70_000L, confirmAge = 46_000L)
        assertEquals("WORKING_RANGE_CONFIRMATION_TO_BUY_OVER_45S", r.reason)
    }

    @Test fun excessiveVwapExtensionIsRejected() {
        val r = assess(vwap = 3.51)
        assertEquals("WORKING_RANGE_VWAP_EXTENSION_ABOVE_3_50_ATR", r.reason)
    }

    @Test fun wideOrMissingFreshSpreadIsRejected() {
        assertEquals("WORKING_RANGE_FRESH_SPREAD_ABOVE_35_BPS",
            assess(spread = 35.1).reason)
        assertEquals("WORKING_RANGE_FRESH_SPREAD_UNAVAILABLE",
            assess(spread = null).reason)
    }

    @Test fun boundedFreshEntryIsAllowed() {
        val r = assess(drift = 0.30, firstAge = 45_000L, confirmAge = 10_000L,
            vwap = 3.2, spread = 24.0, fresh = 100.05)
        assertTrue(r.eligible)
        assertEquals("WORKING_RANGE_ENTRY_INSIDE_BOUNDED_RANGE", r.reason)
    }
}
