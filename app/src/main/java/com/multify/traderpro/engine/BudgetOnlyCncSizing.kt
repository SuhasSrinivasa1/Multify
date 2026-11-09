package com.multify.traderpro.engine

import kotlin.math.floor

/**
 * Broker-wallet-independent CNC sizing. The configured amount is a per-stock,
 * per-order ceiling, never a daily allocation or a claim about Groww cash.
 * Groww alone accepts/rejects insufficient-funds BUYs.
 */
object BudgetOnlyCncSizing {
    private const val EXECUTION_PRICE_BUFFER = 1.02

    fun size(configuredBudget: Double, referencePrice: Double): CncSizingResult {
        if (!configuredBudget.isFinite() || !referencePrice.isFinite() ||
            configuredBudget <= 0.0 || referencePrice <= 0.0) {
            return CncSizingResult(0, 0.0, 0.0,
                if (referencePrice.isFinite()) referencePrice.coerceAtLeast(0.0) else 0.0)
        }
        val buffered = referencePrice * EXECUTION_PRICE_BUFFER
        if (!buffered.isFinite() || buffered <= 0.0) return CncSizingResult(0, 0.0, 0.0, 0.0)
        val count = floor(configuredBudget / buffered).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt().coerceAtLeast(0)
        return CncSizingResult(count, configuredBudget, 0.0, buffered)
    }
}
