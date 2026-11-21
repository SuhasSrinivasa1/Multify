package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class MisIntradayLongPolicyTest {
    @Test fun fixedAndManualKeepExistingLongStopLadder() {
        val x = MisIntradayLongPolicy
        assertEquals(99.0, x.desiredStop(100.0,100.0,false),1e-6)
        assertEquals(100.50,x.desiredStop(100.0,100.55,false),1e-6)
        assertEquals(100.60,x.desiredStop(100.0,100.75,false),1e-6)
        assertEquals(100.70,x.desiredStop(100.0,100.85,false),1e-6)
        assertEquals(100.80,x.desiredStop(100.0,100.95,false),1e-6)
        assertEquals(100.80,x.tighterStop(100.80,100.0,100.60,false),1e-6)
    }
    @Test fun workingRangeUsesUnchangedFullTargetAndOnePercentHardStop() {
        val x=MisIntradayLongPolicy
        assertEquals(99.0,x.desiredStop(100.0,101.8,true),1e-6)
        assertEquals(99.0,x.tighterStop(99.0,100.0,102.0,true),1e-6)
        assertEquals(WorkingRangeExitPolicy.TARGET_REASON,
            WorkingRangeExitPolicy.exitReason(100.0,101.12,101.12))
    }
    @Test fun onlyBrokerPendingSlmProvesMisLongProtection() {
        val x=MisIntradayLongPolicy
        assertFalse(x.verified(x.STOP_PENDING,"abc","OPEN"))
        assertFalse(x.verified(x.STOP_VERIFIED,"abc","EXECUTED"))
        assertFalse(x.verified(x.STOP_VERIFIED,null,"OPEN"))
        assertTrue(x.verified(x.STOP_VERIFIED,"broker123","TRIGGER_PENDING"))
    }
    @Test fun allMisEntriesStopBeforeEarlySquareOff() {
        val z=ZoneId.of("Asia/Kolkata")
        fun d(h:Int,m:Int)=ZonedDateTime.of(2026,11,23,h,m,0,0,z)
        assertTrue(MisIntradayLongPolicy.mayEnter(d(9,16)))
        assertTrue(MisIntradayLongPolicy.mayEnter(d(14,29)))
        assertFalse(MisIntradayLongPolicy.mayEnter(d(14,30)))
        assertFalse(MisIntradayLongPolicy.mustExit(d(14,54)))
        assertTrue(MisIntradayLongPolicy.mustExit(d(14,55)))
    }
}
