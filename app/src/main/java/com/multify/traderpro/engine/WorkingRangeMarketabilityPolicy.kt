package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload

/** Working Range's own presentation-only quote assessment; never substitutes for Alpha execution preflight. */
internal object WorkingRangeMarketabilityPolicy {
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
        if (age < 0L || age > CLIENT_RECEIPT_WINDOW_MS)
            return Result("STALE_CLIENT_QUOTE", quoteClientReceivedAtMs = receivedAtMs,
                clientReceiptAgeMs = age)
        fun positive(value: Double?): Double? = value?.takeIf { it.isFinite() && it > 0.0 }
        val last = positive(quote.lastPrice)
        val upper = positive(quote.upperCircuitLimit)
        val offers = buildList {
            positive(quote.offerPrice)?.let(::add)
            quote.depth?.sell.orEmpty().forEach { positive(it.price)?.let(::add) }
        }
        val offer = offers.minOrNull()
        val state = when {
            offer == null && last != null && upper != null &&
                last >= upper - maxOf(0.01, upper * 0.0002) -> "UPPER_BAND_NO_SELL_OFFER"
            offer == null -> "NO_SELL_OFFER_OBSERVED"
            upper != null && offer > upper + 0.000001 -> "OFFER_OUTSIDE_CIRCUIT"
            else -> "SELL_OFFER_OBSERVED_NOT_FILL_GUARANTEE"
        }
        return Result(state, upper, offer, receivedAtMs, age)
    }
}
