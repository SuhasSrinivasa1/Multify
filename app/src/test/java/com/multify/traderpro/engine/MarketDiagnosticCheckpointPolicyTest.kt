package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class MarketDiagnosticCheckpointPolicyTest {
    @Test fun recommendationCheckpointTriggersAtTwentyNewEventsNotNineteen() {
        assertFalse(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "RECOMMENDATIONS", 39, 20, 1_000L, 2_000L, true))
        assertTrue(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "RECOMMENDATIONS", 40, 20, 1_000L, 2_000L, true))
    }

    @Test fun timeCheckpointRequiresThreeHoursAndAnOpenSession() {
        val last = 1_000_000L
        assertFalse(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "TIME", 10, 10, last, last + 2L * 60L * 60L * 1000L, true))
        assertTrue(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "TIME", 10, 10, last, last + 3L * 60L * 60L * 1000L, true))
        assertFalse(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "TIME", 10, 10, last, last + 4L * 60L * 60L * 1000L, false))
    }

    @Test fun firstTimeCheckpointIsAllowedOnlyInsideMarketWindow() {
        assertTrue(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "TIME", 0, 0, 0L, 10_000L, true))
        assertFalse(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "TIME", 0, 0, 0L, 10_000L, false))
    }

    @Test fun unknownTriggerNeverRuns() {
        assertFalse(MarketDiagnosticCheckpointPolicy.shouldCheckpoint(
            "UNKNOWN", 100, 0, 0L, Long.MAX_VALUE, true))
    }
}
