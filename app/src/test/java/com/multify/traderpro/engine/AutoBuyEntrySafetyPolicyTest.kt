package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AutoBuyEntrySafetyPolicyTest {
    @Test fun freshFixedEntryWithinTwoCyclesIsEligible() {
        assertTrue(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(1_000_000L, 1_150_000L))
        assertTrue(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(1_000_000L, 1_300_000L))
    }

    @Test fun oldOrFutureFixedEntryCannotAutoBuyWhenToggleEnabled() {
        assertFalse(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(1_000_000L, 1_300_001L))
        assertFalse(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(1_000_000L, 1_299_999L + 300_002L))
        assertFalse(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(0L, 10L))
        assertFalse(AutoBuyEntrySafetyPolicy.isFreshFixedEntry(10_000L, 9_000L))
    }

    @Test fun fixedAutoBuyOnlyAcceptsRecommendationsStrictlyAfterToggle() {
        assertFalse(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(10_000L, 0L))
        assertFalse(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(10_000L, 10_001L))
        assertFalse(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(10_000L, 10_000L))
        assertTrue(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(10_001L, 10_000L))
        // A stock with a genuine new event is eligible even when its prior call was not.
        assertTrue(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(20_000L, 10_000L))
        // If the user toggles OFF then ON during quote retrieval, the prior signal is out.
        assertFalse(AutoBuyEntrySafetyPolicy.isPublishedAfterFixedAutoBuyEnabled(20_000L, 20_500L))
    }

    @Test fun forecastAutoBuyRemainsLockedUntilFiveSessions() {
        for (count in 0..4) assertFalse(AutoBuyEntrySafetyPolicy.forecastAutoBuyAllowed(count))
        assertTrue(AutoBuyEntrySafetyPolicy.forecastAutoBuyAllowed(5))
        assertTrue(AutoBuyEntrySafetyPolicy.forecastAutoBuyAllowed(6))
    }
}
