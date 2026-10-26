package com.multify.traderpro.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class FixedDiscoveryWindowSchedulerTest {
    @Test fun preservesOriginalBatchOrderEvenIfSecondResponseFinishesFirst() = runBlocking {
        val active = AtomicInteger()
        val peak = AtomicInteger()
        val xs = listOf("A", "B", "C", "D")
        val results = mutableListOf<Pair<Int, String>>()
        for (start in xs.indices step FixedDiscoveryWindowScheduler.WINDOW_SIZE) {
            results.addAll(FixedDiscoveryWindowScheduler.fetchOrdered(xs, start) { index, symbol ->
                val count = active.incrementAndGet()
                peak.updateAndGet { maxOf(it, count) }
                delay(if (index % 2 == 0) 30L else 5L)
                active.decrementAndGet()
                symbol
            })
        }
        assertEquals(listOf(0 to "A", 1 to "B", 2 to "C", 3 to "D"), results)
        assertEquals(2, peak.get())
    }

    @Test fun lastWindowContainsOnlyRemainingBatch() = runBlocking {
        val values = listOf(10, 20, 30)
        assertEquals(listOf(2 to 30), FixedDiscoveryWindowScheduler.fetchOrdered(values, 2) { _, n -> n })
    }
}
