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
        // Both brokers may offer quotes without offering *any* candles:
        // this must still fail closed.
        assertFalse(p.researchAvailable(true, false, true, false))
        assertFalse(p.researchAvailable(false, true, false, true))
        // Healthy history from Alpha can be paired with a fresh Beta quote.
        assertTrue(p.researchAvailable(false, true, true, false))
        // Healthy quote from Alpha can be paired with Beta history.
        assertTrue(p.researchAvailable(true, false, false, true))
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

    @Test fun brokerCapsStillApplyWhenBetaRecoversAllFiftyThreeBatches() {
        // More *work slots* on Beta cannot expand its independent request cap.
        assertEquals(6, FixedDualAccountPolicy.BETA_LIVE_PER_SECOND)
        assertEquals(180, FixedDualAccountPolicy.BETA_LIVE_PER_MINUTE)
        // v9.0.3: 5 Alpha + 26 Beta successful batches, 22 Alpha failures.
        // The 22 missing slots can be attempted on Beta once each.
        val alphaSuccess = 5
        val betaNormalSuccess = 26
        val betaRescueSlots = 22
        assertEquals(53, alphaSuccess + betaNormalSuccess + betaRescueSlots)
        assertEquals(22, (0 until betaRescueSlots).count {
            p.rescueAlphaBatchOnBeta(false, true, false)
        })
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
