package com.multify.traderpro.engine

/**
 * Transport only. Two separately authenticated Groww accounts each retain their
 * own request and 429 state. A failed Beta READ is allowed to fall back to Alpha
 * only when Alpha is locally ready and has spare headroom for its own protective
 * quotes. Never used to rank, score, select, publish or execute a recommendation.
 */
object FixedAccountTransportAdmissionPolicy {
    const val RESERVED_ALPHA_LIVE_REQUESTS = 12

    /** A Beta-owned discovery batch is never borrowed by Alpha: this prevents cooldown amplification. */
    fun allowAlphaFallback(alphaLiveReady: Boolean, alphaLiveUsed60s: Int, alphaAppLimit60s: Int): Boolean = false
}
