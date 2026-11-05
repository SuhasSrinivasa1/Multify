package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedBetaDetailFallbackPolicyTest {
    @Test fun deterministicPriceGateSkipsRetry() {
        assertFalse(FixedBetaDetailFallbackPolicy.shouldRetryOnAlpha(
            IllegalArgumentException("FIXED_PRICE_BAND_OUTSIDE")))
    }

    @Test fun otherFailuresAllowExistingRecoveryPath() {
        assertTrue(FixedBetaDetailFallbackPolicy.shouldRetryOnAlpha(
            IllegalStateException("transport temporarily unavailable")))
    }
}
