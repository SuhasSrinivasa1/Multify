package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class BrokerOrderAcknowledgementPolicyTest {
 @Test fun nullAndWhitespaceOrderIdsAreAmbiguous() {
  assertNull(BrokerOrderAcknowledgementPolicy.usableId(null))
  assertNull(BrokerOrderAcknowledgementPolicy.usableId(" "))
  assertEquals("ABC", BrokerOrderAcknowledgementPolicy.usableId(" ABC "))
  assertTrue(BrokerOrderAcknowledgementPolicy.unresolvedMessage("SELL","KDDL","CLS").contains("reconcile"))
 }
}
