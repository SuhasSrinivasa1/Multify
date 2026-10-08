package com.multify.traderpro.domain

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationParser @Inject constructor() {
    private val stock = Regex("STOCK\\s*NAME\\s*[:\\-]\\s*([A-Z0-9&._-]+)", RegexOption.IGNORE_CASE)
    private val target = Regex("TARGET\\s*[:\\-]\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val stop = Regex("STOP\\s*LOSS\\s*[:\\-]\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val range = Regex("ENTRY\\s*RANGE\\s*[:\\-]\\s*([0-9]+(?:\\.[0-9]+)?)\\s*(?:-|–|TO)\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val book = Regex("BOOK\\s*PROFIT\\s*[:\\-]\\s*([A-Z0-9&._-]+)", RegexOption.IGNORE_CASE)
    private val exit = Regex("EXIT\\s*PRICE\\s*[:\\-]\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val returns = Regex("RETURNS[^0-9-]*(-?[0-9]+(?:\\.[0-9]+)?)\\s*%", RegexOption.IGNORE_CASE)
    private val qty = Regex("QTY\\s*([0-9]+)", RegexOption.IGNORE_CASE)
    private val submittedSymbol = Regex("INTRADAY\\s+([A-Z0-9&._-]+)", RegexOption.IGNORE_CASE)

    fun parse(title: String?, text: String?, bigText: String?): ParsedSignal {
        val raw = listOf(title, text, bigText).filterNotNull().joinToString("\n")
        val normalized = raw
            .replace("✅", " ").replace("🔶", " ").replace("◆", " ")
            .replace("🚀", " ").replace("⏳", " ").replace("📢", " ")
            .replace("•", "\n")
        val upper = normalized.uppercase()

        // Free ideas are deliberately outside this application's mandate. Only the paid Equity Intraday
        // template and its matching Book Profit lifecycle are actionable.
        if (Regex("\\bFREE\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized)) {
            return ParsedSignal(SignalType.UNKNOWN, rawText = raw, confidence = 0.99)
        }

        if (listOf("FUTURE", "OPTION", "F&O", "COMMODITY", "MCX").any { upper.contains(it) }) {
            return ParsedSignal(SignalType.UNKNOWN, rawText = raw, confidence = 0.99)
        }

        if (upper.contains("EQUITY INTRADAY TRADE") && upper.contains("STOCK NAME")) {
            val sm = stock.find(upper)
            val rm = range.find(upper)
            val tm = target.find(upper)
            val sl = stop.find(upper)
            if (sm != null && rm != null && tm != null && sl != null) {
                val a = rm.groupValues[1].toDouble()
                val b = rm.groupValues[2].toDouble()
                return ParsedSignal(
                    type = SignalType.TRADE_RELEASE,
                    symbol = sm.groupValues[1],
                    target = tm.groupValues[1].toDouble(),
                    entryLow = minOf(a, b),
                    entryHigh = maxOf(a, b),
                    stopLoss = sl.groupValues[1].toDouble(),
                    rawText = raw,
                    confidence = 0.995
                )
            }
        }

        if (upper.contains("BOOK PROFIT")) {
            return ParsedSignal(
                type = SignalType.BOOK_PROFIT,
                symbol = book.find(upper)?.groupValues?.getOrNull(1),
                exitPrice = exit.find(upper)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                returnsPct = returns.find(upper)?.groupValues?.getOrNull(1)?.toDoubleOrNull(),
                rawText = raw,
                confidence = if (book.containsMatchIn(upper)) 0.99 else 0.75
            )
        }

        if (upper.contains("EQUITY INTRADAY TRADE IN") && upper.contains("MIN")) {
            return ParsedSignal(SignalType.PRE_ALERT, rawText = raw, confidence = 0.99)
        }

        if (upper.contains("BUY SUBMITTED") && upper.contains("INTRADAY")) {
            return ParsedSignal(
                type = SignalType.BUY_SUBMITTED,
                symbol = submittedSymbol.find(upper)?.groupValues?.getOrNull(1),
                quantity = qty.find(upper)?.groupValues?.getOrNull(1)?.toIntOrNull(),
                rawText = raw,
                confidence = 0.90
            )
        }

        if (upper.contains("AUTO TRADING PAUSED") || upper.contains("UNPROTECTED FILLED QUANTITY")) {
            return ParsedSignal(SignalType.AUTO_PAUSED, rawText = raw, confidence = 0.99)
        }

        return ParsedSignal(SignalType.UNKNOWN, rawText = raw, confidence = 0.10)
    }
}
