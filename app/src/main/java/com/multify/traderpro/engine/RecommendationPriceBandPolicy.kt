package com.multify.traderpro.engine

/** Production publication/entry eligibility for Fixed and Forecast only. Multify and Univest are unaffected. */
object RecommendationPriceBandPolicy {
    const val MIN_EXCLUSIVE_RUPEES = 200.0
    const val MAX_EXCLUSIVE_RUPEES = 5_000.0

    fun eligible(price: Double?): Boolean =
        price != null && price.isFinite() &&
            price > MIN_EXCLUSIVE_RUPEES && price < MAX_EXCLUSIVE_RUPEES
}
