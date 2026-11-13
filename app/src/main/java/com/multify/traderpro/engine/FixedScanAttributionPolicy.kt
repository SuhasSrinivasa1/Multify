package com.multify.traderpro.engine

/**
 * A recommendation belongs to the one scan in which its durable ID first
 * appears. Wall-clock overlap alone is NOT a valid recommendation attribution.
 * No production filtering is performed.
 */
internal object FixedScanAttributionPolicy {
    fun newlyPublishedIds(
        beforeIds: Set<Long>,
        afterIds: Set<Long>
    ): Set<Long> = afterIds - beforeIds

    fun uniqueScanId(atMs: Long, processSequence: Long): String {
        require(atMs > 0L)
        require(processSequence > 0L)
        return "FX-" + atMs + "-" + processSequence
    }
}
