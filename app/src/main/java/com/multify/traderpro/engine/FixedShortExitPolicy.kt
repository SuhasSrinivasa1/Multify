package com.multify.traderpro.engine

import kotlin.math.floor

/**
 * Short position execution only. Does not change or retrain the frozen Fixed signal.
 * Entry SELL, close BUY; broker-side MIS OCO handles the target and the adverse stop.
 */
object FixedShortExitPolicy {
    const val ARM_GAIN_PCT = SteppedTrailingPolicy.ARM_TRIGGER_PCT
    const val INITIAL_PROTECTED_GAIN_PCT = SteppedTrailingPolicy.INITIAL_STOP_GAIN_PCT
    const val STEP_GAIN_PCT = SteppedTrailingPolicy.STEP_PCT
    const val MAX_ADVERSE_MOVE_PCT = 5.0
    const val ENTRY_CUTOFF_HOUR = 14
    const val ENTRY_CUTOFF_MINUTE = 45
    const val SQUARE_OFF_HOUR = 15
    const val SQUARE_OFF_MINUTE = 0

    fun target(entry: Double, targetPct: Double): Double = entry * (1.0 - targetPct / 100.0)
    fun initialStop(entry: Double): Double = entry * (1.0 + MAX_ADVERSE_MOVE_PCT / 100.0)
    fun favourableGainPct(entry: Double, observedLow: Double): Double =
        if (entry > 0.0) (entry - observedLow) / entry * 100.0 else 0.0

    fun trailingBuyStop(entry: Double, observedLow: Double, existingStop: Double): Double {
        if (entry <= 0.0 || observedLow <= 0.0) return existingStop
        val gain = favourableGainPct(entry, observedLow)
        if (gain + 1e-9 < ARM_GAIN_PCT) return existingStop
        val steps = floor(((gain - ARM_GAIN_PCT + 1e-9) / STEP_GAIN_PCT).coerceAtLeast(0.0))
        val protectedPct = INITIAL_PROTECTED_GAIN_PCT + steps * STEP_GAIN_PCT
        return minOf(existingStop, entry * (1.0 - protectedPct / 100.0))
    }

    /** NSE tick safety: round a BUY stop up and a BUY profit target down. */
    fun roundedStop(price: Double): Double = kotlin.math.ceil(price * 20.0 - 1e-8) / 20.0
    fun roundedTarget(price: Double): Double = floor(price * 20.0 + 1e-8) / 20.0
    fun isEntryTime(hour: Int, minute: Int): Boolean =
        (hour > 9 || (hour == 9 && minute >= 15)) &&
        (hour < ENTRY_CUTOFF_HOUR || (hour == ENTRY_CUTOFF_HOUR && minute < ENTRY_CUTOFF_MINUTE))
    fun isSquareOffTime(hour: Int, minute: Int): Boolean =
        hour > SQUARE_OFF_HOUR || (hour == SQUARE_OFF_HOUR && minute >= SQUARE_OFF_MINUTE)
}
