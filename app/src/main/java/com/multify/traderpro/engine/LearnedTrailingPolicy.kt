package com.multify.traderpro.engine

import kotlin.math.max

object LearnedTrailingPolicy {
    private const val FALLBACK_ARM_ATR = 0.60

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

    fun ratchetStop(entryPrice: Double, ltp: Double, atr: Double, currentStop: Double?): Double {
        val safeAtr = max(atr, max(ltp * 0.001, 0.01))
        val favourableAtr = (ltp - entryPrice) / safeAtr
        val distanceAtr = if (favourableAtr >= 1.50) 0.70 else 0.95
        val lockedProfit = max(entryPrice + safeAtr * 0.20, ltp - safeAtr * distanceAtr)
        return max(currentStop ?: entryPrice, lockedProfit)
    }
}
