package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveDirectionMathTest {
    @Test fun waveIndexAdvancesEveryTwoPercentEitherDirection() {
        assertEquals(0, WaveDirectionMath.waveIndex(100.0, 98.01))
        assertEquals(1, WaveDirectionMath.waveIndex(100.0, 97.5))
        assertEquals(2, WaveDirectionMath.waveIndex(100.0, 95.9))
        assertEquals(1, WaveDirectionMath.waveIndex(100.0, 102.2))
    }

    @Test fun bearishEvidenceSelectsOnlyShort() {
        val d = WaveDirectionMath.decide(
            currentSide="LONG", trancheRupees=5000.0, price=100.0, atr=1.2, spreadBps=8.0,
            longDirectionalScore=-0.45, longConfidence=0.31,
            shortDirectionalScore=0.72, shortConfidence=0.80
        )
        assertEquals("SHORT", d.action)
        assertTrue(d.shortExpectedValueRupees > d.longExpectedValueRupees)
    }

    @Test fun weakMixedEvidenceHoldsInsteadOfForcingBothOrChurning() {
        val d = WaveDirectionMath.decide(
            currentSide="LONG", trancheRupees=5000.0, price=100.0, atr=.8, spreadBps=10.0,
            longDirectionalScore=.06, longConfidence=.54,
            shortDirectionalScore=.05, shortConfidence=.53
        )
        assertEquals("HOLD", d.action)
    }
}
