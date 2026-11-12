package com.multify.traderpro.data.network

/** Actual enforced historical request budget, shared between the gate and diagnostics. Two per second remains below the published four-per-second ceiling. */
object GrowwHistoricalThrottlePolicy {
    const val APP_PER_SECOND = 2
    const val APP_PER_MINUTE = 90

    fun withinBudget(usedSecond: Int, usedMinute: Int): Boolean =
        usedSecond < APP_PER_SECOND && usedMinute < APP_PER_MINUTE
}
