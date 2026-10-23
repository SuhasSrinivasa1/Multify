package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastUniversalPositiveEvidencePolicyTest {
    @Test
    fun trailArmMakesTargetMissPositiveEvidence() {
        assertTrue(
            ForecastUniversalPositiveEvidencePolicy.qualifies(
                realizedPct = null,
                closePct = -0.20,
                maxFavourablePct = 0.56,
                targetHit = false
            )
        )
        assertEquals(
            "TRAIL_PROTECTED_POSITIVE",
            ForecastUniversalPositiveEvidencePolicy.positiveReason(null, -0.20, 0.56, false)
        )
    }

    @Test
    fun subArmNegativeCloseStaysDiagnosticOnly() {
        assertFalse(
            ForecastUniversalPositiveEvidencePolicy.qualifies(
                realizedPct = null,
                closePct = -0.10,
                maxFavourablePct = 0.54,
                targetHit = false
            )
        )
    }

    @Test
    fun actualPositiveCloseAlwaysQualifies() {
        assertTrue(
            ForecastUniversalPositiveEvidencePolicy.qualifies(
                realizedPct = 0.18,
                closePct = 0.18,
                maxFavourablePct = 0.30,
                targetHit = false
            )
        )
    }

    @Test
    fun contextBonusRequiresMatureMultiDayMultiSymbolEvidence() {
        val immature = listOf(
            ForecastPositiveEvidenceCase(
                source = "FIXED", evidenceDate = "2026-10-20", symbol = "AAA",
                strategy = "TREND", regime = "TREND", marketRegime = "BREADTH_BULL",
                sectorRegime = "UNAVAILABLE", capBucket = "LARGE_PROXY", timeBucket = "MORNING",
                dominantFamily = "MOMENTUM_TREND", realizedPct = null, closePct = 0.6,
                maxFavourablePct = 0.8, protectedTrailPct = 0.7
            )
        )
        assertEquals(
            0.0,
            ForecastUniversalPositiveEvidencePolicy.rankingBonus(
                "AAA", "TREND", "TREND", "BREADTH_BULL", "UNAVAILABLE",
                "LARGE_PROXY", "MORNING", "MOMENTUM_TREND", immature
            ),
            0.0
        )

        val mature = listOf(
            "2026-10-20" to "AAA",
            "2026-10-20" to "BBB",
            "2026-10-21" to "CCC",
            "2026-10-22" to "DDD",
            "2026-10-22" to "EEE"
        ).map { (date, symbol) ->
            ForecastPositiveEvidenceCase(
                source = "FIXED", evidenceDate = date, symbol = symbol,
                strategy = "TREND", regime = "TREND", marketRegime = "BREADTH_BULL",
                sectorRegime = "UNAVAILABLE", capBucket = "LARGE_PROXY", timeBucket = "MORNING",
                dominantFamily = "MOMENTUM_TREND", realizedPct = null, closePct = 0.7,
                maxFavourablePct = 0.9, protectedTrailPct = 0.8
            )
        }
        assertTrue(
            ForecastUniversalPositiveEvidencePolicy.rankingBonus(
                "AAA", "TREND", "TREND", "BREADTH_BULL", "UNAVAILABLE",
                "LARGE_PROXY", "MORNING", "MOMENTUM_TREND", mature
            ) > 0.0
        )
    }
}
