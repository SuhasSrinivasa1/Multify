package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class GrowwRecoveryPolicyTest {
    @Test fun firstRateLimitUsesOneMinuteWindowWhenServerGivesNoRetryAfter() {
        assertEquals(60_000L, GrowwRecoveryPolicy.cooldownMs(1, 0L))
        assertEquals(60_000L, GrowwRecoveryPolicy.cooldownMs(1, 5_000L))
    }

    @Test fun repeatedIncidentsEscalateProgressively() {
        assertEquals(180_000L, GrowwRecoveryPolicy.cooldownMs(2, 0L))
        assertEquals(600_000L, GrowwRecoveryPolicy.cooldownMs(3, 0L))
        assertEquals(1_800_000L, GrowwRecoveryPolicy.cooldownMs(4, 0L))
        assertEquals(1_800_000L, GrowwRecoveryPolicy.cooldownMs(9, 0L))
    }

    @Test fun longerServerRetryAfterIsAlwaysRespectedAndBounded() {
        assertEquals(4_200_000L, GrowwRecoveryPolicy.cooldownMs(1, 4_200_000L))
        assertEquals(21_600_000L, GrowwRecoveryPolicy.cooldownMs(1, Long.MAX_VALUE))
    }

    @Test fun rollingDayAuthBudgetLeavesThirtyRequestsUnused() {
        assertTrue(GrowwRecoveryPolicy.mayRequestAuth(0))
        assertTrue(GrowwRecoveryPolicy.mayRequestAuth(119))
        assertFalse(GrowwRecoveryPolicy.mayRequestAuth(120))
        assertFalse(GrowwRecoveryPolicy.mayRequestAuth(150))
    }
}
