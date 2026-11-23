package com.multify.traderpro.engine

import kotlin.math.min

/** EOD-only price reference for a NEXT-session swing research watch, never an order trigger. */
object SwingPullbackResearchPolicy {
    data class Zone(
        val lower: Double,
        val upper: Double,
        val preferred: Double,
        val closeExtensionPct: Double,
        val distantPullback: Boolean
    )

    /**
     * A trend setup above EMA20 does not warrant chasing the previous close.
     * Reference an EMA20 pullback, capped at 1% above that EMA and never above
     * the LAST EOD close. No live fill, current quote, stop or target is invented.
     */
    fun evaluate(lastClose: Double, ema20: Double, ema50: Double): Zone? {
        if (!lastClose.isFinite() || !ema20.isFinite() || !ema50.isFinite() ||
            lastClose <= 0.0 || ema20 <= 0.0 || ema50 <= 0.0 ||
            lastClose <= ema20 || ema20 <= ema50) return null
        val upper = min(lastClose, ema20 * 1.01)
        val preferred = min(lastClose, ema20 * 1.005)
        return Zone(
            lower = ema20,
            upper = upper,
            preferred = preferred,
            closeExtensionPct = (lastClose / preferred - 1.0) * 100.0,
            distantPullback = (lastClose / ema20 - 1.0) * 100.0 > 5.0
        )
    }
}
