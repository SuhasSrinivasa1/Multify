package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload

/** Read-only comparison to observable broker market depth. Never a Fixed quality or BUY gate. */
data class FixedMarketQualityShadow(
    val bookStatus: String,
    val bid: Double?,
    val ask: Double?,
    val observedSpreadBps: Double?,
    val dayTradedShares: Long?,
    val estimatedTradedValueRupees: Double?,
    val quoteDayHigh: Double?,
    val brokerReportedDayChangePct: Double?, 
    val distanceFromQuoteDayHighPct: Double?,
    val appreciationSinceFirstPrimaryPct: Double?,
    val warnings: List<String>
)

object FixedMarketQualityShadowPolicy {
    const val SHADOW_WIDE_SPREAD_BPS = 40.0
    const val SHADOW_LOW_ABSOLUTE_VOLUME = 10_000L
    const val SHADOW_ENTRY_RUNUP_PCT = 1.0

    fun assess(quote: QuotePayload?, firstPrimaryPrice: Double? = null): FixedMarketQualityShadow {
        fun clean(x: Double?): Double? = x?.takeIf { it.isFinite() && it > 0.0 }
        val bid = clean(quote?.bidPrice) ?: quote?.depth?.buy.orEmpty().mapNotNull { clean(it.price) }.maxOrNull()
        val ask = clean(quote?.offerPrice) ?: quote?.depth?.sell.orEmpty().mapNotNull { clean(it.price) }.minOrNull()
        val book = when {
            bid == null || ask == null -> "BID_ASK_MISSING"
            ask < bid -> "CROSSED_MARKET"
            else -> "VALID_BOOK"
        }
        val spread = if (book == "VALID_BOOK") (ask!! - bid!!) / ((ask + bid) / 2.0) * 10_000.0 else null
        val last = clean(quote?.lastPrice)
        val high = clean(quote?.high)
        val volume = quote?.volume?.takeIf { it >= 0L }
        val dayMove = quote?.dayChangePct?.takeIf { it.isFinite() }
        val fromHigh = if (last != null && high != null && high >= last)
            ((high - last) / high) * 100.0 else null
        val runup = if (last != null && firstPrimaryPrice != null && firstPrimaryPrice.isFinite() &&
            firstPrimaryPrice > 0.0) (last / firstPrimaryPrice - 1.0) * 100.0 else null
        val warnings = buildList {
            if (book != "VALID_BOOK") add(book)
            if (spread != null && spread > SHADOW_WIDE_SPREAD_BPS) add("WIDE_SPREAD_SHADOW")
            if (volume != null && volume < SHADOW_LOW_ABSOLUTE_VOLUME) add("LOW_ABSOLUTE_VOLUME_SHADOW")
            if (dayMove != null && dayMove > 5.0) add("ALREADY_UP_OVER_5PCT_SHADOW")
            if (dayMove != null && dayMove > 20.0) add("EXTREME_DAY_RISE_SHADOW")
            if (runup != null && runup > SHADOW_ENTRY_RUNUP_PCT) add("PRICE_CHASING_SHADOW")
            if (fromHigh != null && fromHigh <= 1.0) add("NEAR_BROKER_QUOTE_DAY_HIGH_SHADOW")
        }
        return FixedMarketQualityShadow(
            book, bid, ask, spread, volume,
            if (last != null && volume != null) last * volume else null,
            high, dayMove, fromHigh, runup, warnings
        )
    }
}
