package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationPriceBandPolicyTest {
    @Test fun minAndMaxAreExclusive() {
        assertFalse(RecommendationPriceBandPolicy.eligible(100.0))
        assertFalse(RecommendationPriceBandPolicy.eligible(5_000.0))
        assertTrue(RecommendationPriceBandPolicy.eligible(100.01))
        assertTrue(RecommendationPriceBandPolicy.eligible(4_999.99))
        assertFalse(RecommendationPriceBandPolicy.eligible(99.99))
        assertFalse(RecommendationPriceBandPolicy.eligible(5_000.01))
    }

    @Test fun missingAndNonFiniteAreRejected() {
        assertFalse(RecommendationPriceBandPolicy.eligible(null))
        assertFalse(RecommendationPriceBandPolicy.eligible(Double.NaN))
        assertFalse(RecommendationPriceBandPolicy.eligible(Double.POSITIVE_INFINITY))
        assertFalse(RecommendationPriceBandPolicy.eligible(Double.NEGATIVE_INFINITY))
    }
}
