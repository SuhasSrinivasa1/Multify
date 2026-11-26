package com.multify.traderpro

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.ContextCompat
import com.multify.traderpro.worker.MarketDiagnosticCheckpointScheduler
import com.multify.traderpro.service.TradingEngineService
import com.multify.traderpro.ui.screens.FixedLiteApp
import com.multify.traderpro.ui.theme.MultifyTraderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val notificationsPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPostNotificationsIfNeeded()
        MarketDiagnosticCheckpointScheduler.onAppStarted(this)
        TradingEngineService.start(this)
        setContent {
            MultifyTraderTheme(darkTheme = isSystemInDarkTheme()) {
                FixedLiteApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        frameMonitoring = true
        lastFrameNanos = 0L
        resetFrameWindow()
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onPause() {
        frameMonitoring = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        reportFrameWindow("ACTIVITY_PAUSE")
        super.onPause()
    }

    override fun onDestroy() {
        frameMonitoring = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDestroy()
    }

    private fun resetFrameWindow() {
        frameCount = 0L
        intervalTotalMs = 0.0
        maxFrameIntervalMs = 0.0
        framesOver50Ms = 0L
        framesOver100Ms = 0L
        lastFrameReportAtMs = System.currentTimeMillis()
    }

    /** Main-thread work is limited to counters; serialization and disk writes stay on AuditLogger's worker. */
    private fun reportFrameWindow(reason: String) {
        if (frameCount <= 0L) {
            resetFrameWindow()
            return
        }
        val runtime = Runtime.getRuntime()
        runCatching {
            auditLogger.logAsync("ANDROID_PERFORMANCE", "UI_FRAME_TIMING", mapOf(
                "window_reason" to reason,
                "sampled_frame_intervals" to frameCount,
                "average_frame_interval_ms" to intervalTotalMs / frameCount,
                "max_frame_interval_ms" to maxFrameIntervalMs,
                "frames_at_least_50ms" to framesOver50Ms,
                "frames_at_least_100ms" to framesOver100Ms,
                "java_heap_used_bytes" to (runtime.totalMemory() - runtime.freeMemory()),
                "java_heap_max_bytes" to runtime.maxMemory(),
                "native_heap_allocated_bytes" to Debug.getNativeHeapAllocatedSize(),
                "frame_interval_is_rendered_frame_time" to false,
                "no_trading_or_scoring_effect" to true
            ))
        }
        resetFrameWindow()
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
