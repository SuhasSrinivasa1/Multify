package com.multify.traderpro.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts the monitoring shell after reboot or in-place package replacement. */
class TradingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> TradingEngineService.start(context.applicationContext)
        }
    }
}
