package com.multify.traderpro.engine

/**
 * Keep broker transport recovery separate from Fixed's deterministic eligibility
 * rules. Retrying the SAME out-of-band price through Alpha consumes quota and
 * cannot make the invalid Beta quote eligible.
 */
object FixedBetaDetailFallbackPolicy {
    // Never amplify a Beta outage into an Alpha Live/Historical burst.
    // Fixed must defer incomplete evaluations, not submit speculative retries.
    fun shouldRetryOnAlpha(failure: Throwable): Boolean = false
}
