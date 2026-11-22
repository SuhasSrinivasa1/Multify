package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LargeCapMomentumPolicyTest {
    @Test fun intradayRelativeStrengthUsesOnlySuppliedPricesAndSectorBreadth() {
        val rows = LargeCapMomentumPolicy.intraday(
            listOf(
                LargeCapIntradayInput("BANKA", "Bank A", "Banks", 110.0, 100.0, 109.0),
                LargeCapIntradayInput("BANKB", "Bank B", "Banks", 105.0, 100.0, 104.0),
                LargeCapIntradayInput("ITA", "IT A", "IT", 98.0, 100.0, 99.0)
            ),
            1234L
        )
        assertEquals(3, rows.size)
        val bankA = rows.first { it.symbol == "BANKA" }
        assertEquals(10.0, bankA.dayMovePct, 0.001)
        assertEquals(100.0, bankA.sectorBreadthPct, 0.001)
        assertTrue(bankA.relativeStrengthPct > 0.0)
    }

    @Test fun swingClassificationUsesDailyMomentumAndVolumeWithoutTouchingFixed() {
        val closes = (0 until 60).map { 100.0 + it * 1.5 }
        val highs = closes.map { it + 1.0 }
        val lows = closes.map { it - 1.0 }
        val volumes = (0 until 60).map { if (it == 59) 1500L else 1000L }
        val row = LargeCapMomentumPolicy.swing(
            LargeCapSwingInput("LARGE", "Large Ltd", "Financial Services",
                closes, highs, lows, volumes, "2026-11-20")
        )!!
        assertEquals("BREAKOUT", row.stage)
        assertTrue(row.twentyDayReturnPct > 0.0)
        assertEquals(1.5, row.volumeRatio20!!, 0.001)
        assertEquals("2026-11-20", row.asOfDate)
    }
}
