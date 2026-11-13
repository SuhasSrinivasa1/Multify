package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedScanLatencyShadowPolicyTest {
    @Test fun scanWarningsNeverBecomeRecommendationVetoes() {
        val p = FixedScanLatencyShadowPolicy
        assertEquals("FAST_UP_TO_90S", p.classify(90_000L).bucket)
        assertEquals("INTERMEDIATE_90_TO_120S", p.classify(90_001L).bucket)
        assertEquals("SLOW_120S_OR_MORE", p.classify(120_000L).bucket)
        val slow = p.classify(165_000L)
        assertTrue(slow.wouldWarn)
        assertFalse(slow.canBlockRecommendation)
        assertFalse(slow.canChangeAutoBuy)
    }
    @Test fun localQuoteAgeIsNotFabricatedFromMissingOrStaleReferences() {
        val p = FixedScanLatencyShadowPolicy
        assertNull(p.localQuoteToReadyMs(null, 10_000L, 12_000L))
        assertNull(p.localQuoteToReadyMs(9_999L, 10_000L, 12_000L))
        assertNull(p.localQuoteToReadyMs(12_001L, 10_000L, 12_000L))
        assertEquals(700L, p.localQuoteToReadyMs(11_300L, 10_000L, 12_000L))
    }
}
