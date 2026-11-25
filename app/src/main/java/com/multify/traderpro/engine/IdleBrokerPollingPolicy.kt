package com.multify.traderpro.engine

/** Read-only broker polling optimization, never used for SELL or new BUY authorization. */
object IdleBrokerPollingPolicy {
    const val EMPTY_AFTER_HOURS_RECHECK_MS = 10L * 60L * 1000L

    fun afterHoursRecheckIntervalMs(
        marketOpen: Boolean,
        appOwnedHolding: Boolean,
        brokerInventoryAndGttsVerifiedEmpty: Boolean,
        pendingEntry: Boolean,
        criticalTradeInFlight: Boolean,
        normalIntervalMs: Long = 60_000L
    ): Long = if (!marketOpen && !appOwnedHolding && brokerInventoryAndGttsVerifiedEmpty &&
        !pendingEntry && !criticalTradeInFlight) EMPTY_AFTER_HOURS_RECHECK_MS else normalIntervalMs

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
