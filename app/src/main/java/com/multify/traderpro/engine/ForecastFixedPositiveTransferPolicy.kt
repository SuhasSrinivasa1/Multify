package com.multify.traderpro.engine

import kotlin.math.min

data class FixedPositiveTransferCase(
    val symbol: String,
    val strategy: String,
    val regime: String,
    val marketRegime: String,
    val closeGainPct: Double
)

object ForecastFixedPositiveTransferPolicy {
    const val MAX_BONUS = 0.16

    fun rankingBonus(
        symbol: String,
        strategy: String,
        regime: String,
        marketRegime: String,
        positives: List<FixedPositiveTransferCase>
    ): Double {
        if (positives.isEmpty()) return 0.0
        val sameSymbol = positives.count { it.symbol.equals(symbol, true) }
        val sameSignature = positives.filter { it.strategy == strategy && it.regime == regime }
        val sameMarketRegime = positives.count { it.marketRegime == marketRegime }
        var bonus = min(0.07, sameSymbol * 0.02)
        if (sameSignature.isNotEmpty()) {
            bonus += min(0.06, 0.02 + sameSignature.size * 0.01)
            bonus += (sameSignature.map { it.closeGainPct }.average() / 100.0).coerceIn(0.0, 0.03)
        }
        bonus += min(0.02, sameMarketRegime * 0.002)
        return bonus.coerceIn(0.0, MAX_BONUS)
    }
}
