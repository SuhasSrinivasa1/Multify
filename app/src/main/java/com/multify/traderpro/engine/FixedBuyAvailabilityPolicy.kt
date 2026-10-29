package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Presentation-only Fixed BUY availability; execution has its own authoritative guard. */
object FixedBuyAvailabilityPolicy {
    private val india = ZoneId.of("Asia/Kolkata")
    fun nseRegularSession(atMs: Long): Boolean {
        val local = Instant.ofEpochMilli(atMs).atZone(india)
        return local.dayOfWeek.value < 6 &&
            !local.toLocalTime().isBefore(LocalTime.of(9, 15)) &&
            local.toLocalTime().isBefore(LocalTime.of(15, 30))
    }

    fun canBuy(atMs: Long, brokerReady: Boolean, status: String, executionEligible: Boolean): Boolean =
        nseRegularSession(atMs) && brokerReady && status == "ACTIVE" && executionEligible
}
