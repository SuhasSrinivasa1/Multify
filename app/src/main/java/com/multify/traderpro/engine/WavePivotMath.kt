package com.multify.traderpro.engine

import kotlin.math.abs
import kotlin.math.max

object WavePivotMath {
    const val DEFAULT_THRESHOLD_FRACTION = 0.004
    private const val EPSILON = 1e-12

    fun favourableMoveFraction(direction: String, startPrice: Double, currentPrice: Double): Double {
        if (startPrice <= 0.0 || currentPrice <= 0.0) return 0.0
        return if (direction.equals("DOWN", true)) max(0.0, (startPrice - currentPrice) / startPrice)
        else max(0.0, (currentPrice - startPrice) / startPrice)
    }

    fun reversalFraction(direction: String, extremePrice: Double, currentPrice: Double): Double {
        if (extremePrice <= 0.0 || currentPrice <= 0.0) return 0.0
        return if (direction.equals("DOWN", true)) max(0.0, (currentPrice - extremePrice) / extremePrice)
        else max(0.0, (extremePrice - currentPrice) / extremePrice)
    }

    fun shouldArm(direction: String, startPrice: Double, currentPrice: Double, thresholdFraction: Double = DEFAULT_THRESHOLD_FRACTION): Boolean =
        favourableMoveFraction(direction, startPrice, currentPrice) + EPSILON >= thresholdFraction.coerceAtLeast(0.0)

    fun shouldConfirm(direction: String, extremePrice: Double, currentPrice: Double, thresholdFraction: Double = DEFAULT_THRESHOLD_FRACTION): Boolean =
        reversalFraction(direction, extremePrice, currentPrice) + EPSILON >= thresholdFraction.coerceAtLeast(0.0)

    fun movePct(startPrice: Double, extremePrice: Double): Double {
        if (startPrice <= 0.0 || extremePrice <= 0.0) return 0.0
        return abs(extremePrice / startPrice - 1.0) * 100.0
    }
}
