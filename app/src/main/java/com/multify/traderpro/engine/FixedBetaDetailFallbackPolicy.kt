package com.multify.traderpro.engine

/**
 * Keep broker transport recovery separate from Fixed's deterministic eligibility
 * rules. Retrying the SAME out-of-band price through Alpha consumes quota and
 * cannot make the invalid Beta quote eligible.
 */
object FixedBetaDetailFallbackPolicy {
    fun shouldRetryOnAlpha(failure: Throwable): Boolean =
        !(failure is IllegalArgumentException && failure.message == "FIXED_PRICE_BAND_OUTSIDE")
}
