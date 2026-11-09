package com.multify.traderpro.engine

import kotlinx.coroutines.CancellationException

/** A failed BUY gate must not invalidate a completed immutable Fixed discovery pass. */
object FixedScanBuyIsolationPolicy {
    suspend fun execute(buy: suspend () -> Int, onFailure: (Throwable) -> Unit): Int =
        try {
            buy()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            onFailure(failure)
            0
        }
}
