package com.multify.traderpro.engine

import kotlin.math.max
import kotlin.math.min

object LearnedTrailingPolicy {
    const val AUTONOMOUS_NET_PROFIT_PCT_OF_INVESTED = 1.0

    private const val FALLBACK_ARM_ATR = 0.60
    private const val MIN_TRAIL_RUPEES = 1.0
    private const val BASE_PRICE_TRAIL_PCT = 0.35
    private const val BASE_ATR_TRAIL_MULTIPLIER = 0.70
    private const val RATCHET_STEP_RUPEES = 0.25
    private const val MIN_GAIN_PARTICIPATION = 0.50
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
        return ltp + 1e-9 >= entryPrice * (1.0 + thresholdPct / 100.0)
    }

    fun isArmed(stopPrice: Double?, entryPrice: Double): Boolean =
        stopPrice?.let { it > entryPrice + 0.01 } ?: false

    fun shouldRatchet(alreadyArmed: Boolean, lastRatchetPrice: Double, highWaterPrice: Double): Boolean {
        if (!alreadyArmed) return true
        if (lastRatchetPrice <= 0.0) return true
        return highWaterPrice + 1e-9 >= lastRatchetPrice + RATCHET_STEP_RUPEES
    }

    fun trailDistance(highWaterPrice: Double, atr: Double, entryPrice: Double = highWaterPrice): Double {
        val gainPct = if (entryPrice > 0.0) ((highWaterPrice / entryPrice) - 1.0) * 100.0 else 0.0
        val priceTrailPct = when {
            gainPct >= 1.50 -> 0.20
            gainPct >= 0.75 -> 0.30
            else -> BASE_PRICE_TRAIL_PCT
        }
        val atrMultiplier = when {
            gainPct >= 1.50 -> 0.50
            gainPct >= 0.75 -> 0.60
            else -> BASE_ATR_TRAIL_MULTIPLIER
        }
        val priceDistance = highWaterPrice * (priceTrailPct / 100.0)
        val atrDistance = max(atr, 0.01) * atrMultiplier
        return max(MIN_TRAIL_RUPEES, max(priceDistance, atrDistance))
    }

    fun investedAmount(entryPrice: Double, quantity: Int): Double =
        if (entryPrice > 0.0 && quantity > 0) entryPrice * quantity else 0.0

    fun minimumNetProfitRupees(
        entryPrice: Double,
        quantity: Int,
        pctOfInvested: Double = AUTONOMOUS_NET_PROFIT_PCT_OF_INVESTED
    ): Double = investedAmount(entryPrice, quantity) * max(0.0, pctOfInvested) / 100.0

    fun estimatedNetProfit(entryPrice: Double, exitPrice: Double, quantity: Int): Double {
        if (entryPrice <= 0.0 || exitPrice <= 0.0 || quantity <= 0) return Double.NEGATIVE_INFINITY
        val gross = (exitPrice - entryPrice) * quantity
        val estimatedCosts = (entryPrice + exitPrice) * quantity * ESTIMATED_COST_RATE_PER_SIDE
        return gross - estimatedCosts
    }

    fun minimumExitPriceForNetProfitPct(
        entryPrice: Double,
        pctOfInvested: Double = AUTONOMOUS_NET_PROFIT_PCT_OF_INVESTED
    ): Double {
        if (entryPrice <= 0.0) return entryPrice
        val pct = max(0.0, pctOfInvested) / 100.0
        return entryPrice * (1.0 + ESTIMATED_COST_RATE_PER_SIDE + pct) /
            (1.0 - ESTIMATED_COST_RATE_PER_SIDE)
    }

    fun profitGateReached(
        entryPrice: Double,
        highWaterPrice: Double,
        quantity: Int,
        pctOfInvested: Double = AUTONOMOUS_NET_PROFIT_PCT_OF_INVESTED
    ): Boolean {
        val minimum = minimumNetProfitRupees(entryPrice, quantity, pctOfInvested)
        return minimum > 0.0 &&
            estimatedNetProfit(entryPrice, highWaterPrice, quantity) + 1e-9 >= minimum
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
        currentStop: Double?,
        lastRatchetPrice: Double = 0.0,
        minimumProtectedStop: Double? = null
    ): Double {
        val distanceStop = highWaterPrice - trailDistance(highWaterPrice, atr, entryPrice)
        val protectedFloor = max(
            netBreakEvenFloor(entryPrice),
            minimumProtectedStop?.takeIf { it.isFinite() && it > 0.0 } ?: entryPrice
        )
        val participationStop = entryPrice + max(0.0, highWaterPrice - entryPrice) * MIN_GAIN_PARTICIPATION
        val incrementalStop = if (currentStop != null && lastRatchetPrice > 0.0 && highWaterPrice > lastRatchetPrice) {
            currentStop + (highWaterPrice - lastRatchetPrice) * MIN_GAIN_PARTICIPATION
        } else participationStop
        val maxExecutableStop = max(entryPrice, highWaterPrice - 0.01)
        val proposed = min(maxExecutableStop, max(distanceStop, max(protectedFloor, max(participationStop, incrementalStop))))
        return max(currentStop ?: entryPrice, proposed)
    }
}
