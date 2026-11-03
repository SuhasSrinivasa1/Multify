package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class MarketDataPriorityAdmissionPolicyTest {
    @Test fun reservesSellQuoteCapacityOnlyWhileManagingHoldings() {
        val p = MarketDataPriorityAdmissionPolicy
        val ordinary = p.allowedLiveTraffic(true, false)
        val protected = p.allowedLiveTraffic(true, true)
        val empty = p.allowedLiveTraffic(false, false)
        assertEquals(6, ordinary.perSecond)
        assertEquals(150, ordinary.perMinute)
        assertEquals(7, protected.perSecond)
        assertEquals(210, protected.perMinute)
        assertEquals(protected, empty)
    }

    @Test fun pendingHoldingsStateAndTagAreExplicit() {
        val p = MarketDataPriorityAdmissionPolicy
        p.updateManagedHoldings(2)
        assertTrue(p.hasManagedHoldings)
        p.updateManagedHoldings(0)
        assertFalse(p.hasManagedHoldings)
        assertTrue(p.isProtectionHeader(p.PROTECTION))
        assertFalse(p.isProtectionHeader(null))
        assertFalse(p.isProtectionHeader("RESEARCH"))
        assertEquals(125L, p.ltpPriorityYieldMs(true, false, 1))
        assertEquals(0L, p.ltpPriorityYieldMs(true, true, 1))
        assertEquals(0L, p.ltpPriorityYieldMs(false, false, 1))
        assertEquals(0L, p.ltpPriorityYieldMs(true, false, 0))
    }
}
