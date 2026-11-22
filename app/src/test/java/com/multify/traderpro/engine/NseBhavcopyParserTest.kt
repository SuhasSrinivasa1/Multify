package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.LocalDate

class NseBhavcopyParserTest {
    @Test fun parsesOnlyValidatedEqCashRowsFromMarketWideUdff() {
        val csv = buildString {
            append("TckrSymb,SctySrs,OpnPric,HghPric,LwPric,ClsPric,TtlTradgVol\n")
            repeat(300) { i ->
                append("STOCK$i,EQ,100,110,90,105,10000\n")
            }
            append("ETFTEST,EQ,100,110,90,105,10000\n")
            append("BEONLY,BE,100,110,90,105,10000\n")
            append("BADPRICE,EQ,100,80,90,105,10000\n")
        }
        val bars = NseBhavcopyParser.parse(StringReader(csv), LocalDate.parse("2026-11-20"))
        assertEquals(301, bars.size)
        assertTrue(bars.all { it.tradeDate == "2026-11-20" && it.low == 90.0 && it.volume == 10000L })
        assertTrue(bars.none { it.symbol == "BEONLY" || it.symbol == "BADPRICE" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPartialOrWrongUdffSchema() {
        NseBhavcopyParser.parse(
            StringReader("Symbol,Series,Close\nABC,EQ,100\n"),
            LocalDate.parse("2026-11-20")
        )
    }
}
