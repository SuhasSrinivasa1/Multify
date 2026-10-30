package com.multify.traderpro.engine

/** Prevents a handful of early Forecast outcomes from dominating the live target. */
object ForecastTargetMaturityPolicy {
    const val MIN_SAMPLES = 5
    const val MIN_DAYS = 3
    const val MIN_SYMBOLS = 3

    fun isMature(samples: Int, distinctDays: Int, distinctSymbols: Int): Boolean =
        samples >= MIN_SAMPLES && distinctDays >= MIN_DAYS && distinctSymbols >= MIN_SYMBOLS
}
