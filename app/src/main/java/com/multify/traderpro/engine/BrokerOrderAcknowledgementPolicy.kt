package com.multify.traderpro.engine

/** Gson can decode explicit JSON null into a Kotlin non-null String. Always validate at runtime.
 * A missing broker order id is ambiguous even after HTTP 200: NEVER resubmit automatically.
 */
object BrokerOrderAcknowledgementPolicy {
    fun usableId(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
    fun unresolvedMessage(transaction: String, symbol: String, reference: String): String =
        "BROKER_ACK_UNRESOLVED: $transaction $symbol reference=$reference; reconcile broker order before any retry"
}
