package com.multify.traderpro.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.multify.traderpro.data.repository.TradingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SignalForwardWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: TradingRepository
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val eventId = inputData.getLong(KEY_EVENT_ID, -1L)
        if (eventId <= 0L) return Result.failure()
        return try {
            repository.processEvent(eventId)
            Result.success()
        } catch (_: IllegalArgumentException) {
            Result.failure()
        } catch (_: IllegalStateException) {
            Result.failure()
        } catch (_: Throwable) {
            // Broker execution is deliberately not auto-retried: ambiguous network failures must be
            // reconciled by the next broker refresh instead of risking duplicate live orders.
            Result.failure()
        }
    }

    companion object { const val KEY_EVENT_ID = "event_id" }
}
