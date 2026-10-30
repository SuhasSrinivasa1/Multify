package com.multify.traderpro.engine

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
    private const val MAX_STEPS = 100

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
        var bestStep = -1
        for (step in 0 until MAX_STEPS) {
            val targetNet = MIN_NET_RETURN_PCT + step * STEP_NET_PCT
            val trigger = requiredPrice(targetNet + SLIPPAGE_ALLOWANCE_BPS / 100.0)
            if (trigger <= highWaterPrice + 1e-9) bestStep = step else break
        }
        val effectiveStep = bestStep.coerceAtLeast(0)
        val targetNet = MIN_NET_RETURN_PCT + effectiveStep * STEP_NET_PCT
        val floor = requiredPrice(targetNet)
        val trigger = requiredPrice(targetNet + SLIPPAGE_ALLOWANCE_BPS / 100.0)
        return NetProfitProtectionDecision(
            armed = bestStep >= 0,
            netTargetPct = targetNet,
            netFloorPrice = floor,
            triggerPrice = trigger,
            slippageAllowanceBps = SLIPPAGE_ALLOWANCE_BPS,
            steps = effectiveStep
        )
    }
}
