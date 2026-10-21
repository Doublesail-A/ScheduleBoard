package com.doublesail.scheduleboard.schedule

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun upcomingEvents(events: List<ScheduleEvent>, now: ZonedDateTime, limit: Int = 2): List<ScheduleEvent> =
    events.asSequence().filter { it.endExclusive > now.toInstant() }
        .sortedWith(compareBy<ScheduleEvent> { it.start }.thenBy { it.id }).take(limit).toList()

fun countdownText(event: ScheduleEvent, now: ZonedDateTime): String {
    if (now.toInstant() >= event.endExclusive) return "已结束"
    if (event.allDay) {
        val date = event.start.atZone(now.zone).toLocalDate()
        return when {
            date <= now.toLocalDate() -> "全天"
            date == now.toLocalDate().plusDays(1) -> "明天全天"
            else -> date.format(DateTimeFormatter.ofPattern("M月d日")) + "全天"
        }
    }
    val active = now.toInstant() >= event.start && now.toInstant() < event.endExclusive
    val action = when (event.kind) {
        EventKind.COURSE -> if (active) "下课" else "上课"
        EventKind.EXAM -> if (active) "结束" else "开考"
        EventKind.EVENT -> if (active) "结束" else "开始"
    }
    val localStart = event.start.atZone(now.zone)
    if (!active && localStart.toLocalDate() != now.toLocalDate()) {
        val prefix = if (localStart.toLocalDate() == now.toLocalDate().plusDays(1)) "明天" else
            localStart.format(DateTimeFormatter.ofPattern("M月d日"))
        return prefix + localStart.format(DateTimeFormatter.ofPattern("HH:mm")) + action
    }
    val target = if (active) event.endExclusive else event.start
    val minutes = (Duration.between(now.toInstant(), target).toMillis().coerceAtLeast(1) + 59_999) / 60_000
    val duration = when {
        minutes < 60 -> "${minutes}分钟"
        minutes % 60 == 0L -> "${minutes / 60}小时"
        else -> "${minutes / 60}小时${minutes % 60}分"
    }
    return "${duration}后$action"
}
