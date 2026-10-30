package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class NetGainTrailingPolicyTest {
    @Test fun netTargetIncludesChargesAndSlippage() {
        val target = NetGainTrailingPolicy.minimumNetTargetTrigger(299.29,32)
        assertTrue(target > 299.29 * 1.005)
        assertTrue(GrowwDeliveryCostPolicy.estimatedNetProfit(299.29,target,32) >= 299.29 * 32 * .005)
    }
    @Test fun originalProfitTrailNeverLoosened() {
        val gross = SteppedTrailingPolicy.stopPrice(299.29,.50)
        assertEquals(gross,NetGainTrailingPolicy.additionalNetGainProtection(299.29,32,301.05,gross),.000001)
        assertTrue(NetGainTrailingPolicy.additionalNetGainProtection(299.29,32,315.0,gross) >= gross)
    }
}
