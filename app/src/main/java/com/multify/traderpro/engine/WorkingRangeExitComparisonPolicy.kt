package com.multify.traderpro.engine

/** Sampled-LTP-only comparison. These variants are research outputs and never control live orders. */
internal object WorkingRangeExitComparisonPolicy {
    data class Sample(val atMs: Long, val price: Double)
    data class VariantResult(
        val state: String,
        val sampledReturnPct: Double? = null,
        val exitAtMs: Long? = null,
        val note: String = "SPARSE_CLIENT_LTP_SAMPLES_NOT_INTRABAR_PATH"
    )
    data class Comparison(
        val fixedTargetStop: VariantResult,
        val steppedProtection: VariantResult,
        val partialThenTrail: VariantResult
    )

    fun evaluate(
        entryPrice: Double,
        targetPct: Double,
        samples: List<Sample>,
        stopLossPct: Double = WorkingRangeExitPolicy.HARD_STOP_LOSS_PCT
    ): Comparison {
        if (!entryPrice.isFinite() || entryPrice <= 0.0 || !targetPct.isFinite() ||
            targetPct <= 0.0 || !stopLossPct.isFinite() || stopLossPct <= 0.0) {
            val invalid = VariantResult("INVALID_INPUT")
            return Comparison(invalid, invalid, invalid)
        }
        val ordered = samples.filter { it.atMs > 0L && it.price.isFinite() && it.price > 0.0 }
            .sortedBy { it.atMs }
        return Comparison(
            fixedTargetStop = baseline(entryPrice, targetPct, stopLossPct, ordered),
            steppedProtection = stepped(entryPrice, targetPct, stopLossPct, ordered),
            partialThenTrail = partial(entryPrice, targetPct, stopLossPct, ordered)
        )
    }

    private fun baseline(
        entry: Double, targetPct: Double, stopPct: Double, samples: List<Sample>
    ): VariantResult {
        val stop = entry * (1.0 - stopPct / 100.0)
        val target = entry * (1.0 + targetPct / 100.0)
        samples.forEach { sample ->
            when {
                sample.price <= stop -> return VariantResult("STOP_FIRST_SAMPLED", -stopPct, sample.atMs)
                sample.price >= target -> return VariantResult("TARGET_FIRST_SAMPLED", targetPct, sample.atMs)
            }
        }
        return VariantResult("UNRESOLVED_CENSORED")
    }

    private fun stepped(
        entry: Double, targetPct: Double, stopPct: Double, samples: List<Sample>
    ): VariantResult {
        var highWater = entry
        var stop = entry * (1.0 - stopPct / 100.0)
        val target = entry * (1.0 + targetPct / 100.0)
        samples.forEach { sample ->
            highWater = maxOf(highWater, sample.price)
            val highPct = (highWater / entry - 1.0) * 100.0
            if (highPct >= 0.55) {
                val ratchets = kotlin.math.floor((highPct - 0.55) / 0.10).coerceAtLeast(0.0)
                val protectedPct = 0.50 + ratchets * 0.10
                stop = maxOf(stop, entry * (1.0 + protectedPct / 100.0))
            }
            when {
                sample.price <= stop -> return VariantResult(
                    if (stop > entry) "TRAIL_EXIT_SAMPLED" else "STOP_FIRST_SAMPLED",
                    (stop / entry - 1.0) * 100.0, sample.atMs)
                sample.price >= target -> return VariantResult("TARGET_FIRST_SAMPLED", targetPct, sample.atMs)
            }
        }
        return VariantResult("UNRESOLVED_CENSORED")
    }

    private fun partial(
        entry: Double, targetPct: Double, stopPct: Double, samples: List<Sample>
    ): VariantResult {
        var highWater = entry
        var partialTaken = false
        var stop = entry * (1.0 - stopPct / 100.0)
        val target = entry * (1.0 + targetPct / 100.0)
        samples.forEach { sample ->
            highWater = maxOf(highWater, sample.price)
            val highPct = (highWater / entry - 1.0) * 100.0
            if (highPct >= 0.55) {
                if (!partialTaken && sample.price >= target) {
                    return VariantResult("SAME_SAMPLE_PARTIAL_TARGET_ORDER_UNRESOLVED")
                }
                partialTaken = true
                val ratchets = kotlin.math.floor((highPct - 0.55) / 0.10).coerceAtLeast(0.0)
                val protectedPct = 0.50 + ratchets * 0.10
                stop = maxOf(stop, entry * (1.0 + protectedPct / 100.0))
            }
            if (sample.price <= stop) {
                if (!partialTaken) return VariantResult(
                    "STOP_FIRST_SAMPLED", -stopPct, sample.atMs)
                val remainingPct = (stop / entry - 1.0) * 100.0
                val weighted = 0.5 * 0.55 + 0.5 * remainingPct
                return VariantResult("PARTIAL_THEN_TRAIL_EXIT_SAMPLED", weighted, sample.atMs)
            }
            if (sample.price >= target) {
                if (!partialTaken) return VariantResult("SAME_SAMPLE_PARTIAL_TARGET_ORDER_UNRESOLVED")
                val weighted = 0.5 * 0.55 + 0.5 * targetPct
                return VariantResult("PARTIAL_THEN_TARGET_SAMPLED", weighted, sample.atMs)
            }
        }
        return VariantResult(if (partialTaken) "PARTIAL_THEN_UNRESOLVED_CENSORED" else "UNRESOLVED_CENSORED")
    }
}
