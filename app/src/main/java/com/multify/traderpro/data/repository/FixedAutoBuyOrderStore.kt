package com.multify.traderpro.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Independent broker-entry ledger for the Fixed engine. */
@Singleton
class FixedAutoBuyOrderStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("fixed_auto_buy_orders_v75", Context.MODE_PRIVATE)

    @Synchronized
    internal fun all(): List<ForecastAutoBuyOrderState> =
        prefs.getStringSet(KEY_ORDERS, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .sortedBy { it.placedAtMs }

    @Synchronized
    internal fun all(date: String): List<ForecastAutoBuyOrderState> = all().filter { it.date == date }

    @Synchronized
    internal fun forSymbol(date: String, symbol: String): ForecastAutoBuyOrderState? =
        all(date).firstOrNull { it.symbol.equals(symbol, true) }

    @Synchronized
    internal fun upsert(state: ForecastAutoBuyOrderState) {
        val rows = all().filterNot { it.date == state.date && it.symbol.equals(state.symbol, true) }.toMutableList()
        rows += state
        prefs.edit().putStringSet(KEY_ORDERS, rows.map(::encode).toSet()).commit()
    }

    @Synchronized
    internal fun remove(date: String, symbol: String) {
        val rows = all().filterNot { it.date == date && it.symbol.equals(symbol, true) }
        prefs.edit().putStringSet(KEY_ORDERS, rows.map(::encode).toSet()).commit()
    }

    private fun encode(x: ForecastAutoBuyOrderState): String = listOf(
        x.date, x.forecastId.toString(), x.symbol, x.orderId, x.referenceId,
        x.quantity.toString(), x.fixedEntryPrice.toString(), x.fixedTargetPrice.toString(),
        x.placedAtMs.toString(), x.status, x.brokerKind
    ).joinToString("\t")

    private fun decode(raw: String): ForecastAutoBuyOrderState? {
        val p = raw.split('\t')
        if (p.size != 11) return null
        val id = p[1].toLongOrNull() ?: return null
        val qty = p[5].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val entry = p[6].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val target = p[7].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val placed = p[8].toLongOrNull()?.takeIf { it > 0L } ?: return null
        return ForecastAutoBuyOrderState(
            date = p[0], forecastId = id, symbol = p[2], orderId = p[3], referenceId = p[4],
            quantity = qty, fixedEntryPrice = entry, fixedTargetPrice = target, placedAtMs = placed,
            status = p[9], brokerKind = p[10]
        )
    }

    companion object { private const val KEY_ORDERS = "orders" }
}
