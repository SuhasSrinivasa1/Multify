package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class WorkingRangeEntryMomentumPolicyTest {
    @Test fun welspunReversalIsRejectedEvenIfPriceIsCheaper() {
        val at = 1_000_000L
        assertEquals("WORKING_RANGE_MOMENTUM_LOST_PRICE_RETRACE",
            WorkingRangeEntryMomentumPolicy.rejectionReason(271.20, 267.12, at, at + 65_000))
    }

    @Test fun lateConfirmationCannotBeRevived() {
        assertEquals("WORKING_RANGE_SIGNAL_STALE",
            WorkingRangeEntryMomentumPolicy.rejectionReason(
                271.20, 271.00, 1_000L, 123_000L))
    }

    @Test fun promptlyConfirmedNearPriceIsAllowed() {
        assertNull(WorkingRangeEntryMomentumPolicy.rejectionReason(
            271.20, 270.90, 1_000L, 21_000L))
    }

    @Test fun invalidQuotesFailClosed() {
        assertNotNull(WorkingRangeEntryMomentumPolicy.rejectionReason(
            271.20, Double.NaN, 1_000L, 21_000L))
    }
}

class WorkingRangeExitPolicyTest {
    @Test fun stopAndTargetAreOnlyStandardExits() {
        assertEquals(264.4488, WorkingRangeExitPolicy.stopPrice(267.12), 0.00001)
        assertNull(WorkingRangeExitPolicy.exitReason(267.12, 270.10, 268.80))
        assertEquals(WorkingRangeExitPolicy.STOP_REASON,
            WorkingRangeExitPolicy.exitReason(267.12, 270.10, 263.90))
        assertEquals(WorkingRangeExitPolicy.TARGET_REASON,
            WorkingRangeExitPolicy.exitReason(267.12, 270.10, 270.11))
    }

    @Test fun workingRangeIdentityDoesNotCaptureFixed() {
        assertTrue(WorkingRangeExitPolicy.owns("WRM123", "WORKING_RANGE_LONG"))
        assertFalse(WorkingRangeExitPolicy.owns("FXM123", "TREND"))
    }
}
