package com.multify.traderpro.engine

/** Discretionary Alpha research backoff after broker 429; never blocks protection, orders or Fixed. */
object ResearchRateLimitQuietPolicy {
    const val QUIET_MS = 5L * 60_000L
    fun canRun(nowMs: Long, last429AtMs: Long): Boolean =
        last429AtMs <= 0L || nowMs < last429AtMs || nowMs - last429AtMs >= QUIET_MS
}
