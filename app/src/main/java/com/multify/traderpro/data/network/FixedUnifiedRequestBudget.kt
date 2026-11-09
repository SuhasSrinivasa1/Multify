package com.multify.traderpro.data.network

import android.os.SystemClock
import com.multify.traderpro.engine.FixedUnifiedRequestBudgetPolicy
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * One rolling-window admission ledger for both Groww accounts.
 *
 * Alpha's order account remains Alpha-only. This object coordinates market-data
 * pressure only; each account still keeps its own endpoint gate and 429 cooldown.
 */
internal object FixedUnifiedRequestBudget {
    enum class Account { ALPHA, BETA }
    enum class Category { LIVE, HISTORICAL }

    data class Snapshot(
        val alphaLiveLastMinute: Int,
        val betaLiveLastMinute: Int,
        val sharedLiveLastMinute: Int,
        val alphaHistoricalLastMinute: Int,
        val betaHistoricalLastMinute: Int,
        val sharedHistoricalLastMinute: Int,
        val alphaLtpLastMinute: Int,
        val alphaPressureRemainingMs: Long,
        val betaPressureRemainingMs: Long,
        val sharedPressureRemainingMs: Long
    )

    private data class Windows(
        val live: ArrayDeque<Long> = ArrayDeque(),
        val historical: ArrayDeque<Long> = ArrayDeque(),
        val ltp: ArrayDeque<Long> = ArrayDeque()
    )

    private val lock = Any()
    private val alpha = Windows()
    private val beta = Windows()
    private val pendingFixed = AtomicInteger()
    private var alphaLivePressureUntilMs = 0L
    private var betaLivePressureUntilMs = 0L
    private var sharedLivePressureUntilMs = 0L
    private var alphaHistoricalPressureUntilMs = 0L
    private var betaHistoricalPressureUntilMs = 0L
    private var sharedHistoricalPressureUntilMs = 0L
    private var alphaLastLtpPermitAtMs = 0L
    private var alphaLastLivePermitAtMs = 0L
    private var alphaLastHistoricalPermitAtMs = 0L
    private var betaLastLivePermitAtMs = 0L
    private var betaLastHistoricalPermitAtMs = 0L

    suspend fun acquireAlpha(
        category: GrowwApiUsageLedger.Category,
        path: String,
        isProtection: Boolean,
        isFixed: Boolean,
        maxWaitMs: Long = 90_000L
    ) {
        val mapped = when (category) {
            GrowwApiUsageLedger.Category.LIVE_DATA -> Category.LIVE
            GrowwApiUsageLedger.Category.HISTORICAL -> Category.HISTORICAL
            else -> return
        }
        acquire(Account.ALPHA, mapped, path == "/v1/live-data/ltp", isProtection, isFixed, maxWaitMs)
    }

    suspend fun acquireBeta(
        type: BetaResearchGate.Type,
        isFixed: Boolean,
        maxWaitMs: Long = 90_000L
    ) {
        val mapped = if (type == BetaResearchGate.Type.LIVE) Category.LIVE else Category.HISTORICAL
        acquire(Account.BETA, mapped, false, false, isFixed, maxWaitMs)
    }

    fun on429(account: Account, category: Category, cooldownMs: Long) = synchronized(lock) {
        val now = SystemClock.elapsedRealtime()
        val adaptiveUntil = now + FixedUnifiedRequestBudgetPolicy.ADAPTIVE_WINDOW_MS
        when (category) {
            Category.LIVE -> {
                if (account == Account.ALPHA) alphaLivePressureUntilMs =
                    maxOf(alphaLivePressureUntilMs, adaptiveUntil)
                else betaLivePressureUntilMs = maxOf(betaLivePressureUntilMs, adaptiveUntil)
                sharedLivePressureUntilMs = maxOf(sharedLivePressureUntilMs, adaptiveUntil)
            }
            Category.HISTORICAL -> {
                if (account == Account.ALPHA) alphaHistoricalPressureUntilMs =
                    maxOf(alphaHistoricalPressureUntilMs, adaptiveUntil)
                else betaHistoricalPressureUntilMs = maxOf(betaHistoricalPressureUntilMs, adaptiveUntil)
                sharedHistoricalPressureUntilMs = maxOf(sharedHistoricalPressureUntilMs, adaptiveUntil)
            }
        }
        @Suppress("UNUSED_VARIABLE")
        val observedCooldown = cooldownMs
    }

    fun snapshot(nowMs: Long = SystemClock.elapsedRealtime()): Snapshot = synchronized(lock) {
        pruneAll(nowMs)
        Snapshot(
            alphaLiveLastMinute = alpha.live.size,
            betaLiveLastMinute = beta.live.size,
            sharedLiveLastMinute = alpha.live.size + beta.live.size,
            alphaHistoricalLastMinute = alpha.historical.size,
            betaHistoricalLastMinute = beta.historical.size,
            sharedHistoricalLastMinute = alpha.historical.size + beta.historical.size,
            alphaLtpLastMinute = alpha.ltp.size,
            alphaPressureRemainingMs = (maxOf(alphaLivePressureUntilMs, alphaHistoricalPressureUntilMs) - nowMs).coerceAtLeast(0L),
            betaPressureRemainingMs = (maxOf(betaLivePressureUntilMs, betaHistoricalPressureUntilMs) - nowMs).coerceAtLeast(0L),
            sharedPressureRemainingMs = (maxOf(sharedLivePressureUntilMs, sharedHistoricalPressureUntilMs) - nowMs).coerceAtLeast(0L)
        )
    }

    private suspend fun acquire(
        account: Account,
        category: Category,
        isLtp: Boolean,
        isProtection: Boolean,
        isFixed: Boolean,
        maxWaitMs: Long
    ) {
        val startedAtMs = SystemClock.elapsedRealtime()
        if (isFixed) pendingFixed.incrementAndGet()
        try {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                val waitMs = synchronized(lock) {
                    pruneAll(now)
                    val own = if (account == Account.ALPHA) alpha else beta
                    val sharedLiveUsed = alpha.live.size + beta.live.size
                    val sharedHistoricalUsed = alpha.historical.size + beta.historical.size
                    val livePressure = now < sharedLivePressureUntilMs
                    val historicalPressure = now < sharedHistoricalPressureUntilMs
                    val ownLivePressure = now < if (account == Account.ALPHA) alphaLivePressureUntilMs else betaLivePressureUntilMs
                    val ownHistoricalPressure = now < if (account == Account.ALPHA) alphaHistoricalPressureUntilMs else betaHistoricalPressureUntilMs

                    val ownLimit = when (category) {
                        Category.LIVE -> when (account) {
                            Account.ALPHA -> FixedUnifiedRequestBudgetPolicy.effectiveAlphaLiveLimit(ownLivePressure)
                            Account.BETA -> FixedUnifiedRequestBudgetPolicy.effectiveBetaLiveLimit(ownLivePressure)
                        }
                        Category.HISTORICAL -> when (account) {
                            Account.ALPHA -> FixedUnifiedRequestBudgetPolicy.effectiveAlphaHistoricalLimit(ownHistoricalPressure)
                            Account.BETA -> FixedUnifiedRequestBudgetPolicy.effectiveBetaHistoricalLimit(ownHistoricalPressure)
                        }
                    }
                    val sharedLimit = when (category) {
                        Category.LIVE -> FixedUnifiedRequestBudgetPolicy.effectiveSharedLiveLimit(livePressure)
                        Category.HISTORICAL -> FixedUnifiedRequestBudgetPolicy.effectiveSharedHistoricalLimit(historicalPressure)
                    }
                    val ownWindow = if (category == Category.LIVE) own.live else own.historical
                    val sharedWindow = if (category == Category.LIVE) {
                        if (alpha.live.isEmpty()) beta.live
                        else if (beta.live.isEmpty()) alpha.live
                        else if (alpha.live.peekFirst() <= beta.live.peekFirst()) alpha.live else beta.live
                    } else {
                        if (alpha.historical.isEmpty()) beta.historical
                        else if (beta.historical.isEmpty()) alpha.historical
                        else if (alpha.historical.peekFirst() <= beta.historical.peekFirst()) alpha.historical else beta.historical
                    }
                    val ownWait = if (isProtection && account == Account.ALPHA && category == Category.LIVE) 0L
                        else FixedUnifiedRequestBudgetPolicy.windowWaitMs(
                            now, ownWindow.size, ownLimit, ownWindow.peekFirstOrNull()
                        )
                    val sharedWait = if (isProtection && account == Account.ALPHA && category == Category.LIVE) 0L
                        else FixedUnifiedRequestBudgetPolicy.windowWaitMs(
                            now,
                            if (category == Category.LIVE) sharedLiveUsed else sharedHistoricalUsed,
                            sharedLimit,
                            sharedWindow.peekFirstOrNull()
                        )
                    val ltpWait = if (account == Account.ALPHA && isLtp && !isProtection) {
                        FixedUnifiedRequestBudgetPolicy.windowWaitMs(
                            now, alpha.ltp.size, FixedUnifiedRequestBudgetPolicy.ALPHA_LTP_PER_MINUTE,
                            alpha.ltp.peekFirstOrNull()
                        )
                    } else 0L
                    val alphaLiveGapMs = (1_000L + FixedUnifiedRequestBudgetPolicy.ALPHA_LIVE_PER_SECOND - 1) /
                        FixedUnifiedRequestBudgetPolicy.ALPHA_LIVE_PER_SECOND
                    val alphaHistoricalGapMs = (1_000L + FixedUnifiedRequestBudgetPolicy.ALPHA_HISTORICAL_PER_SECOND - 1) /
                        FixedUnifiedRequestBudgetPolicy.ALPHA_HISTORICAL_PER_SECOND
                    val spacingWait = when {
                        account == Account.ALPHA && isLtp && alphaLastLtpPermitAtMs > 0L ->
                            (alphaLastLtpPermitAtMs + 1_200L - now).coerceAtLeast(0L)
                        account == Account.ALPHA && category == Category.LIVE && alphaLastLivePermitAtMs > 0L ->
                            (alphaLastLivePermitAtMs + alphaLiveGapMs - now).coerceAtLeast(0L)
                        account == Account.ALPHA && category == Category.HISTORICAL && alphaLastHistoricalPermitAtMs > 0L ->
                            (alphaLastHistoricalPermitAtMs + alphaHistoricalGapMs - now).coerceAtLeast(0L)
                        account == Account.BETA && category == Category.LIVE && betaLastLivePermitAtMs > 0L ->
                            (betaLastLivePermitAtMs + 1_200L - now).coerceAtLeast(0L)
                        account == Account.BETA && category == Category.HISTORICAL && betaLastHistoricalPermitAtMs > 0L ->
                            (betaLastHistoricalPermitAtMs + 1_500L - now).coerceAtLeast(0L)
                        else -> 0L
                    }
                    val fixedYield = if (!isFixed && !isProtection && pendingFixed.get() > 0)
                        FixedUnifiedRequestBudgetPolicy.BACKGROUND_YIELD_MS else 0L
                    val wait = maxOf(ownWait, sharedWait, ltpWait, spacingWait, fixedYield)
                    if (wait <= 0L) {
                        when (category) {
                            Category.LIVE -> own.live.addLast(now)
                            Category.HISTORICAL -> own.historical.addLast(now)
                        }
                        if (account == Account.ALPHA && isLtp) {
                            alpha.ltp.addLast(now)
                            alphaLastLtpPermitAtMs = now
                        }
                        if (account == Account.ALPHA && category == Category.LIVE) alphaLastLivePermitAtMs = now
                        if (account == Account.ALPHA && category == Category.HISTORICAL) alphaLastHistoricalPermitAtMs = now
                        if (account == Account.BETA && category == Category.LIVE) betaLastLivePermitAtMs = now
                        if (account == Account.BETA && category == Category.HISTORICAL) betaLastHistoricalPermitAtMs = now
                        0L
                    } else wait
                }
                if (waitMs <= 0L) return
                val elapsed = (SystemClock.elapsedRealtime() - startedAtMs).coerceAtLeast(0L)
                if (elapsed + waitMs > maxWaitMs.coerceAtLeast(0L)) {
                    throw IOException("FIXED_SHARED_API_BUDGET_WAIT_TIMEOUT:$account:$category")
                }
                delay(waitMs.coerceAtLeast(1L))
            }
        } finally {
            if (isFixed) pendingFixed.decrementAndGet()
        }
    }

    private fun pruneAll(nowMs: Long) {
        prune(alpha.live, nowMs)
        prune(alpha.historical, nowMs)
        prune(alpha.ltp, nowMs)
        prune(beta.live, nowMs)
        prune(beta.historical, nowMs)
    }

    private fun prune(queue: ArrayDeque<Long>, nowMs: Long) {
        while (queue.isNotEmpty() && queue.peekFirst() <= nowMs - FixedUnifiedRequestBudgetPolicy.WINDOW_MS) {
            queue.removeFirst()
        }
    }

    private fun ArrayDeque<Long>.peekFirstOrNull(): Long? = if (isEmpty()) null else peekFirst()
}
