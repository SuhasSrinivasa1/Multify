package com.multify.traderpro.engine

import kotlin.math.max

object AdaptiveLearningMath {
    const val MAX_SHORT_RETRACEMENT_FRACTION = 1.0
    const val MIN_SHORT_RETRACEMENT_FRACTION = 0.10
    const val AVERAGE_STEP_FRACTION = 0.02

    fun rollingMean(values: List<Double>, fallback: Double): Double =
        values.filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.average() ?: fallback

    fun shortRetracementFraction(observed: List<Double>): Double =
        if (observed.isEmpty()) MAX_SHORT_RETRACEMENT_FRACTION
        else observed.average().coerceIn(MIN_SHORT_RETRACEMENT_FRACTION, MAX_SHORT_RETRACEMENT_FRACTION)

    fun precedingLongMove(longEntry: Double, longExit: Double): Double = max(0.0, longExit - longEntry)

    fun shortTarget(shortEntry: Double, longEntry: Double, longExit: Double, fraction: Double): Double {
        val clamped = fraction.coerceIn(MIN_SHORT_RETRACEMENT_FRACTION, MAX_SHORT_RETRACEMENT_FRACTION)
        return max(0.05, shortEntry - precedingLongMove(longEntry, longExit) * clamped)
    }

    fun nextAverageTrigger(anchorPrice: Double, addCount: Int): Double =
        anchorPrice * (1.0 - AVERAGE_STEP_FRACTION * (addCount + 1))
}
