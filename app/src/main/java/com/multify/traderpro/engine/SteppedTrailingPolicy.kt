package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.max

data class SteppedTrailState(
    val highWaterGainPct: Double,
    val stopGainPct: Double?,
    val nextTriggerGainPct: Double
)

object SteppedTrailingPolicy {
    const val ARM_TRIGGER_PCT = 0.55
    const val INITIAL_STOP_GAIN_PCT = 0.50
    const val STEP_PCT = 0.10
    const val TRIGGER_BUFFER_PCT = 0.05

    fun gainPct(entryPrice: Double, price: Double): Double {
        if (entryPrice <= 0.0 || !entryPrice.isFinite() || !price.isFinite()) return Double.NEGATIVE_INFINITY
        return (price / entryPrice - 1.0) * 100.0
    }

    fun stopGainPctForHighWater(entryPrice: Double, highWaterPrice: Double): Double? {
        val gain = gainPct(entryPrice, highWaterPrice)
        if (!gain.isFinite() || gain + 1e-9 < ARM_TRIGGER_PCT) return null
        val completedSteps = floor(((gain - ARM_TRIGGER_PCT) + 1e-9) / STEP_PCT).toInt().coerceAtLeast(0)
        return INITIAL_STOP_GAIN_PCT + completedSteps * STEP_PCT
    }

    fun stopPrice(entryPrice: Double, stopGainPct: Double): Double =
        entryPrice * (1.0 + max(0.0, stopGainPct) / 100.0)

    fun stopGainPct(entryPrice: Double, stopPrice: Double?): Double? {
        if (stopPrice == null || stopPrice <= entryPrice || entryPrice <= 0.0) return null
        return gainPct(entryPrice, stopPrice)
    }

    fun nextTriggerGainPct(currentStopGainPct: Double?): Double =
        if (currentStopGainPct == null) ARM_TRIGGER_PCT else currentStopGainPct + TRIGGER_BUFFER_PCT + STEP_PCT

    fun state(entryPrice: Double, highWaterPrice: Double, currentStopPrice: Double?): SteppedTrailState {
        val highGain = gainPct(entryPrice, highWaterPrice)
        val desiredStop = stopGainPctForHighWater(entryPrice, highWaterPrice)
        val existingStop = stopGainPct(entryPrice, currentStopPrice)
        val effectiveStop = listOfNotNull(desiredStop, existingStop).maxOrNull()
        return SteppedTrailState(
            highWaterGainPct = highGain,
            stopGainPct = effectiveStop,
            nextTriggerGainPct = nextTriggerGainPct(effectiveStop)
        )
    }
}
