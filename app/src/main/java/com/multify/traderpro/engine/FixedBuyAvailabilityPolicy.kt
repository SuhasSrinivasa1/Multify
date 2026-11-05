package com.multify.traderpro.engine

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Presentation-only Fixed BUY availability; execution has its own authoritative guard. */
object FixedBuyAvailabilityPolicy {
    private val india = ZoneId.of("Asia/Kolkata")
    fun nseRegularSession(atMs: Long): Boolean {
        val local = Instant.ofEpochMilli(atMs).atZone(india)
        return NseEquitySessionCalendar.isOpen(local)
    }

    /** UI mirrors the independent authoritative Fixed entry cutoff. Existing
     * portfolio SELL/GTT monitoring keeps the full regular market session. */
    fun fixedNewEntryWindow(atMs: Long): Boolean =
        NseEquitySessionCalendar.isFixedNewEntryWindow(Instant.ofEpochMilli(atMs).atZone(india))

    fun canBuy(atMs: Long, brokerReady: Boolean, status: String, executionEligible: Boolean): Boolean =
        fixedNewEntryWindow(atMs) && brokerReady && status == "ACTIVE" && executionEligible
}
