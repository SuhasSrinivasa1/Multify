package com.multify.traderpro

import com.multify.traderpro.data.security.TotpGenerator
import org.junit.Assert.assertEquals
import org.junit.Test

class TotpGeneratorTest {
    @Test
    fun rfc6238SixDigitVectorAt59Seconds() {
        assertEquals("287082", TotpGenerator.generate("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", 59L))
    }
}
