package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveDirectionEngineTest {
    private fun input(
        current: String = "LONG", longScore: Double, longConfidence: Double,
        shortScore: Double, shortConfidence: Double, trajectory: Double,
        spread: Double = 8.0, fresh: Boolean = true, prior: String = "HOLD"
    ) = AdaptiveDirectionInput(
        currentSide = current, waveCapitalRupees = 5_000.0, price = 100.0, atr = 1.0,
        spreadBps = spread, longDirectionalScore = longScore, longConfidence = longConfidence,
        shortDirectionalScore = shortScore, shortConfidence = shortConfidence,
        trajectoryComposite = trajectory, dataFresh = fresh, priorDecision = prior
    )

    @Test fun bearishContinuationChoosesShort() {
        val d = AdaptiveDirectionEngine.decide(input(longScore=-.70,longConfidence=.80,shortScore=.80,shortConfidence=.85,trajectory=-.60))
        assertEquals("SHORT", d.action)
        assertTrue(d.shortExpectedValueRupees > d.longExpectedValueRupees)
    }

    @Test fun capitulationReversalChoosesLong() {
        val d = AdaptiveDirectionEngine.decide(input(longScore=.80,longConfidence=.85,shortScore=-.50,shortConfidence=.75,trajectory=.50))
        assertEquals("LONG", d.action)
        assertTrue(d.longExpectedValueRupees > 0.0)
    }

    @Test fun ambiguousEvidenceChoosesNoTrade() {
        val d = AdaptiveDirectionEngine.decide(input(longScore=.05,longConfidence=.60,shortScore=.03,shortConfidence=.60,trajectory=0.0))
        assertEquals("HOLD", d.action)
    }

    @Test fun staleDataBlocksAutoOrderDecision() {
        val d = AdaptiveDirectionEngine.decide(input(longScore=.9,longConfidence=.9,shortScore=-.8,shortConfidence=.8,trajectory=.8,fresh=false))
        assertEquals("HOLD", d.action)
    }

    @Test fun hysteresisPreventsTinyFlipFlop() {
        val d = AdaptiveDirectionEngine.decide(input(current="LONG",longScore=.22,longConfidence=.68,shortScore=.28,shortConfidence=.70,trajectory=-.04,prior="LONG"))
        assertEquals("HOLD", d.action)
    }
}

class WaveCapitalPolicyTest {
    @Test fun twoPercentOnlyMakesWaveEligible() {
        assertEquals(2, WaveCapitalPolicy.triggeredWave(100.0, 98.0, 2.0, 10))
        val e = WaveCapitalPolicy.eligibility(2, 1, 5_000.0, 50_000.0, 200_000.0, 0.0, 2_500.0, 0.0, 1_500.0)
        assertTrue(e.eligible)
        assertEquals(5_000.0, e.availableCapitalRupees, .01)
    }

    @Test fun campaignCapCannotBeExceeded() {
        val e = WaveCapitalPolicy.eligibility(4, 3, 5_000.0, 198_500.0, 200_000.0, 0.0, 2_500.0, 0.0, 1_500.0)
        assertTrue(e.eligible)
        assertEquals(1_500.0, e.availableCapitalRupees, .01)
        val blocked = WaveCapitalPolicy.eligibility(5, 4, 5_000.0, 200_000.0, 200_000.0, 0.0, 2_500.0, 0.0, 1_500.0)
        assertFalse(blocked.eligible)
    }

    @Test fun lossCapsBlockNewWaveCapital() {
        assertFalse(WaveCapitalPolicy.eligibility(2,1,5_000.0,50_000.0,200_000.0,2_500.0,2_500.0,0.0,1_500.0).eligible)
        assertFalse(WaveCapitalPolicy.eligibility(2,1,5_000.0,50_000.0,200_000.0,0.0,2_500.0,1_500.0,1_500.0).eligible)
    }
}
