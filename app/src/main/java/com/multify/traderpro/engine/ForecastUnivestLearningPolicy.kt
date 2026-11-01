package com.multify.traderpro.engine

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * Research-only Univest notification grammar, adapted from Univest-AutoTrade/UnivestParser.
 * Not an order signal. The 48 features are 40 independently observed factor raws + 8
 * entry-time context values. Missing factors remain null, never fabricated as zero.
 */
object ForecastUnivestLearningPolicy {
    const val OFFICIAL_PACKAGE = "com.univest.capp"
    const val PARAMETER_COUNT = 48
    const val MIN_CLOSED_COHORT = 30
    const val MIN_DAYS = 5
    const val MIN_SYMBOLS = 4
    const val MIN_WIN_RATE = 0.75
    const val MAX_BONUS = 0.045
    private val india = ZoneId.of("Asia/Kolkata")
    private val duration = Regex("(?i)\\b(?:duration|holding(?:\\s+period)?|time\\s*horizon)\\s*[:\\-]?\\s*(\\d+(?:\\.\\d+)?)\\s*(?:[-–—]|to)?\\s*(\\d+(?:\\.\\d+)?)?\\s*months?\\b")
    private val stockLabel = Regex("(?im)^\\s*(?:stock(?:\\s+name)?|symbol|scrip|script|company|advisory\\s+pick)\\s*[:\\-]\\s*([A-Z][A-Z0-9&.\\-]{1,29})\\s*$")
    private val compact = Regex("(?im)^\\s*(?:stock|symbol|advisory\\s+pick)\\s*[:\\-]\\s*([A-Z][A-Z0-9&.\\-]{1,29})\\b")
    enum class Type { ENTRY, EXIT }
    data class Signal(val type: Type, val symbol: String, val durationMonths: Double?)

    fun parse(sourcePackage: String, text: String): Signal? {
        if (sourcePackage != OFFICIAL_PACKAGE) return null
        val lower = text.lowercase(Locale.ROOT)
        if (listOf("options", "option pick", "futures", "future pick", "commodity", "mcx", "forex",
                "currency", "f&o", "fno", "call option", "put option", "nifty", "sensex",
                "bank nifty", "webinar", "discount", "subscribe", "promotion").any { lower.contains(it) }) return null
        val symbol = (stockLabel.find(text)?.groupValues?.get(1)
            ?: compact.find(text)?.groupValues?.get(1))?.trim()?.uppercase(Locale.ROOT)
            ?.removeSuffix("-BE") ?: return null
        if (!symbol.matches(Regex("[A-Z][A-Z0-9&.\\-]{1,29}"))) return null
        if (listOf("equity profit of the day", "book profit for the day", "book profits for the day",
                "equity profit booked", "profit booked", "book profit", "close position",
                "exit stock", "exit recommendation", "recommendation closed", "advisory closed",
                "sell recommendation", "exit now").any { lower.contains(it) }) return Signal(Type.EXIT, symbol, null)
        if (!listOf("new advisory pick", "new equity pick", "new stock pick", "equity pick",
                "stock recommendation", "new recommendation", "equity recommendation",
                "new investment idea", "new stock recommendation").any { lower.contains(it) }) return null
        val window = duration.find(text) ?: return null
        val start = window.groupValues[1].toDoubleOrNull() ?: return null
        val end = window.groupValues[2].takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: start
        if (start < 1.0 || end < start || end > 3.0) return null
        return Signal(Type.ENTRY, symbol, end)
    }

    fun date(at: Long): String = Instant.ofEpochMilli(at).atZone(india).toLocalDate().toString()
    fun sameTradingDay(entry: Long, exit: Long): Boolean =
        entry > 0L && exit > entry && date(entry) == date(exit)

    /** 40 factor RAW values, not normalized vote scores; numeric values missing at entry stay null. */
    fun fingerprint(model: MultifyFactorModel, evaluation: StrategyEvaluation, marketRegime: String): Map<String, Any?> {
        require(model.factors.size == 40)
        val result = linkedMapOf<String, Any?>()
        model.factors.forEach { result[it.name] = it.raw?.takeIf(Double::isFinite) }
        result["entry_strategy"] = evaluation.strategy
        result["entry_regime"] = evaluation.regime
        result["entry_market_regime"] = marketRegime
        result["entry_confidence"] = evaluation.confidence.takeIf(Double::isFinite)
        result["entry_directional_score"] = evaluation.directionalScore.takeIf(Double::isFinite)
        result["entry_family_consensus"] = model.consensusScore.takeIf(Double::isFinite)
        result["entry_positive_families"] = model.positiveFamilies
        result["entry_dominant_family"] = model.dominantFamily
        check(result.size == PARAMETER_COUNT)
        return result
    }

    data class ClosedCase(val symbol: String, val date: String, val returnPct: Double, val features: Map<String, Any?>)
    data class Result(val bonus: Double, val eligible: Boolean, val totalClosed: Int,
                      val hitRate: Double, val matched: Int, val distinctDays: Int,
                      val distinctSymbols: Int, val reason: String)

    /**
     * Complete same-day entry->exit cases alone form the denominator. Successful and unsuccessful
     * outcomes count; future/same-day records may not influence a candidate. Even a positive
     * similarity can only improve ranking AFTER independent historical maturity checks.
     */
    fun evaluate(cases: List<ClosedCase>, candidate: Map<String, Any?>, beforeDate: String): Result {
        val eligible = cases.filter { it.date < beforeDate && it.returnPct.isFinite() }
            .distinctBy { it.date to it.symbol }
        val total = eligible.size
        val days = eligible.map { it.date }.distinct().size
        val symbols = eligible.map { it.symbol }.distinct().size
        val hits = eligible.count { it.returnPct > 0.0 }
        val rate = if (total > 0) hits.toDouble() / total else 0.0
        fun reject(reason: String) = Result(0.0, false, total, rate, 0, days, symbols, reason)
        if (total < MIN_CLOSED_COHORT || days < MIN_DAYS || symbols < MIN_SYMBOLS) return reject("UNIVEST_COHORT_IMMATURE")
        if (rate < MIN_WIN_RATE) return reject("UNIVEST_SAME_DAY_HIT_RATE_BELOW_75PCT")
        val winners = eligible.filter { it.returnPct > 0.0 }
        val close = winners.mapNotNull { row ->
            var shared = 0; var score = 0.0
            for ((key, value) in candidate) {
                if (key !in row.features || value == null) continue
                val old = row.features[key] ?: continue
                if (value is Number && old is Number) {
                    val now = value.toDouble(); val past = old.toDouble()
                    if (!now.isFinite() || !past.isFinite()) continue
                    val scale = maxOf(1.0, abs(now), abs(past))
                    score += (1.0 - abs(now - past) / scale).coerceIn(0.0, 1.0)
                } else if (value is String && old is String) {
                    score += if (value == old) 1.0 else 0.0
                } else continue
                shared++
            }
            if (shared < 20) null else score / shared * (shared.toDouble() / PARAMETER_COUNT)
        }.filter { it >= 0.65 }
        if (close.size < 8) return reject("INSUFFICIENT_COMPARABLE_48_FACTOR_WINNERS")
        val confidence = close.sortedDescending().take(12).average().coerceIn(0.0, 1.0)
        return Result((MAX_BONUS * confidence).coerceIn(0.0, MAX_BONUS), true, total, rate,
            close.size, days, symbols, "MATURE_SAME_DAY_UNIVEST_POSITIVE_PATTERN")
    }
}
