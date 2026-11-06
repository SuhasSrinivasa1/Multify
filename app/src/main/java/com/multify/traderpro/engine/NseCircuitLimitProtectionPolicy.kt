package com.multify.traderpro.engine

import kotlin.math.ceil

/**
 * Preflight guard for CNC MARKET BUY orders.
 *
 * Groww documents market protection as a limit-price buffer and illustrates a 5% example.
 * The actual broker buffer can vary with liquidity; 5% is used as a conservative screening
 * envelope. If that envelope crosses the exchange upper circuit, do not submit the MARKET BUY.
 * This deliberately fails closed instead of guessing a replacement LIMIT price or leaving
 * a potentially unfilled/part-filled order without an app-managed protection record.
 */
object NseCircuitLimitProtectionPolicy {
    const val MARKET_PROTECTION_BUFFER_PCT = 5.0
    private const val PRICE_SCALE = 100.0
    private const val PRICE_TOLERANCE_RUPEES = 0.005

    data class Verdict(
        val allowed: Boolean,
        val reasonCode: String,
        val lastPrice: Double? = null,
        val lowerCircuitLimit: Double? = null,
        val upperCircuitLimit: Double? = null,
        val estimatedProtectedBuyPrice: Double? = null
    )

    fun evaluate(
        lastPrice: Double?,
        upperCircuitLimit: Double?,
        lowerCircuitLimit: Double? = null
    ): Verdict {
        val ltp = lastPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: return Verdict(false, "LAST_PRICE_UNAVAILABLE", lastPrice, lowerCircuitLimit, upperCircuitLimit)
        val upper = upperCircuitLimit?.takeIf { it.isFinite() && it > 0.0 }
            ?: return Verdict(false, "UPPER_CIRCUIT_UNAVAILABLE", ltp, lowerCircuitLimit, upperCircuitLimit)
        val lower = if (lowerCircuitLimit == null) null else
            lowerCircuitLimit.takeIf { it.isFinite() && it > 0.0 }
                ?: return Verdict(false, "LOWER_CIRCUIT_INVALID", ltp, lowerCircuitLimit, upper)
        if (lower != null && lower > upper) {
            return Verdict(false, "CIRCUIT_RANGE_INVALID", ltp, lower, upper)
        }
        if (ltp > upper + PRICE_TOLERANCE_RUPEES ||
            (lower != null && ltp < lower - PRICE_TOLERANCE_RUPEES)
        ) {
            return Verdict(false, "LAST_PRICE_OUTSIDE_CIRCUIT", ltp, lower, upper)
        }

        // Round upward to paise: the estimated BUY protection price must never be understated.
        val protectedPrice = ceil(
            ltp * (1.0 + MARKET_PROTECTION_BUFFER_PCT / 100.0) * PRICE_SCALE
        ) / PRICE_SCALE
        if (!protectedPrice.isFinite()) {
            return Verdict(false, "PROTECTED_PRICE_INVALID", ltp, lower, upper)
        }
        if (protectedPrice > upper + PRICE_TOLERANCE_RUPEES) {
            return Verdict(
                false, "MARKET_PROTECTION_EXCEEDS_UPPER_CIRCUIT",
                ltp, lower, upper, protectedPrice
            )
        }
        return Verdict(true, "CIRCUIT_CHECK_PASSED", ltp, lower, upper, protectedPrice)
    }
}
