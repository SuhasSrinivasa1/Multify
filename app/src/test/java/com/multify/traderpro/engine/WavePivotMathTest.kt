package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WavePivotMathTest {
    @Test fun referenceFallbackArmsAtFortyBasisPoints() {
        assertFalse(WavePivotMath.shouldArm("UP", 100.0, 100.39))
        assertTrue(WavePivotMath.shouldArm("UP", 100.0, 100.40))
        assertFalse(WavePivotMath.shouldArm("DOWN", 100.0, 99.61))
        assertTrue(WavePivotMath.shouldArm("DOWN", 100.0, 99.60))
    }

    @Test fun confirmsAfterReferencePivotReversal() {
        assertFalse(WavePivotMath.shouldConfirm("UP", 102.0, 101.60))
        assertTrue(WavePivotMath.shouldConfirm("UP", 102.0, 101.59))
        assertFalse(WavePivotMath.shouldConfirm("DOWN", 98.0, 98.39))
        assertTrue(WavePivotMath.shouldConfirm("DOWN", 98.0, 98.40))
    }

    @Test fun movePercentIsAbsoluteLegSize() {
        assertEquals(2.0, WavePivotMath.movePct(100.0, 102.0), 0.0001)
        assertEquals(2.0, WavePivotMath.movePct(100.0, 98.0), 0.0001)
    }
}
