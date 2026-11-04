package com.multify.traderpro.engine

/** Read-only pulse detection from two independently collected full-NSE Fixed LTP
 * snapshots. Neither snapshot is assumed to be the NSE 9:15 opening print.
 * There are no broker requests or BUY decisions in this policy.
 */
object FixedMarketPulsePolicy {
    const val MIN_PULSE_PCT = 0.30
    const val MAX_PULSE_PCT = 4.00
    const val MAX_SCAN_GAP_MS = 15L * 60_000L
    const val MAX_ROWS_PER_SCAN = 20

    data class Pulse(val symbol: String, val firstPrice: Double, val latestPrice: Double,
                     val increasePct: Double)

    fun rank(
        previous: Map<String, Double>, current: Map<String, Double>,
        previousAtMs: Long, currentAtMs: Long,
        limit: Int = MAX_ROWS_PER_SCAN
    ): List<Pulse> {
        if (previousAtMs <= 0L || currentAtMs <= previousAtMs ||
            currentAtMs - previousAtMs > MAX_SCAN_GAP_MS) return emptyList()
        return current.entries.asSequence().mapNotNull { (symbol, newPrice) ->
            val old = previous[symbol] ?: return@mapNotNull null
            if (!old.isFinite() || !newPrice.isFinite() || old <= 0.0 || newPrice <= 0.0 ||
                !RecommendationPriceBandPolicy.eligible(newPrice)) return@mapNotNull null
            val pct = (newPrice / old - 1.0) * 100.0
            if (pct !in MIN_PULSE_PCT..MAX_PULSE_PCT) return@mapNotNull null
            Pulse(symbol.uppercase(), old, newPrice, pct)
        }.sortedWith(compareByDescending<Pulse> { it.increasePct }.thenBy { it.symbol })
            .take(limit.coerceIn(0, MAX_ROWS_PER_SCAN))
            .toList()
    }
}
