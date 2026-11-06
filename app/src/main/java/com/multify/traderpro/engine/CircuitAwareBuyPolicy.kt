package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/**
 * BUY-only circuit guard. Uses one contemporaneous broker quote; never scans the market,
 * relaxes strategy selection or touches the existing market SELL and GTT protection.
 * Rejects missing/stale circuit information instead of submitting a protected MARKET
 * order whose implied upper price could breach the NSE operating range.
 */
object CircuitAwareBuyPolicy {
    const val MAX_QUOTE_AGE_MS = 8_000L
    const val MAX_BUY_SLIPPAGE_BPS = 15.0

    data class Decision(val limitPrice: Double, val upperCircuit: Double,
                        val bestOffer: Double?, val lastPrice: Double, val quoteAgeMs: Long)

    fun decide(quote: QuotePayload, receivedAtMs: Long, nowMs: Long): Decision {
        val age = nowMs - receivedAtMs
        require(receivedAtMs > 0L && age in 0L..MAX_QUOTE_AGE_MS) {
            "BUY_QUOTE_STALE"
        }
        fun positive(x: Double?): Double? = x?.takeIf { it.isFinite() && it > 0.0 }
        val upper = positive(quote.upperCircuitLimit)
            ?: error("BUY_UPPER_CIRCUIT_UNKNOWN")
        val lower = positive(quote.lowerCircuitLimit)
        require(lower == null || lower <= upper) { "BUY_INVALID_CIRCUIT_RANGE" }
        val last = positive(quote.lastPrice) ?: error("BUY_MISSING_LTP")
        require(last <= upper + 0.001) { "BUY_LTP_ABOVE_CIRCUIT" }
        val ask = positive(quote.offerPrice) ?: quote.depth?.sell.orEmpty()
            .mapNotNull { positive(it.price) }.minOrNull()
        require(ask == null || ask <= upper + 0.001) { "BUY_OFFER_ABOVE_CIRCUIT" }
        // A best offer is an exchange-accepted tick price. Where both broker values are
        // multiples of 5 paisa, stay on 5-paisa ticks; otherwise use 1-paisa ticks.
        val tick = if (ask != null && isFivePaisaTick(ask) && isFivePaisaTick(upper)) 0.05 else 0.01
        val anchor = ask ?: last
        val cap = min(upper, anchor * (1.0 + MAX_BUY_SLIPPAGE_BPS / 10_000.0))
        val rawLimit = floor(cap / tick + 1e-7) * tick
        val limit = round(rawLimit * 100.0) / 100.0
        require(limit.isFinite() && limit > 0.0) { "BUY_INVALID_LIMIT_PRICE" }
        require(ask == null || limit + 1e-6 >= ask) { "BUY_NO_MARKETABLE_PRICE_WITHIN_CIRCUIT" }
        require(limit <= upper + 1e-6) { "BUY_CIRCUIT_CAP_VIOLATION" }
        return Decision(limit, upper, ask, last, age)
    }

    private fun isFivePaisaTick(value: Double): Boolean =
        abs(value / 0.05 - round(value / 0.05)) <= 1e-5
}
