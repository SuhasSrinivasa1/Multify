package com.multify.traderpro.engine

import kotlin.math.abs

/**
 * Common capital-protection floor for every app-owned CNC LONG holding.
 * The hard loss threshold is 5% below the actual broker fill. The existing
 * profitable stepped-trailing policy remains independent and may only tighten it.
 */
object HardStopLossPolicy {
    const val MAX_LOSS_PCT = 5.0

    /**
     * One-time migration marker for app-owned hard-stop GTTs created by older
     * releases at -2%. Never apply this migration to a profitable trailing GTT.
     */
    const val LEGACY_MAX_LOSS_PCT = 2.0
    private const val LEGACY_TRIGGER_ROUNDING_TOLERANCE = 0.11

    fun stopPrice(entryPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive" }
        return entryPrice * (1.0 - MAX_LOSS_PCT / 100.0)
    }

    fun legacyStopPrice(entryPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive" }
        return entryPrice * (1.0 - LEGACY_MAX_LOSS_PCT / 100.0)
    }

    /** Compares rounded broker GTT trigger values against the old -2% formula. */
    fun isLegacyTwoPercentStop(entryPrice: Double, triggerPrice: Double?): Boolean {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 ||
            triggerPrice == null || !triggerPrice.isFinite() || triggerPrice <= 0.0
        ) return false
        return abs(triggerPrice - legacyStopPrice(entryPrice)) <= LEGACY_TRIGGER_ROUNDING_TOLERANCE
    }

    fun isFivePercentStop(entryPrice: Double, triggerPrice: Double?): Boolean {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 ||
            triggerPrice == null || !triggerPrice.isFinite() || triggerPrice <= 0.0
        ) return false
        return abs(triggerPrice - stopPrice(entryPrice)) <= LEGACY_TRIGGER_ROUNDING_TOLERANCE
    }

    fun lossPct(entryPrice: Double, currentPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Entry price must be positive" }
        return ((currentPrice / entryPrice) - 1.0) * 100.0
    }

    fun breached(entryPrice: Double, currentPrice: Double): Boolean =
        currentPrice.isFinite() && currentPrice > 0.0 && currentPrice <= stopPrice(entryPrice) + 1e-9
}
