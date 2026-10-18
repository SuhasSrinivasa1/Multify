package com.multify.traderpro.engine

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative estimate for standard Groww NSE equity-delivery (CNC) charges.
 * Exact contract-note charges can differ slightly because of exchange/broker rounding
 * and funding mode. Recovery GTT pricing therefore rounds upward to the next ₹0.05 tick.
 */
data class DeliveryChargeEstimate(
    val buyBrokerage: Double,
    val sellBrokerage: Double,
    val stt: Double,
    val stampDuty: Double,
    val exchangeCharges: Double,
    val sebiCharges: Double,
    val ipftCharges: Double,
    val dpCharges: Double,
    val gst: Double,
    val total: Double
)

object GrowwDeliveryCostPolicy {
    private const val BROKERAGE_RATE = 0.001 // 0.1%
    private const val BROKERAGE_MIN = 5.0
    private const val BROKERAGE_MAX = 20.0
    private const val DELIVERY_STT_RATE = 0.001 // buy + sell
    private const val DELIVERY_STAMP_BUY_RATE = 0.00015
    private const val NSE_EXCHANGE_RATE = 0.0000297
    private const val SEBI_RATE = 0.000001
    private const val NSE_IPFT_RATE = 0.000001
    private const val GST_RATE = 0.18
    // Conservative standard DP debit cost: ₹3.50 depository + ₹16.50 Groww before GST.
    private const val DP_SELL_BASE = 20.0
    private const val LOW_VALUE_DP_BASE = 3.50
    const val DEFAULT_NET_RECOVERY_TARGET_RUPEES = 100.0
    const val SAFE_TICK_RUPEES = 0.05

    fun brokerage(turnover: Double): Double {
        if (!turnover.isFinite() || turnover <= 0.0) return 0.0
        return min(BROKERAGE_MAX, max(BROKERAGE_MIN, turnover * BROKERAGE_RATE))
    }

    fun estimateCharges(entryPrice: Double, exitPrice: Double, quantity: Int): DeliveryChargeEstimate {
        require(entryPrice > 0.0 && exitPrice > 0.0 && quantity > 0)
        val buyTurnover = entryPrice * quantity
        val sellTurnover = exitPrice * quantity
        val totalTurnover = buyTurnover + sellTurnover
        val buyBrokerage = brokerage(buyTurnover)
        val sellBrokerage = brokerage(sellTurnover)
        val stt = totalTurnover * DELIVERY_STT_RATE
        val stamp = buyTurnover * DELIVERY_STAMP_BUY_RATE
        val exchange = totalTurnover * NSE_EXCHANGE_RATE
        val sebi = totalTurnover * SEBI_RATE
        val ipft = totalTurnover * NSE_IPFT_RATE
        val dp = if (sellTurnover < 100.0) LOW_VALUE_DP_BASE else DP_SELL_BASE
        val taxable = buyBrokerage + sellBrokerage + exchange + sebi + ipft + dp
        val gst = taxable * GST_RATE
        val total = buyBrokerage + sellBrokerage + stt + stamp + exchange + sebi + ipft + dp + gst
        return DeliveryChargeEstimate(
            buyBrokerage = buyBrokerage,
            sellBrokerage = sellBrokerage,
            stt = stt,
            stampDuty = stamp,
            exchangeCharges = exchange,
            sebiCharges = sebi,
            ipftCharges = ipft,
            dpCharges = dp,
            gst = gst,
            total = total
        )
    }

    fun estimatedNetProfit(entryPrice: Double, exitPrice: Double, quantity: Int): Double {
        val gross = (exitPrice - entryPrice) * quantity
        return gross - estimateCharges(entryPrice, exitPrice, quantity).total
    }

    fun recoverySellPrice(
        entryPrice: Double,
        quantity: Int,
        targetNetProfitRupees: Double = DEFAULT_NET_RECOVERY_TARGET_RUPEES,
        tickSize: Double = SAFE_TICK_RUPEES
    ): Double {
        require(entryPrice > 0.0 && entryPrice.isFinite())
        require(quantity > 0)
        require(targetNetProfitRupees >= 0.0 && targetNetProfitRupees.isFinite())
        require(tickSize > 0.0 && tickSize.isFinite())

        var low = entryPrice
        var high = max(entryPrice + 1.0, entryPrice * 1.02)
        repeat(20) {
            if (estimatedNetProfit(entryPrice, high, quantity) >= targetNetProfitRupees) return@repeat
            high *= 1.25
        }
        repeat(80) {
            val mid = (low + high) / 2.0
            if (estimatedNetProfit(entryPrice, mid, quantity) >= targetNetProfitRupees) high = mid else low = mid
        }
        var rounded = ceil((high - 1e-9) / tickSize) * tickSize
        while (estimatedNetProfit(entryPrice, rounded, quantity) + 1e-6 < targetNetProfitRupees) {
            rounded += tickSize
        }
        return rounded
    }
}
