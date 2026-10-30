package com.multify.traderpro.engine

import kotlin.math.abs

data class FixedMarketDiscoveryCandidate(
    val symbol: String,
    val ltp: Double,
    val pulsePct: Double,
    val sessionPct: Double,
    val score: Double
)

/**
 * Lightweight market-wide prefilter. The original 40-factor model still makes every
 * quality/target decision. A bounded early bucket prevents the prefilter selecting
 * only names that have already made the largest intraday moves.
 */
object FixedMarketDiscoveryPolicy {
    const val DEFAULT_PRE_SPIKE_SLOTS = 10
    const val PRE_SPIKE_MIN_PULSE_PCT = -0.35
    const val PRE_SPIKE_MAX_PULSE_PCT = 0.55
    const val PRE_SPIKE_MIN_SESSION_PCT = 0.20
    const val PRE_SPIKE_MAX_SESSION_PCT = 2.75

    fun rank(
        currentLtps: Map<String, Double>,
        previousLtps: Map<String, Double>,
        sessionOpenLtps: Map<String, Double>,
        limit: Int,
        preSpikeSlots: Int = DEFAULT_PRE_SPIKE_SLOTS
    ): List<FixedMarketDiscoveryCandidate> {
        if (limit <= 0) return emptyList()
        val candidates = currentLtps.mapNotNull { (symbol, ltp) ->
            if (!ltp.isFinite() || ltp < 10.0) return@mapNotNull null
            val previous = previousLtps[symbol]?.takeIf { it.isFinite() && it > 0.0 }
            val sessionOpen = sessionOpenLtps[symbol]?.takeIf { it.isFinite() && it > 0.0 }
            if (previous == null && sessionOpen == null) return@mapNotNull null
            val pulsePct = previous?.let { (ltp / it - 1.0) * 100.0 } ?: 0.0
            val sessionPct = sessionOpen?.let { (ltp / it - 1.0) * 100.0 } ?: pulsePct
            if (pulsePct <= 0.0 && sessionPct <= 0.0) return@mapNotNull null
            val score = sessionPct * 0.65 + pulsePct * 0.35
            FixedMarketDiscoveryCandidate(symbol, ltp, pulsePct, sessionPct, score)
        }
        val momentum = candidates.sortedWith(
            compareByDescending<FixedMarketDiscoveryCandidate> { it.score }
                .thenByDescending { it.sessionPct }
                .thenByDescending { it.pulsePct }
        )
        if (preSpikeSlots <= 0) return momentum.take(limit)
        val early = candidates.asSequence().filter {
            it.pulsePct in PRE_SPIKE_MIN_PULSE_PCT..PRE_SPIKE_MAX_PULSE_PCT &&
                it.sessionPct in PRE_SPIKE_MIN_SESSION_PCT..PRE_SPIKE_MAX_SESSION_PCT
        }.sortedWith(
            compareByDescending<FixedMarketDiscoveryCandidate> {
                it.sessionPct.coerceIn(0.0, PRE_SPIKE_MAX_SESSION_PCT) -
                    abs(it.pulsePct - 0.18) * 0.25
            }.thenByDescending { it.score }
        ).take(preSpikeSlots.coerceAtMost(limit)).toList()
        val earlySymbols = early.map { it.symbol }.toSet()
        return (early + momentum.filterNot { it.symbol in earlySymbols }).take(limit)
    }
}
