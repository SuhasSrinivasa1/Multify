package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedNotificationEventKeyPolicyTest {
    @Test fun separateSameDayEventsUseSeparateNotificationKeys() {
        val first=FixedNotificationEventKeyPolicy.key("abc",1_000L)
        val requalified=FixedNotificationEventKeyPolicy.key("ABC",2_000L)
        assertNotEquals(first,requalified)
        assertEquals(first,FixedNotificationEventKeyPolicy.key("ABC",1_000L))
    }

    @Test fun legacySingleSymbolMarkerSuppressesOnlyItsFirstKnownEvent() {
        val result=FixedNotificationEventKeyPolicy.migrateLegacy(
            setOf("ABC"),listOf("ABC" to 1_000L,"ABC" to 2_000L))
        assertEquals(setOf("ABC|1000"),result)
        assertFalse(result.contains("ABC|2000"))
    }

    @Test fun existingEventKeysSurviveMigration() {
        val result=FixedNotificationEventKeyPolicy.migrateLegacy(
            setOf("ABC|1000","XYZ|5000"),listOf("ABC" to 2000L))
        assertEquals(setOf("ABC|1000","XYZ|5000"),result)
    }
}
