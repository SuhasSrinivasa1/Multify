package com.multify.traderpro.engine

import kotlinx.coroutines.Job

/**
 * Single-flight owner for the official Fixed scan worker.
 *
 * Both Android's service startup and the watchdog may call start concurrently.
 * The job must be created LAZY, published and started under the same lock.
 * This avoids the historical duplicate Fixed worker / 53-batch sweep race.
 *
 * No stock-selection, recommendation, broker-budget or execution decisions.
 */
internal class FixedSingleWorkerStartGate {
    private val lock = Any()
    private var running: Job? = null

    fun startIfAbsent(createLazyJob: () -> Job): Job? = synchronized(lock) {
        if (running?.isActive == true) return@synchronized null
        val created = createLazyJob()
        running = created
        created.start()
        created
    }

    fun isRunning(): Boolean = synchronized(lock) { running?.isActive == true }

    fun cancel() = synchronized(lock) {
        running?.cancel()
        running = null
    }
}
