package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketPriorityPolicyTest {
    @Test
    fun fixedAndCriticalTradingAlwaysBlockForecast() {
        assertFalse(MarketPriorityPolicy.canRunForecast(true, false, 90_000, 0, 210, 0, 180, 0, 360))
        assertFalse(MarketPriorityPolicy.canRunForecast(false, true, 90_000, 0, 210, 0, 180, 0, 360))
    }

    @Test
    fun forecastStopsBeforeNextFixedWindow() {
        assertFalse(MarketPriorityPolicy.canRunForecast(false, false, 30_000, 0, 210, 0, 180, 0, 360))
        assertTrue(MarketPriorityPolicy.canRunForecast(false, false, 90_000, 20, 210, 10, 180, 20, 360))
    }

    @Test
    fun fiveCompletedSessionsUnlockLiveForecast() {
        assertFalse(MarketPriorityPolicy.liveForecastUnlocked(4))
        assertTrue(MarketPriorityPolicy.liveForecastUnlocked(5))
    }
}
