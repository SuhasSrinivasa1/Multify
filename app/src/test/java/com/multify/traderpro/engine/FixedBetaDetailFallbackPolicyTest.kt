package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Test

class FixedBetaDetailFallbackPolicyTest {
    @Test fun neverAmplifiesBetaOutageIntoAlpha() {
        assertFalse(FixedBetaDetailFallbackPolicy.shouldRetryOnAlpha(
            IllegalArgumentException("FIXED_PRICE_BAND_OUTSIDE")))
        assertFalse(FixedBetaDetailFallbackPolicy.shouldRetryOnAlpha(
            IllegalStateException("transport temporarily unavailable")))
        assertFalse(FixedBetaDetailFallbackPolicy.shouldRetryOnAlpha(
            java.io.IOException("BETA_FIXED_RATE_BUDGET_OR_COOLDOWN")))
    }
}
