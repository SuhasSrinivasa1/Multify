package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload

/** Observational UI marketability, based ONLY on a quote already held by Fixed.
 * Current client receipt != verified exchange tick age. No extra broker calls.
 * The existing CircuitAwareBuyPolicy remains the authoritative BUY preflight. */
object FixedQuoteMarketabilityPresentationPolicy {
    const val CLIENT_RECEIPT_WINDOW_MS = 8_000L
    data class Result(
        val state: String,
        val brokerUpperCircuit: Double? = null,
        val bestObservedOffer: Double? = null,
        val quoteClientReceivedAtMs: Long? = null,
        val clientReceiptAgeMs: Long? = null
    )
    fun assess(quote: QuotePayload?, receivedAtMs: Long?, nowMs: Long): Result {
        if (quote == null || receivedAtMs == null || receivedAtMs <= 0L)
            return Result("NO_RECENT_QUOTE")
        val age = nowMs - receivedAtMs
        if (age < 0 || age > CLIENT_RECEIPT_WINDOW_MS)
            return Result("STALE_CLIENT_QUOTE", quoteClientReceivedAtMs = receivedAtMs,
                clientReceiptAgeMs = age)
        fun pos(x: Double?): Double? = x?.takeIf { it.isFinite() && it > 0.0 }
        val last = pos(quote.lastPrice)
        val upper = pos(quote.upperCircuitLimit)
        val asks = buildList {
            pos(quote.offerPrice)?.let(::add)
            quote.depth?.sell.orEmpty().forEach { pos(it.price)?.let(::add) }
        }
        val offer = asks.minOrNull()
        val state = when {
            offer == null && last != null && upper != null &&
                last >= upper - maxOf(0.01, upper * 0.0002) ->
                "UPPER_BAND_NO_SELL_OFFER"
            offer == null -> "NO_SELL_OFFER_OBSERVED"
            upper != null && offer > upper + 0.000001 -> "OFFER_OUTSIDE_CIRCUIT"
            else -> "SELL_OFFER_OBSERVED_NOT_FILL_GUARANTEE"
        }
        return Result(state, upper, offer, receivedAtMs, age)
    }
}
