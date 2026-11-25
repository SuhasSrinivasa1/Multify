package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class NseArchiveRecoveryPolicyTest {
    @Test fun accessDenialPausesEveryDate() {
        val now = 100000L
        for (code in listOf(403, 429)) {
            val until = NseArchiveRecoveryPolicy.deadline(
                now, NseArchiveRecoveryPolicy.cooldownMs(code, null, now)
            )
            assertFalse(NseArchiveRecoveryPolicy.allowed(now + 1000, until))
            assertTrue(NseArchiveRecoveryPolicy.allowed(until, until))
        }
    }

    @Test fun repeated403UsesCappedExponentialBackoffPerHost() {
        assertEquals(3_600_000L, NseArchiveRecoveryPolicy.cooldownMs(403, null, 0, 1))
        assertEquals(7_200_000L, NseArchiveRecoveryPolicy.cooldownMs(403, null, 0, 2))
        assertEquals(14_400_000L, NseArchiveRecoveryPolicy.cooldownMs(403, null, 0, 3))
        assertEquals(86_400_000L, NseArchiveRecoveryPolicy.cooldownMs(403, null, 0, 6))
        assertEquals(86_400_000L, NseArchiveRecoveryPolicy.cooldownMs(403, null, 0, 9))
    }

    @Test fun explicitRetryAfterIsNeverShortened() {
        assertEquals(7_200_000L, NseArchiveRecoveryPolicy.cooldownMs(429, "7200", 0))
        assertEquals(3_600_000L, NseArchiveRecoveryPolicy.cooldownMs(429, "1", 0))
        assertEquals(10_800_000L, NseArchiveRecoveryPolicy.cooldownMs(403, "10800", 0, 1))
        assertEquals(0L, NseArchiveRecoveryPolicy.cooldownMs(404, null, 0))
    }

    @Test fun missingDatesDoNotBlockSourceAndHaveSeparateRetry() {
        assertEquals(7_200_000L, NseArchiveRecoveryPolicy.missingRetryMs(true))
        assertEquals(604_800_000L, NseArchiveRecoveryPolicy.missingRetryMs(false))
    }

    @Test fun transientFailurePacesRecovery() {
        assertEquals(900_000L, NseArchiveRecoveryPolicy.cooldownMs(503, null, 0))
        assertEquals(900_000L, NseArchiveRecoveryPolicy.cooldownMs(0, null, 0))
        assertEquals(0L, NseArchiveRecoveryPolicy.cooldownMs(200, null, 0))
    }

    @Test fun httpDateRetryAfterAndOverflow() {
        assertEquals(7_200_000L, NseArchiveRecoveryPolicy.retryAfterMs("Thu, 1 Jan 1970 02:00:00 GMT", 0))
        assertEquals(Long.MAX_VALUE, NseArchiveRecoveryPolicy.deadline(100L, Long.MAX_VALUE))
    }
}
