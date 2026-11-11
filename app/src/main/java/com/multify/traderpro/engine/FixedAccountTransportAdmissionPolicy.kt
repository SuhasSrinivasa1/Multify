package com.multify.traderpro.engine

/**
 * Transport only. Two separately authenticated Groww accounts each retain their
 * own request and 429 state. Beta-owned reads are skipped during cooldown and are
 * never transferred to Alpha; this prevents discovery and historical fallbacks from
 * consuming the execution account's reserved capacity. Never affects strategy scoring.
 */
object FixedAccountTransportAdmissionPolicy {
    const val RESERVED_ALPHA_LIVE_REQUESTS = 12

    /** A Beta-owned discovery batch is never borrowed by Alpha: this prevents cooldown amplification. */
    fun allowAlphaFallback(alphaLiveReady: Boolean, alphaLiveUsed60s: Int, alphaAppLimit60s: Int): Boolean = false
}
