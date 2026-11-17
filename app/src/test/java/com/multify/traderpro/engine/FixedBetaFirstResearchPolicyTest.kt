package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedBetaFirstResearchPolicyTest {
    private val p = FixedBetaFirstResearchPolicy
    @Test fun betaIsMainScannerWhenHealthy() {
        assertEquals(42, (0 until 53).count { p.preferBetaBatch(it, true, 1_300L) })
        assertEquals(33, (0 until 53).count { p.preferBetaBatch(it, true, 3_000L) })
    }
    @Test fun unavailableBetaNeverUsed() {
        assertFalse(p.preferBetaBatch(1,false,1_300L))
        assertFalse(p.preferBetaDetail("SEJALLTD",false,1_300L))
    }
    @Test fun throttledBetaAndUnavailableAlphaFailClosedEarly() {
        assertTrue(p.shouldDeferBetaOnlySweep(true,false,7_000L))
        assertFalse(p.shouldDeferBetaOnlySweep(true,true,7_000L))
        assertFalse(p.shouldDeferBetaOnlySweep(true,false,2_300L))
    }
    @Test fun deterministicDetailAndNoUniverseChanges() {
        val xs=listOf("SEJALLTD","TRIVENIPT","KDDL","LODHA","INFY")
        assertEquals(xs.map{p.preferBetaDetail(it,true,1_300L)},
            xs.map{p.preferBetaDetail(it.lowercase(),true,1_300L)})
    }
}
