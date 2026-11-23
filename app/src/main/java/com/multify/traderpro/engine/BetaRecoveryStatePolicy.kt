package com.multify.traderpro.engine
object BetaRecoveryStatePolicy {
    const val INCIDENT_WINDOW_MS = 60L * 60L * 1_000L
    const val ADMISSION_WINDOW_MS = 60L * 1_000L
    fun remainingCooldownMs(cooldownUntilEpochMs: Long, nowEpochMs: Long): Long =
        (cooldownUntilEpochMs - nowEpochMs).coerceAtLeast(0L)
    fun cooldownUntilEpochMs(nowEpochMs: Long, remainingCooldownMs: Long): Long =
        if (remainingCooldownMs <= 0L) 0L else nowEpochMs + remainingCooldownMs
    fun parseRecentIncidents(serialized: String?, nowEpochMs: Long): List<Long> =
        parse(serialized).filter { it > 0L && it <= nowEpochMs && nowEpochMs - it < INCIDENT_WINDOW_MS }.sorted()
    fun serializeIncidents(timestamps: Iterable<Long>, nowEpochMs: Long): String =
        timestamps.filter { it > 0L && it <= nowEpochMs && nowEpochMs - it < INCIDENT_WINDOW_MS }.sorted().joinToString(",")
    fun parseRecentAdmissions(serialized: String?, nowEpochMs: Long): List<Long> =
        parse(serialized).filter { it > 0L && it <= nowEpochMs && nowEpochMs - it < ADMISSION_WINDOW_MS }.sorted()
    fun serializeAdmissions(timestamps: Iterable<Long>, nowEpochMs: Long): String =
        timestamps.filter { it > 0L && it <= nowEpochMs && nowEpochMs - it < ADMISSION_WINDOW_MS }.sorted().joinToString(",")
    private fun parse(serialized: String?): List<Long> =
        serialized.orEmpty().split(',').mapNotNull { it.toLongOrNull() }
}
