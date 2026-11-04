package com.multify.traderpro.engine

/** BUY-only protection. Never changes a Fixed/Forecast recommendation, target or SELL rule.
 * This is a broker-response observation, not a provider exchange timestamp. */
object ExecutionEntryPriceGuardPolicy {
    const val MAX_EXTENSION_PCT = 0.40
    const val MAX_REQUEST_ELAPSED_MS = 10_000L
    fun rejectionReason(entryPrice: Double, freshPrice: Double, requestElapsedMs: Long): String? {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 || !freshPrice.isFinite() || freshPrice <= 0.0)
            return "INVALID_PRICE"
        if (requestElapsedMs < 0L || requestElapsedMs > MAX_REQUEST_ELAPSED_MS)
            return "SLOW_OR_STALE_BROKER_RESPONSE"
        val extension = (freshPrice / entryPrice - 1.0) * 100.0
        return if (extension > MAX_EXTENSION_PCT) "PRICE_EXTENSION_ABOVE_0_40_PCT" else null
    }
}
