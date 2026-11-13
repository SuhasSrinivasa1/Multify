package com.multify.traderpro.engine

import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FixedSingleWorkerStartGateTest {
    @Test fun concurrentStartupIsExactlyOnceUntilWorkerTerminates() {
        val gate = FixedSingleWorkerStartGate()
        val starts = AtomicInteger(0)
        val threads = 24
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            repeat(threads) {
                pool.execute {
                    ready.countDown()
                    go.await()
                    gate.startIfAbsent {
                        starts.incrementAndGet()
                        Job()
                    }
                    done.countDown()
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            go.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals(1, starts.get())
            assertTrue(gate.isRunning())
            assertNull(gate.startIfAbsent { Job() })
            gate.cancel()
            assertFalse(gate.isRunning())
            assertNotNull(gate.startIfAbsent {
                starts.incrementAndGet()
                Job()
            })
            assertEquals(2, starts.get())
        } finally {
            gate.cancel()
            pool.shutdownNow()
        }
    }
}
