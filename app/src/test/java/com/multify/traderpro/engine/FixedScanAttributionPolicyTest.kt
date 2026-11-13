package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedScanAttributionPolicyTest {
    @Test fun sameRecommendationCannotBelongToConsecutiveScans() {
        val baseline = setOf(21L, 22L)
        val afterFirst = baseline + setOf(23L, 24L)
        val first = FixedScanAttributionPolicy.newlyPublishedIds(baseline, afterFirst)
        assertEquals(setOf(23L, 24L), first)
        val afterSecond = afterFirst + setOf(25L)
        val second = FixedScanAttributionPolicy.newlyPublishedIds(afterFirst, afterSecond)
        assertEquals(setOf(25L), second)
        assertTrue(first.intersect(second).isEmpty())
        assertTrue(FixedScanAttributionPolicy.newlyPublishedIds(afterSecond, afterSecond).isEmpty())
    }

    @Test fun scanIdsRemainDistinctEvenInSameMillisecond() {
        assertNotEquals(
            FixedScanAttributionPolicy.uniqueScanId(1794552692683L, 1L),
            FixedScanAttributionPolicy.uniqueScanId(1794552692683L, 2L)
        )
    }
}
