package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.engine.GrowwApiBudgetPolicy
import java.util.ArrayDeque
import java.util.EnumMap
import java.util.concurrent.atomic.AtomicLong

/** Process-wide local accounting of every Groww network request made by this app. */
object GrowwApiUsageLedger {
    enum class Category { AUTH, ORDERS, LIVE_DATA, NON_TRADING, HISTORICAL }

    data class CategorySnapshot(
        val category: Category,
        val usedLastSecond: Int,
        val usedLastMinute: Int,
        val appPerSecondLimit: Int,
        val appPerMinuteLimit: Int,
        val hardPerSecondLimit: Int?,
        val hardPerMinuteLimit: Int?,
        val totalRequests: Long,
        val rateLimitResponses: Long,
        val networkMs: Long
    ) {
        val minuteHeadroom: Int get() = (appPerMinuteLimit - usedLastMinute).coerceAtLeast(0)
        val minuteUtilizationPct: Int get() =
            if (appPerMinuteLimit <= 0) 0 else ((usedLastMinute * 100.0) / appPerMinuteLimit).toInt().coerceIn(0, 100)
    }

    data class Snapshot(val capturedAtElapsedMs: Long, val categories: List<CategorySnapshot>) {
        fun compact(): String {
            val live = categories.first { it.category == Category.LIVE_DATA }
            val orders = categories.first { it.category == Category.ORDERS }
            val historical = categories.first { it.category == Category.HISTORICAL }
            return "Live ${live.usedLastMinute}/${live.appPerMinuteLimit}m · " +
                "Hist ${historical.usedLastMinute}/${historical.appPerMinuteLimit}m · " +
                "Orders ${orders.usedLastMinute}/${orders.appPerMinuteLimit}m"
        }
    }

    private data class State(
        val second: ArrayDeque<Long> = ArrayDeque(),
        val minute: ArrayDeque<Long> = ArrayDeque(),
        var totalRequests: Long = 0,
        var rateLimitResponses: Long = 0,
        var networkMs: Long = 0
    )

    private val lock = Any()
    private val states = EnumMap<Category, State>(Category::class.java).apply {
        Category.values().forEach { put(it, State()) }
    }
    private val cacheHits = AtomicLong(0)
    private val cacheMisses = AtomicLong(0)

    fun recordRequest(category: Category, nowMs: Long = SystemClock.elapsedRealtime()) = synchronized(lock) {
        val state = states.getValue(category)
        prune(state, nowMs)
        state.second.addLast(nowMs)
        state.minute.addLast(nowMs)
        state.totalRequests++
    }

    fun recordResponse(category: Category, elapsedMs: Long, code: Int) = synchronized(lock) {
        val state = states.getValue(category)
        state.networkMs += elapsedMs.coerceAtLeast(0L)
        if (code == 429) state.rateLimitResponses++
    }

    fun recordCacheHit() { cacheHits.incrementAndGet() }
    fun recordCacheMiss() { cacheMisses.incrementAndGet() }
    fun cacheHitCount(): Long = cacheHits.get()
    fun cacheMissCount(): Long = cacheMisses.get()

    fun snapshot(nowMs: Long = SystemClock.elapsedRealtime()): Snapshot = synchronized(lock) {
        Snapshot(nowMs, Category.values().map { category ->
            val state = states.getValue(category)
            prune(state, nowMs)
            val limits = limits(category)
            CategorySnapshot(
                category = category,
                usedLastSecond = state.second.size,
                usedLastMinute = state.minute.size,
                appPerSecondLimit = limits.first,
                appPerMinuteLimit = limits.second,
                hardPerSecondLimit = hardLimits(category).first,
                hardPerMinuteLimit = hardLimits(category).second,
                totalRequests = state.totalRequests,
                rateLimitResponses = state.rateLimitResponses,
                networkMs = state.networkMs
            )
        })
    }

    private fun prune(state: State, nowMs: Long) {
        while (state.second.isNotEmpty() && state.second.peekFirst() <= nowMs - 1_000L) state.second.removeFirst()
        while (state.minute.isNotEmpty() && state.minute.peekFirst() <= nowMs - 60_000L) state.minute.removeFirst()
    }

    private fun limits(category: Category): Pair<Int, Int> = when (category) {
        Category.AUTH -> GrowwApiBudgetPolicy.APP_AUTH_PER_SECOND to GrowwApiBudgetPolicy.APP_AUTH_PER_MINUTE
        Category.ORDERS -> GrowwApiBudgetPolicy.APP_ORDER_PER_SECOND to GrowwApiBudgetPolicy.APP_ORDER_PER_MINUTE
        Category.LIVE_DATA -> GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_SECOND to GrowwApiBudgetPolicy.APP_LIVE_DATA_PER_MINUTE
        Category.NON_TRADING -> GrowwApiBudgetPolicy.APP_NON_TRADING_PER_SECOND to GrowwApiBudgetPolicy.APP_NON_TRADING_PER_MINUTE
        Category.HISTORICAL -> GrowwApiBudgetPolicy.APP_HISTORICAL_PER_SECOND to GrowwApiBudgetPolicy.APP_HISTORICAL_PER_MINUTE
    }

    private fun hardLimits(category: Category): Pair<Int?, Int?> = when (category) {
        Category.AUTH -> GrowwApiBudgetPolicy.AUTH_HARD_PER_SECOND to GrowwApiBudgetPolicy.AUTH_HARD_PER_MINUTE
        Category.ORDERS -> GrowwApiBudgetPolicy.ORDER_HARD_PER_SECOND to GrowwApiBudgetPolicy.ORDER_HARD_PER_MINUTE
        Category.LIVE_DATA -> GrowwApiBudgetPolicy.LIVE_DATA_HARD_PER_SECOND to GrowwApiBudgetPolicy.LIVE_DATA_HARD_PER_MINUTE
        Category.NON_TRADING -> GrowwApiBudgetPolicy.NON_TRADING_HARD_PER_SECOND to GrowwApiBudgetPolicy.NON_TRADING_HARD_PER_MINUTE
        Category.HISTORICAL -> null to null
    }
}
