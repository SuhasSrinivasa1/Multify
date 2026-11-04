package com.multify.traderpro.engine

import com.multify.traderpro.data.network.QuotePayload
import org.junit.Assert.*
import org.junit.Test

class FixedExecutionThinLiquidityPolicyTest {
    private val p = FixedExecutionThinLiquidityPolicy

    @Test fun blocksVeryThinReportedTurnover() {
        assertEquals("EXTREMELY_LOW_REPORTED_TURNOVER",
            p.rejectionReason(QuotePayload(lastPrice = 669.0, volume = 574L)))
        assertEquals("EXTREMELY_LOW_REPORTED_TURNOVER",
            p.rejectionReason(QuotePayload(lastPrice = 300.0, volume = 0L)))
    }

    @Test fun highValueOrHighVolumeTradingIsNotRejected() {
        assertNull(p.rejectionReason(QuotePayload(lastPrice = 1_500.0, volume = 1_200L)))
        assertNull(p.rejectionReason(QuotePayload(lastPrice = 220.0, volume = 30_000L)))
        assertNull(p.rejectionReason(QuotePayload(lastPrice = 300.0, volume = 1_500L)))
    }

    @Test fun missingVolumeRemainsUnknownNotFabricatedZero() {
        assertNull(p.rejectionReason(QuotePayload(lastPrice = 600.0)))
        assertNull(p.rejectionReason(QuotePayload(lastPrice = null, volume = 400L)))
    }
}
