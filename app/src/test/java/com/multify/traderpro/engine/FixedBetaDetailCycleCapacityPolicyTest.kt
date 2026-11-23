package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedBetaDetailCycleCapacityPolicyTest {
    private val p = FixedBetaDetailCycleCapacityPolicy

    @Test fun fullFortyEightQuoteCycleAndReserveMustFitBothWindows() {
        assertTrue(p.hasImmediateHeadroomForFullDetailCycle(51, 51))
        assertTrue(p.hasImmediateHeadroomForFullDetailCycle(60, 51))
        assertFalse(p.hasImmediateHeadroomForFullDetailCycle(51, 50))
        assertFalse(p.hasImmediateHeadroomForFullDetailCycle(50, 51))
        assertFalse(p.hasImmediateHeadroomForFullDetailCycle(40, 80))
    }

    @Test fun negativeAndZeroHeadroomFailClosed() {
        assertFalse(p.hasImmediateHeadroomForFullDetailCycle(-1, 80))
        assertFalse(p.hasImmediateHeadroomForFullDetailCycle(80, -1))
        assertFalse(p.hasImmediateHeadroomForFullDetailCycle(0, 0))
    }

    @Test fun betaFortyPerMinuteBudgetCannotBeMistakenForFullDetailCycleCapacity() {
        assertEquals(48, p.REQUIRED_DETAIL_QUOTES)
        assertEquals(3, p.RESERVED_HEADROOM)
        // Even an otherwise empty Beta minute cannot carry 48 quotes plus reserve.
        assertFalse(p.hasImmediateHeadroomForFullDetailCycle(40, 40))
    }
}
