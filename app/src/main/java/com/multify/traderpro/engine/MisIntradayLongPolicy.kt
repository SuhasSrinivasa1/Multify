package com.multify.traderpro.engine

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * NEW app BUYs use NSE CASH MIS. This policy concerns exit mathematics ONLY;
 * the frozen Fixed/Working Range scoring, target and thresholds are unchanged.
 *
 * Legacy CNC positions retain their original overnight GTT lifecycle.
 */
object MisIntradayLongPolicy {
    const val STOP_PENDING = "MIS_LONG_STOP_PENDING"
    const val STOP_VERIFIED = "MIS_LONG_SL_M"
    const val EXIT_PENDING = "MIS_LONG_EXIT_PENDING"
    // MIS is an intraday/manual product with its own unchanged -1% stop.
    // The common app-owned CNC stop is the separately approved -2%.
    const val HARD_STOP_LOSS_PCT = 1.0
    private val IST = ZoneId.of("Asia/Kolkata")
    private val ENTRY_CUTOFF = LocalTime.of(14, 30)
    private val EXIT_TIME = LocalTime.of(14, 55)
    private val PENDING = setOf("OPEN", "NEW", "PENDING", "TRIGGER_PENDING", "TRIGGER PENDING", "VALIDATION_PENDING")

    fun mayEnter(now: ZonedDateTime): Boolean =
        NseEquitySessionCalendar.isFixedNewEntryWindow(now) &&
            now.withZoneSameInstant(IST).toLocalTime().isBefore(ENTRY_CUTOFF)

    fun mustExit(now: ZonedDateTime): Boolean =
        now.withZoneSameInstant(IST).toLocalTime() >= EXIT_TIME

    fun pendingBrokerStop(status: String?): Boolean =
        status?.uppercase() in PENDING

    fun verified(mode: String, orderId: String?, status: String?): Boolean =
        mode == STOP_VERIFIED && !orderId.isNullOrBlank() && pendingBrokerStop(status)

    fun desiredStop(entry: Double, high: Double, workingRange: Boolean): Double {
        require(entry.isFinite() && entry > 0)
        if (workingRange) return WorkingRangeExitPolicy.stopPrice(entry)
        val gain = SteppedTrailingPolicy.stopGainPctForHighWater(entry, high)
        return if (gain == null) entry * (1.0 - HARD_STOP_LOSS_PCT / 100.0)
        else SteppedTrailingPolicy.stopPrice(entry, gain)
    }

    fun tighterStop(previous: Double, entry: Double, high: Double, workingRange: Boolean): Double =
        maxOf(previous, desiredStop(entry, high, workingRange))
}
