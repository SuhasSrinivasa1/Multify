package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedAutoBuyResearchLtpRoutingPolicyTest {
    @Test fun originalSplitUnchangedWithAutoBuyOff() {
        for (index in 0 until 53) {
            assertEquals(FixedDualAccountPolicy.betaDiscoveryBatch(index, true),
                FixedAutoBuyResearchLtpRoutingPolicy.useBetaBatch(index, true, false))
        }
    }

    @Test fun liveAutoBuyReservesMoreAlphaHeadroomWithoutExtraBatches() {
        val choices = (0 until 53).map {
            FixedAutoBuyResearchLtpRoutingPolicy.useBetaBatch(it, true, true)
        }
        assertEquals(53, choices.size)
        assertEquals(35, choices.count { it })
        assertEquals(18, choices.count { !it })
    }

    @Test fun cannotUseBetaWhenUnhealthy() {
        for (index in 0 until 53) {
            assertFalse(FixedAutoBuyResearchLtpRoutingPolicy.useBetaBatch(index, false, true))
        }
    }
}
