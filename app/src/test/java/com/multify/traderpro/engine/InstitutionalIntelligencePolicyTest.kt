package com.multify.traderpro.engine

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InstitutionalIntelligencePolicyTest {
    private val day=LocalDate.of(2026,11,23)
    private val header="Date,Symbol,Security Name,Client Name,Buy/Sell,Quantity Traded,Trade Price/Wght. Avg. Price,Remarks"
    private fun row(symbol:String="ABC",client:String="GOVERNMENT OF SINGAPORE",side:String="BUY",qty:String="1000",price:String="200.00",date:String="23-NOV-2026")=
        "$date,$symbol,Example Ltd,$client,$side,$qty,$price,"
    private fun parse(vararg rows:String,source:String="BULK")=
        InstitutionalIntelligencePolicy.parse((listOf(header)+rows).joinToString("\n"),day,source,123L)
    @Test fun datedExactIdentityGrossSidesAndDuplicates() {
        val a=row()
        val r=parse(a,a,row(client="SBI MUTUAL FUND",qty="2000"),
            row(client="SBI MUTUAL FUND",side="SELL",qty="300"),
            row(client="SBI MUTUAL FUND BROKERAGE"),row(client="SOME FOREIGN CAPITAL"))
        assertEquals(6,r.disclosedRows)
        assertEquals(1,r.duplicateRows)
        assertEquals(2,r.unmatchedRows)
        assertEquals(5,r.retainedRows)
        assertEquals(3,r.matchedRows)
        val all=InstitutionalIntelligencePolicy.top(r,"BUY","ALL").single()
        assertEquals(1000000.0,all.amountRupees,0.01)
        assertEquals(200000.0,all.fiiRupees,0.01)
        assertEquals(400000.0,all.diiRupees,0.01)
        assertEquals(5000L,all.shares)
        assertEquals(600000.0,InstitutionalIntelligencePolicy.top(r,"BUY","VERIFIED").single().amountRupees,0.01)
        assertEquals(400000.0,InstitutionalIntelligencePolicy.top(r,"BUY","UNCLASSIFIED").single().amountRupees,0.01)
        assertEquals(2,r.deals.count{it.category=="UNCLASSIFIED"})
        assertEquals(60000.0,InstitutionalIntelligencePolicy.top(r,"SELL","DII").single().amountRupees,0.01)
        assertTrue(InstitutionalIntelligencePolicy.top(r,"SELL","FII").isEmpty())
    }
    @Test fun strictlyTwentyUniqueStockRankingsByRupees() {
        val r=parse(*Array(25){i->row(symbol="S$i",qty="1000",price=(i+1).toString())})
        val ranked=InstitutionalIntelligencePolicy.top(r,"BUY","FII")
        assertEquals(20,ranked.size)
        assertEquals("S24",ranked.first().symbol)
        assertEquals("S5",ranked.last().symbol)
        assertEquals(20,ranked.map{it.symbol}.toSet().size)
    }
    @Test fun staleInvalidAndUndatedAreRejected() {
        assertTrue(runCatching{parse(row(date="20-NOV-2026"))}.isFailure)
        assertTrue(runCatching{parse(row(price="-1"))}.isFailure)
        assertTrue(runCatching{parse(row(qty="0"))}.isFailure)
        assertTrue(runCatching{parse(row(side="UNKNOWN"))}.isFailure)
        assertTrue(runCatching{parse()}.isFailure)
        assertTrue(runCatching{InstitutionalIntelligencePolicy.parse("<html>bad report</html>",day,"BULK",123)}.isFailure)
    }
    @Test fun sameDealInSeparateFilesIsNotSummed() {
        val a=parse(row(),source="BULK")
        val b=parse(row(),source="BLOCK")
        assertEquals("BULK",a.source)
        assertEquals("BLOCK",b.source)
        assertEquals(200000.0,InstitutionalIntelligencePolicy.top(a,"BUY","ALL").single().amountRupees,0.01)
        assertEquals(200000.0,InstitutionalIntelligencePolicy.top(b,"BUY","ALL").single().amountRupees,0.01)
    }
    @Test fun quotedCommaAndNoUnknownInstitutionGuessing() {
        val r=parse(row().replace("Example Ltd","\"Example, Ltd\"").replace(",1000,",",\"1,000\","))
        assertEquals("Example, Ltd",r.deals.single().company)
        assertEquals(1000L,r.deals.single().shares)
        assertNull(InstitutionalIntelligencePolicy.category("GOLDMAN SACHS SECURITIES"))
        assertNull(InstitutionalIntelligencePolicy.category("SBI MUTUAL FUND UNKNOWN"))
    }
}
