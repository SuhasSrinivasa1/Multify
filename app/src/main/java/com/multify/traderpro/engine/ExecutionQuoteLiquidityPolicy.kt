package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload

/**
 * Execution-only broker-book preflight. Does not alter Fixed factor calculation,
 * ranking, research publications, exit monitoring or SELL/trailing behavior.
 *
 * Unknown bid/ask is NEVER treated as a zero spread.
 */
object ExecutionQuoteLiquidityPolicy {
    const val MAX_ENTRY_SPREAD_BPS = 40.0

    fun rejectionReason(quote: QuotePayload): String? {
        fun valid(x: Double?): Double? = x?.takeIf { it.isFinite() && it > 0.0 }
        val bid = valid(quote.bidPrice) ?: quote.depth?.buy.orEmpty()
            .mapNotNull { valid(it.price) }.maxOrNull()
        val ask = valid(quote.offerPrice) ?: quote.depth?.sell.orEmpty()
            .mapNotNull { valid(it.price) }.minOrNull()
        if (bid == null || ask == null) return "MISSING_BID_OR_ASK"
        if (ask < bid) return "CROSSED_MARKET"
        val spread = (ask - bid) / ((bid + ask) / 2.0) * 10_000.0
        return if (!spread.isFinite() || spread > MAX_ENTRY_SPREAD_BPS) "SPREAD_TOO_WIDE" else null
    }
}
