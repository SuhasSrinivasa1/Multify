package com.multify.traderpro.data.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface GrowwApi {
    @POST("v1/token/api/access")
    suspend fun createAccessToken(
        @Header("Authorization") authorization: String,
        @Body request: TokenRequest
    ): TokenResponse

    @GET("v1/user/detail")
    suspend fun userProfile(
        @Header("Authorization") authorization: String
    ): ApiEnvelope<UserProfilePayload>

    @GET("v1/margins/detail/user")
    suspend fun margins(
        @Header("Authorization") authorization: String
    ): ApiEnvelope<MarginPayload>


    @GET("v1/holdings/user")
    suspend fun holdings(
        @Header("Authorization") authorization: String
    ): ApiEnvelope<HoldingsPayload>

    @GET("v1/positions/user")
    suspend fun positions(
        @Header("Authorization") authorization: String,
        @Query("segment") segment: String = "CASH"
    ): ApiEnvelope<PositionsPayload>

    @GET("v1/live-data/quote")
    suspend fun quote(
        @Header("Authorization") authorization: String,
        @Query("exchange") exchange: String = "NSE",
        @Query("segment") segment: String = "CASH",
        @Query("trading_symbol") tradingSymbol: String
    ): ApiEnvelope<QuotePayload>

    @GET("v1/live-data/ltp")
    suspend fun ltp(
        @Header("Authorization") authorization: String,
        @Query("segment") segment: String = "CASH",
        @Query("exchange_symbols") exchangeSymbols: String
    ): ApiEnvelope<Map<String, Double>>

    @GET("v1/historical/candles")
    suspend fun historicalCandles(
        @Header("Authorization") authorization: String,
        @Query("exchange") exchange: String = "NSE",
        @Query("segment") segment: String = "CASH",
        @Query("groww_symbol") growwSymbol: String,
        @Query("start_time") startTime: String,
        @Query("end_time") endTime: String,
        @Query("candle_interval") candleInterval: String = "5minute"
    ): ApiEnvelope<HistoricalPayload>

    @POST("v1/order/create")
    suspend fun placeOrder(
        @Header("Authorization") authorization: String,
        @Body request: OrderCreateRequest
    ): ApiEnvelope<OrderPayload>

    @GET("v1/order/detail/{orderId}")
    suspend fun orderStatus(
        @Header("Authorization") authorization: String,
        @Path("orderId") orderId: String,
        @Query("segment") segment: String = "CASH"
    ): ApiEnvelope<OrderPayload>

    @POST("v1/order-advance/create")
    suspend fun createOco(
        @Header("Authorization") authorization: String,
        @Body request: OcoCreateRequest
    ): ApiEnvelope<SmartOrderPayload>


    @PUT("v1/order-advance/modify/{smartOrderId}")
    suspend fun modifyOco(
        @Header("Authorization") authorization: String,
        @Path("smartOrderId") smartOrderId: String,
        @Body request: OcoModifyRequest
    ): ApiEnvelope<SmartOrderPayload>

    @GET("v1/order-advance/status/{segment}/{smartOrderType}/internal/{smartOrderId}")
    suspend fun smartOrderStatus(
        @Header("Authorization") authorization: String,
        @Path("segment") segment: String = "CASH",
        @Path("smartOrderType") smartOrderType: String = "OCO",
        @Path("smartOrderId") smartOrderId: String
    ): ApiEnvelope<SmartOrderPayload>

    @GET("v1/order-advance/list")
    suspend fun listSmartOrders(
        @Header("Authorization") authorization: String,
        @Query("segment") segment: String = "CASH",
        @Query("smart_order_type") smartOrderType: String = "OCO",
        @Query("status") status: String = "ACTIVE",
        @Query("page") page: Int = 0,
        @Query("page_size") pageSize: Int = 50
    ): ApiEnvelope<SmartOrderListPayload>

    @POST("v1/order-advance/cancel/{segment}/{smartOrderType}/{smartOrderId}")
    suspend fun cancelSmartOrder(
        @Header("Authorization") authorization: String,
        @Path("segment") segment: String = "CASH",
        @Path("smartOrderType") smartOrderType: String = "OCO",
        @Path("smartOrderId") smartOrderId: String
    ): ApiEnvelope<SmartOrderPayload>
}
