package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.min

data class CncSizingResult(
    val quantity: Int,
    val deployableCash: Double,
    val cashReserve: Double,
    val bufferedPrice: Double
)

object CashAwareCncSizing {
    private const val EXECUTION_PRICE_BUFFER = 1.02

    /**
     * Multify + adaptive Forecast policy:
     * the complete configured budget must be available before an order may be sized.
     * Example: a ₹50,000 budget with only ₹20,000 available returns quantity 0.
     */
    fun sizeStrictBudget(configuredBudget: Double, availableCash: Double, referencePrice: Double): CncSizingResult {
        if (!configuredBudget.isFinite() || !availableCash.isFinite() || !referencePrice.isFinite() ||
            configuredBudget <= 0.0 || availableCash <= 0.0 || referencePrice <= 0.0
        ) {
            return CncSizingResult(0, 0.0, 0.0, referencePrice.coerceAtLeast(0.0))
        }
        val bufferedPrice = referencePrice * EXECUTION_PRICE_BUFFER
        if (availableCash + 1e-9 < configuredBudget) {
            return CncSizingResult(0, 0.0, 0.0, bufferedPrice)
        }
        val qty = floor(configuredBudget / bufferedPrice).toInt().coerceAtLeast(0)
        return CncSizingResult(qty, configuredBudget, 0.0, bufferedPrice)
    }

    /**
     * Fixed-only policy:
     * use the lesser of the configured per-call budget and whatever CNC cash is currently available.
     * This deliberately permits a partial-wallet Fixed buy after an earlier call consumed cash.
     */
    fun sizeFixedAvailable(configuredBudget: Double, availableCash: Double, referencePrice: Double): CncSizingResult {
        if (!configuredBudget.isFinite() || !availableCash.isFinite() || !referencePrice.isFinite() ||
            configuredBudget <= 0.0 || availableCash <= 0.0 || referencePrice <= 0.0
        ) {
            return CncSizingResult(0, 0.0, 0.0, referencePrice.coerceAtLeast(0.0))
        }
        val deployable = min(configuredBudget, availableCash)
        val bufferedPrice = referencePrice * EXECUTION_PRICE_BUFFER
        val qty = floor(deployable / bufferedPrice).toInt().coerceAtLeast(0)
        return CncSizingResult(qty, deployable, 0.0, bufferedPrice)
    }

    // Compatibility alias for older callers; Fixed-style flexible sizing is explicit at new call sites.
    fun size(configuredBudget: Double, availableCash: Double, referencePrice: Double): CncSizingResult =
        sizeFixedAvailable(configuredBudget, availableCash, referencePrice)
}
