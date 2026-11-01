package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ForecastNewsPatternPolicyTest {
    @Test fun fortyFeaturesAndConservativeUnknowns() {
        val r = ForecastNewsPatternPolicy.assess("Major contract awarded", "₹120 crore order awarded", 2, 1, 1, 1, true)
        assertEquals("ORDER_CONTRACT", r.category)
        assertEquals("POSITIVE_POSSIBLE", r.direction)
        assertEquals(40, r.features.size)
        assertEquals(120.0, r.features["reported_amount_crore"])
        assertNull(r.features["earnings_surprise_pct"])
    }

    @Test fun badNewsCannotTriggerPositiveClassification() {
        val r = ForecastNewsPatternPolicy.assess("Quarterly results", "Profit declined 42% and guidance cut", 1, 1, 1, 1, true)
        assertEquals("NEGATIVE_OR_AMBIGUOUS", r.direction)
    }

    @Test fun headlineWithoutEvidenceIsNotPositive() {
        val r = ForecastNewsPatternPolicy.assess("Board meeting", "Shareholders discussed ordinary business", 1, 1, 1, 1, true)
        assertEquals("UNVERIFIED", r.direction)
    }
}
