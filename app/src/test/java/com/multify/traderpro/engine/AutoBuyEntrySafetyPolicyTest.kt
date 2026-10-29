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

    @Test fun forecastAutoBuyRemainsLockedUntilFiveSessions() {
        for (count in 0..4) assertFalse(AutoBuyEntrySafetyPolicy.forecastAutoBuyAllowed(count))
        assertTrue(AutoBuyEntrySafetyPolicy.forecastAutoBuyAllowed(5))
        assertTrue(AutoBuyEntrySafetyPolicy.forecastAutoBuyAllowed(6))
    }
}
