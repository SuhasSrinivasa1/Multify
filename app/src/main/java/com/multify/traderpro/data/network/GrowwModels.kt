package com.multify.traderpro.data.network

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class TokenRequest(
    @SerializedName("key_type") val keyType: String = "totp",
    val totp: String? = null,
    val checksum: String? = null,
    val timestamp: String? = null
)

data class TokenResponse(
    val token: String? = null,
    @SerializedName("tokenRefId") val tokenRefId: String? = null,
    @SerializedName("sessionName") val sessionName: String? = null,
    val expiry: String? = null,
    @SerializedName("isActive") val isActive: Boolean? = null,
    val status: String? = null,
    val error: ApiError? = null
)

data class ApiError(val code: String? = null, val message: String? = null)

data class ApiEnvelope<T>(
    val status: String = "FAILURE",
    val payload: T? = null,
    val error: ApiError? = null
) {
    fun requirePayload(label: String): T {
        if (!status.equals("SUCCESS", ignoreCase = true) || payload == null) {
            kotlin.error(error?.message ?: "$label failed")
        }
        return payload
    }
}

data class UserProfilePayload(
    @SerializedName("vendor_user_id") val vendorUserId: String? = null,
    val ucc: String? = null,
    @SerializedName("nse_enabled") val nseEnabled: Boolean = false,
    @SerializedName("bse_enabled") val bseEnabled: Boolean = false,
    @SerializedName("ddpi_enabled") val ddpiEnabled: Boolean = false,
    @SerializedName("active_segments") val activeSegments: List<String> = emptyList()
)

data class MarginPayload(
    @SerializedName("clear_cash") val clearCash: Double = 0.0,
    @SerializedName("net_margin_used") val netMarginUsed: Double = 0.0,
    @SerializedName("brokerage_and_charges") val brokerageAndCharges: Double = 0.0,
    @SerializedName("collateral_used") val collateralUsed: Double = 0.0,
    @SerializedName("collateral_available") val collateralAvailable: Double = 0.0,
    @SerializedName("equity_margin_details") val equity: EquityMarginPayload? = null
)

data class EquityMarginPayload(
    @SerializedName("net_equity_margin_used") val netEquityMarginUsed: Double = 0.0,
    @SerializedName("cnc_margin_used") val cncMarginUsed: Double = 0.0,
    @SerializedName("cnc_balance_available") val cncBalanceAvailable: Double = 0.0,
)

data class PositionsPayload(val positions: List<GrowwPosition> = emptyList())

data class HoldingsPayload(val holdings: List<GrowwHolding> = emptyList())

data class GrowwHolding(
    val isin: String = "",
    @SerializedName("trading_symbol") val tradingSymbol: String = "",
    val quantity: Int = 0,
    @SerializedName("average_price") val averagePrice: Double = 0.0,
    @SerializedName("t1_quantity") val t1Quantity: Int = 0,
    @SerializedName("demat_free_quantity") val dematFreeQuantity: Int = 0
)


data class GrowwPosition(
    @SerializedName("trading_symbol") val tradingSymbol: String = "",
    val quantity: Int = 0,
    val product: String = "",
    val exchange: String = "NSE",
    @SerializedName("net_price") val netPrice: Double = 0.0,
    @SerializedName("credit_quantity") val creditQuantity: Int = 0,
    @SerializedName("credit_price") val creditPrice: Double = 0.0,
    @SerializedName("debit_quantity") val debitQuantity: Int = 0,
    @SerializedName("debit_price") val debitPrice: Double = 0.0
)

data class QuoteDepthLevel(val price: Double? = null, val quantity: Double? = null)

data class QuoteMarketDepth(
    val buy: List<QuoteDepthLevel> = emptyList(),
    val sell: List<QuoteDepthLevel> = emptyList()
)

data class QuotePayload(
    @SerializedName("average_price") val averagePrice: Double? = null,
    @SerializedName("bid_price") val bidPrice: Double? = null,
    @SerializedName("bid_quantity") val bidQuantity: Double? = null,
    @SerializedName("offer_price") val offerPrice: Double? = null,
    @SerializedName("offer_quantity") val offerQuantity: Double? = null,
    @SerializedName("last_price") val lastPrice: Double? = null,
    @SerializedName("day_change_perc") val dayChangePct: Double? = null,
    @SerializedName("market_cap") val marketCap: Double? = null,
    @SerializedName("total_buy_quantity") val totalBuyQuantity: Double? = null,
    @SerializedName("total_sell_quantity") val totalSellQuantity: Double? = null,
    @SerializedName("week_52_high") val week52High: Double? = null,
    @SerializedName("week_52_low") val week52Low: Double? = null,
    val volume: Long? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val close: Double? = null,
    @SerializedName("depth") val depth: QuoteMarketDepth? = null
)

data class HistoricalPayload(
    val candles: List<List<JsonElement>> = emptyList(),
    @SerializedName("closing_price") val closingPrice: Double? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("interval_in_minutes") val intervalInMinutes: Int? = null
)

data class OrderCreateRequest(
    @SerializedName("trading_symbol") val tradingSymbol: String,
    val quantity: Int,
    val price: Double = 0.0,
    @SerializedName("trigger_price") val triggerPrice: Double = 0.0,
    val validity: String = "DAY",
    val exchange: String = "NSE",
    val segment: String = "CASH",
    val product: String = "CNC",
    @SerializedName("order_type") val orderType: String = "MARKET",
    @SerializedName("transaction_type") val transactionType: String,
    @SerializedName("order_reference_id") val orderReferenceId: String
)

data class OrderPayload(
    @SerializedName("groww_order_id") val growwOrderId: String = "",
    @SerializedName("order_status") val orderStatus: String = "",
    @SerializedName("order_reference_id") val orderReferenceId: String = "",
    val remark: String? = null,
    val price: Double? = null,
    @SerializedName("filled_quantity") val filledQuantity: Int? = null,
    @SerializedName("remaining_quantity") val remainingQuantity: Int? = null,
    @SerializedName("average_fill_price") val averageFillPrice: Double? = null,
    @SerializedName("trading_symbol") val tradingSymbol: String = "",
    @SerializedName("transaction_type") val transactionType: String = "",
    val product: String = "",
    val quantity: Int? = null,
    @SerializedName("order_type") val orderType: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("order_date_time") val orderDateTime: String? = null
)

data class OrderListPayload(
    @SerializedName("order_list") val orderList: List<OrderPayload> = emptyList()
)

data class OrderTradeRecord(
    val price: Double = 0.0,
    val quantity: Int = 0,
    @SerializedName("groww_order_id") val growwOrderId: String = "",
    @SerializedName("groww_trade_id") val growwTradeId: String = "",
    @SerializedName("trade_status") val tradeStatus: String = "",
    @SerializedName("trading_symbol") val tradingSymbol: String = "",
    @SerializedName("transaction_type") val transactionType: String = "",
    val product: String = "",
    @SerializedName("trade_date_time") val tradeDateTime: String? = null
)

data class OrderTradesPayload(
    @SerializedName("trade_list") val tradeList: List<OrderTradeRecord> = emptyList()
)

data class OrderCancelRequest(
    val segment: String = "CASH",
    @SerializedName("groww_order_id") val growwOrderId: String
)

data class GttOrder(
    @SerializedName("order_type") val orderType: String = "MARKET",
    val price: String? = null,
    @SerializedName("transaction_type") val transactionType: String = "SELL"
)

data class GttCreateRequest(
    @SerializedName("reference_id") val referenceId: String,
    @SerializedName("smart_order_type") val smartOrderType: String = "GTT",
    val segment: String = "CASH",
    @SerializedName("trading_symbol") val tradingSymbol: String,
    val quantity: Int,
    @SerializedName("trigger_price") val triggerPrice: String,
    @SerializedName("trigger_direction") val triggerDirection: String,
    val order: GttOrder = GttOrder(),
    @SerializedName("product_type") val productType: String = "CNC",
    val exchange: String = "NSE",
    val duration: String = "DAY"
)

data class GttModifyRequest(
    @SerializedName("smart_order_type") val smartOrderType: String = "GTT",
    val segment: String = "CASH",
    val quantity: Int,
    @SerializedName("trigger_price") val triggerPrice: String,
    @SerializedName("trigger_direction") val triggerDirection: String,
    val order: GttOrder = GttOrder()
)

data class SmartOrderPayload(
    @SerializedName("smart_order_id") val smartOrderId: String = "",
    @SerializedName("smart_order_type") val smartOrderType: String = "",
    @SerializedName("reference_id") val referenceId: String? = null,
    val status: String = "",
    @SerializedName("trading_symbol") val tradingSymbol: String = "",
    val quantity: Int = 0,
    @SerializedName("product_type") val productType: String = "",
    val exchange: String = "NSE",
    @SerializedName("trigger_price") val triggerPrice: String? = null,
    @SerializedName("trigger_direction") val triggerDirection: String? = null,
    val order: GttOrder? = null,
    @SerializedName("triggered_at") val triggeredAt: String? = null,
    val remark: String? = null
)

data class SmartOrderListPayload(val orders: List<SmartOrderPayload> = emptyList())

data class PublicIpPayload(val ip: String = "")
