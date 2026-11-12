package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowwApiBudgetPolicyTest {
    @Test fun alphaLiveResearchBudgetIsReducedAndProtectionReserveIsPreserved() {
        assertEquals(120, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE)
        assertEquals(4, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND)
        assertTrue(GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE < GrowwApiBudgetPolicy.LIVE_DATA_HARD_PER_MINUTE)
        assertEquals(2, GrowwApiBudgetPolicy.fixedLiveDataPerSecond(true))
        assertEquals(60, GrowwApiBudgetPolicy.fixedLiveDataPerMinute(true))
        assertEquals(2, GrowwApiBudgetPolicy.fixedLiveDataPerSecond(false))
        assertEquals(60, GrowwApiBudgetPolicy.fixedLiveDataPerMinute(false))
        assertEquals(2, GrowwApiBudgetPolicy.SELL_MONITOR_RESERVED_PER_SECOND)
        assertEquals(60, GrowwApiBudgetPolicy.SELL_MONITOR_RESERVED_PER_MINUTE)
    }
    @Test fun independentOrderNonTradingAndAuthBudgetsUnchanged() {
        assertEquals(150, GrowwApiBudgetPolicy.APP_ORDER_PER_MINUTE)
        assertEquals(360, GrowwApiBudgetPolicy.APP_NON_TRADING_PER_MINUTE)
        assertEquals(20, GrowwApiBudgetPolicy.APP_AUTH_PER_MINUTE)
    }
}
