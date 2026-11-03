package com.multify.traderpro.data.network

import java.util.Locale

/**
 * Diagnostic-only HTTP timing for Alpha NSE quotes. This is the *client* response time,
 * NOT exchange quote age or the provider's source timestamp. Never used for trading.
 */
internal object FixedQuoteTimingRegistry {
    data class Sample(
        val symbol: String,
        val requestStartedAtMs: Long,
        val responseReceivedAtMs: Long,
        val networkElapsedMs: Long,
        val httpStatus: Int,
        val sourceTimestampAvailable: Boolean = false
    )
    private val lock = Any()
    private val latestBySymbol = LinkedHashMap<String, Sample>()
    private const val MAX_SYMBOLS = 512
    private const val MATCH_WINDOW_MS = 15_000L

    fun record(
        symbol: String?, requestStartedAtMs: Long, responseReceivedAtMs: Long,
        networkElapsedMs: Long, httpStatus: Int
    ) {
        val cleaned = symbol?.trim()?.uppercase(Locale.US)?.takeIf {
            it.matches(Regex("[A-Z0-9&_.-]{1,40}"))
        } ?: return
        if (requestStartedAtMs <= 0L || responseReceivedAtMs < requestStartedAtMs) return
        val sample = Sample(cleaned, requestStartedAtMs, responseReceivedAtMs,
            networkElapsedMs.coerceAtLeast(0L), httpStatus)
        synchronized(lock) {
            latestBySymbol.remove(cleaned)
            latestBySymbol[cleaned] = sample
            while (latestBySymbol.size > MAX_SYMBOLS) {
                val first = latestBySymbol.keys.firstOrNull() ?: break
                latestBySymbol.remove(first)
            }
        }
    }

    fun closestCompletedQuote(symbol: String, evaluationCompletedAtMs: Long): Sample? = synchronized(lock) {
        val sample = latestBySymbol[symbol.uppercase(Locale.US)] ?: return@synchronized null
        if (sample.httpStatus !in 200..299 ||
            evaluationCompletedAtMs < sample.responseReceivedAtMs ||
            evaluationCompletedAtMs - sample.responseReceivedAtMs > MATCH_WINDOW_MS) null
        else sample
    }
}
