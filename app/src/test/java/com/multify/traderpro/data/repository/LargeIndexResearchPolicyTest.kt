package com.multify.traderpro.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LargeIndexResearchPolicyTest {
    private val date=LocalDate.of(2026,11,23)
    private fun report(day:String="23-11-2026"):String=buildString{
        append("Index Name,Index Date,Open Index Value,High Index Value,Low Index Value,Closing Index Value,Points Change,Change(%),Volume,Turnover (Rs. Cr.),P/E,P/B,Div Yield\n")
        append("NIFTY 50,${day},26000,26100,25900,26065,65,0.25,100,1000,22.1,3.4,1.1\n")
        for(i in 0..42) append("NIFTY SECTOR ${i},${day},100,102,98,101,1,1.0,100,100,12.5,2.1,1\n")
    }
    @Test fun exactDatedNiftyAndSectorValuationsAreParsed() {
        val r=LargeIndexResearchPolicy.parseCsv(report(),date)
        assertEquals(44,r.size)
        assertEquals(26065.0,r.first{it.name=="NIFTY 50"}.close,0.0001)
        assertEquals(0.25,r.first{it.name=="NIFTY 50"}.changePct!!,0.001)
        assertEquals(22.1,r.first{it.name=="NIFTY 50"}.pe!!,0.001)
        assertEquals(3.4,r.first{it.name=="NIFTY 50"}.pb!!,0.001)
    }
    @Test fun wrongDayOrPartialFilesAreRejected() {
        assertTrue(runCatching { LargeIndexResearchPolicy.parseCsv(report("20-11-2026"),date) }.isFailure)
        assertTrue(runCatching { LargeIndexResearchPolicy.parseCsv("error",date) }.isFailure)
    }
    @Test fun marketRegimeAndSectorMapRequireKnownEvidence() {
        assertEquals("BULLISH",LargeIndexResearchPolicy.regime(0.3))
        assertEquals("FLAT",LargeIndexResearchPolicy.regime(0.0))
        assertEquals("BEARISH",LargeIndexResearchPolicy.regime(-0.3))
        assertEquals("UNKNOWN",LargeIndexResearchPolicy.regime(null))
        assertEquals("NIFTY IT",LargeIndexResearchPolicy.sectorIndex("Information Technology"))
        assertEquals("NIFTY BANK",LargeIndexResearchPolicy.sectorIndex("BANK"))
        assertNull(LargeIndexResearchPolicy.sectorIndex("UNKNOWN"))
    }
}
