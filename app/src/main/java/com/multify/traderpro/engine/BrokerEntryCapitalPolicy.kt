package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.min

/** Strict all-engine cash capital guard, evaluated under the one-app-owned-BUY mutex.
 * Prices include a conservative buffer and cash checks fail CLOSED when unavailable.
 */
object BrokerEntryCapitalPolicy {
    private const val CASH_RESERVE_FACTOR = 0.98
    private const val PRICE_BUFFER = 1.025
    data class Verdict(val allowedQuantity: Int, val reason: String,
                       val estimatedOpenCapital: Double, val availableCash: Double)
    fun decide(requestedQuantity: Int, price: Double, perOrderCap: Double, totalCap: Double,
               maxHoldings: Int, openHoldings: Int, openCapital: Double, availableCash: Double): Verdict {
        fun deny(reason: String) = Verdict(0, reason, openCapital, availableCash)
        if (requestedQuantity <= 0 || !price.isFinite() || price <= 0.0 ||
            !perOrderCap.isFinite() || perOrderCap <= 0.0 || !totalCap.isFinite() || totalCap <= 0.0)
            return deny("INVALID_ENTRY_BUDGET")
        if (openHoldings >= maxHoldings.coerceAtLeast(1)) return deny("MAX_CONCURRENT_HOLDINGS")
        if (!availableCash.isFinite() || availableCash <= 0.0) return deny("BROKER_CNC_CASH_UNVERIFIED")
        if (!openCapital.isFinite() || openCapital < 0.0) return deny("OPEN_EXPOSURE_UNVERIFIED")
        val amount = min(min(perOrderCap, totalCap - openCapital),
            availableCash * CASH_RESERVE_FACTOR).coerceAtLeast(0.0)
        val shares = floor(amount / (price * PRICE_BUFFER)).toInt().coerceAtLeast(0)
        return if (shares == 0) deny("TOTAL_CAPITAL_OR_BROKER_CASH_EXHAUSTED")
            else Verdict(min(requestedQuantity, shares), "CAPITAL_VERIFIED", openCapital, availableCash)
    }
    fun brokerCncCash(clearCash: Double, cncBalance: Double): Double {
        val cash = clearCash.takeIf { it.isFinite() && it > 0.0 }
        val cnc = cncBalance.takeIf { it.isFinite() && it > 0.0 }
        // When both are populated, neither should be overridden by a more optimistic field.
        return when { cash != null && cnc != null -> min(cash, cnc)
            cash != null -> cash; cnc != null -> cnc; else -> 0.0 }
    }
}
