package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import com.multify.traderpro.data.network.QuoteMarketDepth
import com.multify.traderpro.data.network.QuoteDepthLevel
import org.junit.Assert.*
import org.junit.Test

class ExecutionQuoteLiquidityPolicyTest {
    @Test fun neverTreatMissingSpreadAsZero() {
        assertEquals("MISSING_BID_OR_ASK", ExecutionQuoteLiquidityPolicy.rejectionReason(QuotePayload(lastPrice=500.0)))
        assertEquals("CROSSED_MARKET", ExecutionQuoteLiquidityPolicy.rejectionReason(QuotePayload(bidPrice=502.0, offerPrice=501.0)))
        assertEquals("SPREAD_TOO_WIDE", ExecutionQuoteLiquidityPolicy.rejectionReason(QuotePayload(bidPrice=500.0, offerPrice=505.0)))
        assertNull(ExecutionQuoteLiquidityPolicy.rejectionReason(QuotePayload(bidPrice=500.0, offerPrice=500.4)))
    }
    @Test fun depthQuoteIsAcceptedWhenTopOfBookMissing() {
        val q = QuotePayload(depth=QuoteMarketDepth(
            buy=listOf(QuoteDepthLevel(500.0,100.0)),
            sell=listOf(QuoteDepthLevel(500.2,100.0))
        ))
        assertNull(ExecutionQuoteLiquidityPolicy.rejectionReason(q))
    }
}
