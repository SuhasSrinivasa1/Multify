package com.multify.traderpro.engine

import java.util.ArrayDeque

/**
 * Research-only Alpha endpoint pressure measurements in 1s/60s rolling windows.
 * Counts requests actually admitted to OkHttp (not locally blocked or
 * queued); no new requests, no new rate limits or cooldown override.
 * Empirical 429 thresholds are observations, not proof of Groww quotas.
 */
internal class V9EndpointPressureWindow {
    data class Snapshot(
        val endpoint1s: Int, val endpoint60s: Int,
        val category1s: Int, val category60s: Int,
        val alphaAll60s: Int, val emitMinuteSample: Boolean
    )
    private val endpointStarts = mutableMapOf<String, ArrayDeque<Long>>()
    private val categoryStarts = mutableMapOf<String, ArrayDeque<Long>>()
    private val allStarts = ArrayDeque<Long>()
    private val lastSampleMs = mutableMapOf<String, Long>()

    private fun record(queue: ArrayDeque<Long>, now: Long): Pair<Int, Int> {
        while (queue.isNotEmpty() && now - queue.first > 60_000L) queue.removeFirst()
        queue.addLast(now)
        val inSecond = queue.count { now - it < 1_000L }
        return inSecond to queue.size
    }

    @Synchronized fun admitted(
        method: String, path: String, category: String, nowElapsedMs: Long
    ): Snapshot {
        val key = method.uppercase() + " " + path
        val e = record(endpointStarts.getOrPut(key) { ArrayDeque() }, nowElapsedMs)
        val c = record(categoryStarts.getOrPut(category) { ArrayDeque() }, nowElapsedMs)
        val all = record(allStarts, nowElapsedMs)
        val prior = lastSampleMs[key]
        val sample = prior == null || nowElapsedMs - prior >= 60_000L
        if (sample) lastSampleMs[key] = nowElapsedMs
        return Snapshot(e.first, e.second, c.first, c.second, all.second, sample)
    }
}
