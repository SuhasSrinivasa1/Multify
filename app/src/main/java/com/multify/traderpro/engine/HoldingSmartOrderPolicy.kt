package com.multify.traderpro.engine

object HoldingSmartOrderPolicy {
    const val MODE_NONE = "NONE"
    const val MODE_TRAILING_GTT = "TRAILING_GTT"
    const val MODE_RECOVERY_GTT = "RECOVERY_GTT"

    fun recoveryDirection(lastPrice: Double, targetPrice: Double): String =
        if (lastPrice + 1e-9 < targetPrice) "UP" else "DOWN"

    fun isServerProtected(mode: String, smartOrderId: String?): Boolean =
        !smartOrderId.isNullOrBlank() && mode in setOf(MODE_TRAILING_GTT, MODE_RECOVERY_GTT)
}
