package com.multify.traderpro.engine

/** Negative/flat closes remain stored for diagnostics, but never become forecast-learning examples. */
object PositiveCloseForecastPolicy {
    fun isPositiveClose(entryPrice: Double, finalPrice: Double): Boolean =
        entryPrice > 0.0 && finalPrice.isFinite() && finalPrice > entryPrice

    fun isPositiveResearchClose(closePct: Double?): Boolean =
        closePct?.let { it.isFinite() && it > 0.0 } == true
}
