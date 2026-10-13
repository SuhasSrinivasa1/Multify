package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class CncSizingResult(
    val quantity: Int,
    val deployableCash: Double,
    val cashReserve: Double,
    val bufferedPrice: Double
)

object CashAwareCncSizing {
    private const val MIN_CASH_RESERVE_RUPEES = 1_000.0
    private const val CASH_RESERVE_PCT = 0.02
    private const val EXECUTION_PRICE_BUFFER = 1.02

    fun size(configuredBudget: Double, availableCash: Double, referencePrice: Double): CncSizingResult {
        if (!configuredBudget.isFinite() || !availableCash.isFinite() || !referencePrice.isFinite() ||
            configuredBudget <= 0.0 || availableCash <= 0.0 || referencePrice <= 0.0
        ) {
            return CncSizingResult(0, 0.0, 0.0, referencePrice.coerceAtLeast(0.0))
        }
        val reserve = min(availableCash, max(MIN_CASH_RESERVE_RUPEES, availableCash * CASH_RESERVE_PCT))
        val safeAvailable = max(0.0, availableCash - reserve)
        val deployable = min(configuredBudget, safeAvailable)
        val bufferedPrice = referencePrice * EXECUTION_PRICE_BUFFER
        val qty = floor(deployable / bufferedPrice).toInt().coerceAtLeast(0)
        return CncSizingResult(qty, deployable, reserve, bufferedPrice)
    }
}
