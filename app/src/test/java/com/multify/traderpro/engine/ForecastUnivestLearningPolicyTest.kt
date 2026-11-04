package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastUnivestLearningPolicyTest {
    private val p = ForecastUnivestLearningPolicy
    private fun india(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    @Test fun capturesOfficialLabelledCashEquityAcrossHoldingHorizons() {
        val msg = "New Advisory Pick\nSTOCK: GRAPHITE\nDuration: 1-3 Months"
        assertEquals(ForecastUnivestLearningPolicy.Type.ENTRY, p.parse("com.univest.capp", msg)?.type)
        assertEquals("GRAPHITE", p.parse("com.univest.capp", msg)?.symbol)
        assertNull(p.parse("com.random.app", msg))
        assertEquals(ForecastUnivestLearningPolicy.Type.ENTRY, p.parse("com.univest.capp", msg.replace("1-3", "3-6"))?.type)
        assertEquals(ForecastUnivestLearningPolicy.Type.ENTRY, p.parse("com.univest.capp", msg.replace("1-3", "6"))?.type)
        assertNull(p.parse("com.univest.capp", msg.replace("New Advisory Pick", "Options New Advisory Pick")))
        assertEquals(ForecastUnivestLearningPolicy.Type.ENTRY, p.parse("com.univest.capp", "New Advisory Pick\nAdvisory Pick: ABC\nDuration: 2 Days")?.type)
        assertEquals(ForecastUnivestLearningPolicy.Type.EXIT, p.parse("com.univest.capp", "Position closed\nSTOCK: ABC")?.type)
        assertNull(p.parse("com.univest.capp", "Position closed without any labelled ticker"))
    }

    @Test fun recognizesRealYatharthAdvisoryWithoutTreatingPotentialAsProfit() {
        val message = "New Advisory Pick posted\nSTOCK: YATHARTH\nDuration: 1-3 Months\nPotential: 5.00% 📈"
        val parsed = p.parse("com.univest.capp", message)
        assertEquals(ForecastUnivestLearningPolicy.Type.ENTRY, parsed?.type)
        assertEquals("YATHARTH", parsed?.symbol)
        assertEquals(3.0, parsed!!.durationMonths!!, 0.0001)
    }

    @Test fun officialProfitOfDayExitMatchesSameDayEntryOnly() {
        val exit = p.parse("com.univest.capp", "1st Equity Profit of the Day\nAdvisory Pick: GRAPHITE\nDuration: 2 Days")
        assertEquals(ForecastUnivestLearningPolicy.Type.EXIT, exit?.type)
        assertEquals("GRAPHITE", exit?.symbol)
        val morning = india(2026,10,30,9,30)
        val afternoon = india(2026,10,30,14,30)
        assertTrue(p.sameTradingDay(morning, afternoon))
        assertFalse(p.sameTradingDay(morning, india(2026,11,2,10,15)))
        assertFalse(p.sameTradingDay(afternoon, morning))
    }

    @Test fun trainingRequiresMatureSameDayDenominatorAnd75Percent() {
        val point = (1..48).associate { "p$it" to (it.toDouble() / 48) }
        val cases = (1..30).map { i ->
            ForecastUnivestLearningPolicy.ClosedCase("STK${i}", "2026-10-${(i%10+10)}", if (i <= 24) 1.2 else -0.8, point)
        }
        val good = p.evaluate(cases, point, "2026-11-01")
        assertTrue(good.eligible)
        assertTrue(good.bonus > 0.0)
        assertTrue(good.bonus <= p.MAX_BONUS)
        assertEquals(0.80,good.hitRate,0.00001)
        val bad = cases.mapIndexed { i, x -> if (i < 11) x.copy(returnPct = -0.5) else x }
        assertFalse(p.evaluate(bad, point, "2026-11-01").eligible)
        assertEquals(0.0,p.evaluate(cases.take(12),point,"2026-11-01").bonus,0.0)
        assertEquals(0.0,p.evaluate(cases,point,"2026-10-01").bonus,0.0)
    }

    @Test fun incomplete48FingerprintDoesNotCreateFalseConfidence() {
        val points=(1..30).map { i -> ForecastUnivestLearningPolicy.ClosedCase("T${i%5}","2026-10-${(i%10+10)}",1.0,
            (1..8).associate { "f$it" to it.toDouble() }) }
        assertEquals(0.0,p.evaluate(points,(1..8).associate { "f$it" to it.toDouble() },"2026-11-01").bonus,0.0)
    }
}
