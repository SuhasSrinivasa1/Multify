package com.multify.traderpro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.multify.traderpro.worker.NseSymbolSyncWorker
import com.multify.traderpro.data.network.GrowwRateLimitInterceptor
import com.multify.traderpro.data.network.GrowwRecoveryGate
import com.multify.traderpro.data.preferences.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import java.time.Duration
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltAndroidApp
class MultifyApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var recoveryGate: GrowwRecoveryGate
    @Inject lateinit var preferences: AppPreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Before any background trade scanner/service can run, require explicit opt-in again.
        // Does not disable monitoring/sells for existing app-owned positions.
        runBlocking(Dispatchers.IO) { preferences.disableAutomaticEntriesOnceForV7931() }
        GrowwRateLimitInterceptor.installRecovery(recoveryGate, preferences)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniqueWork(
            "nse-symbol-master-initial",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NseSymbolSyncWorker>().setConstraints(network).build()
        )
        // NSE symbol master is not polled during market hours. Refresh weekly over the weekend.
        // WorkManager may defer the exact run because of Android battery scheduling.
        val indiaNow = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"))
        var saturday = indiaNow.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
            .withHour(10).withMinute(0).withSecond(0).withNano(0)
        if (!saturday.isAfter(indiaNow)) saturday = saturday.plusWeeks(1)
        val untilWeekendMs = Duration.between(indiaNow, saturday).toMillis()
        wm.enqueueUniquePeriodicWork(
            "nse-symbol-master-weekend",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NseSymbolSyncWorker>(7, TimeUnit.DAYS)
                .setInitialDelay(untilWeekendMs, TimeUnit.MILLISECONDS)
                .setConstraints(network).build()
        )
    }
}
