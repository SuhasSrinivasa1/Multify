package com.multify.traderpro.engine

/** Forecast-only fair scheduling. Does not change Fixed or publication eligibility. */
object ForecastConfirmationPriorityPolicy {
    const val MAX_URGENT = 8
    fun prioritize(remaining: List<String>, recentPrimary: List<String>, currentUniverse: List<String>): List<String> {
        val eligible = currentUniverse.toSet()
        val urgent = recentPrimary.asSequence().filter { it in eligible }
            .distinct().take(MAX_URGENT).toList()
        val urgentSet = urgent.toSet()
        return urgent + remaining.filterNot { it in urgentSet }
    }
}
