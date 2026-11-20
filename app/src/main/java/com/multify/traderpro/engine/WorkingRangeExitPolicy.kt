package com.multify.traderpro.engine

/**
 * Source-isolated exit policy for Alpha CNC Working Range holdings only.
 * No +0.55% arm, +0.50% profit stop, ratchet or intermediate SELL.
 * A broker-side DOWN GTT protects the loss boundary; the app observes target
 * using fresh monitored prices and reconciles the outstanding GTT before SELL.
 */
object WorkingRangeExitPolicy {
    const val HARD_STOP_LOSS_PCT = 1.0
    const val TARGET_REASON = "WORKING_RANGE_TARGET_FULL_EXIT"
    const val STOP_REASON = "WORKING_RANGE_STOP_MINUS_1_FULL_EXIT"

    fun owns(openReferenceId: String, regime: String): Boolean =
        openReferenceId.startsWith("WRM") || regime == "WORKING_RANGE_LONG"

    fun stopPrice(entryPrice: Double): Double {
        require(entryPrice.isFinite() && entryPrice > 0.0) { "Invalid Working Range fill price" }
        return entryPrice * (1.0 - HARD_STOP_LOSS_PCT / 100.0)
    }

    fun exitReason(entryPrice: Double, targetPrice: Double?, freshPrice: Double): String? {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 ||
            !freshPrice.isFinite() || freshPrice <= 0.0
        ) return null
        if (freshPrice <= stopPrice(entryPrice)) return STOP_REASON
        if (targetPrice != null && targetPrice.isFinite() && targetPrice > entryPrice &&
            freshPrice >= targetPrice) return TARGET_REASON
        return null
    }
}
