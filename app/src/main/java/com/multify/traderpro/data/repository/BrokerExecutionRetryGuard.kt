package com.multify.traderpro.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrokerExecutionRetryGuard @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("execution_retry_v783", Context.MODE_PRIVATE)
    fun sellBlockedUntil(symbol: String): Long = prefs.getLong("sell_"+symbol, 0L)
    fun blockSell(symbol: String): Long {
        val until = System.currentTimeMillis() + 86_400_000L
        prefs.edit().putLong("sell_"+symbol, until).commit()
        return until
    }
    fun gttBlockedUntil(symbol: String): Long = prefs.getLong("gtt_"+symbol, 0L)
}
