package com.multify.traderpro.engine

/** Visual/transaction guard for Forecast, independent of immutable Fixed. */
object ForecastBuyAvailabilityPolicy {
    fun canBuy(atMs: Long, brokerReady: Boolean, status: String, eligible: Boolean): Boolean =
        FixedBuyAvailabilityPolicy.nseRegularSession(atMs) && brokerReady && status == "ACTIVE" && eligible
}
