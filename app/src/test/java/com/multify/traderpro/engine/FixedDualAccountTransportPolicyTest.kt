package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedDualAccountTransportPolicyTest {
    @Test fun fullNseSweepUsesThirtyFiveBetaAndEighteenAlphaBatches() {
        val total = 53
        val beta = (0 until total).filter {
            FixedDualAccountTransportPolicy.betaDiscoveryBatch(it, total, true)
        }
        assertEquals(35, beta.size)
        assertEquals(18, total - beta.size)
    }

    @Test fun betaDiscoveryAssignmentIsDistributedAcrossTheSweep() {
        val total = 53
        val beta = (0 until total).filter {
            FixedDualAccountTransportPolicy.betaDiscoveryBatch(it, total, true)
        }
        val gaps = beta.zipWithNext { a, b -> b - a }
        assertTrue(gaps.all { it in 1..2 })
    }

    @Test fun betaUnavailableRoutesEveryBatchToAlpha() {
        assertFalse(FixedDualAccountTransportPolicy.betaDiscoveryBatch(0, 53, false))
        assertFalse(FixedDualAccountTransportPolicy.betaDiscoveryBatch(52, 53, false))
    }

    @Test fun detailEvaluationRemainsOnAlpha() {
        assertFalse(FixedDualAccountTransportPolicy.betaDetailedEvaluation("RELIANCE", true))
        assertFalse(FixedDualAccountTransportPolicy.betaDetailedEvaluation("TCS", false))
    }
}
