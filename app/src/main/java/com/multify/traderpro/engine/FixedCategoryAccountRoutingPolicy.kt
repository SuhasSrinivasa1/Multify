package com.multify.traderpro.engine

/**
 * Chooses the read-only BROKER for each category independently.
 *
 * The originally selected Fixed candidate, 40-factor evaluation, 15m candles,
 * price band, PRIMARY rule and publication policy are never changed.
 */
object FixedCategoryAccountRoutingPolicy {
    enum class Account { ALPHA, BETA, NONE }
    data class Route(val live: Account, val historical: Account) {
        val isUsable: Boolean get() = live != Account.NONE && historical != Account.NONE
        val splitAcrossAccounts: Boolean get() = isUsable && live != historical
    }

    fun choose(
        deterministicBeta: Boolean,
        alphaLiveReady: Boolean, alphaHistoricalReady: Boolean,
        betaLiveReady: Boolean, betaHistoricalReady: Boolean,
        betaHistoricalHasHeadroom: Boolean,
        preferBetaLiveForQuote: Boolean = false
    ): Route {
        val live = when {
            (deterministicBeta || preferBetaLiveForQuote) && betaLiveReady -> Account.BETA
            alphaLiveReady -> Account.ALPHA
            betaLiveReady -> Account.BETA
            else -> Account.NONE
        }
        // Only prefer Beta history for its original stable 1-in-6 share when
        // category quota headroom is verified. A healthy Alpha HISTORICAL
        // account is NOT disabled by Alpha's independent LIVE_DATA 429.
        val historical = when {
            deterministicBeta && betaHistoricalReady && betaHistoricalHasHeadroom ->
                Account.BETA
            alphaHistoricalReady -> Account.ALPHA
            betaHistoricalReady -> Account.BETA
            else -> Account.NONE
        }
        return Route(live, historical)
    }

    fun available(
        alphaLiveReady: Boolean, alphaHistoricalReady: Boolean,
        betaLiveReady: Boolean, betaHistoricalReady: Boolean
    ): Boolean =
        (alphaLiveReady || betaLiveReady) && (alphaHistoricalReady || betaHistoricalReady)
}
