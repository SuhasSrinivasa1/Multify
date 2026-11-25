package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class OwnershipIntelligenceTest {
    private val day="2026-11-25"
    private fun deal(symbol:String,side:String,qty:Long,cat:String,investor:String)=InstitutionalDeal(day,symbol,symbol,investor,cat,side,qty,100.0)
    private fun report(source:String,vararg d:InstitutionalDeal)=InstitutionalReport(day,source,1L,d.size,0,0,d.toList(),true)
    @Test fun combinedExcludesOverlapAndMutualFundsAreNotCountedTwice(){
        val mf=deal("ABC","BUY",100,"DII","HDFC MUTUAL FUND")
        val fii=deal("ABC","SELL",30,"FII","GOVERNMENT OF SINGAPORE")
        val promoter=deal("ABC","BUY",50,"PROMOTER","PROMOTER NAME")
        val bulk=report("BULK",mf,fii)
        val block=report("BLOCK",mf)
        val pit=report("PROMOTER",promoter)
        val snap=InstitutionalSnapshot(bulk=bulk,block=block,promoter=pit,sessions=listOf(day))
        val w=InstitutionalHistoryPolicy.window(snap,"COMBINED","BUY","OWNERSHIP",1,limit=10)
        assertEquals(1,w.excludedOverlaps)
        assertEquals(12000.0,w.rows.single().net,0.001)
        assertEquals(10000.0,w.rows.single().mfSide,0.001)
        assertEquals(0.0,w.rows.single().diiSide,0.001)
        assertEquals(5000.0,w.rows.single().promoterSide,0.001)
        assertTrue(InstitutionalHistoryPolicy.window(snap,"COMBINED","SELL","OWNERSHIP",1,limit=10).rows.isEmpty())
        assertTrue(InstitutionalHistoryPolicy.window(snap,"COMBINED","BUY","DII",1).rows.isEmpty())
    }
    @Test fun topTenByNetValue(){
        val bulk=report("BULK",*Array(15){i->deal("S$i","BUY",(i+1).toLong(),"FII","GOVERNMENT OF SINGAPORE")})
        val snap=InstitutionalSnapshot(bulk=bulk,sessions=listOf(day))
        val rows=InstitutionalHistoryPolicy.window(snap,"COMBINED","BUY","OWNERSHIP",1,limit=10).rows
        assertEquals(10,rows.size)
        assertEquals("S14",rows.first().symbol)
    }
    @Test fun xbrlMarketPurchaseAndRevision(){
        val filing=PromoterFiling("new","old","ABC","ABC",day,"https://nsearchives.nseindia.com/corporate/test.xml")
        val xml="""<xbrl xmlns:p="urn:pit">
          <p:CategoryOfPerson contextRef="Disclosure1">Promoter Group</p:CategoryOfPerson>
          <p:TypeOfInstrument contextRef="Disclosure1">Equity</p:TypeOfInstrument>
          <p:NameOfThePerson contextRef="Disclosure1">Example Name</p:NameOfThePerson>
          <p:SecuritiesAcquiredOrDisposedTransactionType contextRef="Disclosure1">Buy</p:SecuritiesAcquiredOrDisposedTransactionType>
          <p:ModeOfAcquisitionOrDisposal contextRef="Disclosure1">Market Purchase</p:ModeOfAcquisitionOrDisposal>
          <p:SecuritiesAcquiredOrDisposedNumberOfSecurity contextRef="Disclosure1">100</p:SecuritiesAcquiredOrDisposedNumberOfSecurity>
          <p:SecuritiesAcquiredOrDisposedValueOfSecurity contextRef="Disclosure1">10500</p:SecuritiesAcquiredOrDisposedValueOfSecurity>
          <p:DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyFromDate contextRef="Disclosure1">2026-11-24</p:DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyFromDate>
          <p:DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyToDate contextRef="Disclosure1">2026-11-24</p:DateOfAllotmentAdviceOrAcquisitionOfSharesOrSaleOfSharesSpecifyToDate>
        </xbrl>"""
        val parsed=PromoterDisclosurePolicy.parseXml(filing,xml,100L)
        assertEquals(10500.0,parsed.deals.single().reportedValue!!,0.01)
        assertEquals("2026-11-24",parsed.deals.single().day)
        assertTrue(PromoterDisclosurePolicy.parseXml(filing,xml.replace("Market Purchase","Gift"),100L).deals.isEmpty())
        val old=parsed.copy(filing=filing.copy(id="old",previousId=""))
        assertEquals(1,PromoterDisclosurePolicy.reports(listOf(old,parsed),setOf("old")).single().deals.size)
    }
    @Test fun filingIndexRejectsUnknownHost(){
        val json="""{"data":[{"appId":"1","symbol":"ABC","companyName":"ABC","broadcastDateTime":"25-Nov-2026 12:30:00","xmlFileName":"https://nsearchives.nseindia.com/corporate/test.xml"}]}"""
        val today=LocalDate.parse(day)
        assertEquals(1,PromoterDisclosurePolicy.parseList(json,today,today).size)
        assertTrue(runCatching{PromoterDisclosurePolicy.parseList(json.replace("nsearchives.nseindia.com","example.org"),today,today)}.isFailure)
    }
}
