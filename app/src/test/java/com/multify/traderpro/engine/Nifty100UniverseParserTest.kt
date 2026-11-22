package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class Nifty100UniverseParserTest {
    @Test fun parsesOfficialCompanySectorAndSymbolFields() {
        val csv = buildString {
            append("Company Name,Industry,Symbol,Series,ISIN Code\n")
            repeat(100) { i -> append("Company $i,Information Technology,STOCK$i,EQ,INE00000000\n") }
        }
        val rows = Nifty100UniverseParser.parse(StringReader(csv))
        assertEquals(100, rows.size)
        assertEquals("Information Technology", rows.first().sector)
        assertTrue(rows.any { it.symbol == "STOCK99" })
    }
}
