package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class ForecastNotificationFreshnessPolicyTest {
    @Test fun neverDeliverOldOrFutureDatedTradeSignal() {
        assertFalse(ForecastNotificationFreshnessPolicy.fresh(0,100L))
        assertFalse(ForecastNotificationFreshnessPolicy.fresh(105,104))
        assertTrue(ForecastNotificationFreshnessPolicy.fresh(1000,121_000))
        assertFalse(ForecastNotificationFreshnessPolicy.fresh(1000,121_001))
    }
}
