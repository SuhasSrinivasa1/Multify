package com.multify.traderpro.data.repository
import org.junit.Assert.*
import org.junit.Test
class FixedFiveMinuteShadowCohortTest {
    private fun row(at: Long, f: String, v: String) = FixedFiveMinuteShadowPair(
        date="2026-11-03",atMs=at,symbol="KROSS",entryPrice=274.0,targetPct=1.2,
        fifteenMinuteTier=f,fiveMinuteTier=v,
        fifteenMinuteConfidence=0.7,fiveMinuteConfidence=0.8,
        fifteenMinuteDirection=0.3,fiveMinuteDirection=0.5,
        fifteenMinuteStrategy="TREND",fiveMinuteStrategy="TREND")
    @Test fun comparisonGroupsDoNotConfuseSharedPriceWithIndependentTrades() {
        val opening=java.time.ZonedDateTime.of(2026,11,3,9,32,0,0,
            java.time.ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertEquals("ONLY_5M_PRIMARY",row(opening,"RESEARCH","PRIMARY").comparisonGroup)
        assertEquals("ONLY_15M_PRIMARY",row(opening,"PRIMARY","RESEARCH").comparisonGroup)
        assertEquals("BOTH_PRIMARY",row(opening,"PRIMARY","PRIMARY").comparisonGroup)
        assertEquals("NEITHER_PRIMARY",row(opening,"RESEARCH","RESEARCH").comparisonGroup)
        assertEquals("OPEN_0915_1015",row(opening,"PRIMARY","PRIMARY").sessionBucket)
        assertFalse(row(opening,"PRIMARY","PRIMARY").sampledTargetBeforeRisk)
    }
}
