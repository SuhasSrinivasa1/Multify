package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class AllocationDecision(
    val allowed: Boolean,
    val quantity: Int = 0,
    val allocationRupees: Double = 0.0,
    val expectedNetRupees: Double = 0.0,
    val reason: String
)

object BudgetAllocator {
    fun allocate(
        budgetRupees: Double,
        confidence: Double,
        price: Double,
        stopDistance: Double,
        targetDistance: Double,
        existingNetQuantity: Int = 0
    ): AllocationDecision {
        if (budgetRupees < 1_000) return AllocationDecision(false, reason = "Daily budget is too small")
        if (price <= 0 || stopDistance <= 0 || targetDistance <= 0) return AllocationDecision(false, reason = "Invalid price/stop/target geometry")
        if (confidence < .60) return AllocationDecision(false, reason = "Strategy confidence is too weak")

        val confidenceFraction = when {
            confidence >= .90 -> .75
            confidence >= .82 -> .60
            confidence >= .74 -> .45
            else -> .25
        }
        val usedNotional = abs(existingNetQuantity) * price
        val remainingBudget = max(0.0, budgetRupees - usedNotional)
        val desiredNotional = min(remainingBudget, budgetRupees * confidenceFraction)
        val notionalQty = floor(desiredNotional / price).toInt()

        // Risk per trade is capped near 0.5% of the configured daily capital budget.
        val riskRupees = max(150.0, budgetRupees * .005)
        val riskQty = floor(riskRupees / stopDistance).toInt()
        val qty = min(notionalQty, riskQty).coerceAtLeast(0)
        if (qty <= 0) return AllocationDecision(false, reason = "Budget/risk-sized quantity is zero")

        val notional = qty * price
        val brokerageOneSide = min(20.0, max(1.0, notional * .001))
        val brokerage = brokerageOneSide * 2
        val slippage = notional * .0004
        val statutoryBuffer = notional * .00025
        val cost = brokerage + slippage + statutoryBuffer
        val winProbability = (.48 + confidence * .22).coerceIn(.48, .70)
        val expected = winProbability * targetDistance * qty - (1 - winProbability) * stopDistance * qty - cost
        val minimumExpected = max(125.0, budgetRupees * .0006)
        if (expected < minimumExpected) {
            return AllocationDecision(false, qty, notional, expected, "Expected-net proxy ₹${expected.toInt()} is below cost/risk gate ₹${minimumExpected.toInt()}")
        }
        return AllocationDecision(true, qty, notional, expected, "₹${notional.toInt()} allocation · expected-net proxy ₹${expected.toInt()}")
    }
}
