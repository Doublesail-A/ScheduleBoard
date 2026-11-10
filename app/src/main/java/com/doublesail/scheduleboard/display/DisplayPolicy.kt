package com.doublesail.scheduleboard.display

import com.doublesail.scheduleboard.schedule.EventKind
import com.doublesail.scheduleboard.schedule.ScheduleEvent
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

enum class DisplayMode { SYSTEM, CLASS_REST, NIGHT }

/** All boundaries are inclusive at the start and exclusive at the end. */
object DisplayPolicy {
    fun evaluate(events: List<ScheduleEvent>, now: ZonedDateTime): DisplayMode {
        val time = now.toLocalTime()
        if (time >= LocalTime.of(2, 0) && time < LocalTime.of(5, 0)) return DisplayMode.NIGHT
        val courses = events.filter { !it.allDay && it.kind == EventKind.COURSE }.sortedBy { it.start }
        var start: java.time.Instant? = null
        var end: java.time.Instant? = null
        fun containsNow(): Boolean {
            val from = start ?: return false
            val until = end ?: return false
            return now.toInstant() >= from.plus(Duration.ofMinutes(10)) &&
                now.toInstant() < until.minus(Duration.ofMinutes(5))
        }
        for (course in courses) {
            val previousEnd = end
            if (previousEnd != null && course.start <= previousEnd.plus(Duration.ofMinutes(20))) {
                end = maxOf(previousEnd, course.endExclusive)
            } else {
                if (containsNow()) return DisplayMode.CLASS_REST
                start = course.start
                end = course.endExclusive
            }
        }
        return if (containsNow()) DisplayMode.CLASS_REST else DisplayMode.SYSTEM
    }
}
