package com.multify.traderpro.engine

/**
 * Broker-admission-only rules. A protection LTP request can consume the full Alpha LIVE
 * capacity. Research and discovery must leave headroom while this app owns a holding.
 * This does not alter Fixed candles, factor calculations, scores or order rules.
 */
object MarketDataPriorityAdmissionPolicy {
    const val HEADER = "X-Multify-Request-Priority"
    const val PROTECTION = "PROTECTION"

    @Volatile var hasManagedHoldings: Boolean = false
        private set

    fun updateManagedHoldings(count: Int) {
        hasManagedHoldings = count > 0
    }

    data class Allowance(val perSecond: Int, val perMinute: Int)

    fun allowedLiveTraffic(
        hasHoldings: Boolean,
        isProtection: Boolean
    ): Allowance {
        // Reserve half of the reduced application budget even when no holding is
        // currently open: a new fill may require an immediate protection quote.
        val reserve = !isProtection
        return Allowance(
            GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND -
                if (reserve) GrowwApiBudgetPolicy.SELL_MONITOR_RESERVED_PER_SECOND else 0,
            GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE -
                if (reserve) GrowwApiBudgetPolicy.SELL_MONITOR_RESERVED_PER_MINUTE else 0
        )
    }

    fun ltpPriorityYieldMs(isLtp: Boolean, isProtection: Boolean, pendingProtectionLtps: Int): Long =
        if (isLtp && !isProtection && pendingProtectionLtps > 0) 125L else 0L

    fun isProtectionHeader(value: String?): Boolean = value == PROTECTION
}
