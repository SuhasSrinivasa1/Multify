package com.multify.traderpro.engine

import kotlin.math.max

/** Non-loosening net 0.50% protective overlay; gross +0.55/0.50 trail and -2% stop remain. */
object NetGainTrailingPolicy {
    const val MIN_NET_RETURN_PCT = 0.50
    const val DEFAULT_SLIPPAGE_ALLOWANCE_BPS = 15.0
    const val NET_ARM_EXTRA_BUFFER_PCT = 0.05

    fun minimumNetTargetTrigger(
        entryPrice: Double, quantity: Int,
        netReturnPct: Double = MIN_NET_RETURN_PCT,
        slippageAllowanceBps: Double = DEFAULT_SLIPPAGE_ALLOWANCE_BPS
    ): Double {
        require(entryPrice.isFinite() && entryPrice > 0 && quantity > 0)
        require(netReturnPct >= 0 && netReturnPct.isFinite())
        require(slippageAllowanceBps in 0.0..150.0)
        val desiredNet = entryPrice * quantity * netReturnPct / 100.0
        val netPrice = GrowwDeliveryCostPolicy.recoverySellPrice(entryPrice, quantity, desiredNet)
        val cushion = entryPrice * slippageAllowanceBps / 10_000.0
        return kotlin.math.ceil((netPrice + cushion - 1e-9) /
            GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES) * GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES
    }

    fun additionalNetGainProtection(
        entryPrice: Double, quantity: Int, observedHighWater: Double, existingGrossStop: Double
    ): Double {
        val floor = minimumNetTargetTrigger(entryPrice, quantity)
        val reached = observedHighWater >= floor * (1.0 + NET_ARM_EXTRA_BUFFER_PCT / 100.0)
        return if (reached) max(existingGrossStop, floor) else existingGrossStop
    }
}
