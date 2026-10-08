package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultifyReverseEngineeringTest {
    @Test fun studyContainsExactlyTwentyPositiveCloseCases() {
        assertEquals(20, MultifyReverseEngineering.positiveCases.size)
        assertTrue(MultifyReverseEngineering.positiveCases.all { it.multifyReturnPct > 0.0 })
        assertTrue(MultifyReverseEngineering.positiveCases.all { it.sessionCloseChangePct > 0.0 })
        assertEquals(20, MultifyReverseEngineering.positiveCases.map { it.symbol }.toSet().size)
    }

    @Test fun positiveOnlyResearchCannotMasqueradeAsLiveWeights() {
        assertTrue(MultifyReverseEngineering.RESEARCH_ONLY)
        val report = MultifyReverseEngineering.report()
        assertTrue(report.contains("RESEARCH ONLY"))
        assertTrue(report.contains("not live execution weights"))
        assertTrue(MultifyReverseEngineering.hypotheses.any { it.contains("walk-forward", ignoreCase = true) })
    }
}
