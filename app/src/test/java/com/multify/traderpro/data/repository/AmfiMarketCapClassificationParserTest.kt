package com.multify.traderpro.data.repository

import org.junit.Assert.*
import org.junit.Test

class AmfiMarketCapClassificationParserTest {
    @Test fun parsesAllThreeOfficialAmfiRankBandsAndSourceVintage() {
        val csv = """
            #AMFI_MARKET_CAP_CLASSIFICATION_V1
            #period=Jan-Jun 2026
            #source=https://www.amfiindia.com/otherdata/categorisation-of-stocks
            symbol,category,rank
            RELIANCE,LARGE_CAP,1
            GAIL,LARGE_CAP,100
            BOSCHLTD,MID_CAP,101
            LUPIN,MID_CAP,250
            SMALLCO,SMALL_CAP,251
        """.trimIndent()

        val parsed = AmfiMarketCapClassificationParser.parse(csv)

        assertEquals("Jan-Jun 2026", parsed.period)
        assertTrue(parsed.source.contains("amfiindia.com"))
        assertEquals("LARGE_CAP", parsed.labels["RELIANCE"]?.category)
        assertEquals(100, parsed.labels["GAIL"]?.rank)
        assertEquals("MID_CAP", parsed.labels["BOSCHLTD"]?.category)
        assertEquals(250, parsed.labels["LUPIN"]?.rank)
        assertEquals("SMALL_CAP", parsed.labels["SMALLCO"]?.category)
        assertNull(parsed.labels["NOT_IN_AMFI_LIST"])
    }

    @Test fun rejectsCategoryThatDoesNotMatchItsOfficialRankBand() {
        val csv = """
            #period=Jan-Jun 2026
            symbol,category,rank
            WRONG,LARGE_CAP,101
            RIGHT,MID_CAP,101
        """.trimIndent()

        val parsed = AmfiMarketCapClassificationParser.parse(csv)

        assertNull(parsed.labels["WRONG"])
        assertEquals("MID_CAP", parsed.labels["RIGHT"]?.category)
    }

    @Test fun malformedOrMissingAssetNeverInventsACategory() {
        assertTrue(AmfiMarketCapClassificationParser.parse("not,a,valid,header").labels.isEmpty())
        assertTrue(AmfiMarketCapClassificationParser.parse("").labels.isEmpty())
    }
}
