package com.multify.traderpro.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** Transport-only: two LTP responses in flight; original batch order and ranking are preserved. */
object FixedDiscoveryWindowScheduler {
    const val WINDOW_SIZE = 2

    suspend fun <T, R> fetchOrdered(
        chunks: List<T>, windowStart: Int, loader: suspend (Int, T) -> R
    ): List<Pair<Int, R>> = coroutineScope {
        require(windowStart >= 0 && windowStart < chunks.size)
        val until = minOf(chunks.size, windowStart + WINDOW_SIZE)
        (windowStart until until).map { index ->
            async { index to loader(index, chunks[index]) }
        }.awaitAll()
    }
}
