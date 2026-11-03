package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.data.network.QuoteMarketDepth
import com.multify.traderpro.data.network.QuoteDepthLevel
import org.junit.Assert.*
import org.junit.Test

class FixedMarketQualityShadowPolicyTest {
    @Test fun badLiquidityVisibleWithoutChangingFixed() {
        val quote = QuotePayload(lastPrice = 100.0, bidPrice = 99.80,
            offerPrice = 100.50, volume = 26L, high = 101.0)
        val row = FixedMarketQualityShadowPolicy.assess(quote, 97.0)
        assertEquals("VALID_BOOK", row.bookStatus)
        assertTrue(row.observedSpreadBps!! > 40.0)
        assertEquals(26L, row.dayTradedShares)
        assertTrue(row.appreciationSinceFirstPrimaryPct!! > 2.0)
        assertTrue("WIDE_SPREAD_SHADOW" in row.warnings)
        assertTrue("LOW_ABSOLUTE_VOLUME_SHADOW" in row.warnings)
        assertTrue("PRICE_CHASING_SHADOW" in row.warnings)
        assertEquals(2600.0, row.estimatedTradedValueRupees!!, 1.0)
    }
    @Test fun extremeDayMoveIsFlaggedEvenWhenLiquidityLooksGood() {
        val quote = QuotePayload(lastPrice = 278.0, bidPrice = 277.95,
            offerPrice = 278.05, volume = 69_579_373L,
            dayChangePct = 85.9867, high = 290.0)
        val row = FixedMarketQualityShadowPolicy.assess(quote)
        assertTrue(row.observedSpreadBps!! < 40.0)
        assertEquals(85.9867, row.brokerReportedDayChangePct!!, 0.0001)
        assertTrue("EXTREME_DAY_RISE_SHADOW" in row.warnings)
        assertTrue("ALREADY_UP_OVER_5PCT_SHADOW" in row.warnings)
        assertFalse("LOW_ABSOLUTE_VOLUME_SHADOW" in row.warnings)
    }

    @Test fun crossedAndMissingBooksAreNeverZeroSpread() {
        val crossed = FixedMarketQualityShadowPolicy.assess(
            QuotePayload(lastPrice = 100.0, bidPrice = 101.0, offerPrice = 100.0))
        assertEquals("CROSSED_MARKET", crossed.bookStatus)
        assertNull(crossed.observedSpreadBps)
        val missing = FixedMarketQualityShadowPolicy.assess(QuotePayload(lastPrice = 100.0))
        assertEquals("BID_ASK_MISSING", missing.bookStatus)
        assertNull(missing.observedSpreadBps)
    }
    @Test fun depthIsResearchFallbackOnly() {
        val row = FixedMarketQualityShadowPolicy.assess(QuotePayload(lastPrice = 100.0,
            depth = QuoteMarketDepth(
                buy = listOf(QuoteDepthLevel(price = 99.0, quantity = 10.0)),
                sell = listOf(QuoteDepthLevel(price = 101.0, quantity = 10.0)))))
        assertEquals("VALID_BOOK", row.bookStatus)
        assertTrue(row.observedSpreadBps!! > 40.0)
    }
}
