package com.multify.traderpro.engine

object ExecutionTelemetryMath {
    fun averageMillis(values: List<Long>): Double =
        if (values.isEmpty()) 0.0 else values.average()

    fun averageDispatchMillis(micros: List<Long>): Double {
        val observed = micros.filter { it > 0L }
        return if (observed.isEmpty()) 0.0 else observed.average() / 1000.0
    }
}
