package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ForecastBetaPriorityPolicyTest {
    private fun eligible(fixed: Boolean = false, trade: Boolean = false,
                         deferred: Boolean = false, due: Long = 80_000L) =
        ForecastBetaPriorityPolicy.canResearch(fixed, trade, deferred, due, 10_000L)

    @Test fun overdueFixedScanDoesNotStarveIndependentBetaWhenAlphaBrokerDeferred() {
        assertTrue(eligible(deferred = true, due = 0L))
        assertTrue(eligible(deferred = true, due = 1_000L))
    }

    @Test fun fixedAndProtectiveSellAlwaysPreemptEvenDuringAlphaCooldown() {
        assertFalse(eligible(fixed = true, deferred = true, due = 0L))
        assertFalse(eligible(trade = true, deferred = true, due = 0L))
        assertFalse(eligible(fixed = true, due = 80_000L))
        assertFalse(eligible(trade = true, due = 80_000L))
    }

    @Test fun ordinaryFixedGuardRemainsEffective() {
        assertFalse(eligible(due = 0L))
        assertFalse(eligible(due = 10_000L))
        assertTrue(eligible(due = 10_001L))
    }
}
