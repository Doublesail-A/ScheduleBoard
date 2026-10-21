package com.doublesail.scheduleboard.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class EventKind { COURSE, EVENT, EXAM }

/** endExclusive is exclusive, including for all-day entries. */
data class ScheduleEvent(
    val id: String,
    val title: String,
    val start: Instant,
    val endExclusive: Instant,
    val allDay: Boolean = false,
    val location: String = "",
    val teacher: String = "",
    val description: String = "",
    val kind: EventKind = EventKind.COURSE,
) {
    init {
        require(endExclusive > start) { "Event end must be after its start" }
    }

    fun occursOn(date: LocalDate, zone: ZoneId): Boolean {
        val dayStart = date.atStartOfDay(zone).toInstant()
        val nextDayStart = date.plusDays(1).atStartOfDay(zone).toInstant()
        return start < nextDayStart && endExclusive > dayStart
    }
}

/** CalendarProvider, local storage, or a remote source can implement this contract later. */
interface ScheduleRepository {
    val snapshot: Flow<ScheduleSnapshot>
    fun observeEvents(date: LocalDate, zone: ZoneId): Flow<List<ScheduleEvent>> =
        snapshot.map { data -> data.events.filter { it.occursOn(date, zone) } }
}

data class ScheduleSnapshot(
    val events: List<ScheduleEvent> = emptyList(),
    val sourceName: String = "",
    val importedAt: Instant? = null,
    val error: String? = null,
)
