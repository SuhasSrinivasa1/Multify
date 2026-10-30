package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class AutoBuyEntryPricePolicyTest {
    @Test fun recommendationPriceAndSmallPositiveSlippageAreAccepted() {
        assertTrue(AutoBuyEntryPricePolicy.evaluate(100.0, 100.0).allowed)
        assertTrue(AutoBuyEntryPricePolicy.evaluate(100.0, 100.35).allowed)
    }

    @Test fun excessivePositiveSlippageIsRejectedBeforeAnyOrderSubmission() {
        val decision = AutoBuyEntryPricePolicy.evaluate(100.0, 100.36)
        assertFalse(decision.allowed)
        assertTrue(decision.deviationPct > AutoBuyEntryPricePolicy.MAX_POSITIVE_SLIPPAGE_PCT)
    }

    @Test fun lowerEntryPriceRemainsEligible() {
        assertTrue(AutoBuyEntryPricePolicy.evaluate(100.0, 99.0).allowed)
    }

    @Test fun invalidPricesAreRejected() {
        assertFalse(AutoBuyEntryPricePolicy.evaluate(0.0, 100.0).allowed)
        assertFalse(AutoBuyEntryPricePolicy.evaluate(100.0, Double.NaN).allowed)
    }
}
