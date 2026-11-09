package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.data.logging.AuditLogger
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Observability-only, independent from all Alpha/Beta governors.
 * Counts outbound HTTP attempts AFTER cache/admission; never consumes a broker permit.
 * No URLs with query strings, authorization values, payloads or credentials are stored.
 */
object GrowwHttpTraceLedger {
    data class Ticket(
        val id: Long, val account: String, val category: String, val endpoint: String,
        val method: String, val startedAtMs: Long, val startedElapsedMs: Long,
        val accountUsed1s: Int, val accountUsed60s: Int,
        val categoryUsed1s: Int, val categoryUsed60s: Int,
        val endpointUsed1s: Int, val endpointUsed60s: Int,
        val overlappingFixedScanId: String?
    )
    data class Counters(val requests: Long, val failures: Long, val rateLimits: Long)
    private data class Window(
        val sec: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque()
    )
    private val lock = Any()
    private val serial = AtomicLong()
    private val byAccount = mutableMapOf<String, Window>()
    private val byCategory = mutableMapOf<String, Window>()
    private val byEndpoint = mutableMapOf<String, Window>()
    private val totals = mutableMapOf<String, Long>()
    private val failures = mutableMapOf<String, Long>()
    private val rateLimits = mutableMapOf<String, Long>()
    @Volatile private var currentFixedScan: String? = null

    fun beginFixedScan(id: String) { currentFixedScan = id }
    fun endFixedScan(id: String) { if (currentFixedScan == id) currentFixedScan = null }

    private fun trim(window: Window, now: Long) {
        while (window.sec.isNotEmpty() && window.sec.first() <= now - 1000L) window.sec.removeFirst()
        while (window.minute.isNotEmpty() && window.minute.first() <= now - 60000L) window.minute.removeFirst()
    }
    private fun bump(window: Window, now: Long): Pair<Int, Int> {
        trim(window, now)
        window.sec.addLast(now); window.minute.addLast(now)
        return window.sec.size to window.minute.size
    }
    private fun snapshot(window: Window?, now: Long): Pair<Int, Int> {
        if (window == null) return 0 to 0
        trim(window, now)
        return window.sec.size to window.minute.size
    }
    fun classify(path: String): String = when {
        path.startsWith("/v1/live-data/") -> "LIVE_DATA"
        path.startsWith("/v1/historical/") -> "HISTORICAL"
        path.startsWith("/v1/order") || path.contains("/orders") || path.contains("/gtt") -> "ORDERS"
        path.contains("/token/") -> "AUTH"
        else -> "NON_TRADING"
    }
    fun start(account: String, method: String, endpoint: String): Ticket = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val category = classify(endpoint)
        val accountKey = account
        val categoryKey = account + "/" + category
        val endpointKey = account + "/" + endpoint
        val a = bump(byAccount.getOrPut(accountKey) { Window() }, now)
        val c = bump(byCategory.getOrPut(categoryKey) { Window() }, now)
        val e = bump(byEndpoint.getOrPut(endpointKey) { Window() }, now)
        totals[categoryKey] = (totals[categoryKey] ?: 0L) + 1L
        Ticket(serial.incrementAndGet(), account, category, endpoint, method,
            System.currentTimeMillis(), now, a.first, a.second,
            c.first, c.second, e.first, e.second, currentFixedScan)
    }
    fun counters(account: String, category: String): Counters = synchronized(lock) {
        val key = account + "/" + category
        Counters(totals[key] ?: 0L, failures[key] ?: 0L, rateLimits[key] ?: 0L)
    }
    fun summary(): Map<String, Map<String, Any>> = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        listOf("ALPHA", "BETA").associateWith { account ->
            val a = snapshot(byAccount[account], now)
            mapOf(
                "all_requests_1s" to a.first, "all_requests_60s" to a.second,
                "categories" to listOf("LIVE_DATA","HISTORICAL","ORDERS","NON_TRADING","AUTH")
                    .associateWith { category ->
                        val key = account + "/" + category
                        val w = snapshot(byCategory[key], now)
                        mapOf(
                            "actual_http_1s" to w.first, "actual_http_60s" to w.second,
                            "total" to (totals[key] ?: 0L),
                            "failures" to (failures[key] ?: 0L),
                            "http_429" to (rateLimits[key] ?: 0L)
                        )
                    }
            )
        }
    }
    fun finish(ticket: Ticket, audit: AuditLogger, httpCode: Int?,
               retryAfter: String? = null, failureType: String? = null) {
        val now = SystemClock.elapsedRealtime()
        val recent = synchronized(lock) {
            val key = ticket.account + "/" + ticket.category
            val accountNow = snapshot(byAccount[ticket.account], now)
            val categoryNow = snapshot(byCategory[key], now)
            val endpointNow = snapshot(byEndpoint[ticket.account + "/" + ticket.endpoint], now)
            if (httpCode == null || httpCode >= 400) failures[key] = (failures[key] ?: 0L) + 1L
            if (httpCode == 429) rateLimits[key] = (rateLimits[key] ?: 0L) + 1L
            listOf(accountNow.first,accountNow.second,categoryNow.first,categoryNow.second,
                endpointNow.first,endpointNow.second)
        }
        audit.logAsync("API_HTTP_TRACE", "REQUEST_FINISHED", mapOf(
            "request_id" to ticket.id,
            "account" to ticket.account, "category" to ticket.category,
            "method" to ticket.method, "endpoint" to ticket.endpoint,
            "started_at_ms" to ticket.startedAtMs,
            "completed_at_ms" to System.currentTimeMillis(),
            "network_elapsed_ms" to (now - ticket.startedElapsedMs).coerceAtLeast(0L),
            "http_status" to httpCode,
            "origin" to (if (httpCode == null) "TRANSPORT_EXCEPTION" else "BROKER_HTTP_RESPONSE"),
            "exception_type" to failureType,
            "retry_after_raw" to retryAfter,
            "account_1s_at_start" to ticket.accountUsed1s,
            "account_60s_at_start" to ticket.accountUsed60s,
            "category_1s_at_start" to ticket.categoryUsed1s,
            "category_60s_at_start" to ticket.categoryUsed60s,
            "endpoint_1s_at_start" to ticket.endpointUsed1s,
            "endpoint_60s_at_start" to ticket.endpointUsed60s,
            "account_1s_at_response" to recent[0],
            "account_60s_at_response" to recent[1],
            "category_1s_at_response" to recent[2],
            "category_60s_at_response" to recent[3],
            "endpoint_1s_at_response" to recent[4],
            "endpoint_60s_at_response" to recent[5],
            "fixed_scan_overlap_id" to ticket.overlappingFixedScanId,
            "scan_attribution" to "TIME_OVERLAP_ONLY_NOT_EXCLUSIVE",
            "broker_quota_proven" to false
        ))
    }
    fun localBlock(audit: AuditLogger, account: String, category: String,
                   endpoint: String, reason: String) {
        audit.logAsync("API_HTTP_TRACE", "LOCAL_ADMISSION_BLOCK", mapOf(
            "account" to account, "category" to category, "endpoint" to endpoint,
            "reason" to reason, "actual_http_dispatched" to false,
            "broker_rejection" to false,
            "timestamp_ms" to System.currentTimeMillis(),
            "recent_usage" to summary()[account]
        ))
    }
}
