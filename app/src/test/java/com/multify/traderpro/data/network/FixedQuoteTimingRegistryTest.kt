package com.multify.traderpro.data.network

import org.junit.Assert.*
import org.junit.Test

class FixedQuoteTimingRegistryTest {
    @Test fun capturesOnlyNearbyCompletedSuccessfulHttpQuotes() {
        val p = FixedQuoteTimingRegistry
        p.record("ABC", 1_000L, 1_300L, 300L, 200)
        assertNull(p.closestCompletedQuote("ABC", 1_000L, 1_200L))
        assertEquals(300L, p.closestCompletedQuote("ABC", 1_000L, 1_900L)?.networkElapsedMs)
        assertNull(p.closestCompletedQuote("ABC", 1_000L, 181_301L))
        p.record("ABC", 2_000L, 2_400L, 400L, 429)
        assertNull(p.closestCompletedQuote("ABC", 2_000L, 2_500L))
    }
}
