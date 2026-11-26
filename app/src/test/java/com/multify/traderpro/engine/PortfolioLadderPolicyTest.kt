package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class PortfolioLadderPolicyTest {
    @Test fun userExample(){
        val s=PortfolioLadderPolicy.initial(100.0)
        assertEquals(90.0,s.nextAddPrice,1e-9)
        assertEquals(33,PortfolioLadderPolicy.sellQuantity(100))
        assertEquals(22,PortfolioLadderPolicy.sellQuantity(67))
        assertEquals(14,PortfolioLadderPolicy.sellQuantity(45))
        val sold=PortfolioLadderPolicy.afterConfirmedProfitSell(s,100.0,110.0)
        assertEquals(105.0,sold.nextAddPrice,1e-9)
        assertEquals(20,sold.nextSellPct)
        val bought=PortfolioLadderPolicy.afterConfirmedAveragingBuy(sold)
        assertEquals(95.0,bought.nextAddPrice,1e-9)
        assertEquals(85.0,PortfolioLadderPolicy.afterConfirmedAveragingBuy(bought).nextAddPrice,1e-9)
        assertEquals(10,bought.nextSellPct)
    }
    @Test fun budgetAndRounding(){
        assertEquals(0,PortfolioLadderPolicy.sellQuantity(2))
        assertEquals(2,PortfolioLadderPolicy.addQuantity(PortfolioLadderPolicy.initial(100.0),250.0,1000.0))
    }
    @Test(expected=IllegalArgumentException::class)fun belowTarget(){
        PortfolioLadderPolicy.afterConfirmedProfitSell(PortfolioLadderPolicy.initial(100.0),100.0,105.0)
    }
}
