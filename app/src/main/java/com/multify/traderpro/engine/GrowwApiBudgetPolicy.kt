package com.multify.traderpro.engine

/** Conservative app budgets below Groww documented shared REST category ceilings. */
object GrowwApiBudgetPolicy {
    const val LIVE_DATA_HARD_PER_SECOND = 10
    const val LIVE_DATA_HARD_PER_MINUTE = 300
    const val APP_LIVE_DATA_PER_SECOND = 7
    const val APP_LIVE_DATA_PER_MINUTE = 210

    const val ORDER_HARD_PER_SECOND = 10
    const val ORDER_HARD_PER_MINUTE = 250
    const val APP_ORDER_PER_SECOND = 6
    const val APP_ORDER_PER_MINUTE = 150

    const val NON_TRADING_HARD_PER_SECOND = 20
    const val NON_TRADING_HARD_PER_MINUTE = 500
    const val APP_NON_TRADING_PER_SECOND = 12
    const val APP_NON_TRADING_PER_MINUTE = 360

    const val AUTH_HARD_PER_SECOND = 5
    const val AUTH_HARD_PER_MINUTE = 30
    const val APP_AUTH_PER_SECOND = 3
    const val APP_AUTH_PER_MINUTE = 20

    const val APP_HISTORICAL_PER_SECOND = 4
    const val APP_HISTORICAL_PER_MINUTE = 180

    const val SELL_MONITOR_RESERVED_PER_SECOND = 1
    const val SELL_MONITOR_RESERVED_PER_MINUTE = 60
    const val RATE_LIMIT_COOLDOWN_MS = 5_000L
    const val FIXED_HISTORICAL_MIN_GAP_MS = 250L

    fun fixedLiveDataPerSecond(hasOpenHoldings: Boolean): Int =
        if (hasOpenHoldings) APP_LIVE_DATA_PER_SECOND - SELL_MONITOR_RESERVED_PER_SECOND
        else APP_LIVE_DATA_PER_SECOND

    fun fixedLiveDataPerMinute(hasOpenHoldings: Boolean): Int =
        if (hasOpenHoldings) APP_LIVE_DATA_PER_MINUTE - SELL_MONITOR_RESERVED_PER_MINUTE
        else APP_LIVE_DATA_PER_MINUTE
}
