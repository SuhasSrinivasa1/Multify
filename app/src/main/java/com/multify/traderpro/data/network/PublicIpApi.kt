package com.multify.traderpro.data.network

import retrofit2.http.GET
import retrofit2.http.Query

interface PublicIpApi {
    @GET("/")
    suspend fun currentIp(@Query("format") format: String = "json"): PublicIpPayload
}
