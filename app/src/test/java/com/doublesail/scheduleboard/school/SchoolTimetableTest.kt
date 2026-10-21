package com.doublesail.scheduleboard.school

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class SchoolTimetableTest {
    private fun table(body: String, changes: String = "") = "<html><table id=Table1><tr><td>标题</td></tr>" +
        "<tr><td>星期</td></tr>$body</table>$changes</html>"
    private val course = "植物学<br>周一第1,2节{第1-4周|单周}<br>庄振宏<br>创104"

    @Test fun parsesOddWeeksAndCourseMetadata() {
        val result = SchoolHtmlParser.parse(table("<tr><td>上午</td><td>第1节</td><td rowspan=2>$course</td></tr>")).single()
        assertEquals(setOf(1, 3), result.weeks)
        assertEquals("庄振宏", result.teacher)
        assertEquals(1, result.weekday)
        assertEquals(2, result.sections)
    }
    @Test fun rowspanKeepsFollowingRowsInCorrectWeekdayColumn() {
        val html = table("<tr><td rowspan=2>上午</td><td>第1节</td><td rowspan=2>$course</td><td></td></tr>" +
            "<tr><td>第2节</td><td>英语<br>{第1-4周}<br>教师B<br>教室B</td></tr>")
        val english = SchoolHtmlParser.parse(html).first { it.title == "英语" }
        assertEquals(2, english.weekday)
        assertEquals(2, english.firstSection)
    }
    @Test fun explicitCancellationRemovesOnlyAffectedWeeks() {
        val changes = "<table id=DBGrid><tr><td>标题</td></tr><tr><td>停1</td><td>植物学</td>" +
            "<td>周1第1节连续2节{第3周}/创104/庄振宏</td><td></td></tr></table>"
        val result = SchoolHtmlParser.parse(table("<tr><td>上午</td><td>第1节</td><td>$course<font>停1</font></td></tr>", changes)).single()
        assertEquals(setOf(1), result.weeks)
    }
    @Test fun explicitMoveChangesDayRoomAndTeacher() {
        val changes = "<table id=DBGrid><tr><td>标题</td></tr><tr><td>调1</td><td>植物学</td>" +
            "<td>周1第1节连续2节{第3周}/创104/庄振宏</td><td>周5第3节连续2节{第3周}/创105/教师B</td></tr></table>"
        val result = SchoolHtmlParser.parse(table("<tr><td>上午</td><td>第1节</td><td>$course<font>调1</font></td></tr>", changes))
        val moved = result.single { it.weekday == 5 }
        assertEquals(setOf(3), moved.weeks)
        assertEquals("创105", moved.location)
        assertEquals("教师B", moved.teacher)
    }
    @Test(expected = IllegalArgumentException::class) fun loginPageCannotBecomeEmptyTimetable() {
        SchoolHtmlParser.parse("<input name=txtUserName>")
    }
    @Test(expected = IllegalArgumentException::class) fun incompleteChangeRecordCannotSilentlyLeaveWrongLessons() {
        SchoolHtmlParser.parse(table("<tr><td>上午</td><td>第1节</td><td>$course</td></tr>",
            "<table id=DBGrid><tr><td>标题</td></tr><tr><td>调1</td><td>植物学</td></tr></table>"))
    }
    @Test fun calendarHolidayAndMakeupDatesAndBellTimesAreApplied() {
        val monday = LocalDate.parse("2026-08-31")
        val calendar = SchoolCalendar(LocalDate.parse("2026-08-30"), closed = setOf(monday, monday.plusWeeks(1)),
            moves = mapOf(monday to LocalDate.parse("2026-09-05")))
        val pattern = ClassPattern("课程", "教师", "教室", 1, setOf(1, 2, 3), 1, 2)
        val events = expandClasses(listOf(pattern), calendar, Campus.MAIN)
        assertEquals(2, events.size)
        assertEquals("[调课]课程", events.first().title)
        assertEquals("2026-09-05T08:00+08:00[Asia/Shanghai]", events.first().start.atZone(ZoneId.of("Asia/Shanghai")).toString())
        assertEquals("09:35", events.first().endExclusive.atZone(ZoneId.of("Asia/Shanghai")).toLocalTime().toString())
    }
    @Test fun qishanHasItsOwnBellTimes() {
        val pattern = ClassPattern("课程", "教师", "教室", 1, setOf(1), 1, 2)
        val event = expandClasses(listOf(pattern), SchoolCalendar(LocalDate.parse("2026-08-30")), Campus.QISHAN).single()
        assertEquals("08:20", event.start.atZone(ZoneId.of("Asia/Shanghai")).toLocalTime().toString())
        assertEquals("09:55", event.endExclusive.atZone(ZoneId.of("Asia/Shanghai")).toLocalTime().toString())
    }
}
