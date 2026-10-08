package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionHealthPolicyTest {
    private fun healthy() = ExecutionHealthInput(
        brokerAuthenticated = true,
        staticIpMatched = true,
        marketOpen = true,
        quoteAgeMs = 400,
        maxQuoteAgeMs = 5_000,
        listenerHeartbeatAgeMs = 2_000,
        maxListenerHeartbeatAgeMs = 30_000,
        symbolMasterFresh = true,
        decisionComplete = true,
        safetyHalt = false
    )

    @Test fun healthyStateAllowsLiveOrder() {
        assertTrue(ExecutionHealthPolicy.evaluate(healthy()).liveOrderAllowed)
    }

    @Test fun staleQuoteBlocksLiveOrderButIsExplicit() {
        val d = ExecutionHealthPolicy.evaluate(healthy().copy(quoteAgeMs = 5_001))
        assertFalse(d.liveOrderAllowed)
        assertTrue(d.reason.contains("quote stale"))
    }

    @Test fun unhealthyListenerBlocksLiveOrder() {
        val d = ExecutionHealthPolicy.evaluate(healthy().copy(listenerHeartbeatAgeMs = 30_001))
        assertFalse(d.liveOrderAllowed)
        assertFalse(d.listenerHealthy)
    }

    @Test fun brokerMarketAndDecisionGuardsAreFailClosed() {
        assertFalse(ExecutionHealthPolicy.evaluate(healthy().copy(brokerAuthenticated = false)).liveOrderAllowed)
        assertFalse(ExecutionHealthPolicy.evaluate(healthy().copy(staticIpMatched = false)).liveOrderAllowed)
        assertFalse(ExecutionHealthPolicy.evaluate(healthy().copy(marketOpen = false)).liveOrderAllowed)
        assertFalse(ExecutionHealthPolicy.evaluate(healthy().copy(symbolMasterFresh = false)).liveOrderAllowed)
        assertFalse(ExecutionHealthPolicy.evaluate(healthy().copy(decisionComplete = false)).liveOrderAllowed)
        assertFalse(ExecutionHealthPolicy.evaluate(healthy().copy(safetyHalt = true)).liveOrderAllowed)
    }
}
