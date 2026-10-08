package com.multify.traderpro.engine

import kotlin.math.max
import kotlin.math.exp

object AdaptiveLearningMath {
    const val MAX_SHORT_RETRACEMENT_FRACTION = 1.0
    const val MIN_SHORT_RETRACEMENT_FRACTION = 0.10

    fun rollingMean(values: List<Double>, fallback: Double): Double =
        values.filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.average() ?: fallback

    fun median(values: List<Double>, fallback: Double): Double {
        val x = values.filter { it.isFinite() }.sorted()
        if (x.isEmpty()) return fallback
        return if (x.size % 2 == 1) x[x.size / 2] else (x[x.size / 2 - 1] + x[x.size / 2]) / 2.0
    }

    fun trimmedMean(values: List<Double>, fallback: Double, trimFraction: Double = 0.10): Double {
        val x = values.filter { it.isFinite() }.sorted()
        if (x.isEmpty()) return fallback
        val trim = (x.size * trimFraction.coerceIn(0.0, .40)).toInt()
        val kept = x.drop(trim).dropLast(trim)
        return kept.takeIf { it.isNotEmpty() }?.average() ?: x.average()
    }

    fun ewma(valuesOldestToNewest: List<Double>, fallback: Double, alpha: Double = 0.22): Double {
        val x = valuesOldestToNewest.filter { it.isFinite() }
        if (x.isEmpty()) return fallback
        var v = x.first()
        val a = alpha.coerceIn(.02, .80)
        for (n in x.drop(1)) v = a * n + (1.0 - a) * v
        return v
    }

    fun quantile(values: List<Double>, q: Double, fallback: Double): Double {
        val x = values.filter { it.isFinite() }.sorted()
        if (x.isEmpty()) return fallback
        val p = q.coerceIn(0.0, 1.0) * (x.size - 1)
        val lo = p.toInt()
        val hi = kotlin.math.ceil(p).toInt()
        if (lo == hi) return x[lo]
        return x[lo] + (x[hi] - x[lo]) * (p - lo)
    }

    fun shortRetracementFraction(observed: List<Double>): Double =
        if (observed.isEmpty()) MAX_SHORT_RETRACEMENT_FRACTION
        else observed.average().coerceIn(MIN_SHORT_RETRACEMENT_FRACTION, MAX_SHORT_RETRACEMENT_FRACTION)

    fun precedingLongMove(longEntry: Double, longExit: Double): Double = max(0.0, longExit - longEntry)

    fun shortTarget(shortEntry: Double, longEntry: Double, longExit: Double, fraction: Double): Double {
        val clamped = fraction.coerceIn(MIN_SHORT_RETRACEMENT_FRACTION, MAX_SHORT_RETRACEMENT_FRACTION)
        return max(0.05, shortEntry - precedingLongMove(longEntry, longExit) * clamped)
    }

}