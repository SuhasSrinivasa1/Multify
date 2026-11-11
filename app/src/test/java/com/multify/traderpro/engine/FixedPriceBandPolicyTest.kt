package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedPriceBandPolicyTest {
    @Test fun fixedUsesItsOwnStrictHundredToFiveThousandBand() {
        assertFalse(FixedPriceBandPolicy.eligible(100.0))
        assertTrue(FixedPriceBandPolicy.eligible(100.01))
        assertTrue(FixedPriceBandPolicy.eligible(200.0))
        assertTrue(FixedPriceBandPolicy.eligible(4_999.99))
        assertFalse(FixedPriceBandPolicy.eligible(5_000.0))
        assertFalse(FixedPriceBandPolicy.eligible(Double.NaN))
        assertFalse(FixedPriceBandPolicy.eligible(null))
    }
}
