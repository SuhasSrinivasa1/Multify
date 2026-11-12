package com.multify.traderpro.data.network

import org.junit.Assert.*
import org.junit.Test

class GrowwHistoricalThrottlePolicyTest {
    @Test fun enforcedLimitsAreConservative() {
        assertEquals(2, GrowwHistoricalThrottlePolicy.APP_PER_SECOND)
        assertEquals(90, GrowwHistoricalThrottlePolicy.APP_PER_MINUTE)
        assertTrue(GrowwHistoricalThrottlePolicy.withinBudget(1, 89))
        assertFalse(GrowwHistoricalThrottlePolicy.withinBudget(2, 0))
        assertFalse(GrowwHistoricalThrottlePolicy.withinBudget(0, 90))
    }
}
