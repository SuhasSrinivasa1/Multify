package com.multify.traderpro.data.repository
data class PortfolioHoldingPreview(val symbol:String,val isin:String,val quantity:Int,val average:Double,
    val free:Int,val t1:Int,val price:Double?,val priceAtMs:Long?)
data class PortfolioPreview(val inventoryAtMs:Long?,val holdings:List<PortfolioHoldingPreview>,val cncPositionSymbols:List<String>)
