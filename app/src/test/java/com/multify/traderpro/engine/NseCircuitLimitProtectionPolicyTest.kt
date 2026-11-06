package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NseCircuitLimitProtectionPolicyTest {
    @Test fun screenshotMarketProtectionPriceIsBlockedBeforeOrderSubmission() {
        // 227.70 + 5% = 239.085, rounded upward to ₹239.09.
        // The exchange upper circuit shown in the Groww rejection is ₹233.38.
        val verdict = NseCircuitLimitProtectionPolicy.evaluate(
            lastPrice = 227.70,
            upperCircuitLimit = 233.38,
            lowerCircuitLimit = 155.60
        )
        assertFalse(verdict.allowed)
        assertEquals("MARKET_PROTECTION_EXCEEDS_UPPER_CIRCUIT", verdict.reasonCode)
        assertEquals(239.09, verdict.estimatedProtectedBuyPrice!!, 0.0001)
        assertEquals(233.38, verdict.upperCircuitLimit!!, 0.0001)
    }

    @Test fun allowsMarketBuyWhenConservativeProtectionFitsInsideCircuit() {
        val verdict = NseCircuitLimitProtectionPolicy.evaluate(
            lastPrice = 220.00,
            upperCircuitLimit = 233.38,
            lowerCircuitLimit = 155.60
        )
        assertTrue(verdict.allowed)
        assertEquals("CIRCUIT_CHECK_PASSED", verdict.reasonCode)
        assertEquals(231.00, verdict.estimatedProtectedBuyPrice!!, 0.0001)
    }

    @Test fun missingUpperCircuitFailsClosed() {
        val verdict = NseCircuitLimitProtectionPolicy.evaluate(
            lastPrice = 220.00,
            upperCircuitLimit = null
        )
        assertFalse(verdict.allowed)
        assertEquals("UPPER_CIRCUIT_UNAVAILABLE", verdict.reasonCode)
    }

    @Test fun invalidOrOutOfBandQuotesFailClosed() {
        assertEquals(
            "LAST_PRICE_UNAVAILABLE",
            NseCircuitLimitProtectionPolicy.evaluate(Double.NaN, 233.38).reasonCode
        )
        assertEquals(
            "CIRCUIT_RANGE_INVALID",
            NseCircuitLimitProtectionPolicy.evaluate(220.00, 200.00, 210.00).reasonCode
        )
        assertEquals(
            "LAST_PRICE_OUTSIDE_CIRCUIT",
            NseCircuitLimitProtectionPolicy.evaluate(234.00, 233.38, 155.60).reasonCode
        )
    }
}
