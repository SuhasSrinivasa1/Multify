package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class WorkingRangeAutoBuyPolicyTest {
    private fun decide(
        enabled: Boolean = true, halt: Boolean = false, activated: Long = 1_000L,
        confirmed: Long = 2_000L, state: String = "IN_RANGE",
        execution: String = "NOT_ATTEMPTED", now: Long = 2_100L
    ) = WorkingRangeAutoBuyPolicy.decide(
        enabled, halt, activated, confirmed, state, execution, now
    )

    @Test fun onlyNewConfirmationAfterExplicitActivationCanBuy() {
        assertTrue(decide().eligible)
        assertEquals("CONFIRMED_BEFORE_ACTIVATION",
            decide(activated = 2_000L, confirmed = 2_000L).reason)
    }

    @Test fun oldResearchToggleCannotArmLiveOrdersWithoutActivationTimestamp() {
        assertEquals("ACTIVATION_TIMESTAMP_MISSING", decide(activated = 0L).reason)
    }

    @Test fun offOrSafetyHaltFailsClosed() {
        assertEquals("WORKING_RANGE_DISABLED", decide(enabled = false).reason)
        assertEquals("SAFETY_HALT", decide(halt = true).reason)
    }

    @Test fun weakenedOrTerminalEventsCannotBuy() {
        assertEquals("NOT_CURRENTLY_IN_RANGE", decide(state = "MOMENTUM_WEAKENING").reason)
        assertEquals("NOT_CURRENTLY_IN_RANGE", decide(state = "TARGET_HIT_SAMPLED").reason)
    }

    @Test fun anOrderIntentCannotBeSubmittedTwice() {
        assertEquals("EVENT_ALREADY_RESERVED_OR_CONSUMED",
            decide(execution = "ORDER_INTENT_RESERVED").reason)
        assertEquals("EVENT_ALREADY_RESERVED_OR_CONSUMED",
            decide(execution = "BUY_EXECUTED").reason)
    }

    @Test fun sameScanEvaluationsNeverConfirmAWorkingRangeAutoBuy() {
        assertFalse(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(
            1_000L, 1_000L, 1_100L, 1_200L))
        assertFalse(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(
            1_000L, 900L, 1_100L, 1_200L))
        assertFalse(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(
            1_000L, 2_000L, 1_200L, 1_150L))
        assertTrue(WorkingRangeDistinctScanPolicy.isIndependentSecondScan(
            1_000L, 2_000L, 1_100L, 2_100L))
    }

    @Test fun staleConfirmationCannotTriggerDelayedAutoBuy() {
        assertEquals("CONFIRMATION_TO_BUY_EXPIRED_30S",
            decide(activated = 500L, confirmed = 1_000L,
                now = 1_000L + WorkingRangeAutoBuyPolicy.MAX_CONFIRMATION_AGE_MS + 1L).reason)
    }
}
