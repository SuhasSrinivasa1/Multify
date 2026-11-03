package com.multify.traderpro.engine

/**
 * Forecast-only opportunity threshold. Uses *prior completed* research outcomes and
 * preserves non-winners in the denominator. This is mean maximum favourable PRICE
 * movement (MFE), not realized profit, a probability of success or a rupee profit.
 * With insufficient comparable observations the candidate stays in RESEARCH.
 */
object ForecastExpectedAverageGainPolicy {
    const val REQUIRED_AVERAGE_GAIN_PCT_EXCLUSIVE = 1.0
    const val MIN_COMPARABLE_OBSERVATIONS = 5
    const val MIN_COMPARABLE_DAYS = 2
    data class Estimate(val averageMfePct: Double?, val observations: Int,
        val distinctDays: Int, val cohort: String, val eligible: Boolean)

    fun estimate(strategy: String, regime: String, marketRegime: String,
        prior: List<ForecastHistoricalOutcome>): Estimate {
        val rows = prior.filter {
            it.maxFavourablePct.isFinite() && it.maxFavourablePct >= 0.0 &&
                it.maxFavourablePct < 25.0 &&
                it.strategy == strategy && it.regime == regime && it.marketRegime == marketRegime
        }.distinctBy { it.date + "|" + it.symbol }
        val days = rows.map { it.date }.distinct().size
        val sufficient = rows.size >= MIN_COMPARABLE_OBSERVATIONS && days >= MIN_COMPARABLE_DAYS
        val mean = if (sufficient) rows.map { it.maxFavourablePct }.average() else null
        return Estimate(mean, rows.size, days, "SAME_FORECAST_STRATEGY_REGIME_BREADTH",
            mean != null && mean > REQUIRED_AVERAGE_GAIN_PCT_EXCLUSIVE)
    }
}
