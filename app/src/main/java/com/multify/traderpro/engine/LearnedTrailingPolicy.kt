package com.multify.traderpro.engine

import kotlin.math.max
import kotlin.math.min

object LearnedTrailingPolicy {
    private const val FALLBACK_ARM_ATR = 0.60

    fun thresholdPct(learnedPct: Double?, entryPrice: Double, atr: Double): Double {
        val learned = learnedPct?.takeIf { it.isFinite() && it > 0.0 }
        if (learned != null) return learned
        if (entryPrice <= 0.0 || atr <= 0.0) return 0.40
        return max(0.40, FALLBACK_ARM_ATR * atr / entryPrice * 100.0)
    }

    fun shouldArm(side: String, entryPrice: Double, ltp: Double, thresholdPct: Double): Boolean {
        if (entryPrice <= 0.0 || thresholdPct <= 0.0) return false
        val threshold = thresholdPct / 100.0
        return if (side.equals("SHORT", true)) {
            ltp <= entryPrice * (1.0 - threshold)
        } else {
            ltp >= entryPrice * (1.0 + threshold)
        }
    }

    fun isArmed(side: String, stopPrice: Double?, entryPrice: Double): Boolean {
        val stop = stopPrice ?: return false
        return if (side.equals("SHORT", true)) stop < entryPrice - 0.01 else stop > entryPrice + 0.01
    }

    fun ratchetStop(side: String, entryPrice: Double, ltp: Double, atr: Double, currentStop: Double?): Double {
        val safeAtr = max(atr, max(ltp * 0.001, 0.01))
        val favourableAtr = if (side.equals("SHORT", true)) {
            (entryPrice - ltp) / safeAtr
        } else {
            (ltp - entryPrice) / safeAtr
        }
        val distanceAtr = if (favourableAtr >= 1.50) 0.70 else 0.95
        return if (side.equals("SHORT", true)) {
            val lockedProfit = min(entryPrice - safeAtr * 0.20, ltp + safeAtr * distanceAtr)
            min(currentStop ?: entryPrice, lockedProfit)
        } else {
            val lockedProfit = max(entryPrice + safeAtr * 0.20, ltp - safeAtr * distanceAtr)
            max(currentStop ?: entryPrice, lockedProfit)
        }
    }
}
