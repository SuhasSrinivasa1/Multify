package com.multify.traderpro.engine

import com.multify.traderpro.data.network.FixedEarlyWatchDto
import com.multify.traderpro.data.network.FixedFastCandidateDto
import com.multify.traderpro.data.network.FixedPulseResearchDto
import org.junit.Assert.*
import org.junit.Test

class EarlyConvictionPolicyTest {
    private val now = 1_798_560_000_000L
    private fun primary(symbol: String = "BSOFT", spread: Double? = 8.0,
        shares: Long? = 200_000, primaryCount: Int = 1,
        lagMs: Long = 35_000L, warnings: List<String> = emptyList()
    ) = FixedEarlyWatchDto(
        symbol = symbol, firstPrimaryScanAtMs = now - 120_000L,
        firstEvaluatedAtMs = now - 100_000L,
        firstPrimaryPrice = 330.0, latestEvaluatedAtMs = now - lagMs,
        latestPrice = 330.3, confirmationObservations = primaryCount,
        quoteBookStatus = "VALID_BOOK", observedSpreadBps = spread,
        dayTradedShares = shares, dayChangePct = 3.2, warnings = warnings
    )
    private fun pulse(symbol: String = "BSOFT") = FixedPulseResearchDto(
        symbol = symbol, firstPulseObservedAtMs = now - 200_000L,
        firstPulsePrice = 329.5, pulsePct = 0.75, latestPrice = 330.3,
        latestObservedAtMs = now - 80_000L
    )
    private fun fast(symbol: String = "BSOFT") = FixedFastCandidateDto(
        symbol = symbol, origin = "MARKET_PULSE_RESEARCH",
        firstObservedAtMs = now - 200_000L, baselinePrice = 329.5,
        latestObservedAtMs = now - 15_000L, latestPrice = 330.4,
        observationCount = 2, moveSinceFirstPct = 0.27
    )

    @Test fun corroboratedPrimaryBecomesEarlyBuyIdeaButNeverExecutable() {
        val result = EarlyConvictionPolicy.rank(
            listOf(primary()), listOf(pulse()), listOf(fast()), emptySet(), now)
        assertEquals(1, result.size)
        assertEquals("EARLY_BUY_IDEA", result.single().status)
        assertFalse(result.single().tradeEligible)
        assertTrue(result.single().qualityScore >= EarlyConvictionPolicy.BUY_IDEA_MIN_QUALITY)
    }

    @Test fun oneUncorroboratedPrimaryRemainsWatch() {
        val result = EarlyConvictionPolicy.rank(
            listOf(primary()), emptyList(), emptyList(), emptySet(), now)
        assertEquals("WATCH", result.single().status)
    }

    @Test fun thinVolumeAndBadSpreadNeverGetBuyStatus() {
        val illiquid = EarlyConvictionPolicy.rank(
            listOf(primary(spread = 190.0,
                warnings = listOf("WIDE_SPREAD_SHADOW"))),
            listOf(pulse()), listOf(fast()), emptySet(), now)
        assertTrue(illiquid.isEmpty() || illiquid.all { it.status == "WATCH" })

        val missingVolume = EarlyConvictionPolicy.rank(
            listOf(primary(shares = null)), listOf(pulse()), listOf(fast()), emptySet(), now)
        assertTrue(missingVolume.isEmpty() || missingVolume.all { it.status == "WATCH" })
    }

    @Test fun publishedOrStaleSignalsAreExcluded() {
        assertTrue(EarlyConvictionPolicy.rank(
            listOf(primary()), listOf(pulse()), listOf(fast()), listOf("BSOFT"), now).isEmpty())
        assertTrue(EarlyConvictionPolicy.rank(
            listOf(primary(lagMs = 9L * 60_000L)), emptyList(), emptyList(), emptySet(), now).isEmpty())
    }

    @Test fun independentPrimaryCanCorroborateButListIsAlwaysAtMostThree() {
        val symbols = listOf("BSOFT", "PDSL", "KALYANIFRG", "FDC", "NILKAMAL")
        val result = EarlyConvictionPolicy.rank(
            symbols.map { primary(symbol = it, primaryCount = 2) },
            emptyList(), emptyList(), emptySet(), now, limit = 100
        )
        assertEquals(3, result.size)
        assertTrue(result.all { it.status == "EARLY_BUY_IDEA" && !it.tradeEligible })
    }

    @Test fun sourceEvidenceCannotBeMisusedAsExchangeTimestamp() {
        val result = EarlyConvictionPolicy.rank(
            listOf(primary(primaryCount = 2)), emptyList(), emptyList(), emptySet(), now)
        assertEquals("ANDROID_RECEIPT_NOT_EXCHANGE_TICK", result.single().evidenceSource)
    }
}
