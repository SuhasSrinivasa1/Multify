package com.multify.traderpro.data.network

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Coalesces an identical Alpha research GET *before* requesting a local API permit.
 *
 * This is not a quote cache, a retry mechanism or a broker data source. A follower
 * receives the leader's freshly cached successful response only after the leader
 * completed. Cancellation, failed requests and timeouts fail followers closed.
 * No order/BUY/SELL/GTT, account, auth or PROTECTION quote can enter the gate.
 */
internal class AlphaResearchAdmissionGate(
    private val cache: GrowwHotDataCacheInterceptor,
    private val followerWaitMs: Long = 25_000L
) {
    internal class Pending {
        val completed = CompletableDeferred<Boolean>()
    }
    data class Ticket internal constructor(
        val key: String,
        internal val pending: Pending,
        val isLeader: Boolean
    )

    private val flights = ConcurrentHashMap<String, Pending>()

    fun enter(request: Request): Ticket? {
        val key = cache.safeResearchCoalescingKey(request) ?: return null
        val created = Pending()
        val current = flights.putIfAbsent(key, created)
        return Ticket(key, current ?: created, current == null)
    }

    suspend fun awaitLeader(ticket: Ticket): Boolean =
        withTimeoutOrNull(followerWaitMs.coerceAtLeast(1L)) {
            ticket.pending.completed.await()
        } == true

    fun finish(ticket: Ticket?, success: Boolean) {
        if (ticket == null || !ticket.isLeader) return
        ticket.pending.completed.complete(success)
        flights.remove(ticket.key, ticket.pending)
    }

    fun activeFlights(): Int = flights.size
}
