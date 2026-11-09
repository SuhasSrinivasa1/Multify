package com.multify.traderpro.engine

/**
 * Transport-only distribution for Fixed detail quote/history reads.
 * Candidate identity, order, factor scoring and eligibility remain untouched.
 * One-third of the already-selected symbols use the read-only Beta account.
 */
object FixedBetaDetailRoutingPolicy {
    const val BETA_DETAIL_DENOMINATOR = 5
    const val BETA_DETAIL_NUMERATOR = 3
    const val BETA_HISTORICAL_PER_MINUTE = 14

    fun useBeta(symbol: String, betaReady: Boolean): Boolean =
        betaReady && symbol.isNotBlank() &&
            Math.floorMod(symbol.hashCode(), BETA_DETAIL_DENOMINATOR) < BETA_DETAIL_NUMERATOR
}
