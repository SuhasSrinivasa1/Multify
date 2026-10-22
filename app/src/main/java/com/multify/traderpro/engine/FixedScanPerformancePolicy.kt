package com.multify.traderpro.engine

/**
 * Performance-only policy for Fixed detailed evaluation.
 *
 * This does not change the frozen v7.4.1 feature model, universe, breadth math, ranking,
 * publication rules, target calibration, or execution rules. It only selects a bounded
 * number of independent symbol evaluations that may run concurrently.
 */
object FixedScanPerformancePolicy {
    const val DEFAULT_CONCURRENCY = 4
    const val HIGH_END_CONCURRENCY = 6
    const val HIGH_END_MIN_PROCESSORS = 8

    fun evaluationConcurrency(availableProcessors: Int): Int =
        if (availableProcessors >= HIGH_END_MIN_PROCESSORS) HIGH_END_CONCURRENCY
        else DEFAULT_CONCURRENCY
}
