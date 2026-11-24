package com.multify.traderpro.data.repository
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
class NseArchiveRecoveryPolicyTest {
 private val now=1_795_536_000_000L
 @Test fun requestPacingIsConservative(){assertEquals(1_500L,NseArchiveRecoveryPolicy.REQUEST_MIN_GAP_MS)}
 @Test fun forbiddenPausesForSixHours(){assertEquals(now+6*60*60*1_000L,NseArchiveRecoveryPolicy.cooldownUntilMs(403,null,now));assertTrue(NseArchiveRecoveryPolicy.shouldPauseBatch(403))}
 @Test fun rateLimitHonoursRetryAfterSeconds(){assertEquals(now+120_000L,NseArchiveRecoveryPolicy.cooldownUntilMs(429,"120",now))}
 @Test fun rateLimitUsesFallback(){assertEquals(now+NseArchiveRecoveryPolicy.RATE_LIMIT_FALLBACK_MS,NseArchiveRecoveryPolicy.cooldownUntilMs(429,null,now))}
 @Test fun retryAfterHttpDateIsParsed(){val v=Instant.ofEpochMilli(now+600_000L).atZone(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);assertEquals(now+600_000L,NseArchiveRecoveryPolicy.retryAfterAtMs(v,now))}
 @Test fun serverErrorsBackoff(){assertEquals(5*60*1_000L,NseArchiveRecoveryPolicy.transientBackoffMs(1));assertEquals(10*60*1_000L,NseArchiveRecoveryPolicy.transientBackoffMs(2));assertEquals(6*60*60*1_000L,NseArchiveRecoveryPolicy.transientBackoffMs(20));assertTrue(NseArchiveRecoveryPolicy.shouldPauseBatch(503))}
 @Test fun notFoundDoesNotCreateGlobalCooldown(){assertNull(NseArchiveRecoveryPolicy.cooldownUntilMs(404,null,now));assertFalse(NseArchiveRecoveryPolicy.shouldPauseBatch(404));assertEquals(now+30*60*1_000L,NseArchiveRecoveryPolicy.latestNotFoundRetryAtMs(now))}
 @Test fun invalidArchiveStopsBatch(){assertTrue(NseArchiveRecoveryPolicy.shouldPauseBatch(null,true));assertEquals("ARCHIVE_SCHEMA_OR_INTEGRITY",NseArchiveRecoveryPolicy.failureCode(null,true,false))}
}
