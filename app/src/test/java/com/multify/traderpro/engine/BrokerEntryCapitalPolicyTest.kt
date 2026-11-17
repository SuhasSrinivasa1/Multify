package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
class BrokerEntryCapitalPolicyTest {
 @Test fun capsByBrokerFundsAndTotalExposure() {
  assertEquals(2, BrokerEntryCapitalPolicy.decide(20,1000.0,90000.0,100000.0,3,1,90000.0,3000.0).allowedQuantity)
  assertEquals(0, BrokerEntryCapitalPolicy.decide(20,1000.0,90000.0,100000.0,3,3,1000.0,90000.0).allowedQuantity)
  assertEquals(0, BrokerEntryCapitalPolicy.decide(20,1000.0,90000.0,100000.0,3,1,1000.0,0.0).allowedQuantity)
  assertEquals(0, BrokerEntryCapitalPolicy.decide(20,1000.0,90000.0,100000.0,3,1,100000.0,90000.0).allowedQuantity)
 }
 @Test fun usesConservativeOfAvailableBrokerCashFields() {
  assertEquals(12000.0, BrokerEntryCapitalPolicy.brokerCncCash(12000.0,30000.0),0.0)
  assertEquals(0.0, BrokerEntryCapitalPolicy.brokerCncCash(0.0,0.0),0.0)
 }
}
