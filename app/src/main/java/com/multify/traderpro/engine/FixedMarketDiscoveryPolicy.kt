package com.multify.traderpro.engine

import kotlin.math.abs

data class FixedMarketDiscoveryCandidate(
    val symbol: String,
    val ltp: Double,
    val pulsePct: Double,
    val sessionPct: Double,
    val score: Double,
    val lane: String = "MOMENTUM"
)

/**
 * Lightweight market-wide prefilter. The frozen 40-factor model and entry-safety overlay
 * remain authoritative. This prefilter reserves a small lane for orderly pre-spike setups.
 */
object FixedMarketDiscoveryPolicy {
    const val EARLY_PULSE_MIN_PCT = -0.15
    const val EARLY_PULSE_MAX_PCT = 0.40
    const val EARLY_SESSION_MIN_PCT = -0.50
    const val EARLY_SESSION_MAX_PCT = 12.00
    const val MOMENTUM_SHARE = 0.65

    fun rank(
        currentLtps: Map<String, Double>,
        previousLtps: Map<String, Double>,
        sessionOpenLtps: Map<String, Double>,
        limit: Int
    ): List<FixedMarketDiscoveryCandidate> {
        if (limit <= 0) return emptyList()
        val candidates = currentLtps.mapNotNull { (symbol, ltp) ->
            if (!ltp.isFinite() || ltp < 10.0) return@mapNotNull null
            val previous = previousLtps[symbol]?.takeIf { it.isFinite() && it > 0.0 }
            val sessionOpen = sessionOpenLtps[symbol]?.takeIf { it.isFinite() && it > 0.0 }
            if (previous == null && sessionOpen == null) return@mapNotNull null
            val pulsePct = previous?.let { (ltp / it - 1.0) * 100.0 } ?: 0.0
            val sessionPct = sessionOpen?.let { (ltp / it - 1.0) * 100.0 } ?: pulsePct
            FixedMarketDiscoveryCandidate(symbol, ltp, pulsePct, sessionPct,
                sessionPct * 0.65 + pulsePct * 0.35, "MOMENTUM")
        }
        val earlyPool = candidates.asSequence()
            .filter { it.pulsePct in EARLY_PULSE_MIN_PCT..EARLY_PULSE_MAX_PCT }
            .filter { it.sessionPct in EARLY_SESSION_MIN_PCT..EARLY_SESSION_MAX_PCT }
            .filter { it.sessionPct >= 0.0 || it.pulsePct >= 0.05 }
            .map { it.copy(lane = "EARLY_PRE_SPIKE",
                score = 1.0 - abs(it.pulsePct - 0.12) * 0.50 + it.sessionPct.coerceIn(
                    EARLY_SESSION_MIN_PCT, EARLY_SESSION_MAX_PCT) * 0.015) }
            .sortedWith(compareBy<FixedMarketDiscoveryCandidate> { abs(it.pulsePct - 0.12) }
                .thenByDescending { it.sessionPct }.thenByDescending { it.pulsePct })
            .toList()
        val earlySymbols = earlyPool.mapTo(mutableSetOf()) { it.symbol.uppercase() }
        val momentum = candidates.asSequence()
            .filter { it.pulsePct > 0.0 && it.sessionPct > 0.0 }
            .filter { it.symbol.uppercase() !in earlySymbols }
            .sortedWith(compareByDescending<FixedMarketDiscoveryCandidate> { it.score }
                .thenByDescending { it.sessionPct }.thenByDescending { it.pulsePct })
            .toList()
        val momentumSlots = (limit * MOMENTUM_SHARE).toInt().coerceAtLeast(1).coerceAtMost(limit)
        val selectedMomentum = momentum.take(momentumSlots)
        val selectedSymbols = selectedMomentum.mapTo(mutableSetOf()) { it.symbol.uppercase() }
        val early = earlyPool.asSequence()
            .filter { it.symbol.uppercase() !in selectedSymbols }
            .take((limit - momentumSlots).coerceAtLeast(0))
            .toList()
        val combined = (selectedMomentum + early).toMutableList()
        if (combined.size < limit) {
            val used = combined.mapTo(mutableSetOf()) { it.symbol.uppercase() }
            momentum.asSequence().filter { it.symbol.uppercase() !in used }
                .take(limit - combined.size).forEach(combined::add)
        }
        return combined.take(limit)
    }
}
