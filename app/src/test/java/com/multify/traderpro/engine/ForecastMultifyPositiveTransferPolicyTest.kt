package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastMultifyPositiveTransferPolicyTest {
    @Test
    fun onlyMatchingPositiveCasesAddBonus() {
        val cases = listOf(MultifyPositiveTransferCase("ABC", 1.2, 2.0))
        assertTrue(ForecastMultifyPositiveTransferPolicy.rankingBonus("ABC", cases) > 0.0)
        assertEquals(0.0, ForecastMultifyPositiveTransferPolicy.rankingBonus("XYZ", cases), 0.0)
    }
}
