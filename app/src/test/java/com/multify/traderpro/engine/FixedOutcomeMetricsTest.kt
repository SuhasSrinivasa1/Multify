package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedOutcomeMetricsTest {
    @Test fun ratesUseFixedSymbolsWithActualPostPublicationObservations() {
        val rows = listOf(
            FixedOutcomeSample("2026-10-27", "A", 1, 1.1161, 1.32, true, true),
            FixedOutcomeSample("2026-10-27", "B", 2, 1.1161, .72, true, false),
            FixedOutcomeSample("2026-10-27", "C", 3, 1.1161, .12, true, false),
            FixedOutcomeSample("2026-10-27", "D", 4, 1.1161, .0, false, false),
            FixedOutcomeSample("2026-10-27", "A", 1, 1.1161, 1.32, true, true)
        )
        val s = FixedOutcomeMetrics.calculate(rows)
        assertEquals(4, s.published)
        assertEquals(3, s.observed)
        assertEquals(2, s.armHits)
        assertEquals(1, s.onePctHits)
        assertEquals(1, s.fullTargetHits)
        assertEquals(66.66666, s.armHitRatePct!!, .0001)
        assertEquals(33.33333, s.onePctHitRatePct!!, .0001)
        assertEquals(1.02, s.armHitMeanMfePct!!, .0001)
        assertEquals(.72, s.medianPct!!, .0001)
        assertEquals(.72, s.meanObservedMfePct!!, .0001)
    }

    @Test fun noQuotesMeansLearningNotArtificialZeroHitRate() {
        val s = FixedOutcomeMetrics.calculate(listOf(FixedOutcomeSample("2026-10-27", "A", 1, 1.1161, 0.0, false, false)))
        assertNull(s.armHitRatePct)
        assertNull(s.fullTargetHitRatePct)
        assertNull(s.meanObservedMfePct)
    }
}
