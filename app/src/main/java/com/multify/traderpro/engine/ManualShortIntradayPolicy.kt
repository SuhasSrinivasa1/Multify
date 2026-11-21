package com.multify.traderpro.engine

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max

/**
 * An explicitly selected NSE CASH MIS SHORT, not a holdings SELL and not a
 * Fixed/Working Range recommendation. Mirror the frozen positive LONG trail
 * across the entry price without changing SteppedTrailingPolicy itself.
 */
object ManualShortIntradayPolicy {
    const val HARD_STOP_PCT = 1.0
    const val ARM_PCT = SteppedTrailingPolicy.ARM_TRIGGER_PCT
    const val PROTECTED_GAIN_PCT = SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT
    const val STEP_PCT = SteppedTrailingPolicy.STEP_PCT
    private val IST = ZoneId.of("Asia/Kolkata")
    // Broker may square off earlier for F&O-enabled equities. Never wait for 15:20.
    private val NEW_ENTRY_CUTOFF = LocalTime.of(14, 30)
    private val SQUARE_OFF_TIME = LocalTime.of(14, 55)

    fun mayOpen(now: ZonedDateTime): Boolean =
        NseEquitySessionCalendar.isFixedNewEntryWindow(now) &&
            now.withZoneSameInstant(IST).toLocalTime().isBefore(NEW_ENTRY_CUTOFF)

    fun mustSquareOff(now: ZonedDateTime): Boolean =
        now.withZoneSameInstant(IST).toLocalTime() >= SQUARE_OFF_TIME

    fun favourablePct(entry: Double, lowWater: Double): Double =
        if (entry <= 0.0 || !entry.isFinite() || !lowWater.isFinite() || lowWater <= 0.0)
            Double.NEGATIVE_INFINITY
        else (entry - lowWater) / entry * 100.0

    fun stopForLowWater(entry: Double, lowWater: Double): Double {
        require(entry.isFinite() && entry > 0.0)
        val fav = favourablePct(entry, lowWater)
        val desiredGain = SteppedTrailingPolicy.stopGainPctForHighWater(
            entry, entry * (1.0 + max(0.0, fav) / 100.0)
        )
        return if (desiredGain == null) entry * (1.0 + HARD_STOP_PCT / 100.0)
            else entry * (1.0 - desiredGain / 100.0)
    }

    fun tighterStop(existing: Double, entry: Double, lowWater: Double): Double =
        minOf(existing, stopForLowWater(entry, lowWater))
}

/**
 * Persist every transition before a broker POST, including uncertain
 * acknowledgements. One open or unresolved MIS short at a time.
 */
data class ManualShortPosition(
    val symbol: String,
    val reference: String,
    val openedAtMs: Long,
    val quantity: Int,
    val entryPrice: Double,
    val lowestPrice: Double,
    val stopTrigger: Double,
    val state: String,
    val entryOrderId: String = "",
    val protectionReference: String = "",
    val protectionOrderId: String = "",
    val coverReference: String = "",
    val coverOrderId: String = "",
    val lastObservedAtMs: Long = 0L,
    val note: String = ""
) {
    fun unresolved(): Boolean = state != "CLOSED" && state != "CANCELLED_NO_FILL"
    fun brokerStopVerified(): Boolean = state == "PROTECTED" && protectionOrderId.isNotBlank()
}
