package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload

/** Working Range BUY-only turnover guard; it does not alter Fixed selection or any SELL path. */
internal object WorkingRangeExecutionLiquidityPolicy {
    const val MIN_DAY_SHARES = 1_500L
    const val MIN_DAY_TURNOVER_RUPEES = 1_500_000.0

    fun rejectionReason(quote: QuotePayload): String? {
        val price = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val volume = quote.volume?.takeIf { it >= 0L } ?: return null
        return if (volume < MIN_DAY_SHARES &&
            price * volume.toDouble() < MIN_DAY_TURNOVER_RUPEES
        ) "EXTREMELY_LOW_REPORTED_TURNOVER" else null
    }
}
