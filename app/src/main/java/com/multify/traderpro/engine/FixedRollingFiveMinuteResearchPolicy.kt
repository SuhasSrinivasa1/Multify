package com.multify.traderpro.engine

import kotlin.math.abs

/**
 * Read-only rolling momentum from existing complete approximately-90s market sweeps.
 * This MUST NEVER affect Fixed production ranking, publication, buys, sells or GTTs.
 */
internal data class FixedRollingMarketSample(
    val atMs: Long,
    val prices: Map<String, Double>
)

internal data class FixedRollingFiveMinuteMover(
    val symbol: String,
    val lastPrice: Double,
    val referencePrice: Double,
    val changePct: Double
)

internal data class FixedRollingFiveMinuteStudy(
    val available: Boolean,
    val referenceAtMs: Long?,
    val lookbackMs: Long?,
    val measuredCount: Int,
    val topMovers: List<FixedRollingFiveMinuteMover>,
    val selectedCandidates: List<FixedRollingFiveMinuteMover>
)

internal object FixedRollingFiveMinuteResearchPolicy {
    const val DESIRED_LOOKBACK_MS = 5L * 60_000L
    const val MIN_LOOKBACK_MS = 4L * 60_000L
    const val MAX_LOOKBACK_MS = 7L * 60_000L

    fun study(
        currentAtMs: Long,
        current: Map<String, Double>,
        history: List<FixedRollingMarketSample>,
        selectedSymbols: Collection<String>,
        maxMovers: Int = 30
    ): FixedRollingFiveMinuteStudy {
        val reference = history.asSequence()
            .filter { currentAtMs - it.atMs in MIN_LOOKBACK_MS..MAX_LOOKBACK_MS }
            .minByOrNull { abs(currentAtMs - it.atMs - DESIRED_LOOKBACK_MS) }
            ?: return FixedRollingFiveMinuteStudy(false, null, null, 0, emptyList(), emptyList())
        val movers = current.mapNotNull { (symbol, price) ->
            val old = reference.prices[symbol]
            if (old == null || !old.isFinite() || old <= 0.0 || !price.isFinite() || price <= 0.0) null
            else FixedRollingFiveMinuteMover(symbol, price, old, (price / old - 1.0) * 100.0)
        }
        val indexed = movers.associateBy { it.symbol }
        return FixedRollingFiveMinuteStudy(
            available = movers.isNotEmpty(),
            referenceAtMs = reference.atMs,
            lookbackMs = currentAtMs - reference.atMs,
            measuredCount = movers.size,
            topMovers = movers.sortedWith(
                compareByDescending<FixedRollingFiveMinuteMover> { it.changePct }.thenBy { it.symbol }
            ).take(maxMovers.coerceIn(0, 50)),
            selectedCandidates = selectedSymbols.distinct().mapNotNull { indexed[it] }
        )
    }
}
