package com.doublesail.scheduleboard.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEventTest {
    private val utc = ZoneId.of("UTC")

    @Test fun eventEndingAtMidnightDoesNotAppearOnFollowingDay() {
        val event = event("2026-10-20T22:00:00Z", "2026-10-21T00:00:00Z")
        assertTrue(event.occursOn(LocalDate.parse("2026-10-20"), utc))
        assertFalse(event.occursOn(LocalDate.parse("2026-10-21"), utc))
    }

    @Test fun overnightEventAppearsOnBothDays() {
        val event = event("2026-10-20T23:00:00Z", "2026-10-21T01:00:00Z")
        assertTrue(event.occursOn(LocalDate.parse("2026-10-20"), utc))
        assertTrue(event.occursOn(LocalDate.parse("2026-10-21"), utc))
    }

    @Test fun daylightSavingDayUsesLocalMidnights() {
        val currentDayEvent = event("2026-03-09T03:30:00Z", "2026-03-09T03:45:00Z")
        assertTrue(currentDayEvent.occursOn(LocalDate.parse("2026-03-08"), ZoneId.of("America/New_York")))
        assertFalse(currentDayEvent.occursOn(LocalDate.parse("2026-03-09"), ZoneId.of("America/New_York")))
        val nextDayEvent = event("2026-03-09T04:30:00Z", "2026-03-09T04:45:00Z")
        assertFalse(nextDayEvent.occursOn(LocalDate.parse("2026-03-08"), ZoneId.of("America/New_York")))
        assertTrue(nextDayEvent.occursOn(LocalDate.parse("2026-03-09"), ZoneId.of("America/New_York")))
    }

    @Test fun displayZoneDeterminesCalendarDay() {
        val event = event("2026-10-20T18:00:00Z", "2026-10-20T19:00:00Z")
        assertTrue(event.occursOn(LocalDate.parse("2026-10-21"), ZoneId.of("Asia/Shanghai")))
        assertFalse(event.occursOn(LocalDate.parse("2026-10-20"), ZoneId.of("Asia/Shanghai")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidIntervalIsRejected() {
        event("2026-10-21T00:00:00Z", "2026-10-21T00:00:00Z")
    }

    private fun event(start: String, end: String) = ScheduleEvent(
        "event", "Event", Instant.parse(start), Instant.parse(end),
    )
}
