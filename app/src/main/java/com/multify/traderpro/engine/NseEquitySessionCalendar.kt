package com.multify.traderpro.engine

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * NSE cash-equity session windows. Only an explicitly published special session
 * can override the normal Monday-Friday schedule. All comparisons are in IST.
 *
 * For 2026-11-08 the CAS stock continuous session closes at 15:15 and the
 * non-CAS session at 15:30. Since Fixed has no authoritative CAS membership
 * source, actionable Fixed scans stop conservatively at 15:15; portfolio
 * monitoring and research-only observations may continue to 15:30;
 * the broker remains authoritative for whether a given instrument can trade.
 *
 * Source: NSE/CMTR/76941 (2026-10-19).
 */
object NseEquitySessionCalendar {
    private val india = ZoneId.of("Asia/Kolkata")
    private val muhurat2026 = LocalDate.of(2026, 11, 8)
    private val normalOpen = LocalTime.of(9, 15)
    private val normalClose = LocalTime.of(15, 30)
    private val muhuratPreOpen = LocalTime.of(14, 5)
    private val muhuratAppStart = LocalTime.of(14, 0)
    private val muhuratOpen = LocalTime.of(14, 20)
    private val muhuratCasClose = LocalTime.of(15, 15)

    private fun local(at: ZonedDateTime): ZonedDateTime = at.withZoneSameInstant(india)
    private fun inWindow(time: LocalTime, from: LocalTime, until: LocalTime): Boolean =
        !time.isBefore(from) && time.isBefore(until)
    private fun isWeekday(at: ZonedDateTime): Boolean =
        at.dayOfWeek != DayOfWeek.SATURDAY && at.dayOfWeek != DayOfWeek.SUNDAY

    fun isSpecialMuhurat2026(at: ZonedDateTime): Boolean =
        local(at).toLocalDate() == muhurat2026

    /** Market-data and position monitoring; CAS-specific broker market status still governs trading. */
    fun isOpen(at: ZonedDateTime): Boolean {
        val now = local(at)
        return if (isSpecialMuhurat2026(now))
            inWindow(now.toLocalTime(), muhuratOpen, normalClose)
        else isWeekday(now) && inWindow(now.toLocalTime(), normalOpen, normalClose)
    }

    /** Fixed full-market scan. Never scan into the CAS auction/closed portion of Muhurat. */
    fun isFixedScanWindow(at: ZonedDateTime): Boolean {
        val now = local(at)
        return if (isSpecialMuhurat2026(now))
            inWindow(now.toLocalTime(), muhuratOpen, muhuratCasClose)
        else isWeekday(now) && inWindow(now.toLocalTime(), normalOpen, normalClose)
    }

    fun isWarmupWindow(at: ZonedDateTime): Boolean {
        val now = local(at)
        return if (isSpecialMuhurat2026(now))
            inWindow(now.toLocalTime(), muhuratAppStart, muhuratOpen)
        else isWeekday(now) && inWindow(now.toLocalTime(), LocalTime.of(9, 0), normalOpen)
    }

    /** Research-only monitoring tail for non-CAS stocks; NO Fixed publications or orders. */
    fun isMuhuratResearchTail(at: ZonedDateTime): Boolean {
        val now = local(at)
        return isSpecialMuhurat2026(now) &&
            inWindow(now.toLocalTime(), muhuratCasClose, normalClose)
    }

    /** Calendar startup trigger; broker work still requires valid data/authentication. */
    fun startupAt(date: LocalDate): ZonedDateTime? = when {
        date == muhurat2026 -> date.atTime(muhuratAppStart).atZone(india)
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> null
        else -> date.atTime(LocalTime.of(9, 0)).atZone(india)
    }

    fun nextStartupAfter(at: ZonedDateTime): ZonedDateTime {
        val now = local(at)
        for (offset in 0..8) {
            val result = startupAt(now.toLocalDate().plusDays(offset.toLong()))
            if (result != null && result.isAfter(now)) return result
        }
        error("No upcoming NSE weekday/special session found")
    }

    /** The usual 15-minute entry cushion applies before the earliest relevant close. */
    fun isFixedNewEntryWindow(at: ZonedDateTime): Boolean {
        val now = local(at)
        val cutoff = if (isSpecialMuhurat2026(now)) LocalTime.of(15, 0) else LocalTime.of(15, 15)
        return isFixedScanWindow(now) && now.toLocalTime().isBefore(cutoff)
    }

    fun mayPrepareNextFixedSweep(at: ZonedDateTime): Boolean {
        val now = local(at)
        return if (isSpecialMuhurat2026(now))
            inWindow(now.toLocalTime(), LocalTime.of(14, 21), LocalTime.of(15, 0))
        else isWeekday(now) && inWindow(now.toLocalTime(), LocalTime.of(9, 16), LocalTime.of(15, 17))
    }

    fun mayContinuePreparedFixedSweep(at: ZonedDateTime): Boolean {
        val now = local(at)
        return if (isSpecialMuhurat2026(now))
            now.toLocalTime().isBefore(LocalTime.of(15, 8))
        else isWeekday(now) && now.toLocalTime().isBefore(LocalTime.of(15, 25))
    }
}
