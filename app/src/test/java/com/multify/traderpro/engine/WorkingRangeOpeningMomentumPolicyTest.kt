package com.multify.traderpro.engine

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class WorkingRangeOpeningMomentumPolicyTest {
    private val india = ZoneId.of("Asia/Kolkata")

    private fun at(date: LocalDate, time: LocalTime): Long =
        ZonedDateTime.of(date, time, india).toInstant().toEpochMilli()

    @Test fun openingSignalCanFormBeforeTheFirstSameDayFifteenMinuteCandle() {
        val today = LocalDate.of(2026, 11, 20)
        val samples = listOf(
            WorkingRangeOpeningMomentumPolicy.QuoteSample(at(today, LocalTime.of(9, 15)), 100.00, 1_000L),
            WorkingRangeOpeningMomentumPolicy.QuoteSample(at(today, LocalTime.of(9, 16, 30)), 100.25, 3_000L),
            WorkingRangeOpeningMomentumPolicy.QuoteSample(at(today, LocalTime.of(9, 18)), 100.50, 7_000L)
        )
        val history = listOf(
            WorkingRangeCandle(at(today.minusDays(1), LocalTime.of(9, 15)),
                99.0, 101.0, 98.0, 100.0, 10_000.0),
            WorkingRangeCandle(at(today.minusDays(2), LocalTime.of(9, 15)),
                98.0, 100.0, 97.0, 99.0, 10_000.0)
        )
        val metrics = WorkingRangeOpeningMomentumPolicy.assess(
            samples, 7_000L, history, at(today, LocalTime.of(9, 18))
        )
        assertTrue(metrics.openingMode)
        assertEquals(3, metrics.sampleCount)
        assertEquals(0.50, metrics.momentumPct!!, 0.0001)
        assertEquals(3.5, metrics.volumePaceRvol!!, 0.0001)
        assertEquals(1.0, metrics.volumeAcceleration!!, 0.0001)
        assertEquals(1.0, metrics.greenVolumeShare!!, 0.0001)
        assertTrue(metrics.signalBasis.contains("LIVE_QUOTE"))
    }

    @Test fun openingWindowExtendsUntilTwoFifteenMinuteBarsCanBeCompleted() {
        val today = LocalDate.of(2026, 11, 20)
        assertTrue(WorkingRangeOpeningMomentumPolicy.assess(
            emptyList(), null, emptyList(), at(today, LocalTime.of(9, 29))
        ).openingMode)
        assertTrue(WorkingRangeOpeningMomentumPolicy.assess(
            emptyList(), null, emptyList(), at(today, LocalTime.of(9, 44))
        ).openingMode)
        assertFalse(WorkingRangeOpeningMomentumPolicy.assess(
            emptyList(), null, emptyList(), at(today, LocalTime.of(9, 45))
        ).openingMode)
    }
}
