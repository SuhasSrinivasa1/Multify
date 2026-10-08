package com.multify.traderpro.engine

data class ExecutionHealthInput(
    val brokerAuthenticated: Boolean,
    val staticIpMatched: Boolean,
    val marketOpen: Boolean,
    val quoteAgeMs: Long,
    val maxQuoteAgeMs: Long,
    val listenerHeartbeatAgeMs: Long,
    val maxListenerHeartbeatAgeMs: Long,
    val symbolMasterFresh: Boolean,
    val decisionComplete: Boolean,
    val safetyHalt: Boolean
)

data class ExecutionHealthDecision(
    val liveOrderAllowed: Boolean,
    val brokerHealthy: Boolean,
    val listenerHealthy: Boolean,
    val marketDataFresh: Boolean,
    val decisionComplete: Boolean,
    val reason: String
)

object ExecutionHealthPolicy {
    fun evaluate(input: ExecutionHealthInput): ExecutionHealthDecision {
        val brokerHealthy = input.brokerAuthenticated && input.staticIpMatched && !input.safetyHalt
        val listenerHealthy = input.listenerHeartbeatAgeMs in 0..input.maxListenerHeartbeatAgeMs
        val marketDataFresh = input.quoteAgeMs in 0..input.maxQuoteAgeMs
        val reasons = buildList {
            if (!input.brokerAuthenticated) add("broker authentication invalid")
            if (!input.staticIpMatched) add("static IP verification failed")
            if (input.safetyHalt) add("safety halt active")
            if (!input.marketOpen) add("market closed")
            if (!marketDataFresh) add("quote stale")
            if (!input.symbolMasterFresh) add("symbol master stale")
            if (!listenerHealthy) add("execution service/listener unhealthy")
            if (!input.decisionComplete) add("decision snapshot incomplete")
        }
        return ExecutionHealthDecision(
            liveOrderAllowed = reasons.isEmpty(),
            brokerHealthy = brokerHealthy,
            listenerHealthy = listenerHealthy,
            marketDataFresh = marketDataFresh,
            decisionComplete = input.decisionComplete,
            reason = if (reasons.isEmpty()) "HEALTHY" else reasons.joinToString("; ")
        )
    }
}
