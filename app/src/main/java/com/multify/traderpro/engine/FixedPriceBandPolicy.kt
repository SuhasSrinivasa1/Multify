package com.multify.traderpro.engine

/** Fixed-only price eligibility, kept separate from Forecast's existing >₹200 band. */
object FixedPriceBandPolicy {
    const val MIN_EXCLUSIVE_RUPEES = 100.0
    const val MAX_EXCLUSIVE_RUPEES = 5_000.0

    fun eligible(price: Double?): Boolean =
        price != null && price.isFinite() &&
            price > MIN_EXCLUSIVE_RUPEES && price < MAX_EXCLUSIVE_RUPEES
}
