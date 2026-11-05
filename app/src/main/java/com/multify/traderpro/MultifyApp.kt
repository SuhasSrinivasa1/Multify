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
import com.multify.traderpro.worker.ForecastNewsSyncWorker
import com.multify.traderpro.data.network.GrowwRateLimitInterceptor
import com.multify.traderpro.data.network.GrowwRecoveryGate
import com.multify.traderpro.data.preferences.AppPreferences
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
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
        runBlocking(Dispatchers.IO) {
            preferences.disableAutomaticEntriesOnceForV7931()
            // Always freeze legacy toggles on startup; never alter Fixed Auto Buy or Multify ARM.
            preferences.setForecastScannerEnabled(false)
            preferences.setForecastAutoBuy(false)
        }
        GrowwRateLimitInterceptor.installRecovery(recoveryGate, preferences)
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniqueWork(
            "nse-symbol-master-initial",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NseSymbolSyncWorker>().setConstraints(network).build()
        )
        // Cancel previously scheduled Forecast news jobs, including pending runs.
        wm.cancelUniqueWork("forecast-news-fallback-15m")
        wm.enqueueUniquePeriodicWork(
            "nse-symbol-master-monthly",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<NseSymbolSyncWorker>(30, TimeUnit.DAYS).setConstraints(network).build()
        )
    }
}
