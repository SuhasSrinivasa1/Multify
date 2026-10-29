package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrokerInventorySymbolPolicyTest {
    @Test fun `null and blank broker symbols are rejected`() {
        assertNull(BrokerInventorySymbolPolicy.normalize(null))
        assertNull(BrokerInventorySymbolPolicy.normalize("  "))
    }

    @Test fun `valid broker symbols are normalized without changing identity`() {
        assertEquals("RELIANCE", BrokerInventorySymbolPolicy.normalize(" reliance "))
        assertEquals("M&M", BrokerInventorySymbolPolicy.normalize("m&m"))
    }
}
