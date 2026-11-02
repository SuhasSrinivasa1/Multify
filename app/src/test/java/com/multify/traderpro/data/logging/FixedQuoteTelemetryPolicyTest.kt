package com.multify.traderpro.data.logging

import com.multify.traderpro.data.network.QuoteDepthLevel
import com.multify.traderpro.data.network.QuoteMarketDepth
import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedQuoteTelemetryPolicyTest {
    @Test fun logsRestingBookAndTradedVolumeSeparately() {
        val q = QuotePayload(
            lastPrice = 243.2,
            bidPrice = 243.1,
            offerPrice = 243.4,
            bidQuantity = 150.0,
            offerQuantity = 120.0,
            totalBuyQuantity = 2200.0,
            totalSellQuantity = 1800.0,
            volume = 47000L,
            depth = QuoteMarketDepth(
                buy = listOf(QuoteDepthLevel(243.1, 150.0)),
                sell = listOf(QuoteDepthLevel(243.4, 120.0))
            )
        )
        val row = FixedQuoteTelemetryPolicy.capture(q)
        assertEquals(243.1, row["bid_price"])
        assertEquals(243.4, row["ask_price"])
        assertEquals(150.0, row["bid_quantity"])
        assertEquals(120.0, row["ask_quantity"])
        assertEquals(2200.0, row["total_buy_quantity"])
        assertEquals(1800.0, row["total_sell_quantity"])
        assertEquals(47000L, row["day_traded_volume"])
        assertEquals("RESTING_NOT_EXECUTED", row["order_book_quantity_kind"])
        assertEquals("VALID_BOOK", row["quote_book_status"])
        assertEquals(true, row["spread_verified"])
        assertEquals(true, row["volume_available"])
        assertNull(row["executed_buy_volume"])
        assertNull(row["executed_sell_volume"])
        assertEquals(1, row["depth_buy_levels_available"])
        assertTrue((row["bid_ask_spread_bps"] as Double) > 0)
    }

    @Test fun unavailableValuesRemainUnobserved() {
        val row = FixedQuoteTelemetryPolicy.capture(QuotePayload(lastPrice = 210.0))
        assertTrue(row["quote_available"] == true)
        assertNull(row["bid_price"])
        assertNull(row["ask_price"])
        assertNull(row["day_traded_volume"])
        assertEquals("MISSING_BID_OR_ASK", row["quote_book_status"])
        assertEquals(false, row["spread_verified"])
        assertEquals(false, row["volume_available"])
        assertEquals("UNAVAILABLE", row["bid_source"])
        assertEquals("UNAVAILABLE", row["ask_source"])
        assertFalse(FixedQuoteTelemetryPolicy.capture(null)["quote_available"] as Boolean)
    }
}
