package com.multify.traderpro.engine

import java.util.Locale

/** Broker payloads are untrusted: a null/blank symbol cannot establish position ownership. */
object BrokerInventorySymbolPolicy {
    fun normalize(symbol: String?): String? =
        symbol?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.US)
}
