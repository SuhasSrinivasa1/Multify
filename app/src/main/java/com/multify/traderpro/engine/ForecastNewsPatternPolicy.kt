package com.multify.traderpro.engine

import java.util.Locale
import kotlin.math.max

/** Forecast research only. Headline classification is not a causal or profitable-trade claim. */
object ForecastNewsPatternPolicy {
    data class Assessment(val category: String, val direction: String, val confidence: Double,
        val features: Map<String, Any?>, val reason: String)

    private val categories = linkedMapOf(
        "EARNINGS" to listOf("quarterly results", "financial results", "quarter ended", "annual results"),
        "PROFIT_MARGIN" to listOf("net profit", "ebitda", "operating margin", "profit after tax"),
        "ORDER_CONTRACT" to listOf("order received", "work order", "contract awarded", "order win", "letter of award"),
        "INSTITUTIONAL_DEAL" to listOf("bulk deal", "block deal", "institutional investment", "stake acquisition", "fund raising"),
        "CUSTOMER_GROWTH" to listOf("subscribers", "customer base", "user growth", "new customers"),
        "EXPANSION" to listOf("capacity expansion", "new plant", "new facility", "expansion", "commercial production"),
        "ACQUISITION" to listOf("acquisition", "merger", "strategic partnership", "joint venture"),
        "REGULATORY" to listOf("regulatory approval", "usfda approval", "drug approval", "license granted", "clearance"),
        "GUIDANCE" to listOf("guidance raised", "outlook revised", "forecast raised", "management guidance"),
        "DIVIDEND" to listOf("interim dividend", "final dividend", "special dividend", "buyback")
    )
    private val negative = listOf("loss widened", "profit declined", "revenue declined", "guidance cut", "penalty", "fraud", "default", "downgrade", "resigned", "termination", "order cancelled")
    private val positive = listOf("rose", "grew", "increased", "improved", "surge", "record", "awarded", "approved", "secured", "wins", "expands", "raises")
    private val percent = Regex("([+-]?\\d+(?:\\.\\d+)?)\\s*%")
    private val money = Regex("(?:₹|rs\\.?|inr)\\s*([\\d,]+(?:\\.\\d+)?)\\s*(crore|cr|lakh|million|billion)?", RegexOption.IGNORE_CASE)

    fun assess(title: String, summary: String, ageMinutes: Long, cluster5: Int, cluster10: Int, cluster15: Int,
               sourceVerified: Boolean, sector: String? = null): Assessment {
        val all = (title + " " + summary).lowercase(Locale.ROOT)
        val category = categories.entries.firstOrNull { e -> e.value.any { all.contains(it) } }?.key ?: "OTHER"
        val negatives = negative.count { all.contains(it) }
        val positives = positive.count { all.contains(it) }
        val direction = when {
            negatives > 0 -> "NEGATIVE_OR_AMBIGUOUS"
            positives > 0 && category != "OTHER" -> "POSITIVE_POSSIBLE"
            else -> "UNVERIFIED"
        }
        val numbers = percent.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val amountMatch = money.find(all)
        val amount = amountMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()
        val moneyUnit = amountMatch?.groupValues?.get(2)?.lowercase(Locale.ROOT).orEmpty()
        val croreAmount = when (moneyUnit) {
            "crore", "cr" -> amount
            "lakh" -> amount?.div(100.0)
            "million" -> amount?.div(10.0)
            "billion" -> amount?.times(100.0)
            else -> null
        }
        val confidence = ((if (sourceVerified) .18 else 0.0) +
            (if (category != "OTHER") .22 else 0.0) +
            (if (direction == "POSITIVE_POSSIBLE") .20 else 0.0) +
            (if (croreAmount != null || numbers.isNotEmpty()) .15 else 0.0) +
            (if (cluster10 >= 2) .05 else 0.0) -
            (if (ageMinutes > 10) .15 else 0.0) -
            (if (negatives > 0) .40 else 0.0)).coerceIn(0.0, 1.0)
        // Exactly forty nullable features. Missing financial/company comparisons are UNKNOWN, not zero.
        val f = linkedMapOf<String, Any?>(
            "news_category" to category, "direction" to direction, "category_confidence" to confidence,
            "source_verified" to sourceVerified, "age_minutes" to ageMinutes,
            "cluster_5m" to cluster5, "cluster_10m" to cluster10, "cluster_15m" to cluster15,
            "positive_phrase_count" to positives, "negative_phrase_count" to negatives,
            "numeric_percent_count" to numbers.size, "reported_growth_pct" to numbers.firstOrNull(),
            "largest_reported_percent" to numbers.maxOrNull(), "reported_amount_crore" to croreAmount,
            "money_unit_original" to moneyUnit.ifEmpty { null }, "headline_length" to title.length,
            "summary_length" to summary.length, "sector" to sector,
            "earnings_event" to (category == "EARNINGS"), "profit_event" to (category == "PROFIT_MARGIN"),
            "contract_event" to (category == "ORDER_CONTRACT"), "institutional_event" to (category == "INSTITUTIONAL_DEAL"),
            "subscriber_event" to (category == "CUSTOMER_GROWTH"), "expansion_event" to (category == "EXPANSION"),
            "acquisition_event" to (category == "ACQUISITION"), "approval_event" to (category == "REGULATORY"),
            "guidance_event" to (category == "GUIDANCE"), "dividend_event" to (category == "DIVIDEND"),
            "revenue_yoy_pct" to null, "profit_yoy_pct" to null, "ebitda_yoy_pct" to null,
            "margin_delta_pp" to null, "earnings_surprise_pct" to null, "deal_to_market_cap_pct" to null,
            "ownership_change_pct" to null, "capacity_growth_pct" to null,
            "relative_sector_strength_pct" to null, "pre_news_volume_ratio" to null,
            "historical_category_hit_rate" to null, "post_news_spike_1pct" to null
        )
        check(f.size == 40)
        return Assessment(category, direction, confidence, f,
            if (direction == "POSITIVE_POSSIBLE") "Potential catalyst; requires fresh candle/liquidity validation" else "Not confirmed positive")
    }
}
