package com.doublesail.scheduleboard.tasks.google

import com.doublesail.scheduleboard.tasks.BoardTask
import org.json.JSONArray
import org.json.JSONObject

data class CachedGoogleTask(val id: String, val remoteId: String = "", val title: String,
    val completed: Boolean = false, val deleted: Boolean = false)

/** Each operation has an immutable revision, so a network acknowledgment cannot erase a newer edit. */
data class TaskEdit(val id: String, val revision: String, val title: String? = null,
    val completed: Boolean? = null, val delete: Boolean = false)

data class GoogleTaskCache(val account: String = "", val listId: String = "", val listTitle: String = "",
    val lists: List<GoogleTaskList> = emptyList(), val records: List<CachedGoogleTask> = emptyList(),
    val edits: List<TaskEdit> = emptyList(), val lastSync: Long = 0) {
    val connected get() = account.isNotEmpty()
    val tasks get() = records.filterNot { it.deleted }.map { BoardTask(it.id, it.title, it.completed) }

    fun merge(remote: List<RemoteTask>): GoogleTaskCache {
        val byRemote = records.filter { it.remoteId.isNotEmpty() }.associateBy { it.remoteId }
        val fetched = remote.filterNot { it.deleted || it.hidden }.map {
            CachedGoogleTask(byRemote[it.id]?.id ?: it.id, it.id, it.title, it.completed)
        }.toMutableList()
        edits.forEach { edit ->
            val old = records.find { it.id == edit.id } ?: return@forEach
            val index = fetched.indexOfFirst { it.id == edit.id }
            val base = if (index >= 0) fetched[index] else old
            // Remote deletions win over pending edits. New, not-yet-uploaded tasks remain visible offline.
            if (index >= 0 || old.remoteId.isEmpty()) {
                val updated = base.copy(title = edit.title ?: base.title, completed = edit.completed ?: base.completed, deleted = edit.delete)
                if (index >= 0) fetched[index] = updated else fetched.add(updated)
            }
        }
        return copy(records = fetched, edits = edits.filter { edit -> fetched.any { it.id == edit.id } })
    }

    fun encode() = JSONObject().apply {
        put("account", account); put("list", listId); put("listTitle", listTitle); put("lastSync", lastSync)
        put("lists", JSONArray().apply { lists.forEach { put(JSONObject().put("id", it.id).put("title", it.title)) } })
        put("records", JSONArray().apply { records.forEach { item -> put(JSONObject().put("id", item.id)
            .put("remote", item.remoteId).put("title", item.title).put("completed", item.completed).put("deleted", item.deleted)) } })
        put("edits", JSONArray().apply { edits.forEach { edit -> put(JSONObject().put("id", edit.id).put("revision", edit.revision)
            .put("title", edit.title ?: JSONObject.NULL).put("completed", edit.completed ?: JSONObject.NULL).put("delete", edit.delete)) } })
    }.toString()

    companion object {
        fun decode(text: String?): GoogleTaskCache {
            if (text == null) return GoogleTaskCache()
            val json = JSONObject(text)
            fun <T> array(name: String, map: (JSONObject) -> T): List<T> {
                val array = json.optJSONArray(name) ?: return emptyList()
                return (0 until array.length()).map { map(array.getJSONObject(it)) }
            }
            return GoogleTaskCache(json.optString("account"), json.optString("list"), json.optString("listTitle"),
                array("lists") { GoogleTaskList(it.getString("id"), it.getString("title")) },
                array("records") { CachedGoogleTask(it.getString("id"), it.optString("remote"), it.getString("title"), it.optBoolean("completed"), it.optBoolean("deleted")) },
                array("edits") { TaskEdit(it.getString("id"), it.getString("revision"),
                    if (it.isNull("title")) null else it.getString("title"),
                    if (it.isNull("completed")) null else it.getBoolean("completed"), it.optBoolean("delete")) }, json.optLong("lastSync"))
        }
    }
}
