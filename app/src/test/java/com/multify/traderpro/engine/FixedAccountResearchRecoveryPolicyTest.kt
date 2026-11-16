package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedAccountResearchRecoveryPolicyTest {
    private val p = FixedAccountResearchRecoveryPolicy

    @Test fun researchContinuesOnHealthyBetaButNeverPretendsEitherUnhealthyAccountIsReady() {
        assertTrue(p.researchAvailable(true, true, false, false))
        assertTrue(p.researchAvailable(false, false, true, true))
        assertTrue(p.researchAvailable(false, false, true, false).not())
        assertFalse(p.researchAvailable(false, false, false, false))
        assertFalse(p.researchAvailable(true, false, true, false))
        assertFalse(p.researchAvailable(false, true, false, true))
    }

    @Test fun originalFiftyThreeDiscoverySlotsRemainIntactAndBetaTakesOverAlphaDuringCooldown() {
        val assigned = (0 until 53).map {
            FixedDualAccountPolicy.betaDiscoveryBatch(it, true)
        }
        assertEquals(26, assigned.count { it })
        assertEquals(27, assigned.count { !it })
        val healthy = assigned.map { p.betaForDiscovery(it, true, true) }
        assertEquals(26, healthy.count { it })
        val alphaCooldown = assigned.map { p.betaForDiscovery(it, false, true) }
        assertEquals(53, alphaCooldown.count { it })
        assertEquals(0, assigned.count { p.betaForDiscovery(it, false, false) })
    }

    @Test fun rescueIsSingleAccountAwareAndDoesNotRetryBetaAfterFailure() {
        assertTrue(p.rescueAlphaBatchOnBeta(false, true, false))
        assertFalse(p.rescueAlphaBatchOnBeta(true, true, false))
        assertFalse(p.rescueAlphaBatchOnBeta(false, false, false))
        assertFalse(p.rescueAlphaBatchOnBeta(false, true, true))
    }

    @Test fun detailUsesOriginalStableOneInSixUnderHealthyAlphaAndBetaOnAlphaOutage() {
        val examples = listOf("NATIONALUM", "HINDALCO", "ITC", "AUTOAXLES")
        examples.forEach { symbol ->
            val original = FixedDualAccountPolicy.betaDetailedEvaluation(symbol, true)
            assertEquals(original, p.betaForDetail(original, true, true, true, true))
            assertTrue(p.betaForDetail(original, false, true, true, true))
            assertTrue(p.betaForDetail(original, true, false, true, true))
            assertFalse(p.betaForDetail(original, false, false, true, false))
        }
    }
}
