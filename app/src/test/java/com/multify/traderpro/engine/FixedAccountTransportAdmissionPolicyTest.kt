package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedAccountTransportAdmissionPolicyTest {
    @Test fun betaCooldownNeverBorrowsAlphaCapacity() {
        assertFalse(FixedAccountTransportAdmissionPolicy.allowAlphaFallback(
            alphaLiveReady = true, alphaLiveUsed60s = 20, alphaAppLimit60s = 120))
        assertFalse(FixedAccountTransportAdmissionPolicy.allowAlphaFallback(
            alphaLiveReady = false, alphaLiveUsed60s = 20, alphaAppLimit60s = 120))
    }

    @Test fun reserveProtectsAlphaQuoteAndSellCapacity() {
        val reserve = FixedAccountTransportAdmissionPolicy.RESERVED_ALPHA_LIVE_REQUESTS
        assertFalse(FixedAccountTransportAdmissionPolicy.allowAlphaFallback(true, 120 - reserve - 1, 120))
        assertFalse(FixedAccountTransportAdmissionPolicy.allowAlphaFallback(true, 120 - reserve, 120))
        assertFalse(FixedAccountTransportAdmissionPolicy.allowAlphaFallback(true, 210, 210))
        assertFalse(FixedAccountTransportAdmissionPolicy.allowAlphaFallback(true, -1, 120))
        assertFalse(FixedAccountTransportAdmissionPolicy.allowAlphaFallback(true, 0, reserve))
    }

    @Test fun frozenDiscoveryAssignmentAndPriceModelRemainUntouched() {
        assertEquals(26, (0 until 53).count { FixedDualAccountPolicy.betaDiscoveryBatch(it, true) })
        assertEquals(0, (0 until 53).count { FixedDualAccountPolicy.betaDiscoveryBatch(it, false) })
        assertEquals(107, FixedFinalContract.CANONICAL_LEARNING_CALLS)
        assertEquals(1.1161043030113955, FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT, 1e-12)
        assertEquals(5.0, HardStopLossPolicy.MAX_LOSS_PCT, 0.00001)
    }
}
