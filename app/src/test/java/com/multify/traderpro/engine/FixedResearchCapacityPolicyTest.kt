package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedResearchCapacityPolicyTest {
    private val p = FixedResearchCapacityPolicy

    @Test fun noRepeatedEightyPercentBetaSaturation() {
        assertEquals(26, (0 until 53).count { p.betaDiscoveryPreferred(it,true,40,40,1300L) })
        assertEquals(0, (0 until 53).count { p.betaDiscoveryPreferred(it,true,6,40,1300L) })
    }

    @Test fun calendarLimitAndAdaptivePaceAreBothRespected() {
        assertFalse(p.betaDiscoveryPreferred(1,true,40,2,1300L))
        assertFalse(p.betaDiscoveryPreferred(1,true,40,40,8000L))
        assertTrue(p.betaDiscoveryPreferred(1,true,40,40,1300L))
    }

    @Test fun highGapAfterCooldownGetsExactlyOneRecoveryProbe() {
        assertTrue(p.betaDiscoveryPreferred(1,true,40,40,6900L,recoveryProbeRequired=true))
        assertFalse(p.betaDiscoveryPreferred(3,true,40,40,6900L,recoveryProbeRequired=true))
        assertFalse(p.betaDiscoveryPreferred(1,true,40,40,6900L,recoveryProbeRequired=false))
        assertFalse(p.betaDiscoveryPreferred(1,false,40,40,6900L,recoveryProbeRequired=true))
    }

    @Test fun earlyPrefetchMayOverlapOnlyWhenBothAccountsHaveHeadroom() {
        assertTrue(p.prefetchMayStart(true,true,40,40,betaLiveHealthy=true))
        assertFalse(p.prefetchMayStart(true,false,40,40,betaLiveHealthy=true))
        assertFalse(p.prefetchMayStart(true,true,40,40,betaLiveHealthy=false))
        assertFalse(p.prefetchMayStart(true,true,9,40,betaLiveHealthy=true))
        assertFalse(p.prefetchMayStart(false,false,9,40,betaLiveHealthy=true))
        assertTrue(p.prefetchMayStart(false,false,15,15,betaLiveHealthy=true))
        // Preserve the original Alpha-only fallback after foreground detail completes.
        assertTrue(p.prefetchMayStart(false,true,0,0,betaLiveHealthy=false))
    }

    @Test fun detailsNeedRealAvailableBetaCapacity() {
        assertFalse(p.betaDetailPreferred("SUVEN",true,3,40,1300L))
        assertFalse(p.betaDetailPreferred("SUVEN",false,40,40,1300L))
    }
}
