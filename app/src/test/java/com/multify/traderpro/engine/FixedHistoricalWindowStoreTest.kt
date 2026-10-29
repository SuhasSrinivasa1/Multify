package com.multify.traderpro.engine

import com.google.gson.JsonParser
import com.multify.traderpro.data.network.HistoricalPayload
import org.junit.Assert.*
import org.junit.Test

class FixedHistoricalWindowStoreTest {
    private fun rows(from: Long, size: Int): HistoricalPayload = HistoricalPayload(
        candles = (0 until size).map { index ->
            JsonParser.parseString("[${from + index * 300L},101,102,100,101,250]").asJsonArray.toList()
        }, intervalInMinutes = 5
    )
    @Test fun `requires initial broker data and never uses only cached current candles`() {
        val store = FixedHistoricalWindowStore()
        assertNull(store.request("RELIANCE", "2026-10-23 09:15:00", 10_000L))
        store.remember("RELIANCE", "2026-10-23 09:15:00", rows(1_790_000_000L, 60), 10_000L)
        assertNotNull(store.request("RELIANCE", "2026-10-23 09:15:00", 11_000L))
        assertNull(store.request("RELIANCE", "2026-10-23 09:15:00", 500_000L))
    }
    @Test fun `overlapping broker bars replace old last bars but preserve long lookback`() {
        val store = FixedHistoricalWindowStore()
        val start = "2026-10-23 09:15:00"
        val original = rows(1_790_000_000L, 60)
        store.remember("RELIANCE", start, original, 10_000L)
        val delta = rows(1_790_000_000L + 57 * 300L, 5)
        val combined = store.stitch("RELIANCE", start, delta, 11_000L)
        assertNotNull(combined)
        assertEquals(62, combined!!.candles.size)
        assertEquals(delta.candles.last(), combined.candles.last())
        assertEquals(original.candles.first(), combined.candles.first())
        store.remember("RELIANCE", start, combined, 11_000L)
        assertNotNull(store.request("RELIANCE", start, 12_000L))
    }
    @Test fun `missing overlapping bars forces full broker request`() {
        val store = FixedHistoricalWindowStore()
        val start = "2026-10-23 09:15:00"
        store.remember("RELIANCE", start, rows(1_790_000_000L, 60), 10_000L)
        assertNull(store.stitch("RELIANCE", start, rows(1_790_100_000L, 5), 11_000L))
    }
    @Test fun `supports Groww local ISO timestamps and still requires overlapping fresh bars`() {
        val store = FixedHistoricalWindowStore()
        val start = "2026-10-25 09:15:00"
        fun iso(startMinute: Int, count: Int) = HistoricalPayload(candles = (0 until count).map { i ->
            val stamp = java.time.LocalDateTime.of(2026, 10, 29, 9, 15).plusMinutes((startMinute + i) * 5L)
            JsonParser.parseString("[\"$stamp\",101,102,100,101,250]").asJsonArray.toList()
        })
        store.remember("RELIANCE", start, iso(0, 60), 1_000L)
        assertEquals("2026-10-29 14:00:00", store.request("RELIANCE", start, 2_000L)?.startTime)
        val stitched = store.stitch("RELIANCE", start, iso(57, 5), 2_000L)
        assertNotNull(stitched)
        assertEquals(62, stitched!!.candles.size)
    }

    @Test fun `rejects malformed rows and explains why without unsafe incremental requests`() {
        val store = FixedHistoricalWindowStore()
        val start = "2026-10-25 09:15:00"
        store.remember("RELIANCE", start, HistoricalPayload(candles = listOf(
            JsonParser.parseString("[\"bad timestamp\",100,101,99,100,250]").asJsonArray.toList()
        )), 1_000L)
        assertNull(store.request("RELIANCE", start, 2_000L))
        assertEquals("REJECTED_TOO_FEW_ROWS_1", store.ineligibilityReason("RELIANCE", start, 2_000L))
        assertEquals("SNAPSHOT_NOT_SEEN", store.ineligibilityReason("MISSING", start, 2_000L))
    }
}
