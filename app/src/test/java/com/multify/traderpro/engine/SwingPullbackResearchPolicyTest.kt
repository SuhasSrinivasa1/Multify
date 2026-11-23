package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class SwingPullbackResearchPolicyTest {
    @Test fun suggestedNextSessionPriceIsBelowEodCloseAndNeverAnOrder() {
        val zone=SwingPullbackResearchPolicy.evaluate(500.0,480.0,460.0)!!
        assertEquals(480.0,zone.lower,0.001)
        assertEquals(484.8,zone.upper,0.001)
        assertEquals(482.4,zone.preferred,0.001)
        assertTrue(zone.preferred < 500.0)
        assertFalse(zone.distantPullback)
    }
    @Test fun extendedSwingShowsDistantPullbackNotChase() {
        val zone=SwingPullbackResearchPolicy.evaluate(600.0,500.0,470.0)!!
        assertEquals(502.5,zone.preferred,0.001)
        assertTrue(zone.distantPullback)
        assertTrue(zone.upper < 600.0)
    }
    @Test fun invalidOrNonTrendingDataGivesNoInventedEntry() {
        assertNull(SwingPullbackResearchPolicy.evaluate(Double.NaN,100.0,90.0))
        assertNull(SwingPullbackResearchPolicy.evaluate(98.0,100.0,90.0))
        assertNull(SwingPullbackResearchPolicy.evaluate(110.0,100.0,101.0))
    }
}
