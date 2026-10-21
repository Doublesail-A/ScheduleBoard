package com.doublesail.scheduleboard.school

import com.doublesail.scheduleboard.schedule.ScheduleEvent
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class ClassPattern(
    val title: String, val teacher: String, val location: String,
    val weekday: Int, val weeks: Set<Int>, val firstSection: Int, val sections: Int,
    val changes: Set<String> = emptySet(),
)

data class SchoolCalendar(
    val firstWeek: LocalDate, val closed: Set<LocalDate> = emptySet(),
    val moves: Map<LocalDate, LocalDate> = emptyMap(), val source: String = "",
)

enum class Campus(val label: String, val starts: List<Int>) {
    MAIN("本部", listOf(800, 850, 955, 1045, 1135, 1400, 1450, 1550, 1640, 1825, 1915, 2005)),
    QISHAN("旗山", listOf(820, 910, 1010, 1100, 1150, 1410, 1500, 1555, 1645, 1825, 1915, 2005)),
}

/** Same Sunday-based week convention and bell times as iFAFU; no estimated semester dates. */
fun expandClasses(patterns: List<ClassPattern>, calendar: SchoolCalendar, campus: Campus): List<ScheduleEvent> {
    val zone = ZoneId.of("Asia/Shanghai")
    val sunday = calendar.firstWeek.minusDays((calendar.firstWeek.dayOfWeek.value % 7).toLong())
    return patterns.flatMap { course ->
        require(course.weekday in 1..7 && course.firstSection in 1..12 && course.sections in 1..12 &&
            course.firstSection + course.sections - 1 <= 12) { "教务课程节次超出支持范围" }
        course.weeks.sorted().mapNotNull { week ->
            require(week in 1..60) { "课程周次不正确" }
            val original = sunday.plusDays((week - 1) * 7L + course.weekday % 7)
            val destination = calendar.moves[original]
            if (destination == null && original in calendar.closed) return@mapNotNull null
            val date = destination ?: original
            fun time(section: Int): LocalTime {
                val value = campus.starts[section - 1]
                return LocalTime.of(value / 100, value % 100)
            }
            val start = date.atTime(time(course.firstSection)).atZone(zone).toInstant()
            val end = date.atTime(time(course.firstSection + course.sections - 1).plusMinutes(45)).atZone(zone).toInstant()
            val title = (if (destination != null) "[调课]" else "") + course.title
            val id = MessageDigest.getInstance("SHA-256").digest(
                "${course.title}|${course.teacher}|${course.location}|$start|$end".toByteArray(),
            ).joinToString("") { "%02x".format(it) }
            ScheduleEvent(id, title, start, end, location = course.location, teacher = course.teacher,
                description = "教师：${course.teacher}\n第 $week 周 · 第 ${course.firstSection}–${course.firstSection + course.sections - 1} 节\n直接同步自福建农林大学教务")
        }
    }.distinctBy { it.id }.sortedBy { it.start }
}
