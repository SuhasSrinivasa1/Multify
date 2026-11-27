package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class FixedDegradedSweepAdmissionTest {
    @Test fun betaOnlyCannotComplete72At40PerMinute(){
        assertFalse(FixedDegradedSweepAdmission.betaOnlyFeasible(72,40,1500))
        assertTrue(FixedDegradedSweepAdmission.betaOnlyFeasible(36,40,1500))
        assertFalse(FixedDegradedSweepAdmission.betaOnlyFeasible(36,40,4000))
    }
    @Test fun invalidInputsFailClosed(){assertFalse(FixedDegradedSweepAdmission.betaOnlyFeasible(72,40,0))}
}
