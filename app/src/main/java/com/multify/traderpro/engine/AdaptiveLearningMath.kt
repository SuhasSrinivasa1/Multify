package com.multify.traderpro.engine

object AdaptiveLearningMath {
    fun realizedReturnPct(storedPct: Double?, entryPrice: Double, exitPrice: Double?): Double? {
        storedPct?.takeIf { it.isFinite() }?.let { return it }
        val exit = exitPrice?.takeIf { it.isFinite() } ?: return null
        if (!entryPrice.isFinite() || entryPrice <= 0.0) return null
        return (exit - entryPrice) / entryPrice * 100.0
    }

    fun rollingMean(values: List<Double>, fallback: Double): Double =
        values.filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.average() ?: fallback

    fun median(values: List<Double>, fallback: Double): Double {
        val x = values.filter { it.isFinite() }.sorted()
        if (x.isEmpty()) return fallback
        return if (x.size % 2 == 1) x[x.size / 2] else (x[x.size / 2 - 1] + x[x.size / 2]) / 2.0
    }

    fun trimmedMean(values: List<Double>, fallback: Double, trimFraction: Double = 0.10): Double {
        val x = values.filter { it.isFinite() }.sorted()
        if (x.isEmpty()) return fallback
        val trim = (x.size * trimFraction.coerceIn(0.0, .40)).toInt()
        val kept = x.drop(trim).dropLast(trim)
        return kept.takeIf { it.isNotEmpty() }?.average() ?: x.average()
    }

    fun ewma(valuesOldestToNewest: List<Double>, fallback: Double, alpha: Double = 0.22): Double {
        val x = valuesOldestToNewest.filter { it.isFinite() }
        if (x.isEmpty()) return fallback
        var v = x.first()
        val a = alpha.coerceIn(.02, .80)
        for (n in x.drop(1)) v = a * n + (1.0 - a) * v
        return v
    }

    fun quantile(values: List<Double>, q: Double, fallback: Double): Double {
        val x = values.filter { it.isFinite() }.sorted()
        if (x.isEmpty()) return fallback
        val p = q.coerceIn(0.0, 1.0) * (x.size - 1)
        val lo = p.toInt()
        val hi = kotlin.math.ceil(p).toInt()
        if (lo == hi) return x[lo]
        return x[lo] + (x[hi] - x[lo]) * (p - lo)
    }
}
