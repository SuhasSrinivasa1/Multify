package com.multify.traderpro.data.repository

import com.multify.traderpro.engine.EquityEodBar
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class LargeWatchFeaturePolicyTest {
    @Test fun exactlySixtyValuesWithNoInventedStockPBOrIndexData() {
        val last=LocalDate.of(2026,11,23)
        val bars=(0 until 90).map { i ->
            val day=last.minusDays((89-i).toLong())
            EquityEodBar(day,"INFY",100.0+i*0.2,101.0+i*0.2,10000L+i*10)
        }
        val e=LargeWatchFeaturePolicy.build(
            bars,last.toString(),18.0,40,
            emptyList(),emptyList(),"NIFTY IT",null,null,null,
            2.0,1.0,65.0,1.5,true,false
        )!!
        assertEquals(60,LargeWatchFeaturePolicy.METRICS.size)
        assertEquals(LargeWatchFeaturePolicy.METRICS,e.features.keys.toList())
        assertEquals(18.0,e.features["stock_pe"]!!,0.01)
        assertNull(e.features["stock_pb"])
        assertNull(e.features["nifty_eod_move_pct"])
        assertEquals("UNKNOWN",e.niftyRegime)
        assertFalse(e.indexEvidenceComplete)
        assertEquals(1.0,e.features["swing_qualified"]!!,0.01)
        assertEquals(0.0,e.features["intraday_qualified"]!!,0.01)
        assertNull(LargeWatchFeaturePolicy.build(bars,last.plusDays(1).toString(),null,null,
            emptyList(),emptyList(),null,null,null,null,null,null,null,null,true,false))
    }
    @Test fun sameDateNiftyAndSectorIndexedComparisonsAreNotLiveQuotes() {
        val d=LocalDate.of(2026,11,23)
        val bars=(0 until 90).map{i->
            EquityEodBar(d.minusDays((89-i).toLong()),"INFY",90.0+i,91.0+i,10_000)
        }
        val nifty=(0..6).map{i->
            LargeIndexDaily(d.minusDays((6-i).toLong()),"NIFTY 50",100.0+i,0.4,20.0,3.0)
        }
        val sector=(0..6).map{i->
            LargeIndexDaily(d.minusDays((6-i).toLong()),"NIFTY IT",100.0+i,0.8,25.0,4.0)
        }
        val e=LargeWatchFeaturePolicy.build(
            bars,d.toString(),null,null,nifty,sector,"NIFTY IT",
            1.0,0.4,60.0,2.0,0.7,60.0,1.3,true,true
        )!!
        assertTrue(e.indexEvidenceComplete)
        assertEquals("BULLISH",e.niftyRegime)
        assertEquals("BULLISH",e.sectorRegime)
        assertEquals(3.0,e.features["nifty_index_pb"]!!,0.01)
        assertEquals(4.0,e.features["sector_index_pb"]!!,0.01)
        assertNull(e.features["stock_pb"])
    }
}
