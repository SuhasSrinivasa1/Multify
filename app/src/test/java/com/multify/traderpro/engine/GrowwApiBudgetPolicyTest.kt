package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class GrowwApiBudgetPolicyTest {
    @Test
    fun appUsesNinetyFivePercentMinuteCeiling() {
        assertEquals(285, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE)
        assertEquals(9, GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND)
    }

    @Test
    fun fixedKeepsSellMonitorReserveWhenHoldingsAreOpen() {
        assertEquals(8, GrowwApiBudgetPolicy.fixedLiveDataPerSecond(true))
        assertEquals(225, GrowwApiBudgetPolicy.fixedLiveDataPerMinute(true))
    }

    @Test
    fun fixedGetsFullAppBudgetWhenNoHoldingsAreOpen() {
        assertEquals(9, GrowwApiBudgetPolicy.fixedLiveDataPerSecond(false))
        assertEquals(285, GrowwApiBudgetPolicy.fixedLiveDataPerMinute(false))
    }
}
