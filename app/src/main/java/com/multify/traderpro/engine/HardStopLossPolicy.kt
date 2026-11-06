package com.multify.traderpro.engine

/**
 * Common capital-protection floor for every app-owned CNC LONG holding.
 * This is deliberately independent from recommendation source and from the
 * profitable stepped-trailing policy. The trailing stop may only tighten it.
 */
object HardStopLossPolicy {
    const val MAX_LOSS_PCT = 5.0

    fun stopPrice(entryPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive" }
        return entryPrice * (1.0 - MAX_LOSS_PCT / 100.0)
    }

    fun lossPct(entryPrice: Double, currentPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive" }
        return ((currentPrice / entryPrice) - 1.0) * 100.0
    }

    /**
     * Migrate a previous hard-stop GTT to the current loss floor.
     * Profit-protecting stops above entry are never loosened.
     */
    fun shouldMigrateHardStopGtt(
        entryPrice: Double,
        existingTriggerPrice: Double,
        desiredStopPrice: Double
    ): Boolean {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 ||
            !existingTriggerPrice.isFinite() || existingTriggerPrice <= 0.0 ||
            !desiredStopPrice.isFinite() || desiredStopPrice <= 0.0
        ) return false
        if (existingTriggerPrice > entryPrice + 1e-9) return false
        return kotlin.math.abs(existingTriggerPrice - desiredStopPrice) > 1e-9
    }

    /**
     * Ignore stale loss-floor prices during policy migration, while retaining
     * any already-profitable local or server-side trailing protection.
     */
    fun previousProfitProtection(
        entryPrice: Double,
        localStopPrice: Double?,
        serverTriggerPrice: Double?,
        serverMode: String
    ): Double? {
        if (!entryPrice.isFinite() || entryPrice <= 0.0) return null
        return listOfNotNull(
            localStopPrice?.takeIf { it.isFinite() && it > entryPrice + 1e-9 },
            serverTriggerPrice?.takeIf {
                it.isFinite() && it > 0.0 &&
                    (serverMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT ||
                        it > entryPrice + 1e-9)
            }
        ).maxOrNull()
    }

    fun breached(entryPrice: Double, currentPrice: Double): Boolean =
        currentPrice.isFinite() && currentPrice > 0.0 && currentPrice <= stopPrice(entryPrice) + 1e-9
}
