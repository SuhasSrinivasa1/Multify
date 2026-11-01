package com.multify.traderpro.engine

import java.util.Locale
import kotlin.math.max

/** Forecast research only. Headline classification is not a causal or profitable-trade claim. */
object ForecastNewsPatternPolicy {
    data class Assessment(val category: String, val direction: String, val confidence: Double,
        val features: Map<String, Any?>, val reason: String)

    // 22 broad catalyst families; preserves original ten category IDs for historical learning.
    private val categories = linkedMapOf(
        "EARNINGS" to listOf("quarterly results", "financial results", "quarter ended", "annual results"),
        "PROFIT_MARGIN" to listOf("net profit", "ebitda", "operating margin", "profit after tax"),
        "ORDER_CONTRACT" to listOf("order received", "work order", "contract awarded", "order win", "letter of award"),
        "INSTITUTIONAL_DEAL" to listOf("bulk deal", "block deal", "institutional investment", "stake acquisition"),
        "CUSTOMER_GROWTH" to listOf("subscribers", "customer base", "user growth", "new customers"),
        "EXPANSION" to listOf("capacity expansion", "new plant", "new facility", "expansion", "commercial production"),
        "ACQUISITION" to listOf("acquisition", "merger", "strategic partnership", "joint venture"),
        "REGULATORY" to listOf("regulatory approval", "usfda approval", "drug approval", "license granted", "clearance"),
        "GUIDANCE" to listOf("guidance raised", "outlook revised", "forecast raised", "management guidance"),
        "DIVIDEND" to listOf("interim dividend", "final dividend", "special dividend"),
        "FUNDING" to listOf("funding secured", "fund raise", "fundraising", "capital infusion", "equity financing", "raised funding"),
        "IPO_LISTING" to listOf("ipo listing", "listed on nse", "initial public offering", "listing approval"),
        "PRODUCT_LAUNCH" to listOf("product launch", "new product", "commercial launch", "product approval"),
        "EXPORT_DEAL" to listOf("export order", "export contract", "overseas order", "international contract"),
        "GOVERNMENT_POLICY" to listOf("government subsidy", "production linked incentive", "pli scheme", "government incentive"),
        "CAPACITY_UTILIZATION" to listOf("capacity utilization", "capacity utilisation", "production ramp", "utilisation increased"),
        "DEBT_REDUCTION" to listOf("debt repayment", "debt reduction", "debt free", "loan repayment", "prepaid debt"),
        "BUYBACK" to listOf("share buyback", "buyback approved", "buyback offer"),
        "PROMOTER_PURCHASE" to listOf("promoter acquired", "promoter purchase", "promoter increased stake"),
        "PATENT_IP" to listOf("patent granted", "patent approval", "intellectual property granted"),
        "STRATEGIC_ALLIANCE" to listOf("strategic alliance", "strategic collaboration", "memorandum of understanding", "signed mou"),
        "RATING_UPGRADE" to listOf("credit rating upgraded", "rating upgrade", "outlook upgraded")
    )
    private val negative = listOf("loss widened", "profit declined", "revenue declined", "guidance cut", "penalty", "fraud", "default", "downgrade", "resigned", "termination", "order cancelled", "guidance lowered", "regulatory warning", "dilution", "pledge", "lawsuit", "insolvency", "fraud investigation")
    private val positive = listOf("rose", "grew", "increased", "improved", "surge", "record", "awarded", "approved", "secured", "wins", "expands", "raises", "secured", "granted", "launched", "upgraded", "inflow", "reduced debt", "awarded contract")
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
        // Amounts are parsed only for explicit INR figures; foreign currencies and
        // missing units remain UNKNOWN, not implicitly converted into rupees.
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
