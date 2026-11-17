package com.multify.traderpro.engine
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
class FixedEntryTimingStudyPolicyTest {
 @Test fun researchOnlyTimingWarningsAreNotBuyRules() {
  val at=LocalDateTime.of(2026,11,17,15,10).atZone(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
  val m=FixedEntryTimingStudyPolicy.assess(at-600000,100.0,at-30000,at,101.0,1.116,0.3,98.0)
  assertTrue(m.caution30Minutes)
  assertTrue(m.caution45Minutes)
  assertTrue(m.wouldWarnTargetCapacity)
  assertTrue(m.wouldWarnExtended)
  assertEquals(600000L,m.firstPrimaryToPublicationMs)
 }
}
