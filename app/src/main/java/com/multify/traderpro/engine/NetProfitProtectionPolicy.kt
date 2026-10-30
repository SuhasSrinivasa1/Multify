package com.multify.traderpro.engine

import kotlin.math.floor

/**
 * A profitable trail is armed only when its trigger targets at least 0.50% estimated net
 * return after delivery charges plus a 25 bps execution-slippage reserve. The existing
 * 0.10 percentage-point ratchet cadence is retained. The common -2% hard stop remains
 * active until this net-profit trigger is reached.
 */
data class NetProfitProtectionDecision(
    val armed: Boolean,
    val netTargetPct: Double,
    val netFloorPrice: Double,
    val triggerPrice: Double,
    val slippageAllowanceBps: Double,
    val steps: Int
)

object NetProfitProtectionPolicy {
    const val MIN_NET_RETURN_PCT = 0.50
    const val SLIPPAGE_ALLOWANCE_BPS = 25.0
    const val STEP_NET_PCT = SteppedTrailingPolicy.STEP_PCT

    fun evaluate(
        entryPrice: Double,
        quantity: Int,
        highWaterPrice: Double,
        tickSize: Double = GrowwDeliveryCostPolicy.SAFE_TICK_RUPEES
    ): NetProfitProtectionDecision {
        require(entryPrice.isFinite() && entryPrice > 0.0)
        require(quantity > 0)
        require(highWaterPrice.isFinite() && highWaterPrice > 0.0)
        val capital = entryPrice * quantity
        fun requiredPrice(netPct: Double): Double =
            GrowwDeliveryCostPolicy.recoverySellPrice(
                entryPrice = entryPrice,
                quantity = quantity,
                targetNetProfitRupees = capital * netPct / 100.0,
                tickSize = tickSize
            )
        val netAtHighWater = GrowwDeliveryCostPolicy.estimatedNetProfit(
            entryPrice, highWaterPrice, quantity
        ) / capital * 100.0
        val maximumProtectedNetPct = netAtHighWater - SLIPPAGE_ALLOWANCE_BPS / 100.0
        var step = floor((maximumProtectedNetPct - MIN_NET_RETURN_PCT) / STEP_NET_PCT).toInt()
        if (step < 0) {
            val floor = requiredPrice(MIN_NET_RETURN_PCT)
            val trigger = requiredPrice(MIN_NET_RETURN_PCT + SLIPPAGE_ALLOWANCE_BPS / 100.0)
            return NetProfitProtectionDecision(
                armed = false, netTargetPct = MIN_NET_RETURN_PCT,
                netFloorPrice = floor, triggerPrice = trigger,
                slippageAllowanceBps = SLIPPAGE_ALLOWANCE_BPS, steps = 0
            )
        }
        var targetNet = MIN_NET_RETURN_PCT + step * STEP_NET_PCT
        var floorPrice = requiredPrice(targetNet)
        var triggerPrice = requiredPrice(targetNet + SLIPPAGE_ALLOWANCE_BPS / 100.0)
        // Tick rounding and fee rounding can put the calculated trigger one tick above
        // the observed high-water. Back off one ratchet step, never the net floor.
        while (triggerPrice > highWaterPrice + 1e-9 && step > 0) {
            step -= 1
            targetNet = MIN_NET_RETURN_PCT + step * STEP_NET_PCT
            floorPrice = requiredPrice(targetNet)
            triggerPrice = requiredPrice(targetNet + SLIPPAGE_ALLOWANCE_BPS / 100.0)
        }
        return NetProfitProtectionDecision(
            armed = triggerPrice <= highWaterPrice + 1e-9,
            netTargetPct = targetNet,
            netFloorPrice = floorPrice,
            triggerPrice = triggerPrice,
            slippageAllowanceBps = SLIPPAGE_ALLOWANCE_BPS,
            steps = step
        )
    }
}
