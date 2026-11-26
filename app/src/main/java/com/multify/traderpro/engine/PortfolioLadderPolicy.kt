package com.multify.traderpro.engine

import kotlin.math.floor
import kotlin.math.min

/** Pure preview policy. No orders, no GTT changes. Caller must supply confirmed broker fills. */
object PortfolioLadderPolicy {
    data class State(val anchor:Double,val nextSellPct:Int=10,val nextAddPrice:Double=anchor*.90)
    fun initial(average:Double):State { require(average.isFinite() && average>0);return State(average) }
    fun sellQuantity(remaining:Int):Int=(remaining.coerceAtLeast(0).toLong()*33/100).toInt()
    fun sellTrigger(state:State,liveAverage:Double):Double {
        require(liveAverage.isFinite() && liveAverage>0)
        return liveAverage*(1+state.nextSellPct/100.0)
    }
    fun addQuantity(state:State,availableBudget:Double,perAdd:Double):Int {
        if(!availableBudget.isFinite() || !perAdd.isFinite() || state.nextAddPrice<=0)return 0
        return floor(min(availableBudget,perAdd).coerceAtLeast(0.0)/state.nextAddPrice).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }
    // 5 and 10 percentage points of cycle reference, matching 110->105->95->85.
    fun afterConfirmedProfitSell(state:State,brokerAverage:Double,actualFill:Double):State {
        require(actualFill.isFinite() && actualFill+1e-8>=sellTrigger(state,brokerAverage))
        return State(brokerAverage,state.nextSellPct+10,(actualFill-brokerAverage*.05).coerceAtLeast(0.0))
    }
    fun afterConfirmedAveragingBuy(state:State):State =
        state.copy(nextSellPct=10,nextAddPrice=(state.nextAddPrice-state.anchor*.10).coerceAtLeast(0.0))
}
