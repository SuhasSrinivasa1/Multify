package com.multify.traderpro.data.logging

import com.multify.traderpro.data.network.QuotePayload

/**
 * Read-only quote observation: buy/sell quantities are RESTING order-book demand/supply,
 * not completed buy/sell trades. Missing broker values remain null.
 */
internal object FixedQuoteTelemetryPolicy {
    fun capture(q: QuotePayload?): Map<String, Any?> {
        if (q == null) return mapOf("quote_available" to false)
        fun clean(v: Double?): Double? = v?.takeIf { it.isFinite() && it > 0.0 }
        val bid = clean(q.bidPrice) ?: q.depth?.buy.orEmpty().mapNotNull { clean(it.price) }.maxOrNull()
        val ask = clean(q.offerPrice) ?: q.depth?.sell.orEmpty().mapNotNull { clean(it.price) }.minOrNull()
        fun levels(buy: Boolean): List<Map<String, Double?>> =
            (if (buy) q.depth?.buy.orEmpty() else q.depth?.sell.orEmpty()).take(5).map {
                mapOf("price" to clean(it.price), "resting_quantity" to clean(it.quantity))
            }
        val quoteBookStatus = when {
            bid == null || ask == null -> "MISSING_BID_OR_ASK"
            ask < bid -> "CROSSED_MARKET"
            else -> "VALID_BOOK"
        }
        val spreadBps = if (bid != null && ask != null && bid > 0.0 && ask >= bid)
            (ask - bid) / ((ask + bid) / 2.0) * 10_000.0 else null
        return mapOf(
            "quote_available" to true,
            "quote_book_status" to quoteBookStatus,
            "bid_source" to when {
                clean(q.bidPrice) != null -> "BROKER_BID_FIELD"
                bid != null -> "MARKET_DEPTH_FALLBACK"
                else -> "UNAVAILABLE"
            },
            "ask_source" to when {
                clean(q.offerPrice) != null -> "BROKER_OFFER_FIELD"
                ask != null -> "MARKET_DEPTH_FALLBACK"
                else -> "UNAVAILABLE"
            },
            "spread_verified" to (quoteBookStatus == "VALID_BOOK"),
            "volume_available" to (q.volume != null),
            "bid_ask_missing_is_not_zero" to true,
            "last_price" to clean(q.lastPrice),
            "bid_price" to bid, "ask_price" to ask,
            "bid_quantity" to clean(q.bidQuantity),
            "ask_quantity" to clean(q.offerQuantity),
            "total_buy_quantity" to clean(q.totalBuyQuantity),
            "total_sell_quantity" to clean(q.totalSellQuantity),
            "bid_ask_spread_bps" to spreadBps,
            "market_depth_buy" to levels(true), "market_depth_sell" to levels(false),
            "depth_buy_levels_available" to (q.depth?.buy?.size ?: 0),
            "depth_sell_levels_available" to (q.depth?.sell?.size ?: 0),
            "day_traded_volume" to q.volume,
            "day_change_pct" to clean(q.dayChangePct),
            "order_book_quantity_kind" to "RESTING_NOT_EXECUTED",
            "executed_buy_volume" to null,
            "executed_sell_volume" to null
        )
    }
}
