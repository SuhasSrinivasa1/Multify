package com.multify.traderpro.engine

/**
 * Transport-only recovery from independent Alpha/Beta broker cooling.
 *
 * This object never changes the Fixed universe, 15-minute features, ranking,
 * PRIMARY confirmation, target, execution or publication.
 * A 429 is never retried on its original account during an active cooldown.
 */
object FixedAccountResearchRecoveryPolicy {
    fun researchAvailable(
        alphaLiveReady: Boolean, alphaHistoricalReady: Boolean,
        betaLiveReady: Boolean, betaHistoricalReady: Boolean
    ): Boolean =
        (alphaLiveReady && alphaHistoricalReady) ||
            (betaLiveReady && betaHistoricalReady)

    fun betaForDiscovery(
        betaAssigned: Boolean, alphaLiveReady: Boolean, betaLiveReady: Boolean
    ): Boolean =
        betaLiveReady && (betaAssigned || !alphaLiveReady)

    fun rescueAlphaBatchOnBeta(
        alphaReturnedData: Boolean, betaLiveReady: Boolean,
        betaFailedThisSweep: Boolean
    ): Boolean =
        !alphaReturnedData && betaLiveReady && !betaFailedThisSweep

    fun betaForDetail(
        deterministicBeta: Boolean, alphaLiveReady: Boolean,
        alphaHistoricalReady: Boolean, betaLiveReady: Boolean,
        betaHistoricalReady: Boolean
    ): Boolean =
        betaLiveReady && betaHistoricalReady &&
            (deterministicBeta || !alphaLiveReady || !alphaHistoricalReady)
}
