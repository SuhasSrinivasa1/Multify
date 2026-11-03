package com.multify.traderpro.data.repository
import org.junit.Assert.*
import org.junit.Test
class ForecastNewsSymbolResolutionPolicyTest {
    private val cat=mapOf("MAN INFRACONSTRUCTION LIMITED" to "MANINFRA",
        "WOCKHARDT LIMITED" to "WOCKPHARMA",
        "MAN INFRA PRIVATE LTD" to "DIFFERENT",
        "ACME VENTURES LIMITED" to "ACME")
    @Test fun handlesExchangeHeadlineAndCorporateSuffix() {
        assertEquals("WOCKPHARMA",ForecastNewsSymbolResolutionPolicy.resolve(
            "Wockhardt Ltd has informed the Exchange about Financial Results",cat))
        assertEquals("ACME",ForecastNewsSymbolResolutionPolicy.resolve(
            "Acme Ventures Limited - Orders worth 500 crore secured",cat))
    }
    @Test fun ambiguousOrWeakTitleNeverGuesses() {
        assertEquals("",ForecastNewsSymbolResolutionPolicy.resolve("Man Infra Ltd secured work orders",cat))
        assertEquals("",ForecastNewsSymbolResolutionPolicy.resolve("Board meeting on 3rd November",cat))
        assertEquals("",ForecastNewsSymbolResolutionPolicy.resolve("Other company acquired Acme Ventures",cat))
    }
}
