package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class ForecastNewsPublicationGateTest {
    @Test fun newsPublicationCannotBypassSharedPriceGate() {
        for (price in listOf(null, 15.79, 200.0, 5_000.0, 5_001.0, Double.NaN)) {
            assertFalse(ForecastNewsPublicationGate.mayPublish(price, true, false))
            assertEquals("OUTSIDE_200_5000_EXCLUSIVE_PRICE_BAND", ForecastNewsPublicationGate.priceRejection(price))
        }
        assertTrue(ForecastNewsPublicationGate.mayPublish(200.01, true, false))
        assertTrue(ForecastNewsPublicationGate.mayPublish(4_999.99, true, false))
        assertFalse(ForecastNewsPublicationGate.mayPublish(400.0, false, false))
        assertFalse(ForecastNewsPublicationGate.mayPublish(400.0, true, true))
    }
}
