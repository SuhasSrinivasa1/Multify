package com.multify.traderpro.engine

/**
 * A news event remains valid research even when its price cannot qualify for
 * production. All published Forecast records, including news-originated ones,
 * must observe the same strict price band and duplicate protections.
 */
object ForecastNewsPublicationGate {
    fun mayPublish(price: Double?, qualifies: Boolean, alreadyRecommended: Boolean): Boolean =
        RecommendationPriceBandPolicy.eligible(price) && qualifies && !alreadyRecommended

    fun priceRejection(price: Double?): String? =
        if (RecommendationPriceBandPolicy.eligible(price)) null
        else "OUTSIDE_200_5000_EXCLUSIVE_PRICE_BAND"
}
