package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.max

/**
 * v7 fixed staircase trailing policy.
 *
 * This policy is deliberately independent of Multify MFE and forecast averages:
 *  +0.55% high-water -> protect +0.50%
 *  +0.65% high-water -> protect +0.60%
 *  +0.75% high-water -> protect +0.70%
 *  ...then one additional +0.10% lock for every +0.10% high-water advance.
 */
object LearnedTrailingPolicy {
    const val ARM_TRIGGER_PCT = 0.55
    const val INITIAL_LOCK_PCT = 0.50
    const val RATCHET_STEP_PCT = 0.10

    private const val ESTIMATED_COST_RATE_PER_SIDE = 0.0006
    private const val EPS = 1e-9

    fun gainPct(entryPrice: Double, price: Double): Double =
        if (entryPrice > 0.0 && price > 0.0) (price / entryPrice - 1.0) * 100.0 else Double.NEGATIVE_INFINITY

    fun desiredLockedPctForGain(gainPct: Double): Double? {
        if (!gainPct.isFinite() || gainPct + EPS < ARM_TRIGGER_PCT) return null
        val steps = floor(((gainPct - ARM_TRIGGER_PCT) + EPS) / RATCHET_STEP_PCT)
            .toInt()
            .coerceAtLeast(0)
        return INITIAL_LOCK_PCT + steps * RATCHET_STEP_PCT
    }

    fun desiredLockedPct(entryPrice: Double, highWaterPrice: Double): Double? =
        desiredLockedPctForGain(gainPct(entryPrice, highWaterPrice))

    fun stopPrice(entryPrice: Double, lockedPct: Double): Double =
        if (entryPrice > 0.0 && lockedPct.isFinite()) entryPrice * (1.0 + max(0.0, lockedPct) / 100.0)
        else entryPrice

    fun lockedPct(entryPrice: Double, stopPrice: Double?): Double? {
        if (entryPrice <= 0.0 || stopPrice == null || stopPrice <= entryPrice) return null
        return (stopPrice / entryPrice - 1.0) * 100.0
    }

    fun isArmed(stopPrice: Double?, entryPrice: Double): Boolean =
        (lockedPct(entryPrice, stopPrice) ?: Double.NEGATIVE_INFINITY) + EPS >= INITIAL_LOCK_PCT

    fun shouldRatchet(entryPrice: Double, currentStop: Double?, desiredLockedPct: Double?): Boolean {
        val desired = desiredLockedPct ?: return false
        val current = lockedPct(entryPrice, currentStop) ?: Double.NEGATIVE_INFINITY
        return desired > current + 0.0001
    }

    fun investedAmount(entryPrice: Double, quantity: Int): Double =
        if (entryPrice > 0.0 && quantity > 0) entryPrice * quantity else 0.0

    fun estimatedNetProfit(entryPrice: Double, exitPrice: Double, quantity: Int): Double {
        if (entryPrice <= 0.0 || exitPrice <= 0.0 || quantity <= 0) return Double.NEGATIVE_INFINITY
        val gross = (exitPrice - entryPrice) * quantity
        val estimatedCosts = (entryPrice + exitPrice) * quantity * ESTIMATED_COST_RATE_PER_SIDE
        return gross - estimatedCosts
    }
}
