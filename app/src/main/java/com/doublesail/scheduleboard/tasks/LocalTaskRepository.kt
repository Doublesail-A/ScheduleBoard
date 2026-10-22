package com.doublesail.scheduleboard.tasks

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.taskStore by preferencesDataStore("local_tasks")

class LocalTaskRepository(context: Context) : TaskRepository {
    private val store = context.applicationContext.taskStore
    override val snapshot = store.data.map { values ->
        try { TaskSnapshot(decode(values[Tasks])) }
        catch (_: Exception) { TaskSnapshot(error = "待办缓存无法读取，请检查本机存储") }
    }.catch { error ->
        if (error is IOException) emit(TaskSnapshot(error = "无法读取待办，请检查存储空间")) else throw error
    }.flowOn(Dispatchers.IO)

    override suspend fun add(title: String) {
        require(title.isNotBlank() && title.trim().length <= 200) { "待办名称需要 1–200 个字符" }
        store.edit { values ->
            val tasks = decode(values[Tasks])
            require(tasks.size < 500) { "待办过多，请先清理已完成项目" }
            values[Tasks] = encode(tasks + BoardTask(UUID.randomUUID().toString(), title.trim()))
        }
    }

    override suspend fun setCompleted(id: String, completed: Boolean) {
        store.edit { values ->
            values[Tasks] = encode(decode(values[Tasks]).map { if (it.id == id) it.copy(completed = completed) else it })
        }
    }

    override suspend fun clearCompleted() {
        store.edit { values -> values[Tasks] = encode(decode(values[Tasks]).filterNot { it.completed }) }
    }

    private fun decode(value: String?): List<BoardTask> {
        val list = JSONArray(value ?: "[]")
        return (0 until list.length()).map { index ->
            val item = list.getJSONObject(index)
            BoardTask(item.getString("id"), item.getString("title"), item.optBoolean("completed"))
        }
    }

    private fun encode(tasks: List<BoardTask>) = JSONArray().apply {
        tasks.forEach { task -> put(JSONObject().apply {
            put("id", task.id); put("title", task.title); put("completed", task.completed)
        }) }
    }.toString()

    private companion object { val Tasks = stringPreferencesKey("tasks_json") }
}
