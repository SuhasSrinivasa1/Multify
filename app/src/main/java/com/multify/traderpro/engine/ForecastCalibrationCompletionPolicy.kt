package com.multify.traderpro.engine

/**
 * Completed, real research outcomes are the ONLY evidence for calibration.
 * Duplicates from the same stock/date do not add sessions or symbol counts.
 */
object ForecastCalibrationCompletionPolicy {
    data class CompletedOutcome(
        val date: String,
        val symbol: String,
        val updatedAtMs: Long?,
        val mfePct: Double?,
        val maePct: Double?,
        val closePct: Double?
    )
    fun qualifyingDates(
        rows: List<CompletedOutcome>,
        today: String,
        todayFullSessionMature: Boolean,
        minimumDistinctSymbols: Int
    ): Set<String> {
        require(minimumDistinctSymbols > 0)
        return rows.asSequence()
            .filter { it.date < today || it.date == today && todayFullSessionMature }
            .filter { it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
            .filter { it.symbol.isNotBlank() && it.updatedAtMs != null && it.updatedAtMs > 0L }
            .filter {
                it.mfePct?.isFinite() == true &&
                    it.maePct?.isFinite() == true &&
                    it.closePct?.isFinite() == true
            }
            .groupBy { it.date }
            .filterValues { group ->
                group.map { it.symbol.uppercase(java.util.Locale.US) }.distinct().size >= minimumDistinctSymbols
            }.keys
    }
}
