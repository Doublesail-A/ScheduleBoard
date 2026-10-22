package com.doublesail.scheduleboard.schedule

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class IcsParserTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun calendar(vararg events: String) = "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" + events.joinToString("\r\n") + "\r\nEND:VCALENDAR\r\n"
    private fun event(id: String = "lesson", extra: String = "") = "BEGIN:VEVENT\r\nUID:$id\r\n" +
        "DTSTART:20261021T084800Z\r\nDTEND:20261021T102000Z\r\nSUMMARY:植物学（双语课）\r\n" +
        "LOCATION:创104\r\nDESCRIPTION:教师：庄振宏\\n第 8 周\\n来自 iFAFU\r\n$extra\r\nEND:VEVENT"

    @Test fun importsIfafuUtcAndTeacherAndIgnoresAlarmDescription() {
        val parsed = IcsParser.parse(calendar(event(extra = "BEGIN:VALARM\r\nDESCRIPTION:提醒名称\r\nEND:VALARM")), zone).single()
        assertEquals(Instant.parse("2026-10-21T08:48:00Z"), parsed.start)
        assertEquals("庄振宏", parsed.teacher)
        assertEquals("创104", parsed.location)
        assertEquals(EventKind.COURSE, parsed.kind)
    }
    @Test fun identicalStableUidsAreDeduplicated() {
        assertEquals(1, IcsParser.parse(calendar(event(), event()), zone).size)
    }
    @Test(expected = IllegalArgumentException::class) fun conflictingUidsAreRejected() {
        IcsParser.parse(calendar(event(), event().replace("创104", "创105")), zone)
    }
    @Test fun unfoldsUnicodeAndTextEscapes() {
        val input = event().replace("SUMMARY:植物学（双语课）", "SUMMARY:植物学\\,双语\\;课\\\\题\r\n 继续")
        assertEquals("植物学,双语;课\\题继续", IcsParser.parse(calendar(input), zone).single().title)
    }
    @Test fun parsesQuotedTzid() {
        val input = event().replace("DTSTART:20261021T084800Z", "DTSTART;TZID=\"Asia/Shanghai\":20261021T164800")
        assertEquals(Instant.parse("2026-10-21T08:48:00Z"), IcsParser.parse(calendar(input), zone).single().start)
    }
    @Test fun allDayEndIsExclusive() {
        val input = "BEGIN:VEVENT\nUID:day\nDTSTART;VALUE=DATE:20261021\nDTEND;VALUE=DATE:20261022\nSUMMARY:日程\nEND:VEVENT"
        val parsed = IcsParser.parse(calendar(input), zone).single()
        assertTrue(parsed.allDay)
        assertEquals(Instant.parse("2026-10-21T16:00:00Z"), parsed.endExclusive)
    }
    @Test(expected = IllegalArgumentException::class) fun unsupportedRecurrenceIsNotSilentlyLost() {
        IcsParser.parse(calendar(event(extra = "RRULE:FREQ=WEEKLY;COUNT=16")), zone)
    }
    @Test(expected = IllegalArgumentException::class) fun incompleteCalendarIsRejected() {
        IcsParser.parse(calendar(event()).removeSuffix("END:VCALENDAR\r\n"), zone)
    }
    @Test(expected = IllegalArgumentException::class) fun emptyImportIsRejected() {
        IcsParser.parse(calendar(), zone)
    }
    @Test fun cancelledEventsAreNotShown() {
        assertEquals(1, IcsParser.parse(calendar(event(), event("cancelled", "STATUS:CANCELLED")), zone).size)
    }
    @Test fun manyFoldedLinesPreserveTheirContent() {
        val title = "课".repeat(100_000)
        val folded = "SUMMARY:" + "课\r\n ".repeat(99_999) + "课"
        val input = event().replace("SUMMARY:植物学（双语课）", folded)
        assertEquals(title, IcsParser.parse(calendar(input), zone).single().title)
    }
    @Test fun lineEndingsBomAndTabContinuationsKeepExistingContent() {
        val input = "\uFEFF" + calendar(event().replace("SUMMARY:植物学（双语课）", "SUMMARY:植物学\r\n\t（双语课）"))
        for (ending in listOf("\r\n", "\n", "\r")) {
            val parsed = IcsParser.parse(input.replace("\r\n", ending), zone).single()
            assertEquals("植物学（双语课）", parsed.title)
        }
    }
    @Test fun concatenatedCalendarsKeepExistingImportBehavior() {
        assertEquals(2, IcsParser.parse(calendar(event()) + calendar(event("second")), zone).size)
    }
    @Test fun nestedCalendarsKeepExistingImportBehavior() {
        assertEquals(1, IcsParser.parse(calendar(calendar(event())), zone).size)
    }
    @Test fun allDayDurationKeepsExistingOneDayFallback() {
        val input = "BEGIN:VEVENT\nUID:day\nDTSTART;VALUE=DATE:20261021\nDURATION:P3D\nSUMMARY:日程\nEND:VEVENT"
        val parsed = IcsParser.parse(calendar(input), zone).single()
        assertEquals(Instant.parse("2026-10-21T16:00:00Z"), parsed.endExclusive)
    }
}
