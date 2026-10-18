package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositiveCloseForecastPolicyTest {
    @Test fun onlyPositiveCloseIsLearningEligible() {
        assertTrue(PositiveCloseForecastPolicy.isPositiveClose(100.0, 100.01))
        assertFalse(PositiveCloseForecastPolicy.isPositiveClose(100.0, 100.0))
        assertFalse(PositiveCloseForecastPolicy.isPositiveClose(100.0, 99.99))
        assertTrue(PositiveCloseForecastPolicy.isPositiveResearchClose(0.01))
        assertFalse(PositiveCloseForecastPolicy.isPositiveResearchClose(0.0))
        assertFalse(PositiveCloseForecastPolicy.isPositiveResearchClose(-0.01))
        assertFalse(PositiveCloseForecastPolicy.isPositiveResearchClose(null))
    }
}
