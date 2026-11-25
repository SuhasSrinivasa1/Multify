package com.multify.traderpro.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HistoricalLowDiscoveryPolicyTest {
    private val day=LocalDate.of(2026,11,25)
    private fun bars(close:Double)=listOf(
        EquityEodBar(day.minusDays(3),"ABC",100.0,105.0,1000),
        EquityEodBar(day.minusDays(2),"ABC",102.0,104.0,1000),
        EquityEodBar(day,"ABC",close-2,close,1000))
    @Test fun priorLowExcludesCurrentDayAndKeepsSignedBreakdown(){
        val x=HistoricalLowDiscoveryPolicy.evaluate("ABC",3,day,bars(98.0),2)!!
        assertEquals(100.0,x.priorLow,0.001)
        assertEquals(-2.0,x.distancePct,0.001)
        assertEquals(day.minusDays(3).toString(),x.priorLowDay)
    }
    @Test fun nearLowIncludesFivePercentButRejectsFarAway(){
        assertEquals(5.0,HistoricalLowDiscoveryPolicy.evaluate("ABC",3,day,bars(105.0),2)!!.distancePct,0.001)
        assertNull(HistoricalLowDiscoveryPolicy.evaluate("ABC",3,day,bars(105.1),2))
    }
    @Test fun missingCurrentCloseAndInsufficientHistoryAreNotResults(){
        assertNull(HistoricalLowDiscoveryPolicy.evaluate("ABC",3,day,bars(100.0).dropLast(1),2))
        assertNull(HistoricalLowDiscoveryPolicy.evaluate("ABC",6,day,bars(100.0),105))
    }
}
