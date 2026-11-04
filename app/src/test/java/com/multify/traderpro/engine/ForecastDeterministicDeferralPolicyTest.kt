package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ForecastDeterministicDeferralPolicyTest {
    @Test fun skipOnlyFarOutsideBandWithVeryFreshEvidence() {
        val p = ForecastDeterministicDeferralPolicy
        assertTrue(p.defer(150.0, 1_000_000L, 1_020_000L))
        assertTrue(p.defer(6_100.0, 1_000_000L, 1_020_000L))
        assertFalse(p.defer(198.0, 1_000_000L, 1_020_000L))
        assertFalse(p.defer(5_002.0, 1_000_000L, 1_020_000L))
        assertFalse(p.defer(150.0, 1_000_000L, 1_100_000L))
        assertFalse(p.defer(null, 1_000_000L, 1_020_000L))
    }
}
