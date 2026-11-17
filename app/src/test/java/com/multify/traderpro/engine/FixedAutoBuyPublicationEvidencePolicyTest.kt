package com.multify.traderpro.engine

import org.junit.Assert.*
import org.junit.Test

class FixedAutoBuyPublicationEvidencePolicyTest {
    private val p = FixedAutoBuyPublicationEvidencePolicy
    private val t = 1_800_000_000_000L
    private fun evidence(sourceDelay: Long, publicationDelay: Long) =
        p.Evidence(1L, "SEJALLTD", t, t + sourceDelay, t + publicationDelay)
    private fun check(e: p.Evidence?, now: Long, activation: Long = t - 1L) =
        p.decide(e, 1L, "SEJALLTD", t, activation, now)

    @Test fun sejalElevenMinuteScanDoesNotExpireOnScanStartAlone() {
        val e = evidence(sourceDelay = 434_000L, publicationDelay = 691_000L)
        val d = check(e, t + 692_000L)
        assertTrue(d.reason, d.eligible)
        assertEquals(t + 734_000L, d.expiresAtMs)
        assertEquals("EVIDENCE_FRESH_REQUIRES_ALPHA_EXECUTION_QUOTE", d.reason)
    }

    @Test fun oldMarketQuoteCannotBeResurrectedAtPublication() {
        val e = evidence(sourceDelay = 100_000L, publicationDelay = 691_000L)
        val d = check(e, t + 692_000L)
        assertFalse(d.eligible)
        assertEquals("SOURCE_QUOTE_OR_PUBLICATION_EXPIRED", d.reason)
    }

    @Test fun cooldownCannotBuyAfterEvidenceExpires() {
        val e = evidence(sourceDelay = 434_000L, publicationDelay = 691_000L)
        assertFalse(check(e, t + 1_300_000L).eligible)
    }

    @Test fun activationDuringScanStillFailsOriginalRule() {
        val e = evidence(434_000L, 691_000L)
        assertEquals("PRE_ACTIVATION_RECOMMENDATION",
            check(e, t + 692_000L, t + 10_000L).reason)
    }

    @Test fun missingOrMismatchedProvenanceFailsClosed() {
        assertFalse(check(null, t + 692_000L).eligible)
        assertFalse(check(evidence(434_000L, 691_000L).copy(recommendationId=2),
            t + 692_000L).eligible)
    }

    @Test fun staleSourceCannotBeMarkedFreshByLaterAlphaQuote() {
        val e = evidence(150_000L, 691_000L)
        assertFalse(check(e, t + 692_000L).eligible)
    }

    @Test fun journalIsBoundedAndProcessLocal() {
        val ledger = FixedAutoBuyPublicationEvidenceLedger()
        (1L..150L).forEach {
            ledger.record(evidence(1_000L,2_000L).copy(recommendationId=it))
        }
        assertNull(ledger.find(1L))
        assertNotNull(ledger.find(150L))
        ledger.clear()
        assertNull(ledger.find(150L))
    }
}
