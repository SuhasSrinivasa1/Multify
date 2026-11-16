package com.multify.traderpro.engine

/** Conservative app budgets below Groww documented shared REST category ceilings. */
object GrowwApiBudgetPolicy {
    const val LIVE_DATA_HARD_PER_SECOND = 10
    const val LIVE_DATA_HARD_PER_MINUTE = 300
    const val APP_LIVE_DATA_PER_SECOND = 10
    const val APP_LIVE_DATA_PER_MINUTE = 300

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

    // Fixed and other non-protective Alpha LIVE_DATA calls may consume at
    // most 60% of the published 10/s and 300/rolling-minute category limits.
    // The complementary headroom is available to higher-priority protection;
    // it is NOT an exemption from Groww's total broker limits.
    const val FIXED_LIVE_DATA_PERCENT = 60
    const val SELL_MONITOR_RESERVED_PER_SECOND = 4
    const val SELL_MONITOR_RESERVED_PER_MINUTE = 120
    const val RATE_LIMIT_COOLDOWN_MS = 5_000L
    const val FIXED_HISTORICAL_MIN_GAP_MS = 20L

    // Fixed recommendation/market research cap is 60%, regardless of current
    // holdings; never borrow protected Alpha LIVE_DATA headroom.
    // Order/GTT and other existing priority policies are intentionally unchanged.
    fun fixedLiveDataPerSecond(hasOpenHoldings: Boolean): Int =
        LIVE_DATA_HARD_PER_SECOND * FIXED_LIVE_DATA_PERCENT / 100
    fun fixedLiveDataPerMinute(hasOpenHoldings: Boolean): Int =
        LIVE_DATA_HARD_PER_MINUTE * FIXED_LIVE_DATA_PERCENT / 100
}
