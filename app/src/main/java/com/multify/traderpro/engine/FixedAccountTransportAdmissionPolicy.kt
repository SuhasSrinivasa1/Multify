package com.multify.traderpro.engine

/**
 * Transport only. Two separately authenticated Groww accounts each retain their
 * own request and 429 state. A failed Beta READ is allowed to fall back to Alpha
 * only when Alpha is locally ready and has spare headroom for its own protective
 * quotes. Never used to rank, score, select, publish or execute a recommendation.
 */
object FixedAccountTransportAdmissionPolicy {
    const val RESERVED_ALPHA_LIVE_REQUESTS = 12

    fun allowAlphaFallback(alphaLiveReady: Boolean, alphaLiveUsed60s: Int, alphaAppLimit60s: Int): Boolean =
        alphaLiveReady && alphaLiveUsed60s >= 0 &&
            alphaAppLimit60s > RESERVED_ALPHA_LIVE_REQUESTS &&
            alphaLiveUsed60s < alphaAppLimit60s - RESERVED_ALPHA_LIVE_REQUESTS
}
