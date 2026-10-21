package com.doublesail.scheduleboard.schedule

import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class UpcomingEventsTest {
    private val now = ZonedDateTime.parse("2026-10-21T16:35:00+08:00[Asia/Shanghai]")
    private fun event(start: String, end: String = "2026-10-21T18:00:00+08:00[Asia/Shanghai]") =
        ScheduleEvent(start, "课程", ZonedDateTime.parse(start).toInstant(), ZonedDateTime.parse(end).toInstant())
    @Test fun minuteCountdownMatchesReference() {
        assertEquals("13分钟后上课", countdownText(event("2026-10-21T16:48:00+08:00[Asia/Shanghai]"), now))
    }
    @Test fun partialMinuteRoundsUpAndStartSwitchesToLessonEnd() {
        val course = event("2026-10-21T16:35:30+08:00[Asia/Shanghai]")
        assertEquals("1分钟后上课", countdownText(course, now))
        assertEquals("1小时25分后下课", countdownText(course, now.plusSeconds(30)))
    }
    @Test fun exactHourDoesNotAddZeroMinutes() {
        val course = event("2026-10-21T18:35:00+08:00[Asia/Shanghai]", "2026-10-21T20:00:00+08:00[Asia/Shanghai]")
        assertEquals("2小时后上课", countdownText(course, now))
    }
    @Test fun nextDayIsExplicit() {
        val course = event("2026-10-22T08:00:00+08:00[Asia/Shanghai]", "2026-10-22T09:35:00+08:00[Asia/Shanghai]")
        assertEquals("明天08:00上课", countdownText(course, now))
    }
    @Test fun activeLessonsRemainAndEndedLessonsDisappearAtEnd() {
        val active = event("2026-10-21T16:00:00+08:00[Asia/Shanghai]")
        val ended = event("2026-10-21T14:00:00+08:00[Asia/Shanghai]", now.toString())
        val future = event("2026-10-21T17:00:00+08:00[Asia/Shanghai]")
        assertEquals(listOf(active, future), upcomingEvents(listOf(future, ended, active), now))
    }
    @Test fun personalEventsUseStartAndEndLabels() {
        val course = event("2026-10-21T16:48:00+08:00[Asia/Shanghai]").copy(kind = EventKind.EVENT)
        assertEquals("13分钟后开始", countdownText(course, now))
    }
}
