package com.multify.traderpro.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastSharedDiscoveryPolicyTest {
    @Test fun acceptsOnlyFreshTimestampedSnapshots() {
        val now = 5_000_000L
        assertTrue(ForecastSharedDiscoveryPolicy.fresh(now, now - 120_000L))
        assertFalse(ForecastSharedDiscoveryPolicy.fresh(now, now - 400_000L))
        assertFalse(ForecastSharedDiscoveryPolicy.fresh(now, now + 1L))
    }
    @Test fun requiresTwoDistinctFullCoverageSweeps() {
        assertFalse(ForecastSharedDiscoveryPolicy.ready(.92, 0.0, 200L, 0L))
        assertFalse(ForecastSharedDiscoveryPolicy.ready(.92, .92, 200L, 200L))
        assertFalse(ForecastSharedDiscoveryPolicy.ready(.92, .84, 200L, 100L))
        assertTrue(ForecastSharedDiscoveryPolicy.ready(.92, .91, 200L, 100L))
    }
}
