package com.multify.traderpro.engine

/**
 * Upgrade only a verified active, app-owned SELL GTT that is below the new
 * hard stop. Never cancel first or loosen a stronger/trailing stop.
 */
object HardStopGttTighteningPolicy {
    fun shouldModifyExisting(
        entryPrice: Double,
        smartOrderMode: String,
        smartOrderId: String?,
        smartOrderStatus: String?,
        brokerTrigger: Double?,
        priceEpsilon: Double = 0.001
    ): Boolean {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 ||
            brokerTrigger == null || !brokerTrigger.isFinite() || brokerTrigger <= 0.0 ||
            !priceEpsilon.isFinite() || priceEpsilon < 0.0
        ) return false
        if (!GttProtectionReadinessPolicy.verified(
                smartOrderMode, smartOrderId, smartOrderStatus
            )) return false
        return brokerTrigger + priceEpsilon < HardStopLossPolicy.stopPrice(entryPrice)
    }
}
