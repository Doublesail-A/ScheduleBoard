package com.doublesail.scheduleboard.display

import com.doublesail.scheduleboard.schedule.EventKind
import com.doublesail.scheduleboard.schedule.ScheduleEvent
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayPolicyTest {
    private fun time(value: String) = ZonedDateTime.parse("2026-11-10T$value+08:00[Asia/Shanghai]")
    private fun course(start: String, end: String) = ScheduleEvent(start, "测试课程", time(start).toInstant(), time(end).toInstant())
    private fun check(mode: DisplayMode, at: String, vararg events: ScheduleEvent) =
        assertEquals(at, mode, DisplayPolicy.evaluate(events.toList(), time(at)))

    @Test fun singleCourseBoundaries() {
        val event = course("08:00", "08:45")
        check(DisplayMode.SYSTEM, "08:09:59", event)
        check(DisplayMode.CLASS_REST, "08:10", event)
        check(DisplayMode.CLASS_REST, "08:39:59", event)
        check(DisplayMode.SYSTEM, "08:40", event)
        check(DisplayMode.SYSTEM, "09:00", event)
    }
    @Test fun entireMorningStaysDarkAcrossBreaksAndLaterStarts() {
        val events = arrayOf(course("08:00", "08:45"), course("09:05", "09:50"), course("10:05", "10:50"))
        for (at in listOf("08:40", "08:50", "09:05", "09:10", "09:49", "10:00", "10:44:59"))
            check(DisplayMode.CLASS_REST, at, *events)
        check(DisplayMode.SYSTEM, "10:45", *events)
    }
    @Test fun gapOverTwentyMinutesSeparatesBlocks() {
        val events = arrayOf(course("08:00", "08:45"), course("09:06", "09:50"))
        check(DisplayMode.SYSTEM, "08:40", *events)
        check(DisplayMode.SYSTEM, "08:55", *events)
        check(DisplayMode.SYSTEM, "09:15:59", *events)
        check(DisplayMode.CLASS_REST, "09:16", *events)
    }
    @Test fun secondsMatterForGap() {
        val events = arrayOf(course("08:00", "08:45"), course("09:05:01", "09:50"))
        check(DisplayMode.SYSTEM, "08:44", *events)
    }
    @Test fun overlappingNestedDuplicateAndUnsortedEventsUseUnion() {
        val events = arrayOf(course("09:00", "10:00"), course("08:00", "09:30"), course("08:10", "08:45"), course("08:00", "09:30"))
        check(DisplayMode.CLASS_REST, "09:40", *events)
        check(DisplayMode.SYSTEM, "09:55", *events)
    }
    @Test fun afternoonIsIndependentOfMorning() {
        val events = arrayOf(course("08:00", "10:00"), course("14:00", "16:00"))
        check(DisplayMode.SYSTEM, "12:00", *events)
        check(DisplayMode.SYSTEM, "14:09:59", *events)
        check(DisplayMode.CLASS_REST, "14:10", *events)
        check(DisplayMode.SYSTEM, "15:55", *events)
    }
    @Test fun nightTakesPriorityAndHasExactBoundaries() {
        check(DisplayMode.SYSTEM, "01:59:59")
        check(DisplayMode.NIGHT, "02:00")
        check(DisplayMode.NIGHT, "04:59:59", course("01:00", "06:00"))
        check(DisplayMode.SYSTEM, "05:00")
        check(DisplayMode.CLASS_REST, "05:00", course("01:00", "06:00"))
    }
    @Test fun nightUsesCurrentLocalZone() {
        val now = ZonedDateTime.parse("2026-11-10T19:00:00Z").withZoneSameInstant(java.time.ZoneId.of("Asia/Shanghai"))
        assertEquals(DisplayMode.NIGHT, DisplayPolicy.evaluate(emptyList(), now))
        assertEquals(DisplayMode.SYSTEM, DisplayPolicy.evaluate(emptyList(), now.withZoneSameInstant(java.time.ZoneId.of("UTC"))))
    }
    @Test fun allDayAndNonCourseEntriesCannotHideBoard() {
        val event = course("08:00", "18:00")
        check(DisplayMode.SYSTEM, "09:00", event.copy(allDay = true), event.copy(kind = EventKind.EVENT), event.copy(kind = EventKind.EXAM))
    }
    @Test fun tooShortCourseHasNoRestInterval() {
        check(DisplayMode.SYSTEM, "08:10", course("08:00", "08:15"))
        check(DisplayMode.SYSTEM, "08:10", course("08:00", "08:12"))
    }
    @Test fun emptyOrOtherDayScheduleDoesNotDimDaytime() {
        check(DisplayMode.SYSTEM, "09:00")
        val previous = course("08:00", "10:00").let { it.copy(start = it.start.minusSeconds(86400), endExclusive = it.endExclusive.minusSeconds(86400)) }
        check(DisplayMode.SYSTEM, "09:00", previous)
    }
    @Test fun classBlockAcrossMidnightIsNotTruncatedAtDateChange() {
        val event = course("23:00", "23:45").let { it.copy(endExclusive = time("01:00").plusDays(1).toInstant()) }
        val now = time("00:30").plusDays(1)
        assertEquals(DisplayMode.CLASS_REST, DisplayPolicy.evaluate(listOf(event), now))
    }
}
