package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class PortfolioShadowPolicyTest {
    @Test fun armingAndGap() {
        val s=PortfolioShadowPolicy.initial(100.0,100)
        val a=PortfolioShadowPolicy.observe(s,100,100.0,120.0,1)
        assertTrue(a.armed)
        val b=PortfolioShadowPolicy.observe(a,100,100.0,140.0,2)
        assertEquals(listOf(135.0,132.0,130.0),PortfolioShadowPolicy.levels(b))
        val c=PortfolioShadowPolicy.observe(b,100,100.0,129.0,3)
        assertEquals(3,c.stages)
        assertEquals(3,c.events.count{it.kind.startsWith("PAPER_TRAIL")})
        assertEquals(PortfolioShadowPolicy.levels(b),PortfolioShadowPolicy.levels(c))
        assertEquals(c,PortfolioShadowPolicy.observe(c,100,100.0,129.0,3))
    }
    @Test fun holdingChangeFreezes() {
        val s=PortfolioShadowPolicy.observe(PortfolioShadowPolicy.initial(100.0,100),100,100.0,130.0,1)
        val t=PortfolioShadowPolicy.observe(s,101,101.0,150.0,2)
        assertTrue(t.changed)
        assertEquals(s.peak,t.peak,0.0)
    }
    @Test fun budgetAndMissingEvidence() {
        assertEquals(4,PortfolioShadowPolicy.stepQty(100.0,500.0))
        assertEquals(0,PortfolioShadowPolicy.stepQty(100.0,0.0))
        assertNull(PortfolioShadowPolicy.trend(emptyList()))
        assertEquals("WAIT",PortfolioShadowPolicy.candidate(PortfolioShadowPolicy.initial(100.0,100),null,110.0,500.0,false).kind)
    }
}
