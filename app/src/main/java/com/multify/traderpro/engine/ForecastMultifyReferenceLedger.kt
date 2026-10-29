package com.multify.traderpro.engine

import com.multify.traderpro.data.local.LearningCallEntity
import java.util.Locale

/**
 * Forecast-only, append-only *logical* view of Multify BUY events.
 *
 * The 107 canonical cases are supplied by the checksum-verified, immutable Fixed snapshot,
 * but this ledger never writes back to Fixed or changes its model/target. Subsequent genuine
 * Multify LIVE BUY events are added as distinct events. A SELL/MFE enrichment updates the
 * existing event instead of incrementing the case count. Old seed re-imports are not new calls.
 *
 * All live calls are retained. Rolling training windows are derived views, not deletions.
 */
object ForecastMultifyReferenceLedger {
    const val BASELINE_CALLS = FixedFinalContract.CANONICAL_LEARNING_CALLS
    const val CUTOFF_DATE = FixedFinalContract.REFERENCE_DATE

    data class Summary(
        val frozenCases: Int,
        val appendedCalls: Int,
        val totalCalls: Int,
        val distinctSymbols: Int,
        val liveCompleted: Int,
        val liveMfeMeasured: Int,
        val livePendingOutcome: Int,
        val latestLiveCallDate: String?
    )

    fun build(canonical: List<LearningCallEntity>, allCalls: List<LearningCallEntity>): List<LearningCallEntity> {
        require(canonical.size == BASELINE_CALLS) { "Forecast canonical reference count changed" }
        val frozen = canonical.filter { it.action.equals("BUY", true) }
        require(frozen.size == BASELINE_CALLS) { "Forecast canonical BUY reference count changed" }
        val live = allCalls.asSequence()
            .filter { it.action.equals("BUY", true) && it.source.equals("LIVE", true) }
            // Only newer live notifications extend the baseline. The earlier 107 stay immutable.
            .filter { it.callDate > CUTOFF_DATE && it.buyAtMs > 0L && it.entryPrice.isFinite() && it.entryPrice > 0.0 }
            .groupBy { it.buyEventId?.let { id -> "EVENT|$id" } ?: "${it.callDate}|${it.symbol.uppercase(Locale.US)}|${it.buyAtMs}" }
            .values.mapNotNull { records ->
                // Prefer the completed/enriched event if the imported ledger has duplicates.
                records.maxWithOrNull(compareBy<LearningCallEntity> {
                    (if (it.sellAtMs != null || it.multifyExitPrice != null) 2 else 0) +
                    (if (it.multifyMfePct != null) 1 else 0)
                }.thenBy { it.updatedAtMs })
            }
            .sortedWith(compareBy<LearningCallEntity> { it.callDate }.thenBy { it.buyAtMs })
            .toList()
        return frozen + live
    }

    fun summary(canonical: List<LearningCallEntity>, allCalls: List<LearningCallEntity>): Summary {
        val complete = build(canonical, allCalls)
        val appended = complete.drop(BASELINE_CALLS)
        return Summary(
            frozenCases = BASELINE_CALLS,
            appendedCalls = appended.size,
            totalCalls = complete.size,
            distinctSymbols = complete.map { it.symbol.uppercase(Locale.US) }.distinct().size,
            liveCompleted = appended.count { it.sellAtMs != null || it.multifyExitPrice != null },
            liveMfeMeasured = appended.count { it.multifyMfePct?.let { x -> x.isFinite() && x >= 0.0 && x < 25.0 } == true },
            livePendingOutcome = appended.count { it.sellAtMs == null && it.multifyExitPrice == null },
            latestLiveCallDate = appended.maxOfOrNull { it.callDate }
        )
    }

    /**
     * Tiny reference prior for established Multify patterns, never an EARLY ENTRY trigger.
     * Today/current-session outcomes cannot enter this historical prior, and no stock is
     * included in Forecast just because it appeared in the historic Multify reference.
     */
    fun historicalContextBonus(symbol: String, beforeDate: String, allReferences: List<LearningCallEntity>): Double {
        val positive = allReferences.filter {
            it.callDate < beforeDate && it.symbol.equals(symbol, true) &&
                (it.longRealizedPct?.let { pnl -> pnl.isFinite() && pnl > 0.0 } == true ||
                 it.multifyMfePct?.let { mfe -> mfe.isFinite() && mfe >= 0.55 } == true)
        }
        val days = positive.map { it.callDate }.distinct().size
        return when {
            positive.size >= 3 && days >= 3 -> 0.010
            positive.size >= 2 && days >= 2 -> 0.006
            else -> 0.0
        }
    }
}
