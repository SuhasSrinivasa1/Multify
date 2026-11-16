package com.multify.traderpro.engine

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.multify.traderpro.data.network.HistoricalPayload
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class Fixed15mHistoryRepairTest {
    private fun bar(ts: String): List<JsonElement> = listOf(
        JsonPrimitive(ts), JsonPrimitive(100.0), JsonPrimitive(101.0),
        JsonPrimitive(99.0), JsonPrimitive(100.5), JsonPrimitive(1000.0)
    )
    private fun series(vararg timestamps: String) =
        HistoricalPayload(candles = timestamps.map(::bar), intervalInMinutes = 15)

    @Test fun recoversShortCurrentSessionOnlyFromCompletedVerifiedPriorBars() {
        val current = series(
            "2026-11-16T09:15:00+05:30",
            "2026-11-16T09:30:00+05:30" // still forming; must be rejected
        )
        val prior = series(
            "2026-11-13T14:15:00+05:30", "2026-11-13T14:30:00+05:30",
            "2026-11-13T14:45:00+05:30", "2026-11-13T15:00:00+05:30",
            "2026-11-13T15:15:00+05:30", "2026-11-16T09:15:00+05:30"
        )
        val at = ZonedDateTime.parse("2026-11-16T09:35:00+05:30").toInstant().toEpochMilli()
        val merged = Fixed15mHistoryRepair.mergeCompleted(current, prior, at)
        assertNotNull(merged)
        assertEquals(6, merged!!.candles.size)
        assertEquals("2026-11-16T09:15:00+05:30", merged.candles.last()[0].asString)
    }

    @Test fun refusesRecoveredHistoryBeforeFirstCurrentCandleIsComplete() {
        val current = series("2026-11-16T09:15:00+05:30")
        val prior = series(
            "2026-11-13T14:15:00+05:30", "2026-11-13T14:30:00+05:30",
            "2026-11-13T14:45:00+05:30", "2026-11-13T15:00:00+05:30",
            "2026-11-13T15:15:00+05:30"
        )
        val at = ZonedDateTime.parse("2026-11-16T09:22:00+05:30").toInstant().toEpochMilli()
        assertNull(Fixed15mHistoryRepair.mergeCompleted(current, prior, at))
    }

    @Test fun refusesFabricatedOrMissingPriorData() {
        val current = series("2026-11-16T09:15:00+05:30")
        val prior = series("INVALID", "2026-11-13T15:15:00+05:30")
        val at = ZonedDateTime.parse("2026-11-16T09:35:00+05:30").toInstant().toEpochMilli()
        assertNull(Fixed15mHistoryRepair.mergeCompleted(current, prior, at))
    }

    @Test fun mondayBackfillEndsOnFridayAndBudgetIsBounded() {
        val now = ZonedDateTime.parse("2026-11-16T09:35:00+05:30")
        val range = Fixed15mHistoryRepair.priorWindow(now)
        assertTrue(range.second.startsWith("2026-11-13"))
        val start = 99_000_000L
        assertTrue((1..6).all { Fixed15mHistoryRepair.allowRecovery(start) })
        assertFalse(Fixed15mHistoryRepair.allowRecovery(start + 50L))
        assertTrue(Fixed15mHistoryRepair.allowRecovery(start + 90_000L))
    }
}
