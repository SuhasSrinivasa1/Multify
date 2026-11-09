package com.multify.traderpro.data.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FixedScanTrendStoreTest {
    @Test fun percentilesAreDefinedWithoutInventedBaseline() {
        assertNull(FixedScanTrendStore.percentile(emptyList(), 0.5))
        assertEquals(20L, FixedScanTrendStore.percentile(listOf(30, 10, 20), 0.5))
        assertEquals(30L, FixedScanTrendStore.percentile(listOf(30, 10, 20), 0.9))
    }
}
