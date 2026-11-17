package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedIndicatorWarmupDiagnosticPolicyTest {
    private val p=FixedIndicatorWarmupDiagnosticPolicy
    @Test fun suvenFourteenBarsAreNotEnoughForNamedIndicators() {
        val s=p.assess(14)
        assertFalse(s.ema50Warm)
        assertFalse(s.atr14Warm)
        assertFalse(s.rsi14Warm)
        assertFalse(s.macdSignalWarm)
        assertTrue(s.ema9Warm)
    }
    @Test fun sufficientBarsDoNotFabricateFiveMinuteRangeFromFifteen() {
        val s=p.assess(60,15)
        assertTrue(s.macdSignalWarm)
        assertTrue(s.ema50Warm)
        assertFalse(s.orb5IndependentlyObservable)
    }
}
