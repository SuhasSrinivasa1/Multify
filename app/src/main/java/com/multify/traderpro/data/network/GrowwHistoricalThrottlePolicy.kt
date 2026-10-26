package com.multify.traderpro.data.network

/** Actual enforced historical request budget, shared between the gate and diagnostics. */
object GrowwHistoricalThrottlePolicy {
    const val APP_PER_SECOND = 1
    const val APP_PER_MINUTE = 45

    fun withinBudget(usedSecond: Int, usedMinute: Int): Boolean =
        usedSecond < APP_PER_SECOND && usedMinute < APP_PER_MINUTE
}
