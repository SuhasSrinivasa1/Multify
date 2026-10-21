package com.multify.traderpro.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class ForecastAutoBuyOrderState(
    val date: String,
    val forecastId: Long,
    val symbol: String,
    val orderId: String,
    val referenceId: String,
    val quantity: Int,
    val fixedEntryPrice: Double,
    val fixedTargetPrice: Double,
    val placedAtMs: Long,
    val status: String
)

/**
 * Persists forecast auto-buy broker orders outside Room so a debug upgrade or Android process restart
 * cannot create duplicate LIMIT BUY orders before the broker state is reconciled.
 */
@Singleton
class ForecastAutoBuyOrderStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("forecast_auto_buy_orders_v74", Context.MODE_PRIVATE)

    @Synchronized
    internal fun all(date: String): List<ForecastAutoBuyOrderState> {
        rotateTo(date)
        return prefs.getStringSet(KEY_ORDERS, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .filter { it.date == date }
            .sortedBy { it.placedAtMs }
    }

    @Synchronized
    internal fun forSymbol(date: String, symbol: String): ForecastAutoBuyOrderState? =
        all(date).firstOrNull { it.symbol.equals(symbol, true) }

    @Synchronized
    internal fun upsert(state: ForecastAutoBuyOrderState) {
        rotateTo(state.date)
        val rows = prefs.getStringSet(KEY_ORDERS, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .filterNot { it.date == state.date && it.symbol.equals(state.symbol, true) }
            .toMutableList()
        rows += state
        prefs.edit().putStringSet(KEY_ORDERS, rows.map(::encode).toSet()).commit()
    }

    @Synchronized
    internal fun remove(date: String, symbol: String) {
        rotateTo(date)
        val rows = prefs.getStringSet(KEY_ORDERS, emptySet()).orEmpty()
            .mapNotNull(::decode)
            .filterNot { it.date == date && it.symbol.equals(symbol, true) }
        prefs.edit().putStringSet(KEY_ORDERS, rows.map(::encode).toSet()).commit()
    }

    @Synchronized
    private fun rotateTo(date: String) {
        val storedDate = prefs.getString(KEY_DATE, null)
        if (storedDate == date) return
        prefs.edit().clear().putString(KEY_DATE, date).commit()
    }

    private fun encode(x: ForecastAutoBuyOrderState): String = listOf(
        x.date,
        x.forecastId.toString(),
        x.symbol,
        x.orderId,
        x.referenceId,
        x.quantity.toString(),
        x.fixedEntryPrice.toString(),
        x.fixedTargetPrice.toString(),
        x.placedAtMs.toString(),
        x.status
    ).joinToString("\t")

    private fun decode(raw: String): ForecastAutoBuyOrderState? {
        val p = raw.split('\t')
        if (p.size != 10) return null
        val forecastId = p[1].toLongOrNull() ?: return null
        val quantity = p[5].toIntOrNull()?.takeIf { it > 0 } ?: return null
        val entry = p[6].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val target = p[7].toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val placedAt = p[8].toLongOrNull()?.takeIf { it > 0L } ?: return null
        if (p[0].isBlank() || p[2].isBlank() || p[3].isBlank() || p[4].isBlank()) return null
        return ForecastAutoBuyOrderState(
            date = p[0],
            forecastId = forecastId,
            symbol = p[2],
            orderId = p[3],
            referenceId = p[4],
            quantity = quantity,
            fixedEntryPrice = entry,
            fixedTargetPrice = target,
            placedAtMs = placedAt,
            status = p[9].ifBlank { "NEW" }
        )
    }

    companion object {
        private const val KEY_DATE = "date"
        private const val KEY_ORDERS = "orders"
    }
}
