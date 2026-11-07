package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload

/**
 * BUY-only circuit guard. Uses one contemporaneous broker quote; never scans the market,
 * relaxes strategy selection or touches the existing market SELL and GTT protection.
 *
 * The BUY limit is the lowest currently displayed sell offer itself. No slippage premium
 * is added and the circuit ceiling is never substituted as an entry price. If that offer
 * moves before the order reaches the exchange, the limit will not chase it upward; the
 * caller cancels and reconciles any unfilled remainder.
 */
object CircuitAwareBuyPolicy {
    const val MAX_QUOTE_AGE_MS = 8_000L

    /** Audited premium over the best available offer. Zero means best-offer-only execution. */
    const val MAX_BUY_SLIPPAGE_BPS = 0.0

    data class Decision(
        val limitPrice: Double,
        val upperCircuit: Double,
        val bestOffer: Double,
        val lastPrice: Double,
        val quoteAgeMs: Long
    )

    fun decide(quote: QuotePayload, receivedAtMs: Long, nowMs: Long): Decision {
        val age = nowMs - receivedAtMs
        require(receivedAtMs > 0L && age in 0L..MAX_QUOTE_AGE_MS) {
            "BUY_QUOTE_STALE"
        }

        fun positive(value: Double?): Double? =
            value?.takeIf { it.isFinite() && it > 0.0 }

        val upper = positive(quote.upperCircuitLimit)
            ?: error("BUY_UPPER_CIRCUIT_UNKNOWN")
        val lower = positive(quote.lowerCircuitLimit)
        require(lower == null || lower <= upper) { "BUY_INVALID_CIRCUIT_RANGE" }

        val last = positive(quote.lastPrice) ?: error("BUY_MISSING_LTP")
        require(last <= upper + 1e-9) { "BUY_LTP_ABOVE_CIRCUIT" }
        require(lower == null || last >= lower - 1e-9) { "BUY_LTP_BELOW_CIRCUIT" }

        // Groww's offer_price is the top ask. Also inspect depth.sell and take the
        // minimum valid offer so a populated depth book is never ignored.
        val sellOffers = buildList {
            positive(quote.offerPrice)?.let(::add)
            quote.depth?.sell.orEmpty().forEach { level ->
                positive(level.price)?.let(::add)
            }
        }
        val bestOffer = sellOffers.minOrNull()
            ?: error("BUY_BEST_OFFER_UNKNOWN")

        require(bestOffer <= upper + 1e-9) { "BUY_OFFER_ABOVE_CIRCUIT" }
        require(lower == null || bestOffer >= lower - 1e-9) {
            "BUY_OFFER_BELOW_CIRCUIT"
        }

        // Do not round up, add a buffer, or replace the ask with the upper circuit.
        // The quote's displayed exchange offer is already an exchange-valid tick price.
        val limit = bestOffer
        require(limit.isFinite() && limit > 0.0) { "BUY_INVALID_LIMIT_PRICE" }
        require(limit <= upper + 1e-9) { "BUY_CIRCUIT_CAP_VIOLATION" }

        return Decision(limit, upper, bestOffer, last, age)
    }
}
