package com.multify.traderpro.engine

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class InstitutionalHistoryPolicyTest {
    private fun d(day:String,side:String,qty:Long,price:Double,symbol:String="ABC",cat:String="FII",investor:String="GOVERNMENT OF SINGAPORE") =
        InstitutionalDeal(day,symbol,"Example",investor,cat,side,qty,price)
    private fun report(day:String,vararg deals:InstitutionalDeal,source:String="BULK") =
        InstitutionalReport(day,source,1L,deals.size,0,0,deals.toList())
    @Test fun sourceSeparatedDeduplicatedGrossNetAndRepeatBuyer() {
        val a=report("2026-11-23",d("2026-11-23","BUY",100,10.0),d("2026-11-23","SELL",20,11.0))
        val b=report("2026-11-24",d("2026-11-24","BUY",200,20.0),
            d("2026-11-24","BUY",50,10.0,"ABC","UNCLASSIFIED","UNKNOWN CLIENT"))
        val other=report("2026-11-24",d("2026-11-24","BUY",900,99.0),source="BLOCK")
        val s=InstitutionalSnapshot(b,other,listOf(a,b,other),listOf("2026-11-23","2026-11-24"))
        val w=InstitutionalHistoryPolicy.window(s,"BULK","BUY","ALL",5)
        assertEquals(2,w.reports)
        assertEquals(5,w.requested)
        val row=w.rows.single()
        assertEquals(5500.0,row.grossBuy,0.01)
        assertEquals(500.0,row.unclassifiedSide,0.01)
        assertEquals(5000.0,row.fiiSide,0.01)
        assertEquals(220.0,row.grossSell,0.01)
        assertEquals(4780.0,row.net,0.01)
        assertEquals(5000.0/300,row.weightedBuy!!,0.01)
        assertEquals(1,row.repeatedBuyers)
        assertEquals(2,row.buyDays)
    }
    @Test fun missingSessionIsNotZeroOrStaleToday() {
        val a=report("2026-11-23",d("2026-11-23","BUY",100,10.0))
        val s=InstitutionalSnapshot(bulk=a,history=listOf(a),sessions=listOf("2026-11-23","2026-11-24"))
        val w=InstitutionalHistoryPolicy.window(s,"BULK","BUY","ALL",1)
        assertEquals(0,w.reports)
        assertTrue(w.rows.isEmpty())
        assertEquals("2026-11-24",w.through)
        assertEquals(1,InstitutionalHistoryPolicy.window(s,"BULK","BUY","ALL",5).reports)
    }
    @Test fun topTwentyAndSectorTotalsIncludeUnshownRows() {
        val day="2026-11-24"
        val r=report(day,*Array(25){i->d(day,"BUY",100,(i+1).toDouble(),"S$i")})
        val s=InstitutionalSnapshot(bulk=r,sessions=listOf(day),sectors=r.deals.associate{it.symbol to "IT"})
        val w=InstitutionalHistoryPolicy.window(s,"BULK","BUY","FII",1)
        assertEquals(20,w.rows.size)
        assertEquals("S24",w.rows.first().symbol)
        assertEquals(32500.0,InstitutionalHistoryPolicy.sectorTotals(s,"BULK","ALL",1).single().buys,0.01)
        assertEquals(32500.0,InstitutionalHistoryPolicy.sectorTotals(s,"BULK","VERIFIED",1).single().buys,0.01)
        assertTrue(InstitutionalHistoryPolicy.window(s,"BULK","BUY","DII",1).rows.isEmpty())
    }
    @Test fun hourlySlotUsesIstAndNeverCatchesUpMissedHours() {
        assertNull(InstitutionalHistoryPolicy.slot(ZonedDateTime.parse("2026-11-24T09:59:00+05:30")))
        assertEquals("2026-11-24-10",InstitutionalHistoryPolicy.slot(ZonedDateTime.parse("2026-11-24T04:30:00Z")))
        assertEquals("2026-11-24-15",InstitutionalHistoryPolicy.slot(ZonedDateTime.parse("2026-11-24T15:20:00+05:30")))
        assertNull(InstitutionalHistoryPolicy.slot(ZonedDateTime.parse("2026-11-24T15:21:00+05:30")))
        assertNull(InstitutionalHistoryPolicy.slot(ZonedDateTime.parse("2026-11-24T16:00:00+05:30")))
        assertNull(InstitutionalHistoryPolicy.movement(100.0,null))
        assertEquals(10.0,InstitutionalHistoryPolicy.movement(110.0,100.0)!!,0.01)
    }
}
