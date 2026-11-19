package com.multify.traderpro.engine

/**
 * Common capital-protection floor for every app-owned CNC LONG holding.
 * This is deliberately independent from recommendation source and from the
 * profitable stepped-trailing policy. The trailing stop may only tighten it.
 */
object HardStopLossPolicy {
    const val MAX_LOSS_PCT = 2.0

    fun stopPrice(entryPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive" }
        return entryPrice * (1.0 - MAX_LOSS_PCT / 100.0)
    }

    fun lossPct(entryPrice: Double, currentPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive" }
        return ((currentPrice / entryPrice) - 1.0) * 100.0
    }

    fun breached(entryPrice: Double, currentPrice: Double): Boolean =
        currentPrice.isFinite() && currentPrice > 0.0 && currentPrice <= stopPrice(entryPrice) + 1e-9
}
