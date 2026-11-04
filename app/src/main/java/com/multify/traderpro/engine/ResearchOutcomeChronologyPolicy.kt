package com.multify.traderpro.engine

/** Chronology of sampled quotes, NOT an executed trade, candle extreme or guaranteed fill. */
data class ResearchOutcomeChronology(
    val key: String, val source: String, val symbol: String, val entryAtMs: Long,
    val entryPrice: Double, val targetPrice: Double, val simulatedStopPrice: Double,
    val firstObservedAtMs: Long = 0L, val lastObservedAtMs: Long = 0L,
    val lastObservedPrice: Double? = null, val sampleCount: Int = 0,
    val firstTargetObservedAtMs: Long? = null, val firstStopObservedAtMs: Long? = null,
    val trailingArmSeenAtMs: Long? = null,
    val firstTouch: String = "UNRESOLVED", val priceSource: String = "UNAVAILABLE",
    val evidence: String = "SPARSE_SAMPLED_PRICES_ONLY_NOT_REALIZED_PNL"
)

object ResearchOutcomeChronologyPolicy {
    fun observe(old: ResearchOutcomeChronology, price: Double, atMs: Long, source: String): ResearchOutcomeChronology {
        if (!price.isFinite() || price <= 0.0 || atMs <= old.entryAtMs || atMs <= old.lastObservedAtMs ||
            old.entryPrice <= 0.0 || old.simulatedStopPrice >= old.targetPrice) return old
        val hitTarget = price >= old.targetPrice
        val hitStop = price <= old.simulatedStopPrice
        val targetAt = old.firstTargetObservedAtMs ?: if (hitTarget) atMs else null
        val stopAt = old.firstStopObservedAtMs ?: if (hitStop) atMs else null
        val first = when {
            old.firstTouch != "UNRESOLVED" -> old.firstTouch
            hitTarget && hitStop -> "AMBIGUOUS"
            hitTarget -> "TARGET_OBSERVED_FIRST"
            hitStop -> "STOP_OBSERVED_FIRST"
            else -> "UNRESOLVED"
        }
        return old.copy(
            firstObservedAtMs = if (old.firstObservedAtMs == 0L) atMs else old.firstObservedAtMs,
            lastObservedAtMs = atMs, lastObservedPrice = price,
            sampleCount = old.sampleCount + 1,
            firstTargetObservedAtMs = targetAt, firstStopObservedAtMs = stopAt,
            trailingArmSeenAtMs = old.trailingArmSeenAtMs ?: if (price >= old.entryPrice * 1.0055) atMs else null,
            firstTouch = first, priceSource = source
        )
    }
}
