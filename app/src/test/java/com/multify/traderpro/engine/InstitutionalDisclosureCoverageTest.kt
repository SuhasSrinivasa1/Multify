package com.multify.traderpro.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InstitutionalDisclosureCoverageTest {
    private val day=LocalDate.of(2026,11,24)
    private val header="Date,Symbol,Security Name,Client Name,Buy/Sell,Quantity Traded,Trade Price/Wght. Avg. Price,Remarks"
    private fun row(name:String,sym:String="ABC",date:String="24-NOV-2026")="$date,$sym,Example,$name,BUY,100,20.00,"
    private fun csv(vararg rows:String)=(listOf(header)+rows).joinToString("\n")
    @Test fun allUnmatchedRetainedWithoutCallingThemInstitutions(){
        val r=InstitutionalIntelligencePolicy.parseLatest(csv(row("UNKNOWN CLIENT"),row("OTHER CLIENT","DEF")),day,"BULK",1L)
        assertTrue(r.retainedAllRows)
        assertEquals(2,r.deals.size)
        assertEquals(2,r.unmatchedRows)
        assertTrue(InstitutionalIntelligencePolicy.top(r,"BUY","ALL").isEmpty())
        assertEquals(2,InstitutionalIntelligencePolicy.top(r,"BUY","DISCLOSED").size)
        assertEquals(2000.0,InstitutionalIntelligencePolicy.top(r,"BUY","UNCLASSIFIED").first().amountRupees,0.01)
        val s=InstitutionalSnapshot(bulk=r,sessions=listOf(day.toString()))
        assertEquals(2,InstitutionalHistoryPolicy.window(s,"BULK","BUY","DISCLOSED",1).rows.size)
        assertEquals(0,InstitutionalHistoryPolicy.window(s,"BULK","BUY","ALL",1).rows.size)
    }
    @Test fun verifiedAndUnknownValuesRemainSeparate(){
        val r=InstitutionalIntelligencePolicy.parseLatest(csv(row("SBI MUTUAL FUND"),row("UNKNOWN CLIENT")),day,"BULK",1L)
        assertEquals(4000.0,InstitutionalIntelligencePolicy.top(r,"BUY","DISCLOSED").single().amountRupees,0.01)
        assertEquals(2000.0,InstitutionalIntelligencePolicy.top(r,"BUY","DII").single().amountRupees,0.01)
        assertNull(InstitutionalIntelligencePolicy.category("SBI MUTUAL FUND BROKER"))
    }
    @Test fun acceptsActualRecentDateButRejectsMixedAndFutureDates(){
        val r=InstitutionalIntelligencePolicy.parseLatest(csv(row("UNKNOWN",date="23-NOV-2026")),day,"BLOCK",1L)
        assertEquals("2026-11-23",r.day)
        assertTrue(runCatching{InstitutionalIntelligencePolicy.parseLatest(csv(row("UNKNOWN",date="25-NOV-2026")),day,"BLOCK",1L)}.isFailure)
        assertTrue(runCatching{InstitutionalIntelligencePolicy.parseLatest(csv(row("UNKNOWN"),row("OTHER",date="23-NOV-2026")),day,"BLOCK",1L)}.isFailure)
    }
}
