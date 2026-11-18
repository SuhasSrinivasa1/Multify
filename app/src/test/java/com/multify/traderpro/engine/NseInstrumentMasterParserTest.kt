package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

class NseInstrumentMasterParserTest {
    @Test fun parsesNseEquityMasterWithBOMAndQuotedCompanyName() {
        val csv = "\uFEFFSYMBOL,NAME OF COMPANY,SERIES\n" +
            "RELIANCE,\"Reliance Industries, Ltd\",EQ\n" +
            "KDDL,KDDL LTD,EQ\n" +
            "INVALID SYMBOL,Example,EQ\n"
        assertEquals(listOf("KDDL", "RELIANCE"), NseInstrumentMasterParser.parseNse(StringReader(csv)))
    }

    @Test fun parsesOnlyNseCashEquitiesFromGrowwOfficialInstruments() {
        val csv = "exchange,exchange_token,trading_symbol,groww_symbol,name,instrument_type,segment,series,isin,buy_allowed\n" +
            "NSE,1,RELIANCE,NSE-RELIANCE,\"Reliance, Industries\",EQ,CASH,EQ,INE002A01018,1\n" +
            "NSE,2,EXIDEIND,NSE-EXIDEIND,EXIDE,EQ,CASH,EQ,INE302A01020,1\n" +
            "NSE,3,NIFTY,NSE-NIFTY,NIFTY,INDEX,CASH,EQ,,1\n" +
            "NSE,4,NIFTY27MAR20000CE,NSE-NIFTY-C,Option,CE,FNO,, ,1\n" +
            "BSE,5,TESTSTOCK,BSE-TESTSTOCK,Example,EQ,CASH,EQ,INE000A01000,1\n" +
            "NSE,6,OTHER,NSE-OTHER,Example,EQ,CASH,ZZ,INE000A01002,1\n"
        assertEquals(listOf("EXIDEIND", "RELIANCE"),
            NseInstrumentMasterParser.parseGroww(StringReader(csv)))
    }

    @Test fun missingHeadersFailClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            NseInstrumentMasterParser.parseGroww(StringReader("<html>Forbidden</html>\n"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NseInstrumentMasterParser.parseNse(StringReader("status,message\n403,Forbidden\n"))
        }
    }

    @Test fun quotedCsvAndDuplicateSymbols() {
        assertEquals(listOf("A,\"B\"", "C"),
            NseInstrumentMasterParser.csvFields("\"A,\"\"B\"\"\",C"))
        val csv = "exchange,segment,trading_symbol,series,instrument_type\n" +
            "NSE,CASH,RELIANCE,EQ,EQ\nNSE,CASH,RELIANCE,EQ,EQ\n"
        assertEquals(listOf("RELIANCE"),
            NseInstrumentMasterParser.parseGroww(StringReader(csv)))
    }
}
