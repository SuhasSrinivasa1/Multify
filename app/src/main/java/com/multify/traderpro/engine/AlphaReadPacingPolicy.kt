package com.multify.traderpro.engine

/**
 * Pure, account-local HTTP GET pacing. Keeps published Fixed scoring, selection,
 * BUY, SELL and broker-side protective GTT rules untouched.
 *
 * Broker 429 responses have occurred below our aggregate category budgets.
 * Separate lanes therefore space Alpha quote starts and routine account reads
 * independently of the existing Live/Historical category governor.
 */
object AlphaReadPacingPolicy {
    enum class Lane { QUOTE, ACCOUNT_READ }

    data class State(val gapMs: Long, val lastAdjustmentAtMs: Long)

    fun lane(method: String, path: String): Lane? {
        if (method != "GET") return null
        return when {
            path == "/v1/live-data/quote" -> Lane.QUOTE
            path == "/v1/holdings/user" ||
                path == "/v1/positions/user" ||
                path == "/v1/order-advance/list" ||
                path.startsWith("/v1/order-advance/status/") -> Lane.ACCOUNT_READ
            else -> null
        }
    }

    fun startGapMs(lane: Lane): Long = when (lane) {
        Lane.QUOTE -> 850L
        Lane.ACCOUNT_READ -> 1_000L
    }

    fun waitMs(nowMs: Long, lastGrantedAtMs: Long, gapMs: Long): Long =
        if (lastGrantedAtMs <= 0L) 0L
        else (lastGrantedAtMs + gapMs - nowMs).coerceAtLeast(0L)

    fun on429(lane: Lane, state: State, nowMs: Long): State {
        val floor = when (lane) {
            Lane.QUOTE -> 1_800L
            Lane.ACCOUNT_READ -> 2_000L
        }
        return State(maxOf(floor, state.gapMs * 2L).coerceAtMost(8_000L), nowMs)
    }

    fun onSuccess(lane: Lane, state: State, nowMs: Long): State {
        // Do not immediately accelerate after one successful request.
        if (nowMs - state.lastAdjustmentAtMs < 120_000L) return state
        val floor = when (lane) {
            Lane.QUOTE -> 650L
            Lane.ACCOUNT_READ -> 900L
        }
        return State((state.gapMs - 50L).coerceAtLeast(floor), nowMs)
    }
}
