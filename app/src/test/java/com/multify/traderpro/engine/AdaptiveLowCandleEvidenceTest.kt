package com.multify.traderpro.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLowCandleEvidenceTest {
    private fun b(i:Int,o:Double?,h:Double?,l:Double,c:Double)=
        EquityEodBar(LocalDate.of(2026,11,1).plusDays(i.toLong()),"TEST",l,c,1000,open=o,high=h)

    @Test fun hammerAndEngulfingUseRealOhlc() {
        val hammer=b(1,99.0,101.0,90.0,100.0)
        assertEquals("HAMMER_SHAPE",AdaptiveLowCandleEvidence.patternAt(listOf(hammer),0))
        val engulf=listOf(b(0,105.0,106.0,98.0,99.0),b(1,98.0,108.0,97.0,106.0))
        assertEquals("BULLISH_ENGULFING",AdaptiveLowCandleEvidence.patternAt(engulf,1))
    }
    @Test fun noFutureLeakOrFabricatedOhlc() {
        val missing=b(1,null,null,90.0,100.0)
        assertNull(AdaptiveLowCandleEvidence.patternAt(listOf(missing),0))
        assertNull(AdaptiveLowCandleEvidence.patternAt(listOf(b(1,100.0,99.0,90.0,101.0)),0))
        val today=b(1,102.0,104.0,98.0,99.0)
        val future=b(2,98.0,110.0,97.0,109.0)
        assertEquals(AdaptiveLowCandleEvidence.patternAt(listOf(today),0),
            AdaptiveLowCandleEvidence.patternAt(listOf(today,future),0))
        assertFalse(AdaptiveLowCandleEvidence.bullish(null))
        assertFalse(AdaptiveLowCandleEvidence.bullish("DOJI_SHAPE"))
    }
    @Test fun piercingAndStrongCloseAreSeparateResearchShapes() {
        val p=b(0,110.0,111.0,99.0,100.0)
        val c=b(1,98.0,109.0,97.0,107.0)
        assertEquals("PIERCING_SHAPE",AdaptiveLowCandleEvidence.patternAt(listOf(p,c),1))
        assertEquals("STRONG_BULLISH_CLOSE",AdaptiveLowCandleEvidence.patternAt(listOf(b(1,91.0,101.0,90.0,100.0)),0))
    }
}
