package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowwApiBudgetPolicyTest {
    @Test fun liveDataKeepsHeadroom() {
        assertEquals(210, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE)
        assertEquals(7, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND)
        assertTrue(GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE < GrowwApiBudgetPolicy.LIVE_DATA_HARD_PER_MINUTE)
    }
    @Test fun fixedKeepsSellMonitorReserve() {
        assertEquals(6, GrowwApiBudgetPolicy.fixedLiveDataPerSecond(true))
        assertEquals(150, GrowwApiBudgetPolicy.fixedLiveDataPerMinute(true))
    }
    @Test fun otherBucketsKeepHeadroom() {
        assertEquals(150, GrowwApiBudgetPolicy.APP_ORDER_PER_MINUTE)
        assertEquals(360, GrowwApiBudgetPolicy.APP_NON_TRADING_PER_MINUTE)
        assertEquals(20, GrowwApiBudgetPolicy.APP_AUTH_PER_MINUTE)
    }
}
