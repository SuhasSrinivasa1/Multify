package com.multify.traderpro.engine

import kotlin.math.min

data class MultifyPositiveTransferCase(
    val symbol: String,
    val realizedPct: Double,
    val maxFavourablePct: Double
)

/** Positive-only Multify assistance for adaptive Forecast. Fixed never reads this policy. */
object ForecastMultifyPositiveTransferPolicy {
    const val MAX_BONUS = 0.08

    /** An official Multify same-session BOOK PROFIT with a positive reported
     * or explicitly observed return. Neither MFE nor simulated trailing qualifies.
     */
    fun qualifiesVerifiedClose(
        source: String, action: String, buyEventId: Long?, sellEventId: Long?,
        buyAtMs: Long, sellAtMs: Long?, realizedPct: Double?
    ): Boolean = source == "LIVE" && action.equals("BUY", true) &&
        buyEventId != null && sellEventId != null && buyAtMs > 0L &&
        sellAtMs != null && sellAtMs > buyAtMs &&
        ForecastUnivestLearningPolicy.sameTradingDay(buyAtMs, sellAtMs) &&
        realizedPct != null && realizedPct.isFinite() && realizedPct > 0.0


    fun rankingBonus(symbol: String, cases: List<MultifyPositiveTransferCase>): Double {
        val matches = cases.filter { it.symbol.equals(symbol, true) }
        if (matches.isEmpty()) return 0.0
        val avgRealized = matches.map { it.realizedPct }.average().coerceAtLeast(0.0)
        val avgMfe = matches.map { it.maxFavourablePct }.average().coerceAtLeast(0.0)
        val sample = min(0.03, matches.size * 0.01)
        val quality = min(0.05, avgRealized * 0.012 + avgMfe * 0.006)
        return (sample + quality).coerceIn(0.0, MAX_BONUS)
    }
}
