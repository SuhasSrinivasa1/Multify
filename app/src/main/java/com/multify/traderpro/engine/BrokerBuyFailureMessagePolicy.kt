package com.multify.traderpro.engine

/** Pure classification of explicit broker error text; no wallet-balance request. */
object BrokerBuyFailureMessagePolicy {
    fun insufficientFunds(errorText: String?): Boolean {
        val s = errorText.orEmpty().lowercase()
        return listOf("insufficient fund", "insufficient balance", "insufficient margin",
            "not enough fund", "not enough balance", "available balance",
            "funds shortfall", "insufficient buying power", "margin shortfall")
            .any { s.contains(it) }
    }
}
