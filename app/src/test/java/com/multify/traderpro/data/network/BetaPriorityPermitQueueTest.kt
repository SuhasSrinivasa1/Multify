package com.multify.traderpro.data.network
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
class BetaPriorityPermitQueueTest {
    @Test fun discoveryOvertakesQueuedDetailWithoutPreemptingActiveRead() = runBlocking {
        val queue = BetaPriorityPermitQueue()
        val activeEntered = CompletableDeferred<Unit>()
        val releaseActive = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val active = launch { queue.withPermit(10) { order += "active"; activeEntered.complete(Unit); releaseActive.await() } }
        activeEntered.await()
        val detail = launch { queue.withPermit(20) { order += "detail" } }
        while (queue.queuedCount() != 1) yield()
        val discovery = launch { queue.withPermit(0) { order += "discovery" } }
        while (queue.queuedCount() != 2) yield()
        releaseActive.complete(Unit)
        joinAll(active, detail, discovery)
        assertEquals(listOf("active", "discovery", "detail"), order)
    }
    @Test fun cancelledQueuedRequestDoesNotLeakPermitOrBlockNextRead() = runBlocking {
        val queue = BetaPriorityPermitQueue()
        val activeEntered = CompletableDeferred<Unit>()
        val releaseActive = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        val active = launch { queue.withPermit(10) { order += "active"; activeEntered.complete(Unit); releaseActive.await() } }
        activeEntered.await()
        val cancelled = launch { queue.withPermit(0) { order += "cancelled" } }
        while (queue.queuedCount() != 1) yield()
        cancelled.cancelAndJoin()
        assertEquals(0, queue.queuedCount())
        val next = launch { queue.withPermit(10) { order += "next" } }
        while (queue.queuedCount() != 1) yield()
        releaseActive.complete(Unit)
        joinAll(active, next)
        assertEquals(listOf("active", "next"), order)
    }
}
