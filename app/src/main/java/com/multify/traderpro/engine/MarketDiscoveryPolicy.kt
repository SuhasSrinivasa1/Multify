package com.multify.traderpro.engine

data class MarketDiscoveryCandidate(
    val symbol: String,
    val ltp: Double,
    val pulsePct: Double,
    val sessionPct: Double,
    val score: Double
)

/**
 * Lightweight market-wide prefilter. It is deliberately independent from ARM/execution.
 * The full 40-factor model still decides whether a discovered name is publishable.
 */
object MarketDiscoveryPolicy {
    fun rank(
        currentLtps: Map<String, Double>,
        previousLtps: Map<String, Double>,
        sessionOpenLtps: Map<String, Double>,
        limit: Int
    ): List<MarketDiscoveryCandidate> {
        if (limit <= 0) return emptyList()
        return currentLtps.mapNotNull { (symbol, ltp) ->
            if (!ltp.isFinite() || ltp < 10.0) return@mapNotNull null
            val previous = previousLtps[symbol]?.takeIf { it.isFinite() && it > 0.0 }
            val sessionOpen = sessionOpenLtps[symbol]?.takeIf { it.isFinite() && it > 0.0 }
            if (previous == null && sessionOpen == null) return@mapNotNull null
            val pulsePct = previous?.let { (ltp / it - 1.0) * 100.0 } ?: 0.0
            val sessionPct = sessionOpen?.let { (ltp / it - 1.0) * 100.0 } ?: pulsePct
            if (pulsePct <= 0.0 && sessionPct <= 0.0) return@mapNotNull null
            val score = sessionPct * 0.65 + pulsePct * 0.35
            MarketDiscoveryCandidate(symbol, ltp, pulsePct, sessionPct, score)
        }.sortedWith(
            compareByDescending<MarketDiscoveryCandidate> { it.score }
                .thenByDescending { it.sessionPct }
                .thenByDescending { it.pulsePct }
        ).take(limit)
    }
}
