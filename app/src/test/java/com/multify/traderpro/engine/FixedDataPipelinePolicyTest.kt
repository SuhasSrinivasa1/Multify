package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedDataPipelinePolicyTest {
    @Test fun cooldownPreventsUnproductiveScans() {
        assertEquals(FixedDataPipelinePolicy.Readiness.HISTORICAL_COOLDOWN,
            FixedDataPipelinePolicy.readiness(false, 1_500L, 1_000L))
        assertEquals(FixedDataPipelinePolicy.Readiness.READY,
            FixedDataPipelinePolicy.readiness(false, 1_500L, 1_500L))
    }

    @Test fun authenticationIsAnIndependentHigherPriorityBlocker() {
        assertEquals(FixedDataPipelinePolicy.Readiness.AUTH_RECONNECT_REQUIRED,
            FixedDataPipelinePolicy.readiness(true, 0L, 100L))
        assertEquals(FixedDataPipelinePolicy.Readiness.AUTH_RECONNECT_REQUIRED,
            FixedDataPipelinePolicy.readiness(true, 5_000L, 100L))
    }

    @Test fun avoidSpammingTheAuditLedgerEverySecond() {
        val pause = FixedDataPipelinePolicy.Readiness.HISTORICAL_COOLDOWN
        assertTrue(FixedDataPipelinePolicy.shouldAuditSuppression(null, pause, 0L, 1_000L))
        assertFalse(FixedDataPipelinePolicy.shouldAuditSuppression(pause, pause, 1_000L, 2_000L))
        assertTrue(FixedDataPipelinePolicy.shouldAuditSuppression(pause, pause, 1_000L, 61_000L))
    }

    @Test fun frozenHistoricalReferenceContractRemains107() {
        assertEquals(107, FixedFinalContract.CANONICAL_LEARNING_CALLS)
        assertEquals(1.1161043030113955, FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT, 1e-12)
        assertEquals("2026-10-19", FixedFinalContract.EXCLUDED_LEARNING_DATE)
        assertEquals("SPORTKING", FixedFinalContract.EXCLUDED_LEARNING_SYMBOL)
    }
}
