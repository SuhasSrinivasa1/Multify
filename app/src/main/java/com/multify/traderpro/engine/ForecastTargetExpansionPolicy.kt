package com.multify.traderpro.engine

import kotlin.math.min

/** Positive-only target expansion for adaptive Forecast. Fixed never calls this policy. */
object ForecastTargetExpansionPolicy {
    const val MAX_UPLIFT_PCT_POINTS = 0.75
    const val MIN_MATCHES_FOR_SIGNATURE_UPLIFT = 2

    fun targetPct(
        baseTargetPct: Double,
        symbol: String,
        strategy: String,
        regime: String,
        marketRegime: String,
        volatilityCapacityPct: Double,
        positives: List<FixedPositiveTransferCase>
    ): Double {
        if (baseTargetPct <= 0.0 || volatilityCapacityPct <= baseTargetPct || positives.isEmpty()) return baseTargetPct
        val sameSymbol = positives.filter { it.symbol.equals(symbol, true) }
        val sameSignature = positives.filter {
            it.strategy == strategy && it.regime == regime && it.marketRegime == marketRegime
        }
        val evidence = when {
            sameSymbol.isNotEmpty() -> sameSymbol
            sameSignature.size >= MIN_MATCHES_FOR_SIGNATURE_UPLIFT -> sameSignature
            else -> return baseTargetPct
        }
        val observedUpside = evidence.map { maxOf(it.closeGainPct, it.maxFavourablePct) }
            .filter { it.isFinite() && it > baseTargetPct }
        if (observedUpside.isEmpty()) return baseTargetPct
        val avgUpside = observedUpside.average()
        val evidenceUplift = ((avgUpside - baseTargetPct) * 0.35).coerceAtLeast(0.0)
        val bounded = baseTargetPct + min(MAX_UPLIFT_PCT_POINTS, evidenceUplift)
        return min(bounded, volatilityCapacityPct).coerceAtLeast(baseTargetPct)
    }
}
