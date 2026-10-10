package com.multify.traderpro.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ExecutionTelemetryMathTest {
    @Test fun averagesSuccessfulExecutionDurations() {
        assertEquals(600.0, ExecutionTelemetryMath.averageMillis(listOf(450L, 600L, 750L)), 0.0001)
        assertEquals(0.0, ExecutionTelemetryMath.averageMillis(emptyList()), 0.0001)
    }

    @Test fun dispatchAverageIgnoresOrdersWithoutNotificationTiming() {
        assertEquals(35.0, ExecutionTelemetryMath.averageDispatchMillis(listOf(20_000L, 0L, 50_000L)), 0.0001)
        assertEquals(0.0, ExecutionTelemetryMath.averageDispatchMillis(listOf(0L, 0L)), 0.0001)
    }
}
