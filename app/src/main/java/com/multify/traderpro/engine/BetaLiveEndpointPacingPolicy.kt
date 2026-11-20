package com.multify.traderpro.engine

/**
 * Per-endpoint adaptive pacing for Beta's read-only LIVE_DATA lane.
 * Rolling/calendar 40-per-minute and 6-per-second admission remain shared
 * and unchanged in BetaResearchGate. A quote 429 must not inflate the LTP
 * spacing when the broker has not rejected LTP.
 */
object BetaLiveEndpointPacingPolicy {
    enum class Endpoint { LTP, QUOTE }

    data class State(
        val ltp: LtpAdaptivePacingPolicy.State,
        val quote: LtpAdaptivePacingPolicy.State
    )

    fun initial(nowMs: Long): State = State(
        LtpAdaptivePacingPolicy.State(LtpAdaptivePacingPolicy.START_GAP_MS, nowMs),
        LtpAdaptivePacingPolicy.State(LtpAdaptivePacingPolicy.START_GAP_MS, nowMs)
    )

    fun gapMs(state: State, endpoint: Endpoint): Long = when (endpoint) {
        Endpoint.LTP -> state.ltp.gapMs
        Endpoint.QUOTE -> state.quote.gapMs
    }

    fun lastAdjustmentAtMs(state: State, endpoint: Endpoint): Long = when (endpoint) {
        Endpoint.LTP -> state.ltp.lastAdjustmentAtMs
        Endpoint.QUOTE -> state.quote.lastAdjustmentAtMs
    }

    fun onSuccess(state: State, endpoint: Endpoint, nowMs: Long): State = when (endpoint) {
        Endpoint.LTP -> state.copy(ltp = LtpAdaptivePacingPolicy.onSuccess(state.ltp, nowMs))
        Endpoint.QUOTE -> state.copy(quote = LtpAdaptivePacingPolicy.onSuccess(state.quote, nowMs))
    }

    fun on429(state: State, endpoint: Endpoint, nowMs: Long): State = when (endpoint) {
        Endpoint.LTP -> state.copy(ltp = LtpAdaptivePacingPolicy.on429(state.ltp, nowMs))
        Endpoint.QUOTE -> state.copy(quote = LtpAdaptivePacingPolicy.on429(state.quote, nowMs))
    }
}
