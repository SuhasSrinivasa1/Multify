package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class BrokerBuyFailureMessagePolicyTest {
    @Test fun explicitBrokerFundsErrorsAreRecognized() {
        assertTrue(BrokerBuyFailureMessagePolicy.insufficientFunds("Order rejected: insufficient funds"))
        assertTrue(BrokerBuyFailureMessagePolicy.insufficientFunds("{\"message\":\"Margin shortfall\"}"))
        assertTrue(BrokerBuyFailureMessagePolicy.insufficientFunds("Not enough balance in account"))
    }
    @Test fun unrelatedFailuresDoNotShowFalseFundsAlert() {
        assertFalse(BrokerBuyFailureMessagePolicy.insufficientFunds("429 Too Many Requests"))
        assertFalse(BrokerBuyFailureMessagePolicy.insufficientFunds("HTTP 401 Unauthorized"))
        assertFalse(BrokerBuyFailureMessagePolicy.insufficientFunds(null))
    }
}
