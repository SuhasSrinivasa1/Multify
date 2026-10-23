package com.multify.traderpro.engine

/**
 * Production request budget derived from Groww's documented REST limits.
 *
 * Live Data: 10 requests/sec and 300 requests/min. The app intentionally caps itself at
 * 9/sec and 285/min (95% of the minute allowance) to retain safety headroom.
 *
 * When holdings are actively monitored, one request/sec and up to 60/min are reserved for
 * portfolio LTP protection. Fixed receives the entire remaining live-data budget.
 */
object GrowwApiBudgetPolicy {
    const val LIVE_DATA_HARD_PER_SECOND = 10
    const val LIVE_DATA_HARD_PER_MINUTE = 300
    const val APP_LIVE_DATA_PER_SECOND = 9
    const val APP_LIVE_DATA_PER_MINUTE = 285

    const val SELL_MONITOR_RESERVED_PER_SECOND = 1
    const val SELL_MONITOR_RESERVED_PER_MINUTE = 60

    const val RATE_LIMIT_COOLDOWN_MS = 1_500L
    const val FIXED_HISTORICAL_MIN_GAP_MS = 200L

    fun fixedLiveDataPerSecond(hasOpenHoldings: Boolean): Int =
        if (hasOpenHoldings) APP_LIVE_DATA_PER_SECOND - SELL_MONITOR_RESERVED_PER_SECOND
        else APP_LIVE_DATA_PER_SECOND

    fun fixedLiveDataPerMinute(hasOpenHoldings: Boolean): Int =
        if (hasOpenHoldings) APP_LIVE_DATA_PER_MINUTE - SELL_MONITOR_RESERVED_PER_MINUTE
        else APP_LIVE_DATA_PER_MINUTE
}
