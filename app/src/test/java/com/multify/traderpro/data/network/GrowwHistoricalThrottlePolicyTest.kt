package com.multify.traderpro.data.network

import org.junit.Assert.*
import org.junit.Test

class GrowwHistoricalThrottlePolicyTest {
    @Test fun enforcedLimitsAreConservative() {
        assertEquals(1, GrowwHistoricalThrottlePolicy.APP_PER_SECOND)
        assertEquals(45, GrowwHistoricalThrottlePolicy.APP_PER_MINUTE)
        assertTrue(GrowwHistoricalThrottlePolicy.withinBudget(0, 44))
        assertFalse(GrowwHistoricalThrottlePolicy.withinBudget(1, 0))
        assertFalse(GrowwHistoricalThrottlePolicy.withinBudget(0, 45))
    }
}
