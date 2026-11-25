package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class FixedScanScheduleDiagnosticsTest {
    @Test fun nullPriorScanIsNotMisreportedAsMissed() {
        assertEquals(
            "UNKNOWN_AFTER_RESTART_OR_NO_PRIOR_SCAN",
            FixedScanScheduleDiagnostics.classify(null).status
        )
    }

    @Test fun cadenceBucketsAreDiagnosticOnly() {
        assertEquals("ON_TARGET", FixedScanScheduleDiagnostics.classify(105_000L).status)
        assertEquals("LATE", FixedScanScheduleDiagnostics.classify(180_000L).status)
        val gap = FixedScanScheduleDiagnostics.classify(270_000L)
        assertEquals("INTERVAL_GAP", gap.status)
        assertEquals(2, gap.estimatedSkippedIntervals)
    }

    @Test fun durationBucketsDoNotAlterCadencePolicy() {
        assertEquals("UNDER_30S", FixedScanScheduleDiagnostics.durationBucket(29_999L))
        assertEquals("30_TO_60S", FixedScanScheduleDiagnostics.durationBucket(30_000L))
        assertEquals("60S_TO_TARGET_INTERVAL", FixedScanScheduleDiagnostics.durationBucket(60_000L))
        assertEquals("AT_OR_ABOVE_TARGET_INTERVAL", FixedScanScheduleDiagnostics.durationBucket(90_000L))
    }
}
