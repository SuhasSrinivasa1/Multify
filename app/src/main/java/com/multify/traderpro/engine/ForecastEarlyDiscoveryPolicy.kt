package com.multify.traderpro.engine

/** Market-wide early-momentum screen; all NSE symbols are first sampled by bulk LTP.
 * Unlike the Fixed momentum ranker, do not favor the largest already-spiked movers.
 */
object ForecastEarlyDiscoveryPolicy {
    fun rank(current: Map<String, Double>, previous: Map<String, Double>, sessionOpen: Map<String, Double>, limit: Int): List<MarketDiscoveryCandidate> {
        if (limit <= 0) return emptyList()
        return current.mapNotNull { (symbol, ltp) ->
            val before = previous[symbol]?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            if (!ltp.isFinite() || ltp < 10.0) return@mapNotNull null
            val pulse = (ltp / before - 1.0) * 100.0
            val open = sessionOpen[symbol]?.takeIf { it > 0.0 } ?: before
            val session = (ltp / open - 1.0) * 100.0
            if (pulse !in -0.35..0.85 || session !in -2.0..5.0) return@mapNotNull null
            // Mild positive pulses and stable retests are prioritized before mature spikes.
            val score = 2.0 - kotlin.math.abs(pulse - 0.18) * 2.0 + session.coerceIn(-1.0, 2.0) * 0.08
            MarketDiscoveryCandidate(symbol, ltp, pulse, session, score)
        }.sortedWith(compareByDescending<MarketDiscoveryCandidate> { it.score }.thenBy { it.symbol }).take(limit)
    }
}
