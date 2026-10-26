package com.multify.traderpro.data.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.multify.traderpro.engine.GrowwRecoveryPolicy
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-wide, restart-persistent protection from repeated HTTP 429 and expired-token 401 traffic.
 * Only this class reserves authentication attempts; blocked requests never reach Groww or consume budget.
 */
@Singleton
class GrowwRecoveryGate @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("groww_recovery_v782", Context.MODE_PRIVATE)
    private val lock = Any()

    data class Status(val reconnectRequired: Boolean, val authAttemptsLast24h: Int, val authRemaining: Int,
                      val categoryCooldownUntilMs: Map<GrowwApiUsageLedger.Category, Long>)

    class LocallyBlocked(val reason: String, val nextAllowedAtMs: Long) : IOException(
        "Groww locally paused: $reason" +
            (if (nextAllowedAtMs > 0L) "; retry after ${java.util.Date(nextAllowedAtMs)}" else "; reconnect Groww manually")
    )

    fun status(now: Long = System.currentTimeMillis()): Status = synchronized(lock) {
        val auth = rolling(AUTH_REQUEST_TIMES, now, DAY_MS)
        Status(preferences.getBoolean(RECONNECT_REQUIRED, false), auth.size,
            (GrowwRecoveryPolicy.AUTH_REQUEST_BUDGET_24H - auth.size).coerceAtLeast(0),
            GrowwApiUsageLedger.Category.values().associateWith { category ->
                preferences.getLong(cooldownKey(category), 0L)
            })
    }

    fun requiresReconnect(): Boolean = synchronized(lock) {
        preferences.getBoolean(RECONNECT_REQUIRED, false)
    }

    /** Validate without reserving an authentication request (used before the per-second governor). */
    fun checkRequestAllowed(category: GrowwApiUsageLedger.Category, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        check(category, now)
    }

    /** Atomically re-check and reserve an outbound request immediately before network dispatch. */
    fun reserveRequest(category: GrowwApiUsageLedger.Category, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        check(category, now)
        if (category == GrowwApiUsageLedger.Category.AUTH) {
            val attempts = rolling(AUTH_REQUEST_TIMES, now, DAY_MS)
            preferences.edit().putString(AUTH_REQUEST_TIMES, (attempts + now).joinToString(",")).commit()
        }
    }

    private fun check(category: GrowwApiUsageLedger.Category, now: Long) {
        if (category != GrowwApiUsageLedger.Category.AUTH && preferences.getBoolean(RECONNECT_REQUIRED, false)) {
            throw LocallyBlocked("AUTH_EXPIRED_HTTP_401", 0L)
        }
        val until = preferences.getLong(cooldownKey(category), 0L)
        if (until > now) throw LocallyBlocked("HTTP_429_${category.name}_COOLDOWN", until)
        if (category == GrowwApiUsageLedger.Category.AUTH) {
            val attempts = rolling(AUTH_REQUEST_TIMES, now, DAY_MS)
            if (attempts.size >= GrowwRecoveryPolicy.AUTH_REQUEST_BUDGET_24H) {
                val next = (attempts.first() + DAY_MS).coerceAtLeast(now + 1L)
                throw LocallyBlocked("AUTH_24_HOUR_BUDGET", next)
            }
        }
    }

    /** After a 429, cease requests to the affected endpoint category, not a sleep on an HTTP thread. */
    fun on429(category: GrowwApiUsageLedger.Category, retryAfterMs: Long, now: Long = System.currentTimeMillis()): Long = synchronized(lock) {
        val history = rolling(historyKey(category), now, HOUR_MS)
        val failures = history.size + 1
        val duration = GrowwRecoveryPolicy.cooldownMs(failures, retryAfterMs)
        val until = maxOf(preferences.getLong(cooldownKey(category), 0L), now + duration)
        preferences.edit()
            .putString(historyKey(category), (history + now).joinToString(","))
            .putLong(cooldownKey(category), until)
            .commit()
        duration
    }

    /** A 401 on a broker endpoint revokes permission to auto-call broker endpoints until fresh authentication. */
    fun onUnauthorized() = synchronized(lock) {
        preferences.edit().putBoolean(RECONNECT_REQUIRED, true).commit()
    }

    /** Called only after Groww returns a successful access-token response. */
    fun onAccessTokenSuccess() = synchronized(lock) {
        preferences.edit().putBoolean(RECONNECT_REQUIRED, false)
            .putLong(cooldownKey(GrowwApiUsageLedger.Category.AUTH), 0L)
            .remove(historyKey(GrowwApiUsageLedger.Category.AUTH)).commit()
    }

    private fun rolling(key: String, now: Long, periodMs: Long): List<Long> =
        preferences.getString(key, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
            .filter { it > 0L && it <= now && now - it < periodMs }.sorted()

    private fun cooldownKey(category: GrowwApiUsageLedger.Category) = "cooldown_${category.name}"
    private fun historyKey(category: GrowwApiUsageLedger.Category) = "429_times_${category.name}"

    companion object {
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 60L * MINUTE_MS
        private const val DAY_MS = 24L * HOUR_MS
        private const val AUTH_REQUEST_TIMES = "auth_request_times"
        private const val RECONNECT_REQUIRED = "reconnect_required"
    }
}
