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
        val status = symbols.refresh()
        auditLogger.log("NSE_MASTER", "SYNC_SUCCESS", mapOf("count" to status.count, "updated_at_ms" to status.updatedAtMs))
        Result.success()
    }.getOrElse {
        auditLogger.log("NSE_MASTER", "SYNC_FAILED", mapOf("type" to it.javaClass.simpleName, "message" to (it.message ?: "")))
        Result.retry()
    }
}
