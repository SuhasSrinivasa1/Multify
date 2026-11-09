package com.multify.traderpro.engine

/** Read-only broker polling optimization, never used for SELL or new BUY authorization. */
object IdleBrokerPollingPolicy {
    fun maySkip(
        marketOpen: Boolean, localOpenRows: Int, pendingEntry: Boolean,
        criticalTradeInFlight: Boolean, inventoryObservedAtMs: Long?,
        gttObservedAtMs: Long?, nowMs: Long, maxRecheckIntervalMs: Long
    ): Boolean {
        if (!marketOpen || localOpenRows != 0 || pendingEntry || criticalTradeInFlight ||
            maxRecheckIntervalMs <= 0L || nowMs <= 0L) return false
        val inventoryAge = inventoryObservedAtMs?.let { nowMs - it } ?: return false
        val gttAge = gttObservedAtMs?.let { nowMs - it } ?: return false
        return inventoryAge in 0 until maxRecheckIntervalMs &&
            gttAge in 0 until maxRecheckIntervalMs
    }
}
