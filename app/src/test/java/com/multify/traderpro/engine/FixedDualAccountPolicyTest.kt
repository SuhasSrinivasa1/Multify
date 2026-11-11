package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedDualAccountPolicyTest {
    @Test fun discoveryRoutesApproximatelyHalfToBetaWithoutChangingUniverse() {
        val n = (0 until 53).count { FixedDualAccountPolicy.betaDiscoveryBatch(it, true) }
        assertEquals(26, n)
        assertEquals(0, (0 until 53).count { FixedDualAccountPolicy.betaDiscoveryBatch(it, false) })
        assertFalse(FixedDualAccountPolicy.betaDiscoveryBatch(-1, true))
    }
    @Test fun historicalRoutingIsDeterministicAndNoBetaMeansAlphaOnly() {
        val symbols = listOf("TONBO", "BSOFT", "FDC", "ROLEXRINGS", "HINDALCO")
        assertTrue(symbols.all {
            !FixedDualAccountPolicy.betaDetailedEvaluation(it, false)
        })
        assertEquals(
            symbols.map { FixedDualAccountPolicy.betaDetailedEvaluation(it, true) },
            symbols.map { FixedDualAccountPolicy.betaDetailedEvaluation(it, true) }
        )
    }
    @Test fun incompleteOrAbortedMarketSweepCannotQualify() {
        assertTrue(FixedDualAccountPolicy.acceptableCoverage(2431, 2630, false))
        assertFalse(FixedDualAccountPolicy.acceptableCoverage(1200, 2630, false))
        assertFalse(FixedDualAccountPolicy.acceptableCoverage(2431, 2630, true))
        assertFalse(FixedDualAccountPolicy.acceptableCoverage(0, 0, false))
    }
    @Test fun betaReadOnlyBudgetsConservativeAndBatchSizeFrozen() {
        assertEquals(50, FixedDualAccountPolicy.FULL_NSE_LTP_BATCH_LIMIT)
        assertEquals(270, FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE)
        assertEquals(40, FixedDualAccountPolicy.BETA_LIVE_ENDPOINT_PER_MINUTE)
        assertEquals(1_000L, FixedDualAccountPolicy.BETA_LTP_MIN_GAP_MS)
        val assigned = (0 until 1_000).count {
            FixedDualAccountPolicy.betaDetailedEvaluation("SYMBOL$it", true)
        }
        assertTrue(assigned in 240..260)
        assertTrue(FixedDualAccountPolicy.BETA_HISTORICAL_PER_MINUTE <= 8)
    }
}
