package com.multify.traderpro.data.repository

import java.io.BufferedReader
import java.io.StringReader
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NseBhavcopyFileImporterTest {
    private fun udiRows(count: Int, date: String = "2026-11-24"): String {
        val header = "TckrSymb,SctySrs,TradDt,OpnPric,HghPric,LwPric,ClsPric,TtlTradgVol"
        return header + "\n" + (1..count).joinToString("\n") {
            "STK" + it + ",EQ," + date + ",10,11,9,10,1000"
        }
    }

    @Test fun validUdiFFFileProducesOneDateAndAllEqRows() {
        val parsed = NseBhavcopyFileImporter.parseUdiFF(
            BufferedReader(StringReader(udiRows(1500))),
            "BhavCopy_NSE_CM_20261124_F.csv"
        )
        assertEquals(LocalDate.parse("2026-11-24"), parsed.first)
        assertEquals(1500, parsed.second.size)
    }

    @Test fun udiFFFilenameDateMustMatchTradeDate() {
        var rejected = false
        try {
            NseBhavcopyFileImporter.parseUdiFF(
                BufferedReader(StringReader(udiRows(1500))),
                "BhavCopy_NSE_CM_20261125_F.csv"
            )
        } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun duplicateUdiFFSymbolsAreRejected() {
        val csv = "TckrSymb,SctySrs,TradDt,OpnPric,HghPric,LwPric,ClsPric,TtlTradgVol\n" +
            "DUP,EQ,2026-11-24,10,11,9,10,1000\n" +
            "DUP,EQ,2026-11-24,10,11,9,10,1000\n"
        var rejected = false
        try {
            NseBhavcopyFileImporter.parseUdiFF(
                BufferedReader(StringReader(csv)),
                "BhavCopy_NSE_CM_20261124_F.csv"
            )
        } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun validFullBhavcopyCsvParsesDateAndEqRows() {
        val header = "SYMBOL,SERIES,DATE1,OPEN_PRICE,HIGH_PRICE,LOW_PRICE,CLOSE_PRICE,TTL_TRD_QNTY"
        val csv = header + "\n" + (1..1500).joinToString("\n") {
            "STK" + it + ",EQ,24-Nov-2026,10,11,9,10,1000"
        }
        val parsed = NseBhavcopyFileImporter.parseFullBhavcopy(BufferedReader(StringReader(csv)))
        assertEquals(LocalDate.parse("2026-11-24"), parsed.first)
        assertEquals(1500, parsed.second.size)
    }

    @Test fun invalidOhlcRowsCannotSatisfyMinimumCoverage() {
        val header = "SYMBOL,SERIES,DATE1,OPEN_PRICE,HIGH_PRICE,LOW_PRICE,CLOSE_PRICE,TTL_TRD_QNTY"
        val csv = header + "\n" + (1..1500).joinToString("\n") {
            "STK" + it + ",EQ,24-Nov-2026,10,8,9,10,1000"
        }
        var rejected = false
        try {
            NseBhavcopyFileImporter.parseFullBhavcopy(BufferedReader(StringReader(csv)))
        } catch (_: IllegalStateException) { rejected = true }
        assertTrue(rejected)
    }
}
