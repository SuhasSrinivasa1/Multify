package com.multify.traderpro.data.repository
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
/** Conservative retry policy for public NSE EOD archives. */
object NseArchiveRecoveryPolicy {
 const val REQUEST_MIN_GAP_MS=1_500L
 const val ACCESS_DENIED_COOLDOWN_MS=6L*60L*60L*1000L
 const val RATE_LIMIT_FALLBACK_MS=30L*60L*1000L
 const val LATEST_NOT_FOUND_RETRY_MS=30L*60L*1000L
 const val INVALID_ARCHIVE_COOLDOWN_MS=24L*60L*60L*1000L
 private const val MAX_TRANSIENT_BACKOFF_MS=6L*60L*60L*1000L
 fun retryAfterAtMs(header:String?,nowMs:Long):Long? {
  val value=header?.trim()?.takeIf{it.isNotEmpty()}?:return null
  value.toLongOrNull()?.let{return nowMs+it.coerceAtLeast(1L).coerceAtMost(86_400L)*1_000L}
  return runCatching{ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli().coerceAtLeast(nowMs)}.getOrNull()
 }
 fun transientBackoffMs(failureCount:Int):Long {
  val step=(failureCount.coerceAtLeast(1)-1).coerceAtMost(6)
  return (5L*60L*1000L*(1L shl step)).coerceAtMost(MAX_TRANSIENT_BACKOFF_MS)
 }
 fun cooldownUntilMs(statusCode:Int?,retryAfterHeader:String?,nowMs:Long,consecutiveTransientFailures:Int=1):Long?=when(statusCode){
  403->maxOf(nowMs+ACCESS_DENIED_COOLDOWN_MS,retryAfterAtMs(retryAfterHeader,nowMs)?:nowMs)
  429->retryAfterAtMs(retryAfterHeader,nowMs)?:nowMs+RATE_LIMIT_FALLBACK_MS
  in 500..599->nowMs+transientBackoffMs(consecutiveTransientFailures)
  else->null
 }
 fun latestNotFoundRetryAtMs(nowMs:Long)=nowMs+LATEST_NOT_FOUND_RETRY_MS
 fun shouldPauseBatch(statusCode:Int?,invalidArchive:Boolean=false)=invalidArchive||statusCode==403||statusCode==429||(statusCode!=null&&statusCode in 500..599)
 fun failureCode(statusCode:Int?,invalidArchive:Boolean,networkFailure:Boolean):String=when{
  invalidArchive->"ARCHIVE_SCHEMA_OR_INTEGRITY"
  statusCode==403->"HTTP_403_ACCESS_DENIED"
  statusCode==429->"HTTP_429_RATE_LIMITED"
  statusCode==404->"HTTP_404_NOT_FOUND_OR_HOLIDAY"
  statusCode!=null&&statusCode in 500..599->"HTTP_${statusCode}_SERVER_ERROR"
  networkFailure->"NETWORK_OR_TIMEOUT"
  statusCode!=null->"HTTP_${statusCode}_OTHER"
  else->"ARCHIVE_FAILURE"
 }
}
