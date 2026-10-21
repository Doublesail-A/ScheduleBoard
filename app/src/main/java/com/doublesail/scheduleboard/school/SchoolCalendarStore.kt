package com.doublesail.scheduleboard.school

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import org.json.JSONObject

class SchoolCalendarStore(private val context: Context) {
    private val file = AtomicFile(File(context.filesDir, "school-calendar-data.json"))

    fun resolve(year: String, term: String, override: LocalDate?): SchoolCalendar {
        var text = runCatching { file.openRead().bufferedReader().use { it.readText() } }
            .getOrElse { context.assets.open("school-calendars.json").bufferedReader().use { it.readText() } }
        if (!file.baseFile.exists() || System.currentTimeMillis() - file.baseFile.lastModified() > 86_400_000L) {
            val updated = runCatching {
                val url = URL("https://raw.githubusercontent.com/Doublesail-A/iFAFU/codex/material-you-redesign/data/school-calendars.json")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5_000; connection.readTimeout = 6_000
                    check(connection.responseCode == 200)
                    val value = connection.inputStream.bufferedReader().use { reader ->
                        val buffer = CharArray(8192)
                        buildString {
                            while (true) {
                                val count = reader.read(buffer)
                                if (count < 0) break
                                require(length + count <= 1_000_000)
                                append(buffer, 0, count)
                            }
                        }
                    }
                    validate(value)
                    value
                } finally { connection.disconnect() }
            }.getOrNull()
            if (updated != null) {
                text = updated
                val output = file.startWrite()
                try { output.write(text.toByteArray()); file.finishWrite(output) }
                catch (error: Exception) { file.failWrite(output) }
            }
        }
        val data = runCatching { validate(text) }.getOrElse {
            validate(context.assets.open("school-calendars.json").bufferedReader().use { it.readText() })
        }
        val calendars = data.getJSONArray("calendars")
        val record = (0 until calendars.length()).map { calendars.getJSONObject(it) }.firstOrNull {
            it.getString("school") == "FAFU" && it.getString("year") == year && it.getString("term") == term
        }
        require(record != null || override != null) { "该学期尚无已核对校历，请在登录页填写第一周日期后重试" }
        val closed = mutableSetOf<LocalDate>()
        val moves = mutableMapOf<LocalDate, LocalDate>()
        record?.getJSONArray("holidays")?.let { holidays ->
            for (index in 0 until holidays.length()) {
                val holiday = holidays.getJSONObject(index)
                val start = LocalDate.parse(holiday.getString("from"))
                repeat(holiday.getInt("days")) { closed += start.plusDays(it.toLong()) }
                val changes = holiday.getJSONObject("changes")
                changes.keys().forEach { from -> moves[LocalDate.parse(from)] = LocalDate.parse(changes.getString(from)) }
            }
        }
        return SchoolCalendar(override ?: LocalDate.parse(requireNotNull(record).getString("firstWeek")),
            closed, moves, record?.optString("source").orEmpty())
    }

    private fun validate(text: String): JSONObject {
        require(text.length in 1..1_000_000)
        val json = JSONObject(text)
        require(json.getInt("version") == 1)
        val calendars = json.getJSONArray("calendars")
        require(calendars.length() in 1..500)
        for (index in 0 until calendars.length()) {
            val item = calendars.getJSONObject(index)
            require(item.getString("year").matches(Regex("\\d{4}-\\d{4}")))
            require(item.getString("term") in listOf("1", "2"))
            LocalDate.parse(item.getString("firstWeek"))
            val holidays = item.getJSONArray("holidays")
            require(holidays.length() <= 100)
            for (h in 0 until holidays.length()) {
                val holiday = holidays.getJSONObject(h)
                require(holiday.getInt("days") in 0..100)
                LocalDate.parse(holiday.getString("from"))
                val changes = holiday.getJSONObject("changes")
                require(changes.length() <= 100)
                changes.keys().forEach { LocalDate.parse(it); LocalDate.parse(changes.getString(it)) }
            }
        }
        return json
    }
}
