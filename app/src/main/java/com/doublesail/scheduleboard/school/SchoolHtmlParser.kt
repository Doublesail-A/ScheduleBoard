package com.doublesail.scheduleboard.school

import org.jsoup.Jsoup

/** Legacy 正方 Table1/DBGrid format documented by woolsen/iFAFU and Doublesail-A/iFAFU. */
object SchoolHtmlParser {
    fun parse(html: String): List<ClassPattern> {
        val document = Jsoup.parse(html)
        val table = requireNotNull(document.getElementById("Table1")) { "教务未返回课表，请重新登录" }
        val patterns = mutableListOf<ClassPattern>()
        val occupied = mutableSetOf<Pair<Int, Int>>()
        table.select("tr").drop(2).forEachIndexed { row, tr ->
            var column = 0
            var section = 0
            tr.children().forEach { cell ->
                while ((row to column) in occupied) column++
                val span = cell.attr("rowspan").toIntOrNull()?.coerceIn(1, 20) ?: 1
                val width = cell.attr("colspan").toIntOrNull()?.coerceIn(1, 12) ?: 1
                for (r in row until row + span) for (c in column until column + width) occupied += r to c
                val plain = cell.text().trim()
                val index = Regex("^第(\\d+)节$").matchEntire(plain)
                if (index != null) section = index.groupValues[1].toInt()
                else if (plain.isNotEmpty() && plain !in listOf("上午", "中午", "下午", "晚上", "早晨") && column in 2..8) {
                    val blocks = cell.html().replace("&nbsp;", "").split(Regex("(?:<br\\s*/?>\\s*){2,}"))
                    blocks.filter(String::isNotBlank).forEach { raw ->
                        if (raw.trimStart().startsWith("<font")) {
                            if (patterns.isNotEmpty()) patterns[patterns.lastIndex] = patterns.last().copy(
                                changes = patterns.last().changes + references(raw))
                        } else {
                            val fragment = Jsoup.parseBodyFragment(raw)
                            fragment.select("font").remove()
                            val lines = fragment.body().html().split(Regex("<br\\s*/?>"))
                                .map { Jsoup.parseBodyFragment(it).text().trim() }
                            if (lines.size >= 2 && lines[1].contains("周")) {
                                patterns += pattern(lines, column - 1, section, span, references(raw))
                            } else if (lines.any(String::isNotBlank)) error("课程格式发生变化，原课表已保留")
                        }
                    }
                }
                column += width
            }
        }
        document.getElementById("DBGrid")?.select("tr")?.drop(1)?.forEach { row ->
            val cells = row.children().map { it.text().trim() }
            if (cells.firstOrNull()?.firstOrNull() !in listOf('调', '停', '换', '补')) return@forEach
            require(cells.size >= 4) { "教务调课记录不完整，原课表已保留" }
            val id = cells[0]
            val name = cells[1]
            if (id.startsWith("补")) {
                patterns += change(cells[3], name)
            } else {
                val before = change(cells[2], name)
                val affected = patterns.filter { it.title == name && it.weekday == before.weekday &&
                    it.firstSection == before.firstSection && it.sections == before.sections &&
                    (id in it.changes || it.weeks.any(before.weeks::contains)) }
                affected.forEach { old ->
                    patterns.remove(old)
                    patterns += old.copy(weeks = old.weeks - before.weeks)
                }
                if (!id.startsWith("停") && affected.isNotEmpty()) patterns += change(cells[3], name)
            }
        }
        val mergedWeeks = patterns.filter { it.weeks.isNotEmpty() }.groupBy {
            listOf(it.title, it.teacher, it.location, it.weekday, it.firstSection, it.sections)
        }.values.map { group -> group.first().copy(weeks = group.flatMap { it.weeks }.toSet()) }
        val merged = mutableListOf<ClassPattern>()
        mergedWeeks.sortedWith(compareBy({ it.title }, { it.weekday }, { it.firstSection })).forEach { course ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.title == course.title && previous.teacher == course.teacher &&
                previous.location == course.location && previous.weekday == course.weekday && previous.weeks == course.weeks &&
                previous.firstSection + previous.sections == course.firstSection) {
                merged[merged.lastIndex] = previous.copy(sections = previous.sections + course.sections)
            } else merged += course
        }
        return merged
    }

    private fun pattern(lines: List<String>, weekday: Int, first: Int, span: Int, changes: Set<String>): ClassPattern {
        val text = lines[1]
        val day = Regex("周([一二三四五六日天1-7])").find(text)?.groupValues?.get(1)?.let {
            when (it) { "日", "天" -> 7; else -> it.toIntOrNull() ?: "一二三四五六".indexOf(it) + 1 }
        } ?: weekday
        val nodes = Regex("第([0-9,，-]+)节").find(text)?.groupValues?.get(1)?.let {
            Regex("\\d+").findAll(it).map { result -> result.value.toInt() }.toList()
        }.orEmpty()
        val start = nodes.firstOrNull() ?: first
        val length = if (nodes.isNotEmpty()) nodes.last() - start + 1 else
            Regex("(\\d+)节[/\\\\]周").find(text)?.groupValues?.get(1)?.toInt() ?: span
        require(day in 1..7 && start in 1..12 && length in 1..12) { "无法确定课程星期或节次" }
        return ClassPattern(lines[0], lines.getOrElse(2) { "" }, lines.getOrElse(3) { "" },
            day, weeks(text), start, length, changes)
    }

    private fun weeks(text: String): Set<Int> {
        val range = requireNotNull(Regex("第(\\d+)(?:-(\\d+))?周").find(text)) { "无法解析课程周次" }
        val first = range.groupValues[1].toInt()
        val last = range.groupValues[2].toIntOrNull() ?: first
        require(first in 1..60 && last in first..60) { "课程周次超出范围" }
        return (first..last).filter { when { text.contains("单周") -> it % 2 == 1; text.contains("双周") -> it % 2 == 0; else -> true } }.toSet()
    }

    private fun references(text: String) = Regex("[补停换调]\\d+").findAll(text).map { it.value }.toSet()

    private fun change(text: String, title: String): ClassPattern {
        val parts = text.split('/')
        val day = requireNotNull(Regex("周([1-7])").find(parts[0])) { "调课星期格式不正确" }.groupValues[1].toInt()
        val section = requireNotNull(Regex("第(\\d+)节").find(parts[0])) { "调课节次格式不正确" }.groupValues[1].toInt()
        val count = requireNotNull(Regex("连续(\\d+)节").find(parts[0])) { "调课时长格式不正确" }.groupValues[1].toInt()
        return ClassPattern(title, parts.getOrElse(2) { "" }, parts.getOrElse(1) { "" }, day, weeks(parts[0]), section, count)
    }
}
