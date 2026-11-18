package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.repository.NseSymbolRepository
import com.multify.traderpro.data.logging.AuditLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class NseSymbolSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val symbols: NseSymbolRepository,
    private val auditLogger: AuditLogger
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val current = symbols.status()
        if (current.count > NseSymbolRepository.MIN_SYMBOL_COUNT && !symbols.isStale()) {
            auditLogger.log("NSE_MASTER", "SYNC_SKIPPED_FRESH", mapOf(
                "count" to current.count,
                "updated_at_ms" to current.updatedAtMs,
                "source" to current.source,
                "last_primary_error" to current.nsePrimaryError,
                "work_attempt" to runAttemptCount,
                "network_calls" to 0
            ))
            return@runCatching Result.success()
        }
        auditLogger.log("NSE_MASTER", "SYNC_STARTED", mapOf(
            "count_before" to current.count, "source_before" to current.source,
            "last_success_at_ms" to current.updatedAtMs,
            "work_attempt" to runAttemptCount,
            "has_valid_cached_master" to (current.count > NseSymbolRepository.MIN_SYMBOL_COUNT)
        ))
        val status = symbols.refresh()
        auditLogger.log("NSE_MASTER", "SYNC_SUCCESS", mapOf(
            "count" to status.count,
            "updated_at_ms" to status.updatedAtMs,
            "source" to status.source,
            "nse_primary_error" to status.nsePrimaryError,
            "fallback_was_used" to (status.source == "GROWW_OFFICIAL_NSE_CASH"),
            "work_attempt" to runAttemptCount,
            "fixed_scoring_changed" to false
        ))
        Result.success()
    }.getOrElse {
        val state = symbols.status()
        val retry = runAttemptCount < 2
        auditLogger.log("NSE_MASTER", "SYNC_FAILED", mapOf(
            "type" to it.javaClass.simpleName,
            "message" to (it.message ?: "").take(180),
            "nse_primary_error" to state.nsePrimaryError,
            "cached_symbol_count" to state.count,
            "cached_source" to state.source,
            "last_good_master_preserved" to (state.count > NseSymbolRepository.MIN_SYMBOL_COUNT),
            "work_attempt" to runAttemptCount,
            "retry_scheduled" to retry,
            "market_discovery_must_not_claim_healthy_without_a_full_sweep" to true
        ))
        if (retry) Result.retry() else Result.failure()
    }
}
