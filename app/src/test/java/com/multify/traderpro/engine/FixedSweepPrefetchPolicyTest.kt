package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedSweepPrefetchPolicyTest {
    @Test fun idleOnlyAndBrokerAvailable() {
        assertTrue(FixedSweepPrefetchPolicy.canStart(100_000L, 150_000L, false, true))
        assertFalse(FixedSweepPrefetchPolicy.canStart(100_000L, 150_000L, true, true))
        assertFalse(FixedSweepPrefetchPolicy.canStart(100_000L, 150_000L, false, false))
        assertFalse(FixedSweepPrefetchPolicy.canStart(100_000L, 110_000L, false, true))
        assertFalse(FixedSweepPrefetchPolicy.canStart(100_000L, 300_000L, false, true))
    }

    @Test fun staleIncompleteAndAbortedCannotReplaceOfficialSweep() {
        assertTrue(FixedSweepPrefetchPolicy.canConsume(100_000L, 110_000L, 2432, 2630, false))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(100_000L, 150_000L, 2432, 2630, false))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(100_000L, 101_000L, 1000, 2630, false))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(100_000L, 101_000L, 2432, 2630, true))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(100_000L, 90_000L, 2432, 2630, false))
        assertFalse(FixedSweepPrefetchPolicy.canConsume(0L, 101_000L, 2432, 2630, false))
    }

    @Test fun officialSweepFallsBackOnlyBeforePrefetchBrokerRequests() {
        // The next official scan must not be lost because a pending job
        // was scheduled but never fetched even a single LTP batch.
        assertTrue(FixedSweepPrefetchPolicy.officialSweepAllowedWithoutDuplication(false))
        // Once started, the official scan must await or safely defer.
        assertFalse(FixedSweepPrefetchPolicy.officialSweepAllowedWithoutDuplication(true))
    }

    @Test fun matchesFrozenMinimumCoverageAndKeepsStrictAgeLimit() {
        assertEquals(0.85, FixedSweepPrefetchPolicy.MIN_COVERAGE, 0.000001)
        assertTrue(FixedSweepPrefetchPolicy.MAX_ACCEPTED_AGE_MS < 60_000L)
    }
}
