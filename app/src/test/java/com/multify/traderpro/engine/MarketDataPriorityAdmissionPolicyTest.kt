package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class MarketDataPriorityAdmissionPolicyTest {
    @Test fun reserveAlwaysProtectsExecutionQuotes() {
        val p = MarketDataPriorityAdmissionPolicy
        val ordinary = p.allowedLiveTraffic(true, false)
        val priority = p.allowedLiveTraffic(true, true)
        val beforeFirstHolding = p.allowedLiveTraffic(false, false)
        assertEquals(8, ordinary.perSecond)
        assertEquals(240, ordinary.perMinute)
        assertEquals(10, priority.perSecond)
        assertEquals(300, priority.perMinute)
        assertEquals(ordinary, beforeFirstHolding)
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
