package com.multify.traderpro.engine

import kotlin.math.max
import kotlin.math.min

object LearnedTrailingPolicy {
    private const val FALLBACK_ARM_ATR = 0.60
    private const val MIN_TRAIL_RUPEES = 1.0
    private const val PRICE_TRAIL_PCT = 0.35
    private const val ATR_TRAIL_MULTIPLIER = 0.70
    private const val RATCHET_STEP_RUPEES = 1.0
    private const val ESTIMATED_COST_RATE_PER_SIDE = 0.0006
    private const val PROFIT_BUFFER_RATE = 0.0005

    fun thresholdPct(learnedPct: Double?, entryPrice: Double, atr: Double): Double {
        val learned = learnedPct?.takeIf { it.isFinite() && it > 0.0 }
        if (learned != null) return learned
        if (entryPrice <= 0.0 || atr <= 0.0) return 0.40
        return max(0.40, FALLBACK_ARM_ATR * atr / entryPrice * 100.0)
    }

    fun shouldArm(entryPrice: Double, ltp: Double, thresholdPct: Double): Boolean {
        if (entryPrice <= 0.0 || thresholdPct <= 0.0) return false
        return ltp >= entryPrice * (1.0 + thresholdPct / 100.0)
    }

    fun isArmed(stopPrice: Double?, entryPrice: Double): Boolean =
        stopPrice?.let { it > entryPrice + 0.01 } ?: false

    fun shouldRatchet(alreadyArmed: Boolean, lastRatchetPrice: Double, highWaterPrice: Double): Boolean {
        if (!alreadyArmed) return true
        if (lastRatchetPrice <= 0.0) return true
        return highWaterPrice + 1e-9 >= lastRatchetPrice + RATCHET_STEP_RUPEES
    }

    fun trailDistance(highWaterPrice: Double, atr: Double): Double {
        val priceDistance = highWaterPrice * (PRICE_TRAIL_PCT / 100.0)
        val atrDistance = max(atr, 0.01) * ATR_TRAIL_MULTIPLIER
        return max(MIN_TRAIL_RUPEES, max(priceDistance, atrDistance))
    }

    fun netBreakEvenFloor(entryPrice: Double): Double {
        if (entryPrice <= 0.0) return entryPrice
        val costBreakEven = entryPrice * (1.0 + ESTIMATED_COST_RATE_PER_SIDE) /
            (1.0 - ESTIMATED_COST_RATE_PER_SIDE)
        return costBreakEven * (1.0 + PROFIT_BUFFER_RATE)
    }

    fun ratchetStop(
        entryPrice: Double,
        highWaterPrice: Double,
        atr: Double,
        currentStop: Double?
    ): Double {
        val distanceStop = highWaterPrice - trailDistance(highWaterPrice, atr)
        val protectedFloor = netBreakEvenFloor(entryPrice)
        val maxExecutableStop = max(entryPrice, highWaterPrice - 0.01)
        val proposed = min(maxExecutableStop, max(distanceStop, protectedFloor))
        return max(currentStop ?: entryPrice, proposed)
    }
}
