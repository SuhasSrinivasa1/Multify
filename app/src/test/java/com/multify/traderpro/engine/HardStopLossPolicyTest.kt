package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HardStopLossPolicyTest {
    @Test fun stopIsFivePercentBelowActualFill() {
        assertEquals(95.0, HardStopLossPolicy.stopPrice(100.0), 0.0001)
        assertEquals(237.5, HardStopLossPolicy.stopPrice(250.0), 0.0001)
    }

    @Test fun breachOccursAtOrBelowFivePercentLoss() {
        assertFalse(HardStopLossPolicy.breached(100.0, 95.01))
        assertTrue(HardStopLossPolicy.breached(100.0, 95.0))
        assertTrue(HardStopLossPolicy.breached(100.0, 94.0))
    }
}
