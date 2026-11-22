package com.multify.traderpro.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDate

class EquityEodResearchRepositoryTest {
    private val ist=ZoneId.of("Asia/Kolkata")
    private fun at(hour:Int, minute:Int): ZonedDateTime =
        ZonedDateTime.of(2026,11,23,hour,minute,0,0,ist)

    @Test fun noNetworkDuringMarketOrPremarketHours() {
        assertFalse(EquityEodResearchRepository.afterHours(at(9,15)))
        assertFalse(EquityEodResearchRepository.afterHours(at(15,30)))
        assertFalse(EquityEodResearchRepository.afterHours(at(18,29)))
        assertFalse(EquityEodResearchRepository.afterHours(at(8,0)))
        assertTrue(EquityEodResearchRepository.afterHours(at(18,30)))
        assertTrue(EquityEodResearchRepository.afterHours(at(23,55)))
        assertTrue(EquityEodResearchRepository.afterHours(at(7,59)))
    }

    @Test fun latestEodDateIsNotInventedBeforeEveningAvailability() {
        assertEquals("2026-11-22",
            EquityEodResearchRepository.latestEligibleDate(at(18,29)).toString())
        assertEquals("2026-11-23",
            EquityEodResearchRepository.latestEligibleDate(at(18,30)).toString())
    }

    @Test fun csvCellsHandleQuotedCompaniesCommasAndEscapedQuotes() {
        val line="INFY,EQ,100.05,\"A, B Company\",\"She said \"\"yes\"\"\""
        assertEquals(listOf("INFY","EQ","100.05","A, B Company","She said \"yes\""),
            EquityEodResearchRepository.csvCells(line))
    }
    @Test fun threeMonthCoverageUnlocksFromTheExistingValidatedArchive() {
        val asOf = LocalDate.of(2026, 11, 22)
        assertTrue(EquityEodResearchRepository.threeMonthCoverageComplete(
            60, LocalDate.of(2026, 8, 24), asOf
        ))
        assertFalse(EquityEodResearchRepository.threeMonthCoverageComplete(
            54, LocalDate.of(2026, 8, 24), asOf
        ))
        assertFalse(EquityEodResearchRepository.threeMonthCoverageComplete(
            60, LocalDate.of(2026, 9, 6), asOf
        ))
    }

    @Test fun threeMonthSymbolRowsRequireEightyFivePercentCoverageAndFiftySessions() {
        assertEquals(55, EquityEodResearchRepository.minimumThreeMonthSymbolSessions(64))
        assertEquals(50, EquityEodResearchRepository.minimumThreeMonthSymbolSessions(55))
    }

    @Test fun latestCloseDistanceIsSignedAgainstThePreviousReferenceLow() {
        assertEquals(-20.0,
            EquityEodResearchRepository.distanceFromReferenceLowPct(80.0, 100.0), 0.000001)
        assertEquals(0.0,
            EquityEodResearchRepository.distanceFromReferenceLowPct(100.0, 100.0), 0.000001)
        assertEquals(5.0,
            EquityEodResearchRepository.distanceFromReferenceLowPct(105.0, 100.0), 0.000001)
    }

    @Test fun priorLowScreenerIncludesEveryBreakdownAndCapsOnlyAboveTenPercent() {
        assertTrue(EquityEodResearchRepository.isWithinTenPctAboveOrBelow(-75.0))
        assertTrue(EquityEodResearchRepository.isWithinTenPctAboveOrBelow(-0.01))
        assertTrue(EquityEodResearchRepository.isWithinTenPctAboveOrBelow(0.0))
        assertTrue(EquityEodResearchRepository.isWithinTenPctAboveOrBelow(10.0))
        assertFalse(EquityEodResearchRepository.isWithinTenPctAboveOrBelow(10.01))
        assertFalse(EquityEodResearchRepository.isWithinTenPctAboveOrBelow(Double.NaN))
    }

}
