package com.doublesail.scheduleboard.schedule

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.scheduleStore by preferencesDataStore("imported_schedule")

class IcsScheduleRepository(context: Context) : ScheduleRepository {
    private val store = context.applicationContext.scheduleStore
    override val snapshot = store.data.map { values ->
        try {
            val list = JSONArray(values[Events] ?: "[]")
            val events = (0 until list.length()).map { index ->
                val item = list.getJSONObject(index)
                ScheduleEvent(
                    item.getString("id"), item.getString("title"), Instant.ofEpochMilli(item.getLong("start")),
                    Instant.ofEpochMilli(item.getLong("end")), item.optBoolean("allDay"),
                    item.optString("location"), item.optString("teacher"), item.optString("description"),
                    EventKind.valueOf(item.optString("kind", "COURSE")),
                )
            }
            ScheduleSnapshot(events, values[Source].orEmpty(), values[ImportedAt]?.let(Instant::ofEpochMilli))
        } catch (_: Exception) {
            ScheduleSnapshot(error = "课表缓存无法读取，请重新导入")
        }
    }.catch { error ->
        if (error is IOException) emit(ScheduleSnapshot(error = "无法读取课表，请检查存储空间")) else throw error
    }

    suspend fun import(input: InputStream, sourceName: String, zone: ZoneId): Int = withContext(Dispatchers.IO) {
        val output = ByteArrayOutputStream()
        input.use { stream ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= 4 * 1024 * 1024) { "课表文件超过 4 MB" }
                output.write(buffer, 0, count)
            }
        }
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output.toByteArray())).toString()
        val events = IcsParser.parse(text, zone)
        replace(events, sourceName)
        events.size
    }

    suspend fun replace(events: List<ScheduleEvent>, sourceName: String) = withContext(Dispatchers.IO) {
        val json = JSONArray()
        events.forEach { event ->
            json.put(JSONObject().apply {
                put("id", event.id); put("title", event.title); put("start", event.start.toEpochMilli())
                put("end", event.endExclusive.toEpochMilli()); put("allDay", event.allDay)
                put("location", event.location); put("teacher", event.teacher); put("description", event.description)
                put("kind", event.kind.name)
            })
        }
        store.edit {
            it[Events] = json.toString()
            it[Source] = sourceName.take(200)
            it[ImportedAt] = System.currentTimeMillis()
        }
    }

    suspend fun clear() { store.edit { it.clear() } }

    private companion object {
        val Events = stringPreferencesKey("events_json")
        val Source = stringPreferencesKey("source")
        val ImportedAt = longPreferencesKey("imported_at")
    }
}
