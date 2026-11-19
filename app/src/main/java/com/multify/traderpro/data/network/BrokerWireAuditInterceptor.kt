package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.data.logging.AuditLogger
import com.multify.traderpro.engine.DualMinuteAdmissionPolicy
import okhttp3.Interceptor
import okhttp3.Response
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Network-interceptor observability. Counts EACH actual OkHttp network exchange
 * (not only one logical Retrofit request), after cache/admission. Never stores
 * query parameters, tokens, payloads or customer identifiers.
 */
internal class BrokerWireAuditInterceptor(
    private val account: String, private val audit: AuditLogger
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val endpoint = req.url.encodedPath
        val ticket = BrokerWireAttemptLedger.start(account, req.method, endpoint)
        return try {
            val response = chain.proceed(req)
            BrokerWireAttemptLedger.finish(ticket, response.code, audit)
            response
        } catch (e: Exception) {
            BrokerWireAttemptLedger.finish(ticket, null, audit,
                e.javaClass.simpleName)
            throw e
        }
    }
}

internal object BrokerWireAttemptLedger {
    data class Ticket(
        val id: Long, val account: String, val method: String,
        val endpoint: String, val category: String, val startedAtMs: Long,
        val monotonicStartedMs: Long, val categoryRolling60s: Int,
        val categoryCalendarMinute: Int, val endpointRolling60s: Int,
        val accountRolling60s: Int, val calendarMinuteId: Long
    )
    private data class State(
        val minute: ArrayDeque<Long> = ArrayDeque(),
        var calendar: DualMinuteAdmissionPolicy.CalendarState =
            DualMinuteAdmissionPolicy.CalendarState(),
        var total: Long = 0, var status429: Long = 0L,
        var failures: Long = 0L
    )
    private val lock = Any()
    private val serial = AtomicLong()
    private val accounts = mutableMapOf<String, State>()
    private val categories = mutableMapOf<String, State>()
    private val endpoints = mutableMapOf<String, State>()

    private fun advance(s: State, epoch: Long, elapsed: Long) {
        while (s.minute.isNotEmpty() && s.minute.first() <= elapsed-60_000L)
            s.minute.removeFirst()
        s.minute.addLast(elapsed)
        s.calendar = DualMinuteAdmissionPolicy.reserve(s.calendar, epoch)
        s.total++
    }
    fun start(account: String, method: String, endpoint: String): Ticket {
        val epoch = System.currentTimeMillis()
        val now = SystemClock.elapsedRealtime()
        val category = GrowwHttpTraceLedger.classify(endpoint)
        return synchronized(lock) {
            val a = accounts.getOrPut(account) { State() }
            val c = categories.getOrPut("$account/$category") { State() }
            val e = endpoints.getOrPut("$account/$endpoint") { State() }
            advance(a,epoch,now); advance(c,epoch,now); advance(e,epoch,now)
            Ticket(serial.incrementAndGet(),account,method,endpoint,category,epoch,now,
                c.minute.size,c.calendar.used,e.minute.size,a.minute.size,
                DualMinuteAdmissionPolicy.minuteId(epoch))
        }
    }
    fun finish(t: Ticket, status: Int?, audit: AuditLogger,
               exception: String? = null) {
        synchronized(lock) {
            val categoryState = categories.getValue(t.account + "/" + t.category)
            val accountState = accounts.getValue(t.account)
            val endpointState = endpoints.getValue(t.account + "/" + t.endpoint)
            // Keep account, category and endpoint roll-ups consistent with the
            // actual wire event. Previously only category counters were updated,
            // so account/endpoint summaries falsely reported zero 429s/failures.
            listOf(categoryState, accountState, endpointState).forEach { state ->
                if (status == 429) state.status429++
                if (status == null || status >= 400) state.failures++
            }
        }
        audit.logAsync("API_WIRE", "ACTUAL_WIRE_ATTEMPT_V909", mapOf(
            "wire_attempt_id" to t.id, "account" to t.account,
            "category" to t.category, "method" to t.method,
            "endpoint" to t.endpoint, "started_at_ms" to t.startedAtMs,
            "finished_at_ms" to System.currentTimeMillis(),
            "duration_ms" to (SystemClock.elapsedRealtime()-t.monotonicStartedMs).coerceAtLeast(0),
            "broker_http_status" to status, "exception_type" to exception,
            "category_rolling_60s_including_this" to t.categoryRolling60s,
            "category_fixed_calendar_minute_including_this" to t.categoryCalendarMinute,
            "account_rolling_60s_including_this" to t.accountRolling60s,
            "endpoint_rolling_60s_including_this" to t.endpointRolling60s,
            "calendar_minute_id_utc" to t.calendarMinuteId,
            "not_a_logical_call_count" to true,
            "query_token_and_body_suppressed" to true
        ))
    }

    fun summary(): Map<String, Any> = synchronized(lock) {
        val epoch = System.currentTimeMillis()
        val now = SystemClock.elapsedRealtime()
        fun counter(s: State?): Map<String, Any> {
            if (s==null) return mapOf("total" to 0, "actual_wire_rolling_60s" to 0,
                "actual_wire_fixed_minute" to 0, "http_429" to 0)
            val rolling=s.minute.count { it > now-60_000L }
            return mapOf("total" to s.total,
                "actual_wire_rolling_60s" to rolling,
                "actual_wire_fixed_minute" to DualMinuteAdmissionPolicy
                    .normalize(s.calendar,epoch).used,
                "http_429" to s.status429, "failure_count" to s.failures)
        }
        mapOf(
            "source" to "ACTUAL_NETWORK_INTERCEPTOR_NOT_RETROFIT_LOGICAL_CALLS",
            "captured_at_ms" to epoch,
            "minute_id_utc" to DualMinuteAdmissionPolicy.minuteId(epoch),
            "accounts" to listOf("ALPHA","BETA").associateWith { account ->
                mapOf("all" to counter(accounts[account]),
                    "by_category" to listOf("LIVE_DATA","HISTORICAL","ORDERS","NON_TRADING","AUTH")
                        .associateWith { counter(categories["$account/$it"]) },
                    "by_endpoint" to endpoints.filterKeys { it.startsWith("$account/") }
                        .mapKeys { it.key.removePrefix("$account/") }
                        .mapValues { counter(it.value) })
            })
    }
}
