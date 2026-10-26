package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class BrokerExecutionSafetyPolicyTest {
    @Test fun verifySellableQuantityNotTotalHoldings() {
        assertFalse(BrokerExecutionSafetyPolicy.maySell(2, 1, 0))
        assertFalse(BrokerExecutionSafetyPolicy.maySell(1, 0, 0))
        assertTrue(BrokerExecutionSafetyPolicy.maySell(1, 1, 0))
        assertTrue(BrokerExecutionSafetyPolicy.maySell(2, 0, 2))
    }
    @Test fun terminalBrokerOrderOutcomesDoNotCountAsFills() {
        assertTrue(BrokerExecutionSafetyPolicy.isTerminal("FAILED"))
        assertTrue(BrokerExecutionSafetyPolicy.isTerminal("rejected"))
        assertFalse(BrokerExecutionSafetyPolicy.isTerminal("EXECUTED"))
        assertTrue(BrokerExecutionSafetyPolicy.isInsufficientShares("You don't have enough shares in your account to sell"))
    }
    @Test fun rejectionCooldownLongerThanRapidPoll() {
        assertEquals(86_400_000L, BrokerExecutionSafetyPolicy.REJECTED_SELL_BACKOFF_MS)
        assertEquals(1_800_000L, BrokerExecutionSafetyPolicy.REJECTED_GTT_BACKOFF_MS)
    }
}
