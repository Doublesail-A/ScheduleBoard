package com.doublesail.scheduleboard.schedule

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Imports expanded VEVENTs as exported by iFAFU. Unsupported recurrence is rejected. */
object IcsParser {
    private val dateFormat = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT)
    private val timeFormat = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss").withResolverStyle(ResolverStyle.STRICT)
    private val shortTimeFormat = DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmm").withResolverStyle(ResolverStyle.STRICT)
    private data class Property(val name: String, val parameters: Map<String, String>, val value: String)

    fun parse(text: String, defaultZone: ZoneId): List<ScheduleEvent> {
        require(text.length <= 4 * 1024 * 1024) { "课表文件过大" }
        val lines = mutableListOf<String>()
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if (line.startsWith(' ') || line.startsWith('\t')) {
                require(lines.isNotEmpty()) { "ICS 折行格式不正确" }
                lines[lines.lastIndex] += line.substring(1)
            } else if (line.isNotBlank()) lines += line
        }
        require(lines.firstOrNull()?.uppercase() == "BEGIN:VCALENDAR" &&
            lines.lastOrNull()?.uppercase() == "END:VCALENDAR") { "请选择 iFAFU 导出的完整 ICS 课表" }
        var zone = defaultZone
        var current: MutableMap<String, Property>? = null
        val components = ArrayDeque<String>()
        val events = linkedMapOf<String, ScheduleEvent>()
        var seenEvents = 0
        for (line in lines) {
            val property = property(line)
            if (property.name == "BEGIN") {
                val component = property.value.uppercase()
                if (component == "VEVENT") {
                    require(current == null && components.lastOrNull() == "VCALENDAR") { "ICS 事件结构不正确" }
                    current = linkedMapOf()
                }
                components.addLast(component)
                continue
            }
            if (property.name == "END") {
                val component = property.value.uppercase()
                require(components.removeLastOrNull() == component) { "ICS 事件结构不完整" }
                if (component == "VEVENT") {
                    seenEvents++
                    require(seenEvents <= 10_000) { "课表事件过多" }
                    val fields = requireNotNull(current)
                    if (fields["STATUS"]?.value?.uppercase() != "CANCELLED") {
                        val event = event(fields, zone)
                        require(events[event.id] == null || events[event.id] == event) { "课表中存在冲突的重复 UID" }
                        events[event.id] = event
                    }
                    current = null
                }
                continue
            }
            if (components.lastOrNull() == "VCALENDAR" && property.name == "X-WR-TIMEZONE") {
                zone = ZoneId.of(property.value.trim())
            }
            if (components.lastOrNull() == "VEVENT") {
                require(property.name !in setOf("RRULE", "RDATE", "EXDATE", "RECURRENCE-ID")) {
                    "此 ICS 包含重复规则，请使用 iFAFU 的完整课表导出功能"
                }
                if (property.name in setOf("UID", "DTSTART", "DTEND", "SUMMARY", "LOCATION", "DESCRIPTION", "STATUS")) {
                    require(current?.put(property.name, property) == null) { "ICS 事件字段重复" }
                }
            }
        }
        require(components.isEmpty() && current == null) { "ICS 结构不完整" }
        require(events.isNotEmpty()) { "文件中没有可导入的有效安排，原课表已保留" }
        return events.values.sortedWith(compareBy({ it.start }, { it.id }))
    }

    private fun property(line: String): Property {
        var quoted = false
        val separator = line.indexOfFirst { character ->
            if (character == '"') quoted = !quoted
            character == ':' && !quoted
        }
        require(separator > 0) { "ICS 属性格式不正确" }
        val head = line.substring(0, separator).split(';')
        val parameters = head.drop(1).associate { parameter ->
            val pair = parameter.split('=', limit = 2)
            require(pair.size == 2) { "ICS 属性参数不正确" }
            pair[0].uppercase() to pair[1].trim('"')
        }
        return Property(head[0].uppercase(), parameters, line.substring(separator + 1))
    }

    private fun event(fields: Map<String, Property>, zone: ZoneId): ScheduleEvent {
        val startField = requireNotNull(fields["DTSTART"]) { "课表事件缺少开始时间" }
        val allDay = startField.parameters["VALUE"] == "DATE" || startField.value.length == 8
        val start = instant(startField, zone)
        val endField = fields["DTEND"]
        require(endField == null || (endField.value.length == 8) == allDay) { "事件起止时间类型不一致" }
        val end = if (endField != null) instant(endField, zone) else {
            require(allDay) { "课表事件缺少结束时间" }
            LocalDate.parse(startField.value, dateFormat).plusDays(1).atStartOfDay(zone).toInstant()
        }
        val title = unescape(fields["SUMMARY"]?.value.orEmpty()).trim()
        require(title.isNotEmpty()) { "课表事件缺少名称" }
        val location = unescape(fields["LOCATION"]?.value.orEmpty())
        val description = unescape(fields["DESCRIPTION"]?.value.orEmpty())
        val teacherLine = description.lineSequence().map(String::trim)
            .firstOrNull { it.startsWith("教师：") || it.startsWith("教师:") }
        val teacher = teacherLine?.drop(3)?.trim().orEmpty()
        val kind = when {
            description.trimStart().startsWith("考试") -> EventKind.EXAM
            teacherLine != null -> EventKind.COURSE
            else -> EventKind.EVENT
        }
        val id = fields["UID"]?.value?.takeIf(String::isNotBlank) ?: MessageDigest.getInstance("SHA-256")
            .digest("$title|$start|$end|$location".toByteArray()).joinToString("") { "%02x".format(it) }
        return ScheduleEvent(id, title, start, end, allDay, location, teacher, description, kind)
    }

    private fun instant(property: Property, fallback: ZoneId): Instant {
        val value = property.value
        if (value.length == 8) return LocalDate.parse(value, dateFormat).atStartOfDay(fallback).toInstant()
        val zone = if (value.endsWith('Z')) ZoneOffset.UTC
            else property.parameters["TZID"]?.let(ZoneId::of) ?: fallback
        val local = value.removeSuffix("Z")
        val format = if (local.length == 13) shortTimeFormat else timeFormat
        return LocalDateTime.parse(local, format).atZone(zone).toInstant()
    }

    private fun unescape(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val character = value[index++]
            if (character == '\\' && index < value.length) {
                val escaped = value[index++]
                append(if (escaped == 'n' || escaped == 'N') '\n' else escaped)
            } else append(character)
        }
    }
}
