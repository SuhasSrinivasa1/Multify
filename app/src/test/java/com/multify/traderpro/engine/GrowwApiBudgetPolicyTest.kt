package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GrowwApiBudgetPolicyTest {
    @Test fun alphaLiveCeilingAndResearchReserve() {
        assertEquals(300, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE)
        assertEquals(10, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND)
        assertEquals(GrowwApiBudgetPolicy.LIVE_DATA_HARD_PER_MINUTE, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE)
        assertEquals(8, GrowwApiBudgetPolicy.fixedLiveDataPerSecond(true))
        assertEquals(240, GrowwApiBudgetPolicy.fixedLiveDataPerMinute(true))
        assertEquals(8, GrowwApiBudgetPolicy.fixedLiveDataPerSecond(false))
        assertEquals(240, GrowwApiBudgetPolicy.fixedLiveDataPerMinute(false))
        assertEquals(60, GrowwApiBudgetPolicy.SELL_MONITOR_RESERVED_PER_MINUTE)
    }
    @Test fun independentOrderNonTradingAndAuthBudgetsUnchanged() {
        assertEquals(150, GrowwApiBudgetPolicy.APP_ORDER_PER_MINUTE)
        assertEquals(360, GrowwApiBudgetPolicy.APP_NON_TRADING_PER_MINUTE)
        assertEquals(20, GrowwApiBudgetPolicy.APP_AUTH_PER_MINUTE)
    }
}
