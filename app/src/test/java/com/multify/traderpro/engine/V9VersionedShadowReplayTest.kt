package com.multify.traderpro.engine

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class V9VersionedShadowReplayTest {
    private fun candle(ts: String): List<JsonElement> = listOf(
        JsonPrimitive(ts), JsonPrimitive(100.0), JsonPrimitive(102.0),
        JsonPrimitive(99.0), JsonPrimitive(101.0), JsonPrimitive(1000.0)
    )
    @Test fun originalV16TimestampFallbackDoesNotAlterCurrentLiveValidator() {
        val invalid = HistoricalPayload(candles = List(12) { candle("INVALID") },
            intervalInMinutes = 15)
        val quote = QuotePayload(lastPrice = 101.0, bidPrice = 100.9, offerPrice = 101.1)
        // Evidence: v16 accepted invalid timestamps as numerical index values.
        val old = V9Fixed8316StrategyShadow.buildFeatures(quote, invalid, 15)
        assertEquals(12, old.candles.size)
        // Current production rejects fabricated/unverified timestamps.
        try {
            FixedLocalStrategyEngine.buildFeatures(quote, invalid, 15)
            fail("Incomplete/unverified historical evidence must not create a live signal")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty().contains("FIXED_NO_VERIFIED_COMPLETED_CANDLES"))
        }
    }
    @Test fun v16OlderOpeningRangeDiffersFromVerified15mProduction() {
        val candles = listOf(
            candle("2026-11-12T09:15:00+05:30"),
            candle("2026-11-12T09:30:00+05:30"),
            candle("2026-11-13T09:15:00+05:30"),
            candle("2026-11-13T09:30:00+05:30"),
            candle("2026-11-13T09:45:00+05:30"),
            candle("2026-11-13T10:00:00+05:30")
        )
        val historical = HistoricalPayload(candles = candles, intervalInMinutes = 15)
        val quote = QuotePayload(lastPrice = 101.0, bidPrice = 100.9, offerPrice = 101.1)
        val a = V9Fixed8316StrategyShadow.buildFeatures(quote, historical, 15)
        assertNotNull(a.orb5High)
        val asOf = ZonedDateTime.parse("2026-11-13T10:20:00+05:30").toInstant().toEpochMilli()
        val b = FixedLocalStrategyEngine.buildFeatures(quote, historical, 15, asOf)
        assertNull(b.orb5High)
        assertEquals(4, b.sessionCandleCount)
    }
    @Test fun frozenTargetsAndQualifiersAreStillAvailable() {
        assertEquals(1.1161043030113955, FixedFinalContract.CANONICAL_MULTIFY_MFE_AVG_PCT, 0.000001)
        assertEquals(1.0, V9Fixed8316QualityShadow.MIN_TARGET_PCT, 0.00001)
        assertEquals(2.0, HardStopLossPolicy.MAX_LOSS_PCT, 0.00001)
    }
}
