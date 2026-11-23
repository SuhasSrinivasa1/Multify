package com.multify.traderpro.data.network
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.PriorityQueue
/** Beta-only priority permit: lower rank first, FIFO within rank, never preempts an active read. */
internal class BetaPriorityPermitQueue {
    private data class Waiter(val priority: Int, val sequence: Long, val granted: CompletableDeferred<Unit>)
    private val lock = Mutex()
    private val waiting = PriorityQueue<Waiter>(compareBy<Waiter> { it.priority }.thenBy { it.sequence })
    private var occupied = false
    private var nextSequence = 0L
    suspend fun <T> withPermit(priority: Int, block: suspend () -> T): T {
        val waiter = lock.withLock {
            val next = Waiter(priority, nextSequence++, CompletableDeferred())
            if (!occupied) { occupied = true; next.granted.complete(Unit) } else waiting.add(next)
            next
        }
        try { waiter.granted.await() }
        catch (cancelled: CancellationException) {
            lock.withLock { if (!waiting.remove(waiter)) releaseLocked() }
            throw cancelled
        }
        try { return block() } finally { lock.withLock { releaseLocked() } }
    }
    internal suspend fun queuedCount(): Int = lock.withLock { waiting.size }
    private fun releaseLocked() {
        val next = waiting.poll()
        if (next == null) occupied = false else next.granted.complete(Unit)
    }
}
