package com.multify.traderpro.domain

enum class SignalType {
    TRADE_RELEASE, BOOK_PROFIT, PRE_ALERT, BUY_SUBMITTED, AUTO_PAUSED, UNKNOWN
}

data class ParsedSignal(
    val type: SignalType,
    val symbol: String? = null,
    val target: Double? = null,
    val entryLow: Double? = null,
    val entryHigh: Double? = null,
    val stopLoss: Double? = null,
    val exitPrice: Double? = null,
    val returnsPct: Double? = null,
    val quantity: Int? = null,
    val rawText: String,
    val confidence: Double
) {
    val actionable: Boolean
        get() = type == SignalType.TRADE_RELEASE || type == SignalType.BOOK_PROFIT || type == SignalType.AUTO_PAUSED

    fun summary(): String = buildString {
        append(type.name.replace('_', ' '))
        symbol?.let { append(" · ").append(it) }
        if (entryLow != null && entryHigh != null) append(" · ₹").append(entryLow).append("–").append(entryHigh)
        target?.let { append(" · target ₹").append(it) }
        stopLoss?.let { append(" · SL ₹").append(it) }
        exitPrice?.let { append(" · exit ₹").append(it) }
    }
}
