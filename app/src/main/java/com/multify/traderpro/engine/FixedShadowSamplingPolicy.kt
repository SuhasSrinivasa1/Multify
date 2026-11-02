package com.multify.traderpro.engine

/**
 * A completed Fixed production cycle remains 15-minute only.
 * Every tenth completed production cycle admits one independent 5-minute SHADOW experiment.
 * Research samples are never eligible for orders or notification publication.
 */
object FixedShadowSamplingPolicy {
    const val EVERY_N_COMPLETED_SCANS = 10
    const val MAX_PAIRED_SYMBOLS_PER_SAMPLE = 6
    fun shouldSample(completedScanNumber: Int): Boolean =
        completedScanNumber > 0 && completedScanNumber % EVERY_N_COMPLETED_SCANS == 0
}
