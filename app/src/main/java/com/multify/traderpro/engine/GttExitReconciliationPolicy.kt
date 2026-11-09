package com.multify.traderpro.engine

import com.multify.traderpro.data.network.GrowwOrderBookRow
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Pure read-only broker-exit matching. Requires the exact app GTT reference; no symbol/qty guess. */
object GttExitReconciliationPolicy {
    private val terminal = setOf("EXECUTED", "COMPLETED")

    private val india = ZoneId.of("Asia/Kolkata")
    private const val CLOCK_TOLERANCE_MS = 30_000L
    private const val MAX_GTT_CHILD_PLACEMENT_DELAY_MS = 5L * 60_000L

    data class ConfirmedExit(val order: GrowwOrderBookRow, val evidence: String)

    /**
     * Groww's smart-order status schema does not guarantee a returned reference_id.
     * Reconstruct only the exact app reference used when the protection was created.
     * A hard-stop GTT can later become TRAILING_GTT by modification without changing ID/ref.
     */
    fun knownAppProtectionReferences(positionId: Long, symbol: String, openedAtMs: Long): Set<String> {
        if (positionId <= 0L || symbol.isBlank() || openedAtMs <= 0L) return emptySet()
        val seed = "$positionId-$symbol-$openedAtMs"
        val hash = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(14)
        return setOf("MHS$hash", "MTG$hash", "MTR$hash")
    }

    private fun parseBrokerTimestamp(value: String?): Long? {
        val text = value?.trim()?.replace(' ', 'T')?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(text).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text).atZone(india).toInstant().toEpochMilli() }.getOrNull()
    }

    /**
     * Read-only fallback for a completed GTT when the parent omits reference_id.
     * Prefer a uniquely filled SELL bearing our deterministic protection reference.
     * Some Groww child SELL rows use a different reference: in that case require
     * confirmed flat broker inventory, one uniquely identifiable full same-day SELL,
     * and a parseable placement timestamp close to the parent GTT trigger.
     * Never accept a GTT trigger, a quote, or symbol/quantity alone as a fill.
     */
    fun resolveConfirmedTriggeredExit(
        orders: List<GrowwOrderBookRow>, symbol: String, quantity: Int,
        openingOrderId: String, positionId: Long, openedAtMs: Long,
        parentReferenceId: String?, parentTriggeredAt: String?,
        brokerQuantity: Int, observedAtMs: Long
    ): ConfirmedExit? {
        if (quantity <= 0 || brokerQuantity != 0 || openingOrderId.isBlank()) return null
        val refs = knownAppProtectionReferences(positionId, symbol, openedAtMs)
        if (refs.isEmpty()) return null
        // A returned parent reference must not identify a different GTT or a manual one.
        if (!parentReferenceId.isNullOrBlank() && parentReferenceId !in refs) return null

        val full = orders.filter {
            it.tradingSymbol.equals(symbol, true) &&
                it.transactionType.equals("SELL", true) &&
                it.product.equals("CNC", true) &&
                it.quantity == quantity && it.filledQuantity == quantity &&
                it.orderStatus.uppercase() in terminal &&
                it.growwOrderId.isNotBlank() &&
                !it.growwOrderId.equals(openingOrderId, true) &&
                (it.averageFillPrice ?: 0.0).let { price -> price.isFinite() && price > 0.0 }
        }.distinctBy { it.growwOrderId }

        val exact = full.filter { it.orderReferenceId in refs }
        if (exact.size == 1) return ConfirmedExit(exact.single(), "EXACT_APP_GTT_REFERENCE")
        if (exact.isNotEmpty()) return null // ambiguous exact app reference

        val triggeredAtMs = parseBrokerTimestamp(parentTriggeredAt) ?: return null
        if (triggeredAtMs < openedAtMs - CLOCK_TOLERANCE_MS ||
            triggeredAtMs > observedAtMs + CLOCK_TOLERANCE_MS) return null
        val lower = maxOf(openedAtMs, triggeredAtMs) - CLOCK_TOLERANCE_MS
        val upper = minOf(observedAtMs + CLOCK_TOLERANCE_MS,
            triggeredAtMs + MAX_GTT_CHILD_PLACEMENT_DELAY_MS)
        // Even a partial competing same-symbol SELL makes broker-child attribution unclear.
        val competingSells = orders.filter { row ->
            row.tradingSymbol.equals(symbol, true) && row.transactionType.equals("SELL", true) &&
                row.product.equals("CNC", true) && row.growwOrderId.isNotBlank() &&
                parseBrokerTimestamp(row.createdAt)?.let { it in lower..upper } == true
        }.distinctBy { it.growwOrderId }
        if (competingSells.size != 1) return null
        val candidate = competingSells.single()
        if (candidate !in full) return null
        // Another app's GTT reference must never be attributed to this position.
        if (AppPositionOwnershipPolicy.isAppProtectionReference(candidate.orderReferenceId) &&
            candidate.orderReferenceId !in refs) return null
        return ConfirmedExit(candidate, "UNIQUE_EXECUTED_SELL_AFTER_GTT_TRIGGER_BROKER_FLAT")
    }

    fun shouldQuarantinePendingChild(
        mode: String, gttId: String?, currentStatus: String,
        brokerQuantity: Int, trackedQuantity: Int
    ): Boolean =
        !gttId.isNullOrBlank() && mode in setOf("TRAILING_GTT", "HARD_STOP_GTT") &&
            (currentStatus.uppercase() in setOf("TRIGGERED", "COMPLETED") || brokerQuantity != trackedQuantity)

    fun confirmedFullSells(
        orders: List<GrowwOrderBookRow>,
        symbol: String,
        quantity: Int,
        openingOrderId: String,
        expectedGttReferenceId: String
    ): List<GrowwOrderBookRow> {
        if (expectedGttReferenceId.isBlank()) return emptyList()
        return orders.filter {
            it.tradingSymbol.equals(symbol, true) &&
                it.transactionType.equals("SELL", true) &&
                it.product.equals("CNC", true) &&
                it.quantity == quantity &&
                it.filledQuantity == quantity &&
                it.orderStatus.uppercase() in terminal &&
                it.growwOrderId.isNotBlank() &&
                !it.growwOrderId.equals(openingOrderId, true) &&
                it.orderReferenceId.equals(expectedGttReferenceId, true) &&
                (it.averageFillPrice ?: 0.0).let { price -> price.isFinite() && price > 0.0 }
        }.distinctBy { it.growwOrderId }
    }

    fun uniqueConfirmedFullSell(
        orders: List<GrowwOrderBookRow>,
        symbol: String,
        quantity: Int,
        openingOrderId: String,
        expectedGttReferenceId: String
    ): GrowwOrderBookRow? =
        confirmedFullSells(orders, symbol, quantity, openingOrderId, expectedGttReferenceId).singleOrNull()

    fun identifiedPartialFills(
        orders: List<GrowwOrderBookRow>,
        symbol: String,
        quantity: Int,
        openingOrderId: String,
        expectedGttReferenceId: String
    ): List<GrowwOrderBookRow> {
        if (expectedGttReferenceId.isBlank()) return emptyList()
        return orders.filter {
            it.tradingSymbol.equals(symbol, true) &&
                it.transactionType.equals("SELL", true) &&
                it.product.equals("CNC", true) &&
                it.filledQuantity in 1 until quantity &&
                it.growwOrderId.isNotBlank() &&
                !it.growwOrderId.equals(openingOrderId, true) &&
                it.orderReferenceId.equals(expectedGttReferenceId, true)
        }.distinctBy { it.growwOrderId }
    }
}
