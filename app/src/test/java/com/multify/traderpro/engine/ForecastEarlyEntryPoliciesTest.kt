package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ForecastEarlyEntryPoliciesTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ist(date: String): Long = LocalDateTime.parse(date).atZone(zone).toInstant().toEpochMilli()

    private fun evaluation(
        startMs: Long,
        fiveMinuteHighs: List<Double>,
        fiveMinuteLows: List<Double>,
        fiveMinuteCloses: List<Double>,
        ltp: Double
    ): StrategyEvaluation {
        val candles = buildList {
            for (barIndex in fiveMinuteHighs.indices) {
                val open = fiveMinuteCloses[barIndex]
                repeat(5) { minute ->
                    val ts = (startMs / 1000L) + barIndex * 300L + minute * 60L
                    add(Candle(
                        ts = ts, open = open,
                        high = fiveMinuteHighs[barIndex], low = fiveMinuteLows[barIndex],
                        close = open, volume = 1_000.0 + barIndex * 25.0
                    ))
                }
            }
        }
        val features = FeatureSnapshot(
            ltp = ltp, bid = ltp - 0.01, ask = ltp + 0.01, spreadBps = 2.0,
            orderBookImbalance = 0.20, dayChangePct = 1.2, marketCap = null, range52Position = 0.6,
            vwap = 99.5, ema9 = 100.2, ema20 = 100.0, ema50 = 99.5,
            ema9Slope = 0.10, ema20Slope = 0.05, vwapSlope = 0.05, macdHistogramSlope = 0.05,
            rsiSlope = 1.0, volumeAcceleration = 0.25, greenVolumeShare = 0.65,
            structureScore = 0.50, sessionHighDistancePct = 0.2, sessionLowDistancePct = 1.5,
            atr14 = 0.05, rsi14 = 62.0, bollWidth = 0.02, macdHistogram = 0.1,
            trendSlopeAtr = 0.2, rvol = 1.2, donchianHigh = 100.5, donchianLow = 98.0,
            orb5High = 100.5, orb5Low = 99.0, orb15High = 100.5, orb15Low = 98.5,
            lastOpen = fiveMinuteCloses.last(), lastHigh = fiveMinuteHighs.last(),
            lastLow = fiveMinuteLows.last(), lastClose = fiveMinuteCloses.last(),
            prevOpen = fiveMinuteCloses[fiveMinuteCloses.lastIndex - 1],
            prevHigh = fiveMinuteHighs[fiveMinuteHighs.lastIndex - 1],
            prevLow = fiveMinuteLows[fiveMinuteLows.lastIndex - 1],
            prevClose = fiveMinuteCloses[fiveMinuteCloses.lastIndex - 1],
            candles = candles
        )
        return StrategyEvaluation("TREND", 0.65, 0.82, "VWAP trend continuation", "test", emptyList(), features)
    }

    @Test fun forecastBuyClosesAt1530AndAfterTarget() {
        assertTrue(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T15:29:59"), true, "ACTIVE", true))
        assertFalse(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T15:30:00"), true, "ACTIVE", true))
        assertFalse(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T13:00:00"), true, "TARGET_HIT", true))
        assertFalse(ForecastBuyAvailabilityPolicy.canBuy(ist("2026-10-27T13:00:00"), false, "ACTIVE", true))
    }

    @Test fun repeatRequiresCompletedTargetCooldownAndPullback() {
        val now = 1_000_000L
        assertTrue(ForecastRepeatEntryPolicy.mayPublish(null, null, null, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("ACTIVE", 1000L, 105.0, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("MISSED", 1000L, 105.0, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", 900_000L, 105.0, 100.0, now))
        assertFalse(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", 1000L, 105.0, 105.0, now))
        assertTrue(ForecastRepeatEntryPolicy.mayPublish("TARGET_HIT", 1000L, 105.0, 104.0, now))
    }

    @Test fun fullMarketEarlyRankDoesNotFavorCompletedSpikes() {
        val winners = ForecastEarlyDiscoveryPolicy.rank(
            mapOf("EARLY" to 101.0, "SPIKED" to 110.0, "FLAT" to 100.0),
            mapOf("EARLY" to 100.8, "SPIKED" to 101.0, "FLAT" to 100.1),
            mapOf("EARLY" to 100.0, "SPIKED" to 100.0, "FLAT" to 100.0),
            100
        )
        assertEquals("EARLY", winners.first().symbol)
        assertFalse(winners.any { it.symbol == "SPIKED" })
    }

    @Test fun fiveMinuteAtrCapacityUsesFiveMinuteRangeNotOneMinuteAtr() {
        val start = ist("2026-10-30T09:15:00")
        val e = evaluation(start, List(8) { 100.60 }, List(8) { 99.60 }, List(8) { 100.20 }, 100.40)
        val asOf = ist("2026-10-30T09:56:00")
        val assessment = ForecastEarlyEntryPolicy.assess(e, asOf)
        assertEquals(8, assessment.completedFiveMinuteBars)
        assertTrue(assessment.volatilityCapacityPct > 2.0)
        val quality = ForecastEarlyEntryPolicy.quality(e, 1.14, asOf)
        assertTrue(quality.volatilityCapacityPct > 2.0)
        assertEquals("PRIMARY", quality.tier)
    }

    @Test fun multiPeakRetestAllowsControlledSecondBreakoutWithoutChasing() {
        val start = ist("2026-10-30T09:15:00")
        val highs = listOf(100.20, 100.55, 100.35, 100.40, 100.50, 100.45, 100.60, 100.55, 100.50, 100.48)
        val lows = listOf(99.50, 99.60, 99.65, 99.75, 99.85, 99.95, 100.05, 100.15, 100.25, 100.30)
        val closes = listOf(100.00, 100.20, 100.15, 100.25, 100.35, 100.35, 100.45, 100.48, 100.48, 100.48)
        val e = evaluation(start, highs, lows, closes, 100.85)
        val assessment = ForecastEarlyEntryPolicy.assess(e, ist("2026-10-30T10:06:00"))
        assertTrue(assessment.multiPeakRetest)
        assertTrue(assessment.qualifies)
    }
}
