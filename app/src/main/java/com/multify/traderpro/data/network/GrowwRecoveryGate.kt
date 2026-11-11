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
                      val categoryCooldownUntilMs: Map<GrowwApiUsageLedger.Category, Long>,
                      val endpointCooldownUntilMs: Map<String, Long> = emptyMap())

    class LocallyBlocked(val reason: String, val nextAllowedAtMs: Long) : IOException(
        "Groww locally paused: $reason" +
            (if (nextAllowedAtMs > 0L) "; retry after ${java.util.Date(nextAllowedAtMs)}" else "; reconnect Groww manually")
    )

    fun status(now: Long = System.currentTimeMillis()): Status = synchronized(lock) {
        val auth = rolling(AUTH_REQUEST_TIMES, now, DAY_MS)
        val endpointCooldowns = preferences.all.keys
            .filter { it.startsWith(ENDPOINT_COOLDOWN_PREFIX) }
            .associate { key -> key.removePrefix(ENDPOINT_COOLDOWN_PREFIX) to preferences.getLong(key, 0L) }
        Status(preferences.getBoolean(RECONNECT_REQUIRED, false), auth.size,
            (GrowwRecoveryPolicy.AUTH_REQUEST_BUDGET_24H - auth.size).coerceAtLeast(0),
            GrowwApiUsageLedger.Category.values().associateWith { category ->
                preferences.getLong(cooldownKey(category), 0L)
            }, endpointCooldowns)
    }

    fun requiresReconnect(): Boolean = synchronized(lock) {
        preferences.getBoolean(RECONNECT_REQUIRED, false)
    }

    /** Validate without reserving an authentication request (category-wide guard). */
    fun checkRequestAllowed(category: GrowwApiUsageLedger.Category, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        check(category, now, null)
    }

    /** Endpoint-aware guard: a /ltp 429 does not unnecessarily pause quotes or other categories. */
    fun checkRequestAllowed(category: GrowwApiUsageLedger.Category, endpointPath: String,
                            now: Long = System.currentTimeMillis()) = synchronized(lock) {
        check(category, now, endpointPath)
    }

    /** Atomically re-check and reserve an outbound request immediately before network dispatch. */
    fun reserveRequest(category: GrowwApiUsageLedger.Category, now: Long = System.currentTimeMillis()) = synchronized(lock) {
        check(category, now, null)
        reserveAuth(category, now)
    }

    fun reserveRequest(category: GrowwApiUsageLedger.Category, endpointPath: String,
                       now: Long = System.currentTimeMillis()) = synchronized(lock) {
        check(category, now, endpointPath)
        reserveAuth(category, now)
    }

    private fun reserveAuth(category: GrowwApiUsageLedger.Category, now: Long) {
        if (category == GrowwApiUsageLedger.Category.AUTH) {
            val attempts = rolling(AUTH_REQUEST_TIMES, now, DAY_MS)
            preferences.edit().putString(AUTH_REQUEST_TIMES, (attempts + now).joinToString(",")).commit()
        }
    }

    private fun check(category: GrowwApiUsageLedger.Category, now: Long, endpointPath: String?) {
        if (category != GrowwApiUsageLedger.Category.AUTH && preferences.getBoolean(RECONNECT_REQUIRED, false)) {
            throw LocallyBlocked("AUTH_EXPIRED_HTTP_401", 0L)
        }
        val categoryUntil = preferences.getLong(cooldownKey(category), 0L)
        if (categoryUntil > now) throw LocallyBlocked("HTTP_429_${category.name}_COOLDOWN", categoryUntil)
        val endpointUntil = endpointPath?.let {
            preferences.getLong(endpointCooldownKey(category, it), 0L)
        } ?: 0L
        if (endpointUntil > now) throw LocallyBlocked(
            "HTTP_429_${category.name}_ENDPOINT_COOLDOWN:${endpointPath}", endpointUntil
        )
        if (category == GrowwApiUsageLedger.Category.AUTH) {
            val attempts = rolling(AUTH_REQUEST_TIMES, now, DAY_MS)
            if (attempts.size >= GrowwRecoveryPolicy.AUTH_REQUEST_BUDGET_24H) {
                val next = (attempts.first() + DAY_MS).coerceAtLeast(now + 1L)
                throw LocallyBlocked("AUTH_24_HOUR_BUDGET", next)
            }
        }
    }

    /** Legacy category-wide 429 guard for endpoints without a stable path. */
    fun on429(category: GrowwApiUsageLedger.Category, retryAfterMs: Long,
              now: Long = System.currentTimeMillis()): Long = synchronized(lock) {
        apply429(category, retryAfterMs, now, cooldownKey(category), historyKey(category))
    }

    /** Endpoint-scoped 429 guard keeps an LTP throttle from freezing unrelated live quote traffic. */
    fun on429(category: GrowwApiUsageLedger.Category, retryAfterMs: Long, endpointPath: String,
              now: Long = System.currentTimeMillis()): Long = synchronized(lock) {
        apply429(category, retryAfterMs, now,
            endpointCooldownKey(category, endpointPath), endpointHistoryKey(category, endpointPath))
    }

    private fun apply429(category: GrowwApiUsageLedger.Category, retryAfterMs: Long, now: Long,
                         untilKey: String, historyKey: String): Long {
        val history = rolling(historyKey, now, HOUR_MS)
        // Multiple replies in the same 5-second burst count as one incident.
        val sameIncident = history.lastOrNull()?.let { now - it < SAME_INCIDENT_WINDOW_MS } == true
        val failures = history.size + if (sameIncident) 0 else 1
        val duration = GrowwRecoveryPolicy.cooldownMs(failures.coerceAtLeast(1), retryAfterMs)
        val until = maxOf(preferences.getLong(untilKey, 0L), now + duration)
        preferences.edit()
            .putString(historyKey, (if (sameIncident) history else history + now).joinToString(","))
            .putLong(untilKey, until)
            .commit()
        return duration
    }

    fun endpointCooldownUntilMs(category: GrowwApiUsageLedger.Category, endpointPath: String): Long =
        synchronized(lock) { preferences.getLong(endpointCooldownKey(category, endpointPath), 0L) }

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
    private fun endpointSuffix(category: GrowwApiUsageLedger.Category, path: String) =
        "${category.name}_${path.trim('/').replace('/', '_').replace('-', '_')}"
    private fun endpointCooldownKey(category: GrowwApiUsageLedger.Category, path: String) =
        ENDPOINT_COOLDOWN_PREFIX + endpointSuffix(category, path)
    private fun endpointHistoryKey(category: GrowwApiUsageLedger.Category, path: String) =
        "429_times_endpoint_${endpointSuffix(category, path)}"

    companion object {
        private const val ENDPOINT_COOLDOWN_PREFIX = "endpoint_cooldown_"
        private const val SAME_INCIDENT_WINDOW_MS = 5_000L
        private const val MINUTE_MS = 60_000L
        private const val HOUR_MS = 60L * MINUTE_MS
        private const val DAY_MS = 24L * HOUR_MS
        private const val AUTH_REQUEST_TIMES = "auth_request_times"
        private const val RECONNECT_REQUIRED = "reconnect_required"
    }
}
