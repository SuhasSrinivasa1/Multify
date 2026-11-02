package com.multify.traderpro.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixedShadowSamplingPolicyTest {
    @Test fun oneInTenCompletedScans() {
        val selected = (1..100).filter(FixedShadowSamplingPolicy::shouldSample)
        assertTrue(selected == listOf(10,20,30,40,50,60,70,80,90,100))
        assertFalse(FixedShadowSamplingPolicy.shouldSample(0))
        assertFalse(FixedShadowSamplingPolicy.shouldSample(9))
        assertFalse(FixedShadowSamplingPolicy.shouldSample(11))
    }
}
