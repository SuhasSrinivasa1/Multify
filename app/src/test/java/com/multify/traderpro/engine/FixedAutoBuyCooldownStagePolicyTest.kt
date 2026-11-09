package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedAutoBuyCooldownStagePolicyTest {
    @Test fun quoteStageRequiresFreshQuoteAndOwnershipServices() {
        val categories = FixedAutoBuyCooldownStagePolicy.requiredCategories(
            FixedAutoBuyCooldownStagePolicy.Stage.BEFORE_QUOTE
        )
        assertEquals(setOf(
            FixedAutoBuyCooldownStagePolicy.ApiCategory.LIVE_DATA,
            FixedAutoBuyCooldownStagePolicy.ApiCategory.NON_TRADING
        ), categories)
    }

    @Test fun quoteFailureOnlyChecksTheQuoteEndpoint() {
        assertEquals(
            setOf(FixedAutoBuyCooldownStagePolicy.ApiCategory.LIVE_DATA),
            FixedAutoBuyCooldownStagePolicy.requiredCategories(
                FixedAutoBuyCooldownStagePolicy.Stage.AFTER_QUOTE_FAILURE
            )
        )
    }

    @Test fun unrelatedLiveDataCooldownCannotBlockOrderAfterFreshQuote() {
        val categories = FixedAutoBuyCooldownStagePolicy.requiredCategories(
            FixedAutoBuyCooldownStagePolicy.Stage.BEFORE_ORDER
        )
        assertTrue(FixedAutoBuyCooldownStagePolicy.ApiCategory.NON_TRADING in categories)
        assertTrue(FixedAutoBuyCooldownStagePolicy.ApiCategory.ORDERS in categories)
        assertFalse(FixedAutoBuyCooldownStagePolicy.ApiCategory.LIVE_DATA in categories)
    }
}
