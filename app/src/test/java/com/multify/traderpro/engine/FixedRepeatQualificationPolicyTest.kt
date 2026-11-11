package com.multify.traderpro.engine

import com.multify.traderpro.data.repository.FixedRecommendationRecord
import com.multify.traderpro.data.repository.FixedScanObservation
import com.multify.traderpro.data.repository.FixedRecommendationStore
import org.junit.Assert.*
import org.junit.Test

class FixedRepeatQualificationPolicyTest {
    private val date = "2026-11-04"
    private val t0 = 1_000_000_000L
    private fun record(at: Long, symbol: String = "ABC", status: String = "ACTIVE") = FixedRecommendationRecord(
        id = FixedRecommendationStore.fixedEventId(date, symbol, at),
        recommendationDate = date, rank = 1, symbol = symbol,
        entryPrice = 400.0, targetPct = 1.1161043, targetPrice = 404.4644,
        confidence = 0.93, score = 0.94, strategy = "FIXED",
        regime = "TREND", marketRegime = "BREADTH_BULL",
        championKey = "", reason = "test", generatedAtMs = at, status = status,
        lastPrice = 400.0, lastObservedAtMs = at
    )
    private fun scan(at: Long, qualified: Boolean, symbol: String = "ABC",
                     price: Double = if (qualified) 420.0 else 390.0) =
        FixedScanObservation(date, at, symbol,
            if (qualified) "PRIMARY" else "SECONDARY",
            qualified, qualified, observedPrice = price
        )

    @Test fun firstEverQualifiedSetupIsNotSuppressed() {
        assertTrue(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC", t0, emptyList(), emptyList()))
    }

    @Test fun continuingPrimaryCannotGenerateDuplicateRepeatedAlerts() {
        val data = listOf(scan(t0+10_000, true), scan(t0+100_000, true),
            scan(t0+2_200_000, true))
        assertFalse(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC", t0+2_400_000, listOf(record(t0)), data))
    }

    @Test fun newSetupAfterExplicitResetAndFreshConfirmationCanRequalify() {
        val observations = listOf(scan(t0+10_000, true),
            scan(t0+1_900_000, false), scan(t0+1_990_000, true))
        assertTrue(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC", t0+2_100_000, listOf(record(t0)), observations))
        assertEquals(1, FixedRepeatQualificationPolicy.primaryHitsSinceReset(
            "ABC", t0, observations))
        assertNotEquals(FixedRecommendationStore.fixedEventId(date, "ABC", t0),
            FixedRecommendationStore.fixedEventId(date, "ABC", t0+2_100_000))
    }

    @Test fun freshQualificationMustBeAfterResetAndAtLeastThirtyMinutesLater() {
        val scans = listOf(scan(t0+2_000, true), scan(t0+100_000, false))
        assertFalse(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC",t0+2_200_000,listOf(record(t0)),scans))
        val newScans = scans + scan(t0+120_000,true)
        assertFalse(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC",t0+1_500_000,listOf(record(t0)),newScans))
        assertTrue(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC",t0+2_200_000,listOf(record(t0)),newScans))
    }

    @Test fun mostRecentEventDeterminesResetNotFirstEventOfDay() {
        val previous = listOf(record(t0),record(t0+2_100_000))
        val observations = listOf(scan(t0+1_900_000,false),
            scan(t0+1_990_000,true),scan(t0+4_200_000,true))
        assertFalse(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC",t0+4_300_000,previous,observations))
    }

    @Test fun importedPriorSignalDoesNotBlockFreshIndependentSetup() {
        val importedLastAtMs = t0
        val resetAndConfirmation = listOf(
            scan(t0+1_900_000L,false),
            scan(t0+1_990_000L,true)
        )
        assertFalse(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC",t0+2_100_000L,emptyList(),emptyList(),
            importedLastEventAtMs=importedLastAtMs))
        assertTrue(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC",t0+2_100_000L,emptyList(),resetAndConfirmation,
            importedLastEventAtMs=importedLastAtMs))
    }


    @Test fun targetHitReentryRequiresObservedHalfPercentPullbackFromTarget() {
        val targetHit = record(t0, status = "TARGET_HIT")
        val noPullback = listOf(
            scan(t0 + 1_900_000, false, price = 403.0),
            scan(t0 + 1_990_000, true)
        )
        assertFalse(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC", t0 + 2_100_000, listOf(targetHit), noPullback))

        val withPullback = listOf(
            scan(t0 + 1_900_000, false, price = 403.0),
            scan(t0 + 1_920_000, false, price = 402.0),
            scan(t0 + 1_990_000, true)
        )
        assertTrue(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC", t0 + 2_100_000, listOf(targetHit), withPullback))
    }

    @Test fun noAssumedResetFromMissingResearch() {
        assertFalse(FixedRepeatQualificationPolicy.qualifiedForNewEvent(
            "ABC",t0+10_000_000,listOf(record(t0)),emptyList()))
    }
}
