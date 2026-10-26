package com.multify.traderpro.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.data.network.*
import com.multify.traderpro.data.preferences.AppPreferences
import com.multify.traderpro.engine.FixedShortExitPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/** Independent MIS SHORT executor. It consumes already-published Fixed rows, never runs Fixed. */
@Singleton
class FixedShortExecutionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiFactory: GrowwApiFactory,
    private val appPreferences: AppPreferences,
    private val auditLogger: AuditLogger
) {
    data class ShortRecord(
        val symbol: String,
        val signalDate: String,
        val sourceEntry: Double,
        val sourceTargetPct: Double,
        val sourceConfidence: Double,
        val quantity: Int,
        val orderReference: String,
        val orderId: String = "",
        val entryPrice: Double = 0.0,
        val targetPrice: Double = 0.0,
        val stopPrice: Double = 0.0,
        val bestPrice: Double = 0.0,
        val ocoReference: String = "",
        val ocoId: String = "",
        val state: String = "ENTRY_PENDING",
        val lastUpdatedMs: Long = System.currentTimeMillis(),
        val lastQuoteMs: Long = 0L,
        val lastOcoCheckMs: Long = 0L,
        val lastRiskAlertMs: Long = 0L
    )

    private val zone = ZoneId.of("Asia/Kolkata")
    private val gson = Gson()
    private val prefs = context.getSharedPreferences("fixed_short_mis_v790", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val listType: java.lang.reflect.Type = object : TypeToken<List<ShortRecord>>() {}.type
    private var records: MutableList<ShortRecord> =
        runCatching { gson.fromJson<List<ShortRecord>>(prefs.getString("records", "[]"), listType) }
            .getOrNull()?.toMutableList() ?: mutableListOf()
    @Volatile private var shortBusy = false

    fun snapshots(): List<ShortRecord> = synchronized(records) { records.toList() }
    fun hasUnprotectedShort(): Boolean = snapshots().any {
        it.state in setOf("FILLED_UNPROTECTED", "PROTECTION_FAILED", "ENTRY_PENDING", "EXIT_PENDING")
    }
    fun hasOpenShortFor(symbol: String): Boolean = snapshots().any {
        it.symbol.equals(symbol, true) && it.state !in setOf("CLOSED", "ENTRY_REJECTED")
    }

    private fun save(row: ShortRecord) {
        synchronized(records) {
            val existing = records.indexOfFirst { it.signalDate == row.signalDate && it.symbol == row.symbol }
            if (existing >= 0) records[existing] = row else records.add(row)
            prefs.edit().putString("records", gson.toJson(records)).commit()
        }
    }
    private fun indiaNow() = ZonedDateTime.now(zone)
    private fun ref(prefix: String, seed: String): String {
        val sha = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
        return prefix + sha.take(7).joinToString("") { "%02x".format(it) }
    }
    private fun fmt(price: Double): String = String.format(Locale.US, "%.2f", price)
    private fun bearer(token: String) = "Bearer $token"
    private fun isFilled(status: String): Boolean = status.uppercase(Locale.US) in setOf("EXECUTED", "COMPLETED", "COMPLETE", "FILLED")
    private fun isTerminal(status: String): Boolean = status.uppercase(Locale.US) in setOf("FAILED", "REJECTED", "CANCELLED", "CANCELED")
    private suspend fun brokerMisPositions(token: String): Map<String, GrowwPosition> =
        apiFactory.groww.positions(bearer(token)).requirePayload("Fixed Short MIS positions")
            .positions.filter { it.product.equals("MIS", true) && it.exchange.equals("NSE", true) }
            .associateBy { it.tradingSymbol.uppercase(Locale.US) }

    /** Called AFTER Fixed's order-first recommendation event; no additional Fixed scan. */
    suspend fun submit(token: String, candidates: List<ForecastDto>, budget: Long, excluded: Set<String>): Int = mutex.withLock {
        val now = indiaNow()
        if (now.dayOfWeek.value >= 6 || !FixedShortExitPolicy.isEntryTime(now.hour, now.minute)) return@withLock 0
        if (hasUnprotectedShort()) return@withLock 0
        val existingBrokerPositions = runCatching { brokerMisPositions(token) }.getOrElse {
            auditLogger.log("FIXED_SHORT", "ENTRY_SKIPPED_NO_BROKER_RECONCILIATION", mapOf("error" to (it.message ?: "")))
            return@withLock 0
        }
        var entered = 0
        for (candidate in candidates) {
            // Re-read toggle immediately before each order. This never affects Fixed recommendations.
            if (hasOpenShortFor(candidate.symbol) ||
                excluded.contains(candidate.symbol.uppercase(Locale.US)) ||
                existingBrokerPositions[candidate.symbol.uppercase(Locale.US)]?.quantity != null &&
                existingBrokerPositions[candidate.symbol.uppercase(Locale.US)]!!.quantity != 0
            ) continue
            val nowAtOrder = indiaNow()
            if (!FixedShortExitPolicy.isEntryTime(nowAtOrder.hour, nowAtOrder.minute)) break
            val freshQuote = runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = candidate.symbol).requirePayload("Fixed Short fresh quote")
            }.onFailure {
                auditLogger.log("FIXED_SHORT", "QUOTE_FAILED", mapOf("symbol" to candidate.symbol))
            }.getOrNull() ?: continue
            val ltp = freshQuote.lastPrice ?: continue
            if (!ltp.isFinite() || ltp <= 100.0) continue
            val margins = runCatching {
                apiFactory.groww.margins(bearer(token)).requirePayload("Fixed Short available cash")
            }.onFailure { auditLogger.log("FIXED_SHORT", "MARGIN_FAILED", mapOf("symbol" to candidate.symbol)) }
                .getOrNull() ?: break
            // Deliberately no assumed leverage: cap notional at unencumbered cash and configured budget.
            val available = margins.clearCash.coerceAtLeast(0.0)
            val quantity = floor(minOf(budget.toDouble(), available) / ltp).toInt().coerceAtLeast(0)
            if (quantity <= 0) continue
            val reference = ref("FS", "${candidate.symbol}-${nowAtOrder.toLocalDate()}-${candidate.generatedAtMs}")
            val pending = ShortRecord(
                symbol = candidate.symbol, signalDate = nowAtOrder.toLocalDate().toString(),
                sourceEntry = candidate.entryPrice, sourceTargetPct = candidate.targetPct,
                sourceConfidence = candidate.confidence, quantity = quantity,
                orderReference = reference
            )
            // Durable idempotency marker must exist BEFORE dispatch.
            save(pending)
            try {
                val order = apiFactory.groww.placeOrder(bearer(token), OrderCreateRequest(
                    tradingSymbol = candidate.symbol, quantity = quantity,
                    product = "MIS", transactionType = "SELL", orderReferenceId = reference
                )).requirePayload("Fixed Short MIS SELL entry")
                save(pending.copy(orderId = order.growwOrderId, lastUpdatedMs = System.currentTimeMillis()))
                auditLogger.log("FIXED_SHORT", "ENTRY_SELL_ACCEPTED", mapOf(
                    "symbol" to candidate.symbol, "quantity" to quantity, "order_id" to order.growwOrderId,
                    "reference" to reference, "fixed_algorithm_changed" to false
                ))
                entered++
                // Confirmation and broker OCO are handled without another Fixed scan.
                refreshFillAndProtection(token, pending.copy(orderId = order.growwOrderId))
            } catch (t: Throwable) {
                // Unknown network outcome remains ENTRY_PENDING until reconciled by reference:
                // never send a duplicate SELL blindly.
                auditLogger.log("FIXED_SHORT", "ENTRY_OUTCOME_UNKNOWN_RECONCILE_REFERENCE", mapOf(
                    "symbol" to candidate.symbol, "reference" to reference, "error" to (t.message ?: "")
                ))
                urgent(candidate.symbol, "Short entry unconfirmed; verify broker orders")
            }
        }
        entered
    }

    private suspend fun refreshFillAndProtection(token: String, row: ShortRecord) {
        if (row.state !in setOf("ENTRY_PENDING", "FILLED_UNPROTECTED", "PROTECTION_FAILED")) return
        val brokerOrder = runCatching {
            if (row.orderId.isNotBlank()) apiFactory.groww.orderStatus(bearer(token), row.orderId)
                .requirePayload("Short entry order")
            else apiFactory.groww.orderStatusByReference(bearer(token), row.orderReference)
                .requirePayload("Short entry reference")
        }.getOrNull()
        if (brokerOrder != null && isTerminal(brokerOrder.orderStatus)) {
            save(row.copy(state = "ENTRY_REJECTED", orderId = brokerOrder.growwOrderId, lastUpdatedMs = System.currentTimeMillis()))
            return
        }
        if (brokerOrder == null || !isFilled(brokerOrder.orderStatus)) return
        val positions = runCatching { brokerMisPositions(token) }.getOrNull() ?: return
        val net = positions[row.symbol.uppercase(Locale.US)]?.quantity ?: return
        if (net >= 0) return // Never create a BUY OCO against an unconfirmed short.
        val filled = minOf(-net, brokerOrder.filledQuantity?.takeIf { it > 0 } ?: row.quantity)
        if (filled <= 0) return
        val fill = brokerOrder.averageFillPrice?.takeIf { it.isFinite() && it > 0.0 }
            ?: positions[row.symbol.uppercase(Locale.US)]?.netPrice?.takeIf { it > 0.0 }
            ?: return
        val open = row.copy(
            orderId = brokerOrder.growwOrderId, quantity = filled,
            entryPrice = fill, targetPrice = FixedShortExitPolicy.roundedTarget(FixedShortExitPolicy.target(fill, row.sourceTargetPct)),
            stopPrice = FixedShortExitPolicy.roundedStop(FixedShortExitPolicy.initialStop(fill)),
            bestPrice = fill, state = "FILLED_UNPROTECTED", lastUpdatedMs = System.currentTimeMillis()
        )
        save(open)
        ensureOco(token, open, net)
    }

    private suspend fun ensureOco(token: String, row: ShortRecord, net: Int) {
        if (row.ocoId.isNotBlank() || net >= 0 || row.state == "CLOSED") return
        val ref = row.ocoReference.ifBlank { ref("FQ", row.orderReference) }
        val toSave = row.copy(ocoReference = ref)
        save(toSave)
        try {
            val order = apiFactory.groww.createOco(bearer(token), OcoCreateRequest(
                referenceId = ref, tradingSymbol = row.symbol,
                quantity = minOf(row.quantity, -net), netPositionQuantity = net,
                target = OcoExitLeg(fmt(row.targetPrice), "MARKET"),
                stopLoss = OcoExitLeg(fmt(row.stopPrice), "SL_M")
            )).requirePayload("Fixed Short BUY-to-cover OCO")
            if (order.smartOrderId.isBlank()) error("OCO returned no broker ID")
            save(toSave.copy(ocoId = order.smartOrderId, state = "OCO_ACTIVE",
                lastUpdatedMs = System.currentTimeMillis()))
            auditLogger.log("FIXED_SHORT", "BUY_COVER_OCO_ARMED", mapOf(
                "symbol" to row.symbol, "id" to order.smartOrderId, "target" to row.targetPrice,
                "stop" to row.stopPrice, "quantity" to minOf(row.quantity, -net)
            ))
        } catch (t: Throwable) {
            save(toSave.copy(state = "PROTECTION_FAILED", lastUpdatedMs = System.currentTimeMillis()))
            appPreferences.setFixedShortAutoTrade(false)
            urgent(row.symbol, "Short OCO BUY protection FAILED. Check/cover in Groww immediately")
            auditLogger.log("FIXED_SHORT", "URGENT_OCO_UNPROTECTED_SHORT", mapOf(
                "symbol" to row.symbol, "error" to (t.message ?: t.javaClass.simpleName)
            ))
        }
    }

    private fun urgent(symbol: String, detail: String) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "fixed_short_urgent_v790"
        if (Build.VERSION.SDK_INT >= 26) mgr.createNotificationChannel(
            NotificationChannel(channel, "Fixed SHORT urgent risk", NotificationManager.IMPORTANCE_HIGH))
        @Suppress("MissingPermission")
        runCatching {
            mgr.notify(7900 + symbol.hashCode().and(0x7fff) % 1000,
                NotificationCompat.Builder(context, channel)
                    .setSmallIcon(android.R.drawable.stat_notify_error)
                    .setContentTitle("Fixed SHORT: $symbol needs attention")
                    .setContentText(detail).setStyle(NotificationCompat.BigTextStyle().bigText(detail))
                    .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build())
        }
    }

    /** Always runs off the Fixed scanner; live protection exists at broker via OCO. */
    suspend fun maintain(token: String, permitNonUrgentQuotes: Boolean): Int = mutex.withLock {
        val active = snapshots().filter { it.state !in setOf("CLOSED", "ENTRY_REJECTED") }
        if (active.isEmpty()) return@withLock 0
        val now = indiaNow()
        val nowMs = System.currentTimeMillis()
        val positions = runCatching { brokerMisPositions(token) }.getOrElse {
            if (FixedShortExitPolicy.isSquareOffTime(now.hour, now.minute)) urgent("MIS", "Cannot confirm intraday short positions; verify in Groww")
            return@withLock active.size
        }
        for (row in active) {
            val net = positions[row.symbol.uppercase(Locale.US)]?.quantity ?: 0
            if (row.state == "ENTRY_PENDING") {
                refreshFillAndProtection(token, row)
                continue
            }
            if (net >= 0) {
                save(row.copy(state = "CLOSED", lastUpdatedMs = nowMs))
                auditLogger.log("FIXED_SHORT", "BROKER_CONFIRMED_SHORT_CLOSED", mapOf("symbol" to row.symbol))
                continue
            }
            if (row.ocoId.isBlank()) {
                // Do not blind-retry unknown OCO creation: a timed-out response may have armed one.
                urgent(row.symbol, "No verified protective OCO; manually verify/close MIS short in Groww")
                continue
            }
            if (FixedShortExitPolicy.isSquareOffTime(now.hour, now.minute)) {
                squareOff(token, row, net)
                continue
            }
            if (!permitNonUrgentQuotes || row.state != "OCO_ACTIVE" || nowMs - row.lastQuoteMs < 4_000L) continue
            val quote = runCatching {
                apiFactory.groww.quote(bearer(token), tradingSymbol = row.symbol).requirePayload("Fixed Short monitoring quote")
            }.getOrNull() ?: continue
            val last = quote.lastPrice?.takeIf { it.isFinite() && it > 0.0 } ?: continue
            val best = minOf(row.bestPrice.takeIf { it > 0.0 } ?: row.entryPrice, last)
            val candidateStop = FixedShortExitPolicy.roundedStop(
                FixedShortExitPolicy.trailingBuyStop(row.entryPrice, best, row.stopPrice))
            if (candidateStop + 0.001 < row.stopPrice && candidateStop > last) {
                try {
                    apiFactory.groww.modifyOco(bearer(token), row.ocoId, OcoModifyRequest(
                        quantity = minOf(row.quantity, -net), stopLoss = OcoTriggerUpdate(fmt(candidateStop))
                    )).requirePayload("Short trailing BUY OCO")
                    save(row.copy(bestPrice = best, stopPrice = candidateStop, lastQuoteMs = nowMs,
                        lastUpdatedMs = nowMs))
                    auditLogger.log("FIXED_SHORT", "TRAILING_BUY_STOP_LOWERED", mapOf(
                        "symbol" to row.symbol, "stop" to candidateStop, "market" to last
                    ))
                } catch (t: Throwable) {
                    // Broker retains previous stop; never delete old OCO on a failed modification.
                    save(row.copy(bestPrice = best, lastQuoteMs = nowMs))
                    auditLogger.log("FIXED_SHORT", "TRAILING_MODIFY_FAILED_OLD_OCO_PRESERVED", mapOf(
                        "symbol" to row.symbol, "error" to (t.message ?: "")
                    ))
                }
            } else save(row.copy(bestPrice = best, lastQuoteMs = nowMs))
        }
        active.size
    }

    private suspend fun squareOff(token: String, row: ShortRecord, net: Int) {
        if (row.state == "EXIT_PENDING") return
        // Cancel the OCO before BUY-to-cover. If cancellation is ambiguous, don't create a
        // second BUY that could race with a triggered OCO and open an unintended long.
        try {
            val status = apiFactory.groww.smartOrderStatus(bearer(token), "CASH", "OCO", row.ocoId)
                .requirePayload("Short OCO before squareoff")
            if (status.status.equals("ACTIVE", true)) {
                apiFactory.groww.cancelSmartOrder(bearer(token), "CASH", "OCO", row.ocoId)
                    .requirePayload("Cancel short OCO before squareoff")
            }
            val finalStatus = apiFactory.groww.smartOrderStatus(bearer(token), "CASH", "OCO", row.ocoId)
                .requirePayload("Verify OCO cancelled")
            if (finalStatus.status.equals("ACTIVE", true)) error("OCO still active")
            val refreshed = brokerMisPositions(token)[row.symbol.uppercase(Locale.US)]?.quantity ?: 0
            if (refreshed >= 0) {
                save(row.copy(state = "CLOSED", lastUpdatedMs = System.currentTimeMillis()))
                return
            }
            val reference = ref("FC", "${row.orderReference}-CLOSE")
            save(row.copy(state = "EXIT_PENDING", lastUpdatedMs = System.currentTimeMillis()))
            apiFactory.groww.placeOrder(bearer(token), OrderCreateRequest(
                tradingSymbol = row.symbol, quantity = -refreshed, product = "MIS",
                transactionType = "BUY", orderReferenceId = reference
            )).requirePayload("Fixed Short BUY-to-cover squareoff")
            auditLogger.log("FIXED_SHORT", "TIMED_BUY_COVER_DISPATCHED", mapOf(
                "symbol" to row.symbol, "quantity" to -refreshed
            ))
        } catch (t: Throwable) {
            urgent(row.symbol, "MIS short not confirmed closed at 3 PM; check Groww immediately")
            auditLogger.log("FIXED_SHORT", "URGENT_INTRADAY_SQUAREOFF_UNCONFIRMED", mapOf(
                "symbol" to row.symbol, "error" to (t.message ?: "")
            ))
        }
    }
}
