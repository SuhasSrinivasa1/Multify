package com.multify.traderpro.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationParserTest {
    private val parser = NotificationParser()

    @Test fun parsesVijayaRelease() {
        val s = parser.parse(
            "Multyfi",
            "Released: Equity Intraday Trade",
            """✅ Released: Equity Intraday Trade
🔶 Stock Name: VIJAYA
🔶 Target: 1540
🔶 Entry Range: 1487.2-1489.2
🔶 Stop Loss: 1470"""
        )
        assertEquals(SignalType.TRADE_RELEASE, s.type)
        assertEquals("VIJAYA", s.symbol)
        assertEquals(1487.2, s.entryLow!!, 0.0001)
        assertEquals(1489.2, s.entryHigh!!, 0.0001)
    }

    @Test fun parsesCommaFormattedPricesAndReportedReturn() {
        val s = parser.parse(
            "Multyfi",
            "Released: Equity Intraday Trade",
            """✅ Released: Equity Intraday Trade
🔶 Stock Name: PGIL
🔶 Target: 1,355
🔶 Entry Range: 1,296.20-1,298.20
🔶 Stop Loss: 1,280"""
        )
        assertEquals(SignalType.TRADE_RELEASE, s.type)
        assertEquals(1355.0, s.target!!, 0.0001)
        assertEquals(1296.20, s.entryLow!!, 0.0001)
        assertEquals(1298.20, s.entryHigh!!, 0.0001)
        assertEquals(1280.0, s.stopLoss!!, 0.0001)

        val exit = parser.parse("Multyfi", null, "✅ Book Profit : PGIL\nExit Price: 1,321\nReturns 🚀 : 1.83% on capital")
        assertEquals(1321.0, exit.exitPrice!!, 0.0001)
        assertEquals(1.83, exit.returnsPct!!, 0.0001)
    }

    @Test fun parsesTarsonsSubmitted() {
        val s = parser.parse("BUY SUBMITTED", "BUY SUBMITTED • INTRADAY • TARSONS • qty 292", null)
        assertEquals(SignalType.BUY_SUBMITTED, s.type)
        assertEquals("TARSONS", s.symbol)
        assertEquals(292, s.quantity)
    }

    @Test fun parsesShadowfaxBookProfit() {
        val s = parser.parse("Multyfi", null, "✅ Book Profit : SHADOWFAX\nExit Price: 251.05\nReturns 🚀 : 0.11% on capital")
        assertEquals(SignalType.BOOK_PROFIT, s.type)
        assertEquals("SHADOWFAX", s.symbol)
        assertEquals(251.05, s.exitPrice!!, 0.0001)
    }
}
