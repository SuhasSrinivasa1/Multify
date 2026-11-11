package com.multify.traderpro.engine

/**
 * Observational latency cohorts for after-market research only.
 * A client HTTP response timestamp is NOT an exchange tick or executable fill.
 * Never use these labels in Fixed admission, BUY, SELL, or GTT decisions.
 */
object FixedDelayResearchPolicy {
    fun bucket(clientQuoteToNotificationMs: Long?): String = when {
        clientQuoteToNotificationMs == null || clientQuoteToNotificationMs < 0 -> "UNVERIFIED"
        clientQuoteToNotificationMs <= 10_000L -> "0_TO_10_SECONDS"
        clientQuoteToNotificationMs <= 30_000L -> "10_TO_30_SECONDS"
        clientQuoteToNotificationMs <= 60_000L -> "30_TO_60_SECONDS"
        else -> "OVER_60_SECONDS"
    }
}
