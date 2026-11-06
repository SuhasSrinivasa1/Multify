package com.multify.traderpro.engine

/**
 * Common capital-protection floor for every app-owned CNC LONG holding.
 * This is deliberately independent from recommendation source and from the
 * profitable stepped-trailing policy. The trailing stop may only tighten it.
 */
object HardStopLossPolicy {
    const val MAX_LOSS_PCT = 5.0
    const val LEGACY_MAX_LOSS_PCT = 2.0 // Migration marker only; not an active stop.
    private const val LEGACY_STOP_TOLERANCE_PCT = 0.15

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
        val existingLossPct = lossPct(entryPrice, existingTriggerPrice)
        val legacyTwoPercentFloor = kotlin.math.abs(existingLossPct + LEGACY_MAX_LOSS_PCT) <=
            LEGACY_STOP_TOLERANCE_PCT
        // Migrate the previous default -2% floor, or tighten a stop that is
        // looser than -5%. Preserve any other user-tightened loss stop.
        return legacyTwoPercentFloor || existingTriggerPrice < desiredStopPrice - 1e-9
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
        val newFloor = stopPrice(entryPrice)
        fun isLegacyTwoPercentFloor(price: Double): Boolean =
            kotlin.math.abs(lossPct(entryPrice, price) + LEGACY_MAX_LOSS_PCT) <=
                LEGACY_STOP_TOLERANCE_PCT
        return listOfNotNull(
            localStopPrice?.takeIf {
                it.isFinite() && it > newFloor + 1e-9 && !isLegacyTwoPercentFloor(it)
            },
            serverTriggerPrice?.takeIf {
                it.isFinite() && it > 0.0 &&
                    (serverMode == HoldingSmartOrderPolicy.MODE_TRAILING_GTT ||
                        (it > newFloor + 1e-9 && !isLegacyTwoPercentFloor(it)))
            }
        ).maxOrNull()
    }

    fun breached(entryPrice: Double, currentPrice: Double): Boolean =
        currentPrice.isFinite() && currentPrice > 0.0 && currentPrice <= stopPrice(entryPrice) + 1e-9
}
