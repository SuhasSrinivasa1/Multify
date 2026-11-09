package com.multify.traderpro.engine

/**
 * Transport-only distribution for Fixed detail quote/history reads.
 * Candidate identity, order, factor scoring and eligibility remain untouched.
 * One-third of the already-selected symbols use the read-only Beta account.
 */
object FixedBetaDetailRoutingPolicy {
    const val BETA_DETAIL_DENOMINATOR = 3

    fun useBeta(symbol: String, betaReady: Boolean): Boolean =
        betaReady && symbol.isNotBlank() && Math.floorMod(symbol.hashCode(), BETA_DETAIL_DENOMINATOR) == 0
}
