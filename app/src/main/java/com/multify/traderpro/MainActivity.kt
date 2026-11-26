package com.multify.traderpro

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import com.multify.traderpro.data.repository.FixedRecommendationStore
import com.multify.traderpro.worker.MarketDiagnosticCheckpointScheduler
import com.multify.traderpro.service.TradingEngineService
import com.multify.traderpro.ui.screens.FixedLiteApp
import com.multify.traderpro.ui.theme.MultifyTraderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.time.LocalDate
import java.time.ZoneId

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var fixedRecommendationStore: FixedRecommendationStore

    private val notificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPostNotificationsIfNeeded()
        val today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString()
        MarketDiagnosticCheckpointScheduler.onAppStarted(
            this, fixedRecommendationStore.recommendations(today).size
        )
        TradingEngineService.start(this)
        setContent {
            MultifyTraderTheme(darkTheme = isSystemInDarkTheme()) {
                FixedLiteApp()
            }
        }
    }

    private fun requestPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationsPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
