package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ForecastRepeatEntryPolicyTest {
    @Test fun freshSymbolMayEnterButActiveOrUnhitPreviousSetupMustNotRepublish() {
        val now = 2_000_000_000_000L
        assertTrue(ForecastRepeatEntryPolicy.mayPublish(null, null, null, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("ACTIVE", now - 600_000L, 105.0, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("MISSED", now - 600_000L, 105.0, 100.0, now))
    }

    @Test fun secondAndThirdPeaksAllowedOnlyAfterTargetHitCooldownAndRealPullback() {
        val now = 2_000_000_000_000L
        val previous = now - 600_000L
        assertTrue(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", previous, 105.0, 104.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", previous, 105.0, 104.90, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", now - 120_000L, 105.0, 103.0, now))
    }
}
