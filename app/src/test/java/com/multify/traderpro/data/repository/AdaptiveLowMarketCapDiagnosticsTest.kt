package com.multify.traderpro.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLowMarketCapDiagnosticsTest {
    @Test fun distinguishesMappingGapFromRealNoPatternResult() {
        val missing=AdaptiveLowMarketCapDiagnostics(assetLarge=100)
        assertEquals("NO_AMFI_LARGE_SYMBOL_IN_LATEST_NSE_EOD",missing.largeCapCoverageStatus)
        val noHistory=AdaptiveLowMarketCapDiagnostics(assetLarge=100,
            largeCapUniverseSymbols=listOf("TCS"))
        assertEquals("LARGE_SYMBOLS_LACK_COMPLETE_HISTORY",noHistory.largeCapCoverageStatus)
        val noPattern=AdaptiveLowMarketCapDiagnostics(assetLarge=100,
            largeCapUniverseSymbols=listOf("TCS","INFY"),
            largeCapEligibleSymbols=listOf("TCS","INFY"))
        assertEquals("LARGE_SYMBOLS_EVALUATED_NO_PATTERN_MATCH",noPattern.largeCapCoverageStatus)
        val found=noPattern.copy(largeCapQualifiedSymbols=listOf("TCS"))
        assertEquals("LARGE_CAP_PATTERN_MATCHES_FOUND",found.largeCapCoverageStatus)
    }
}
